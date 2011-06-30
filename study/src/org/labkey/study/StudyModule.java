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

package org.labkey.study;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.EnumConverter;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.ExperimentRunTypeSource;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.AssayDomainKind;
import org.labkey.api.exp.property.AssayResultDomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceLoader;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudySerializationRegistry;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayRunType;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.study.reports.CrosstabReportDescriptor;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.DefaultWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.wiki.WikiService;
import org.labkey.study.assay.AssayManager;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.assay.FileBasedModuleDataHandler;
import org.labkey.study.assay.ModuleAssayLoader;
import org.labkey.study.assay.TsvAssayProvider;
import org.labkey.study.assay.TsvDataHandler;
import org.labkey.study.assay.query.AssayAuditViewFactory;
import org.labkey.study.assay.query.AssaySchemaImpl;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.controllers.DatasetController;
import org.labkey.study.controllers.ParticipantGroupController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.StudyDefinitionController;
import org.labkey.study.controllers.StudyPagesController;
import org.labkey.study.controllers.StudyPropertiesController;
import org.labkey.study.controllers.assay.AssayController;
import org.labkey.study.controllers.designer.DesignerController;
import org.labkey.study.controllers.plate.PlateController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.controllers.samples.SpecimenApiController;
import org.labkey.study.controllers.samples.SpecimenController;
import org.labkey.study.controllers.security.SecurityController;
import org.labkey.study.dataset.DatasetAuditViewFactory;
import org.labkey.study.dataset.DatasetSnapshotProvider;
import org.labkey.study.designer.view.StudyDesignsWebPart;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.importer.StudyImportProvider;
import org.labkey.study.importer.StudyReload;
import org.labkey.study.model.CohortDomainKind;
import org.labkey.study.model.ContinuousDatasetDomainKind;
import org.labkey.study.model.DateDatasetDomainKind;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyDomainKind;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.TestDatasetDomainKind;
import org.labkey.study.model.VisitDatasetDomainKind;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.plate.PlateManager;
import org.labkey.study.plate.query.PlateSchema;
import org.labkey.study.query.StudySchemaProvider;
import org.labkey.study.reports.AttachmentReport;
import org.labkey.study.reports.ChartReportView;
import org.labkey.study.reports.EnrollmentReport;
import org.labkey.study.reports.ExportExcelReport;
import org.labkey.study.reports.ExternalReport;
import org.labkey.study.reports.StudyChartQueryReport;
import org.labkey.study.reports.StudyCrosstabReport;
import org.labkey.study.reports.StudyQueryReport;
import org.labkey.study.reports.StudyRReport;
import org.labkey.study.reports.StudyReportUIProvider;
import org.labkey.study.samples.SampleSearchWebPart;
import org.labkey.study.samples.SamplesWebPart;
import org.labkey.study.samples.SpecimenCommentAuditViewFactory;
import org.labkey.study.security.roles.AssayDesignerRole;
import org.labkey.study.security.roles.SpecimenCoordinatorRole;
import org.labkey.study.security.roles.SpecimenRequesterRole;
import org.labkey.study.view.*;
import org.labkey.study.writer.StudySerializationRegistryImpl;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;


public class StudyModule extends SpringModule implements SearchService.DocumentProvider
{
    public static final String MODULE_NAME = "Study";

