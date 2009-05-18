/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.writer;

import org.apache.xmlbeans.XmlObject;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.VirtualFile;
import org.labkey.data.xml.query.QueryDocument;
import org.labkey.data.xml.query.QueryType;
import org.labkey.study.model.Study;

import java.io.PrintWriter;
import java.util.List;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 4:49:55 PM
 */
public class QueryWriter implements Writer<Study>
{
    public static final String FILE_EXTENSION = ".sql";
    public static final String META_FILE_EXTENSION =  ".query.xml";

    private static final String DEFAULT_DIRECTORY = "queries";

    public void write(Study study, ExportContext ctx, VirtualFile root) throws Exception
    {
        Container c = study.getContainer();
        List<QueryDefinition> queries = QueryService.get().getQueryDefs(c);

        if (queries.size() > 0)
        {
            ctx.getStudyXml().addNewQueries().setDir(DEFAULT_DIRECTORY);
            root.makeDir(DEFAULT_DIRECTORY);
            VirtualFile queriesDir = root.getDir(DEFAULT_DIRECTORY);

            for (QueryDefinition query : queries)
            {
                PrintWriter sql = queriesDir.getPrintWriter(query.getName() + FILE_EXTENSION);
                sql.println(query.getSql());
                sql.close();

                QueryType qtDoc = QueryType.Factory.newInstance();
                qtDoc.setDescription(query.getDescription());
                qtDoc.setHidden(query.isHidden());
                qtDoc.setSchemaName(query.getSchemaName());

                if (null != query.getMetadataXml())
                {
                    XmlObject xObj = XmlObject.Factory.parse(query.getMetadataXml());
                    qtDoc.setMetadata(xObj);
                }

                QueryDocument qDoc = QueryDocument.Factory.newInstance();
                qDoc.setQuery(qtDoc);

                PrintWriter pw = queriesDir.getPrintWriter(query.getName() + META_FILE_EXTENSION);
                StudyXmlWriter.saveDoc(pw, qDoc);
            }
        }
    }
}
