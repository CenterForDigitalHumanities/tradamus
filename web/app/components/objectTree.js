tradamus.directive('objectTree', function () {
    return {
        restrict: "EA",
        scope: {
            parent: '=',
            fullscreen: '='
        },
        replace: false,
        templateUrl: "app/components/tree.html",
        controller: 'treeController',
        link: function (scope, element, attrs) {
        }
    };
});

tradamus.controller('treeController', function ($scope) {
    $scope.isObj = function (node) {
        return angular.isObject(node) && !angular.isArray(node);
    };
    $scope.adjustLabel = function (label, fullscreen) {
        var length = fullscreen ? 40 : 18;
        if (label.length > length) {
            return "…" + label.substr(-length);
        }
        return label;
    };
    $scope.removeNode = function (k) {
        delete $scope.parent[k];
    };
    $scope.flattenNode = function (key) {
        if ($scope.isObj($scope.parent[key])) {
            angular.forEach($scope.parent[key], function (child, k) {
                if ($scope.parent[key + "." + k]) {
                    // This should never happen, but an accidental overwrite would really upset a scholar.
                    var i = 0;
                    while ($scope.parent[key + "." + k + ++i]) {
                    }
                    k = k + "+" + i;
                }
                $scope.parent[key + "." + k] = child;
            });
            delete $scope.parent[key];
        }
    };
});