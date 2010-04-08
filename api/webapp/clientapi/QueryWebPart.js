/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 10.1
 * @license Copyright (c) 2009-2010 LabKey Corporation
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
 * @description Constructs a LABKEY.QueryWebPart class instance
 * @class The LABKEY.QueryWebPart simplifies the task of dynamically adding a query web part to your page.  Please use
 * this class for adding query web parts to a page instead of {@link LABKEY.WebPart},
 * which can be used for other types of web parts.
 *              <p>Additional Documentation:
 *              <ul>
 *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=webPartConfig">
 *  				        Web Part Configuration Properties</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
 *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeySql">
 *                      LabKey SQL Reference</a></li>
 *              </ul>
 *           </p>
 * @constructor
 * @param {Object} config A configuration object with the following possible properties:
 * @param {String} config.schemaName The name of the schema the web part will query
 * @param {String} config.queryName The name of the query within the schema the web part will select and display
 * @param {String} [config.viewName] the name of a saved view you wish to display for the given schema and query name
 * @param {String} [config.renderTo] The id of the element inside of which the part should be rendered. This is typically a &lt;div&gt;.
 * If not supplied in the configuration, you must call the render() method to render the part into the page.
 * @param {String} [config.title] An optional title for the web part. If not supplied, the query name will be used as the title.
 * @param {String} [config.titleHref] If supplied, the title will be rendered as a hyperlink with this value as the href attribute.
 * @param {String} [config.buttonBarPosition] Specifies the button bar position for the web part.
 * This may be one of the following: 'none', 'top', 'bottom', 'both'.
 * @param {boolean} [config.allowChooseQuery] If the button bar is showing, whether or not it should be include a button
 * to let the user choose a different query.
 * @param {boolean} [config.allowChooseView] If the button bar is showing, whether or not it should be include a button
 * to let the user choose a different view.
 * @param {boolean} [config.showDetailsColumn] If the underlying table has a details URL, show a column that renders a [details] link (default true).  If true, the record selectors will be included regardless of the 'showRecordSelectors' config option.
 * @param {boolean} [config.showUpdateColumn] If the underlying table has an update URL, show a column that renders an [edit] link (default true).
 * @param {boolean} [config.showInsertNewButton] If the underlying table has an insert URL, show a "Insert New" button in the button bar (default true).
 * @param {boolean} [config.showDeleteButton] Show a "Delete" button in the button bar (default true).
 * @param {boolean} [config.showExportButtons] Show the export button menu in the button bar (default true).
 * @param {boolean} [config.showBorders] Render the table with borders (default true).
 * @param {boolean} [config.showSurroundingBorder] Render the table with a surrounding border (default true).
 * @param {boolean} [config.showRecordSelectors] Render the select checkbox column (default true).  If 'showDeleteButton' is true, the checkboxes will be  included regardless of the 'showRecordSelectors' config option.
 * @param {boolean} [config.showPagination] Show the pagination links and count (default true).
 * @param {boolean} [config.shadeAlternatingRows] Shade every other row with a light gray background color (default true).
 * @param {String} [config.sort] A base sort order to use. This may be a comma-separated list of column names, each of
 * which may have a - prefix to indicate a descending sort.
 * @param {Array} [config.filters] A base set of filters to apply. This should be an array of {@link LABKEY.Filter} objects
 * each of which is created using the {@link LABKEY.Filter.create} method.
 * For compatibility with the {@link LABKEY.Query} object, you may also specify base filters using config.filterArray.
 * @param {Array} [config.aggregates] A array of aggregate definitions. The objects in this array should have two
 * properties: 'column' and 'type'. The column property is the column name, and the type property may be one of the
 * the {@link LABKEY.AggregateTypes} values.
 * @param {String} [config.dataRegionName] The name to be used for the data region. This should be unique within
 * the set of query views on the page. If not supplied, a unique name is generated for you.
 * @param {String} [config.frame] The frame style to use for the web part. This may be one of the following:
 * 'div', 'portal', 'none', 'dialog', 'title', left-nav'.
 * @param {String} [config.bodyClass] A CSS style class that will be added to the enclosing element for the web part.
 * @param {Function} [config.successCallback] A function to call after the part has been rendered.
 * @param {Object} [config.scope] An object to use as the callback function's scope. Defaults to this.
 * @example
 * &lt;div id='queryTestDiv1'/&gt;
 * &lt;script type="text/javascript"&gt;
var qwp1 = new LABKEY.QueryWebPart({
	renderTo: 'queryTestDiv1',
	title: 'My Query Web Part',
	schemaName: 'lists',
	queryName: 'People',
	buttonBarPosition: 'none',
	aggregates: [
		{column: 'First', type: LABKEY.AggregateTypes.COUNT},
		{column: 'Age', type: LABKEY.AggregateTypes.AVG}
	],
	filters: [
		LABKEY.Filter.create('Last', 'Flintstone')
	],
    sort: '-Last'
});

 //note that you may also register for the 'render' event
 //instead of using the successCallback config property.
 //registering for events is done using Ext event registration.
 //Example:
 qwp1.on("render", onRender);
 function onRender()
 {
    //...do something after the part has rendered...
 }
 
