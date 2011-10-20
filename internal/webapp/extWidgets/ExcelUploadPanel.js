/*
 * Copyright (c) 2010-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4ClientAPI();

Ext4.namespace('LABKEY.ext');

/**
 * Constructs a new LabKey ExcelUploadPanel using the supplied configuration.
 * @class LabKey extension to the <a href="http://docs.sencha.com/ext-js/4-0/#!/api/Ext.form.Panel">Ext.form.Panel</a> class,
 * which creates a the UI to upload an excel file with information to the specified table.
 *
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeyExt">Tips for Using Ext to Build LabKey Views</a></li>
 *              </ul>
 *           </p>
 * @constructor
 * @param config Configuration properties.
 * @param {String} config.schemaName The LabKey schema to query.
 * @param {String} config.queryName The query name within the schema to fetch.
 * @param {String} [config.viewName] A saved custom view of the specified query to use if desired.
 * @param {String} [config.containerPath] The containerPath to use when fetching the query
 * @param {String} [config.columns] A comma-delimited list of column names to fetch from the specified query.
 * @param {Object} [config.metadata] A metadata object that will be applied to the default metadata returned by the server.  See example below for usage.
 * @param {Object} [config.fieldDefaults] A metadata object that will be applied to every field of the default metadata returned by the server.  Will be superceeded by the metadata object in case of conflicts. See example below for usage.
 */

