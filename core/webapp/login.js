/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

(function ($)
{
    // bind triggers, page init, etc
    function onReady() {
        // on document ready
        $('.signin-btn').click(authenticateUser);
        $('.loginSubmitButton').click(authenticateUser);
        init();
        getTermsOfUse();
        getOtherLoginMechanisms();
        registrationEnabled();
    }

    function authenticateUser() {
        if (document.getElementById('remember').checked == true) {
            LABKEY.Utils.setCookie('email', encodeURIComponent(document.getElementById('email').value), true, 360);
        } else {
            LABKEY.Utils.deleteCookie('email', true);
        }
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('login', 'loginApi.api', this.containerPath),
            method: 'POST',
            params: {
                remember: document.getElementById('remember').value,
                email: document.getElementById('email').value,
                password: document.getElementById('password').value,
                approvedTermsOfUse: document.getElementById('approvedTermsOfUse').checked,
                termsOfUseType: document.getElementById('termsOfUseType').value,
                returnUrl: LABKEY.ActionURL.getParameter("returnUrl"),
                urlHash: document.getElementById('urlhash'),
                'X-LABKEY-CSRF': document.getElementById('X-LABKEY-CSRF')
            },
            success: LABKEY.Utils.getCallbackWrapper(function (response) {
                if(response && response.returnUrl){
                    window.location = response.returnUrl;
                }
            }, this),
            failure: LABKEY.Utils.getCallbackWrapper(function (response) {
                if(document.getElementById('errors') && response && response.exception) {
                    document.getElementById('errors').innerHTML = response.exception;
                }
            }, this)
        });
    }

    function acceptTermsOfUse() {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('login', 'acceptTermsOfUseApi.api', this.containerPath),
            method: 'POST',
            params: {
                approvedTermsOfUse: document.getElementById('approvedTermsOfUse').checked,
                termsOfUseType: document.getElementById('termsOfUseType').value,
                returnUrl: LABKEY.ActionURL.getParameter("returnUrl"),
                urlHash: document.getElementById('urlhash'),
                'X-LABKEY-CSRF': document.getElementById('X-LABKEY-CSRF')
            },
            success: function () {
                window.location = LABKEY.ActionURL.getParameter("returnUrl")
            },
            failure: LABKEY.Utils.getCallbackWrapper(function (response) {
                if (document.getElementById('errors') && response && response.exception) {
                    document.getElementById('errors').innerHTML = response.exception;
                }
            }, this)
        });
    }

    function getTermsOfUse() {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('login', 'getTermsOfUseApi.api', this.containerPath),
            method: 'POST',
            params: {
                returnUrl: LABKEY.ActionURL.getParameter("returnUrl"),
                urlHash: document.getElementById('urlhash'),
                'X-LABKEY-CSRF': document.getElementById('X-LABKEY-CSRF')
            },
            success: LABKEY.Utils.getCallbackWrapper(function (response) {
                var termsContents = document.getElementsByClassName('termsOfUseContent');
                var termsSections = document.getElementsByClassName('termsOfUseSection');
                if (termsSections && termsSections.length >= 1 && termsContents && termsContents.length >=1) {
                    if (!response.termsOfUseContent) {
                        termsSections[0].hidden = true;
                    } else {
                        termsContents[0].innerHTML = response.termsOfUseContent;
                    }
                }
                if (document.getElementById('termsOfUseType') && response && response.termsOfUseType) {
                    document.getElementById('termsOfUseType').value = response.termsOfUseType;
                }
            }, this)
        });
    }

    function getOtherLoginMechanisms() {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('login', 'getLoginMechanismsApi.api', this.containerPath),
            method: 'POST',
            params: {
                returnUrl: LABKEY.ActionURL.getParameter("returnUrl"),
                urlHash: document.getElementById('urlhash'),
                'X-LABKEY-CSRF': document.getElementById('X-LABKEY-CSRF')
            },
            success: LABKEY.Utils.getCallbackWrapper(function (response) {
                var otherLoginContents = document.getElementsByClassName('otherLoginMechanismsContent');
                var otherLoginSections = document.getElementsByClassName('otherLoginMechanismsSection');
                if (otherLoginSections && otherLoginSections.length >=1 && otherLoginContents && otherLoginContents.length >= 1) {
                    if (!response || !response.otherLoginMechanismsContent) {
                        otherLoginSections[0].hidden = true;
                    } else {
                        otherLoginContents[0].innerHTML = response.otherLoginMechanismsContent;
                    }
                }
             }, this)
        });
    }


    function registrationEnabled() {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('login', 'getRegistrationConfigApi.api', this.containerPath),
            method: 'POST',
            params: {
                'X-LABKEY-CSRF': document.getElementById('X-LABKEY-CSRF')
            },
            success: LABKEY.Utils.getCallbackWrapper(function (response) {
                var registrationSections = document.getElementsByClassName('registrationSection');
                if (registrationSections && registrationSections.length >=1) {
                    registrationSections[0].hidden = !response || !response.enabled;
                }
            }, this)
        });
    }

    function init() {
        // Provide support for persisting the url hash through a login redirect
        if (window && window.location && window.location.hash) {
            var h = document.getElementById('urlhash');
            if (h) {
                h.value = window.location.hash;
            }
        };

        // Issue 22094: Clear password on login page after session timeout has been exceeded
        var timeout = 86400000;
        if (timeout > 0) {
            var passwordField = document.getElementById('password');

            // The function to do the clearing
            var clearPasswordField = function () {
                passwordField.value = '';
            };

            // Start the clock when the page loads
            var timer = setInterval(clearPasswordField, timeout);

            // Any time the value changes reset the clock
            var changeListener = function () {
                if (timer) {
                    clearInterval(timer);
                }
                timer = setInterval(clearPasswordField, timeout);
            };

            // Wire up the listener for changes to the password field
            passwordField.onchange = changeListener;
            passwordField.onkeypress = changeListener;
        }

        // examine cookies to determine if user wants the email pre-populated on form
        var h = document.getElementById('email');
        if (h && LABKEY.Utils.getCookie("email")) {
            h.value = decodeURIComponent(LABKEY.Utils.getCookie("email"));
        };
        h = document.getElementById('remember');
        if (h && LABKEY.Utils.getCookie("email")) {
            h.checked = true;
        } else {
            h.checked = false;
        };
    }

    $(onReady);

})(jQuery);