*/
LABKEY.QueryWebPart = Ext.extend(Ext.util.Observable, {
    _paramTranslationMap : {
        frame: 'webpart.frame',
        bodyClass: 'webpart.bodyClass',
        title: 'webpart.title',
        titleHref: 'webpart.titleHref',
        aggregates: false,
        renderTo: false,
        sort: false,
        filterArray: false,
        filters: false,
        _paramTranslationMap: false,
        events: false,
        filterOptRe: false,
        userFilters: false,
        qsParamsToIgnore: false,
        buttonBar: false
    },

    constructor : function(config)
    {

        Ext.apply(this, config, {
            dataRegionName: Ext.id(undefined, "aqwp"),
            returnURL: window.location.href
        });

        LABKEY.QueryWebPart.superclass.constructor.apply(this, arguments);

        this.filters = this.filters || this.filterArray;
        this.qsParamsToIgnore = {};

        /**
         * @memberOf LABKEY.QueryWebPart#
         * @name render
         * @event
         * @description Fired after the web part html is rendered into the page.
         */
        this.addEvents("render");

        if(this.renderTo)
            this.render();
    },

    /**
     * Requests the query web part content and renders it within the element identified by the renderTo parameter.
     * Note that you do not need to call this method explicitly if you specify a renderTo property on the config object
     * handed to the class constructor. If you do not specify renderTo in the config, then you must call this method
     * passing the id of the element in which you want the part rendered
     * @name render
     * @function
     * @memberOf LABKEY.QueryWebPart#
     * @param renderTo The id of the element in which you want the part rendered.
     */
    render : function(renderTo) {

        var idx = 0; //array index counter

        //allow renderTo param to override config property
        if(renderTo)
            this.renderTo = renderTo;

        if(!this.renderTo)
            Ext.Msg.alert("Configuration Error", "You must supply a renderTo property either in the configuration object, or as a parameter to the render() method!");

        //setup the params
        var params = {};
        params["webpart.name"] = "Query";
        LABKEY.Utils.applyTranslated(params, this, this._paramTranslationMap, true, false);

        //handle base-filters and sorts (need data region prefix)
        if(this.sort)
            params[this.dataRegionName + ".sort"] = this.sort;

        if(this.filters)
        {
            for(idx = 0; idx < this.filters.length; ++idx)
                params[this.filters[idx].getURLParameterName(this.dataRegionName)] = this.filters[idx].getURLParameterValue();
        }

        //add user filters (already in encoded form
        if (this.userFilters)
        {
            for (var name in this.userFilters)
            {
                params[name] = this.userFilters[name];
            }
        }
        
        //handle aggregates separately
        if(this.aggregates)
        {
            for(idx = 0; idx < this.aggregates.length; ++idx)
            {
                if(this.aggregates[idx].type &&  this.aggregates[idx].column)
                    params[this.dataRegionName + '.agg.' + this.aggregates[idx].column] = this.aggregates[idx].type;
            }
        }

        //forward query string parameters for this data region
        //except those we should specifically ignore
        var qsParams = LABKEY.ActionURL.getParameters();
        for(var qsParam in qsParams)
        {
            if(-1 != qsParam.indexOf(this.dataRegionName + ".")
                    && !this.qsParamsToIgnore[qsParam])
                params[qsParam] = qsParams[qsParam];
        }

        //Ext uses a param called _dc to defeat caching, and it may be
        //on the URL if the Query web part has done a sort or filter
        //strip it if it's there so it's not included twice (Ext always appends one)
        delete params["_dc"];

        //add the button bar config if any
        var json = {};
        if (this.buttonBar && this.buttonBar.items && this.buttonBar.items.length > 0)
            json.buttonBar = this.processButtonBar();

        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("project", "getWebPart", this.containerPath),
            success: function(response) {
                var targetElem = Ext.get(this.renderTo);
                if(targetElem)
                {
                    targetElem.update(response.responseText, true); //execute scripts

                    //get the data region and subscribe to events
                    Ext.onReady(function(){
                        var dr = LABKEY.DataRegions[this.dataRegionName];
                        if (!dr)
                            throw "Couldn't get dataregion object!";
                        dr.on("beforeoffsetchange", this.beforeOffsetChange, this);
                        dr.on("beforemaxrowschange", this.beforeMaxRowsChange, this);
                        dr.on("beforesortchange", this.beforeSortChange, this);
                        dr.on("beforeclearsort", this.beforeClearSort, this);
                        dr.on("beforefilterchange", this.beforeFilterChange, this);
                        dr.on("beforeclearfilter", this.beforeClearFilter, this);
                        dr.on("beforeclearallfilters", this.beforeClearAllFilters, this);
                        dr.on("beforechangeview", this.beforeChangeView, this);
                        dr.on("beforeshowrowschange", this.beforeShowRowsChange, this);
                        dr.on("buttonclick", this.onButtonClick, this);
                    }, this, {delay: 100});

                    if(this.successCallback)
                        Ext.onReady(function(){this.successCallback.call(this.scope || this);}, this, {delay: 100}); //8721: need to use onReady()
                    this.fireEvent("render");
                }
                else
                    Ext.Msg.alert("Rendering Error", "The element '" + this.renderTo + "' does not exist in the document!");
            },
            failure: LABKEY.Utils.getCallbackWrapper(this.errorCallback, this.scope, true),
            method: 'POST',
            params: params,
            jsonData: json,
            scope: this
        });
    },

    processButtonBar : function() {
        this.processButtonBarItems(this.buttonBar.items);
        return this.buttonBar;
    },

    processButtonBarItems : function(items) {
        if (!items || !items.length || items.length <= 0)
            return;
        
        var item;
        for (var idx = 0; idx < items.length; ++idx)
        {
            item = items[idx];
            if (item.handler && Ext.isFunction(item.handler))
            {
                item.id = item.id || Ext.id(undefined, "");
                item.onClick = "return LABKEY.DataRegions['" + this.dataRegionName + "'].onButtonClick('"
                        + item.id + "');";
            }
            if (item.items)
                this.processButtonBarItems(item.items);
        }
    },

    onButtonClick : function(buttonId, dataRegion) {
        var item = this.findButtonById(this.buttonBar.items, buttonId);
        if (item && item.handler && Ext.isFunction(item.handler))
        {
            try
            {
                return item.handler.call(item.scope || this, dataRegion);
            }
            catch(ignore) {}
        }
        return false;
    },

    findButtonById : function(items, id) {
        if (!items || !items.length || items.length <= 0)
            return null;

        var ret;
        for (var idx = 0; idx < items.length; ++idx)
        {
            if (items[idx].id == id)
                return items[idx];
            ret = this.findButtonById(items[idx].items, id);
            if (null != ret)
                return ret;
        }
        return null;
    },

    beforeOffsetChange : function(dataRegion, newoffset) {
        this.offset = newoffset;
        this.render();
        return false;
    },

    beforeMaxRowsChange : function(dataRegion, newmax) {
        this.maxRows = newmax;
        this.offset = 0;
        delete this.showRows;
        this.render();
        return false;
    },

    beforeSortChange : function(dataRegion, columnName, sortDirection) {
        this.sort = dataRegion.alterSortString(this.sort, columnName, sortDirection);
        this.render();
        return false;
    },

    beforeClearSort : function(dataRegion, columnName) {
        this.sort = dataRegion.alterSortString(this.sort, columnName, null);
        this.render();
        return false;
    },

    beforeFilterChange : function(dataRegion, newFilterPairs) {
        this.offset = 0;
        this.userFilters = this.userFilters || {};
        for (var idx = 0; idx < newFilterPairs.length; ++idx)
        {
            this.userFilters[newFilterPairs[idx][0]] = newFilterPairs[idx][1];
        }
        this.render();
        return false;
    },

    beforeClearFilter : function(dataRegion, columnName) {
        this.offset = 0;
        var namePrefix = this.dataRegionName + "." + columnName + "~";
        if (this.userFilters)
        {
            for (var name in this.userFilters)
            {
                if (name.indexOf(namePrefix) >= 0)
                    delete this.userFilters[name];
            }
        }
        this.render();
        return false;
    },

    beforeClearAllFilters : function(dataRegion) {
        this.offset = 0;
        this.userFilters = null;
        this.render();
        return false;
    },

    beforeChangeView : function(dataRegion, viewName) {
        delete this.offset;
        delete this.userFilters;
        delete this.sort;
        this.viewName = viewName;
        this.qsParamsToIgnore[this.getQualifiedParamName("viewName")] = true;
        this.render();
        return false;
    },

    getQualifiedParamName : function(paramName) {
        return this.dataRegionName + "." + paramName;
    },

    beforeShowRowsChange : function(dataRegion, showRowsSetting) {
        this.showRows = showRowsSetting;
        delete this.offset;
        delete this.maxRows;
        this.render();
        return false;
    }
});

LABKEY.QueryWebPart.builtInButtons = {
    query: 'query',
    views: 'views',
    insertNew: 'insert new',
    deleteRows: 'delete',
    exportRows: 'export',
    print: 'print',
    pageSize: 'page size'
};


/**
 * @namespace A predefined set of aggregate types, for use in the config.aggregates array in the
 * {@link LABKEY.QueryWebPart} constructor.
 */
LABKEY.AggregateTypes = {
    /**
     * Displays the sum of the values in the specified column
     */
        SUM: 'sum',
    /**
     * Displays the average of the values in the specified column
     */
        AVG: 'avg',
    /**
     * Displays the count of the values in the specified column
     */
        COUNT: 'count',
    /**
     * Displays the maximum value from the specified column
     */
        MIN: 'min',
    /**
     * Displays the minimum values from the specified column
     */
        MAX: 'max'
};

