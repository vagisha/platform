/*
 * Copyright (c) 2008-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.experiment.pipeline;

import org.apache.commons.io.FileUtils;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.exp.pipeline.XarGeneratorId;
import org.labkey.api.exp.pipeline.XarGeneratorFactorySettings;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.*;
import org.labkey.api.util.FileType;
import org.labkey.api.util.NetworkDrive;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.ExpRunImpl;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.DataURLRelativizer;
import org.labkey.experiment.ExperimentRunGraph;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.util.*;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Creates an experiment run to represent the work that the task's job has done so far.
 * User: jeckels
 * Date: Jul 25, 2008
*/
public class XarGeneratorTask extends PipelineJob.Task<XarGeneratorTask.Factory> implements XarWriter
{
    public static class Factory extends AbstractTaskFactory<XarGeneratorFactorySettings, Factory>
    {
        private FileType _outputType = XarGeneratorId.FT_PIPE_XAR_XML;

        public Factory()
        {
            super(XarGeneratorId.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new XarGeneratorTask(this, job);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public FileType getOutputType()
        {
            return _outputType;
        }

        public String getStatusName()
        {
            return "IMPORT RESULTS";
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        protected File getXarFile(PipelineJob job)
        {
            FileAnalysisJobSupport jobSupport = job.getJobSupport(FileAnalysisJobSupport.class);
            return getOutputType().newFile(jobSupport.getAnalysisDirectory(), jobSupport.getBaseName());
        }

        public boolean isJobComplete(PipelineJob job)
        {
            // We can use an existing XAR file from disk if it's been generated, but we need to load it because
            // there's no simple way to tell if it's already been imported or not, or if it's been subsequently deleted
            return false;
        }

        public void configure(XarGeneratorFactorySettings settings)
        {
            super.configure(settings);

            if (settings.getOutputExt() != null)
                _outputType = new FileType(settings.getOutputExt());
        }
    }
    
    public XarGeneratorTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    /**
     * The basic steps are:
     * 1. Start a transaction.
     * 2. Create a protocol and a run and insert them into the database, not loading the data files.
     * 3. Export the run and protocol to a temporary XAR on the file system.
     * 4. Commit the transaction.
     * 5. Import the temporary XAR (not reloading the runs it references), which causes its referenced data files to load.
     * 6. Rename the temporary XAR to its permanent name.
     *
     * This allows us to quickly tell if the task is already complete by checking for the XAR file. If it exists, we
     * can simply reimport it. If the temporary file exists, we can skip directly to step 5 above. 
     */
    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            // Keep track of all of the runs that have been created by this task
            Set<ExpRun> importedRuns = new HashSet<ExpRun>();

            File permanentXAR = _factory.getXarFile(getJob());
            if (NetworkDrive.exists(permanentXAR))
            {
                // Be sure that it's been imported (and not already deleted from the database)
                importedRuns.addAll(ExperimentService.get().importXar(new FileXarSource(permanentXAR, getJob()), getJob(), false));
            }
            else
            {
                if (!NetworkDrive.exists(getLoadingXarFile()))
                {
                    XarSource source = new XarGeneratorSource(getJob(), _factory.getXarFile(getJob()));
                    importedRuns.add(ExpGeneratorHelper.insertRun(getJob(), source, this));
                }

                // Load the data files for this run
                importedRuns.addAll(ExperimentService.get().importXar(new FileXarSource(getLoadingXarFile(), getJob()), getJob(), false));

                getLoadingXarFile().renameTo(permanentXAR);
            }

            // Check if we've been cancelled. If so, delete any newly created runs from the database
            PipelineStatusFile statusFile = PipelineService.get().getStatusFile(getJob().getLogFile());
            if (statusFile != null && (PipelineJob.CANCELLED_STATUS.equals(statusFile.getStatus()) || PipelineJob.CANCELLING_STATUS.equals(statusFile.getStatus())))
            {
                for (ExpRun importedRun : importedRuns)
                {
                    getJob().info("Deleting run " + importedRun.getName() + " due to cancellation request");
                    importedRun.delete(getJob().getUser());
                }
            }
        }
        catch (SQLException e)
        {
            throw new PipelineJobException("Failed to save experiment run in the database", e);
        }
        catch (ExperimentException e)
        {
            throw new PipelineJobException("Failed to import data files", e);
        }
        return new RecordedActionSet();
    }

    // XarWriter interface
    public void writeToDisk(ExpRunImpl run) throws PipelineJobException
    {
        File f = getLoadingXarFile();
        File tempFile = new File(f.getPath() + ".temp");

        FileOutputStream fOut = null;
        try
        {
            XarExporter exporter = new XarExporter(LSIDRelativizer.FOLDER_RELATIVE, DataURLRelativizer.RUN_RELATIVE_LOCATION.createURLRewriter());
            exporter.addExperimentRun(run);

            fOut = new FileOutputStream(tempFile);
            exporter.dumpXML(fOut);
            fOut.close();
            FileUtils.moveFile(tempFile, f);
            fOut = null;
        }
        catch (ExperimentException e)
        {
            throw new PipelineJobException("Failed to write XAR to disk", e);
        }
        catch (IOException e)
        {
            throw new PipelineJobException("Failed to write XAR to disk", e);
        }
        finally
        {
            if (fOut != null)
            {
                try { fOut.close(); } catch (IOException e) {}
            }
        }
    }

    private File getLoadingXarFile()
    {
        return new File(_factory.getXarFile(getJob()) + ".loading");
    }
}