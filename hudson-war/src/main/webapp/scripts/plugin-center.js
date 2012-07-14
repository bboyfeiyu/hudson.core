/* 
    Document   : plugin-center.jss
    Author     : Winston  Prakash <winston.prakash@oracle.com>
    Description:
        Javascript for Plugin Center
 */

jQuery.noConflict();

var filesToUpload;

jQuery(document).ready(function() {
    
    var images = [
    imageRoot + '/green-check.jpg',
    imageRoot + '/progressbar.gif',
    imageRoot + '/error.png'
    ];

    jQuery(images).each(function() {
        jQuery('<img />').attr('src', this);
    });
    
    jQuery("#outerTabs").tabs();
    jQuery("#innerTabs").tabs();
    
    jQuery("#outerTabs tr").hover(
        function () {
            jQuery(this).css("background","#EEEDED");
        }, 
        function () {
            jQuery(this).css("background","");
        }
        );
    
    var installButton = jQuery('#installButton');
    installButton.button();
    installButton.click(function() {
        jQuery("#pluginInstallMsg").hide()
        var pluginsToInstall = getPluginsToInstall();
        jQuery(pluginsToInstall).each(function(){
            installPlugin(this);
        });
    });
     
    var updateButton = jQuery('#updateButton');
    updateButton.button();
    updateButton.click(function() {
        jQuery("#pluginUpdateMsg").hide();
        var pluginsToUpdate = getPluginsToUpdate();
        jQuery(pluginsToUpdate).each(function(){
            installPlugin(this);
        });
    });

    jQuery('#installedTab button').each(function(){
        jQuery(this).button();
        jQuery(this).click(function(){
            var selected = this;
            var buttonText = jQuery(selected).children('span').text();
            var title = "Disable Plugin?";
            if (buttonText == "Enable"){
                title = "Enable Plugin?";
                jQuery('#confirmMsg').text("Do you want to enable Plugin - " + jQuery(selected).val() + " ?");
            }else{
                title = "Disable Plugin?";
                jQuery('#confirmMsg').text("Do you want to disable Plugin - " + jQuery(selected).val() + " ?");
            }
            jQuery('#dialog-confirm').dialog({
                resizable: false,
                height:150,
                width: 350,
                modal: true,
                title: title,
                buttons: {
                    'Yes': function() {
                        jQuery( this ).dialog( "close" );
                        enablePlugin(selected);
                    },
                    Cancel: function() {
                        jQuery( this ).dialog( "close" );
                    }
                }
            });
        });
            
    });
    
    var fileSelect = jQuery('#fileSelect');
    fileSelect.change(function(e){
        filesToUpload = e.target.files;
    });
    
    var uploadButton = jQuery('#uploadButton');
    uploadButton.button();
    uploadButton.click(function() {
        for (var i = 0, f; f = filesToUpload[i]; i++) {
            uploadFile(f);
        }
    });
    
    var progressbar = jQuery('#progressbar');
    
    progressbar.progressbar({
        value: 0
    });
    progressbar.height(5);
    
    jQuery('#proxyUser').show();
    jQuery('#proxyPassword').show();
            
    jQuery('#proxyAuth').click(function() {
        if (jQuery('#proxyAuth').is(':checked')){
            jQuery('#proxyUser').show();
            jQuery('#proxyPassword').show();
        }else{
            jQuery('#proxyUser').hide();
            jQuery('#proxyPassword').hide();
        }
    });
        
    var proxySubmitButton = jQuery('#proxySubmitButton');
    proxySubmitButton.button();
    proxySubmitButton.click(function() {
        submitPoxyForm();
    });
    
    var configureUpdateSiteButton = jQuery('#configureUpdateSiteButton');
    configureUpdateSiteButton.button();
    configureUpdateSiteButton.click(function() {
        configureUpdateSite();
    });
    
    var refreshUpdatesButton = jQuery('#refreshUpdatesButton');
    refreshUpdatesButton.button();
    refreshUpdatesButton.click(function() {
        refreshUpdateCenter();
    });
    
     
    jQuery('.category-head').click(function() {
        jQuery(this).next().toggle();
        return false;
    }).not(':first').next().hide();
    
    
    var pluginSearchButton = jQuery('#pluginSearchButton');
    pluginSearchButton.button();
    pluginSearchButton.click(function() {
        jQuery("#searchPlugins").append("<p>Searching ..</p>")
        var searchStr = jQuery("#pluginSearchText").val();
        var searchDescription = jQuery("#searchDesc").is(':checked');
        jQuery("#searchContents").load('searchPlugins', 
        {
            'searchStr' : searchStr, 
            'searchDescription' : searchDescription
        }
        );
    });
             
});

