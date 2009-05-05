package org.labkey.api.collections;

import org.apache.log4j.Logger;

import java.beans.Introspector;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
* User: adam
* Date: May 4, 2009
* Time: 1:11:17 PM
*/
public class ResultSetRowMapFactory extends RowMapFactory<Object> implements Serializable
{
    private static final Logger _log = Logger.getLogger(ResultSetRowMapFactory.class);

    public ResultSetRowMapFactory(ResultSet rs) throws SQLException
    {
        super(rs.getMetaData().getColumnCount() + 1);

        Map<String, Integer> findMap = getFindMap();
        ResultSetMetaData md = rs.getMetaData();
        int count = md.getColumnCount();
        findMap.put("_row", 0);  // We're going to stuff the current row index at index 0

        for (int i = 1; i <= count; i++)
        {
            String propName = md.getColumnName(i);

            if (propName.length() > 0 && Character.isUpperCase(propName.charAt(0)))
                propName = Introspector.decapitalize(propName);

            findMap.put(propName, i);
        }
    }


    public RowMap<Object> getRowMap(ResultSet rs) throws SQLException
    {
        RowMap<Object> map = super.getRowMap();

        int len = rs.getMetaData().getColumnCount();

        // Stuff current row into rowMap
        int currentRow;

        try
        {
            currentRow = rs.getRow();
        }
        catch (SQLException e)
        {
            currentRow = 1;   // TODO: Implement a counter for SAS
        }

        List<Object> _list = map.getRow();

        if (0 == _list.size())
            _list.add(currentRow);
        else
            _list.set(0, currentRow);

        for (int i = 1; i <= len; i++)
        {
            Object o = rs.getObject(i);
            // Note: When using getObject() on a SQL column of type Text, the Microsoft SQL Server jdbc driver returns
            // a String, while the jTDS driver returns a Clob.  For consistency we map here.
            // Could map at lower level, but don't want to preclude ever using Clob as Clob
            if (o instanceof Clob)
            {
                Clob clob = (Clob) o;

                try
                {
                    o = clob.getSubString(1, (int) clob.length());
                }
                catch (SQLException e)
                {
                    _log.error(e);
                }
            }

            // BigDecimal objects are rare, and almost always are converted immediately
            // to doubles for ease of use in Java code; we can take care of this centrally here.
            if (o instanceof BigDecimal)
            {
                BigDecimal dec = (BigDecimal) o;
                o = dec.doubleValue();
            }

            if (i == _list.size())
                _list.add(o);
            else
                _list.set(i, o);
        }

        return map;
    }
}
