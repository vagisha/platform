package org.labkey.query;

import org.apache.commons.lang3.BooleanUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: 9/10/13
 */
public class DataViewsWebPartFactory extends BaseWebPartFactory
{
    public static final String NAME = "Data Views";

    public DataViewsWebPartFactory()
    {
        super(NAME, WebPartFactory.LOCATION_BODY, true, false); // is editable
        addLegacyNames("Dataset Browse", "Dataset Browse (Experimental)");
    }

    @Override
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        JspView<Portal.WebPart> view = new JspView<>("/org/labkey/query/reports/view/dataViews.jsp", webPart);
        view.setTitle("Data Views");
        view.setFrame(WebPartView.FrameType.PORTAL);
        Container c = portalCtx.getContainer();
        NavTree menu = new NavTree();
        Map<String, String> properties = webPart.getPropertyMap();

        // the adminView flag refers to manage views
        boolean adminView = false;
        if (properties.containsKey("adminView"))
            adminView = BooleanUtils.toBoolean(properties.get("adminView"));

        if (portalCtx.hasPermission(InsertPermission.class))
        {
            NavTree reportMenu = new NavTree("Add Report");

            if (adminView)
            {
                List<ReportService.DesignerInfo> designers = new ArrayList<>();
                for (ReportService.UIProvider provider : ReportService.get().getUIProviders())
                    designers.addAll(provider.getDesignerInfo(portalCtx));

                Collections.sort(designers, new Comparator<ReportService.DesignerInfo>()
                {
                    @Override
                    public int compare(ReportService.DesignerInfo o1, ReportService.DesignerInfo o2)
                    {
                        return o1.getLabel().compareTo(o2.getLabel());
                    }
                });

                for (ReportService.DesignerInfo info : designers)
                {
                    NavTree item = new NavTree(info.getLabel(), info.getDesignerURL().getLocalURIString(), info.getIconPath());

                    item.setId(info.getId());
                    item.setDisabled(info.isDisabled());

                    reportMenu.addChild(item);
                }
            }
            else    // eventually should have the same menu for data views and manage views
            {
                reportMenu.addChild("From File", PageFlowUtil.urlProvider(ReportUrls.class).urlAttachmentReport(portalCtx.getContainer(), portalCtx.getActionURL()));
                reportMenu.addChild("From Link", PageFlowUtil.urlProvider(ReportUrls.class).urlLinkReport(portalCtx.getContainer(), portalCtx.getActionURL()));
            }
            menu.addChild(reportMenu);
        }

        if (portalCtx.hasPermission(AdminPermission.class))
        {
            if (!adminView)
            {
                NavTree customize = new NavTree("");
                String customizeScript = "customizeDataViews(" + webPart.getRowId() + ", \'" + webPart.getPageId() + "\', " + webPart.getIndex() + ");";

                customize.setScript(customizeScript);
                view.setCustomize(customize);
            }
            String editScript = "editDataViews(" + webPart.getRowId() + ");";
            NavTree edit = new NavTree("Edit", "javascript:" + editScript, portalCtx.getContextPath() + "/_images/partedit.png");
            view.addCustomMenu(edit);

            if (StudyService.get().getStudy(c) != null)
                menu.addChild("Manage Datasets", PageFlowUtil.urlProvider(StudyUrls.class).getManageDatasetsURL(c));
            menu.addChild("Manage Queries", PageFlowUtil.urlProvider(QueryUrls.class).urlSchemaBrowser(c));
        }

        if(!adminView && portalCtx.hasPermission(ReadPermission.class) && !portalCtx.getUser().isGuest())
        {
            ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(c);

            if (StudyService.get().getStudy(c) != null)
                url = PageFlowUtil.urlProvider(StudyUrls.class).getManageReports(c);
            menu.addChild("Manage Views", url);
        }

        view.setNavMenu(menu);

        return view;
    }
}