function getPluginsToInstall(){
    var installables = [];
    jQuery('#availableTab .items-container input[@type=checkbox]:checked').each(function(){
        installables.push(this);
    });
    return installables;
}

function getPluginsToUpdate(){
    var updates = [];
    jQuery('#updatesTab .items-container input[@type=checkbox]:checked').each(function(){
        updates.push(this);
    });
    return updates;
}

function getPluginsToDisable(){
    var updates = [];
    jQuery('#installedTab input[@type=checkbox]:checked').each(function(){
        updates.push(this);
    });
    return updates;
}

function installPlugin(selected){
    jQuery(".install_img_" + jQuery(selected).val()).each(function(){
        jQuery(this).show();
        jQuery(this).attr('src', imageRoot + '/progressbar.gif');
    });
    
    jQuery(".install_cb_" + jQuery(selected).val()).each(function(){
        jQuery(this).hide();
    });
     
    jQuery.ajax({
        type: 'POST',
        url: "installPlugin",
        data: {
            pluginName:jQuery(selected).val()
        },
        success: function(){
            jQuery(".install_img_" + jQuery(selected).val()).each(function(){
                jQuery(this).show();
                jQuery(this).attr('src', imageRoot + '/green-check.jpg');
            });
            jQuery(selected).attr("checked", false);
            jQuery('#restart-message').show();
        },
        error: function(){
            jQuery(".install_img_" + jQuery(selected).val()).each(function(){
                jQuery(this).show();
                jQuery(this).attr('src', imageRoot + '/error.png');
            });
            showMessage(jQuery("#pluginInstallMsg"), msg.responseText, true);
        },
        statusCode: {
            403: function() {
                showLoginDialog();
            }
        },
        dataType: "html"
    }); 
}

function updatePlugin(selected){
    jQuery(".update_img_" + jQuery(selected).val()).each(function(){
        jQuery(this).show();
        jQuery(this).attr('src', imageRoot + '/progressbar.gif');
    });
    jQuery(".update_cb_" + jQuery(selected).val()).each(function(){
        jQuery(this).hide();
    });
    jQuery.ajax({
        type: 'POST',
        url: "updatePlugin",
        data: {
            pluginName:jQuery(selected).val()
        },
        success: function(){
            jQuery(".update_img_" + jQuery(selected).val()).each(function(){
                jQuery(this).show();
                jQuery(this).attr('src', imageRoot + '/green-check.jpg');
            });
            jQuery(selected).attr("checked", false);
            jQuery('#restart-message').show();
        },
        error: function(){
            jQuery(".update_img_" + jQuery(selected).val()).each(function(){
                jQuery(this).show();
                jQuery(this).attr('src', imageRoot + '/error.png');
            });
            showMessage(jQuery("#pluginUpdateMsg"), msg.responseText, true);
        },
        statusCode: {
            403: function() {
                showLoginDialog();
            }
        },
        dataType: "html"
    }); 
}

function enablePlugin(selected){
    var enable = false;
    if (jQuery(selected).text() == "Enable"){
        enable = true;
    }
    
    jQuery(".installed_img_" + jQuery(selected).val()).each(function(){
        jQuery(this).show();
        jQuery(this).attr('src', imageRoot + '/progressbar.gif');
    });
    
    jQuery(".installed_cb_" + jQuery(selected).val()).each(function(){
        jQuery(this).hide();
    });
    
    jQuery.ajax({
        type: 'POST',
        url: "enablePlugin",
        data: {
            pluginName:jQuery(selected).val(),
            enable: enable
        },
        success: function(){
            if (enable){
                jQuery(".installed_img_" + jQuery(selected).val()).each(function(){
                    jQuery(this).show();
                    jQuery(this).attr('src', imageRoot + '/green-check.jpg');
                });
                jQuery(selected).children('span').text('Disable');
            }else{
                jQuery(".update_img_" + jQuery(selected).val()).each(function(){
                    jQuery(this).hide();
                });
                jQuery(selected).children('span').text('Enable');
            }
            jQuery('#restart-message').show();
        },
        error: function(msg){
            jQuery(".installed_img_" + jQuery(selected).val()).each(function(){
                jQuery(this).show();
                jQuery(this).attr('src', imageRoot + '/error.png');
            });
            showMessage(jQuery("#pluginDisableMsg"), msg.responseText, true);
        },
        statusCode: {
            403: function() {
                showLoginDialog();
            }
        },
        dataType: "html"
    }); 
}

