/**
* @fileOverview
* @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
* @license Copyright (c) 2008-2011 LabKey Corporation
* <p/>
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* <p/>
*/

/**
 * @namespace The DataRegion class allows you to interact with LabKey grids, including querying and modifying selection state, filters, and more.
 */
if (!LABKEY.DataRegions)
{
    LABKEY.DataRegions = {};
}

/**
 * The DataRegion constructor is private - to get a LABKEY.DataRegion object,
 * use <code>Ext.ComponentMgr.get(<em>&lt;dataregionname&gt;</em>)</code> or <code>Ext.ComponentMgr.onAvailable(<em>&lt;dataregionname&gt;</em>, callback)</code>.
 * @class The DataRegion class allows you to interact with LabKey grids, including querying and modifying selection state, filters, and more.
 * @constructor
 */
LABKEY.DataRegion = Ext.extend(Ext.Component,
/** @lends LABKEY.DataRegion.prototype */
{
    constructor : function (config)
    {
        this.config = config || {};

        /**
         * Config Options
         *  name       - Name of the DataRegion. Should be unique within a given page. Read-only. This will also be used
         *               as the Id.
         *  schemaName - Schema name of the query to which this DataRegion is bound. Read-only.
         *  queryName  - Name of the query to which this DataRegion is bound. Read-only.
         *  viewName   - Name of the custom view to which this DataRegion is bound, may be blank. Read-only.
         *  view
         *  sortFilter
         *  complete
         *  offset     - Starting offset of the rows to be displayed. 0 if at the beginning of the results. Read-only.
         *  maxRows    - Maximum number of rows to be displayed. 0 if the count is not limited. Read-only.
         *  totalRows  - (may be undefined)
         *  rowCount   - (may be undefined)
         *  showRows
         *  showRecordSelectors
         *  showInitialSelectMessage
         *  selectionKey - Unique string used to associate the selected items with this DataRegion, schema, query, and view.
         *  selectorCols
         *  requestURL
         */
        Ext.apply(this, config, {
            viewName : "",
            offset   : 0,
            maxRows  : 0
        });

        /**
         * Non-Configurable Options
         *  selectionModified
         *  currentPanelButton  - The button for the ribbon panel that we're currently showing
         *  panelButtonContents - All of the different ribbon panels that have been constructed for this data region
         *  allowHeaderLock     - A partially configurable option that allows for lockable headers on scrolling. Only
         *                        includes "modern browsers" as of 9.8.2011
         */
        Ext.apply(this, {
            selectionModified  : false,
            currentPanelButton : null,
            panelButtonContents: []
        });

        this.id = this.name;
        this._allowHeaderLock = this.allowHeaderLock && (Ext.isIE9 || Ext.isWebKit || Ext.isGecko);

        LABKEY.DataRegions[this.name] = this;

        this.addEvents(
                /**
                 * @memberOf LABKEY.DataRegion#
                 * @name selectchange
                 * @event
                 * @description Fires when the selection has changed.
                 * @param {LABKEY.DataRegion} dataRegion this DataRegion object.
                 * @param {Boolean} hasSelection true if the DataRegion has at least one selected item.
                 * @example Here's an example of subscribing to the DataRegion 'selectchange' event:
                 * Ext.ComponentMgr.onAvailable("dataRegionName", function (dataregion) {
                 *     dataregion.on('selectchange', function (dr, selected) {
                 *         var btn = Ext.get('my-button-id');
                 *         if (selected) {
                 *             btn.replaceClass('labkey-disabled-button', 'labkey-button');
                 *         }
                 *         else {
                 *             btn.replaceClass('labkey-button', 'labkey-disabled-button');
                 *         }
                 *     });
                 *  });
                 */
                "selectchange",
                "beforeoffsetchange",
                "beforemaxrowschange",
                "beforesortchange",
                "beforeclearsort",
                "beforeclearfilter",
                "beforeclearallfilters",
                "beforechangeview",
                "beforeshowrowschange",
                "beforesetparameters",
                "buttonclick",
                "afterpanelhide",
                "afterpanelshow",
                /**
                 * @memberOf LABKEY.DataRegion#
                 * @name beforerefresh
                 * @event
                 * @description Fires when a refresh of the DataRegion has been requested. If no handler consumes the event,
                 * the whole page will be reloaded.
                 * @param {LABKEY.DataRegion} dataRegion this DataRegion object.
                 */
                "beforerefresh"
        );

        this._initElements();
        this._showPagination(this.header);
        this._showPagination(this.footer);

        if (this.view && this.view.session)
        {
            var msg;
            if (this.view.savable)
            {
                msg = (this.viewName ? "The current view '<em>" + Ext.util.Format.htmlEncode(this.viewName) + "</em>'" : "The current <em>&lt;default&gt;</em> view") + " is unsaved.";
                msg += " &nbsp;";
                msg += "<span class='labkey-button unsavedview-revert'>Revert</span>";
                msg += "&nbsp;";
                msg += "<span class='labkey-button unsavedview-edit'>Edit</span>";
                msg += "&nbsp;";
                msg += "<span class='labkey-button unsavedview-save'>Save</span>";
            }
            else
            {
                msg = ("The current view has been customized.");
                msg += " &nbsp;";
                msg += "<span class='labkey-button unsavedview-revert' title='Revert'>Revert</span>";
                msg += ", &nbsp;";
                msg += "<span class='labkey-button unsavedview-edit'>Edit</span>";
            }

            // add the customize view message, the link handlers will get added after render in _onRenderMessageArea
            var el = this.addMessage(msg, 'customizeview');
        }

        if (this.showInitialSelectMessage)
        {
            switch (this.showRows)
            {
                case "all":
                    this._showSelectMessage("Showing all " + this.totalRows + " rows.");
                    break;
                case "selected":
                    this._showSelectMessage("Showing only <em>selected</em> rows.");
                    break;
                case "unselected":
                    this._showSelectMessage("Showing only <em>unselected</em> rows.");
                    break;
            }
        }

        this.rendered = true; // prevent Ext.Component.render() from doing anything
        LABKEY.DataRegion.superclass.constructor.call(this, config);
    },

    /**
     * Set the parameterized query values for this query.  These parameters
     * are named by the query itself.
     * @param {Mixed} params An Object or Array or Array key/val pairs.
     */
    setParameters : function (params)
    {
        if (false === this.fireEvent("beforesetparameters", this, params))
            return;

        // convert Object into Array of Array pairs and prefix the parameter name if necessary.
        if (Ext.isObject(params))
        {
            var values = params,
                params = [];
            for (var key in values)
            {
                if (key.indexOf(this.name + ".param.") !== 0)
                    key = this.name + ".param." + key;
                params.push([key, values[key]]);
            }
        }

        this._setParams(params, [".offset", ".param."]);
    },

    /**
     * Changes the current row offset for paged content
     * @param newoffset row index that should be at the top of the grid
     */
    setOffset : function (newoffset)
    {
        if (false === this.fireEvent("beforeoffsetchange", this, newoffset))
            return;

        this._setParam(".offset", newoffset, [".offset", ".showRows"]);
    },

    /**
     * Changes the maximum number of rows that the grid will display at one time
     * @param newmax the maximum number of rows to be shown
     */
    setMaxRows : function (newmax)
    {
        if (false === this.fireEvent("beforemaxrowschange", this, newmax))
            return;

        this._setParam(".maxRows", newmax, [".offset", ".maxRows", ".showRows"]);
    },

    /**
     * Refreshes the grid, via AJAX if loaded through a QueryWebPart, and via a page reload otherwise.
     */
    refresh : function ()
    {
        if (false === this.fireEvent("beforerefresh", this))
            return;

        window.location.reload(false);
    },

    /**
     * Forces the grid to do paging based on the current maximum number of rows
     */
    showPaged : function ()
    {
        if (false === this.fireEvent("beforeshowrowschange", this, null))
            return;

        this._removeParams([".showRows"]);
    },

    /**
     * Forces the grid to show all rows, without any paging
     */
    showAll : function ()
    {
        if (false === this.fireEvent("beforeshowrowschange", this, "all"))
            return;

        this._setParam(".showRows", "all", [".offset", ".maxRows", ".showRows"]);
    },

    /**
     * Forces the grid to show only rows that have been selected
     */
    showSelected : function ()
    {
        if (false === this.fireEvent("beforeshowrowschange", this, "selected"))
            return;

        this._setParam(".showRows", "selected", [".offset", ".maxRows", ".showRows"]);
    },

    /**
     * Forces the grid to show only rows that have not been selected
     */
    showUnselected : function ()
    {
        if (false === this.fireEvent("beforeshowrowschange", this, "unselected"))
            return;

        this._setParam(".showRows", "unselected", [".offset", ".maxRows", ".showRows"]);
    },

    /** Displays the first page of the grid */
    pageFirst : function ()
    {
        this.setOffset(0);
    },

    selectRow : function (el)
    {
        this.setSelected({ids: [el.value], checked: el.checked});
        var toggle = this.form[".toggle"];
        if (el.checked)
        {
            if (toggle && this.isPageSelected())
                toggle.checked = true;
            this.onSelectChange(true);
        }
        else
        {
            if (toggle)
                toggle.checked = false;
            this.hideMessage();
            this.onSelectChange(this.hasSelected());
        }
    },

    /**
     * Get selected items on the current page of the DataRegion.  Selected items may exist on other pages.
     * @see LABKEY.DataRegion#getSelected
     */
    getChecked : function ()
    {
        return getCheckedValues(this.form, '.select');
    },

    /**
     * Get all selected items for this DataRegion.
     *
     * @param config A configuration object with the following properties:
     * @param {Function} config.success The function to be called upon success of the request.
     * The callback will be passed the following parameters:
     * <ul>
     * <li><b>data:</b> an object with the property 'selected' that is an array of the primary keys for the selected rows.
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     * @param {Function} [config.failure] The function to call upon error of the request.
     * The callback will be passed the following parameters:
     * <ul>
     * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     * @param {Object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
     * @param {string} [config.containerPath] An alternate container path. If not specified, the current container path will be used.
     *
     * @see LABKEY.DataRegion.getSelected static method.
     */
    getSelected : function (config)
    {
        if (!this.selectionKey)
            return;

        config = config || { };
        config.selectionKey = this.selectionKey;
        LABKEY.DataRegion.getSelected(config);
    },

    /**
     * Add or remove items from the selection associated with the this DataRegion.
     *
     * @param config A configuration object with the following properties:
     * @param {Array} config.ids Array of primary key ids for each row to select/unselect.
     * @param {Boolean} config.checked If true, the ids will be selected, otherwise unselected.
     * @param {Function} config.success The function to be called upon success of the request.
     * The callback will be passed the following parameters:
     * <ul>
     * <li><b>data:</b> an object with the property 'count' to indicate the updated selection count.
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     * @param {Function} [config.failure] The function to call upon error of the request.
     * The callback will be passed the following parameters:
     * <ul>
     * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     * @param {Object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
     * @param {string} [config.containerPath] An alternate container path. If not specified, the current container path will be used.
     *
     * @see LABKEY.DataRegion#getSelected to get the selected items for this DataRegion.
     * @see LABKEY.DataRegion#clearSelected to clear all selected items for this DataRegion.
     */
    setSelected : function (config)
    {
        if (!this.selectionKey)
            return;

        if (arguments.length > 1)
        {
            config = {
                ids: arguments[0],
                checked: arguments[1],
                success: arguments[2]
            };
        }

        config = config || {};
        if (!config.ids || config.ids.length == 0)
            return;

        config.selectionKey = this.selectionKey;
        config.scope = config.scope || this;

        function failureCb(response, options) { this.addMessage("Error sending selection."); }
        config.failure = LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config) || failureCb, this, true);

        this.selectionModified = true;
        LABKEY.DataRegion.setSelected(config);
    },

    /**
     * Set the selection state for all checkboxes on the current page of the DataRegion.
     * @param checked whether all of the rows on the current page should be selected or unselected
     * @returns {Array} Array of ids that were selected or unselected.
     *
     * @see LABKEY.DataRegion#setSelected to set selected items on the current page of the DataRegion.
     * @see LABKEY.DataRegion#clearSelected to clear all selected.
     */
    selectPage : function (checked)
    {
        var ids = this._setAllCheckboxes(checked, '.select');
        if (ids.length > 0)
        {
            var toggle = this.form[".toggle"];
            if (toggle)
                toggle.checked = checked;
            this.onSelectChange(checked);
            this.setSelected({ids: ids, checked: checked, success: function (response, options) {
                var count = 0;
                try {
                    var json = Ext.util.JSON.decode(response.responseText);
                    if (json)
                        count = json.count;
                }
                catch (e) {
                    // ignore
                }
                if (count > 0)
                {
                    var msg;
                    if (count == this.totalRows)
                        msg = "Selected all " + this.totalRows + " rows.";
                    else
                        msg = "Selected " + count + " of " + this.totalRows + " rows.";
                    this._showSelectMessage(msg);
                }
                else
                {
                    this.hideMessage();
                }
            }});
        }
        return ids;
    },

    /**
     * Returns true if any row is checked on the current page of the DataRegion. Selected items may exist on other pages.
     * @returns {Boolean} true if any row is checked on the current page of the DataRegion.
     * @see LABKEY.DataRegion#getSelected to get all selected rows.
     */
    hasSelected : function ()
    {
        if (!this.form)
            return false;
        var len = this.form.length;
        for (var i = 0; i < len; i++)
        {
            var e = this.form[i];
            if (e.type == 'checkbox' && e.name != ".toggle")
            {
                if (e.checked)
                    return true;
            }
        }
        return false;
    },

    /**
     * Returns true if all rows are checked on the current page of the DataRegion and at least one row is present.
     * @returns {Boolean} true if all rows are checked on the current page of the DataRegion and at least one row is present.
     * @see LABKEY.DataRegion#getSelected to get all selected rows.
     */
    isPageSelected : function ()
    {
        if (!this.form)
            return false;
        var len = this.form.length;
        var hasCheckbox = false;
        for (var i = 0; i < len; i++)
        {
            var e = this.form[i];
            if (e.type == 'checkbox' && e.name != ".toggle")
            {
                hasCheckbox = true;
                if (!e.checked)
                    return false;
            }
        }
        return hasCheckbox;
    },

    selectNone : function (config)
    {
        return this.clearSelected(config);
    },

    /**
     * Clear all selected items for the current DataRegion.
     *
     * @param config A configuration object with the following properties:
     * @param {Function} config.success The function to be called upon success of the request.
     * The callback will be passed the following parameters:
     * <ul>
     * <li><b>data:</b> an object with the property 'count' of 0 to indicate an empty selection.
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     * @param {Function} [config.failure] The function to call upon error of the request.
     * The callback will be passed the following parameters:
     * <ul>
     * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     * @param {Object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
     * @param {string} [config.containerPath] An alternate container path. If not specified, the current container path will be used.
     *
     * @see LABKEY.DataRegion#selectPage
     * @see LABKEY.DataRegion.clearSelected static method.
     */
    clearSelected : function (config)
    {
        if (!this.selectionKey)
            return;

        this.onSelectChange(false);

        config = config || { };
        config.selectionKey = this.selectionKey;
        LABKEY.DataRegion.clearSelected(config);

        if (this.showRows == "selected")
        {
            this._removeParams([".showRows"]);
        }
        else if (this.showRows == "unselected")
        {
            // keep ".showRows=unselected" parameter
            window.location.reload(true);
        }
        else
        {
            this._setAllCheckboxes(false);
            this.hideMessage();
        }
    },

    /**
     * Replaces the sort on the given column, if present, or sets a brand new sort
     * @param columnName name of the column to be sorted
     * @param sortDirection either "+' for ascending or '-' for descending
     */
    changeSort : function (columnName, sortDirection)
    {
        if (false === this.fireEvent("beforesortchange", this, columnName, sortDirection))
            return;

        var newSortString = this.alterSortString(LABKEY.DataRegion._filterUI.getParameter(this.name + ".sort"), columnName, sortDirection);
        this._setParam(".sort", newSortString, [".sort", ".offset"]);
    },

    /**
     * Removes the sort on a specified column
     * @param columnName name of the column
     */
    clearSort : function (columnName)
    {
        if (!columnName)
            return;

        if (false === this.fireEvent("beforeclearsort", this, columnName))
            return;

        var newSortString = this.alterSortString(LABKEY.DataRegion._filterUI.getParameter(this.name + ".sort"), columnName, null);
        if (newSortString.length > 0)
            this._setParam(".sort", newSortString, [".sort", ".offset"]);
        else
            this._removeParams([".sort", ".offset"]);
    },

    // private
    changeFilter : function (newParamValPairs, newQueryString)
    {
        if (false === this.fireEvent("beforefilterchange", this, newParamValPairs))
            return;

        LABKEY.DataRegion._filterUI.setSearchString(this.name, newQueryString);
    },

    /**
     * Removes all the filters for a particular field
     * @param fieldName the name of the field from which all filters should be removed
     */
    clearFilter : function (fieldName)
    {
        if (false === this.fireEvent("beforeclearfilter", this, fieldName))
            return;
        this._removeParams(["." + fieldName + "~", ".offset"]);
    },

    /** Removes all filters from the DataRegion */
    clearAllFilters : function ()
    {
        if (false === this.fireEvent("beforeclearallfilters", this))
            return;
        this._removeParams([".", ".offset"]);
    },

    /**
     * Returns the user filter from the URL. The filter is represented as an Array of objects of the form:
     * <ul>
     *   <li><b>fieldKey</b>: {String} The field key of the filter.
     *   <li><b>op</b>: {String} The filter operator (eg. "eq" or "in")
     *   <li><b>value</b>: {String} Optional value to filter by.
     * </ul>
     * @returns {Object} Object representing the user filter.
     */
    getUserFilter : function ()
    {
        var userFilter = [];
        var paramValPairs = LABKEY.DataRegion._filterUI.getParamValPairs(this.requestURL, null);
        for (var i = 0; i < paramValPairs.length; i++)
        {
            var pair = paramValPairs[i];
            if (pair[0].indexOf(this.name + ".") == 0 && pair[0].indexOf('~') > -1)
            {
                var tilde = pair[0].indexOf('~');
                var fieldKey = pair[0].substring(this.name.length + 1, tilde);
                var op = pair[0].substring(tilde + 1);
                var value = pair[1];

                userFilter.push({fieldKey: fieldKey, op: op, value: value});
            }
        }

        return userFilter;
    },

    /**
     * Returns the user {@link LABKEY.Query.containerFilter} parameter from the URL.
     * @returns {LABKEY.Query.containerFilter} The user container filter.
     */
    getUserContainerFilter : function ()
    {
        return LABKEY.DataRegion._filterUI.getParameter(this.name + ".containerFilterName");
    },

    /**
     * Returns the user sort from the URL. The sort is represented as an Array of objects of the form:
     * <ul>
     *   <li><b>fieldKey</b>: {String} The field key of the sort.
     *   <li><b>dir</b>: {String} The sort direction, either "+" or "-".
     * </ul>
     * @returns {Object} Object representing the user sort.
     */
    getUserSort : function ()
    {
        var userSort = [];
        var sortParam = LABKEY.DataRegion._filterUI.getParameter(this.name + ".sort");
        if (sortParam)
        {
            var sortArray = sortParam.split(",");
            for (var i = 0; i < sortArray.length; i++)
            {
                var sort = sortArray[i];
                var fieldKey = sort;
                var dir = "+";
                if (sort.charAt(0) == "-")
                {
                    fieldKey = fieldKey.substring(1);
                    dir = "-";
                }
                else if (sort.charAt(0) == "+")
                {
                    fieldKey = fieldKey.substring(1);
                }
                userSort.push({fieldKey: fieldKey, dir: dir});
            }
        }

        return userSort;
    },

    /**
     * Show a message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     * @param part
     * @return {Ext.Element} The Ext.Element of the newly created message div.
     */
    addMessage : function (html, part)
    {
        if (this.msgbox)
            this.msgbox.addMessage(html, part);
    },

    /**
     * Show a message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     * @return {Ext.Element} The Ext.Element of the newly created message div.
     * @deprecated use addMessage(html, msg) instead.
     */
    showMessage : function (html)
    {
        if (this.msgbox)
            this.msgbox.addMessage(html);
    },

    showMessageArea : function()
    {
        if (this.msgbox)
            this.msgbox.render();
    },

    /**
     * Show a message in the header of this DataRegion with a loading indicator.
     * @param html the HTML source of the message to be shown
     */
    showLoadingMessage : function (html)
    {
        html = html || "Loading...";
        this.addMessage("<div><span class='loading-indicator'>&nbsp;</span><em>" + html + "</em></div>");
    },

    /**
     * Show a success message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     */
    showSuccessMessage : function (html)
    {
        html = html || "Completed successfully.";
        this.addMessage("<div class='labkey-message'>" + html + "</div>");
    },

    /**
     * Show an error message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     */
    showErrorMessage : function (html)
    {
        html = html || "An error occurred.";
        this.addMessage("<div class='labkey-error'>" + html + "</div>");
    },

    /**
     * Returns true if a message is currently being shown for this DataRegion. Messages are shown as a header.
     * @return {Boolean} true if a message is showing.
     */
    isMessageShowing : function()
    {
        return this.msgbox && this.msgbox.isVisible();
    },

    /** If a message is currently showing, hide it and clear out its contents */
    hideMessage : function ()
    {
        if (this.msgbox)
        {
            this.msgbox.setVisible(false);
            this.msgbox.clear();
        }
    },

    /** Clear the message box contents. */
    clearMessage : function ()
    {
        if (this.msgbox) this.msgbox.clear();
    },

    /**
     * Get the message area if it exists.
     * @return {LABKEY.DataRegion.MessageArea} The message area object.
     */
    getMessageArea : function()
    {
        return this.msgbox;
    },

    alterSortString : function(currentSortString, columnName, direction)
    {
        var newSortArray = [];

        if (currentSortString != null)
        {
            var sortArray = currentSortString.split(",");
            for (var j = 0; j < sortArray.length; j++)
            {
                if (sortArray[j] != columnName && sortArray[j] != "+" + columnName && sortArray[j] != "-" + columnName)
                    newSortArray.push(sortArray[j]);
            }
        }

        if (direction == "+") //Easier to read without the encoded + on the URL...
            direction = "";

        if (null !== direction)
            newSortArray = [direction + columnName].concat(newSortArray);

        return newSortArray.join(",");
    },

    /**
     * Change the currently selected view to the named view
     * @param {Object} view An object which contains the following properties.
     * @param {String} [view.type] the type of view, either a 'view' or a 'report'.
     * @param {String} [view.viewName] If the type is 'view', then the name of the view.
     * @param {String} [view.reportId] If the type is 'report', then the report id.
     * @param urlParameters <b>NOTE: Experimental parameter; may change without warning.</b> A set of filter and sorts to apply as URL parameters when changing the view.
     */
    changeView : function(view, urlParameters)
    {
        if (false === this.fireEvent("beforechangeview", this, view, urlParameters))
            return;

        var skipPrefixes = [".offset", ".showRows", ".viewName", ".reportId"];
        var newParamValPairs = [];        
        if (view)
        {
            if (view.type == 'report')
                newParamValPairs.push([".reportId", view.reportId]);
            else if (view.type == 'view')
                newParamValPairs.push([".viewName", view.viewName]);
            else
                newParamValPairs.push([".viewName", view]);
        }

        if (urlParameters)
        {
            if (urlParameters.filter && urlParameters.filter.length > 0)
            {
                for (var i = 0; i < urlParameters.filter.length; i++)
                {
                    var filter = urlParameters.filter[i];
                    newParamValPairs.push(["." + filter.fieldKey + "~" + filter.op, filter.value]);
                }
            }

            if (urlParameters.sort && urlParameters.sort.length > 0)
            {
                var newSortArray = [];
                for (var i = 0; i < urlParameters.sort.length; i++)
                {
                    var sort = urlParameters.sort[i];
                    newSortArray.push((sort.dir == "+" ? "" : sort.dir) + sort.fieldKey);
                }
                newParamValPairs.push([".sort", newSortArray.join(",")]);
            }

            if (urlParameters.containerFilter)
                newParamValPairs.push([".containerFilterName", urlParameters.containerFilter]);

            // removes all filter, sort, and container filter parameters
            skipPrefixes.push(".");
            skipPrefixes.push(".sort");
            skipPrefixes.push(".containerFilterName");
        }


        this._setParams(newParamValPairs, skipPrefixes);
    },

    // private
    _initElements : function ()
    {
        this.form  = document.forms[this.name];
        this.table = Ext.get("dataregion_" + this.name);
        var msgEl  = Ext.get("dataregion_msgbox_" + this.name);
        if (msgEl)
        {
            this.msgbox = new LABKEY.MessageArea({parent: msgEl});
            this.msgbox.on('rendermsg', this._onRenderMessageArea, this);
        }
        this.header = Ext.get("dataregion_header_" + this.name);
        if (this.header)
            this.header.applyStyles('width: 100%');
        this.footer = Ext.get("dataregion_footer_" + this.name);

        // derived DataRegion's may not include the form id
        if (!this.form && this.table)
        {
            var el = this.table.dom;
            do
            {
                el = el.parentNode;
            } while (el != null && el.tagName != "FORM");
            if (el) this.form = el;
        }

        if (this.form)
        {
            this.form.dataRegion = this;
            if (this.showRecordSelectors)
            {
                if (this.isPageSelected())
                {
                    // set the 'select all on page' checkbox state
                    var toggle = this.form[".toggle"];
                    if (toggle)
                        toggle.checked = true;
                    this.onSelectChange(true);
                }
                else
                {
                    this.onSelectChange(this.hasSelected());
                }
            }
            else
            {
                this.updateRequiresSelectionButtons(false);
            }
        }

        if (this.headerLock()) {
            this._initHeaderLock();
        }

        Ext.EventManager.on(window, "load", this._resizeContainer, this, {single: true});
        Ext.EventManager.on(window, "resize", this._resizeContainer, this);
    },

    headerLock : function() {
        return this._allowHeaderLock === true;
    },

    _initHeaderLock : function() {
        // initialize constants
        this.headerRow          = Ext.get('dataregion_header_row_' + this.name);
        this.headerRowContent   = this.headerRow.child('td');
        this.headerSpacer       = Ext.get('dataregion_header_row_spacer_' + this.name);
        this.colHeaderRow       = Ext.get('dataregion_column_header_row_' + this.name);
        this.colHeaderRowSpacer = Ext.get('dataregion_column_header_row_spacer_' + this.name);

        // check if the header row is being used
        this.includeHeader = this.headerRow.isDisplayed();

        // initialize row contents
        this.rowContent         = Ext.query(" > td[class*=labkey-column-header]", this.colHeaderRow.id);
        this.rowSpacerContent   = Ext.query(" > td[class*=labkey-column-header]", this.colHeaderRowSpacer.id);

        // performance degradation
        if (this.rowContent.length > 30)
        {
            this._allowHeaderLock = false;
            return;
        }

        // initialize panel listeners
        this.on('afterpanelshow', this._resizeContainer, this);
        this.on('afterpanelhide', this._resizeContainer, this);
        Ext.EventManager.on(window, "scroll", this._scrollContainer, this);

        // initialize timer task for resizing and scrolling
        this.first = -1;
        this.resizeTask = new Ext.util.DelayedTask(function(){
            this.first = -1;
            this._resetHeader();
            this._calculateHeader();
        }, this);

        this._calculateHeader();
    },

    _calculateHeader : function() {
        this._calculateHeaderLock();
        this._scrollContainer();
    },

    _calculateHeaderLock : function() {
        var el, z, s;
        for (var i=0; i < this.rowContent.length; i++) {
            el = Ext.get(this.rowContent[i]);
            s = { width : el.getWidth(), height: el.getHeight() };
            el.setSize(s);
            z = Ext.get(this.rowSpacerContent[i]); // must be done after 'el' is set (ext side-effect?)
            z.setSize(s);
        }

        if (this.first < 0) this.first = this._findPos((this.includeHeader ? this.headerRow : this.colHeaderRow));
        this.hdrLocked = false;
    },

    _findPos : function(o) {
        // TODO: look for Ext optimized function -- possibly use o.getXY()
        var obj = o.dom;
        var curleft = curtop = 0;
        if (obj && obj.offsetParent) {
            do {
                curleft += obj.offsetLeft;
                curtop  += obj.offsetTop;
            } while (obj = obj.offsetParent);
        }

        var curbottom = curtop+this.table.getHeight();
        curbottom -= (this.includeHeader ? (this.headerRow.getComputedHeight()*2) : 0) + (this.colHeaderRow.getComputedHeight()*2);
        var hdrOffset = this.includeHeader ? this.headerRow.getComputedHeight() : 0;

        return [ curleft, curtop, curbottom, hdrOffset ];
    },

    // WARNING: This function is called often. Performance implications for each line.
    _scrollContainer : function() {
        // calculate Y scrolling
        if (window.pageYOffset > this.first[1] && window.pageYOffset < this.first[2]) {
            var tWidth = this.table.getComputedWidth();
            this.headerSpacer.dom.style.display = "table-row";
            this.colHeaderRowSpacer.dom.style.display = "table-row";
            this.headerRow.applyStyles("top: 0; position: fixed; " +
                    "min-width: " + tWidth + "px; ");
            this.headerRowContent.applyStyles("min-width: " + (tWidth-4) + "px; ");
            this.colHeaderRow.applyStyles("position: fixed; background: white; top: " + this.first[3] + "px;" +
                    "min-width: " + tWidth + "px; box-shadow: -2px 5px 5px #DCDCDC;");
            this.hdrLocked = true;
        }
        else if (this.hdrLocked && window.pageYOffset >= this.first[2]) {
            var top = this.first[2]-window.pageYOffset;
            this.headerRow.applyStyles("top: " + top + "px;");
            this.colHeaderRow.applyStyles("top: " + (top + this.first[3]) + "px;");
        }
        else { this._resetHeader(); }

        // Calculate X Scrolling
        if (this.hdrLocked && window.pageXOffset > 0) {
            this.headerRow.applyStyles("left: " + (this.first[0]-window.pageXOffset) + "px;");
            this.colHeaderRow.applyStyles("left: " + (this.first[0]-window.pageXOffset) + "px;");
        }
        else if (window.pageXOffset == 0) {
            this.headerRow.applyStyles("left: auto;");
            this.colHeaderRow.applyStyles("left: auto;");
        }
    },

    // puts header back in normal spot
    _resetHeader : function() {
        this.hdrLocked = false;
        this.headerRow.applyStyles("top: auto; position: static;");
        this.colHeaderRow.applyStyles("top: auto; position: static; box-shadow: none;");
        this.headerSpacer.dom.style.display = "none";
        this.headerSpacer.setHeight(this.headerRow.getHeight());
        this.colHeaderRowSpacer.dom.style.display = "none";
    },

    // private
    _showPagination : function (el)
    {
        if (!el) return;
        var pagination = el.child("div[class='labkey-pagination']", true);
        if (pagination)
            pagination.style.visibility = "visible";
    },

    // private
    _resizeContainer : function (init)
    {
        if (!this.table) return;

        var headerWidth = this.table.getWidth(true);

        if (this.header)
        {
            var frameWidth = this.header.getFrameWidth("lr") + this.header.parent().getFrameWidth("lr");
            this.header.setWidth(headerWidth - frameWidth);
        }

        if (this.footer)
        {
            var frameWidth = this.footer.getFrameWidth("lr") + this.footer.parent().getFrameWidth("lr");
            this.footer.setWidth(headerWidth - frameWidth);
        }

        if (init !== true && this.headerLock()) {
            if (this.resizeTask) this.resizeTask.delay(110);
        }
    },

    // private
    _removeParams : function (skipPrefixes)
    {
        this._setParams(null, skipPrefixes);
    },

    _setParam : function (param, value, skipPrefixes)
    {
        this._setParams([[param, value]], skipPrefixes);
    },

    // private
    _setParams : function (newParamValPairs, skipPrefixes)
    {
        for (var i in skipPrefixes)
            skipPrefixes[i] = this.name + skipPrefixes[i];

        var paramValPairs = LABKEY.DataRegion._filterUI.getParamValPairs(this.requestURL, skipPrefixes);
        if (newParamValPairs)
        {
            for (var i = 0; i < newParamValPairs.length; i++)
            {
                var param = newParamValPairs[i][0],
                    value = newParamValPairs[i][1];
                if (null != param && null != value)
                {
                    if (param.indexOf(this.name) !== 0)
                        param = this.name + param;

                    paramValPairs[paramValPairs.length] = [param, value];
                }
            }
        }
        LABKEY.DataRegion._filterUI.setSearchString(this.name, LABKEY.DataRegion._filterUI.buildQueryString(paramValPairs));
    },

    // private
    _setAllCheckboxes : function (value, elementName)
    {
        var elems = this.form.elements;
        var l = elems.length;
        var ids = [];
        for (var i = 0; i < l; i++)
        {
            var e = elems[i];
            if (e.type == 'checkbox' && !e.disabled && (elementName == null || elementName == e.name))
            {
                e.checked = value;
                if (e.name != ".toggle")
                    ids.push(e.value);
            }
        }
        return ids;
    },

    // private
    _showSelectMessage : function (msg)
    {
        if (this.showRecordSelectors)
        {
            msg += "&nbsp;<span class='labkey-button select-none'>Select None</span>";
            var showOpts = new Array();
            if (this.showRows != "all")
                showOpts.push("<span class='labkey-button show-all'>Show All</span>");
            if (this.showRows != "selected")
               showOpts.push("<span class='labkey-button show-selected'>Show Selected</span>");
            if (this.showRows != "unselected")
               showOpts.push("<span class='labkey-button show-unselected'>Show Unselected</span>");
            msg += "&nbsp;" + showOpts.join(" ");
        }

        // add the record selector message, the link handlers will get added after render in _onRenderMessageArea
        var el = this.addMessage(msg, 'selection');
    },

    // private
    /**
     * render listener for the message area, to add handlers for the link targets.
     */
    _onRenderMessageArea : function (cmp, partName, el)
    {
        if (this.showRecordSelectors && partName == 'selection' && el)
        {
            var selectNoneEl = el.child(".labkey-button.select-none");
            if (selectNoneEl)
                selectNoneEl.on('click', this.selectNone, this);

            var showAllEl = el.child(".labkey-button.show-all");
            if (showAllEl)
                showAllEl.on('click', this.showAll, this);

            var showSelectedEl = el.child(".labkey-button.show-selected");
            if (showSelectedEl)
                showSelectedEl.on('click', this.showSelected, this);

            var showUnselectedEl = el.child(".labkey-button.show-unselected");
            if (showUnselectedEl)
                showUnselectedEl.on('click', this.showUnselected, this);
        }
        else if (partName == 'customizeview' && el)
        {
            var revertEl = el.child(".labkey-button.unsavedview-revert");
            if (revertEl)
                revertEl.on('click', this.revertCustomView, this);

            var showCustomizeViewEl = el.child(".labkey-button.unsavedview-edit");
            if (showCustomizeViewEl)
                showCustomizeViewEl.on('click', function () { this.showCustomizeView(undefined, true); }, this);

            var saveEl = el.child(".labkey-button.unsavedview-save");
            if (saveEl)
                saveEl.on('click', this.saveSessionCustomView, this);
        }
    },

    // private
    updateRequiresSelectionButtons : function (hasSelected)
    {
        var fn = hasSelected ? LABKEY.Utils.enableButton : LABKEY.Utils.disableButton;

        // 10566: for javascript perf on IE stash the requires selection buttons
        if (!this._requiresSelectionButtons)
        {
            // escape ', ", and \
            var escaped = this.name.replace(/('|"|\\)/g, "\\$1");
            this._requiresSelectionButtons = Ext.DomQuery.select("a[labkey-requires-selection='" + escaped + "']");
        }
        Ext.each(this._requiresSelectionButtons, fn);

    },

    // private
    onSelectChange : function (hasSelected)
    {
        this.updateRequiresSelectionButtons(hasSelected);
        this.fireEvent('selectchange', this, hasSelected);
    },

    onButtonClick : function(buttonId)
    {
        return this.fireEvent("buttonclick", buttonId, this);
    },

    /**
     * Show a ribbon panel. tabPanelConfig is an Ext config object for a TabPanel, the only required
     * value is the items array.
     */
    showButtonPanel : function (panelButton, tabPanelConfig)
    {
        this._showButtonPanel(this.header, panelButton.getAttribute("panelId"), true, tabPanelConfig);
    },

    _showButtonPanel : function(headerOrFooter, panelId, animate, tabPanelConfig)
    {
        var panelDiv = headerOrFooter.child(".labkey-ribbon");
        if (panelDiv)
        {
            var panelToHide = null;
            // If we find a spot to put the panel, check its current contents
            if (this.currentPanelId)
            {
                // We're currently showing a ribbon panel, so remember that we need to hide it
                panelToHide = this.panelButtonContents[this.currentPanelId];
            }

            var _duration = 0.4, y, h;

            // Create a callback function to render the requested ribbon panel
            var callback = function()
            {
                if (panelToHide)
                {
                    panelToHide.setVisible(false);
                }
                if (this.currentPanelId != panelId)
                {
                    panelDiv.setDisplayed(true);
                    if (!this.panelButtonContents[panelId])
                    {
                        var minWidth = 0;
                        var tabContentWidth = 0;
                        var VERTICAL_TAB_HEIGHT = 28; // pixels. Way to measure how tall the main panel should be
                        var height = VERTICAL_TAB_HEIGHT * 4;
                        if (tabPanelConfig.items.length > 4)
                            height = VERTICAL_TAB_HEIGHT * tabPanelConfig.items.length;

                        // New up the TabPanel if we haven't already
                        // Only create one per button, even if that button is rendered both above and below the grid
                        tabPanelConfig.cls ='vertical-tabs';
                        tabPanelConfig.tabWidth = 80;
                        tabPanelConfig.renderTo = panelDiv;
                        tabPanelConfig.activeGroup = 0;
                        tabPanelConfig.height = height;
                        var newItems = new Array(tabPanelConfig.items.length);
                        for (var i = 0; i < tabPanelConfig.items.length; i++)
                        {
                            newItems[i] = tabPanelConfig.items[i];
                            newItems[i].autoScroll = true;

                            //FF and IE won't auto-resize the tab panel to fit the content
                            //so we need to calculate the min size and set it explicitly
                            if (Ext.isGecko || Ext.isIE)
                            {
                                var item = newItems[i];
                                if (!item.events)
                                    newItems[i] = item = Ext.create(item, 'grouptab');
                                item.removeClass("x-hide-display");
                                if (item.items.getCount() > 0 && item.items.items[0].contentEl)
                                {
                                    tabContentWidth = Ext.get(item.items.items[0].contentEl).getWidth();
                                    item.addClass("x-hide-display");
                                    minWidth = Math.max(minWidth, tabContentWidth);
                                }
                            }
                        }
                        tabPanelConfig.items = newItems;
                        if ((Ext.isGecko || Ext.isIE) && minWidth > 0 && headerOrFooter.getWidth() < minWidth)
                            tabPanelConfig.width = minWidth;
                        this.panelButtonContents[panelId] = new Ext.ux.GroupTabPanel(tabPanelConfig);
                    }
                    else
                    {
                        // Otherwise, be sure that it's parented correctly - it might have been shown
                        // in a different button bar position
                        this.panelButtonContents[panelId].getEl().appendTo(Ext.get(panelDiv));
                    }

                    this.currentPanelId = panelId;

                    // Slide it into place
                    var panelToShow = this.panelButtonContents[panelId];
                    panelToShow.setVisible(true);

                    if (this.headerLock()) {
                        y = this.colHeaderRow.getY();
                        h = this.headerSpacer.getHeight();
                    }

                    panelToShow.getEl().slideIn('t',{
                        callback : function() {
                            this.fireEvent('afterpanelshow');
                        },
                        concurrent : true,
                        duration   : _duration,
                        scope      : this
                    });

                    if (this.headerLock()) {
                        this.headerSpacer.setHeight(h+panelToShow.getHeight());
                        this.colHeaderRow.shift({y:(y+panelToShow.getHeight()), duration : _duration, concurrent: true, scope: this});
                    }

                    panelToShow.setWidth(panelToShow.getResizeEl().getWidth());
                }
                else
                {
                    this.currentPanelId = null;
                    panelDiv.setDisplayed(false);
                }
            };

            if (this.currentPanelId)
            {
                // We're already showing a ribbon panel, so hide it before showing the new one
                if (this.headerLock()) {
                    y = this.colHeaderRow.getY();
                    h = this.headerSpacer.getHeight();
                }

                panelToHide.getEl().slideOut('t',{
                    callback: function() {
                        this.fireEvent('afterpanelhide');
                        callback.call(this);
                    },
                    concurrent : true,
                    duration   : _duration,
                    scope      : this
                });

                if (this.headerLock()) {
                    this.headerSpacer.setHeight(h-panelToHide.getHeight());
                    this.colHeaderRow.shift({y:(y-panelToHide.getHeight()), duration : _duration, concurrent: true, scope: this});
                }
            }
            else
            {
                // We're not showing another ribbon panel, so show the new one right away
                callback.call(this);
            }
        }
    },

    /**
     * Show the customize view interface.
     * @param activeTab {[String]} Optional. One of "ColumnsTab", "FilterTab", or "SortTab".  If no value is specified (or undefined), the ColumnsTab will be shown.
     * @param hideMessage {[boolean]} Optional. True to hide the DataRegion message bar when showing.
     * @param animate {[boolean]} Optional. True to slide in the ribbon panel.
     */
    showCustomizeView : function (activeTab, hideMessage, animate)
    {
        if (hideMessage)
            this.hideMessage();

        // UNDONE: when both header and footer are rendered, need to show the panel in the correct button bar
        var headerOrFooter = this.header || this.footer;

        if (!this.customizeView)
        {
            var timerId = function () {
                timerId = 0;
                this.showLoadingMessage("Opening custom view designer...");
            }.defer(500, this);

            LABKEY.initializeViewDesigner(function () {
                var additionalFields = {};
                var userFilter = this.getUserFilter();
                var userSort = this.getUserSort();

                for (var i = 0; i < userFilter.length; i++)
                    additionalFields[userFilter[i].fieldKey] = true;

                for (i = 0; i < userSort.length; i++)
                    additionalFields[userSort[i].fieldKey] = true;

                var fields = [];
                for (var fieldKey in additionalFields)
                    fields.push(fieldKey);

                var viewName = (this.view && this.view.name) || this.viewName || "";
                LABKEY.Query.getQueryDetails({
                    schemaName : this.schemaName,
                    queryName  : this.queryName,
                    viewName   : viewName,
                    fields     : fields,
                    initializeMissingView : true,
                    success    : function (json, response, options) {
                        if (timerId > 0)
                            clearTimeout(timerId);
                        else
                            this.hideMessage();

                        var minWidth = Math.max(700, headerOrFooter.getWidth(true));
                        var renderTo = Ext.getBody().createChild({tag: "div", customizeView: true, style: {display: "none"}});

                        this.customizeView = new LABKEY.DataRegion.ViewDesigner({
                            renderTo    : renderTo,
                            width       : headerOrFooter.getWidth(true),
                            activeGroup : activeTab,
                            dataRegion  : this,
                            schemaName  : this.schemaName,
                            queryName   : this.queryName,
                            viewName    : viewName,
                            query       : json,
                            userFilter  : userFilter,
                            userSort    : userSort,
                            userContainerFilter       : this.getUserContainerFilter(),
                            allowableContainerFilters : this.allowableContainerFilters
                        });

                        this.customizeView.on("viewsave", this.onViewSave, this);

                        this.panelButtonContents["~~customizeView~~"] = this.customizeView;
                        this._showButtonPanel(headerOrFooter, "~~customizeView~~", animate, null);
                    },
                    scope: this
                });
            }, this);
        }
        else
        {
            if (activeTab)
            {
                this.customizeView.setActiveGroup(activeTab);
                var group = this.customizeView.activeGroup;
                if (!group.activeItem)
                    group.setActiveTab(group.getMainItem());
            }
            if (this.currentPanelId != "~~customizeView~~")
                this._showButtonPanel(headerOrFooter, "~~customizeView~~", animate, null);
        }
    },

    /**
     * Hide the customize view interface if it is showing.
     */
    hideCustomizeView : function ()
    {
        if (this.customizeView && this.customizeView.isVisible())
            this._showButtonPanel(this.header || this.footer, "~~customizeView~~", true, null);
    },

    // private
    toggleShowCustomizeView : function ()
    {
        if (this.customizeView && this.customizeView.isVisible())
            this.hideCustomizeView();
        else
            this.showCustomizeView();
    },

    // private
    deleteCustomView : function ()
    {
        var title = "Delete " +
                (this.view && this.view.shared ? "shared " : "your ") +
                (this.view && this.view.session ? "unsaved" : "") + "view";
        var msg = "Are you sure you want to delete the ";
        if (this.viewName)
            msg += " '<em>" + Ext.util.Format.htmlEncode(this.viewName) + "</em>'";
        else
            msg += "default";
        msg += " saved view";
        if (this.view && this.view.containerPath && this.containerPath != LABKEY.ActionURL.getContainer())
        {
            msg += " from '" + this.view.containerPath + "'";
        }
        msg += "?";
        Ext.Msg.confirm(title, msg, function (btnId) {
            if (btnId == "yes")
            {
                this._deleteCustomView(true, "Deleting view...");
            }
        }, this);
    },

    // private
    revertCustomView : function ()
    {
        this._deleteCustomView(false, "Reverting view...");
    },

    // private
    _deleteCustomView : function (complete, message)
    {
        var timerId = function () {
            timerId = 0;
            this.showLoadingMessage(message);
        }.defer(500, this);

        Ext.Ajax.request({
            url      : LABKEY.ActionURL.buildURL("query", "deleteView"),
            jsonData : {schemaName: this.schemaName, queryName: this.queryName, viewName: this.viewName, complete: complete},
            method   : "POST",
            scope    : this,
            success  : LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                if (timerId > 0)
                    clearTimeout(timerId);
                this.showSuccessMessage();
                // change view to either a shadowed view or the default view
                var viewName = json.viewName;
                this.changeView({type:'view', viewName: viewName});
            }, this),
            failure  : LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                if (timerId > 0)
                    clearTimeout(timerId);
                this.showErrorMessage(json.exception);
            }, this, true)
        });
    },

    // private
    saveSessionCustomView : function ()
    {
        // Note: currently only will save session views. Future version could create a new view using url sort/filters.
        if (!(this.view && this.view.session))
            return;

        var self = this;
        function showPrompt()
        {
            var config = Ext.applyIf({
                canEditSharedViews: self.canEditSharedViews,
                canEdit: LABKEY.DataRegion._getCustomViewEditableErrors(config).length == 0,
                success: function (win, o) {
                    var timerId = function () {
                        timerId = 0;
                        self.showLoadingMessage("Saving custom view...");
                    }.defer(500, self);

                    var jsonData = {
                        schemaName: self.schemaName,
                        "query.queryName": self.queryName,
                        "query.viewName": self.viewName,
                        newName: o.name,
                        inherit: o.inherit,
                        shared: o.shared
                    };

                    Ext.Ajax.request({
                        url: LABKEY.ActionURL.buildURL("query", "saveSessionView"),
                        method: "POST",
                        jsonData: jsonData,
                        headers : {
                            'Content-Type' : 'application/json'
                        },
                        scope: self,
                        success: LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                            if (timerId > 0)
                                clearTimeout(timerId);
                            self.showSuccessMessage();
                            self.changeView({type:'view', viewName:o.name});
                        }, self),
                        failure: LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                            if (timerId > 0)
                                clearTimeout(timerId);
                            self.showErrorMessage(json.exception);
                        }, self, true)
                    });
                },
                scope: self
            }, self.view);

            LABKEY.DataRegion.saveCustomizeViewPrompt(config);
        }

        // CONSIDER: moving into LABKEY.DataRegion constructor
        if (this.canEditSharedViews === undefined)
        {
            LABKEY.Security.getUserPermissions({
                userId: LABKEY.user.id,
                success: function (info) {
                    var canEditSharedViews = false;
                    if (info && info.container && info.container.effectivePermissions)
                        canEditSharedViews = info.container.effectivePermissions.indexOf("org.labkey.api.security.permissions.EditSharedViewPermission") != -1;

                    this.canEditSharedViews = canEditSharedViews;
                    showPrompt();
                },
                scope: this
            });
        }
        else
        {
            showPrompt();
        }

    },

    onViewSave : function (designer, savedViewsInfo, urlParameters) {
        if (savedViewsInfo && savedViewsInfo.views.length > 0)
        {
            this.hideCustomizeView();
            this.changeView({
                type: 'view',
                viewName:savedViewsInfo.views[0].name}, urlParameters);
        }
    }

});


