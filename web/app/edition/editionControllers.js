tradamus.controller('editionController', function ($scope, EditionService, Display, PermissionService, Edition, Materials, $modal) {
    /**
     * Holds scope for entire Edition.
     */
    $scope.edition = Edition;
    $scope.deleteEdition = function () {
        $scope.modal = $modal.open({
            templateUrl: 'app/edition/deleteWarning.html',
            scope: $scope,
            size: 'lg',
            windowClass: 'bg-danger'
        });
    };
    $scope.create = EditionService.create;
    $scope.informedDelete = function () {
        EditionService.delete().then(function () {
            $scope.modal.close();
        }, function (err) {
            Display["del_edition_err" + Edition.id] = err.status + ": " + err.statusText;
        });
    };
    $scope.updateTitle = function (title) {
        EditionService.updateTitle({title: title}).then(function () {
            $scope.title.$setPristine();
        });
    };
    $scope.getOwner = function (prop) {
        if (!Edition || !Edition.permissions) {
            return "";
        }
        if (Edition.id === 0) {
            Edition.id = (Display.material) ? Display.material.edition : 0;
        }
        if (Edition.permissions && !Edition.permissions.length) {
            if (!Display.loadingOwner) {
                Display.loadingOwner = true;
            return EditionService.get().then(function () {
                    var deferredOwner = PermissionService.getOwner(Edition.permissions);
                    delete Display.loadingOwner;
                if (prop && deferredOwner[prop]) {
                    return deferredOwner[prop];
                }
                });
            }
            return "[loading]";
        }
        var owner = PermissionService.getOwner(Edition.permissions);
        if (prop && owner[prop]) {
            return owner[prop];
        }
        return;
    };
    $scope.getCollaborators = function () {
        if (!Edition || !Edition.permissions) {
            return "";
        }
        $scope.collaborators = [];
        PermissionService.getCollaborators(Edition.permissions).then(function (cols) {
            $scope.collaborators = cols;
        });
    };
});

tradamus.directive('editionTag', function () {
    return {
        restrict: 'E',
        replace: true,
        templateURL: 'app/edition/editionTag.html'
    };
});