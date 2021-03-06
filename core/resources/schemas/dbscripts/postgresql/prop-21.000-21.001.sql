-- Rename 'MobileAppStudy' module to 'Response'
UPDATE prop.propertysets SET category = 'moduleProperties.Response' WHERE category = 'moduleProperties.MobileAppStudy';
UPDATE prop.properties props SET name = 'Response'
    FROM prop.propertysets sets
    WHERE props.set = sets.set AND props.name = 'MobileAppStudy' AND sets.category = 'activeModules';
UPDATE prop.properties props SET value = 'MyStudies Response'
    FROM prop.propertysets sets
    WHERE props.set = sets.set AND props.name = 'name' AND props.value = 'Mobile App Study' AND sets.category = 'folderType';
