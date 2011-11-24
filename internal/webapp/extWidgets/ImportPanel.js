/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4ClientAPI();
LABKEY.requiresScript("/extWidgets/ExcelUploadPanel.js");
LABKEY.requiresScript("/extWidgets/Ext4FormPanel.js");

Ext4.define('LABKEY.ext.ImportPanel', {
    extend: 'Ext.tab.Panel',
    initComponent: function(){
        this.store = Ext4.create('LABKEY.ext4.Store', {
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: this.viewName,
            columns: '*',
            maxRows: 0,
            autoLoad: true,
            listeners: {
                load: function(store){
                    delete store.maxRows;
                }
            }
        });

        Ext4.apply(this, {
            activeTab: 0,
            defaults: {
                style: 'padding: 10px;'
            },
            items: [{
                title: 'Import Single',
                xtype: 'labkey-formpanel',
                store: this.store,
                listeners: {
                    uploadcomplete: function(){
                        window.location = LABKEY.ActionURL.getParameter('srcURL') || LABKEY.ActionURL.buildURL('project', 'begin');
                    }
                }
//            },{
//                title: 'Import Multiple',
//                xtype: 'labkey-gridpanel',
//                store: Ext4.create('LABKEY.ext4.Store', {
//                    schemaName: this.schemaName,
//                    queryName: this.queryName,
//                    viewName: this.viewName,
//                    maxRows: 0
//                })
            },{
                title: 'Import Spreadsheet',
                xtype: 'labkey-exceluploadpanel',
                store: this.store, //saves redundant loading
                schemaName: this.schemaName,
                queryName: this.queryName,
                viewName: this.viewName,
                listeners: {
                    uploadcomplete: function(){
                        window.location = LABKEY.ActionURL.getParameter('srcURL') || LABKEY.ActionURL.buildURL('project', 'begin');
                    }
                }
            }]
        });

        this.callParent(arguments);
    }
});