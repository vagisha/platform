package org.labkey.study.reports;

import org.labkey.api.reports.report.ChartQueryReport;

/**
 * We don't need to render this report as of 19.1 but we need to be able to register and instance of it so
 * it can be converted to a javascript report. This class can be deleted in the 21.2 release.
 */
@Deprecated
public class DatasetChartReport extends ChartQueryReport
{
    public static final String TYPE = "Study.datasetChart";

    public String getType()
    {
        return TYPE;
    }
}