tradamus.service('DraftService', function () {
    var service = this;
});

tradamus.controller('draftController', function ($scope, $filter, Annotations, Edition, Outlines, OutlineService, Materials, MaterialService, $modal, Lists, Display) {
    if (!$scope.materials) {
        $scope.materials = Materials;
    }
    if (!$scope.edition) {
        $scope.edition = Edition;
    }
    $scope.outlines = Outlines;
    $scope.display = Display;
    $scope.annotations = Annotations;
//    $scope.structuralAnnotations = $filter('hasTag')(Annotations,)
    /**
     * Remove section from Edition.outlines collection and update database.
     * @param {model} oid outline object id
     */
    $scope.deleteOutline = function (oid, event) {
        event.stopPropagation();
        $scope.outline = $scope.outlines["id" + oid];
        $scope.modal = $modal.open({
            templateUrl: 'app/draft/deleteWarning.html',
            scope: $scope,
            size: 'lg',
            windowClass: 'bg-danger'
        });
    };
    $scope.informedDelete = function (oid) {
        OutlineService.delete(oid).then(function () {
            if ($scope.display.outline === oid) {
                $scope.display.outline = null;
            }
            $scope.modal.close();
        }, function (err) {
            Display["del_outline_err" + oid] = err.status + ": " + err.statusText;
        });
    };
    $scope.modalForBounds = function (outline) {
        Display.outline = outline;
        if (!$scope.display.material) {
            $scope.display.material = Materials["id" + $scope.edition.witnesses[0]];
        }
        angular.forEach(Materials, function (m) {
            if (!m.annotations) {
                MaterialService.getAnnotations(m.id || m);
            }
        });
        $scope.modal = $modal.open({
            templateUrl: 'app/draft/boundsForm.html',
            scope: $scope,
            size: 'lg'
        });
    };
    $scope.addBound = function (anno, bounds) {
        Lists.addIfNotIn(anno, bounds, "id");
    };
    $scope.containsBounds = function (mid, annoArray) {
        for (var i = 0; i < annoArray.length; i++) {
            if (!annoArray[i].attributes) {
                // deprecated unhelpful outline bound with parallel target and no witness
                return false;
            }
            if (annoArray[i].attributes.targetMaterial == mid) { // 1 is as good as "1"
                return true;
            }
        }
        return false;
    };
    $scope.update = function (outline) {
        OutlineService.setOutline(outline).then(function () {
            Display.outline$dirty = false;
        }, function (err) {
            throw err;
        });
    };
    $scope.included = function (value, arr, field) {
        if (arguments.length < 3) {
            return false;
        }
        for (var i = 0; i < arr.length; i++) {
            if (!(arr[i] && arr[i][field])) {
                continue;
            }
            var trimField = typeof arr[i][field] === "string"
                ? arr[i][field].substr(arr[i][field].indexOf("/") + 1) // returns full value if not found
                : parseInt(arr[i][field]);
            if (trimField == value) {  // 1 is as good as "1"
                return true;
            }
        }
        return false;
    };
    $scope.createOutline = function (label, bounds, id) {
        if (id > 0) { // expect "new" for truly new ones
            return $scope.update({id: id, title: label, bounds: bounds});
        }
        var outline = {
            bounds: bounds,
            index: Edition.outlines.length + 1,
            title: label
        };
        if (!label || !bounds || !bounds.length) {
            $scope.modalForBounds(outline);
            return;
        }
        OutlineService.add(outline).then(function (response) {
            $scope.display.outline = null;
            return true;
        }, function (err) {
            return err;
        });
    };
});

