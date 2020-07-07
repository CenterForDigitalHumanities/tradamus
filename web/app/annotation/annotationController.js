/* global angular, $document, tradamus */

tradamus.controller('annotationController', function ($scope, Annotations, AnnotationService, Display, Lists, $modal, TagService) {
    $scope.display = Display;
    var sorts;
    $scope.sortedAnnos = function (aids, cache) {
        if (!aids || !cache) {
            return [];
        }
        if (sorts && sorts.length === aids.length) {
            return sorts;
        }
        sorts = Lists.dereferenceFrom(aids, cache)
            .sort(function (a, b) {
                return parseInt(a.target.split(':')[1]) - parseInt(b.target.split(':')[1]);
            });
    };
    $scope.annotations = Annotations;
    $scope.listBy = "type";
    $scope.known = {
        'tr-metadata': 'label-default',
        'tr-outline': 'label-default',
        'tr-outline-annotation': 'label-default'
    };
    if (!Display.page || !Display.page.id) {
        var dereg = $scope.$watch('Display.material.transcription', function () {
            if (Display.material && Display.material.transcription && Display.material.transcription.id) {
                Display.page = Display.material.transcription.pages[0];
                dereg();
            }
        });
    }
    $scope.annoTags = TagService.fullListOfTags;
    $scope.typesInOutline = function (outline) {
        if (!Display["typesOutline" + outline.id]) {
            Display["typesOutline" + outline.id] = [];
            angular.forEach(outline.annotations, function (aid) {
                Lists.addIfNotIn(Annotations["id" + aid].type, Display["typesOutline" + outline.id]);
            });
        }
        return Display["typesOutline" + outline.id];
    };
    $scope.tagsInOutline = function (outline) {
        if (!Display["tagsOutline" + outline.id]) {
            Display["tagsOutline" + outline.id] = [];
            var annos = [];
            angular.forEach(outline.annotations, function (aid, i) {
                annos[i] = Annotations["id" + aid];
            });
            Display["tagsOutline" + outline.id] = Lists.getAllPropValues("tags", annos);
        }
        return Display["tagsOutline" + outline.id]
    };
    $scope.makeLabel = function (a) {
        return "“" + $scope.getSelectedText(a) + "”";
    };
    $scope.deleteAnnotation = function (aid) {
        return AnnotationService.delete(aid)
            .then(function () {
                Display.annotation = null;
                Display.showDelete = false;
                var container = Display.material || Display.outline;
                var index = container.annotations.indexOf(aid);
                if (index > -1) {
                    container.annotations.splice(index, 1);
                }
                $scope.display = Display;
            }, function (err) {
                console.log(err);
            });
    };
    /**
     * Tests for a single tag or tag object in annotation.tags.
     * @param {Object,String} anno full annotation to test or just id
     * @param {Array,String} tag single string to find or tag array
     * @returns {Boolean} True if tag (any tag) is found
     */
    $scope.hasTag = function (anno, tag) {
        if (!anno.id) {
            anno = Annotations["id" + anno];
        }
        if (!anno.tags || !anno.tags.length || !tag) {
            return tag && tag['none'];
        }
        if (angular.isObject(tag)) {
            var test = anno.tags.split(" ");
            for (var i = 0; i < test.length; i++) {
                if (tag[test[i]]) {
                    return true;
                }
            }
        }
        return anno.tags.indexOf(tag) > -1;
    };
    $scope.annotationView = function (annos, listBy) {
        $scope.listBy = listBy;
        $scope.theseAnnotations = Lists.dereferenceFrom(annos, Annotations);
        $scope.modal = $modal.open({
            templateUrl: 'app/annotation/annotationView.html',
            scope: $scope
        });
        return false;
    };
    /**
     * Just open a popover with the content in it.
     * @param {type} content simple content to display
     * @returns {undefined}
     */
    $scope.previewText = function (content, config) {
        var modal = config ? config : {
            template: '<div class="reading clearfix"><page class="col-xs-12">'
                + content
                + '</page><small class="pull-right"><kbd>ESC</kbd> to close</small></div>'
        };
        $scope.modal = $modal.open(config || modal);
    };
    $scope.$watch('Display.annotation', function (newAnno, oldAnno) {
        if ((newAnno || oldAnno) && $scope.display) {
            // hide deletion when selection is changed
            $scope.display.showDelete = false;
        }
    });
    $scope.select = AnnotationService.select;
});

