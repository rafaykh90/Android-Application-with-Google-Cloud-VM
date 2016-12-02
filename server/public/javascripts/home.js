$(document).ready(function() {
  var currentLat, currentLong, areAtCS;

  // Get applications and order them
  orderApps();

  // Bind click handler for logout button
  $('#logout-button').click(function() {
    if ($(this).is("[disabled]")) {
      event.preventDefault();
      return;
    }
    else {
      // First stop the VM's, then logout
      $.get('/stopvm/inkscape')
      .done(function() { $.get('/stopvm/openoffice')
        .done(function() { $.get('/logout')
          .done(function(response) {
            if (response.success) window.location.replace('/');
          });
        });
      });
    }
  });

  // Code modified from:
  // https://facebook.github.io/react-native/docs/geolocation.html
  navigator.geolocation.getCurrentPosition(
    function(position) {
      currentLat = position.coords.latitude;
      currentLong = position.coords.longitude;
    },
    function(error) {
      $('#error-container').css('display', 'block');
      $('#error-container').html('Geolocation disabled!');
    },
    {enableHighAccuracy: true, timeout: 20000, maximumAge: 1000}
  );

  navigator.geolocation.watchPosition(function(position) {
    currentLat = position.coords.latitude;
    currentLong = position.coords.longitude;
    $('#error-container').css('display', 'none');
    orderApps();
  });

  function orderApps() {
    startLoading();

    $.get('/validuser', function(response) {
      apps = response.availableVMs;

      // Check if we have openoffice
      var foundOpenOffice = -1;
      for(var i = 0; i < apps.length; i++) {
        if (apps[i].vmName == 'openoffice') {
          foundOpenOffice = i;
          break;
        }
      }
      $('#app-list-container').empty();
      if (checkIfAtCSBuilding() && foundOpenOffice > -1) {
        // Add OpenOffice to UI and remove from list
        $('#app-list-container').append(
          '<a class="list-group-item list-group-item-info list-group-item-action" vmName=' +
          apps[foundOpenOffice].vmName + '>' + apps[foundOpenOffice].vmDisplayName+'</a>');
        apps.splice(foundOpenOffice, 1);
      }

      // Add all to UI
      apps.forEach(function(app) {
        $('#app-list-container').append(
          '<a class="list-group-item list-group-item-info list-group-item-action" vmName=' +
          app.vmName + '>' + app.vmDisplayName+'</a>');
      });

      // Bind click handler
      $("#app-list-container > a").on("click", function(event){
        if ($(this).is("[disabled]")) {
          event.preventDefault();
          return;
        }
        else {
          var appName = $(event.target).attr('vmname')
          startLoading();
          $.get('/startvm/' + appName, function(response) {
            // Here we connect to the Websockify proxy
            window.location.replace('/vnc?port=6080&app=' + appName);
          });
        }
      });
    });
    stopLoading();
  }

  function startLoading() {
    $('#home-loading').show();
    $('#app-list-container > a').css('color', '#717171');
    $('#app-list-container > a').css('background-color', '#26547b');
    $("#app-list-container > a").attr("disabled", "disabled");
    $('#logout-button').attr("disabled", "disabled");
  }

  function stopLoading() {
    $('#home-loading').hide();
    $('#app-list-container > a').css('background-color', '');
    $('#app-list-container > a').css('color', 'black');
    $('#app-list-container > a').removeAttr("disabled");
    $('#logout-button').removeAttr("disabled");
  }

  function checkIfAtCSBuilding() {
    var accuracy = 0.0005;
    var diffLat = Math.abs(currentLat - 60.186874);
    var diffLong = Math.abs(currentLong - 24.822188);

    if (diffLat <= accuracy && diffLong <= accuracy && !areAtCS) {
      $('#info-container').css('display', 'block')
        .delay(4000).slideUp(200);
      areAtCS = true;
      return true;
    }
    $('#info-container').css('display', 'none');
    areAtCS = false;
    return false;
  }
});
