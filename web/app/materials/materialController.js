/* global angular */

tradamus.service('MaterialService', function ($q, $http, $rootScope, $timeout, Materials, Annotations, Edition, TranscriptionService, Lists, PageService, CanvasService, ManifestService, TagService, Messages, Display) {
    var service = this;
    this.getAll = function (materialArray, noAJAX) {
        if(materialArray && materialArray[0].then){ // probably promises
            return $q.all(materialArray);
        }
        var allMaterials = [];
        angular.forEach(Materials, function (m) {
            allMaterials.push(service.get(m,noAJAX));
        });
        return $q.all(allMaterials);
    };
    this.get = function (witness, noAJAX) {
        var wid = witness.id || witness;
        var w = service.getById(wid);
        if (noAJAX) {
            return w;
        }
        if (!w || !w.transcription) {
            return service.fetch(wid);
        } else {
            return $q.when(w);
        }
    };
    this.getById = function (wid) {
        var w = parseInt(wid);
        if (w > 0) {
            w = Materials["id" + w];
            if (w && !w.transcription && service.material) {
                if (w.id === service.material.id) {
                    Materials["id" + w.id] = service.material;
                }
            }
            return w;
        }
        return false;
    };
    this.getByContainsPage = function (pid, noAJAX) {
        var deferred = $q.defer();
        for (var i in Materials) {
            if (!Materials[i]) {
                // Possible loading error or other issue left a blank node
                delete Materials[i];
                continue;
            }
            if (Materials[i].transcription && Materials[i].transcription.pages) {
                // transcription is loaded
                for (var j = 0; j < Materials[i].transcription.pages.length; j++) {
                    if (Materials[i].transcription.pages[j].id == pid) { // 1 or "1"
                        if (noAJAX) {
                            return Materials[i];
                        }
                        deferred.resolve(Materials[i]);
                        break;
                    }
                }
            } else if (noAJAX) {
                return {};
            } else {
                // no transcription loaded, determine how deep
                var getThis = (Materials[i].transcription) ?
                    service.getTranscription : service.fetch;
                getThis(Materials[i], true).then(function () {
                    // load each and recheck
                    return service.getByContainsPage(pid);
                }).then(function (w) {
                    if (w) {
                        deferred.resolve(w);
                    } else {
                        deferred.reject("No Material found");
                    }
                });
            }
        }
        return deferred.promise;
    };
    this.new = function (material, cfg, params) {
        $rootScope.$broadcast('wait', 'Waiting for material creation...');
        var config = cfg || {};
        if (material["@context"]) {
            config.headers = {
                'Content-Type': 'application/ld+json;charset=UTF-8' // override default json
            };
        }
        var url = "edition/" + Edition.id + "/witnesses";
        if (params) {
            url += "?" + params;
        }
        return $http.post(url, material, config)
            .success(function (data, status, headers) {
                var loc = headers('Location');
                var mid = parseInt(loc.substring(loc.lastIndexOf('/') + 1));
                service.get(mid).then(function (m) {
                    Materials["id" + m.id] = m;
                    Edition.witnesses.push(m.id);
                    $rootScope.$broadcast('resume');
                });
            })
            .error(function (data, status) {
                var errorMessage = angular.element(data)[3].innerHTML;
                Messages.addMaterial = {
                    type: 'danger',
                    messsage: errorMessage
                }
                console.log('failed to save ' + (material.title || material.label || '[no title]') + ' to edition:' + status);
                $rootScope.$broadcast('resume');
            });
    };

    this.set = function (witness, props) {
        angular.extend(witness, props);
        Materials["id"+witness.id] = witness;
        Lists.addIfNotIn(witness.id, Edition.witnesses);
        return witness;
    };
    this.fetch = function (witness, noCache) {
        var wid = (witness.id) ? witness.id : witness; // witness may be {witness} or just INT
        var deferred = $q.defer();
        $http.get("witness/" + wid, {cache: !noCache})
            .then(function (wit) {
                witness = service.set(wit.data);
                Lists.segregate(witness.metadata, Annotations);
                return witness;
            }, function (error) {
                // TODO handle 404, 401, 500
                console.log('Failed to get witness details', error);
            })
            .then(function () {
                return $q.all([
                    service.getCanvases(witness, "all", true).then(function(){
                        return service.getTranscription(witness, true);
                        // canvases first so pages can fill in if null
                    }),
                    service.getAnnotations(witness.id)
                ]);
            }).then(function (all) {
            deferred.resolve(witness);
        });
        return deferred.promise;
    };
    this.remove = function (mid) {
        if (!mid) {
            throw Error("No ID for delete.");
        }
        return $http.delete("witness/" + mid)
            .success(function () {
                delete Materials["id" + mid];
                var i = Lists.indexBy(mid, null, Edition.witnesses);
                if (i > -1) {
                    Edition.witnesses.splice(i, 1);
                }
            }).error(function (error) {
            alert(error);
        });
    };
    this.save = function (material, props) {
        var data = props ? props : material;
        if (material) {
            return $http.put("witness/" + material.id, data)
                .success(function () {
                    return true;
                }).error(function (err) {
                return err;
            });
        } else {
            return $q.reject("No material to save");
        }
    };
    this.getAnnotations = function (wid, noCache) {
        return $http.get("witness/" + wid + "/annotations", {cache: !noCache})
            .success(function (annos) {
                Display.addType(annos);
                TagService.addTagsFrom(annos);
                Materials["id" + wid].annotations = annos;
                Lists.segregate(Materials["id" + wid].annotations, Annotations);
//                $timeout(function () {
//                    Materials["id" + wid].annotations = annos;
//                    // FIXME: this horrible timeout is because something is resetting Annotations before the next $digest and I cannot find it.
//                    Lists.segregate(Materials["id" + wid].annotations, Annotations);
//                }, 2000);
                return Annotations;
            }).error(function (err) {
            return Error("Failed to fetch annotations for material.");
        });
    };
    /**
     * Load canvases from manifest.
     * @param {Witness} w Witness whose manifest to use for lookup
     * @param {int} getThisIndex The position of the canvas to get. "all" for complete set.
     * @param {boolean} force Force ajax even when object exists
     * @returns {unresolved} promise then Canvas or [Canvas]
     */
    this.getCanvases = function (m, getThisIndex, force, noCache) {
        var deferred = $q.defer();
        if (force || !angular.isObject(m.manifest.canvasses)) {
            ManifestService.get(m).then(function (manifest) {
                service.set(m, {manifest: manifest});
                var allCanvasses = [];
                if (getThisIndex === 'all') {
                    angular.forEach(m.manifest.canvasses, function (c) {
                        allCanvasses.push(CanvasService.fetch(c, noCache));
                    });
                    $q.all(allCanvasses).then(function (canvases) {
                        // returns array of canvasses
                        var these = [];
                        angular.forEach(canvases, function (c) {
                            these.push(c.data);
                        });
                        angular.extend(m.manifest.canvasses, these);
                        deferred.resolve(m.manifest.canvasses);
                    }, function (err) {
                        console.log(err);
                        deferred.reject(m.manifest.canvasses[getThisIndex]);
                    });
                } else {
                    var thisCanvas = findItemByPosition(m.manifest.canvasses, getThisIndex)
                    if (thisCanvas) {
                        CanvasService.fetch(thisCanvas, noCache).then(function (canvas) {
                            deferred.resolve(CanvasService.set(thisCanvas, canvas.data));
                        });
                    } else {
                        deferred.reject();
                    }
                }
            });
        } else {
            if (getThisIndex === 'all') {
                deferred.resolve(m.manifest.canvasses);
            } else {
                deferred.resolve(m.manifest.canvasses[getThisIndex]);
            }
        }
        return deferred.promise;
    };
    var fetchPages = function (material, getThisIndex, deferred) {
        var allPages = [];
        if (getThisIndex === 'all') {
            angular.forEach(material.transcription.pages, function (p) {
                allPages.push(PageService.get(p));
            });
        } else {
            allPages.push(PageService.get(material.transcription.pages[getThisIndex]));
        }
        $q.all(allPages).then(function (pages) {
            // returns array of pages
            if (getThisIndex === 'all') {
                material.transcription.pages = sortedByIndex(pages);
                deferred.resolve(material.transcription.pages);
            } else {
                deferred.resolve(material.transcription.pages[getThisIndex]);
            }
        }, function (err) {
            deferred.log(err);
            deferred.resolve(material.transcription);
        });
        return deferred.promise;
    };
    this.getPages = function (w, getThisIndex, force) {
        var deferred = $q.defer();
        return service.getTranscription(w, force).then(function ( ) {
            return fetchPages(w, getThisIndex, deferred);
            });
        return deferred.promise;
    };
    var sortedByIndex = function (array) {
        var toret = [];
        var leftovers = [];
        if (!angular.isArray(array))
            return [array];
        angular.forEach(array, function (a) {
            if (a.data && a.data.id) {
                a = a.data;
            }
            if (parseInt(a.index) > -1) {
                toret[parseInt(a.index)] = a;
            } else {
                leftovers.push(a);
            }
        });
        return toret.concat(leftovers);
    };
    this.getTranscription = function (w, force) {
        var deferred = $q.defer();
        if (force || !angular.isObject(w.transcription)) {
            // fetch transcription from id
            TranscriptionService.fetch(w.transcription, force).then(function (transcription) {
                deferred.resolve(service.set(w, {transcription: transcription}));
            });
        } else {
            // has details and full pages
            deferred.resolve(w.transcription);
        }
        return deferred.promise;
    };
    this.getPageByCanvas = function (canvasId, material) {
        if (!material) {
            material = service.material;
            // TODO possible lookup for async lookup in MaterialService.getByContainsPage()
        }
        var index = Lists.indexBy(canvasId, 'canvas', material.transcription.pages);
        if (index > -1) {
            return material.transcription.pages[index];
        }
        return false;
    };
    var findItemByPosition = function (array, position) {
        var itemIndex = 0;
        angular.forEach(array, function (item, index) {
            if (item.index === position) {
                itemIndex = index;
            }
        });
        return array[itemIndex];
    };
    this.firstCanvas = function (w) {
        var deferred = $q.defer();
        if (w.manifest && w.manifest.canvasses && angular.isObject(w.manifest.canvasses[0])) {
            deferred.resolve(findItemByPosition(w.manifest.canvasses, 0));
        } else {
            this.getCanvases(w, 0, true).then(function (canvas) {
                deferred.resolve(canvas);
            });
        }
        return deferred.promise;
    };
    this.firstPage = function (w) {
        if (w.transcription && w.transcription.pages && w.transcription[0]) {
            return w.transcription.pages[0];
        } else {
            // no transcription loaded
            return this.getPages(w, 0, true);
        }
    };
});

