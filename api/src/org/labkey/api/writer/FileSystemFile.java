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
package org.labkey.api.writer;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.admin.ImportException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MinorConfigurationException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;

import java.io.*;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:30:23 PM
 */
public class FileSystemFile extends AbstractVirtualFile
{
    private final File _root;

    // Required for xstream serialization on Java 7
    @SuppressWarnings({"UnusedDeclaration"})
    private FileSystemFile()
    {
        _root = null;
    }

    public FileSystemFile(File root)
    {
        ensureWriteableDirectory(root);

        _root = root;
    }

    public String getLocation()
    {
        return _root.getAbsolutePath();
    }

    public PrintWriter getPrintWriter(String filename) throws IOException
    {
        File file = new File(_root, makeLegalName(filename));

        return new PrintWriter(file);
    }

    public OutputStream getOutputStream(String filename) throws IOException
    {
        File file = new File(_root, makeLegalName(filename));

        return new FileOutputStream(file);
    }

    public void saveXmlBean(String filename, XmlObject doc) throws IOException
    {
        try
        {
            XmlBeansUtil.validateXmlDocument(doc, filename);
        }
        catch (XmlValidationException e)
        {
            throw new RuntimeException(e);
        }

        saveXmlBean(filename, doc, XmlBeansUtil.getDefaultSaveOptions());
    }

    // Expose this if/when some caller needs to customize the options
    private void saveXmlBean(String filename, XmlObject doc, XmlOptions options) throws IOException
    {
        File file = new File(_root, makeLegalName(filename));
        doc.save(file, options);
    }

    public VirtualFile getDir(String name)
    {
        return new FileSystemFile(new File(_root, makeLegalName(name)));
    }

    public VirtualFile createZipArchive(String name) throws FileNotFoundException
    {
        return new ZipFile(_root, name);
    }

    public String makeLegalName(String name)
    {
        return makeLegal(name);
    }

    public static String makeLegal(String name)
    {
        return FileUtil.makeLegalName(name);
    }

    public static void ensureWriteableDirectory(File dir)
    {
        if (!dir.exists())
            //noinspection ResultOfMethodCallIgnored
            dir.mkdir();

        if (!dir.isDirectory())
            throw new MinorConfigurationException(dir.getAbsolutePath() + " is not a directory.");

        if (!dir.canWrite())
            throw new MinorConfigurationException("Can't write to " + dir.getAbsolutePath());
    }

    @Override
    public XmlObject getXmlBean(String filename) throws IOException
    {
        File file = new File(_root, makeLegalName(filename));

        if (file.exists())
        {
            try
            {
                return XmlObject.Factory.parse(file, XmlBeansUtil.getDefaultParseOptions());
            }
            catch (XmlException e)
            {
                throw new IOException(e);
            }
        }
        return null;
    }

    @Override
    public InputStream getInputStream(String filename) throws IOException
    {
        File file = new File(_root, makeLegalName(filename));

        if (file.exists())
            return new FileInputStream(file);
        else
            return null;
    }

    @Override
    public String getRelativePath(String filename)
    {
        File file = new File(_root, makeLegalName(filename));
        return ImportException.getRelativePath(_root, file);
    }

    @Override
    public String[] list()
    {
        File[] files = _root.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file)
            {
                return file.isFile();
            }
        });
        String[] fileNames = new String[files.length];
        int i=0;
        for (File file : files)
            fileNames[i++] = file.getName();

        return fileNames;
    }

    @Override
    public String[] listDirs()
    {
        File[] dirs = _root.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file)
            {
                return file.isDirectory();
            }
        });
        String[] dirNames = new String[dirs.length];
        int i=0;
        for (File dir : dirs)
            dirNames[i++] = dir.getName();

        return dirNames;
    }

    @Override
    public boolean delete(String filename)
    {
        File file = new File(_root, makeLegalName(filename));
        return file.delete();
    }

    @Override
    public void close() throws IOException
    {
        // no op
    }
}
