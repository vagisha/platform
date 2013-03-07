/*
 * Copyright (c) 2011-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.query");

LABKEY.requiresScript("editarea/edit_area_full.js");
LABKEY.requiresCss("_images/icons.css");

// http://stackoverflow.com/questions/494035/how-do-you-pass-a-variable-to-a-regular-expression-javascript/494122#494122
if (window.RegExp && !window.RegExp.quote) {
    RegExp.quote = function(str) {
        return (str+'').replace(/([.?*+^$[\]\\(){}|-])/g, "\\$1");
    };
}

LABKEY.query.SourceEditorPanel = Ext.extend(Ext.Panel, {

    constructor : function(config) {
        Ext.applyIf(config, {
            title        : 'Source',
            bodyStyle    : 'padding: 5px',
            monitorValid : true
        });

        LABKEY.query.SourceEditorPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        var items = [];

        if (this.query.canEdit)
        {
            items.push({
                xtype   : 'button',
                text    : 'Save & Finish',
                cls     : 'query-button',
                tooltip : 'Save & View Results',
                handler : function() { this.onSave(true); },
                scope   : this
            });
        }
        else
        {
            items.push({
                xtype   : 'button',
                text    : 'Done',
                cls     : 'query-button',
                tooltip : 'Save & View Results',
                handler : this.onDone,
                scope   : this
            });
        }
        items.push({
            xtype   : 'button',
            text    : 'Save',
            cls     : 'query-button',
            tooltip : 'Ctrl+S',
            handler : this.onSave,
            disabled: !this.query.canEdit,
            scope   : this
        },{
            xtype   : 'button',
            text    : 'Execute Query',
            tooltip : 'Ctrl+Enter',
            cls     : 'query-button',
            handler : function(btn) { this.execute(true); },
            scope   : this
        });

        if (this.query.propEdit) {
            items.push({
                xtype  : 'button',
                text   : 'Edit Properties',
                tooltip: 'Name, Description, Sharing',
                cls    : 'query-button',
                handler: function(btn) {
                    var url = LABKEY.ActionURL.buildURL('query', 'propertiesQuery', null, {
                        schemaName : this.query.schema,
                        'query.queryName' : this.query.query
                    });
                    window.location = url;
                },
                scope : this
            });
        }
        
        if (this.query.metadataEdit) {
            items.push({
                xtype   : 'button',
                text    : 'Edit Metadata',
                cls     : 'query-button',
                handler : function(btn) {
                    var url = LABKEY.ActionURL.buildURL('query', 'metadataQuery', null, {
                        schemaName : this.query.schema,
                        'query.queryName' : this.query.query
                    });
                    window.location = url;
                },
                scope   : this
            });
        }

        items.push({
            xtype : 'button',
            text  : 'Help',
            cls   : 'query-button',
            style : 'float: none;',
            menu  : new Ext.menu.Menu({
                        id : 'keyboard-menu',
                        cls : 'extContainer',
                        items : [{
                            text  : 'Shortcuts',
                            menu  : {
                                id   : '',
                                cls  : 'extContainer',
                                items: [{
                                    text : 'Save&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Ctrl+S',
                                    handler : this.onSave,
                                    scope : this
                                },{
                                    text : 'Execute&nbsp;&nbsp;&nbsp;Ctrl+Enter',
                                    handler : function() { this.execute(true); },
                                    scope   : this
                                },{
                                    text : 'Edit&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Ctrl+E',
                                    handler : function() { this.focusEditor(); },
                                    scope : this
                                }]
                            }
                        },'-',{
                            text  : 'SQL Reference',
                            handler : function() { window.open(this.query.help, '_blank'); },
                            scope : this
                        }]
                    })      
        });

        this.editorId = 'queryText';
        this.editorBoxId = Ext.id();
        
        this.editor = new Ext.Panel({
            border: false, frame : false,
            autoHeight : true,
            items : [
                {
                    id    : this.editorBoxId,
                    xtype : 'box',
                    autoEl: {
                        tag  : 'textarea',
                        id   : this.editorId,
                        rows : 17,
                        cols : 80,
                        style: 'width: 100%; height: 100%;',
                        wrap : 'off',
                        html : Ext.util.Format.htmlEncode(this.query.queryText)
                    }
                }
            ],
            listeners : {
                afterrender : function()
                {
                    this.eal = editAreaLoader;
                    var config = {
                        id     : this.editorId,
                        syntax : 'sql',
                        start_highlight: Ext.isIE ? false : true,
                        is_editable : !this.query.builtIn && this.query.canEditSql,
                        plugins : "save"
                    };
//                    Ext.EventManager.on(this.editorId, 'keydown', handleTabsInTextArea);
                    this.eal.init(config);
                    this.doLayout(false, true);
                },
                resize : function(x)
                {
                    if (!x)
                    {
                        var h = this.getHeight() - 160;
                        this.editor.setHeight(h);
                        var box = Ext.getCmp(this.editorBoxId);
                        if (box)
                        {
                            box.setHeight(h);
                            var _f = Ext.get('frame_' + this.editorId);
                            if (_f)
                                _f.setHeight(h, false);
                        }
                        this.doLayout(false, true);
                    }
                },
                scope : this
            },
            scope : this
        });
        
        items.push(this.editor);

        this.display = 'query-response-panel';
        this.queryResponse = new Ext.Panel(
        {
            autoScroll : true,
            border: false, frame: false,
            items : [{
                layout : 'fit',
                id : this.display,
                border : false, frame : false
            }]
        });

        items.push(this.queryResponse);

        this.items = items;

        this.executeTask = new Ext.util.DelayedTask(function(args){
            this.onExecuteQuery(args.force);
        }, this, []);

        LABKEY.query.SourceEditorPanel.superclass.initComponent.apply(this, arguments);

        this.on('resize', function(){
            this.editor.fireEvent('resize');
        });
    },

    setDisplay : function(id)
    {
        if (id) this.display = id;           
    },

    focusEditor : function()
    {
        this.eal.execCommand(this.editorId, 'focus');
    },

    execute : function(force)
    {
        this.executeTask.delay(200, null, null, [{force: force}]);
    },

    onExecuteQuery : function(force)
    {
        this.queryEditorPanel.onExecuteQuery(force);
    },

    save : function(showView) {
        this.onSave(showView);
    },

    onDone : function()
    {
        this.queryEditorPanel.onDone();
    },

    onSave : function(showView)
    {
        this.queryEditorPanel.onSave(showView);
    },

    onShow : function()
    {
        LABKEY.query.SourceEditorPanel.superclass.onShow.call(this);
        
        this.eal.show(this.editorId);
    },

    onHide : function()
    {
        this.eal.hide(this.editorId);
        
        LABKEY.query.SourceEditorPanel.superclass.onHide.call(this);
    },


    saved : function()
    {
        this.query.queryText = this.getValue();
    },

    /**
     * Returns whether the query has changed from its last saved state.
     */
    isSaveDirty : function()
    {
        // 12607: Prompt to confirm leaving when page isn't dirty -- watch for \s\r\n
        return this.eal.getValue(this.editorId).toLowerCase().replace(/(\s)/g, '') != this.query.queryText.toLowerCase().replace(/(\s)/g, '');
    },

    /**
     * Returns false if the query has been executed in it's current state. Otherwise, true.
     */
    isQueryDirty : function(update)
    {
        var res = !this.cachedSql || this.cachedSql != this.getValue();
        if (update)
            this.cachedSql = this.getValue();
        return res;
    },

    getValue : function()
    {
        var queryText = this.query.queryText;
        if (this.eal)
            queryText = this.eal.getValue(this.editorId);
        return queryText;
    }
});



