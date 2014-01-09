/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.api.defaults;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.*;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/*
 * User: brittp
 * Date: Jan 27, 2009
 * Time: 4:52:21 PM
 */

@RequiresPermissionClass(AdminPermission.class)
public class SetDefaultValuesAction<FormType extends DomainIdForm> extends DefaultValuesAction<FormType>
{
    private URLHelper _returnUrl;

    public SetDefaultValuesAction()
    {
    }

    public SetDefaultValuesAction(Class formClass)
    {
        super(formClass);
    }

    public void validateCommand(FormType target, Errors errors)
    {
    }

    private class DefaultableDataColumn extends DataColumn implements DefaultableDisplayColumn
    {
        private DomainProperty _property;

        public DefaultableDataColumn(DomainProperty property, ColumnInfo col)
        {
            super(col);
            _property = property;
        }

        public DefaultValueType getDefaultValueType()
        {
            return _property.getDefaultValueTypeEnum();
        }

        public Class getJavaType()
        {
            return _property.getPropertyDescriptor().getPropertyType().getJavaType();
        }

        @Override
        protected boolean renderRequiredIndicators()
        {
            return false;
        }

        @Override
        protected boolean isDisabledInput()
        {
            return _property.getPropertyDescriptor().getPropertyType().getJavaType() == File.class;
        }
    }

    protected class DefaultValueDataRegion extends DataRegion
    {
        public void render(RenderContext ctx, Writer out) throws IOException
        {
            renderFormHeader(ctx, out, MODE_INSERT);
            renderMainErrors(ctx, out);
            out.write("<table>");
            out.write("<tr><th>Field</th>" +
                    "<th>Initial/Default Value</th>" +
                    "<th>Default type</th><tr>");
            for (DisplayColumn renderer : getDisplayColumns())
            {
                if (!shouldRender(renderer, ctx) || !(renderer instanceof DefaultableDisplayColumn))
                    continue;
                renderInputError(ctx, out, 1, renderer);
                out.write("<tr>");
                renderer.renderDetailsCaptionCell(ctx, out);
                renderer.renderInputCell(ctx, out, 1);
                out.write("<td>");
                if (((DefaultableDisplayColumn) renderer).getJavaType() == File.class)
                    out.write("Defaults cannot be set for file fields.");
                else
                {
                    DefaultValueType defaultType = ((DefaultableDisplayColumn) renderer).getDefaultValueType();
                    if (defaultType == null)
                        defaultType = DefaultValueType.FIXED_EDITABLE;
                    out.write(PageFlowUtil.filter(defaultType.getLabel()));
                    out.write(PageFlowUtil.helpPopup("Default Value Type: " + defaultType.getLabel(), defaultType.getHelpText(), true));
                }
                out.write("</td>");
                out.write("</tr>");
            }
            out.write("</table>");
            ButtonBar bbar = getButtonBar(MODE_INSERT);
            bbar.setStyle(ButtonBar.Style.separateButtons);
            bbar.render(ctx, out);
            renderFormEnd(ctx, out);
        }
    }

    protected DataRegion createDataRegion()
    {
        return new DefaultValueDataRegion();
    }

    public ModelAndView getView(FormType domainIdForm, boolean reshow, BindException errors) throws Exception
    {
        _returnUrl = domainIdForm.getReturnURLHelper();
        Domain domain = getDomain(domainIdForm);
        DomainProperty[] properties = domain.getProperties();
        if (properties.length == 0)
        {
            return new HtmlView("No fields are defined for this table.<br><br>" + 
                    PageFlowUtil.generateButton("Cancel", new ActionURL(domainIdForm.getReturnUrl())));
        }


        DataRegion rgn = createDataRegion();
        TableInfo baseTable = OntologyManager.getTinfoObject();
        rgn.setTable(baseTable);
        for (DomainProperty dp : properties)
        {
            ColumnInfo info = dp.getPropertyDescriptor().createColumnInfo(baseTable, "objecturi", getUser(), getContainer());
            rgn.addDisplayColumn(new DefaultableDataColumn(dp, info));
        }
        InsertView view = new InsertView(rgn, errors);

        if (reshow)
            view.setInitialValues(domainIdForm.getRequest().getParameterMap());
        else
        {
            Map<DomainProperty, Object> defaults = DefaultValueService.get().getDefaultValues(domainIdForm.getContainer(), domain);
            Map<String, Object> formDefaults = new HashMap<>();
            for (Map.Entry<DomainProperty, Object> entry : defaults.entrySet())
            {
                if (entry.getValue() != null)
                {
                    String stringValue = entry.getValue().toString();
                    formDefaults.put(ColumnInfo.propNameFromName(entry.getKey().getName()), stringValue);
                }
            }
            view.setInitialValues(formDefaults);
        }

        boolean defaultsDefined = DefaultValueService.get().hasDefaultValues(domainIdForm.getContainer(), domain, false);

        ButtonBar bbar = new ButtonBar();
        bbar.setStyle(ButtonBar.Style.separateButtons);
        ActionURL setDefaultsURL = getViewContext().getActionURL().clone().deleteParameters();
        ActionButton saveButton = new ActionButton(setDefaultsURL, "Save Defaults");
        saveButton.setActionType(ActionButton.Action.POST);
        bbar.add(saveButton);
        if (defaultsDefined)
        {
            ActionURL clearURL = new ActionURL(ClearDefaultValuesAction.class, domainIdForm.getContainer());
            ActionButton clearButton = new ActionButton(clearURL, "Clear Defaults");
            clearButton.setActionType(ActionButton.Action.POST);
            bbar.add(clearButton);
        }
        bbar.add(new ActionButton("Cancel", _returnUrl));
        rgn.addHiddenFormField("domainId", "" + domainIdForm.getDomainId());
        rgn.addHiddenFormField(ActionURL.Param.returnUrl, domainIdForm.getReturnUrl());
        rgn.setButtonBar(bbar);

        List<Container> overridees = DefaultValueService.get().getDefaultValueOverridees(domainIdForm.getContainer(), domain);
        boolean inherited = !overridees.isEmpty();
        StringBuilder headerHtml = new StringBuilder("<span class=\"normal\">");
        if (!defaultsDefined)
        {
            if (inherited)
                headerHtml.append("This table inherits default values from a parent folder.");
            else
                headerHtml.append("No defaults are defined for this table in this folder.");
        }
        else
            headerHtml.append("Defaults are currently defined for this table in this folder.");
        headerHtml.append("</span>");
        if (!domain.getContainer().equals(getContainer()) && domain.getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            ActionURL url = new ActionURL(this.getClass(), domain.getContainer());
            url.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().getLocalURIString());
            url.addParameter("domainId", domain.getTypeId());
            headerHtml.append(PageFlowUtil.textLink("edit default values for this table in " + PageFlowUtil.filter(domain.getContainer().getPath()), url));
        }
        headerHtml.append("<br><br>Default values set here will be inherited by all sub-folders that use this table and do not specify their own defaults.");

