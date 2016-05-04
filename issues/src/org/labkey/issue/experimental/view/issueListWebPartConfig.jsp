<%
/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.issue.experimental.IssuesListView" %>
<%@ page import="org.labkey.issue.model.IssueListDef" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart webPart = me.getModelBean();
    ViewContext context = getViewContext();
    Map<String, String> pm = webPart.getPropertyMap();

    Set<String> issueDefs = new HashSet<>();
    issueDefs.addAll(IssueManager.getIssueListDefs(getContainer()).stream().map(IssueListDef::getName).collect(Collectors.toSet()));
    String selected = pm.get(IssuesListView.ISSUE_LIST_DEF_NAME);
%>

<labkey:form method="post" action="<%=h(webPart.getCustomizePostURL(context))%>">
    <table>
        <tr>
            <td class="labkey-form-label">Issue List Definition:</td>
            <td>
                <select name="<%=IssuesListView.ISSUE_LIST_DEF_NAME%>">
                    <labkey:options value="<%=selected%>" set="<%=issueDefs%>"/>
                </select>
            </td>
        </tr>
    </table><p/>
    <labkey:button text="Submit"/>
</labkey:form>

