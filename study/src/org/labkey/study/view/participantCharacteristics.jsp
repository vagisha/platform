<%
/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
<%@ page import="org.apache.commons.beanutils.ConvertUtils"%>
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.collections.CsvSet"%>
<%@ page import="org.labkey.api.data.SimpleFilter"%>
<%@ page import="org.labkey.api.data.Sort"%>
<%@ page import="org.labkey.api.data.TableInfo"%>
<%@ page import="org.labkey.api.data.TableSelector"%>
<%@ page import="org.labkey.api.exp.PropertyDescriptor"%>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.ReportService" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.view.ChartDesignerBean" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.study.DataSet" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.controllers.DatasetController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.reports.StudyChartQueryReport" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    ViewContext context = HttpView.currentContext();
    UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "study");
    JspView<StudyManager.ParticipantViewConfig> me = (JspView<StudyManager.ParticipantViewConfig>) HttpView.currentView();
    StudyManager.ParticipantViewConfig bean = me.getModelBean();

    ChartDesignerBean chartBean = new ChartDesignerBean();

    chartBean.setReportType(StudyChartQueryReport.TYPE);
    chartBean.setSchemaName(schema.getSchemaName());
    String currentUrl = bean.getRedirectUrl();
    if (currentUrl == null)
        currentUrl = context.getActionURL().getLocalURIString();

    ActionURL url = ReportUtil.getChartDesignerURL(context, chartBean);
    url.setAction(ReportsController.DesignChartAction.class);
    url.addParameter("returnUrl", currentUrl);
    url.addParameter("isParticipantChart", "true");
    url.addParameter("participantId", bean.getParticipantId());

    StudyManager manager = StudyManager.getInstance();
    Study study = manager.getStudy(context.getContainer());

    User user = (User) request.getUserPrincipal();
    DataSetDefinition[] datasets = manager.getDataSetDefinitions(study);
    Map<Integer, String> expandedMap = StudyController.getExpandedState(context, bean.getDatasetId());
    boolean updateAccess = study.getContainer().hasPermission(user, UpdatePermission.class);
%>

