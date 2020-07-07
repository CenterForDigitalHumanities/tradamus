/*
 * @ngdoc service
 * @name tradamus.UserService
 * @description Handles the <User> login, registration, and logging activities.
 * @requires $rootScope, $q, $http, User, authService
 */
tradamus.service('UserService', function ($rootScope, $q, $http, $location, Display, $window, User, authService) {
    var service = this;
    /*
     * Get the currently logged in user id.
     * @returns {Integer} User.id
     */
    this.active = function () {
        return User.id;
    };
    /*
     * Gets User or fetches if not available.
     * @requires {User}
     * @returns {promise} resolves to User or error
     *
     * FIXME:  Runs infinitely when user is not logged in.  Causes problems on the publication page.
     */
    this.get = function () {
        var deferred = $q.defer();
        var service = this;
        if (User.lastLogin) {
            deferred.resolve(User);
        }
        //else if (User.id > 0) {
        else if (User.id !== false) { //allows for 0 to be valid, which is the public user
            return (service.fetch());
        } else {
            var locationURL = $location.absUrl();
                checkForSession().then(function (user) {
                    return service.get();
                })
                .then(function () {
                    deferred.resolve(User);
                },
                function (err, status) {
                    console.log(status + ": Cannot GET User, no one is logged in");
                    // 401 expected often
                    deferred.reject(err);
                });
        }
        return deferred.promise;
    };
    /*
     * Extends User with new properties.
     * @requires {User}
     * @param {Object} key-value set of properties to be extended
     * @returns {User} modified User
     */
    this.set = function (props) {
        if (!angular.isObject(User))
            User = {id: User};
        angular.extend(User, props);
        return User;
    };
    /*
     * Fetches User from server.
     * @requires {User}
     * @requires {$q}
     * @requires {$http}
     * @returns {promise} resolves to User or error
     */
    this.fetch = function () {
        var deferred = $q.defer();
        if (User.id !== false) {
            $http.get("user/" + User.id)
                .success(function (user) {
                    service.set(user);
                    console.log('loaded new user');
                    deferred.resolve(User);
                })
                .error(function (error) {
                    // http error
                    if (error.status === 401) {
                        // login should show automagically
                    } else if (error.status === 404) {
                        console.log(uid + ' is not a user id');
                    } else {
                        console.log("Error Unknown", error);
                    }
                    deferred.reject(error);
                });

        } else {
            // uid error
            console.log("Invalid user ID: " + User.id);
            deferred.reject("Invalid user ID: " + User.id);
        }
        return deferred.promise;
    };
    /*
     * Updates User information.
     * @requires {User}
     * @requires {$http}
     * @param {Object} User information like "mail", "name", or "password"
     * @returns {promise} AJAX success or failure only
     */
    this.update = function (tosend) {
        return $http.put("user/" + User.id, tosend)
            .success(function () {
                if (tosend.password) {
                    service.logout();
                    alert('Please log back in with your new password.');
                }
                return User;
            })
            .error(function () {
                alert('Today is not our day. Nothing has been changed');
                console.log('Failed to update user');
                // TODO build out errors when API exists.
                return false;
            });
    };
    /*
     * Request a password reset. Sends an email to User.mail
     * @param {string} mail User.mail
     * @requires {$http}
     * @returns {promise} resolves to AJAX success or failure
     */
    this.resetPassword = function (mail) {
        return $http.put('user?reset=' + mail)
            .success(function () {
                alert('Please check your e-mail for instructions.');
            }).error(function (err) {
                if(err.status==="404"){
                    alert('No user with that address. \nPlease check the e-mail field and try again.');
                    return err;
                }
            alert('Reset is not working as expected. \nWe are sorry we cannot resolve it at this time.');
        });
    };
    /*
     * Request a new confirmation. Sends an email to User.mail
     * @param {string} mail User.mail
     * @requires {$http}
     * @returns {promise} resolves to AJAX success or failure
     */
    this.resendEmail = function (mail) {
        return $http.put('user?resend=' + mail)
            .success(function () {
                Display.authMessage = {
                    type: "success",
                    msg: 'Please check your e-mail for another copy of the confirmation.'
                }
            }).error(function (err, status) {
            if (status === 404) {
                Display.authMessage = {
                    title: "404: Not Found",
                    type: "warning",
                    msg: 'User account was not found. Please register again.'
                };
            } else if (status === 410) {
                Display.authMessage = {
                    type: "warning",
                    msg: 'This account is already activated. If you have trouble logging in, you may need to reset your password.'
                };
            } else {
                Display.authMessage = {
                    title: "Unknown Error:" + status,
                    type: "warning",
                    msg: 'Resend is not working as expected. \nWe are sorry we cannot resolve it at this time.' + err
                };
            }
        });
    };
    /*
     * Load activity for User to User.activities
     * @requires {$http}
     * @requires {$rootScope}
     * @returns {promise} resolves to set User.activities or failure
     */
    this.getActivityLog = function (limit) {
        $rootScope.$broadcast('wait-load', {for : 'userActivity', message: 'Looking for your activity...'});
        var url = "activity?" + ((limit && "limit=15&") || "") +
//              "table="+table+
            "user=" + User.id;
        return $http.get(url)
            .success(function (entries) {
                service.set({"activity": entries});
                $rootScope.$broadcast('resume', {for : 'userActivity'});
            })
            .error(function () {
                $rootScope.$broadcast('resume', {for : 'userActivity'});
                console.log("Failed to retrieve activities");
            });
    };
    /*
     * Load User's Editions to User.editions. Editions have "title","id"
     * @requires {$http}
     * @requires {$rootScope}
     * @returns {promise} resolves to set User.editions or failure
     */
    this.getEditions = function () {
        $rootScope.$broadcast('wait-load', {for : 'userEditions', message: 'Looking for your editions...'});
        return $http.get("editions")
            .success(function (editions) {
                service.set({"editions": editions});
                $rootScope.$broadcast('resume', {for : 'userEditions'});
            })
            .error(function (error) {
                console.log('Failed to fetch editions', error);
                $rootScope.$broadcast('resume');
            });
    };
    /*
     * Load User's simple details such as "name","mail" for sharing.
     * @param {integer} uid User id
     * @requires {$http}
     * @returns {promise} resolves to User or error
     */
    this.fetchUserDetails = function (uid, noCache) {
        return $http.get("user/" + uid, {cache: !noCache})
            .success(function (data) {
                return data;
            })
            .error(function (error, status, headers) {
                // http error
                if (status === 401) {
                    // login should show automagically
                } else if (status === 404) {
                    console.log(uid + ' is not a known user id');
                } else {
                    console.log("Error Unknown", status, error);
                }
            });
    };
    /*
     * Checks for login and grabs data if found. 403 Error will prompt login UI.
     * @requires {$http}
     * @returns {promise} AJAX resolves to get User, activity, and editions or failure
     */
    var checkForSession = function () {
        return $http.get('login', {'ignoreAuthModule': true})
            .success(function (user) {
                if (!user || user.id === false) {
                    user = {// Makes public User if none
                        id: 0,
                        activity: [],
                        creation: "",
                        editions: [],
                        lastLogin: "",
                        name: "Public User"
                    };
                } else {
                service.set(user);
                    service.getEditions();
                }
                return User;
            })
            .error(function (err) {
                return err;
            });
    };
    /*
     * Login User with password and mail.
     * @requires {$http}
     * @requires {$rootScope}
     * @returns {promise} AJAX resolves to set User.id or failure
     */
    this.login = function (m, p) {
        if (!m) {
            m = User.mail;
            p = User.password;
        }
        $rootScope.$broadcast('wait', 'Waiting for login...');
        return $http.post('login', {mail: m, password: p}, {ignoreAuthModule: true})
            .success(function (data, status, headers) {
                var loc = headers('Location');
                console.log('Logged in ', loc);
                authService.loginConfirmed();
                service.set({id: parseInt(loc.substring(loc.lastIndexOf('/') + 1))});
                $window.location.reload();
            })
            .error(function (err, status) {
                var fmsg = failMsg(err);
                if (status === 401) {
                    var pwdMask = (function (p) {
                        var m = [];
                        for (var i = 0; i < p.length; i++) {
                            m.push("*");
                        }
                        return m.join("");
                    })(p);
                    Display.authMessage = {
                        title: status,
                        msg: fmsg + "\n\nYou entered " + User.mail + ": " + pwdMask + " (hidden)",
                        type: "warning"
                    };
                } else {
                    Display.authMessage = {
                        title: status,
                        msg: fmsg + "\n\nYou entered " + User.mail + ": " + pwdMask + " (hidden)",
                        type: "warning"
                    };
                }
                return err;
            }).finally(function () {
            $rootScope.$broadcast('resume');
        });
    };
    /*
     * Signup new user with mail, name, and password
     * @requires {$http}
     * @requires {$rootScope}
     * @returns {promise} AJAX success or failure
     */
    this.signup = function (u, m, p) {
        if (!u) {
            u = User.name;
            m = User.mail;
            p = User.password;
        }
        $rootScope.$broadcast('wait', 'Waiting for account creation...');
        return $http.post('users', {mail: m, name: u, password: p})
            .then(function (data) {
                Display.authMessage = {
                    title: "Account created",
                    msg: "Please check " + m + " for a link to confirm your account.",
                    type: "success"
                };
                User.password = ""; // redacted
                return data.data;
            },
                function (err, status) {
                    var pwdMask = (function (p) {
                        var m = [];
                        for (var i = 0; i < p.length; i++) {
                            m.push("*");
                        }
                        return m.join("");
                    })(User.password);
                    Display.authMessage = {
                        title: status,
                        msg: failMsg(err)
                            + "\n\nSignup was unsuccessful. Please check your username and password and try again.\n\nYou entered: \""
                            + u + "\" (" + m + "):" + pwdMask + " (hidden)",
                        type: "warning"
                    };
                    return err;
                }).finally(function () {
            $rootScope.$broadcast('resume');
        });
    };
    /*
     * Present user with failure message for login troubles.
     * @param {Error} error reason for failure.
     * @returns {string} message to display
     */
    var failMsg = function (error) {
        var msg = "An unexpected error occurred. Please refresh and retry. If this error persists, please contact us.";
        possibleMsg = [
            {term: "Duplicate entry", reply: "An account with that email already exists."},
            {term: "NullPointerException", reply: "It looks like you have left a field blank."},
            {term: "Not logged in", reply: "Your username or password was invalid."}
        ];
        for (var i = 0; i < possibleMsg.length; i++) {
            if (error.indexOf(possibleMsg[i].term) > -1) {
                msg = possibleMsg[i].reply;
                break;
            }
        }
        return msg;
    };
    var reset = function () {
        User = {
            name: "",
            mail: "",
            password: "",
            activity: [],
            id: -1
        };
    };
    /*
     * Destroy User session locally and on server.
     * @requires {$http}
     * @requires {$rootScope}
     * @returns {promise} AJAX success or failure
     */
    this.logout = function () {
        $rootScope.$broadcast('wait', 'Logging Out...');
        return $http.post('login')
            .success(function (data, status, headers) {
                reset();
                $rootScope.$broadcast('resume');
                $rootScope.$broadcast('logout', User);
                $location.path("/");
                return User;
            })
            .error(function (err) {
                $rootScope.$broadcast('resume');
                return err;
            });
    };
});
tradamus.service('Messages', function () {
    this.set = function (name, message, trace) {
        this[name] = {
            message: message,
            trace: trace
        };
    };
});
tradamus.service('WitnessImportService', function ($http, $rootScope, User, Messages) {
    var partner = [{name: 'tpen', location: 'www.t-pen.org/TPEN/projects'}];
    var service = this;
    this.getLink = function (index) {
        return partner[index].location;
    };
    this.list = [];
    this.fetchRemote = function () {
        Messages.set("import", "Projects loading...");
        $rootScope.$broadcast('wait-load', "Projects loading...");
        return $http.get(partner[0].name + '/projects?user=' + User.mail)
            .success(function (witnesses) {
                service.list = witnesses;
                Messages.set("import", "Select projects below to import into this Edition as Witnesses.");
                $rootScope.$broadcast('resume');
                return witnesses;
            })
            .error(function (error, status) {
                if (status === 403) {
                    Messages.set("import", "Login to " + partner[0].name + " failed. Try authenticating at that" +
                        " site in another tab before retrying.");
                } else {
                    Messages.set("import", status + " Error: unable to import project list from " + partner[0].location);
                }
                $rootScope.$broadcast('resume');
                console.log(error);
            });
    };
    this.fetchRemoteDetails = function (wID) {
        if (wID > 0) {
            $rootScope.$broadcast('wait-load');
            return $http.get(partner[0].name + '/project/' + wID + '?user=' + User.mail)
                .success(function (witness) {
                    $rootScope.$broadcast('resume');
                    return witness;
                })
                .error(function (error) {
                    alert('Unable to load remote witness at: ' + partner[0].name + '/project/' + wID + '?user=' + User.mail);
                    console.log('Failed to connect to ' + partner[0].name + wID, error);
                    $rootScope.$broadcast('resume');
                });
        } else {
            alert('bad projectID: failed import ' + wID);
            return false;
        }
    };
});
tradamus.service('_cache', function () {
    this.get = function (key) {
        return this[key];
    };
    this.store = function (key, value) {
        return this[key] = value;
    };
    this.drop = function (key) {
        if (this[key]) {
            delete this[key];
            return true;
        }
        return false;
    };
    this.find = function (value) {
        for (prop in this) {
            if (this[prop] === value) {
                return this[prop];
            }
        }
        return false;
    };
    this.findAll = function (value) {
        var res = [];
        angular.forEach(this, function (v, k) {
            if (value === v) {
                res.push({k: v});
            }
        });
        return res;
    };
});
tradamus.service('WitnessService', function ($q, $http, $rootScope, Lists, Witness, Materials, Annotations, Edition, TranscriptionService, PageService, CanvasService, ManifestService, TagService) {
    var service = this;
    this.get = function (witness, noAJAX) {
        var wid = witness.id || witness;
        var w = service.getById(wid);
        if (noAJAX) {
            return w
        }
        ;
        if (!w.transcription) {
            return service.fetch(wid);
        } else {
            return $q.when(w);
        }
    };
    this.getById = function (wid) {
        var w = parseInt(wid);
        if (w > 0) {
            return getWitnessById(w);
        }
        return false;
    };
    this.getByContainsPage = function (pid, noAJAX) {
        var deferred = $q.defer();
        var len = Edition.witnesses.length;
        for (var i = 0; i < len; i++) {
            var w = Materials["id" + Edition.witnesses[i]] = Materials["id" + Edition.witnesses[i]];
            if (w.transcription && w.transcription.pages) {
                // transcription is loaded
                for (var j = 0; j < w.transcription.pages.length; j++) {
                    if (w.transcription.pages[j].id === pid) {
                        if (noAJAX) {
                            return Materials["id" + Edition.witnesses[i]];
                        }
                        deferred.resolve(Materials["id" + Edition.witnesses[i]]);
                        break;
                    }
                }
            } else {
                // no transcription loaded, determine how deep
                var getThis = (w.transcription) ?
                    service.getTranscription : service.fetch;
                getThis(w).then(function () {
                    // load each and recheck
                    service.getByContainsPage(pid).then(function (witness) {
                        if (witness) {
                            deferred.resolve(witness);
                        }
                    });
                });
            }
        }
        return deferred.promise;
    };
    this.remove = function (wid) {
        var wIndex = getWitnessIndexById(wid);
        Edition.witnesses.splice(wIndex, 1);
        console.log('Removed, but no http call yet');
    };
    var getWitnessById = function (wid) {
        var w = getWitnessIndexById(wid);
        if (w > -1) {
            w = Edition.witnesses[w];
        }
        return w;
    };

    var getWitnessIndexById = function (wid) {
        var index = -1;
        var w = Edition.witnesses;
        for (var i = 0; i < w.length; i++) {
            if (w[i].id === wid) {
                index = i;
                break;
            }
        }
        ;
        return index;
    };
    var put = function (witness) {
        $rootScope.$broadcast('wait', 'Waiting for Witness creation...');
        var config = {};
        if (witness["@context"]) {
            config.headers = {
                'Content-Type': 'application/ld+json;charset=UTF-8' // override default json
            };
        }
        return $http.post("witnesses?edition=" + Edition.id, witness, config)
            .success(function (data, status, headers) {
                console.log('saved ' + witness.title || witness.label + ' to edition');
                var loc = headers('Location');
                witness.id = parseInt(loc.substring(loc.lastIndexOf('/') + 1));
                Edition.witnesses.push(witness);
                $rootScope.$broadcast('resume');
            })
            .error(function (error) {
                console.log('failed to save ' + witness.title || witness.label + ' to edition', error);
                $rootScope.$broadcast('resume');
            });
    };

    this.add = function (witness) {
        var deferred = $q.defer();
        var thisWit = getWitnessById(witness.id);
        if (angular.isObject(thisWit)) {
            // update existing witness
            deferred.resolve(this.set(thisWit, witness));
        } else {
            // create new witness
            witness = angular.extend(angular.copy(Witness), witness);
            put(witness).then(function () {
                deferred.resolve(witness);
            });
        }
        return deferred.promise;
    };

    this.set = function (witness, props) {
        if (!angular.isObject(witness))
            witness = {id: witness};
        angular.extend(witness, props);
        return witness;
    };

    this.fetch = function (witness, andAnnos, andTranscription, andManifest) {
        var wid = (witness.id) ? witness.id : witness; // witness may be {witness} or just INT
        var deferred = $q.defer();
        $http.get("witness/" + wid)
            .then(function (wit) {
                Materials["id" + wid] = witness;
                return witness;
            }, function (error) {
                // TODO handle 404, 401, 500
                console.log('Failed to get witness details', error);
            })
            .then(function () {
                return $q.all([
                    service.getTranscription(witness, true),
//                    service.getCanvases(witness, 0, true),
                    service.getAnnotations(witness)
                ]);
            }).then(function () {
            deferred.resolve(witness);
        });
        return deferred.promise;
    };

    this.remove = function (witness) {
        if (witness.id) {
            var cfrm = confirm("Delete this witness: " + witness.title + "?");
            if (cfrm) {
                $http.delete("witness/" + witness.id)
                    .success(function () {
                        Edition.witnesses.splice(getWitnessIndexById(witness.id), 1);
                        return true;
                    })
                    .error(function () {
                        console.log('Failed to remove witness');
                        return false;
                    });
            } else {
                return false;
            }
        }
    };
    this.save = function (wid, props) {
        if (wid && props) {
            $http.put("witness/" + wid, props)
                .success(function () {
                    return true;
                }).error(function (err) {
                return err;
            });
        }
    };
    this.getAnnotations = function (w, noCache) {
        var wid = (w.id) ? w.id : w; // w may be {w} or just INT
        return $http.get("witness/" + wid + "/annotations", {cache: !noCache})
            .success(function (annos) {
                Materials["id" + wid] = annos;
                Lists.segregate(Materials["id" + wid].annos, Annotations);
                TagService.addTagsFrom(annos);
            }).error(function (err) {
            console.log("Failed to fetch annotations for witness " + w.title);
        });
    };
    /**
     * Load canvases from manifest.
     * @param {Witness} w Witness whose manifest to use for lookup
     * @param {int} getThisIndex The position of the canvas to get. "all" for complete set.
     * @param {boolean} force Force ajax even when object exists
     * @returns {unresolved} promise then Canvas or [Canvas]
     */
    this.getCanvases = function (w, getThisIndex, force) {
        var deferred = $q.defer();
        if (force || !angular.isObject(w.manifest.canvasses)) {
            ManifestService.get(w).then(function (m) {
                service.set(w, {manifest: m});
                var allCanvasses = [];
                if (getThisIndex === 'all') {
                    angular.forEach(w.manifest.canvasses, function (c) {
                        allCanvasses.push(CanvasService.fetch(c));
                    });
                    $q.all(allCanvasses).then(function (canvases) {
                        // returns array of canvasses
                        var these = [];
                        angular.forEach(canvases, function (c) {
                            these.push(c.data);
                        });
                        service.set(w.manifest.canvasses, these);
                        deferred.resolve(w.manifest.canvasses);
                    }, function (err) {
                        console.log(err);
                        deferred.resolve(w.manifest.canvasses[getThisIndex]);
                    });
                } else {
                    var thisCanvas = Lists.getAllByProp("index", getThisIndex, w.manifest.canvasses)[0];
                    if (thisCanvas) {
                        CanvasService.fetch(thisCanvas).then(function (canvas) {
                            deferred.resolve(CanvasService.set(thisCanvas, canvas.data));
                        });
                    } else {
                        deferred.reject();
                    }
                }
            });
        } else {
            if (getThisIndex === 'all') {
                deferred.resolve(w.manifest.canvasses);
            } else {
                deferred.resolve(w.manifest.canvasses[getThisIndex]);
            }
        }
        return deferred.promise;
    };
    this.getPages = function (w, getThisIndex, force) {
        var deferred = $q.defer();
        if (force || !angular.isObject(w.transcription.pages)) {
            TranscriptionService.get(w).then(function ( ) {
                var allPages = [];
                if (getThisIndex === 'all') {
                    angular.forEach(w.transcription.pages, function (p) {
                        allPages.push(PageService.get(p));
                    });
                } else {
                    allPages.push(PageService.get(w.transcription.pages[getThisIndex]));
                }
                $q.all(allPages).then(function (pages) {
                    // returns array of pages
                    if (getThisIndex === 'all') {
                        w.transcription.pages = sortedByIndex(pages);
                        deferred.resolve(w.transcription.pages);
                    } else {
                        deferred.resolve(w.transcription.pages[getThisIndex]);
                    }
                }, function (err) {
                    console.log(err);
                    deferred.resolve(w.transcription);
                });
            });
        } else {
            deferred.resolve(w.transcription);
        }
        return deferred.promise;
    };
    var sortedByIndex = function (array) {
        var toret = [];
        var leftovers = [];
        if (!angular.isArray(array))
            return [array];
        angular.forEach(array, function (a) {
            if (parseInt(a.index)) {
                toret[parseInt(a.index)] = a;
            } else {
                leftovers.push(a);
            }
        });
        return toret.concat(leftovers);
    };
    this.getTranscription = function (w, force) {
        var deferred = $q.defer();
        if (force || !angular.isObject(w.transcription)) {
            // fetch transcription from id
            TranscriptionService.fetch(w.transcription, true).then(function (transcription) {
                deferred.resolve(service.set(w, {transcription: transcription}));
            });
        } else {
            // has details and full pages
            deferred.resolve(w.transcription);
        }
        return deferred.promise;
    };
    this.firstCanvas = function (w) {
        var deferred = $q.defer();
        if (w.manifest && w.manifest.canvasses && angular.isObject(w.manifest.canvasses[0])) {
            deferred.resolve(Lists.getAllByProp("index", 0, w.manifest.canvasses)[0]);
        } else {
            this.getCanvases(w, 0, true).then(function (canvas) {
                deferred.resolve(canvas);
            });
        }
        return deferred.promise;
    };
    this.firstPage = function (w) {
        if (w.transcription && w.transcription.pages && w.transcription[0]) {
            return w.transcription.pages[0];
        } else {
            // no transcription loaded
            return this.getPages(w, 0, true);
        }
    };
});

