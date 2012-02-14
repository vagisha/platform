package org.labkey.api.study.assay.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;

/**
 * Pipeline job for completing the final steps of the assay upload wizard in the background.
 * Runs any transform or validation scripts, and does the actual import
 * User: jeckels
 * Date: Feb 13, 2012
 */
public class AssayUploadPipelineJob<ProviderType extends AssayProvider> extends PipelineJob
{
    private int _batchId;
    private AssayRunAsyncContext _context;
    private File _primaryFile;
    private boolean _forceSaveBatchProps;

    /**
     * @param forceSaveBatchProps whether we need to save the batch properties, or if it's already been handled
     */
    public AssayUploadPipelineJob(AssayRunAsyncContext<ProviderType> context, ViewBackgroundInfo info, @NotNull ExpExperiment batch, boolean forceSaveBatchProps, PipeRoot root, File primaryFile) throws IOException, ExperimentException
    {
        super(context.getProvider().getName(), info, root);
        String baseName = primaryFile.getName();
        if (baseName.indexOf(".") != -1)
        {
            baseName = baseName.substring(0, baseName.lastIndexOf("."));
        }
        setLogFile(FT_LOG.newFile(primaryFile.getParentFile(), baseName));
        _forceSaveBatchProps = forceSaveBatchProps;

        _context = context;
        _batchId = batch.getRowId();
        _primaryFile = primaryFile;
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Assay upload";
    }

    @Override
    public void run()
    {
        // Create the basic run
        ExpRun run = AssayService.get().createExperimentRun(_context.getName(), getContainer(), _context.getProtocol(), _primaryFile);
        run.setComments(_context.getComments());

        try
        {
            // Find a batch for the run
            ExpExperiment batch = ExperimentService.get().getExpExperiment(_batchId);
            if (batch == null)
            {
                // Batch was deleted already, make a new one
                batch = AssayService.get().createStandardBatch(getContainer(), null, _context.getProtocol());
                batch.save(getUser());

                // Be sure to save batch properties since we had a make a new one
                _forceSaveBatchProps = true;
            }

            // Do all the real work of the import
            _context.getProvider().getRunCreator().saveExperimentRun(_context, batch, run, _forceSaveBatchProps);
            setStatus(COMPLETE_STATUS);
        }
        catch (Exception e)
        {
            getLogger().error("Error", e);
            setStatus(ERROR_STATUS);
        }
    }
}
