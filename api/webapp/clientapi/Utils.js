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
 * @namespace Utils static class to provide miscellaneous utility functions.
 */
LABKEY.Utils = new function()
{
    // private member variables
    var _extParamMapping = {
        "start" : "query.offset",
        "limit" : "query.maxRows",
        "sort" : "query.sort",
        "dir" : "query.sortdir"
        };

    // Private array of chars to use for UUID generation
    var CHARS = '0123456789abcdefghijklmnopqrstuvwxyz'.split('');

    //When using Ext dateFields you can use DATEALTFORMATS for the altFormat: config option.
    var DATEALTFORMATS= 'n/j/y g:i:s a|n/j/Y g:i:s a|n/j/y G:i:s|n/j/Y G:i:s|' +
                        'n-j-y g:i:s a|n-j-Y g:i:s a|n-j-y G:i:s|n-j-Y G:i:s|' +
                        'n/j/y g:i a|n/j/Y g:i a|n/j/y G:i|n/j/Y G:i|' +
                        'n-j-y g:i a|n-j-Y g:i a|n-j-y G:i|n-j-Y G:i|' +
                        'j-M-y g:i a|j-M-Y g:i a|j-M-y G:i|j-M-Y G:i|' +
                        'n/j/y|n/j/Y|' +
                        'n-j-y|n-j-Y|' +
                        'j-M-y|j-M-Y|' +
                        'Y-n-d H:i:s|Y-n-d|' +
                        'j M Y G:i:s O|' + // 10 Sep 2009 11:24:12 -0700
                        'j M Y H:i:s|c';
    // private functions
    function handleLoadError(This, o, arg, e)
    {
        LABKEY.Utils.displayAjaxErrorResponse(arg, e);
    }

    function mapQueryParameters(store, options)
    {
        // map all parameters from ext names to labkey names:
        for (var param in options)
        {
            if (_extParamMapping[param])
                options[_extParamMapping[param]] = options[param];
        }

        // fix up any necessary parameter values:
        if ("DESC" == options['query.sortdir'])
        {
            var sortCol = options['query.sort'];
            options['query.sort'] = "-" + sortCol;
        }
    }
    
    function createHttpProxyImpl(containerPath, errorListener)
    {
        var proxy = new Ext.data.HttpProxy(new Ext.data.Connection({
                //where to retrieve data
                url: LABKEY.ActionURL.buildURL("query", "selectRows", containerPath), //url to data object (server side script)
                method: 'GET'
            }));

        if (errorListener)
            proxy.on("loadexception", errorListener);

        proxy.on("beforeload", mapQueryParameters);

        return proxy;
    }



    function isObject(v)
    {
        return typeof v == "object" && Object.prototype.toString.call(v) === '[object Object]';
    }


    function _copy(o, depth)
    {
        if (depth==0 || !isObject(o))
            return o;
        var copy = {};
        for (var key in o)
            copy[key] = _copy(o[key], depth-1);
        return copy;
    }


    // like a general version of Ext.apply() or mootools.merge()
    function _merge(to, from, overwrite, depth)
    {
        for (var key in from)
        {
            if (isObject(to[key]) && isObject(from[key]))
            {
               _merge(to[key], from[key], overwrite, depth-1);
            }
            else if (undefined === to[key] || overwrite)
            {
                to[key] = _copy(from[key], depth-1);
            }
        }
    }


    /** @scope LABKEY.Utils */
    return {
        // public functions

        /**
        * Encodes the html passed in so that it will not be interpreted as HTML by the browser.
        * For example, if your input string was "&lt;p&gt;Hello&lt;/p&gt;" the output would be
        * "&amp;lt;p&amp;gt;Hello&amp;lt;/p&amp;gt;". If you set an element's innerHTML property
        * to this string, the HTML markup will be displayed as literal text rather than being
        * interpreted as HTML.
        *
        * @param {String} html The HTML to encode
		* @return {String} The encoded HTML
		*/
        encodeHtml : function(html)
        {
            var div = document.createElement('div');
            var text = document.createTextNode(html);
            div.appendChild(text);
            return div.innerHTML;
        },

        /**
         * Returns date formats for use in an Ext.form.DateField. Useful when using a DateField in an Ext object,
         * it contains a very large set of date formats, which helps make a DateField more robust. For example, a
         * user would be allowed to enter dates like 6/1/2011, 06/01/2011, 6/1/11, etc.
         */
        getDateAltFormats : function(){
            return DATEALTFORMATS;
        },

        /**
        * Shows an error dialog box to the user in response to an error from an AJAX request, including
        * any error messages from the server.
        * @param {XMLHttpRequest} responseObj The XMLHttpRequest object containing the response data.
        * @param {Error} [exceptionObj] A JavaScript Error object caught by the calling code.
        * @param {boolean} [showExceptionClass] Flag to display the java class of the exception.
        * @param {String} [msgPrefix] Prefix to the error message (defaults to: 'An error occurred trying to load:')
        * The error dialog will display the Error's name and message, if available. Ext.data.DataReader implementations
        * may throw this type of error object.
        */
        displayAjaxErrorResponse: function(responseObj, exceptionObj, showExceptionClass, msgPrefix)
        {
            if (responseObj.status == 0)
            {
                // Don't show an error dialog if the user cancelled the request in the browser, like navigating
                // to another page
                return;

            }
            var error;
            var prefix = msgPrefix || 'An error occurred trying to load:\n';

            if (responseObj &&
                responseObj.responseText &&
                responseObj.getResponseHeader('Content-Type') &&
                responseObj.getResponseHeader('Content-Type').indexOf('application/json') >= 0)
            {
                var jsonResponse = Ext.util.JSON.decode(responseObj.responseText);
                if (jsonResponse && jsonResponse.exception)
                {
                    error = prefix + jsonResponse.exception;
                    if (showExceptionClass)
                        error += "\n(" + (jsonResponse.exceptionClass ? jsonResponse.exceptionClass : "Exception class unknown") + ")";
                }
            }
            if (!error)
                error = prefix + "Status: " + responseObj.statusText + " (" + responseObj.status + ")";
            if (exceptionObj && exceptionObj.message)
                error += "\n" + exceptionObj.name + ": " + exceptionObj.message;
            Ext.Msg.alert("Error", error);
        },
        
        /**
        * Creates an Ext.data.Store that queries the LabKey Server database and can be used as the data source
        * for various components, including GridViews, ComboBoxes, and so forth.
        *
        * @param {Object} config Describes the GridView's properties.
        * @param {String} config.schemaName Name of a schema defined within the current
        *                 container.  Example: 'study'.  See also: <a class="link"
                          href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
                          How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.queryName Name of a query defined within the specified schema
        *                 in the current container.  Example: 'SpecimenDetail'. See also: <a class="link"
                          href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
                          How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} [config.containerPath] The container path in which the schemaName and queryName are defined.
        * @param {String} [config.viewName] Name of a custom view defined over the specified query.
        *                 in the current container. Example: 'SpecimenDetail'.  See also: <a class="link"
                          href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
                          How To Find schemaName, queryName &amp; viewName</a>.
        * @param {Object} [config.allowNull] If specified, this configuration will be used to insert a blank
        *                 entry as the first entry in the store.
        * @param {String} [config.allowNull.keyColumn] If specified, the name of the column in the underlying database
        *                 that holds the key.
        * @param {String} [config.allowNull.displayColumn] If specified, the name of the column in the underlying database
        *                 that holds the value to be shown by default in the display component.
        * @param {String} [config.allowNull.emptyName] If specified, what to show in the list for the blank entry.
        *                 Defaults to '[None]'.
        * @param {String} [config.allowNull.emptyValue] If specified, the value to be used for the blank entry.
        *                 Defaults to the empty string.
        *
		* @return {Ext.data.Store} The initialized Store object
		*/
        createExtStore: function (storeConfig)
        {
            if (!storeConfig)
                storeConfig = {};
            if (!storeConfig.baseParams)
                storeConfig.baseParams = {};
            storeConfig.baseParams['query.queryName'] = storeConfig.queryName;
            storeConfig.baseParams['schemaName'] = storeConfig.schemaName;
            if (storeConfig.viewName)
                storeConfig.baseParams['query.viewName'] = storeConfig.viewName;

            if (!storeConfig.proxy)
                storeConfig.proxy = createHttpProxyImpl(storeConfig.containerPath);

            if (!storeConfig.remoteSort)
                storeConfig.remoteSort = true;

            if (!storeConfig.listeners || !storeConfig.listeners.loadexception)
                storeConfig.listeners = { loadexception : { fn : handleLoadError } };

            storeConfig.reader = new Ext.data.JsonReader();

            var result = new Ext.data.Store(storeConfig);

            if (storeConfig.allowNull)
            {
                var emptyValue = storeConfig.allowNull.emptyValue;
                if (!emptyValue)
                {
                    emptyValue = "";
                }
                var emptyName = storeConfig.allowNull.emptyName;
                if (!emptyName)
                {
                    emptyName = "[None]";
                }
                result.on("load", function(store)
                    {
                    var emptyRecordConstructor = Ext.data.Record.create([storeConfig.allowNull.keyColumn, storeConfig.allowNull.displayColumn]);
                    var recordData = {};
                    recordData[storeConfig.allowNull.keyColumn] = emptyValue;
                    recordData[storeConfig.allowNull.displayColumn] = emptyName;
                    var emptyRecord = new emptyRecordConstructor(recordData);
                    store.insert(0, emptyRecord);
                    });
            }

            return result;
        },

        /**
         * This method takes an object that is/extends an Ext.Container (e.g. Panels, Toolbars, Viewports, Menus) and
         * resizes it so the Container fits inside the viewable region of the window. This is generally used in the case
         * where the Container is not rendered to a webpart but rather displayed on the page itself (e.g. SchemaBrowser,
         * manageFolders, etc).
         * @param extContainer - (Required) outer container which is the target to be resized
         * @param width - (Required) width of the viewport. In many cases, the window width.
         * @param height - (Required) height of the viewport. In many cases, the window height.
         * @param paddingX - distance from the right edge of the viewport. Defaults to 30.
         * @param paddingY - distance from the bottom edge of the viewport. Defaults to 30.
         */
        resizeToViewport : function(extContainer, width, height, paddingX, paddingY)
        {
            if (!extContainer || !extContainer.rendered)
                return;

            var padding = [];
            if (paddingX !== undefined && paddingX != null)
                padding.push(paddingX);
            else
                padding.push(35);
            if (paddingY !== undefined && paddingY != null)
                padding.push(paddingY);
            else
                padding.push(30);

            var xy = extContainer.el.getXY();
            var size = {
                width  : Math.max(100,width-xy[0]-padding[0]),
                height : Math.max(100,height-xy[1]-padding[1])
            };
            extContainer.setSize(size);
            extContainer.doLayout();
        },

        /**
         * Returns a URL to the appropriate file icon image based on the specified file name.
         * Note that file name can be a full path or just the file name and extension.
         * If the file name does not include an extension, the URL for a generic image will be returned
         * @param {String} fileName The file name.
         * @return {String} The URL suitable for use in the src attribute of an img element.
         */
        getFileIconUrl : function(fileName) {
            var idx = fileName.lastIndexOf(".");
            var extension = (idx >= 0) ? fileName.substring(idx + 1) : "_generic";
            return LABKEY.ActionURL.buildURL("core", "getAttachmentIcon", "", {extension: extension});
        },

        /**
         * Sends a JSON object to the server which turns it into an Excel file and returns it to the browser to be saved or opened.
         * @param {Object} spreadsheet the JavaScript representation of the data
         * @param {String} spreadsheet.fileName name to suggest to the browser for saving the file. If the fileName is
         * specified and ends with ".xlsx", it will be returned in Excel 2007 format.
         * @param {String} spreadsheet.sheets array of sheets, which are objects with properties:
         * <ul>
         * <li><b>name:</b> name of the Excel sheet</li>
         * <li><b>data:</b> two dimensional array of values</li>
         * </ul>
         * The value array may be either primitives (booleans, numbers, Strings, and dates), or may be a map with
         * the following structure:
         * <ul>
         * <li><b>value:</b> the boolean, number, String, or date value of the cell</li>
         * <li><b>formatString:</b> for dates and numbers, the Java format string used with SimpleDateFormat
         * or DecimalFormat to control how the value is formatted</li>
         * <li><b>timeOnly:</b> for dates, whether the date part should be ignored and only the time value is important</li>
         * </ul>
         * @example &lt;script type="text/javascript"&gt;
LABKEY.Utils.convertToExcel(
{
	fileName: 'output.xls',
	sheets:
	[
		{
			name: 'FirstSheet',
			data:
			[
				['Row1Col1', 'Row1Col2'],
				['Row2Col1', 'Row2Col2']
			]
		},
		{
			name: 'SecondSheet',
			data:
			[
				['Col1Header', 'Col2Header'],
				[{value: 1000.5, formatString: '0,000.00'}, {value: '5 Mar 2009 05:14:17', formatString: 'yyyy MMM dd'}],
				[{value: 2000.6, formatString: '0,000.00'}, {value: '6 Mar 2009 07:17:10', formatString: 'yyyy MMM dd'}]

			]
		}
	]
});
&lt;/script&gt;
         */
        convertToExcel : function(spreadsheet) {
            // Insert a hidden <form> into to page, put the JSON into it, and submit it - the server's response
            // will make the browser pop up a dialog
            var newForm = Ext.DomHelper.append(document.getElementsByTagName('body')[0],
                '<form method="POST" action="' + LABKEY.ActionURL.buildURL("experiment", "convertArraysToExcel") + '">' +
                '<input type="hidden" name="json" value="' + Ext.util.Format.htmlEncode(Ext.util.JSON.encode(spreadsheet)) + '" />' +
                '</form>');
            newForm.submit();
        },

        /**
         * This is used internally by other class methods to automatically parse returned JSON
         * and call another success function passing that parsed JSON.
         * @param fn The callback function to wrap
         * @param scope The scope for the callback function
         * @param isErrorCallback Set to true if the function is an error callback
         */
        getCallbackWrapper : function(fn, scope, isErrorCallback) {
            return function(response, options)
            {
                var json = response.responseJSON;
                if (!json)
                {
                    //ensure response is JSON before trying to decode
                    if(response && response.getResponseHeader && response.getResponseHeader('Content-Type')
                            && response.getResponseHeader('Content-Type').indexOf('application/json') >= 0)
                        json = Ext.util.JSON.decode(response.responseText);

                    response.responseJSON = json;
                }

                if(!json && isErrorCallback)
                {
                    json = new Object();
                }

                if (!json.exception && isErrorCallback)
                {
                    // Try to make sure we don't show an empty error message
                    json.exception = (response && response.statusText ? response.statusText : "Communication failure.");
                }

                if(fn)
                    fn.call(scope || this, json, response, options);
                else if(isErrorCallback && response.status != 0)
                {
                    // Don't show an error dialog if the user cancelled the request in the browser, like navigating
                    // to another page
                    Ext.Msg.alert("Error", json.exception);
                }
            };
        },

        /**
         * Applies properties from the source object to the target object, translating
         * the property names based on the translation map. The translation map should
         * have an entry per property that you wish to rename when it is applied on
         * the target object. The key should be the name of the property on the source object
         * and the value should be the desired name on the target object. The value may
         * also be set to null or false to prohibit that property from being applied.
         * By default, this function will also apply all other properties on the source
         * object that are not listed in the translation map, but you can override this
         * by supplying false for the applyOthers paramer.
         * @param target The target object
         * @param source The source object
         * @param translationMap A map listing property name translations
         * @param applyOthers Set to false to prohibit application of properties
         * not explicitly mentioned in the translation map.
         * @param applyFunctions Set to false to prohibit application of properties
         * that are functions
         */
        applyTranslated : function(target, source, translationMap, applyOthers, applyFunctions)
        {
            if(undefined === target)
                target = {};
            if(undefined === applyOthers)
                applyOthers = true;
            if (undefined == applyFunctions && applyOthers)
                applyFunctions = true;
            var targetPropName;
            for(var prop in source)
            {
                //special case: Ext adds a "constructor" property to every object, which we don't want to apply
                if (prop == "constructor" || Ext.isFunction(prop))
                    continue;
                
                targetPropName = translationMap[prop];
                if(targetPropName)
                    target[translationMap[prop]] = source[prop];
                else if(undefined === targetPropName && applyOthers && (applyFunctions || !Ext.isFunction(source[prop])))
                    target[prop] = source[prop];
            }
        },

        /**
         * Ensure BoxComponent is visible on the page.  
         * @param boxComponent
         */
        ensureBoxVisible : function (boxComponent)
        {
            var box = boxComponent.getBox(true);
            var viewportWidth = Ext.lib.Dom.getViewWidth();
            var scrollLeft = Ext.dd.DragDropMgr.getScrollLeft();

            var scrollBarWidth = 20;
            if (viewportWidth - scrollBarWidth + scrollLeft < box.width + box.x) {
                boxComponent.setPosition(viewportWidth + scrollLeft - box.width - scrollBarWidth);
            }
        },

        /**
         * Sets a client-side cookie.  Useful for saving non-essential state to provide a better
         * user experience.  Note that some browser settings may prevent cookies from being saved,
         * and users can clear browser cookies at any time, so cookies are not a substitute for
         * database persistence.
         * @param {String} name The name of the cookie to be saved.
         * @param {String} value The value of the cookie to be saved.
         * @param {Boolean} pageonly Whether this cookie should be scoped to the entire site, or just this page.
         * Page scoping considers the entire URL without parameters; all URL contents after the '?' are ignored.
         * @param {int} days The number of days the cookie should be saved on the client.
         */
        setCookie : function(name, value, pageonly, days) {
            var expires;
            if (days)
            {
                var date = new Date();
                date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
                expires = "; expires=" + date.toGMTString();
            }
            else
                expires = "";
            var path = "/";
            if (pageonly)
                path = location.pathname.substring(0, location.pathname.lastIndexOf('/'));
            var cookieString = name + "=" + value + expires + "; path=" + path;
            document.cookie = cookieString;
        },

        /**
         * Retrieves a client-side cookie.  Useful for retrieving non-essential state to provide a better
         * user experience.  Note that some browser settings may prevent cookies from being saved,
         * and users can clear browser cookies at any time, so previously saved cookies should not be assumed
         * to be available.
         * @param {String} name The name of the cookie to be retrieved.
         * @param {String} defaultvalue The value to be returned if no cookie with the specified name is found on the client.
         */
        getCookie : function(name, defaultvalue) {
            var nameEQ = name + "=";
            var ca = document.cookie.split(';');
            for(var i=0; i < ca.length; i++)
            {
                var c = ca[i];
                while (c.charAt(0) == ' ')
                    c = c.substring(1, c.length);
                if (c.indexOf(nameEQ) == 0)
                    return c.substring(nameEQ.length, c.length);
            }
            return defaultvalue;
        },

        /**
         * Deletes a client-side cookie.  Note that 'name' and 'pageonly' should be exactly the same as when the cookie
         * was set.
         * @param {String} name The name of the cookie to be deleted.
         * @param {Boolean} pageonly Whether the cookie is scoped to the entire site, or just this page.
         * Deleting a site-level cookie has no impact on page-level cookies, and deleting page-level cookies
         * has no impact on site-level cookies, even if the cookies have the same name.
         */
        deleteCookie : function (name, pageonly)
        {
            LABKEY.Utils.setCookie(name, "", pageonly, -1);
        },

        /**
         * Includes a script file into the page. If the file was already included by some other code, this
         * function will simply ignore the call. This may be used to include files defined in your module's web/ directory
         * or any existing script file in the code web application (e.g., FileUploadField.js) 
         * @param {String} filePath The path to the script file to include. This path should be relative to the web application
         * root. So for example, if you wanted to include a file in your module's web/mymodule/scripts/ directory,
         * the path would be "mymodule/scripts/myscript.js"
         * @param {Boolean} [immediate] Set to false to indicate that the script is not needed until the page is fully
         * loaded (defaults to true). If true, a script element referencing this path will be added immediately
         * following the script block from which this function is called.
         */
        requiresScript : function(filePath, immediate)
        {
            LABKEY.requiresScript(filePath, immediate);
        },

        /**
         * Includes a Cascading Style Sheet (CSS) file into the page. If the file was already included by some other code, this
         * function will simply ignore the call. This may be used to include CSS files defined in your module's web/ directory.
         * @param {String} filePath The path to the script file to include. This path should be relative to the web application
         * root. So for example, if you wanted to include a file in your module's web/mymodule/styles/ directory,
         * the path would be "mymodule/styles/mystyles.css"
         */
        requiresCSS : function(filePath)
        {
            LABKEY.requiresCss(filePath);
        },

        /**
         * Returns true if value ends with ending
         * @param value the value to examine
         * @param ending the ending to look for
         */
        endsWith : function(value, ending)
        {
            if (!value || !ending)
                return false;
            if (value.length < ending.length)
                return false;
            return value.substring(value.length - ending.length) == ending;
        },

        /**
         * Iteratively calls a tester function you provide, calling another callback function once the
         * tester function returns true. This function is useful for advanced JavaScript scenarios, such
         * as cases where you are including common script files dynamically using the requiresScript()
         * method, and need to wait until classes defined in those files are parsed and ready for use.
         *  
         * @param {Object} config a configuration object with the following properties:
         * @param {Function} config.testCallback A function that returns true or false. This will be called every
         * ten milliseconds until it returns true or the maximum number of tests have been made.
         * @param {Array} [config.testArguments] A array of arguments to pass to the testCallback function
         * @param {Function} config.success The function to call when the testCallback returns true.
         * @param {Array} [config.successArguments] A array of arguments to pass to the successCallback function
         * @param {Object} [config.failure] A function to call when the testCallback throws an exception, or when
         * the maximum number of tests have been made.
         * @param {Array} [config.errorArguments] A array of arguments to pass to the errorCallback function
         * @param {Object} [config.scope] A scope to use when calling any of the callback methods (defaults to this)
         * @param {int} [config.maxTests] Maximum number of tests before the errorCallback is called (defaults to 1000).
         *
         * @example
&lt;script&gt;
    LABKEY.Utils.requiresScript("FileUploadField.js");
    LABKEY.Utils.requiresCSS("FileUploadField.css");
&lt;/script&gt;

&lt;script&gt;
    function tester()
    {
        return undefined != Ext.form.FileUploadField;
    }

    function onTrue(msg)
    {
        //this alert is merely to demonstrate the successArguments config property
        alert(msg);

        //use the file upload field...
    }

    function onFailure(msg)
    {
        alert("ERROR: " + msg);
    }

    LABKEY.Utils.onTrue({
        testCallback: tester,
        success: onTrue,
        successArguments: ['FileUploadField is ready to use!'],
        failure: onFailure,
        maxTests: 100
    });
&lt;/script&gt;
         */
        onTrue : function(config) {
            config.maxTests = config.maxTests || 1000;
            try
            {
                if(config.testCallback.apply(config.scope || this, config.testArguments || []))
                    LABKEY.Utils.getOnSuccess(config).apply(config.scope || this, config.successArguments || []);
                else
                {
                    if (config.maxTests <= 0) {
                        throw "Maximum number of tests reached!";
                    } else {
                        --config.maxTests;
                        LABKEY.Utils.onTrue.defer(10, this, [config]);
                    }
                }
            }
            catch(e)
            {
                if (LABKEY.Utils.getOnFailure(config)) {
                    LABKEY.Utils.getOnFailure(config).apply(config.scope || this, [e,config.errorArguments]);
                }
            }
        },

        /**
          * Returns a universally unique identifier, of the general form: "92329D39-6F5C-4520-ABFC-AAB64544E172"
          * Based on original Math.uuid.js (v1.4)
          * http://www.broofa.com
          * mailto:robert@broofa.com
          * Copyright (c) 2010 Robert Kieffer
          * Dual licensed under the MIT and GPL licenses.
        */
        generateUUID : function() {
            // First see if there are any server-generated UUIDs available to return
            if (LABKEY && LABKEY.uuids && LABKEY.uuids.length > 0)
            {
                return LABKEY.uuids.pop();
            }
            // From the original Math.uuidFast implementation
            var chars = CHARS, uuid = new Array(36), rnd = 0, r;
            for (var i = 0; i < 36; i++)
            {
                if (i == 8 || i == 13 || i == 18 || i == 23)
                {
                    uuid[i] = '-';
                }
                else if (i == 14)
                {
                    uuid[i] = '4';
                }
                else
                {
                    if (rnd <= 0x02) rnd = 0x2000000 + (Math.random() * 0x1000000) | 0;
                    r = rnd & 0xf;
                    rnd = rnd >> 4;
                    uuid[i] = chars[(i == 19) ? (r & 0x3) | 0x8 : r];
                }
            }
            return uuid.join('');
        },

        /**
         * Returns a string containing a well-formed html anchor that will apply theme specific styling. The configuration
         * takes any property value pair and places them on the anchor.
         * @param {Object} config a configuration object that models html anchor properties:
         * @param {String} config.href (required if config.onClick not specified) the reference the anchor will use.
         * @param {String} config.onClick {requried if config.href not specified) script called when the onClick event is fired by
         * the anchor.
         * @param {String} config.text text that is rendered inside the anchor element.
         */
        textLink : function(config)
        {
            if (config.href === undefined && !config.onClick === undefined)
            {
                throw "href AND/OR onClick required in call to LABKEY.Utils.textLink()";
            }
            var attrs = " ";
            if (config)
            {
                for (var i in config)
                {
                    if (i.toString() != "text" && i.toString() != "class")
                    {
                        attrs += i.toString() + '=\"' + config[i] + '\" ';
                    }
                }

                return '<a class="labkey-text-link"' + attrs + '>' + (config.text != null ? config.text : "") + '<span class="css-arrow-right"></span></a>';
            }
            throw "Config object not found for textLink.";
        },

        /**
         *
         * Standard documented name for error callback arguments is "failure" but various other names have been employed in past.
         * This function provides reverse compatibility by picking the failure callback argument out of a config object
         * be it named failure, failureCallback or errorCallback.
         *
         * @param config
         */
        getOnFailure : function(config)
        {
            return config.failure || config.errorCallback || config.failureCallback;
            // maybe it be desirable for this fall all the way back to returning LABKEY.Utils.displayAjaxErrorResponse?
        },

        /**
         *
         * Standard documented name for success callback arguments is "success" but various names have been employed in past.
         * This function provides reverse compatibility by picking the success callback argument out of a config object,
         * be it named success or successCallback.
         *
         * @param config
         */
        getOnSuccess : function(config)
        {
            return config.success || config.successCallback
        },


        /**
         * Apply properties from b, c, ...  to a.  Properties of each subsequent
         * object overwrites the previous.
         *
         * The first object is modified.
         *
         * Use merge({}, o) to create a deep copy of o.
         */
        merge : function(a, b, c)
        {
            var o = a;
            for (var i=1 ; i<arguments.length ; i++)
                _merge(o, arguments[i], true, 50);
            return o;
        },


        /**
         * Apply properites from b, c, ... to a.  Properties are not overwritten.
         *
         * The first object is modified.
         */
        mergeIf : function(a, b, c)
        {
            var o = arguments[0];
            for (var i=1 ; i<arguments.length ; i++)
                _merge(o, arguments[i], false, 50);
            return o;
        },


        // private
        enableButton : function (elem)
        {
            return Ext.get(elem).replaceClass("labkey-disabled-button", "labkey-button");
        },

        // private
        disableButton : function (elem)
        {
            return Ext.get(elem).replaceClass("labkey-button", "labkey-disabled-button");
        },

        /**
         * Provides a generic error callback.  This helper will call Ext.Msg.hide(), log the error to the console
         * and will log the error to the audit log table. The user must have insert permissions on the selected container for
         * this to work.  By default, it will insert the error into the Shared project.  A containerPath param can be passed to
         * use a different container.  The intent of this helper is to provide site admins with a mechanism to identify errors associated
         * with client-side code.  If noAuditLog=true is used, the helper will not log the error.
         *
         * @param {Object} error The error object passed to the callback function
         * @param {String} [error.containerPath] Container where errors will be logged. Defaults to /shared
         * @param {Boolean} [error.noAuditLog] If false, the errors will not be logged in the audit table.  Defaults to true
         *
         * @example &lt;script type="text/javascript"&gt;
        //basic usage
        LABKEY.Query.selectRows({
            schemaName: 'core',
            queryName: 'users',
            success: function(){}
            failure: LABKEY.Utils.onError
        });

        //custom container and turning off logging
        LABKEY.Query.selectRows({
            schemaName: 'core',
            queryName: 'users',
            success: function(){}
            failure: function(error){
                 error.containerPath = 'myContainer';
                 error.noAuditLog = true;
                 LABKEY.Utils.onError(error);
            }
        });
        &lt;/script&gt;
        */
        onError : function(error){
            Ext.Msg.hide();

            if(!error)
                return;

            console.log('ERROR: ' + error.exception);
            console.log(error);

            if(!error.noAuditLog){
                LABKEY.Query.insertRows({
                     //it would be nice to store them in the current folder, but we cant guarantee the user has write access..
                     containerPath: error.containerPath || '/shared',
                     schemaName: 'auditlog',
                     queryName: 'audit',
                     rows: [{
                        EventType: "Client API Actions",
                        Key1: 'Client Error',
                        //NOTE: labkey should automatically crop these strings to the allowable length for that field
                        Key2: window.location.href,
                        Key3: (error.stackTrace && Ext.isArray(error.stackTrace) ? error.stackTrace.join('\n') : null),
                        Comment: (error.exception || error.statusText || error.message),
                        Date: new Date()
                     }],
                     success: function(){
                         console.log('Error successfully logged')
                     },
                     failure: function(error){
                        console.log('Problem logging error');
                        console.log(error)
                     }
                });
            }
        },

        /**
         * Returns true if the passed object is empty (ie. {}) and false if not.
         *
         * @param {Object} object The object to test
         * @return {Boolean} the result of the test
        */
        isEmptyObj : function(ob){
           for(var i in ob){ return false;}
           return true;
        },

        /**
         * Rounds the passed number to the specified number of decimals
         *
         * @param {Number} input The number to round
         * @param {Number} dec The number of decimal places to use
         * @return {Number} The rounded number
        */
        roundNumber : function(input, dec){
            return Math.round(input*Math.pow(10,dec))/Math.pow(10,dec);
        },

        /**
         * Will pad the input string with zeros to the desired length.
         *
         * @param {Number/String} input The input string / number
         * @param {Integer} length The desired length
         * @param {String} padChar The character to use for padding.
         * @return {String} The padded string
        **/
        padString : function(input, length, padChar){
            if(typeof input != 'string')
                input = input.toString();

            var pd = '';
            if (length > input.length){
                for (var i=0; i < (length-input.length); i++){
                    pd += padChar;
                }
            }
            return pd + input;
        },

        /**
         * Returns true if the arguments are case-insensitive equal.  Note: the method converts arguments to strings for the purposes of comparing numbers, which means that it will return odd behaviors with objects (ie. LABKEY.Utils.caseInsensitiveEquals({t: 3}, '[object Object]') returns true)
         *
         * @param {String/Number} a The first item to test
         * @param {String/Number} b The second item to test
         * @return {boolean} True if arguments are case-insensitive equal, false if not
        */
        caseInsensitiveEquals: function(a, b){
            a = String(a);
            b = String(b);

            if (a.toLowerCase() == b.toLowerCase())
                return true;
            else
                return false;
        },

        /**
         * Sets the title of the webpart on the page.  This change is not sticky, so it will be reverted on refresh.
         * @param {string} title The title string
         * @param {integer} webPartId The ID of the webpart
         */
        setWebpartTitle: function(title, webPartId)
        {
           var titleEl = Ext.query('th[class=labkey-wp-title-left]:first', 'webpart_' + webPartId);
           if (titleEl && (titleEl.length >= 1))
           {
               titleEl[0].innerHTML = LABKEY.Utils.encodeHtml(title);
           }
        },

        /**
         * Tests whether the passed value can be used as boolean, using a loose definition.  Acceptable values for true are: 'true', 'yes', 1, 'on' or 't'.  Acceptable values for false are: 'false', 'no', 0, 'off' or 'f'.  Values are case-insensitive.
         * @param value The value to test
         */
        isBoolean: function(value){
            var upperVal = value.toString().toUpperCase();
            if (upperVal == "TRUE" || value == "1" || upperVal == "Y" || upperVal == "YES" || upperVal == "ON" || upperVal == "T"
                    || upperVal == "FALSE" || value == "0" || upperVal == "N" || upperVal == "NO" || upperVal == "OFF" || upperVal == "F"){
                return true;
            }
        }

    };
};
