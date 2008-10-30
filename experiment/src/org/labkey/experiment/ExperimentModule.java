/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
package org.labkey.experiment;

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentRunFilter;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.DefaultExperimentDataHandler;
import org.labkey.api.exp.api.ExpSchema;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SamplesSchema;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.LogDataType;
import org.labkey.experiment.api.SampleSetDomainType;
import org.labkey.experiment.api.property.PropertyServiceImpl;
import org.labkey.experiment.api.property.RangeValidator;
import org.labkey.experiment.api.property.RegExValidator;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.controllers.list.ListController;
import org.labkey.experiment.controllers.list.ListWebPart;
import org.labkey.experiment.controllers.list.SingleListWebPartFactory;
import org.labkey.experiment.controllers.property.PropertyController;
import org.labkey.experiment.list.*;
import org.labkey.experiment.pipeline.ExperimentPipelineProvider;
import org.labkey.experiment.types.TypesController;
import org.labkey.experiment.xar.DefaultRunExpansionHandler;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: phussey (Peter Hussey)
 * Date: Jul 18, 2005
 * Time: 3:25:52 PM
 */
public class ExperimentModule extends SpringModule
{
    private static final String SAMPLE_SET_WEB_PART_NAME = "Sample Sets";
    private static final String PROTOCOL_WEB_PART_NAME = "Protocols";
    public static final String EXPERIMENT_RUN_WEB_PART_NAME = "Experiment Runs";

    private static final Logger _log = Logger.getLogger(ExperimentModule.class);

    public ExperimentModule()
    {
        super(ExperimentService.MODULE_NAME, 8.23, "/org/labkey/experiment", true, createWebPartList());
    }

    protected void init()
    {
        addController("experiment", ExperimentController.class);
        addController("experiment-types", TypesController.class);
        addController("property", PropertyController.class);
        addController("list", ListController.class);
        ExperimentService.setInstance(new ExperimentServiceImpl());
        PropertyService.setInstance(new PropertyServiceImpl());
        ListService.setInstance(new ListServiceImpl());

        ExperimentProperty.register();
        SamplesSchema.register();
        ExpSchema.register();
        ListSchema.register();
        PropertyService.get().registerDomainKind(new SampleSetDomainType());
        PropertyService.get().registerDomainKind(new ListDomainType());
    }

    @Override
    protected ContextType getContextType()
    {
        return ContextType.config;
    }

