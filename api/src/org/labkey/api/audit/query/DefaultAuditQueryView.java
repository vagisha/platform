/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.audit.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;

import java.io.PrintWriter;

/**
 * User: Karl Lum
 * Date: Oct 5, 2007
 */
public class DefaultAuditQueryView extends AuditLogQueryView
{
    public DefaultAuditQueryView(UserSchema schema, QuerySettings settings, @Nullable SimpleFilter filter)
    {
        super(schema, settings, filter);
    }

    public void addDisplayColumn(int index, DisplayColumn dc)
    {
    }

    protected void renderDataRegion(PrintWriter out) throws Exception
    {
    }
}
