Ext4.define('File.data.webdav.XMLResponse', {

    extend : 'Ext.data.Model',

//    requires : [ 'File.data.webdav.URI' ],

    statics : {
        getURI : function(v, rec) {
            var uri = rec.uriOBJECT || Ext4.create('File.data.webdav.URI', v);
            if (!Ext4.isIE && !rec.uriOBJECT)
                try {rec.uriOBJECT = uri;} catch (e) {}
            return uri;
        }
    },

    fields : [
        {
            name : 'uri', mapping : 'href', convert : function(v, rec) {
            var uri = File.data.webdav.XMLResponse.getURI(v,rec);
            return uri ? uri.href : '';
        }},
        {
            name : 'fileLink', mapping : 'href', convert : function(v, rec) {
            var uri = File.data.webdav.XMLResponse.getURI(v,rec);

            if (uri && uri.file) {
                return Ext4.DomHelper.markup({
                    tag  :'a',
                    href : Ext4.util.Format.htmlEncode(uri.href + '?contentDisposition=attachment'),
                    html : Ext4.util.Format.htmlEncode(decodeURIComponent(uri.file))
                });
            }

            return '';
        }},
        {
            name : 'path'//, mapping : 'href'
//            convert : function (v, rec)
//            {
//                var uri = File.data.webdav.XMLResponse.getURI(v,rec);
//                var path = decodeURIComponent(uri.pathname);
//                if (path.length >= prefixDecode.length && path.substring(0,prefixDecode.length) == prefixDecode)
//                    path = path.substring(prefixDecode.length);
//                return path;
//            }
        },{
            name : 'name', mapping : 'propstat/prop/displayname'
        },{
            name: 'fileExt', mapping: 'propstat/prop/displayname',
                convert : function (v, rec)
                {
                    // parse the file extension from the file name
                    var idx = v.lastIndexOf('.');
                    if (idx != -1)
                        return v.substring(idx+1);
                    return '';
                }
        },{
            name: 'file', mapping: 'href', type: 'boolean',
                convert : function (v, rec)
                {
                    // UNDONE: look for <collection>
                    var uri = File.data.webdav.XMLResponse.getURI(v, rec);
                    var path = uri.pathname;
                    return path.length > 0 && path.charAt(path.length-1) != '/';
                }
        },
        {name: 'href', convert : function() {return '';}},
        {name: 'text',        mapping: 'propstat/prop/displayname'},
        {name: 'icon',        mapping: 'propstat/prop/iconHref'},
        {name: 'created',     mapping: 'propstat/prop/creationdate',     type: 'date', dateFormat : "c"},
        {name: 'createdBy',   mapping: 'propstat/prop/createdby'},
        {name: 'modified',    mapping: 'propstat/prop/getlastmodified',  type: 'date'},
        {name: 'modifiedBy',  mapping: 'propstat/prop/modifiedby'},
        {name: 'size',        mapping: 'propstat/prop/getcontentlength', type: 'int'},
        {name: 'iconHref'},
        {name: 'contentType', mapping: 'propstat/prop/getcontenttype'},
        {name: 'options'}
    ]
});

Ext4.define('File.data.webdav.JSONReponse', {

    extend : 'Ext.data.Model',

    fields : [
        {name : 'creationdate', type : 'date'},
        {name : 'contentlength', type : 'int'},
        {name : 'collection', type : 'boolean'},
        {name : 'contenttype'},
        {name : 'etag'},
        {name : 'href'},
        {name : 'id'},
        {name : 'lastmodified', type : 'date'},
        {name : 'leaf', type : 'boolean'},
        {name : 'size', type : 'int'},
        {name : 'name', mapping : 'text'},
        {name : 'icon', mapping : 'iconHref'}
    ]
});