tradamus.controller('textAnnotationController', function ($scope, $q, $modal, Annotations, Lists, PageService, AnnotationService, MaterialService, DecisionService, Display, Canvases) {
    /**
     * Selecting various text ranges.
     */
    $scope.highlight = {};
    $scope.display = Display;
    $scope.display.lockStart, $scope.display.lockEnd;
    $scope.deselect = function (anno) {
        if (anno && anno.id === "new") {
            delete anno;
        }
        Display.annotation = undefined;
    };
    var getDecision = function (id, array) {
        if (Annotations["id" + id]) {
            return Annotations["id" + id];
        }
        if (!array) {
            var array = Display.outline.decisions;
        }
        for (var i = 0; i < array.length; i++) {
            if (array[i].id === id) {
                return array[i];
            }
        }
    };

    /**
     * Extract the highlighted text string.
     * @todo Implement rangy for better browser support
     * @returns {String} Selected text
     */
    $scope.getSelectedText = function (anno) {
        if (!anno) {
            anno = Display.annotation;
        }
        if (!anno) {
            return "";
        }
        var text;
        var targ = "";
        if (anno) {
            var startId, endId, startOffset, endOffset, startD, endD;
            if (anno.target && anno.target.indexOf('-') > -1) {
                targ = anno.target;
                // TODO: reliable way to get decision-type thing here /annotation/12#6-14:25
                startId = parseInt(targ.substring(targ.lastIndexOf("#") + 1));
                endId = parseInt(targ.substring(targ.lastIndexOf("-") + 1));
                startOffset = parseInt(targ.substring(targ.indexOf(":", targ.lastIndexOf("#")) + 1));
                endOffset = parseInt(targ.substring(targ.lastIndexOf(":") + 1));
                if (targ.indexOf("outline") > -1) {
                    text = Display["cache_anno" + anno.id];
                }
                if (anno.startPage && anno.startOffset && anno.endPage && anno.endOffset) {
                    text = Display["cache_page" + anno.startPage + ":" + anno.startOffset
                        + "-" + anno.endPage + ":" + anno.endOffset];
                }
                if (text) {
                    return text;
                }
                if (startId === endId) {
                    startD = getDecision(startId);
                    if (!startD) {
                        // orphaned annotation
                        return "";
                    }

                    if (startD.content && startD.content.length === 0) {
                        startD.content = $scope.showUndecided(startD);
                        startD.tags += (" tr-autoSelected");
                    }
                    text = AnnotationService.getBoundedText(startD)
                        .substring(startOffset, endOffset)
                        || startD.content.substring(startOffset, endOffset);
                } else {
                    startD = getDecision(startId);
                    endD = getDecision(endId);
                    if (!startD || !endD) {
                        // orphaned annotation
                        return "";
                    }
                    if (startD.content.length === 0) {
                        startD.content = $scope.showUndecided(startD);
                        startD.tags += (" tr-autoSelected");
                    }
                    if (endD.content.length === 0) {
                        endD.content = $scope.showUndecided(endD);
                        startD.tags += (" tr-autoSelected");
                    }
//                    text = getDecision(startId).content.substring(startOffset) + " "
//                        + getDecision(endId).content.substring(0, endOffset);
                    var inSelection;
                    angular.forEach($scope.display.outline.decisions, function (d) {
                        if (d.id === startId) {
                            text = d.content.substring(startOffset);
                            inSelection = true;
                        } else if (d.id === endId) {
                            text += " "+d.content.substring(0, endOffset);
                            inSelection = false;
                        } else if (inSelection && d.content.length) {
                            text += " "+d.content;
                        }
                    });
                }
            } else if (anno.startPage > 0) {

                if (!$scope.material || !$scope.material.transcription) {
                    return MaterialService.getByContainsPage(anno.startPage)
                        .then(function (material) {
                            $scope.material = material;
                            return $scope.getSelectedText(anno);
                        }, function (err) {
                            return err;
                        });
                }
                if (!$scope.material.transcription.pages[0].id) {
                    // transcription loaded but not pages
                }
                var startPage = PageService.getById(anno.startPage, $scope.material.transcription.pages);
                var endPage = PageService.getById(anno.endPage, $scope.material.transcription.pages);
                if (startPage.text && endPage.text) {
                    if (startPage === endPage) {
                        text = startPage.text.substring(anno.startOffset, anno.endOffset)
                    } else {
                        text = startPage.text.substring(anno.startOffset);
                        var index = startPage.index;
                        while (++index < endPage.index) {
                            var aPage = Lists.getAllByProp('index', index, $scope.material.transcription.pages)[0];
                            if (aPage) {
                                text += aPage.text;
                            }
                        }
                        text += endPage.text.substring(0, anno.endOffset);
                    }
                }
            }
        }
        if (targ && targ.indexOf("outline") > -1 && anno.id > 1) {
            Display["cache_anno" + anno.id] = text;
        }
        if (anno.startPage && anno.startOffset && anno.endPage && anno.endOffset) {
            Display["cache_page" + anno.startPage + ":" + anno.startOffset
                + "-" + anno.endPage + ":" + anno.endOffset] = text;
        }
        return text || false;
    };
    $scope.showUndecided = function (decision, prefer) {
        if (!prefer) {
            prefer = Display.baseText;
        }
        // find placeholder content
        for (var i = 0; i < decision.motes.length; i++) {
            // check for undecided collation
            var wid = decision.motes[i].startPage && MaterialService.getByContainsPage(decision.motes[i].startPage, true).id;
            if (wid == prefer || !prefer) { // cast to INT, if needed
                return decision.motes[i].content || "␣";
            }
        }
        return "␣"; // base text not represented in this decision
    };
    /**
     * Creates temporary annotation from selected range.
     * @param {Range} highlighted selected range
     */
    $scope.updateSelection = function (highlighted) {
        var range = highlighted.getRangeAt(0);
        positionsFromRange(range)
            .then(function (p) {
                if (!p) {
                    return false;
                }
//            p.bounds = range.getBoundingClientRect();
                angular.extend($scope.highlight, p);
                $scope.showLine(makeNewAnnotation($scope.highlight));
            });
    };
    /**
     * Creates temporary annotation.
     * @param {Object} anno temporary annotation
     * @returns {Object} annotation
     */
    var makeNewAnnotation = function (anno) {
        var automatic = {
            id: 'new',
            type: "tr-userAdded",
            tags: ""
        };
        if ($scope.hasTarget) {
            automatic.target = $scope.hasTarget;
        }
        angular.extend(anno, automatic);
        return anno;
    };
    /**
     * Check element and parent(5th) for class.
     * @param {element} elem
     * @param {string} className
     * @param {Integer} i iterator
     * @returns {Boolean||element} Element with class or false on failure
     */
    var ofClass = function (elem, className, i) {
        var i = i || 0;
        if (elem && i < 5 && !elem.hasClass(className)) {
            elem = ofClass(elem.parent(), className, ++i);
        } else if (elem.hasClass(className)) {
        }
        return elem || false;
    };
    var getXYWHfromSelector = function (selector, dimension) {
        // expect a selector like "canvas/432#xywh=0,0,135,18"
        var dims = selector.substr(selector.indexOf("xywh=") + 5)
            .split(",");
        if (dims.length !== 4) {
            throw Error("Unexpected selector format: " + selector);
        }
        switch (dimension) {
            case "x":
                return dims[0];
            case "y":
                return dims[1];
            case "w":
                return dims[2];
            case "h":
                return dims[3];
            default:
                return dims;
        }
    };
    var setDecisionPositions = function ($startD, $endD, range) {
        var deferred = $q.defer();
        var positions = {
            //            target: "outline/id#frag"
            //  SAMPLE: outline/11#1:2-33:4 in outline 11, character 2 of decision ID 1 up to character 4 of decision ID 33
        };
        var bound = {
            id: $startD.attr('bound-id'), // only one
            offsets: [range.startOffset, range.endOffset]
        };
        if (bound.id) {
            // single witness annotation
            positions.target = "outline/" + Display.outline.id
                + "#" + bound.id
                + ":" + range.startOffset
                + "-" + bound.id + ":" + range.endOffset;
            deferred.resolve(positions);
        } else {
            var startAnno = {
                id: $startD.attr('decision-id'),
                isDecided: $startD.attr('decision-complete')
            };
            var endAnno = ($startD === $endD) ? startAnno : {
                id: $endD.attr('decision-id'),
                isDecided: $endD.attr('decision-complete')
            };
            if (startAnno.id === "new" || endAnno.id === "new") {
                var cfrm = confirm("You have not saved these decisions yet. Click 'OK' to do this now.");
                if (cfrm) {
                    DecisionService.saveAll($scope.selected.outline.decisions);
                }
                Display.annotation = AnnotationService.select();
                deferred.reject('cancelled');
//                DecisionService.saveAll($scope.selected.outline.decisions)
//                    .then(function () {
//                        return setDecisionPositions($startD, $endD, range);
//                    })
//                    .then(function (positions) {
//                        deferred.resolve(positions);
//                    });
            } else {
                positions.target = "outline/" + Display.outline.id
                    + "#" + startAnno.id
                    + ":" + range.startOffset
                    + "-" + endAnno.id + ":" + range.endOffset;
                deferred.resolve(positions);
            }
        }
        return deferred.promise;
    };
    /**
     * Create the annotation positions from range.
     * @param {Range} range
     * @returns {Object|Boolean} positions for annotations
     */
    var positionsFromRange = function (range) {
        var deferred = $q.defer();
        var positions = {
//            startPage: INT ID,
//            endPage: INT ID,
//            startOffset: INT,
//            endOffset: INT,
//            canvas: String like "canvas/16000#xywh=0,0,12,50",
        };
        if ($scope.material) {
            positions.attributes = {
                // 'parallels' breaks the target relationship with the material
                targetMaterial: $scope.material.id
            };
        }
        if (range.startContainer === range.endContainer) {
            // in a single line
            var $line = ofClass(angular.element(range.startContainer), 'line');
            if ($line.hasClass('edition')) {
                setDecisionPositions($line, $line, range)
                    .then(function (pos) {
                        positions = pos;
                        deferred.resolve(positions);
                    }, function () {
                        deferred.reject();
                    });
                // edition annotation, not witness, so divert
            } else {
                var anno = Annotations["id" + $line.attr('annotation-id')];
                if (anno) {
                    positions.startPage = positions.endPage = anno.startPage;
                    positions.startOffset = (anno && anno.startOffset) + range.startOffset;
                    positions.endOffset = (anno && anno.startOffset) + range.endOffset;
                    positions.canvas = anno.canvas;
                    positions.attributes.trStartsIn = anno.id;
                    positions.attributes.trEndsIn = anno.id;
                } else {
                    // likely the 'line' is just the page text
                    positions.startPage = positions.endPage = $line.parent()
                        .attr("page-id");
                    positions.startOffset = range.startOffset;
                    positions.endOffset = range.endOffset;
                }
                deferred.resolve(positions);
            }
        } else {
            // multiple lines spanned
            var $startLine = ofClass(angular.element(range.startContainer), 'line');
            var $endLine = ofClass(angular.element(range.endContainer), 'line');
            if ($startLine.hasClass('edition')) {
                setDecisionPositions($startLine, $endLine, range)
                    .then(function (pos) {
                        positions = pos;
                        deferred.resolve(positions);
                    }, function () {
                        deferred.reject();
                    });
                // edition annotation, not witness, so divert
            } else {
                if ($startLine && $endLine) {
                    var startAnno = Annotations["id" + $startLine.attr('annotation-id')];
                    var endAnno = Annotations["id" + $endLine.attr('annotation-id')];
                    if (startAnno && endAnno) {
                        positions.startPage = startAnno.startPage;
                        positions.endPage = endAnno.endPage;
                        positions.startOffset = startAnno.startOffset + range.startOffset;
                        positions.endOffset = endAnno.startOffset + range.endOffset;
                        if (startAnno.canvas && endAnno.canvas) {
                            if (startAnno.canvas !== endAnno.canvas) {
                                var pos1 = getXYWHfromSelector(startAnno.canvas);
                                var pos2 = getXYWHfromSelector(endAnno.canvas);
                                positions.canvas = "canvas/"
                                    + parseInt(startAnno.canvas.substr(startAnno.canvas.indexOf("canvas/") + 7))
                                    + "#xywh=" + absoluteDistance(pos1, pos2)
                                    .join(",");
                                // TODO: does not show multipage annotations
                            } else {
                                positions.canvas = startAnno.canvas;
                            }
                        }
                        positions.attributes.trStartsIn = startAnno.id;
                        positions.attributes.trEndsIn = endAnno.id;
                        deferred.resolve(positions);
                    } else {
                        deferred.reject("Missing anchoring annotations");
                    }
                } else {
                    // what is selected here?
                    deferred.reject("Unknown selection range");
                }
            }
        }
        return deferred.promise;
    };
    function absoluteDistance (a, b) {
        var x, y, w, h;
        for (var arr in arguments) {
            arguments[arr] = arguments[arr].map(function (x) {
                return x >> 0;
            });
        }
        if (a[0] > b[0]) {
            x = b[0];
            w = Math.max(a[0] - b[0] + a[2], b[2]);
        } else {
            x = a[0];
            w = Math.max(b[0] - a[0] + b[2], a[2]);
        }
        if (a[1] > b[1]) {
            y = b[1];
            h = Math.max(a[1] - b[1] + a[3], b[3]);
        } else {
            y = a[1];
            h = Math.max(b[1] - a[1] + b[3], a[3]);
        }
        return [x, y, w, h];
    }
    ;
    $scope.showLine = function (line) {
        if (!line) {
            AnnotationService.select(null); // remove detail
        }
        var anno = (line.id) ? line : AnnotationService.getById(parseInt(line));
//        $scope.apply(
        AnnotationService.select(anno);
//            );
    };
    /**
     * Just open a popover with the content in it.
     * @param {type} content simple content to display
     * @returns {undefined}
     */
    $scope.previewText = function (content, config) {
        var modal = config ? config : {
            template: '<div class="reading clearfix"><page class="col-xs-12">'
                + content
                + '</page><small class="pull-right"><kbd>ESC</kbd> to close</small></div>'
        };
        $scope.modal = $modal.open(config || modal);
    };
    $scope.clearLocks = function (anno) {
        Display.lockEnd = Display.lockStart = Display.label = undefined;
        if (anno) {
            $scope.setLocks(anno);
        }
    };
    $scope.setLocks = function (anno) {
        Display.lockStart = {
            page: anno.startPage,
            offset: anno.startOffset
        }
        Display.lockEnd = {
            page: anno.endPage,
            offset: anno.endOffset
        };
    };
    $scope.updateStructure = function (a, label) {
        if ($scope.display.lockStart && $scope.display.lockEnd) {
            a.startPage = Display.lockStart.page;
            a.startOffset = Display.lockStart.offset;
            a.endPage = Display.lockEnd.page;
            a.endOffset = Display.lockEnd.offset;
            if (a.tags.indexOf("tr-structure") === -1) {
                a.tags = a.tags + " tr-structure";
            }
            a.target = "witness/" + $scope.material.id;
            if (label) {
                if (!a.attributes) {
                    a.attributes = {};
                }
                a.attributes.label = label;
            }
            var url = "transcription/" + $scope.material.transcription.id + "/annotations";
            $scope.saveAnnotation(a, url)
                .then(function () {
                    //success
                    $scope.clearLocks();
                }, function (err) {
                    throw err;
                });
        }
    };
    $scope.saveAnnotation = function (a, andSelect) {
        var a = a || Display.annotation;
        if (a.id > 0) {
            // updating
            Display.annoMessage = {
                type: "info",
                msg: "updating annotation"
            };
            return AnnotationService.setAnno(a)
                .then(function (anno) {
                    Display.annotation = null;
                    Display.annoMessage = {
                        type: "success",
                        msg: "saved annotation"
                    };
                });
        } else {
            // creating
            Display.annoMessage = {
                type: "info",
                msg: "creating annotation"
            };
            return AnnotationService.setAnno(a)
                .then(function (anno) {
                    if (anno.target.indexOf("outline") === 1 || anno.target.indexOf("annotation") === 1) {
                        // Edition Annotation or annotation annotations
                        $scope.edition.metadata.push(anno.id);
                    } else {
                        var container = Display.material || Display.outline;
                        container.annotations.push(anno.id);
                    }
                    if (andSelect) {
                        Display.annotation = anno;
                    } else {
                        Display.annotation = null;
                    }
                    Display.annoMessage = {
                        type: "success",
                        msg: "created annotation"
                    };
                });
        }
    };
    $scope.isContained = function (anno, container) {
        if (!anno || !container) {
            return false;
        }
        var offset;
        if (anno.endPage === container.startPage) {
            offset = anno.startPage === container.startPage ? container.startOffset : -1;
            if (anno.endOffset < offset) {
                // ends before container
                return false;
            }
        }
        if (anno.startPage === container.endPage) {
            offset = anno.endPage === container.endPage ? container.endOffset : -1;
            if (anno.startOffset > offset) {
                // starts after container
                return false;
            }
        }
        return true;
    };
});

