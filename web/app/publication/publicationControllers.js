/* global angular */

tradamus.controller('viewPublicationController', function ($scope, User, Publication, Sections, Display, Edition, $filter, $timeout, $modal, Outlines, RangeService, Annotations, Materials, Lists, CollationService, AnnotationService, UserService, OutlineService, $http) {
    $scope.display = Display;
    $scope.edition = Edition;
    $scope.user = User;
    $scope.materials = Materials;
    $scope.outlines = Outlines;
    $scope.annotations = Annotations;
    $scope.pub = {
        textSections: Lists.getAllByProp("type", "TEXT", Sections),
        noteSections: Lists.getAllByProp("type", "ENDNOTE", Sections),
        indexSections: Lists.getAllByProp("type", "INDEX", Sections),
        allSections: Lists.toArray(Sections),
        title: Publication.title
    };
    Display.section = $scope.pub.textSections[0];
    $scope.sharedSources = function(a,b){
        if (!Display["sources" + a.toString() + "-" + b.toString()]) {
            Display["sources" + a.toString() + "-" + b.toString()] = Lists.intersectArrays(a, b);
        }
        return Display["sources" + a.toString() + "-" + b.toString()];
    };
    var sorts;
    $scope.sortedAnnos = function (aids, cache) {
        if (sorts && sorts.length === aids.length) {
            return sorts;
        }
        sorts = Lists.dereferenceFrom(aids, cache);
//        sorts = Lists.dereferenceFrom(aids, cache).sort(function (a, b) {
//            return parseInt(a.target.split(':')[1]) - parseInt(b.target.split(':')[1]);
//        });
        return sorts;
    };

    $scope.getRules = function (anno, rules, isText) {
        if (anno && rules) {
        var style = [];
        angular.forEach(rules, function (r) {
            if (r.selector.indexOf("type") === 0) {
               // compare and apply type
                if (anno.type === r.selector.substring(5)) {
                   style.push(r.action);
               }
            } else {
                // tag selector
                if (anno.tags&&anno.tags.indexOf(r.selector.substring(4)) > -1) {
                    style.push(r.action);
                }
            }
        });
        if(style.length){
            return style.join("");
        }
        // Suppress
            return isText || "display:none;";
        }
    };
    $scope.applyRules = function (sect) {
        var annos = $scope.visibleAnnotations(sect);
        angular.forEach(annos, function (a) {
            var css = $scope.getRules(a, sect.decoration.concat(sect.layout), sect.type === "TEXT");
            if (css.length) {
                if(a.type!=="tr-decision"&&css.indexOf("display:block")>-1){
                    // spans multiple elements and will apply all these rules to every one, unless...
                    var format = (css.indexOf("padding:.5em 0")>-1)
                    ? "padding:.5em 0;display:block;" : "display:block;";
//                    var r = document.createElement("format-rule");
//                    r.style.cssText = format;
//                    r.classList.add('ruled');
                    var targ=a.target;
                    var firstTargetDecision = document.getElementById("d"+parseInt(targ.substring(targ.lastIndexOf("#") + 1)));
                    var lastTargetDecision = document.getElementById("d"+parseInt(targ.substring(targ.lastIndexOf("-") + 1)));
//                    if(firstTargetDecision===lastTargetDecision){
//                        // no need for a new element, but maybe there are offsets
//                        RangeService.format(a, format);
//                    } else {
var altClass = format.indexOf("padding") > -1 ? "paragraph" : "line";
                        css=css.replace("display:block;","").replace("padding:.5em 0;");
                        if(firstTargetDecision===lastTargetDecision){
                        RangeService.format(a, css,"contained","tr-start"+altClass+" tr-end"+altClass);
                        }
                        else if(firstTargetDecision){
                        RangeService.format(a, css,"start","tr-start"+altClass);
                            // angular.element(firstTargetDecision).parent()[0].insertBefore(r,firstTargetDecision);
                            // no .before() in jQlite
                        }
                        else if(lastTargetDecision){
                            RangeService.format(a, css,"end","tr-end"+altClass);
// angular.element(lastTargetDecision).after(r.cloneNode(false));
                        }
//                    }
                        if(css==="undefined"||css.length<3){return;} //"undefined" if blank, possible stray ';' or whitespace
                }
                RangeService.format(a, css);
            }
        });
    };
    $scope.hasVariants = function(sources_source){
        var sources = angular.copy(sources_source);
        var oid=sources.pop();
        while(oid){
            if(Outlines["id"+oid].bounds.length>1){
                return true;
            }
            oid=sources.pop();
        }
        return false;
    };

    $scope.visibleAnnotations = function (sect) {
        var section = sect;
        if (angular.isArray(section)) {
            section = angular.isObject(section[0]) ? section[0] : Sections["id" + section[0]];
        }
        if (!section) {
            section = Display.section;
        }
        var annos = Display["annos" + section.id] || [];
        if (!annos.length) {
            angular.forEach(section.sources, function (s) {
                annos = annos.concat(Lists.dereferenceFrom(Outlines["id" + s].annotations, Annotations), Outlines["id" + s].decisions);
            });
        }
        return Display["annos" + section.id] = annos;
    };
    $scope.printAnnotations = function (sect) {
        var annos = $scope.visibleAnnotations(sect);
        var list = [];
        angular.forEach(annos, function (a) {
            var rules = $scope.getRules(a, sect.layout);
            if (rules.indexOf("SUPPRESS") + rules.indexOf("display:none") === -2) {
                if (a.type === "tr-decision" && (!a.motesets || a.motesets.length)) {
                    CollationService.groupMotes([a]);
                    if (a.motesets.length > 1) {
                        list.push(a);
                    }
                } else {
                    list.push(a);
                }
            }
        });
        return list;
    };
    $scope.updateNotes = function (annos, sId) {
        return true;
        angular.forEach(annos, function (a) {
            var note = Display['note' + a.id][sId];
            Display['note' + a.id][sId] = (!prefix || isNaN(parseInt(note))) ? prefix + note.substring(1) : prefix + note;
        });
    };
    $scope.annotationTypes = function (section) {
        if (!Display["aTypes" + Display.section.id]) {
            Display["aTypes" + Display.section.id] = Lists.getAllPropValues("type", $scope.visibleAnnotations(section));
        }
        return Display["aTypes" + Display.section.id] || ["default"];
    };
    $scope.annotationTags = function (section) {
        if (!Display["aTags" + Display.section.id]) {
            Display["aTags" + Display.section.id] = Lists.getAllPropValues("tags", $scope.visibleAnnotations(section));
        }
        return Display["aTags" + Display.section.id] || ["default"];
    };
    $scope.resetDefaults = function (except) {
        if (Display.baseText !== except) {
            Display.baseText = "";
        }
        if ($scope.byType !== except) {
            $scope.byType = null;
            RangeService.removeHighlight("byType");
        }
        if ($scope.byTags !== except) {
            $scope.byTags = null;
            RangeService.removeHighlight("byTag");
        }
    };
    /**
     * Filter or highlight by $scope.byType has been selected.
     * @returns {undefined}
     */
    $scope.highlightThese = function (by) {
        var applyClass = (by === "type") ? "byType" : "byTag";
        RangeService.removeHighlight(applyClass);
        angular.forEach($scope.theseAnnos(by), function (anno) {
            RangeService.highlight(anno, true, applyClass);
        });
    };
    $scope.theseAnnos = function (by, section) {
        var annos = (by === "type")
            ? $filter('thisIs')($scope.visibleAnnotations(section), 'type', $scope.byType)
            : $filter('hasTag')($scope.visibleAnnotations(section), $scope.byTags);
        return annos;
    };

    $scope.listAnnos = function (by) {
        $scope.annoList = (angular.isArray(by)) ? by : $scope.theseAnnos(by);
        $scope.modal = $modal.open({
            template: '<div class="modal-body clearfix">'
                + '<a ng-click="modal.close()" class="btn btn-danger pull-right">&times;</a>'
                + '<div ng-repeat="a in annoList" anno-id="{{a.id}}" class="annotation list-group-item text-overflow" '
                + 'ng-click="clickedAnno(a);modal.close()">'
            + '<tags-list item="a" readonly="true" ng-if="!a.content.length" class="pull-right"></tags-list>'
            + '<strong>{{a.attributes.label||$index}}</strong> '
                + '{{a.content || getSelectedText(display.annotation)}}</div></div>',
            scope: $scope
        });
    };
    $scope.attachComment = function (annotation) {
        $scope.annotation = annotation;
        $scope.modal = $modal.open({
            templateUrl: 'app/publication/comment.html',
            scope: $scope,
            controller: 'commentFormController'
        });
    };
    $scope.selectSection = function (index) {
        $scope.movingSection = (index < Display.section.index) ? "animated fadeInLeft" : "animated fadeInRight";
        Display.section = $scope.getSectionByIndex(index, 'TEXT');
        $timeout(function () {
            $scope.applyRules($scope.display.section);
            $scope.movingSection = "";
        }, 1000);
    };
    $scope.getSectionByIndex = function (index,ofType) {
        if(!Display.section||(Display.section.index===undefined)||index<0){
            return false;
        }
        var theSection = Lists.getAllByProp("index", index, Sections)[0] || {};
        if(!theSection){
            // no Section with that index
            return false;
        }
        var direction = index>Display.section.index?1:-1;
        while(ofType && theSection.type!==ofType){
            index += direction;
            if (index > Publication.sections.length - 1) {
                index = 0;
            }
            if (index < 0) {
                index = Publication.sections.length - 1;
            }
            theSection = Lists.getAllByProp("index", index, Sections)[0];
        }
        return theSection;
    };
    $scope.getVariants = function (section) {
        if (Display["variants" + section.id]) {
            return Display["variants" + section.id];
        }
        var variants = [];
        angular.forEach(section.sources, function (s) {
            angular.forEach(Outlines["id" + s].decisions, function (d) {
                if (!d.motesets || d.motesets.length) {
                    CollationService.groupMotes(Outlines["id" + s].decisions);
                }
                for (var m = 0; m < d.motesets.length; m++) {
                    if (d.content !== d.motesets[m].content) {
                        variants.push(d);
                        break;
                    }
                }
            });
        });
        Display["variants" + section.id] = variants;
        return variants;
    };
    $scope.isChosen = function (dContent, thisContent) {
        if (!Display.baseText || Display.baseText.title === "Show default") {
            return true;
        }
        return CollationService.matchContent(dContent, thisContent);
    };
    $scope.groupMotes = CollationService.groupMotes;
    $scope.getAllOverlapping = AnnotationService.getAllOverlapping;
$scope.showWitnessList = function (witnesses) {
        $scope.modal = $modal.open({
            template: '<div class="modal-body clearfix">'
                + '<div class="list-group form-group max-height-15"><span ng-repeat="m in '
                + angular.toJson(witnesses) + '"'
                + 'class="list-group-item">'
                + '<span class="label label-default">{{materials["id"+m].siglum}}</span> '
                + '{{materials["id"+m].title}}</span></div>'
                + '<button class="btn btn-primary pull-right" ng-click="modal.close($event)">Close</button>'
                + '</div>',
            scope: $scope
        });
    };
    $scope.getUsername = function (uid) {
            UserService.fetchUserDetails(uid).success(function (user) {
            Display["attribution" + Display.annotation.id] = user.name;
            }).error(function () {
                Display["attribution" + Display.annotation.id] = "ERROR FETCHING NAME";
            });
            Display["attribution" + Display.annotation.id] = "loading attribution&hellip;";
    };
    $scope.clickedAnno = function (anno, noSelect) {
        RangeService.removeHighlight("alert-info"); // highlighting selection
        if (anno.target) {
            if (anno.type === "tr-decision") {
//                $scope.halo = [anno.id];
            } else {
                var targ = anno.target;
                if (targ.indexOf("outline") > -1) {
                    var outlineId = parseInt(targ.substring(8));
                    var startId = parseInt(targ.substring(targ.lastIndexOf("#") + 1));
                    var endId = parseInt(targ.substring(targ.lastIndexOf("-") + 1));
                    var startOffset = parseInt(targ.substring(targ.indexOf(":", targ.lastIndexOf("#")) + 1));
                    var endOffset = parseInt(targ.substring(targ.lastIndexOf(":") + 1));
                    OutlineService.get(outlineId).then(function (outline) {
                        var i = Lists.indexBy(startId,"id",outline.decisions);
                        var rangeAccompli = false;
//                        $scope.halo = [];
                        if (!startId || i === -1) {
                            return false;
                        }
                        while (!rangeAccompli) {
//                            $scope.halo.push(outline.decisions[i].id);
                            rangeAccompli = outline.decisions[i].id === endId;
                            i++;
                        }
                    });
                }
                RangeService.highlight(anno, false, "alert-info");
            }
            Display["overlap" + anno.id] = AnnotationService.getAllOverlapping(anno);
        }
        if (!noSelect)
        AnnotationService.select(anno);
    };
    $scope.showIndex = function (index) {
        var annos = $scope.visibleAnnotations(index);
        $scope.listAnnos(annos);
    };
    function inversionTest (content, m) {
        var mwords = m.content.split(" ");
        for (var i = 0; i < mwords.length; i++) {
            if (content.indexOf(mwords[i]) === -1) {
                return false;
            }
        }
        return content.length === m.content.length;
    }
    /**
     * Determine action for display in footnotes with variants.
     * Loop to confirm all actions
     * @param {type} anno decision to review
     * @returns {Map} {action:content} for display in footnote
     */
    $scope.variantType = function(anno){
        var action = {};
        // add., om., inv.,  with trailing space or nothing
        var content = anno.content;
        angular.forEach(anno.motesets, function (m) {
            if (m.content !== anno.content) {
                if (m.content.length === 0) {
                    if (action["om. "]) {
                        action["om. "] += m.sigla.join("");
                    } else {
                        action["om. "] = m.sigla.join("");
                    }
                } else
                if (m.content.length > content.length
                    && m.content.indexOf(content) > -1) {
                    if (action["add. "]) {
                        action["add. "] += m.content + " " + m.sigla.join("");
                    } else
                    {
                        action["add. "] = m.content + " " + m.sigla.join("");
                    }
                } else
                if (inversionTest(content, m)) {
                    if (action["inv. "]) {
                        action["inv. "] += m.content + " " + m.sigla.join("");
                    } else {
                        action["inv. "] = m.content + " " + m.sigla.join("");
                    }
                } else {
                    // just a variant
                    if (action["variant"]) {
                        action["variant"] += m.content + " " + m.sigla.join("");
                    } else {
                        action["variant"] = m.content + " " + m.sigla.join("");
                    }
                }
            }
        });
        return action;
    };
    $scope.commentOn = function (anno) {
        if (Display["comment" + anno.id]) {
            return Display["comment" + anno.id];
        }
        for (var i in Annotations) {
            if (Annotations[i].target && Annotations[i].target.indexOf("annotation/" + anno.id) > -1) {
                return Display["comment" + anno.id] = Annotations[i].content;
            }
        }
        ;
        return undefined;
    };
    $scope.pdf = function () {
        var serializer = new XMLSerializer;
        var doc = serializer.serializeToString(document);
        $http.post("pdfs", doc).success(function (data, status, headers) {
            $scope.pdfLocation = headers('Location').substring(1); // trim leading "/" for relative path
        }).error(function () {
            alert("Pee Dee Effing Failed.");
        });
    };
    $scope.resetDefaults();
});
tradamus.directive('format', function (Display, $timeout) {
    return {
        restrict: "A",
        link: function ($scope) {
            $timeout(function () {
                $scope.applyRules(Display.section);
            }, 0, false);
        }
    };
});
//tradamus.controller('styleController', function ($scope) {
//    $scope.theme = {};
//    $scope.themes = [{
//            css: "https://maxcdn.bootstrapcdn.com/bootswatch/3.3.4/cerulean/bootstrap.min.css",
//            label: "Cerulean"
//        }, {
//            css: "https://maxcdn.bootstrapcdn.com/bootswatch/3.3.4/cosmo/bootstrap.min.css",
//            label: "Cosmo"
//        }, {
//            css: "https://maxcdn.bootstrapcdn.com/bootswatch/3.3.4/cyborg/bootstrap.min.css",
//            label: "Cyborg"
//        }, {
//            css: "https://maxcdn.bootstrapcdn.com/bootswatch/3.3.4/darkly/bootstrap.min.css",
//            label: "Darkly"
//        }, {
//            css: "https://maxcdn.bootstrapcdn.com/bootswatch/3.3.4/journal/bootstrap.min.css",
//            label: "Journal"
//        }, {
//            css: "",
//            label: "Default"
//        }];
//    $scope.ctrl = function (e) {
//        $scope.CTRL = e.ctrlKey;
//    };
//});
tradamus.controller('createPublicationController', function ($scope, $modal, $location, Publication, PublicationService, Edition, publicationTypes) {
    $scope.publicationForm = {};
    $scope.showCreateForm = function () {
        $scope.modal = $modal.open({
            templateUrl: 'app/publication/createPublication.html',
            scope: $scope,
            controller: "createPublicationController",
            size: 'lg'
        });
    };
    $scope.createPublication = function () {
        PublicationService.create({
            type: $scope.publicationForm.type || "DYNAMIC",
            title: $scope.publicationForm.title || Edition.title,
            edition: Edition.id
        }, $scope.modal.close);
        // redirect to edit on success
    };
    $scope.pubTypes = publicationTypes;
    $scope.publicationForm.type = "DYNAMIC";
});
tradamus.controller('publicationController', function ($scope, $timeout, Display, $modal, $location, $q, RangeService, Materials, Outlines, OutlineService, Annotations, AnnotationService, CollationService, UserService, PublicationService, Sections, Publication, Lists) {
    $scope.publication = Publication;
    $scope.annotations = Annotations;
    $scope.display = Display;
    Display.section = Display.section || Sections["id" + Publication.sections[0]];
    Display.editionAnnotations = (function () {
        var annos = [];
        angular.forEach(Annotations, function (a) {
            if (a.target && a.target.indexOf("outline") > -1) {
                annos.push(a);
            }
        });
        return annos;
    })();
    $scope.outlines = Outlines;
    $scope.sections = Sections;
    $scope.materials = Materials;
    $scope.annoCount = 0;
    // TODO: put this into scope for reuse within apparatus
    $scope.viewableAnnotations = function () {
        var list = [];
        angular.forEach(Annotations, function (i) {
            if (i.type !== "tr-metadata" && i.type !== "tr-outline-annotation"
                && i.type !== "line" && i.target && i.target != null
                && i.target.indexOf("parallel") === -1
                && (!i.tags || i.tags.indexOf("tr-metadata") === -1)) {
                list.push(i);
            }
            // TODO: check for publication configuration hide annos
        });
        $scope.annoCount = list.length;
        return list;
    };
    $scope.longTitle = $scope.publication.title ?
        $scope.publication.title.length > 40 : $scope.edition.title.length > 40;
    $scope.startDecision = function (a) {
        return a.target && parseInt(a.target.substring(a.target.lastIndexOf('#') + 1));
    };
    $scope.getBoundedText = function (anno) {
        var content = anno.content;
        if (anno.startPage) {
            // get from page
            content = AnnotationService.getBoundedText(anno);
        }
        return content;
    };
    var getIndex = function (id, array) {
        for (var i = 0; i < array.length; i++) {
            if (array[i].id === id) {
                return i;
            }
        }
        return -1;
    };
//    $scope.halo = [];
    var apparatusText = function (anno) {
        var text = {};
        if (anno.content === null || anno.content.length === 0) {
            // no content, so meaning is determined by tags
            if (false)//check for config TODO
            {
            } else {
                text.content = anno.tags;
            }
        } else {
            text.content = anno.content;
        }
        text.label = ""; // TODO: set with line number? something indicative?
        //DEBUG
        if (!text.content || text.content.length < 1) {
            text.content = "Unpreferenced variant";
        }
        anno.display = text;
    };
//    $scope.lightUp = function (d) {
//        if ($scope.halo.indexOf(d) > -1) {
//            return true;
//        }
//        return false;
//    };
    // DEBUG
//    angular.forEach($scope.edition.annotations, function (a) {
//        apparatusText(a);
//    });
//    angular.forEach($scope.edition.outlines, function (o) {
//        angular.forEach($filter('variants')(o.decisions), function (d) {
//            apparatusText(d);
//        });
//    });
    $scope.groupMotes = CollationService.groupMotes;
    $scope.isChosen = function (dContent, thisContent) {
        if (!Display.baseText || Display.baseText.title === "Show default") {
            return true;
        }
        return CollationService.matchContent(dContent, thisContent);
    };
    $scope.getUsername = function (uid) {
        if (!uid) {
            return false;
        }
        return "fetchUserDetails(uid) is bugged"; // TODO
        return UserService.fetchUserDetails(uid).then(function (user) {
            return user.name;
        }, function () {
            return "ERROR FETCHING NAME";
        });
    };
    $scope.varCount = [];
    $scope.sumUp = function (arr) {
        if (!arr || arr.length === 0) {
            return 0;
        }
        return arr.reduce(function (a, b) {
            var total = angular.isArray(a) ? a.length : a;
            return total + b.length;
        });
    };
    $scope.showWitnessList = function () {
        $scope.modal = $modal.open({
            template: '<div class="modalPub pre-scrollable"><a ng-click="modal.close()" class="closeBtn">&times;</a>'
                + '<div class="list-group"><a ng-repeat="w in materials"'
                + 'class="list-group-item">'
                + '<span class="badge">{{w.siglum}}</span>'
                + '{{w.title}}</a></div>',
            scope: $scope
        });
    };
});
tradamus.controller('editPublicationController', function ($scope, $q, Publication, PublicationService, EditionService, OutlineService, $modal, $location, SectionService, Sections, Outlines, Display, Lists, publicationTypes, sectionTypes) {
    var pub = $scope.publication = Publication;
    $scope.pubTypes = publicationTypes;
    $scope.sectionTypes = sectionTypes;
    $scope.publication.type = "DYNAMIC";
    $scope.display = Display;
    if (!$scope.display.section) {
        $scope.display.section = Sections["id" + pub.sections[0]];
    }
    $scope.sections = Sections;
    var ed;
    $q.when(EditionService.get({id: pub.edition})).then(function (edition) {
        ed = $scope.edition;
    }, function (err) {
    });
    $scope.saveTitle = function (title, form) {
        PublicationService.save(pub.id, {"title": title}).then(function () {
            form.$setPristine();
        });
    };
    $scope.pagebreak = function (sIndex) {
        if (sIndex < 1)
            return false;
        return Lists.getAllByProp("index", sIndex - 1, Sections)[0]["type"] !== "TEXT";
    };
    Display.prefixes = [];
    var sects = function () {
        var sids = Publication.sections;
        return $scope.sortedSections = Lists.dereferenceFrom(sids, Sections).sort(function (a, b) {
            return parseInt(a.index) - parseInt(b.index);
        });
    };
    $scope.sortedSections = sects();
    $scope.$on('section-order-changed', sects);
    $scope.getOwner = function (prop) {
        var owner = {
            mail: "unrecorded",
            name: "unrecorded"
        };
        for (var i = 0; i < pub.permissions.length; i++) {
            if (pub.permissions[i].role === "OWNER") {
                var exists = pub.permissions[i][prop];
                owner = exists || pub.permissions[i];
                break;
            }
        }
        return owner;
    };
    $scope.note = function (aid) {
        var note = [];
        angular.forEach(Publication.sections, function (s, i) {
            if (Display['note' + aid] && Display['note' + aid][i]) {
                note.push((Display.prefixes[i] || "") + Display['note' + aid][i]);
            }
        });
        return note.join(",");
    };
    $scope.fetchSections = function (sects) {
        var qall = [];
        angular.forEach(sects, function (s) {
            qall.push(SectionService.get(s));
        });
        return $q.all(qall);
    };
    $scope.getSection = function (sid, prop) {
        var deferred = $q.defer();
        SectionService.get(sid).then(function (s) {
            if (prop) {
                deferred.resolve(s[prop]);
            } else {
                deferred.resolve(s);
            }
        });
        if (!sid) {
            deferred.reject("No Section ID");
        }
        return deferred.promise;
    };
    $scope.fetchOutlines = function (ols) {
        return OutlineService.getOutlines(ols);
    };
    $scope.outlines = Outlines;
    $scope.getOutlines = function (oidArray) {
        return OutlineService.getOutlines(oidArray, true);
    };
    $scope.getOutline = function (oid, prop) {
        var o = OutlineService.get(oid, true);
        if (prop) {
            return o[prop];
        } else {
            return o;
        }
    };
    $scope.addCollaborator = function (c) {
        console.log($scope.form);
        return false;
    };
    $scope.inPublication = function (sid) {
        for (var i = 0; i < pub.sections.length; i++) {
            if (pub.sections[i].id === sid) {
                return true;
            }
        }
        return false;
    };
    $scope.moveSection = function (section, moveDown) {
        if (moveDown) {
            section.index++;
            $scope.publication.sections[section.index - 1].index--;
        } else {
            section.index--;
            $scope.publication.sections[section.index + 1].index++;
        }
        console.log($scope.publication.sections);
    };
    $scope.addToPublication = function (section) {
        var newSection = {id: section.id, title: section.title, index: pub.sections.length};
        if (!$scope.inPublication(section.id)) {
            pub.sections.push(newSection);
        } else {
            pub.sections.splice(pub.sections.indexOf(newSection), 1);
        }
    };
    $scope.removeSource = function (xSource, s) {
        SectionService.get(s).then(function (section) {
            var i = section.sources.indexOf(xSource);
            section.sources.splice(i, 1);
            return SectionService.update(section);
        });
    };
    $scope.removeSection = function (s) {
        if (confirm("Really remove this section? \n" + s.title)) {
            return SectionService.delete(s.id).then(function () {
                Display.section = null;
            sects(); // remove from SortedSections
            });
        }
    };
    $scope.addToSection = function (outline, section) {
        section.sources.push(outline.id);
        return SectionService.update(section);
    };
    $scope.newSect = {};
    $scope.newSection = function (title, type, decoration, layout, outlines, template) {
        var section = {
            publication: Publication.id,
            title: title || $scope.newSect.title || "untitled",
            index: Publication.sections.length,
            type: type || "TEXT",
            decoration: decoration || [],
            layout: layout || [],
            sources: outlines || [],
            template: template || "default"
        };
        return SectionService.add(section).then(function () {
            $scope.newSect.title = "";
            pub.sections.push(section.id);
            sects(); // add to SortedSections
        });
    };
    $scope.discardPublication = function () {
        $scope.modal = $modal.open({
            templateUrl: 'app/publication/deleteWarning.html',
            scope: $scope,
            size: 'lg',
            windowClass: 'bg-danger'
        });
    };
    $scope.informedDelete = function (pid) {
        PublicationService.delete(pid).then(function () {
            PublicationService.getAll(true).then(function () {
                $location.path("/publication");
            }, function (err) {
                Display["del_publication_err" + pid] = err.status + ": " + err.statusText;
            });
            $scope.modal.close();
        });
    };
    $scope.showEditSections = function () {
        $scope.modal = $modal.open({
            templateUrl: 'app/publication/editSections.html',
            scope: $scope,
            controller: "editPublicationController",
            size: 'lg'
        });
    };
    $scope.updateSections = SectionService.updateAll;
    $scope.parseRule = function (action) {
        switch (action) {
            case "display:block;":
                return "Line";
            case "padding:.5em 0;":
                return "Paragraph";
            case "display:none;":
                return "Hidden";
            case "display:inline;":
                return "Inline (default)";
            default:
                return "unknown";
        }
    };
});
tradamus.controller('publicationViewController', function ($scope, $filter, $modal, Outlines, RangeService, Annotations, Materials, Display, Lists) {
});
tradamus.controller('publicationLayoutController', function ($scope, $modal, $q, Lists, SectionService, Outlines, Annotations, AnnotationService) {
    $scope.ignoredAnnotations = [];
    var ignore = function (anno) {
        if (!anno.id) {
            throw "Anno has no ID";
        }
        $scope.ignoredAnnotations.push(anno.id);
    };
    angular.forEach($scope.edition.metadata, function (a) {
        Annotations["id" + a.id] = a;
    });
    $scope.updateSections = SectionService.updateAll;
for(var s in $scope.sections){
            if ($scope.sections.hasOwnProperty(s) && $scope.publication.sections.indexOf($scope.sections[s].id)===-1) {
                // not a section in this pub, drop it
                delete $scope.sections[s];
            }
        }
    $scope.annotations = (function () {
        var allAnnos = angular.copy(Annotations);
        var outlinesInSections = [];
        angular.forEach($scope.sections,function(s){
            angular.forEach(s.sources,function(src){
                Lists.addIfNotIn(src,outlinesInSections);
            });
        });
        angular.forEach(Outlines, function (o) {
            Lists.segregate(o.decisions, Annotations);
        });
        for (var a in allAnnos) {
            var targ;
            var isOutlineAnnotation = function(){
                if(allAnnos[a].target === undefined) {return false;}
                var i = allAnnos[a].target.lastIndexOf("outline/");
                if (i===-1){
                    return false;
                    // not an outline annotation, discard
                } else {
                    targ = parseInt(allAnnos[a].target.substr(i+8));
                    return true;
                }
            };
            if (!isOutlineAnnotation() || allAnnos.hasOwnProperty(a) && outlinesInSections.indexOf(targ)===-1) {
                // not an outline in this pub, drop it
                delete allAnnos[a];
            }
        }
    $scope.annotationTypes = Lists.getAllPropValues('type', allAnnos);
        $scope.annotationTags = Lists.getAllPropValues('tags', allAnnos);
        return $scope.annotations = allAnnos;
    })();
    $scope.selector = $scope.selector || {};
    $scope.selector.overline = false;
    $scope.selector.underline = false;
    $scope.selector.strikethrough = false;
    $scope.selector.subscript = false;
    $scope.selector.superscript = false;
    $scope.selector.layout = '';
    $scope.openStyleForm = function (selector) {
        $scope.selector = selector;
        $scope.selector.sections = [];
        $scope.modal = $modal.open({
            templateUrl: 'app/publication/applyStyle.html',
            controller: 'publicationLayoutController',
            scope: $scope
        });
    };
    $scope.previewStyle = function (sel) {
        var style = {};
        if (sel.layout === 'p') {
            style.display = 'block';
            style.padding = '.5em 0';
        }
        if (sel.layout === 'l') {
            style.display = 'block';
        }
        if (sel.layout === 'x') {
            style.display = 'none';
        }
        if (sel.layout === 'i') {
            style.display = 'inline';
        }
        if (sel.overline) {
            style['text-decoration'] = 'overline';
        }
        if (sel.underline) {
            style['text-decoration'] = 'underline';
        }
        if (sel.italic) {
            style['font-style'] = 'italic';
        }
        if (sel.superscript) {
            style['vertical-align'] = 'super';
            style['font-size'] = '68%';
        }
        if (sel.subscript) {
            style['vertical-align'] = 'sub';
            style['font-size'] = '68%';
        }
        if (sel.strikethrough) {
            style['text-decoration'] = 'line-through';
        }
        if (sel.bold) {
            style['font-weight'] = 'bold';
        }
        return style;
    };
    $scope.saveRules = function (selector) {
        var action = "";
        // rule = { id, section, selector, action, type }
        var ruleSelector = selector.type + ":" + selector.value;
        if (selector.layout === 'suppress') {
            angular.forEach(selector.sections, function (s) {
                var section = SectionService.get(s, true);
                section.layout.push({
                    section: s,
                    selector: ruleSelector,
                    type: "LAYOUT",
                    action: "SUPPRESS"
                });
            });
        } else {
            if (selector.layout && selector.layout !== "") {
                // layout
                switch (selector.layout) {
                    case "p":
                        action += "padding:.5em 0;";
                    case "l":
                        action += "display:block;";
                        break;
                    case "x":
                        action += "display:none;";
                        break;
                    default:
                        action += "display:inline;";
                }
                angular.forEach(selector.sections, function (s) {
                    var section = SectionService.get(s, true);
                    section.layout.push({
                        section: s,
                        selector: ruleSelector,
                        type: "LAYOUT",
                        action: action
                    });
                });
            }
            // decoration
            var action = "";
            if (selector.custom) {
                selector.custom = selector.custom.trim().trim(";");
                action += selector.custom + ";";
            }
            if (selector.overline) {
                action += "text-decoration:overline;";
            } else if (selector.strikethrough) {
                action += "text-decoration:line-through;";
            } else if (selector.underline) {
                action += "text-decoration:underline;";
            }
            if (selector.italic) {
                action += "font-style:italic;";
            }
            if (selector.superscript) {
                action += "vertical-align:super;font-size:68%;";
            }
            if (selector.subscript) {
                action += "vertical-align:sub;font-size:68%;";
            }
            if (selector.bold) {
                action += "font-weight:bold;";
            }
            if (selector.uppercase) {
                action += "text-transform:uppercase;";
            }
        }
        if (action.length) {
            angular.forEach(selector.sections, function (s) {
                var section = SectionService.get(s, true);
                section.decoration.push({
                    section: s,
                    selector: ruleSelector,
                    type: "DECORATION",
                    action: action
                });
            });
        }
        var qall = [];
        angular.forEach(selector.sections, function (s) {
            var section = SectionService.get(s, true);
            qall.push(SectionService.update(section));
        });
        $q.all(qall).then(function () {
            // success
            $scope.modal.close();
        }, function (err) {
            alert(err);
        });
    };
    $scope.getOutline = function (oid) {
        return AnnotationService.getById(oid, $scope.edition.outlines);
    };
    $scope.cleanTextdecoration = function (selector) {
        return (selector.underline ? 1 : 0) + (selector.overline ? 1 : 0) + (selector.strikethrough ? 1 : 0) < 2;
    };
    $scope.cleanPosition = function (selector) {
        return (selector.subscript ? 1 : 0) + (selector.superscript ? 1 : 0) < 2;
    };
    $scope.cleanSelector = function (selector) {
        return $scope.cleanPosition(selector) && $scope.cleanTextdecoration(selector);
    };
    $scope.allSectionIDs = function () {
        if ($scope.selector.sections.length) {
            $scope.selector.sections = [];
        } else {
            $scope.selector.sections = [];
            angular.forEach($scope.publication.sections, function (s) {
                $scope.selector.sections.push(s);
            });
        }
    };
});
tradamus.controller('publicationFootnoteController', function ($scope, Publication, Display, Annotations) {
    $scope.note = function (aid) {
        var note = "";
        angular.forEach(Publication.sections, function (s, i) {
            if (Display['note' + aid] && Display['note' + aid][i]) {
                note += Display.prefixes[i] || "";
                note += Display['note' + aid][i] || "";
            }
        });
        return note;
    };
    $scope.getNotes = function (endIn) {
        if (Display['notes' + endIn]) {
            return Display['notes' + endIn];
        }
        var notes = [];
        angular.forEach(Annotations, function (a) {
            if (!a.target || a.target.indexOf("outline") !== 0) {
// nothing
            } else {
                var d = a.target.lastIndexOf(endIn);
                if (d > -1 && a.target.substr(d).indexOf("-") === -1) {
//                    var offset = parseInt(a.target.substr(a.target.lastIndexOf(":") + 1));
//                    var note = document.createElement("sup");
//                    note.textContent = "{{note(" + a.id + ")}}";
                    // FIXME: placing the node is better, but angular breaks the handling...
                    notes.push(a.id);
                }
            }
        });
        Display['notes' + endIn] = notes;
        return notes;
    };
});

tradamus.directive('trText', function () {
    return {
        restrict: 'A',
        templateUrl: "app/publication/print-text.html",
        scope: {
            section: "="
        }
    };
});
tradamus.directive('trNote', function () {
    return {
        restrict: 'A',
        templateUrl: "app/publication/print-note.html",
        scope: {
            section: "="
        }
    };
});
tradamus.directive('trIndex', function () {
    return {
        restrict: 'A',
        templateUrl: "app/publication/print-index.html",
        scope: {
            section: "="
        }
    };
});
tradamus.directive('footNote',function(){
    return {
        restrict:'E',
        controller: "publicationFootnoteController",
        scope: {annotation: "="},
        replace: true,
        template: "<sup class='footnote' ng-repeat='s in getNotes(annotation)'>{{note(s)}}</sup>"
    };
});