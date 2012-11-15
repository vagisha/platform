<%
/*
 * Copyright (c) 2005-2012 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.exp.api.ExpObject" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.experiment.ConfirmDeleteView" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.exp.api.ExperimentUrls" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="org.labkey.api.data.Entity" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.security.SecurableResource" %>

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ConfirmDeleteView.ConfirmDeleteBean> me = (JspView<ConfirmDeleteView.ConfirmDeleteBean>) HttpView.currentView();
    ConfirmDeleteView.ConfirmDeleteBean bean = me.getModelBean();
    Container currentContainer = bean.getViewContext().getContainer();
%>

<% if (bean.getObjects().isEmpty())
{
    %><p>There are no selected objects to delete.</p>
    <%= text(bean.getReturnUrl() == null || bean.getReturnUrl().isEmpty() ? generateButton("OK", buildURL(ExperimentController.BeginAction.class)) : generateButton("OK", bean.getReturnUrl()))%><%
}
else
{ %>
    <p>Are you sure you want to delete the following <%= h(bean.getObjectType()) %><%= h(bean.getObjects().size() > 1 ? (bean.getObjectType().endsWith("h") ? "es" : "s") : "") %>?</p>

    <ul>
    <% for (ExpObject object : bean.getObjects()) { %>
        <li><a href="<%= h(bean.getDetailAction()) %>.view?rowId=<%= object.getRowId() %>"><%= h(object.getName()) %></a></li>
    <% } %>
    </ul>

    <% if (bean.getDeleteableExtras().size() > 0) { %>
        <%= h(bean.getDeleteableExtras().size() > 1 ? Integer.toString(bean.getDeleteableExtras().size()) : "One") %> <%= h(bean.getExtraNoun())%><%= h(bean.getDeleteableExtras().size() > 1 ? "s" : "") %> will also be deleted:

        <ul>
        <%  int count = 0;
            for (Pair<SecurableResource, ActionURL> entry : bean.getDeleteableExtras()) {
            if (count >= 50)
            {
                %>(<%= bean.getRunsWithPermission().size() - count %> <%= h(bean.getExtraNoun()) %>s omitted from list)<%
                break;
            }
            count++;
            %>
            <li>
                <a href="<%= entry.getValue() %>"><%= h(entry.getKey().getResourceName()) %></a>
                <% if (!entry.getKey().getResourceContainer().equals(currentContainer))
                { %>
                    (in <a href="<%= entry.getKey().getResourceContainer().getStartURL(bean.getViewContext().getUser()) %>"><%= h(entry.getKey().getResourceContainer().getPath()) %></a>)
                <% } %>
            </li>
        <% } %>
        </ul>
    <% } %>

<% if (bean.getNoPermissionExtras().size() > 0) { %>
    <span class="labkey-error">The <%= h(bean.getObjectType()) %><%= h(bean.getObjects().size() > 1 ? "s" : "") %> are also referenced by the following
        <%= h(bean.getExtraNoun()) %><%= h(bean.getNoPermissionExtras().size() > 1 ? "s" : "") %>, which you do not have permission to delete:</span>

    <ul>
    <%  int count = 0;
    for (Pair<SecurableResource, ActionURL> entry : bean.getNoPermissionExtras()) {
        if (count >= 50)
        {
            %>(<%= bean.getNoPermissionExtras().size() - count %> <%= h(bean.getExtraNoun()) %>s omitted from list)<%
            break;
        }
        count++;
        %>
        <li>
            <a href="<%= h(entry.getValue()) %>"><%= h(entry.getKey().getResourceName()) %></a>
            <% if (!entry.getKey().getResourceContainer().equals(currentContainer))
            { %>
                (in <a href="<%= entry.getKey().getResourceContainer().getStartURL(bean.getViewContext().getUser()) %>"><%= h(entry.getKey().getResourceContainer().getPath()) %></a>)
            <% } %>
        </li>
    <% } %>
    </ul>
<% } %>

    <% if (bean.getRunsWithPermission().size() > 0) { %>
        <%= h(bean.getRunsWithPermission().size() > 1 ? Integer.toString(bean.getRunsWithPermission().size()) : "One") %> run<%= h(bean.getRunsWithPermission().size() > 1 ? "s" : "") %> will also be deleted:

        <ul>
        <%  int count = 0;
            for (Map.Entry<ExpRun, Container> runEntry : bean.getRunsWithPermission().entrySet()) {
            ExpRun run = runEntry.getKey();
            Container runContainer = runEntry.getValue();
            if (count >= 50)
            {
                %>(<%= bean.getRunsWithPermission().size() - count %> runs omitted from list)<%
                break;
            }
            count++;
            %>
            <li>
                <% ActionURL url = urlProvider(ExperimentUrls.class).getShowRunGraphURL(run); %>
                <a href="<%= url %>"><%= h(run.getName()) %></a>
                <% if (!runContainer.equals(currentContainer))
                { %>
                    (in <a href="<%= runContainer.getStartURL(bean.getViewContext().getUser()) %>"><%= h(runContainer.getPath()) %></a>)
                <% } %>
            </li>
        <% } %>
        </ul>
    <% } %>

    <% if (bean.getRunsWithoutPermission().size() > 0) { %>
        <span class="labkey-error">The <%= h(bean.getObjectType()) %><%= h(bean.getObjects().size() > 1 ? "s" : "") %> are also referenced by the following
            run<%= h(bean.getRunsWithoutPermission().size() > 1 ? "s" : "") %>, which you do not have permission to delete:</span>

        <ul>
        <%  int count = 0;
        for (Map.Entry<ExpRun, Container> runEntry : bean.getRunsWithoutPermission().entrySet()) {
            ExpRun run = runEntry.getKey();
            Container runContainer = runEntry.getValue();
            if (count >= 50)
            {
                %>(<%= bean.getRunsWithoutPermission().size() - count %> runs omitted from list)<%
                break;
            }
            count++;
            %>
            <li>
                <% ActionURL url = urlProvider(ExperimentUrls.class).getShowRunGraphURL(run); %>
                <a href="<%= url %>"><%= h(run.getName()) %></a>
                <% if (!runContainer.equals(currentContainer))
                { %>
                    (in <a href="<%= runContainer.getStartURL(bean.getViewContext().getUser()) %>"><%= h(runContainer.getPath()) %></a>)
                <% } %>
            </li>
        <% } %>
        </ul>
    <% } %>

    <form action="<%= h(bean.getViewContext().cloneActionURL().deleteParameters()) %>" method="post">
        <%
            if (bean.getViewContext().getRequest().getParameterValues(DataRegion.SELECT_CHECKBOX_NAME) != null)
            {
                for (String selectedValue : bean.getViewContext().getRequest().getParameterValues(DataRegion.SELECT_CHECKBOX_NAME))
                { %>
                    <input type="hidden" name="<%= h(DataRegion.SELECT_CHECKBOX_NAME) %>" value="<%= h(selectedValue) %>" /><%
                }
            }
        %>
        <% if (bean.getSingleObjectRowId() != null) { %>
            <input type="hidden" name="singleObjectRowId" value="<%= bean.getSingleObjectRowId() %>" />
        <% }
        if (bean.getDataRegionSelectionKey() != null) { %>
            <input type="hidden" name="<%= h(DataRegionSelection.DATA_REGION_SELECTION_KEY) %>" value="<%= h(bean.getDataRegionSelectionKey()) %>" />
        <% }
        if (bean.getReturnUrl() != null)
        { %>
            <input type="hidden" name="returnURL" value="<%= h(bean.getReturnUrl()) %>"/>
        <% } %>
        <input type="hidden" name="forceDelete" value="true"/>
        <% if (bean.getRunsWithoutPermission().isEmpty() && bean.getNoPermissionExtras().isEmpty() )
        { %>
            <%= generateSubmitButton("Confirm Delete") %>
        <% } %>
        <%= text(bean.getReturnUrl() == null || bean.getReturnUrl().isEmpty()? generateButton("Cancel", buildURL(ExperimentController.BeginAction.class)) : generateButton("Cancel", bean.getReturnUrl()))%>
    </form>
<% } %>