<table class="labkey-data-region">

    <%
        for (DataSetDefinition dataSet : datasets)
        {
            // Only interested in default-visible visible demographic data
            if (!dataSet.isDemographicData() || !dataSet.isShowByDefault())
                continue;

            String typeURI = dataSet.getTypeURI();
            if (null == typeURI)
                continue;

            int datasetId = dataSet.getDataSetId();
            boolean expanded = true;
            if ("collapse".equalsIgnoreCase(expandedMap.get(datasetId)))
                expanded = false;

            // sort the properties so they appear in the same order as the grid view
            PropertyDescriptor[] pds = sortProperties(StudyController.getParticipantPropsFromCache(HttpView.getRootContext(), typeURI), dataSet, HttpView.getRootContext());
            if (!dataSet.canRead(user))
            {   if (user != User.getSearchUser())
                {
                    %><tr class="labkey-header"><th nowrap align="left" class="labkey-expandable-row-header"><%=h(dataSet.getDisplayString())%></th><td nowrap align="left" class="labkey-expandable-row-header">(no access)</td></tr><%
                }
                continue;
            }

            %>
            <tr class="labkey-header">
            <th nowrap colspan="<%=2%>" align="left" class="labkey-expandable-row-header">
                <a title="Click to expand/collapse" href="<%=new ActionURL(StudyController.ExpandStateNotifyAction.class, study.getContainer()).addParameter("datasetId", Integer.toString(datasetId)).addParameter("id", Integer.toString(bean.getDatasetId()))%>" onclick="return toggleLink(this, true);">
                    <img src="<%= context.getContextPath() %>/_images/<%= expanded ? "minus.gif" : "plus.gif" %>" alt="Click to expand/collapse">
                    <%=h(dataSet.getDisplayString())%>
                </a><%
            if (null != StringUtils.trimToNull(dataSet.getDescription()))
            {
                %><%=PageFlowUtil.helpPopup(dataSet.getDisplayString(), dataSet.getDescription())%><%
            }
            %></th>
            </tr>
            <%

            for (Report report : ReportService.get().getReports(user, study.getContainer(), Integer.toString(datasetId)))
            {
                if (updateAccess)
                {
                %>
                <tr style="<%=expanded ? "" : "display:none"%>">
                    <td><a href="<%=new ActionURL(ReportsController.DeleteReportAction.class, study.getContainer()).addParameter(ReportDescriptor.Prop.redirectUrl.name(), currentUrl).addParameter(ReportDescriptor.Prop.reportId.name(), report.getDescriptor().getReportId().toString())%>">[remove]</a></td>
                </tr>
                <%
                }
                %>
                <tr style="<%=expanded ? "" : "display:none"%>">
                    <td><img src="<%=new ActionURL(ReportsController.PlotChartAction.class, study.getContainer()).addParameter("participantId", bean.getParticipantId()).addParameter(ReportDescriptor.Prop.reportId.name(), report.getDescriptor().getReportId().toString())%>"></td>
                </tr>
                <%
            }

            int row = 0;

            // Cache the request for data, so that we don't have to repeat this query
            // once for each row and column, but only once per row.
            TableInfo datasetTable = dataSet.getTableInfo(user);
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(StudyService.get().getSubjectColumnName(dataSet.getContainer()), bean.getParticipantId());

            Sort sort;
            if (study.getTimepointType() != TimepointType.VISIT)
            {
                sort = new Sort("-Date");
            }
            else
            {
                sort = new Sort("-SequenceNum");
            }


            Map<String, Object> result = new TableSelector(datasetTable, new CsvSet("lsid," + StudyService.get().getSubjectColumnName(dataSet.getContainer()) + ",Date,SequenceNum"), filter, sort).getObject(Map.class);
            String lsid = result != null ? (String)result.get("lsid") : null;
            Map<String,Object> datasetRow = null;

            if (lsid != null)
            {
                datasetRow = StudyService.get().getDatasetRow(user, context.getContainer(), datasetId, lsid);
            }

            boolean editAccess = dataSet.canWrite(user);        
            if (datasetRow == null)
            {
                if (editAccess)
                {
                    ActionURL addAction = new ActionURL(DatasetController.InsertAction.class, context.getContainer());
                    addAction.addParameter("datasetId", datasetId);
                    addAction.addParameter("quf_ParticipantId", bean.getParticipantId());
                    
                    %><td colspan="2" class="labkey-alternate-row"><%=textLink("add", addAction.getLocalURIString())%></td> <%
                }

                continue;
            }

            if (editAccess)
            {
                %>
                <tr class="labkey-alternate-row" style="<%=expanded ? "" : "display:none"%>">
                    <td colspan="2"><%

                            ActionURL editAction = new ActionURL(DatasetController.UpdateAction.class, context.getContainer());
                            editAction.addParameter("datasetId", datasetId);
                            editAction.addParameter("lsid", lsid);

                        %><%=textLink("edit data", editAction.getLocalURIString())%></td>
                </tr>
                <%
            }

            for (PropertyDescriptor pd : pds)
            {
                if (pd == null) continue;
                row++;
                String className = row % 2 == 0 ? "labkey-alternate-row" : "labkey-row";
                String labelName = pd.getLabel();
                if (StringUtils.isEmpty(labelName))
                    labelName = pd.getName();
                %>
                <tr class="<%=className%>" style="<%=expanded ? "" : "display:none"%>"><td align="left" nowrap><%=h(labelName)%></td><%

                    Object value = datasetRow.get(pd.getName());
                    %><td><%= (null == value ? "&nbsp;" : h(ConvertUtils.convert(value), true))%></td><%
                   
            %></tr><%
            }
        }
    %>
</table>
<%!
PropertyDescriptor[] sortProperties(PropertyDescriptor[] pds, DataSet dsd, ViewContext context)
{
    final Map<String, Integer> sortMap = StudyController.getSortedColumnList(context, dsd);
    if (sortMap != null && !sortMap.isEmpty())
    {
        final PropertyDescriptor[] props = new PropertyDescriptor[sortMap.size()];
        for (PropertyDescriptor p : pds)
        {
            if (sortMap.containsKey(p.getName()))
                props[sortMap.get(p.getName())] = p;
        }
        return props;
    }
    return pds;
}
%>
