/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.view;

import org.labkey.api.util.PageFlowUtil;
import org.apache.commons.lang.StringUtils;

import java.io.StringWriter;
import java.io.Writer;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: May 13, 2008
 * Time: 3:30:25 PM
 */
public class PopupMenu
{
    private NavTree _navTree;
    private Align _align = Align.LEFT;
    private ButtonStyle _buttonStyle = ButtonStyle.MENUBUTTON;

    public PopupMenu()
    {
        this(new NavTree());
    }

    public PopupMenu(NavTree navTree)
    {
        _navTree = navTree;
    }

    public PopupMenu(NavTree navTree, Align align, ButtonStyle buttonStyle)
    {
        _navTree = navTree;
        _align = align;
        _buttonStyle = buttonStyle;
    }

    public NavTree getNavTree()
    {
        return _navTree;
    }

    public void setNavTree(NavTree navTree)
    {
        _navTree = navTree;
    }

    String renderString()
    {
        try
        {
            StringWriter out = new StringWriter();
            render(out);
            return out.toString();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void render(Writer out) throws IOException
    {
        renderMenuButton(out);
        renderMenuScript(out);
    }

    public void renderMenuButton(Writer out) throws IOException
    {
        if (null == _navTree.getKey())
            return;

        if (_buttonStyle == ButtonStyle.TEXTBUTTON)
        {
            out.append("[");
            out.append("<a href='javascript:void(0)'");
            out.append("onclick=\"showMenu(this, '").append(getId()).append("','").append(_align.getExtPosition()).append("');\">");
            out.append(PageFlowUtil.filter(_navTree.getKey())).append(" &gt;&gt;");
            out.append("</a>");
            out.append("]");
        }
        else if (_buttonStyle == ButtonStyle.MENUBUTTON)
        {
            out.append(PageFlowUtil.generateDropDownButton(_navTree.getKey(), "javascript:void(0)",
                    "showMenu(this, '" + getId() + "','" + _align.getExtPosition() + "');"));
        }
        else if (_buttonStyle == ButtonStyle.BOLDTEXT)
        {
            out.append(PageFlowUtil.generateDropDownTextLink(_navTree.getKey(), "javascript:void(0)",
                    "showMenu(this, '" + getId() + "','" + _align.getExtPosition() + "');"));
        }
    }

    public void renderMenuScript(Writer out)
            throws IOException
    {
        out.append(
            "<script type=\"text/javascript\">\n" +
                "LABKEY.requiresClientAPI();\n" +
             "</script>" +
             "<script type=\"text/javascript\">\n" +
                "        new Ext.menu.Menu(");
        out.append(renderMenuModel(_navTree.getChildren(), getId()));
        out.append(
                "         );"+
                "\n" +
                "</script>");
    }

    private String renderMenuModel(NavTree[] trees, String id)
    {
        String sep = "";
        StringBuilder sb = new StringBuilder();

        sb.append("{cls:'extContainer',");
        sb.append("id:").append(PageFlowUtil.jsString(id)).append(",\n");
        sb.append("items:[");
        for (NavTree tree : trees)
        {
            sb.append(sep);
            if (tree == NavTree.MENU_SEPARATOR)
            {
                sb.append("'-'");
                continue;
            }

            String title = tree.getKey();
            sb.append("{").append("text:").append(PageFlowUtil.jsString(title));
            if (StringUtils.isNotEmpty(tree.getId()))
                sb.append(", id:").append(PageFlowUtil.jsString(tree.getId()));
            if (tree.isSelected())
                sb.append(", checked:true");
            if (null != tree.getImageSrc())
                sb.append(", icon:").append(PageFlowUtil.jsString(tree.getImageSrc()));
            if (tree.isDisabled())
                sb.append(", disabled:true");
            if (null != tree.getValue())
                sb.append(",").append("href:").append(PageFlowUtil.jsString(tree.getValue()));
            if (null != tree.getScript())
                sb.append(", handler:function(){").append(tree.getScript()).append("}");
            if (null != tree.getChildren() && tree.getChildren().length > 0)
            {
                sb.append(", hideOnClick:false");
                sb.append(",\n menu:").append(renderMenuModel(tree.getChildren(), null)).append("\n");
            }
            sb.append("}\n");
            sep = ",";
        }
        sb.append("]}");

        return sb.toString();
    }

    public Align getAlign()
    {
        return _align;
    }

    public void setAlign(Align align)
    {
        _align = align;
    }

    public ButtonStyle getButtonStyle()
    {
        return _buttonStyle;
    }

    public void setButtonStyle(ButtonStyle buttonStyle)
    {
        _buttonStyle = buttonStyle;
    }

    public String getId()
    {
        return null != StringUtils.trimToNull(_navTree.getId()) ? _navTree.getId() : String.valueOf(System.identityHashCode(this));
    }

    public enum Align
    {
        LEFT("tl-bl?"),
        RIGHT("tr-br?");

        String extPosition;
        Align(String position)
        {
            extPosition = position;
        }

        public String getExtPosition()
        {
            return extPosition;
        }
    }

    public enum ButtonStyle
    {
        MENUBUTTON("shadedMenu"),
        BOLDTEXT("boldMenu"),
        TEXTBUTTON(null);

        private String _styleText;
        ButtonStyle(String buttonStyle)
        {
            _styleText = buttonStyle;
        }

        public String getStyleText()
        {
            return _styleText;
        }

        public void setStyleText(String buttonStyle)
        {
            _styleText = buttonStyle;
        }
    }
}
