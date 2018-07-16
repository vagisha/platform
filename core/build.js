/*
 * Copyright (c) 2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
const fs = require('fs-extra');

/**
 * Experimental build configuration to use @labkey/api distribution in lieu of default client/api/core files.
 * For more information see https://github.com/LabKey/labkey-api-js#running-against-a-labkey-server
 */
const USE_LABKEY_API = false;

function copyAPIFiles() {
    const apiDistDir = __dirname + '/node_modules/@labkey/api/dist/';
    const targetDir = __dirname + '/resources/web/clientapi/';

    const files = [
        'labkey-api-js-core.min.js',
        'labkey-api-js-core.min.js.map'
    ];

    files.forEach((file) => {
        fs.copy(apiDistDir + file, targetDir + file);
    });
}

function overwriteLibXML() {
    const clientLibDir = __dirname + '/../../api/webapp/';
    const clientLibFileName = 'clientapi_core.lib.xml';

    const libContents = [
        '<!-- DO NOT COMMIT! -->',
        '<!-- This file was autogenerated by core/build.js to allow for experimental preview of @labkey/api. -->',
        '<libraries xmlns="http://labkey.org/clientLibrary/xml/">',
            '<library compileInProductionMode="false">',
                '<script path="clientapi/labkey-api-js-core.min.js"/>',
            '</library>',
        '</libraries>'
    ].join('\n');

    fs.writeFile(clientLibDir + clientLibFileName, libContents);
}

if (USE_LABKEY_API === true) {
    copyAPIFiles();
    overwriteLibXML();
}