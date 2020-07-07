tradamus.directive('trStoa', function () {
    return {
        restrict: 'E',
        templateUrl: 'assets/partials/trStoaElement.html',
        compile: function (tElement, tAttrs, transclude) {

        }
    };
});
tradamus.directive('siteMatrix', function () {
    return {
        restrict: 'E',
        templateUrl: 'assets/partials/navigation/nav.html',
        controller: 'siteMatrixController',
        compile: function (tElement, tAttrs, transclude) {

        }
    };
});
tradamus.directive('offline', function () {
    return {
        restrict: "A",
        replace: true,
        template: '<div class="modal" style="display: block;">'
            + '        <div class="modal-dialog">'
            + '             <div class="modal-body">'
            + '                 <div class="modal-header">'
            + '                     <h4 class="modal-title text-warning">Unavailable</h4>'
            + '                 </div>'
            + '                 <div class="modal-body">'
            + '                     <p>This view is offline for maintenance.</p>'
            + '                 </div>'
            + '             </div>'
            + '       </div>'
            + '    </div>'
    };
});
tradamus.directive('x_login', function (User) {
    return {
        restrict: "E",
        scope: {},
        replace: true,
        templateUrl: "assets/partials/login.html",
        controller: 'loginCtrl',
        link: function () {
        }
    };
});
tradamus.directive('feedback', function () {
    return {
        restrict: "E",
        scope: {},
        replace: true,
        template: "<button class='btn' id='feedback' ng-click='showFeedback()'>✉</button>",
        controller: 'bugReportController'
    };
});
tradamus.directive('noImage', function () {
    return {
        scope: '=',
        link: function (scope, element, attrs) {
            element.bind('error', function () {
                if (!scope.noImage)
                    scope.noImage = {};
                scope.noImage['canvas' + scope.canvas.id] = true;
                if (attrs.noImageSrc) {
                    element.attr("src", attrs.noImageSrc);
                }
                scope.$apply();
            });
        }
    };
});
tradamus.directive('objectLabel', function () {
    return {
        restrict: "E",
        replace: true,
        template: "<div id='objectLabel' ng-show='edition.objectLabel'>{{edition.objectLabel}}<help></help></div>"
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
//tradamus.directive('variantList', function ($animate) {
//    return {
//        restrict: "E",
//        scope: {decision: '=', isContext: '@'},
//        templateUrl: "assets/partials/variantList.html",
//        controller: 'variantListController',
//        link: function (scope, element) {
//            scope.$watch('decision', function (newVal, oldVal) {
//                var direction;
//                if (oldVal && newVal) {
//                    direction = (!isNaN(newVal) && newVal.id || newVal)
//                        - (!isNaN(oldVal) && oldVal.id || oldVal);
//                }
//                var newClass = (direction < 0) ? "descending" : "ascending";
//                if (direction ^ 2 > 1) {
//                    newClass += " speed";
//                }
//                element.removeClass('ascending descending speed');
//                $animate.addClass(element, newClass);
//            });
//        }
//    };
//});
tradamus.directive('metadataForm', function () {
    return {
        restrict: "E",
        scope: {attach: '='},
        replace: true,
        templateUrl: "assets/partials/metadataForm.html",
        controller: 'metadataFormController',
        link: function (scope, elem, attrs) {
            scope.m = {};
        }
    };
});

tradamus.directive('modal', function () {
    return {
        restrict: "E",
        scope: true,
        replace: true,
        template: "<div class='ng-modal' data-ng-show='modal.isShown'>{{modal.message}}</div>",
        controller: 'modalCtrl'
    };
});
tradamus.directive('tree', function () {
    return {
        restrict: "E",
        scope: {treeNodes: '='},
        controller: 'treeController',
        template: "<ul> \n" +
            "  <li data-ng-repeat='item in treeNodes.nodes'> \n" +
            "    <a data-ng-click='select(item)'>{{item.label}}</a> \n" +
            "      <tree tree-nodes=item.nodes data-ng-if='item.nodes'></tree> \n" +
            "  </li> \n" +
            "</ul>"
    };
});
tradamus.directive('tagsWidget', function (TagService) {
    return {
        restrict: "E",
        templateUrl: "assets/partials/tagsWidget.html",
        scope: {
            item: '=',
            cache: '=',
            annos: '=' // annotations for tags
        },
        link: function (scope, element, attrs) {
            scope.readonly = attrs.readonly;
            TagService.addTagsFrom(scope.annos,scope.cache);
            scope.fullListOfTags = TagService[scope.cache||'fullListOfTags'];
        }
    };
});
tradamus.directive('tagsList', function () {
    return {
        restrict: "E",
        templateUrl: "assets/partials/tagsList.html",
        scope: {
            item: '='
        }
    };
});
tradamus.directive('annoBounds', function () {
    return {
        restrict: "E",
//        replace: true,
//      require:'annotationDetails',
        templateUrl: "assets/partials/annotationBounds.html",
        scope: {
            annotation: '='
        },
        controller: 'annotationBoundsController',
        link: function (scope, element, attrs) {
            scope.$on('scroll', function (event, data) {
                scope.$apply(scope.setBounds(scope.annotation));
            });
        }
    };
});
tradamus.directive('trackScroll', function () {
    return function (scope, element, attrs) {
        element.on('scroll', function () {
            scope.$broadcast('scroll', {type: 'text', scroll: element.scrollTop});
        });
    };
});
//tradamus.directive('fileInput', function ($parse) {
//    return {
//        restrict: "EA",
//        template: "<input type='file' />",
//        replace: true,
//        link: function (scope, element, attrs) {
//
//            var modelGet = $parse(attrs.fileInput);
//            var modelSet = modelGet.assign;
//            var onChange = $parse(attrs.onChange);
//
//            var updateModel = function () {
//                scope.$apply(function () {
//                    modelSet(scope, element[0].files[0]);
//                    onChange(scope);
//                });
//            };
//
//            element.bind('change', updateModel);
//        }
//    };
//});
tradamus.directive('help', function () {
    return {
        restrict: "E",
        controller: 'helpController',
        template: "<span class='actionable help'>help</span>"
    };
});
tradamus.directive('loader', function () {
    return {
        restrict: "E",
        scope: {for : '@for'},
        replace: true,
        template:
            '<div id="squaresWaveG" data-ng-show="loading.isShown">' +
            '<h4 class="wait-load">{{loading.message}}</h4>' +
            '<div id="squaresWaveG">' +
            '<div id="squaresWaveG_2" class="squaresWaveG"></div>' +
            '<div id="squaresWaveG_3" class="squaresWaveG"></div>' +
            '<div id="squaresWaveG_4" class="squaresWaveG"></div>' +
            '<div id="squaresWaveG_5" class="squaresWaveG"></div>' +
            '<div id="squaresWaveG_6" class="squaresWaveG"></div>' +
            '<div id="squaresWaveG_7" class="squaresWaveG"></div>' +
            '<div id="squaresWaveG_8" class="squaresWaveG"></div>' +
            '</div></div>',
        controller: 'loaderCtrl'
    };
});
tradamus.directive('overlay', function () {
    return {
        restrict: 'E',
        scope: true,
        template: "<div id='overlay'>"
            + "<object-label></object-label>"
            + "<div ng-transclude></div>"
            + "<foot-tag></foot-tag><intraform></intraform></div>",
        replace: true,
        transclude: true,
        link: function (scope, iElement, iAttrs, controller) {
            // nothing needed
        }
    };
});
tradamus.directive('footTag', function () {
    return {
        restrict: 'E',
        scope: true,
        controller: 'loginCtrl',
        templateUrl: "assets/partials/footTag.html"
    };
});
tradamus.directive('intraform', function () {
    return {
        restrict: 'E',
        template: "<div class='intraform' ng-show='intraform.show'>"
            + "<object-label></object-label>"
            + "<span ng-include='intraform.content'></span>"
            + "<foot-tag></foot-tag></div>"
    };
});
tradamus.directive('annotationDetails', function () {
    return {
        restrict: 'E',
        templateUrl: 'assets/partials/annotationDetails.html',
        controller: 'annotationDetailsController',
        link: function (scope, element, attrs) {
            scope.readonly = attrs.readonly;
        }
    };
});
tradamus.directive('annotateText', function () {
    return {
//    restrict: 'A', by default
        controller: 'textSelection',
        link: function (scope, element, attrs) {
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
                    scope.$apply();
//          scope.showLine(null); // TODO, show multiple lines here
                } else if (angular.element($event.target).hasClass('line')) {
                    // nothing selected, but line clicked
                    var annoId = angular.element($event.target).attr('data-line-id');
                    scope.showLine(annoId);
                    scope.$apply(); // FIXME Why is this needed?
                }
            });
        }
    };
});
tradamus.directive('tagInput', function () {
    return {
        restrict: 'A',
//    scope: {item: "@", Edition: "&"},
        controller: 'tagInput',
//    function($scope, Edition, Selection) {
////  $scope.edition = Edition;
////var inputTags = Edition.text[$scope.selected].tags;
////	$scope.inputTags = ["ypu","grit","kool"];
//      $scope.tagText = '';
//      $scope.addTag = function() {
//        if ($scope.tagText.length === 0) {
//          return;
//        }
//        $scope.addDecisionTag($scope.tagText);
//        $scope.tagText = '';
//      };
//      $scope.deleteTag = function(key) {
//        if (Edition.text[Selection.decision].tags.length > 0 &&
//                $scope.tagText.length === 0 &&
//                key === undefined) {
//          Edition.text[Selection.decision].tags.pop();
//        } else if (key !== undefined) {
//          Edition.text[Selection.decision].tags.splice(key, 1);
//        }
//      };
//    },
        link: function (scope, element, attrs) {
            scope.inputWidth = 20;
//      if (!Edition.text[Selection.decision].tags) {
//        scope.buildDecisionTags();
//      }

            // Watch for changes in text field
            scope.$watch(attrs.ngModel, function (value) {
                if (value !== undefined) {
                    var tempEl = angular.element('<span id="tempEl">' + value + '</span>');
                    angular.element(document.querySelector('body')).append(tempEl);
//					scope.inputWidth = (document.getElementById("tempEl").width || 30) + 5;
                    scope.inputWidth = "100%";
                    tempEl.remove();
                }
            });

            element.bind('keydown', function (e) {
                if (e.which === 9) {
                    e.preventDefault();
                }

                if (e.which === 8) {
                    scope.$apply(attrs.deleteTag);
                }
            });

            element.bind('keyup', function (e) {
                var key = e.which;
                // Tab, Space, Enter pressed
                if (key === 9 || key === 13 || key === 32) {
                    e.preventDefault();
                    scope.$apply(attrs.newTag);
                }
            });
        }
    };
});

tradamus.directive('ngXlink', function () {
    return {
        priority: 99,
        restrict: 'A',
        link: function (scope, element, attr) {
            var attrName = 'xlink:href';
            attr.$observe('ngXlink', function (value) {
                if (!value)
                    return;
                attr.$set(attrName, value);
            });
        }
    };
});

tradamus.directive('bounceOnChange', function ($animate, $timeout, CollationService) {
    return function (scope, elem, attr) {
        var className = "animated bounce";
        scope.$watch(attr.bounceOnChange, function (nv, ov) {
            if (!CollationService.matchContent(nv, ov)) {
                $animate.addClass(elem, className).then(function () {
                    $timeout(function () {
                        $animate.removeClass(elem, className);
                    });
                });
            }
        });
    };
});
tradamus.directive('pulseOnChange', function ($animate, $timeout) {
    return function (scope, elem, attr) {
        var className = "animated pulse";
        scope.$watch(attr.pulseOnChange, function (nv, ov) {
            if (nv != ov) {
                $animate.addClass(elem, className).then(function () {
                    $timeout(function () {
                        $animate.removeClass(elem, className);
                    });
                });
            }
        });
    };
});