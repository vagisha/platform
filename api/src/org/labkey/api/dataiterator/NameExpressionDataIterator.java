package org.labkey.api.dataiterator;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class NameExpressionDataIterator extends WrapperDataIterator
{
    private final DataIteratorContext _context;
    private Map<String, Pair<NameGenerator, NameGenerator.State>> _nameGeneratorMap = new HashMap<>();
    private final Integer _nameCol;
    private Integer _expressionCol;
    private TableInfo _parentTable;

    public NameExpressionDataIterator(DataIterator di, DataIteratorContext context, @Nullable TableInfo parentTable)
    {
        super(DataIteratorUtil.wrapMap(di, false));
        _context = context;
        _parentTable = parentTable;

        Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
        _nameCol = map.get("name");
        _expressionCol = map.get("nameExpression");
        assert _nameCol != null;
        assert _expressionCol != null;
    }

    MapDataIterator getInput()
    {
        return (MapDataIterator)_delegate;
    }

    private BatchValidationException getErrors()
    {
        return _context.getErrors();
    }

    private void addNameGenerator(String nameExpression)
    {
        NameGenerator nameGen = new NameGenerator(nameExpression, _parentTable, false);
        NameGenerator.State state = nameGen.createState(false, false);
        _nameGeneratorMap.put(nameExpression, Pair.of(nameGen, state));
    }

    @Override
    public Object get(int i)
    {
        if (i == _nameCol)
        {
            Object curName = super.get(_nameCol);
            if (curName instanceof String)
                curName = StringUtils.isEmpty((String)curName) ? null : curName;

            if (curName != null)
                return curName;

            Map<String, Object> currentRow = getInput().getMap();

            try
            {
                String nameExpression = (String) super.get(_expressionCol);
                if (!_nameGeneratorMap.containsKey(nameExpression))
                {
                    addNameGenerator(nameExpression);
                }

                Pair<NameGenerator, NameGenerator.State> nameGenPair = _nameGeneratorMap.get(nameExpression);
                String newName = nameGenPair.first.generateName(nameGenPair.second, currentRow);
                if (!StringUtils.isEmpty(newName))
                    return newName;
            }
            catch (NameGenerator.NameGenerationException e)
            {
                getErrors().addRowError(new ValidationException(e.getMessage()));
            }
        }

        return super.get(i);
    }

}