function uploadFile(file) {
    jQuery("#pluginUploadMsg").hide();
    var xhr = new XMLHttpRequest();
    if (xhr.upload) {

        jQuery("#progressbar").show();
        xhr.upload.addEventListener("progress", function(e) {
            var pc = parseInt((e.loaded / e.total) * 100);
            jQuery("#progressbar").progressbar("value", pc);
        }, false);

        // file received/failed
        xhr.onreadystatechange = function(e) {
            if (xhr.readyState == 4) {
                jQuery("#progressbar").hide();
                if (xhr.status == 200){
                    showMessage(jQuery("#pluginUploadMsg"), "Plugin " + file.name + " sucessfully uploaded.");
                    jQuery('#restart-message').show();
                }else{
                    showMessage(jQuery("#pluginUploadMsg"), xhr.responseText, true);
                }
            }
        };

        // start upload
        xhr.open("POST", "uploadPlugin", true);
        var formData = new FormData();
        formData.append("file", file);
        xhr.send(formData);
    }
}

function showMessage(infoTxt, msg, error){
    infoTxt.text(msg);
    if (error == true){
        infoTxt.css("color","red");
    }else{
        infoTxt.css("color","green");  
    }
    infoTxt.show();
}

function submitPoxyForm(){
    forProxy = false;
    jQuery('#proxySuccess').hide();
    jQuery('#proxyError').hide();
    var dataString = jQuery("#proxyForm").serialize();
    jQuery.ajax({
        type: 'POST',
        url: "proxyConfigure",
        data: dataString,
        success: function(){
            var msg = 'Hudson server could successfully connect to the internet';
            showMessage(jQuery("#proxyMsg"), msg);
        },
        error: function(){
            var msg = 'Hudson server still could not connect to the internet. Check the HTTP proxy settings and try again.';
            showMessage(jQuery("#proxyMsg"), msg, true);
        },
        statusCode: {
            403: function() {
                forProxy = true;
                showLoginDialog();
            }
        },
        dataType: "html"
    }); 
}

function configureUpdateSite(){
    jQuery("#configureUpdateSiteMsg").show();
    jQuery("#configureUpdateSiteMsg").text("Configuring ..");
    var dataString = jQuery("#configureUpdateSiteForm").serialize();
    jQuery.ajax({
        type: 'POST',
        url: "configureUpdateSite",
        data: dataString,
        success: function(){
            var msg = 'Update Site Successfully Configured.';
            showMessage(jQuery("#configureUpdateSiteMsg"), msg);
        },
        error: function(msg){
            //var msg = 'Udate Site could note be Configured. Check the HTTP proxy settings and try again.';
            showMessage(jQuery("#configureUpdateSiteMsg"), msg.responseText, true);
        },
        statusCode: {
            403: function() {
                forProxy = true;
                showLoginDialog();
            }
        },
        dataType: "html"
    }); 
}

function refreshUpdateCenter(){
    jQuery("#updateRefreshMsg").show();
    jQuery("#updateRefreshMsg").text("Refreshing ..");
    jQuery.ajax({
        type: 'POST',
        url: "refreshUpdateCenter",
        success: function(){
            window.location.href=".";
        },
        error: function(msg){
            //var msg = 'Failed to refresh updates. Check the HTTP proxy settings and try again.';
            showMessage(jQuery("#updateRefreshMsg"), msg.responseText, true);
        },
        statusCode: {
            403: function() {
                showLoginDialog();
            }
        }
    }); 
}



 