/**
 * Static method to add or remove items from the selection for a given {@link #selectionKey}.
 *
 * @param config A configuration object with the following properties:
 * @param {String} config.selectionKey See {@link #selectionKey}.
 * @param {Array} config.ids Array of primary key ids for each row to select/unselect.
 * @param {Boolean} config.checked If true, the ids will be selected, otherwise unselected.
 * @param {Function} config.success The function to be called upon success of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>data:</b> an object with the property 'count' to indicate the updated selection count.
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Function} [config.failure] The function to call upon error of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
 * @param {string} [config.containerPath] An alternate container path. If not specified, the current container path will be used.
 *
 * @see LABKEY.DataRegion#getSelected
 * @see LABKEY.DataRegion#clearSelected
 */
LABKEY.DataRegion.setSelected = function (config)
{
    var url = LABKEY.ActionURL.buildURL("query", "setSelected.api", config.containerPath,
        { 'key' : config.selectionKey, 'checked' : config.checked });
    var params = { id: config.ids || config.id };

    Ext.Ajax.request({
        url: url,
        method: "POST",
        params: params,
        scope: config.scope,
        success: LABKEY.Utils.getOnSuccess(config),
        failure: LABKEY.Utils.getOnFailure(config)
    });
};

/**
 * Static method to clear all selected items for a given {@link #selectionKey}.
 *
 * @param config A configuration object with the following properties:
 * @param {String} config.selectionKey See {@link #selectionKey}.
 * @param {Function} config.success The function to be called upon success of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>data:</b> an object with the property 'count' of 0 to indicate an empty selection.
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Function} [config.failure] The function to call upon error of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
 * @param {string} [config.containerPath] An alternate container path. If not specified, the current container path will be used.
 *
 * @see LABKEY.DataRegion#setSelected
 * @see LABKEY.DataRegion#getSelected
 */
