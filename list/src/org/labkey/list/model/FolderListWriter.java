/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

package org.labkey.list.model;

import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

/*
* User: adam
* Date: Aug 25, 2009
* Time: 10:11:16 AM
*/
public class FolderListWriter extends BaseFolderWriter
{
    private static final String DEFAULT_DIRECTORY = "lists";

    public String getSelectionText()
    {
        return "Lists";
    }

    @Override
    public boolean includeInType(AbstractFolderContext.ExportType type)
    {
        return AbstractFolderContext.ExportType.ALL == type || AbstractFolderContext.ExportType.STUDY == type;
    }

    @Override
    public void write(Container object, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
    {
        Container c = ctx.getContainer();

        VirtualFile listsDir = root.getDir(DEFAULT_DIRECTORY);

        ListWriter listWriter = new ListWriter();

        if (listWriter.write(c, ctx.getUser(), listsDir))
            ctx.getXml().addNewLists().setDir(DEFAULT_DIRECTORY);
    }

    public static class Factory implements FolderWriterFactory
    {
        public FolderWriter create()
        {
            return new FolderListWriter();
        }
    }
}