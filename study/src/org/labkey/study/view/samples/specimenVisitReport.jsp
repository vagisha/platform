<%
/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.samples.report.SpecimenVisitReport" %>
<%@ page import="org.labkey.study.samples.report.SpecimenVisitReportParameters" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="java.util.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenVisitReportParameters> me = (JspView<SpecimenVisitReportParameters>) HttpView.currentView();
    SpecimenVisitReportParameters bean = me.getModelBean();
    String contextPath = request.getContextPath();

    for (SpecimenVisitReport report : bean.getReports())
    {
        VisitImpl[] visits = report.getVisits();
        int colCount = visits.length + report.getLabelDepth();
%>
<table class="labkey-data-region labkey-show-borders"><colgroup>
    <%
        for (int i = 0; i < colCount; i++)
        {
    %>
        <col>
    <%
        }
    %></colgroup>
    <tr>
        <th style="text-align:left" class="labkey-data-region-title" colspan="<%= colCount %>"><%= h(report.getTitle())%></th>
    </tr>

    <tr class="labkey-alternate-row">
        <%
        if (report.getLabelDepth() > 0)
        {
        %>
        <th class="labkey-data-region-title" colspan="<%= report.getLabelDepth() %>">&nbsp;</th>
        <%
        }
        for (VisitImpl visit : visits)
        {
            String label = visit.getDisplayString();
            %><th class="labkey-col-header" align="center"><%= h(label) %></th><%
        }
        %>
    </tr>
        <%
            if (report.getRows() == null || report.getRows().isEmpty())
            {
                %>
            <tr>
                <td colspan="<%= colCount %>"><em>No data to show.</em></td>
            </tr>
                <%
            }
            else
            {
                int rowIndex = 0;
                String[] previousTitleHierarchy = null;
                for (SpecimenVisitReport.Row row : (Collection<SpecimenVisitReport.Row>) report.getRows())
                {
                    String[] currentTitleHierarchy = row.getTitleHierarchy();
                    %><tr class="<%= (rowIndex++)%2==1 ? "labkey-alternate-row" : "labkey-row" %>"  style="vertical-align:top">
                    <%
                    for (int i = 0; i < currentTitleHierarchy.length; i++)
                    {
                        String titleElement = currentTitleHierarchy[i];
                        boolean outputElement = previousTitleHierarchy == null;
                        for (int j = i; j >= 0 && !outputElement; j--)
                        {
                            String currentRow = currentTitleHierarchy[j];
                            String previousRow = previousTitleHierarchy[j];
                            if (currentRow == null)
                                currentRow = "]";
                            if (previousRow == null)
                                previousRow = "";
                            outputElement = !currentRow.equals(previousRow);
                        }
                        String style = "border-bottom:0;border-right:1px solid #E0E0E0;border-top:solid " + (outputElement ? "1px" : "0px") + " #808080";
                    %>
                        <td <%= i < currentTitleHierarchy.length - 1 ? "class=\"labkey-blank-cell\"" : ""%> style="<%= style %>">
                            <%= outputElement ? h(titleElement != null ? titleElement : "[unspecified]") : "&nbsp;" %>
                        </td>
                    <%
                    }

                    previousTitleHierarchy = currentTitleHierarchy;
                    for (VisitImpl visit : visits)
                    {
                        %><td align="center"><%
                            %><%= row.getCellHtml(visit) %><%
                        %></td><%
                    }
                    %></tr><%
                }
            }
%>
        </table><br><br>
<%
    }
%>