    private static BaseWebPartFactory[] createWebPartList()
    {
        List<BaseWebPartFactory> result = new ArrayList<BaseWebPartFactory>();
        
        BaseWebPartFactory runGroupsFactory = new BaseWebPartFactory(RunGroupWebPart.WEB_PART_NAME)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new RunGroupWebPart(portalCtx, WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), webPart);
            }
        };
        runGroupsFactory.addLegacyNames("Experiments", "Experiment", "Experiment Navigator");
        result.add(runGroupsFactory);
        BaseWebPartFactory narrowRunGroupsFactory = new BaseWebPartFactory(RunGroupWebPart.WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new RunGroupWebPart(portalCtx, WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), webPart);
            }
        };
        narrowRunGroupsFactory.addLegacyNames("Experiments", "Narrow Experiments");
        result.add(narrowRunGroupsFactory);

        BaseWebPartFactory runTypesFactory = new BaseWebPartFactory(RunTypeWebPart.WEB_PART_NAME)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new RunTypeWebPart();
            }
        };
        result.add(runTypesFactory);

        BaseWebPartFactory runTypesNarrowFactory = new BaseWebPartFactory(RunTypeWebPart.WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new RunTypeWebPart();
            }
        };
        result.add(runTypesNarrowFactory);

        result.add(new BaseWebPartFactory(ExperimentModule.EXPERIMENT_RUN_WEB_PART_NAME){
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart)
            {
                return ExperimentService.get().createExperimentRunWebPart(new ViewContext(portalCtx), ExperimentRunFilter.ALL_RUNS_FILTER, true, true);
            }
        });
        result.add(new BaseWebPartFactory(SAMPLE_SET_WEB_PART_NAME){
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new SampleSetWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx);
            }
        });
        BaseWebPartFactory narrowSampleSetFactory = new BaseWebPartFactory(SAMPLE_SET_WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new SampleSetWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx);
            }
        };
        narrowSampleSetFactory.addLegacyNames("Narrow Sample Sets");
        result.add(narrowSampleSetFactory);
        BaseWebPartFactory narrowProtocolFactory = new BaseWebPartFactory(PROTOCOL_WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new ProtocolWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx);
            }
        };
        narrowProtocolFactory.addLegacyNames("Narrow Protocols");
        result.add(narrowProtocolFactory);
        result.add(ListWebPart.FACTORY);
        result.add(new SingleListWebPartFactory());

        return result.toArray(new BaseWebPartFactory[result.size()]);
    }


    @Override
    public void startup(ModuleContext context)
    {
        PipelineService.get().registerPipelineProvider(new ExperimentPipelineProvider());
        ExperimentService.get().registerExperimentRunFilter(ExperimentRunFilter.ALL_RUNS_FILTER);
        ExperimentService.get().registerRunExpansionHandler(new DefaultRunExpansionHandler());
        ExperimentService.get().registerExperimentDataHandler(new DefaultExperimentDataHandler());
        ExperimentService.get().registerDataType(new LogDataType());
        AuditLogService.get().addAuditViewFactory(ListAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(DomainAuditViewFactory.getInstance());

        ContainerManager.addContainerListener(new ContainerManager.ContainerListener()
        {
            public void containerCreated(Container c)
            {
            }

            public void containerDeleted(Container c, User user)
            {
                try
                {
                    ExperimentService.get().deleteAllExpObjInContainer(c, user);
                }
                catch (SQLException e)
                {
                    throw new RuntimeException("Delete failed", e);
                }
                catch (Exception ee)
                {
                    throw new RuntimeException(ee);
                }
            }

            public void propertyChange(PropertyChangeEvent evt)
            {
                if (evt.getPropertyName().equals("Parent"))
                {
                    Container c = (Container) evt.getSource();
                    Container cOldParent = (Container) evt.getOldValue();
                    Container cNewParent = (Container) evt.getNewValue();
                    try
                    {
                        ExperimentService.get().moveContainer(c, cOldParent, cNewParent);
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeException(e);
                    }
                    catch (ExperimentException e)
                    {
                        throw new RuntimeException(e);
                    }

                }
            }
        });
        SystemProperty.registerProperties();
        OntologyManager.initCaches();

        PropertyService.get().registerValidatorKind(new RegExValidator());
        PropertyService.get().registerValidatorKind(new RangeValidator());

        super.startup(context);
    }

    public Collection<String> getSummary(Container c)
    {
        Collection<String> list = new LinkedList<String>();
        int count = ExperimentService.get().getExperiments(c).length;
        if (count > 0)
            list.add("" + count + " Run Group" + (count > 1 ? "s" : ""));
        return list;
    }


    @Override
    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return new HashSet<Class<? extends TestCase>>(Arrays.asList(
            LSIDRelativizer.TestCase.class,
            OntologyManager.TestCase.class,
            LsidUtils.TestCase.class));
    }


    @Override
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(ExpSchema.SCHEMA_NAME);
    }


    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(ExperimentService.get().getSchema());
    }


    @SuppressWarnings("ThrowableInstanceNeverThrown")
    @Override
    public void afterSchemaUpdate(ModuleContext moduleContext, ViewContext viewContext)
    {
        double version = moduleContext.getInstalledVersion();
        if (version > 0 && version < 8.23)
        {
            try
            {
                doVersion_132Update();
            } 
            catch (Exception e)
            {
                String msg = "Error running afterSchemaUpdate doVersion_132Update on ExperimentModule, upgrade from version " + String.valueOf(version);
                _log.error(msg, e);
                ExceptionUtil.getErrorRenderer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e, viewContext.getRequest(), false, false);
            }
        }

        if (version > 0 && version < 2.23)
        {
            try
            {
                doPopulateListEntityIds();
            }
            catch (Exception e)
            {
                String msg = "Error running afterSchemaUpdate doPopulateListEntityIds on ExperimentModule, upgrade from version " + String.valueOf(version);
                _log.error(msg, e);
                ExceptionUtil.getErrorRenderer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e, viewContext.getRequest(), false, false);
            }
        }

        super.afterSchemaUpdate(moduleContext, viewContext);
    }

    private void doVersion_132Update() throws SQLException, NamingException, ServletException
    {
        DbSchema tmpSchema = DbSchema.createFromMetaData("exp");
        doProjectColumnUpdate(tmpSchema, "exp.PropertyDescriptor");
        doProjectColumnUpdate(tmpSchema, "exp.DomainDescriptor");
        alterDescriptorTables(tmpSchema);
    }

    private void doProjectColumnUpdate(DbSchema tmpSchema, String descriptorTable) throws SQLException
    {
        String sql = "SELECT DISTINCT(Container) FROM " + descriptorTable + " WHERE Project IS NULL ";
        String[] cids = Table.executeArray(tmpSchema, sql, new Object[]{}, String.class);
        String projectId;
        String newContainerId;
        String rootId = ContainerManager.getRoot().getId();
        for (String cid : cids)
        {
            newContainerId = cid;
            if (cid.equals(rootId) || cid.equals("00000000-0000-0000-0000-000000000000"))
                newContainerId = ContainerManager.getSharedContainer().getId();
            projectId = ContainerManager.getForId(newContainerId).getProject().getId();
            setDescriptorProject(tmpSchema, cid, projectId, newContainerId, descriptorTable);
        }
    }

    private void doPopulateListEntityIds() throws SQLException
    {
        ListDef[] listDefs = ListManager.get().getAllLists();

        for (ListDef listDef : listDefs)
        {
            int listId = listDef.getRowId();
            ListDefinitionImpl impl = new ListDefinitionImpl(listDef);
            TableInfo tinfo = impl.getIndexTable();

            SimpleFilter lstItemFilter = new SimpleFilter("ListId", listId);
            ListItm[] itms = Table.select(tinfo, Table.ALL_COLUMNS, lstItemFilter, null, ListItm.class);

            String sql = "UPDATE " + tinfo + " SET EntityId = ? WHERE ListId = ? AND " + tinfo.getSqlDialect().getColumnSelectName("Key") + " = ?";

            for (ListItm itm : itms)
            {
                Table.execute(tinfo.getSchema(), sql, new Object[]{GUID.makeGUID(), listId, itm.getKey()});
            }
        }
    }

    private void setDescriptorProject(DbSchema tmpSchema, String containerId, String projectId, String newContainerId, String descriptorTable) throws SQLException
    {
        String sql = " UPDATE " + descriptorTable + " SET Project = ?, Container = ? WHERE Container = ? ";
        Table.execute(tmpSchema, sql, new Object[]{projectId, newContainerId, containerId});
    }

    private void alterDescriptorTables(DbSchema tmpSchema) throws SQLException
    {
        String indexOption = " ";
        String keywordNotNull = " ENTITYID ";

        if (tmpSchema.getSqlDialect().isSqlServer())
            indexOption = " CLUSTERED ";

        if (tmpSchema.getSqlDialect().isPostgreSQL())
            keywordNotNull = " SET ";

        String sql = " ALTER TABLE exp.PropertyDescriptor ALTER COLUMN Project " + keywordNotNull + " NOT NULL ;";
        Table.execute(tmpSchema, sql, new Object[]{});

        try
        {
            sql = " ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT UQ_PropertyDescriptor UNIQUE " + indexOption + " (Project, PropertyURI);" ;
            Table.execute(tmpSchema, sql, new Object[]{});
        }
        catch (SQLException ex)
        {
            // 42P07 DUPLICATE TABLE (pgsql)
            // 42S11 Index already exists (mssql)
            // S0001 sql server bug?
            if (!"42P07".equals(ex.getSQLState()) && !"42S11".equals(ex.getSQLState()) && !"S0001".equals(ex.getSQLState()))
                throw ex;
        }
        sql = " ALTER TABLE exp.DomainDescriptor ALTER COLUMN Project " + keywordNotNull + " NOT NULL ;";
        Table.execute(tmpSchema, sql, new Object[]{});

        try
        {
            sql = " ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT UQ_DomainDescriptor UNIQUE " + indexOption + " (Project, DomainURI);"  ;
            Table.execute(tmpSchema, sql, new Object[]{});
        }
        catch (SQLException ex)
        {
            if (!"42P07".equals(ex.getSQLState()) && !"42S11".equals(ex.getSQLState()) && !"S0001".equals(ex.getSQLState()))
                throw ex;
        }
    }
}
