/**
 * @author cubap@slu.edu
 * @file Main AngularJS application and routing for {@link http://www.tradamus.org Tradamus Suite}
 * @copyright Copyright 2011-2014 Saint Louis University
 * @license http://www.osedu.org/licenses/ECL-2.0
 * @disclaimer Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

var tradamus = angular.module('tradamus',
    ['http-auth-interceptor', 'ui.bootstrap', 'angularFileUpload', 'ngRoute',
        'ngAnimate', 'ngScrollTo', 'utils', 'ui.sortable', 'angular-loading-bar'])
    .config(['$routeProvider',
        function ($routeProvider, $locationProvider, Edition) {
            $routeProvider
                .when('/dashboard', {
                    templateUrl: 'app/dashboard/dashboard.html',
                    controller: 'DashboardCtrl',
                    resolve: {
                        current: function (navService) {
                            navService.mainHelp = "How%20To%20Use";
                            return navService.current = 'dashboard';
                        }
                    }
                })
                .when('/edition', {
                    templateUrl: 'app/edition/editionList.html',
                    resolve: {
                        editions: function (UserService, navService) {
                            navService.current = 'edition';
                            navService.mainHelp = "Edition";
                            return UserService.getEditions();
                        }
                    }
                })
                .when('/edition/:editionId', {
                    templateUrl: 'app/edition/edition.html',
                    controller: 'editionController',
                    resolve: {
                        e: function ($q, $route, EditionService, $location, navService) {
                            navService.current = 'edition';
                            navService.mainHelp = "Edition";
                            var editionId = $route.current.params.editionId;
                            if (editionId === "new") {
                                EditionService.set({
                                    isNew: true,
                                    title: ""
                                });
                                return true;
                            }
                            if (!(editionId > 0)) {
                                // non-real id provided
                                return $location.path('/edition');
                            }
                            var deferred = $q.defer();
                            if (isNaN(parseInt(editionId))) {
                                deferred.reject("Invalid ID provided:" + editionId);
                            } else {
                                $q.when(EditionService.get({id: editionId})).then(function (edition) {
                                    deferred.resolve(edition);
                                }, function () {
                                    deferred.reject("No Edition found.");
                                });
                            }
                            return deferred.promise;
                        }
                    }
                })
                .when('/draft/:editionId/annotate/:outlineId?', {
                    redirectTo: function ($route) {
                        var editionId = $route.editionId;
                        var outlineId = $route.outlineId;
                        return '/draft/' + editionId + '/collation/' + outlineId || '';
                    }
                })
                .when('/draft/:editionId/collation/:outlineId?', {
                    templateUrl: 'app/draft/collation.html',
                    controller: 'collationController',
                    resolve: {
                        isNew: function ($q, $route, EditionService, OutlineService, $location, navService, MaterialService) {
                            navService.current = 'draft';
                            navService.mainHelp = "Collation";
                            var editionId = $route.current.params.editionId;
                            if (!(editionId > 0)) {
                                // non-real id provided
                                return $location.path('/edition');
                            }
                            var deferred = $q.defer();
                            if (isNaN(parseInt(editionId))) {
                                deferred.reject("Invalid ID provided:" + editionId);
                            } else {
                                $q.when(EditionService.get({id: editionId})).then(function (edition) {
                                    if (!edition.outlines || edition.outlines.length === 0) {
                                        // edition contains no outlines
                                        return $location.path('/edition/' + editionId + '/structure');
                                    }
                                    if (!$route.current.params.outlineId) {
                                        // no outline to display, pick the first
                                        return $location.path('/edition/' + editionId + '/collate/' + edition.outlines[0]);
                                    }
                                    // Load Materials deeply, as all are needed
                                    $q.all([
                                        OutlineService.getOutlines(edition.outlines),
                                        MaterialService.getAll(edition.witnesses)])
                                        .then(function () {
                                            OutlineService.selected = $route.current.params.outlineId;
                                            deferred.resolve(edition);
                                        }, function (err) {
                                            // failed to get outlines
                                            deferred.reject(err);
                                        });
                                }, function () {
                                    deferred.reject("No Edition found.");
                                });
                            }
                            return deferred.promise;
                        }
                    }
                })
                .when('/draft/:editionId', {
                    templateUrl: 'app/draft/compose.html',
                    controller: 'draftController',
                    resolve: {
                        isNew: function ($q, $route, EditionService, OutlineService, $location, navService) {
                            navService.current = 'draft';
                            navService.mainHelp = "Draft";
                            var editionId = $route.current.params.editionId;
                            if (!(editionId > 0)) {
                                // non-real id provided
                                return $location.path('/edition');
                            }
                            var deferred = $q.defer();
                            if (isNaN(parseInt(editionId))) {
                                deferred.reject("Invalid ID provided:" + editionId);
                            } else {
                                $q.when(EditionService.get({id: editionId})).then(function (edition) {
                                    OutlineService.getOutlines(edition.outlines).then(function () {
                                        deferred.resolve(edition);
                                    }, function (err) {
                                        deferred.reject(err); // failed to get outlines
                                    });
                                }, function () {
                                    deferred.reject("No Edition found.");
                                });
                            }
                            return deferred.promise;
                        }
                    }
                })
                .when('/material', {
                    templateUrl: 'app/materials/materialsList.html',
                    resolve: {
                        temp: function (navService, Materials, Edition, EditionService, $location) {
                            navService.current = 'material';
                            navService.mainHelp = "Material";
                            if (!Edition.id || Edition.id < 1) {
                                $location.path('/edition');
                            } else if (Materials.length !== Edition.witnesses.length) {
                                return EditionService.get({id: Edition.id});
                            }
                            return false;
                        }
                    }
                })
                .when('/material/:materialId?', {
                    templateUrl: 'app/materials/material.html',
                    controller: 'materialsController',
                    resolve: {
                        temp: function ($q, $route, MaterialService, navService, $location, Display) {
                            navService.current = 'material';
                            navService.mainHelp = "Material";
                            var materialId = $route.current.params.materialId;
                            if (!materialId) {
                                $location.path('/material');
                                return false;
                            }
                            var deferred = $q.defer();
                            if (isNaN(parseInt(materialId))) {
                                deferred.reject("Invalid ID provided:" + materialId);
                            } else {
                                MaterialService.get(materialId).then(function (material) {
                                    return Display.material = material;
                                }, function () {
                                    Display.material = null;
                                    deferred.reject("No Material found.");
                                }).then(function (m) {
                                    return MaterialService.getPages(m, 'all');
                                }).then(function (m) {
                                    deferred.resolve(Display.material);
                                });
                            }
                            return deferred.promise;
                        }
                    }
                })
                .when('/material/:materialId/structure/:pageId?', {
                    templateUrl: 'app/annotation/annotateStructure.html',
                    controller: 'annotationController',
                    resolve: {
                        temp: function ($q, $route, MaterialService, navService, $location, Lists, Display) {
                            navService.current = 'material';
                            navService.mainHelp = "Structure";
                            var materialId = $route.current.params.materialId;
                            if (!materialId) {
                                $location.path('/material');
                                return false;
                            }
                            var deferred = $q.defer();
                            if (isNaN(parseInt(materialId))) {
                                deferred.reject("Invalid ID provided:" + materialId);
                            } else {
                                MaterialService.get(materialId).then(function (material) {
                                    Display.material = material;
                                    Display.page = Lists.getAllByProp('id', parseInt($route.current.params.pageId), material.transcription.pages)[0]
                                        || material.transcription.pages[0];
                                    // reset interface if changing
                                    Display.annotation = null;
                                    deferred.resolve(material);
                                }, function () {
                                    Display.material = null;
                                    deferred.reject("No Material found.");
                                });
                            }
                            return deferred.promise;
                        }
                        //TODO: use pageID from route to init default page
                    }
                })
                .when('/material/:materialId/annotate/:pageId?', {
                    templateUrl: 'app/annotation/annotateMaterial.html',
                    controller: 'annotationController',
                    resolve: {
                        temp: function ($q, $route, MaterialService, navService, $location, Lists, Display) {
                            navService.current = 'material';
                            navService.mainHelp = "Material";
                            var materialId = $route.current.params.materialId;
                            if (!materialId) {
                                $location.path('/material');
                                return false;
                            }
                            var deferred = $q.defer();
                            if (isNaN(parseInt(materialId))) {
                                deferred.reject("Invalid ID provided:" + materialId);
                            } else {
                                $q.when(MaterialService.get(materialId)).then(function (material) {
                                    Display.material = material;
                                    Display.page = Lists.getAllByProp('id', parseInt($route.current.params.pageId), material.transcription.pages)[0]
                                        || material.transcription.pages[0];
                                    // reset interface if changing
                                    Display.annotation = null;

                                    deferred.resolve(material);
                                }, function () {
                                    Display.material = null;
                                    deferred.reject("No Material found.");
                                });
                            }
                            return deferred.promise;
                        }
                        //TODO: use pageID from route to init default page
                    }
                })
                .when('/edition/:editionId/witness/:witnessId', {
                    templateUrl: 'assets/partials/editWitness.html',
                    controller: 'editWitness',
                    resolve: {
                        edition: function ($route, $q, EditionService, WitnessService, Selection, Display, navService) {
                            var deferred = $q.defer();
                            var editionId = $route.current.params.editionId;
                            navService.mainHelp = "Material";
                            $q.when(EditionService.get({id: editionId})).then(function (edition) {
                                var wit = WitnessService.getById($route.current.params.witnessId);
                                WitnessService.get(wit, true, true).then(function (witness) {
                                    wit = witness;
                                    Display.material = wit;
                                    Selection.select('witness', wit);
                                    deferred.resolve(edition.data);
                                }, function (err) {
                                    console.log(err);
                                });
                            });
                            return deferred.promise;
                        }
                    }
                })
                .when('/publication/:publicationId/edit', {
                    templateUrl: 'app/publication/editPublication.html',
                    controller: 'editPublicationController',
                    resolve: {
                        Publication: function ($route, $q, $location, PublicationService, Publication, OutlineService, Edition, EditionService, Display, navService) {
                            navService.current = "publish";
                            navService.mainHelp = "Publishing";
                            var deferred = $q.defer();
                            var pubId = parseInt($route.current.params.publicationId);
                            if (pubId === "simple") {
                                Display.permissions = Publication.permissions;
                                deferred.resolve(Publication); // TODO: inject some easy templates
                            } else if (Publication.id === pubId && Publication.title) {
                                if (!angular.isObject(Publication.sections[0]) && Publication.sections[0] > 0) {
                                    // just the outline ID loaded
                                    angular.forEach(Edition.outlines, function (o) {
                                        var oid = parseInt(o) || (o && o.id);
                                        OutlineService.get(oid).then(function (outline) {
                                            Edition.outlines[outline.index] = outline;
                                        });
                                    });
                                }
                                PublicationService.populateSections().then(function () {
                                    Display.permissions = Publication.permissions;
                                    deferred.resolve(Publication);
                                });
                            } else {
                                PublicationService.get({id: pubId}).then(function (p) {
                                    Display.permissions = Publication.permissions;
                                    return Publication;
                                }, function (err) {
                                    $location.path("/publication");
                                })
                                    .then(function () {
                                        $q.when(EditionService.get({id: Publication.edition})).then(function () {
                                            OutlineService.getOutlines(Edition.outlines)
                                                .then(function (os) {
                                                Display.permissions = Publication.permissions;
                                                deferred.resolve(Publication);
                                            });
                                        });
                                    }, function (err) {
                                        deferred.reject(err);
                                    });
                            }
                            return deferred.promise;
                        }
                    }
                })
                .when('/edition/:editionId/publications', {
                    templateUrl: 'app/publication/publicationsList.html',
                    controller: 'publicationController',
                    resolve: {
                        Publications: function (PublicationService, navService, $route, EditionService, $q) {
                            navService.current = "publish";
                            navService.mainHelp = "Publishing";
                            return $q.when(EditionService.get({id: $route.current.params.editionId}))
                                .then(function () {
                                    return PublicationService.getAll();
                                });
                        }
                    }
                })
                .when('/publication/:publicationId', {
                    templateUrl: 'app/publication/viewPublication.html',
                    controller: 'viewPublicationController',
                    resolve: {
                        Publication: function ($route, $q, $rootScope, PublicationService, Publication, navService, MaterialService, Outlines, Sections, OutlineService, Edition, EditionService) {
                            navService.current = "viewPublication";
                            navService.mainHelp = "Publishing";
                            var deferred = $q.defer();
                            Publication.id = parseInt($route.current.params.publicationId);
                            PublicationService.get(Publication)
                                .then(function (pub) {
                                    $rootScope.pageTitle = pub.title + " - Tradamus Digital Publication";
                                    $q.when(EditionService.get({id: pub.edition}))
                                        .then(function () {
                                            return Edition;
                                        });
                                })
                                .then(function (edition) {
                                    return OutlineService.getOutlines(Edition.outlines);
                                })
                                .then(function () {
                                    return MaterialService.getAll(Edition.witnesses);
                                })
                                .then(EditionService.getAnnotations, function (err) {
                                    deferred.reject(err);
                                }).then(function () {
                                deferred.resolve(Publication);
                            }, function (err) {
                                deferred.reject(err);
                            });
                            return deferred.promise;
                        }
                    }
                })
                .when('/publication/:publicationId/print', {
                    templateUrl: 'app/publication/print.html',
                    controller: 'viewPublicationController',
                    resolve: {
                        Publication: function ($route, $q, $rootScope, PublicationService, Publication, navService, MaterialService, Outlines, Sections, OutlineService, Edition, EditionService) {
                            navService.current = "viewPublication";
                            navService.mainHelp = "Publishing";
                            var deferred = $q.defer();
                            Publication.id = parseInt($route.current.params.publicationId);
                            PublicationService.get(Publication)
                                .then(function (pub) {
                                    $rootScope.pageTitle = pub.title + " - Tradamus Digital Publication";
                                    $q.when(EditionService.get({id: pub.edition}))
                                        .then(function () {
                                            return Edition;
                                        });
                                })
                                .then(function (edition) {
                                    return OutlineService.getOutlines(Edition.outlines);
                                })
                                .then(function () {
                                    return MaterialService.getAll(Edition.witnesses);
                                })
                                .then(EditionService.getAnnotations, function (err) {
                                    deferred.reject(err);
                                }).then(function () {
                                deferred.resolve(Publication);
                            }, function (err) {
                                deferred.reject(err);
                            });
                            return deferred.promise;
                        }
                    }
                })
                .when('/publication/:publicationId/json', {
                    template: '<pre>{{pub|json}}</pre>',
                    controller: 'viewPublicationController',
                    resolve: {
                        Publication: function ($route, $q, $rootScope, PublicationService, Publication, navService, MaterialService, Outlines, Sections, OutlineService, Edition, EditionService) {
                            navService.current = "viewPublication";
                            navService.mainHelp = "Publishing";
                            var deferred = $q.defer();
                            Publication.id = parseInt($route.current.params.publicationId);
                            PublicationService.get(Publication)
                                .then(function (pub) {
                                    $rootScope.pageTitle = pub.title + " - Tradamus Digital Publication";
                                    $q.when(EditionService.get({id: pub.edition}))
                                        .then(function () {
                                            return Edition;
                                        });
                                })
                                .then(function (edition) {
                                    return OutlineService.getOutlines(Edition.outlines);
                                })
                                .then(function () {
                                    return MaterialService.getAll(Edition.witnesses);
                                })
                                .then(EditionService.getAnnotations, function (err) {
                                    deferred.reject(err);
                                }).then(function () {
                                deferred.resolve(Publication);
                            }, function (err) {
                                deferred.reject(err);
                            });
                            return deferred.promise;
                        }
                    }
                })
                .otherwise(({redirectTo: '/dashboard'}));
        }]);
tradamus.controller('mainController', function ($scope, $window, HelpService, EditionService, Outlines, Annotations, Lists, Sections, Publication, User, Display) {
    $scope.$on('logout', function (event, user) {
        EditionService.reset();
        Outlines = {};
        Annotations = {};
        Sections = [];
        Publication = {};
        $scope.user = User = user;
        Display = {};
        return true;
    });
    $scope.$on("$routeChangeError", function (event, current, previous, rejection) {
        alert(rejection.config.url + ": " + rejection.statusText);
    });
    $scope.removeFrom = Lists.removeFrom;
    angular.element($window).on('keydown', function (event) {
        if (event.code === "F1") {
            event.stopPropagation();
            event.preventDefault(); // wHY still BUBBLRES!
            $scope.$apply(HelpService.show);
        }
    });
});

angular.module("ngScrollTo", [])
    .directive("scrollTo", ["$window", function ($window) {
            return {
                restrict: "AC",
                compile: function () {

                    var document = $window.document;
                    function scrollInto (idOrName, inside) {//find element with the give id of name and scroll to the first element it finds
                        if (!idOrName || idOrName.length < 3 || idOrName == "dnull") {
                            $window.scrollTo(0, 0);
                            return;
                        }
                        var el;
                        //check if an element can be found with id attribute
                        el = document.getElementById(idOrName);
                        if (!el) {
                            // check for class selector instead
                            if (idOrName.startsWith(".")) {
                                el = document.getElementsByClassName(idOrName.substr(1));
                                if (el && el.length)
                                    el = el[0];
                                else
                                    el = null;
                            } else {
                                //check if an element can be found with name attribute if there is no such id
                                el = document.getElementsByName(idOrName);
                                if (el && el.length)
                                    el = el[0];
                                else
                                    el = null;
                            }
                        }
                        var inside = inside || document.getElementById(inside) || document.getElementsByTagName('html')[0];
//                        if (el) //if an element is found, scroll to the element
//                           el.scrollIntoView();
                        //CUBAP
                        var parent = el;
                        var thisOffset;
                        var offsets = el.offsetTop;
                        do {
                            parent = parent.offsetParent;
                            thisOffset = parent.offsetTop;
                            offsets += thisOffset;
                        }
                        while ((thisOffset > 0) && (parent !== inside))
                        var centering = $window.innerHeight * .4;
                        offsets = Math.max(0, offsets - centering);
                        var animateScroll = function () {
                            inside.scrollTop += Math.round((offsets - inside.scrollTop) / 6);
                            if (Math.abs(offsets - inside.scrollTop) < 5) {
                                window.clearInterval(breakInt);
                                inside.scrollTop = offsets;
                            }
                        };
                        var breakInt = window.setInterval(animateScroll, 33); // ~30FPS
//                        if (inside.scrollTop > 0) {
//                            inside.scrollTop -= $window.innerHeight * .4;
//                        }
                        //otherwise, ignore
                    }

                    return function (scope, element, attr) {
                        element.bind("click", function (event) {
                            var inside = document.getElementById(attr.scrollInside);
                            if(!inside){
                                inside = document.getElementsByClassName(attr.scrollInside)[0];
                            }
                            scrollInto(attr.scrollTo, inside);
                        });
                    };
                }
            };
        }]);