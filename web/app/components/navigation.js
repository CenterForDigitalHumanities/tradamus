tradamus.service('navService', function () {
    this.current;
});

tradamus.controller('navController', function ($scope, User, UserService, Edition, MaterialService, Publication, navService) {
    $scope.nav = navService;
    $scope.isOpen = false;
    $scope.user = User;
    $scope.edition = Edition;
    $scope.ms = MaterialService;
    $scope.publication = Publication;
    /**
     * Clears user credentials, forcing loss of access where authorization is needed.
     * @todo cascade check for changes to authorization
     * @alters {scope} $scope.user Clears User information
     */
    $scope.logout = function () {
        if (confirm("Log Out - are you sure?")) {
            UserService.logout().then(function () {
            }, function (err) {
                alert('failed to logout. All your base are belong to us.');
                console.log(err);
            });
        }
    };
});

tradamus.directive('trNav', function () {
    return {
        restrict: "E",
        replace: true,
        controller: "navController",
        templateUrl: "app/components/trNav.html"
    };
});