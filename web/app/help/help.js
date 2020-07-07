/* global angular, Markdown */

tradamus.service('HelpService', function ($modal, $http, $q, $rootScope) {
    var service = this;
    var articles = [];
    this.getTopics = function () {
        return topics;
    };
    this.getAllArticles = function () {
        if (articles.length) {
            return $q.when(articles);
        }
        return this.getWiki().then(function (entry) {
            return articles = entry.pages;
        });
    };
    this.getWiki = function (page, onlyText) {
        var directUrl = (page)
            ? "http://sourceforge.net/rest/p/tradamus/wiki/" + page
            : "http://sourceforge.net/rest/p/tradamus/wiki";
        var url = (page)
            ? "sf/wiki/" + page
            : "sf/wiki";
        var req;
        try {
            req = $http.get(directUrl);
        } catch (err) {
            console.warn("No CORS");
            req = $http.get(url);
        }
        return req.then(function (res) {
            var entry = res.data;
            if (entry.pages) {
                return entry;
            }
            if (entry.text) {
                // received single article, otherwise list of all articles
                var includes = entry.text.split("[[include ref=");
                var qall = [];
                angular.forEach(includes, function (i) {
                    var ref = i.indexOf("]]");
                    if (ref > -1) {
                        var title = i.substring(0, ref);
                        if (title !== 'header') {
                            qall.push(service.getWiki(title, true)
                                .then(function (res) {
                                    res.text += i.substring(ref + 2);
                                    return res.text || res;
                                })
                                );
                        }
                    } else {
                        qall.push($q.when(i));
                    }
                });
                return $q.all(qall).then(function (entries) {
                    if (onlyText) {
                        return entries[0];
                    }
                    entry.text = entries.join("");
                var converter = new Markdown.Converter();
                    var html = angular.element(converter.makeHtml(entry.text));
                    var as = html.find('a');
                    angular.forEach(as, function (aDOM) {
                        var a = angular.element(aDOM);
                        if (!a.attr('href') || a.attr('href').startsWith('http'))
                            return;
                        if (a.attr('href').startsWith('#')) {
                            a.attr('scroll-to', a.attr('href').substring(1));
                            a.attr('scroll-inside', 'modal');
                        } else {
                            a.attr('ng-click', "help('" + a.attr('href') + "')");
                        }
                        a.removeAttr('href');
                    });
                    var tmp = document.createElement('tmp');
                    entry.template = angular.element(tmp).append(html).html();
                    return entry;
                }, function () {
                    return "Loading content failed.";
                });
            };
        }, function () {
            return "Loading content failed.";
        });
    };
    var showHelp = false;
    this.show = function () {
        showHelp = !showHelp;
        $rootScope.$broadcast('toggle-help', showHelp);
    };
});

tradamus.controller('helpController', function ($scope, HelpService, $modal, $rootScope) {
    $scope.help = function (help, event) {
        event && event.stopPropagation(); // FIXME: menu clicks still bubble
        HelpService.message = help;
        HelpService.getWiki(help).then(function (res) {
            $scope.entry = res;
            $scope.modalInstance && $scope.modalInstance.close();
            $scope.modalInstance = $modal.open({
                templateUrl: 'app/help/help-shell.html',
                controller: 'helpController',
                size: 'lg',
                scope: $scope
            });
        });

    };
    HelpService.getAllArticles().then(function (topics) {
        $scope.topics = topics;
    });
    $scope.message = HelpService.message;
    $scope.toggleHelp = HelpService.show;
    $scope.$on('toggle-help', function (event, show) {
        $rootScope.showHelp = show;
        // controller isn't attaching scope to small buttons
    });
});

tradamus.controller('landingController', function ($scope, $sce, HelpService) {
    HelpService.getWiki('Welcome').then(function (data) {
        $scope.welcome = $sce.trustAsHtml(data.template);
    });
    HelpService.getWiki('How%20To%20Use').then(function (data) {
        $scope.using = $sce.trustAsHtml(data.template);
    });
    HelpService.getWiki('Publishing').then(function (data) {
        $scope.publishing = $sce.trustAsHtml(data.template);
    });
});

tradamus.directive('wikiLink', function (HelpService, $compile, $sce) {
    return {
        restrict: "E",
        link: function ($scope, element) {
            $scope.$watch('entry.template', function () {
                if ($scope.entry.template) {
                    element.html($sce.trustAsHtml($scope.entry.template));
                    $compile(element.contents())($scope);
                }
            });
        }
    };
});

tradamus.directive('trHelp', function () {
    return {
        restrict: "E",
        scope: {
            topic: '@',
            message: '@',
            detail: '@'
        },
        replace: true,
        controller: "helpController",
        templateUrl: "app/help/helpBtn.html"
    };
});