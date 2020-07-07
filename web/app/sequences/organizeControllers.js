tradamus.controller('organizeController', function ($scope, $modal, Display) {
    $scope.organize = function (sequence) {
        $scope.sequence = sequence;
        $scope.modal = $modal.open({
            templateUrl: 'app/sequences/organize.html',
            scope: $scope,
            size: 'lg'
        });
        return false;
    };
    $scope.sortOptions = {
        orderChanged: function (sourceItemScope, destScope) {
            angular.forEach($scope.sequence, function (item, index) {
                item.index = index;
            })
        }
    };
    $scope.saveSequence = function () {
        if ($scope.sequence[0].transcription) {
            // pages
        } else if ($scope.sequence[0].manifest) {
            // canvases
        } else {
            throw Error("Unrecognized sequence type");
        }
    };
    $scope.display = Display;
    $scope.display.by = 'title';
});