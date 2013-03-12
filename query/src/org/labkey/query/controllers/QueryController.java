/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.query.controllers;

import org.antlr.runtime.tree.Tree;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.tika.io.IOUtils;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.view.AuditChangesView;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.query.*;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotForm;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.thumbnail.BaseThumbnailAction;
import org.labkey.api.thumbnail.StaticThumbnailProvider;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.UpdateView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.ZipFile;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.data.xml.externalSchema.TemplateSchemaType;
import org.labkey.query.CustomViewImpl;
import org.labkey.query.CustomViewUtil;
import org.labkey.query.ExternalSchemaDocumentProvider;
import org.labkey.query.QueryServiceImpl;
import org.labkey.query.persist.LinkedSchemaDef;
import org.labkey.query.TableWriter;
import org.labkey.query.TableXML;
import org.labkey.query.audit.QueryAuditViewFactory;
import org.labkey.query.audit.QueryUpdateAuditViewFactory;
import org.labkey.query.design.DgMessage;
import org.labkey.query.design.ErrorsDocument;
import org.labkey.query.metadata.MetadataServiceImpl;
import org.labkey.query.metadata.client.MetadataEditor;
import org.labkey.query.persist.AbstractExternalSchemaDef;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.ExternalSchemaDef;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.sql.QNode;
import org.labkey.query.sql.Query;
import org.labkey.query.sql.SqlParser;
import org.labkey.query.xml.ApiTestsDocument;
import org.labkey.query.xml.TestCaseType;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class QueryController extends SpringActionController
{
    private static final Logger LOG = Logger.getLogger(QueryController.class);

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(QueryController.class,
            ValidateQueryAction.class,
            ValidateQueriesAction.class,
            GetSchemaQueryTreeAction.class,
            GetQueryDetailsAction.class,
            ViewQuerySourceAction.class);

    public QueryController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    public static void registerAdminConsoleLinks()
    {
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Diagnostics, "data sources", new ActionURL(DataSourceAdminAction.class, ContainerManager.getRoot()));
    }

    public static class QueryUrlsImpl implements QueryUrls
    {
        public ActionURL urlCreateSnapshot(Container c)
        {
            return new ActionURL(CreateSnapshotAction.class, c);
        }

        public ActionURL urlCustomizeSnapshot(Container c)
        {
            return new ActionURL(EditSnapshotAction.class, c);
        }

        public ActionURL urlUpdateSnapshot(Container c)
        {
            return new ActionURL(UpdateSnapshotAction.class, c);
        }

        public ActionURL urlSchemaBrowser(Container c)
        {
            return new ActionURL(BeginAction.class, c);
        }

        @Override
        public ActionURL urlSchemaBrowser(Container c, String schemaName)
        {
            ActionURL ret = urlSchemaBrowser(c);
            ret.addParameter(QueryParam.schemaName.toString(), schemaName);
            return ret;
        }

        @Override
        public ActionURL urlSchemaBrowser(Container c, String schemaName, String queryName)
        {
            if (StringUtils.isEmpty(queryName))
                return urlSchemaBrowser(c, schemaName);
            ActionURL ret = urlSchemaBrowser(c);
            ret.addParameter(QueryParam.schemaName.toString(), schemaName);
            ret.addParameter(QueryParam.queryName.toString(), queryName);
            return ret;
        }

        public ActionURL urlExternalSchemaAdmin(Container c)
        {
            return urlExternalSchemaAdmin(c, null);
        }

        public ActionURL urlExternalSchemaAdmin(Container c, @Nullable String reloadedSchema)
        {
            ActionURL url = new ActionURL(AdminAction.class, c);

            if (null != reloadedSchema)
                url.addParameter("reloadedSchema", reloadedSchema);

            return url;
        }

        public ActionURL urlInsertExternalSchema(Container c)
        {
            return new ActionURL(InsertExternalSchemaAction.class, c);
        }

        public ActionURL urlNewQuery(Container c)
        {
            return new ActionURL(NewQueryAction.class, c);
        }

        public ActionURL urlUpdateExternalSchema(Container c, AbstractExternalSchemaDef def)
        {
            ActionURL url = new ActionURL(QueryController.EditExternalSchemaAction.class, c);
            url.addParameter("externalSchemaId", Integer.toString(def.getExternalSchemaId()));
            return url;
        }

        public ActionURL urlDeleteExternalSchema(Container c, AbstractExternalSchemaDef def)
        {
            ActionURL url = new ActionURL(QueryController.DeleteExternalSchemaAction.class, c);
            url.addParameter("externalSchemaId", Integer.toString(def.getExternalSchemaId()));
            return url;
        }

        public ActionURL urlCreateExcelTemplate(Container c, String schemaName, String queryName)
        {
            return new ActionURL(ExportExcelTemplateAction.class, c)
                    .addParameter(QueryParam.schemaName, schemaName)
                    .addParameter("query.queryName", queryName);
        }

        @Override
        public ActionURL urlThumbnail(Container c)
        {
            return new ActionURL(ThumbnailAction.class, c);
        }
    }

    protected boolean queryExists(QueryForm form)
    {
        try
        {
            return form.getSchema() != null && form.getSchema().getTable(form.getQueryName()) != null;
        }
        catch (QueryParseException x)
        {
            // exists with errors
            return true;
        }
        catch (QueryException x)
        {
            // exists with errors
            return true;
        }
    }

    @Override
    public PageConfig defaultPageConfig()
    {
        // set default help topic for query controler
        PageConfig config = super.defaultPageConfig();
        config.setHelpTopic(new HelpTopic("querySchemaBrowser"));
        return config;
    }

    /**
     * ensureQueryExists throws NotFound if the query/table does not exist.
     * Does not guarantee that the query is syntactically correct, or can execute.
     */
    protected void ensureQueryExists(QueryForm form)
    {
        if (form.getSchema() == null)
        {
            throw new NotFoundException("Could not find schema: " + form.getSchemaName());
        }

        if (StringUtils.isEmpty(form.getQueryName()))
        {
            throw new NotFoundException("Query not specified");
        }

        if (!queryExists(form))
        {
            throw new NotFoundException("Query '" + form.getQueryName() + "' in schema '" + form.getSchemaName() + "' doesn't exist.");
        }
    }


    @AdminConsoleAction
    public class DataSourceAdminAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ExternalSchemaDef[] allDefs = QueryManager.get().getExternalSchemaDefs(null);

            MultiMap<String, ExternalSchemaDef> byDataSourceName = new MultiHashMap<String, ExternalSchemaDef>();

            for (ExternalSchemaDef def : allDefs)
                byDataSourceName.put(def.getDataSource(), def);

            StringBuilder sb = new StringBuilder();

            sb.append("\n<table>\n");
            sb.append("  <tr><td colspan=5>This page lists all the data sources defined in your labkey.xml file that were available at server startup and the external schemas defined in each.</td></tr>\n");
            sb.append("  <tr><td colspan=5>&nbsp;</td></tr>\n");
            sb.append("  <tr><th>Data Source</th><th>Current Status</th><th>URL</th><th>Product Name</th><th>Product Version</th></tr>\n");

            for (DbScope scope : DbScope.getDbScopes())
            {
                sb.append("  <tr><td>");
                sb.append(scope.getDisplayName());
                sb.append("</td><td>");

                String status;
                Connection conn = null;

                try
                {
                    conn = scope.getConnection();
                    status = "connected";
                }
                catch (SQLException e)
                {
                    status = "<font class=\"labkey-error\">disconnected</font>";
                }
                finally
                {
                    if (null != conn)
                        scope.releaseConnection(conn);
                }

                sb.append(status);
                sb.append("</td><td>");
                sb.append(scope.getURL());
                sb.append("</td><td>");
                sb.append(scope.getDatabaseProductName());
                sb.append("</td><td>");
                sb.append(scope.getDatabaseProductVersion());
                sb.append("</td></tr>\n");

                Collection<ExternalSchemaDef> dsDefs = byDataSourceName.get(scope.getDataSourceName());

                sb.append("  <tr><td colspan=5>\n");
                sb.append("    <table>\n");

                if (null != dsDefs)
                {
                    MultiMap<String, ExternalSchemaDef> byContainerPath = new MultiHashMap<String, ExternalSchemaDef>();

                    for (ExternalSchemaDef def : dsDefs)
                        byContainerPath.put(def.getContainerPath(), def);

                    TreeSet<String> paths = new TreeSet<String>(byContainerPath.keySet());
                    QueryUrlsImpl urls = new QueryUrlsImpl();

                    for (String path : paths)
                    {
                        sb.append("      <tr><td colspan=4>&nbsp;</td><td>\n");
                        Container c = ContainerManager.getForPath(path);

                        if (null != c)
                        {
                            Collection<ExternalSchemaDef> cDefs = byContainerPath.get(path);

                            sb.append("        <table>\n        <tr><td colspan=3>");

                            ActionURL schemaAdminURL = urls.urlExternalSchemaAdmin(c);

                            sb.append("<a href=\"").append(PageFlowUtil.filter(schemaAdminURL)).append("\">").append(PageFlowUtil.filter(path)).append("</a>");
                            sb.append("</td></tr>\n");
                            sb.append("          <tr><td>&nbsp;</td><td>\n");
                            sb.append("            <table>\n");

                            for (ExternalSchemaDef def : cDefs)
                            {
                                ActionURL url = urls.urlUpdateExternalSchema(c, def);

                                sb.append("              <tr><td>");
                                sb.append("<a href=\"").append(PageFlowUtil.filter(url)).append("\">").append(PageFlowUtil.filter(def.getUserSchemaName()));

                                if (!def.getSourceSchemaName().equals(def.getUserSchemaName()))
                                {
                                    sb.append(" (");
                                    sb.append(PageFlowUtil.filter(def.getSourceSchemaName()));
                                    sb.append(")");
                                }

                                sb.append("</a></td></tr>\n");
                            }

                            sb.append("            </table>\n");
                            sb.append("          </td></tr>\n");
                            sb.append("        </table>\n");
                        }

                        sb.append("      </td></tr>\n");
                    }
                }
                else
                {
                    sb.append("      <tr><td>&nbsp;</td></tr>\n");
                }

                sb.append("    </table>\n");
                sb.append("  </td></tr>\n");
            }

            sb.append("</table>\n");

            return new HtmlView(sb.toString());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            PageFlowUtil.urlProvider(AdminUrls.class).appendAdminNavTrail(root, "Data Source Administration ", null);
            return root;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class BrowseAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<Object>(QueryController.class, "browse.jsp", null);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Schema Browser");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends QueryViewAction
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            return new JspView<QueryForm>(QueryController.class, "browse.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Query Schema Browser", new QueryUrlsImpl().urlSchemaBrowser(getContainer()));
            return root;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SchemaAction extends QueryViewAction
    {
        public SchemaAction() {}

        SchemaAction(QueryForm form)
        {
            _form = form;
        }

        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            _form = form;
            return new JspView<QueryForm>(QueryController.class, "browse.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (getContainer().hasPermission(getUser(), AdminPermission.class) || getUser().isDeveloper())
            {
                // Don't show the full query nav trail to non-admin/non-developer users as they almost certainly don't
                // want it
                String schemaName = _form.getSchema().getSchemaPath().toDisplayString();
                ActionURL url = new ActionURL(BeginAction.class, _form.getViewContext().getContainer());
                url.addParameter("schemaName", _form.getSchemaName());
                url.addParameter("queryName", _form.getQueryName());
                (new BeginAction()).appendNavTrail(root)
                    .addChild(schemaName + " Schema", url);
            }
            return root;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class NewQueryAction extends FormViewAction<NewQueryForm>
    {
        NewQueryForm _form;
        ActionURL _successUrl;

        public void validateCommand(NewQueryForm target, org.springframework.validation.Errors errors)
        {
            target.ff_newQueryName = StringUtils.trimToNull(target.ff_newQueryName);
            if (null == target.ff_newQueryName)
                errors.reject(ERROR_MSG, "QueryName is required");
                //errors.rejectValue("ff_newQueryName", ERROR_REQUIRED);
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public ModelAndView getView(NewQueryForm form, boolean reshow, BindException errors) throws Exception
        {
            if (form.getSchema() == null)
            {
                if (form.getSchemaName().isEmpty())
                {
                    throw new NotFoundException("Schema name not specified");
                }
                else
                {
                    throw new NotFoundException("Could not find schema: " + form.getSchemaName());
                }
            }
            if (!form.getSchema().canCreate())
            {
                throw new UnauthorizedException();
            }
            getPageConfig().setFocusId("ff_newQueryName");
            _form = form;
            setHelpTopic(new HelpTopic("customSQL"));
            return new JspView<NewQueryForm>(QueryController.class, "newQuery.jsp", form, errors);
        }

        public boolean handlePost(NewQueryForm form, BindException errors) throws Exception
        {
            if (form.getSchema() == null)
            {
                throw new NotFoundException("Could not find schema: " + form.getSchemaName());
            }
            if (!form.getSchema().canCreate())
            {
                throw new UnauthorizedException();
            }
            try
            {
                if (form.ff_baseTableName == null || "".equals(form.ff_baseTableName))
                {
                    errors.reject(ERROR_MSG, "You must select a base table or query name.");
                    return false;
                }

                UserSchema schema = form.getSchema();
                String newQueryName = form.ff_newQueryName;
                QueryDef existing = QueryManager.get().getQueryDef(getContainer(), form.getSchemaName(), newQueryName, true);
                if (existing != null)
                {
                    errors.reject(ERROR_MSG, "The query '" + newQueryName + "' already exists.");
                    return false;
                }
                TableInfo existingTable = form.getSchema().getTable(newQueryName);
                if (existingTable != null)
                {
                    errors.reject(ERROR_MSG, "A table with the name '" + newQueryName + "' already exists.");
                    return false;
                }
                // bug 6095 -- conflicting query and dataset names
                if (form.getSchema().getTableNames().contains(newQueryName))
                {
                    errors.reject(ERROR_MSG, "The query '" + newQueryName + "' already exists as a table");
                    return false;
                }
                QueryDefinition newDef = QueryService.get().createQueryDef(getUser(), getContainer(), form.getSchemaName(), form.ff_newQueryName);
                Query query = new Query(schema);
                query.setRootTable(FieldKey.fromParts(form.ff_baseTableName));
                newDef.setSql(query.getQueryText());

                try
                {
                    newDef.save(getUser(), getContainer());
                }
                catch (SQLException x)
                {
                    if (SqlDialect.isConstraintException(x))
                    {
                        errors.reject(ERROR_MSG, "The query '" + newQueryName + "' already exists.");
                        return false;
                    }
                    else
                    {
                        throw x;
                    }
                }

                _successUrl = newDef.urlFor(form.ff_redirect);
                return true;
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                errors.reject(ERROR_MSG, StringUtils.defaultString(e.getMessage(),e.toString()));
                return false;
            }
        }

        public ActionURL getSuccessURL(NewQueryForm newQueryForm)
        {
            return _successUrl;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root)
                    .addChild("New Query", new QueryUrlsImpl().urlNewQuery(getContainer()));
            return root;
        }
    }


    // CONSIDER : deleting this action after the SQL editor UI changes are finalized, keep in mind that built-in views
    // use this view as welll via the edit metadata page.
    @RequiresPermissionClass(ReadPermission.class)
    public class SourceQueryAction extends FormViewAction<SourceForm>
    {
        SourceForm _form;

        public void validateCommand(SourceForm target, Errors errors)
        {
        }

        public ModelAndView getView(SourceForm form, boolean reshow, BindException errors) throws Exception
        {
            _form = form;
            if (form.getQueryDef() == null)
	    	{
                throw new NotFoundException();
	    	}
            if (form.ff_queryText == null)
            {
                form.ff_queryText = form.getQueryDef().getSql();
                form.ff_metadataText = form.getQueryDef().getMetadataXml();
            }
            try
            {
                QueryDefinition query = form.getQueryDef();
                for (QueryException qpe : query.getParseErrors(form.getSchema()))
                {
                    errors.reject(ERROR_MSG, StringUtils.defaultString(qpe.getMessage(),qpe.toString()));
                }
            }
            catch (Exception e)
            {
                try
                {
                    ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                }
                catch (Throwable t)
                {
                    //
                }
                errors.reject("ERROR_MSG", e.toString());
                Logger.getLogger(QueryController.class).error("Error", e);
            }

            return new JspView<SourceForm>(QueryController.class, "sourceQuery.jsp", form, errors);
        }

        public boolean handlePost(SourceForm form, BindException errors) throws Exception
        {
            _form = form;

            if (!form.canEdit())
                return false;

            try
            {
                QueryDefinition query = form.getQueryDef();
                query.setSql(form.ff_queryText);
                if (query.isTableQueryDefinition() && StringUtils.trimToNull(form.ff_metadataText) == null)
                {
                    if (QueryManager.get().getQueryDef(getContainer(), form.getSchemaName(), form.getQueryName(), false) != null)
                    {
                        // Remember the URL and redirect immediately because the form won't be able to create
                        // the URL again after the query definition is deleted
                        ActionURL redirect = _form.getForwardURL();
                        query.delete(getUser());
                        throw new RedirectException(redirect);
                    }
                }
                else
                {
                    query.setMetadataXml(form.ff_metadataText);
                    /* if query definition has parameters set hidden==true by default */
                    ArrayList<QueryException> qerrors = new ArrayList<QueryException>();
                    TableInfo t = query.getTable(qerrors, false);
                    if (null != t && qerrors.isEmpty())
                    {
                        boolean hasParams = !t.getNamedParameters().isEmpty();
                        if (hasParams)
                            query.setIsHidden(true);
                    }
                    query.save(getUser(), getContainer());
                }
                return true;
            }
            catch (SQLException e)
            {
                errors.reject("An exception occurred: " + e);
                LOG.error("Error", e);
                return false;
            }
        }

        public ActionURL getSuccessURL(SourceForm sourceForm)
        {
            return _form.getForwardURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("customSQL"));

            (new SchemaAction(_form)).appendNavTrail(root)
                    .addChild("Edit " + _form.getQueryName(), _form.urlFor(QueryAction.sourceQuery));
            return root;
        }
    }

    /**
     * Ajax action to save a query. If the save is successful the request will return successfully. A query
     * with SQL syntax errors can still be saved successfully.
     *
     * If the SQL contains parse errors, a parseErrors object will be returned which contains an array of
     * JSON serialized error information.
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class SaveSourceQueryAction extends MutatingApiAction<SourceForm>
    {
        @Override
        public ApiResponse execute(SourceForm form, BindException errors) throws Exception
        {
            if (form.getQueryDef() == null)
                throw new NotFoundException("Query definition not found, schemaName and queryName are required.");

            if (!form.canEdit())
                throw new UnauthorizedException("Edit permissions are required.");

            ApiSimpleResponse response = new ApiSimpleResponse();

            try
            {
                QueryDefinition query = form.getQueryDef();
                query.setSql(form.ff_queryText);

                if (query.isTableQueryDefinition() && StringUtils.trimToNull(form.ff_metadataText) == null)
                {
                    if (QueryManager.get().getQueryDef(getContainer(), form.getSchemaName(), form.getQueryName(), false) != null)
                    {
                        // delete the query in order to reset the metadata over a built-in query
                        query.delete(getUser());
                    }
                }
                else
                {
                    query.setMetadataXml(form.ff_metadataText);
                    /* if query definition has parameters set hidden==true by default */
                    ArrayList<QueryException> qerrors = new ArrayList<QueryException>();
                    TableInfo t = query.getTable(qerrors, false);
                    if (null != t && qerrors.isEmpty())
                    {
                        boolean hasParams = !t.getNamedParameters().isEmpty();
                        if (hasParams)
                            query.setIsHidden(true);
                    }
                    query.save(getUser(), getContainer());

                    // the query was successfully saved, validate the query but return any errors in the success response
                    List<QueryParseException> parseErrors = query.getParseErrors(form.getSchema());
                    if (!parseErrors.isEmpty())
                    {
                        JSONArray errorArray = new JSONArray();

                        for (QueryException e : parseErrors)
                        {
                            errorArray.put(e.toJSON(form.ff_queryText));
                        }
                        response.put("parseErrors", errorArray);
                    }
                }
            }
            catch (SQLException e)
            {
                errors.reject("An exception occurred: " + e);
                LOG.error("Error", e);
            }

            if (errors.hasErrors())
                return null;

            //if we got here, the query is OK
            response.put("success", true);
            return response;
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteQueryAction extends ConfirmAction<QueryForm>
    {
        QueryForm _form;

        public ModelAndView getConfirmView(QueryForm queryForm, BindException errors) throws Exception
        {
            return new JspView<QueryForm>(QueryController.class, "deleteQuery.jsp", queryForm, errors);
        }

        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            _form = form;
            QueryDefinition d = form.getQueryDef();
            if (null == d)
                return false;
            try
            {
                d.delete(getUser());
            }
            catch (Table.OptimisticConflictException x)
            {
            }
            return true;
        }

        public void validateCommand(QueryForm queryForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(QueryForm queryForm)
        {
            return _form.getSchema().urlFor(QueryAction.schema);
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ExecuteQueryAction extends QueryViewAction
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            ensureQueryExists(form);

            _form = form;

            QueryDefinition query = form.getQueryDef();
            if (null == query)
            {
                throw new NotFoundException("Query '" + form.getQueryName() + "' in schema '" + form.getSchemaName() + "' not found");
            }

            QueryView queryView = QueryView.create(form, errors);
            if (isPrint())
            {
                queryView.setPrintView(true);
                getPageConfig().setTemplate(PageConfig.Template.Print);
                getPageConfig().setShowPrintDialog(true);
            }
            queryView.setShadeAlternatingRows(true);
            queryView.setShowBorders(true);
            setHelpTopic(new HelpTopic("customSQL"));
            return queryView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root);
            TableInfo ti = null;
            try
            {
                ti = _form.getSchema() == null ? null : _form.getSchema().getTable(_form.getQueryName());
            }
            catch (QueryParseException x)
            {
                /* */
            }
            String display = ti == null ? _form.getQueryName() : ti.getTitle();
            ActionURL gridUrl = _form.urlFor(QueryAction.executeQuery);
            if (gridUrl == null)
                root.addChild(display);
            else
                root.addChild(display, gridUrl);
            return root;
        }
    }

    @RequiresSiteAdmin
    public class RawTableMetaDataAction extends QueryViewAction
    {
        private String _schemaName;
        private String _tableName;

        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            ensureQueryExists(form);

            _form = form;

            QueryDefinition query = form.getQueryDef();
            if (null == query)
            {
                throw new NotFoundException("Query '" + form.getQueryName() + "' in schema '" + form.getSchemaName() + "' not found");
            }

            QueryView queryView = QueryView.create(form, errors);
            TableInfo ti = queryView.getTable();

            DbSchema schema = ti.getSchema();
            DbScope scope = schema.getScope();
            _schemaName = schema.getName();

            // Try to get the underlying schema table and use the meta data name, #12015
            if (ti instanceof FilteredTable)
                ti = ((FilteredTable) ti).getRealTable();

            if (ti instanceof SchemaTableInfo)
                _tableName = ((SchemaTableInfo) ti).getMetaDataName();
            else
                _tableName = ti.getSelectName();

            ActionURL url = new ActionURL(RawSchemaMetaDataAction.class, getContainer());
            url.addParameter("schemaName", _schemaName);
            HttpView scopeInfo = new ScopeView("Scope and Schema Information", scope, _schemaName, url);

            ModelAndView metaDataView;
            ModelAndView pkView;
            Connection con = null;

            try
            {
                con = scope.getConnection();
                SqlDialect dialect = scope.getSqlDialect();
                DatabaseMetaData dbmd = con.getMetaData();

                ResultSet columnsRs;

                if (dialect.treatCatalogsAsSchemas())
                    columnsRs = dbmd.getColumns(_schemaName, null, _tableName, null);
                else
                    columnsRs = dbmd.getColumns(null, _schemaName, _tableName, null);

                metaDataView = new ResultSetView(new CachedResultSet(columnsRs, true, Table.ALL_ROWS), "Table Meta Data");

                ResultSet pksRs;

                if (dialect.treatCatalogsAsSchemas())
                    pksRs = dbmd.getPrimaryKeys(_schemaName, null, _tableName);
                else
                    pksRs = dbmd.getPrimaryKeys(null, _schemaName, _tableName);

                pkView = new ResultSetView(new CachedResultSet(pksRs, true, Table.ALL_ROWS), "Primary Key Meta Data");
            }
            finally
            {
                if (null != con)
                    scope.releaseConnection(con);
            }

            return new VBox(scopeInfo, metaDataView, pkView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root);
            root.addChild("JDBC Meta Data For Table \"" + _schemaName + "." + _tableName + "\"");
            return root;
        }
    }


    @RequiresSiteAdmin
    public class RawSchemaMetaDataAction extends SimpleViewAction
    {
        private String _schemaName;

        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            _schemaName = getViewContext().getActionURL().getParameter("schemaName");
            DbSchema schema = DefaultSchema.get(getUser(), getContainer()).getSchema(_schemaName).getDbSchema();
            DbScope scope = schema.getScope();
            SqlDialect dialect = scope.getSqlDialect();

            HttpView scopeInfo = new ScopeView("Scope Information", scope);

            ModelAndView tableInfo;
            Connection con = null;

            try
            {
                con = scope.getConnection();
                DatabaseMetaData dma = con.getMetaData();
                ResultSet rs;

                if (dialect.treatCatalogsAsSchemas())
                    rs = dma.getTables(_schemaName, null, null, null);
                else
                    rs = dma.getTables(null, _schemaName, null, null);

                ActionURL url = new ActionURL(RawTableMetaDataAction.class, getContainer());
                url.addParameter("schemaName", _schemaName);
                String tableLink = url.getEncodedLocalURIString() + "&query.queryName=";
                tableInfo = new ResultSetView(new CachedResultSet(rs, true, Table.ALL_ROWS), "Tables", 3, tableLink) {
                    @Override
                    protected boolean shouldLink(ResultSet rs) throws SQLException
                    {
                        // Only link to tables and views (not indexes or sequences)
                        String type = rs.getString(4);
                        return "TABLE".equalsIgnoreCase(type) || "VIEW".equalsIgnoreCase(type);
                    }
                };
            }
            finally
            {
                if (null != con)
                    scope.releaseConnection(con);
            }

            return new VBox(scopeInfo, tableInfo);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("JDBC Meta Data For Schema \"" + _schemaName + "\"");
            return root;
        }
    }


    public static class ScopeView extends WebPartView
    {
        private final DbScope _scope;
        private final String _schemaName;
        private final ActionURL _url;

        private ScopeView(String title, DbScope scope)
        {
            this(title, scope, null, null);
        }

        private ScopeView(String title, DbScope scope, String schemaName, ActionURL url)
        {
            _scope = scope;
            _schemaName = schemaName;
            _url = url;
            setTitle(title);
        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            StringBuilder sb = new StringBuilder("<table>\n");

            if (null != _schemaName)
                sb.append("<tr><td>Schema:</td><td><a href=\"").append(PageFlowUtil.filter(_url)).append("\">").append(PageFlowUtil.filter(_schemaName)).append("</a></td></tr>\n");

            sb.append("<tr><td>Scope:</td><td>").append(_scope.getDisplayName()).append("</td></tr>\n");
            sb.append("<tr><td>Dialect:</td><td>").append(_scope.getSqlDialect().getClass().getSimpleName()).append("</td></tr>\n");
            sb.append("<tr><td>URL:</td><td>").append(_scope.getURL()).append("</td></tr>\n");
            sb.append("</table>\n");

            out.print(sb);
        }
    }


    public static class ResultSetView extends WebPartView
    {
        private final ResultSet _rs;
        private final int _linkColumn;
        private final String _link;

        private ResultSetView(ResultSet rs, String title) throws SQLException
        {
            this(rs, title, 0, null);
        }

        private ResultSetView(ResultSet rs, String title, int linkColumn, String link) throws SQLException
        {
            _rs = rs;
            _linkColumn = linkColumn;
            _link = link;
            setTitle(title);
        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            out.println("<table>");

            try
            {
                ResultSetMetaData md = _rs.getMetaData();
                int columnCount = md.getColumnCount();

                out.print("  <tr>");

                for (int i = 1; i <= columnCount; i++)
                {
                    out.print("<th>");
                    out.print(PageFlowUtil.filter(md.getColumnName(i)));
                    out.print("</th>");
                }

                out.println("</tr>\n");

                while (_rs.next())
                {
                    out.print("  <tr>");

                    for (int i = 1; i <= columnCount; i++)
                    {
                        Object val = _rs.getObject(i);

                        out.print("<td>");

                        if (null != _link && _linkColumn == i && shouldLink(_rs))
                        {
                            out.print("<a href=\"");
                            out.print(PageFlowUtil.filter(_link + val.toString()));
                            out.print("\">");
                        }

                        out.print(null == val ? "&nbsp;" : PageFlowUtil.filter(val));

                        if (null != _link && _linkColumn == i)
                            out.print("</a>");

                        out.print("</td>");
                    }

                    out.println("</tr>\n");
                }
            }
            finally
            {
                ResultSetUtil.close(_rs);
            }

            out.println("</table>\n");
        }

        protected boolean shouldLink(ResultSet rs) throws SQLException
        {
            return true;
        }
    }

    // for backwards compat same as _executeQuery.view ?_print=1
    @RequiresPermissionClass(ReadPermission.class)
    public class PrintRowsAction extends ExecuteQueryAction
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            ensureQueryExists(form);
            _print = true;
            String title = form.getQueryName();
            if (StringUtils.isEmpty(title))
                title = form.getSchemaName();
            getPageConfig().setTitle(title, true);
            return super.getView(form, errors);
        }
    }


    abstract class _ExportQuery<K extends QueryForm> extends SimpleViewAction<K>
    {
        public ModelAndView getView(K form, BindException errors) throws Exception
        {
            ensureQueryExists(form);
            QueryView view = QueryView.create(form, errors);
            getPageConfig().setTemplate(PageConfig.Template.None);
            HttpServletResponse response = getViewContext().getResponse();
            response.setHeader("X-Robots-Tag", "noindex");
            try
            {
                _export(form, view);
                return null;
            }
            catch (QueryService.NamedParameterNotProvided x)
            {
                ExceptionUtil.decorateException(x, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
                throw x;
            }
            catch (QueryParseException x)
            {
                ExceptionUtil.decorateException(x, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
                throw x;
            }
        }

        abstract void _export(K form, QueryView view) throws Exception;

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class ExportScriptForm extends QueryForm
    {
        private String _type;

        public String getScriptType()
        {
            return _type;
        }

        public void setScriptType(String type)
        {
            _type = type;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ExportScriptAction extends SimpleViewAction<ExportScriptForm>
    {
        public ModelAndView getView(ExportScriptForm form, BindException errors) throws Exception
        {
            ensureQueryExists(form);

            return ExportScriptModel.getExportScriptView(QueryView.create(form, errors), form.getScriptType(), getPageConfig(), getViewContext().getResponse());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ExportRowsExcelAction extends _ExportQuery
    {
        void _export(QueryForm form, QueryView view) throws Exception
        {
            view.exportToExcel(getViewContext().getResponse(), ExcelWriter.ExcelDocumentType.xls);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ExportRowsXLSXAction extends _ExportQuery
    {
        void _export(QueryForm form, QueryView view) throws Exception
        {
            view.exportToExcel(getViewContext().getResponse(), ExcelWriter.ExcelDocumentType.xlsx);
        }
    }

    public static class TemplateForm extends QueryForm
    {
        ExcelWriter.CaptionType captionType = ExcelWriter.CaptionType.Label;
        boolean insertColumnsOnly = true;
        String filenamePrefix;

        public void setCaptionType(String s)
        {
            try
            {
                captionType = ExcelWriter.CaptionType.valueOf(s);
            }
            catch (Exception x)
            {

            }
        }

        public String getCaptionType()
        {
            return captionType.name();
        }

        public String getFilenamePrefix()
        {
            return filenamePrefix;
        }

        public void setFilenamePrefix(String prefix)
        {
            this.filenamePrefix = prefix;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)

    /**
     * Can be used to generate an excel template for import into a table.  Supported URL params include:
     * filenamePrefix: the prefix of the excel file that is generated, defaults to '_data'
     * query.viewName: if provided, the resulting excel file will use the fields present in this view, with the caveat
     * that any non-existent columns (like a lookup) or non-usereditable columns will be skipped.  Any required columns
     * missing from this view will be appended to the end of the query.
     * captionType: determines which column property is used in the header.  either Label or Name
     *
     */
    public class ExportExcelTemplateAction extends _ExportQuery<TemplateForm>
    {
        public ExportExcelTemplateAction()
        {
            setCommandClass(TemplateForm.class);
        }

        void _export(TemplateForm form, QueryView view) throws Exception
        {
            boolean respectView = form.getViewName() != null;
            view.exportToExcelTemplate(getViewContext().getResponse(), form.captionType, form.insertColumnsOnly, respectView, form.getFilenamePrefix());
        }
    }

    public static class ExportRowsTsvForm extends QueryForm
    {
        private TSVWriter.DELIM _delim = TSVWriter.DELIM.TAB;
        private TSVWriter.QUOTE _quote = TSVWriter.QUOTE.DOUBLE;

        public TSVWriter.DELIM getDelim()
        {
            return _delim;
        }

        public void setDelim(TSVWriter.DELIM delim)
        {
            _delim = delim;
        }

        public TSVWriter.QUOTE getQuote()
        {
            return _quote;
        }

        public void setQuote(TSVWriter.QUOTE quote)
        {
            _quote = quote;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ExportRowsTsvAction extends _ExportQuery<ExportRowsTsvForm>
    {
        public ExportRowsTsvAction()
        {
            setCommandClass(ExportRowsTsvForm.class);
        }

        void _export(ExportRowsTsvForm form, QueryView view) throws Exception
        {
            view.exportToTsv(getViewContext().getResponse(), form.isExportAsWebPage(), form.getDelim(), form.getQuote());
        }
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    public class ExcelWebQueryAction extends ExportRowsTsvAction
    {
        public ModelAndView getView(ExportRowsTsvForm form, BindException errors) throws Exception
        {
            if (!getContainer().hasPermission(getUser(), ReadPermission.class))
            {
                if (!getUser().isGuest())
                {
                    throw new UnauthorizedException();
                }
                getViewContext().getResponse().setHeader("WWW-Authenticate", "Basic realm=\"" + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getDescription() + "\"");
                getViewContext().getResponse().setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return null;
            }

            // Bug 5610. Excel web queries don't work over SSL if caching is disabled,
            // so we need to allow caching so that Excel can read from IE on Windows.
            HttpServletResponse response = getViewContext().getResponse();
            // Set the headers to allow the client to cache, but not proxies
            response.setHeader("Pragma", "private");
            response.setHeader("Cache-Control", "private");

            ensureQueryExists(form);
            QueryView view = QueryView.create(form, errors);
            getPageConfig().setTemplate(PageConfig.Template.None);
            view.exportToExcelWebQuery(getViewContext().getResponse());
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ExcelWebQueryDefinitionAction extends SimpleViewAction<QueryForm>
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            ensureQueryExists(form);
            String queryViewActionURL = form.getQueryViewActionURL();
            ActionURL url;
            if (queryViewActionURL != null)
            {
                url = new ActionURL(queryViewActionURL);
            }
            else
            {
                url = getViewContext().cloneActionURL();
                url.setAction(ExcelWebQueryAction.class);
            }
            getViewContext().getResponse().setContentType("text/x-ms-iqy");
            String filename =  FileUtil.makeFileNameWithTimestamp(form.getQueryName(), "iqy");
            getViewContext().getResponse().setHeader("Content-disposition", "attachment; filename=\"" + filename +"\"");
            PrintWriter writer = getViewContext().getResponse().getWriter();
            writer.println("WEB");
            writer.println("1");
            writer.println(url.getURIString());

            QueryService.get().addAuditEvent(getUser(), getContainer(), form.getSchemaName(), form.getQueryName(), url, "Exported to Excel Web Query definition", null);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CreateSnapshotAction extends FormViewAction<QuerySnapshotForm>
    {
        ActionURL _successURL;
        public void validateCommand(QuerySnapshotForm form, Errors errors)
        {
            String name = StringUtils.trimToNull(form.getSnapshotName());

            if (name != null)
            {
                QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName(), name);
                if (def != null)
                    errors.reject("snapshotQuery.error", "A Snapshot with the same name already exists");
            }
            else
                errors.reject("snapshotQuery.error", "The Query Snapshot name cannot be blank");
        }

        public ModelAndView getView(QuerySnapshotForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!reshow)
            {
                List<DisplayColumn> columns = QuerySnapshotService.get(form.getSchemaName()).getDisplayColumns(form, errors);
                String[] columnNames = new String[columns.size()];
                int i=0;

                for (DisplayColumn dc : columns)
                    columnNames[i++] = dc.getName();
                form.setSnapshotColumns(columnNames);
            }
            return new JspView<QueryForm>("/org/labkey/query/controllers/createSnapshot.jsp", form, errors);
        }

        public boolean handlePost(QuerySnapshotForm form, BindException errors) throws Exception
        {
            _successURL = QuerySnapshotService.get(form.getSchemaName()).createSnapshot(form, errors);
            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(QuerySnapshotForm queryForm)
        {
            return _successURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Query Snapshot");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class EditSnapshotAction extends FormViewAction<QuerySnapshotForm>
    {
        ActionURL _successURL;

        public void validateCommand(QuerySnapshotForm form, Errors errors)
        {
        }

        public ModelAndView getView(QuerySnapshotForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!reshow)
                form.init(QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName(), form.getSnapshotName()), getUser());

            VBox box = new VBox();
            QuerySnapshotService.I provider = QuerySnapshotService.get(form.getSchemaName());

            if (provider != null)
            {
                boolean showHistory = BooleanUtils.toBoolean(getViewContext().getActionURL().getParameter("showHistory"));

                box.addView(new JspView<QueryForm>("/org/labkey/query/controllers/editSnapshot.jsp", form, errors));

                if (showHistory)
                {
                    HttpView historyView = provider.createAuditView(form);
                    if (historyView != null)
                        box.addView(historyView);
                }

                box.addView(new JspView<QueryForm>("/org/labkey/query/controllers/createSnapshot.jsp", form, errors));
            }
            return box;
        }

        public boolean handlePost(QuerySnapshotForm form, BindException errors) throws Exception
        {
            QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName(), form.getSnapshotName());
            if (def != null)
            {
                def.setColumns(form.getFieldKeyColumns());

                _successURL = QuerySnapshotService.get(form.getSchemaName()).updateSnapshotDefinition(getViewContext(), def, errors);
                if (errors.hasErrors())
                    return false;
            }
            else
                errors.reject("snapshotQuery.error", "Unable to create QuerySnapshotDefinition");

            return false;
        }

        public ActionURL getSuccessURL(QuerySnapshotForm form)
        {
            return _successURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Edit Query Snapshot");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class UpdateSnapshotAction extends SimpleViewAction<QuerySnapshotForm>
    {
        public ModelAndView getView(QuerySnapshotForm form, BindException errors) throws Exception
        {
            ActionURL url = QuerySnapshotService.get(form.getSchemaName()).updateSnapshot(form, errors);
            if (url != null)
                return HttpView.redirect(url);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class MetadataServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new MetadataServiceImpl(getViewContext());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class MetadataQueryAction extends FormViewAction<QueryForm>
    {
        QueryDefinition _query = null;
        QueryForm _form = null;

        public void validateCommand(QueryForm target, Errors errors)
        {
        }

        public ModelAndView getView(QueryForm form, boolean reshow, BindException errors) throws Exception
        {
            ensureQueryExists(form);
            _form = form;
            _query = _form.getQueryDef();
            Map<String, String> props = new HashMap<String, String>();
            props.put("schemaName", form.getSchemaName());
            props.put("queryName", form.getQueryName());
            props.put(MetadataEditor.EDIT_SOURCE_URL, _form.getQueryDef().urlFor(QueryAction.sourceQuery, getContainer()).toString() + "#metadata");
            props.put(MetadataEditor.VIEW_DATA_URL, _form.getQueryDef().urlFor(QueryAction.executeQuery, getContainer()).toString());

            return new GWTView(MetadataEditor.class, props);
        }

        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            return false;
        }

        public ActionURL getSuccessURL(QueryForm metadataForm)
        {
            return _query.urlFor(QueryAction.metadataQuery);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root);
            root.addChild("Edit Metadata: " + _form.getQueryName(), _query.urlFor(QueryAction.metadataQuery));
            return root;
        }
    }

    // Uck. Supports the old and new view designer.
    protected Map<String, Object> saveCustomView(Container container, QueryDefinition queryDef,
                                                 String regionName, String viewName,
                                                 boolean share, boolean inherit,
                                                 boolean session, boolean saveFilter,
                                                 JSONObject jsonView,
                                                 ActionURL srcURL,
                                                 BindException errors)
    {
        User owner = getUser();
        boolean canSaveForAllUsers = container.hasPermission(getUser(), EditSharedViewPermission.class);
        if (share && canSaveForAllUsers && !session)
        {
            owner = null;
        }
        String name = StringUtils.trimToNull(viewName);

        boolean isHidden = false;
        CustomView view = queryDef.getCustomView(owner, getViewContext().getRequest(), name);

        // 11179: Allow editing the view if we're saving to session.
        // NOTE: Check for session flag first otherwise the call to canEdit() will add errors to the errors collection.
        boolean canEdit = view == null || session || view.canEdit(container, errors);
        if (errors.hasErrors())
            return null;
        
        if (canEdit)
        {
            // Issue 13594: Disallow setting of the customview inherit bit for query views
            // that have no available container filter types.  Unfortunately, the only way
            // to get the container filters is from the QueryView.  Ideally, the query def
            // would know if it was container filterable or not instead of using the QueryView.
            if (inherit && canSaveForAllUsers && !session)
            {
                UserSchema schema = queryDef.getSchema();
                QueryView queryView = schema.createView(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, queryDef.getName(), errors);
                if (queryView != null)
                {
                    List<ContainerFilter.Type> allowableContainerFilterTypes = queryView.getAllowableContainerFilterTypes();
                    if (allowableContainerFilterTypes == null || allowableContainerFilterTypes.size() <= 1)
                    {
                        errors.reject(ERROR_MSG, "QueryView doesn't support inherited custom views");
                        return null;
                    }
                }
            }

            // Create a new view if none exists or the current view is a shared view
            // and the user wants to override the shared view with a personal view.
            if (view == null || (owner != null && view.isShared()))
            {
                view = queryDef.createCustomView(owner, name);
                if (owner != null && session)
                    ((CustomViewImpl) view).isSession(true);
            }
            else if (session != view.isSession())
            {
                if (session)
                {
                    assert !view.isSession();
                    if (owner == null)
                    {
                        errors.reject(ERROR_MSG, "Session views can't be saved for all users");
                        return null;
                    }

                    // The form is saving to session but the view is in the database.
                    // Make a copy in case it's a read-only version from an XML file
                    view = queryDef.createCustomView(owner, name);
                    ((CustomViewImpl) view).isSession(true);
                }
                else
                {
                    // Remove the session view and call saveCustomView again to either create a new view or update an existing view.
                    assert view.isSession();
                    boolean success = false;
                    try
                    {
                        view.delete(getUser(), getViewContext().getRequest());
                        Map<String, Object> ret = saveCustomView(container, queryDef, regionName, viewName, share, inherit, session, saveFilter, jsonView, srcURL, errors);
                        success = !errors.hasErrors() && ret != null;
                        return success ? ret : null;
                    }
                    finally
                    {
                        if (!success)
                        {
                            // dirty the view then save the deleted session view back in session state
                            view.setName(view.getName());
                            view.save(getUser(), getViewContext().getRequest());
                        }
                    }
                }
            }

            // NOTE: Updaing, saving, and deleting the view may throw an exception
            ((CustomViewImpl)view).update(jsonView, saveFilter);
            if (canSaveForAllUsers && !session)
            {
                view.setCanInherit(inherit);
            }
            isHidden = view.isHidden();
            ((CustomViewImpl) view).setContainer(container);
            view.save(getUser(), getViewContext().getRequest());
            if (owner == null)
            {
                // New view is shared so delete any previous custom view owned by the user with the same name.
                CustomView personalView = queryDef.getCustomView(getUser(), getViewContext().getRequest(), name);
                if (personalView != null && !personalView.isShared())
                {
                    personalView.delete(getUser(), getViewContext().getRequest());
                }
            }
        }

        ActionURL returnURL = srcURL;
        if (null == returnURL)
        {
            returnURL = getViewContext().cloneActionURL().setAction(QueryAction.executeQuery.name());
        }
        else
        {
            returnURL = returnURL.clone();
            if (name == null || !canEdit)
            {
                returnURL.deleteParameter(regionName + "." + QueryParam.viewName);
            }
            else if (!isHidden)
            {
                returnURL.replaceParameter(regionName + "." + QueryParam.viewName, name);
            }
            returnURL.deleteParameter(regionName + "." + QueryParam.ignoreFilter.toString());
            if (saveFilter)
            {
                for (String key : returnURL.getKeysByPrefix(regionName + "."))
                {
                    if (isFilterOrSort(regionName, key))
                        returnURL.deleteFilterParameters(key);
                }
            }
        }

        Map<String, Object> ret = new HashMap<String, Object>();
        ret.put("redirect", returnURL);
        if (view != null)
            ret.put("view", CustomViewUtil.toMap(view, getViewContext().getUser(), true));
        return ret;
    }

    private boolean isFilterOrSort(String dataRegionName, String param)
    {
        assert param.startsWith(dataRegionName + ".");
        String check = param.substring(dataRegionName.length() + 1);
        if (check.indexOf("~") >= 0)
            return true;
        if ("sort".equals(check))
            return true;
        if (check.equals("containerFilterName"))
            return true;
        return false;
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SaveQueryViewsAction extends ApiAction<SimpleApiJsonForm>
    {
        @Override
        public ApiResponse execute(SimpleApiJsonForm form, BindException errors) throws Exception
        {
            JSONObject json = form.getJsonObject();
            if (json == null)
                throw new NotFoundException("Empty request");

            String schemaName = json.getString(QueryParam.schemaName.toString());
            String queryName = json.getString(QueryParam.queryName.toString());
            if (schemaName == null || queryName == null)
                throw new NotFoundException("schemaName and queryName are required");

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), schemaName);
            if (schema == null)
                throw new NotFoundException("schema not found");

            QueryDefinition queryDef = QueryService.get().getQueryDef(getUser(), getContainer(), schemaName, queryName);
            if (queryDef == null)
                queryDef = schema.getQueryDefForTable(queryName);
            if (queryDef == null)
                throw new NotFoundException("query not found");

            Map<String, Object> response = new HashMap<String, Object>();
            response.put(QueryParam.schemaName.toString(), schemaName);
            response.put(QueryParam.queryName.toString(), queryName);
            List<Map<String, Object>> views = new ArrayList<Map<String, Object>>();
            response.put("views", views);

            ActionURL redirect = null;
            JSONArray jsonViews = json.getJSONArray("views");
            for (int i = 0; i < jsonViews.length(); i++)
            {
                final JSONObject jsonView = jsonViews.getJSONObject(i);
                String viewName = jsonView.getString("name");

                boolean shared = jsonView.optBoolean("shared", false);
                boolean inherit = jsonView.optBoolean("inherit", false);
                boolean session = jsonView.optBoolean("session", false);
                // Users may save views to a location other than the current container
                String containerPath = jsonView.optString("containerPath", getContainer().getPath());
                Container container;
                if (inherit)
                {
                    // Only respect this request if it's a view that is inheritable in subfolders
                    container = ContainerManager.getForPath(containerPath);
                }
                else
                {
                    // Otherwise, save it in the current container
                    container = getViewContext().getContainer();
                }

                if (container == null)
                {
                    throw new NotFoundException("No such container: " + containerPath);
                }

                Map<String, Object> savedView = saveCustomView(
                        container, queryDef, QueryView.DATAREGIONNAME_DEFAULT, viewName,
                        shared, inherit, session, true, jsonView, null, errors);

                if (savedView != null)
                {
                    if (redirect == null)
                        redirect = (ActionURL)savedView.get("redirect");
                    views.add((Map<String, Object>)savedView.get("view"));
                }
            }

            if (redirect != null)
                response.put("redirect", redirect);

            if (errors.hasErrors())
                return null;
            else
                return new ApiSimpleResponse(response);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class PropertiesQueryAction extends FormViewAction<PropertiesForm>
    {
        PropertiesForm _form = null;
        private String _queryName;

        public void validateCommand(PropertiesForm target, Errors errors)
        {
        }

        public ModelAndView getView(PropertiesForm form, boolean reshow, BindException errors) throws Exception
        {
            // assertQueryExists requires that it be well-formed
            // assertQueryExists(form);
            QueryDefinition queryDef = form.getQueryDef();
            if (queryDef == null)
			{
                throw new NotFoundException("Query not found");
			}
            _form = form;
            _form.setDescription(queryDef.getDescription());
            _form.setInheritable(queryDef.canInherit());
            _form.setHidden(queryDef.isHidden());
            setHelpTopic(new HelpTopic("customSQL"));
            _queryName = form.getQueryName();

            return new JspView<PropertiesForm>(QueryController.class, "propertiesQuery.jsp", form, errors);
        }

        public boolean handlePost(PropertiesForm form, BindException errors) throws Exception
        {
            // assertQueryExists requires that it be well-formed
            // assertQueryExists(form);
            if (!form.canEdit())
            {
                throw new UnauthorizedException();
            }
            QueryDefinition queryDef = form.getQueryDef();
            _queryName = form.getQueryName();
            if (queryDef == null || !queryDef.getContainer().getId().equals(getContainer().getId()))
                throw new NotFoundException("Query not found");

			_form = form;

			if (!StringUtils.isEmpty(form.rename) && !form.rename.equalsIgnoreCase(queryDef.getName()))
			{
				QueryService s = QueryService.get();
				QueryDefinition copy = s.createQueryDef(getUser(), queryDef.getContainer(), queryDef.getSchemaName(), form.rename);
				copy.setSql(queryDef.getSql());
				copy.setMetadataXml(queryDef.getMetadataXml());
				copy.setDescription(form.description);
				copy.setCanInherit(form.inheritable);
				copy.setIsHidden(form.hidden);
				copy.save(getUser(), copy.getContainer());
				queryDef.delete(getUser());
				// update form so getSuccessURL() works
				_form = new PropertiesForm(form.getSchemaName(), form.rename);
				_form.setViewContext(form.getViewContext());
                _queryName = form.rename;
				return true;
			}

            queryDef.setDescription(form.description);
            queryDef.setCanInherit(form.inheritable);
            queryDef.setIsHidden(form.hidden);
            queryDef.save(getUser(), getContainer());
            return true;
        }

        public ActionURL getSuccessURL(PropertiesForm propertiesForm)
        {
            ActionURL url = new ActionURL(BeginAction.class, propertiesForm.getViewContext().getContainer());
            url.addParameter("schemaName", propertiesForm.getSchemaName());
            if (null != _queryName)
                url.addParameter("queryName", _queryName);
            return url;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root);
            root.addChild("Edit query properties");
            return root;
        }
    }


    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteQueryRowsAction extends FormHandlerAction<QueryForm>
    {
        ActionURL _url = null;

        public void validateCommand(QueryForm target, Errors errors)
        {
        }

        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            ActionURL forward = null;
            String returnURL = (String)this.getProperty(QueryParam.srcURL); // UNDONE: add to QueryForm
            if (returnURL != null)
                forward = new ActionURL(returnURL);
            TableInfo table = form.getQueryDef().getTable(form.getSchema(), null, true);

            if (!table.hasPermission(getUser(), DeletePermission.class))
            {
                throw new UnauthorizedException();
            }

            QueryUpdateService updateService = table.getUpdateService();
            if (updateService == null)
                throw new UnsupportedOperationException("Unable to delete - no QueryUpdateService registered for " + form.getSchemaName() + "." + form.getQueryName());

            Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), true);
            List<ColumnInfo> pks = table.getPkColumns();
            int numPks = pks.size();

            //normalize the pks to arrays of correctly-typed objects
            List<Map<String, Object>> keyValues = new ArrayList<Map<String, Object>>(ids.size());
            for (String id : ids)
            {
                String[] stringValues;
                if (numPks > 1)
                {
                    stringValues = id.split(",");
                    if (stringValues.length != numPks)
                        throw new IllegalStateException("This table has " + numPks + " primary-key columns, but " + stringValues.length + " primary-key values were provided!");
                }
                else
                    stringValues = new String[]{id};

                Map<String, Object> rowKeyValues = new HashMap<String, Object>();
                for (int idx = 0; idx < numPks; ++idx)
                {
                    ColumnInfo keyColumn = pks.get(idx);
                    Object keyValue = keyColumn.getJavaClass() == String.class ? stringValues[idx] : ConvertUtils.convert(stringValues[idx], keyColumn.getJavaClass());
                    rowKeyValues.put(keyColumn.getName(), keyValue);
                }
                keyValues.add(rowKeyValues);
            }

            DbSchema dbSchema = table.getSchema();
            try
            {
                dbSchema.getScope().ensureTransaction();

                updateService.deleteRows(getUser(), getContainer(), keyValues, null);

                dbSchema.getScope().commitTransaction();

                _url = forward;
            }
            catch (SQLException x)
            {
                if (!SqlDialect.isConstraintException(x))
                    throw x;
                errors.reject(ERROR_MSG, getMessage(table.getSchema().getSqlDialect(), x));
                return false;
            }
            catch (BatchValidationException x)
            {
                x.addToErrors(errors);
            }
            catch (Exception x)
            {
                errors.reject(ERROR_MSG, null == x.getMessage() ? x.toString() : x.getMessage());
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), x);
            }
            finally
            {
                dbSchema.getScope().closeConnection();
            }

            return true;
        }

        public ActionURL getSuccessURL(QueryForm queryForm)
        {
            return _url;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public static class DetailsQueryRowAction extends UserSchemaAction
    {
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
        {
            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);

            if (_schema != null && _table != null)
            {
                if (_table.hasPermission(getViewContext().getUser(), UpdatePermission.class))
                {
                    StringExpression updateExpr = _form.getQueryDef().urlExpr(QueryAction.updateQueryRow, _schema.getContainer());
                    if (updateExpr != null)
                    {
                        String url = updateExpr.eval(tableForm.getTypedValues());
                        if (url != null)
                        {
                            ActionURL updateUrl = new ActionURL(url);
                            ActionButton editButton = new ActionButton("Edit", updateUrl);
                            bb.add(editButton);
                        }
                    }
                }


                ActionURL gridUrl;
                if (_form.getReturnActionURL() != null)
                {
                    // If we have a specific return URL requested, use that
                    gridUrl = _form.getReturnActionURL();
                }
                else
                {
                    // Otherwise go back to the default grid view
                    gridUrl = _schema.urlFor(QueryAction.executeQuery, _form.getQueryDef());
                }
                if (gridUrl != null)
                {
                    ActionButton gridButton = new ActionButton("Show Grid", gridUrl);
                    bb.add(gridButton);
                }
            }

            DetailsView detailsView = new DetailsView(tableForm);
            detailsView.getDataRegion().setButtonBar(bb);

            VBox view = new VBox(detailsView);

            DetailsURL detailsURL = QueryService.get().getAuditDetailsURL(getViewContext().getUser(), getViewContext().getContainer(), _table);

            if (detailsURL != null)
            {
                String url = detailsURL.eval(tableForm.getTypedValues());
                if (url != null)
                {
                    ActionURL auditURL = new ActionURL(url);

                    QueryView historyView = QueryUpdateAuditViewFactory.getInstance().createDetailsQueryView(getViewContext(),
                            auditURL.getParameter(QueryParam.schemaName),
                            auditURL.getParameter(QueryParam.queryName),
                            auditURL.getParameter("keyValue"));


                    historyView.setFrame(WebPartView.FrameType.PORTAL);
                    historyView.setTitle("History");

                    view.addView(historyView);
                }
            }
            return view;
        }

        public boolean handlePost(QueryUpdateForm tableForm, BindException errors) throws Exception
        {
            return false;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            super.appendNavTrail(root);
            root.addChild("Details");
            return root;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public static class InsertQueryRowAction extends UserSchemaAction
    {
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
        {
            InsertView view = new InsertView(tableForm, errors);
            view.getDataRegion().setButtonBar(createSubmitCancelButtonBar(tableForm));
            return view;
        }

        public boolean handlePost(QueryUpdateForm tableForm, BindException errors) throws Exception
        {
            doInsertUpdate(tableForm, errors, true);
            return 0 == errors.getErrorCount();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            super.appendNavTrail(root);
            root.addChild("Insert " + _form.getQueryName());
            return root;
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public static class UpdateQueryRowAction extends UserSchemaAction
    {
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
        {
            ButtonBar bb = createSubmitCancelButtonBar(tableForm);
            UpdateView view = new UpdateView(tableForm, errors);
            view.getDataRegion().setButtonBar(bb);
            return view;
        }

        public boolean handlePost(QueryUpdateForm tableForm, BindException errors) throws Exception
        {
            doInsertUpdate(tableForm, errors, false);
            return 0 == errors.getErrorCount();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            super.appendNavTrail(root);
            root.addChild("Edit " + _form.getQueryName());
            return root;
        }
    }

    // alias
    public class DeleteAction extends DeleteQueryRowsAction
    {
    }

    public abstract class QueryViewAction extends SimpleViewAction<QueryForm>
    {
        QueryForm _form;
    }

    public abstract class QueryFormAction extends FormViewAction<QueryForm>
    {
        QueryForm _form;
    }

    public static class APIQueryForm extends QueryForm
    {
        private Integer _start;
        private Integer _limit;
        private String _sort;
        private String _dir;
        private boolean _includeDetailsColumn = false;
        private boolean _includeUpdateColumn = false;
        private String _containerFilter;
        private boolean _includeTotalCount = true;
        private boolean _includeStyle = false;

        public Integer getStart()
        {
            return _start;
        }

        public void setStart(Integer start)
        {
            _start = start;
        }

        public Integer getLimit()
        {
            return _limit;
        }

        public void setLimit(Integer limit)
        {
            _limit = limit;
        }

        public String getSort()
        {
            return _sort;
        }

        public void setSort(String sort)
        {
            _sort = sort;
        }

        public String getDir()
        {
            return _dir;
        }

        public void setDir(String dir)
        {
            _dir = dir;
        }

        public String getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(String containerFilter)
        {
            _containerFilter = containerFilter;
        }

        public boolean isIncludeTotalCount()
        {
            return _includeTotalCount;
        }

        public void setIncludeTotalCount(boolean includeTotalCount)
        {
            _includeTotalCount = includeTotalCount;
        }

        public boolean isIncludeStyle()
        {
            return _includeStyle;
        }

        public void setIncludeStyle(boolean includeStyle)
        {
            _includeStyle = includeStyle;
        }

        public boolean isIncludeDetailsColumn()
        {
            return _includeDetailsColumn;
        }

        public void setIncludeDetailsColumn(boolean includeDetailsColumn)
        {
            _includeDetailsColumn = includeDetailsColumn;
        }

        public boolean isIncludeUpdateColumn()
        {
            return _includeUpdateColumn;
        }

        public void setIncludeUpdateColumn(boolean includeUpdateColumn)
        {
            _includeUpdateColumn = includeUpdateColumn;
        }
    }


    @ActionNames("selectRows, getQuery")
    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(9.1)
    public class SelectRowsAction extends ApiAction<APIQueryForm>
    {
        public ApiResponse execute(APIQueryForm form, BindException errors) throws Exception
        {
            ensureQueryExists(form);

            // Issue 12233: add implicit maxRows=100k when using client API
            if (null == form.getLimit()
                    && null == getViewContext().getRequest().getParameter(form.getDataRegionName() + "." + QueryParam.maxRows)
                    && null == getViewContext().getRequest().getParameter(form.getDataRegionName() + "." + QueryParam.showRows))
            {
                form.getQuerySettings().setShowRows(ShowRows.PAGINATED);
                form.getQuerySettings().setMaxRows(100000);
            }

            if (form.getLimit() != null)
            {
                form.getQuerySettings().setShowRows(ShowRows.PAGINATED);
                form.getQuerySettings().setMaxRows(form.getLimit().intValue());
            }
            if (form.getStart() != null)
                form.getQuerySettings().setOffset(form.getStart().intValue());
            if (form.getSort() != null)
            {
                ActionURL sortFilterURL = getViewContext().getActionURL().clone();
                boolean desc = "DESC".equals(form.getDir());
                sortFilterURL.replaceParameter("query.sort", (desc ? "-" : "") + form.getSort());
                form.getQuerySettings().setSortFilterURL(sortFilterURL); //this isn't working!
            }
            if (form.getContainerFilter() != null)
            {
                // If the user specified an incorrect filter, throw an IllegalArgumentException
                ContainerFilter.Type containerFilterType =
                    ContainerFilter.Type.valueOf(form.getContainerFilter());
                form.getQuerySettings().setContainerFilterName(containerFilterType.name());
            }

            QueryView view = QueryView.create(form, errors);

            view.setShowPagination(form.isIncludeTotalCount());

            //if viewName was specified, ensure that it was actually found and used
            //QueryView.create() will happily ignore an invalid view name and just return the default view
            if (null != StringUtils.trimToNull(form.getViewName()) &&
                    null == view.getQueryDef().getCustomView(getUser(), getViewContext().getRequest(), form.getViewName()))
            {
                throw new NotFoundException("The view named '" + form.getViewName() + "' does not exist for this user!");
            }

            boolean isEditable = isQueryEditable(view.getTable());
            boolean metaDataOnly = form.getQuerySettings().getMaxRows() == 0;

            //if requested version is >= 9.1, use the extended api query response
            if (getRequestedApiVersion() >= 9.1)
            {
                ExtendedApiQueryResponse response = new ExtendedApiQueryResponse(view, getViewContext(), isEditable, true,
                        form.getSchemaName(), form.getQueryName(), form.getQuerySettings().getOffset(), null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn());
                response.includeStyle(form.isIncludeStyle());
                return response;
            }
            else
            {
                return new ApiQueryResponse(view, getViewContext(), isEditable, true,
                        form.getSchemaName(), form.getQueryName(), form.getQuerySettings().getOffset(), null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn());
            }
        }
    }

    protected boolean isQueryEditable(TableInfo table)
    {
        if (!getViewContext().getContainer().hasPermission("isQueryEditable", getUser(), DeletePermission.class))
            return false;
        QueryUpdateService updateService = null;
        try
        {
            updateService = table.getUpdateService();
        }
        catch(Exception ignore) {}
        return null != table && null != updateService;
    }

    public static class ExecuteSqlForm extends APIQueryForm
    {
        private String _sql;
        private Integer _maxRows;
        private Integer _offset;
        private boolean _saveInSession;

        public String getSql()
        {
            return _sql;
        }

        public void setSql(String sql)
        {
            _sql = sql;
        }

        public Integer getMaxRows()
        {
            return _maxRows;
        }

        public void setMaxRows(Integer maxRows)
        {
            _maxRows = maxRows;
        }

        public Integer getOffset()
        {
            return _offset;
        }

        public void setOffset(Integer offset)
        {
            _offset = offset;
        }

        public void setLimit(Integer limit)
        {
            _maxRows = limit;
        }

        public void setStart(Integer start)
        {
            _offset = start;
        }

        public boolean isSaveInSession()
        {
            return _saveInSession;
        }

        public void setSaveInSession(boolean saveInSession)
        {
            _saveInSession = saveInSession;
        }

        @Override
        public String getQueryName()
        {
            // ExecuteSqlAction doesn't allow setting query name parameter.
            return null;
        }

        @Override
        public void setQueryName(String name)
        {
            // ExecuteSqlAction doesn't allow setting query name parameter.
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(9.1)
    public class ExecuteSqlAction extends ApiAction<ExecuteSqlForm>
    {
        public ApiResponse execute(ExecuteSqlForm form, BindException errors) throws Exception
        {
            if (form.getSchema() == null)
            {
                throw new NotFoundException("Could not find schema: " + form.getSchemaName());
            }

            String schemaName = StringUtils.trimToNull(form.getQuerySettings().getSchemaName());
            if (null == schemaName)
                throw new IllegalArgumentException("No value was supplied for the required parameter 'schemaName'.");
            String sql = StringUtils.trimToNull(form.getSql());
            if (null == sql)
                throw new IllegalArgumentException("No value was supplied for the required parameter 'sql'.");

            //create a temp query settings object initialized with the posted LabKey SQL
            //this will provide a temporary QueryDefinition to Query
            QuerySettings settings = form.getQuerySettings();
            if (form.isSaveInSession())
            {
                QueryDefinition def = QueryService.get().saveSessionQuery(getViewContext(), getContainer(), schemaName, sql);
                settings.setDataRegionName("executeSql");
                settings.setQueryName(def.getName());
            }
            else
            {
                settings = new TempQuerySettings(getViewContext(), sql, settings);
            }

            //need to explicitly turn off various UI options that will try to refer to the
            //current URL and query string
            settings.setAllowChooseView(false);
            settings.setAllowCustomizeView(false);

            // Issue 12233: add implicit maxRows=100k when using client API
            settings.setShowRows(ShowRows.PAGINATED);
            settings.setMaxRows(100000);

            // 16961: ExecuteSql API without maxRows parameter defaults to returning 100 rows
            //apply optional settings (maxRows, offset)
            boolean metaDataOnly = false;
            if (null != form.getMaxRows() && form.getMaxRows().intValue() >= 0)
            {
                settings.setShowRows(ShowRows.PAGINATED);
                settings.setMaxRows(Table.ALL_ROWS == form.getMaxRows() ? 1 : form.getMaxRows());
                metaDataOnly = (Table.ALL_ROWS == form.getMaxRows());
            }

            int offset = 0;
            if (null != form.getOffset())
            {
                settings.setOffset(form.getOffset().longValue());
                offset = form.getOffset();
            }

            if (form.getContainerFilter() != null)
            {
                // If the user specified an incorrect filter, throw an IllegalArgumentException
                ContainerFilter.Type containerFilterType =
                    ContainerFilter.Type.valueOf(form.getContainerFilter());
                settings.setContainerFilterName(containerFilterType.name());
            }

            //build a query view using the schema and settings
            QueryView view = new QueryView(form.getSchema(), settings, errors);
            view.setShowRecordSelectors(false);
            view.setShowExportButtons(false);
            view.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
            view.setShowPagination(form.isIncludeTotalCount());

            TableInfo t = view.getTable();
            boolean isEditable = null != t && isQueryEditable(view.getTable());

            if (getRequestedApiVersion() >= 9.1)
                return new ExtendedApiQueryResponse(view, getViewContext(), isEditable,
                        false, schemaName, form.isSaveInSession() ? settings.getQueryName() : "sql", offset, null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn());
            else
                return new ApiQueryResponse(view, getViewContext(), isEditable,
                        false, schemaName, form.isSaveInSession() ? settings.getQueryName() : "sql", offset, null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SelectDistinctAction extends ApiAction<QueryForm>
    {
        @Override
        public ApiResponse execute(QueryForm form, BindException errors) throws Exception
        {
            ensureQueryExists(form);

            SQLFragment sql = getDistinctSql(form, errors);

            if (errors.hasErrors())
                return null;

            ApiResponseWriter writer = new ApiJsonWriter(getViewContext().getResponse());
            writer.startResponse();

            writer.writeProperty("schemaName", form.getSchemaName());
            writer.writeProperty("queryName", form.getQueryName());
            writer.startList("values");

            ResultSet rs = null;
            try
            {
                rs = Table.executeQuery(form.getSchema().getDbSchema(), sql);

                while (rs.next())
                {
                    writer.writeListEntry(rs.getObject("value"));
                }
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
            writer.endList();
            writer.endResponse();

            return null;
        }

        @Nullable
        private SQLFragment getDistinctSql(QueryForm form, BindException errors)
        {
            TableInfo table = form.getSchema().getTable(form.getQueryName());
            QuerySettings settings = form.getQuerySettings();
            QueryService service = QueryService.get();

            Map<FieldKey, ColumnInfo> columns = service.getColumns(table, settings.getFieldKeys());
            if (columns.size() != 1)
            {
                errors.reject(ERROR_MSG, "Select Distinct requires that only one column be requested.");
                return null;
            }

            ColumnInfo col = table.getColumn(settings.getFieldKeys().get(0));
            if (col == null)
            {
                errors.reject(ERROR_MSG, "\"" + settings.getFieldKeys().get(0).getName() + "\" is not a valid column.");
                return null;
            }

            // Attach any URL-based filters. This would apply to 'filterArray' from the JavaScript API.
            SimpleFilter filter = new SimpleFilter(settings.getBaseFilter());
            filter.addUrlFilters(getViewContext().getActionURL(), "query");

            SQLFragment selectSql = service.getSelectSQL(table, columns.values(), filter, null, Table.ALL_ROWS, Table.NO_OFFSET, false);

            // Regenerate the column since the alias may have changed after call to getSelectSQL()
            col = table.getColumn(settings.getFieldKeys().get(0));

            SQLFragment sql = new SQLFragment("SELECT DISTINCT " + col.getAlias() + " AS value FROM(");
            sql.append(selectSql);
            sql.append(") AS S ORDER BY value");

            return sql;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ImportAction extends AbstractQueryImportAction<QueryForm>
    {
        QueryForm _form;

        public ImportAction()
        {
            super(QueryForm.class);
        }

        @Override
        protected void initRequest(QueryForm form) throws ServletException
        {
            _form = form;
            ensureQueryExists(form);

            QueryDefinition query = form.getQueryDef();
            List<QueryException> qpe = new ArrayList<QueryException>();
            TableInfo t = query.getTable(form.getSchema(), qpe, true);
            if (!qpe.isEmpty())
                throw qpe.get(0);
            if (null != t)
                setTarget(t);
        }

        @Override
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            initRequest(form);
            return super.getDefaultImportView(form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root);
            root.addChild(_form.getQueryName(), _form.urlFor(QueryAction.executeQuery));
            root.addChild("Import Data");
            return root;
        }
    }


    public static class ExportSqlForm
    {
        private String _sql;
        private String _schemaName;
        private String _containerFilter;
        private String _format = "excel";

        public String getSql()
        {
            return _sql;
        }

        public void setSql(String sql)
        {
            _sql = sql;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(String containerFilter)
        {
            _containerFilter = containerFilter;
        }

        public String getFormat()
        {
            return _format;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setFormat(String format)
        {
            _format = format;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(9.2)
    public class ExportSqlAction extends ExportAction<ExportSqlForm>
    {
        public void export(ExportSqlForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            String schemaName = StringUtils.trimToNull(form.getSchemaName());
            if (null == schemaName)
                throw new NotFoundException("No value was supplied for the required parameter 'schemaName'");
            String sql = StringUtils.trimToNull(form.getSql());
            if (null == sql)
                throw new NotFoundException("No value was supplied for the required parameter 'sql'");

            UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), schemaName);

            //create a temp query settings object initialized with the posted LabKey SQL
            //this will provide a temporary QueryDefinition to Query
            TempQuerySettings settings = new TempQuerySettings(getViewContext(), sql);

            //need to explicitly turn off various UI options that will try to refer to the
            //current URL and query string
            settings.setAllowChooseView(false);
            settings.setAllowCustomizeView(false);

            //return all rows
            settings.setShowRows(ShowRows.ALL);

            //add container filter if supplied
            if (form.getContainerFilter() != null && form.getContainerFilter().length() > 0)
            {
                ContainerFilter.Type containerFilterType =
                    ContainerFilter.Type.valueOf(form.getContainerFilter());
                settings.setContainerFilterName(containerFilterType.name());
            }

            //build a query view using the schema and settings
            QueryView view = new QueryView(schema, settings, errors);
            view.setShowRecordSelectors(false);
            view.setShowExportButtons(false);
            view.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);

            //export it
            response.setHeader("Pragma", "private");
            response.setHeader("Cache-Control", "private");
            response.setHeader("X-Robots-Tag", "noindex");

            if ("excel".equalsIgnoreCase(form.getFormat()))
                view.exportToExcel(response);
            else if ("tsv".equalsIgnoreCase(form.getFormat()))
                view.exportToTsv(response);
            else
                errors.reject(null, "Invalid format specified; must be 'excel' or 'tsv'");

            for (QueryException qe : view.getParseErrors())
                errors.reject(null, qe.getMessage());

            if (errors.hasErrors())
                throw new ExportException(new SimpleErrorView(errors, false));
        }
    }

    public static class ApiSaveRowsForm extends SimpleApiJsonForm
    {
    }

    private enum CommandType
    {
        insert(InsertPermission.class)
        {
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException
            {
                BatchValidationException errors = new BatchValidationException();
                List<Map<String, Object>> insertedRows = qus.insertRows(user, container, rows, errors, extraContext);
                if (errors.hasErrors())
                    throw errors;
                return qus.getRows(user, container, insertedRows);
            }
        },
        insertWithKeys(InsertPermission.class)
        {
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException
            {
                List<Map<String, Object>> newRows = new ArrayList<Map<String, Object>>();
                List<Map<String, Object>> oldKeys = new ArrayList<Map<String, Object>>();
                for (Map<String, Object> row : rows)
                {
                    //issue 13719: use CaseInsensitiveHashMaps.  Also allow either values or oldKeys to be null
                    CaseInsensitiveHashMap newMap = row.get(SaveRowsAction.PROP_VALUES) != null ? new CaseInsensitiveHashMap((Map<String, Object>)row.get(SaveRowsAction.PROP_VALUES)) : new CaseInsensitiveHashMap();
                    newRows.add(newMap);

                    CaseInsensitiveHashMap oldMap = row.get(SaveRowsAction.PROP_OLD_KEYS) != null ? new CaseInsensitiveHashMap((Map<String, Object>)row.get(SaveRowsAction.PROP_OLD_KEYS)) : new CaseInsensitiveHashMap();
                    oldKeys.add(oldMap);
                }
                BatchValidationException errors = new BatchValidationException();
                List<Map<String, Object>> updatedRows = qus.insertRows(user, container, newRows, errors, extraContext);
                if (errors.hasErrors())
                    throw errors;
                updatedRows = qus.getRows(user, container, updatedRows);
                List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
                for (int i = 0; i < updatedRows.size(); i++)
                {
                    Map<String, Object> result = new HashMap<String, Object>();
                    result.put(SaveRowsAction.PROP_VALUES, updatedRows.get(i));
                    result.put(SaveRowsAction.PROP_OLD_KEYS, oldKeys.get(i));
                    results.add(result);
                }
                return results;
            }
        },
        update(UpdatePermission.class)
        {
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException
            {
                List<Map<String, Object>> updatedRows = qus.updateRows(user, container, rows, rows, extraContext);
                return qus.getRows(user, container, updatedRows);
            }
        },
        updateChangingKeys(UpdatePermission.class)
        {
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException
            {
                List<Map<String, Object>> newRows = new ArrayList<Map<String, Object>>();
                List<Map<String, Object>> oldKeys = new ArrayList<Map<String, Object>>();
                for (Map<String, Object> row : rows)
                {
                    // issue 13719: use CaseInsensitiveHashMaps.  Also allow either values or oldKeys to be null.
                    // this should never happen on an update, but we will let it fail later with a better error message instead of the NPE here
                    CaseInsensitiveHashMap newMap = row.get(SaveRowsAction.PROP_VALUES) != null ? new CaseInsensitiveHashMap((Map<String, Object>)row.get(SaveRowsAction.PROP_VALUES)) : new CaseInsensitiveHashMap();
                    newRows.add(newMap);

                    CaseInsensitiveHashMap oldMap = row.get(SaveRowsAction.PROP_OLD_KEYS) != null ? new CaseInsensitiveHashMap((Map<String, Object>)row.get(SaveRowsAction.PROP_OLD_KEYS)) : new CaseInsensitiveHashMap();
                    oldKeys.add(oldMap);
                }
                List<Map<String, Object>> updatedRows = qus.updateRows(user, container, newRows, oldKeys, extraContext);
                updatedRows = qus.getRows(user, container, updatedRows);
                List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
                for (int i = 0; i < updatedRows.size(); i++)
                {
                    Map<String, Object> result = new HashMap<String, Object>();
                    result.put(SaveRowsAction.PROP_VALUES, updatedRows.get(i));
                    result.put(SaveRowsAction.PROP_OLD_KEYS, oldKeys.get(i));
                    results.add(result);
                }
                return results;
            }
        },
        delete(DeletePermission.class)
        {
            @Override
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException
            {
                return qus.deleteRows(user, container, rows, extraContext);
            }
        };

        private final Class<? extends Permission> _permission;

        private CommandType(Class<? extends Permission> permission)
        {
            _permission = permission;
        }

        public Class<? extends Permission> getPermission()
        {
            return _permission;
        }

        public abstract List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<String, Object> extraContext)
                throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException;
    }

    /**
     * Base action class for insert/update/delete actions
     */
    public abstract class BaseSaveRowsAction extends ApiAction<ApiSaveRowsForm>
    {
        public static final String PROP_SCHEMA_NAME = "schemaName";
        public static final String PROP_QUERY_NAME = "queryName";
        public static final String PROP_COMMAND = "command";
        private static final String PROP_ROWS = "rows";

        protected JSONObject executeJson(JSONObject json, CommandType commandType, boolean allowTransaction, Errors errors) throws Exception
        {
            JSONObject response = new JSONObject();
            Container container = getViewContext().getContainer();
            User user = getViewContext().getUser();

            if (json == null)
                throw new IllegalArgumentException("Empty request");

            JSONArray rows = json.getJSONArray(PROP_ROWS);
            if (null == rows || rows.length() < 1)
                throw new IllegalArgumentException("No 'rows' array supplied!");

            String schemaName = json.getString(PROP_SCHEMA_NAME);
            String queryName = json.getString(PROP_QUERY_NAME);
            TableInfo table = getTableInfo(container, user, schemaName, queryName);

            if (!table.hasPermission(user, commandType.getPermission()))
                throw new UnauthorizedException();

            if (table.getPkColumns().size() == 0)
                throw new IllegalArgumentException("The table '" + table.getPublicSchemaName() + "." +
                        table.getPublicName() + "' cannot be updated because it has no primary key defined!");

            QueryUpdateService qus = table.getUpdateService();
            if (null == qus)
                throw new IllegalArgumentException("The query '" + queryName + "' in the schema '" + schemaName +
                        "' is not updatable via the HTTP-based APIs.");

            int rowsAffected = 0;

            List<Map<String, Object>> rowsToProcess = new ArrayList<Map<String, Object>>();

            // NOTE RowMapFactory is faster, but for update it's important to preserve missing v explicit NULL values
            // Do we need to support some soft of UNDEFINED and NULL instance of MvFieldWrapper?
            RowMapFactory f = null;
            if (commandType == CommandType.insert || commandType == CommandType.insertWithKeys)
                f = new RowMapFactory();

            for (int idx = 0; idx < rows.length(); ++idx)
            {
                JSONObject jsonObj;
                try
                {
                    jsonObj = rows.getJSONObject(idx);
                }
                catch (JSONException x)
                {
                    throw new IllegalArgumentException("rows[" + idx + "] is not an object.");
                }
                if (null != jsonObj)
                {
                    Map<String,Object> rowMap = null == f ? new CaseInsensitiveHashMap<Object>() : f.getRowMap();
                    rowMap.putAll(jsonObj);
                    rowsToProcess.add(rowMap);
                    rowsAffected++;
                }
            }

            Map<String, Object> extraContext = json.optJSONObject("extraContext");

            //we will transact operations by default, but the user may
            //override this by sending a "transacted" property set to false
            // 11741: A transaction may already be active if we're trying to
            // insert/update/delete from within a transformation/validation script.
            boolean transacted = allowTransaction && json.optBoolean("transacted", true);
            if (transacted)
                table.getSchema().getScope().ensureTransaction();

            //setup the response, providing the schema name, query name, and operation
            //so that the client can sort out which request this response belongs to
            //(clients often submit these async)
            response.put(PROP_SCHEMA_NAME, schemaName);
            response.put(PROP_QUERY_NAME, queryName);
            response.put("command", commandType.name());
            response.put("containerPath", container.getPath());

            try
            {
                List<Map<String, Object>> responseRows =
                        commandType.saveRows(qus, rowsToProcess, getViewContext().getUser(), getViewContext().getContainer(), extraContext);

                response.put("rows", responseRows);

                if (transacted)
                    table.getSchema().getScope().commitTransaction();
            }
            catch (Table.OptimisticConflictException e)
            {
                //issue 13967: provide better message for OptimisticConflictException
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            }
            catch (ConversionException e)
            {
                //Issue 14294: improve handling of ConversionException
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage() == null ? e.toString() : e.getMessage());
            }
            finally
            {
                if (transacted)
                    table.getSchema().getScope().closeConnection();
            }

            response.put("rowsAffected", rowsAffected);

            return response;
        }

        @NotNull
        protected TableInfo getTableInfo(Container container, User user, String schemaName, String queryName)
        {
            if (null == schemaName || null == queryName)
                throw new IllegalArgumentException("You must supply a schemaName and queryName!");

            UserSchema schema = QueryService.get().getUserSchema(user, container, schemaName);
            if (null == schema)
                throw new IllegalArgumentException("The schema '" + schemaName + "' does not exist.");

            TableInfo table = schema.getTable(queryName);
            if (table == null)
                throw new IllegalArgumentException("The query '" + queryName + "' in the schema '" + schemaName + "' does not exist.");
            return table;
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    @ApiVersion(8.3)
    public class UpdateRowsAction extends BaseSaveRowsAction
    {
        @Override
        public ApiResponse execute(ApiSaveRowsForm apiSaveRowsForm, BindException errors) throws Exception
        {
            JSONObject response = executeJson(apiSaveRowsForm.getJsonObject(), CommandType.update, true, errors);
            if (response == null || errors.hasErrors())
                return null;
            return new ApiSimpleResponse(response);
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    @ApiVersion(8.3)
    public class InsertRowsAction extends BaseSaveRowsAction
    {
        @Override
        public ApiResponse execute(ApiSaveRowsForm apiSaveRowsForm, BindException errors) throws Exception
        {
            JSONObject response = executeJson(apiSaveRowsForm.getJsonObject(), CommandType.insert, true, errors);
            if (response == null || errors.hasErrors())
                return null;
            return new ApiSimpleResponse(response);
        }
    }

    @ActionNames("deleteRows, delRows")
    @RequiresPermissionClass(DeletePermission.class)
    @ApiVersion(8.3)
    public class DeleteRowsAction extends BaseSaveRowsAction
    {
        @Override
        public ApiResponse execute(ApiSaveRowsForm apiSaveRowsForm, BindException errors) throws Exception
        {
            JSONObject response = executeJson(apiSaveRowsForm.getJsonObject(), CommandType.delete, true, errors);
            if (response == null || errors.hasErrors())
                return null;
            return new ApiSimpleResponse(response);
        }
    }

    @RequiresNoPermission //will check below
    public class SaveRowsAction extends BaseSaveRowsAction
    {
        public static final String PROP_VALUES = "values";
        public static final String PROP_OLD_KEYS = "oldKeys";

        @Override
        public ApiResponse execute(ApiSaveRowsForm apiSaveRowsForm, BindException errors) throws Exception
        {
            JSONObject json = apiSaveRowsForm.getJsonObject();
            if (json == null)
                throw new IllegalArgumentException("Empty request");

            JSONArray commands = (JSONArray)json.get("commands");
            JSONArray result = new JSONArray();
            if (commands == null || commands.length() == 0)
            {
                throw new NotFoundException("Empty request");
            }

            Map<String, Object> extraContext = json.optJSONObject("extraContext");

            boolean transacted = json.optBoolean("transacted", true);
            DbScope scope = null;
            if (transacted)
            {
                for (int i = 0; i < commands.length(); i++)
                {
                    JSONObject commandJSON = commands.getJSONObject(i);
                    String schemaName = commandJSON.getString(PROP_SCHEMA_NAME);
                    String queryName = commandJSON.getString(PROP_QUERY_NAME);
                    TableInfo tableInfo = getTableInfo(getContainer(), getUser(), schemaName, queryName);
                    if (scope == null)
                    {
                        scope = tableInfo.getSchema().getScope();
                    }
                    else if (scope != tableInfo.getSchema().getScope())
                    {
                        throw new IllegalArgumentException("All queries must be from the same source database");
                    }
                }

                // 11741: A transaction may already be active if we're trying to
                // insert/update/delete from within a transformation/validation script.
                scope.ensureTransaction();
            }

            try
            {
                for (int i = 0; i < commands.length(); i++)
                {
                    JSONObject commandObject = commands.getJSONObject(i);
                    String commandName = commandObject.getString(PROP_COMMAND);
                    if (commandName == null)
                    {
                        throw new ApiUsageException(PROP_COMMAND + " is required but was missing");
                    }
                    CommandType command = CommandType.valueOf(commandName);

                    // Copy the top-level 'extraContext' and merge in the command-level extraContext.
                    Map<String, Object> commandExtraContext = new HashMap<String, Object>();
                    if (extraContext != null)
                        commandExtraContext.putAll(extraContext);
                    if (commandObject.has("extraContext"))
                    {
                        commandExtraContext.putAll(commandObject.getJSONObject("extraContext"));
                    }
                    commandObject.put("extraContext", commandExtraContext);

                    JSONObject commandResponse = executeJson(commandObject, command, !transacted, errors);
                    if (commandResponse == null || errors.hasErrors())
                        return null;
                    result.put(commandResponse);
                }
                if (transacted)
                {
                    scope.commitTransaction();
                }
            }
            finally
            {
                if (transacted)
                {
                    scope.closeConnection();
                }
            }
            return new ApiSimpleResponse("result", result);
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ApiTestAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/query/controllers/apitest.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("API Test");
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class AdminAction extends SimpleViewAction<QueryForm>
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
           setHelpTopic(new HelpTopic("externalSchemas"));
           return new JspView<QueryForm>(getClass(), "admin.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root);
            root.addChild("Schema Administration", new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer()));
            return root;
        }
    }

    private abstract class BaseInsertExternalSchemaAction<F extends AbstractExternalSchemaForm<T>, T extends AbstractExternalSchemaDef> extends FormViewAction<F>
    {
        protected BaseInsertExternalSchemaAction(Class<F> commandClass)
        {
            super(commandClass);
        }

        public void validateCommand(F form, Errors errors)
        {
            form.validate(errors);
        }

        public boolean handlePost(F form, BindException errors) throws Exception
        {
            try
            {
                form.doInsert();
            }
            catch (SQLException e)
            {
                if (SqlDialect.isConstraintException(e))
                {
                    errors.reject(ERROR_MSG, "A schema by that name is already defined in this folder");
                    return false;
                }

                throw e;
            }

            return true;
        }

        public ActionURL getSuccessURL(F form)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new AdminAction().appendNavTrail(root);
            root.addChild("Define Schema", new ActionURL(getClass(), getContainer()));
            return root;
        }

    }

    @RequiresSiteAdmin
    public class InsertLinkedSchemaAction extends BaseInsertExternalSchemaAction<LinkedSchemaForm, LinkedSchemaDef>
    {
        public InsertLinkedSchemaAction()
        {
            super(LinkedSchemaForm.class);
        }

        public ModelAndView getView(LinkedSchemaForm form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("linkedSchema"));
            return new JspView<LinkedSchemaBean>(QueryController.class, "linkedSchema.jsp", new LinkedSchemaBean(getContainer(), form.getBean(), true), errors);
        }
    }

    @RequiresSiteAdmin
    public class InsertExternalSchemaAction extends BaseInsertExternalSchemaAction<ExternalSchemaForm, ExternalSchemaDef>
    {
        public InsertExternalSchemaAction()
        {
            super(ExternalSchemaForm.class);
        }

        public ModelAndView getView(ExternalSchemaForm form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("externalSchemas"));
            return new JspView<ExternalSchemaBean>(QueryController.class, "externalSchema.jsp", new ExternalSchemaBean(getContainer(), form.getBean(), true), errors);
        }
    }

    private abstract class BaseDeleteSchemaAction<F extends AbstractExternalSchemaForm<T>, T extends AbstractExternalSchemaDef> extends ConfirmAction<F>
    {
        protected BaseDeleteSchemaAction(Class<F> commandClass)
        {
            super(commandClass);
        }

        public String getConfirmText()
        {
            return "Delete";
        }

        public ModelAndView getConfirmView(F form, BindException errors) throws Exception
        {
            form.refreshFromDb();
            return new HtmlView("Are you sure you want to delete the schema '" + form.getBean().getUserSchemaName() + "'? The tables and queries defined in this schema will no longer be accessible.");
        }

        public boolean handlePost(F form, BindException errors) throws Exception
        {
            delete(form);
            return true;
        }

        protected abstract void delete(F form) throws Exception;

        public void validateCommand(F form, Errors errors)
        {
        }

        public ActionURL getSuccessURL(F form)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer());
        }
    }

    @RequiresSiteAdmin
    public class DeleteLinkedSchemaAction extends BaseDeleteSchemaAction<LinkedSchemaForm, LinkedSchemaDef>
    {
        public DeleteLinkedSchemaAction()
        {
            super(LinkedSchemaForm.class);
        }

        protected void delete(LinkedSchemaForm form) throws Exception
        {
            form.refreshFromDb();
            QueryManager.get().delete(getUser(), form.getBean());
        }
    }

    @RequiresSiteAdmin
    public class DeleteExternalSchemaAction extends BaseDeleteSchemaAction<ExternalSchemaForm, ExternalSchemaDef>
    {
        public DeleteExternalSchemaAction()
        {
            super(ExternalSchemaForm.class);
        }

        protected void delete(ExternalSchemaForm form) throws Exception
        {
            form.refreshFromDb();
            QueryManager.get().delete(getUser(), form.getBean());
        }
    }

    private abstract class BaseEditSchemaAction<F extends AbstractExternalSchemaForm<T>, T extends AbstractExternalSchemaDef> extends FormViewAction<F>
    {
        protected BaseEditSchemaAction(Class<F> commandClass)
        {
            super(commandClass);
        }

        public void validateCommand(F form, Errors errors)
        {
            form.validate(errors);
        }

        protected abstract T getFromDb(int externalSchemaId);

        protected T getDef(F form, boolean reshow, BindException errors) throws Exception
        {
            T def;
            Container defContainer;

            if (reshow)
            {
                def = form.getBean();
                T fromDb = getFromDb(def.getExternalSchemaId());
                defContainer = fromDb.lookupContainer();
            }
            else
            {
                form.refreshFromDb();
                def = form.getBean();
                defContainer = def.lookupContainer();
            }

            if (!getContainer().equals(defContainer))
                throw new UnauthorizedException();

            return def;
        }

        public boolean handlePost(F form, BindException errors) throws Exception
        {
            T def = form.getBean();
            T fromDb = getFromDb(def.getExternalSchemaId());

            // Unauthorized if def in the database reports a different container
            if (!getContainer().equals(fromDb.lookupContainer()))
                throw new UnauthorizedException();

            try
            {
                form.doUpdate();
                ExternalSchemaDocumentProvider.getInstance().enumerateDocuments(null, getContainer(), null);
            }
            catch (SQLException e)
            {
                if (SqlDialect.isConstraintException(e))
                {
                    errors.reject(ERROR_MSG, "A schema by that name is already defined in this folder");
                    return false;
                }

                throw e;
            }
            return true;
        }

        public ActionURL getSuccessURL(F externalSchemaForm)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new AdminAction().appendNavTrail(root);
            root.addChild("Edit Schema", new ActionURL(getClass(), getContainer()));
            return root;
        }
    }

    @RequiresSiteAdmin
    public class EditLinkedSchemaAction extends BaseEditSchemaAction<LinkedSchemaForm, LinkedSchemaDef>
    {
        public EditLinkedSchemaAction()
        {
            super(LinkedSchemaForm.class);
        }

        protected LinkedSchemaDef getFromDb(int externalId)
        {
            return QueryManager.get().getLinkedSchemaDef(externalId);
        }

        public ModelAndView getView(LinkedSchemaForm form, boolean reshow, BindException errors) throws Exception
        {
            LinkedSchemaDef def = getDef(form, reshow, errors);

            setHelpTopic(new HelpTopic("linkedSchemas"));
            return new JspView<LinkedSchemaBean>(QueryController.class, "linkedSchema.jsp", new LinkedSchemaBean(getContainer(), def, false), errors);
        }
    }

    @RequiresSiteAdmin
    public class EditExternalSchemaAction extends BaseEditSchemaAction<ExternalSchemaForm, ExternalSchemaDef>
    {
        public EditExternalSchemaAction()
        {
            super(ExternalSchemaForm.class);
        }

        protected ExternalSchemaDef getFromDb(int externalId)
        {
            return QueryManager.get().getExternalSchemaDef(externalId);
        }

        public ModelAndView getView(ExternalSchemaForm form, boolean reshow, BindException errors) throws Exception
        {
            ExternalSchemaDef def = getDef(form, reshow, errors);

            setHelpTopic(new HelpTopic("externalSchemas"));
            return new JspView<ExternalSchemaBean>(QueryController.class, "externalSchema.jsp", new ExternalSchemaBean(getContainer(), def, false), errors);
        }
    }


    @RequiresSiteAdmin  // TODO: NYI
    public class InsertMultipleExternalSchemasAction extends FormViewAction<ExternalSchemaForm>
    {
        public void validateCommand(ExternalSchemaForm form, Errors errors)
        {
			form.validate(errors);
        }

        public ModelAndView getView(ExternalSchemaForm form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("externalSchemas"));
            return new JspView<ExternalSchemaBean>(QueryController.class, "multipleSchemas.jsp", new ExternalSchemaBean(getContainer(), form.getBean(), true), errors);
        }

        public boolean handlePost(ExternalSchemaForm form, BindException errors) throws Exception
        {
            try
            {
                form.doInsert();
            }
            catch (SQLException e)
            {
                if (SqlDialect.isConstraintException(e))
                {
                    errors.reject(ERROR_MSG, "A schema by that name is already defined in this folder");
                    return false;
                }

                throw e;
            }

            return true;
        }

        public ActionURL getSuccessURL(ExternalSchemaForm externalSchemaForm)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new AdminAction().appendNavTrail(root);
            root.addChild("Define Multiple Schemas", new ActionURL(QueryController.InsertMultipleExternalSchemasAction.class, getContainer()));
            return root;
        }
    }

    public static class DataSourceInfo
    {
        public String sourceName;
        public String displayName;
        public boolean editable;

        public DataSourceInfo(DbScope scope)
        {
            this(scope.getDataSourceName(), scope.getDisplayName(), scope.getSqlDialect().isEditable());
        }

        public DataSourceInfo(Container c)
        {
            this(c.getId(), c.getName(), false);
        }

        public DataSourceInfo(String sourceName, String displayName, boolean editable)
        {
            this.sourceName = sourceName;
            this.displayName = displayName;
            this.editable = editable;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DataSourceInfo that = (DataSourceInfo) o;
            if (sourceName != null ? !sourceName.equals(that.sourceName) : that.sourceName != null) return false;
            return true;
        }

        @Override
        public int hashCode()
        {
            return sourceName != null ? sourceName.hashCode() : 0;
        }
    }

    public static abstract class BaseExternalSchemaBean<T extends AbstractExternalSchemaDef>
    {
        protected final Container _c;
        protected final T _def;
        protected final boolean _insert;
        protected final Map<String, String> _help = new HashMap<String, String>();

        protected final Map<DataSourceInfo, Collection<String>> _sourcesAndSchemas = new LinkedHashMap<DataSourceInfo, Collection<String>>();
        protected final Map<DataSourceInfo, Collection<String>> _sourcesAndSchemasIncludingSystem = new LinkedHashMap<DataSourceInfo, Collection<String>>();

        public BaseExternalSchemaBean(Container c, T def, boolean insert)
        {
            _c = c;
            _def = def;
            _insert = insert;

            TableInfo ti = QueryManager.get().getTableInfoExternalSchema();

            for (ColumnInfo ci : ti.getColumns())
                if (null != ci.getDescription())
                    _help.put(ci.getName(), ci.getDescription());

            initSources();
        }

        protected abstract void initSources();

        public abstract DataSourceInfo getInitialSource();

        public Collection<DataSourceInfo> getSources()
        {
            return _sourcesAndSchemas.keySet();
        }

        public Collection<String> getSchemaNames(DataSourceInfo source, boolean includeSystem)
        {
            if (includeSystem)
                return _sourcesAndSchemasIncludingSystem.get(source);
            else
                return _sourcesAndSchemas.get(source);
        }

        public T getSchemaDef()
        {
            return _def;
        }

        public boolean isInsert()
        {
            return _insert;
        }

        public ActionURL getReturnURL()
        {
            return new ActionURL(AdminAction.class, _c);
        }

        public ActionURL getDeleteURL()
        {
            return new QueryUrlsImpl().urlDeleteExternalSchema(_c, _def);
        }

        public String getHelpHTML(String fieldName)
        {
            return _help.get(fieldName);
        }

    }

    public static class LinkedSchemaBean extends BaseExternalSchemaBean<LinkedSchemaDef>
    {
        public LinkedSchemaBean(Container c, LinkedSchemaDef def, boolean insert)
        {
            super(c, def, insert);
        }

        public DataSourceInfo getInitialSource()
        {
            Container sourceContainer = getInitialContainer();
            return new DataSourceInfo(sourceContainer);
        }

        private @NotNull Container getInitialContainer()
        {
            LinkedSchemaDef def = getSchemaDef();
            Container sourceContainer = def.lookupSourceContainer();
            if (sourceContainer == null)
                sourceContainer = def.lookupContainer();
            if (sourceContainer == null)
                sourceContainer = _c;
            return sourceContainer;
        }

        protected void initSources()
        {
            User user = HttpView.currentContext().getUser();
            MultiMap<Container, Container> containerTree = ContainerManager.getContainerTree(_c.getProject());
            Set<Container> adminContainers = ContainerManager.getContainerSet(containerTree, user, AdminPermission.class);

            SortedSet<Container> sortedContainers = new TreeSet<Container>(new Comparator<Container>() {
                    public int compare(Container o1, Container o2)
                    {
                        return o1.getPath().compareTo(o2.getPath());
                    }
            });
            sortedContainers.addAll(adminContainers);

            // Add initial container if it isn't in the project tree
            sortedContainers.add(getInitialContainer());

            for (Container c : sortedContainers)
            {
                Collection<String> schemaNames = new LinkedList<String>();
                //Collection<String> schemaNamesIncludingSystem = new LinkedList<String>();

                DefaultSchema defaultSchema = DefaultSchema.get(user, c);
                Set<SchemaKey> userSchemas = defaultSchema.getUserSchemaPaths();
                for (SchemaKey key : userSchemas)
                {
                    schemaNames.add(key.toString());
                }

                DataSourceInfo source = new DataSourceInfo(c);
                _sourcesAndSchemas.put(source, schemaNames);
                _sourcesAndSchemasIncludingSystem.put(source, schemaNames);
            }
        }

    }

    public static class ExternalSchemaBean extends BaseExternalSchemaBean<ExternalSchemaDef>
    {
        public ExternalSchemaBean(Container c, ExternalSchemaDef def, boolean insert)
        {
            super(c, def, insert);
        }

        @Override
        public DataSourceInfo getInitialSource()
        {
            ExternalSchemaDef def = getSchemaDef();
            DbScope scope = def.lookupDbScope();
            if (scope == null)
                scope = DbScope.getLabkeyScope();
            return new DataSourceInfo(scope);
        }

        protected void initSources()
        {
            Collection<DbScope> scopes = DbScope.getDbScopes();
            for (DbScope scope : scopes)
            {
                Connection con = null;
                ResultSet rs = null;
                SqlDialect dialect = scope.getSqlDialect();

                try
                {
                    con = scope.getConnection();
                    DatabaseMetaData dbmd = con.getMetaData();

                    if (scope.getSqlDialect().treatCatalogsAsSchemas())
                        rs = dbmd.getCatalogs();
                    else
                        rs = dbmd.getSchemas();

                    Collection<String> schemaNames = new LinkedList<String>();
                    Collection<String> schemaNamesIncludingSystem = new LinkedList<String>();

                    while(rs.next())
                    {
                        String schemaName = rs.getString(1).trim();

                        schemaNamesIncludingSystem.add(schemaName);

                        if (dialect.isSystemSchema(schemaName))
                            continue;

                        if (scope.isModuleSchema(schemaName))
                            continue;

                        schemaNames.add(schemaName);
                    }

                    DataSourceInfo source = new DataSourceInfo(scope);
                    _sourcesAndSchemas.put(source, schemaNames);
                    _sourcesAndSchemasIncludingSystem.put(source, schemaNamesIncludingSystem);
                }
                catch (SQLException e)
                {
                    LOG.error("Exception retrieving schemas from DbScope '" + scope.getDataSourceName() + "'", e);
                }
                finally
                {
                    ResultSetUtil.close(rs);

                    if (null != con)
                    {
                        try
                        {
                            con.close();
                        }
                        catch (SQLException e)
                        {
                            // ignore
                        }
                    }
                }
            }
        }

    }


    public static class GetTablesForm
    {
        private String _dataSource;
        private String _schemaName;

        public String getDataSource()
        {
            return _dataSource;
        }

        public void setDataSource(String dataSource)
        {
            _dataSource = dataSource;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }
    }

    @RequiresSiteAdmin
    public class GetTablesAction extends ApiAction<GetTablesForm>
    {
        @Override
        public ApiResponse execute(GetTablesForm form, BindException errors) throws Exception
        {
            List<Map<String, Object>> rows = new LinkedList<Map<String, Object>>();
            List<String> tableNames = new ArrayList<String>();

            if (null != form.getSchemaName())
            {
                DbScope scope = DbScope.getDbScope(form.getDataSource());
                if (null != scope)
                {
                    DbSchema schema = scope.getSchema(form.getSchemaName());
                    if (null != schema)
                        tableNames.addAll(schema.getTableNames());
                }
                else
                {
                    Container c = ContainerManager.getForId(form.getDataSource());
                    if (null != c)
                    {
                        UserSchema schema = QueryService.get().getUserSchema(getUser(), c, form.getSchemaName());
                        if (null != schema)
                            tableNames.addAll(schema.getTableAndQueryNames(true));
                    }
                }
            }

            Collections.sort(tableNames);

            for (String tableName : tableNames)
            {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("table", tableName);
                rows.add(row);
            }

            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("rows", rows);

            return new ApiSimpleResponse(properties);
        }
    }


    public static class SchemaTemplateForm
    {
        private String _name;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }
    }

    @RequiresSiteAdmin
    public class SchemaTemplateAction extends ApiAction<SchemaTemplateForm>
    {
        @Override
        public ApiResponse execute(SchemaTemplateForm form, BindException errors) throws Exception
        {
            String name = form.getName();
            if (name == null)
                throw new IllegalArgumentException("name required");

            Container c = getContainer();
            TemplateSchemaType template = QueryServiceImpl.get().getSchemaTemplate(c, name);
            if (template == null)
                throw new NotFoundException("template not found");

            JSONObject templateJson = QueryServiceImpl.get().schemaTemplateJson(name, template);

            return new ApiSimpleResponse("template", templateJson);
        }
    }

    @RequiresSiteAdmin
    public class SchemaTemplatesAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            Container c = getContainer();
            QueryServiceImpl svc = QueryServiceImpl.get();
            Map<String, TemplateSchemaType> templates = svc.getSchemaTemplates(c);

            JSONArray ret = new JSONArray();
            for (String key : templates.keySet())
            {
                TemplateSchemaType template = templates.get(key);
                JSONObject templateJson = svc.schemaTemplateJson(key, template);
                ret.put(templateJson);
            }

            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("templates", ret);
            resp.put("success", true);
            return resp;
        }
    }



    // UNDONE: should use POST, change to FormHandlerAction
    @RequiresPermissionClass(AdminPermission.class)
    public class ReloadExternalSchemaAction extends SimpleViewAction<ExternalSchemaForm>
    {
        public ModelAndView getView(ExternalSchemaForm form, BindException errors) throws Exception
        {
            form.refreshFromDb();
            ExternalSchemaDef def = form.getBean();

            try
            {
                QueryManager.get().reloadExternalSchema(def);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "Could not reload schema " + def.getUserSchemaName() + ". The data source for the schema may be unreachable, or the schema may have been deleted.");
                getPageConfig().setTemplate(PageConfig.Template.Dialog);
                return new SimpleErrorView(errors);
            }

            return HttpView.redirect(getSuccessURL(form));
        }

        public ActionURL getSuccessURL(ExternalSchemaForm form)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer(), form.getBean().getUserSchemaName());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ReloadAllUserSchemas extends SimpleRedirectAction
    {
        @Override
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            QueryManager.get().reloadAllExternalSchemas(getContainer());
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer(), "ALL");
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class TableInfoAction extends SimpleViewAction<TableInfoForm>
    {
        public ModelAndView getView(TableInfoForm form, BindException errors) throws Exception
        {
            TablesDocument ret = TablesDocument.Factory.newInstance();
            TablesType tables = ret.addNewTables();

            FieldKey[] fields = form.getFieldKeys();
            if (fields.length != 0)
            {
                TableInfo tinfo = QueryView.create(form, errors).getTable();
                Map<FieldKey, ColumnInfo> columnMap = CustomViewImpl.getColumnInfos(tinfo, Arrays.asList(fields));
                TableXML.initTable(tables.addNewTable(), tinfo, null, columnMap.values());
            }

            for (FieldKey tableKey : form.getTableKeys())
            {
                TableInfo tableInfo = form.getTableInfo(tableKey);
                TableType xbTable = tables.addNewTable();
                TableXML.initTable(xbTable, tableInfo, tableKey);
            }
            getViewContext().getResponse().setContentType("text/xml");
            getViewContext().getResponse().getWriter().write(ret.toString());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    @RequiresPermissionClass(ReadPermission.class) @RequiresLogin
    public class DeleteViewAction extends ApiAction<DeleteViewForm>
    {
        @Override
        protected ModelAndView handleGet() throws Exception
        {
            throw new UnauthorizedException();
        }

        public ApiResponse execute(DeleteViewForm form, BindException errors) throws Exception
        {
            CustomView view = form.getCustomView();
            if (view == null)
            {
                throw new NotFoundException();
            }
            if (view.isShared())
            {
                if (!getViewContext().getContainer().hasPermission(getUser(), EditSharedViewPermission.class))
                    throw new UnauthorizedException();
            }
            view.delete(getUser(), getViewContext().getRequest());

            // Delete the first shadowed custom view, if available.
            if (form.isComplete())
            {
                form.setCustomView(null);
                CustomView shadowed = form.getCustomView();
                if (shadowed != null && shadowed.isEditable())
                {
                    if (!shadowed.isShared() || getViewContext().getContainer().hasPermission(getUser(), EditSharedViewPermission.class))
                        shadowed.delete(getUser(), getViewContext().getRequest());
                }
            }

            // Try to get a custom view of the same name as the view we just deleted.
            // The deleted view may have been a session view or a personal view masking shared view with the same name.
            form.setCustomView(null);
            view = form.getCustomView();
            String nextViewName = null;
            if (view != null)
                nextViewName = view.getName();

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("viewName", nextViewName);
            return response;
        }
    }

    public static class SaveSessionViewForm extends QueryForm
    {
        private String newName;
        private boolean inherit;
        private boolean shared;

        public String getNewName()
        {
            return newName;
        }

        public void setNewName(String newName)
        {
            this.newName = newName;
        }

        public boolean isInherit()
        {
            return inherit;
        }

        public void setInherit(boolean inherit)
        {
            this.inherit = inherit;
        }

        public boolean isShared()
        {
            return shared;
        }

        public void setShared(boolean shared)
        {
            this.shared = shared;
        }
    }

    // Moves a session view into the database.
    @RequiresPermissionClass(ReadPermission.class) @RequiresLogin
    public class SaveSessionViewAction extends ApiAction<SaveSessionViewForm>
    {
        @Override
        public ApiResponse execute(SaveSessionViewForm form, BindException errors) throws Exception
        {
            CustomView view = form.getCustomView();
            if (view == null)
            {
                throw new NotFoundException();
            }
            if (!view.isSession())
                throw new IllegalArgumentException("This action only supports saving session views.");

            if (!getContainer().getId().equals(view.getContainer().getId()))
                throw new IllegalArgumentException("View may only be saved from container it was created in.");

            assert !view.canInherit() && !view.isShared() && view.isEditable(): "Session view should never be inheritable or shared and always be editable";

            if (form.isShared() || form.isInherit())
            {
                if (!getViewContext().getContainer().hasPermission(getUser(), EditSharedViewPermission.class))
                    throw new UnauthorizedException();
            }

            try
            {
                QueryManager.get().getDbSchema().getScope().ensureTransaction();

                // Delete the session view.  The view will be restored if an exception is thrown.
                view.delete(getUser(), getViewContext().getRequest());

                // Get any previously existing non-session view.
                // The session custom view and the view-to-be-saved may have different names.
                // If they do have different names, we may need to delete an existing session view with that name.
                // UNDONE: If the view has a different name, we will clobber it without asking.
                CustomView existingView = form.getQueryDef().getCustomView(getUser(), null, form.getNewName());
                if (existingView != null && existingView.isSession())
                {
                    // Delete any session view we are overwriting.
                    existingView.delete(getUser(), getViewContext().getRequest());
                    existingView = form.getQueryDef().getCustomView(getUser(), null, form.getNewName());
                }

                if (existingView == null)
                {
                    User owner = form.isShared() ? null : getUser();

                    CustomViewImpl viewCopy = new CustomViewImpl(form.getQueryDef(), owner, form.getNewName());
                    viewCopy.setColumns(view.getColumns());
                    viewCopy.setCanInherit(form.isInherit());
                    viewCopy.setFilterAndSort(view.getFilterAndSort());
                    viewCopy.setColumnProperties(view.getColumnProperties());
                    viewCopy.setIsHidden(view.isHidden());

                    viewCopy.save(getUser(), getViewContext().getRequest());
                }
                else if (!existingView.isEditable())
                {
                    throw new IllegalArgumentException("Existing view '" + form.getNewName() + "' is not editable.  You may save this view with a different name.");
                }
                else
                {
                    // UNDONE: changing shared and inherit properties is unimplemented.  Not sure if it makes sense from a usability point of view.
                    existingView.setColumns(view.getColumns());
                    existingView.setFilterAndSort(view.getFilterAndSort());
                    existingView.setColumnProperties(view.getColumnProperties());

                    existingView.save(getUser(), getViewContext().getRequest());
                }

                QueryManager.get().getDbSchema().getScope().commitTransaction();
                return new ApiSimpleResponse("success", true);
            }
            catch (Exception e)
            {
                // dirty the view then save the deleted session view back in session state
                view.setName(view.getName());
                view.save(getUser(), getViewContext().getRequest());
                
                throw e;
            }
            finally
            {
                QueryManager.get().getDbSchema().getScope().closeConnection();
            }
        }
    }

    @RequiresNoPermission
    public class CheckSyntaxAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException bindErrors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);

            String sql = (String)getProperty("sql");
            if (sql == null)
                sql = PageFlowUtil.getStreamContentsAsString(getViewContext().getRequest().getInputStream());
            ErrorsDocument ret = ErrorsDocument.Factory.newInstance();
            org.labkey.query.design.Errors xbErrors = ret.addNewErrors();
            List<QueryParseException> errors = new ArrayList<QueryParseException>();
            try
            {
                (new SqlParser()).parseExpr(sql, errors);
            }
            catch (Throwable t)
            {
                Logger.getLogger(QueryController.class).error("Error", t);
                errors.add(new QueryParseException("Unhandled exception: " + t, null, 0, 0));
            }
            for (QueryParseException e : errors)
            {
                DgMessage msg = xbErrors.addNewError();
                msg.setStringValue(e.getMessage());
                msg.setLine(e.getLine());
            }
            HtmlView view = new HtmlView(ret.toString());
            view.setContentType("text/xml");
            view.setFrame(WebPartView.FrameType.NONE);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ManageViewsAction extends SimpleViewAction<QueryForm>
    {
        QueryForm _form;

        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            _form = form;
            return new JspView<QueryForm>(QueryController.class, "manageViews.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root);
            root.addChild("Manage Views", QueryController.this.getViewContext().getActionURL());
            return root;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class InternalDeleteView extends ConfirmAction<InternalViewForm>
    {
        public ModelAndView getConfirmView(InternalViewForm form, BindException errors) throws Exception
        {
            return new JspView<InternalViewForm>(QueryController.class, "internalDeleteView.jsp", form, errors);
        }

        public boolean handlePost(InternalViewForm form, BindException errors) throws Exception
        {
            CstmView view = form.getViewAndCheckPermission();
            QueryManager.get().delete(getUser(), view);
            return true;
        }

        public void validateCommand(InternalViewForm internalViewForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(InternalViewForm internalViewForm)
        {
            return new ActionURL(ManageViewsAction.class, getContainer());
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class InternalSourceViewAction extends FormViewAction<InternalSourceViewForm>
    {
        public void validateCommand(InternalSourceViewForm target, Errors errors)
        {
        }

        public ModelAndView getView(InternalSourceViewForm form, boolean reshow, BindException errors) throws Exception
        {
            CstmView view = form.getViewAndCheckPermission();
            form.ff_inherit = QueryManager.get().canInherit(view.getFlags());
            form.ff_hidden = QueryManager.get().isHidden(view.getFlags());
            form.ff_columnList = view.getColumns();
            form.ff_filter = view.getFilter();
            return new JspView<InternalSourceViewForm>(QueryController.class, "internalSourceView.jsp", form, errors);
        }

        public boolean handlePost(InternalSourceViewForm form, BindException errors) throws Exception
        {
            CstmView view = form.getViewAndCheckPermission();
            int flags = view.getFlags();
            flags = QueryManager.get().setCanInherit(flags, form.ff_inherit);
            flags = QueryManager.get().setIsHidden(flags, form.ff_hidden);
            view.setFlags(flags);
            view.setColumns(form.ff_columnList);
            view.setFilter(form.ff_filter);
            QueryManager.get().update(getUser(), view);
            return true;
        }

        public ActionURL getSuccessURL(InternalSourceViewForm form)
        {
            return new ActionURL(ManageViewsAction.class, getViewContext().getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new ManageViewsAction().appendNavTrail(root);
            root.addChild("Edit source of Grid View");
            return root;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class InternalNewViewAction extends FormViewAction<InternalNewViewForm>
    {
        int _customViewId = 0;

        public void validateCommand(InternalNewViewForm form, Errors errors)
        {
            if (StringUtils.trimToNull(form.ff_schemaName) == null)
            {
                errors.reject(ERROR_MSG, "Schema name cannot be blank.");
            }
            if (StringUtils.trimToNull(form.ff_queryName) == null)
            {
                errors.reject(ERROR_MSG, "Query name cannot be blank");
            }
        }

        public ModelAndView getView(InternalNewViewForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<InternalNewViewForm>(QueryController.class, "internalNewView.jsp", form, errors);
        }

        public boolean handlePost(InternalNewViewForm form, BindException errors) throws Exception
        {
            if (form.ff_share)
            {
                if (!getContainer().hasPermission(getUser(), AdminPermission.class))
                    throw new UnauthorizedException();
            }
            CstmView[] existing = QueryManager.get().getCstmViews(getContainer(), form.ff_schemaName, form.ff_queryName, form.ff_viewName, form.ff_share ? null : getUser(), false);
            CstmView view;
            if (existing.length != 0)
            {
                view = existing[0];
            }
            else
            {
                view = new CstmView();
                view.setSchema(form.ff_schemaName);
                view.setQueryName(form.ff_queryName);
                view.setName(form.ff_viewName);
                view.setContainerId(getContainer().getId());
                if (form.ff_share)
                {
                    view.setCustomViewOwner(null);
                }
                else
                {
                    view.setCustomViewOwner(getUser().getUserId());
                }
                if (form.ff_inherit)
                {
                    view.setFlags(QueryManager.get().setCanInherit(view.getFlags(), form.ff_inherit));
                }
                InternalViewForm.checkEdit(getViewContext(), view);
                try
                {
                    view = QueryManager.get().insert(getUser(), view);
                }
                catch (Exception e)
                {
                    Logger.getLogger(QueryController.class).error("Error", e);
                    errors.reject(ERROR_MSG, "An exception occurred: " + e);
                    return false;
                }
                _customViewId = view.getCustomViewId();
            }
            return true;
        }

        public ActionURL getSuccessURL(InternalNewViewForm form)
        {
            ActionURL forward = new ActionURL(InternalSourceViewAction.class, getContainer());
            forward.addParameter("customViewId", Integer.toString(_customViewId));
            return forward;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            //new ManageViewsAction().appendNavTrail(root);
            root.addChild("Create New Grid View");
            return root;
        }
    }


    @ActionNames("clearSelected, selectNone")
    @RequiresPermissionClass(ReadPermission.class)
    public static class SelectNoneAction extends ApiAction<SelectForm>
    {
        public SelectNoneAction()
        {
            super(SelectForm.class);
        }

        public ApiResponse execute(final SelectForm form, BindException errors) throws Exception
        {
            DataRegionSelection.clearAll(getViewContext(), form.getKey());
            return new SelectionResponse(0);
        }
    }

    public static class SelectForm
    {
        protected String key;

        public String getKey()
        {
            return key;
        }

        public void setKey(String key)
        {
            this.key = key;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public static class SelectAllAction extends ApiAction<SelectAllForm>
    {
        public SelectAllAction()
        {
            super(SelectAllForm.class);
        }

        public void validateForm(SelectAllForm form, Errors errors)
        {
            if (form.getSchemaName().isEmpty() ||
                form.getQueryName() == null)
            {
                errors.reject(ERROR_MSG, "schemaName and queryName required");
            }
        }

        public ApiResponse execute(final SelectAllForm form, BindException errors) throws Exception
        {
            int count = DataRegionSelection.selectAll(
                    getViewContext(), form.getKey(), form);
            return new SelectionResponse(count);
        }
    }

    public static class SelectAllForm extends QueryForm
    {
        protected String key;

        public String getKey()
        {
            return key;
        }

        public void setKey(String key)
        {
            this.key = key;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public static class GetSelectedAction extends ApiAction<SelectForm>
    {
        public GetSelectedAction()
        {
            super(SelectForm.class);
        }

        public ApiResponse execute(final SelectForm form, BindException errors) throws Exception
        {
            Set<String> selected = DataRegionSelection.getSelected(
                    getViewContext(), form.getKey(), true, false);
            return new ApiSimpleResponse("selected", selected);
        }
    }

    @ActionNames("setSelected, setCheck")
    @RequiresPermissionClass(ReadPermission.class)
    public static class SetCheckAction extends ApiAction<SetCheckForm>
    {
        public SetCheckAction()
        {
            super(SetCheckForm.class);
        }

        public ApiResponse execute(final SetCheckForm form, BindException errors) throws Exception
        {
            String[] ids = form.getId(getViewContext().getRequest());
            List<String> selection = new ArrayList<String>();
            if (ids != null)
            {
                for (String id : ids)
                {
                    if (StringUtils.isNotBlank(id))
                        selection.add(id);
                }
            }

            int count = DataRegionSelection.setSelected(
                    getViewContext(), form.getKey(),
                    selection, form.isChecked());
            return new SelectionResponse(count);
        }
    }

    public static class SetCheckForm extends SelectForm
    {
        protected String[] ids;
        protected boolean checked;

        public String[] getId(HttpServletRequest request)
        {
            // 5025 : DataRegion checkbox names may contain comma
            // Beehive parses a single parameter value with commas into an array
            // which is not what we want.
            return request.getParameterValues("id");
        }

        public void setId(String[] ids)
        {
            this.ids = ids;
        }

        public boolean isChecked()
        {
            return checked;
        }

        public void setChecked(boolean checked)
        {
            this.checked = checked;
        }
    }

    public static class SelectionResponse extends ApiSimpleResponse
    {
        public SelectionResponse(int count)
        {
            super("count", count);
        }
    }

    public static String getMessage(SqlDialect d, SQLException x)
    {
        return x.getMessage();
    }

    public static class GetSchemasForm
    {
        private SchemaKey _schemaName;

        public SchemaKey getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(SchemaKey schemaName)
        {
            _schemaName = schemaName;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(12.3)
    public class GetSchemasAction extends ApiAction<GetSchemasForm>
    {
        public ApiResponse execute(GetSchemasForm form, BindException errors) throws Exception
        {
            final Container container = getViewContext().getContainer();
            final User user = getViewContext().getUser();

            if (getRequestedApiVersion() >= 9.3)
            {
                SimpleSchemaTreeVisitor visitor = new SimpleSchemaTreeVisitor<Void, JSONObject>()
                {
                    @Override
                    public Void visitUserSchema(UserSchema schema, Path path, JSONObject json)
                    {
                        JSONObject schemaProps = new JSONObject();

                        schemaProps.put("schemaName", schema.getName());
                        schemaProps.put("fullyQualifiedName", schema.getSchemaName());
                        schemaProps.put("description", schema.getDescription());
                        NavTree tree = schema.getSchemaBrowserLinks(user);
                        if (tree != null && tree.hasChildren())
                            schemaProps.put("menu", tree.toJSON());

                        // Collect children schemas
                        JSONObject children = new JSONObject();
                        visit(schema.getSchemas(), path, children);
                        if (children.size() > 0)
                            schemaProps.put("schemas", children);

                        // Add node's schemaProps to the parent's json.
                        json.put(schema.getName(), schemaProps);
                        return null;
                    }
                };

                // By default, start from the root.
                QuerySchema schema;
                if (form.getSchemaName() != null)
                    schema = DefaultSchema.get(user, container, form.getSchemaName());
                else
                    schema = DefaultSchema.get(user, container);

                // Create the JSON response by visiting the schema children.  The parent schema information isn't included.
                JSONObject ret = new JSONObject();
                visitor.visitTop(schema.getSchemas(), ret);

                ApiSimpleResponse resp = new ApiSimpleResponse();
                resp.putAll(ret);
                return resp;
            }
            else
            {
                return new ApiSimpleResponse("schemas", DefaultSchema.get(user, container).getUserSchemaPaths());
            }
        }
    }

    public static class GetQueriesForm
    {
        private String _schemaName;
        private boolean _includeUserQueries = true;
        private boolean _includeSystemQueries = true;
        private boolean _includeColumns = true;

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public boolean isIncludeUserQueries()
        {
            return _includeUserQueries;
        }

        public void setIncludeUserQueries(boolean includeUserQueries)
        {
            _includeUserQueries = includeUserQueries;
        }

        public boolean isIncludeSystemQueries()
        {
            return _includeSystemQueries;
        }

        public void setIncludeSystemQueries(boolean includeSystemQueries)
        {
            _includeSystemQueries = includeSystemQueries;
        }

        public boolean isIncludeColumns()
        {
            return _includeColumns;
        }

        public void setIncludeColumns(boolean includeColumns)
        {
            _includeColumns = includeColumns;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetQueriesAction extends ApiAction<GetQueriesForm>
    {
        public ApiResponse execute(GetQueriesForm form, BindException errors) throws Exception
        {
            if (null == StringUtils.trimToNull(form.getSchemaName()))
                throw new IllegalArgumentException("You must supply a value for the 'schemaName' parameter!");

            ApiSimpleResponse response = new ApiSimpleResponse();
            UserSchema uschema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), form.getSchemaName());
            if (null == uschema)
                throw new NotFoundException("The schema name '" + form.getSchemaName()
                        + "' was not found within the folder '" + getViewContext().getContainer().getPath() + "'");

            response.put("schemaName", form.getSchemaName());

            List<Map<String, Object>> qinfos = new ArrayList<Map<String, Object>>();

            //user-defined queries
            if (form.isIncludeUserQueries())
            {
                Map<String,QueryDefinition> queryDefMap = uschema.getQueryDefs();
                for (Map.Entry<String,QueryDefinition> entry : queryDefMap.entrySet())
                {
                    QueryDefinition qdef = entry.getValue();
                    if (!qdef.isTemporary())
                    {
                        ActionURL viewDataUrl = uschema.urlFor(QueryAction.executeQuery, qdef);
                        qinfos.add(getQueryProps(qdef.getName(), qdef.getDescription(), viewDataUrl, true, uschema, form.isIncludeColumns()));
                    }
                }
            }

            //built-in tables
            if (form.isIncludeSystemQueries())
            {
                for (String qname : uschema.getVisibleTableNames())
                {
                    // Go direct against the UserSchema instead of calling into QueryService, which takes a schema and
                    // query name as strings and therefore has to create new instances
                    QueryDefinition qdef = uschema.getQueryDefForTable(qname);
                    if (qdef != null)
                    {
                        ActionURL viewDataUrl = uschema.urlFor(QueryAction.executeQuery, qdef);
                        qinfos.add(getQueryProps(qname, qdef.getDescription(), viewDataUrl, false, uschema, form.isIncludeColumns()));
                    }
                }
            }
            response.put("queries", qinfos);

            return response;
        }

        protected Map<String, Object> getQueryProps(String name, String description, ActionURL viewDataUrl, boolean isUserDefined, UserSchema schema, boolean includeColumns)
        {
            Map<String, Object> qinfo = new HashMap<String, Object>();
            qinfo.put("name", name);
            qinfo.put("isUserDefined", isUserDefined);
            if (null != description)
                qinfo.put("description", description);
            if (viewDataUrl != null)
                qinfo.put("viewDataUrl", viewDataUrl);

            if (includeColumns)
            {
                try
                {
                    //get the table info if the user requested column info
                    TableInfo table = schema.getTable(name);

                    if (null != table)
                    {
                        //enumerate the columns
                        List<Map<String, Object>> cinfos = new ArrayList<Map<String, Object>>();
                        for(ColumnInfo col : table.getColumns())
                        {
                            Map<String, Object> cinfo = new HashMap<String, Object>();
                            cinfo.put("name", col.getName());
                            if (null != col.getLabel())
                                cinfo.put("caption", col.getLabel());
                            if (null != col.getShortLabel())
                                cinfo.put("shortCaption", col.getShortLabel());
                            if (null != col.getDescription())
                                cinfo.put("description", col.getDescription());

                            cinfos.add(cinfo);
                        }
                        if (cinfos.size() > 0)
                            qinfo.put("columns", cinfos);
                    }
                }
                catch(Exception e)
                {
                    //may happen due to query failing parse
                }
            }

            return qinfo;
        }
    }

    public static class GetQueryViewsForm
    {
        private String _schemaName;
        private String _queryName;
        private String _viewName;
        private boolean _metadata;

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public String getViewName()
        {
            return _viewName;
        }

        public void setViewName(String viewName)
        {
            _viewName = viewName;
        }

        public boolean isMetadata()
        {
            return _metadata;
        }

        public void setMetadata(boolean metadata)
        {
            _metadata = metadata;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetQueryViewsAction extends ApiAction<GetQueryViewsForm>
    {
        public ApiResponse execute(GetQueryViewsForm form, BindException errors) throws Exception
        {
            if (null == StringUtils.trimToNull(form.getSchemaName()))
                throw new IllegalArgumentException("You must pass a value for the 'schemaName' parameter!");
            if (null == StringUtils.trimToNull(form.getQueryName()))
                throw new IllegalArgumentException("You must pass a value for the 'queryName' parameter!");

            UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), form.getSchemaName());
            if (null == schema)
                throw new NotFoundException("The schema name '" + form.getSchemaName()
                        + "' was not found within the folder '" + getViewContext().getContainer().getPath() + "'");

            QueryDefinition querydef = QueryService.get().createQueryDefForTable(schema, form.getQueryName());
            if (null == querydef || querydef.getTable(null, true) == null)
                throw new NotFoundException("The query '" + form.getQueryName() + "' was not found within the '"
                        + form.getSchemaName() + "' schema in the container '"
                        + getViewContext().getContainer().getPath() + "'!");

            Map<String, CustomView> views = querydef.getCustomViews(getViewContext().getUser(), getViewContext().getRequest(), true);
            if (null == views)
                views = Collections.emptyMap();

            Map<FieldKey, Map<String, Object>> columnMetadata = new HashMap<FieldKey, Map<String, Object>>();

            List<Map<String, Object>> viewInfos = Collections.emptyList();
            if (getViewContext().getBindPropertyValues().contains("viewName"))
            {
                // Get info for a named view or the default view (null)
                String viewName = StringUtils.trimToNull(form.getViewName());
                CustomView view = views.get(viewName);
                if (view != null)
                {
                    viewInfos = Collections.singletonList(CustomViewUtil.toMap(view, getViewContext().getUser(), form.isMetadata()));
                }
                else if (viewName == null)
                {
                    // The default view was requested but it hasn't been customized yet. Create the 'default default' view.
                    viewInfos = Collections.singletonList(CustomViewUtil.toMap(getViewContext(), schema, form.getQueryName(), null, form.isMetadata(), true, columnMetadata));
                }
            }
            else
            {
                boolean foundDefault = false;
                viewInfos = new ArrayList<Map<String, Object>>(views.size());
                for (CustomView view : views.values())
                {
                    if (view.getName() == null)
                        foundDefault = true;
                    viewInfos.add(CustomViewUtil.toMap(view, getViewContext().getUser(), form.isMetadata()));
                }

                if (!foundDefault)
                {
                    // The default view hasn't been customized yet. Create the 'default default' view.
                    viewInfos.add(CustomViewUtil.toMap(getViewContext(), schema, form.getQueryName(), null, form.isMetadata(), true, columnMetadata));
                }
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("schemaName", form.getSchemaName());
            response.put("queryName", form.getQueryName());
            response.put("views", viewInfos);

            return response;
        }

    }

    @RequiresNoPermission
    public class GetServerDateAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            return new ApiSimpleResponse("date", new Date());
        }
    }

    private static class SaveApiTestForm
    {
        private String _getUrl;
        private String _postUrl;
        private String _postData;
        private String _response;

        public String getGetUrl()
        {
            return _getUrl;
        }

        public void setGetUrl(String getUrl)
        {
            _getUrl = getUrl;
        }

        public String getPostUrl()
        {
            return _postUrl;
        }

        public void setPostUrl(String postUrl)
        {
            _postUrl = postUrl;
        }

        public String getResponse()
        {
            return _response;
        }

        public void setResponse(String response)
        {
            _response = response;
        }

        public String getPostData()
        {
            return _postData;
        }

        public void setPostData(String postData)
        {
            _postData = postData;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SaveApiTestAction extends ApiAction<SaveApiTestForm>
    {
        public ApiResponse execute(SaveApiTestForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            ApiTestsDocument doc = ApiTestsDocument.Factory.newInstance();

            TestCaseType test = doc.addNewApiTests().addNewTest();
            test.setName("recorded test case");
            ActionURL url = null;

            if (!StringUtils.isEmpty(form.getGetUrl()))
            {
                test.setType("get");
                url = new ActionURL(form.getGetUrl());
            }
            else if (!StringUtils.isEmpty(form.getPostUrl()))
            {
                test.setType("post");
                test.setFormData(form.getPostData());
                url = new ActionURL(form.getPostUrl());
            }

            if (url != null)
            {
                String uri = url.getLocalURIString();
                if (uri.startsWith(url.getContextPath()))
                    uri = uri.substring(url.getContextPath().length() + 1);

                test.setUrl(uri);
            }
            test.setResponse(form.getResponse());

            XmlOptions opts = new XmlOptions();
            opts.setSaveCDataEntityCountThreshold(0);
            opts.setSaveCDataLengthThreshold(0);
            opts.setSavePrettyPrint();
            opts.setUseDefaultNamespace();

            response.put("xml", doc.xmlText(opts));

            return response;
        }
    }


    private abstract class ParseAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            List<QueryParseException> qpe = new ArrayList<QueryParseException>();
            String expr = getViewContext().getRequest().getParameter("q");
            ArrayList<String> html = new ArrayList<String>();
            html.add("<html><body><form method=GET><textarea cols=100 rows=10 name=q>" + PageFlowUtil.filter(expr) + "</textarea><br><input type=submit onclick='Ext.getBody().mask();'></form>");

            QNode e = null;
            if (null != expr)
            {
                try
                {
                    e = _parse(expr,qpe);
                }
                catch (RuntimeException x)
                {
                    qpe.add(new QueryParseException(x.getMessage(),x, 0, 0));
                }
            }

            Tree tree = null;
            if (null != expr)
            {
                try
                {
                    tree = _tree(expr);
                } catch (Exception x)
                {
                    qpe.add(new QueryParseException(x.getMessage(),x, 0, 0));
                }
            }

            for (Throwable x : qpe)
            {
                if (null != x.getCause() && x != x.getCause())
                    x = x.getCause();
                html.add("<br>" + PageFlowUtil.filter(x.toString()));
                Logger.getLogger(QueryController.class).debug(expr,x);
            }
            if (null != e)
            {
                String prefix = SqlParser.toPrefixString(e);
                html.add("<hr>");
                html.add(PageFlowUtil.filter(prefix));
            }
            if (null != tree)
            {
                String prefix = SqlParser.toPrefixString(tree);
                html.add("<hr>");
                html.add(PageFlowUtil.filter(prefix));
            }
            html.add("</body></html>");
            return new HtmlView(StringUtils.join(html,""));
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        abstract QNode _parse(String e, List<QueryParseException> errors);
        abstract Tree _tree(String e) throws Exception;
    }

    @RequiresSiteAdmin
    public class ParseExpressionAction extends ParseAction
    {
        QNode _parse(String s, List<QueryParseException> errors)
        {
            return new SqlParser().parseExpr(s, errors);
        }

        @Override
        Tree _tree(String e)
        {
            return null;
        }
    }

    @RequiresSiteAdmin
    public class ParseQueryAction extends ParseAction
    {
        QNode _parse(String s, List<QueryParseException> errors)
        {
            return new SqlParser().parseQuery(s, errors);
        }

        @Override
        Tree _tree(String s) throws Exception
        {
            return new SqlParser().rawQuery(s);
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ThumbnailAction extends BaseThumbnailAction
    {
        @Override
        public StaticThumbnailProvider getProvider(Object o) throws Exception
        {
            return new StaticThumbnailProvider()
            {
                @Override
                public Thumbnail getStaticThumbnail()
                {
                    InputStream is = QueryController.class.getResourceAsStream("query.png");
                    return new Thumbnail(is, "image/png");
                }

                @Override
                public String getStaticThumbnailCacheKey()
                {
                    return "Query";
                }
            };
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ValidateQueryMetadataAction extends ApiAction<QueryForm>
    {
        public ApiResponse execute(QueryForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            UserSchema schema = form.getSchema();
            TableInfo table = schema.getTable(form.getQueryName());
            QueryManager.get().validateQuery(table, true);

            SchemaKey schemaKey = SchemaKey.fromString(form.getSchemaName());
            Set<String> queryErrors = QueryManager.get().validateQueryMetadata(schemaKey, form.getQueryName(), getUser(), getContainer());
            queryErrors.addAll(QueryManager.get().validateQueryViews(schemaKey, form.getQueryName(), getUser(), getContainer()));

            for (String e : queryErrors)
            {
                errors.reject(ERROR_MSG, e);
            }
            return response;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class AuditHistoryAction extends SimpleViewAction<QueryForm>
    {
        @Override
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            return QueryUpdateAuditViewFactory.getInstance().createHistoryQueryView(getViewContext(), form);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Audit History");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class AuditDetailsAction extends SimpleViewAction<QueryDetailsForm>
    {
        @Override
        public ModelAndView getView(QueryDetailsForm form, BindException errors) throws Exception
        {
            return QueryUpdateAuditViewFactory.getInstance().createDetailsQueryView(getViewContext(), form);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Audit History");
        }
    }

    public static class QueryDetailsForm extends QueryForm
    {
        String _keyValue;

        public String getKeyValue()
        {
            return _keyValue;
        }

        public void setKeyValue(String keyValue)
        {
            _keyValue = keyValue;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class QueryAuditChangesAction extends SimpleViewAction<AuditChangesForm>
    {
        @Override
        public ModelAndView getView(AuditChangesForm form, BindException errors) throws Exception
        {
            int auditRowId = form.getAuditRowId();
            AuditLogEvent event = AuditLogService.get().getEvent(auditRowId);
            if (event == null)
            {
                throw new NotFoundException("Could not find event " + auditRowId + " to display.");
            }

            Map<String, Object> dataMap = OntologyManager.getProperties(ContainerManager.getSharedContainer(), event.getLsid());
            String oldRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(
                    QueryUpdateAuditViewFactory.QUERY_UPDATE_AUDIT_EVENT,
                    QueryUpdateAuditViewFactory.OLD_RECORD_PROP_NAME));
            String newRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(
                    QueryUpdateAuditViewFactory.QUERY_UPDATE_AUDIT_EVENT,
                    QueryUpdateAuditViewFactory.NEW_RECORD_PROP_NAME));

            if (oldRecord != null || newRecord != null)
            {
                Map<String,String> oldData = QueryAuditViewFactory.decodeFromDataMap(oldRecord);
                Map<String,String> newData = QueryAuditViewFactory.decodeFromDataMap(newRecord);

                return new AuditChangesView(event, oldData, newData);
            }
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Audit Details");
        }
    }

    public static class AuditChangesForm
    {
        private int auditRowId;

        public int getAuditRowId() {return auditRowId;}

        public void setAuditRowId(int auditRowId) {this.auditRowId = auditRowId;}
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ExportTablesAction extends FormViewAction<ExportTablesForm>
    {
        private ActionURL _successUrl;

        @Override
        public void validateCommand(ExportTablesForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ExportTablesForm form, BindException errors)
        {
            HttpServletResponse httpResponse = getViewContext().getResponse();
            Container container = getContainer();
            TableWriter writer = new TableWriter();

            OutputStream outputStream = null;
            try
            {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                outputStream = new BufferedOutputStream(baos);
                ZipFile zip = new ZipFile(outputStream, true);
                writer.write(container, getUser(), zip, form);
                zip.close();

                PageFlowUtil.streamFileBytes(httpResponse, FileUtil.makeFileNameWithTimestamp(container.getName(), "tables.zip"), baos.toByteArray(), false);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
            finally
            {
                IOUtils.closeQuietly(outputStream);
            }
            
            if (errors.hasErrors())
            {
                _successUrl = new ActionURL(ExportTablesAction.class, getContainer());
            }

            return !errors.hasErrors();
        }

        @Override
        public ModelAndView getView(ExportTablesForm form, boolean reshow, BindException errors) throws Exception
        {
            // When exporting the zip to the browser, the base action will attempt to reshow the view since we returned
            // null as the success URL; returning null here causes the base action to stop pestering the action.
            if (reshow && !errors.hasErrors())
                return null;
            
            return new JspView<ExportTablesForm>("/org/labkey/query/controllers/exportTables.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Export Tables");
        }

        @Override
        public ActionURL getSuccessURL(ExportTablesForm form)
        {
            return _successUrl;
        }
    }

    public static class ExportTablesForm implements HasBindParameters
    {
        HashMap<String, ArrayList<String>> _schemas;

        public HashMap<String, ArrayList<String>> getSchemas()
        {
            return _schemas;
        }

        public void setSchemas(HashMap<String, ArrayList<String>> schemas)
        {
            _schemas = schemas;
        }


        public BindException bindParameters(PropertyValues values)
        {
            BindException errors;
            errors = new NullSafeBindException(this, "form");
            _schemas = new HashMap<String, ArrayList<String>>();

            for (PropertyValue value : values.getPropertyValues())
            {
                String[] queries = ((String)value.getValue()).split(";");

                if(queries == null || queries.length == 0)
                {
                    errors.reject(ERROR_MSG, "At least one query must be specified for each schema.");
                }

                _schemas.put(value.getName(), new ArrayList<String>(Arrays.asList(queries)));
            }

            return errors;
        }
    }


    @RequiresSiteAdmin
    public static class Expr extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView(QueryController.class, "expressionEval.jsp", null);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }
}
