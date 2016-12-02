var express = require('express');
var router = express.Router();
var passport = require('passport');
var google = require('googleapis');
var compute = google.compute('v1');
var waterfall = require('async-waterfall');
var User = require('../models/user');
var WebsockifyHandler = new require('./websockify-handler')();

/* Used to protect routes. */
var isAuthenticated = function (req, res, next) {
  if (req.isAuthenticated())
    return next();
  res.redirect('/');
}

/* GET main page. Only for web fron end. */
router.get('/', function(req, res) {
  res.render('index.ejs');
});

/* GET login page. Only for web front end. */
router.get('/login', function(req, res) {
  res.render('login.ejs', { message: req.flash('loginMessage') });
});

/* GET signup page. Only for web front end. */
router.get('/signup', function(req, res) {
  res.render('signup.ejs', { message: req.flash('signupMessage') });
});

/**
  * POST login. Requires in POST body a json object.
  * {username: String, password: String}
  */
router.post('/login',
  passport.authenticate('login', {
    successRedirect: '/validuser', failureRedirect: '/invaliduser', failureFlash : true
  })
);

/**
  * POST signup. Requires in POST body a json object.
  * {username: String, password: String}
  */
router.post('/signup',
  passport.authenticate('signup', {
    successRedirect: '/validuser', failureRedirect: '/invaliduser', failureFlash : true
  })
);

/* User authenticated. signup/login successful. */
router.get('/validuser', isAuthenticated, function(req, res){
  waterfall([
    function(next) {
      google.auth.getApplicationDefault(function(err, authClient) {
        if (err) {
          console.log(err.toString());
          next(err.toString());
        } else {
          next(null, authClient)
        }
      });
    },
    function(authClient, next) {
      if (authClient.createScopedRequired && authClient.createScopedRequired()) {
        var scopes = ['https://www.googleapis.com/auth/cloud-platform'];
        authClient = authClient.createScoped(scopes);
        var request = {
          project: "mcc-2016-g05-p1",
          zone: "europe-west1-c",
          auth: authClient
        };
        console.log('Enumerating available VMs ...');
        compute.instances.list(request, function (err, vms) {
          if (err) {
            console.log(err.errors[0].message);
            next(err.errors[0].message)
          } else {
            availableVMs = [];
            if (vms.items != null) {
              for(var i = 0; i < vms.items.length; i++) {
                if (vms.items[i].name == 'back-end') {
                  continue;
                }
                // use vmname as displayname if vmDisplayName metadata is not available
                var vmDisplayName = vms.items[i].name
                if (vms.items[i].metadata.items != null) {
                  for (var ii = 0; ii < vms.items[i].metadata.items.length; ii++) {
                    if (vms.items[i].metadata.items[ii].key == 'vmDisplayName') {
                      vmDisplayName = vms.items[i].metadata.items[ii].value;
                      break;
                    }
                  }
                }
                availableVMs.push({
                  'vmName': vms.items[i].name,
                  'vmDisplayName': vmDisplayName
                });
              }
            }
            next(null, availableVMs)
          }
        });
      }
    }
  ], function(err, result) {
    if (err) {
      console.log(err);
      res.json({'success': false, 'message': err});
    } else {
      res.json({'success': true, 'availableVMs': result});
    }
    req.logout();
  });
});

/* If user login/signup credentails are not valid. */
router.get('/invaliduser', function(req, res){
  res.json({'success': false});
});

/**
  * GET home page after successful login/signup.
  * Only for web front end where user will be shown list of apps.
  */
router.get('/home', isAuthenticated, function(req, res){
  res.render('home.ejs');
});

router.get('/vnc', isAuthenticated, function(req, res){
  res.render('vnc.ejs');
});

/**
  * Starts an instance. Requires instance name as route parameter.
  */
