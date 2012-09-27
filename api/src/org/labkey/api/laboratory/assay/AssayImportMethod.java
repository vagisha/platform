package org.labkey.api.laboratory.assay;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/15/12
 * Time: 2:18 PM
 */
public interface AssayImportMethod
{
    abstract public String getName();

    /**
     * The display label
     * @return
     */
    abstract public String getLabel();

    /**
     * An array of fieldname from the batch domain that will be hidden
     */
    abstract public List<String> getSkippedBatchFields();

    /**
     * An array of fieldname from the runs domain that will be hidden
     */
    abstract public List<String> getSkippedRunFields();

    /**
     * An array of fieldname from the runs domain that will be hidden
     */
    abstract public List<String> getSkippedResultFields();

    /**
     * An array of new fields to add to the form.  Each item must be an object with the properties, 'name', 'label' and 'domain'.
     * @return
     */
    abstract public List<String> getAdditionalFields();

    /**
     * An array of result field names that will be displayed below the runs section, and will not appear in the excel template.
     */
    abstract public List<String> getPromotedResultFields();

    /**
     * If true, no link to download a template will appear.  Defaults to false
     * @return
     */
    abstract public boolean hideTemplateDownload();

    /**
     * @return A tooltip used for the radio button
     */
    abstract public String getTooltip();

    /**
     * controls whether file content area shows.  otherwise this would be a web form only.  defulats to false
     * @return
     */
    abstract public boolean doEnterResultsInGrid();

    /**
     * URL to a to file with example data
     * @return
     */
    abstract public String getExampleDataUrl(ViewContext ctx);

    /**
     * instructions that will be displayed above the results area
     * @return
     */
    abstract public String getTemplateInstructions();

    /**
     * The name of an Ext class that will render the results preview.  Defaults to 'Laboratory.ext.AssayPreviewPanel'
     * @return
     */
    abstract public String getPreviewPanelClass();

    /**
     * A metadata config object that will be applied to the fields
     * @return
     */
    abstract public JSONObject getMetadata(ViewContext ctx);

    abstract public JSONObject toJson(ViewContext ctx);

    abstract public AssayParser getFileParser(Container c, User u, int assayId, JSONObject formData);

}
