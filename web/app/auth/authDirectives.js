var pathTo = "app/auth/";
tradamus.directive('login', function (User) {
    return {
        restrict: "E",
        scope: {},
        replace: true,
        templateUrl: pathTo + "unknownUser.html",
        controller: 'authController',
        link: function () {
        }
    };
});
tradamus.directive('version', function ($http) {
    return {
        restrict: "E",
        replace: true,
        scope: {},
        template: "<div class='version'>Version:{{version}} ({{revision}}) DB:{{dbVersion}}</div>",
        controller: function ($scope, $http) {
            $scope.versionNumber = 0;
            $http.get('config').success(function (config) {
                $scope.version = config.version;
                $scope.revision = config.revision;
                $scope.dbVersion = config.dbVersion;
            });
        }
    };
});