tradamus.service('TranscriptionService', function ($http, Materials) {
    var service = this;
    this.fetch = function (t, forceDetails, noCache) {
        var tid = (t.id) ? t.id : t; // t may be {t} or just INT
        return $http.get("transcription/" + tid, {cache: !noCache})
            .then(function (data) {
                t = service.set(t, data.data);
                if (forceDetails) {
                    return service.getAllPages(t);
                }
                return t;
            },
                function (error) {
// TODO handle 404, 401, 500
                    console.log('Failed to get transcription details');
                    console.log(error);
                })
            .then(function () {
                return t;
            });
    };
    this.get = function (w, forceDetails) {
        var tid = (w.transcription.id) ? w.transcription.id : w.transcription; // t may be {t} or just INT
        if (w.transcription.pages && w.transcription.pages.length > 0) {
            return w.transcription;
        } else if (tid > 0) {
            return this.fetch(w.transcription, forceDetails)
                .then(function (t) {
                    w.transcription = t;
                });
        } else {
            console.log('Error: no transcription available');
            return false;
        }
    };
    this.getAllPages = function (transcription, noCache) {
        var tid = (transcription.id) ? transcription.id : transcription; // t may be {t} or just INT
        if (transcription.pages[0] && transcription.pages[0].text) {
            // already fetched
            return transcription.pages;
        } else {
            return $http.get('transcription/' + tid + '/pages', {cache: !noCache}).success(function (pages) {
                angular.forEach(pages, function (p) {
                    if (!p.canvas) {
                        // no canvas, build a placeholder
                        var manifest = Materials["id" + transcription.witness].manifest;
                        var nullCanvas = {
                            id: "null" + Date.now(),
                            index: p.index,
                            title: p.title,
                            width: 800,
                            height: 1000,
                            images: [{}],
                            manifest: manifest.id
                        };
                        manifest.canvasses.push(nullCanvas);
                    }
                });
                return transcription.pages = pages;
            });
        }
    };
    this.set = function (t, props) {
        if (!angular.isObject(t))
            t = {id: t};
        angular.extend(t, props);
        return t;
    };
});

