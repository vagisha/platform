/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.api.exp.api;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.query.*;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.HttpView;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExperimentService
{
    static private Interface instance;

    public static final String MODULE_NAME = "Experiment";

    public static final String SCHEMA_LOCATION = "http://cpas.fhcrc.org/exp/xml http://www.labkey.org/download/XarSchema/V2.3/expTypes.xsd";

    static public Interface get()
    {
        return instance;
    }

    static public void setInstance(Interface impl)
    {
        instance = impl;
    }

    public interface Interface extends ExperimentRunTypeSource
    {
        public static final String SAMPLE_DERIVATION_PROTOCOL_LSID = "urn:lsid:labkey.org:Protocol:SampleDerivationProtocol";

        @Nullable
        ExpObject findObjectFromLSID(String lsid);

        ExpRun getExpRun(int rowid);
        ExpRun getExpRun(String lsid);
        ExpRun[] getExpRuns(Container container, ExpProtocol parentProtocol, ExpProtocol childProtocol);
        ExpRun createExperimentRun(Container container, String name);

        ExpData getExpData(int rowid);
        ExpData getExpData(String lsid);
        ExpData[] getExpDatas(Container container, DataType type);
        /**
         * Create a data object.  The object will be unsaved, and will have a name which is a GUID.
         */
        ExpData createData(Container container, DataType type);
        ExpData createData(Container container, DataType type, String name);
        ExpData createData(Container container, String name, String lsid);
        ExpData createData(URI uri, XarSource source) throws XarFormatException;

        ExpMaterial createExpMaterial(Container container, String lsid, String name);
        ExpMaterial getExpMaterial(int rowid);
        ExpMaterial getExpMaterial(String lsid);

        /**
         * Looks in all the sample sets visible from the given container for a single match with the specified name 
         */
        List<? extends ExpMaterial> getExpMaterialsByName(String name, Container container, User user);

        Map<String, ExpSampleSet> getSampleSetsForRoles(Container container, ContainerFilter filter, ExpProtocol.ApplicationType type);

        /**
         * Create a new SampleSet with the provided properties.  If a 'Name' property exists in the list, it will be used
         * as the 'id' property of the SampleSet.  Either a 'Name' property must exist or at least one idCol index must be provided.
         */
        ExpSampleSet createSampleSet(Container container, User user, String name, String description, List<GWTPropertyDescriptor> properties, int idCol1, int idCol2, int idCol3, int parentCol)
            throws ExperimentException, SQLException;

        ExpSampleSet createSampleSet();
        ExpSampleSet getSampleSet(int rowid);
        ExpSampleSet getSampleSet(String lsid);

        /**
         * @param includeOtherContainers whether sample sets from the shared container or the container's project should be included
         */
        ExpSampleSet[] getSampleSets(Container container, User user, boolean includeOtherContainers);
        ExpSampleSet getSampleSet(Container container, String name);
        ExpSampleSet lookupActiveSampleSet(Container container);
        void setActiveSampleSet(Container container, ExpSampleSet sampleSet);

        ExpExperiment createHiddenRunGroup(Container container, User user, ExpRun... runs);

        ExpExperiment createExpExperiment(Container container, String name);
        ExpExperiment getExpExperiment(int rowid);
        ExpExperiment getExpExperiment(String lsid);
        ExpExperiment[] getExperiments(Container container, User user, boolean includeOtherContainers, boolean includeBatches);

        ExpProtocol getExpProtocol(int rowid);
        ExpProtocol getExpProtocol(String lsid);
        ExpProtocol getExpProtocol(Container container, String name);
        ExpProtocol createExpProtocol(Container container, ExpProtocol.ApplicationType type, String name);
        ExpProtocol createExpProtocol(Container container, ExpProtocol.ApplicationType type, String name, String lsid);

        /**
         * @param type may be null. If non-null, only return roles that are used for that type of application (input, output, or intermediate)
         */
        Set<String> getDataInputRoles(Container container, ContainerFilter containerFilter, ExpProtocol.ApplicationType... type);
        /**
         * @param type may be null. If non-null, only return roles that are used for that type of application (input, output, or intermediate) 
         */
        Set<String> getMaterialInputRoles(Container container, ExpProtocol.ApplicationType... type);


        /**
         * The following methods return TableInfo's suitable for using in queries.
         * These TableInfo's initially have no columns, but have methods to
         * add particular columns as needed by the client.
         */
        ExpRunTable createRunTable(String name, UserSchema schema);
        /** Create a RunGroupMap junction table joining Runs and RunGroups. */
        ExpRunGroupMapTable createRunGroupMapTable(String name, UserSchema schema);
        ExpDataTable createDataTable(String name, UserSchema schema);
        ExpDataInputTable createDataInputTable(String name, ExpSchema expSchema);
        ExpSampleSetTable createSampleSetTable(String name, UserSchema schema);
        ExpProtocolTable createProtocolTable(String name, UserSchema schema);
        ExpExperimentTable createExperimentTable(String name, UserSchema schema);
        ExpMaterialTable createMaterialTable(String name, UserSchema schema);
        ExpMaterialInputTable createMaterialInputTable(String name, ExpSchema expSchema);
        ExpProtocolApplicationTable createProtocolApplicationTable(String name, UserSchema schema);

        String generateLSID(Container container, Class<? extends ExpObject> clazz, String name);
        String generateGuidLSID(Container container, Class<? extends ExpObject> clazz);
        String generateLSID(Container container, DataType type, String name);
        String generateGuidLSID(Container container, DataType type);

        DataType getDataType(String namespacePrefix);

        void ensureTransaction();
        void commitTransaction();
        void closeTransaction();

        ExperimentRunListView createExperimentRunWebPart(ViewContext context, ExperimentRunType type);

        public DbSchema getSchema();

        ExpProtocolApplication getExpProtocolApplication(String lsid);
        ExpProtocolApplication[] getExpProtocolApplicationsForProtocolLSID(String protocolLSID) throws SQLException;

        ExpData[] getExpData(Container c) throws SQLException;
        ExpData getExpDataByURL(String canonicalURL, @Nullable Container container);
        ExpData getExpDataByURL(File f, @Nullable Container c);
        
        TableInfo getTinfoMaterial();
        TableInfo getTinfoMaterialSource();
        TableInfo getTinfoProtocol();
        TableInfo getTinfoProtocolApplication();
        TableInfo getTinfoExperiment();
        TableInfo getTinfoExperimentRun();
        TableInfo getTinfoRunList();
        TableInfo getTinfoData();
        TableInfo getTinfoDataInput();
        TableInfo getTinfoPropertyDescriptor();
        ExpSampleSet ensureDefaultSampleSet();
        ExpSampleSet ensureActiveSampleSet(Container container);
        public String getDefaultSampleSetLsid();

        ExpRun[] getRunsUsingMaterials(int... materialIds) throws SQLException;
        List<? extends ExpRun> getRunsUsingDatas(List<ExpData> datas);

        ExpRun getCreatingRun(File file, Container c);
        List<? extends ExpRun> getExpRunsForProtocolIds(boolean includeRelated, int... rowIds) throws SQLException;
        ExpRun[] getRunsUsingSampleSets(ExpSampleSet... sampleSets) throws SQLException;

        /**
         * @return the subset of these runs which are supposed to be deleted when one of their inputs is deleted.
         */
        List<ExpRun> runsDeletedWithInput(ExpRun[] runs) throws SQLException;

        void deleteAllExpObjInContainer(Container container, User user) throws ExperimentException;

        Lsid getSampleSetLsid(String name, Container container);

        void clearCaches();

        ProtocolApplicationParameter[] getProtocolApplicationParameters(int rowId);

        void moveContainer(Container c, Container cOldParent, Container cNewParent) throws SQLException, ExperimentException;

        LsidType findType(Lsid lsid);

        Identifiable getObject(Lsid lsid);

        ExpData[] deleteExperimentRunForMove(int runId, User user) throws SQLException, ExperimentException;

        /** Kicks off an asynchronous move - a PipelineJob is submitted to the queue to perform the move */
        void moveRuns(ViewBackgroundInfo targetInfo, Container sourceContainer, List<ExpRun> runs) throws IOException;
        public ExpProtocol insertSimpleProtocol(ExpProtocol baseProtocol, User user);

        /**
         * The run must be a instance of a protocol created with insertSimpleProtocol().
         * The run must have at least one input and one output.
         * @param run ExperimentRun, populated with protocol, name, etc
         * @param inputMaterials map from input role name to input material
         * @param inputDatas map from input role name to input data
         * @param outputMaterials map from output role name to output material
         * @param outputDatas map from output role name to output data
         * @param info context information, including the user
         * @param log output log target
         */
        public ExpRun saveSimpleExperimentRun(ExpRun run, Map<ExpMaterial, String> inputMaterials, Map<ExpData, String> inputDatas, Map<ExpMaterial, String> outputMaterials, Map<ExpData, String> outputDatas, Map<ExpData, String> transformedDatas, ViewBackgroundInfo info, Logger log, boolean loadDataFiles) throws ExperimentException;
        public ExpRun deriveSamples(Map<ExpMaterial, String> inputMaterials, Map<ExpMaterial, String> outputMaterials, ViewBackgroundInfo info, Logger log) throws ExperimentException;

        public void registerExperimentDataHandler(ExperimentDataHandler handler);
        public void registerExperimentRunTypeSource(ExperimentRunTypeSource source);
        public void registerDataType(DataType type);
        public void registerProtocolImplementation(ProtocolImplementation impl);

        public ProtocolImplementation getProtocolImplementation(String name);

        ExpProtocolApplication getExpProtocolApplication(int rowId);
        ExpProtocolApplication[] getExpProtocolApplicationsForRun(int runId);

        ExpProtocol[] getExpProtocols(Container... containers);
        ExpProtocol[] getAllExpProtocols();

        /**
         * Kicks off a pipeline job to asynchronously load the XAR from disk
         * @return the job responsible for doing the work
         */
        PipelineJob importXarAsync(ViewBackgroundInfo info, File file, String description, PipeRoot root) throws IOException;

        /**
         * Loads the xar synchronously, in the context of the pipelineJob
         * @return the runs loaded from the XAR
         */
        public List<ExpRun> importXar(XarSource source, PipelineJob pipelineJob, boolean reloadExistingRuns) throws ExperimentException;

        /**
         * Provides access to an object that should be locked before inserting experiment runs, protocols, etc.
         * @return lock object on which to synchronize
         */
        public Object getImportLock();

        HttpView createRunExportView(Container container, String defaultFilenamePrefix);
        HttpView createFileExportView(Container container, String defaultFilenamePrefix);

        void auditRunEvent(User user, ExpProtocol protocol, ExpRun run, String message);

        ExpExperiment[] getMatchingBatches(String name, Container container, ExpProtocol protocol);
    }
}
