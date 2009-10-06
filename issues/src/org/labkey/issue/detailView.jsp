<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.DataRegion"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.issue.IssuePage"%>
<%@ page import="org.labkey.issue.IssuesController"%>
<%@ page import="org.labkey.issue.model.Issue" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.HString" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuePage> me = (JspView<IssuePage>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    IssuePage bean = me.getModelBean();
    final Issue issue = bean.getIssue();
    final Container c = context.getContainer();
    final String issueId = Integer.toString(issue.getIssueId());
    final boolean hasUpdatePerms = bean.getHasUpdatePermissions();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c);
%>
<% if (!bean.isPrint())
{
%><form name="jumpToIssue" action="jumpToIssue.view" method="get">
    <table><tr><%

    if (bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("new " + names.singularName.toLowerCase(), PageFlowUtil.getLastFilter(context, IssuesController.issueURL(context.getContainer(), "insert")))%></td><%
    }%>

    <td><%= textLink("view grid", PageFlowUtil.getLastFilter(context, IssuesController.issueURL(context.getContainer(), "list")))%></td><%

    if (bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("update", IssuesController.issueURL(context.getContainer(), "update").addParameter("issueId", issueId))%></td><%
    }

    if (issue.getStatus().equals(Issue.statusOPEN) && bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("resolve", IssuesController.issueURL(context.getContainer(), "resolve").addParameter("issueId", issueId))%></td><%
    }
    else if (issue.getStatus().equals(Issue.statusRESOLVED) && bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("close", IssuesController.issueURL(context.getContainer(), "close").addParameter("issueId", issueId))%></td>
        <td><%= textLink("reopen", IssuesController.issueURL(context.getContainer(), "reopen").addParameter("issueId", issueId))%></td><%
    }
    else if (issue.getStatus().equals(Issue.statusCLOSED) && bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("reopen", IssuesController.issueURL(context.getContainer(), "reopen").addParameter("issueId", issueId))%></td><%
    }
    %><td><%= textLink("print", context.cloneActionURL().replaceParameter("_print", "1"))%></td>
    <td><%= textLink("email prefs", IssuesController.issueURL(context.getContainer(), "emailPrefs").addParameter("issueId", issueId))%></td>
    <td>&nbsp;&nbsp;&nbsp;Jump to <%=names.singularName%>: <input type="text" size="5" name="issueId"/></td>
    </tr></table>
</form><%
}
%>

<table width=640>
    <tr><td class="labkey-wp-title" colspan="3"><%=issueId + " : " + issue.getTitle()%></td></tr>
    <tr>
        <td valign="top" width="34%"><table>
            <tr><td class="labkey-form-label">Status</td><td><%=h(issue.getStatus())%></td></tr>
            <tr><td class="labkey-form-label">Assigned&nbsp;To</td><td><%=h(issue.getAssignedToName(context))%></td></tr>
            <tr><td class="labkey-form-label">Type</td><td><%=h(issue.getType())%></td></tr>
            <tr><td class="labkey-form-label">Area</td><td><%=h(issue.getArea())%></td></tr>
            <tr><td class="labkey-form-label">Priority</td><td><%=bean._toString(issue.getPriority())%></td></tr>
            <tr><td class="labkey-form-label">Milestone</td><td><%=h(issue.getMilestone())%></td></tr>
        </table></td>
        <td valign="top" width="33%"><table>
            <tr><td class="labkey-form-label">Opened&nbsp;By</td><td><%=h(issue.getCreatedByName(context))%></td></tr>
            <tr><td class="labkey-form-label">Opened</td><td><%=bean.writeDate(issue.getCreated())%></td></tr>
            <tr><td class="labkey-form-label">Resolved By</td><td><%=h(issue.getResolvedByName(context))%></td></tr>
            <tr><td class="labkey-form-label">Resolved</td><td><%=bean.writeDate(issue.getResolved())%></td></tr>
            <tr><td class="labkey-form-label">Resolution</td><td><%=h(issue.getResolution())%></td></tr><%
            if (bean.isEditable("resolution") || !"open".equals(issue.getStatus()) && null != issue.getDuplicate())
            {
                %><tr><td class="labkey-form-label">Duplicate</td><td>
                <%=bean.writeInput(new HString("duplicate",false), HString.valueOf(issue.getDuplicate()))%>
                </td></tr><%
            }
%>
            <%=bean.writeCustomColumn(c, new HString("int1"), HString.valueOf(issue.getInt1()), IssuesController.ISSUE_NONE)%>
            <%=bean.writeCustomColumn(c, new HString("int2"), HString.valueOf(issue.getInt2()), IssuesController.ISSUE_NONE)%>
        </table></td>
        <td valign="top" width="33%"><table>
            <tr><td class="labkey-form-label">Changed&nbsp;By</td><td><%=h(issue.getModifiedByName(context))%></td></tr>
            <tr><td class="labkey-form-label">Changed</td><td><%=bean.writeDate(issue.getModified())%></td></tr>
            <tr><td class="labkey-form-label">Closed&nbsp;By</td><td><%=h(issue.getClosedByName(context))%></td></tr>
            <tr><td class="labkey-form-label">Closed</td><td><%=bean.writeDate(issue.getClosed())%></td></tr>

            <%
                if (hasUpdatePerms)
                {
                    %><tr><td class="labkey-form-label">Notify</td><td><%=bean.getNotifyList(c, issue)%></td></tr><%
            }
            %><%=bean.writeCustomColumn(c, new HString("string1",false), issue.getString1(), IssuesController.ISSUE_STRING1)%>
            <%=bean.writeCustomColumn(c, new HString("string2",false), issue.getString2(), IssuesController.ISSUE_STRING2)%>
        </table></td>
    </tr>
</table>
<%
    if (bean.getCallbackURL() != null)
    {
        %><input type="hidden" name="callbackURL" value="<%=bean.getCallbackURL()%>"/><%
    }

    for (Issue.Comment comment : issue.getComments())
    {
        %><hr><table width="100%"><tr><td align="left"><b>
        <%=bean.writeDate(comment.getCreated())%>
        </b></td><td align="right"><b>
        <%=h(comment.getCreatedByName(context))%>
        </b></td></tr></table>
        <%=comment.getComment().getSource()%>
        <%=bean.renderAttachments(context, comment)%><%
    }
%>