tradamus.service('PageService', function ($q, $http) {
    var service = this;
    this.set = function (page, props) {
        if (!angular.isObject(page))
            page = {id: page};
        angular.extend(page, props);
        return page;
    };
    /**
     * @deprecated use TranscriptionService.getAllPages()
     * @param {type} transcription
     * @returns {$q@call;defer.promise}
     */
    this.getAll = function (transcription) {
        var deferred = $q.defer();
        var allPages = [];
        angular.forEach(transcription.pages, function (p, index) {
            allPages.push(service.fetch(p)
                .then(function (page) {
                    transcription.pages[index] = service.set(p, page.data);
                }, function (err) {
                    console.log(err);
                }));
        });
        $q.all(allPages).then(function () {
            deferred.resolve(transcription);
        });
        return deferred.promise;
    };
    this.fetch = function (p) {
        var pid = (p.id) ? p.id : p; // p may be {p} or just INT
        return $http.get('page/' + pid)
            .success(function (data) {
                console.log('got page');
                service.set(p, data);
                return p;
            }).error(function (err) {
            console.log('failed fetching page');
        });
//    return $q.all([
//      $http.get('page/' + pid)
//              .then(function(data) {
//        console.log('got page');
//                service.set(p, data.data);
//        return p.canvas;
//      })
//              .then(function(cid) {
//                if (cid)
//                  CanvasService.add(p, cid);
//        return p;
//      }),
//      $http.get('page/' + pid + '/annotations')
//              .then(function(data) {
//        console.log('got page annotations');
//                service.set(p, {annotations: data.data});
//        return p;
//              }),
//        function(err) {
//              console.log("oops: ", err);
//        }
//      ]);
    };
    this.fetchAnnotations = function (p) {
        var pid = (p.id) ? p.id : p; // p may be {p} or just INT
        $http.get('page/' + pid + '/annotations')
            .success(function (annos) {
                console.log('got annotations for page', pid);
//                angular.forEach(annos, function (anno) {
//                    if (!angular.isArray(anno.tags)) {
//                        if (anno.tags) {
//                            anno.tags = anno.tags.split(' ');
//                        }
//                    }
//                });
                service.set(p.annotations, annos);
                return p;
            })
            .error(function (error) {
                // TODO handle 404, 401, 500
                console.log('Failed to get annotations details', error);
            });
    };
    this.getById = function (p, pages) {
        var pid = (p.id) ? p.id : p; // p may be {p} or just INT
        var thisPage = {id: parseInt(pid)};
        angular.forEach(pages, function (page) {
            if (page.id === thisPage.id) {
                thisPage = page;
            }
        });
        return thisPage;
    };
    this.get = function (p) {
        if (p.text)
            return p;
        var pid = (p.id) ? p.id : p; // p may be {p} or just INT
        if (!pid) {
            console.log('no page id provided');
            return false;
        } else {
            return this.fetch(p);
        }
    };
});

tradamus.service('CanvasService', function ($http, $q, Canvas, Canvases) {
    var service = this;
    this.update = function (canvas) {
        return $http.put("canvas/" + canvas.id, canvas);
    };
    this.updateImages = function (canvas) {
        return $http.put("canvas/" + canvas.id + "/images", canvas.images);
    };
    this.set = function (c, props) {
        if (!angular.isObject(c))
            c = {id: c};
        angular.extend(c, props);
        return c;
    };
    this.add = function (p, c) {
        var canvas = angular.copy(Canvas);
        if (!c) {
            p.canvas = canvas;
            return canvas;
        }
        p.canvas = (angular.isObject(c)) ? angular.extend(p.canvas, c) : this.fetch(c);
        return canvas;
    };
    this.get = function (c, noCache) {
        var deferred = $q.defer();
        if (c.page) {
            deferred.resolve(c);
        } else {
            var cid = findCanvasId(c); // c may be {c}, "canvas/:id*" or just INT
            this.fetch(c, noCache).then(function (canvas) {
                deferred.resolve(canvas);
            });
        }
        return deferred.promise;
    };
    this.fetch = function (c, noCache) {
        if (!c) {
            return;
        }
        var cid = findCanvasId(c); // c may be {c}, "canvas/:id*" or just INT
        if (Canvases["id" + cid]) {
            return $q.when(Canvases["id" + cid]);
        }
        return $http.get('canvas/' + cid, {cache: !noCache})
            .success(function (canvas) {
                Canvases["id" + canvas.id] = canvas;
                return canvas;
            })
            .error(function (err) {
                console.log('oops: ', err);
                return err;
            });
    };
    var findCanvasId = function (c) {
        var cid = (c.id) ? c.id : c; // c may be {c}, "canvas/:id*" or just INT
        if (isNaN(cid)) {
            // expect "canvas/:id" or "canvas/:id#xywh=n,n,n,n"
            cid = parseInt(cid.substr(cid.indexOf('canvas/') + 7));
        }
        return cid;
    };
    this.getById = function (c, canvases) {
        if (c.page)
            return c; // already defined
        var cid = findCanvasId(c); // c may be {c}, "canvas/:id*" or just INT
        var c = {id: parseInt(cid)};
        angular.forEach(canvases, function (canvas) {
            if (canvas.id === cid) {
                service.set(c, canvas);
            }
        });
        return c;
    };
});