Ext4.define('LABKEY.ext4.ExcelUploadPanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.labkey-exceluploadpanel',
    initComponent: function(){
        Ext4.QuickTips.init();

        Ext4.applyIf(this, {
            autoHeight: true
            ,url: LABKEY.ActionURL.buildURL("query", "import", null, {schemaName: this.schemaName, 'query.queryName': this.queryName})
            ,title: 'Upload Data'
            ,bodyBorder: false
            ,border: true
            ,bodyStyle:'padding:5px'
            ,frame: false
            ,defaults: {
                bodyStyle:'padding:5px'
            }
            ,buttonAlign: 'left'
            ,monitorValid: true
            ,store: Ext4.create('LABKEY.ext4.Store', {
                containerPath: this.containerPath
                ,schemaName: this.schemaName
                ,queryName: this.queryName
                ,viewName: this.viewName
                ,columns: this.columns
                ,metadata: this.metadata
                ,fieldDefaults: this.fieldDefaults
                ,maxRows: 0
                ,autoLoad: true
            })
            ,items: [{
                xtype: 'button'
                ,text: 'Download Excel Template'
                ,border: false
                ,listeners: {
                    scope: this,
                    click: this.makeExcel
                },
                scope: this,
                handler: this.makeExcel
            },{
                xtype: 'panel',
                itemId: 'errorArea',
                border: false,
                defaults: {
                    border: false,
                    bodyBorder: false
                }
            },{
                xtype: 'radiogroup',
                name: 'uploadType',
                isFormField: false,
                itemId: 'inputType',
                width: 350,
                items: [{
                    boxLabel: 'Copy/Paste Data',
                    xtype: 'radio',
                    name: 'uploadType',
                    isFormField: false,
                    inputValue: 'text',
                    checked: true,
                    scope: this,
                    handler: function(fb, y){
                        if (!y){return};

                        var fileArea = this.down('#fileArea');
                        fileArea.removeAll();

                        fileArea.add({
                            itemId:"fileContent",
                            name: 'text',
                            xtype: 'textarea',
                            height:350,
                            width: 700
                        },{
                            xtype: 'combo',
                            name: 'format',
                            itemId: 'formatField',
                            width: 300,
                            value: 'tsv',
                            displayField: 'displayText',
                            valueField: 'value',
                            queryMode: 'local',
                            store: Ext4.create('Ext.data.ArrayStore', {
                                fields: [
                                    'value',
                                    'displayText'
                                ],
                                idIndex: 0,
                                data: [
                                    ['tsv', 'Tab-separated text (tsv)'],
                                    ['csv', 'Comma-separated text (csv)']
                                ]
                            })
                        });

                        this.doLayout();

                        this.uploadType = 'text';
                    }
                },{
                    boxLabel: 'File Upload',
                    xtype: 'radio',
                    name: 'uploadType',
                    inputValue: 'file',
                    handler: function(fb, y){
                        if (!y){return};

                        var fileArea = this.down('#fileArea');
                        fileArea.removeAll();

                        fileArea.add({
                            xtype: 'filefield',
                            name: 'file',
                            itemId: 'fileContent',
                            buttonText: 'Select File...'

                        });
                        this.doLayout();

                        this.uploadType = 'file';
                    },
                    scope: this
                }]
            },{
                xtype: 'panel',
                itemId: 'fileArea',
                border: false,
                items: [{
                    itemId: 'fileContent',
                    xtype: 'textarea',
                    name: 'text',
                    height:350,
                    width: 700
                },{
                    xtype: 'combo',
                    name: 'format',
                    itemId: 'formatField',
                    width: 300,
                    value: 'tsv',
                    displayField: 'displayText',
                    valueField: 'value',
                    triggerAction: 'all',
                    mode: 'local',
                    store: Ext4.create('Ext.data.ArrayStore', {
                        fields: [
                            'value',
                            'displayText'
                        ],
                        idIndex: 0,
                        data: [
                            ['tsv', 'Tab-separated text (tsv)'],
                            ['csv', 'Comma-separated text (csv)']
                        ]
                    })
                }]
            }]
            ,buttons: [{
                text: 'Submit'
                ,width: 50
                ,handler: this.formSubmit
                ,scope: this
                ,formBind: true
            },{
                text: 'Cancel'
                ,width: 50
                ,scope: this
                ,handler: function(){
                    window.location = LABKEY.ActionURL.buildURL('project', 'begin.view')
                }
            }]
            ,listeners: {
                scope: this
                ,actioncomplete: this.processResponse
                ,actionfailed: this.processResponse
            }
        });

        this.uploadType = 'text';

        this.callParent();

        this.addEvents('uploadexception', 'uploadcomplete');
    },

    makeExcel: function(){
        var header = [];

        this.store.getFields().each(function(f){
            if (LABKEY.ext.MetaHelper.shouldShowInInsertView(f)){
                header.push(f.name);
            }
        }, this);

        //TODO: Add formatting or validation in the excel sheet?
        var config = {
            fileName : this.queryName + '_' + (new Date().format('Y-m-d H_i_s')) + '.xls',
            sheets : [{
                name: 'data',
                data: [header]
            }]
        };

        LABKEY.Utils.convertToExcel(config);
    },

    formSubmit: function(){
        Ext4.Msg.wait("Uploading...");

        this.down('#errorArea').removeAll();
        this.doLayout();

        this.form.fileUpload = !(this.uploadType == 'text');
        this.form.url = this.url;
        this.form.submit();
    },

    processResponse: function(form, action){
        var errorArea = this.down('#errorArea');
        errorArea.removeAll();

        var response = Ext4.JSON.decode(action.response.responseText);
        if(response && response.errors && response.errors._form){
            errorArea.add({
                style: 'color:red;',
                html: response.errors._form
            });
        }

        Ext4.Msg.hide();

        if(!response.success){
            alert('There was a problem with the upload');
            this.fireEvent('uploadexception', response);
        }
        else {
            if(response.rowCount > 0)
                alert('Success! '+response.rowCount+' rows inserted.');
            else
                alert('No rows inserted.');

            this.fireEvent('uploadcomplete', response);
        }

        this.doLayout();
    }
});



Ext4.define('LABKEY.ext4.ExcelUploadWin', {
    extend: 'Ext.Window',
    alias: 'widget.labkey-exceluploadwin',
    initComponent: function(){
        Ext4.apply(this, {
            closeAction:'hide',
            title: 'Upload Data',
            width: 730,
            items: [{
                xtype: 'labkey-exceluploadpanel',
                bubbleEvents: ['uploadexception', 'uploadcomplete'],
                itemId: 'theForm',
                title: null,
                buttons: null,
                schemaName: this.schemaName,
                queryName: this.queryName,
                viewName: this.viewName
            }],
            buttons: [{
                text: 'Upload'
                ,width: 50
                ,handler: function(){
                    var form = this.down('#theForm');
                    form.formSubmit.call(form);
                }
                ,scope: this
                ,formBind: true
            },{
                text: 'Close'
                ,width: 50
                ,scope: this
                ,handler: function(btn){
                    this.hide();
                }
            }]
        });

        this.callParent();

        this.addEvents('uploadexception', 'uploadcomplete');
    }
});