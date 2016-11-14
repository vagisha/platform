<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.security.permissions.DeletePermission" %>
<%@ page import="org.labkey.api.util.Button" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.pipeline.analysis.AnalysisController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("codemirror");
    }

    private Button.ButtonBuilder makeButton(AnalysisController.ProtocolTask action, ActionURL urlBase)
    {
        String actionStr = action.toString();
        String confirmMsg = "Are you sure you want to " + actionStr + " this protocol?";
        String urlStr = urlBase.clone().addParameter("action", actionStr).toLocalString(true);
        return button(actionStr).onClick("if (!window.confirm('"+confirmMsg + "')) {return false;} this.form.action='" + urlStr + "'").submit(true);
    }
%>
<%
    AnalysisController.ProtocolDetailsForm form = ((HttpView<AnalysisController.ProtocolDetailsForm>) HttpView.currentView()).getModelBean();
    ActionURL returnUrl = form.getReturnActionURL(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer()));
    Button.ButtonBuilder cancelButton = button("Cancel").href(returnUrl);
%>

<script type="text/javascript">
    Ext4.onReady(function()
    {
        var el = Ext4.get("xmlParameters");
        if (el)
        {
            this.codeMirror = CodeMirror.fromTextArea(el.dom, {
                mode: "xml",
                readOnly: "nocursor",
                viewportMargin: Infinity
            });
            codeMirror.setValue(<%=q(form.getXml())%>);
            document.getElementsByClassName("CodeMirror")[0].style.backgroundColor = window.getComputedStyle(document.getElementById("bodyElement")).backgroundColor;
        }
    });
</script>
<style type="text/css">
    .CodeMirror {
        height: auto;
    }
</style>
<labkey:errors />
<% if (null == form.getXml()) { %>
    <div class=labkey-error>Protocol not found.</div>
    <br/>
    <%=cancelButton%>
<% } else { %>
    <% if (getContainer().hasPermission(getUser(), DeletePermission.class)) {
            ActionURL urlBase = new ActionURL(AnalysisController.ProtocolManagementAction.class, getViewContext().getContainer());
            urlBase.addParameter("taskId", form.getTaskId());
            urlBase.addParameter("name", form.getName());
            urlBase.addReturnURL(returnUrl);
        %>
        <textarea id="xmlParameters"></textarea>
        <br/>
        <labkey:form id="protocol_details_form" method="POST">
            <%= makeButton(form.isArchived() ? AnalysisController.ProtocolTask.unarchive : AnalysisController.ProtocolTask.archive, urlBase)%>
            <%= makeButton(AnalysisController.ProtocolTask.delete, urlBase)%>
            <%=cancelButton%>
        </labkey:form>
    <% } %>
<% } %>