tradamus.service('CollationService', function ($rootScope, $q, Edition, _cache, $http, AnnotationService, MaterialService) {
    var service = this;
    this.digestCollation = function (collation, retrieveFirst) {
        $rootScope.$broadcast('wait-load', {for : 'collation', message: 'building collations...'});
        var collation = collation || _cache.get("collation");
        if (!collation) {
            return false;
        }
        var decisions = [];
        var cIndex = 0;
        var iMote = {};
        // We need all the transcriptions loaded first...
        return MaterialService.getAll(Edition.witnesses).then(function (mats) {
            while (cIndex < collation.length) {
                iMote = collation[cIndex];
                var newMoteSet = getMoteSet(iMote);
                newMoteSet.index = decisions.length;
                decisions.push(newMoteSet);
                console.log(decisions[decisions.length - 1].cIndex);
                cIndex = Math.max.apply(Math, newMoteSet.cIndex) + 1; // check next
            }
            _cache.store("decisions", service.groupMotes(decisions));
            return decisions;
        }, function (err) {
            return err;
        });
    };
    this.collateOutline = function (outline, defer) {
        var deferred = $q.defer();
        var toSend = [];
        _cache.store("outline", outline);
        if (outline.decisions && outline.decisions.length) {
            deferred.reject("Already has decisions");
        } else if (outline.bounds.length === 1) {
            // this does not need collation
            deferred.reject("This doesn't need collation. I apologize if the interface mislead you.");
        } else {
            angular.forEach(outline.bounds, function (b) {
                if (!b.startPage && b.content) {
                    // direct annotations
                    toSend.push(b);
                } else if (!b.startPage && !isNaN(b)) {
                    // annotation id only
                    toSend.push(AnnotationService.getById(b));
                } else {
                    // good enough to collate
                    toSend.push({
                        startPage: b.startPage.id || b.startPage,
                        startOffset: b.startOffset,
                        endPage: b.endPage.id || b.endPage,
                        endOffset: b.endOffset
                    });
                }
            });
            return collateThis(toSend, defer).then(function (decisions) {
                if (!defer) {
                    outline.decisions = decisions;
                    return outline;
                } else {
                    // delayed collation location
                    return decisions;
                }
            });
        }
        return deferred;
    };
    var collateThis = function (bounds, deferred) {
        return service.collate(bounds, deferred).then(function (collation) {
            if (!deferred) {
                var coll = _cache.get("collation");
                if (coll && coll.length) {
                    return service.digestCollation(collation.data);
                } else {
                    alert("No collation returned.");
                    return [];
                }
            } else {
                // returned location
                return collation.headers("Location");
            }
        }, function (error) {
            console.log(error);
            // failed to collate
            return [];
        });
    };
    this.collate = function (data, deferred) {
        $rootScope.$broadcast('wait-load', {for : 'collation', message: 'collating...'});
        var url = (deferred) ? "collation?deferred=true" : "collation";
        var req = (data) ? $http.post(url, data) : $http.post("collation/" + Edition.id);
        return req
            .success(function (data, status, headers) {
                if (status !== 201) {
                    _cache.store("collation", data);
                    console.log('collated sucessfully', data.length);
                    $rootScope.$broadcast('resume', {for : 'collation'});
                    return _cache.get('collation');
                } else {
                    var loc = headers('Location');
                    return loc;
                }
            }).error(function () {
            console.log('broke getting motes');
            $rootScope.$broadcast('resume', {for : 'collation'});
        });
    };
    /**
     * merge mote annotations from selected decision with
     * neighboring annotations.
     * @param {Array} a1 List of annotations in first decision
     * @param {Array} a2 List of annotations in neighbor decision
     * @return {Array} combined New combined annotations
     */
    var combineMotes = function (a1, a2) {
        var combined = a1;
        angular.forEach(a2, function (a) {
            var addTo = {};
            for (var i = 0; i < combined.length; i++) {
                if (getAnnoWit(combined[i]) === getAnnoWit(a)) {
                    addTo = combined[i];
                    break;
                }
            }
            if (addTo.target) {
                if (addTo.startPage > a.startPage) {
                    // FIXME: This isn't really a detection for the earliest page
                    // startPage
                    addTo.startPage = a.startPage;
                    addTo.startOffset = a.startOffset;
                } else if (addTo.startPage === a.startPage) {
                    // same page
                    addTo.startOffset = Math.min(addTo.startOffset, a.startOffset);
                } else {
                    // earliest start already recorded
                }
                if (addTo.endPage < a.endPage) {
                    // FIXME: This isn't really a detection for the latest page
                    // endPage
                    addTo.endPage = a.endPage;
                    addTo.endOffset = a.endOffset;
                } else if (addTo.endPage === a.endPage) {
                    // same page
                    addTo.endOffset = Math.max(addTo.endOffset, a.endOffset);
                } else {
                    // latest end already recorded
                }
            } else {
                // witness not in list yet
                combined.push(a);
            }
        });
        return combined;
    };

    var getMoteSet = function (startMote) {
//    var moteSet = {
//      content: "",
//      annotations: [],
//      tags: [],
//      index: -1,
//      approvedBy: 0
//    };
        var moteSet = getAdjacent(1, startMote); // send it forward for the first pass
        // also check for any new motes introduced
        var newMotes = angular.copy(moteSet.cIndex);
//    var rem = newMotes.indexOf($scope.selected.mote.index);
//    if (rem > 0) {
//      newMotes.splice(0, rem);
//    }
        while (newMotes.length !== 1) { // do not recheck original mote
            var newMoteIndex = newMotes.pop();
            var newMote = _cache.get("collation")[newMoteIndex];
            var newMoteSet = getAdjacent(1, newMote);
            angular.forEach(newMoteSet.cIndex, function (aMote, i) {
                if (newMotes.indexOf(aMote) === -1 && moteSet.cIndex.indexOf(aMote) === -1) {
                    // not yet present in new motes
                    newMotes.push(aMote);
                    moteSet.motes = combineMotes(moteSet.motes, _cache.get("collation")[aMote]);
                    moteSet.cIndex.push(aMote);
                }
            });
        }
//    // set default text if no variant
//    if (!moteSet.motes[1]) {
//      moteSet.text = moteSet.motes[0].text;
//    }
        return moteSet;
    };
    /**
     * Assemble a list of related Motes. Reiterates to find all variants. Default
     * uses current selection as starting point.
     * @param {?number | Array.<number>} witnessID witness reference
     * @param {?number} startAt index of current collation
     * @param {?Object=} toret the MoteSet being assembled
     * @param {?number:1|-1} direction look forward or backward
     * @param {?number} i iterator for tracking distance from original collation
     * @returns {Object=} toret MoteSet including current selection and all neighbors
     */
    var getAdjacent = function (direction, startAt, witnessID, toret, i) {
        // default to current selection
        var witnessID = service.listWits(startAt) || witnessID;
        witnessID = (witnessID instanceof Array) ? witnessID : [witnessID];
        var startAt = startAt;
        var direction = direction || -1;
        var i = i || 1;
        // check for ends of array
        if (!(startAt.index > -1)) { // index is not tracked internally
            startAt.index = _cache.get("collation").indexOf(startAt);
        }
        var inBounds = (startAt.index + direction > -1) && (startAt.index + direction < _cache.get("collation").length);
        if (!toret) {
            toret = {
                content: "",
                cIndex: [startAt.index],
                motes: startAt
            };
        }
        if (witnessID.length === Edition.witnesses.length) {
            // agreement, get out of this mess.
            return toret;
        }
        if (inBounds) {
            var checking = _cache.get("collation")[startAt.index + direction];
            if (!(checking.index > -1)) {
                checking.index = startAt.index + direction;
            }
            var matchingWitnesses = intersectArrays(witnessID, service.listWits(checking));
            // see if any of these witnesses are in neighbor
            if (matchingWitnesses.length === 0) {
                // checking mote is a variant of this one
                // (the witness is not present in it)
                toret.motes = combineMotes(toret.motes, checking);
                toret.cIndex.push(checking.index);
                if (service.listWits(toret.motes).length < Edition.witnesses.length) {
                    // more witnesses to capture, keep going
                    i++;
                }
            } else {
                // ran into earlier text,
                i--; //@FIXME may not be needed
            }
        }
        if (service.listWits(toret.motes).length < Edition.witnesses.length) {
            // ran into earlier text or end of list before finding all the witnesses
            // reverse direction
            i++;
            if (direction === 1) {
                // we've been this way before...
                // this is an addition, omission, or otherwise incomplete moteSet
                return toret;
            }
            direction = -direction;
            toret = getAdjacent(direction, startAt, witnessID, toret, i);
        }
        return toret;
    };
    this.groupMotes = function (decisions) {
        if (!decisions) {
            return false;
        }
        var ds = (angular.isArray(decisions) ? decisions : [decisions]);
        angular.forEach(ds, function (d) {
            !d.motesets && toMotesets(d);
        });
        return decisions;
    };
    var toMotesets = function (decision) {
        decision.motesets = [];
        angular.forEach(decision.motes, function (a) {
            var found = false;
            for (var i = 0; i < decision.motesets.length; i++) {
//        if (!decision.motes[0] || getText(a) === decision.motes[i].content) {
                if (service.matchContent(getText(a), decision.motesets[i].content)) {
                    // found match, add to mote
                    decision.motesets[i].motes.push(a);
                    if (!decision.motesets[i].witnesses) {
                        decision.motesets[i].witnesses = [];
                    }
                    decision.motesets[i].witnesses.push(getAnnoWit(a));
                    setSigla(decision.motesets[i]);
                    found = true;
                    break;
                }
            }
            if (!found) {
                // match not found
                decision.motesets.push({
                    content: getText(a),
                    motes: [a],
                    witnesses: [getAnnoWit(a)]
                });
                setSigla(decision.motesets[i]);
            }
        });
        return decision.motesets;
    };
    var getText = function (a) {
        var text = "";
        if (a.content) {
            text = a.content;
        } else {
            // get from page
            text = "TODO";
        }
        return text;
    };
    var getSigla = function (wid) {
        return (MaterialService.get(wid, true) && (MaterialService.get(wid, true).siglum || MaterialService.get(wid, true).title)) || "_";
    };
    var setSigla = function (mote) {
        var sigla = mote.witnesses;
        mote.sigla = [];

        angular.forEach(sigla, function (s, i) {
            mote.sigla[i] = getSigla(s);
        });
        return mote.sigla;
    };
    /**
     * Record of all scrubbed differences when comparing text for collation.
     * @type Array
     */
    var filteredComparison = [
        function (str) {
            if (str) {
//            str = str.replace(/\n/g, ''); // eliminate newlines
//            str = str.replace(/ {2,}/g, ' '); // eliminate multiple spaces
                str = str.replace(/\s+/g, ' '); // eliminate any whitespaces
            }
            return str;
        }
        // other functions may include:
        // desensitizing case;
        // dropping <tags>, [brackets], punctuation;
        // equating single and double quotes, ae and æ, etc.
    ];
    this.matchContent = function (a, b) {
        for (var i = 0; i < filteredComparison.length; i++) {
            a = filteredComparison[i](a);
            b = filteredComparison[i](b);
        }
        return a === b;
    };

    /**
     * Find all witnessIDs referenced in a Mote
     * @param {Array || Annotation} annos one or more Mote to investigate
     * @returns {Array.<number>} wits id array of each represented witness
     */
    this.listWits = function (annos) {
        var annos = annos;
        annos = (angular.isArray(annos)) ? annos : [annos];
        var wits = [];
        for (var i = 0; i < annos.length; i++) {
            // for loop, not angular js for want of break/continue;
            if (!annos[i])
                continue; // accidental blank index submitted
            var wit = getAnnoWit(annos[i]);
            if (wits.indexOf(wit) === -1) {
                wits.push(wit);
            }
        }
        return wits;
    };
    var getAnnoWit = function (anno) {
        var wid;
        if (anno.target) {
            wid = parseInt(anno.target.substr(anno.target.lastIndexOf('#') + 1));
        } else if (anno.startPage) {
            wid = MaterialService.getByContainsPage(anno.startPage, true).id;
        } else {
            // No witness is obvious
            wid = -1;
        }
        return wid;
    };
    var intersectArrays = function (arrayA, arrayB) {
        var a = arrayA.slice(0);
        var toret = [];
        while (a.length) {
            var i = arrayB.indexOf(a.pop());
            if (i > -1) {
                toret.push(arrayB[i]);
            }
        }
        return toret;
    };
});
tradamus.service('PermissionsService', function ($http, $q, $rootScope, UserService) {
    this.savePermission = function (p, target) {
        p.target = target;
        var send = [{
                user: p.id,
                role: p.role,
                target: p.target
            }];
        return $http.put(target + "/permissions", send)
            .success(function () {
                Edition.permissions.push(p);
                return true;
            })
            .error(function (error) {
                console.log("Failed to set permissions", error);
                return false;
            });
    };
    this.savePermissions = function (user) {
        var send = [];
        Edition.permissions.push(user);
        angular.forEach(Edition.permissions, function (p) {
            send.push({
                id: p.id,
                user: p.user,
                role: p.role,
                target: p.target
            });
        });
        return $http.put("edition/" + Edition.id + "/permissions", send)
            .success(function () {
                return true;
            })
            .error(function (error) {
                console.log("Failed to set permissions", error);
                return false;
            });
    };

    this.getCollaborators = function (permissions) {
        var csAll = [];
        angular.forEach(permissions, function (p) {
            if (p.role !== "OWNER") {
                csAll.push(UserService.fetchUserDetails(p.user).then(function (u) {
                    angular.extend(u.data, {role: p.role});
                    return u.data;
                }, function () {
                }));
            }
        });
        return $q.all(csAll);
    };
    this.getPublicUser = function (permissions) {
        var pu = {role: 'NONE'};
        for (var i = 0; i < permissions.length; i++) {
            if (permissions[i].user === 0) {
                pu = permissions[i];
                break;
            }
        }
        return pu;
    };
    this.getOwner = function (permissions) {
        var owner = {
            mail: "unrecorded",
            name: "unrecorded"
        };
        for (var i = 0; i < permissions.length; i++) {
            if (permissions[i].role === "OWNER") {
                owner = permissions[i];
                break;
            }
        }
        return owner;
    };
});
tradamus.service('EditionService', function ($http, $q, Lists, $rootScope, Edition, Materials, Outlines, $location, _cache, User, Annotations) {
    var service = this;
    this.create = function (edition) {
        if (edition) {
            Edition = edition;
        }
        $rootScope.$broadcast('wait', 'Waiting for Edition creation...');
        return $http.post('editions', {title: Edition.title})
            .success(function (data, status, headers) {
                var loc = headers('Location');
                Edition.id = parseInt(loc.substring(loc.lastIndexOf('/') + 1));
                $rootScope.$broadcast('resume');
                return service.fetch().then(function (ed) {
                    User.editions.push({id: ed.id, title: ed.title});
                    delete Edition.isNew;
                    $location.path(loc);
                }, function (err) {
                    alert(err);
                });
            })
            .error(function (error) {
                alert('error ' + error);
                return false;
            });
    };
    this.set = function (props) {
        if (!angular.isObject(Edition))
            Edition = {id: Edition};
        angular.extend(Edition, props);
        return Edition;
    };
    var resetCache = function (idArray, cache) {
        //debug
        return;
        angular.forEach(cache, function (val, key) {
            if (val.id && idArray.indexOf(val.id) === -1) {
                delete cache[key];
            }
        });
    };
    var reset = function (e) {
        delete Edition.isNew;
        angular.extend(Edition, {
            id: e.id,
            title: "",
            witnesses: [],
            metadata: [],
            permissions: [],
            creator: -1,
            siglum: ""
        });
        resetCache([], Materials);
        resetCache([], Outlines);
        resetCache([], Annotations);
    };
    this.reset = function () {
        reset({id: false});
    };
    var clearOld = function (e) {
        if (e && e.id != Edition.id) {
            reset(e);
            console.log('reset Edition ', e);
        }
        return true;
    };
    this.get = function (e, prop) {
        if (prop) {
            return Edition[prop];
        } else if (clearOld(e) && Edition.permissions.length) {
            return Edition;
        } else {
            return service.fetch();
        }
    };
    this.fetch = function (noCache) {
        var deferred = $q.defer();
        if (Edition.id > 0) {
            $http.get("edition/" + Edition.id, {cache: !noCache})
                .success(function (data) {
                    service.set(data);
                    Lists.segregate(Edition.witnesses, Materials);
                    Lists.segregate(Edition.metadata, Annotations);
                    deferred.resolve(Edition);
                })
                .error(function (err) {
                    deferred.reject('Failed to fetch Edition details', err);
                });
        } else {
            deferred.reject(Edition.id + " is a bad editionID ", Edition);
        }
        return deferred.promise;
    };
    /*
     * @deprecated
     * @returns {unresolved}
     */
    this.getDecisions = function () {
        return $http.get('edition/' + Edition.id + '/decisions')
            .success(function (decisions) {
                service.set({text: decisions});
                return decisions;
            }).error(function (err) {
            throw err;
            return [];
        });
    };
    this.collate = function (data) {
        $rootScope.$broadcast('wait-load', {for : 'collation', message: 'collating...'});
        var req = (data) ? $http.post("collation", data) : $http.post("collation/" + Edition.id);
        return req
            .success(function (data) {
                _cache.store("collation", data);
                console.log('collated sucessfully', data.length);
                $rootScope.$broadcast('resume', {for : 'collation'});
                return _cache.get('collation');
            }).error(function () {
            console.log('broke getting motes');
            $rootScope.$broadcast('resume', {for : 'collation'});
        });
    };
    /**
     * props: [{"k":"v"},{"k":"v"}]
     * currently only "title" is valid
     */
    this.updateTitle = function (props) {
        return $http.put('edition/' + Edition.id, props)
            .success(function () {
                return true;
            })
            .error(function (error) {
                console.log("Failed to set metadata", error);
                return false;
            });
    };
    this.makeDecision = function (text, dIndex) {
        if (!_cache.decisions || !_cache.decisions[dIndex])
            return false;
        _cache.decisions[dIndex].content = text;
    };
    /**
     * Saves sets of Decisions to the database,
     * replacing any existing ones.
     * @returns {Array<Object>} text Decisions by the editor
     */
    this.setText = function () {
        if (!_cache.decisions)
            return false;
        $http.put('edition/' + Edition.id + '/motes', _cache.decisions)
            .success(function () {
                console.log('Edition Decisions saved');
                return true;
            })
            .error(function (error) {
                console.log("Failed to set Decisions", error);
                return false;
            });
        return true;
    };
    this.addMetadata = function (metadata, context, cid) {
        if (!context) {
            context = "edition";
        }
        if (!metadata) {
            throw Error("No metadata provided for " + context);
        }
        var url = context + "/" + cid + "/metadata";
        return $http.post(url, metadata)
            .success(function (data, status, headers) {
                var loc = headers('Location');
                metadata.id = parseInt(loc.substring(loc.lastIndexOf('/') + 1));
                if (context === "witness") {
                    Materials["id" + cid].metadata.unshift(metadata.id);
                } else {
                    Edition.metadata.unshift(metadata.id);
                    $rootScope.$broadcast("updated-metadata");
                }
                return Annotations["id" + metadata.id] = metadata;
            })
            .error(function (error, status) {
                console.log("Failed to set metadata", status);
                return error;
            });
    };
    /**
     * Saves the Edition.metadata to the database,
     * replacing any existing ones.
     * Format : [{'type':key,'content':value}]
     */
    this.saveMetadata = function (metadata, overwrite) {
        if (!metadata || metadata.length === 0)
            return false;
        var url = "/metadata";
        if (overwrite) {
            url += "?merge=false";
        }
        return $http.put("edition/" + Edition.id + url, metadata)
            .success(function (data, status, headers) {
                // hacky bit to grab the ids of the new metadata
                return service.fetch(true).then(function () {
                    $rootScope.$broadcast("updated-metadata");
                }, function () {
                });
            })
            .error(function (error) {
                console.log("Failed to set metadata", error);
                return error;
            });
    };

    this.getMetadata = function () {
        //included in Edition.fetch()
    };
    this.getAnnotations = function () {
        return $http.get("edition/" + Edition.id + "/annotations")
            .success(function (annos) {
                angular.forEach(annos, function (anno) {
                    Annotations["id" + anno.id] = anno;
                });
                return annos;
            }).error(function (err) {
            console.log(err);
            return(err);
        });
    };
    this.addUser = function (user) {
        if (user.mail === 'publicUser') {
            // public
            return service.savePermission(user, "edition/" + Edition.id);
        }
        if (user.id === undefined) {
            // checking address
            $http.get('user?mail=' + encodeURIComponent(user.mail))
                .then(function (response) {
                    angular.extend(user, response.data);
                    service.savePermission(user, "edition/" + Edition.id);
                },
                    function (error) {
                        if (error.status === 404) {
                            user.name = prompt('(to be placed in pretty dialog box)\n\n\nThis '
                                + 'username was not found in our membership roles.\n\nPlease '
                                + 'enter their full name to invite them or cancel and try again.');
                            if (user.name) {
                                return user;
                            }
                        }
                    })
                .then(function (user) {
                    if (!user)
                        return true;
                    return $http.post('users', {'mail': user.mail, 'name': user.name, 'edition': Edition.id})
                        .then(function (response) {
                            var loc = headers('Location');
                            user.user = parseInt(loc.substring(loc.lastIndexOf('/') + 1));
                            service.savePermission(user, "edition/" + Edition.id);
                            return true;
                        },
                            function (error) {
                                if (error.status === 409) {
                                    alert('Something went wrong. ' +
                                        user.mail + ' is already in use.');
                                }
                                console.log('failed to invite ' + user.name, error);
                                return error;
                            });
                });
        }
    };
    this.addWitnessFromFile = function (file, config) {
        var url = 'witnesses';
        angular.extend(config, {
            params: {
                edition: Edition.id
            }
        });
        return $http.post(url, file, config).success(function (status) {
            service.fetch(true); // reget edition with new witness
            return true;
        }).error(function (err) {
            throw err;
            return false;
        });
    };
    this.delete = function () {
        return $http.delete('edition/' + Edition.id)
            .success(function () {
                Lists.removeFrom(Edition.id, User.editions, "id");
                $location.path('dashboard');
            }).error(function (err) {
            if (err.indexOf("source_source") > -1) {
                alert('Delete Failed: this Edition is used by another object (probably a publication)');
            } else {
                alert("Failed to delete: " + err.text);
            }
        });
    };

});