LABKEY.DataRegion.clearSelected = function (config)
{
    var url = LABKEY.ActionURL.buildURL("query", "clearSelected.api", config.containerPath,
        { 'key' : config.selectionKey });

    Ext.Ajax.request({ url: url });
};

/**
 * Static method to get all selected items for a given {@link #selectionKey}.
 *
 * @param config A configuration object with the following properties:
 * @param {String} config.selectionKey See {@link #selectionKey}.
 * @param {Function} config.success The function to be called upon success of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>data:</b> an object with the property 'selected' that is an array of the primary keys for the selected rows.
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Function} [config.failure] The function to call upon error of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
 * @param {string} [config.containerPath] An alternate container path. If not specified, the current container path will be used.
 *
 * @see LABKEY.DataRegion#setSelected
 * @see LABKEY.DataRegion#clearSelected
 */
LABKEY.DataRegion.getSelected = function (config)
{
    var url = LABKEY.ActionURL.buildURL("query", "getSelected.api", config.containerPath,
        { 'key' : config.selectionKey });

    Ext.Ajax.request({
        url: url,
        success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
        failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
    });
};

// private
LABKEY.DataRegion._getCustomViewEditableErrors = function (view)
{
    var errors = [];
    if (view)
    {
        if (!view.editable)
            errors.push("The view is read-only and cannot be edited.");
    }
    return errors;
};