tradamus.controller('materialsController', function ($scope, $q, Materials, EditionService, MaterialService, Display, $modal, Messages) {
    if (!$scope.materials) {
        $scope.materials = Materials;
    }
    if (!Messages.addMaterial) {
        Messages.addMaterial = {};
    }
    $scope.$watchCollection('Messages.addMaterial', function () {
        $scope.msg = Messages.addMaterial;
    });
    $scope.material = $scope.material || Display.material;
    $scope.hasKeys = function (obj) {
        return Object.keys(obj).length > 0;
    };
    $scope.addMaterial = function (mat, config, params) {
        return MaterialService.new(mat, config, params);
    };
    $scope.create = function (mat) {
        var params, config, m;
        if (mat.text && mat.text.length) {
            m = mat.text;
            var params = "title=" + mat.title + "&siglum=" + mat.siglum;
            if (mat.lineBreak) {
                params += "&lineBreak=" + mat.lineBreak;
            }
            if (mat.pageBreak) {
                params += "&pageBreak=" + mat.pageBreak;
            }
            config = {
                headers: {
                    'Content-Type': 'text/plain;charset=UTF-8' // override default json
                }
            };
        } else {
            m = {
                title: mat.title,
                siglum: mat.siglum
            };
        }
        return $scope.addMaterial(m, config, params).then(function () {
            $scope.data.manually = null;
        });
    };
    $scope.updateMaterial = MaterialService.save;
    $scope.updateTitles = function (material) {
        MaterialService.save(material, {title: material.title, siglum: material.siglum})
            .then(function () {
                $scope.title.$setPristine();
            }, function (err, status) {
                console.log(status + " " + err);
            });
    };
    var editionId = ($scope.edition && $scope.edition.id > 0)
        ? $scope.edition.id
        : $scope.material.edition;
    EditionService.get({id: editionId}).witnesses;
    $scope.deleteMaterial = function (mid) {
        $scope.material = Materials["id" + mid];
        $scope.modal = $modal.open({
            templateUrl: 'app/materials/deleteWarning.html',
            scope: $scope,
            size: 'lg',
            windowClass: 'bg-danger'
        });
    };
    $scope.informedDelete = function (mid) {
        MaterialService.remove(mid).then(function () {
            $scope.modal.close();
        }, function (err) {
            Display["del_material_err" + mid] = err.status + ": " + err.statusText;
        });
    };
    $scope.editMaterialForm = function (material) {
        $scope.material = material;
        $scope.modal = $modal.open({
            templateUrl: 'app/materials/materialsForm.html',
            controller: 'materialsController',
            scope: $scope,
            size: 'lg'
        });
    };
    /**
     * Creates a new Witness from the uploaded file.
     * @returns {Boolean} false on failure
     */
    $scope.importMaterial = function (mat, type) {
        var config = {};
        switch (type) {
            case "JSON" :
                config = {
                    headers: {
                        'Content-Type': 'application/json'
                    }
                };
                break;
            case "XML" :
                // DEBUG
                config = {
                    headers: {
                        'Content-Type': 'text/xml'
                    }
                };
                break;
            default :
                throw "Improper file format";
                return false;
        }
        MaterialService.new(mat, config).then(function () {
            // TODO set success msg
            $scope.modal.close("Created Witness");
        }, function (err) {
            if (err.status === 500) {
                alert("The server was not able to create anything from this document. The standards are robust, but this tool may be fragile. Consider the text entry form for simpler input or first creating a IIIF-compliant object at rerum.io and then importing it here.");
            } else {
                alert("Error " + err.status + ": something went wrong. No material was created.");
            }
        });
    };
    $scope.inputType = "link";
    var loadMaterials = function () {
        var ms = [];
        angular.forEach($scope.edition.witnesses, function (m) {
            if (parseInt(m)) {
                ms.push(MaterialService.get(m));
            }
        });
        return $q.all(ms).then(function (materials) {
            $scope.materials = materials;
        });
    };
    $scope.checkForMaterials = function () {
        if ($scope.edition.id === 0) {
            return EditionService.get($scope.edition).success(function (edition) {
                return loadMaterials();
            });
        } else if ($scope.edition.witnesses.length !== Object.keys($scope.materials).length) {
            return loadMaterials();
        } else {
            return true;
        }
    };
});
tradamus.service('MaterialImportService', function ($http, $rootScope, User, Messages, Edition) {
    var partner = [{name: 'tpen', location: 'http://www.t-pen.org/TPEN'}];
    var service = this;
    this.getLink = function (index) {
        return partner[index].location;
    };
    this.list = [];
    this.fetchRemote = function () {
        Messages.set("import", "Projects loading...", 0);
        $rootScope.$broadcast('wait-load', "Projects loading...");
        return $http.get(partner[0].name + '/projects?user=' + User.mail, {'ignoreAuthModule': true})
            .success(function (projects) {
                service.list = projects;
                Messages.set("import", "Select projects below to import into this Edition as Witnesses.", 200);
                $rootScope.$broadcast('resume');
                return projects;
            })
            .error(function (error, status) {
                if (status === 403) {
                    Messages.set("import", "Login to " + partner[0].name + " failed. Try authenticating at that" +
                        " site in another tab before retrying.", status);
                } else if (status === 401) {
                    Messages.set("import", "Login to " + partner[0].name + " failed. User/password was not found.", status);
                } else {
                    Messages.set("import", status + " Error: unable to import project list from " + partner[0].location, status);
                }
                $rootScope.$broadcast('resume');
                console.log(error);
            });
    };
    this.fetchRemoteDetails = function (pid) {
        if (pid) {
            $rootScope.$broadcast('wait-load');
            return $http.get(partner[0].name + '/' + pid + '?user=' + User.mail)
                .success(function (project) {
                    $rootScope.$broadcast('resume');
                    return project;
                })
                .error(function (error) {
                    alert('Unable to load remote witness at: ' + partner[0].name + '/' + pid + '?user=' + User.mail);
                    console.log('Failed to connect to ' + partner[0].name + pid, error);
                    $rootScope.$broadcast('resume');
                });
        } else {
            throw Error('Project ID not provided');
        }
    };
    this.importFromLocation = function (pid) {
        if (pid) {
            $rootScope.$broadcast('wait-load');
            return $http.post('edition/' + Edition.id + '/witnesses?src=' + partner[0].location + '/' + pid + '?user=' + User.mail)
                .success(function (project) {
                    $rootScope.$broadcast('resume');
                    return project;
                })
                .error(function (error) {
                    console.log('Failed to connect remote witness at: ' + partner[0].location + pid, error);
                    $rootScope.$broadcast('resume');
                });
        } else {
            throw Error('Project ID not provided');
        }
    };
});

