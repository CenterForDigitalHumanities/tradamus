/* global angular */

/**
 * @author cubap@slu.edu
 * @file AngularJS controller functions
 * @copyright Copyright 2011-2014 Saint Louis University
 * @license http://www.osedu.org/licenses/ECL-2.0
 * @disclaimer Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
tradamus.value('publicationTypes', {
    PDF:
        {value: "PDF", label: "PDF file preparation", icon: "fa-file-pdf-o"},
    TEI:
        {value: "TEI", label: "TEI/XML document", icon: "fa-file-code-o"},
    DYNAMIC:
        {value: "DYNAMIC", label: "Interactive website", icon: "fa-globe"},
    OAC:
        {value: "OAC", label: "OAC/IIIF JSON-LD", icon: "fa-share-alt"},
    XML:
        {value: "XML", label: "XML document", icon: "fa-code"}});
tradamus.value('sectionTypes', {// Ignoring TABLE_OF_CONTENTS and FOOTNOTE
    TEXT:
        {label: "text", description: "This section will be included in the body text of the publication as collated or transcribed.", icon: "fa-file-text"},
    ENDNOTE:
        {label: "apparatus", description: "This section will be placed as an apparatus, containing the annotations from the included sources.", icon: "fa-comment"},
    INDEX:
        {label: "list", description: "The annotations in these sources will be placed in a sorted list as an appendix.", icon: "fa-list"}});
tradamus.controller('newEditionCtrl', function ($scope, EditionService) {
    /**
     * Holds scope for new, empty, top-level Edition objects.
     * @param {scope} $scope
     * @param EditionService
     */

    /**
     * Sets title to string and calls service to create new Edition.
     * @returns {location} path to new Edition
     */
    $scope.newEdition = function () {
        EditionService.set({title: $scope.title});
        EditionService.create();
    };
});
tradamus.controller('updateUserController', function ($scope, UserService, User) {
    $scope.updating = angular.copy(User);
    /**
     * Update existing user account information.
     * mail, name, password (if updating)
     * @uses {model} $scope.user User
     * @returns {HTTP}
     */
    $scope.updateUser = function () {
        if ($scope.updating.password && $scope.updating.password !== $scope.updating.confirm) {
            alert('passwords do not match'); // TODO: modal error message
            return false;
        }
        var tosend = {
            mail: $scope.updating.mail,
            name: $scope.updating.name
        };
        if ($scope.updating.password) {
            tosend.password = $scope.updating.password;
        }
        UserService.update(tosend).then(function () {
            $scope.updating.password = $scope.updating.mail = "";
            $scope.modal.close();
        }, function () {
        });
    };
});
tradamus.controller('annotationDetailsController', function ($scope, AnnotationService, PageService, Selection, Display) {
    /**
     * Holds scope for viewing and updating details of an annotation.
     */
    $scope.selected = Selection;
    $scope.display = Display;
    $scope.deleteAnno = function () {
        AnnotationService.delete($scope.selected.annotation);
    };
    var getDecision = function (id, array) {
        if (!array) {
            var array = $scope.display.outline.decisions;
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
    $scope.getSelectedText = function () {
        var text = "";
        if ($scope.selected && $scope.selected.annotation) {
            if ($scope.selected.annotation.target && $scope.selected.annotation.target.indexOf('-') > -1) {
                // TODO: reliable way to get decision-type thing here outline/12#6-14:25
                var targ = $scope.selected.annotation.target;
                var startId = parseInt(targ.substring(targ.lastIndexOf("#") + 1));
                var endId = parseInt(targ.substring(targ.lastIndexOf("-") + 1));
                var startOffset = parseInt(targ.substring(targ.indexOf(":", targ.lastIndexOf("#")) + 1));
                var endOffset = parseInt(targ.substring(targ.lastIndexOf(":") + 1));
                if (startId === endId) {
                    var startD = getDecision(startId);
                    if (startD.content.length === 0) {
                        startD.content = $scope.showUndecided(startD);
                        startD.tags += (" tr-autoSelected");
                    }
                    text = "“" + startD.content.substring(startOffset, endOffset) + "”";
                } else {
                    var startD = getDecision(startId);
                    var endD = getDecision(endId);
                    if (startD.content.length === 0) {
                        startD.content = $scope.showUndecided(startD);
                        startD.tags += (" tr-autoSelected");
                    }
                    if (endD.content.length === 0) {
                        endD.content = $scope.showUndecided(endD);
                        startD.tags += (" tr-autoSelected");
                    }
                    text = "“" + getDecision(startId).content.substring(startOffset) + " "
                        + getDecision(endId).content.substring(0, endOffset) + "”";
                    // TODO: include text between
                }
            } else if ($scope.selected.annotation.type === "tr-decision") {
                text = ($scope.selected.annotation.content.length) ? "“" + $scope.selected.annotation.content + "”" : "( undecided )";
            } else if ($scope.selected.annotation.startPage) {
                var page = PageService.getById($scope.selected.annotation.startPage, $scope.witness.transcription.pages);
                if (page.text) {
                    text = "“" + page.text.substring($scope.selected.annotation.startOffset, $scope.selected.annotation.endOffset) + "”";
                }
            } else {
                text = "type: " + $scope.selected.annotation.type;
            }
        }
        return text;
    };
    $scope.deselect = function () {
        AnnotationService.select(null);
    };
});
tradamus.controller('collationTableController', function ($scope) {
    $scope.file = {};
    $scope.table = {};
    $scope.previewContent = function () {
        var dlm = $scope.table.delimiter || " ";
        $scope.table.data = $scope.file.content.split(dlm);
        $scope.table.header = $scope.edition.witnesses;
    };
    $scope.updateTableData = function () {
        $scope.previewContent();
    };
    $scope.addNote = function () {
        // TODO: Make the note an annotation on the decision
    };
});
tradamus.controller('rangeSelectorController', function ($scope, Selection, Annotations) {
    $scope.annotations = Annotations;
    $scope.selected = Selection;
    $scope.hideWitnesses = [];
    $scope.rebuildText = function () {
        var page = $scope.selected.page;
        var text = page.text;
        $scope.brText = [{
                id: page.id,
                text: text
            }];
        var br = $scope.selected.breakOn;
        if (br) {
            $scope.brText = [];
            angular.forEach($scope.annotations, function (a) {
                if (a.type === br
                    && a.startPage <= page.id
                    && a.endPage >= page.id) {
                    var startOffset = (a.startPage === page.id)
                        ? a.startOffset
                        : 0;
                    var endOffset = (a.endPage === page.id)
                        ? a.endOffset
                        : text.length;
                    var add = {
                        id: a.id,
                        text: text.substring(startOffset, endOffset)
                    };
                    $scope.brText.push(add);
                }
            });
        }
        return $scope.brText;
    };
    $scope.newCollator = {
        startPage: null,
        startOffset: null,
        endPage: null,
        endOffset: null
    };
    $scope.bookendText = function (direction) {
        if (!Selection.annotation) {
            return false;
        }
        var text = false;
        if (direction === 1) {
            text = Selection.page.text.substr(Selection.annotation.startOffset, 15) + "...";
        } else {
            text = "..." + Selection.page.text.substr(Selection.annotation.endOffset - 15, 15);
        }
        return text;
    };
    $scope.lockIn = function (key, unlock) {
        if (!Selection.annotation) {
            return false;
        }
        $scope.newCollator.witness = unlock ? "" : Selection.witness;
        if (key === "end") {
            $scope.newCollator.endPage = unlock ? "" : Selection.annotation.endPage;
            $scope.newCollator.endOffset = unlock ? "" : Selection.annotation.endOffset;
            $scope.newCollator.endText = unlock ? "" : $scope.bookendText(-1);
        } else if (key === "start") {
            $scope.newCollator.startPage = unlock ? "" : Selection.annotation.startPage;
            $scope.newCollator.startOffset = unlock ? "" : Selection.annotation.startOffset;
            $scope.newCollator.startText = unlock ? "" : $scope.bookendText(1);
        } else {
            throw Error("Invalid key: expected 'start' or 'end'");
        }
    };
    $scope.saveCollator = function () {
        $scope.addCollator($scope.newCollator);
        Selection.deselect(["witness", "page", "annotation", "breakOn"]);
        $scope.save();
    };
});
tradamus.controller('selectCollateController', function ($scope, EditionService, WitnessService, PageService, $modal) {
    /**
     * Selecting specific ranges to feed to the Collation.
     * @requires each Witness to have a transcription to collate
     */
    $scope.content = $scope.content || {};
    angular.forEach($scope.edition.witnesses, function (w, i) {
        if (!w.transcription) {
            WitnessService.fetch(w, true, true, true).then(function (witness) {
                WitnessService.set($scope.edition.witnesses[i], witness.data);
            });
        }
    });
    /**
     * Add another range selector to the group.
     * @uses {Array} $scope.collator group of range selectors
     * @alters {Array} $scope.collator
     */
    $scope.addCollator = function (c) {
        if (!$scope.content.collator) {
            $scope.content.collator = [c];
        } else {
            $scope.content.collator.push(c);
        }
    };
    $scope.removeCollator = function (c) {
        $scope.content.collator.splice($scope.content.collator.indexOf(c), 1);
    };
    $scope.witnessTitle = function (page) {
        var w = WitnessService.getByContainsPage(page, true);
        return w.title;
    };
    $scope.bookendText = function (collator) {
        var w = WitnessService.getByContainsPage(collator.startPage, true);
        var sPage = PageService.getById(collator.startPage, w.transcription.pages);
        var ePage = PageService.getById(collator.endPage, w.transcription.pages);
        collator.startText = sPage.text.substr(collator.startOffset, 15) + "&hellip;"
        collator.endText = "&hellip;" + ePage.text.substr(collator.endOffset - 15, 15);
        return collator.startText + " " + collator.endText;
    };
    $scope.openCollatorForm = function (collator) {
        $scope.modal = $modal.open({
            templateUrl: 'assets/partials/forms/rangeSelector.html',
            controller: 'selectCollateController',
            scope: $scope
        });
    };
    $scope.close = function (collator) {
        $scope.modal.close(function () {
            // collator cleared
            collator = {};
        });
    };
    $scope.save = function () {
        $scope.modal.close(function () {
            // collator changes already saved
            console.log('Saved collator');
        });
    };

    /**
     * Sends collator collection to collation endpoint. Scrubs additional
     * properties from the request.
     * @see {@link https://sourceforge.net/p/tradamus/wiki/Tradamus%20Server%20API/#11-collation Collation API}
     * @returns {location} path for reviewing collation
     */
    $scope.collate = function () {
        if ($scope.content.collator[0].startPage) {
            var toSend = [];
            angular.forEach($scope.content.collator, function (c) {
                toSend.push({
                    startPage: c.startPage.id,
                    startOffset: c.startOffset,
                    endPage: c.endPage.id,
                    endOffset: c.endOffset
                });
            });
            EditionService.collate(toSend);
        } else {
            EditionService.collate();
        }
    };
});
tradamus.controller('editImageController', function ($scope, $q) {
    /**
     * Alter image selectors and annotations.
     */
    $scope.choice = $scope.selected.canvas.images[0];
    $scope.updateImageDimensions = function () {
        var i = new Image();

        i.onload = function () {
            $scope.choice.height = i.height || -1;
            $scope.choice.width = i.width || -1;
            i.onload = null;
            $scope.$apply();
        };
        i.src = $scope.choice.uri;
    };
    $scope.addImage = function () {
        var newImage = angular.copy($scope.selected.canvas.images[0]);
        angular.extend(newImage, {
            id: 'new',
            uri: 'no image',
            index: $scope.selected.canvas.images.length + 1
        });
        $scope.selected.canvas.images.push(newImage);
        $scope.choice = $scope.selected.canvas.images[$scope.selected.canvas.images.length - 1];
    };
    $scope.removeImage = function () {
        var choiceIndex = $scope.selected.canvas.images.indexOf($scope.choice);
        $scope.selected.canvas.images.splice(choiceIndex, 1);
        $scope.choice = $scope.selected.canvas.images[0];
    };
});
tradamus.controller('modalCtrl', function ($scope, Edition, User) {
    /**
     * Display modal messages to users that must be resolved to continue.
     */
    $scope.modal = {};
    $scope.modal.isShown = false;
    $scope.$on('wait', function (event, msg) {
        /**
         * Listen for any undescribed wait event and display message.
         * @todo Specify listeners, allow chaining of asynchs
         */
        $scope.modal.isShown = true;
        $scope.modal.message = msg;
    });
    $scope.$on('resume', function () {
        /**
         * Listen for any resume event and remove modal.
         * @todo Specify listeners, allow chaining of asynchs
         */
        $scope.modal.isShown = false;
        $scope.modal.message = '';
    });
    /**
     * Check for Edition permissions.
     * @returns {Boolean} true if allowed
     */
    var checkEdition = function () {
        var isMember = function () {
            if (User.id === Edition.creator)
                return true;
            for (var i = 0; i < Edition.permissions.length; i++) {
                if (Edition.permissions[i].user === User.id ||
                    Edition.permissions[i].user === 0) {
                    return true;
                    break;
                }
            }
            ;
            if (User.id < 1)
                return true;
            return false;
        };
        if (!isMember()) {
            $scope.modal.isShown = true;
            $scope.modal.message = 'You are not a member of this Edition.';
        }
    };
    $scope.$watch(Edition, function () {
        checkEdition;
    });
    checkEdition();
});
tradamus.controller('loaderCtrl', function ($scope) {
    /**
     * Inline loaders for specific AngularJS broadcasts.
     */
    $scope.loading = {
        isShown: false,
        message: "loading..."
    };
    $scope.$on('wait-load', function (event, loader) {
        /**
         * Update loader element with message on specific AngularJS broadcasts.
         * Typically in response to an HTTP request.
         */
        if (loader && loader.for === $scope.for) {
            $scope.loading = {
                isShown: true,
                message: loader.message
            };
        }
    });
    $scope.$on('resume', function (event, loader) {
        /**
         * Clears specific loader elements.
         */
        if (loader && loader.for === $scope.for) {
            $scope.loading = {};
        }
    });
});
tradamus.controller('loginCtrl', function ($scope, UserService, User, $route, $modal) {
    /**
     * Basic User access and creation.
     */

    /**
     * Checks if home dashboard view is visible.
     * @returns {Boolean} true if on home page
     */
    $scope.onHome = function () {
        return $route.current.$$route && $route.current.$$route.templateUrl.indexOf("dashboard") > 0;
    };
    $scope.user = User;
    UserService.get(); // TODO: handle failure
    $scope.switchFormString = ["Login", "New Account"];
    $scope.is = {LoginShown: 1};
    $scope.toggleForm = function () {
        $scope.is.LoginShown ^= true;
    };
    $scope.$on('logout', function (event, user) {
        $scope.Login = {
            password: "",
            mail: ""
        };
        $scope.user = user;
    });
    /**
     * Getter for all User model properties.
     * @param {type} [prop] property to get
     * @returns {User,property}
     */
    $scope.getUser = function (prop) {
        var toret = (prop) ? User[prop] : User;
        return toret;
    };
    /**
     * Starts session for authorized user.
     * @uses {scope} $scope.Login
     * @returns {Boolean} false on failure
     */
    $scope.login = function () {
        if ($scope.loginForm.$valid) {
            UserService.set({
                password: $scope.Login.password,
                mail: $scope.Login.mail
            });
            UserService.login().then(function () {
                UserService.fetch();
//                UserService.getActivityLog();
                UserService.getEditions();
                $scope.Login = {
                    password: "",
                    mail: ""
                };
            }, function (err) {
                alert(err)
            });
        } else {
            alert("Form appears to be invalid, please retype");
            console.log("check mail and password", $scope.Login);
            return false;
        }
    };
    /**
     * Tests registration form for validity.
     * @returns {Boolean} true if valid
     */
    $scope.validate = function () {
        var valid = $scope.newUser && $scope.newUser.password === $scope.newUser.password2;
        $scope.signupForm.$valid = $scope.signupForm.password2.$valid = valid;
        $scope.signupForm.password2.$invalid = !valid;
        return valid;
    };
    /**
     * Requests a new account from the UserService.
     * @uses {scope} $scope.newUser registration information
     * @returns {Boolean} false on failure
     */
    $scope.signup = function () {
        if ($scope.signupForm.$valid) {
            UserService.set({
                password: $scope.newUser.password,
                mail: $scope.newUser.mail,
                name: $scope.newUser.name
            });
            return UserService.signup().then(function () {
                UserService.set({password: ""});
                $scope.is.LoginShown = 1; // default to login form if signup was recent
            }, function (err) {
                // leave form visible
            });
        } else {
            console.log("check mail and password: ", $scope.newUser);
            return false;
        }
    };
    /**
     * Clears user credentials, forcing loss of access where authorization is needed.
     * @todo cascade check for changes to authorization
     * @alters {scope} $scope.user Clears User information
     */
    $scope.logout = function () {
        if (confirm("Log Out - are you sure?")) {
            UserService.logout().then(function () {
            }, function (err) {
                alert('failed to logout. All your base are belong to us.');
                console.log(err);
            });
        }
    };
    /**
     * Request a reset of password, resulting in an e-mail.
     * @returns {Boolean} false on failure
     */
    $scope.resetPassword = function () {
        if (!$scope.Login.mail) {
            alert('Please enter your e-mail address first');
            return false;
        }
        UserService.resetPassword($scope.Login.mail);
    };    /**
     * Request a new copy of the confirmation, resulting in an e-mail.
     * @returns {Boolean} false on failure
     */
    $scope.resendEmail = function () {
        if (!$scope.Login.mail) {
            alert('Please enter your e-mail address first');
            return false;
        }
        UserService.resendEmail($scope.Login.mail);
    };
    $scope.openUserForm = function () {
        $scope.modal = $modal.open({
            templateUrl: 'assets/partials/forms/updateUser.html',
            controller: 'updateUserController',
            scope: $scope
        });
    };

    angular.element(document).scope().$on('event:auth-loginRequired', function () {
        /**
         * Listen for authorization requirement failure event and enforce
         * a pseudo logout without clearing cascading data.
         */
        UserService.set({id: false});
        console.log('auth-loginRequired');
    });
});
tradamus.controller('metadataFormController', function ($scope) {
    /**
     * Manage metadata on Edition and Witness models.
     */

    /**
     * Add metadata to Edition and Witness models.
     * @param {model} attach object to describe
     * @returns {Boolean} false on error
     */
    $scope.addDatum = function (attach) {
        if (!$scope.m.type || !$scope.m.content) {
            alert('error: invalid entry');
            return false;
        }
//  metadata format: [{'type':key,'content':value,'purpose':optional}]
        if (!attach.metadata) {
            attach.metadata = [];
        }
        attach.metadata.unshift(angular.copy($scope.m));
        $scope.m.type = $scope.m.content = '';
    };
});
tradamus.controller('importRemoteWitnessController', function ($scope, $filter, Messages, WitnessImportService, WitnessService) {
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
        return Messages.import && Messages.import.message;
    };
    $scope.import = $scope.import || {};
    $scope.import.list = WitnessImportService.list;
    $scope.import.link = WitnessImportService.getLink(0);
    /**
     * Load remote details for each requested witness and adds it to the Edition.
     * @returns {Boolean} false on failure
     */
    $scope.importWits = function () {
        if (!$filter('onlyNumbers')($scope.imports)[0]) {
            alert("You have not selected any projects to import.");
            return false;
        }
        angular.forEach($scope.imports, function (project) {
            WitnessImportService.fetchRemoteDetails(project).then(function (witness) {
                WitnessService.add(witness.data);
            }, function (err) {
                return err;
            });
        });
        $scope.closeIntraform();
    };
    $scope.imports = [];
    $scope.importsLength = function () {
        return $filter('onlyNumbers')($scope.imports).length;
    };
    /**
     * Get id of project from @id field.
     * @param {string} id full URI or other project identifier
     * @returns {Integer} project id
     */
    $scope.getId = function (id) {
        var index = id.lastIndexOf('/');
        return parseInt(id.substring(index + 1));
    };
    if ($scope.import.list && $scope.import.list.length < 1) {
        WitnessImportService.fetchRemote().then(function (witnesses) {
            $scope.import.list = WitnessImportService.list;
            // should be set in Service
        }, function (err) {
            // should be set in Service
        });
    }
});
tradamus.controller('WitnessCtrl', function ($scope, WitnessService, WitnessImportService, Messages) {
    /**
     * Simple, top-level Witness manipulation.
     */

    /**
     * Open remote project import interface.
     */
    $scope.getWitnesses = function () {
        $scope.intraform.show = true;
        $scope.intraform.content = 'assets/partials/fetchWitnesses.html';
    };
    /**
     * Remove from Edition model and send delete request.
     * @param {model} witness Witness object to remove
     * @alters {Array} $scope.Edition.witnesses collection
     */
    $scope.deleteWitness = function (witness) {
        if (confirm("Remove " + witness.title + " from this edition?")) {
            WitnessService.remove(witness);
        }
    };
    /**
     * Add a newly created Witness to the Edition.witnesses collection.
     * Closes the intraform interface on success.
     * @uses {scope} $scope.newWitness
     */
    $scope.addWitness = function () {
        WitnessService.add($scope.newWitness).then(function () {
            $scope.closeIntraform($scope.newWitness);
        });
    };
    /**
     * Initialize empty Witness and open creation interface.
     * @creates {scope} $scope.newWitness
     */
    $scope.createNewWitness = function () {
        $scope.newWitness = {
            title: '',
            metadata: []
        };
        $scope.openIntraform('assets/partials/createWitness.html');
    };
    /**
     * Open file upload interface.
     */
    $scope.showFileUpload = function () {
        $scope.openIntraform('assets/partials/witnessFileUpload.html');
    };
    /**
     * Load the details for the scoped Witness model.
     */
    $scope.getDetails = function () {
        WitnessService.fetch($scope.witness, true, true, true).then(function (witness) {
        });
    };
});
tradamus.controller('metadataFromFileController', function ($scope, FileUploadService, $sce, EditionService) {
    /**
     * Holds scope for metadata file uploads.
     */
});
tradamus.controller('witnessFromFileController', function ($scope, FileUploadService, $sce, EditionService) {
    /**
     * Creating a witness from a file.
     */
    $scope.file = {};
    $scope.FileReaderSupported = !!FileUploadService.isSupported();
    /**
     * Read the results from a file proposed from upload.
     */
    $scope.previewContent = function () {
        FileUploadService.readAsText($scope.file, $scope).then(function (result) {
            $scope.file.content = result;
            $scope.textIsType();
        });
    };
    /**
     * Test string for JSON validity.
     * @todo Move to a Utility module
     * @param {string} str string to test
     * @returns {Boolean} true if valid JSON
     */
    $scope.isJson = function (str) {
        try {
            JSON.parse(str);
        } catch (e) {
            return false;
        }
        return true;
    };
    $scope.textType = "";
    /**
     * Transforms string to an AngularJS trusted HTML string.
     * @param {string} str string to trust
     * @returns {$sce} Trusted HTML string
     */
    $scope.trust = function (str) {
        return $sce.trustAsHtml(str);
    };
    /**
     * Test text type from file upload and return a user readable message to the user.
     * @alters {string} $scope.textType Result of type test. Type or HTML message.
     * @returns {Boolean} false on failure
     */
    $scope.textIsType = function () {
        if (!$scope.file.content) {
            return false;
        }
        if ($scope.isJson($scope.file.content)) {
            $scope.textType = "JSON";
        } else if ($scope.file.content.indexOf('<?xml version=') > -1) {
            // hacky, but finds intent as XML, at least
            $scope.textType = "XML";
        } else if ($scope.file.content.indexOf("{") > -1) {
            // maybe bad JSON
            $scope.textType = $scope.trust("It looks like you may be trying to upload a JSON object, "
                + "but it is improperly formed. <a href='http://jsonlint.com/"
                //+ "?json="+encodeURIComponent($scope.file.content) // easily made query too big for server
                + "' target='_blank'>JSON validator</a>");
        } else if ($scope.file.content.indexOf("<") > -1) {
            $scope.textType = $scope.trust("It looks like you may be trying to input XML markup, "
                + "but it is invalid. <a href='validator.w3.org/‎' target='_blank'>"
                + "XML validator</a>");
        } else {
            $scope.textType = $scope.trust("The content of this file does not seem to be valid XML or JSON. "
                + "If you are attempting to upload only a transcription, please create "
                + "a new witness and then edit the transcription directly.");
        }
    };
    /**
     * Creates a new Witness from the uploaded file.
     * @returns {Boolean} false on failure
     */
    $scope.importWitness = function () {
        var config = {};
        switch ($scope.textType) {
            case "JSON" :
                // check for -LD
                var json = JSON.parse($scope.file.content);
                if (json['@context'] || json['@id']) {
                    // hacky check for @context||@id keywords for LD-ness
                    // DEBUG
                    if ($scope.file.type !== "application/ld+json") {
                        console.log("type set to application/ld+json from " + $scope.file.type);
                        $scope.file.type = "application/ld+json";
                    }
                    config = {
                        headers: {
                            'Content-Type': 'application/ld+json'
                        }
                    };
                } else {
                    // DEBUG
                    if ($scope.file.type !== "application/json") {
                        console.log("type set to application/json from " + $scope.file.type);
                        $scope.file.type = "application/json";
                    }
                    // TODO: This breaks right now because
                    // only title and siglum are accepted
                    config = {
                        headers: {
                            'Content-Type': 'application/json'
                        }
                    };
                }
                break;
            case "XML" :
                // DEBUG
                if ($scope.file.type !== "text/xml") {
                    console.log("type set to text/xml from " + $scope.file.type);
                    $scope.file.type = "text/xml";
                }
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
        EditionService.addWitnessFromFile($scope.file, config).then(function () {
            // TODO set success msg
            $scope.closeIntraform($scope.file);
        }, function (err) {
            console.log(err);
        });
    };
});
tradamus.controller('importWitnessFromFileController', function ($scope, $upload) {
    /**
     * Holds scope for Witness creation from file upload.
     */

    /**
     * Fires when upload file form is submitted.
     * @param {file} $file File to upload
     */
    $scope.onFileSelect = function ($file) {
        $scope.upload = $upload.upload({
            url: 'witnesses?edition=' + $scope.edition.id,
            method: 'POST',
            headers: {'Content-Type': 'text/xml'},
            withCredential: true,
            file: $file
        }).success(function (data, status, headers, config) {
            // file is uploaded successfully
            console.log(data);
        });
        //.error(...)
    };
});
tradamus.controller('textSelection', function ($scope, $q, Selection, PageService, AnnotationService, DecisionService, Display) {
    var witness = Selection.witness;
    /**
     * Selecting various text ranges.
     */
    $scope.highlight = {};
    var getDecision = function (id, array) {
        if (!array) {
            var array = $scope.selected.outline.decisions;
        }
        for (var i = 0; i < array.length; i++) {
            if (array[i].id === id) {
                return array[i];
            }
        }
    };
//    /**
//     * Extract the highlighted text string.
//     * @todo Implement rangy for better browser support
//     * @returns {String} Selected text
//     */
//    $scope.getSelectedText = function () {
//        var text = "";
//        if ($scope.selected && $scope.selected.annotation) {
//            if ($scope.selected.annotation.target && $scope.selected.annotation.target.indexOf('-') > -1) {
//                // TODO: reliable way to get decision-type thing here outline/12#6-14:25
//                var targ = $scope.selected.annotation.target;
//                var startId = parseInt(targ.substring(targ.lastIndexOf("#") + 1));
//                var endId = parseInt(targ.substring(targ.lastIndexOf("-") + 1));
//                var startOffset = parseInt(targ.substring(targ.indexOf(":", targ.lastIndexOf("#")) + 1));
//                var endOffset = parseInt(targ.substring(targ.lastIndexOf(":") + 1));
//                if (startId === endId) {
//                    var startD = getDecision(startId);
//                    if (startD.content.length === 0) {
//                        startD.content = $scope.showUndecided(startD);
//                        startD.tags += (" tr-autoSelected");
//                    }
//                    text = startD.content.substring(startOffset, endOffset);
//                } else {
//                    var startD = getDecision(startId);
//                    var endD = getDecision(endId);
//                    if (startD.content.length === 0) {
//                        startD.content = $scope.showUndecided(startD);
//                        startD.tags += (" tr-autoSelected");
//                    }
//                    if (endD.content.length === 0) {
//                        endD.content = $scope.showUndecided(endD);
//                        startD.tags += (" tr-autoSelected");
//                    }
//                    text = getDecision(startId).content.substring(startOffset) + " "
//                        + getDecision(endId).content.substring(0, endOffset);
//                    // TODO: include text between
//                }
//            } else if ($scope.witness && $scope.witness.transcription) {
//                var page = PageService.getById($scope.selected.annotation.startPage, $scope.witness.transcription.pages);
//                if (page.text) {
//                    text = page.text.substring($scope.selected.annotation.startOffset, $scope.selected.annotation.endOffset);
//                }
//            }
//        }
//        return text || false;
//    };
//    /**
//     * Creates temporary annotation from selected range.
//     * @param {Range} highlighted selected range
//     */
//    $scope.updateSelection = function (highlighted) {
//        var range = highlighted.getRangeAt(0);
//        positionsFromRange(range).then(function (p) {
//            if (!p) {
//                return false;
//            }
//            angular.extend($scope.highlight, p);
//            AnnotationService.select(makeNewAnnotation($scope.highlight));
//        });
//    };
//    /**
//     * Creates temporary annotation.
//     * @param {Object} anno temporary annotation
//     * @returns {Object} annotation
//     */
//    var makeNewAnnotation = function (anno) {
//        var automatic = {
//            id: 'new',
//            type: "tr-userAdded",
//            tags: ""
//        };
//        angular.extend(anno, automatic);
//        return anno;
//    };
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
            positions.target = "annotation/" + bound.id
                + "#" + bound.offsets[0]
                + "-" + bound.offsets[1];
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
                    DecisionService.saveAll(Display.outline.decisions);
                }
                Display.annotation = AnnotationService.select();
                deferred.reject('cancelled');
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
//    var positionsFromRange = function (range) {
//        var deferred = $q.defer();
//        var witness = $scope.witness || {};
//        var positions = {
////            startPage: INT ID,
////            endPage: INT ID,
////            startOffset: INT,
////            endOffset: INT,
////            canvas: OBJ,
////            height: INT,
////            width: INT,
////            x: INT,
////            y: INT
//        };
//        if (range.startContainer === range.endContainer) {
//            // in a single line
//            var $line = ofClass(angular.element(range.startContainer), 'line');
//            if ($line.hasClass('edition')) {
//                setDecisionPositions($line, $line, range).then(function (pos) {
//                    positions = pos;
//                    deferred.resolve(positions);
//                }, function () {
//                    deferred.reject();
//                });
//                // edition annotation, not witness, so divert
//            } else {
//                positions.startPage = parseInt(ofClass($line, 'page').attr('data-page-id'));
//                positions.endPage = positions.startPage;
//                var anno = AnnotationService.getById(parseInt($line.attr('data-line-id')), witness.annotations);
//                positions.startOffset = (anno && anno.startOffset) + range.startOffset;
//                positions.endOffset = (anno && anno.startOffset) + range.endOffset;
//                if (anno.canvas) {
//                    positions.canvas = anno.canvas;
//                    positions.height = anno.height;
//                    positions.width = anno.width;
//                    positions.x = anno.x;
//                    positions.y = anno.y;
//                }
//                positions.attributes = {
//                    trStartsIn: anno.id,
//                    trEndsIn: anno.id
//                };
//                deferred.resolve(positions);
//            }
//        } else {
//            // multiple lines spanned
//            var $startLine = ofClass(angular.element(range.startContainer), 'line');
//            var $endLine = ofClass(angular.element(range.endContainer), 'line');
//            if ($startLine.hasClass('edition')) {
//                setDecisionPositions($startLine, $endLine, range).then(function (pos) {
//                    positions = pos;
//                    deferred.resolve(positions);
//                }, function () {
//                    deferred.reject();
//                });
//                // edition annotation, not witness, so divert
//            } else {
//                var $page = ofClass(angular.element(range.startContainer), 'page'); // assume just one for now
//                if ($startLine && $endLine && $page) {
//                    positions.startPage = positions.endPage = parseInt($page.attr('data-page-id'));
//                    var startAnno = AnnotationService.getById(parseInt($startLine.attr('data-line-id')), witness.annotations);
//                    var endAnno = AnnotationService.getById(parseInt($endLine.attr('data-line-id')), witness.annotations);
//                    positions.startOffset = (startAnno && startAnno.startOffset) + range.startOffset;
//                    positions.endOffset = (endAnno && endAnno.startOffset) + range.endOffset;
//                    if (startAnno && startAnno.canvas && endAnno && endAnno.canvas) {
//                        positions.canvas = startAnno.canvas;
//                        if (endAnno.canvas && startAnno.canvas && endAnno.canvas.id !== startAnno.canvas.id) {
////        positions.canvas = [startAnno.canvas, endAnno.canvas];
//// ticket 127
//                        }
//                        positions.x = Math.min(startAnno.x, endAnno.x);
//                        positions.y = Math.min(startAnno.y, endAnno.y);
//                        positions.height = Math.max(startAnno.y + startAnno.height, endAnno.y + endAnno.height) - positions.y;
//                        positions.width = Math.max(startAnno.x + startAnno.width, endAnno.x + endAnno.width) - positions.x;
//                    }
//                    positions.attributes = {
//                        "tr-startsIn": startAnno.id,
//                        "tr-endsIn": endAnno.id
//                    };
//                    deferred.resolve(positions);
//                } else {
//                    // what is selected here?
//                    deferred.reject("Unknown selection range");
//                }
//            }
//        }
//        return deferred.promise;
//    };
    $scope.showLine = function (line) {
        if (!line) {
            AnnotationService.select(null); // remove detail
        }
        AnnotationService.select(AnnotationService.getById(parseInt(line)));
    };
});
tradamus.controller('STOACtrl', function ($scope, $routeParams, Edition, Witness, $rootScope, Selection) {
    /**
     * @deprecated I think these functions have been moved to other controllers.
     */
    $scope.stoa = {name: 'somename'}; // Synthesized Transcription Object for Annotation
    if (!Edition.title)
        Edition.fetch();
//  $scope.$on('LoadedWitness', function(event, w) {
//    $scope.edition.witnesses = Edition.witnesses;
//  });
//  $scope.$on('LoadedTranscription', function(){
//    $scope.edition.witnesses = Edition.witnesses;
//  });
//    $scope.$on('EditionDetails-load', function() {
//        angular.forEach(Edition.witnesses, function(w, index) {
//            w = Witness.fetch(w, index);
//        });
//    });
    Selection.page = Selection.page || '';
    Selection.witness = Selection.witness || '';
    $scope.selectWitness = function () {
        Selection.page = '';
        console.log('choose ' + Selection.witness);
        $rootScope.$broadcast('selection-wit');
    };
});
tradamus.controller('witnessTranscriptionCtrl', function ($scope, Transcription, Page, Selection) {
    /**
     * Interacting with Witness Transcriptions
     */
    // FIXME this should not be a watcher
    $scope.$on('selection-wit', function (event, selection) {
        if (Selection.witness > -1) {
            $scope.clear = true;
            if (!angular.isObject(Edition.witnesses[Selection.witness].transcription)) {
                Edition.witnesses[Selection.witness].transcription = {id: Edition.witnesses[Selection.witness].transcription};
            }
            angular.extend(Edition.witnesses[Selection.witness].transcription, Transcription);
            Edition.witnesses[Selection.witness].transcription.fetchPages(
                false // do not force reGet
                );
            // TODO remove $watch
            $scope.$watch('Selection.page', function () {
                $scope.selectPage(false, false);
                console('remove watch here, selectPage()');
            });
            $scope.selectPage = function (byLine, force) {
                if (Selection.page) {
                    $scope.clear = false;
                    var p = Edition.witnesses[Selection.witness].transcription.pages[Selection.page];
                    if (!angular.isObject(Edition.witnesses[Selection.witness].transcription.pages[Selection.page])) {
                        Edition.witnesses[Selection.witness].transcription.pages[Selection.page] = {id: Edition.witnesses[Selection.witness].transcription.pages[Selection.page]};
                    }
                    angular.extend(Edition.witnesses[Selection.witness].transcription.pages[Selection.page], Page);
                    Edition.witnesses[Selection.witness].transcription.pages[Selection.page].fetchText(byLine, force);
                }
            };
        }
    });
});
tradamus.controller('editionCtrl', function ($scope, $routeParams, EditionService, WitnessService, isNew) {
    /**
     * Holds scope for entire Edition.
     */
    $scope.isNew = isNew;
    /**
     * Opens intraform for specific Witness interactions.
     * @param {model} witness Witness to put in scope
     * @param {string} action intention switch
     */
    $scope.showWitnessIntraform = function (witness, action) {
        $scope.witness = witness;
        if (!($scope.witness.transcription && $scope.witness.transcription.pages)) {
            WitnessService.fetch($scope.witness, true, true, true);
        }
        switch (action) {
            case "annotate witness" :
                $scope.openIntraform('assets/partials/annotateWitness.html');
                break;
            case "read witness" :
                $scope.openIntraform('assets/partials/readWitness.html');
                break;
            default:
                $scope.openIntraform('assets/partials/witnessSummary.html');
        }
    };
    $scope.deleteEdition = function () {
        EditionService.delete();
    };
});
tradamus.controller('composeEditionController', function ($scope, $filter, _cache, Selection, Annotations, AnnotationService, WitnessService, Edition, CollationService, Outlines, OutlineService, Display) {
    /**
     * Interactions for the organization of Edition sections.
     * @todo Bloated
     */
    $scope.display = Display;
    $scope.editing = {};
    $scope.editLink = function () {
        return "#/edition/" + $scope.edition.id + "/edit/" + $scope.editing.id;
    }; // for trusted use in a form
    $scope.outlinesAsArray = function () {
        var os = [];
        for (var v in Outlines) {
            os.push(Outlines[v]);
        }
        return os;
    };
    /**
     * @todo sort this out when structure has stabilized
     * @returns {unresolved}
     */
    $scope.buildOutline = function () {
//    if (!$scope.edition.outlines) {
//      $scope.edition.outlines = [];
//    }
//    angular.forEach(Edition.outline, function(a) {
//      if (a.type === "tr-outline") {
//        addIfNotIn(a, $scope.edition.outlines);
//        if (a.attributes && a.attributes.trGroup) {
//          angular.forEach(a.attributes.trGroup.bounds, function(b) {
//            var set;
//            if (a.attributes.trGroup.category === "directAnnotation") {
//              set = Edition.metadata;
//            }
//            b = AnnotationService.getById(b, set);
//          });
//        }
//      }
//    });
//debugList($scope.edition.outlines);
        return loadOutline();
    };
    /**
     * Get each outlines' details.
     */
    var loadOutline = function () {
        angular.forEach(Edition.outlines, function (o, i) {
            OutlineService.get(o).then(function (outline) {
                Outlines["id" + outline.id] = outline;
                Edition.outlines[i] = outline.id;
            });
        });
        return $scope.edition.outlines = Edition.outlines;
    };
    /**
     * Return title or load details to return title.
     * @param {model} o Outline object in scope
     */
    $scope.outlineLabel = function (o) {
        if (o.title) {
            return o.title;
        } else {
            OutlineService.get(o).then(function (outline) {
                o = outline;
            });
        }
    };
    /**
     * Add annotation to collection if not already included.
     * Used here for annotation collections, but generizable.
     * @param {Object} a annotation to add, checked by id
     * @param {Array} list collection to check against
     */
    var addIfNotIn = function (a, list) {
        var found;
        for (var i = 0; i < list.length; i++) {
            if (list[i].id === a.id
                || list[i] === a) {
                found = true;
                break;
            }
        }
        if (!found) {
            list.push(a);
        }
    };
    /**
     * Organizing display options.
     * @param {string} choice
     */
    $scope.toSelect = function (choice) {
        switch (choice) {
            case "collation":
                $scope.display.collate = true;
                $scope.display.option = "witness";
                break;
            case "witness":
                $scope.display.option = "witness";
                break;
            default:
                $scope.display.option = "annotation";
        }
    };
    $scope.contentSummaryOutput = "";

    /**
     * Display human readable version of content summary.
     * @param {type} bounds
     * @returns {Boolean|string} Human readable type
     */
    $scope.contentSummary = function (bounds) {
        if (!bounds) {
            return false;
        }
        // TODO: cleanup and organize responses
        $scope.contentSummaryOutput = "pending functionality " + bounds.length;
        return;
        switch (bounds.length) {
            case undefined:
            case null:
            case 0:
                // trouble... there should always be a bound
                break;
            case 1:
                // direct annotation or single witness
                if (bounds[0].startPage > -1) {
                    titleFromPage(bounds[0].startPage).then(function (title) {
                        $scope.contentSummaryOutput = "Single attestation from "
                            + title
                            + ".";
                    });
                } else {
                    $scope.contentSummaryOutput = "Direct text annotation."
                }
                break;
            default:
                // more than one witness
//                var summary = [];
//                // TODO: no title from pages yet because waiting for multiple responses is hard
//                angular.forEach(bounds, function (b, i) {
//                    if (i === bounds.length - 1) {
//                        summary.push('and ');
//                    } else {
//                        summary.push(', ');
//                    }
//                    summary.push(titleFromPage(b.startPage));
//                });
//                summary = summary.join(' ');
                $scope.contentSummaryOutput = bounds.length + " different attestations."
        }
    };
    var titleFromTarget = function (target) {
        var wid = parseInt(target.substr(target.lastIndexOf('/') + 1));
        var w = WitnessService.getById(wid);
        return w.title || w.title || 'an unlabelled witness';
    };
    /**
     * Find title of Witness from page.
     * @param {Object||number} page Page model or integer page id
     */
    var titleFromPage = function (page) {
        var pid = page.id || page;
        return WitnessService.getByContainsPage(pid).then(function (w) {
            return w.title || w.title || 'an unlabelled witness';
        });
    };
    /**
     * Update scope to select a new outline section
     * @param {model} section
     * @returns {Boolean} false on failure
     */
    $scope.selectOutline = function (section) {
        if (section && $scope.editing.id === section.id) {
            return false;
        } else {
            if (section) {
                $scope.contentSummary(section.bounds);
            }
            $scope.editing = section || {};
            Selection.select("outline", section);
        }
    };
    $scope.content = {};
    /**
     *
     * @returns {undefined}Reset display options
     */
    $scope.defaultDisplay = function () {
        $scope.display.option = '';
    };
    /**
     * Validate ranges in selector
     * @param {Range} range
     * @returns {Boolean} true if valid
     */
    var rangesValidate = function (range) {
        var valid = true;

        // existence check
        if (!range.witness) {
            alert("Missing witness (debug message)");
        }
        if (!range.startPage || !(range.startOffset > -1)) {
            alert("Missing start range (debug message)");
        }
        if (!range.endPage || !(range.endOffset > -1)) {
            alert("Missing end range (debug message)");
        }
        if (valid) {
            // sanity check
            if (!range.witness.id) {
                alert("Missing witness id (debug message)");
            }
            if (range.endPage.index < range.startPage.index) {
                alert("Last page is before first page (debug message)");
            } else if (range.startPage.index === range.endPage.index && range.endOffset < range.startOffset) {
                alert("End of range is before the range start (debug message)");
            }
        }
        return valid;
    };
    /**
     * Remove section from Edition.outlines collection and update database.
     * @param {model} o outline object
     */
    $scope.deleteSection = function (o) {
        if (confirm("Remove section: " + o.title + "?")) {
            OutlineService.delete(o).then(function () {
                $scope.selectOutline();
            });
        }
    };
    /**
     * Create a new section with defined content.
     * @todo Clean up prompt with dialogue
     * @param {type} content
     */
    $scope.saveNewSection = function (content) {
        var s = {
            index: $scope.edition.outlines.length + 1, // ERIC ? no 0-index in outlines?
            title: prompt("Enter a helpful label for this section for your personal reference:"),
            decisions: []
        };
        if (!content.text > 0) {
            // collator passed in
            s.bounds = [];
            var b;
            var len = content.collator.length;
            do {
                b = content.collator.shift();
                if (!rangesValidate(b)) {
                    return false;
                }
                var bound = {
                    endOffset: b.endOffset,
                    endPage: b.endPage.id || b.endPage,
                    startOffset: b.startOffset,
                    startPage: b.startPage.id || b.startPage
                };
                s.bounds.push(bound);
//                AnnotationService.setAnno(bound).then(function (anno) {
//                    var w = WitnessService.getById(b.witness.id);
////                    if (w.annotations) {
////                        Annotations["id" + anno.id] = anno;
////                        if(w.annotations.indexOf(anno.id)===-1){
////                            w.annotations.push(anno.id);
////                        }
////                        s.bounds.push(w.annotations[w.annotations.length - 1]);
////                        if (s.bounds.length === len) {
////                            saveAndClear(s); // save when last bound is updated
////                        }
////                    } else {
////                        WitnessService.getAnnotations(w).then(function () {
////                            Annotations["id" + anno.id] = anno;
////                            if (w.annotations.indexOf(anno.id) === -1) {
////                                w.annotations.push(anno.id);
////                            }
////                            s.bounds.push(w.annotations[w.annotations.length - 1]);
////                            if (s.bounds.length === len) {
////                                saveAndClear(s); // save when last bound is updated
////                            }
////                        });
////                    }
//                });
                saveAndClear(s);
            } while (content.collator.length);
        } else {
            // string from direct entry
            AnnotationService.setAnno({
                id: "new",
                type: "tr-outline-annotation",
                content: content.text
            }).then(function (anno) {
                Edition.metadata.push(anno.id);
                s.bounds = [anno.id];
                saveAndClear(s);
                return anno;
            }, function (error) {
                console.log(error);
            });
        }
    };
    /**
     * Save section and reset display.
     * @fires $scope.defaultDisplay
     */
    var saveAndClear = function (s) {
        $scope.saveSectionAsOutline(s).then(function () {
            delete $scope.content.text;
            $scope.selectOutline(s);
            $scope.defaultDisplay();
        });
    };
    /**
     * Save new section to outlines collection.
     * @param {type} s
     */
    $scope.saveSectionAsOutline = function (s) {
        return OutlineService.add(s);
    };
    /**
     * Get the neighbor outline based on implied index.
     * @param {type} index
     * @param {type} direction
     * @returns {next.index}
     */
    var nextToIndex = function (index, direction) {
        var outlines = $filter('orderBy')($scope.edition.outlines, 'index');
        var len = outlines.length;
        var next;
        for (var i = 0; i < len; i++) {
            if (outlines[i].index === index) {
                next = outlines[i + direction];
                break;
            }
        }
        return next && next.index;
    };
    /**
     * Move up in section order
     * @param {type} section
     */
    $scope.moveUp = function (section) {
        swapSection(section.index, nextToIndex(section.index, -1));
    };
    /**
     * Move down in section order
     * @param {type} section
     */
    $scope.moveDown = function (section) {
        swapSection(section.index, nextToIndex(section.index, 1));
    };
    /**
     * Swap two sections in outlines order
     * @param {type} section
     */
    var swapSection = function (a, b) {
        var s1 = getByProp($scope.edition.outlines, a, 'index');
        var s2 = getByProp($scope.edition.outlines, b, 'index');
        var swap = s1.index;
        s1.index = s2.index;
        s2.index = swap;
    };
    /**
     * Get item from array by identity or property.
     * @todo consider moving to utility module
     * @param {Array} ay Array to look within
     * @param {*} val Value to match
     * @param {string} [prop] Property to match
     * @param {Array} inside Deeper collection to look within
     * @returns {*} toret Matched item
     */
    var getByProp = function (ay, val, prop, inside) {
        var toret;
        var ins = inside || [];
        var isObj = !angular.isArray(ay) && angular.isObject(ay);
        for (var i = 0; i < ay.length; i++) {
            var compareTo = (isObj) ? ay.keys[i] : ay[i];
            for (var j = 0; j < ins.length; j++) {
                compareTo = compareTo[ins[j]];
            }
            if (compareTo[prop] === val) {
                toret = ay[i];
                break;
            }
        }
        return toret;
    };
    /**
     * Interpret target property to find Witness
     * @returns {model} Witness
     */
    $scope.targetToWitness = function (target) {
        // expected format "witness/3" or "3"
        if (!target) {
            return;
        }
        var wid = (isNaN(target)) ? target.substr(target.lastIndexOf("/") + 1) : target;
        return WitnessService.getById(wid);
    };
    $scope.openForCollation = function () {
        if ($scope.editing.decisions.length > 0) {
            _cache.store("decisions", $scope.editing.decisions);
//  routed          $scope.openIntraform('assets/partials/collate.html');
        } else {
            CollationService.collateOutline($scope.editing);
        }
    };
//    var collateThis = function(outline) {
//        CollationService.collate(outline).then(function(collation) {
//            _cache.store("outline", $scope.editing);
//            var coll = _cache.get("collation");
//            if (coll && coll.length) {
//                CollationService.digestCollation(collation.data);
//                $scope.openIntraform('assets/partials/collate.html');
//            } else {
//                alert("No collation returned.");
//            }
//        }, function(error) {
//            console.log(error);
//            // failed to collate
//        });
//    };
    $scope.buildOutline();
});
tradamus.controller('tagInput', function ($scope, TagService, _cache) {
    $scope.tagText = '';
    $scope.addTag = function (tag) {
        $scope.item.tags = $scope.item.tags || "";
        if (tag) {
            $scope.item.tags += " " + tag;
        }
        if ($scope.tagText.length > 0) {
            var pattern = new RegExp("\\b" + $scope.tagText + "\\b", "g");
//            if ($scope.item.tags.indexOf($scope.tagText) !== -1) {
            if (pattern.test($scope.item.tags)) {
                throw Error('That tag is already included');
            } else {
                $scope.item.tags += " " + $scope.tagText;
                TagService.add($scope.tagText);
                // $scope.fullListOfTags = TagService.fullListOfTags;
                $scope.tagText = "";
            }
            console.log('New Tags are: ', $scope.item.tags);
        }
    };
    $scope.deleteTag = function (tag) {
        if (!$scope.item.tags) {
            $scope.item.tags = "";
        }
        var reduce = $scope.item.tags.split(" ");
        if ($scope.item.tags.length > 0 && $scope.tagText.length === 0 && tag === undefined) {
            reduce.pop();
        } else {
            reduce.splice(reduce.indexOf(tag), 1);
            // item remains in the list of all tags until reload
        }
        $scope.item.tags = reduce.join(" ");
    };
    $scope.fullListOfTags = TagService.fullListOfTags;
});
tradamus.controller('collateController', function ($scope, $timeout, Edition, Witness, $rootScope, CollationService, EditionService, Selection, DecisionService, _cache) {
//  if ($scope.edition.id < 1)
//    $scope.edition = Edition;
    $scope.edition.objectLabel = "Assisted Collation";
    $scope.showLinear = true;
    $scope.pick = {};
    $scope.collation = _cache.collation;
//    $scope.selected = $scope.selected || Selection;
    $scope.selected.outline = $scope.selected.outline || $scope.edition.outlines && $scope.edition.outlines[0] || {};
    $scope.selected.outline.decisions = $scope.selected.outline.decisions || _cache.get("decisions");//    $scope.selected.decision = $scope.selected.decision || 0; // TODO: Why is this set to an object?
    $scope.selected.decision = 0;
    $scope.$on("resume", function (event, loader) {
        if (loader && loader.for === "collation") {
            $scope.collation = _cache.collation;
        }
    });
    $scope.selectSingleVariants = function () {
        var count = 0;
        var decisions = $scope.selected.outline.decisions;
        CollationService.groupMotes(decisions);
        angular.forEach(decisions, function (d) {
            if (!d.content && d.motesets.length === 1) {
                d.content = d.motesets[0].content;
                count++;
            }
        });
        $scope.display.singleMsg = count + " choices made";
    };
    $scope.saveAll = function () {
        DecisionService.saveAll($scope.selected.outline.decisions);
    };
    $scope.new = {};
//  Edition.sigla = Edition.sigla || [];
    $scope.decisionFlesh = function () {
        // prevent $digest loop
        $scope.omittedWitnesses = $scope.getOmittedWitnesses();
    };
    $scope.getAnnoWit = function (anno) {
        var wit;
//    if (!anno.target)
//      alert('oops');
        wit = anno.target.substr(anno.target.lastIndexOf('#') + 1);
        return wit;
    };
    $scope.selectDecision = function (index) {
        // direction for animations
        var decisions = $scope.selected.outline.decisions;
        CollationService.groupMotes(decisions[index - 1]);
        CollationService.groupMotes(decisions[index]);
        CollationService.groupMotes(decisions[index + 1]);
        // TODO: this should be manageable from the variantListController, but I'm not that smart yet.
        Selection.select("decision", index);
        console.log("New decision #", index);
//        return ds[$scope.selected.decision];
    };
    CollationService.groupMotes();
    $scope.changeDecision = function (direction) {
        $scope.selectDecision($scope.selected.decision + direction);
    };
    $scope.unmadeDecision = function (direction) {
        var decisions = $scope.selected.outline.decisions;
        var index = -1;
        for (var i = $scope.selected.decision + direction; i > 0 && i < decisions.length; i = i + direction) {
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
    var getByProp = function (set, prop, val) {
        var inSet;
        for (var i = 0; i < set.length; i++) {
            if (set[i][prop] === val) {
                inSet = set[i];
                break;
            }
        }
        return inSet;
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
        $scope.selected.decision = spliceIndex;
        return merged;
    };
    $scope.showWitness = function (witness, $event) {
        $event.preventDefault();
        $scope.selected.witness = witness;
    };
//  $scope.collate = function() {
//    EditionService.collate();
//  };
//  $scope.addDecisionTag = function(newTag) {
//    var theseTags = Edition.text[$scope.display.record].tags;
//    if (theseTags.indexOf(newTag) !== -1) {
//      alert('That tag is already included');
//      return false;
//    }
//    if ($scope.display.decisionTags.indexOf(newTag) === -1) {
//      $scope.display.decisionTags.push(newTag);
//    }
//    // goofy hack to update input
//    var tags = angular.copy(theseTags);
//    tags.push(newTag);
//    Edition.text[$scope.display.record].tags = tags;
//    // end hack
//    console.log('New Tags are: ', $scope.display.decisionTags);
//  };
//  if (!$scope.pick.mote)
//    $scope.pick.mote = 0;
//  $scope.listSigla  = function(motes) {
//    var sigla = [];
//    // @deprecate for witnessID usage
//    var motes = motes || [Edition.collation[$scope.pick.mote]];
//    motes = (motes instanceof Array) ? motes : [motes];
//    for(var i=0;i<motes.length;i++){
//      // for loop, not angular js for want of break/continue;
//      if (!motes[i]) continue; // accidental blank mote submitted
//      var anchors = (motes[i].anchors) ? motes[i].anchors : motes[i];
//      angular.forEach(anchors, function(obj){
//        if (obj.witness) {
//          sigla.push($scope.getSiglum(obj.witness));
//        }
//      });
//    };
//    return sigla;
//  };

    /**
     * Find all witnessIDs referenced in a Mote
     * @param {Array || Annotation} annos one or more Mote to investigate
     * @returns {Array.<number>} wits id array of each represented witness
     */
    $scope.listWits = function (annos) {
        var annos = annos || $scope.selected.outline.decisions[$scope.selected.decision];
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
        var decisions = $scope.selected.outline.decisions;
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
        var neighbor = $scope.selected.outline.decisions[$scope.display.record].motesets[0].cIndex + direction;
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
                // no transcription loaded yet, so wait for it.
//      var loadText = $scope.$watch(witness,function(w){
//        if(angular.isObject(w.transcription)) {
//          $scope.getMoteContext(mote,direction,buffer);
//          // remove watch
//          loadText();
//        }
//      });
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
        var decisions = $scope.selected.outline.decisions;
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
        var decisions = $scope.selected.outline.decisions;
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
    $scope.collateSelectedOutline = function () {
        CollationService.collateOutline($scope.selected.outline);
    };
    $scope.digestCollation = function (collation) {
        if (!_cache.get("collation") && !collation) {
            return false;
        }
        var collation = collation || _cache.get("collation");
        var decisions = $scope.selected.outline.decisions = [];
        var cIndex = 0;
        var iMote = {};
        while (cIndex < collation.length) {
            iMote = collation[cIndex];
            var newMoteSet = $scope.getMoteSet(iMote);
            newMoteSet.index = decisions.length;
            decisions.push(newMoteSet);
            console.log(decisions[decisions.length - 1].cIndex);
            cIndex = Math.max.apply(Math, newMoteSet.cIndex) + 1; // check next
        }
        _cache.store("decisions", $scope.groupMotes(decisions));
    };
//  $scope.moteCount = function(){
//    var moteCount = 0;
//    angular.forEach(Edition.text,function(moteSet){
//      moteCount += moteSet.motes.length;
//    });
//    return moteCount;
//  };
//  $scope.addToEditionText = function(moteIndex) {
//    // build a type, add to editionText, return to move to the next
//    var toAdd = $scope.getMoteSet(moteIndex);
//    // should look like:
//    // { text : "chosen text", motes: [motes] }
//    Edition.text.push(toAdd);
//    return toAdd;
//  };
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
//    var rem = newMotes.indexOf($scope.selected.mote.index);
//    if (rem > 0) {
//      newMotes.splice(0, rem);
//    }
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
//    // set default text if no variant
//    if (!moteSet.motes[1]) {
//      moteSet.text = moteSet.motes[0].text;
//    }
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
//  $scope.saveDecisions = function(){
//    EditionService.setEditionText();
//  };
    $scope.refineAnnotation = function () {
        alert('functionaliy pending... I am sorry.');
    };
//  $scope.learnMore = function() {
//    alert('Images and tighter annotations as available.\n\n'+
//      'New layout for interaction. \n\n'+
//      'Schema for categorization/typing\n'+
//      'of annotations and Decisions.\n\n'+
//      'Insert holder for blank Decision.');
//  };
//  $scope.hasSigla = function(match, toCheck) {
//    // Check for specific sigla in mote anchors using listSigla
//    var stringla = $scope.listSigla(toCheck);
//    var hasSigla = false;
//    match = (match instanceof Array) ? match : [match];
//    angular.forEach(match,function(m){
//      if (stringla.indexOf(m) !== -1) {
//        hasSigla = true;
//        return hasSigla;
//      }
//    });
//    return hasSigla;
//  };
    var intersectArrays = function (arrayA, arrayB) {
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
//  var noBreaks = function(str) {
//    return str.replace(/\u000A/g, "").replace(/  +/g, " ");
//  };
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
//    angular.forEach(mote.motes, function(a) {
//      var wit = getAnnoWit(a);
//      if (sigla.indexOf(wit) === -1) {
//        sigla.push(wit);
//      }
//    });
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
//    $scope.groupMotes = function(decisions) {
//        angular.forEach(decisions, function(d) {
//            $scope.toMotesets(d);
//        });
//        return decisions;
//    };
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
        var witnessID = $scope.listWits(startAt) || witnessID || $scope.listWits($scope.selected.mote);
        witnessID = (witnessID instanceof Array) ? witnessID : [witnessID];
        var startAt = startAt || $scope.selected.mote;
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
            var matchingWitnesses = intersectArrays(witnessID, $scope.listWits(checking));
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
        var decision = decision || $scope.selected.outline.decisions;
        [$scope.selected.decision];
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
        if (!$scope.selected.outline.decisions) {
            if (_cache.get("collation"))
                $scope.digestCollation();
        } else {
            stopDigest();
        }
    });
    $scope.setDecisionText = function (text) {
        EditionService.makeDecision(text, $scope.display.record);
    };
});
tradamus.controller('annotateDraftController', function ($scope, OutlineService, Outlines) {
    $scope.countDecisions = function () {
        var count = 0;
//        var decisions = $scope.selected.outline.decisions;
        angular.forEach(Outlines, function (o) {
            if (!angular.isObject(o)) {
                OutlineService.get(o).then(function (ol) {
                    count += ol.decisions && ol.decisions.length;
                    $scope.decisionCount = count;
                }, function (err) {
                    console.log(err);
                });
            } else {
                count += o.decisions && o.decisions.length;
                $scope.decisionCount = count;
            }
        });
    };
    $scope.decisionCount = $scope.decisionCount || $scope.countDecisions();
//    $scope.annotateDraft = function () {
//        $scope.openIntraform('assets/partials/annotateDraft.html');
//    };
});
tradamus.controller('nonDigitalWitnessController', function ($scope) {
    $scope.newContext = function (witness) {
        if (!witness.context) {
            witness.context = {};
        }
        witness.context["New Context"] = "";
        console.log(witness.context);
    };
});
tradamus.controller('stoaDisplayController', function ($scope, _cache, WitnessService, AnnotationService, CollationService) {
    $scope.collation = _cache.collation;
    $scope.witnesses = function () {
        var witnesses = [];
        var bs = _cache.get("outline").attributes.trGroup.bounds;
        angular.forEach(bs, function (b) {
            if (!b.target && b) {
                b = AnnotationService.getById(b);
            }
            ;
            var wid = parseInt(b.target.substr(b.target.lastIndexOf('/') + 1));
            witnesses.push(WitnessService.getById(wid));
        });
        return witnesses;
    };
    $scope.intersectArrays = function (arrayA, arrayB) {
        if (!arrayA || !arrayB || !arrayA.length || !arrayB.length) {
            return false;
        }
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
    $scope.listSigla = function (mote) {
        var sigla = [];
        angular.forEach(mote.witnesses, function (wid) {
            if (wid > -1) {
                sigla.push($scope.getSiglum(wid));
            }
        });
        return sigla;
    };
});
tradamus.controller('editEdition', function ($scope, $filter, Edition, EditionService, UserService, _cache) {
    $scope.edition.objectLabel = $scope.edition.title + " Draft";
    var allPermissionUserDetails = function () {
        angular.forEach($scope.edition.permissions, function (c) {
            permissionUserDetails(c);
        });
    };
    var permissionUserDetails = function (p) {
        UserService.fetchUserDetails(p.user).then(function (user) {
            user.user = user.id;
            delete user.id;
            angular.extend(p, user);
        }); // id is permission id, user is uid
    };
//    if ($scope.edition.permissions[0] && !$scope.edition.permissions[0].name) {
//            $q.when(EditionService.get()).then(allPermissionUserDetails(),
//                    function(err) {
//                        console.log(err, "failed getting edition - did not fetch permission details");
//                    });
//    }
    /*
     * @deprecate
     * @param {type} force
     * @returns {_L1673.$scope.edition.text}
     */
    $scope.getEditionText = function (force) {
        if (force || !_cache.decisions || _cache.decisions.length) {
            // no text yet
            EditionService.getDecisions().then(function () {
                return _cache.decisions;
            });
        } else {
            // text exists
        }
        return _cache.decisions;
    };
    $scope.composeChoice = function (option) {
        $scope.compose = {
            option: option
        };
    };
    $scope.composeDraft = function () {
        $scope.composeChoice('draft text');
        $scope.openIntraform('assets/partials/composeEdition.html');
    };

//  $scope.edition.text = $scope.edition.text || $scope.getEditionText(true);
    $scope.newDatum = {type: "", content: "", tags: "tr-metadata"};
    $scope.addDatum = function (datum) {
        if (datum) {
            $scope.newDatum = datum;
        }
        if (!$scope.newDatum.type || !$scope.newDatum.content) {
            alert('error: invalid entry');
            return false;
        }
//  metadata format: [{'type':key,'content':value,'purpose':optional}]
        EditionService.set({
            metadata: [angular.copy($scope.newDatum)].concat($scope.edition.metadata)
        });
        $scope.saveMeta = true;
        $scope.newDatum.type = $scope.newDatum.content = '';
        $scope.saveMetadata;
    };
    $scope.saveMeta = false; // debug
    $scope.saveMetadata = function () {
        var metadata = $filter('metadata')($scope.edition.metadata);
        EditionService.saveMetadata(metadata).then(function (success) {
            $scope.saveMeta = false;
        }, function (err) {
            console.log(err);
        });
    };
    $scope.deleteDatum = function (index) {
        var cfrm = confirm("Remove this data pair?");
        if (cfrm) {
            Edition.metadata.splice(index, 1);
            $scope.saveMeta = true;
        }
    };
    $scope.saveTitle = function () {
        $scope.showSaveTitle = !EditionService.updateTitle({title: $scope.edition.title});
    };
    $scope.feedback = {
        title: {msg: "You can change your the title of Your Edition at any time."}
    };
    $scope.getOwner = function (prop) {
        var owner;
        if (!$scope.edition || !$scope.edition.permissions ||
            ($scope.edition.id && $scope.edition.permissions && !$scope.edition.permissions.length)) {
            EditionService.get().then(function () {
                $scope.edition = Edition;
            });
            return "[loading]";
        }
        for (var i = 0; i < $scope.edition.permissions.length; i++) {
            if ($scope.edition.permissions[i].role === "OWNER") {
                var exists = $scope.edition.permissions[i][prop];
                owner = (prop && exists) ? $scope.edition.permissions[i][prop] : $scope.edition.permissions[i];
                break;
            }
        }
        return owner;
    };
//    $scope.parseCollaborators = function() {
//        if($scope.edition.permissions.length > 1){
//                    return "You have shared this edition with "+$scope.edition.permissions.length-1
//            + " other"+($scope.edition.permissions.length === 1&&". "||"s. ");
//    }
//    return false;
//};
});
tradamus.controller('permissionsController', function ($scope, Edition, EditionService) {
    $scope.roles = ["EDITOR", "VIEWER", "NONE"]; // "OWNER" not available
    $scope.getPublicUser = function () {
        var pub = {role: 'NONE'};
        if (!Edition.permissions)
            return pub;
        angular.forEach($scope.edition.permissions, function (u) {
            if (u.user === 0 || u.id === 0)
                pub = u;
        });
        return pub;
    };
    $scope.publicUser = $scope.getPublicUser();
    //// collaboration format: [{'user':1,'role':'OWNER'}] NONE, VIEWER, EDITOR, OWNER
    $scope.addCollaborator = function (collaborator, isPublicUser) {
        var found = false;
        if (isPublicUser) {
            angular.extend(collaborator, {
                mail: "publicUser",
                id: 0,
                name: "publicUser"
            });
        }
        angular.forEach($scope.edition.permissions, function (name, i) {
            if (name.mail === collaborator.mail) {
                if (name.role !== collaborator.role) {
                    $scope.edition.permissions.splice(i, 1);
                } else {
                    alert('This user is already in that role.');
                    collaborator = '';
                    return;
                }
            }
        });
        if ($scope.memberForm.$valid) {
            if (found) {
                EditionService.savePermission(collaborator).then(function () {
                    collaborator = {};
                }, function (err) {
                    console.log('Something failed when saving permissions');
                });
            } else {
                EditionService.addUser(collaborator);
                collaborator = {};
            }
        }
    };
});
tradamus.controller('treeController', function ($scope) {
    if (!$scope.treeNodes)
        $scope.treeNodes = [{label: "nothing loaded"}];
    // add handling for interacting with the annotation groups.
});
tradamus.controller('editWitness', function ($scope, $filter, WitnessService, CanvasService, Selection, AnnotationService, $q) {
    $scope.witness = $scope.witness || Selection.witness || {};
    $scope.selection = $scope.selection || Selection;
//    $scope.selected = {};
    $scope.saveTitle = function () {
        WitnessService.save($scope.witness.id, {"title": $scope.witness.title, "siglum": $scope.witness.siglum});
    };
    $scope.saveMetadata = function () {
        WitnessService.save($scope.witness.id, {"metadata": $filter('metadata')($scope.witness.metadata)});
    };
    $scope.selectAnno = function (anno) {
        if (!anno) // clearing selection
            return $scope.selected.annotation = Selection.select('annotation', anno);
        $scope.selected.annotation = Selection.select('annotation', anno); // setting selection
        if ($scope.selected.annotation.startPage && !$scope.selected.annotation.bounds) {
            setBounds(anno);
        }
//    $rootScope.$broadcast("selectAnno");
    };
    var scrollToView = function (bounds) {
        var el = document.getElementById("transcription");
        var ab = bounds;
        if (!el || !ab) {
            return;
        }
        var aPos = ab;
        var ePos = el.getBoundingClientRect();
        if (aPos.top < ePos.top || aPos.top > ePos.bottom) {
            // outside of view, scroll to fit
            var scroll = el.scrollTop;
            var a = scroll - aPos.top + ePos.top;
            var aheight = aPos.height || aPos.bottom - aPos.top;
            var eheight = ePos.height || ePos.bottom - ePos.top;
            var center = aheight / 2 + a;
            var scrollTo = center - eheight / 2;
            el.scrollTop = scrollTo;
        }
    };
    var setBounds = function (anno) {
        /**
         * Create a selection to grab the bounding rectangle to highlight the
         * text range on screen.
         * @param {Annotation} anno The annotation to create the range from
         * @returns {Annotation} anno The full annotation with new bounds property
         */
        var range = document.createRange();
        var startAnno, endAnno;
        if (anno.type === 'line') {
            startAnno = endAnno = anno;
        } else {
            startAnno = (anno.attributes && anno.attributes.trStartsIn) ?
                AnnotationService.getById(anno.attributes.trStartsIn, $scope.witness.annotations) :
                getLineByOffset(anno.startOffset, anno.startPage);
            endAnno = (anno.attributes && anno.attributes.trEndsIn) ?
                AnnotationService.getById(anno.attributes.trEndsIn, $scope.witness.annotations) :
                getLineByOffset(anno.endOffset, anno.endPage);
        }
        var startNode = getLineByLineId(startAnno.id);
        var endNode = getLineByLineId(endAnno.id);
        var startOffset = anno.startOffset - startAnno.startOffset;
        var endOffset = anno.endOffset - endAnno.startOffset;
        range.setStart(startNode, startOffset);
        range.setEnd(endNode, endOffset);
        anno.bounds = range.getBoundingClientRect();
//    scrollToView(anno.bounds);
        return anno;
    };
    var getLineByLineId = function (lineid) {
        var matchingElement;
        var allElements = document.getElementsByClassName('line');
        for (var i = 0, n = allElements.length; i < n; i++) {
            if (parseInt(allElements[i].getAttribute('data-line-id')) === lineid) {
                matchingElement = (allElements[i].nodeType !== 3) ?
                    allElements[i].firstChild : allElements[i];
                break;
            }
        }
        return matchingElement;
    };
    var getLineByOffset = function (offset, page) {
        var line; // assumes lines do not overlap, per TPEN
        for (var i = 0; i < $scope.witness.annotations.length; i++) {
            if ($scope.witness.annotations[i].type !== 'line' || // not a line
                $scope.witness.annotations[i].endPage < page || // ends before
                $scope.witness.annotations[i].startPage > page || // starts after
                ($scope.witness.annotations[i].endPage === page &&
                    $scope.witness.annotations[i].endOffset) < offset || // ends before
                ($scope.witness.annotations[i].startPage === page &&
                    $scope.witness.annotations[i].startOffset > offset)) { //starts after
                continue; // no index check at the moment TODO
            }
            line = $scope.witness.annotations[i];
            break;
        }
        return line;
    };
    $scope.getLeft = function (anno) {
        return (anno.x / Selection.canvas.width || 0) * 100;
    };
    $scope.getTop = function (anno) {
        return (anno.y / Selection.canvas.height || 0) * 100;
    };
    $scope.getWidth = function (anno) {
        return (anno.width / Selection.canvas.width || 0) * 100;
    };
    $scope.getHeight = function (anno) {
        return (anno.height / Selection.canvas.height || 0) * 100;
    };
    $scope.attachCanvas = function (anno) {
        anno.canvas = $scope.selected.canvas;
    };
    $scope.imgContainerStyle = "display:none;";
    $scope.clipHeight = "height:6em;";
    $scope.clippedLine = function (anno) {
        // This is dumb and shouldn't be here, but angularJS doesn't seem to release the canvas or the ng-if is thinking that selection.canvas:null means there is something there...
        if (!anno.canvas || !anno.canvas.width) {
            $scope.imgContainerStyle = "display:none;";
            return;
        }
        var rect = []; // top, right, bottom, left
        var width;
        var targetWidth = 29; // ems, half the screen for now
        var buffer = .2; // ems, on each side, just picked out of the blue
        var maxHeight = 6; // ems, max height at targetWidth
        width = targetWidth * anno.canvas.width / anno.width;
        rect[0] = targetWidth * anno.y / anno.width + -buffer;
        rect[1] = targetWidth * (anno.width + anno.x) / anno.width + 2 * buffer;
        rect[2] = targetWidth * (anno.y + anno.height) / anno.width + 2 * buffer;
        rect[3] = targetWidth * anno.x / anno.width + -buffer;
        $scope.clipHeight = "height:" + (rect[2] - rect[0]) + "em";
        $scope.imgContainerStyle = "clip:rect(" + rect.join("em ") + "em); width:" + width + "em; top:" + -rect[0] + "em; left:" + -(rect[3]) + "em;";
        if (rect[2] - rect[0] > maxHeight) {
            // too tall, shrink to fit
            var resize = maxHeight / (rect[2] - rect[0]);
            width *= resize;
            rect[0] *= resize;
            rect[1] *= resize;
            rect[2] *= resize;
            rect[3] *= resize;
            $scope.clipHeight = "left:50%; height:" + (rect[2] - rect[0]) + "em";
            $scope.imgContainerStyle = "clip:rect(" + rect.join("em ") + "em); width:" + width + "em; top:" + -rect[0] + "em; left:" + -(rect[3] + (rect[1] - rect[3]) / 2) + "em;";
        }
    };
    var checkForCanvas = function () {
        if (!Selection.canvas) {
            if (!Selection.annotation.canvas)
                return false;
            var deferred = $q.defer();
            CanvasService.get(Selection.annotation.canvas).then(function (canvas) {
                $scope.selected.canvas = canvas;
                deferred.resolve(true);
            });
            return deferred.promise;
        }
        return true;
    };
    $scope.annotationContent = function (k, v) {
        if (angular.isObject(v) || angular.isArray(v)) {
            // more complex
            return "is array:" + v;
        } else {
            return v;
        }
    };
});
tradamus.controller('witnessMetadata', function ($scope, WitnessService) {
    $scope.addDatum = function () {
        if (!$scope.newDatum.type || !$scope.newDatum.content) {
            alert('error: invalid entry');
            return false;
        }
//  metadata format: [{'type':key,'content':value,tags:'tr-metadata','purpose':optional}]
        EditionService.set({
            metadata: [angular.copy($scope.newDatum)].concat($scope.edition.metadata)
        });
        $scope.saveMeta = true;
        $scope.newDatum.type = $scope.newDatum.content = '';
    };
});
tradamus.controller('WitnessStructuralController', function ($scope) {
    $scope.witness.structure = [{label: "first level", nodes: [{label: "second level"}]}, {label: "2nd item - no child"}];
//  $scope.hide = {
//    line: true
//  };
//  $scope.hideTag = {
//    none: true
//  }
    $scope.addToStructure = function (item, index) {
        var index = index || [-1];
        var thisLevel = $scope.witness.structure; // place to insert item
        //check index for recursion
        index = (angular.isArray(index)) ? index : [index];
        while (index.length > 1) {
            thisLevel = thisLevel[index.shift()];
        }
        if (index > -1) {
            thisLevel.splice(index[0], 0, item);
        } else {
            thisLevel.push(item);
        }
        return $scope.witness.structure;
    };
    var textAnnotations = function () {
        var annos = [];
        angular.forEach($scope.witness.transcription, function (page) {
            annos.push(page.annotations);
        });
        return annos;
    };
    var isExtant = function (anno, exists, checkArray) {
        var exists = exists || false;
        var checkArray = checkArray || angular.copy($scope.witness.annoList);
        while (checkArray.length > 1) {
            var thisItem = checkArray.pop();
            exists = isExtant(anno, exists, thisItem);
            if (exists)
                break;
        }
        for (var i = 0; i < checkArray.length; i++) {
            if (exists)
                break;
            exists = (checkArray[i].type === anno.type &&
                checkArray[i].motivation === anno.motivation &&
                checkArray[i].purpose === anno.purpose);
        }
        return exists;
    };
    $scope.typeAnnotation = function (a) {
        switch (a.purpose) {
            case "NONE":
                return "none";
            case "LB":
            case "CB":
            case "PB":
                return "layout";
            case "P":
            case "DIV":
            case "HEAD":
                return "semantics";
            case "NAME":
            case "QUOTE":
            case "NOTE":
                return "reference";
            case "SIC":
            case "CORR":
            case "CHOICE":
            case "ORIG":
            case "REG":
            case "GAP":
            case "UNCLEAR":
            case "ADD":
            case "DEL":
            case "SUBST":
                return "editorial";
            case "TITLE":
            case "AUTHOR":
            case "EDITOR":
            case "WITNESS":
                return "witness";
            case "SETTLEMENT":
            case "INSTITUTION":
            case "REPOSITORY":
            case "COLLECTION":
            case "IDNO":
                return "manuscript";
            default:
                console.log(a.purpose, " was not in ENUM");
                return "unknown";
        }
        ;
    };
    $scope.analyzeAnnotations = function () {
        angular.forEach(textAnnotations, function (a) {

        });
    };

});
tradamus.controller('witnessTranscriptionController', function ($scope, Selection, WitnessService, PageService, AnnotationService, _cache, Display) {
    $scope.display = Display;
    $scope.hide = $scope.hideTag = {};
    $scope.updatePage = function (index) {
        if ($scope.witness && $scope.witness.transcription && $scope.witness.transcription.pages) {
            if (index !== undefined) {
                $scope.selectpage = getPageByPosition(index);
            }
            Selection.select("page", $scope.selectpage);
            if ($scope.selectpage.transcription) {
                // page loaded, no reload
            } else {
                // load a page
                PageService.fetch($scope.selectpage).success(function (page) {
                    $scope.witness.transcription.pages[index] = page; // DEBUG unneccessary assignment?
                });
            }
            $scope.selected.annotation = null;
        }
    };
    var getPageByPosition = function (position) {
        var thisPage = $scope.witness.transcription.pages[position];
        if (thisPage.index !== position) {
            angular.forEach($scope.witness.transcription.pages, function (page, index) {
                if (position === page.index) {
                    thisPage = $scope.witness.transcription.pages[index];
                }
            });
        }
        return thisPage;
    };
    $scope.nextPage = function () {
        var nextIndex = $scope.selectpage.index + 1;
        if (nextIndex + 1 > $scope.witness.transcription.pages.length)
            nextIndex = 0;
        $scope.updatePage(nextIndex);
//    var next = getPageByPosition(nextIndex);
//    $scope.selected.page = next;
//    delete $scope.selected.annotation;
    };
    $scope.previousPage = function () {
        var previousIndex = $scope.selectpage.index - 1;
        if (previousIndex < 0)
            previousIndex = $scope.witness.transcription.pages.length - 1;
        $scope.updatePage(previousIndex);
//    var previous = getPageByPosition(previousIndex);
//    $scope.selected.page = previous;
    };
    $scope.getLineText = function (aid) {
        if (aid.id) {
            aid = aid.id;
        }
        var theAnno = AnnotationService.getById(aid);
        if (!theAnno || !theAnno.content) {
            // did not find annotation
            return;
        }
//        Selection.page = $scope.selected.page;
//        return Selection.page.text.substring(theAnno.startOffset, theAnno.endOffset);
        return theAnno.content;
    };

    $scope.showLine = function (line) {
        if (!line) {
            $scope.selectAnno(null); // remove detail
        }
        $scope.selectAnno($scope.getAnnotationById(parseInt(line)));
    };
    $scope.hideAnnoList = function () {
        $scope.display.annoList = null;
    };
    $scope.fullListOfTags = _cache.fullListOfTags;
    $scope.notThisTag = function (tags, hide) {
        if (!tags || tags.length === 0) {
            tags = "none";
        }
        var tArray = tags.split(" ");
        for (var i = 0; i < tArray.length; i++) {
            if (hide[tArray[i]]) {
                return true;
            }
        }
        return false;
    };
    if (!$scope.selected.page) {
        if (!$scope.witness.transcription) {
            // no transcription
            WitnessService.fetch($scope.witness, true, true, true).then(function (result) {
                $scope.updatePage(0);
            });
        } else if (!$scope.witness.transcription.pages || !$scope.witness.transcription.pages[0]) {
            WitnessService.getPages($scope.witness, 0).then(function (page) {
                $scope.updatePage(0);
            });
        } else {
            $scope.updatePage(0);
        }
    }
});


tradamus.controller('sourceSortController', function ($scope, SectionService, Display) {
    $scope.sortableControl = {
//        itemMoved:function(event){},
        containerPositioning: 'relative',
        orderChanged: function (event) {
            // DEBUG: ticket #505
            return true;
            SectionService.update({
                id: Display.section.id,
                sources: Display.section.sources
            }).then(function (ss) {
            }, function (err, status) {
                Display.sourceMessage = {
                    type: 'danger',
                    msg: "Failed to update outlines: " + status
                };
                throw err;
            });
        },
        containment: '#contain'
    };
});

tradamus.controller('sectionSortController', function ($scope, Publication, Sections) {
    $scope.sortableControl = {
//        itemMoved:function(event){},
        containerPositioning: 'relative',
        orderChanged: function (event) {
            var ss = [];
            var destIndex = event.dest.index;
            var orgIndex = event.source.index;
//        var sids = Publication.sections;
//        $scope.sortedSections = [];
//        angular.forEach(sids, function (s,index) {
//            Sections["id"+s].index=index;
//            $scope.sortedSections.push(Sections["id"+s]);
//        });
            angular.forEach($scope.sortedSections, function (s, $index) {
                if (s.index === orgIndex) {
                    s.index = destIndex;
                } else if (destIndex > orgIndex && destIndex >= s.index && orgIndex < s.index) {
                    s.index--;
                } else if (destIndex < orgIndex && destIndex <= s.index && orgIndex > s.index) {
                    s.index++;
                }
                ss.push(s);
            });
            // sanity check
            ss.sort(function (a, b) {
                return parseInt(a.index) - parseInt(b.index);
            });
            ss.map(function(s,index){
                if(s.index!= index){
                    console.warn("Out of order: "+s.index+" in "+ index +" position.");
                }
               return s.index=index;
            });

            $scope.updateSections(ss);
            $scope.$emit('section-order-changed');
        },
        containment: '#containSections'
    };
});

tradamus.controller('editionTranscriptionController', function ($scope, Materials, Outlines, Sections, AnnotationService, MaterialService, OutlineService, _cache, Display) {
    $scope.display = Display;
    $scope.display.baseText = "";
    $scope.hide = $scope.hideTag = {};
    if (!Display.outline) {
        Display.outline = Outlines["id" + $scope.edition.outlines[0]];
    }
    $scope.materials = Materials;
    $scope.outlines = Outlines;
    $scope.sections = Sections;
    $scope.getOutlines = function (oidArray, noAJAX) {
        return OutlineService.getOutlines(oidArray, noAJAX);
    };
    $scope.getOutline = function (oid, prop) {
        var o = OutlineService.get(oid, true);
        if (prop) {
            return o[prop];
        } else {
            return o;
        }
    };
    $scope.sectionWits = function () {
        var wits = [];
        angular.forEach($scope.display.outline.bounds, function (b) {
            var w = b.startPage && MaterialService.getByContainsPage(b.startPage, true);
            if (w) {
                wits.push(w);
            }
        });
        return wits;
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
    $scope.updateSection = function (outline) {
        if ($scope.edition && $scope.edition.outlines) {
            Display.outline = outline;
            Display.annotation = null;
            $scope.selectDecision(0);
        }
    };
    $scope.nextTitle = function (direction) {
        var s = $scope.edition.outlines[$scope.selected.outline.index + direction];
        return s && s.title;
    };
    $scope.nextSection = function () {
        OutlineService.get($scope.selected.outline).then(function () {
            var nextIndex = $scope.selected.outline.index + 1;
            if (nextIndex + 1 > $scope.edition.outlines.length)
                nextIndex = 0;
            $scope.updateSection($scope.edition.outlines[nextIndex]);
        });
    };
    $scope.previousSection = function () {
        var previousIndex = $scope.selected.outline.index - 1;
        if (previousIndex < 0)
            previousIndex = $scope.edition.outlines.length - 1;
        $scope.updateSection($scope.edition.outlines[previousIndex]);
    };
    $scope.getLineText = function (aid) {
        var theAnno = $scope.getAnnotationById(aid);
        if (!theAnno || !theAnno.content) {
            // did not find annotation
            return;
        }
        return theAnno.content;
    };

    $scope.showLine = function (line) {
        if (!line) {
            $scope.selectAnno(null); // remove detail
        }
        $scope.selectAnno($scope.getAnnotationById(parseInt(line)));
    };
    $scope.hideAnnoList = function () {
        $scope.display.annoList = null;
    };
    $scope.fullListOfTags = _cache.fullListOfTags;
    $scope.notThisTag = function (tags) {
        if (!tags || tags.length === 0) {
            tags = "none";
        }
        var tArray = tags.split(" ");
        for (var i = 0; i < tArray.length; i++) {
            if (Display["hideTag" + tArray[i]]) {
                return true;
            }
        }
        return false;
    };
    $scope.getBoundedText = function (anno) {
        var content = anno.content;
        if (anno.startPage) {
            // get from page
            content = AnnotationService.getBoundedText(anno);
        }
        return content;
    };
});

tradamus.controller('annotationBoundsController', function ($scope, AnnotationService, Edition) {
    $scope.setBounds = function (anno) { //FIXME -lifted from another controller
        /**
         * Create a selection to grab the bounding rectangle to highlight the
         * text range on screen.
         * @param {Annotation} anno The annotation to create the range from
         * @returns {Annotation} anno The full annotation with new bounds property
         */
        var range = document.createRange();
        var startAnno, endAnno;
        if (anno.type === 'line') {
            startAnno = endAnno = anno;
        } else {
            startAnno = (anno.attributes && anno.attributes.trStartsIn) ?
                AnnotationService.getById(anno.attributes.trStartsIn, $scope.witness.annotations) :
                getLineByOffset(anno.startOffset, anno.startPage);
            endAnno = (anno.attributes && anno.attributes.trEndsIn) ?
                AnnotationService.getById(anno.attributes.trEndsIn, $scope.witness.annotations) :
                getLineByOffset(anno.endOffset, anno.endPage);
        }
        var startNode = getLineByLineId(startAnno.id);
        var endNode = getLineByLineId(endAnno.id);
        var startOffset = anno.startOffset - startAnno.startOffset;
        var endOffset = anno.endOffset - endAnno.startOffset;
        range.setStart(startNode, startOffset);
        range.setEnd(endNode, endOffset);
        anno.bounds = range.getBoundingClientRect();
        return anno;
    };
    var getLineByLineId = function (lineid) {
        var matchingElement;
        var allElements = document.getElementsByClassName('line');
        for (var i = 0, n = allElements.length; i < n; i++) {
            if (parseInt(allElements[i].getAttribute('data-line-id')) === lineid) {
                matchingElement = (allElements[i].nodeType !== 3) ?
                    allElements[i].firstChild : allElements[i];
                break;
            }
        }
        return matchingElement;
    };
    var findWitnessFromPage = function (page) {
        var wit;
        for (var i = 0; i < Edition.witnesses.length; i++) {
            var w = Edition.witnesses[i];
            if (w.transcription && w.transcription.pages) {
                for (var j = 0; j < w.transcription.pages.length; j++) {
                    var p = w.transcription.pages[j];
                    if (p && p.id === page) {
                        wit = w;
                        break;
                    }
                }
            }
            if (wit) {
                break;
            }
        }
        return wit;
    };
    var getLineByOffset = function (offset, page) {
        var line; // assumes lines do not overlap, per TPEN
        var witness = findWitnessFromPage(page);
        for (var i = 0; i < witness.annotations.length; i++) {
            if (witness.annotations[i].type !== 'line' || // not a line
                witness.annotations[i].endPage < page || // ends before
                witness.annotations[i].startPage > page || // starts after
                (witness.annotations[i].endPage === page &&
                    witness.annotations[i].endOffset) < offset || // ends before
                (witness.annotations[i].startPage === page &&
                    witness.annotations[i].startOffset > offset)) { //starts after
                continue; // no index check at the moment TODO
            }
            line = witness.annotations[i];
            break;
        }
        return line;
    };
});
tradamus.controller('canvasController', function ($scope, CanvasService, $http) {
    $scope.thisPage = $scope.thisPage || {};
    $scope.$watch($scope.thisPage, function () {
        if ($scope.thisPage.canvas) {
            $scope.getCanvas($scope.thisPage.canvas);
        }
    });
    $scope.thisCanvas = {
        uri: ""
    };
    $scope.getCanvas = function (c) {
        return CanvasService.get(c).then(function (canvas) {
            $scope.thisCanvas = canvas.data.images[0];
        });
    };
    $scope.addImage = function (canvas) {
        $scope.openIntraform('assets/partials/addImageToCanvas.html', $scope);
    };
    $scope.previewImage = new Image();
    $scope.fetchImage = function () {
        $scope.msg.addImageToCanvas = "";
        angular.element($scope.previewImage).on("load", function () {
            $scope.thisCanvas = {
                uri: $scope.previewImage.src,
                height: $scope.previewImage.height,
                width: $scope.previewImage.width
            };
            $scope.msg.addImageToCanvas = "Canvas updated with new image."
            $scope.$apply();
        });
        $scope.previewImage.src = $scope.addUri;

    };
    $scope.msg = {
        addImageToCanvas: ""
    };
    $scope.removeImage = function () {
        if ($scope.thisCanvas.id) {
            $scope.getCanvas.then(function (c) {
                delete c.data.images[0].uri, $scope.previewImage.src, $scope.thisCanvas.uri;
                $scope.msg.addImageToCanvas = "Canvas image removed."
            });
        }
    };
    $scope.canvasImage = function (c) {
        if (!c) {
            return false;
        }
        var img;
        if (!c.images[0]) {
            img = c.images[0];
        } else {
            CanvasService.get(c).then(function (canvas) {
                img = canvas.data.images[0];
            });
        }
        return img;
// TODO if, multiple, return array
    };
});
tradamus.controller('witnessCanvasController', function ($scope, Selection, WitnessService) {
    if (!$scope.selected.canvas) {
        WitnessService.firstCanvas($scope.witness).then(function (canvas) {
            $scope.selected.canvas = Selection.select("canvas", canvas);
        });
    }
    ;
    if (!$scope.witness.manifest.canvasses || !angular.isObject($scope.witness.manifest.canvasses[0])) {
        // load canvas details
        WitnessService.getCanvases($scope.witness, "all").then(function (canvases) {
            WitnessService.set($scope.witness.manifest.canvasses, canvases);
        });
    }
    $scope.updateCanvas = function () {
        if ($scope.witness && $scope.witness.manifest && $scope.witness.manifest.canvasses) {
            Selection.select("canvas", $scope.selected.canvas);
        }
    };
    $scope.getAnnotationById = function (aid) {
        var theAnno = aid;
        angular.forEach($scope.witness.annotations, function (anno) {
            if (anno.id === aid) {
                theAnno = anno;
            }
        });
        return theAnno;
    };
    var findIndexByPosition = function (array, position) {
        var itemIndex = 0;
        angular.forEach(array, function (item, index) {
            if (item.index === position) {
                itemIndex = index;
            }
        });
        return itemIndex;
    };
    $scope.getCanvasAnnotations = function (canvas) {
        if (!canvas)
            return false;
        angular.forEach(canvas.lines, function (line, index) {
            canvas.lines[index] = $scope.getAnnotationById(line);
        });
        return canvas.lines;
    };
    var selectAdjacentCanvas = function (direction) {
        $scope.selected.canvas = $scope.witness.manifest.canvasses[findIndexByPosition($scope.witness.manifest.canvasses, $scope.selected.canvas.index + direction)];
        if (!$scope.selected.canvas)
            $scope.selected.canvas = $scope.witness.manifest.canvasses[0];
        $scope.updateCanvas();
    };
    $scope.nextPage = function () {
        $scope.selectAnno(null);
        selectAdjacentCanvas(1);
    };
    $scope.previousPage = function () {
        $scope.selectAnno(null);
        selectAdjacentCanvas(-1);
    };
    $scope.$on('tabClick', function (event, msg) {
        if (msg === "Images" && !$scope.selected.canvas) {
            if (!$scope.witness.manifest.canvasses || !$scope.witness.manifest.canvasses[0]) {
                WitnessService.getCanvases($scope.witness, "all").then(function (canvases) {
                    $scope.selected.canvas = $scope.witness.manifest.canvasses[0];
                    $scope.updateCanvas();
                });
            } else {
                $scope.selected.canvas = $scope.witness.manifest.canvasses[0];
                $scope.updateCanvas();
            }
        }
    });
});
tradamus.controller('textInputMetadataController', function ($scope, EditionService, $http) {
    $scope.metadata = {};
    $scope.updatePreview = function () {
        $scope.metadata.preview = $scope.parsed();
    };
    $scope.parsed = function () {
        var preview = {};
        if ($scope.isJson($scope.metadata.input)) {
            // JSON
            preview = JSON.parse($scope.metadata.input);
        } else if ($scope.metadata.input.indexOf(',') !== -1 && CSVtoArray($scope.metadata.input, true)) {
            // CSV
            var csvalue = CSVtoArray($scope.metadata.input);
            for (var i = 0; i < csvalue.length - 1; i += 2) {
                preview[csvalue[i]] = csvalue[i + 1] || "undefined";
            }
        } else {
            // XML or bust
            var x2js = new X2JS();
            var test = x2js.xml_str2json($scope.metadata.input);
            preview = test || $scope.metadata.input;
        }
        return preview;
    };
    $scope.isJson = function (str) {
        try {
            JSON.parse(str);
        } catch (e) {
            return false;
        }
        return true;
    };
    $scope.manipulated = [];
    $scope.resetManipulated = function () {
        $scope.manipulated = $scope.manipulateObject($scope.metadata.preview);
    };
    $scope.manipulateObject = function (obj) {
        var newObj = [];
        angular.forEach(obj, function (content, type) {
            newObj.push({type: type, content: content});
        });
        return newObj;
    };
    $scope.isObj = function (data) {
        return angular.isObject(data);
    };
    var arrayToObj = function (array, key) {
        var toret = {};
        angular.forEach(array, function (item, $index) {
            toret[key + "_" + $index] = item;
        });
        return toret;
    };
    $scope.removeNode = function (type, replaceWith) {
        // an array
        var len = $scope.manipulated.length;
        for (var i = 0; i < len; i++) {
            if (type === $scope.manipulated[i].type) {
                toRemove = $scope.manipulated.splice(i, 1);
                angular.forEach(replaceWith, function (prop) {
                    $scope.manipulated.splice(i++, 0, prop);
                });
                break;
            }
        }
        return $scope.manipulated;
    };

    $scope.expandNode = function (type) {
        // an array
        var len = $scope.manipulated.length;
        for (var i = 0; i < len; i++) {
            if (type === $scope.manipulated[i].type) {
                var guts = $scope.manipulateObject($scope.manipulated[i].content);
                if (angular.isArray(guts)) {
                    guts = arrayToObj(guts, type);
                }
                $scope.removeNode(type, guts);
                break;
            }
        }
        return $scope.manipulated;
    };

    $scope.enterNode = function (type) {
        // an array
        var len = $scope.manipulated.length;
        for (var i = 0; i < len; i++) {
            if (type === $scope.manipulated[i].type) {
                toEnter = $scope.manipulated = $scope.manipulateObject($scope.manipulated[i].content);
                break;
            }
        }
        return $scope.manipulated;
    };
    $scope.addDatum = function (datum) {
        if (!datum.type || !datum.content) {
            alert('error: invalid entry - ' + datum);
            return false;
        }
//  metadata format: [{'type':key,'content':value,tags:'tr-metadata','purpose':optional}]
        EditionService.set({
            metadata: [datum].concat($scope.edition.metadata)
        });
    };
    $scope.addMetadata = function () {
        angular.forEach($scope.manipulated, function (datum) {
            $scope.addDatum(datum);
        });
        EditionService.saveMetadata();
        $scope.closeIntraform();
    };
    // Return array of string values, or NULL if CSV string not well formed.
    var CSVtoArray = function (text, testOnly) {
        var re_valid = /^\s*(?:'[^'\\]*(?:\\[\S\s][^'\\]*)*'|"[^"\\]*(?:\\[\S\s][^"\\]*)*"|[^,'"\s\\]*(?:\s+[^,'"\s\\]+)*)\s*(?:,\s*(?:'[^'\\]*(?:\\[\S\s][^'\\]*)*'|"[^"\\]*(?:\\[\S\s][^"\\]*)*"|[^,'"\s\\]*(?:\s+[^,'"\s\\]+)*)\s*)*$/;
        var re_value = /(?!\s*$)\s*(?:'([^'\\]*(?:\\[\S\s][^'\\]*)*)'|"([^"\\]*(?:\\[\S\s][^"\\]*)*)"|([^,'"\s\\]*(?:\s+[^,'"\s\\]+)*))\s*(?:,|$)/g;
        // Return NULL if input string is not well formed CSV string.
        if (!re_valid.test(text))
            return null;
        if (testOnly)
            return true;
        var a = [];                     // Initialize array to receive values.
        text.replace(re_value, // "Walk" the string using replace with callback.
            function (m0, m1, m2, m3) {
                // Remove backslash from \' in single quoted values.
                if (m1 !== undefined)
                    a.push(m1.replace(/\\'/g, "'"));
                // Remove backslash from \" in double quoted values.
                else if (m2 !== undefined)
                    a.push(m2.replace(/\\"/g, '"'));
                else if (m3 !== undefined)
                    a.push(m3);
                return ''; // Return empty string.
            });
        // Handle special case of empty last value.
        if (/,\s*$/.test(text))
            a.push('');
        return a;
    };
    $scope.remote = "";
    $scope.resolveURI = function () {
        if (!$scope.remote) {
            return false;
        }
        $http.get($scope.remote).success(function (metadata) {
            if (angular.isObject(metadata)) {
                //maybe JSON?
                try {
                    metadata = JSON.stringify(metadata);
                } catch (e) {
                    // silent
                }
            }
            $scope.metadata.input = metadata;
            $scope.updatePreview();
        }).error(function (e) {
            $scope.metadata.input = "Request failed to connect.";
            throw e;
        });
    };
});
tradamus.controller('AnnotationListController', function ($scope) {
    // show and select list of annotations
    $scope.hide = {
        line: true,
        "tr-outline-annotation": true
    };
    $scope.hideTag = {};
    $scope.selectAndShow = function (anno) {
        $scope.selectAnno(anno);
//    $scope.display.annoList = null;
    };
});
tradamus.controller('editAnnotationController', function ($scope, AnnotationService, Display, Lists, Annotations) {
    $scope.sensible = function (pair) {
        // TODO this can be min and max in HTML5 forms. probably
        switch (pair) {
            case "offset":
                if ($scope.selected.annotation.endOffset > $scope.selected.page.text.length) {
                    $scope.selected.annotation.endOffset = $scope.selected.page.text.length;
                }
                if ($scope.selected.annotation.startOffset > $scope.selected.annotation.endOffset) {
                    $scope.selected.annotation.startOffset = $scope.selected.annotation.endOffset;
                }
                break;
            case "page":
                if ($scope.selected.annotation.startPage > $scope.selected.annotation.endPage) {
                    // TODO this is bogus because the page number is not the same as the index, necessarily
                    $scope.selected.annotation.startPage = $scope.selected.annotation.endPage;
                }
                break;
            default:
                break;
        }
        ;
    };
    $scope.getContextText = function (direction, length) {
        var direction = direction || 1;
        var length = length || 20; // buffer
        var text = "";
        if ($scope.selected && $scope.selected.page) {
            var page = $scope.selected.page;
            if ($scope.selected.annotation) {
                var start = (direction === 1) ? $scope.selected.annotation.endOffset : $scope.selected.annotation.startOffset - length;
                if (start < 0) {
                    length = $scope.selected.annotation.startOffset;
                    start = 0;
                }
                text = page.text.substr(start, length);
            }
        }
        return text;
    };
    $scope.saveAnnotation = function (a) {
        var a = a || Display.annotation;
        if (a.id > 0) {
            // updating
            AnnotationService.setAnno(a).then(function (anno) {
                Display.anno = Annotations[anno.id];
            });
        } else {
            // creating
            AnnotationService.setAnno(a).then(function (anno) {
                if (anno.target.indexOf("outline") === 1 || anno.target.indexOf("annotation") === 1) {
                    // Outline Annotation or annotation annotations
                    if (!Display.outline.annotations) {
                        Display.outline.annotations = [];
                    }
                    Lists.addIfNotIn(anno.id, Display.outline.annotations);
                } else if ($scope.material) {
                    Lists.addIfNotIn(anno.id, $scope.material.annotations);
                }
                Annotations[anno.id] = anno;
                Display.anno = Annotations[anno.id];
            });
        }
    };
});
tradamus.controller('variantListController', function ($scope, _cache, Display, CollationService, MaterialService, Lists, Annotations) {
    $scope.debugescape = function (str) { //DEBUG
        return escape(str);
    };
    $scope.choose = function (chosenContent, event) {
        if (event) {
            event.stopPropagation();
        }
        var dIndex = getIndex($scope.decision)
        if (Display.decision === dIndex) {
            $scope.decision.content = chosenContent;
        } else {
            // direction for animations
            Display.decision = dIndex;
            if (Display.outline && Display.outline.decisions) {
                CollationService.groupMotes(Display.outline.decisions[dIndex - 1]);
                CollationService.groupMotes(Display.outline.decisions[dIndex]);
                CollationService.groupMotes(Display.outline.decisions[dIndex + 1]);
            }
        }
    };
    $scope.direction = function () {
        return Display.direction;
    };
    $scope.isChosen = function (chosenContent) {
        return CollationService.matchContent($scope.decision.content, chosenContent);
    };
    $scope.noMotes = function (moteset) {
        if (!moteset) {
            return;
        }
        var hidden = 0;
        angular.forEach(moteset, function (mote) {
            if ($scope.isHidden(mote.witnesses)) {
                hidden++;
            }
        });
        return hidden === moteset.length;
    };
    $scope.isHidden = function (idArray) {
        if (!idArray) {
            return;
        }
        var hidden = 0;
        angular.forEach(idArray, function (mid) {
            if (Display["hideMaterial" + mid]) {
                hidden++;
            }
        });
        return hidden === idArray.length;
    };
    $scope.hasUniqueContent = function (motesets) {
        if (!motesets && $scope.decision) {
            // try to get motesets
            CollationService.groupMotes([$scope.decision]);
        }
        if (!motesets || !$scope.decision || !$scope.decision.content || !$scope.decision.content.length) {
            return;
        }
        for (var i = 0; i < motesets.length; i++) {
            if ($scope.isChosen(motesets[i].content) && !$scope.isHidden(motesets[i].witnesses)) {
                return false;
            }
        }
        return true;
    };
    var getIndex = function (decision) {
        var decisions = Display.outline.decisions || _cache.decisions;
        for (var i = 0; i < decisions.length; i++) {
            if (decisions[i] === decision) {
                return i;
            }
        }
    };
    $scope.getCanvasSelector = function (mote) {
        var selector, page, lines, startLine, endLine;
            var material = MaterialService.getByContainsPage(mote.startPage, true);
            if (!material || !material.transcription || !material.transcription.pages) {
                // selector will return undefined
                // TODO: asynch to load pages and then get canvases, if available
            } else {
            page = Lists.getAllByProp("id", mote.startPage, material.transcription.pages)[0];
            lines = Lists.dereferenceFrom(page.lines, Annotations);
            if (mote.startPage === mote.endPage) {
                // find and start and end line
                for (var i = 0; i < lines.length; i++) {
                    if (!startLine
                        && lines[i].endOffset > mote.startOffset
                        && lines[i].startOffset <= mote.startOffset) {
                        startLine = lines[i];
                    }
                    if (!endLine
                        && lines[i].startOffset < mote.endOffset
                        && lines[i].endOffset >= mote.endOffset) {
                        endLine = lines[i];
                    }
                    if (startLine && endLine) {
                        break;
                    }
                }
            } else {
                // variant spans pages, just show the beginning for now
                endLine = lines[0];
                for (var i = 0; i < lines.length; i++) {
                    if (!startLine
                        && lines[i].endOffset > mote.startOffset
                        && lines[i].startOffset <= mote.startOffset) {
                        startLine = lines[i];
                    }
                    if (endLine.endOffset < lines[i].endOffset) {
                        endLine = lines[i];
                    }
                }
            }
            if (startLine.canvas !== endLine.canvas) {
                var pos1 = getXYWHfromSelector(startLine.canvas);
                var pos2 = getXYWHfromSelector(endLine.canvas);
                selector = "canvas/"
                    + parseInt(startLine.canvas.substr(startLine.canvas.indexOf("canvas/") + 7))
                    + "#xywh=" + absoluteDistance(pos1, pos2).join(",");
                // TODO: does not show multipage annotations
            } else {
                selector = startLine.canvas;
            }
        }
        mote.canvas = selector;
        return selector;
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
    function getXYWHfromSelector (selector, dimension) {
        // expect a selector like "canvas/432#xywh=0,0,135,18"
        var dims = selector.substr(selector.indexOf("xywh=") + 5).split(",");
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
    }
    ;

});

tradamus.controller('collationCanvasController', function ($scope, Edition, _cache) {
    $scope.thisNode = $scope.thisNode || 0;
//    $scope.edition = $scope.edition || Edition;
    $scope.nodes = _cache.decisions; // []
    $scope.position = {
        centerAt: 0, // position along entire tree
        width: 500, // of complete canvas
        height: 200, // of complete canvas
        spread: 40, // horizontal gap between dots
        radius: 10, // of node dots
        groupPadding: 35 // vertical gap between branches
    };
    var r = $scope.position.radius;
    var c = $scope.position.centerAt;
    var w = $scope.position.width;
    var h = $scope.position.height;
    var s = $scope.position.spread;
    $scope.drawDot = function (context, dot) {
        context.beginPath();
        context.arc(dot.x - c + w / 2, dot.y, r * (dot.proportion || 1), 0, Math.PI * 2, false);
//    if (dot.proportion) {
//      context.globalAlpha = dot.proportion;
//    }
        context.fill();
//    context.globalAlpha = 1;
    };
    $scope.visibleLines = [];
    $scope.resetDots = true; // DEBUG changing spread
    $scope.connectJumpNode = function (context, firstDot, secondDot) {
        // find left-most node
        var l = (firstDot.x < secondDot.x) ? {
            x1: firstDot.x - c + w / 2,
            y1: firstDot.y,
            p1: firstDot.proportion,
            x2: secondDot.x - c + w / 2,
            y2: secondDot.y,
            p2: secondDot.proportion
        } : {
            x1: secondDot.x - c + w / 2,
            y1: secondDot.y,
            p1: secondDot.proportion,
            x2: firstDot.x - c + w / 2,
            y2: firstDot.y,
            p2: firstDot.proportion
        };
        var ctrl = {
            x1: l.x1 + s,
            x2: l.x2 - .5 * s
        };
        ctrl.y1 = (l.y1 < h / 2) ? l.y1 - 1.5 * $scope.position.groupPadding : l.y1 + .5 * $scope.position.groupPadding;
        ctrl.y2 = (l.y2 < h / 2) ? l.y2 - 1.5 * $scope.position.groupPadding : l.y2 + .5 * $scope.position.groupPadding;
        if (ctrl.y1 < l.y1 && l.y2 === h / 2) {
            // top half
            ctrl.y2 -= $scope.position.groupPadding;
        }
        context.beginPath();
        context.moveTo(l.x1 + r * l.p1, l.y1);
        context.bezierCurveTo(ctrl.x1, ctrl.y1, ctrl.x2, ctrl.y2, l.x2 - r * l.p2, l.y2); // edge of dot to edge
        context.lineCap = "butt";
        context.stroke();
        $scope.visibleLines.push(l);
    };
    $scope.connectDots = function (context, firstDot, secondDot, stroke) {
        // find left-most node
        var l = (firstDot.x < secondDot.x) ? {
            x1: firstDot.x - c + w / 2,
            y1: firstDot.y,
            p1: firstDot.proportion,
            x2: secondDot.x - c + w / 2,
            y2: secondDot.y,
            p2: secondDot.proportion
        } : {
            x1: secondDot.x - c + w / 2,
            y1: secondDot.y,
            p1: secondDot.proportion,
            x2: firstDot.x - c + w / 2,
            y2: firstDot.y,
            p2: firstDot.proportion
        };
        context.beginPath();
        context.moveTo(l.x1 + r * l.p1, l.y1);
        context.lineTo(l.x2 - r * l.p2, l.y2); // edge of dot to edge
        context.lineWidth = stroke;
        context.lineCap = "butt";
        context.stroke();
        $scope.visibleLines.push(l);
    };
    $scope.drawDecision = function (canvas, nodeIndex) {
        r = $scope.position.radius;
        w = $scope.position.width;
        h = $scope.position.height;
        s = $scope.position.spread;
        c = nodeIndex * s;
        var nodeIndex = nodeIndex || $scope.thisNode;
        // DEBUG
        var canvas = document.getElementById(canvas);
        $scope.nodes = $scope.nodes || _cache.decisions;
        var context = canvas.getContext('2d');
        // Create gradient
        var grd = context.createRadialGradient(250.000, 250.000, 0.000, 250.000, 250.000, 250.000);

        // Add colors
        grd.addColorStop(0.000, 'rgba(247, 0, 0, 1.000)');
        grd.addColorStop(1.000, 'rgba(114, 2, 2, 1.000)');
        context.shadowColor = "rgba(0,0,0,.5)";
        context.shadowBlur = r / 2;
        // Fill with gradient
        context.fillStyle = grd;
        // clear and midline
        context.clearRect(0, 0, w, h);
        context.moveTo(w / 2, 0);
        context.lineTo(w / 2, h);
        context.strokeStyle = "silver";
        context.stroke();
        context.strokeStyle = "black";
        // find all nodes
        var theseNodes = collectVisibleNeighbors(nodeIndex, -1).concat($scope.nodes[nodeIndex]).concat(collectVisibleNeighbors(nodeIndex, 1));
        $scope.stuff = $scope.nodes[nodeIndex];
        // distribute them vertically
        angular.forEach(theseNodes, function (d, di) {
            if (!d) {
                theseNodes.splice(di, 1);
                // DEBUG: weird undefined from collectVisibleNeighbors()
            } else {
                var stack = d.motesets.length;
                angular.forEach(d.motesets, function (n, i) {
                    if ($scope.resetDots || !theseNodes[di].motesets[i].dot) {
                        theseNodes[di].motesets[i].dot = n.dot = {
                            x: theseNodes[di].index * s,
                            y: $scope.position.groupPadding * ((-2 * i + stack - 1) / 2) + h / 2, // vertically center stack
                            proportion: n.witnesses.length / $scope.edition.witnesses.length / 2 + .5 // .5-1
                        };
                    }
                    $scope.drawDot(context, n.dot);
                });
            }
        });
        $scope.visibleNodes = theseNodes;
        // connect to adjacent nodes
        angular.forEach(theseNodes, function (d, index) {
            if (index < theseNodes.length - 1) {
                angular.forEach(d.motesets, function (n) {
                    if ($scope.drawAllLines(context, n, theseNodes[index + 1].motesets)) {
                        // lines drawn as expected
                    } else {
                        // no neighbor found
                        $scope.drawJumpLine(context, n, theseNodes, index + 1);
                    }
                    ;
                });
            }
        });
    };
    $scope.findClickTarget = function (e) {
        var canvas;
        if (!e) {
            var e = window.event;
        }
        if (e.target) {
            canvas = e.target;
        }
        else if (e.srcElement) {
            canvas = e.srcElement;
        }
        if (canvas.nodeType === 3) { // defeat Safari bug
            canvas = canvas.parentNode;
        }
        var click = (e.offsetX === null) ? {
            x: e.originalEvent.layerX, // firefox
            y: e.originalEvent.layerY
        } : {
            x: e.offsetX,
            y: e.offsetY
        };
        for (var i = 0; i < $scope.visibleNodes.length; i++) {
            if (Math.abs(click.x - $scope.visibleNodes[i].x) < r &&
                Math.abs(click.y - $scope.visibleNodes[i].y) < r) {
                alert('clicked node:' + $scope.visibleNodes[i]);
                return $scope.visibleNodes[i];
            }
        }
        for (var i = 0; i < $scope.visibleLines.length; i++) {
            if (click.x > $scope.visibleLines[i].x1 &&
                click.x < $scope.visibleLines[i].x2 &&
                click.y > $scope.visibleLines[i].y1 &&
                click.y < $scope.visibleLines[i].y2) {
                // in bounding box, test for on line
                var m = ($scope.visibleLines[i].y2 - $scope.visibleLines[i].y1) / ($scope.visibleLines[i].x2 - $scope.visibleLines[i].x1);
                var b = -m * $scope.visibleLines[i].x1 + $scope.visibleLines[i].y1;
                if (click.y === m * click.x + b) {
                    // on the line
                    alert('clicked line:' + $scope.visibleLines[i]);
                    return $scope.visibleLines[i];
                }
            }
        }
        return false;
    };
    $scope.drawJumpLine = function (context, node, theseNodes, index) {
        angular.forEach(node.witnesses, function (wid) {
            var continuesAt = {};
            for (var i = index; i < theseNodes.length; i++) {
                for (var j = 0; j < theseNodes[i].motesets.length; j++) {
                    if (theseNodes[i].motesets[j].witnesses.indexOf(wid) > -1) {
                        continuesAt = theseNodes[i].motesets[j].dot;
                        break;
                    }
                }
                if (continuesAt.y) { // will only jump forward for now TODO: improve range
                    $scope.connectJumpNode(context, node.dot, continuesAt);
                    break;
                }
            }
            if (!continuesAt.y && theseNodes.length - index > 2) { // nothing visible, look further
                var collectionIndex = $scope.thisNode - (theseNodes.length / 2 + .5) + index + 1;
                $scope.drawDistantLine(context, node, node.witnesses[0], collectionIndex, $scope.nodes); // FIXME: pass a real witness ID all the way through
            }
        });
    };
    $scope.drawDistantLine = function (context, node, wid, index, collection) {
        var point;
        for (var i = index; i < collection.length; i++) {
            for (var j = 0; j < collection[i].motesets.length; j++) {
                if (collection[i].motesets[j].witnesses.indexOf(wid) > -1) {
                    $scope.drawForwardArrow(context, node.dot);
                    point = true;
                    break;
                }
            }
            if (point)
                break; // Thank you very much - and don't forget to vote!
        }
    };
    $scope.drawForwardArrow = function (context, dot) {
        context.beginPath();
        var adjx = w / 2 - c;
        var end = {x: w};
        var ctrl = {
            x1: dot.x + adjx,
            x2: dot.x + adjx
        };
        var start = {
            x: dot.x + adjx
        };
        if (dot.y > h / 2) {
            end.y = h - r * 2;
            ctrl.y1 = dot.y + (h - dot.y) / 2;
            ctrl.y2 = h;
            start.y = dot.y + r * dot.proportion;
        } else {
            end.y = r * 2;
            ctrl.y1 = dot.y / 2;
            ctrl.y2 = 0;
            start.y = dot.y - r * dot.proportion;
        }
        context.strokeStyle = "rgba(0, 0, 0, .5)";
        arrow(context, start, end, ctrl);
        context.stroke();
        context.font = ".75em 'Roboto',sans-serif";
        context.textBaseline = "bottom";
        context.fillText("continues", end.x - 100, end.y);
        context.strokeStyle = "rgb(0, 0, 0)";
    };
    var arrow = function (context, from, to, ctrl) {
        var headlen = r;   // length of head in pixels
        var angle;
        context.moveTo(from.x, from.y);
        if (ctrl) {
            // curve
            if (ctrl.x1) {
                // bezier
                angle = Math.atan2(to.y - ctrl.y2, to.x - ctrl.x2);
                context.bezierCurveTo(ctrl.x1, ctrl.y1, ctrl.x2, ctrl.y2, to.x, to.y);
            } else if (ctrl.x) {
                // quadratic
                angle = Math.atan2(to.y - ctrl.y, to.x - ctrl.x);
                context.quadraticCurveTo(ctrl.x, ctrl.y, to.x, to.y);
            } else {
                throw Error("Unknown ctrl format");
            }
        } else {
            // simple line
            angle = Math.atan2(to.y - from.y, to.x - from.x);
            context.lineTo(to.x, to.y);
        }
        context.lineTo(to.x - headlen * Math.cos(angle - Math.PI / 6), to.y - headlen * Math.sin(angle - Math.PI / 6));
        context.moveTo(to.x, to.y);
        context.lineTo(to.x - headlen * Math.cos(angle + Math.PI / 6), to.y - headlen * Math.sin(angle + Math.PI / 6));
    };
    $scope.drawAllLines = function (context, node, drawTo) {
        // if this node shares a witness with that next node, draw a line, thicker for
        var hasConnection = false;
        angular.forEach(node.witnesses, function (wid) {
            var isLinked = 0;
            for (var i = 0; i < drawTo.length; i++) {
                if (drawTo[i].witnesses.indexOf(wid) > -1) {
                    isLinked++; // TODO: build in strength with trace
                    $scope.connectDots(context, node.dot, drawTo[i].dot, isLinked);
                    hasConnection = true;
                }
            }
            isLinked = 0; // reset
        });
        return hasConnection;
    };
    var collectVisibleNeighbors = function (nodeIndex, direction) {
        var neighbors = [];
        var onscreen = (direction === -1) ? Math.min(nodeIndex, parseInt(w / 2 / s + 3)) : Math.min($scope.nodes.length - nodeIndex, parseInt(w / 2 / s + 4)); // offscreen buffer of 4 motes for drawing lines
        if (direction > 0) {
            if ($scope.nodes[nodeIndex + 1] !== undefined) { //skip if end of list
                neighbors = neighbors.concat($scope.nodes.slice(nodeIndex + 1, nodeIndex + onscreen));
            }
        } else {
            neighbors = $scope.nodes.slice(nodeIndex - onscreen, nodeIndex).concat(neighbors);
        }
        return neighbors;
    };
    // TODO: clicked line means show connection options
    // TODO: clicked node means show details, focus context, recenter display
    // TODO: scroll and scale tree display
});
tradamus.controller('commentFormController', function ($scope, AnnotationService) {
    $scope.possibleTypes = [
        {label: "Comment",
            motivation: "oa:commenting",
            description: "Comment on or review this annotation and its referenced resources"
        },
        {label: "Edit",
            motivation: "oa:editing",
            description: "Request a modification or edit to this annotation and its referenced resources"
        },
        {label: "Question",
            motivation: "oa:questioning",
            description: "Ask about this annotation and its referenced resources"
        },
        {label: "Link",
            motivation: "oa:linking",
            description: "Link this annotation and its resources to another resource"
        },
        {label: "Tagging",
            motivation: "oa:tagging",
            description: "Tag this annotation with a simple string"
        }
    ];
    $scope.pickedType = $scope.possibleTypes[0];
    $scope.comment = {
        attributes: {
            target: "annotation/" + $scope.annotation.id  // DEBUG #510
        },
        type: "tr-publicationComment",
        content: "",
        target: "annotation/" + $scope.annotation.id
    };
    $scope.saveComment = function (comment) {
        angular.extend($scope.comment.attributes, {
            "@type": "http://www.w3.org/ns/oa#Motivation",
            "http://www.w3.org/ns/oa#motivatedBy": $scope.pickedType.motivation
        });
//        var url = "annotations" // DEBUG #510
        var url = "edition/" + $scope.edition.id + "/metadata";
        AnnotationService.setAnno(comment, url).then(function () {
            $scope.modal.close();
        });
    };
});
tradamus.controller('helpController', function ($scope) {
});
tradamus.controller('siteMatrixController', function ($scope) {
    $scope.$on('show-matrix', function () {
        $scope.showMatrix = true;
    });
});
tradamus.controller('bugReportController', function ($scope, $location, $modal, User) {
    /**
     * Create a new ticket in SourceForge under the Tradamus project.
     * No supporting service for just the http.
     */
    $scope.messages = [];
    $scope.umail = User.mail;
    $scope.url = $location.path();
    $scope.showFeedback = function () {
        $scope.modal = $modal.open({
            templateUrl: 'assets/partials/forms/bugReport.html',
            controller: 'bugReportController',
            scope: $scope
        });
    };
//    $scope.$on('logout', function (event, user) {
//        $scope.umail = user.mail;
//    });
});
tradamus.controller('tradamusController', function ($scope, Edition, EditionService, User, Sections, Display, Outlines, Annotations, Publication) {
    $scope.edition = Edition;
    $scope.user = User;
    $scope.$on('logout', function (event, user) {
        EditionService.reset();
        Outlines = {};
        Annotations = {};
        Sections = [];
        Publication = {};
        $scope.user = User = user;
        return true;
    });
});