LABKEY.query.MetadataXMLEditorPanel = Ext.extend(Ext.Panel, {
    constructor : function(config)
    {
        Ext.applyIf(config, {
            title: 'XML Metadata',
            bodyStyle: 'padding:5px',
            autoScroll: true,
            monitorValid: true,
            
            // Panel Specific
            editorId   : 'metadataText',
            save       : function() {}
        });
        
        LABKEY.query.MetadataXMLEditorPanel.superclass.constructor.call(this, config);
    },

    initComponent : function()
    {
        var items = [];

        if (this.query.canEdit)
        {
            items.push({
                    xtype   : 'button',
                    text    : 'Save & Finish',
                    cls     : 'query-button',
                    tooltip : 'Save & View Results',
                    handler : function() { this.onSave(true); },
                    scope   : this
                }
            );
        }
        else
        {
            items.push({
                    xtype   : 'button',
                    text    : 'Done',
                    cls     : 'query-button',
                    tooltip : 'View Results',
                    handler : this.onDone,
                    scope   : this
                }
            );
        }
        items.push({
                xtype   : 'button',
                text    : 'Save',
                cls     : 'query-button',
                disabled: !this.query.canEdit,
                handler : this.onSave,
                scope   : this
            },{
                xtype   : 'button',
                text    : 'Help',
                cls     : 'query-button',
                style   : 'float: none;',
                handler : function() { window.open(this.query.metadataHelp, '_blank'); },
                scope   : this
            }
        );


        this.editorBoxId = Ext.id();
        
        this.editor = new Ext.Panel({
            border: false, frame : false,
            autoHeight: true,
            items : [
                {
                    id : this.editorBoxId,
                    xtype : 'box',
                    autoEl: {
                        tag  : 'textarea',
                        id   : this.editorId,
                        rows : 17,
                        cols : 80,
                        wrap : 'off',
                        style: 'width: 100%; height: 100%;',
                        html : Ext.util.Format.htmlEncode(this.query.metadataText)
                    }
                }
            ],
            listeners :
            {
                afterrender : function()
                {
                    this.eal = editAreaLoader;
                    var config = {
                        id     : this.editorId,
                        syntax : 'xml',
                        start_highlight: Ext.isIE ? false : true,
                        plugins : "save"
                    };
                    Ext.EventManager.on(this.editorId, 'keydown', handleTabsInTextArea);
                    this.eal.init(config);
                },
                resize : function(x)
                {
                    if (!x) {
                        var h = this.getHeight() - 160;
                        this.editor.setHeight(h);
                        var box = Ext.getCmp(this.editorBoxId);
                        if (box) {
                            box.setHeight(h);
                            var _f = Ext.get('frame_' + this.editorId);
                            if (_f) {
                                _f.setHeight(h, false);
                            }
                        }
                        this.doLayout(false, true);
                    }
                },
                scope : this
            },
            scope : this
        });

        items.push(this.editor);

        this.display = 'xml-response-panel';
        this.queryResponse = new Ext.Panel({
            autoScroll : true,
            border     : false, frame: false,
            items      : [{
                layout : 'fit',
                id     : this.display,
                border : false, frame : false
            }]
        });

        items.push(this.queryResponse);


        this.items = items;
        
        LABKEY.query.MetadataXMLEditorPanel.superclass.initComponent.apply(this, arguments);

        this.on('resize', function()
        {
            this.editor.fireEvent('resize');
        });
    },

    setDisplay : function(id)
    {
        if (id) this.display = id;
    },

    onShow : function()
    {

        if (Ext.isIE) {
            // Doing this due to faulty editor when tabbing back
            this.eal = editAreaLoader;
            Ext.EventManager.on(this.editorId, 'keydown', handleTabsInTextArea);
            this.eal.init({
                id     : this.editorId,
                syntax : 'xml',
                start_highlight: true
            });
        }

        LABKEY.query.MetadataXMLEditorPanel.superclass.onShow.call(this);

        if (!Ext.isIE && this.eal)
            this.eal.show(this.editorId);
    },

    onHide : function()
    {

        if (this.eal) {
            if (Ext.isIE)
                this.eal.delete_instance(this.editorId);
            else
                this.eal.hide(this.editorId);
        }

        LABKEY.query.MetadataXMLEditorPanel.superclass.onHide.call(this);
    },

    saved : function()
    {
        this.query.metadataText = this.getValue();
    },

    isSaveDirty : function()
    {
        var _val = this.getValue().toLowerCase().replace(/(\s)/g, '');
        var _meta = this.query.metadataText.toLowerCase().replace(/(\s)/g, '');
        return _val != _meta;
    },

    getValue : function()
    {
        if (this.eal)
        {
            return this.eal.getValue(this.editorId);
        }
        else
        {
            var el = Ext.get('metadataText');
            if (el)
                return el.getValue();
        }
        return this.query.metadataText;
    },

    isQueryDirty : function(update)
    {
        if ((this.cachedMeta === undefined || this.cachedMeta.length == 0) && this.getValue().length == 0)
            return false;
        var res = !this.cachedMeta || (this.cachedMeta != this.getValue());
        if (update)
            this.cachedMeta = this.getValue();
        return res;
    },

    onDone : function()
    {
        this.queryEditorPanel.onDone();
    },

    onSave : function(showView)
    {
        this.queryEditorPanel.onSave(showView);
    }
});



