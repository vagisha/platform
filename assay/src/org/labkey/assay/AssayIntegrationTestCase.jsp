/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
<%@ page import="com.google.common.base.Charsets" %>
<%@ page import="org.apache.logging.log4j.LogManager" %>
<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.hamcrest.MatcherAssert" %>
<%@ page import="org.junit.After" %>
<%@ page import="org.junit.Before" %>
<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.assay.AssayDataCollector" %>
<%@ page import="org.labkey.api.assay.AssayDomainService" %>
<%@ page import="org.labkey.api.assay.AssayProvider" %>
<%@ page import="org.labkey.api.assay.AssayRunCreator" %>
<%@ page import="org.labkey.api.assay.AssayService" %>
<%@ page import="org.labkey.api.assay.PipelineDataCollector" %>
<%@ page import="org.labkey.api.assay.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.JdbcType" %>
<%@ page import="org.labkey.api.data.PropertyStorageSpec" %>
<%@ page import="org.labkey.api.exp.ExperimentException" %>
<%@ page import="org.labkey.api.exp.OntologyManager" %>
<%@ page import="org.labkey.api.exp.OntologyObject" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.exp.api.ExpData" %>
<%@ page import="org.labkey.api.exp.api.ExpExperiment" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.exp.property.Domain" %>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page import="org.labkey.api.exp.property.PropertyService" %>
<%@ page import="org.labkey.api.exp.query.ExpSchema" %>
<%@ page import="org.labkey.api.files.FileContentService" %>

<%@ page import="org.labkey.api.files.FilesAdminOptions" %>
<%@ page import="org.labkey.api.gwt.client.assay.AssayException" %>
<%@ page import="org.labkey.api.gwt.client.assay.model.GWTProtocol" %>
<%@ page import="org.labkey.api.gwt.client.model.GWTDomain" %>
<%@ page import="org.labkey.api.gwt.client.model.GWTPropertyDescriptor" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.query.ValidationException" %>

<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.FileUtil" %>
<%@ page import="org.labkey.api.util.JunitUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="org.labkey.api.view.ViewBackgroundInfo" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.assay.AssayDomainServiceImpl" %>
<%@ page import="org.labkey.assay.TsvAssayProvider" %>
<%@ page import="org.springframework.mock.web.MockMultipartHttpServletRequest" %>
<%@ page import="java.io.File" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="java.nio.file.Files" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="static org.labkey.api.files.FileContentService.UPLOADED_FILE" %>
<%@ page import="static java.util.Collections.emptySet" %>
<%@ page import="static org.hamcrest.CoreMatchers.hasItem" %>
<%@ page import="static org.hamcrest.CoreMatchers.not" %>

