LABKEY.Security = new function(impl) {

    /**
     * @private
     */
    impl.Impersonation = new function() {

        // Helper for lazy intializing impersonation dependencies
        var _display = function(componentName)
        {
            LABKEY.requiresExt4ClientAPI(true, function() {
                LABKEY.requiresScript('Impersonate.js', true, function() {
                    Ext4.onReady(function() {
                        Ext4.create(componentName).show();
                    });
                });
            });
        };

        return {
            showImpersonateUser: function() { _display('LABKEY.Security.ImpersonateUser'); },
            showImpersonateGroup: function() { _display('LABKEY.Security.ImpersonateGroup'); },
            showImpersonateRole: function() { _display('LABKEY.Security.ImpersonateRoles'); }
        };
    };

    return impl;

}(LABKEY.Security);