        HtmlView headerView = new HtmlView(headerHtml.toString());

        StringBuilder overrideHtml = new StringBuilder();
        if (!overridees.isEmpty())
        {
            overrideHtml.append("<span class=\"normal\">");
            if (!defaultsDefined)
                overrideHtml.append("If saved, these values will override defaults set in the following folder:");
            else
                overrideHtml.append("These values override defaults set in the following folder:");
            overrideHtml.append("</span><br>");
            Container container = overridees.get(overridees.size() - 1);
            appendEditURL(overrideHtml, container, domain, domainIdForm.getReturnUrl());
        }
        List<Container> overriders = DefaultValueService.get().getDefaultValueOverriders(domainIdForm.getContainer(), domain);
        if (!overriders.isEmpty())
        {
            if (!overridees.isEmpty())
                overrideHtml.append("<br>");
            overrideHtml.append("<span class=\"normal\">");
            if (!defaultsDefined)
                overrideHtml.append("If saved, these values will be overridden by defaults the following folder(s):");
            else
                overrideHtml.append("These values are overridden by defaults set in the following folder(s):");
            overrideHtml.append("</span><br>");
            for (Container container : overriders)
                appendEditURL(overrideHtml, container, domain, domainIdForm.getReturnUrl());
        }

        return new VBox(headerView, view, new HtmlView(overrideHtml.toString()));
    }

    private void appendEditURL(StringBuilder builder, Container container, Domain domain, ReturnURLString returnUrl)
    {
        ActionURL editURL = new ActionURL(this.getClass(), container);
        editURL.addParameter("domainId", domain.getTypeId());
        editURL.addParameter(ActionURL.Param.returnUrl, returnUrl);
        builder.append("<a href=\"").append(PageFlowUtil.filter(editURL.getLocalURIString())).append("\">");
        builder.append(PageFlowUtil.filter(container.getPath()));
        builder.append("</a><br>");
    }

    public boolean handlePost(FormType domainIdForm, BindException errors) throws Exception
    {
        Domain domain = getDomain(domainIdForm);
        // first, we validate the post:
        boolean failedValidation = false;
        Map<DomainProperty, Object> values = new HashMap<>();
        for (DomainProperty property : domain.getProperties())
        {
            String propName = ColumnInfo.propNameFromName(property.getName());
            String value = domainIdForm.getRequest().getParameter(propName);
            String label = property.getPropertyDescriptor().getNonBlankCaption();
            PropertyType type = property.getPropertyDescriptor().getPropertyType();
            if (value != null && value.length() > 0)
            {
                try
                {
                    Object converted = ConvertUtils.convert(value, type.getJavaType());
                    values.put(property, converted);
                }
                catch (ConversionException e)
                {
                    failedValidation = true;
                    errors.reject(SpringActionController.ERROR_MSG,
                            label + " must be of type " + ColumnInfo.getFriendlyTypeName(type.getJavaType()) + ".");
                }
            }
        }
        if (failedValidation)
            return false;

        try
        {
            if (values.size() > 0)
                DefaultValueService.get().setDefaultValues(domainIdForm.getContainer(), values);
        }
        catch (ExperimentException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            return false;
        }
        return true;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        root.addChild("Edit Type", _returnUrl);
        root.addChild("Set Default Values");
        return root;
    }
}
