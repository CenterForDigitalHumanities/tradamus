tradamus.controller('metadataController', function ($scope, Edition, EditionService, MaterialService, Annotations, $modal, AnnotationService, Lists, Display) {
    $scope.display = Display; // For UI control, like fullscreen adjustments and such
    var itemType = function () {
        if ($scope.item.witnesses) {
            return "edition";
        }
        if ($scope.item.transcription) {
            return "material";
        }
        if ($scope.item.sections) {
            return "publication";
        }
        throw Error("Unknown item type for $scope.item:" + $scope.item);
    };
    if ($scope.item && !$scope.item.metadata) {
        $scope.item.metadata = [];
        switch (itemType()) {
            case "edition" :
                EditionService.fetch(true) // prevent cache
                    .success(function () {
                        $scope.item = Edition;
                    }).error(function () {
                    throw Error("Unable to load Edition");
                });
            case "material" :
                MaterialService.get($scope.item)
                    .success(function (mat) {
                        $scope.item = mat;
                    }).error(function () {
                    throw Error("Unable to load Edition");
                });
            default :
                throw Error("Unrecognized item type: " + itemType());
        }
    }
    var resetForm = function (event) {
        $scope.editing.type =
            $scope.editing.id =
            $scope.editing.content = null;
        if (event) {
//            event.target[0].focus(); // DEBUG possible $digest error?
        }
    };
    $scope.addMetadata = function (m, event) {
        var data = {};
        if (m.id) {
            m = angular.extend(Annotations["id" + m.id], m);
            updateData(m).then(function () {
                resetForm(event);
            });
        } else {
            data = {
                type: m.type,
                content: m.content,
                tags: "tr-metadata"
            };
            saveNewData(data).then(function () {
                resetForm(event);
            });
        }
        // TODO: switch for Witness metadata, etc.
    };
    var updateData = function (data, overwrite) {
        return AnnotationService.save(data, overwrite);
    };
    var saveNewData = function (data) {
        switch (itemType()) {
            case "edition":
                return EditionService.addMetadata(data, 'edition', Edition.id);
            case "material":
                return EditionService.addMetadata(data, 'witness', $scope.item.id) // FIXME - this works, but is dumb
        }
        return false;
    };
    $scope.editMetadata = function (m) {
        $scope.editing.type = m.type;
        $scope.editing.id = m.id;
        $scope.editing.content = m.content;
    };
    $scope.annotations = Annotations;
    var metadataList = function (idArray) {
        return Lists.dereferenceFrom(idArray, Annotations);
    };
    $scope.cancelDelete = function (mid) {
        delete Annotations["id" + mid].deleting;
    };
    $scope.removeMetadata = function (mid) {
        var index = $scope.item.metadata.indexOf(mid);
        $scope.item.metadata.splice(index, 1);
        angular.forEach(Annotations, function (a) {
            delete a.deleting;
        });
        EditionService.saveMetadata(metadataList($scope.item.metadata), true);
    };
    $scope.editMetadataForm = function (item) {
        $scope.item = item;
        $scope.editing = $scope.editing || {};
        $scope.modal = $modal.open({
            templateUrl: 'app/metadata/metadataForm.html',
            controller: 'metadataController',
            scope: $scope,
            size: 'lg'
        });
    };
    $scope.adjustLabel = function (label, fullscreen) {
        if (!label) {
            return "( unlabelled )";
        }
        var length = fullscreen ? 40 : 18;
        if (label.length > length) {
            return "…" + label.substr(-length);
        }
        return label;
    };
    $scope.inputType = "file";
});

tradamus.directive('metadataFor', function () {
    return {
        restrict: 'A',
        controller: 'metadataController',
        link: function (scope, element, attrs) {
            scope.item = scope[attrs.metadataFor];
        }
    };
});