tradamus.directive('textAnnotation', function () {
    return {
        controller: 'textAnnotationController',
        link: function (scope, element, attrs) {
            // prefer a target for new annotations, like in a single material
            scope.hasTarget = attrs.hasTarget;

            element.bind('mouseup', function ($event) {
                var highlighted = getSelection();
                if (highlighted.toString()) {
                    // On recent browsers, only $event.stopPropagation() is needed
                    if ($event.stopPropagation) {
                        $event.stopPropagation();
                    } else if ($event.preventDefault) {
                        $event.preventDefault();
                    }
                    $event.cancelBubble = true;
                    $event.returnValue = false;
                    scope.updateSelection(highlighted);
//                    scope.$apply();
                } else if (angular.element($event.target)
                    .hasClass('line')) {
                    // nothing selected, but line clicked
//                    var annoId = angular.element($event.target).attr('annotation-id');
//                    scope.showLine(annoId);
//                    scope.$apply(); // FIXME Why is this needed?
                }
            });
        }
    };
});

tradamus.directive('annotationSummary', function (Annotations) {
    return {
        restrict: 'E',
        templateUrl: 'app/annotation/annotationSummary.html',
        scope: {
            selected: "=",
            closeable: "@"
        },
        controller: 'textAnnotationController',
        link: function (scope, element, attrs) {
            scope.readonly = attrs.readonly;
        }
    };
});

