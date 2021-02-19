/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'authenticationConfiguration',
        title: 'Authentication',
        permission: 'admin',
        path: './src/client/AuthenticationConfiguration'
    },{
        name: 'components',
        title: '@labkey/components',
        permission: 'admin',
        path: './src/client/LabKeyUIComponentsPage'
    },{
        name: 'errorHandler',
        title: 'Error Handler',
        path: './src/client/ErrorHandler',
        generateLib: true // used in errorView.jsp
    },{
        name: 'productNavigation',
        title: 'Product Navigation',
        path: './src/client/ProductNavigation',
        generateLib: true // used in header.jsp
    }]
};