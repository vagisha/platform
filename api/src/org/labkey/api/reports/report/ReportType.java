package org.labkey.api.reports.report;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;

public class ReportType implements AttachmentType
{
    private static final ReportType INSTANCE = new ReportType();

    public static ReportType get()
    {
        return INSTANCE;
    }

    private ReportType()
    {
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return getClass().getName();
    }

    @Override
    public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
    {
        sql.append(parentColumn).append(" IN (SELECT EntityId FROM ").append(CoreSchema.getInstance().getTableInfoReport(), "reports").append(")");
    }
}