// private
LABKEY.DataRegion.saveCustomizeViewPrompt = function (config)
    {
        var success = config.success;
        var scope = config.scope;

        var viewName = config.name;
        var hidden = config.hidden;
        var session = config.session;
        var inherit = config.inherit;
        var shared = config.shared;
        var containerPath = config.containerPath;
        var canEdit = config.canEdit;
        var canEditSharedViews = config.canEditSharedViews;
        var targetContainers = config.targetContainers;
        var allowableContainerFilters = config.allowableContainerFilters;

        var containerData = new Array();
        if (targetContainers)
        {
            for (var i = 0; i < targetContainers.length; i++)
            {
                var targetContainer = targetContainers[i];
                containerData[i] = [targetContainers[i].path];
            }
        }
        else
        {
            // Assume view should be saved to current container
            containerData[0] = LABKEY.ActionURL.getContainer();
        }

        var containerStore = new Ext.data.ArrayStore({
            fields: [ 'path' ],
            data: containerData
        });

        var disableSharedAndInherit = LABKEY.user.isGuest || hidden /*|| session*/ || (containerPath && containerPath != LABKEY.ActionURL.getContainer());
        var newViewName = viewName || "New View";
        if (!canEdit && viewName)
            newViewName = viewName + " Copy";

        var warnedAboutMoving = false;

        var win = new Ext.Window({
            title: "Save Custom View" + (viewName ? ": " + Ext.util.Format.htmlEncode(viewName) : ""),
            cls: "extContainer",
            bodyStyle: "padding: 6px",
            modal: true,
            width: 480,
            height: 260,
            layout: "form",
            defaults: {
                tooltipType: "title"
            },
            items: [{
                ref: "defaultNameField",
                xtype: "radio",
                fieldLabel: "View Name",
                boxLabel: "Default view for this page",
                inputValue: "default",
                name: "saveCustomView_namedView",
                checked: canEdit && !viewName,
                disabled: hidden || !canEdit
            },{
                xtype: "compositefield",
                ref: "nameCompositeField",
                items: [{
                    xtype: "radio",
                    fieldLabel: "",
                    boxLabel: "Named",
                    inputValue: "named",
                    name: "saveCustomView_namedView",
                    checked: !canEdit || viewName,
                    handler: function (radio, value) {
                        // nameCompositeField.items will be populated after initComponent
                        if (win.nameCompositeField.items.get)
                        {
                            var nameField = win.nameCompositeField.items.get(1);
                            if (value)
                                nameField.enable();
                            else
                                nameField.disable();
                        }
                    },
                    scope: this
                },{
                    fieldLabel: "",
                    xtype: "textfield",
                    name: "saveCustomView_name",
                    tooltip: "Name of the custom view",
                    tooltipType: "title",
                    allowBlank: false,
                    emptyText: "Name is required",
                    maxLength: 50,
                    autoCreate: {tag: 'input', type: 'text', size: '50'},
                    selectOnFocus: true,
                    value: newViewName,
                    disabled: hidden || (canEdit && !viewName)
                }]
            },{
                xtype: "box",
                style: "padding-left: 122px; padding-bottom: 8px",
                html: "<em>The current view is not editable.<br>Please enter an alternate view name.</em>",
                hidden: canEdit
            },{
                xtype: "spacer",
                height: "8"
            },{
                ref: "sharedField",
                xtype: "checkbox",
                name: "saveCustomView_shared",
                fieldLabel: "Shared",
                boxLabel: "Make this grid view available to all users",
                checked: shared,
                disabled: disableSharedAndInherit || !canEditSharedViews
            },{
                ref: "inheritField",
                xtype: "checkbox",
                name: "saveCustomView_inherit",
                fieldLabel: "Inherit",
                boxLabel: "Make this grid view available in child folders",
                checked: inherit,
                disabled: disableSharedAndInherit,
                hidden: !allowableContainerFilters || allowableContainerFilters.length <= 1,
                listeners: {
                    check: function(checkbox, checked) {
                        Ext.ComponentMgr.get("saveCustomView_targetContainer").setDisabled(!checked);
                    }
                }
            },{
                ref: "targetContainer",
                xtype: "combo",
                name: "saveCustomView_targetContainer",
                id: "saveCustomView_targetContainer",
                fieldLabel: "Save in Folder",
                store: containerStore,
                value: config.containerPath,
                displayField: 'path',
                valueField: 'path',
                width: 300,
                triggerAction: 'all',
                mode: 'local',
                editable: false,
                hidden: !allowableContainerFilters || allowableContainerFilters.length <= 1,
                disabled: !inherit,
                listeners: {
                    select: function(combobox) {
                        if (!warnedAboutMoving && combobox.getValue() != config.containerPath)
                        {
                            warnedAboutMoving = true;
                            Ext.Msg.alert("Moving a Saved View", "If you save, this view will be moved from '" + config.containerPath + "' to " + combobox.getValue());
                        }
                    }
                }
            }],
            buttons: [{
                text: "Save",
                handler: function () {
                    var nameField = win.nameCompositeField.items.get(1);
                    if (!canEdit && viewName == nameField.getValue())
                    {
                        Ext.Msg.alert("Error saving", "You must save this view with an alternate name.");
                        return;
                    }

                    var o = {};
                    if (hidden)
                    {
                        o = {
                            name: viewName,
                            shared: shared,
                            hidden: true,
                            session: session // set session=false for hidden views?
                        };
                    }
                    else
                    {
                        o.name = "";
                        if (!win.defaultNameField.getValue())
                            o.name = nameField.getValue();
                        o.session = false;
                        if (!o.session && canEditSharedViews)
                        {
                            o.shared = win.sharedField.getValue();
                            o.inherit = win.inheritField.getValue();
                        }
                    }

                    if (o.inherit)
                    {
                        o.containerPath = win.targetContainer.getValue();
                    }

                    success.call(scope, win, o);
                    win.close();
                },
                scope: this
            },{
                text: "Cancel",
                handler: function () { win.close(); }
            }]
        });
        win.show();
    };

