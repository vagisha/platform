package org.labkey.issue.view;

import org.labkey.api.data.DataRegion;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.VBox;
import org.labkey.issue.IssuesController;
import org.labkey.issue.query.IssuesQuerySchema;

import java.io.PrintWriter;

/**
 * Created by klum on 5/1/2016.
 */
public class IssuesListView extends VBox
{
    public static final String ISSUE_LIST_DEF_NAME = "issueDefName";

    public IssuesListView(String issueDefName)
    {
        String dataRegionName = "issues-" + issueDefName;
        UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), IssuesQuerySchema.SCHEMA_NAME);
        QuerySettings settings = schema.getSettings(getViewContext(), dataRegionName, issueDefName);
        settings.getBaseSort().insertSortColumn(FieldKey.fromParts("IssueId"), Sort.SortDirection.DESC);

        QueryView queryView = schema.createView(getViewContext(), settings, null);

        // check for any legacy custom view parameters, and if so display a warning
        if (settings.getViewName() == null)
        {
            QuerySettings legacySettings = schema.getSettings(getViewContext(), "Issues", issueDefName);
            if (legacySettings.getViewName() != null)
            {
                ActionURL url = getViewContext().cloneActionURL().
                        deleteParameter(legacySettings.param(QueryParam.viewName)).
                        addParameter(settings.param(QueryParam.viewName), legacySettings.getViewName());

                HtmlView warning = new HtmlView("<span class='labkey-error'>The specified URL contains an obsolete viewName parameter and is being ignored. " +
                        "Please update your bookmark to this new URL : <a target='blank' href='" + PageFlowUtil.filter(url.getLocalURIString()) + "'>" + PageFlowUtil.filter(url.getURIString()) + "</a></span>");
                addView(warning);
            }
        }
        // add the header for buttons and views
        addView(new JspView<>("/org/labkey/issue/view/list.jsp", issueDefName));
        addView(queryView);

        setTitleHref(new ActionURL(IssuesController.ListAction.class, getViewContext().getContainer()).
                addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDefName).
                addParameter(DataRegion.LAST_FILTER_PARAM, true));
    }


    public static class IssuesListConfig extends HttpView
    {
        private Portal.WebPart _webPart;

        public IssuesListConfig(Portal.WebPart webPart)
        {
            _webPart = webPart;
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            JspView view = new JspView<>("/org/labkey/issue/view/issueListWebPartConfig.jsp", _webPart);
            include(view);
        }
    }
}
