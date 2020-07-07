tradamus.controller('authController', function ($scope, User, UserService, Display, $rootScope) {
    /**
     * Basic User access and creation.
     */
    UserService.get().then(function (user) {
        $scope.user = User;
    });
    if(!$scope.display){
        $scope.display = Display;
    }
    $scope.$on('logout', function (event, user) {
        $scope.auth = {};
        $scope.user = user; // BUG: User is often not reset here and I have to manually reset it
    });
    /**
     * Starts session for authorized user.
     * @uses {scope} $scope.Login
     * @returns {Boolean} false on failure
     */
    $scope.login = function (m, p) {
        if ($scope.loginForm.$valid) {
            UserService.login(m, p).then(function () {
                $scope.auth = {};
            }, function (err) {
                console.log(err);
            });
        } else {
            Display.authMessage = {
                type:"danger",
                msg:"Form appears to be invalid, please retype",
                title:"Login Failure"
            };
            return false;
        }
    };
    /**
     * Tests registration form for validity.
     * @param {string} p1 password
     * @param {string} p2 confirm password
     * @returns {Boolean} true if valid
     */
    $scope.validate = function (p1, p2) {
        var valid = p1 === p2;
        $scope.signupForm.$valid = $scope.signupForm.password2.$valid = valid;
        $scope.signupForm.password2.$invalid = !valid;
        return valid;
    };
    /**
     * Requests a new account from the UserService.
     * @uses {scope} $scope.newUser registration information
     * @returns {Boolean} false on failure
     */
    $scope.signup = function (u, m, p) {
        if ($scope.signupForm.$valid) {
            return UserService.signup(u, m, p).then(function () {
                $scope.auth.signup = false; // default to login form if signup was recent
            }, function (err, status) {
                // leave form visible
            Display.authMessage = {
                type:"danger",
                msg:err,
                title:status
            };
            });
        } else {
            Display.authMessage = {
                type:"warning",
                msg:"Mail or password does not validate",
                title:"Signup Failure"
            };
            console.log("check mail and password: ", $scope.auth);
            return false;
        }
    };
    /**
     * Clears user credentials, forcing loss of access where authorization is needed.
     * @todo cascade check for changes to authorization
     * @alters {scope} $scope.user Clears User information
     */
    $scope.logout = function () {
        if (confirm("Log Out - are you sure?")) {
            UserService.logout().then(function () {
            }, function (err) {
                alert('Failed to logout. Please restart your browser.');
                console.log(err);
            });
        }
    };
    /**
     * Request a reset of password, resulting in an e-mail.
     * @returns {Boolean} false on failure
     */
    $scope.resetPassword = function (m) {
        if (!m) {
            Display.authMessage = {
                type:"warning",
                msg:"Please enter your e-mail address first",
                title:"Email Address Missing"
            };
            return false;
        }
        UserService.resetPassword(m);
    };
    /**
     * Request a new copy of the confirmation, resulting in an e-mail.
     * @returns {Boolean} false on failure
     */
    $scope.resendEmail = function (m) {
        if (!m) {
            Display.authMessage = {
                type:"warning",
                msg:"Please enter your e-mail address first",
                title:"Email Address Missing"
            };
            return false;
        }
        UserService.resendEmail(m);
    };
    $scope.showForm = function () {
        $rootScope.showLoginForm = true;
    };
    angular.element(document).scope().$on('event:auth-loginRequired', function () {
        /**
         * Listen for authorization requirement failure event and enforce
         * a pseudo logout without clearing cascading data.
         */
        UserService.set({id: false});
        console.log('auth-loginRequired');
    });
});
tradamus.controller('updateUserController', function ($scope, UserService, User, Display, $modal) {
    $scope.updating = angular.copy(User);
    /**
     * Update existing user account information.
     * mail, name, password (if updating)
     * @param {Object} user basic information to update; based on $scope.updating
     * @returns {HTTP}
     */
    $scope.updateUser = function (user) {
        if (user.password && user.password !== user.confirm) {
            Display.authMessage = {
                type:"warning",
                msg:"Passwords do not match",
                title:"Validation Error"
            };
            return false;
        }
        var tosend = {
            mail: user.mail,
            name: user.name
        };
        if (user.password && user.password.length) {
            tosend.password = user.password;
        }
        UserService.update(tosend).then(function () {
            $scope.user.name = $scope.updating.name;
            $scope.updating.password = $scope.updating.confirm = $scope.updating.mail = "";
            $scope.modal.close();
        }, function (err, status) {
            // update failed
            Display.authMessage = {
                type:"warning",
                msg:err,
                title:status
            };
        });
    };
    $scope.openUserForm = function () {
        $scope.modal = $modal.open({
            templateUrl: 'app/auth/updateUser.html',
            controller: 'updateUserController',
            scope: $scope
        });
    };

});
