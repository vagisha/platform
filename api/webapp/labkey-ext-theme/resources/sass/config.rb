# $ext_path: This should be the path of the Ext JS SDK relative to this file
$ext_path = "../../../ext-4.1.0"

# This is the path relative to the webapp directory. In the standard case it will be
# the same as $ext_path. If a theme is defined in a separate labkey module than
# it should be set to "../../../ext-<most-recent-version>"
$labkey_ext_path = $ext_path

# sass_path: the directory your Sass files are in. THIS file should also be in the Sass folder
# Generally this will be in a resources/sass folder
# <root>/resources/sass
sass_path = File.dirname(__FILE__)

# css_path: the directory you want your CSS files to be.
# Generally this is a folder in the parent directory of your Sass files
# <root>/resources/css
css_path = File.join(sass_path, "..", "css")

# output_style: The output style for your compiled CSS
# nested, expanded, compact, compressed
# More information can be found here http://sass-lang.com/docs/yardoc/file.SASS_REFERENCE.html#output_style
output_style = :compressed

# We need to load in the Ext4 themes folder, which includes all it's default styling, images, variables and mixins
load File.join(File.dirname(__FILE__), $ext_path, 'resources', 'themes')
