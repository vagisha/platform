/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.core.notification;

import org.labkey.api.admin.AdminUrls;
import org.labkey.api.admin.notification.Notification;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.data.Container;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.PopupMenu;
import org.labkey.api.view.PopupMenuView;
import org.labkey.api.view.ViewContext;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Popup menu for upper-right corner of main frame
 * User: jeckels
 * Date: Oct 20, 2011
 */
public class NotificationMenuView extends PopupMenuView
{
    public static final String EXPERIMENTAL_NOTIFICATIONMENU = "experimental-notificationmenu";

    boolean hasErrorNotification = false;

    public static HttpView createView(ViewContext context)
    {
        NotificationService.Service service = ServiceRegistry.get(NotificationService.Service.class);
        if (null == service || context.getUser().isGuest())
            return null;
        return new NotificationMenuView(context);
    }

    public NotificationMenuView(ViewContext context)
    {
        User user = context.getUser();
        Container c = context.getContainer();

        NotificationService.Service service = ServiceRegistry.get(NotificationService.Service.class);
        List<Notification> notifications = service.getNotificationsByUser(null, user.getUserId(), true);

        NavTree tree = new NavTree("");
        tree.setImageCls("fa fa-bell");

        notifications.stream().forEach((n)->
        {
            tree.addChild(n.getDescription(), n.getActionLinkURL());
        });

        if (user.isSiteAdmin())
        {
            // TODO add this functionality to the NotificationService.getNotificationsByUser()
            //admin-only mode--show to admins
            if (AppProps.getInstance().isUserRequestedAdminOnlyMode())
            {
                tree.addChild(
                        "This site is configured so that only administrators may sign in. To allow other users to sign in, turn off admin-only mode via the site settings page.",
                        PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeSiteURL());
                hasErrorNotification = true;
            }

            //module failures during startup--show to admins
            Map<String, Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();
            if (null != moduleFailures && moduleFailures.size() > 0)
            {
                tree.addChild(
                        "The following modules experienced errors during startup: " + PageFlowUtil.filter(moduleFailures.keySet()),
                        PageFlowUtil.urlProvider(AdminUrls.class).getModuleErrorsURL(c));
                hasErrorNotification = true;
            }

            //upgrade message--show to admins
            String upgradeMessage = UsageReportingLevel.getUpgradeMessage();
            if (!StringUtils.isEmpty(upgradeMessage))
            {
                tree.addChild(upgradeMessage);
                hasErrorNotification = true;
            }
        }

        tree.setText(String.valueOf(tree.getChildCount()));
        if (tree.getChildCount() == 0)
            tree.addChild("No notifications");

        tree.setId("lk-notificationMenu");

        setNavTree(tree);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);

        getModelBean().setIsSingletonMenu(true);
    }

    @Override
    protected void renderInternal(PopupMenu model, PrintWriter out) throws Exception
    {
        // This doesn't actually work, need custom button renderer
        if (hasErrorNotification)
            out.write("<span class='labkey-error'>");
        super.renderInternal(model, out);
        if (hasErrorNotification)
            out.write("</span>");
    }
}
