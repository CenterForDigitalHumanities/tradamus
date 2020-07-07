tradamus.controller('exportController', function ($scope, $modal, $location) {
    $scope.showShareLinks = function (item) {
        $scope.item = item;
        $scope.modal = $modal.open({
            templateUrl: 'app/components/shareLinks.html',
            scope: $scope,
            size: 'md'
        });
    };
    $scope.links;
    /**
     * Take in an item and return the series of endpoints for sharing it. Expect Edition,
     * Material, or Annotation. Attempts to return a view, JSON, and JSON-LD link.
     *
     * @param {type} item Object from which to derive links - expect Edition, Material, Annotation
     * @returns {Array} links Object with label and link for each valid entry
     */
    $scope.getRecordLinks = function (item) {
        var links = [];
        if (!item.id) {
            return false;
        }
//        var path = $location.absUrl().substring(0, $location.absUrl().indexOf("#"));
        var path = "http://www.tradamus.org";
        if (item.witnesses) {
            // This is an Edition
            links.push({
                label: "Edition Summary",
                link: path + "/#/edition/" + item.id
            }, {
                label: "Simple JSON",
                link: path + "/edition/" + item.id + "?format=json"
            }, {
                label: "URI (JSON-LD)",
                link: "not yet minted"
            });
        } else if (item.transcription) {
            // This is a Material
            links.push({
                label: "Material Summary",
                link: path + "/#/material/" + item.id
            }, {
                label: "Simple JSON",
                link: path + "/witness/" + item.id + "?format=json"
            }, {
                label: "URI (JSON-LD)",
                link: "not yet minted"
            });
        } else if (item.sections) {
            // This is a Publication
            links.push({
                label: "Publication Location",
                link: path + "/#/publication/" + item.id
            });
        } else if (item.target) {
            // This is an Annotation (of some undescribed sort so far)
            links.push({
                label: "Annotation Summary",
                link: path + "/#/annotation/" + item.id
            }, {
                label: "Simple JSON",
                link: path + "/annotation/" + item.id + "?format=json"
            }, {
                label: "URI (JSON-LD)",
                link: "not yet minted"
            });
        }
        return $scope.links = links;
    };
});