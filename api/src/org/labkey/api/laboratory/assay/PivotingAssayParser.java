package org.labkey.api.laboratory.assay;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 12/9/12
 * Time: 7:51 AM
 */
public class PivotingAssayParser extends DefaultAssayParser
{
    PivotingImportMethod _pivotMethod;

    public PivotingAssayParser(PivotingImportMethod method, Container c, User u, int assayId, JSONObject formData)
    {
        super(method, c, u, assayId, formData);
        _pivotMethod = method;
    }

    @Override
    protected String readRawFile(File inputFile) throws BatchValidationException
    {
        CSVReader csv = null;
        try
        {
            StringBuilder sb = new StringBuilder();
            DomainProperty valueCol = _pivotMethod.getValueColumn(_protocol);
            DomainProperty pivotCol = _pivotMethod.getPivotColumn(_protocol);

            csv = new CSVReader(new FileReader(inputFile), '\t');
            String[] line;
            Map<Integer, String> resultCols = null;
            Integer rowIdx = 0;
            while ((line = csv.readNext()) != null)
            {
                if (rowIdx == 0)
                {
                    resultCols = inspectHeader(line);
                }

                List<String> rowBase = new ArrayList<String>();
                List<Pair<String, String>> otherFields = new ArrayList<Pair<String, String>>();
                int cellIdx = 0;
                for (String cell : line)
                {
                    if (resultCols.keySet().contains(cellIdx))
                    {
                        if (!StringUtils.isEmpty(cell))
                            otherFields.add(Pair.of(resultCols.get(cellIdx), cell));
                    }
                    else
                    {
                        rowBase.add(cell);
                    }
                    cellIdx++;
                }

                if (rowIdx > 0)
                {
                    for (Pair<String, String> pair : otherFields)
                    {
                        List<String> row = new ArrayList<String>();
                        row.addAll(rowBase);
                        row.add(pair.first);
                        row.add(pair.second);
                        row.add(rowIdx.toString());
                        sb.append(StringUtils.join(row, "\t")).append(System.getProperty("line.separator"));
                    }
                }
                else
                {
                    List<String> row = new ArrayList<String>();
                    row.addAll(rowBase);
                    row.add(pivotCol.getLabel());
                    row.add(valueCol.getLabel());
                    row.add("_rowIdx");
                    sb.append(StringUtils.join(row, "\t")).append(System.getProperty("line.separator"));
                }

                rowIdx++;
            }

            return sb.toString();

        }
        catch (IOException e)
        {
            BatchValidationException ex = new BatchValidationException();
            ex.addRowError(new ValidationException(e.getMessage()));
            throw ex;
        }
        finally
        {
            try { if (csv != null) csv.close(); } catch (IOException e) {}
        }
    }

    /**
     * Inspects the header line and returns a list of all columns inferred to contain results
     * and other columns are assumed to
     */
    private Map<Integer, String> inspectHeader(String[] header) throws BatchValidationException
    {
        Map<Integer, String> resultMap = new HashMap<Integer, String>();
        Map<String, String> allowable = new CaseInsensitiveHashMap();
        BatchValidationException errors = new BatchValidationException();

        for (String val : _pivotMethod.getAllowableValues())
        {
            allowable.put(val, val);
        }

        Set<String> knownColumns = new HashSet<String>();
        for (DomainProperty dp : _provider.getResultsDomain(_protocol).getProperties())
        {
            knownColumns.add(dp.getLabel());
            knownColumns.add(dp.getName());
        }

        Integer idx = 0;
        for (String col : header)
        {
            String normalized = null;
            if (allowable.containsKey(col))
            {
                normalized = allowable.get(col);
            }
            else
            {
                if (!knownColumns.contains(col))
                {
                    normalized = handleUnknownColumn(col, allowable, errors);
                }
            }

            if (normalized != null)
                resultMap.put(idx, normalized);

            idx++;
        }

        if (errors.hasErrors())
            throw errors;

        return resultMap;
    }

    protected String handleUnknownColumn(String col, Map<String, String> allowable, BatchValidationException errors)
    {
        //TODO: allow a flag that lets us assume known columns hold results
        errors.addRowError(new ValidationException("Unknown column: " + col));
        return null;
    }
}
