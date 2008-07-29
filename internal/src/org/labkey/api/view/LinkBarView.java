/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.common.util.Pair;

import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: Tamra Myers
 * Date: Jun 2, 2006
 * Time: 3:29:33 PM
 */
public class LinkBarView extends WebPartView
{
    private Pair<String, String>[] _links;
    private boolean _drawLine = false;

    public LinkBarView(Pair<String, String>... links)
    {
        _links = links;
    }

    public void setDrawLine(boolean fDrawLine)
    {
        this._drawLine = fDrawLine;
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        out.write("<table width=\"100%\" cellpadding=0><tr><td>");
        for (Pair<String, String> link : _links)
        {
            out.write("[<a href=\"" + link.second + "\">" + link.first + "</a>]&nbsp;");
        }
        out.write("</td></tr>");
        if(_drawLine)
        {
            out.write("<tr style=\"height:1;\"><td colspan=3 class=\"labkey-title-area-line\"><img height=1 width=1 src=\"" +
                    getViewContext().getContextPath()+ "/_.gif\"></td></tr>");
        }
        out.write("</table>");
    }
}
