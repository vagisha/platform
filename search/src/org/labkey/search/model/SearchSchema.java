/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.search.model;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.TableInfo;

/**
 * User: adam
 * Date: 6/3/13
 * Time: 9:41 PM
 */
public class SearchSchema
{
    private static final SearchSchema INSTANCE = new SearchSchema();

    public static SearchSchema getInstance()
    {
        return INSTANCE;
    }

    private SearchSchema()
    {
    }

    public DbSchema getSchema()
    {
        return DbSchema.get("search", DbSchemaType.Module);
    }

    public TableInfo getCrawlCollectionsTable()
    {
        return getSchema().getTable("CrawlCollections");
    }
}
