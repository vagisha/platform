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
package org.labkey.api.announcements.api;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Nick
 * Date: Jun 30, 2010
 * Time: 5:50:52 PM
 */
public class AnnouncementService
{
    static private Interface instance;
    
    public static final String MODULE_NAME = "Announcement";
    
    static public Interface get()
    {
        return instance;
    }

    static public void setInstance(Interface impl)
    {
        instance = impl;
    }

    public interface Interface
    {
        // IRUD (Insert, Read, Update, Delete)
        Announcement insertAnnouncement(Container c, User u, String title, String body);

        // Get One
        Announcement getAnnouncement(Container container, int RowId);
        
        // Get Many
        List<Announcement> getAnnouncements(Container... containers);

        // Update
        Announcement updateAnnouncement(int RowId, Container c, User u, String title, String body);

        // Delete
        void deleteAnnouncement(Announcement announcement);
    }
}
