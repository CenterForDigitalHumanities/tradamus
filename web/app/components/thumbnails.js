tradamus.directive('thumbsText', function () {
    return {
        restrict: "EA",
        scope: {
            slides: '=',
            sortable: '='
        },
        replace: true,
        controller: "thumbsController",
        templateUrl: "app/components/thumbsText.html"
    };
});

tradamus.directive('thumbsCanvas', function () {
    return {
        restrict: "EA",
        scope: {
            slides: '=',
            sortable: '='
        },
        replace: true,
        controller: "thumbsController",
        templateUrl: "app/components/thumbsCanvas.html"
    };
});

tradamus.controller('thumbsController', function ($scope, Display) {
    $scope.display = Display;
    $scope.getBoundaryTip = function (page) {
        if (!page.text || page.text.length === 0) {
            return false;
        }
        if ($scope.display['cache' + page.id + 'tip']) {
            return $scope.display['cache' + page.id + 'tip'];
        }
        var charin = 25;
        var charout = 15;
        var tip = (page.text.length > (charin + charout + 3))
            ? page.text.substr(0, charin) + "…<br><span class='text-right'>…" + page.text.substr(-charout) + "</span>"
            : page.text;
        $scope.display['cache' + page.id + 'tip'] = tip;
        return tip;
    };
});