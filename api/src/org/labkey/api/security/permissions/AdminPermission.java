/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
package org.labkey.api.security.permissions;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.UserPrincipal;

import java.util.HashSet;
import java.util.Set;

/*
* User: Dave
* Date: Apr 28, 2009
* Time: 4:02:35 PM
*/
public class AdminPermission extends AbstractPermission
{
    public AdminPermission()
    {
        this("Administrate", "Users may perform general administration");
    }

    protected AdminPermission(String name, String description)
    {
        super(name, description);
    }

    @NotNull
    @Override
    public Set<UserPrincipal> getExcludedPrincipals()
    {
        Set<UserPrincipal> principals = new HashSet<>();
        principals.add(SecurityManager.getGroup(Group.groupGuests));
        principals.add(SecurityManager.getGroup(Group.groupUsers));
        return principals;
    }
}