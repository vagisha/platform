/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.study.security.roles;

import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.roles.AbstractRole;
import org.labkey.study.StudyModule;
import org.labkey.study.security.permissions.RequestSpecimensPermission;

/*
* User: Dave
* Date: May 18, 2009
* Time: 2:23:42 PM
*/
public class SpecimenRequesterRole extends AbstractRole
{
    public SpecimenRequesterRole()
    {
        super("Specimen Requester",
                "Specimen Requesters may request specimen vials.",
                StudyModule.class,
                RequestSpecimensPermission.class);
        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
    }
}