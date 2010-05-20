/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
package org.labkey.api.exp.api;

import java.util.List;

/*
* User: jeckels
* Date: Jul 28, 2008
*/
public interface ExpProtocolOutput extends ExpObject
{
    ExpProtocol getSourceProtocol();

    ExpRun getRun();
    Integer getRunId();

    ExpProtocolApplication[] getTargetApplications();
    ExpRun[] getTargetRuns();

    void setSourceApplication(ExpProtocolApplication sourceApplication);
    ExpProtocolApplication getSourceApplication();

    void setRun(ExpRun run);

    List<ExpProtocolApplication> getSuccessorApps();

    List<ExpRun> getSuccessorRuns();

    String getCpasType();
    void setCpasType(String type);
}