<%@ page extends="org.labkey.api.jsp.JspTest.BVT" %>
<%!
    Logger log;
    User user;
    Container c;
    final String ASSAY_NAME = "MyAssay";

    @Before
    public void setUp()
    {
        log = LogManager.getLogger("AssayIntegrationTestCase");
        JunitUtil.deleteTestContainer();
        c = JunitUtil.getTestContainer();
        user = TestContext.get().getUser();
    }

    @After
    public void tearDown()
    {
    }

    public ExpMaterial createMaterial()
    {
        final String materialName = "TestMaterial";
        final String materialLsid = ExperimentService.get().generateLSID(c, ExpMaterial.class, "TestMaterial");
        ExpMaterial material = ExperimentService.get().createExpMaterial(c, materialLsid, materialName);
        material.save(user);
        return material;
    }

    public Pair<AssayProvider, ExpProtocol> createAssay(ViewContext context)
            throws AssayException, ValidationException
    {
        // create assay design
        AssayDomainService assayDomainService = new AssayDomainServiceImpl(context);
        GWTProtocol assayTemplate = assayDomainService.getAssayTemplate("General");
        assayTemplate.setName(ASSAY_NAME);
        List<GWTDomain<GWTPropertyDescriptor>> domains = assayTemplate.getDomains();

        // clear the batch domain fields
        GWTDomain<GWTPropertyDescriptor> batchDomain = domains.stream().filter(d -> "Batch Fields".equals(d.getName())).findFirst().orElseThrow();
        batchDomain.getFields().clear();

        // clear the run domain fields
        GWTDomain<GWTPropertyDescriptor> runDomain = domains.stream().filter(d -> "Run Fields".equals(d.getName())).findFirst().orElseThrow();
        runDomain.getFields().clear();

        // clear the result domain fields and add a sample lookup
        GWTDomain<GWTPropertyDescriptor> resultDomain = domains.stream().filter(d -> "Data Fields".equals(d.getName())).findFirst().orElseThrow();
        resultDomain.getFields().clear();
        GWTPropertyDescriptor sampleLookup = new GWTPropertyDescriptor("SampleLookup", "int");
        sampleLookup.setLookupSchema(ExpSchema.SCHEMA_NAME);
        sampleLookup.setLookupQuery(ExpSchema.TableType.Materials.name());
        resultDomain.getFields().add(sampleLookup);

        // create the assay
        log.info("creating assay");
        GWTProtocol savedAssayDesign = assayDomainService.saveChanges(assayTemplate, true);
        ExpProtocol assayProtocol = ExperimentService.get().getExpProtocol(savedAssayDesign.getProtocolId());
        AssayProvider provider = AssayService.get().getProvider(assayProtocol);

        return Pair.of(provider, assayProtocol);
    }

    private ExpRun assayImportFile(Container c, User user, AssayProvider provider, ExpProtocol assayProtocol, File file, boolean allowCrossRunFileInputs)
            throws ExperimentException, ValidationException
    {
        HttpSession session = TestContext.get().getRequest().getSession();

        // Use the AssayFileUploadForm and the PipelineDataCollector to simulate the user selecting a file from the filebrowser.
        PipelineDataCollector.setFileCollection(session, c, assayProtocol, List.of(Map.of(AssayDataCollector.PRIMARY_FILE, file)));

        // Use a multipart request to trigger the AssayFileWriter.savePipelineFiles() code path that copies files into the assayupload directory
        var mockRequest = new MockMultipartHttpServletRequest();
        mockRequest.setUserPrincipal(user);
        mockRequest.setSession(session);

        var mockContext = new ViewContext(mockRequest, null, null);
        mockContext.setContainer(c);
        mockContext.setUser(user);

        var uploadForm = new AssayRunUploadForm<TsvAssayProvider>();
        uploadForm.setViewContext(new ViewContext(mockRequest, null, null));
        uploadForm.setContainer(c);
        uploadForm.setUser(user);
        uploadForm.setRowId(assayProtocol.getRowId());
        uploadForm.setName("New Run2");
        uploadForm.setDataCollectorName("Pipeline");
        if (allowCrossRunFileInputs)
            uploadForm.setAllowCrossRunFileInputs(true);

        // create a new run
        AssayRunCreator runCreator = provider.getRunCreator();
        Pair<ExpExperiment, ExpRun> pair = runCreator.saveExperimentRun(uploadForm, null);
        return pair.second;
    }

    // Issue 41675: ERROR: insert or update on table "edge" violates foreign key constraint "fk_edge_to_object"
    // - imports a file into an assay
    // - sets some file properties
    // - deletes the assay run
    // - verifies the exp.data is detatched from the run, but the properties haven't been deleted
    // - re-imports the same file again
    //
    // Issue 42141: assay: importing a file into assay after deleting the run creates a new exp.data row
    // - verify only a single exp.data exists for the file
    @Test
    public void testIssue41675() throws Exception
    {
        final var info = new ViewBackgroundInfo(c, user, null);
        final var context = new ViewContext(info);
        final var pipeRoot = PipelineService.get().findPipelineRoot(info.getContainer());


        // create a custom file property
        FileContentService fileSvc = FileContentService.get();
        PropertyDescriptor someStuffProp = null;
        if (fileSvc != null)
        {
            String domainUri = fileSvc.getDomainURI(c, FilesAdminOptions.fileConfig.useCustom);
            Domain domain = PropertyService.get().createDomain(c, domainUri, "FileProps");
            domain.addProperty(new PropertyStorageSpec("SomeStuff", JdbcType.VARCHAR));
            domain.save(user);

            DomainProperty dp = domain.getPropertyByName("SomeStuff");
            someStuffProp = dp.getPropertyDescriptor();
        }

        // create the assay
        var assayPair = createAssay(context);
        var provider = assayPair.first;
        var assayProtocol = assayPair.second;

        // create a sample that will be used as an input to the run
        log.info("creating material");
        var material = createMaterial();
        final var materialName = material.getName();

        // create a file in the pipeline root to import
        var file = File.createTempFile(getClass().getSimpleName(), ".tsv", pipeRoot.getRootPath());
        Files.writeString(file.toPath(), "SampleLookup\n" + materialName + "\n", Charsets.UTF_8);

        // import the file
        log.info("first import");
        var run = assayImportFile(c, user, provider, assayProtocol, file, false);

        // verify the exp.data is attached to the run
        assertEquals(1, run.getDataOutputs().size());
        final ExpData originalOutputData = run.getDataOutputs().get(0);
        assertEquals(file.getName(), originalOutputData.getName());

        final int dataRowId = originalOutputData.getRowId();
        final String dataLsid = originalOutputData.getLSID();
        final Integer dataOID = originalOutputData.getObjectId();
        final OntologyObject oo1 = OntologyManager.getOntologyObject(originalOutputData.getObjectId());
        assertNotNull(oo1);

        // set some properties that will be verified later
        originalOutputData.setComment(user, "hello world");
        if (someStuffProp != null)
            originalOutputData.setProperty(user, someStuffProp, "SomeData");

        // verify lineage
        var parents = ExperimentService.get().getParents(c, user, originalOutputData);
        assertThat(parents.second, hasItem(material));

        // delete the run
        log.info("run delete");
        run.delete(user);

        // verify the exp.data, exp.object, and the properties were not deleted
        log.info("verifying post run delete");
        ExpData data2 = ExperimentService.get().getExpData(dataRowId);
        assertNotNull(data2);
        assertEquals(dataLsid, data2.getLSID());
        assertEquals(dataOID, data2.getObjectId());
        assertEquals("hello world", data2.getComment());
        if (someStuffProp != null)
            assertEquals("SomeData", data2.getProperty(someStuffProp));

        assertNull(data2.getRunId());
        parents = ExperimentService.get().getParents(c, user, data2);
        assertThat(parents.second, not(hasItem(material)));

        OntologyObject oo2 = OntologyManager.getOntologyObject(data2.getObjectId());
        assertNotNull(oo2);
        assertEquals(oo1.getObjectId(), oo2.getObjectId());
        assertEquals(oo1.getObjectURI(), oo2.getObjectURI());
        assertEquals(oo1.getContainer(), oo2.getContainer());
        assertEquals(oo1.getOwnerObjectId(), oo2.getOwnerObjectId());

        // import the same file again
        log.info("second import");
        var run2 = assayImportFile(c, user, provider, assayProtocol, file, false);

        // verify the exp.data and exp.object again
        log.info("verifying second upload");

        ExpData data3 = ExperimentService.get().getExpData(dataRowId);
        assertNotNull(data3);
        assertEquals(dataLsid, data3.getLSID());
        assertEquals(dataOID, data3.getObjectId());
        assertEquals("hello world", data3.getComment());
        if (someStuffProp != null)
            assertEquals("SomeData", data3.getProperty(someStuffProp));

        // Issue 42141: assay: importing a file into assay after deleting the run creates a new exp.data row
        var dataList = ExperimentService.get().getAllExpDataByURL(data3.getDataFileUrl(), null);
        assertEquals("Expected to re-use existing exp.data instead of creating a new one:\n" +
                dataList.toString(), 1, dataList.size());

        assertNotNull(data3.getRunId());
        assertEquals(run2.getRowId(), data3.getRunId().intValue());
        parents = ExperimentService.get().getParents(c, user, data3);
        assertThat(parents.second, hasItem(material));
    }

    // Issue 43470: Cannot import the output file from a provenance run into an assay
    // - attempt to import the file into the assay again
    // - verify an exception is thrown
    // - set allowCrossRunFileInput flag, attempt to import the file into the assay again
    // - verify the import is successful and a new exp.data is created for the same file
    @Test
    public void testCrossRunFileInputs() throws Exception
    {
        final var info = new ViewBackgroundInfo(c, user, null);
        final var context = new ViewContext(info);
        final var pipeRoot = PipelineService.get().findPipelineRoot(info.getContainer());

        // create the assay
        var assayPair = createAssay(context);
        var provider = assayPair.first;
        var assayProtocol = assayPair.second;

        // create a sample that will be used as an input to the run
        log.info("creating material");
        var material = createMaterial();
        final var materialName = material.getName();

        // create a file in the pipeline root to import
        var file = File.createTempFile(getClass().getSimpleName(), ".tsv", pipeRoot.getRootPath());
        Files.writeString(file.toPath(), "SampleLookup\n" + materialName + "\n", Charsets.UTF_8);

        var firstData = ExperimentService.get().createData(c, UPLOADED_FILE, file.getName());
        firstData.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(file).toURI());
        firstData.save(user);

        // create a sample derivation run where the file is an output
        ExpRun run = ExperimentService.get().createExperimentRun(c, "external transform run");
        run.setFilePathRoot(pipeRoot.getRootPath());

        ExpProtocol protocol = ExperimentService.get().ensureSampleDerivationProtocol(user);
        run.setProtocol(protocol);

        run = ExperimentService.get().saveSimpleExperimentRun(run,
                Map.of(material, "sample"),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Map.of(firstData, "output file"),
                Collections.emptyMap(),
                info,
                null,
                false,
                emptySet(),
                emptySet());

        // verify the material is an input and the file is an output
        MatcherAssert.assertThat(run.getMaterialInputs().keySet(), hasItem(material));
        MatcherAssert.assertThat(run.getDataOutputs(), hasItem(firstData));


        log.info("first assay import attempt");
        try
        {
            assayImportFile(c, user, provider, assayProtocol, file, false);
            fail("Expected to throw an exception");
        }
        catch (ValidationException e)
        {
            assertEquals("File '" + pipeRoot.relativePath(file) + "' has been previously imported in run '" + run.getName() + "' (" + run.getRowId() + ")",
                    e.getMessage());
        }

        log.info("second assay import attempt");
        var assayRun = assayImportFile(c, user, provider, assayProtocol, file, true);

        // verify the first exp.data is attached as an input and not as an output
        MatcherAssert.assertThat(assayRun.getMaterialInputs().keySet(), hasItem(material));
        MatcherAssert.assertThat(assayRun.getDataInputs().keySet(), hasItem(firstData));

        // verify there is a new exp.data attached as the assay output
        // and that it has the same dataFileUrl as the input file
        var dataOutputs = assayRun.getDataOutputs();
        assertEquals(1, dataOutputs.size());
        var assayOutputData = dataOutputs.get(0);
        assertNotEquals(firstData, assayOutputData);
        assertEquals(firstData.getDataFileUrl(), assayOutputData.getDataFileUrl());

        var dataList = ExperimentService.get().getAllExpDataByURL(firstData.getDataFileUrl(), null);
        assertEquals(assayOutputData, dataList.get(0));
        assertEquals(firstData, dataList.get(1));
    }
%>