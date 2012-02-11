/*
 * Copyright (c) 2010-2012 LabKey Corporation
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
package org.labkey.announcements.query;

import org.labkey.announcements.AnnouncementsController;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
 * User: Nick
 * Date: Jul 1, 2010
 * Time: 4:04:11 PM
 */
public class AnnouncementSchema extends UserSchema
{
    public static final String SCHEMA_NAME = "announcement";
    public static final String ANNOUNCEMENT_TABLE_NAME = "Announcement";
    public static final String FORUM_SUBSCRIPTION_TABLE_NAME = "ForumSubscription";
    public static final String ANNOUNCEMENT_SUBSCRIPTION_TABLE_NAME = "AnnouncementSubscription";
    public static final String EMAIL_OPTION_TABLE_NAME = "EmailOption";
    public static final String EMAIL_FORMAT_TABLE_NAME = "EmailFormat";

    public static final int PAGE_TYPE_ID = 0;

    private static final Set<String> TABLE_NAMES;

    static
    {
        Set<String> names = new TreeSet<String>();
        names.add(ANNOUNCEMENT_TABLE_NAME);
        names.add(FORUM_SUBSCRIPTION_TABLE_NAME);
        names.add(ANNOUNCEMENT_SUBSCRIPTION_TABLE_NAME);
        names.add(EMAIL_OPTION_TABLE_NAME);
        names.add(EMAIL_FORMAT_TABLE_NAME);
        TABLE_NAMES = Collections.unmodifiableSet(names);
    }

    public static void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                // Brute force fix for #3453 -- no query access to secure message board  TODO: Filter based on permissions instead.
                if (AnnouncementsController.getSettings(schema.getContainer()).isSecure())
                    return null;
                else
                    return new AnnouncementSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public AnnouncementSchema(User user, Container container)
    {
        super(SCHEMA_NAME, "Contains forums, announcements, and subscriptions", user, container, CommSchema.getInstance().getSchema());
    }

    @Override
    protected TableInfo createTable(String name)
    {
        if (ANNOUNCEMENT_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createAnnouncementTable();
        }
        if (EMAIL_FORMAT_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createEmailFormatTable();
        }
        if (EMAIL_OPTION_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createEmailOptionTable();
        }
        if (FORUM_SUBSCRIPTION_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createForumSubscriptionTable();
        }
        if (ANNOUNCEMENT_SUBSCRIPTION_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createAnnouncementSubscriptionTable();
        }
        return null;
    }

    public TableInfo createEmailFormatTable()
    {
        FilteredTable result = new FilteredTable(CommSchema.getInstance().getTableInfoEmailFormats());
        result.setName(EMAIL_FORMAT_TABLE_NAME);
        result.wrapAllColumns(true);
        result.setPublicSchemaName(getName());
        return result;
    }

    public TableInfo createEmailOptionTable()
    {
        FilteredTable result = new FilteredTable(CommSchema.getInstance().getTableInfoEmailOptions());
        result.setName(EMAIL_OPTION_TABLE_NAME);
        result.addWrapColumn(result.getRealTable().getColumn("EmailOptionId"));
        result.addWrapColumn(result.getRealTable().getColumn("EmailOption"));
        result.setPublicSchemaName(getName());
        result.addCondition(result.getRealTable().getColumn("Type"), "messages");
        return result;
    }

    public TableInfo createAnnouncementSubscriptionTable()
    {
        return new AnnouncementSubscriptionTable(this);
    }

    public TableInfo createForumSubscriptionTable()
    {
        return new ForumSubscriptionTable(this);
    }

    public AnnouncementTable createAnnouncementTable()
    {
        return new AnnouncementTable(this);
    }

    @Override
    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }
}