tradamus.controller('importRemoteMaterialController', function ($scope, $filter, Messages, MaterialImportService, MaterialService) {
    /**
     * Connecting to remote servers (like T-PEN) and import witnesses.
     * @todo Move messages to Messages Service
     */

    /**
     * Import message for display to user.
     * @field
     * @returns {String} Messages.import.message
     */
    $scope.msg = function () {
        return Messages.import;
    };
    $scope.import = $scope.import || {};
    $scope.imports = [];
    $scope.import.list = MaterialImportService.list;
    $scope.import.link = MaterialImportService.getLink(0);
    /**
     * Load remote details for each requested witness and adds it to the Edition.
     * @returns {Boolean} false on failure
     */
    $scope.importMaterials = function (imports, closeFn) {
        if (!imports) {
            imports = $scope.imports;
        }
        angular.forEach(imports, function (pid) {
            // T-PEN returns an id that is "projects/4345" but that's 404,
            // it should be "project/4345"
            pid = pid.replace('jects/', 'ject/');
    if (pid) {
    MaterialImportService.importFromLocation(pid)
        .success(function (data, status, headers) {
        var loc = headers('Location');
            var mid = parseInt(loc.substring(loc.lastIndexOf('/') + 1));
                        MaterialService.get(mid);
                        if (closeFn) {
                            closeFn();
                        }
        }).error(function(err){return err; });
            }
        });
    };
        $scope.importsLength = function () {
        var count = 0;
        angular.forEach($scope.imports, function (pid) {
            if (pid) {
            count++;
            }
        });
        return count;
    };
    if (MaterialImportService.list.length < 1) {
    MaterialImportService.fetchRemote().then(function (projects) {
    $scope.import.list = MaterialImportService.list;
        // should be set in Service
    }, function (err) {
    // should be set in Service
    });
    }
});
