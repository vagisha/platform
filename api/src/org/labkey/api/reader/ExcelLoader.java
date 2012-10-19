/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.apache.commons.collections15.iterators.ArrayIterator;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileType;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Data loader for Excel files -- can infer columns and return rows of data
 *
 * User: jgarms
 * Date: Oct 22, 2008
 */
public class ExcelLoader extends DataLoader
{
    public static FileType FILE_TYPE = new FileType(Arrays.asList("xlsx", "xls"), "xlsx");
    static {
        FILE_TYPE.setExtensionsMutuallyExclusive(false);
    }

    public static class Factory extends AbstractDataLoaderFactory
    {
        @NotNull @Override
        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new ExcelLoader(file, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull @Override
        public DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new ExcelLoader(is, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull @Override
        public FileType getFileType() { return FILE_TYPE; }
    }

    private InputStream _is = null;
    private Workbook _workbook = null;

    private String sheetName;

    private boolean deleteFileOnClose = false;

    public ExcelLoader(File file) throws IOException
    {
        this(file, false);
    }

    public ExcelLoader(File file, boolean hasColumnHeaders) throws IOException
    {
        this(file, hasColumnHeaders, null);
    }

    public ExcelLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setHasColumnHeaders(hasColumnHeaders);
        _is = is;
    }


    public ExcelLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setHasColumnHeaders(hasColumnHeaders);
        setSource(file);
    }


    private Workbook getWorkbook() throws IOException
    {
        if (null == _workbook)
        {
            try
            {
                if (null != _file)
                {
                    _workbook = ExcelFactory.create(_file);
                }
                else if (null != _is)
                {
                    _workbook = ExcelFactory.create(_is);
                }
            }
            catch (InvalidFormatException e)
            {
                throw new IOException(e.getMessage());
            }
        }
        return _workbook;
    }


    public void setDeleteFileOnClose(boolean del)
    {
        deleteFileOnClose = del;
    }
    
    public List<String> getSheetNames() throws IOException
    {
        List<String> names = new ArrayList<String>();

        Workbook workbook = getWorkbook();
        for (int i=0; i < workbook.getNumberOfSheets(); i++)
            names.add(workbook.getSheetName(i));
        return names;
    }

    public void setSheetName(String sheetName)
    {
        this.sheetName = sheetName;
    }

    private Sheet getSheet() throws IOException
    {
        try
        {
            Workbook workbook = getWorkbook();
            if (sheetName != null)
                return workbook.getSheet(sheetName);
            else
                return workbook.getSheetAt(0);
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            throw new IOException("Invalid Excel file");
        }
    }

    public String[][] getFirstNLines(int n) throws IOException
    {
        Sheet sheet = getSheet();

        List<String[]> cells = new ArrayList<String[]>();
        for (Row currentRow : sheet)
        {
            List<String> rowData = new ArrayList<String>();

            // Excel can report back more rows than exist. If we find no data at all,
            // we should not add a row.
            boolean foundData = false;
            if (currentRow.getPhysicalNumberOfCells() != 0)
            {
                for (int column = 0; column < currentRow.getLastCellNum(); column++)
                {
                    Cell cell = currentRow.getCell(column);
                    if (cell != null)
                    {
                        String data = String.valueOf(PropertyType.getFromExcelCell(cell));

                        if (data != null && !"".equals(data))
                            foundData = true;

                        rowData.add(data != null ? data : "");
                    }
                    else
                        rowData.add("");
                }
                if (foundData)
                    cells.add(rowData.toArray(new String[rowData.size()]));
            }
            if (--n == 0)
                break;
        }
        return cells.toArray(new String[cells.size()][]);
    }

