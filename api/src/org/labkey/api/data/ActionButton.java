/*
 * Copyright (c) 2004-2011 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DisplayElement;
import org.springframework.web.servlet.mvc.Controller;

import java.io.IOException;
import java.io.Writer;

public class ActionButton extends DisplayElement implements Cloneable
{
    public enum Action
    {
        POST("post"), GET("get"), LINK("link"), SCRIPT("script");

        private String _description;

        private Action(String desc)
        {
            _description = desc;
        }

        public String getDescription()
        {
            return _description;
        }

        public String toString()
        {
            return getDescription();
        }
    }

    public static ActionButton BUTTON_DELETE = null;
    public static ActionButton BUTTON_SHOW_INSERT = null;
    public static ActionButton BUTTON_SHOW_UPDATE = null;
    public static ActionButton BUTTON_SHOW_GRID = null;
    public static ActionButton BUTTON_DO_INSERT = null;
    public static ActionButton BUTTON_DO_UPDATE = null;
    public static ActionButton BUTTON_SELECT_ALL = null;
    public static ActionButton BUTTON_CLEAR_ALL = null;

    static
    {
        BUTTON_DELETE = new ActionButton("delete.post", "Delete");
        BUTTON_DELETE.setDisplayPermission(DeletePermission.class);
        BUTTON_DELETE.setRequiresSelection(true, "Are you sure you want to delete the selected row?", "Are you sure you want to delete the selected rows?");
        BUTTON_DELETE.lock();
        assert MemTracker.remove(BUTTON_DELETE);

        BUTTON_SHOW_INSERT = new ActionButton("showInsert.view", "Insert New");
        BUTTON_SHOW_INSERT.setActionType(Action.LINK);
        BUTTON_SHOW_INSERT.setDisplayPermission(InsertPermission.class);
        BUTTON_SHOW_INSERT.lock();
        assert MemTracker.remove(BUTTON_SHOW_INSERT);

        BUTTON_SHOW_UPDATE = new ActionButton("showUpdate.view", "Edit");
        BUTTON_SHOW_UPDATE.setActionType(Action.GET);
        BUTTON_SHOW_UPDATE.setDisplayPermission(UpdatePermission.class);
        BUTTON_SHOW_UPDATE.lock();
        assert MemTracker.remove(BUTTON_SHOW_UPDATE);

        BUTTON_SHOW_GRID = new ActionButton("begin.view?.lastFilter=true", "Show Grid");
        BUTTON_SHOW_GRID.setActionType(Action.LINK);
        BUTTON_SHOW_GRID.lock();
        assert MemTracker.remove(BUTTON_SHOW_GRID);

        BUTTON_DO_INSERT = new ActionButton("insert.post", "Submit");
        BUTTON_DO_INSERT.lock();
        assert MemTracker.remove(BUTTON_DO_INSERT);

        BUTTON_DO_UPDATE = new ActionButton("update.post", "Submit");
        BUTTON_DO_UPDATE.lock();
        assert MemTracker.remove(BUTTON_DO_UPDATE);

        BUTTON_SELECT_ALL = new ActionButton("selectAll", "Select All");
        BUTTON_SELECT_ALL.setScript("setAllCheckboxes(this.form, true);return false;");
        BUTTON_SELECT_ALL.setActionType(ActionButton.Action.GET);
        BUTTON_SELECT_ALL.lock();
        assert MemTracker.remove(BUTTON_SELECT_ALL);

        BUTTON_CLEAR_ALL = new ActionButton("clearAll", "Clear All");
        BUTTON_CLEAR_ALL.setScript("setAllCheckboxes(this.form, false);return false;");
        BUTTON_CLEAR_ALL.setActionType(ActionButton.Action.GET);
        BUTTON_CLEAR_ALL.lock();
        assert MemTracker.remove(BUTTON_CLEAR_ALL);
    }


    private Action _actionType = Action.POST;
    private StringExpression _caption;
    private StringExpression _actionName;
    private StringExpression _url;
    private StringExpression _script;
    private StringExpression _title;
    private String _target;
    private boolean _appendScript;
    protected boolean _requiresSelection;
    private @Nullable String _singularConfirmText;
    private @Nullable String _pluralConfirmText;
    private String _encodedSubmitForm;

    public ActionButton(String caption, URLHelper link)
    {
        _caption = StringExpressionFactory.create(caption);
        _url = StringExpressionFactory.create(link.toString(), true);
        _actionType = Action.LINK;
    }

    public ActionButton(ActionURL url, String caption)
    {
        this(url.getLocalURIString(), caption);
    }

    @Deprecated /** Use version that takes an action class instead */
    public ActionButton(String actionName, String caption)
    {
        _actionName = StringExpressionFactory.create(actionName);
        _caption = StringExpressionFactory.create(caption);
    }

    public ActionButton(Class<? extends Controller> action, String caption)
    {
        this(SpringActionController.getActionName(action) + ".view", caption);
    }

    public ActionButton(ActionURL url, String caption, int displayModes)
    {
        this(url, caption);
        setDisplayModes(displayModes);
    }

    public ActionButton(Class<? extends Controller> action, String caption, int displayModes, Action actionType)
    {
        this(SpringActionController.getActionName(action) + ".view", caption, displayModes, actionType);
    }

    public ActionButton(String actionName, String caption, int displayModes, Action actionType)
    {
        this(actionName, caption);
        setDisplayModes(displayModes);
        setActionType(actionType);
    }

    public ActionButton(ActionButton ab)
    {
        _actionName = ab._actionName;
        _actionType = ab._actionType;
        _caption = ab._caption;
        _script = ab._script;
        _title = ab._title;
        _url = ab._url;
        _target = ab._target;
        _requiresSelection = ab._requiresSelection;
        _pluralConfirmText = ab._pluralConfirmText;
        _singularConfirmText = ab._singularConfirmText;
    }

    public String getActionType()
    {
        return _actionType.toString();
    }

    public void setActionType(Action actionType)
    {
        checkLocked();
        _actionType = actionType;
    }

    public void setActionName(String actionName)
    {
        checkLocked();
        _actionName = StringExpressionFactory.create(actionName);
    }

    public String getActionName(RenderContext ctx)
    {
        return _eval(_actionName, ctx);
    }

    public String getCaption(RenderContext ctx)
    {
        if (null == _caption)
            return _eval(_actionName, ctx);
        else
            return _eval(_caption, ctx);
    }

    public String getCaption()
    {
        if (null != _caption)
            return _caption.getSource();
        else if (null != _actionName)
            return _actionName.getSource();
        return null;    
    }

    public void setCaption(String caption)
    {
        checkLocked();
        _caption = StringExpressionFactory.create(caption);
    }

    public void setURL(ActionURL url)
    {
        setURL(url.getLocalURIString());
    }

    public void setURL(String url)
    {
        checkLocked();
        _actionType = Action.LINK;
        _url = StringExpressionFactory.create(url, true);
    }

    public void setURL(HString url)
    {
        checkLocked();
        _actionType = Action.LINK;
        _url = StringExpressionFactory.create(url.getSource(), true);
    }

    public String getURL(RenderContext ctx)
    {
        return _eval(_url, ctx);
    }

    public String getScript(RenderContext ctx)
    {
        return _eval(_script, ctx);
    }

    public void setScript(String script)
    {
        setScript(script, false);
    }

    public void setScript(String script, boolean appendToDefaultScript)
    {
        checkLocked();
        if (!appendToDefaultScript) // Only change the action type if this is a full script replacement
            _actionType = Action.SCRIPT;
        _script = StringExpressionFactory.create(script);
        _appendScript = appendToDefaultScript;
    }

    public void setTarget(String target)
    {
        _target = target;
    }

    public String getTarget()
    {
        return _target;
    }

    public void setRequiresSelection(boolean requiresSelection)
    {
        setRequiresSelection(requiresSelection, null, null, null);
    }

    // Confirm text strings can include ${selectedCount} -- when the message is rendered, this will be replaced by the actual count.
    public void setRequiresSelection(boolean requiresSelection, @NotNull String singularConfirmText, @NotNull String pluralConfirmText)
    {
        setRequiresSelection(requiresSelection, singularConfirmText, pluralConfirmText, null);
    }

    // Confirm text strings can include ${selectedCount} -- when the message is rendered, this will be replaced by the actual count.
    public void setRequiresSelection(boolean requiresSelection, @Nullable String singularConfirmText, @Nullable String pluralConfirmText, @Nullable String encodedSubmitForm)
    {
        checkLocked();
        _requiresSelection = requiresSelection;
        _singularConfirmText = singularConfirmText;
        _pluralConfirmText = pluralConfirmText;
        _encodedSubmitForm = encodedSubmitForm;
    }

    private String renderDefaultScript(RenderContext ctx) throws IOException
    {
        if (_requiresSelection)
        {
            // We pass in the plural text first since some javascript usages of verifySelected() pass in only a single (plural)
            // confirmation message.
            return "return verifySelected(" +
                        (_encodedSubmitForm != null ? _encodedSubmitForm : "this.form") + ", " +
                        "\"" + (_url != null ? getURL(ctx) : getActionName(ctx)) + "\", " +
                        "\"" + _actionType.toString() + "\", " +
                        "\"rows\"" +
                        (_pluralConfirmText != null ? ", \"" + PageFlowUtil.filter(_pluralConfirmText) + "\"" : "") +
                        (_singularConfirmText != null ? ", \"" + PageFlowUtil.filter(_singularConfirmText) + "\"" : "") +
                    ")";
        }
        else
        {
            String action = getActionName(ctx);
            if (action == null)
                action = getURL(ctx);
            return "this.form.action=\"" +
                    action +
                    "\";this.form.method=\"" +
                    _actionType.toString() + "\";";
        }
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        if (!shouldRender(ctx))
            return;

        StringBuilder attributes = new StringBuilder();

        if (_requiresSelection)
        {
            DataRegion dataRegion = ctx.getCurrentRegion();
            assert dataRegion != null : "ActionButton.setRequiresSelection() needs to be rendered in context of a DataRegion";
            attributes.append(" labkey-requires-selection=\"").append(PageFlowUtil.filter(dataRegion.getName())).append("\"");
        }
        
        if (_actionType.equals(Action.POST) || _actionType.equals(Action.GET))
        {
            StringBuilder onClickScript = new StringBuilder();
            if (null == _script || _appendScript)
                onClickScript.append(renderDefaultScript(ctx));
            if (_script != null)
                onClickScript.append(getScript(ctx));

            attributes.append("name='").append(getActionName(ctx)).append("'");
            out.write(PageFlowUtil.generateSubmitButton(getCaption(ctx), onClickScript.toString(), attributes.toString()));
        }
        else if (_actionType.equals(Action.LINK))
        {
            if (_target != null)
                attributes.append(" target=\"").append(PageFlowUtil.filter(_target)).append("\"");
            out.write(PageFlowUtil.generateButton(getCaption(ctx), (_url != null ? getURL(ctx) : getActionName(ctx)), _script == null ? "" : getScript(ctx),
                    attributes.toString()));
        }
        else
        {
            out.write(PageFlowUtil.generateButton(getCaption(ctx), "#",
                    (_appendScript ? renderDefaultScript(ctx) : "") + getScript(ctx), attributes.toString()));
        }
    }

    public ActionButton clone()
    {
        try
        {
            ActionButton button = (ActionButton) super.clone();
            button._locked = false;
            return button;
        }
        catch (CloneNotSupportedException e)
        {
            throw new RuntimeException("Superclass was expected to be cloneable", e);
        }
    }

    private static String _eval(StringExpression expr, RenderContext ctx)
    {
        return expr == null ? null : expr.eval(ctx);
    }
}
