/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
package org.labkey.api.visualization;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.Report;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

public interface VisualizationUrls extends UrlProvider
{
    ActionURL getTimeChartDesignerURL(Container container);
    ActionURL getTimeChartDesignerURL(Container container, User user, QuerySettings settings);
    ActionURL getTimeChartDesignerURL(Container container, Report report);
    ActionURL getViewerURL(Container container, Report report);

    // generic chart urls
    ActionURL getGenericChartDesignerURL(Container container, User user, @Nullable QuerySettings settings, @Nullable GenericChartReport.RenderType type);
}