tradamus.service('OutlineService', function ($http, $q, Edition, Outlines, Lists, Annotations) {
    var service = this;
    var getById = function (oid) {
        if (!Outlines) {
            // looking up outlines without a loaded Edition
            return false;
        }
        if (oid.id) {
            // sent an object in, give it back
            return oid;
        }
        return Outlines["id" + oid];
    };
    this.getOutlines = function (oidArray, noAJAX) {
        var outlines = [];
        angular.forEach(oidArray, function (oid) {
            var o = getById(oid);
            if (o) {
                outlines.push(o);
            } else {
                outlines.push(service.get(oid));
            }
        });
        if (noAJAX) {
            return outlines;
        } else {
            return $q.all(outlines);
        }
    };
    this.get = function (oid, noAJAX) {
        var deferred = $q.defer();
        var o = getById(oid);
        if (noAJAX) {
            return o;
        }
        if (o && o.id) {
            deferred.resolve(o);
        } else {
            return $http.get('outline/' + oid, {cache: true})
                .success(function (outline) {
                    Outlines["id" + outline.id] = outline;
                    Lists.addIfNotIn(outline.id, Edition.outlines);
                    return getAnnotations(outline);
                }).error(function () {
                console.log("failed to get outline " + oid);
                return Error("failed to get outline " + oid);
            });
        }
        return deferred.promise;
    };
    function getAnnotations (outline) {
        var oid = outline.id || outline;
        return $http.get("outline/" + oid + "/annotations")
            .success(function (annos) {
                Outlines["id" + oid].annotations = annos;
                Lists.segregate(Outlines["id" + oid].annotations, Annotations);
            });
    }
    ;
    var scrubBounds = function (outline) {
        return outline;
        // Do not scrub, PUT needs full outlines for now, not references
        var scrubbed = angular.copy(outline);
        if (scrubbed.bounds) {
            scrubbed.bounds = Lists.getAllPropValues("id", scrubbed.bounds);
        }
        return scrubbed;
    };
    this.populateOutlines = function () {
        var deferred = $q.defer();
        var qall = [];
        angular.forEach(Edition.outlines, function (o, ind) {
            qall.push(service.get(o));
        });
        $q.all(qall).then(function (os) {
            while (os.length) {
                var o = os.pop();
                Outlines["id" + o.id] = o;
            }
            deferred.resolve(Outlines);
        });
        return deferred.promise;
    };
    this.add = function (outline) {
        return $http.post('edition/' + Edition.id + '/outlines', scrubBounds(outline))
            .success(function (data, status, headers) {
                var loc = headers('Location');
                var oid = parseInt(loc.substring(loc.lastIndexOf('/') + 1));
                return service.get(oid);
            }).error(function (err) {
            return err;
        });
    };
    this.delete = function (outline) {
        var oid = outline.id || outline;
        if (isNaN(oid)) {
            alert("Could not identify section to delete.");
            return false;
        }
        return $http.delete('outline/' + oid)
            .success(function () {
                delete Outlines["id" + oid];
                Edition.outlines.splice(Edition.outlines.indexOf(oid), 1);
            }).error(function (err) {
            return err;
        });
    };
    this.setOutline = function (outline) {
        var url = "outline/" + outline.id;
        var deferred = $q.defer();
        var toSend = scrubBounds(outline);
        delete toSend.annotations;
        $http.put(url, toSend)
            .then(function () {
                deferred.resolve($http.get(url));
            }, function (err) {
                deferred.reject(err);
            });
        return deferred.promise;
    };
});
tradamus.service('DecisionService', function ($http, $q, Edition, $rootScope, Selection, Display, OutlineService, Lists) {
    this.saveAll = function (decisions) {
        if (!decisions) {
            decisions = Display.outline.decisions;
        }
        var outline = {
            id: Display.outline.id,
            edition: Display.outline.edition || Edition.id,
            index: Display.outline.index,
            title: Display.outline.title,
            decisions: []
        };
        angular.forEach(decisions, function (d) {
            outline.decisions.push({
                id: d.id || null,
                outline: outline.id,
                motes: d.motes,
                content: d.content,
                tags: d.tags || "",
                type: d.type,
                target: d.target
                    // ignored: index, modifiedBy, approvedBy, startPage, startOffset,
                    // endpage, endOffset, attributes, canvas
            });
        });
        return OutlineService.setOutline(outline).then(function (response) {
            Display.outline = response.data;
            Display.savedDecisionsMsg = ("saved " + outline.decisions.length + " decisions successfully");
        }, function (err) {
            return Display.savedDecisionsMsg = "Error: " + err.status;
        });
    };
});
tradamus.service('PublicationService', function ($http, $q, $rootScope, $location, Display, EditionService, OutlineService, Publication, SectionService) {
    var pub = Publication;
    var service = this;
    this.save = function (pid, data) {
        var url = "publication/" + (pid || pub.id);
        if (pub.id > 0) {
            return $http.put(url, data || pub);
        }
    };
    this.getAll = function (noCache) {
        if (!noCache && Display.publications) {
            return Display.publications; // Sometimes the cache would overwrite the new information
        }
        return $http.get("publications", {cache: !noCache}).success(function (pubs) {
            return Display.publications = pubs;
        }, function (err) {
            return err;
        });
    };
    this.getShared = function (noCache) {
        if (!noCache && Display.publicEditions) {
            return Display.publicEditions; // Sometimes the cache would overwrite the new information
        }
        return $http.get("publications?public=true", {cache: !noCache}).success(function (pubs) {
            return Display.publicEditions = pubs;
        }, function (err) {
            return err;
        });
    };
    this.delete = function (pid) {
        return $http.delete("publication/" + pid);
    };
    this.get = function (p) {
        var deferred = $q.defer();
        var pubId = p.id || p || Publication.id;
        if (Publication.id === pubId && Publication.title) {
            deferred.resolve(Publication);
        } else {
            fetch(pubId)
                .then(SectionService.populateSections)
                .then(function () {
                    return EditionService.get({id: Publication.edition});
                }).then(function () {
                deferred.resolve(Publication);
            }, function (err) {
                deferred.reject(err);
            });
        }
        return deferred.promise;
    };
    var fetch = function (p) {
        var deferred = $q.defer();
        var pid = p.id || p || Publication.id;
        var url = "publication/" + pid;
        $http.get(url).success(function (publication) {
            angular.extend(Publication, publication);
            deferred.resolve(Publication);
        })
            .error(function (err) {
                deferred.reject(err);
            });
        return deferred.promise;
    };
    this.create = function (p, callback) {
        var url = "publications";
        $rootScope.$broadcast('wait', 'Creating Publication...');
        return $http.post(url, p)
            .success(function (data, status, headers) {
                var loc = headers('Location');
                p.id = parseInt(loc.substring(loc.lastIndexOf('/') + 1));
                $rootScope.$broadcast('resume');
                service.getAll(true);
                $location.path(loc + "/edit");
                if (callback) {
                    callback();
                }
                return p;
            })
            .error(function (error) {
                console.log('error ' + error);
                $rootScope.$broadcast('resume');
                return error;
            });
    };
    this.addSection = function (section) {
        var url = "publication/" + section.publication + "/sections";
        return SectionService.addSection()
            .success(function (sid) {
                Publication.sections.push(sid);
                $rootScope.$broadcast('resume');
                return section;
            })
            .error(function (error, status) {
                console.log('error ' + error);
                $rootScope.$broadcast('resume');
                return error;
            });
    };
    /**
     * @deprecated This is not the way to do this. See SectionService
     * @returns {$q@call;defer.promise}
     */
    var expandSection = function (section) {
        if (section.id) {
            var qall = [];
            angular.forEach(section.sources, function (src, i) {
                qall.push(OutlineService.get(src));
            });
            if (qall.length) {
                return $q.all(qall).then(function (sources) {
                    section.sources = sources;
                    return section;
                });
            } else {
                return section;
            }
        } else {
            return service.getSection(section);
        }
    };
    /**
     * @deprecated This is not the way to do this. See SectionService
     * @returns {$q@call;defer.promise}
     */
    this.populateSections = function () {
        var deferred = $q.defer();
        var qall = [];
        angular.forEach(pub.sections, function (section, ind) {
            qall.push(expandSection(section).then(function (s) {
                return pub.sections[ind] = s;
            }));
        });
        $q.all(qall).then(function (sections) {
            deferred.resolve(sections);
        });
        return deferred.promise;
    };
});
tradamus.service('SectionService', function ($http, $q, Sections, $timeout, $rootScope, Publication, Lists) {
    var service = this;
    this.add = function (section) {
        var url = "publication/" + section.publication + "/sections";
        return $http.post(url, section)
            .success(function (data, status, headers) {
                var loc = headers('Location');
                section.id = parseInt(loc.substring(loc.lastIndexOf('/') + 1));
                Sections["id" + section.id] = section;
                $rootScope.$broadcast('resume');
                return section.id;
            })
            .error(function (error, status) {
                if (error.indexOf("Deadlock") > -1) {
                    return $timeout(service.addSection(section), 250);
                } else {
                    if (status === 409) {
                        alert("Publication section title '" + section.publication.title + "' is already in use.");
                    }
                    console.log('error ' + error);
                    $rootScope.$broadcast('resume');
                    return error;
                }
            });
    };
    var fetch = function (sid) {
        var url = "section/" + sid;
        return $http.get(url, {cache: true})
            .success(function (s) {
                Sections["id" + s.id] = s;
                return s;
            })
            .error(function (err) {
                return err;
            });
    };
    this.get = function (sid, noAJAX) {
        var deferred = $q.defer();
        var section = Sections["id" + sid];
        if (section) {
            if (noAJAX) {
                return section;
            } else {
                deferred.resolve(section);
            }
        }
        if (!section) {
            if (noAJAX) {
                return {};
            }
            fetch(sid).success(function (s) {
                deferred.resolve(s);
            }).error(function (err) {
                deferred.reject(err);
            });
        }
        return deferred.promise;
    };
    this.update = function (section) {
        var url = "section/" + section.id;
        return $http.put(url, section)
            .success(function (data, status, headers) {
                $rootScope.$broadcast('resume');
                return section;
            })
            .error(function (error) {
                console.log('error ' + error);
                $rootScope.$broadcast('resume');
                alert('Section Update Failed: '+error.status);
                return error;
            });
    };
    this.updateAll = function (sections, callback) {
        var qall = [];
        angular.forEach(sections, function (s) {
            qall.push(service.update(s));
        });
        $q.all(qall).then(function () {
            if (callback) {
                callback();
            }
        }, function (err) {
            throw err;
        });
    };
    this.delete = function (sid) {
        var url = "section/" + sid;
        return $http.delete(url)
            .success(function (data, status, headers) {
                $rootScope.$broadcast('resume');
                delete Sections["id" + sid];
                Lists.removeFrom(sid, Publication.sections);
                return true;
            })
            .error(function (error) {
                console.log('error ' + error);
                $rootScope.$broadcast('resume');
                return error;
            });
    };
    /**
     * @returns {$q@call;defer.promise}
     */
    this.populateSections = function () {
        var deferred = $q.defer();
        var qall = [];
        angular.forEach(Publication.sections, function (section) {
            qall.push(service.get(section).then(function (s) {
                return Sections["id" + s.id] = s;
            }));
        });
        $q.all(qall).then(function (sections) {
            deferred.resolve(Sections);
        });
        return deferred.promise;
    };
});
tradamus.service('AnnotationService', function ($http, $q, $rootScope, Display, Edition, Annotations, Outlines, Materials, CanvasService, Canvases, MaterialService, TranscriptionService, OutlineService, Lists) {
    var service = this;
    this.delete = function (aid) {
        var aid = aid.id || aid;
        if (aid === "new") {
            // unsaved, just remove
            service.select(null);
            return $q.when("Removed");
        } else {
            $rootScope.$broadcast('wait', 'Deleting Annotation...');
            return $http.delete('annotation/' + aid)
                .then(function () {
                    service.select(null);
                    // remove from model
                    var index = Edition.metadata.indexOf(aid);
                    if (index > -1) {
                        Edition.metadata.splice(index, 1);
                    } else {
                        for (var i = 0; i < Edition.witnesses.length; i++) {
                            if (Edition.witnesses[i].annotations) {
                                index = Edition.witnesses[i].annotations.indexOf(aid);
                                if (index > -1 && confirm("Delete annotation?")) {
                                    Edition.witnesses[i].annotations.splice(index, 1);
                                    break;
                                }
                            }
                        }
                    }
                    delete Annotations["id" + aid];
                    return "Removed";
                }, function () {
                    return Error("Error deleting annotation " + aid);
                }).finally(function () {
                $rootScope.$broadcast('resume');
            });
        }
    };
    this.setAnno = this.save = function (anno, url) {
        if (angular.isObject(anno.canvas)) {
            anno.canvas = anno.canvas.id;
        }
        if (angular.isObject(anno.page)) {
            anno.page = anno.page.id;
        }
        $rootScope.$broadcast('wait', 'Saving Annotation...');
        var deferred = $q.defer();
        var url = url;
        if (!url) {
            if (anno.id > 0) {
                // has a current id to update PUT
                url = "annotation/" + anno.id;
                $http.put(url, anno).success(function () {
                    $rootScope.$broadcast('resume');
                    deferred.resolve(anno);
                }).error(function (err) {
                    $rootScope.$broadcast('resume');
                    deferred.reject(err);
                });
            } else if (anno.type === "tr-outline-annotation" || anno.type === "tr-outline") {
// outline annotation on Edition POST
                url = "edition/" + Edition.id + "/metadata";
            } else if (anno.startPage > 0) {
                var tid;
                // new annotation on a page POST
                if (anno.target.indexOf("witness") > -1) {
                    var mid = parseInt(anno.target.substr(anno.target.lastIndexOf("/") + 1));
                    if (!mid) {
                        throw Error("Failed to find material ID in target: " + anno.target);
                    }
                    var mat = Materials["id" + mid];
                    if (!mat) {
                        throw Error("Failed to find material " + mid + " in Materials:" + Materials);
                    }
                    if (!mat.transcription) {
                        // load material with transcription first to check pages
                        MaterialService.get(mat)
                            .then(function (material) {
                                tid = material.transcription.id || material.transcription;
                                url = "transcription/" + tid + "/annotations";
                                deferred.resolve(service.setAnno(anno, url));
                            }, function (err) {
                                deferred.reject(err);
                            });
                        return; // Broke the promise chain with a new lookup
                    } else {
                        tid = mat.transcription.id || mat.transcription;
                        url = "transcription/" + tid + "/annotations";
                    }
                }
            } else if (anno.canvas > 0) {
                // new annotation on a canvas POST
                url = "canvas/" + anno.canvas + "/annotations";
            } else if (anno.target.indexOf("outline") === 0) {
                // new annotation on a decision range
                url = "outline/" + parseInt(anno.target.substring(8)) + "/annotations";
            } else {
                // error in determining type of anno
                throw Error("Improperly formed annotation:", anno);
            }
        }
        if (anno.id === "new" || !anno.id) {
            delete anno.id;
            $http.post(url, anno).success(function (data, status, headers) {
                var loc = headers('Location');
                anno.id = parseInt(loc.substring(loc.lastIndexOf('/') + 1));
                Annotations["id" + anno.id] = anno;
                $rootScope.$broadcast('resume');
                deferred.resolve(anno);
            }).error(function (err) {
                $rootScope.$broadcast('resume');
                deferred.reject(err);
            });
        }
        return deferred.promise;
    };
    var getFromArrayWithId = function (aid, startIn) {
        var annotation;
        if (startIn) {
            var sl = startIn.length;
            for (var i = 0; i < sl; i++) {
                if (startIn[i].id === aid) {
                    annotation = startIn[i];
                    break;
                }
            }
        }
        return annotation;
    };
    this.getById = function (aid, startIn) {
        var annotation;
        if (!aid || isNaN(aid)) {
            return false;
        }
        if (Annotations["id" + aid]) { // Map of all Annotations eventually
            return Annotations["id" + aid];
        }
        var locations = (startIn && startIn.length > 0) ? [startIn, Edition.annotations] : [Edition.annotations];
        angular.forEach(Edition.outlines, function (o) {
            locations.push(o.decisions);
        });
        angular.forEach(Edition.witnesses, function (w) {
            locations.push(w.annotations);
        });
        var loopBreak = 0;
        while (!annotation && ++loopBreak < 1000) {
            annotation = getFromArrayWithId(aid, locations.shift());
        }
        return annotation;
    };
    var getIndex = function (id, array) {
        for (var i = 0; i < array.length; i++) {
            if (array[i].id === id) {
                return i;
            }
        }
        return -1;
    };
    this.getAllOverlapping = function (ref, forceRefresh) {
        if (!ref || !ref.id) {
            return undefined;
        }
        var annos = [];
        if (Display["overlap" + ref.id] && !forceRefresh) {
            return Display["overlap" + ref.id];
        }
        var locations = Annotations;
        if (ref.target.indexOf('outline') > -1) {
            // Edition.annotation object has outline target with decisions
            var target = ref.target;
            var outlineId = parseInt(target.substring(8));
            var startId = parseInt(target.substring(target.lastIndexOf("#") + 1));
            var endId = parseInt(target.substring(target.lastIndexOf("-") + 1));
            OutlineService.get(outlineId).then(function (outline) {
                var i = getIndex(startId, outline.decisions);
                var rangeAccompli = false;
                if (!startId || i === -1) {
                    return false;
                }
                while (!rangeAccompli) {
                    annos.push(outline.decisions[i]);
                    rangeAccompli = outline.decisions[i].id === endId;
                    i++;
                }
            });
        }
        angular.forEach(locations, function (a) {
            var targ = a.target;
            if (targ) {
                var startId = parseInt(targ.substring(targ.lastIndexOf("#") + 1));
                var endId = parseInt(targ.substring(targ.lastIndexOf("-") + 1));
                if (startId === ref.id || endId === ref.id) {
                    annos.push(a);
                }
            }
            // TODO: check if this is in the middle of something
        });
        Display["overlap" + ref.id] = annos;
        return annos;
    };
    var getAnnotations = function (object, noCache) {
        var url = (object.page) ? 'canvas/' : 'page/';
        return $http.get(url + object.id + '/annotations', {cache: !noCache})
            .success(function (annos) {
                angular.forEach(annos, function (anno) {
                    Annotations["id" + anno.id] = anno;
                });
            },
                function (err) {
                    console.log(err);
                });
    };
    this.getBoundedText = function (annotation, forceUpdate) {
        var text = "";
        if (!forceUpdate && Display["_content" + annotation.id]) {
            return Display["_content" + annotation.id];
        }
        if (annotation.startPage
            && annotation.endPage) {
            var w = MaterialService.getByContainsPage(annotation.startPage, true);
            if (w.transcription && w.transcription.pages) {
                var startPage = getFromArrayWithId(annotation.startPage, w.transcription.pages);
                var endPage = getFromArrayWithId(annotation.endPage, w.transcription.pages);
                if (startPage.text) {
                    // page is gotten
                    if (startPage.id === endPage.id) {
                        // same page, simple
                        text = startPage.text.substring(annotation.startOffset, annotation.endOffset);
                    } else {
                        //different pages, trickier
                        text = startPage.text.substring(annotation.startOffset);
                        var i = startPage.index + 1;
                        var page = function (i) {
                            for (var j = 0; j < w.transcription.pages.length; j++) {
                                if (w.transcription.pages[j].index === i) {
                                    return w.transcription.pages[j];
                                }
                            }
                            return {text: ""}; // in case of gap in index
                        };
                        while (endPage.index !== i) {
                            // add all text from pages between
                            text += page(i).text;
                            i++;
                        }
                        text += startPage.text.substring(0, annotation.endOffset);
                    }
                }
            } else {
                Display["_content" + annotation.id] = "[loading]";
                return MaterialService.getByContainsPage(annotation.startPage).then(function () {
                    Display["_content" + annotation.id] = service.getBoundedText(annotation, true);
                    return Display["_content" + annotation.id];
                });
            }
        }
        Display["_content" + annotation.id] = text;
        return text;
    };
    this.select = function (anno) {
        if (!anno) // clearing selection
            return Display.annotation = anno;
        Display.annotation = anno; // setting selection
        if (Display.annotation.canvas) { // fullCanvas may not be detailed at this point
            var cid = parseInt(Display.annotation.canvas.substr(Display.annotation.canvas.indexOf("canvas/") + 7));
            var canvas = Canvases["id" + cid];
            if (!canvas) {
                canvas = CanvasService.getById(Display.annotation.canvas, Display.material.manifest.canvasses);
            }
            if (!canvas) {
                return CanvasService.get(Display.annotation.canvas).then(function (canvas) {
                    Display.canvas = Canvases["id" + cid] = canvas;
                    return anno;
                });
            } else {
                Display.canvas = Canvases["id" + cid] = canvas;
            }
        }
//        RangeService.highlight(anno);
        return anno;
    };
    var getLineByLineId = function (lineid) {
        var matchingElement;
        var allElements = document.getElementsByClassName('line');
        for (var i = 0, n = allElements.length; i < n; i++) {
            if (parseInt(allElements[i].getAttribute('data-line-id')) === lineid) {
                matchingElement = (allElements[i].nodeType !== 3) ?
                    allElements[i].firstChild : allElements[i];
                break;
            }
        }
        return matchingElement;
    };
    var getLineByOffset = function (offset, page) {
        var line; // assumes lines do not overlap, per TPEN
        var witness = findWitnessFromPage(page);
        for (var i = 0; i < witness.annotations.length; i++) {
            if (witness.annotations[i].type !== 'line' || // not a line
                witness.annotations[i].endPage < page || // ends before
                witness.annotations[i].startPage > page || // starts after
                (witness.annotations[i].endPage === page &&
                    witness.annotations[i].endOffset) < offset || // ends before
                (witness.annotations[i].startPage === page &&
                    witness.annotations[i].startOffset > offset)) { //starts after
                continue; // no index check at the moment TODO
            }
            line = witness.annotations[i];
            break;
        }
        return line;
    };
});
tradamus.service('RangeService', function () {
    var highlighter = rangy.createCssClassApplier("highlight");
    var clearSpans = function (classOverride) {
        var remClass = classOverride || "highlight";
        var spans = document.getElementsByClassName(remClass.split(" ")[0]);
        var content = "";
        var i = 0;
        while (spans.item(i)) {
            var a = spans.item(i);
            var p = a.parentNode;
            if (a.nodeType === 1 && a.nodeName === "SPAN" && a.className.length > 1) {
                content = document.createTextNode(a.innerHTML);
                p.insertBefore(content, a);
                p.removeChild(a);
            } else {
                angular.element(a).removeClass(remClass);
                i++;
            }
            p.normalize();
        }
    };
    var applyRules = function (range, rule, altClass) {
        if (range && rule.length) {
            var CSSApply = rangy.createCssClassApplier(altClass || "ruled",
                {
                    elementTagName: "format-rule",
                    elementAttributes: {
                        style: rule
                    }
                });
            CSSApply.applyToRange(range);
        }
    };
    var applyHighlight = function (rangeIn, addHighlight, alternateClass) {
        var range = rangeIn || rangy.getSelection();
        var applyClass = alternateClass ? rangy.createCssClassApplier(alternateClass) : highlighter;
        if (!addHighlight) {
            clearSpans(alternateClass);
        }
        applyClass.applyToRange(range);
    };
    var annoToRange = function (anno, addHighlight, alternateClass, bookend) {
        // TODO: anno must be an Edition annotation at this point and target decisions
        var range = rangy.createRange();
        var targ = anno.target;
        var startId = parseInt(targ.substring(targ.lastIndexOf("#") + 1));
        var endId = parseInt(targ.substring(targ.lastIndexOf("-") + 1));
        var startOffset = parseInt(targ.substring(targ.indexOf(":", targ.lastIndexOf("#")) + 1));
        var endOffset = parseInt(targ.substring(targ.lastIndexOf(":") + 1));
        if (anno.type === 'tr-decision') {
            // just highlight a full decision
            startId = endId = anno.id;
            startOffset = 0;
            endOffset = Math.Infinity; // Math.min later for length
        }
        if (!startId || !endId) {
            return false;
        }
        if (!addHighlight) {
            clearSpans(alternateClass);
        }
        var start = document.getElementById('d' + startId);
        var end = document.getElementById('d' + endId);
        if(bookend==="start"){
            setMark(range, getRangeBoundary(start, startOffset), true);
            setMark(range, getRangeBoundary(start, Math.Infinity));
        } else if (bookend==="end"){
            setMark(range, getRangeBoundary(end, 0), true);
            setMark(range, getRangeBoundary(end, endOffset));
        } else if(start && end){
            setMark(range, getRangeBoundary(start, startOffset), true);
            setMark(range, getRangeBoundary(end, endOffset));
        }
        return range;
    };
    function setMark (range, b, start) {
        // I cannot get apply to work with Rangy above...
        if (start) {
            return range.setStart(b[0], b[1]);
        } else {
            return range.setEnd(b[0], b[1]);
        }
    }
    function getRangeBoundary (el, offset) {
        var nodes = el.childNodes;
        var nl = nodes.length;
        for (var i = 0; i < nl; i++) {
            var node = nodes.item(i);
            while (node.tagName === "SPAN" || node.tagName === "FORMAT-RULE") {
                node = node.childNodes[0];
            }
            if (node.length > offset) {
                // range ends inside the first piece (some span created for highlighting)
                return [node, offset];
            } else {
                offset -= node.length;
            }
        }
        // offset oversized the element. Just capture it all then
        return [nodes.item(nl - 1), nodes.item(nl - 1).length];
        throw Error("Range boundary could not be set. Offset could not be located in node:" + node);
    }
    this.highlight = function (anno, addHighlight, alternateClass) {
        var range = annoToRange(anno, addHighlight, alternateClass);
        if (range) {
            applyHighlight(range, addHighlight, alternateClass);
        }
    };
    this.format = function (anno, rules, bookend, altClass) {
        var range = annoToRange(anno, true, altClass, bookend);
        if (range) {
            applyRules(range, rules, altClass);
        }
    };
    this.removeHighlight = function (classOverride) {
        clearSpans(classOverride);
    };
});

