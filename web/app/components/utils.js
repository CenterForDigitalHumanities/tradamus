angular.module('utils', [])
    .service('Lists', function () {
        var service = this;
        this.indexBy = function (val, byProp, arr) {
            if (byProp) {
            if (arguments.length === 3) {
                for (var i = 0; i < arr.length; i++) {
                    if (arr[i][byProp] === val) {
                        return i;
                    }
                }
                }
            } else {
                return arr.indexOf(val);
            }
            return -1;
        };
        this.addIfNotIn = function (entry, arr, byProp) {
            var foundAt = -1;
            if (!arr) {
                arr = [entry];
            }
            if (!entry) {
                return arr;
            }
            if (byProp === undefined) {
                foundAt = arr.indexOf(entry);
            } else {
                for (var i = 0; i < arr.length; i++) {
                    if (entry[byProp] === arr[i][byProp]) {
                        foundAt = i;
                        break;
                    }
                }
            }
            if (foundAt > -1) {
                arr.splice(foundAt, 1, entry);
            } else {
                arr.push(entry);
            }
            return arr;
        };
        this.removeFrom = function (entry, arr, byProp) {
            if (!arr || !entry) {
                return false;
            }
            var index = service.indexBy(entry, byProp, arr);
            if (index > -1) {
                arr.splice(index, 1);
                return true;
            }
            return false;
        };
        this.segregate = function (toClean, cache) {
            angular.forEach(toClean, function (i, index) {
                if (angular.isObject(i) && i.id) {
                    if (cache['id' + i.id]) {
                        angular.extend(cache['id' + i.id], i);
                    } else {
                        cache['id' + i.id] = i;
                    }
                    toClean[index] = i.id;
                }
            });
        };
        this.toArray = function (obj) {
            var list = [];
            angular.forEach(obj, function (item) {
                list.push(item);
            });
            return list;
        };
        this.dereferenceFrom = function (idArray, cache) {
            var list = [];
            if (idArray.length === 0) {
                return list;
            }
            angular.forEach(idArray, function (id, index) {
                if (id.id) {
                    // already an object, so no need for the cache
                    // FIXME: partially dereferenced things will break this
                    list[index]=id;
                } else {
                    list[index] = cache["id" + id];
                }
            });
            return list;
        };
        this.getAllByProp = function (prop, val, cache, idOnly) {
            var list = [];
            angular.forEach(cache, function (item, key) {
                if (item[prop] == val) { // "1" or 1 is fine
                    if (idOnly) {
                        list.push(key);
                    } else {
                        list.push(item);
                    }
                }
            });
            return list;
        };
        function getTags (cache) {
            var tags = [];
            angular.forEach(cache, function (item) {
                if (item.tags) {
                    var t = item.tags.split(" ");
                    while (t.length) {
                        service.addIfNotIn(t.pop(), tags);
                    }
                }
            });
            return tags;
        }
        ;
        this.getAllPropValues = function (prop, cache, exceptTags) {
            var list = [];
            if (!exceptTags) {
                exceptTags = [];
            }
            if (prop === "tags") {
                list = getTags(cache);
                angular.forEach(exceptTags, function (t) {
                    var i = list.indexOf(t);
                    if (i > -1) {
                        list.splice(i, 1);
                    }
                });
            } else {
            angular.forEach(cache, function (item) {
                for (var i = 0; i < exceptTags.length; i++) {
                    if (item.tags && item.tags.indexOf(exceptTags[i]) > -1) {
                        return false;
                    }
                }
                service.addIfNotIn(item[prop], list);
                });
            }
            return list;
        };
        this.intersectArrays = function (arrayA, arrayB) {
            var a = arrayA.slice(0);
            var toret = [];
            while (a.length) {
                var i = arrayB.indexOf(a.pop());
                if (i > -1) {
                    toret.push(arrayB[i]);
                }
            }
            return toret;
        };
    });