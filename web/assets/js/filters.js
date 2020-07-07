tradamus.filter('dedup', function() {
    /*
     * Remove duplicates from returned list for display.
     * Pass parameters in AngularJS with :param notation
     * @param {Array} items list to be filtered
     * @param {String} byProp filter based on property instead of compare
     * @param {Boolean} breakArray drill into arrays of arrays
     * @returns {Array}
     */
    return function(items, byProp, breakArray) {
        var deduped = [];
        if (byProp) {
            angular.forEach(items, function(item) {
                if (item && item[byProp]) {
                    if (breakArray) {
                        while (item[byProp].length) {
                            var singleDeep = item[byProp].pop();
                            if (deduped.indexOf(singleDeep) === -1) {
                                deduped.push(singleDeep);
                            }
                        }
                    } else if (byProp === "tags") {
                        // split tags to test
                        var tags = item[byProp].trim().split(" ");
                        var each;
                        while (tags.length) {
                            each = tags.pop();
                            if (deduped.indexOf(each) === -1) {
                                deduped.push(each);
                            }
                        }
                    } else if (deduped.indexOf(item[byProp]) === -1) {
                        deduped.push(item[byProp]);
                    }
                }
            });
        } else {
            angular.forEach(items, function(item) {
                if (deduped.indexOf(item) === -1) {
                    deduped.push(item);
                }
            });
        }
        return deduped;
    };
});
tradamus.filter('listAs', function () {
    return function (items, prop) {
        if (!prop) {
            return items;
        } else {
            var list = [];
            angular.forEach(items, function (i) {
                list.push(i[prop]);
            });
            return list;
        }
    };
});
tradamus.filter('thisIs', function() {
    /*
     * Filter only matching types for display.
     * Pass parameters in AngularJS with :param notation
     * @param {Array} items list to be filtered
     * @param {String} key property to test
     * @param {String|Number} val value to match
     * @returns {Array}
     */
    return function(items, key, val) {
        var list = [];
        if (!key || !val) {
            return list;
        }
        angular.forEach(items, function(i) {
            if (i[key] === val) {
                list.push(i);
            }
        });
        return list;
    };
});
tradamus.filter('hideInternals', function() {
    /**
     * Filter out certain prefixes of tags and annotation types to hide
     * Tradamus internal or other prefixes from display and manipulation.
     *
     * @param {Array} items all items
     * @param {string=} [override=tr-] prefix other than default to use
     * @param {...string=} [allow=tr-userAdded] add any argument to allow an
     *      otherwise excluded entry
     * @returns {Array} filtered list
     */
    return function(items, override) {
        var list = [];
        var prefix = override || "tr-"; // Tradamus namespaced types.
        var allow = (arguments.length > 2) ?
            arguments.slice(2) :
            ["tr-userAdded"];
        angular.forEach(items, function(i) {
            if (i.type && i.type.indexOf(prefix) === -1 || allow.indexOf(i.type) > -1) {
                list.push(i);
            }
        });
        return list;
    };
});
tradamus.filter('metadata', function () {
    return function (items) {
        var list = [];
        angular.forEach(items, function (i) {
            if (i.tags === "tr-metadata") {
                list.push(i);
            }
        });
        return list;
    };
});
tradamus.filter('justAnnos', function () {
    return function (items) {
        // convert [OBJ] to sneak past angularJS prejudice against objects
        items = items[0];
        var list = [];
        angular.forEach(items, function (i) {
            if ((i.type !== "tr-metadata" && i.type !== "tr-outline-annotation")
                && (!i.tags || i.tags.indexOf("tr-metadata") === -1)) {
                list.push(i);
            }
        });
        return list;
    };
});
tradamus.filter('variants', function (CollationService) {
    return function (items) {
        var list = [];
        angular.forEach(items, function (i) {
            if (!i.motesets || i.motesets.length) {
                CollationService.groupMotes(items);
            }
            for (var m = 0; m < i.motesets.length; m++) {
                if (i.content !== i.motesets[m].content) {
                    list.push(i);
                    break;
                }
            }
        });
        return list;
    };
});
tradamus.filter('pageOrFollowing', function () {
    /*
     * Returns transcription pages >= current page.
     * Pass parameters in AngularJS with :param notation
     * @param {type} list All pages
     * @param {type} num starting page
     * @param {type} prop index property to compare
     * @returns {Array} filtered list
     */
    return function(list, num, prop) {
        var gt = [];
        if (prop) {
            angular.forEach(list, function(l) {
                if (l[prop] >= num) {
                    gt.push(l);
                }
            });
        } else {
            angular.forEach(list, function(l) {
                if (l >= num) {
                    gt.push(num);
                }
            });
        }
        return gt;
    };
});
tradamus.filter('collaborators', function() {
    /*
     * Removes public user and NONE from list of collaborators.
     * @returns {Array} filtered list
     */
    return function(users) {
        var list = [];
        angular.forEach(users, function(user) {
            if ((user.user !== 0) &&
                (user.role !== 'NONE')) {
                list.push(user);
            }
        });
        return list;
    };
});
tradamus.filter('unused', function () {
    var orId = function (v) {
        if (angular.isArray(v)) {
            var cs = [];
            angular.forEach(v, function (c) {
                cs.push(c.id);
            });
            return cs;
        } else {
            return v.id || v;
        }
    };
    /*
     * Returns only unused tags for display.
     * Pass parameters in AngularJS with :param notation
     * @param {Array} mask list of used tags to omit
     * @returns {Array} filtered list
     */
    return function (list, mask) {
        var mask = mask || [];
        var unused = [];
        angular.forEach(list, function (entry) {
            if (orId(mask).indexOf(orId(entry)) === -1) {
                unused.push(entry);
            }
        });
        return unused;
    };
});
tradamus.filter('unusedById', function () {
    /*
     * Returns only unused items for display.
     * Pass parameters in AngularJS with :param notation
     * @param {Array} mask list of used items to omit
     * @returns {Array} filtered list
     */
    return function (list, mask) {
        var mask = mask || [];
        var unused = [];
        angular.forEach(list, function (entry) {
            if (mask.indexOf(entry.id) === -1) {
                unused.push(entry);
            }
        });
        return unused;
    };
});
tradamus.filter('onlyNumbers', function () {
    return function (array) {
//        var numbers = [];
//        angular.forEach(array, function (i) {
//            if (!isNaN(i)) {
//                numbers.push(i);
//            }
//        });
        return array.filter(function (i) {
            return !isNaN(i);
        });
    };
});
tradamus.filter('splitTags', function () {
    return function (tagsString) {
        if (!tagsString) {
            return [];
        }
        if (angular.isArray(tagsString)) {
            return tagsString;
        }
        return tagsString.trim().split(" ");
    };
});
tradamus.filter('hasTag', function () {
    return function (items, tag, resolve, resolveFrom, pre) {
        var list = [];
        angular.forEach(items, function (item) {
            if (resolve && resolveFrom) {
                item = resolveFrom[pre + item];
            }
            if (item.tags && item.tags.indexOf(tag) > -1) {
                list.push(item);
            }
        });
        return list;
    };
});
tradamus.filter('inRange', function () {
    /*
     * check for annotations in range of a list of IDs
     */
    return function (list, ids) {
        var valid = [];
        angular.forEach(list, function (i) {
            if (ids.indexOf(i.id) === -1) {
                valid.push(i);
            }
        });
        return valid;
    };
});
//tradamus.filter('ignoreMotes', function (whitespace, caps, ))