    public CloseableIterator<Map<String, Object>> iterator()
    {
        try
        {
            if (null != _file)
            {
                try
                {
                    return new XlsxIterator();
                }
                catch (InvalidFormatException x)
                {
                    /* fall through */
                }
            }
            return new ExcelIterator();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

/*
    public void finalize() throws Throwable
    {
        workbook.close();
        super.finalize();
    }
*/

    public void close()
    {
        if (deleteFileOnClose && null != _file)
        {
            _file.delete();
        }
    }


//    public List<Map<String, Object>> loadSAXY() throws IOException
//    {
//        try
//        {
//            LinkedList<ArrayList<Object>> output = loadSheetFromXLSX();
//
//            // arrays to maps
//            if (output.isEmpty())
//                return Collections.emptyList();
//            ArrayListMap.FindMap<String> findMap = new ArrayListMap.FindMap<String>(new CaseInsensitiveHashMap<Integer>());
//            List<Object> firstRow = output.removeFirst();
//            for (int index=0 ; index<firstRow.size() ; index++)
//                findMap.put(String.valueOf(firstRow.get(index)),index);
//            ArrayList<Map<String,Object>> maps = new ArrayList<Map<String, Object>>(output.size());
//            for (ArrayList<Object> row : output)
//                maps.add(new _ArrayListMap(findMap,row));
//            return maps;
//        }
//        catch (InvalidFormatException x)
//        {
//            // maybe .xls
//            return load();
//        }
//    }


    private LinkedList<ArrayList<Object>> loadSheetFromXLSX() throws IOException, InvalidFormatException
    {
        try
        {
            LinkedList<ArrayList<Object>> output = new LinkedList<ArrayList<Object>>();

            OPCPackage xlsxPackage = OPCPackage.open(_file.getPath(), PackageAccess.READ);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(xlsxPackage);
            XSSFReader xssfReader = new XSSFReader(xlsxPackage);
            XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            if (iter.hasNext())
            {
                InputStream stream = iter.next();
                InputSource sheetSource = new InputSource(stream);
                SAXParserFactory saxFactory = SAXParserFactory.newInstance();
                SAXParser saxParser = saxFactory.newSAXParser();
                XMLReader sheetParser = saxParser.getXMLReader();
                ContentHandler handler = new SheetHandler(strings, 1, output);
                sheetParser.setContentHandler(handler);
                sheetParser.parse(sheetSource);
            }
            return output;
        }
        catch (InvalidOperationException x)
        {
            throw new InvalidFormatException("File is not an xlsx file: " + _file.getPath());
        }
        catch (InvalidFormatException x)
        {
            throw x;
        }
        catch (IOException x)
        {
            throw x;
        }
        catch (Exception x)
        {
            throw new IOException(x);
        }
    }


    private class XlsxIterator extends DataLoaderIterator
    {
        final List<ArrayList<Object>> grid;

        public XlsxIterator() throws IOException, InvalidFormatException
        {
            super(_skipLines == -1 ? 1 : _skipLines, true);
            grid = loadSheetFromXLSX();
        }

        @Override
        protected Object[] readFields() throws IOException
        {
            if (lineNum() >= grid.size())
                return null;
            ArrayList row = grid.get(lineNum());
            return row.toArray();
        }
    }


    private class ExcelIterator extends DataLoader.DataLoaderIterator
    {
        private final Sheet sheet;
        private final int numRows;

        public ExcelIterator() throws IOException
        {
            super(_skipLines == -1 ? 1 : _skipLines, true);

            sheet = getSheet();
            numRows = sheet.getLastRowNum() + 1;
        }

        @Override
        protected Object[] readFields() throws IOException
        {
            if (lineNum() >= numRows)
                return null;

            ColumnDescriptor[] allColumns = getColumns();
            Iterator<ColumnDescriptor> columnIter = new ArrayIterator<ColumnDescriptor>(allColumns);
            Object[] fields = new Object[_activeColumns.length];

            Row row = sheet.getRow(lineNum());
            if (row != null)
            {
                int numCols = row.getLastCellNum();
                for (int columnIndex = 0, fieldIndex = 0; columnIndex < allColumns.length; columnIndex++)
                {
                    boolean loadThisColumn = ((columnIter.hasNext() && columnIter.next().load));

                    if (loadThisColumn)
                    {
                        ColumnDescriptor column = _activeColumns[fieldIndex];
                        Object contents;

                        if (columnIndex < numCols) // We can get asked for more data than we contain, as extra columns can exist
                        {
                            Cell cell = row.getCell(columnIndex);
                            if (cell == null)
                            {
                                contents = "";
                            }
                            else if (column.clazz.equals(String.class))
                            {
                                contents = ExcelFactory.getCellStringValue(cell);
                            }
                            else
                            {
                                contents = PropertyType.getFromExcelCell(cell);
                            }
                        }
                        else
                        {
                            contents = "";
                        }
                        fields[fieldIndex++] = contents;
                    }
                }
            }
            return fields;
        }

        public void close() throws IOException
        {
            super.close();       // TODO: Shouldn't this close the workbook?
        }
    }


    public static class ExcelLoaderTestCase extends Assert
    {
        @Test
        public void testColumnTypes() throws Exception
        {
            AppProps.Interface props = AppProps.getInstance();
            if (!props.isDevMode()) // We can only run the excel tests if we're in dev mode and have access to our samples
                return;

            String projectRootPath =  props.getProjectRoot();
            File projectRoot = new File(projectRootPath);

            File excelSamplesRoot = new File(projectRoot, "sampledata/dataLoading/excel");

            if (!excelSamplesRoot.exists() || !excelSamplesRoot.canRead())
                throw new IOException("Could not read excel samples in: " + excelSamplesRoot);

            File metadataSample = new File(excelSamplesRoot, "ExcelLoaderTest.xls");

            ExcelLoader loader = new ExcelLoader(metadataSample, true);
            checkColumnMetadata(loader);
            checkData(loader);
            loader.close();
        }

        private static void checkColumnMetadata(ExcelLoader loader) throws IOException
        {
            ColumnDescriptor[] columns = loader.getColumns();

            assertTrue(columns.length == 18);

            assertEquals(columns[0].clazz, Date.class);
            assertEquals(columns[1].clazz, Integer.class);
            assertEquals(columns[2].clazz, Double.class);

            assertEquals(columns[4].clazz, Boolean.class);

            assertEquals(columns[17].clazz, String.class);
        }

        private static void checkData(ExcelLoader loader) throws IOException
        {
            List<Map<String, Object>> data = loader.load();

            assertTrue(data.size() == 7);

            for (Map map : data)
            {
                assertTrue(map.size() == 18);
            }

            Map firstRow = data.get(0);
            assertTrue(firstRow.get("scan").equals(96));
            assertTrue(firstRow.get("accurateMZ").equals(false));
            assertTrue(firstRow.get("description").equals("description"));
        }
    }




    /* code modified from example code found XLS2CSV.java
     *
     */

/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

    enum xssfDataType
    {
        BOOL,
        ERROR,
        FORMULA,
        INLINESTR,
        SSTINDEX,
        NUMBER,
    }

    class SheetHandler extends DefaultHandler
    {
        /**
         * Table with unique strings
         */
        private ReadOnlySharedStringsTable sharedStringsTable;

        /**
         * Destination for data
         */
        private final Collection<ArrayList<Object>> output;
        ArrayList<Object> currentRow;
        int widestRow = 1;

        /**
         * Number of columns to read starting with leftmost
         */
        private final int minColumnCount;

        // Set when V start element is seen
        private boolean vIsOpen;

        // Set when cell start element is seen;
        // used when cell close element is seen.
        private xssfDataType nextDataType;

        private int thisColumn = -1;

        // Gathers characters as they are seen.
        private StringBuffer value;

        private int debugIndent = 0;

        /**
         * Accepts objects needed while parsing.
         *
         * @param strings Table of shared strings
         * @param cols    Minimum number of columns to show
         * @param target  Sink for output
         */
        public SheetHandler(
                ReadOnlySharedStringsTable strings,
                int cols,
                Collection<ArrayList<Object>> target)
        {
            this.sharedStringsTable = strings;
            this.minColumnCount = cols;
            this.value = new StringBuffer();
            this.nextDataType = xssfDataType.NUMBER;
            this.output = target;
        }

        private void debugPrint(String s)
        {
//            System.out.println(StringUtils.repeat(" ", debugIndent) + s);
        }

        /*
           * (non-Javadoc)
           * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
           */
        public void startElement(String uri, String localName, String name,
                                 Attributes attributes) throws SAXException
        {
            debugIndent++;
            debugPrint("<" + name + ">");
            if ("row".equals(name))
            {
                currentRow = new ArrayList<Object>(Math.max(1,widestRow));
                output.add(currentRow);
            }
            if ("inlineStr".equals(name) || "v".equals(name) || "t".equals(name))
            {
                vIsOpen = true;
                // Clear contents cache
                value.setLength(0);
            }
            // c => cell
            else if ("c".equals(name))
            {
                // Get the cell reference
                String r = attributes.getValue("r");
                int firstDigit = -1;
                for (int c = 0; c < r.length(); ++c)
                {
                    if (Character.isDigit(r.charAt(c)))
                    {
                        firstDigit = c;
                        break;
                    }
                }
                thisColumn = nameToColumn(r.substring(0, firstDigit));

                // Set up defaults.
                this.nextDataType = xssfDataType.NUMBER;
                String cellType = attributes.getValue("t");
                String cellStyleStr = attributes.getValue("s");
                if ("b".equals(cellType))
                    nextDataType = xssfDataType.BOOL;
                else if ("e".equals(cellType))
                    nextDataType = xssfDataType.ERROR;
                else if ("inlineStr".equals(cellType))
                    nextDataType = xssfDataType.INLINESTR;
                else if ("s".equals(cellType))
                    nextDataType = xssfDataType.SSTINDEX;
                else if ("str".equals(cellType))
                    nextDataType = xssfDataType.FORMULA;
            }
        }

        /*
           * (non-Javadoc)
           * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
           */
        public void endElement(String uri, String localName, String name)
                throws SAXException
        {
            Object thisValue = null;

            if ("c".equals(name))
            {
                // Process the value contents as required.
                // Do now, as characters() may be called more than once
                switch (nextDataType)
                {

                    case BOOL:
                        char first = value.charAt(0);
                        thisValue = first == '0' ? Boolean.FALSE : Boolean.TRUE;
                        break;

                    case ERROR:
                        thisValue = "\"ERROR:" + value.toString() + '"';
                        break;

                    case FORMULA:
                        // A formula could result in a string value,
                        // so always add double-quote characters.
                        thisValue = value.toString();
                        break;

                    case INLINESTR:
                        XSSFRichTextString rtsi = new XSSFRichTextString(value.toString());
                        thisValue = rtsi.toString();
                        break;

                    case SSTINDEX:
                        String sstIndex = value.toString();
                        try
                        {
                            int idx = Integer.parseInt(sstIndex);
                            XSSFRichTextString rtss = new XSSFRichTextString(sharedStringsTable.getEntryAt(idx));
                            thisValue = rtss.toString();
                        }
                        catch (NumberFormatException ex)
                        {
                            thisValue = "Failed to parse SST index '" + sstIndex + "': " + ex.toString();
                        }
                        break;

                    case NUMBER:
                        thisValue = Double.parseDouble(value.toString());
                        break;

                    default:
                        thisValue = "(TODO: Unexpected type: " + nextDataType + ")";
                        break;
                }

                while (currentRow.size() <= thisColumn)
                    currentRow.add(null);
                currentRow.set(thisColumn, thisValue);
            }
            else if ("row".equals(name))
            {
                // We're onto a new row
                widestRow = Math.max(widestRow,currentRow.size());
                currentRow = null;
            }

            debugPrint("</" + name + ">");
            debugIndent--;
        }

        /**
         * Captures characters only if a suitable element is open.
         * Originally was just "v"; extended for inlineStr also.
         */
        public void characters(char[] ch, int start, int length)
                throws SAXException
        {
            debugPrint((vIsOpen?"+":" ") + "chars:" + new String(ch,start,length));
            if (vIsOpen)
                value.append(ch, start, length);
        }

        /**
         * Converts an Excel column name like "C" to a zero-based index.
         *
         * @param name
         * @return Index corresponding to the specified name
         */
        private int nameToColumn(String name)
        {
            int column = -1;
            for (int i = 0; i < name.length(); ++i)
            {
                int c = name.charAt(i);
                column = (column + 1) * 26 + c - 'A';
            }
            return column;
        }
    }


    static class _ArrayListMap extends ArrayListMap<String, Object>
    {
        _ArrayListMap(FindMap<String> findMap, ArrayList<Object> row)
        {
            super(findMap, row);
        }
    }
}