// FILTER UI

// TODO convert completely to Ext
// NOTE filter UI is shared, but I still don't like all these global/single instance variables

// private
LABKEY.DataRegion._filterUI =
{
    _tableName : "",
    _fieldName : "",
    _fieldCaption : "",
    _filterWin : null,

    hideFilterPanel : function ()
    {
        if (this._filterWin)
            this._filterWin.close();
    },

    showFilterPanel : function(dataRegionName, colName, caption, dataType, mvEnabled, queryString, dialogTitle, confirmCallback)
    {
        this._fieldName = colName;
        this._fieldCaption = caption;
        this._tableName = dataRegionName;
        this._mappedType = this.getMappedType(dataType);

        if (!queryString)
        {
            queryString = LABKEY.DataRegions[dataRegionName] ? LABKEY.DataRegions[dataRegionName].requestURL : null;
        }
        var paramValPairs = this.getParamValPairs(queryString, null);
        
        if (!confirmCallback)
        {
            // Invoked as part of a regular filter dialog on a grid
            this.changeFilterCallback = this.changeFilter;
        }
        else
        {
            // Invoked from GWT, which will handle the commit itself
            this.changeFilterCallback = confirmCallback;
        }

        var comboStore1 = this.fillOptions(mvEnabled, this._mappedType, 0);
        var comboStore2 = this.fillOptions(mvEnabled, this._mappedType, 1);

        var self = this; //Used so we can get _mappedType within Ext component configs (comboBoxes && validators).

        var filterComboBox1 = new Ext.form.ComboBox({
            emptyText: 'Choose a filter:',
            autoSelect: false,
            width: 250,
            allowBlank: 'false',
            triggerAction: 'all',
            fieldLabel: 'Filter Type',
            store: comboStore1,
            displayField: 'text',
            typeAhead: 'false',
            forceSelection: true,
            mode: 'local',
            clearFilterOnReset: false,
            editable: false,
            listeners:{
                scope: this,
                select:setField1,
                afterRender: function(combo){
                    //afterRender of combobox we set the default value.
                    if(this._mappedType == 'LONGTEXT' || this._mappedType == 'TEXT'){
                        //Starts With
                        combo.setValue(combo.getStore().getAt(8).data.text);
                    } else{
                        //Equals
                        combo.setValue(combo.getStore().getAt(1).data.text);
                    }
                }
            },
            scope: this
        });

        var filterComboBox2 = new Ext.form.ComboBox({
            emptyText: 'Choose a filter:',
            width: 250,
            allowBlank: 'false',
            triggerAction: 'all',
            fieldLabel: 'and',
            store: comboStore2,
            displayField: 'text',
            typeAhead: 'false',
            forceSelection: true,
            mode: 'local',
            clearFilterOnReset: false,
            editable: false,
            listeners:{
                scope: this,
                disable: function(combo){
                    inputField2.disable(); //If this combobox is disabled then so is the input field.
                },
                enable: setField2, //When this combobox gets re-enabled we want to re-enable the input box as well, but only if we're supposed to.
                select:setField2,
                afterRender: function(combo){
                    //Set the default field to "No Other Filter"
                    combo.setValue(combo.getStore().getAt(0).data.text);
                    inputField2.disable();
                }
            },
            scope: this
        });

        function setField1(combo){
            var selectedValue = combo.getStore().getAt(combo.getStore().find('text', combo.getValue())).data.value;
            if(selectedValue == 'isblank' || selectedValue == 'isnonblank'|| selectedValue == 'hasmvvalue'|| selectedValue == 'nomvvalue' || selectedValue == ''){
                //Disable filterComboBox2.
                filterComboBox2.disable();
                //Disable the field and allow it to be blank for values 'isblank' and 'isnonblank'.
                inputField1.disable();
            } else{
                //enable filterComboBox2.
                filterComboBox2.enable();
                inputField1.enable();
                inputField1.focus('', 50);
            }
        }
        function setField2(combo){
            //Get the 'value' field of the selected item in the combo box.
            var selectedValue = combo.getStore().getAt(combo.getStore().find('text', combo.getValue())).data.value;
            if(selectedValue == 'isblank' || selectedValue == 'isnonblank'|| selectedValue == 'hasmvvalue'|| selectedValue == 'nomvvalue'|| selectedValue == ''){
                //Disable the field and allow it to be blank for values 'isblank' and 'isnonblank'.
                inputField2.disable();
            } else{
                inputField2.enable();
                inputField2.focus('', 50);
            }
        }

        var inputFieldConfig1 = {
            name          : 'value_1',
            id            : 'value_1',
            allowBlank    : false,
            width         : 250,
            blankText     : 'You must enter a value.',
            validateOnBlur: false,
            validator     : inputFieldValidator1,
            listeners     : {
                disable   : function(field){
                    //Call validate after disable so any pre-existing validation errors go away.
                    this.validate();
                }
            }
        };

        var inputFieldConfig2 = {
            name          : 'value_2',
            id            : 'value_2',
            allowBlank    : false,
            width         : 250,
            blankText     : 'You must enter a value.',
            validateOnBlur: false,
            validator     : inputFieldValidator2,
            listeners     : {
                disable   : function(field){
                    //Call validate after disable so any pre-existing validation errors go away.
                    this.validate();
                }
            }
        };

        if(this._mappedType == "DATE"){
            inputFieldConfig1.altFormats = LABKEY.Utils.getDateAltFormats();
            inputFieldConfig1.altFormats = LABKEY.Utils.getDateAltFormats();
        }

        var inputField1 = new Ext.form.TextField(inputFieldConfig1);
        var inputField2 = new Ext.form.TextField(inputFieldConfig2);

        // create a task to set the input focus that will get started after layout is complete, the task will
        // run for a max of 2000ms but will get stopped when the component receives focus
        this.focusTask = {interval:150, run: function(){inputField1.focus();}, scope: this, duration: 2000};
        inputField1.on('focus', function(){Ext.TaskMgr.stop(this.focusTask)}, this);

        function inputFieldValidator(input, cb)
        {
            if(cb.getStore().getAt(cb.getStore().find('text', cb.getValue())).data.value == 'in'){
                return validateEqOneOf(input, self._mappedType);
            }
            return validateInputField(input, self._mappedType);
        }

        function inputFieldValidator1(input){
            return inputFieldValidator(input, filterComboBox1);
        }

        function inputFieldValidator2(input){
            return inputFieldValidator(input, filterComboBox2);
        }

        function validateInputField(input, mappedType){
            if(input){
                    if(mappedType == "INT" && !isFinite(input)){
                        return input + " is not a valid integer.";
                    } else if(mappedType == "DECIMAL" && !isFinite(input)){
                        return input + " is not a valid decimal.";
                    } else if(mappedType == "DATE"){
                        //Javascript does not parse ISO dates, but if date matches we're done
                        if (input.match(/^\s*(\d\d\d\d)-(\d\d)-(\d\d)\s*$/) ||
                                input.match(/^\s*(\d\d\d\d)-(\d\d)-(\d\d)\s*(\d\d):(\d\d)\s*$/))
                        {
                            return true;
                        } else{
                            var dateVal = new Date(input);
                            if (isNaN(dateVal))
                            {
                                //If the user entered something other than numbers.
                                return input + " is not a valid date - it must be in the format m/d/Y";
                            } else {
                                //If the user entered part of a date we'll try to filter by it.
                                return true;
                            }
                        }
                    } else if(mappedType == "BOOL"){
                        var upperVal = input.toUpperCase();
                        if (upperVal == "TRUE" || value == "1" || upperVal == "Y" || upperVal == "YES" || upperVal == "ON" || upperVal == "T"
                                || upperVal == "FALSE" || value == "0" || upperVal == "N" || upperVal == "NO" || upperVal == "OFF" || upperVal == "F"){
                            return true;
                        } else {
                            return input + " is not a valid boolean. Try true,false; yes,no; on,off; or 1,0.";
                        }
                    } else {
                        //If it's not a DECIMAL, INT, DATE, or BOOL, then it's a string, and in that case the characters
                        //do not matter, so return true.
                        return true;
                    }
                } else{
                //If for some reason the input or mappedType are null then return false.
                //Generally happens when doing Equals One Of
                return "You must enter a value.";
            }
        }

        function validateEqOneOf(input, mappedType){
            // Used when "Equals One Of.." is selected. Calls validateInputField on each value entered.
            var values = input.split(';');
            var isValid = "";
            for(var i = 0; i < values.length; i++){
                isValid = validateInputField(values[i], mappedType);
                if(isValid === false){
                    return isValid;
                }
            }
            //If we make it out of the for loop we had no errors.
            return true;
        }
        
        var okHandler = function(){
            //Step 1: validate
            if(inputField1.isValid() && inputField2.isValid()){
                var filterType1 = filterComboBox1.getStore().getAt(filterComboBox1.getStore().find('text', filterComboBox1.getValue())).data.value;
                var filterType2 = filterComboBox2.getStore().getAt(filterComboBox2.getStore().find('text', filterComboBox2.getValue())).data.value;
                self.setFilter(inputField1.getValue(), inputField2.getValue(), filterType1, filterType2);
                self._filterWin.close();
            }
        };

        var cancelHandler = function(){
            self._filterWin.close();
        };

        var clearFilterHandler = function(){
            LABKEY.DataRegion._filterUI.clearFilter();
        };

        var clearAllFiltersHandler = function(){
            LABKEY.DataRegion._filterUI.clearAllFilters();
        };

        var filterPanel = new Ext.form.FormPanel({
            //Here we set up the Form Panel for the filter window.
            autoWidth: true,
            autoHeight: true,
            resizable: false,
            bodyStyle: 'padding: 6px',
            defaults:{
                msgTarget: 'under'
            },
            items: [filterComboBox1, inputField1, filterComboBox2, inputField2],
            buttons: [
                {text: 'OK', handler: okHandler},
                {text: 'CANCEL', handler: cancelHandler},
                {text: 'CLEAR FILTER', handler: clearFilterHandler},
                {text: 'CLEAR ALL FILTERS', handler: clearAllFiltersHandler}
            ]
        });
        filterPanel.on('afterlayout', function(cmp){Ext.TaskMgr.start(this.focusTask)}, this);

         this._filterWin = new Ext.Window({
             width: 400,
             autoHeight: true,
             modal: true,
             resizable: false,
             closeAction: 'close',
             keys:[{
                 key:Ext.EventObject.ENTER,
                 scope: this,
                 handler: okHandler
             },{
                 key:Ext.EventObject.ESC,
                 scope: this,
                 handler: cancelHandler
             }],
             items: filterPanel
         });

        this._filterWin.setTitle(dialogTitle ? dialogTitle : "Show Rows Where " + caption + "...");
        this._filterWin.show();

        //Fill in existing filters...
        var setCombo1 = true; //true if we have not filled in the existing filter for filterComboBox1
        for (var i = 0; i < paramValPairs.length; i++)
        {
            var pair = paramValPairs[i]; // Filter 1 or 2.
            var key = pair[0]; // Something like: "Issues.Title~startswith"
            var comparison = (key.split("~"))[1]; // combobox value (eq, neqornull, gte, etc.)
            var value = pair.length > 1 ? pair[1] : ""; //The user input.
            //If no filter is set then we will skip.
            if (key.indexOf(this._tableName + "." + this._fieldName + "~") != 0)
                continue;

            if(setCombo1){
                //Find the text of the comparison (ex: in = "Equals One Of") && set the text of the combobox.
                filterComboBox1.setValue(comboStore1.getAt(comboStore1.find('value', comparison)).get('text'));
                //set the value of the input field.
                inputField1.setValue(value);
                //Call setField so the textfield/datefield is properly disabled/enabled.
                setField1(filterComboBox1);
                setCombo1 = false;
            } else {
                //Find the text of the comparison (ex: in = "Equals One Of") && set the text of the combobox.
                filterComboBox2.setValue(comboStore2.getAt(comboStore2.find('value', comparison)).get('text'));
                //set the value of the input field.
                inputField2.setValue(value);
                //Call setField so the textfield/datefield is properly disabled/enabled.
                setField2(filterComboBox2);
            }
        }
    },

    _typeMap : {
        "BIGINT":"INT",
        "BIGSERIAL":"INT",
        "BIT":"BOOL",
        "BOOL":"BOOL",
        "BOOLEAN":"BOOL",
        "CHAR":"TEXT",
        "CLOB":"LONGTEXT",
        "DATE":"DATE",
        "DECIMAL":"DECIMAL",
        "DOUBLE":"DECIMAL",
        "DOUBLE PRECISION":"DECIMAL",
        "FLOAT":"DECIMAL",
        "INTEGER":"INT",
        "LONGVARCHAR":"LONGTEXT",
        "NTEXT":"LONGTEXT",
        "NUMERIC":"DECIMAL",
        "REAL":"DECIMAL",
        "SMALLINT":"INT",
        "TIME":"TEXT",
        "TIMESTAMP":"DATE",
        "TINYINT":"INT",
        "VARCHAR":"TEXT",
        "INT":"INT",
        "INT IDENTITY":"INT",
        "DATETIME":"DATE",
        "TEXT":"TEXT",
        "NVARCHAR":"TEXT",
        "INT2":"INT",
        "INT4":"INT",
        "INT8":"INT",
        "FLOAT4":"DECIMAL",
        "FLOAT8":"DECIMAL",
        "SERIAL":"INT",
        "USERID":"INT",
        "VARCHAR2":"TEXT" // Oracle
    },

    _mappedType : "TEXT",


    getMappedType : function(dataType)
    {
        var mappedType = this._typeMap[dataType.toUpperCase()];
        if (mappedType == undefined)
            mappedType = dataType.toUpperCase();
        return mappedType;
    },

    fillOptions : function(mvEnabled, mappedType, storeNum)
    {
        var store       = new Ext.data.ArrayStore({fields: ['text', 'value']});
        var comboRecord = Ext.data.Record.create(['text', 'value']);

        if(storeNum == 1){
            store.add(new comboRecord({text:'No Other Filter', value: ''}));
        } else{
            store.add(new comboRecord({text:'Has Any Value', value: ''}));
        }

        if (mappedType != "LONGTEXT")
        {
            if(mappedType == "DATE"){
                store.add(new comboRecord({text:'Equals', value: 'dateeq'}))
            } else {
                store.add(new comboRecord({text:'Equals', value: 'eq'}));
            }

            if (mappedType != "BOOL" && mappedType != "DATE"){
                store.add(new comboRecord({text:"Equals One Of (e.g. \"a;b;c\")", value: 'in'}));
            }

            if(mappedType == "DATE"){
                store.add(new comboRecord({text:'Does Not Equal', value: 'dateneq'}));
            } else {
                store.add(new comboRecord({text:'Does Not Equal', value: 'neqornull'}));
            }
        }

        if (mappedType != "LONGTEXT" && mappedType != "BOOL")
        {
            if(mappedType == "DATE"){
                store.add(new comboRecord({text:'Is Greater Than',             value: 'dategt'}));
                store.add(new comboRecord({text:'Is Less Than',                value: 'datelt'}));
                store.add(new comboRecord({text:'Is Greater Than Or Equal To', value: 'dategte'}));
                store.add(new comboRecord({text:'Is Less Than Or Equal To',    value: 'datelte'}));
            } else {
                store.add(new comboRecord({text:'Is Greater Than',             value: 'gt'}));
                store.add(new comboRecord({text:'Is Less Than',                value: 'lt'}));
                store.add(new comboRecord({text:'Is Greater Than Or Equal To', value: 'gte'}));
                store.add(new comboRecord({text:'Is Less Than Or Equal To',    value: 'lte'}));
            }
        }

        if (mappedType == "TEXT" || mappedType == "LONGTEXT")
        {
            store.add(new comboRecord({text:'Starts With',         value: 'startswith'}));
            store.add(new comboRecord({text:'Does Not Start With', value: 'doesnotstartwith'}));
            store.add(new comboRecord({text:'Contains',            value: 'contains'}));
            store.add(new comboRecord({text:'Does Not Contain',    value: 'doesnotcontain'}));
        }

        //All mappedTypes will have these:
        store.add(new comboRecord({text:'Is Blank',     value: 'isblank'}));
        store.add(new comboRecord({text:'Is Not Blank', value: 'isnonblank'}));

        if (mvEnabled)
        {
            store.add(new comboRecord({text:'Has A Missing Value Indicator',           value: 'hasmvvalue'}));
            store.add(new comboRecord({text:'Does Not Have A Missing Value Indicator', value: 'nomvvalue'}));
        }

        return store;
    },

    savedSearchString : null,

    getSearchString : function()
    {
        if (null == this.savedSearchString)
            this.savedSearchString = document.location.search.substring(1) || "";
        return this.savedSearchString;
    },

    setSearchString : function(tableName, search)
    {
        this.savedSearchString = search || "";
        // If the search string doesn't change and there is a hash on the url, the page won't reload.
        // Remove the hash by setting the full path plus search string.
        window.location.assign(window.location.pathname + "?" + this.savedSearchString);
    },


    getParamValPairs : function(queryString, skipPrefixes)
    {
        if (!queryString)
        {
            queryString = this.getSearchString();
        }
        else
        {
            if (queryString.indexOf("?") > -1)
            {
                queryString = queryString.substring(queryString.indexOf("?") + 1);
            }
        }
        var iNew = 0;
        var newParamValPairs = new Array(0);
        if (queryString != null && queryString.length > 0)
        {
            var paramValPairs = queryString.split("&");
            PARAM_LOOP: for (var i = 0; i < paramValPairs.length; i++)
            {
                var paramPair = paramValPairs[i].split("=", 2);
                paramPair[0] = decodeURIComponent(paramPair[0]);

                if (paramPair[0] == ".lastFilter")
                    continue;

                if (skipPrefixes)
                {
                    for (var j = 0; j < skipPrefixes.length; j++)
                    {
                        var skipPrefix = skipPrefixes[j];
                        if (skipPrefix && paramPair[0].indexOf(skipPrefix) == 0)
                        {
                            // only skip filter params and sort.
                            if (paramPair[0] == skipPrefix)
                                continue PARAM_LOOP;
                            if (paramPair[0].indexOf("~") > 0)
                                continue PARAM_LOOP;
                            if (paramPair[0] == skipPrefix + "sort")
                                continue PARAM_LOOP;
                        }
                    }
                }
                if (paramPair.length > 1)
                    paramPair[1] = decodeURIComponent(paramPair[1]);
                newParamValPairs[iNew] = paramPair;
                iNew++;
            }
        }
        return newParamValPairs;
    },


    getParameter : function(paramName)
    {
        var paramValPairs = this.getParamValPairs(null, null);
        for (var i = 0; i < paramValPairs.length; i++)
            if (paramValPairs[i][0] == paramName)
                if (paramValPairs[i].length > 1)
                    return paramValPairs[i][1];
                else
                    return "";

        return null;
    },


    buildQueryString : function(pairs)
    {
        if (pairs == null || pairs.length == 0)
            return "";

        var queryString = [];
        for (var i = 0; i < pairs.length; i++)
        {
            var key = pairs[i][0];
            var value = pairs[i].length > 1 ? pairs[i][1] : undefined;

            queryString.push(encodeURIComponent(key));
            if (undefined != value)
            {
                if (Ext.isDate(value))
                {
                    value = value.toISOString();
                    if (-1 != key.indexOf("~date"))
                        value = value.substring(0,10);
                    if (LABKEY.Utils.endsWith(value,"Z"))
                        value = value.substring(0,value.length-1);
                }
                queryString.push("=");
                queryString.push(encodeURIComponent(value));
            }
            queryString.push("&");
        }

        if (queryString.length > 0)
            queryString.pop();

        return queryString.join("");
    },

    clearFilter : function()
    {
        var dr = LABKEY.DataRegions[this._tableName];
        if (!dr)
            return;
        dr.clearFilter(this._fieldName);
    },

    clearAllFilters : function()
    {
        var dr = LABKEY.DataRegions[this._tableName];
        if (!dr)
            return;
        dr.clearAllFilters();
    },

    changeFilter : function(newParamValPairs, newQueryString)
    {
        var dr = LABKEY.DataRegions[this._tableName];
        if (!dr)
            return;
        dr.changeFilter(newParamValPairs, newQueryString);
    },

    setFilter: function(input1, input2, comparison1, comparison2){
        //This is a replacement for doFilter. Will probably be renamed to doFilter.
        //input1 and input2 have already been validated, no need to do that here.
        //We do however need to modify the date if it's not in the proper format, and parse ints/floats.
        
        var queryString = LABKEY.DataRegions[this._tableName] ? LABKEY.DataRegions[this._tableName].requestURL : null;
        var newParamValPairs = this.getParamValPairs(queryString, [this._tableName + "." + this._fieldName + "~", this._tableName + ".offset"]);
        var iNew = newParamValPairs.length;
        var comparisons = new Array(0);

        if(comparison1 !=''){
            comparisons[comparisons.length] = this.getCompares(input1, comparison1);
        }
        if(comparison2 != ''){
            comparisons[comparisons.length] = this.getCompares(input2, comparison2);
        }
        for (var i = 0; i < comparisons.length; i++)
        {
            newParamValPairs[iNew] = comparisons[i];
            iNew ++;
        }

        var newQueryString = this.buildQueryString(newParamValPairs);
        var filterParamsString = this.buildQueryString(comparisons);

        this.changeFilterCallback.call(this, newParamValPairs, newQueryString, filterParamsString);
    },

    getCompares: function(input,comparison){
        //Used to be getValidComparesFromForm, but since we validate before setting a filter we got rid of the validation here.
        var pair;
        if (comparison == "isblank" || comparison == "isnonblank" || comparison == "nomvvalue" || comparison == "hasmvvalue")
        {
            pair = [this._tableName + "." + this._fieldName + "~" + comparison];
        } else{
            pair = [this._tableName + "." + this._fieldName + "~" + comparison, input];
        }
        return pair;
    },

    changeFilterCallback : null,

    clearSort : function(tableName, columnName)
    {
        if(!tableName || !columnName)
            return;

        var dr = LABKEY.DataRegions[tableName];
        if (!dr)
            return;
        dr.clearSort(columnName);
    }
};