tradamus.controller('outlineSortController', function ($scope, $q, $timeout, Edition, Display, Outlines, OutlineService) {
    $scope.sortableControl = {
//        itemMoved:function(event){},
        containerPositioning: 'relative',
        orderChanged: function (event) {
            var saveAll = [];
            angular.forEach(Edition.outlines, function (o, $index) {
                Outlines["id" + o].index = ++$index; // 1-indexed in database
                saveAll.push(OutlineService.setOutline({id: o, index: $index}));
            });
            $q.all(saveAll).then(function (os) {
                Display.outlineMessage = {
                    type: 'success',
                    msg: "Successfully saved " + os.length + " outlines"
                };
                $timeout(function () {
                    Display.outlineMessage = {};
                }, 3000);
            }, function (err, status) {
                Display.outlineMessage = {
                    type: 'danger',
                    msg: "Failed to update outlines: " + status
                };
                throw err;
            });
        },
        containment: '#contain'
    };
});

tradamus.controller('collationController', function ($scope, $http, $filter, Edition, Witness, Outlines, OutlineService, CollationService, EditionService, Materials, MaterialService, AnnotationService, DecisionService, Lists, _cache, Display, $modal) {
    $scope.display = Display;
    $scope.collation = _cache.collation;
    $scope.outlines = Outlines;
    $scope.display.outline = Outlines["id" + OutlineService.selected];
    $scope.$on("wait", function (event, loader) {
        if (loader && loader.for === "collation") {
            $scope.spinning = true;
        }
    });
    $scope.$on("resume", function (event, loader) {
        if (loader && loader.for === "collation") {
            $scope.collation = _cache.collation;
            $scope.spinning = false;
        }
    });
    $scope.selectSingleVariants = function () {
        var count = 0;
        var decisions = $scope.display.outline.decisions;
        CollationService.groupMotes(decisions);
        angular.forEach(decisions, function (d) {
            if (!d.content && d.motesets.length === 1) {
                d.content = d.motesets[0].content;
                count++;
            }
        });
        $scope.display.singleMsg = count + " choices made";
    };
    $scope.saveAll = function (decisions) {
        DecisionService.saveAll(decisions);
    };
    $scope.new = {};
    $scope.decisionFlesh = function () {
        // prevent $digest loop
        $scope.omittedWitnesses = $scope.getOmittedWitnesses();
    };
    $scope.getAnnoWit = function (anno) {
        return anno.target.substr(anno.target.lastIndexOf('#') + 1);
    };
    $scope.selectDecision = function (index) {
        if (!$scope.display.outline) {
            // outline not loaded yet
            return;
        }
        // direction for animations
        var decisions = $scope.display.outline.decisions;
        CollationService.groupMotes(decisions[index - 1]);
        CollationService.groupMotes(decisions[index]);
        CollationService.groupMotes(decisions[index + 1]);
        // TODO: this should be manageable from the variantListController, but I'm not that smart yet.
        $scope.display.decision = index;
        console.log("New decision #", index);
    };
    if (!($scope.display.decision > -1)) {
        $scope.selectDecision(0);
    }
    CollationService.groupMotes();
    $scope.changeDecision = function (direction) {
        $scope.selectDecision($scope.display.decision + direction);
    };
    $scope.unmadeDecision = function (direction) {
        var decisions = $scope.display.outline.decisions;
        var index = -1;
        for (var i = $scope.display.decision + direction; i > 0 && i < decisions.length; i = i + direction) {
            if (!decisions[i].content) {
                index = i;
                break;
            }
        }
        if (index > -1) {
            $scope.selectDecision(index);
        } else {
            var msg = (direction > 0) ? "after" : "before";
            alert("There are no unmade decisions " + msg + " this one.");
        }
    };
    $scope.pickText = function (decision, wid) {
        var text = '';
        for (var i = 0; i < decision.motes.length; i++) {
            if ($scope.getAnnoWit(decision.motes[i]) === wid) {
                text = decision.motes[i].content;
                break;
            }
        }
        return text;
    };
    /**
     * Variant for collation table visualization
     * @param {Object} decision Decision set of motes from collation
     * @param {number} wid id of Witness
     * @returns {String} text of variant
     */
    $scope.variant = function (decision, wid) {
        var moteText = $scope.pickText(decision, wid);
        var pickText = $scope.pickText(decision, $scope.pick.witness);
        if (!moteText)
            return "omission";
        if (moteText === pickText) {
            return moteText = "✔"; // if text is the same, show no variant
        } else {
            return moteText = $scope.pickText(decision, wid);
        }
    };
    /**
     * Determines type of variant for collation table based on the selected
     * base-text witness and the currently iterated witness. Calls upon
     * $scope.pick.witness which is the selected base-text.
     * @param {type} decision Collection of motes regarding this text block
     * @param {type} wid Iterated witness ID
     * @returns {String}
     */
    $scope.variantType = function (decision, wid) {
        if (!decision || !wid)
            return false;
        var text = $scope.pickText(decision, wid);
        var pickText = $scope.pickText(decision, $scope.pick.witness);
        var dWits = $scope.listWits(decision.motes);
        var type = '';
        if (dWits.indexOf(wid) === -1) {
            // this witness is not in the decision
            type = 'omission';
        } else if (text !== pickText) {
            if (dWits.indexOf(parseInt($scope.pick.witness)) > -1) {
                // this witness does not match the base-text in this decision
                type = 'replacement';
            } else {
                // no text match (possibly undefined) and no entry
                type = 'addition';
            }
        } else {
            type = 'identity';
        }
        return type;
    };
    /**
     * merge mote annotations from selected decision with
     * neighboring annotations.
     * @param {Array} a1 List of annotations in first decision
     * @param {Array} a2 List of annotations in neighbor decision
     * @return {Array} combined New combined annotations
     * @deprecated This has been moved to a service
     */
    var combineMotes = function (a1, a2) {
        var combined = [];
        angular.forEach(a1.concat(a2), function (a) {
            var addTo = {};
            for (var i = 0; i < combined.length; i++) {
                if ($scope.getAnnoWit(combined[i]) === $scope.getAnnoWit(a)) {
                    addTo = combined[i];
                    break;
                }
            }
            if (addTo.target) {
                if (addTo.startPage > a.startPage) {
                    // FIXME: This isn't really a detection for the earliest page
                    // startPage
                    addTo.startPage = a.startPage;
                    addTo.startOffset = a.startOffset;
                } else if (addTo.startPage === a.startPage) {
                    // same page
                    addTo.startOffset = Math.min(addTo.startOffset, a.startOffset);
                } else {
                    // earliest start already recorded
                }
                if (addTo.endPage < a.endPage) {
                    // FIXME: This isn't really a detection for the latest page
                    // endPage
                    addTo.endPage = a.endPage;
                    addTo.endOffset = a.endOffset;
                } else if (addTo.endPage === a.endPage) {
                    // same page
                    addTo.endOffset = Math.max(addTo.endOffset, a.endOffset);
                } else {
                    // latest end already recorded
                }
            } else {
                // witness not in list yet
                combined.push(a);
            }
        });
        return combined;
    };
    /**
     * Combine moteSet with neighbor to expand collation block
     * @param {Object} decision Decision in current selection
     * @param {Integer} neighbor merge forward or backward
     * @returns {Object} merged Decision
     */
    $scope.mergeMoteset = function (decision, neighbor, dIndex) {
        if (!neighbor) {
            throw Error("No neighbor found for Decision");
            return decision;
        }
        var merged = {
            content: "",
            tags: "",
            edition: decision.edition,
            approvedBy: 0,
            annotations: combineMotes(neighbor.motes, decision.motes)
        };
        var spliceIndex = Math.min(neighbor.index, dIndex);
        _cache.get("collation").splice(spliceIndex, 2, merged);
        $scope.display.decision = spliceIndex;
        return merged;
    };
    $scope.showWitness = function (witness, $event) {
        $event.preventDefault();
        $scope.display.witness = witness;
    };
    /**
     * Find all witnessIDs referenced in a Mote
     * @param {Array || Annotation} annos one or more Mote to investigate
     * @returns {Array.<number>} wits id array of each represented witness
     */
    $scope.listWits = function (annos) {
        var annos = annos || $scope.display.outline.decisions[$scope.display.decision];
        annos = (annos instanceof Array) ? annos : [annos];
        var wits = [];
        for (var i = 0; i < annos.length; i++) {
            // for loop, not angular js for want of break/continue;
            if (!annos[i])
                continue; // accidental blank index submitted
            var wit = $scope.getAnnoWit(annos[i]);
            if (wits.indexOf(wit) === -1) {
                wits.push(wit);
            }
        }
        ;
        return wits;
    };

    /**
     * Return (buffer) characters of neighbor text for display from the decisions
     * @param {number:1|-1} direction look forward or backward
     * @param {number} current index of current Decision
     * @param {number} buffer count of characters to include
     * @param {number} i loop catch
     * @return {string} theText truncated text
     */
    $scope.getEditionContext = function (direction, current, buffer, i) {
        var decisions = $scope.display.outline.decisions;
        return ""; // DEBUG
        var i = i || 0;
        var buffer = buffer || 20;
        var theText = '';
        if ((current + direction < 0) || (current + direction > decisions.length)) {
            // out of bounds
            return ':EOF:'; //FIXME for now this is descriptive
        }
        if (i > 10) {
            console.log('Too many iterations of getEditionContent');
            return theText;
        }
        theText = decisions[current + direction].content;
        if (!theText.length) {
            theText = '˽';
        }
        if (theText.length > buffer) {
            theText = (direction > 0) ? theText.substr(0, buffer - 3) + '...' : '...' + theText.substr(theText.length - buffer - 3);
        } else {
            // keep going to fill buffer
            console.log('adding text: ', theText);
            theText = (direction > 0) ?
                theText + ' ' + $scope.getEditionContext(direction, current + direction, buffer - theText.length, ++i) :
                $scope.getEditionContext(direction, current + direction, buffer - theText.length, ++i) + ' ' + theText;
        }
        return theText;
    };
    /**
     * Return (buffer) characters of neighbor text for display from the witness itself
     * @param {Object=Mote} mote from Decision.motesets
     * @param {number:1|-1} direction look forward or backward
     * @param {number} buffer count of characters to include
     * @return {string} theText truncated text, just the first witness listed
     */
    $scope.getMoteContext = function (mote, direction, buffer) {
//    return ' pending ';
        var buffer = buffer || 10;
        var theText = '';
        var pos = {
            startPage: mote.anchors[0].pos[0], // first as default
            startChar: mote.anchors[0].pos[1],
            endPage: mote.anchors[0].pos[2],
            endChar: mote.anchors[0].pos[3]
        };
        var neighbor = $scope.display.outline.decisions[$scope.display.record].motesets[0].cIndex + direction;
        var inBounds = (neighbor > 0) && (neighbor < _cache.get("collation").length);
        if (inBounds) {
            var witness = $scope.getWitnessById(mote.anchors[0].witness);
            if (witness.transcription) {
                if (direction > 0) {
                    theText = witness.transcription.page[pos.endPage].text.substr(pos.endChar, buffer);
                } else {
                    var cut = (pos.startChar < buffer) ? pos.startChar : buffer;
                    theText = witness.transcription.page[pos.startPage].text.substr(pos.startChar - cut, cut);
                }
                if (theText.length > buffer) {
                    theText = (direction > 0) ? theText.substr(0, buffer - 3) + '...' : '...' + theText.substr(theText.length - buffer - 3);
                } else {
                    // keep going to fill buffer
                    console.log('adding mote text: ', theText);
                    theText = (direction > 0) ? theText + $scope.getMoteContext(direction, current + direction, buffer - theText.length, ++i) : $scope.getMoteContext(direction, current + direction, buffer - theText.length, ++i) + theText;
                }
                return theText;
            } else {
                angular.extend(witness, Witness).getTranscription();
            }
        }
    };
    $scope.getSiglum = function (witnessID) {
        var siglum = (witnessID > -1) ? $scope.getWitnessById(parseInt(witnessID)).siglum : false;
        return siglum;
    };
    $scope.getWitnessById = function (id) {
        var witness;
        angular.forEach($scope.edition.witnesses, function (w) {
            if (w.id === id)
                witness = w;
        });
        return witness;
    };
    $scope.getText = function (direction, current, mote, buffer) {
        var decisions = $scope.display.outline.decisions;
        var buffer = buffer || 20;
        var theText = '';
        if (mote) {
            // looking for edition text
            if ((current + direction < 0) || (current + direction > decisions.length)) {
                // out of bounds
                return '';
            }
            theText = decisions[current + direction].content;
        } else {
            // looking for specific witness
            var thisMoteset = decisions[$scope.display.record].motesets[current];
            if (($scope.display.record + direction < 0) || ($scope.display.record + direction > decisions.length)) {
                // out of bounds
                return '';
            }
            theText = $scope.findText(thisMoteset, direction);
            console.log('found text at ' + current + direction, theText);
        }
        if (theText.length > buffer) {
            theText = (direction > 0) ? theText.substr(0, buffer - 3) + '...' : '...' + theText.substr(theText.length - buffer - 3);
        }
        return theText;
    };
    $scope.context = function (mote, direction) {
        var context = '';
        var neighbor = mote.cIndex + direction;
        var inBounds = (neighbor > 0) && (neighbor < _cache.get("collation").length);
        if (inBounds) {
            var witnesses = $scope.edition.witnesses;
            var siglum = mote.anchors[0].siglum;
            $scope.locateWitBySiglum(siglum);
        }
    };
    $scope.findText = function (mote, direction, index) {
        var decisions = $scope.display.outline.decisions;
        var index = (index > -2) ? index : $scope.display.record;
        var foundText = '';
        if (index < 0 || index > decisions.length)
            return '';
        angular.forEach(decisions[index + direction].motesets, function (neighbor) {
            // maybe for loop to allow for break
            if ($scope.hasSigla($scope.listSigla(mote), neighbor)) {
                // found nearby text
                foundText = neighbor.text;
                console.log('found text at ' + index, foundText);
            } else {
                //try again, loop risk
                foundText = $scope.findText(mote, direction, index + direction);
            }
        });
        return foundText;
    };
    $scope.collateSelectedOutline = function (outline) {
        CollationService.collateOutline(outline).then(function (o) {
            $scope.display.outline = o;
        });
    };
    $scope.serverCollation = function (outline) {
        CollationService.collateOutline(outline, true).then(function (location) {
            $scope.collationCode = location.substring(location.indexOf("deliver"));
        });
    };
    $scope.loadCollation = function (outline, location) {
        var index = location.indexOf("deliver");
        if (index < 0) {
            throw Error("Unrecognized location failure. " + location);
        }
        location = location.substring(index);
        return $http.get(location).success(function (collation) {
            _cache.store("collation", collation);
            return $scope.collateSelectedOutline(outline);
        });
    };
    $scope.digestCollation = function (collation) {
        if (!_cache.get("collation") && !collation) {
            return false;
        }
        var collation = collation || _cache.get("collation");
        var decisions = $scope.display.outline.decisions = [];
        var cIndex = 0;
        var iMote = {};
        // We need all the transcriptions loaded first...
        MaterialService.getAll(Materials).then(function (mats) {
            while (cIndex < collation.length) {
                iMote = collation[cIndex];
                var newMoteSet = $scope.getMoteSet(iMote);
                newMoteSet.index = decisions.length;
                decisions.push(newMoteSet);
                console.log(decisions[decisions.length - 1].cIndex);
                cIndex = Math.max.apply(Math, newMoteSet.cIndex) + 1; // check next
            }
            _cache.store("decisions", $scope.groupMotes(decisions));
        }, function (err) {
            return err;
        });
    };
    $scope.getMoteSet = function (startMote) {
//    var moteSet = {
//      content: "",
//      annotations: [],
//      tags: [],
//      index: -1,
//      approvedBy: 0
//    };
        var moteSet = $scope.getAdjacent(1, startMote); // send it forward for the first pass
        // also check for any new motes introduced
        var newMotes = angular.copy(moteSet.cIndex);
        while (newMotes.length !== 1) { // do not recheck original mote
            var newMoteIndex = newMotes.pop();
            var newMote = _cache.get("collation")[newMoteIndex];
            var newMoteSet = $scope.getAdjacent(1, newMote);
            angular.forEach(newMoteSet.cIndex, function (aMote, i) {
                if (newMotes.indexOf(aMote) === -1 && moteSet.cIndex.indexOf(aMote) === -1) {
                    // not yet present in new motes
                    newMotes.push(aMote);
                    moteSet.motes = combineMotes(moteSet.motes, _cache.get("collation")[aMote]);
                    moteSet.cIndex.push(aMote);
                }
            });
        }
        return moteSet;
    };
    /**
     * Check for witness in neighboring Mote.
     * @param {number | Array.<number>} match witnessID to match
     * @param {Object=Mote} toCheck Mote to compare match to
     */
    $scope.hasWitness = function (matchTo, toCheck) {
        var witList = $scope.listWits(toCheck);
        var hasWitness = false;
        matchTo = (matchTo instanceof Array) ? matchTo : [matchTo];
        angular.forEach(matchTo, function (m) {
            if (witList.indexOf(m) !== -1) {
                hasWitness = true;
                return hasWitness;
            }
        });
        return hasWitness;
    };
    $scope.refineAnnotation = function () {
        alert('functionaliy pending... I am sorry.');
    };
    var getText = function (a) {
        var text = "";
        if (a.content) {
            text = a.content;
        } else {
            // get from page
            text = "TODO";
        }
        return text;
    };
    var getAnnoWit = function (anno) {
        var wit = -1;
        if (!anno.target)
            alert('oops');
        wit = anno.target.substr(anno.target.lastIndexOf('#') + 1);
        return wit;
    };
    var getSigla = function (wid) {
        for (var i = 0; i < Edition.witnesses.length; i++) {
            if (Edition.witnesses[i].id === wid) {
                return Edition.witnesses[i].siglum || Edition.witnesses[i].title;
            }
        }
        return "_";
    };
    var setSigla = function (mote) {
        var sigla = mote.witnesses;
        angular.forEach(sigla, function (s) {
            s = getSigla(s);
        });
        return mote.sigla = sigla;
    };
    /**
     * Record of all scrubbed differences when comparing text for collation.
     * @type Array
     */
    var filteredComparison = [
        function (str) {
            return str.replace(/\n/g, ''); // eliminate newlines
        }
        // other functions may include:
        // desensitizing case;
        // dropping <tags>, [brackets], punctuation;
        // equating single and double quotes, ae and æ, etc.
    ];
    var matchContent = function (a, b) {
        for (var i = 0; i < filteredComparison.length; i++) {
            a = filteredComparison[i](a);
            b = filteredComparison[i](b);
        }
        return a === b;
    };
    $scope.toMotesets = function (decision) {
        decision.motesets = [];
        angular.forEach(decision.motes, function (a) {
            var found = false;
            for (var i = 0; i < decision.motesets.length; i++) {
//        if (!decision.motes[0] || getText(a) === decision.motesets[i].content) {
                if (matchContent(getText(a), decision.motesets[i].content)) {
                    // found match, add to mote
                    decision.motesets[i].annotations.push(a);
                    if (!decision.motesets[i].witnesses) {
                        decision.motesets[i].witnesses = [];
                    }
                    decision.motesets[i].witnesses.push($scope.getAnnoWit(a));
                    setSigla(decision.motesets[i]);
                    found = true;
                    break;
                }
            }
            if (!found) {
                // match not found
                decision.motesets.push({
                    content: getText(a),
                    annotations: [a]
                });
                setSigla(decision.motesets[i]);
            }
        });
        return decision.motesets;
    };
    /**
     * Assemble a list of related Motes. Reiterates to find all variants. Default
     * uses current selection as starting point.
     * @param {?number | Array.<number>} witnessID witness reference
     * @param {?number} startAt index of current collation
     * @param {?Object=} toret the MoteSet being assembled
     * @param {?number:1|-1} direction look forward or backward
     * @param {?number} i iterator for tracking distance from original collation
     * @returns {Object=} toret MoteSet including current selection and all neighbors
     */
    $scope.getAdjacent = function (direction, startAt, witnessID, toret, i) {
        // default to current selection
        var witnessID = $scope.listWits(startAt) || witnessID || $scope.listWits($scope.display.mote);
        witnessID = (witnessID instanceof Array) ? witnessID : [witnessID];
        var startAt = startAt || $scope.display.mote;
        var direction = direction || -1;
        var i = i || 1;
        // check for ends of array
        if (!(startAt.index > -1)) { // index is not tracked internally
            startAt.index = _cache.get("collation").indexOf(startAt);
        }
        var inBounds = (startAt.index + direction > -1) && (startAt.index + direction < _cache.get("collation").length);
        if (!toret) {
            toret = {
                content: "",
                motes: startAt,
                cIndex: [startAt.index]
            };
        }
        if (witnessID.length === $scope.edition.witnesses.length) {
            // agreement, get out of this mess.
            return toret;
        }
        if (inBounds) {
            var checking = _cache.get("collation")[startAt.index + direction];
            if (!(checking.index > -1)) {
                checking.index = startAt.index + direction;
            }
            var matchingWitnesses = Lists.intersectArrays(witnessID, $scope.listWits(checking));
            // see if any of these witnesses are in neighbor
            if (matchingWitnesses.length === 0) {
                // checking mote is a variant of this one
                // (the witness is not present in it)
                toret.motes = combineMotes(toret.motes, checking);
                toret.cIndex.push(checking.index);
                if ($scope.listWits(toret.motes).length < $scope.edition.witnesses.length) {
                    // more witnesses to capture, keep going
                    i++;
                }
            } else {
                // ran into earlier text,
                i--; //@FIXME may not be needed
            }
        }
        if ($scope.listWits(toret.motes).length < $scope.edition.witnesses.length) {
            // ran into earlier text or end of list before finding all the witnesses
            // reverse direction
            i++;
            if (direction === 1) {
                // we've been this way before...
                // this is an addition, omission, or otherwise incomplete moteSet
                return toret;
            }
            direction = -direction;
            toret = $scope.getAdjacent(direction, startAt, witnessID, toret, i);
        }
        return toret;
    };
    $scope.getOmittedWitnesses = function (decision) {
        var decision = decision || $scope.display.outline.decisions;
        [$scope.display.decision];
        var wits = $scope.edition.witnesses;
        var omits = [];
        var included = $scope.listWits(decision.motes);
        angular.forEach(wits, function (w) {
            if (included.indexOf(w.id) === -1) {
                omits.push({
                    id: w.id,
                    siglum: w.siglum,
                    title: w.title
                });
            }
        });
        return omits;
    };
    var stopDigest = $scope.$on('resume', function (load) {
        // remove this automatic digester if text is already loaded
        if (!$scope.display.outline.decisions) {
            if (_cache.get("collation"))
                $scope.digestCollation();
        } else {
            stopDigest();
        }
    });
    $scope.setDecisionText = function (text) {
        EditionService.makeDecision(text, $scope.display.record);
    };
    $scope.adjacentSection = function (direction) { //prop, val, cache, idOnly
        var s, outOfBounds;
        var index = $scope.display.outline.index;
        while (!s && !outOfBounds) {
            index += direction;
            if (index > Edition.outlines.length) {
                index = 0;
                outOfBounds = true;
            } else if (index < 0) {
                index = Edition.outlines.length;
                outOfBounds = true;
            }
            s = Lists.getAllByProp("index", index, Outlines)[0];
        }
        return s;
    };
    $scope.nextSection = function () {
        OutlineService.get($scope.display.outline).then(function () {
            var nextIndex = $scope.display.outline.index + 1;
            if (nextIndex + 1 > $scope.edition.outlines.length)
                nextIndex = 0;
            $scope.updateSection($scope.edition.outlines[nextIndex]);
        });
    };
    $scope.previousSection = function () {
        var previousIndex = $scope.display.outline.index - 1;
        if (previousIndex < 0)
            previousIndex = $scope.edition.outlines.length - 1;
        $scope.updateSection($scope.edition.outlines[previousIndex]);
    };
    $scope.updateSection = function (outline) {
        if (!outline) {
            return false;
        }
        var oid = outline.id || outline;
        if ($scope.edition && $scope.edition.outlines) {
            Display.outline = Outlines["id" + oid];
            Display.annotation = null;
            $scope.selectDecision(0);
            Display.savedDecisionsMsg = Display.singleMsg = undefined;
        }
    };
    $scope.outlineMaterials = function (outline) {
        var mats = [];
        angular.forEach(outline.bounds, function (b) {
            if (b.startPage) {
                mats.push(MaterialService.getByContainsPage(b.startPage));
            }
        });
        Display.decision = 0;
        return MaterialService.getAll(mats).then(function (mats) {
            $scope.display["_cache_outline" + outline.id + "materials"] = mats;
        });
    };
    $scope.addManualVariant = function (decision) {
        $scope.availableMaterials = (function () {
            var ms = angular.copy($scope.edition.witnesses);
                var allWits = [];
                angular.forEach(decision.motesets, function (m) {
                    allWits = allWits.concat(m.witnesses);
            });
            angular.forEach($filter('dedup')(allWits), function (m) {
                Lists.removeFrom(m, ms);
            });
            return Lists.dereferenceFrom(ms, Materials);
        })();
        $scope.newVariant = {
            title: "",
            id: "new",
            siglum: ""
        };
        $scope.modal = $modal.open({
            templateUrl: 'app/draft/addVariant.html',
            scope: $scope,
            windowClass: 'bg-info'
        });
    };
    $scope.saveManualVariant = function (newVariant) {
        if (newVariant.id > 0) {
            // selected a known material
            Display.outline.decisions[Display.decision].motes.push({
                content: newVariant.content,
                type: "tr-mote",
                startPage: newVariant.page,
                endPage: newVariant.page,
                startOffset: -1,
                endOffset: -1
            });
            CollationService.groupMotes(Display.outline.decisions[Display.decision]);
        } else {
            MaterialService.new({
                title: newVariant.title,
                siglum: newVariant.siglum
            }).then(function (m) {
                Display.outline.decisions[Display.decision].motes.push({
                    content: newVariant.content,
                    type: "tr-mote"
                });
                CollationService.groupMotes(Display.outline.decisions[Display.decision]);
            });
        }
    };
    $scope.$watch('display.outline', $scope.outlineMaterials);
});

tradamus.directive('variantList', function ($animate, Display) {
    return {
        restrict: "E",
        scope: {decision: '=', isContext: '@'},
        templateUrl: "app/draft/variantList.html",
        controller: 'variantListController',
        link: function (scope, element) {
            scope.$watch('decision', function (newVal, oldVal) {
                var direction;
                var decisions = Display.outline && Display.outline.decisions;
                if (oldVal && newVal && decisions) {
                    direction = decisions.indexOf(newVal) - decisions.indexOf(oldVal);
                }
                var newClass = (direction < 0) ? "descending" : "ascending";
                if (direction * direction > 1) {
                    newClass += " fast";
                }
                element.removeClass('ascending descending fast');
                $animate.addClass(element, newClass);
            });
        }
    };
});