tradamus.directive('structuralAnnotation', function (Annotations) {
    return {
        restrict: 'E',
        templateUrl: 'app/annotation/structuralAnnotation.html',
        controller: 'textAnnotationController',
        link: function (scope, element, attrs) {
            scope.readonly = attrs.readonly;
            scope.$watch(attrs.selected, function (newVal) {
                scope.selected = newVal;
            });
        }
    };
});

tradamus.directive('selector', function (Canvases, Display, CanvasService) {
    return {
        scope: {
            selector: "="
        },
        controller: function ($scope, $element) {
            // tiny white pic
//            $element.attr('src', "data:image/gif;base64,R0lGODlhBAABAIAAAP///////yH5BAEAAAEALAAAAAAEAAEAAAIChFEAOw==");
            $scope.updateCrop = function () {
                var note = "<div class='help-block text-center bg-secondary'>no image</div>";
                if (!$scope.selector) {
                    $element.next()
                        .remove(); // delete any backup <canvas> that has been added
                    $element.after(note); // add "no image" note
                    $element.addClass('ng-hide');
                    return false;
                }
                CanvasService.get($scope.selector)
                    .then(function (canvas) {
                        $scope.canvas = canvas;
                        var pos = $scope.selector.substr($scope.selector.indexOf("xywh=") + 5)
                            .split(",");
                        var hiddenCanvas = document.createElement('canvas');
                        hiddenCanvas.width = pos[2];
                        hiddenCanvas.height = pos[3];
                        var ctx = hiddenCanvas.getContext("2d");
                        var img = Canvases["cache" + $scope.canvas.id] || new Image();
                        img.onload = function () {
                            Canvases["cache" + $scope.canvas.id] = img;
                            $element.next()
                                .remove(); // delete any backup <canvas> that has been added
                            $element.removeClass('ng-hide');
                            var scale = img.width / $scope.canvas.width;
                            ctx.drawImage(img, pos[0] * scale, pos[1] * scale, pos[2] * scale, pos[3] * scale, 0, 0, hiddenCanvas.width, hiddenCanvas.height);
                            try {
                                $element.attr("src", hiddenCanvas.toDataURL());
                            } catch (err) {
                                // Doesn't serve CORS images, so this doesn't work.
                                // load the canvas itself into the DOM since it is 'tainted'
                                $element.after(hiddenCanvas);
                                var ratio = $element[0].width / hiddenCanvas.width;
                                // BUG: When the img element is hidden, the width is 100, which is often a smaller slice than the screen really allows
                                $element.addClass('ng-hide');
                                hiddenCanvas.width = hiddenCanvas.width * ratio;
                                hiddenCanvas.height = hiddenCanvas.height * ratio;
                                // redraw, after width change
                                ctx.drawImage(img, pos[0] * scale, pos[1] * scale, pos[2] * scale, pos[3] * scale, 0, 0, hiddenCanvas.width, hiddenCanvas.height);
                            }
                        };
                        img.onerror = function (event) {
                            // CORS H8, probably, load tainted canvas
                            img.crossOrigin = null;
                            img.onerror = null; // prevent cascades
                            img.src = $scope.canvas.images[0].uri;
                        };
                        img.crossOrigin = "anonymous";
                        img.src = $scope.canvas.images[0].uri;
                    }, function (err) {
                        throw err;
                    });
            };
            $scope.$watch('selector', $scope.updateCrop);
        }
    };
});

tradamus.filter('hasTag', function (Annotations) {
    /**
     * Tests for a single tag or tag object in annotation.tags.
     * @param {Object,String} anno full annotation to test or just id
     * @param {Array,String} tag single string to find or tag array
     * @returns {Boolean} True if tag (any tag) is found
     */
    return function (items, tag) {
        var list = [];
        angular.forEach(items, function (item) {
            if (!isNaN(item)) {
                item = Annotations["id" + item];
            }
            if (item.tags && item.tags.indexOf(tag) > -1) {
                list.push(item);
            }
        });
        return list;
    };
});