router.get('/startvm/:instance', isAuthenticated, function(req, res){
  google.auth.getApplicationDefault(function(err, authClient) {
    if (err) {
      console.log('Authentication failed because of ', err);
      res.json({message: err});
    }
    if (authClient.createScopedRequired && authClient.createScopedRequired()) {
      var scopes = ['https://www.googleapis.com/auth/cloud-platform'];
      authClient = authClient.createScoped(scopes);
    }
    var request = {
      project: "mcc-2016-g05-p1",
      zone: "europe-west1-c",
      instance: req.params.instance,
      auth: authClient
    };
    waterfall([
      function(next) {
        console.log('Starting ' + req.params.instance + ' ...');
        compute.instances.start(request, function(err, result) {
          if (err) {
            next({message: err.errors[0].message});
          } else {
            next(null);
          }
        });
      },
      function(next) {
        console.log('Getting vm status ...');
        var getStatus = function() {
          compute.instances.get(request, function(err, result) {
            if (err) {
              next({message: err.errors[0].message});
            } else {
              if (result.status === 'RUNNING') {  //result.status could be one of RUNNING, PENDING, DONE
                var instanceIP = result.networkInterfaces[0].accessConfigs[0].natIP;
                console.log(req.params.instance + ' is running on ' + instanceIP + ' ...');

                // Launch websockify proxy to enable connecting from a browser
                WebsockifyHandler.createProxy(instanceIP);

                next(null, {'externalIP': instanceIP});
              } else {
                getStatus();
              }
            }
          });
        }
        getStatus();
      }
    ], function(err, result) {
      if (err) {
        res.json(err);
      } else {
        res.json(result); //result.externalIP contains the IP to be used to connect to remote desktop
      }
    });
  });
});

/**
  * Stops an instance. Requires instance name as route parameter.
  */
router.get('/stopvm/:instance', isAuthenticated, function(req, res){
  WebsockifyHandler.killProxies();
  google.auth.getApplicationDefault(function(err, authClient) {
    if (err) {
      console.log('Authentication failed because of ', err);
      res.json({message: err});
    }
    if (authClient.createScopedRequired && authClient.createScopedRequired()) {
      var scopes = ['https://www.googleapis.com/auth/cloud-platform'];
      authClient = authClient.createScoped(scopes);
    }
    var request = {
      project: "mcc-2016-g05-p1",
      zone: "europe-west1-c",
      instance: req.params.instance,
      auth: authClient
    };
    console.log('Stopping ' + req.params.instance + ' ...');
    compute.instances.stop(request, function(err, result) {
      if (err) {
        res.json({message: err.errors[0].message});
      } else {
        res.json({staus: result.status}); //result.status could be one of RUNNING, PENDING, DONE, In this case PENDING.
      }
    });
  });
});

/* GET logout page. */
router.get('/logout', isAuthenticated, function(req, res) {
  waterfall([
    function(next) {
      google.auth.getApplicationDefault(function(err, authClient) {
        if (err) {
          console.log(err.toString());
          next(err.toString());
        } else {
          next(null, authClient)
        }
      });
    },
    function(authClient, next) {
      if (authClient.createScopedRequired && authClient.createScopedRequired()) {
        var scopes = ['https://www.googleapis.com/auth/cloud-platform'];
        authClient = authClient.createScoped(scopes);
        var request = {
          project: "mcc-2016-g05-p1",
          zone: "europe-west1-c",
          auth: authClient
        };
        console.log('Enumerating available VMs ...');
        compute.instances.list(request, function (err, vms) {
          if (err) {
            console.log(err.errors[0].message);
            next(err.errors[0].message)
          } else {
            if (vms.items != null) {
              for(var i = 0; i < vms.items.length; i++) {
                if (vms.items[i].name == 'back-end') {
                  continue;
                }
                request.instance = vms.items[i].name;
                console.log('Sending stop instruction to ' + vms.items[i].name);
                compute.instances.stop(request, function(err, result) {
                  if (err) {
                    console.log(err.errors[0].message);
                  }
                });
              }
            }
          }
        });
      }
      next(null);
    }
  ], function(err, result) {
    req.logout();
    if (err) {
      console.log(err);
      res.json({'success': false, 'message': err});
    } else {
      res.json({'success': true});
    }
  });
});

module.exports = router;
