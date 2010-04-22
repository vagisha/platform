/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.NavTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Apr 8, 2010
 * Time: 11:06:49 AM
 */

/**
 * Represents configuration information for a specific button in a button bar configuration.
 */
public class UserDefinedButtonConfig implements ButtonConfig
{
    private String _text;
    private String _url;
    private String _onClick;
    private List<NavTree> _menuItems;

    public String getText()
    {
        return _text;
    }

    public void setText(String text)
    {
        _text = text;
    }

    public String getUrl()
    {
        return _url;
    }

    public void setUrl(String url)
    {
        _url = url;
    }

    public String getOnClick()
    {
        return _onClick;
    }

    public void setOnClick(String onClick)
    {
        _onClick = onClick;
    }

    public void setMenuItems(List<NavTree> items)
    {
        _menuItems = items;
    }

    public DisplayElement createButton(List<DisplayElement> originalButtons)
    {
        if (null != _menuItems)
        {
            MenuButton btn = new MenuButton(_text);
            for (NavTree item : _menuItems)
            {
                btn.addMenuItem(item);
            }
            return btn;
        }
        else
        {
            ActionButton btn = new ActionButton(_text);
            if (null != _url)
                btn.setURL(_url);
            if (null != _onClick)
                btn.setScript(_onClick, false);

            return btn;
        }
    }
}
