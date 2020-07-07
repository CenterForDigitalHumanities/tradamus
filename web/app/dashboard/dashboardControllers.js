
tradamus.controller('DashboardCtrl', function ($scope, PublicationService, Display) {
    /**
     * Controls main dashboard interactions.
     * @param {scope} $scope
     * @param UserService
     */
// TODO: This may be better as a filter...
    $scope.sortEditionsRecent = function (ed) {
        return recentEdition(ed);
    };
    var tableEds = [];
    var getEditionTableChanges = function () {
        angular.forEach($scope.user.activity, function (act) {
            if (act.table === "EDITIONS")
                tableEds.push(act);
        });
        return tableEds;
    };
    var recentEdition = function (ed) {
        var actions = (tableEds.length) ? tableEds : getEditionTableChanges();
        var time;
        angular.forEach(actions, function (act) {
            if (ed.id === act.id) {
                time = (time > act.time) ? time : act.time;
            }
        });
        return time;
    };
    if (!Display.publicEditions) {
        PublicationService.getShared().success(function (ps) {
            Display.publicEditions = ps;
        }).error(function (err) {
            // list will not load silently
        });
    }
    $scope.display = Display;
});
tradamus.controller('activityController', function ($scope, $filter, User, Lists, Outlines, Sections, Materials, Display, UserService) {
    if (!$scope.user) {
        $scope.user = User;
    }
    if (!$scope.display) {
        $scope.display = Display;
    }
    /**
     * Convert machiney array of activities into human
     * readable and actionable entries.
     *
     * @param {Array} activity objects from user.activity returns from database
     * @returns {Array} formatted and parsed for display
     */
    var parseActivity = function (activity) {
        if (Display.activity && Display.activity[0] && Display.activity[0].time === activity[0].time) {
            return Display.activity;
        }
        var entries = [];
        /**
         * full of {entries} like
         *     action: String "You | updated | the title of | section
         *         | 'Gimli' | in your publication"
         *     href: String "#/publication/32/edit"
         *     btnLabel: String "Edit Publication"
         *     date: String "13 seconds/minutes/hours ago" or "yesterday" or
         *         "Wednesday" or "3:55 AM" or "06/15/15"
         */
        // build action
        angular.forEach(activity, function (a) {
            var content = a.content;
            try {
                content = JSON.parse(content);
            } catch (e) {
                // nothing
            }
            var action = "";
            // agent
            if (a.user === User.id) {
                action += "You";
            } else {
                action += "Someone else";
            }
            // act [UPDATE, DELETE, INSERT, VIEW]
            action += " " + ((a.operation + "ed").replace("Ee", "e")).toLowerCase().replace("insert", "add");
            if (a.entity.startsWith("annotation") && content.tags.indexOf("tr-metadata") > -1) { // known things
                // metadata annotation
                action += " metadata ";
            } else {
            // attribute (if available)
                if (Object.keys(content).length === 1) {
                    var attr = Object.keys(content)[0];
                    switch (attr) {
                        case "index":
                            attr = "the order";
                            break;
                        case "title":
                            attr = "the title";
                            break;
                    }
                    action += " " + attr + " of ";
            }
            // target
            var vowels = ["a", "e", "i", "o", "u"];
            var ent = a.entity.substring(0, a.entity.indexOf("/"));
            if (vowels.indexOf(ent.substring(0, 1)) > -1) {
                ent = "an " + ent;
            } else {
                ent = "a " + ent;
            }
                action += " " + ent + " ";
            // target label (if available)
            if (Object.keys(content).length === 1) {
                if (content.title) {
                    action += " " + content.title;
                } else if (content.label) {
                    action += " " + content.label;
                } else if (content.attributes && content.attributes.label) {
                    action += " " + content.attributes.label;
                }
            } else if (typeof content === "string") {
                action += content;
                }
            }
            // parent (if available)
            if (a.parent) {
                var p = a.parent.split("/");
                if (a.operation === "INSERT") {
                    var preposition = " to "
                }
                switch (p[0]) {
                    case "annotation" :
                    case "manifest" :
                    case "transcription" :
                        action += " in a " + p[0];
                        break;
                    case "canvas":
                        action += " on a " + p[0];
                        break;
                    case "edition":
                        var ed = Lists.getAllByProp("id", p[1], User.editions);
                        if (ed[0] && ed[0].title) {
                            action += (preposition || " from ") + ed[0].title;
                        } else {
                            action += (preposition || " from ") + "an edition";
                        }
                        break;
                    case "publication":
                        var pub = Lists.getAllByProp("id", p[1], User.publications);
                        if (pub[0] && pub[0].title) {
                            action += (preposition || " in ") + pub[0].title;
                        } else {
                            action += (preposition || " in ") + "a publication";
                        }
                        break;
                    case "outline" :
                        if (Outlines["id" + p[1]]) {
                            action += (preposition || " in ") + Outlines["id" + p[1]].title;
                        } else {
                            action += (preposition || " in ") + "an outline";
                        }
                        break;
                    case "page":
                        for (var m in Materials) {
                            if (m.transcription && m.transcription.pages) {
                                var page = Lists.getAllByProp("id", p[1], m.transcription.pages)[0];
                            }
                            if (page && page.title) {
                                action += " on " + page.title;
                                break;
                            } else {
                                action += " on a page";
                            }
                        }
                        break;
                    case "witness":
                        if (Materials["id" + p[1]]) {
                            action += (preposition || " in ") + Materials["id" + p[1]].title;
                        } else {
                            action += (preposition || " in ") + "a witness";
                        }
                        break;
                    case "section":
                        if (Sections["id" + p[1]]) {
                            action += " in " + Sections["id" + p[1]].title;
                        } else {
                            action += " in a section";
                        }
                        break;
                }
            }
            // build href and btnLabel
            var href = "";
            var btnLabel = "link";
            if (a.entity) {
                var e = a.entity.split("/");
                switch (e[0]) {
                    case "publication":
                        href = "/edit";
                    case "edition":
                        href = "#/" + a.entity + href;
                        btnLabel = "edit";
                        break;
                    case "section":
                        if (content.publication) {
                            href = "#/publication/" + content.publication + "/edit";
                            btnLabel = "edit";
                        } // otherwise edition and publication ids are not available
                        break;
                    case "manifest" :
                    case "transcription" :
                        if (content.edition) {
                            href = "#/edition/" + content.edition;
                            btnLabel = "edit";
                        } // otherwise edition ids are not available
                        break;
                    case "witness":
                        href = "#/material/" + e[1];
                        btnLabel = "edit";
                        break;
                    case "outline" :
                        if (content.edition) {
                            href = "#/draft/" + content.edition;
                            if (content.decisions && content.decisions.length) {
                                href += "/collation/" + e[1];
                            }
                            btnLabel = "edit";
                        } // otherwise edition ids are not available
                    case "page":
                    case "canvas":
                    case "annotation" :
                    default:
                        // leave it undefined and a gap will be left in view
                }
            }

            // build date maybe "13 seconds/minutes/hours ago" or "yesterday" or "Wednesday" or "3:55 AM" or "06/15/15"
            var date;
            var ago = Date.now() - Date.parse(a.time);
            if (ago < 120000) { // last 2 minutes
                date = "moments ago";
            } else if (ago < 7.2e+6) { // less than 2 hours
                date = Math.ceil(ago / 6e+4) + " minutes ago";
            } else if (ago < 1.728e+8) { // less than 2 days
                date = Math.ceil(ago / 3.6e+6) + " hours ago";
            } else if (ago < 6.048e+8) { // under a week
                date = Math.floor(ago / 8.64e+7) + " days ago";
            } else { // more than a week
                date = $filter('date')(a.time, 'shortDate');
            }

            entries.push({
                action: action.replace("  ", " "),
                href: href,
                btnLabel: btnLabel,
                date: date,
                id: a.time
            });
        });
        Display.activity = entries;
        return entries;
    };
    $scope.$watch('user.id', function () {
        if (User.id > 0) {
            UserService.getActivityLog(15).success(parseActivity).error(function (err) {
        return err;
            });
        }
    });

});


