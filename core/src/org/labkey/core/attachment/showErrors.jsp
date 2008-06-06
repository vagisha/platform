<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.attachment.AttachmentServiceImpl" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AttachmentServiceImpl.ErrorView me = (AttachmentServiceImpl.ErrorView) HttpView.currentView();
%>
<%=formatMissedErrors("form")%>
<%=null != me.errorHtml ? me.errorHtml : ""%>
<br><br><a href="<%=h(me.returnURL)%>"><img border=0 src="<%=PageFlowUtil.buttonSrc("Continue")%>"></a>
