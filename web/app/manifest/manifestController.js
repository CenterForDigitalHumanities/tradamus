/* global tradamus */
tradamus.value('Canvases', {});
tradamus.controller('manifestController', function ($scope, $timeout, $modal, CanvasService, MaterialService, Display, Lists, Annotations, MaterialService) {
    $scope.viewCanvas = function (canvases, material, size) {
        $scope.material = material;
        $scope.canvases = canvases;
        $scope.modal = $modal.open({
            templateUrl: 'app/manifest/manifestView.html',
            scope: $scope,
            size: size || 'lg'
        });
        return false;
    };
    $scope.display = Display;
    if (!$scope.material) {
        // such as `/material/ID/`structure route
        $scope.material = Display.material;
    }
    $scope.display.linebreak = 'line';
    $scope.display.activeCanvas = 0;
    $scope.display.show = {};
    $scope.activeImg = new Image();
    $scope.$watch("canvases[display.activeCanvas].images[0].uri", function (newURI) {
        if (newURI) {
            $scope.activeImg.src = newURI;
        }
        if ($scope.canvases && $scope.canvases[Display.activeCanvas].page !== Display.page.id) {
            Display.page = $scope.getPageByCanvas(Display.activeCanvas);
        }
    });
    $scope.show = function (index) {
        if (index < 0) {
            Display.activeCanvas = $scope.canvases.length;
        } else if (index > $scope.canvases.length - 1) {
            Display.activeCanvas = 0;
        } else {
            Display.activeCanvas = index;
        }
    };
    $scope.getImgProp = function (imgURI, prop) {
        if (!imgURI) {
            return "(no image)";
        }
        if (imgURI === $scope.activeImg.src) {
            return $scope.activeImg[prop];
        } else {
            $scope.activeImg.src = imgURI;
            return "loading…";
        }
    };
    $scope.updateCanvas = function (form) {
        CanvasService.updateImages($scope.canvases[Display.activeCanvas])
            .success(function () {
                CanvasService.update($scope.canvases[Display.activeCanvas])
                    .success(function () {
                        form.$setPristine();
                        $scope.display.editCanvas = false;
                    });
            })
            .error(function (err) {
                alert("Update failed.", err);
            });
    };
    $scope.getPageByCanvas = function (canvas, material) {
        if (!material) {
            material = Display.material;
        }
        var cid = canvas.id || canvas;
        var page = Lists.getAllByProp("index", cid, material.transcription.pages)[0];
        if (!page) {
            page = MaterialService.getPageByCanvas(canvas.id, material);
        }
        return page;
    };
    $scope.onPage = function (type, pid) {
        return $scope.getAnnosByPageAndType({id: pid}, type).length > 0;
    };
    $scope.makeLabel = function (a) {
        if (!a.attributes || !a.attributes.label) {
            var label = a.type;
            if (a.content && a.content !== null) {
                label += ": " + a.content;
            }
            label += " (" + a.id + ")";
            if (!a.attributes) {
                a.attributes = {};
            }
            a.attributes.label = label;
        }
        return a.attributes.label;
    };
    $scope.getAnnosByPageAndType = function (page, type, force) {
        if (Display['annos' + page.id + type] && !force) {
            return Display['annos' + page.id + type];
        }
        var pageAnnos = Display["annos_page" + page.id];
        if (!pageAnnos || !pageAnnos.length) {
            pageAnnos = Lists.getAllByProp('startPage', page.id, Annotations);
        }
        var annos = Lists.getAllByProp('type', type, pageAnnos);
        if (annos) {
            Display['annos' + page.id + type] = annos;
        }
        return annos;
    };
    $scope.getLineText = function (page, force) {
        if (!page) {
            return false;
        }
        var annos = $scope.getAnnosByPageAndType(page, Display.linebreak, force);
        if (annos.length) {
            var lines = [];
            angular.forEach(annos, function (a) {
                if (a.startOffset > -1) {
                    var start = (a.startPage === page.id) ? a.startOffset : 0;
                    var end = (a.endPage === page.id) ? a.endOffset : undefined;
                    lines.push({text: page.text.substring(start, end), id: a.id});
                }
            });
        } else {
            lines = [{text: page.text, id: page.id}];
        }
        Display['text' + page.id + Display.linebreak] = lines;
        return lines;
    };
    $scope.lineText = function (lineID, pageID) {
        if (Display['text' + pageID + "line"]) {
            var text = Lists.getAllByProp("id", lineID, Display['text' + pageID + "line"]);
            if (text && text.length) {
                return text.pop().text;
            }
        } else {
            $scope.getLineText({id: pageID});
            return $scope.lineText(lineID, pageID);
        }
        return "";
    };
    $scope.incipit = function (text) {
        if (!text || text.length < 15) {
            return text;
        }
        return text.substr(0, 90) + "…"; // long enough to get trimmed
    };
    $scope.explicit = function (text, force) {
        if (!text || text.length < 45) {
            if (force) {
                return text;
            }
            return "";
        }
        return  "…" + text.substr(-35);
    };
    var targetsCanvas = function(anno,canvas){
        if(!anno.canvas){
            return false;
        }
        if(anno.canvas.indexOf(canvas)===7){ // "canvas/#id"
            return (anno.canvas.indexOf("#xywh=") > -1);
        }
    };
    $scope.buildBoxes = function(canvas){
        angular.forEach(Annotations,function(anno){
            if (targetsCanvas(anno, canvas.id) && anno.content) {
                var pos = anno.canvas.substring(anno.canvas.indexOf("#xywh=") + 6).split(",");
                var box = {
                    id: anno.id,
                    style: {
                        top: 100 * (pos[1] / canvas.height) + "%",
                        left: 100 * (pos[0] / canvas.width) + "%",
                        width: 100 * (pos[2] / canvas.width) + "%",
                        height: 100 * (pos[3] / canvas.height) + "%",
                    },
                    text: $scope.lineText(anno.id, canvas.page)
                };
                if(Display['cache_canvasBoxes'+canvas.id]){
                    Display['cache_canvasBoxes'+canvas.id].push(box);
                } else {
                    Display['cache_canvasBoxes' + canvas.id] = [box];
                }
            }
        });
        return Display['cache_canvasBoxes' + canvas.id] || [];
    };
});

tradamus.directive('sharedCanvas', function () {
    return {
        restrict: "EA",
        scope: {
            canvas: '=',
            showAnnotations: '='
        },
        controller: "manifestController",
        templateUrl: "app/manifest/sharedCanvas.html"
    };
});
