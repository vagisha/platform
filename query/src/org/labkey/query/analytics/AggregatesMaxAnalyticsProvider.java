package org.labkey.query.analytics;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.analytics.BaseAggregatesAnalyticsProvider;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;

public class AggregatesMaxAnalyticsProvider extends BaseAggregatesAnalyticsProvider
{
    @Override
    public Aggregate.Type getAggregateType()
    {
        return Aggregate.BaseType.MAX;
    }

    @Override
    public boolean isApplicable(@NotNull ColumnInfo col)
    {
        return (col.isNumericType() && !col.isLookup()) || col.isDateTimeType();
    }

    @Override
    public Integer getSortOrder()
    {
        return 204;
    }
}
