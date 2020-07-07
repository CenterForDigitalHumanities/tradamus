tradamus.service('PermissionService', function ($http, $q, Edition, $modal, $rootScope, UserService) {
    this.usersDetails = function (pArray) {
        var qall = [];
        var deferred = $q.defer();
        angular.forEach(pArray, function (p) {
            qall.push(userDetails(p));
        });
        $q.all(qall).then(function (collaborators) {
            deferred.resolve(collaborators);
        }, function (err) {
            deferred.reject(err);
        });
        return deferred.promise;
    };
    var userDetails = function (p) {
        var deferred = $q.defer();
        var u = {
            role: p.role,
            target: p.target
        };
        if (p.user > 0) {
            UserService.fetchUserDetails(p.user).then(function (user) {
                u.user = user.id;
                u.mail = user.mail;
                u.name = user.name;
                deferred.resolve(u);
            });
        } else {
            // checking address
            $http.get('user?mail=' + encodeURIComponent(p.mail))
                .then(function (response) {
                    var user = response.data;
                    u.user = user.id;
                    u.mail = user.mail;
                    u.name = user.name;
                    deferred.resolve(u);
                },
                    function (error) {
                        deferred.reject(error);
                    });
        }
        return deferred.promise;
    };
    this.save = function (p, overwrite) {
        if (!angular.isArray(p)) {
            p = [p];
        }
        var url = p[0].target + "/permissions";
        if (overwrite) {
            url += "?merge=false";
        }
        return $http.put(url, p)
            .success(function () {
                return true; // TODO: update permissions, but right now it is just a EditionService.fetch() which is overkill
            })
            .error(function (error) {
                console.log("Failed to set permissions", error);
                return false;
            });
    };
    this.getCollaborators = function (permissions) {
        var csAll = [];
        angular.forEach(permissions, function (p) {
            if (p.role !== "OWNER" && p.user > 0) {
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
        if (permissions) {
            for (var i = 0; i < permissions.length; i++) {
                if (permissions[i].user === 0) {
                    return permissions[i];
                }
            }
        }
        return {role: 'NONE'};
    };
    this.getOwner = function (permissions) {
        var owner = {
            mail: "",
            name: ""
        };
        if (permissions) {
            for (var i = 0; i < permissions.length; i++) {
                if (permissions[i].role === "OWNER") {
                    owner = permissions[i];
                    break;
                }
            }
        }
        return owner;
    };
    this.invite = function(user){
    return $http.post('users', {'mail': user.mail, 'name': user.name, 'edition': Edition.id})
        .then(function (response) {
        var loc = headers('Location');
            user.user = parseInt(loc.substring(loc.lastIndexOf('/') + 1));
                user.role = user.role || "VIEWER";
            user.target = "edition/" + Edition.id;
            return service.save(user);
        },
            function (error) {
            if (error.status === 409) {
            alert('Something went wrong. ' +
                user.mail + ' is already in use.');
            }
            return Error('failed to invite ' + user.name + ': ' + error.status);
            });
    };
});
tradamus.controller('permissionsController', function ($scope, $modal, Edition, Lists, PermissionService, Display) {
    if (!$scope.permissions) {
        $scope.permissions = Display.permissions;
    }
    var resetForm = function (event) {
        $scope.collaborator = {
            role: "VIEWER"
        };
        if (event) {
            // event.target[0].focus(); //DEBUG possible $digest error?
        }
    };
    $scope.getOwner = function (prop) {
        var owner = PermissionService.getOwner(Edition.permissions);
        if (prop) {
            return owner[prop];
        }
        return owner;
    };
    $scope.roles = [ // "OWNER" not available
        {
            value: "EDITOR",
            description: "Full editing"
        }, {
            value: "CONTRIBUTOR",
            description: "Contribute annotation"
        }, {
            value: "VIEWER",
            description: "View Only"
        }, {
            value: "NONE",
            description: "Remove Permission"
        }];
    $scope.getPublicUser = function (pArray) {
        return $scope.publicUser = PermissionService.getPublicUser(pArray);
    };
    $scope.parsePublicRole = function (permissions) {
        switch (PermissionService.getPublicUser(permissions).role) {
            case "EDITOR" :
                return "completely open";
            case "VIEWER" :
                return "view only";
            case "CONTRIBUTOR" :
                return "shared for annotation";
            case "NONE"   :
                return "disallowed";
        }
    };

    //// collaboration format: [{'user':1,'role':'OWNER'}] NONE, VIEWER, EDITOR, OWNER
    $scope.addCollaborator = function (collaborator, context, isPublicUser) {
        if (!collaborator.target) {
        if (context.canvasses) {
            collaborator.target = "manifest/" + context.id;
        } else if (context.pages) {
            collaborator.target = "transcription/" + context.id;
        } else if (context.metadata) {
                collaborator.target = "edition/" + context.id;
            } else if (context.sections) {
                collaborator.target = "publication/" + context.id;
        } else {
            throw Error("Invalid context for permissions:" + context);
            }
        }
        if (isPublicUser) {
            angular.extend(collaborator, {
                mail: "none",
                user: 0,
                name: "Public User"
            });
            PermissionService.save(collaborator).then(function () {
                Lists.getAllByProp('user', 0, Display.permissions)[0] = collaborator;
                $scope.publicForm.$setPristine();
                resetForm();
            }, function (err) {
                console.log('Something failed when saving permissions');
            });
        } else {
            if (collaborator.user == undefined) {
            $scope.findDetails(collaborator);
            return false;
        }
        if ($scope.memberForm.$valid) {
                PermissionService.save(collaborator).then(function () {
                    Lists.addIfNotIn(collaborator, $scope.permissions);
                resetForm();
                }, function (err) {
                    console.log('Something failed when saving permissions');
            });
            }
        }
    };
    $scope.editPermission = function (p) {
        $scope.collaborator = p;
    };
    $scope.removePermission = function (mid) {
        var index = Lists.indexBy(mid, "id", $scope.permissions);
        $scope.permissions.splice(index, 1);
        PermissionService.save($scope.permissions, true);
    };
    $scope.findDetails = function (collaborator) {
        if ($scope.memberForm.user.$valid && collaborator) {
            PermissionService.usersDetails([collaborator]).then(function (uArray) {
                angular.extend($scope.collaborator, uArray[0]);
            }, function (error) {
                if (error.status === 404) {
                    inviteForm($scope.collaborator);
                }
            });
        }
    };
    var inviteForm = function () {
        if (!$scope.collaborator.mail) {
            throw Error("Expected an email for user to invite.");
        }
        $scope.modal = $modal.open({
            templateUrl: 'app/permissions/inviteForm.html',
            controller: 'permissionsController',
            scope: $scope
        });
        $scope.modal.result.finally(function (result, event) {
            resetForm(event);
        });
    };
    $scope.invite = function(){
        return $scope.modal.close(PermissionService.invite($scope.collaborator));
    };
    $scope.editPermissionForm = function (permissions, context) {
        $scope.permissionList = {
            context: context,
            publicUser: $scope.getPublicUser(permissions)
        };
        Display.permissions = permissions;
        $scope.editing = $scope.editing || {};
        $scope.modal = $modal.open({
            templateUrl: 'app/permissions/permissionForm.html',
            controller: 'permissionsController',
            scope: $scope,
            size: 'lg'
        });
    };
    $scope.exists = function (user, permissions) {
        return Lists.indexBy(user, "user", permissions) > -1;
    };
    if (!$scope.collaborator) {
        resetForm();
    }
});