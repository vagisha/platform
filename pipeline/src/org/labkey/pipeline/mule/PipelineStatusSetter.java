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
package org.labkey.pipeline.mule;

import org.labkey.pipeline.api.PipelineStatusManager;
import org.labkey.api.data.Container;
import org.apache.log4j.Logger;

import java.sql.SQLException;

/**
 * <code>PipelineStatusSetter</code>
 *
 * @author brendanx
 */
public class PipelineStatusSetter
{
    public void set(EPipelineStatus status) throws Container.ContainerException, SQLException
    {
        PipelineStatusManager.setStatusFile(status.getInfo(), status.getStatusFile());
    }
}
