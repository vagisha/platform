<%
/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
<%@ page import="org.labkey.study.controllers.StudyController.ImportVisitMapAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageVisitsAction" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<labkey:errors/>
You can import a visit map in XML format to quickly configure a study. An example visit map is available in the documentation topic: <%=helpLink("importVisitMap", "Import Visit Map")%><br><br>
<labkey:form action="<%=urlFor(ImportVisitMapAction.class)%>" method="post">
    Paste visit map content here:<br>
    <textarea name="content" cols="80" rows="30"></textarea><br>
    <%= button("Import").submit(true) %>&nbsp;<%= button("Cancel").href(urlFor(ManageVisitsAction.class)) %>
</labkey:form>