tradamus.service('TagService', function (Lists) {
    var service = this;
    this.allTags = function (force,tagCache) {
        if (force || !service.fullListOfTags || service.fullListOfTags.length === 0) {
            service.fullListOfTags = [];
            angular.forEach(service.annotationLocations, function (each) {
                service.addTagsFrom(each);
            });
        }
    };
    this.addLocation = function (loc) {
        service.annotationLocations.push(loc);
    };
    this.add = function (tag, tagCache) {
        if (tag) {
            tagCache = tagCache || 'fullListOfTags';
            if (!service[tagCache]) {
                service[tagCache] = [];
            }
            Lists.addIfNotIn(tag.trim(), service[tagCache]);
        }
    };
    this.addTagsFrom = function (annos,tagCache) {
        angular.forEach(annos, function (a) {
            service.add(a.tags,tagCache);
        });
    };
    this.allTags();
});

tradamus.service('ManifestService', function ($http, $q, Manifest) {
    this.get = function (w, noCache) {
        var deferred = $q.defer();
        if (angular.isObject(w.manifest)) {
            deferred.resolve(w.manifest);
        } else if (w.manifest) {
            this.fetch(w.manifest, noCache).then(function (manifest) {
                deferred.resolve(manifest.data || manifest);
            });
        } else {
            console.log('No witness loaded - no manifest property');
        }
        return deferred.promise;
    };
    this.fetch = function (m, noCache) {
        var mid = (m.id) ? m.id : m; // w may be {w} or just INT
        if (!mid)
            return ("No witness id");
        return $http.get("manifest/" + mid, {cache: !noCache})
            .success(function (manifest) {
                return manifest;
            }).error(function (err) {
            console.log(err);
            return false;
        });
    };
});