function showFilterPanel(dataRegionName, colName, caption, dataType, mvEnabled, queryString, dialogTitle, confirmCallback)
{
    LABKEY.DataRegion._filterUI.showFilterPanel(dataRegionName, colName, caption, dataType, mvEnabled, queryString, dialogTitle, confirmCallback);
}


// If at least one checkbox on the form is selected then GET/POST url.  Otherwise, display an error.
function verifySelected(form, url, method, pluralNoun, pluralConfirmText, singularConfirmText)
{
    var checked = 0;
    var elems = form.elements;
    var l = elems.length;

    for (var i = 0; i < l; i++)
    {
        var e = elems[i];

        if (e.type == 'checkbox' && e.checked && e.name == '.select')
        {
            checked++;
        }
    }

    if (checked > 0)
    {
        if ((window.parent == window) && (null != pluralConfirmText))
        {
            var confirmText = (1 == checked && null != singularConfirmText ? singularConfirmText : pluralConfirmText);

            if (!window.confirm(confirmText.replace("${selectedCount}", checked)))
                return false;
        }

        form.action = url;
        form.method = method;
        return true;
    }
    else
    {
        window.alert('Please select one or more ' + pluralNoun + '.');
        return false;
    }
}


function doSort(tableName, columnName, sortDirection)
{
    if (!tableName || !columnName)
        return;

    var dr = LABKEY.DataRegions[tableName];
    if (!dr)
        return;
    dr.changeSort(columnName, sortDirection);
}