LABKEY.query.QueryEditorPanel = Ext.extend(Ext.Panel, {

    constructor : function(config)
    {
        Ext.apply(this, config);

        this._executeSucceeded = false;
        this._executing = false;

        if (!Ext.isDefined(this.query.canEdit))
            this.query.canEdit = true;
        if (!Ext.isDefined(this.query.canEditSql))
            this.query.canEditSql = this.query.canEdit;
        if (!Ext.isDefined(this.query.canEditMetaData))
            this.query.canEditMetaData = this.canEdit;

        LABKEY.query.QueryEditorPanel.superclass.constructor.call(this);
    },

    initComponent : function()
    {
        var items = [];

        this._dataTabId = Ext.id();
        this.dataTab = new Ext.Panel({
            title : 'Data',
            autoScroll : true,
            items : [{
                id    : this._dataTabId,
                xtype : 'panel',
                border: false, frame : false
            }],
            listeners : {
                activate : function(p)
                {
                    this.sourceEditor.setDisplay(this._dataTabId);

                    // difference between clicking on tab and clicking execute -- avoid repeating execution
                    if (!this._executing)
                        this.sourceEditor.execute();
                },
                scope : this
            }
        });

        var _metaId = Ext.id();
        this.sourceEditor = new LABKEY.query.SourceEditorPanel({
            query : this.query,
            metadata : true,
            metaId : _metaId,
            queryEditorPanel : this // back pointer for onSave()
        });


        this.metaEditor = new LABKEY.query.MetadataXMLEditorPanel({
            id    : _metaId,
            query : this.query,
            queryEditorPanel : this // back pointer for onSave()
        });

        this.tabPanel = new Ext.TabPanel({
            activeTab : this.activeTab,
            width     : '100%',
            items     : [this.sourceEditor, this.dataTab, this.metaEditor]
        });

        items.push(this.tabPanel);

        this.items = items;

        LABKEY.query.QueryEditorPanel.superclass.initComponent.apply(this, arguments);
    },

    onRender : function()
    {
        LABKEY.query.QueryEditorPanel.superclass.onRender.apply(this, arguments);
        
        window.onbeforeunload = LABKEY.beforeunload(this.isDirty, this);
    },

    isDirty : function()
    {
        return this.sourceEditor.isSaveDirty() || this.metaEditor.isSaveDirty();
    },


    onDone : function()
    {
        if (this.query.executeUrl) {
            window.location = this.query.executeUrl;
        }
    },

    onSave : function(showView)
    {
        if (!this.query.canEdit)
            return;

        this.fireEvent('beforesave', this);

        var json = {
            schemaName   : this.query.schema,
            queryName    : this.query.query,
            ff_metadataText : this.getMetadataEditor().getValue()
        };

        if (this.query.builtIn)
            json.ff_queryText = null;
        else
            json.ff_queryText = this.getSourceEditor().getValue();

        Ext.Ajax.request(
        {
            url    : LABKEY.ActionURL.buildURL('query', 'saveSourceQuery.api'),
            method : 'POST',
            success: LABKEY.Utils.getCallbackWrapper(onSuccess, this),
            jsonData : Ext.encode(json),
            failure: LABKEY.Utils.getCallbackWrapper(onError, this, true),
            headers : {
                'Content-Type' : 'application/json'
            },
            scope : this
        });

        function onSuccess(json, response, opts)
        {
            if (json.parseErrors)
            {
                // There are errors
                var msgs = [];
                var errors = json.parseErrors;
                for (var i=0; i<errors.length;i++)
                    msgs.push(errors[i]);
                this.showErrors(msgs);
            }
            else
                this.clearErrors();

            this.getMetadataEditor().saved();
            this.getSourceEditor().saved();
            this.fireEvent('save', this, true, json);

            if (showView === true && this.query.executeUrl)
                this.onDone();
        }

        function onError(json, response, opts)
        {
            this.fireEvent('save', this, false, json);
        }
    },

    clearErrors : function()
    {
        var errorEls = Ext.DomQuery.select('.error-container');
        for (var i=0; i < errorEls.length; i++) {
            Ext.get(errorEls[i]).parent().update('');
        }
    },

    showErrors : function(errors)
    {
        var tabEl;
        if (errors && errors.length > 0)
        {
            this.gotoError(errors[0]);

            // default to showing errors are source tab
            var tabEl = errors[0].type == 'xml' ? this.getMetadataEditor().display : this.getSourceEditor().display;
        }

        var errorEl = tabEl ? Ext.get(tabEl) : undefined;
        var queryEl = Ext.get('query-response-panel');

        if (!errors || errors.length == 0)
        {
            if (errorEl) errorEl.update('');
            if (queryEl) queryEl.update('');
            return;
        }

        var inner = '<div class="labkey-error error-container"><ul>';
        for (var e = 0; e < errors.length; e++)
        {
            inner += '<li>' + errors[e].msg + '</li>'
        }
        inner += '</ul></div>';

        if (errorEl)
        {
            errorEl.update('');
            errorEl.update(inner);
        }

        if (queryEl)
        {
            queryEl.update('');
            queryEl.update(inner);
        }
    },


    gotoError : function(error)
    {
        var _editor = error.type == 'xml' ? this.metaEditor : this.sourceEditor;
        this.tabPanel.setActiveTab(_editor);
        if (_editor && _editor.eal)
        {
            var cmd = _editor.eal.execCommand;
            if (cmd)
            {
                // First highlight the line
                cmd(_editor.editorId, 'resync_highlight', true);
                if (error && error.line)
                    cmd(_editor.editorId, 'go_to_line', error.line.toString());

                // calculate string position offset -- 12703
                var val    = _editor.eal.getValue(_editor.editorId);
                var valArr = val.split('\n');
                var _s = -1;
                if (error && error.line && error.line > 1) {
                    if (RegExp) {
                        _s = valArr[error.line-1].search(RegExp.quote(error.errorStr));
                    }
                    else {
                        _s = valArr[error.line-1].search(error.errorStr);
                    }

                    // calculate string position offset
                    if (_s >= 0) {
                        var offset = 0;
                        Ext.each(valArr.slice(0,error.line-1), function(line){
                            offset += line.length + 1; // + 1 for \n
                        });
                        _s += offset;
                    }
                }
                else
                    _s = val.search(error.errorStr);

                // Highlight selected text
                if (_s >= 0 && error.errorStr) {
                    var end = _s + error.errorStr.length;
                    _editor.eal.setSelectionRange(_editor.editorId, _s, end);
                }
                _editor.eal.getSelectedText(_editor.editorId);
            }
        }
    },

    onExecuteQuery : function(force)
    {
        this._executing = true;
        var sourceDirty = !this.query.builtIn && this.getSourceEditor().isQueryDirty(true);
        var metaDirty   = this.getMetadataEditor().isQueryDirty(true);
        var dirty = sourceDirty || metaDirty;
        if (!dirty && !force && this._dataLoaded) {
            this._executing = false;
            return;
        }
        this.clearErrors();
        this.getSourceEditor().setDisplay(this._dataTabId);
        this.tabPanel.setActiveTab(this.dataTab);
        this.dataTab.getEl().mask('Loading Query...', 'loading-indicator indicator-helper');

        var qwpEl = Ext.get(this.getSourceEditor().display);
        if (qwpEl) { qwpEl.update(''); }

        // QueryWebPart Configuration
        var config = {
            renderTo     : qwpEl,
            schemaName   : this.query.schema,
            errorType    : 'json',
            allowChooseQuery : false,
            allowChooseView  : false,
            allowHeaderLock  : false,
            frame     : 'none',
            title     : '',
            masking   : false,
            timeout   : Ext.Ajax.timeout, // 12451 -- control the timeout
            buttonBarPosition : 'top',    // 12644 -- only have a top toolbar
            success   : function(response) {
                this._executeSucceeded = true;
                this.dataTab.getEl().unmask();
                this._executing = false;
            },
            failure   : function(response) {
                this._executeSucceeded = false;
                this.dataTab.getEl().unmask();
                if (response && response.parseErrors)
                {
                    var errors = [];
                    for (var e=0; e < response.parseErrors.length; e++)
                        errors.push(response.parseErrors[e]);
                    this.showErrors(errors);
                }
                this._executing = false;
            },
            scope : this
        };

        // Choose queryName or SQL as source
        if (this.query.builtIn)
        {
            config.queryName = this.query.query;
        }
        else
        {
            config.sql = this.getSourceEditor().getValue();
        }

        // Apply Metadata Override
        var _meta = this.getMetadataEditor().getValue();
        if (_meta && _meta.length > 0)
            config.metadata = { type : 'xml', value: _meta };

        this._dataLoaded = true;
        var qwp = new LABKEY.QueryWebPart(config);
    },

    addTab : function(config, makeActive)
    {
        this.tabPanel.add(config);
        this.tabPanel.doLayout();
        this.doLayout();
        
        this.tabPanel.setActiveTab(this.tabPanel.items.length-1);
    },

    save : function()
    {
        this.tabPanel.getActiveTab().onSave();
    },

    openSourceEditor : function(focus)
    {
        this.tabPanel.setActiveTab(this.sourceEditor);
        if (focus) {
            this.sourceEditor.focusEditor();
        }
    },

    // Allows others to hook events on sourceEditor
    getSourceEditor : function() { return this.sourceEditor; },

    // Allows others to hook events on metadataEditor
    getMetadataEditor : function() { return this.metaEditor; }
});