    public static final BaseWebPartFactory reportsPartFactory = new ReportsWebPartFactory();
    public static final WebPartFactory reportsWidePartFactory = new ReportsWideWebPartFactory();
    public static final WebPartFactory samplesPartFactory = new SamplesWebPartFactory("right");
    public static final WebPartFactory samplesWidePartFactory = new SamplesWebPartFactory(HttpView.BODY);
    public static final WebPartFactory subjectsWideWebPartFactory = new SubjectsWebPartFactory(HttpView.BODY);
    public static final WebPartFactory subjectsWebPartFactory = new SubjectsWebPartFactory("right");
    public static final WebPartFactory sampleSearchPartFactory = new SampleSearchWebPartFactory(HttpView.BODY);
    public static final WebPartFactory datasetsPartFactory = new DatasetsWebPartFactory();
    public static final WebPartFactory manageStudyPartFactory = new StudySummaryWebPartFactory();
    public static final WebPartFactory enrollmentChartPartFactory = new EnrollmentChartWebPartFactory();
    public static final WebPartFactory studyDesignsWebPartFactory = new StudyDesignsWebPartFactory();
    public static final WebPartFactory studyDesignSummaryWebPartFactory = new StudyDesignSummaryWebPartFactory();
    public static final WebPartFactory assayListWebPartFactory = new AssayListWebPartFactory();
    public static final WebPartFactory assayBatchesWebPartFactory = new AssayBatchesWebPartFactory();
    public static final WebPartFactory assayRunsWebPartFactory = new AssayRunsWebPartFactory();
    public static final WebPartFactory assayResultsWebPartFactory = new AssayResultsWebPartFactory();
    public static final WebPartFactory subjectDetailsWebPartFactory = new SubjectDetailsWebPartFactory();
    public static final WebPartFactory assayList2WebPartFactory = new AssayList2WebPartFactory();
    public static final WebPartFactory studyListWebPartFactory = new StudyListWebPartFactory();

    public String getName()
    {
        return MODULE_NAME;
    }

    public double getVersion()
    {
        return 11.16;
    }

    protected void init()
    {
        addController("study", StudyController.class);
        addController("study-reports", ReportsController.class);
        addController("study-samples", SpecimenController.class);
        addController("study-samples-api", SpecimenApiController.class);
        addController("study-security", SecurityController.class);
        addController("study-designer", DesignerController.class);
        addController("plate", PlateController.class);
        addController("assay", AssayController.class);
        addController("dataset", DatasetController.class);
        addController("study-definition", StudyDefinitionController.class);
        addController("cohort", CohortController.class);
        addController("study-properties", StudyPropertiesController.class);
        addController("study-pages", StudyPagesController.class);
        addController("participant-group", ParticipantGroupController.class);

        PlateService.register(new PlateManager());
        AssayService.setInstance(new AssayManager());
        StudyService.register(StudyServiceImpl.INSTANCE);
        DefaultSchema.registerProvider("study", new StudySchemaProvider());
        DefaultSchema.registerProvider("plate", new PlateSchema.Provider());
        DefaultSchema.registerProvider("assay", new AssaySchemaImpl.Provider());

        PropertyService.get().registerDomainKind(new VisitDatasetDomainKind());
        PropertyService.get().registerDomainKind(new DateDatasetDomainKind());
        PropertyService.get().registerDomainKind(new ContinuousDatasetDomainKind());
        PropertyService.get().registerDomainKind(new TestDatasetDomainKind());
        PropertyService.get().registerDomainKind(new AssayDomainKind());
        PropertyService.get().registerDomainKind(new AssayResultDomainKind());
        PropertyService.get().registerDomainKind(new CohortDomainKind());
        PropertyService.get().registerDomainKind(new StudyDomainKind());

        EnumConverter.registerEnum(SecurityType.class);
        EnumConverter.registerEnum(TimepointType.class);
        QuerySnapshotService.registerProvider(StudyManager.getSchemaName(), DatasetSnapshotProvider.getInstance());

        ServiceRegistry.get().registerService(StudySerializationRegistry.class, StudySerializationRegistryImpl.get());

        ExperimentService.get().registerExperimentDataHandler(new FileBasedModuleDataHandler());
    }

    @Override
    public Set<ModuleResourceLoader> getResourceLoaders()
    {
        Set<ModuleResourceLoader> loaders = new HashSet<ModuleResourceLoader>();
        loaders.add(new ModuleAssayLoader());
        loaders.add(new StudyViewLoader());
        return loaders;
    }

