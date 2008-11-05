/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.core;

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.module.*;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.security.*;
import org.labkey.api.security.AuthenticationManager.Priority;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavService;
import org.labkey.core.admin.AdminController;
import org.labkey.core.admin.sql.SqlScriptController;
import org.labkey.core.analytics.AnalyticsController;
import org.labkey.core.analytics.AnalyticsServiceImpl;
import org.labkey.core.attachment.AttachmentServiceImpl;
import org.labkey.core.ftp.FtpController;
import org.labkey.core.junit.JunitController;
import org.labkey.core.login.LoginController;
import org.labkey.core.query.*;
import org.labkey.core.security.SecurityController;
import org.labkey.core.test.TestController;
import org.labkey.core.user.UserController;
import org.labkey.core.webdav.FileSystemAuditViewFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: migra
 * Date: Jul 25, 2005
 * Time: 2:54:30 PM
 */
public class CoreModule extends SpringModule implements ContainerManager.ContainerListener, FirstRequestHandler.FirstRequestListener
{
    private static Logger _log = Logger.getLogger(CoreModule.class);

    public String getName()
    {
        return CORE_MODULE_NAME;
    }

    public double getVersion()
    {
        return 8.30;
    }

    protected void init()
    {
        SqlDialect.register(new SqlDialectPostgreSQL());

        addController("admin", AdminController.class);
        addController("admin-sql", SqlScriptController.class);
        addController("security", SecurityController.class);
        addController("user", UserController.class);
        addController("login", LoginController.class);
        addController("junit", JunitController.class);
        addController("core", CoreController.class);
        addController("test", TestController.class);
        addController("ftp", FtpController.class);
        addController("analytics", AnalyticsController.class);

        AuthenticationManager.registerProvider(new DbLoginAuthenticationProvider(), Priority.Low);
        AttachmentService.register(new AttachmentServiceImpl());
        AnalyticsServiceImpl.register();
        FirstRequestHandler.addFirstRequestListener(this);

        DefaultSchema.registerProvider("core", new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new CoreQuerySchema(schema.getUser(), schema.getContainer());
            }
        });

        if (null != getSourcePath())
        {
            File projectRoot = new File(getSourcePath(), "../../..");
            if (projectRoot.exists())
            {
                try
                {
                    AppProps.getInstance().setProjectRoot(projectRoot.getCanonicalPath());

                    String root = AppProps.getInstance().getProjectRoot();
                    ResourceFinder api = new ResourceFinder("API", root + "/server/api", root + "/build/modules/api");
                    ModuleLoader.getInstance().registerResourcePrefix("/org/labkey/api", api);
                    ResourceFinder internal = new ResourceFinder("Internal", root + "/server/internal", root + "/build/modules/internal");
                    ModuleLoader.getInstance().registerResourcePrefix("/org/labkey/api", internal);
                }
                catch(IOException e)
                {
                    // Do nothing -- leave project root null
                }
            }
        }
    }

    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Collections.singletonList(new BaseWebPartFactory("Contacts")
            {
                public WebPartView getWebPartView(ViewContext ctx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
                {
                    return new ContactWebPart();
                }
            });
    }

    @Override
    public void bootstrap()
    {
        CoreSchema core = CoreSchema.getInstance();

        try
        {
            core.getSqlDialect().prepareNewDatabase(core.getSchema());
        }
        catch(ServletException e)
        {
            throw new RuntimeException(e);
        }

        super.bootstrap();
    }


    // Note: Core module is special -- versionUpdate gets called during Tomcat startup so we don't hit the Logins, ACLs,
    // Members, UsersData, etc. tables before they're created (bootstrap time) or modified (upgrade time).  This code
    // is not thread-safe -- it is called once at startup.
    @Override
    public ActionURL versionUpdate(ModuleContext moduleContext, ViewContext viewContext)
    {
        // TODO: PageFlowUtil.getContentsAsString()
        // TODO: Break up PG scripts into multiple executes

        beforeSchemaUpdate(moduleContext, viewContext);

        try
        {
            // This provider retrieves scripts in the core schema
            SqlScriptProvider coreProvider = new FileSqlScriptProvider(this) {
                @Override
                protected boolean shouldInclude(SqlScript script)
                {
                    return script.getSchemaName().equals("core");
                }
            };

            // This provider retrieves scripts in the other schemas (portal, prop, test)
            SqlScriptProvider nonCoreProvider = new FileSqlScriptProvider(this) {
                @Override
                protected boolean shouldInclude(SqlScript script)
                {
                    return !script.getSchemaName().equals("core");
                }
            };

            List<SqlScript> scripts = new ArrayList<SqlScript>();

            if (0.0 != moduleContext.getInstalledVersion())
            {
                scripts.addAll(coreProvider.getDropScripts());
                scripts.addAll(nonCoreProvider.getDropScripts());
            }

            // Must run all the core schema scripts first followed by the other schemas
            scripts.addAll(SqlScriptRunner.getRecommendedScripts(coreProvider, null, moduleContext.getInstalledVersion(), getVersion()));
            scripts.addAll(SqlScriptRunner.getRecommendedScripts(nonCoreProvider, null, moduleContext.getInstalledVersion(), getVersion()));
            scripts.addAll(coreProvider.getCreateScripts());
            scripts.addAll(nonCoreProvider.getCreateScripts());

            SqlScriptRunner.runScripts(null, scripts, coreProvider);
            SqlScriptRunner.waitForScriptsToFinish();

            DbSchema.invalidateSchemas();
        }
        catch(SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (InterruptedException e)
        {
            throw(new RuntimeException(e));
        }
        catch (SqlScriptRunner.SqlScriptException e)
        {
            throw new RuntimeException(e);
        }

        Exception se = SqlScriptRunner.getException();
        if (null != se)
            throw new RuntimeException(se);

        afterSchemaUpdate(moduleContext, viewContext);

        try
        {
            // Increment on every core module upgrade to defeat browser caching of static resources. 
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return null;
    }


    @Override
    public void beforeUpdate()
    {
        // Do nothing
    }

    @Override
    public void afterSchemaUpdate(ModuleContext moduleContext, ViewContext viewContext)
    {
        double installedVersion = moduleContext.getInstalledVersion();

        if (installedVersion == 0.0)
        {
            // Other containers inherit permissions from root; admins get all permisssions, users & guests none
            ContainerManager.bootstrapContainer("/", ACL.PERM_ALLOWALL, 0, 0);

            // Users & guests can read from /home
            ContainerManager.bootstrapContainer(ContainerManager.HOME_PROJECT_PATH, ACL.PERM_ALLOWALL, ACL.PERM_READ, ACL.PERM_READ);

            // Create the initial groups
            GroupManager.bootstrapGroup(Group.groupAdministrators, "Administrators");
            GroupManager.bootstrapGroup(Group.groupUsers, "Users");
            GroupManager.bootstrapGroup(Group.groupGuests, "Guests");
        }

        if (installedVersion < 8.11)
            GroupManager.bootstrapGroup(Group.groupDevelopers, "Developers", GroupManager.PrincipalType.ROLE);
        if (installedVersion > 0 && installedVersion < 8.12)
            migrateLdapSettings();
        if (installedVersion > 0 && installedVersion < 8.22)
            migrateLookAndFeelSettings();
    }

    @Override
    public void afterUpdate()
    {
    }

    private void migrateLdapSettings()
    {
        try
        {
            Map<String, String> props = AppProps.getInstance().getProperties(ContainerManager.getRoot());
            String domain = props.get("LDAPDomain");

            if (null != domain && domain.trim().length() > 0)
            {
                PropertyMap map = PropertyManager.getWritableProperties("LDAPAuthentication", true);
                map.put("Servers", props.get("LDAPServers"));
                map.put("Domain", props.get("LDAPDomain"));
                map.put("PrincipalTemplate", props.get("LDAPPrincipalTemplate"));
                map.put("SASL", props.get("LDAPAuthentication"));
                PropertyManager.saveProperties(map);
                saveAuthenticationProviders(true);
            }
            else
            {
                saveAuthenticationProviders(false);
            }
        }
        catch (SQLException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }

    private void migrateLookAndFeelSettings()
    {
        PropertyMap configProps = PropertyManager.getWritableProperties(-1, ContainerManager.getRoot().getId(), "SiteConfig", true);
        PropertyMap lafProps = PropertyManager.getWritableProperties(-1, ContainerManager.getRoot().getId(), "LookAndFeel", true);

        for (String settingName : new String[] {"systemDescription", "systemShortName", "themeName", "folderDisplayMode",
                "navigationBarWidth", "logoHref", "themeFont", "companyName", "systemEmailAddress", "reportAProblemPath"})
        {
            migrateSetting(configProps, lafProps, settingName);
        }

        PropertyManager.saveProperties(configProps);
        PropertyManager.saveProperties(lafProps);
    }

    private void migrateSetting(PropertyMap configProps, PropertyMap lafProps, String propertyName)
    {
        lafProps.put(propertyName, configProps.get(propertyName));
        configProps.remove(propertyName);
    }

    private static void saveAuthenticationProviders(boolean enableLdap)
    {
        PropertyMap map = PropertyManager.getWritableProperties("Authentication", true);
        String activeAuthProviders = map.get("Authentication");

        if (null == activeAuthProviders)
            activeAuthProviders = "Database";

        if (enableLdap)
        {
            if (!activeAuthProviders.contains("LDAP"))
                activeAuthProviders = activeAuthProviders + ":LDAP";
        }
        else
        {
            activeAuthProviders = activeAuthProviders.replaceFirst("LDAP:", "").replaceFirst(":LDAP", "").replaceFirst("LDAP", "");
        }

        map.put("Authentication", activeAuthProviders);
        PropertyManager.saveProperties(map);
    }

    @Override
    public void destroy()
    {
        super.destroy();
        UsageReportingLevel.cancelUpgradeCheck();
    }


    @Override
    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);

        ContainerManager.addContainerListener(this);
        org.labkey.api.security.SecurityManager.init();
        ModuleLoader.getInstance().registerFolderType(FolderType.NONE);
        AppProps.getInstance().getUsageReportingLevel().scheduleUpgradeCheck();
        SystemMaintenance.setTimer();

        AuditLogService.get().addAuditViewFactory(UserAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(GroupAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(AttachmentAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(ContainerAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(FileSystemAuditViewFactory.getInstance());

        ContextListener.addStartupListener(TempTableTracker.getStartupListener());
        ContextListener.addShutdownListener(TempTableTracker.getShutdownListener());
        ContextListener.addShutdownListener(org.labkey.core.webdav.DavController.getShutdownListener());

        AdminController.registerAdminConsoleLinks();
        AnalyticsController.registerAdminConsoleLinks();

        WebdavService.setResolver(WebdavResolverImpl.get());
    }

    @Override
    public String getTabName(ViewContext context)
    {
        return "Admin";
    }


    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        if (user == null)
        {
            return AppProps.getInstance().getHomePageActionURL();
        }
        else if (c != null && "/".equals(c.getPath()) && user.isAdministrator())
        {
            return PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL();
        }
        else if (c != null && c.hasPermission(user, ACL.PERM_ADMIN))
        {
            return PageFlowUtil.urlProvider(SecurityUrls.class).getProjectURL(c);
        }
        else
        {
            return PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(c, user.getUserId());
        }
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return Module.TabDisplayMode.DISPLAY_NEVER;
    }


    public void containerCreated(Container c)
    {
        User user = UserManager.getGuestUser();
        try {
            ViewContext context = HttpView.currentContext();
            if (context != null)
            {
                user = context.getUser();
            }
        }
        catch (RuntimeException e){}
        String message = c.isProject() ? "Project " + c.getName() + " was created" :
                "Folder " + c.getName() + " was created";
        addAuditEvent(user, c, message);
    }

    private void addAuditEvent(User user, Container c, String comment)
    {
        if (user != null)
        {
            AuditLogEvent event = new AuditLogEvent();

            event.setCreatedBy(user);
            event.setEventType(ContainerManager.CONTAINER_AUDIT_EVENT);
            event.setContainerId(c.getId());
            event.setComment(comment);

            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());

            AuditLogService.get().addEvent(event);
        }
    }

    public void containerDeleted(Container c, User user)
    {
        try
        {
            PropertyManager.purgeObjectProperties(c.getId());
            // Let containerManager delete ACLs, we want that to happen last

            String message = c.isProject() ? "Project " + c.getName() + " was deleted" :
                    "Folder " + c.getName() + " was deleted";
            addAuditEvent(user, c, message);
        }
        catch (SQLException e)
        {
            _log.error("Failed to delete Properties for container '" + c.getPath() + "'.", e);
        }
    }


    public void propertyChange(PropertyChangeEvent evt)
    {
    }


    @Override
    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return new HashSet<Class<? extends TestCase>>(Arrays.asList(
            org.labkey.api.data.Table.TestCase.class,
            org.labkey.api.data.DbSchema.TestCase.class,
            org.labkey.api.data.TableViewFormTestCase.class,
            ActionURL.TestCase.class,
            org.labkey.api.security.SecurityManager.TestCase.class,
            org.labkey.api.data.PropertyManager.TestCase.class,
            org.labkey.api.util.DateUtil.TestCase.class,
            org.labkey.common.tools.PeptideTestCase.class,
            org.labkey.api.data.ContainerManager.TestCase.class,
            org.labkey.common.tools.TabLoader.TabLoaderTestCase.class,
            org.labkey.common.tools.ExcelLoader.ExcelLoaderTestCase.class,
            ModuleDependencySorter.TestCase.class,
            org.labkey.api.security.GroupManager.TestCase.class,
            DateUtil.TestCase.class,
            DatabaseCache.TestCase.class,
            SecurityController.TestCase.class,
            AttachmentServiceImpl.TestCase.class,
            BooleanFormat.TestCase.class,
            XMLWriterTest.TestCase.class,
            WebdavResolverImpl.TestCase.class,
            org.labkey.api.exp.Lsid.TestCase.class,
            MimeMap.TestCase.class
        )
        );
    }


    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set
                (
                    CoreSchema.getInstance().getSchema(),       // core
                    Portal.getSchema(),                         // portal
                    PropertyManager.getSchema(),                // prop
                    TestSchema.getInstance().getSchema()        // test
                );
    }

    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set
                (
                    CoreSchema.getInstance().getSchemaName(),       // core
                    Portal.getSchemaName(),                         // portal
                    PropertyManager.getSchemaName(),                // prop
                    TestSchema.getInstance().getSchemaName()        // test
                );
    }

    public void handleFirstRequest(HttpServletRequest request)
    {
        ViewServlet.initialize();
        ModuleLoader.getInstance().initPageFlowToModule();        
        AuthenticationManager.initialize();
    }


    @Override
    public boolean hasScripts()
    {
        return true;
    }

    public List<String> getAttributions()
    {
        return Arrays.asList(
            "<a href=\"http://www.apache.org\" target=\"top\"><img src=\"http://www.apache.org/images/asf_logo.gif\" alt=\"Apache\" width=\"185\" height=\"50\"></a>",
            "<a href=\"http://www.springframework.org\" target=\"top\"><img src=\"http://static.springframework.org/images/spring21.png\" alt=\"Spring\" width=\"100\" height=\"48\"></a>"
        );
    }
}