LABKEY.MessageArea = Ext.extend(Ext.util.Observable, {

    constructor : function(config)
    {
        this.parentEl = config.parent;
        this.parentEl.enableDisplayMode();

        this.parts = {};

        LABKEY.MessageArea.superclass.constructor.call(this, config);

        this.addEvents(
            /**
             * @event rendermsg
             * Fires after an individual message part is rendered.
             * @param {LABKEY.MessageArea} this
             * @param {String} the name of the message part
             * @param {Ext.Element} the rendered element
             */
            'rendermsg',
            'clearmsg'
        );
    },

    addMessage : function(msg, part) {

        part = part || 'info';
        this.parts[part] = msg;
        this.setVisible(true);
        this._refresh();
    },

    getMessage : function(part) {

        return this.parts[part];
    },

    removeMessage : function(part) {

        delete this.parts[part];
        this._refresh();
    },

    /**
     * Deletes all stored messages and clears the rendered area
     */
    removeAll : function() {

        this.parts = {};
        this._refresh();
    },

    render : function() {

        this.clear();
        for (var name in this.parts)
        {
            var msg = this.parts[name];
            if (msg)
            {
                var div = this.parentEl.child("div");
                if (div.first())
                    div.createChild({tag: 'hr'});
                var el = div.createChild({tag: 'div', cls: 'labkey-dataregion-msg', html: msg});

                this.fireEvent('rendermsg', this, name, el);
            }
        }
        this.setVisible(true);
    },

    setVisible : function(visible) {

        this.parentEl.setVisible(visible, false);
    },
    
    isVisible : function() {

        return this.parentEl.isVisible();
    },

    /**
     * Clears the rendered DOM elements.
     */
    clear : function() {

        var div = this.parentEl.child("div");
        if (div)
            div.dom.innerHTML = "";
    },

    /**
     * private
     */
    _refresh : function() {

        if (this.isVisible())
        {
            this.clear();
            this.render();
        }
    }
});