    public boolean hasScripts()
    {
        return true;
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(reportsPartFactory, reportsWidePartFactory, samplesPartFactory,
                samplesWidePartFactory, datasetsPartFactory, manageStudyPartFactory,
                enrollmentChartPartFactory, studyDesignsWebPartFactory, studyDesignSummaryWebPartFactory,
                assayListWebPartFactory, assayBatchesWebPartFactory, assayRunsWebPartFactory, assayResultsWebPartFactory,
                subjectDetailsWebPartFactory, assayList2WebPartFactory, studyListWebPartFactory, sampleSearchPartFactory,
                subjectsWebPartFactory, subjectsWideWebPartFactory));
    }

    public Collection<String> getSummary(Container c)
    {
        Study study = StudyManager.getInstance().getStudy(c);

        if (study != null)
        {
            Collection<String> list = new LinkedList<String>();
            list.add("Study: " + study.getLabel());
            long participants = StudyManager.getInstance().getParticipantCount(study);
            if (0 < participants)
                list.add("" + participants + " " + StudyService.get().getSubjectNounPlural(c));
            int datasets = study.getDataSets().size();
            if (0 < datasets)
                list.add("" + datasets + " datasets");
            return list;
        }
        else
            return Collections.emptyList();
    }


    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        //register roles
        RoleManager.registerRole(new SpecimenCoordinatorRole());
        RoleManager.registerRole(new SpecimenRequesterRole());
        RoleManager.registerRole(new AssayDesignerRole());

        PipelineService.get().registerPipelineProvider(new StudyPipeline(this));
        PipelineService.get().registerPipelineProvider(new StudyImportProvider(this));
        // This is in the First group because when a container is deleted,
        // the Experiment listener needs to be called after the Study listener,
        // because Study needs the metadata held by Experiment to delete properly.
        ContainerManager.addContainerListener(new StudyContainerListener(), ContainerManager.ContainerListener.Order.First);
        AssayPublishService.register(new AssayPublishManager());
        SpecimenService.register(new SpecimenServiceImpl());
        LsidManager.get().registerHandler("Study", StudyManager.getLsidHandler());
        WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
        if(null != wikiService)
            wikiService.registerMacroProvider("study", new StudyMacroProvider());
        PlateManager.get().registerLsidHandlers();
        registerFolderTypes();
        SecurityManager.addViewFactory(new SecurityController.StudySecurityViewFactory());
        AssayService.get().registerAssayProvider(new TsvAssayProvider());
        ExperimentService.get().registerExperimentDataHandler(new TsvDataHandler());
        ExperimentService.get().registerExperimentRunTypeSource(new ExperimentRunTypeSource()
        {
            public Set<ExperimentRunType> getExperimentRunTypes(Container container)
            {
                if (container.getActiveModules().contains(StudyModule.this))
                {
                    Set<ExperimentRunType> result = new HashSet<ExperimentRunType>();
                    for (final ExpProtocol protocol : AssayService.get().getAssayProtocols(container))
                    {
                        result.add(new AssayRunType(protocol, container));
                    }
                    return result;
                }
                return Collections.emptySet();
            }
        });
        AuditLogService.get().addAuditViewFactory(AssayAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(DatasetAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(SpecimenCommentAuditViewFactory.getInstance());

        ReportService.get().registerReport(new StudyController.StudyChartReport());
        ReportService.get().registerReport(new EnrollmentReport());
        ReportService.get().registerReport(new StudyQueryReport());
        ReportService.get().registerReport(new ChartReportView.DatasetChartReport());
        ReportService.get().registerReport(new ExternalReport());
        ReportService.get().registerReport(new AttachmentReport());
        ReportService.get().registerReport(new ExportExcelReport());
        ReportService.get().registerReport(new ChartReportView());
        ReportService.get().registerReport(new StudyChartQueryReport());
        ReportService.get().registerReport(new CrosstabReport());
        ReportService.get().registerReport(new StudyCrosstabReport());
        ReportService.get().registerReport(new StudyRReport());

        ReportService.get().registerDescriptor(new ChartReportView.ChartReportViewDescriptor());
        ReportService.get().registerDescriptor(new CrosstabReportDescriptor());

        ReportService.get().addViewFactory(new ReportsController.StudyRReportViewFactory());
        ReportService.get().addUIProvider(new StudyReportUIProvider());

        StudyReload.initializeAllTimers();

        SearchService ss = ServiceRegistry.get().getService(SearchService.class);

        if (null != ss)
        {
            ss.addSearchCategory(StudyManager.subjectCategory);
            ss.addSearchCategory(StudyManager.datasetCategory);
            ss.addSearchCategory(StudyManager.assayCategory);
            ss.addDocumentProvider(this);
        }
    }


    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(StudyManager.getSchemaName(), "studydataset");
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(StudySchema.getInstance().getSchema());
    }

    private void registerFolderTypes()
    {
        ModuleLoader.getInstance().registerFolderType(this, new StudyFolderType(this));
        ModuleLoader.getInstance().registerFolderType(this, new AssayFolderType(this));
    }

    private static class ReportsWebPartFactory extends BaseWebPartFactory
    {
        public ReportsWebPartFactory()
        {
            super("Views", WebPartFactory.LOCATION_RIGHT);
            addLegacyNames("Reports", "Reports and Views");
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Views", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Views", "This folder does not contain a study");
            return new ReportsController.ReportsWebPart(false);
        }
    }

    private static class ReportsWideWebPartFactory extends BaseWebPartFactory
    {
        public ReportsWideWebPartFactory()
        {
            super("Views");
            addLegacyNames("Reports", "Reports and Views");
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Views", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Views", "This folder does not contain a study");

            return new ReportsController.ReportsWebPart(!"right".equalsIgnoreCase(webPart.getLocation()));
        }
    }

    private static class SamplesWebPartFactory extends DefaultWebPartFactory
    {
        public SamplesWebPartFactory(String position)
        {
            super("Specimens", position, SamplesWebPart.class);
        }

        @Override
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException, InstantiationException
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Specimens", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Specimens", "This folder does not contain a study.");
            return new SamplesWebPart(webPart.getLocation().equals(HttpView.BODY));
        }
    }

    private static class SampleSearchWebPartFactory extends DefaultWebPartFactory
    {
        public SampleSearchWebPartFactory(String position)
        {
            super("Specimen Search", position, SampleSearchWebPart.class);
        }

        @Override
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException, InstantiationException
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Specimens", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Specimens", "This folder does not contain a study.");
            return new SampleSearchWebPart(true);
        }
    }

    private static class SubjectsWebPartFactory extends DefaultWebPartFactory
    {
        public SubjectsWebPartFactory(String position)
        {
            super("Subject List", position, SubjectsWebPart.class);
        }

        @Override
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException, InstantiationException
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Subject List", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Subject List", "This folder does not contain a study.");
            return new SubjectsWebPart(HttpView.BODY.equals(webPart.getLocation()));
        }
    }

    private static class DatasetsWebPartFactory extends DefaultWebPartFactory
    {
        public DatasetsWebPartFactory()
        {
            super("Datasets", DatasetsWebPartView.class);
        }


        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException, InstantiationException
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Datasets", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Datasets", "This folder does not contain a study.");

            return new DatasetsWebPartView();
        }
    }


    private static class EnrollmentChartWebPartFactory extends BaseWebPartFactory
    {
        public EnrollmentChartWebPartFactory()
        {
            super("Enrollment Report");
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            Container c = portalCtx.getContainer();
            Report report = EnrollmentReport.getEnrollmentReport(portalCtx.getUser(), StudyManager.getInstance().getStudy(c), true);
            WebPartView view = new EnrollmentReport.EnrollmentView(report);
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }
    }

    private static class StudyDesignsWebPartFactory extends BaseWebPartFactory
    {
        public StudyDesignsWebPartFactory()
        {
            super("Vaccine Study Protocols");
            addLegacyNames("Study Designs");
        }
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            return new StudyDesignsWebPart(portalCtx, true);
        }
    }

    private static class StudyDesignSummaryWebPartFactory extends BaseWebPartFactory
    {
        public StudyDesignSummaryWebPartFactory()
        {
            super("Study Protocol Summary");
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            JspView view = new JspView("/org/labkey/study/designer/view/studyDesignSummary.jsp");
            view.setTitle("Study Protocol Summary");
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }
    }

    @Override
    public Set<Class> getJUnitTests()
    {
        Set<Class> set = new HashSet<Class>();
        set.add(SpecimenImporter.TestCase.class);
        set.add(StudyManager.DatasetImportTestCase.class);
        return set;
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new StudyUpgradeCode();
    }

    public void enumerateDocuments(@NotNull final SearchService.IndexTask task, final Container c, final Date modifiedSince)
    {
        StudyManager._enumerateDocuments(task, c);
    }
    

    public void indexDeleted() throws SQLException
    {
    }
}