tradamus.service('Selection', function () {
    var service = this;
    this.select = function (ref, obj) {
        return this[ref] = obj;
    };
    this.reset = function () {
        for (var each in this) {
            if (typeof this[each] === "function") {
                continue;
            }
            delete this[each];
        }
        ;
    };
    this.deselect = function (ref) {
        if (!angular.isArray(ref)) {
            ref = [ref];
        }
        angular.forEach(ref, function (r) {
            delete service[r];
        });
        return true;
    };
    this.checkForSelection = function (ref) {
        return (ref in this);
    };
});

tradamus.service('Display', function (Lists) {
    var service = this;
    this.intraform = {
        memory: [],
        show: false,
        content: ''
    };
    this.openIntraform = function (path) {
        this.intraform.show = true;
        this.intraform.content = path;
        this.intraform.memory.push(path);
    };
    this.closeIntraform = function (discard) {
        delete discard;
        this.intraform.memory.pop();
        this.intraform.content = this.intraform.memory.pop();
        this.intraform.show = this.intraform.content;
    };
    this.addType = function (annos) {
        if (!angular.isArray(annos)) {
            annos = [annos];
        }
        angular.forEach(annos, function (a) {
            Lists.addIfNotIn(a.type, service.annoTypes);
        });
    };
    this.annoTypes = [];
    this.zoom = 1;
});