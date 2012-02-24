/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.announcements.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;

/**
 * User: adam
 * Date: Nov 16, 2006
 * Time: 9:56:01 AM
 */
public class SecureMessageBoardPermissions extends NormalMessageBoardPermissions
{
    public SecureMessageBoardPermissions(Container c, User user, DiscussionService.Settings settings)
    {
        super(c, user, settings);
    }

    public boolean allowRead(@Nullable AnnouncementModel ann)
    {
        if (_user == User.getSearchUser())
            return true;
        
        // Editors can read all messages
        if (hasPermission(SecureMessageBoardReadPermission.class))
            return true;

        // If not an editor, message board must have a member list, user must be on it, and user must have read permissions
        return null != ann && _settings.hasMemberList() && hasPermission(ReadPermission.class) && ann.getMemberList().contains(_user);
    }

    public boolean allowDeleteMessage(AnnouncementModel ann)
    {
        return false;
    }

    public boolean allowDeleteAnyThread()
    {
        return false;
    }

    public boolean allowResponse(AnnouncementModel ann)
    {
        // Editors can respond to any message
        if (hasPermission(SecureMessageBoardRespondPermission.class))
            return true;

        // If not an editor, message board must have a member list, user must be on it, and user must have insert permissions
        return _settings.hasMemberList() && hasPermission(InsertPermission.class) && ann.getMemberList().contains(_user);
    }

    public boolean allowUpdate(AnnouncementModel ann)
    {
        return false;
    }

    public SimpleFilter getThreadFilter()
    {
        SimpleFilter filter = super.getThreadFilter();

        // Filter for non-editors
        if (!hasPermission(SecureMessageBoardReadPermission.class))
            filter.addWhereClause("RowId IN (SELECT MessageId FROM " + _comm.getTableInfoMemberList() + " WHERE UserId = ?)", new Object[]{_user.getUserId()});

        return filter;
    }
}