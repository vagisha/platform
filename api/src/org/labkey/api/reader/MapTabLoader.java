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
package org.labkey.api.reader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.util.CloseableIterator;

import java.io.IOException;
import java.util.*;

/**
 * kevink
 */
public class MapTabLoader extends DataLoader<Map<String, Object>>
{
    String[] headers;
    String[][] data;

    public MapTabLoader(List<Map<String, String>> rows) throws IOException
    {
        convertToArrays(rows);
        _skipLines = rows.size() > 0 ? 1 : 0;
    }

    // Convert the list of maps.
    // The UploadSamplesHelper changes the ColumnDescriptor.name to
    // propertyURIs after inferring the columns but before calling load()
    // causing the map.get(column.name) on each row to fail.
    private void convertToArrays(List<Map<String, String>> rows)
    {
        List<String[]> lineFields = new ArrayList<String[]>(rows.size());

        if (rows.size() > 0)
        {
            Collection<String> keys = rows.get(0).keySet();
            headers = keys.toArray(new String[keys.size()]);
            lineFields.add(headers);

            for (Map<String, String> row : rows)
            {
                if (!(row instanceof CaseInsensitiveMapWrapper))
                    row = new CaseInsensitiveMapWrapper<String>(row);

                ArrayList<String> values = new ArrayList<String>(headers.length);
                for (String header : headers)
                {
                    String value = row.get(header);
                    values.add(value);
                }
                lineFields.add(values.toArray(new String[values.size()]));
            }
        }

        data = lineFields.toArray(new String[rows.size()][]);
    }

    @Override
    public String[][] getFirstNLines(int n) throws IOException
    {
        String[][] first = Arrays.copyOf(data, Math.min(n, data.length), String[][].class);
        return first;
    }

    @Override
    public CloseableIterator<Map<String, Object>> iterator()
    {
        try
        {
            return new MapLoaderIterator();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close()
    {
    }

    public class MapLoaderIterator extends DataLoaderIterator
    {
        protected MapLoaderIterator()
                throws IOException
        {
            super(_skipLines, false);
        }

        @Override
        protected String[] readFields()
        {
            if (lineNum() < data.length)
                return data[lineNum()];

            return null;
        }

        public void close() throws IOException
        {
        }
    }

    public static class MapLoaderTestCase extends TestCase
    {
        public static Test suite()
        {
            return new TestSuite(MapLoaderTestCase.class);
        }

        public MapLoaderTestCase()
        {
            this("MapLoader Test");
        }

        public MapLoaderTestCase(String name)
        {
            super(name);
        }

        public void testLoad() throws Exception
        {
            Map<String, String> row1 = new LinkedHashMap<String, String>();
            row1.put("name", "bob");
            row1.put("date", "1/2/2006");
            row1.put("number", "1.1");

            Map<String, String> row2 = new HashMap<String, String>();
            row2.put("name", "jim");
            row2.put("date", "");
            row2.put("number", "");

            Map<String, String> row3 = new CaseInsensitiveHashMap<String>();
            row3.put("Name", "sally");
            row3.put("Date", "2-Jan-06");
            row3.put("Number", "1.2");

            List<Map<String, String>> rows = Arrays.asList(row1, row2, row3);
            MapTabLoader loader = new MapTabLoader(rows);

            ColumnDescriptor[] cd = loader.getColumns();
            assertEquals("name",   cd[0].name); assertEquals(String.class, cd[0].clazz);
            assertEquals("date",   cd[1].name); assertEquals(Date.class,   cd[1].clazz);
            assertEquals("number", cd[2].name); assertEquals(Double.class, cd[2].clazz);

            List<Map<String, Object>> data = loader.load();
            assertEquals("bob", data.get(0).get("name"));
            assertEquals(new GregorianCalendar(2006, 0, 2).getTime(), data.get(0).get("date"));
            assertEquals(1.1, data.get(0).get("number"));
        }
    }
}
