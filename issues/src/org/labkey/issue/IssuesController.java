/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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

package org.labkey.issue;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.*;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.*;
import org.labkey.api.security.*;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssueSearch;
import org.labkey.issue.query.IssuesQuerySchema;
import org.labkey.issue.query.IssuesQueryView;
import org.labkey.issue.query.IssuesTable;
import org.springframework.validation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class IssuesController extends SpringActionController
{
    private static Logger _log = Logger.getLogger(IssuesController.class);

    private static String helpTopic = "issues";

    // keywords enum
    public static final int ISSUE_NONE = 0;
    public static final int ISSUE_AREA = 1;
    public static final int ISSUE_TYPE = 2;
    public static final int ISSUE_MILESTONE = 3;
    public static final int ISSUE_STRING1 = 4;
    public static final int ISSUE_STRING2 = 5;
    public static final int ISSUE_PRIORITY = 6;
    public static final int ISSUE_RESOLUTION = 7;


    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(IssuesController.class);

    public IssuesController() throws Exception
    {
        setActionResolver(_actionResolver);
    }


    public static ActionURL getDetailsURL(Container c, Integer issueId, boolean print)
    {
        ActionURL url = new ActionURL(DetailsAction.class, c);

        if (print)
            url.addParameter("_print", "1");

        if (null != issueId)
            url.addParameter("issueId", issueId.toString());

        return url;
    }


    public PageConfig defaultPageConfig()
    {
        PageConfig config = super.defaultPageConfig();
        config.setHelpTopic(new HelpTopic(helpTopic, HelpTopic.Area.SERVER));
        return config;
    }


    private Issue getIssue(int issueId) throws SQLException
    {
        return IssueManager.getIssue(openSession(), getContainer(), issueId);
    }


    private ActionURL issueURL(String action)
    {
        return new ActionURL("issues", action, getContainer());
    }


    public static ActionURL issueURL(Container c, String action)
    {
        return new ActionURL("issues", action, c);
    }


    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(getListURL(getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Issues", getListURL(getContainer()));
        }
    }


    private IssueManager.CustomColumnConfiguration getCustomColumnConfiguration() throws SQLException, ServletException
    {
        return IssueManager.getCustomColumnConfiguration(getContainer());
    }


    private Map<String, String> getColumnCaptions() throws SQLException, ServletException
    {
        return getCustomColumnConfiguration().getColumnCaptions();
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class SetCustomColumnConfigurationAction extends FormHandlerAction
    {
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            IssueManager.CustomColumnConfiguration ccc = new IssueManager.CustomColumnConfiguration(getViewContext());
            IssueManager.saveCustomColumnConfiguration(getContainer(), ccc);
            return true;
        }

        public void validateCommand(Object o, Errors errors)
        {
        }

        public ActionURL getSuccessURL(Object o)
        {
            return (new AdminAction()).getUrl();
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class UpdateRequiredFieldsAction extends FormHandlerAction<IssuePreferenceForm>
    {
        public boolean handlePost(IssuePreferenceForm form, BindException errors) throws Exception
        {
            final StringBuffer sb = new StringBuffer();
            if (form.getRequiredFields().length > 0)
            {
                String sep = "";
                for (HString field : form.getRequiredFields())
                {
                    sb.append(sep);
                    sb.append(field);
                    sep = ";";
                }
            }
            IssueManager.setRequiredIssueFields(getContainer(), sb.toString());
            return true;
        }

        public void validateCommand(IssuePreferenceForm issuePreferenceForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(IssuePreferenceForm issuePreferenceForm)
        {
            return (new AdminAction()).getUrl();
        }
    }


    public static ActionURL getListURL(Container c)
    {
        ActionURL url = new ActionURL(ListAction.class, c);
        url.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
        return url;
    }


    private static final String ISSUES_QUERY = "Issues";
    private HttpView getIssuesView() throws SQLException, ServletException
    {
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), IssuesQuerySchema.SCHEMA_NAME);
        QuerySettings settings = schema.getSettings(getViewContext(), ISSUES_QUERY, ISSUES_QUERY);
        IssuesQueryView queryView = new IssuesQueryView(getViewContext(), schema, settings);

        // add the header for buttons and views
        QueryDefinition qd = schema.getQueryDefForTable(ISSUES_QUERY);
        Map<String, CustomView> views = qd.getCustomViews(getUser(), getViewContext().getRequest());
        // don't include a customized default view in the list
        if (views.containsKey(null))
            views.remove(null);

        VBox box = new VBox();

        box.addView(new JspView("/org/labkey/issue/list.jsp"));
        box.addView(queryView);
        return box;
    }


    private ResultSet getIssuesResultSet() throws IOException, SQLException, ServletException
    {
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), IssuesQuerySchema.SCHEMA_NAME);
        QuerySettings settings = schema.getSettings(getViewContext(), ISSUES_QUERY);
        settings.setQueryName(ISSUES_QUERY);

        IssuesQueryView queryView = new IssuesQueryView(getViewContext(), schema, settings);

        return queryView.getResultSet();
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ListAction extends SimpleViewAction<ListForm>
    {

        public ListAction() {}

        public ListAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }

        public ModelAndView getView(ListForm form, BindException errors) throws Exception
        {
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());

            // convert AssignedTo/Email to AssignedTo/DisplayName: old bookmarks
            // reference Email, which is no longer displayed.
            ActionURL url = getViewContext().cloneActionURL();
            String[] emailFilters = url.getKeysByPrefix(ISSUES_QUERY + ".AssignedTo/Email");
            if (emailFilters != null && emailFilters.length > 0)
            {
                for (String emailFilter : emailFilters)
                    url.deleteParameter(emailFilter);
                return HttpView.redirect(url);
            }

            getPageConfig().setRssProperties(new RssAction().getUrl(), names.pluralName.toString());
            HttpView view = getIssuesView();
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return root.addChild(names.pluralName + " List", getURL());
        }

        public ActionURL getURL()
        {
            return issueURL("list").addParameter(".lastFilter","true");
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ExportTsvAction extends SimpleViewAction<QueryForm>
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            QueryView view = QueryView.create(form);
            final TSVGridWriter writer = view.getTsvWriter();
            return new HttpView()
            {
                protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
                {
                    writer.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.caption);
                    writer.write(getViewContext().getResponse());
                }
            };
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DetailsAction extends SimpleViewAction<IssueIdForm>
    {
        Issue _issue = null;

        public DetailsAction()
        {
        }

        public DetailsAction(Issue issue, ViewContext context)
        {
            _issue = issue;
            setViewContext(context);
        }

        public ModelAndView getView(IssueIdForm form, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = getIssue(issueId);

            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            if (null == _issue)
            {
                HttpView.throwNotFound("Unable to find " + names.singularName + " " + form.getIssueId());
                return null;
            }

            IssuePage page = new IssuePage();
            page.setPrint(isPrint());
            page.setIssue(_issue);
            page.setCustomColumnConfiguration(getCustomColumnConfiguration());
            //pass user's update perms to jsp page to determine whether to show notify list
            page.setUserHasUpdatePermissions(hasUpdatePermission(getUser(), _issue));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));

            getPageConfig().setTitle("" + _issue.getIssueId() + " : " + _issue.getTitle().getSource());

            JspView v = new JspView<IssuePage>(IssuesController.class, "detailView.jsp", page);
            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new ListAction(getViewContext()).appendNavTrail(root)
                    .addChild("Detail -- " + _issue.getIssueId(), getURL());
        }

        public ActionURL getURL()
        {
            return issueURL("details").addParameter("issueId", _issue.getIssueId());
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class DetailsListAction extends SimpleViewAction<ListForm>
    {
        public ModelAndView getView(ListForm listForm, BindException errors) throws Exception
        {
            // convert AssignedTo/Email to AssignedTo/DisplayName: old bookmarks
            // reference Email, which is no longer displayed.
            ActionURL url = getViewContext().cloneActionURL();
            String[] emailFilters = url.getKeysByPrefix(ISSUES_QUERY + ".AssignedTo/Email");
            if (emailFilters != null && emailFilters.length > 0)
            {
                for (String emailFilter : emailFilters)
                    url.deleteParameter(emailFilter);
                return HttpView.redirect(url);
            }

            Set<String> issueIds = DataRegionSelection.getSelected(getViewContext(), false);
            ArrayList<Issue> issueList = new ArrayList<Issue>();

            if (!issueIds.isEmpty())
            {
                for (String issueId : issueIds)
                {
                    issueList.add(getIssue(Integer.parseInt(issueId)));
                }
            }
            else
            {
                ResultSet rs = null;

                try
                {
                    rs = getIssuesResultSet();
                    int issueColumnIndex = rs.findColumn("issueId");

                    while (rs.next())
                    {
                        issueList.add(getIssue(rs.getInt(issueColumnIndex)));
                    }
                }
                finally
                {
                    ResultSetUtil.close(rs);
                }
            }

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "detailList.jsp", page);

            page.setIssueList(issueList);
            page.setCustomColumnConfiguration(getCustomColumnConfiguration());
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setDataRegionSelectionKey(listForm.getDataRegionSelectionKey());

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return new ListAction(getViewContext()).appendNavTrail(root).addChild(names.singularName + " Details");
        }
    }


    @RequiresPermission(ACL.PERM_INSERT)
    public class InsertAction extends FormViewAction<IssuesForm>
    {
        Issue _issue = null;

        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            _issue = reshow ? form.getBean() : new Issue();

            if (_issue.getAssignedTo() != null)
            {
                User user = UserManager.getUser(_issue.getAssignedTo());
                if (user != null)
                {
                    _issue.setAssignedTo(user.getUserId());
                }
            }

            _issue.Open(getContainer(), getUser());
            setNewIssueDefaults(_issue);

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp", page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction("insert");
            page.setIssue(_issue);
            page.setPrevIssue(_issue);
            page.setCustomColumnConfiguration(ccc);
            page.setBody(form.getComment() == null ? form.getBody() : form.getComment());
            page.setCallbackURL(form.getCallbackURL());
            page.setEditable(getEditableFields(page.getAction(), ccc));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);

            return v;
        }

        public void validateCommand(IssuesForm form, Errors errors)
        {
            if (!form.getSkipPost())
            {
                validateRequiredFields(form, errors);
                validateNotifyList(form.getBean(), form, errors);
            }
        }

        public boolean handlePost(IssuesForm form, BindException errors) throws Exception
        {
            if (form.getSkipPost())
                return false;

            Container c = getContainer();
            User user = getUser();

            _issue = form.getBean();
            _issue.Open(c, user);
            validateNotifyList(_issue, form, errors);

            DbScope scope = IssuesSchema.getInstance().getSchema().getScope();
            boolean ownsTransaction = !scope.isTransactionActive();
            try
            {
                if (ownsTransaction)
                    scope.beginTransaction();
                // for new issues, the original is always the default.
                Issue orig = new Issue();
                orig.Open(getContainer(), getUser());

                Issue.Comment comment = addComment(_issue, orig, user, form.getAction(), form.getComment(), getColumnCaptions(), getViewContext());
                IssueManager.saveIssue(openSession(), user, c, _issue);
                AttachmentService.get().addAttachments(user, comment, getAttachmentFileList());
                if (ownsTransaction)
                    scope.commitTransaction();
            }
            catch (Exception x)
            {
                Throwable ex = x.getCause() == null ? x : x.getCause();
                String error = ex.getMessage();
                _log.debug("IssuesContoller.doInsert", x);
                _issue.Open(c, user);

                errors.addError(new LabkeyError(error));
                return false;
            }
            finally
            {
                if (ownsTransaction)
                    scope.closeConnection();
            }

            ActionURL url = new DetailsAction(_issue, getViewContext()).getURL();

            final String assignedTo = UserManager.getDisplayName(_issue.getAssignedTo(), getViewContext());
            if (assignedTo != null)
                sendUpdateEmail(_issue, null, url, "opened and assigned to " + assignedTo, form.getAction());
            else
                sendUpdateEmail(_issue, null, url, "opened", form.getAction());

            return true;
        }


        public ActionURL getSuccessURL(IssuesForm issuesForm)
        {
            if (!StringUtils.isEmpty(issuesForm.getCallbackURL()))
            {
                ActionURL url = new ActionURL(issuesForm.getCallbackURL());
                url.addParameter("issueId", _issue.getIssueId());
                return url;
            }
            
            ActionURL forwardURL = new DetailsAction(_issue, getViewContext()).getURL();
            return forwardURL;
        }


        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return new ListAction(getViewContext()).appendNavTrail(root).addChild("Insert New " + names.singularName);
        }
    }


    private Issue setNewIssueDefaults(Issue issue) throws SQLException, ServletException
    {
        Map<Integer, HString> defaults = IssueManager.getAllDefaults(getContainer());

        issue.setArea(defaults.get(ISSUE_AREA));
        issue.setType(defaults.get(ISSUE_TYPE));
        issue.setMilestone(defaults.get(ISSUE_MILESTONE));
        issue.setString1(defaults.get(ISSUE_STRING1));
        issue.setString2(defaults.get(ISSUE_STRING2));

        HString priority = defaults.get(ISSUE_PRIORITY);
        issue.setPriority(null != priority ? priority.parseInt() : 3);

        return issue;
    }


    protected abstract class IssueUpdateAction extends FormViewAction<IssuesForm>
    {
        Issue _issue = null;

        public boolean handlePost(IssuesForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            User user = getUser();

            Issue issue = form.getBean();
            Issue prevIssue = (Issue)form.getOldValues();
            requiresUpdatePermission(user, issue);
            ActionURL detailsUrl;

            DbScope scope = IssuesSchema.getInstance().getSchema().getScope();
            boolean ownsTransaction = !scope.isTransactionActive();
            try
            {
                if (ownsTransaction)
                    scope.beginTransaction();
                detailsUrl = new DetailsAction(issue, getViewContext()).getURL();

                if ("resolve".equals(form.getAction()))
                    issue.Resolve(user);
                else if ("open".equals(form.getAction()) || "reopen".equals(form.getAction()))
                    issue.Open(c, user, true);
                else if ("close".equals(form.getAction()))
                    issue.Close(user);
                else
                    issue.Change(user);

                Issue.Comment comment = addComment(issue, prevIssue, user, form.getAction(), form.getComment(), getColumnCaptions(), getViewContext());
                IssueManager.saveIssue(openSession(), user, c, issue);
                AttachmentService.get().addAttachments(user, comment, getAttachmentFileList());
                if (ownsTransaction)
                    scope.commitTransaction();
            }
            catch (Exception x)
            {
                errors.addError(new ObjectError("main", new String[] {"Error"}, new Object[] {x}, x.getMessage()));
                return false;
            }
            finally
            {
                if (ownsTransaction)
                    scope.closeConnection();
            }

            // Send update email...
            //    ...if someone other than "created by" is closing a bug
            //    ...if someone other than "assigned to" is updating, reopening, or resolving a bug
            if ("close".equals(form.getAction()))
            {
                sendUpdateEmail(issue, prevIssue, detailsUrl, "closed", form.getAction());
            }
            else
            {
                String change = ("open".equals(form.getAction()) || "reopen".equals(form.getAction()) ? "reopened" : form.getAction() + "d");
                sendUpdateEmail(issue, prevIssue, detailsUrl, change, form.getAction());
            }
            return true;
        }

        public void validateCommand(IssuesForm form, Errors errors)
        {
            validateRequiredFields(form, errors);
            validateNotifyList(form.getBean(), form, errors);
        }

        public ActionURL getSuccessURL(IssuesForm form)
        {
            return form.getForwardURL();
        }
    }



    // SAME as AttachmentForm, just to demonstrate GuidString
    public static class _AttachmentForm
    {
        private GuidString _entityId = null;
        private String _name = null;


        public GuidString getEntityId()
        {
            return _entityId;
        }


        public void setEntityId(GuidString entityId)
        {
            _entityId = entityId;
        }


        public String getName()
        {
            return _name;
        }


        public void setName(String name)
        {
            _name = name;
        }
    }

    


    @RequiresPermission(ACL.PERM_READ)
    public class DownloadAction extends SimpleViewAction<_AttachmentForm>
    {
        public ModelAndView getView(final _AttachmentForm form, BindException errors) throws Exception
        {
            if (form.getEntityId() != null && form.getName() != null)
            {
                getPageConfig().setTemplate(PageConfig.Template.None);
                final AttachmentParent parent = new IssueAttachmentParent(getContainer(), form.getEntityId());

                return new HttpView()
                {
                    protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
                    {
                        AttachmentService.get().download(response, parent, form.getName());
                    }
                };
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public class IssueAttachmentParent extends AttachmentParentEntity
    {
        public IssueAttachmentParent(Container c, GuidString entityId)
        {
            setContainer(c.getId());
            setEntityId(entityId.toString());
        }
    }


    @RequiresPermission(ACL.PERM_UPDATEOWN)
    public class UpdateAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getBean().getIssueId();
            _issue = getIssue(issueId);
            if (_issue == null)
                HttpView.throwNotFound();

            Issue prevIssue = (Issue)_issue.clone();
            User user = getUser();
            requiresUpdatePermission(user, _issue);

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp", page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction("update");
            page.setIssue(_issue);
            page.setPrevIssue(prevIssue);
            page.setCustomColumnConfiguration(ccc);
            page.setBody(form.getComment());
            page.setEditable(getEditableFields(page.getAction(), ccc));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new DetailsAction(_issue, getViewContext()).appendNavTrail(root)
                    .addChild("(update) " + _issue.getTitle().getSource());
        }
    }


    private Set<String> getEditableFields(String action, IssueManager.CustomColumnConfiguration ccc)
    {
        final Set<String> editable = new HashSet<String>(20);

        editable.add("title");
        editable.add("assignedTo");
        editable.add("type");
        editable.add("area");
        editable.add("priority");
        editable.add("milestone");
        editable.add("comments");
        editable.add("attachments");

        for (String columnName : ccc.getColumnCaptions().keySet())
            editable.add(columnName);

        //if (!"insert".equals(action))
        editable.add("notifyList");

        if ("resolve".equals(action))
        {
            editable.add("resolution");
            editable.add("duplicate");
        }

        return editable;
    }


    @RequiresPermission(ACL.PERM_UPDATEOWN)
    public class ResolveAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = getIssue(issueId);
            if (null == _issue)
                HttpView.throwNotFound();

            Issue prevIssue = (Issue)_issue.clone();
            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.beforeResolve(user);

            if (_issue.getResolution().isEmpty())
            {
                Map<Integer, HString> defaults = IssueManager.getAllDefaults(getContainer());

                HString resolution = defaults.get(ISSUE_RESOLUTION);

                if (resolution != null && !resolution.isEmpty())
                    _issue.setResolution(resolution);
            }

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp", page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction("resolve");
            page.setIssue(_issue);
            page.setPrevIssue(prevIssue);
            page.setCustomColumnConfiguration(ccc);
            page.setBody(form.getComment());
            page.setEditable(getEditableFields(page.getAction(), ccc));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return (new DetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Resolve " + names.singularName);
        }
    }


    @RequiresPermission(ACL.PERM_UPDATEOWN)
    public class CloseAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getBean().getIssueId();
            _issue = getIssue(issueId);
            if (null == _issue)
                HttpView.throwNotFound();

            Issue prevIssue = (Issue)_issue.clone();
            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.Close(user);

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp",page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction("close");
            page.setIssue(_issue);
            page.setPrevIssue(prevIssue);
            page.setCustomColumnConfiguration(ccc);
            page.setBody(form.getComment());
            page.setEditable(getEditableFields(page.getAction(), ccc));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return (new DetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Close " + names.singularName);
        }
    }


    @RequiresPermission(ACL.PERM_UPDATEOWN)
    public class ReopenAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getBean().getIssueId();
            _issue = getIssue(issueId);
            Issue prevIssue = (Issue)_issue.clone();

            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.beforeReOpen();
            _issue.Open(getContainer(), user);

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp",page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction("reopen");
            page.setIssue(_issue);
            page.setPrevIssue(prevIssue);
            page.setCustomColumnConfiguration(ccc);
            page.setBody(form.getComment());
            page.setEditable(getEditableFields(page.getAction(), ccc));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);

            return v;
            //return _renderInTemplate(v, "(open) " + issue.getTitle(), null);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return (new DetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Reopen " + names.singularName);
        }
    }


    private static ActionURL getDetailsForwardURL(ViewContext context, Issue issue)
    {
        ActionURL url = context.cloneActionURL();
        url.setAction("details");
        url.addParameter("issueId", "" + issue.getIssueId());
        return url;
    }


    private void validateRequiredFields(IssuesForm form, Errors errors)
    {
        HString requiredFields = IssueManager.getRequiredIssueFields(getContainer());
        final Map<String, String> newFields = form.getStrings();
        if (requiredFields.isEmpty())
            return;

        MapBindingResult requiredErrors = new MapBindingResult(newFields, errors.getObjectName());
        if (newFields.containsKey("title"))
            validateRequired("title", newFields.get("title"), requiredFields, requiredErrors);
        if (newFields.containsKey("assignedTo") && !(Issue.statusCLOSED.equals(form.getBean().getStatus())))
            validateRequired("assignedto", newFields.get("assignedTo"), requiredFields, requiredErrors);
        if (newFields.containsKey("type"))
            validateRequired("type", newFields.get("type"), requiredFields, requiredErrors);
        if (newFields.containsKey("area"))
            validateRequired("area", newFields.get("area"), requiredFields, requiredErrors);
        if (newFields.containsKey("priority"))
            validateRequired("priority", newFields.get("priority"), requiredFields, requiredErrors);
        if (newFields.containsKey("milestone"))
            validateRequired("milestone", newFields.get("milestone"), requiredFields, requiredErrors);
        if (newFields.containsKey("notifyList"))
            validateRequired("notifylist", newFields.get("notifyList"), requiredFields, requiredErrors);
        if (newFields.containsKey("int1"))
            validateRequired("int1", newFields.get("int1"), requiredFields, requiredErrors);
        if (newFields.containsKey("int2"))
            validateRequired("int2", newFields.get("int2"), requiredFields, requiredErrors);
        if (newFields.containsKey("string1"))
            validateRequired("string1", newFields.get("string1"), requiredFields, requiredErrors);
        if (newFields.containsKey("string2"))
            validateRequired("string2", newFields.get("string2"), requiredFields, requiredErrors);

        errors.addAllErrors(requiredErrors);
    }


    private void validateRequired(String columnName, String value, HString requiredFields, Errors errors)
    {
        if (requiredFields != null)
        {
            if (requiredFields.indexOf(columnName) != -1)
            {
                if (StringUtils.isEmpty(value))
                {
                    final IssueManager.CustomColumnConfiguration ccc = IssueManager.getCustomColumnConfiguration(getContainer());
                    String name = null;
                    if (ccc.getColumnCaptions().containsKey(columnName))
                        name = ccc.getColumnCaptions().get(columnName);
                    else
                    {
                        ColumnInfo column = IssuesSchema.getInstance().getTableInfoIssues().getColumn(columnName);
                        if (column != null)
                            name = column.getName();
                    }
                    String display = name == null ? columnName : name;
                    errors.rejectValue(columnName, "NullError", new Object[] {display}, display + " is required.");
                }
            }
        }
    }
    

    private void validateNotifyList(Issue issue, IssuesForm form, Errors errors)
    {
        String[] rawEmails = _toString(form.getNotifyList()).split("\n");
        List<String> invalidEmails = new ArrayList<String>();
        List<ValidEmail> emails = org.labkey.api.security.SecurityManager.normalizeEmails(rawEmails, invalidEmails);

        StringBuffer message = new StringBuffer();

        for (String rawEmail : invalidEmails)
        {
            rawEmail = rawEmail.trim();
            // Ignore lines of all whitespace, otherwise show an error.
            if (!"".equals(rawEmail))
            {
                message.append("Failed to add user ").append(rawEmail).append(": Invalid email address");
                errors.rejectValue("notifyList","Error",new Object[] {message.toString()}, message.toString());
            }
        }

        if (!emails.isEmpty())
        {
            HStringBuilder notify = new HStringBuilder();
            for (int i=0; i < emails.size(); i++)
            {
                notify.append(emails.get(i));
                if (i < emails.size()-1)
                    notify.append(';');
            }
            issue.setNotifyList(notify.toHString());
        }
    }

    public static class CompleteUserForm
    {
        private String _prefix;
        private String _issueId;

        public String getPrefix(){return _prefix;}
        public void setPrefix(String prefix){_prefix = prefix;}

        public String getIssueId(){return _issueId;}
        public void setIssueId(String issueId){_issueId = issueId;}
    }


    @RequiresPermission(ACL.PERM_READ)
    public class CompleteUserAction extends AjaxCompletionAction<CompleteUserForm>
    {
        public List<AjaxCompletion> getCompletions(CompleteUserForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            final int issueId = Integer.valueOf(form.getIssueId());
            Issue issue = getIssue(issueId);
            if (issue == null)
            {
                issue = new Issue();
                issue.Open(c, getUser());
            }
            User[] users = IssueManager.getAssignedToList(c, issue);
            return UserManager.getAjaxCompletions(form.getPrefix(), users, getViewContext());
        }
    }

    public class UpdateEmailPage
    {
        public UpdateEmailPage(String url, Issue issue, boolean isPlain)
        {
            this.url = url;
            this.issue = issue;
            this.isPlain = isPlain;
        }
        public String url;
        public Issue issue;
        public boolean isPlain;
    }


    private void sendUpdateEmail(Issue issue, Issue prevIssue, ActionURL detailsURL, String change, String action)
    {
        try
        {
            final String to = getEmailAddresses(issue, prevIssue, action);
            if (to.length() > 0)
            {
                Issue.Comment lastComment = issue.getLastComment();
                String messageId = "<" + issue.getEntityId() + "." + lastComment.getCommentId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";
                String references = messageId + " <" + issue.getEntityId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";
                MailHelper.ViewMessage m = MailHelper.createMessage(LookAndFeelProperties.getInstance(getContainer()).getSystemEmailAddress(), to);
                HttpServletRequest request = AppProps.getInstance().createMockRequest();  // Use base server url for root of links in email
                if (m.getAllRecipients().length > 0)
                {
                    m.setMultipart(true);
                    m.setSubject("Issue #" + issue.getIssueId() + ", \"" + issue.getTitle().getSource() + ",\" has been " + change);
                    m.setHeader("References", references);

                    JspView viewPlain = new JspView<UpdateEmailPage>(IssuesController.class, "updateEmail.jsp", new UpdateEmailPage(detailsURL.getURIString(), issue, true));
                    m.setTemplateContent(request, viewPlain, "text/plain");

                    JspView viewHtml = new JspView<UpdateEmailPage>(IssuesController.class, "updateEmail.jsp", new UpdateEmailPage(detailsURL.getURIString(), issue, false));
                    m.setTemplateContent(request, viewHtml, "text/html");

                    MailHelper.send(m);
                    MailHelper.addAuditEvent(getUser(), getContainer(), m);
                }
            }
        }
        catch (Exception e)
        {
            _log.error("sendUpdateEmail", e);
        }
    }

    /**
     * Builds the list of email addresses for notification based on the user
     * preferences and the explicit notification list.
     */
    private String getEmailAddresses(Issue issue, Issue prevIssue, String action) throws ServletException
    {
        final Set<String> emailAddresses = new HashSet<String>();
        final Container c = getContainer();
        int assignedToPref = IssueManager.getUserEmailPreferences(c, issue.getAssignedTo());
        int assignedToPrevPref = prevIssue != null ? IssueManager.getUserEmailPreferences(c, prevIssue.getAssignedTo()) : 0;
        int createdByPref = IssueManager.getUserEmailPreferences(c, issue.getCreatedBy());


        if ("insert".equals(action))
        {
            if ((assignedToPref & IssueManager.NOTIFY_ASSIGNEDTO_OPEN) != 0)
                emailAddresses.add(UserManager.getEmailForId(issue.getAssignedTo()));
        }
        else
        {
            if ((assignedToPref & IssueManager.NOTIFY_ASSIGNEDTO_UPDATE) != 0)
                emailAddresses.add(UserManager.getEmailForId(issue.getAssignedTo()));

            if ((assignedToPrevPref & IssueManager.NOTIFY_ASSIGNEDTO_UPDATE) != 0)
                emailAddresses.add(UserManager.getEmailForId(prevIssue.getAssignedTo()));

            if ((createdByPref & IssueManager.NOTIFY_CREATED_UPDATE) != 0)
                emailAddresses.add(UserManager.getEmailForId(issue.getCreatedBy()));
        }

/*
        if ((filter & IssueManager.getUserEmailPreferences(c, issue.getAssignedTo())) != 0)
        {
            emailAddresses.add(UserManager.getEmailForId(issue.getAssignedTo()));
        }

        if ((filter & IssueManager.getUserEmailPreferences(c, issue.getCreatedBy())) != 0)
        {
            emailAddresses.add(UserManager.getEmailForId(issue.getCreatedBy()));
        }
*/

        // add any explicit notification list addresses
        final HString notify = issue.getNotifyList();
        if (notify != null)
        {
            StringTokenizer tokenizer = new StringTokenizer(notify.getSource(), ";\n\r\t");
            while (tokenizer.hasMoreTokens())
            {
                emailAddresses.add((String)tokenizer.nextElement());
            }
        }

        final String current = getUser().getEmail();
        final StringBuffer sb = new StringBuffer();

        boolean selfSpam = !((IssueManager.NOTIFY_SELF_SPAM & IssueManager.getUserEmailPreferences(c, getUser().getUserId())) == 0);
        if (selfSpam)
            emailAddresses.add(current);

        // build up the final semicolon delimited list, excluding the current user
        for (String email : emailAddresses.toArray(new String[emailAddresses.size()]))
        {
            if (selfSpam || !email.equals(current))
            {
                sb.append(email);
                sb.append(';');
            }
        }
        return sb.toString();
    }

    @RequiresPermission(ACL.PERM_READ)
    public class EmailPrefsAction extends FormViewAction<EmailPrefsForm>
    {
        String _message = null;

        public ModelAndView getView(EmailPrefsForm form, boolean reshow, BindException errors) throws Exception
        {
            if (getViewContext().getUser().isGuest())
                HttpView.throwUnauthorized();

            int emailPrefs = IssueManager.getUserEmailPreferences(getContainer(), getUser().getUserId());
            int issueId = form.getIssueId() == null ? 0 : form.getIssueId();
            JspView v = new JspView<EmailPrefsBean>(IssuesController.class, "emailPreferences.jsp",
                new EmailPrefsBean(emailPrefs, errors, _message, issueId));
            return v;
        }

        public boolean handlePost(EmailPrefsForm form, BindException errors) throws Exception
        {
            int emailPref = 0;
            for (int pref : form.getEmailPreference())
            {
                emailPref |= pref;
            }
            IssueManager.setUserEmailPreferences(getContainer(), getUser().getUserId(),
                    emailPref, getUser().getUserId());
            _message = "Settings updated successfully";
            return true;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return (new ListAction(getViewContext())).appendNavTrail(root).addChild("Email preferences");
        }


        public void validateCommand(EmailPrefsForm emailPrefsForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(EmailPrefsForm emailPrefsForm)
        {
            return null;
        }
    }


    public static final String REQUIRED_FIELDS_COLUMNS = "Title,AssignedTo,Type,Area,Priority,Milestone,NotifyList";
    public static final String DEFAULT_REQUIRED_FIELDS = "title;assignedto";


    @RequiresPermission(ACL.PERM_ADMIN)
    public class AdminAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            // TODO: This hack ensures that priority & resolution option defaults get populated if first reference is the admin page.  Fix this.
            IssuePage page = new IssuePage()
            {
                public void _jspService(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException
                {
                }
            };
            page.getPriorityOptions(getContainer());
            page.getResolutionOptions(getContainer());
            // </HACK>

            AdminView adminView = new AdminView(getContainer(), getCustomColumnConfiguration());
            return adminView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return (new ListAction(getViewContext())).appendNavTrail(root).addChild(names.pluralName + " Admin Page", getUrl());
        }

        public ActionURL getUrl()
        {
            return issueURL("admin");
        }
    }


    public abstract class AdminFormAction extends FormHandlerAction<AdminForm>
    {
        public void validateCommand(AdminForm adminForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(AdminForm adminForm)
        {
            return issueURL("admin");
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class AddKeywordAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            IssueManager.addKeyword(getContainer(), form.getType(), form.getKeyword());
            return true;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteKeywordAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            IssueManager.deleteKeyword(getContainer(), form.getType(), form.getKeyword());
            return true;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class SetKeywordDefaultAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            IssueManager.setKeywordDefault(getContainer(), form.getType(), form.getKeyword());
            return true;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ClearKeywordDefaultAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            IssueManager.clearKeywordDefault(getContainer(), form.getType());
            return true;
        }
    }

    public static class EntryTypeNamesForm
    {
        public static enum ParamNames
        {
            entrySingularName,
            entryPluralName
        }

        private HString _entrySingularName;
        private HString _entryPluralName;

        public HString getEntrySingularName()
        {
            return _entrySingularName;
        }

        public void setEntrySingularName(HString entrySingularName)
        {
            _entrySingularName = entrySingularName;
        }

        public HString getEntryPluralName()
        {
            return _entryPluralName;
        }

        public void setEntryPluralName(HString entryPluralName)
        {
            _entryPluralName = entryPluralName;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SetEntryTypeNames extends FormHandlerAction<EntryTypeNamesForm>
    {
        public void validateCommand(EntryTypeNamesForm form, Errors errors)
        {
            if (form.getEntrySingularName().trimToEmpty().length() == 0)
                errors.reject(EntryTypeNamesForm.ParamNames.entrySingularName.name(), "You must specify a value for the entry type singular name!");
            if (form.getEntryPluralName().trimToEmpty().length() == 0)
                errors.reject(EntryTypeNamesForm.ParamNames.entryPluralName.name(), "You must specify a value for the entry type plural name!");
        }

        public boolean handlePost(EntryTypeNamesForm form, BindException errors) throws Exception
        {
            IssueManager.EntryTypeNames names = new IssueManager.EntryTypeNames();
            
            names.singularName = form.getEntrySingularName();
            names.pluralName = form.getEntryPluralName();

            IssueManager.saveEntryTypeNames(getViewContext().getContainer(), names);
            return true;
        }

        public ActionURL getSuccessURL(EntryTypeNamesForm form)
        {
            return issueURL("admin");
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class RssAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            ResultSet rs = null;
            try
            {
                DataRegion r = new DataRegion();
                TableInfo tinfo = IssuesSchema.getInstance().getTableInfoIssues();
                List<ColumnInfo> cols = tinfo.getColumns("IssueId,Created,CreatedBy,Area,Type,Title,AssignedTo,Priority,Status,Milestone");
                r.addColumns(cols);

                rs = r.getResultSet(new RenderContext(getViewContext()));
                ObjectFactory f = ObjectFactory.Registry.getFactory(Issue.class);
                Issue[] issues = (Issue[]) f.handleArray(rs);

                ActionURL url = getDetailsURL(getContainer(), 1, isPrint());
                String filteredURLString = PageFlowUtil.filter(url);
                String detailsURLString = filteredURLString.substring(0, filteredURLString.length() - 1);

                WebPartView v = new JspView<RssBean>("/org/labkey/issue/rss.jsp", new RssBean(issues, detailsURLString));
                v.setFrame(WebPartView.FrameType.NONE);

                return v;
            }
            catch (SQLException x)
            {
                x.printStackTrace();
                throw new ServletException(x);
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        private ActionURL getUrl()
        {
            return issueURL("rss");
        }
    }


    public static class RssBean
    {
        public Issue[] issues;
        public String filteredURLString;

        private RssBean(Issue[] issues, String filteredURLString)
        {
            this.issues = issues;
            this.filteredURLString = filteredURLString;
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class PurgeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            if (!getUser().isAdministrator())   // GLOBAL
                HttpView.throwUnauthorized();
            String message = IssueManager.purge();
            return new HtmlView(message);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class JumpToIssueAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String issueId = (String)getProperty("issueId");
            if (issueId != null)
            {
                issueId = issueId.trim();
                try
                {
                    int id = Integer.parseInt(issueId);
                    Issue issue = getIssue(id);
                    if (issue != null)
                    {
                        ActionURL url = getViewContext().cloneActionURL();
                        url.deleteParameters();
                        url.addParameter("issueId", Integer.toString(id));
                        url.setAction("details.view");
                        return HttpView.redirect(url);
                    }
                }
                catch (NumberFormatException e)
                {
                    // fall through
                }
            }
            ActionURL url = getViewContext().cloneActionURL();
            url.deleteParameters();
            url.addParameter("error", "Invalid issue id '" + issueId + "'");
            url.setAction("list.view");
            url.addParameter(".lastFilter", "true");
            return HttpView.redirect(url);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class SearchAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            String searchTerm = (String)getProperty("search", "");

            getPageConfig().setHelpTopic(new HelpTopic("search", HelpTopic.Area.DEFAULT));

            HttpView results = new Search.SearchResultsView(c, Collections.singletonList(IssueSearch.getInstance(c)), searchTerm, new ActionURL(SearchAction.class, c), getUser(), false, false);
            return results;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new ListAction(getViewContext()).appendNavTrail(root).addChild("Search Results");
        }
    }


    static boolean _equal(String a, String b)
    {
        return _toString(a).equals(_toString(b));
    }


    static String _toString(Object a)
    {
        return null == a ? "" : a.toString();
    }


    static void _appendChange(StringBuffer sb, String field, HString from, HString to)
    {
        from = from == null ? HString.EMPTY : from;
        to = to == null ? HString.EMPTY : to;
        if (!from.equals(to))
        {
            HString encFrom = PageFlowUtil.filter(from);
            HString encTo = PageFlowUtil.filter(to);
            sb.append("<tr><td>").append(field).append("</td><td>").append(encFrom).append("</td><td>&raquo;</td><td>").append(encTo).append("</td></tr>\n");
        }
    }


    static Issue.Comment addComment(Issue issue, Issue previous, User user, String action, String comment, Map<String, String> customColumns, ViewContext context)
    {
        StringBuffer sbChanges = new StringBuffer();
        if (!action.equals("insert") && !action.equals("update"))
        {
            sbChanges.append("<b>").append(action);

            if (action.equals("resolve"))
            {
                // Add the resolution; e.g. "resolve as Fixed"
                sbChanges.append(" as ").append(issue.getResolution());
            }

            sbChanges.append("</b><br>\n");
        }
        

        // CONSIDER: write changes in wiki
        // CONSIDER: and postpone formatting until render
        if (null != previous)
        {
            // issueChanges is not defined yet, but it leaves things flexible
            sbChanges.append("<table class=issues-Changes>");
            _appendChange(sbChanges, "Title", previous.getTitle(), issue.getTitle());
            _appendChange(sbChanges, "Status", previous.getStatus(), issue.getStatus());
            _appendChange(sbChanges, "Assigned To", previous.getAssignedToName(context), issue.getAssignedToName(context));
            _appendChange(sbChanges, "Notify", previous.getNotifyList(), issue.getNotifyList());
            _appendChange(sbChanges, "Type", previous.getType(), issue.getType());
            _appendChange(sbChanges, "Area", previous.getArea(), issue.getArea());
            _appendChange(sbChanges, "Priority", HString.valueOf(previous.getPriority()), HString.valueOf(issue.getPriority()));
            _appendChange(sbChanges, "Milestone", previous.getMilestone(), issue.getMilestone());

            _appendCustomColumnChange(sbChanges, "int1", HString.valueOf(previous.getInt1()), HString.valueOf(issue.getInt1()), customColumns);
            _appendCustomColumnChange(sbChanges, "int2", HString.valueOf(previous.getInt2()), HString.valueOf(issue.getInt2()), customColumns);
            _appendCustomColumnChange(sbChanges, "string1", previous.getString1(), issue.getString1(), customColumns);
            _appendCustomColumnChange(sbChanges, "string2", previous.getString2(), issue.getString2(), customColumns);

            sbChanges.append("</table>\n");
        }

        //why we are wrapping issue comments in divs???
        HStringBuilder formattedComment = new HStringBuilder();
        formattedComment.append("<div class=\"wiki\">");
        formattedComment.append(sbChanges);
        //render issues as plain text with links
        WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
        if(null != wikiService)
        {
            WikiRenderer w = wikiService.getRenderer(WikiRendererType.TEXT_WITH_LINKS);
            formattedComment.append(w.format(comment).getHtml());
        }
        else
            formattedComment.append(comment);

        formattedComment.append("</div>");

        return issue.addComment(user, formattedComment.toHString());
    }

    private static void _appendCustomColumnChange(StringBuffer sb, String field, HString from, HString to, Map<String, String> columnCaptions)
    {
        String caption = columnCaptions.get(field);

        if (null != caption)
            _appendChange(sb, caption, from, to);
    }


    //
    // VIEWS
    //
    public static class AdminView extends JspView<AdminBean>
    {
        public AdminView(Container c, IssueManager.CustomColumnConfiguration ccc)
        {
            super("/org/labkey/issue/admin.jsp");

            KeywordAdminView keywordView = new KeywordAdminView(c, ccc);
            keywordView.addKeyword("Type", ISSUE_TYPE);
            keywordView.addKeyword("Area", ISSUE_AREA);
            keywordView.addKeyword("Priority", ISSUE_PRIORITY);
            keywordView.addKeyword("Milestone", ISSUE_MILESTONE);
            keywordView.addKeyword("Resolution", ISSUE_RESOLUTION);
            keywordView.addCustomColumn("string1", ISSUE_STRING1);
            keywordView.addCustomColumn("string2", ISSUE_STRING2);

            List<String> columnNames = new ArrayList<String>();
            columnNames.addAll(Arrays.asList(REQUIRED_FIELDS_COLUMNS.split(",")));
            columnNames.addAll(IssuesTable.getCustomColumnCaptions(c).keySet());
            List<ColumnInfo> cols = IssuesSchema.getInstance().getTableInfoIssues().getColumns(columnNames.toArray(new String[columnNames.size()]));

            IssuesPreference ipb = new IssuesPreference(cols, IssueManager.getRequiredIssueFields(c), IssueManager.getEntryTypeNames(c));

            AdminBean bean = new AdminBean();

            bean.ccc = ccc;
            bean.keywordView = keywordView;
            bean.requiredFieldsView = new JspView<IssuesPreference>("/org/labkey/issue/requiredFields.jsp", ipb);
            bean.entryTypeNames = IssueManager.getEntryTypeNames(c);
            setModelBean(bean);
        }
    }


    public static class AdminBean
    {
        public IssueManager.CustomColumnConfiguration ccc;
        public KeywordAdminView keywordView;
        public JspView<IssuesPreference> requiredFieldsView;
        public IssueManager.EntryTypeNames entryTypeNames;
    }


    // Renders the pickers for all keywords; would be nice to render each picker independently, but that makes it hard to align
    // all the top and bottom sections with each other.
    public static class KeywordAdminView extends JspView<List<KeywordPicker>>
    {
        private Container _c;
        private List<KeywordPicker> _keywordPickers = new ArrayList<KeywordPicker>(5);
        public IssueManager.CustomColumnConfiguration _ccc;

        public KeywordAdminView(Container c, IssueManager.CustomColumnConfiguration ccc)
        {
            super("/org/labkey/issue/keywordAdmin.jsp");
            setModelBean(_keywordPickers);
            _c = c;
            _ccc = ccc;
        }

        // Add keyword admin for custom columns with column picker enabled
        private void addCustomColumn(String tableColumn, int type)
        {
            if (_ccc.getPickListColumns().contains(tableColumn))
            {
                String caption = _ccc.getColumnCaptions().get(tableColumn);
                addKeyword(caption, type);
            }
        }

        private void addKeyword(String name, int type)
        {
            _keywordPickers.add(new KeywordPicker(_c, name, type));
        }
    }


    public static class KeywordPicker
    {
        public String name;
        public String plural;
        public int type;
        public IssueManager.Keyword[] keywords;

        KeywordPicker(Container c, String name, int type)
        {
            this.name = name;
            this.plural = name.endsWith("y") ? name.substring(0, name.length() - 1) + "ies" : name + "s";
            this.type = type;
            this.keywords = IssueManager.getKeywords(c.getId(), type);
        }
    }


    public static class EmailPrefsBean
    {
        private int _emailPrefs;
        private BindException _errors;
        private String _message;
        private Integer _issueId;

        public EmailPrefsBean(int emailPreference, BindException errors, String message, Integer issueId)
        {
            _emailPrefs = emailPreference;
            _errors = errors;
            _message = message;
            _issueId = issueId;
        }

        public int getEmailPreference()
        {
            return _emailPrefs;
        }

        public BindException getErrors()
        {
            return _errors;
        }

        public String getMessage()
        {
            return _message;
        }

        public int getIssueId()
        {
            return _issueId;
        }
    }

    public static class EmailPrefsForm
    {
        private Integer[] _emailPreference = new Integer[0];
        private Integer _issueId;

        public Integer[] getEmailPreference()
        {
            return _emailPreference;
        }

        public void setEmailPreference(Integer[] emailPreference)
        {
            _emailPreference = emailPreference;
        }

        public Integer getIssueId()
        {
            return _issueId;
        }

        public void setIssueId(Integer issueId)
        {
            _issueId = issueId;
        }
    }

    public static class AdminForm
    {
        private int type;
        private HString keyword;


        public int getType()
        {
            return type;
        }


        public void setType(int type)
        {
            this.type = type;
        }


        public HString getKeyword()
        {
            return keyword;
        }


        public void setKeyword(HString keyword)
        {
            this.keyword = keyword;
        }
    }

    public static class IssuesForm extends BeanViewForm<Issue>
    {
        public IssuesForm()
        {
            super(Issue.class, IssuesSchema.getInstance().getTableInfoIssues(), new String[]{"action", "comment", "callbackURL"});
            setValidateRequired(false);
        }

        public String getAction()
        {
            return _stringValues.get("action");
        }

        public String getComment()
        {
            return _stringValues.get("comment");
        }

        public String getNotifyList()
        {
            return _stringValues.get("notifyList");
        }

        public String getCallbackURL()
        {
            return _stringValues.get("callbackURL");
        }

        public String getBody()
        {
            return _stringValues.get("body");
        }

        /**
         * A bit of a hack but to allow the mothership controller to continue to create issues
         * in the way that it previously did, we need to be able to tell the issues controller
         * to not handle the post, and just get the view.
         * @return
         */
        public boolean getSkipPost()
        {
            return BooleanUtils.toBoolean(_stringValues.get("skipPost"));
        }

        public ActionURL getForwardURL()
        {
            ActionURL url;
            String callbackURL = getCallbackURL();
            if (callbackURL != null)
            {
                url = new ActionURL(callbackURL).addParameter("issueId", "" + getBean().getIssueId());
                return url;
            }
            else
            {
                return getDetailsForwardURL(getViewContext(), getBean());
            }
        }

        public int getIssueId()
        {
            return NumberUtils.toInt(_stringValues.get("issueId"));
        }
    }


    public static class SummaryWebPart extends JspView<SummaryBean>
    {
        public SummaryWebPart()
        {
            super("/org/labkey/issue/summaryWebpart.jsp", new SummaryBean());

            SummaryBean bean = getModelBean();

            ViewContext context = getViewContext();
            Container c = context.getContainer();

            //set specified web part title
            Object title = context.get("title");
            if (title == null)
                title = "Issues Summary";
            setTitle(title.toString());

            User u = context.getUser();
            bean.hasPermission = c.hasPermission(u, ACL.PERM_READ);
            if (!bean.hasPermission)
                return;

            setTitleHref(getListURL(c));

            bean.listURL = getListURL(c).deleteParameters();

            try
            {
                bean.bugs = IssueManager.getSummary(c);
            }
            catch (SQLException x)
            {
                setVisible(false);
            }
        }
    }


    public static class SummaryBean
    {
        public boolean hasPermission;
        public Map[] bugs;
        public ActionURL listURL;
    }


    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super("IssueController");
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void testIssue()
                throws SQLException, ServletException
        {
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }


    Object openSession()
    {
//        if (null == _s)
//            _s = IssueManager.openSession();
//        return _s;
        return null;
    }


    void closeSession()
    {
//        if (null != _s)
//            _s.close();
//        _s = null;
    }


    protected synchronized void afterAction(Throwable t)
    {
        super.afterAction(t);
        closeSession();
    }

    /**
     * Does this user have permission to update this issue?
     */
    private boolean hasUpdatePermission(User user, Issue issue)
    {
        // If we have full Update rights on the container, continue
        if (getViewContext().hasPermission(ACL.PERM_UPDATE))
            return true;

        // If UpdateOwn on the container AND we created this Issue, continue
        //noinspection RedundantIfStatement
        if (getViewContext().hasPermission(ACL.PERM_UPDATEOWN)
                && issue.getCreatedBy() == user.getUserId())
            return true;

        return false;
    }


    /**
     * Throw an exception if user does not have permission to update issue
     */
    private void requiresUpdatePermission(User user, Issue issue)
            throws ServletException
    {
        if (!hasUpdatePermission(user, issue))
            HttpView.throwUnauthorized();
    }


    public static class InsertForm
    {
        private String _body;
        private Integer _assignedto;
        private String _callbackURL;
        private String _title;

        public String getBody()
        {
            return _body;
        }

        public void setBody(String body)
        {
            _body = body;
        }

        public Integer getAssignedto()
        {
            return _assignedto;
        }

        public void setAssignedto(Integer assignedto)
        {
            _assignedto = assignedto;
        }

        public String getCallbackURL()
        {
            return _callbackURL;
        }

        public void setCallbackURL(String callbackURL)
        {
            _callbackURL = callbackURL;
        }

        public String getTitle()
        {
            return _title;
        }

        public void setTitle(String title)
        {
            _title = title;
        }
    }


    public static class ListForm
    {
        private QuerySettings _settings;
        private boolean _export;
        private ActionURL _customizeURL;
        private Map<String, CustomView> _views;
        private String dataRegionSelectionKey = null;
        private Map<String, String> _reports;

        public boolean getExport()
        {
            return _export;
        }

        public void setExport(boolean export)
        {
            _export = export;
        }

        public ActionURL getCustomizeURL() {return _customizeURL;}
        public void setCustomizeURL(ActionURL url) {_customizeURL = url;}
        public Map<String, CustomView> getViews() {return _views;}
        public void setViews(Map<String, CustomView> views) {_views = views;}
        public QuerySettings getQuerySettings()
        {
            return _settings;
        }
        public void setQuerySettings(QuerySettings settings)
        {
            _settings = settings;
        }

        public String getDataRegionSelectionKey()
        {
            return dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            this.dataRegionSelectionKey = dataRegionSelectionKey;
        }

        public Map<String, String> getReports()
        {
            return _reports;
        }

        public void setReports(Map<String, String> reports)
        {
            _reports = reports;
        }
    }

    public static class IssuesPreference
    {
        private List<ColumnInfo> _columns;
        private HString _requiredFields;
        private IssueManager.EntryTypeNames _entryTypeNames;

        public IssuesPreference(List<ColumnInfo> columns, HString requiredFields, IssueManager.EntryTypeNames typeNames)
        {
            _columns = columns;
            _requiredFields = requiredFields;
            _entryTypeNames = typeNames;
        }

        public List<ColumnInfo> getColumns(){return _columns;}
        public HString getRequiredFields(){return _requiredFields;}
        public IssueManager.EntryTypeNames getEntryTypeNames() {return _entryTypeNames;}
    }


    public static class IssuePreferenceForm
    {
        private HString[] _requiredFields = new HString[0];

        public void setRequiredFields(HString[] requiredFields){_requiredFields = requiredFields;}
        public HString[] getRequiredFields(){return _requiredFields;}
    }


    public static class IssueIdForm
    {
        private int issueId = -1;

        public int getIssueId()
        {
            return issueId;
        }

        public void setIssueId(int issueId)
        {
            this.issueId = issueId;
        }
    }


    public static class RequiredError extends FieldError
    {
        RequiredError(String field, String display)
        {
            super("issue", field, "", true, new String[] {"NullError"}, new Object[] {display}, "Error: The field: " + display + " is required");
        }
    }
}
