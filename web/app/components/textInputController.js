tradamus.controller('textInputController', function ($scope, $sce, EditionService, $http, FileUploadService) {
    $scope.file = {};
    $scope.FileReaderSupported = !!FileUploadService.isSupported();
    $scope.newData = $scope.newData || {};
    if (!$scope.data) {
        $scope.data = {};
    }
    $scope.updatePreview = function () {
        $scope.newData.preview = $scope.parsed($scope.newData.input);
        $scope.importText.$setPristine();
        $scope.newData.isObj = $scope.isObj($scope.newData.preview);
        if (!$scope.newData.isObj) {
            $scope.textIsType($scope.newData.input);
        }
    };
    $scope.previewContent = function () {
        FileUploadService.readAsText($scope.file, $scope).then(function (result) {
            $scope.newData.input = result;
            $scope.updatePreview();
        }, function () {
            // error
        });
    };
    $scope.commitEdits = function (commitObj) {
        $scope.newData.input = ($scope.isJson(commitObj)) ? commitObj : JSON.stringify(commitObj);
        $scope.data.fullscreen = false;
    };
    /**
     * Transforms string to an AngularJS trusted HTML string.
     * @param {string} str string to trust
     * @returns {$sce} Trusted HTML string
     */
    $scope.trust = function (str) {
        return $sce.trustAs('html', str);
    };
    /**
     * Test text type from file upload and return a user readable message to the user.
     * @alters {string} $scope.textType Result of type test. Type or HTML message.
     * @returns {Boolean} false on failure
     */
    $scope.textIsType = function (input) {
        if (!input) {
            return false;
        }
        if (input.indexOf("{") > -1) {
            // maybe bad JSON
            $scope.newData.textType = $scope.trust("It looks like you may be trying to upload a JSON object, "
                + "but it is improperly formed. <a href='http://jsonlint.com/"
                //+ "?json="+encodeURIComponent($scope.metadata.input) // easily made query too big for server
                + "' target='_blank'>JSON validator</a>");
        } else if (input.indexOf("<") > -1 && input.indexOf(">") > -1) {
            $scope.newData.textType = $scope.trust("It looks like you may be trying to input XML markup, "
                + "but it is invalid. <a href='http://validator.w3.org/#validate_by_input' target='_blank'>"
                + "XML validator</a>");
        } else {
            $scope.newData.textType = $scope.trust("The content of this file does not seem to be valid XML or JSON. "
                + "If you are attempting to upload only a transcription, please create "
                + "a new witness and then edit the transcription directly.");
        }
    };
    $scope.parsed = function (input) {
        var preview = {};
        if (input) {
            if ($scope.isJson(input)) {
                // JSON
                preview = JSON.parse(input);
            } else if (input.indexOf(',') !== -1 && CSVtoArray(input, true)) {
                // CSV
                var csvalue = CSVtoArray(input);
                for (var i = 0; i < csvalue.length - 1; i += 2) {
                    preview[csvalue[i]] = csvalue[i + 1] || "undefined";
                }
            } else {
                // XML or bust
                var x2js = new X2JS();
                var test = x2js.xml_str2json(input);
                preview = test || input;
            }
        }
        if (preview.parsererror) {
//            Firefox has a strange problem with x2js where a parsererror is not
//            caught and is returned as an object, so we are just checking for
//            that here and resetting the preview if it is found.
            preview = input;
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
    $scope.isObj = function (data) {
        return angular.isObject(data);
    };
    $scope.addMetadata = function (dataObj) {
        var mArray = [];
        angular.forEach(dataObj, function (v, k) {
            if (angular.isObject(v)) {
                v = JSON.stringify(v);
            }
            mArray.push({
                type: k, content: v, tags: 'tr-metadata'
            });
        });
        EditionService.saveMetadata(mArray).success(function () {
            if ($scope.modal) {
                $scope.modal.close("Added new metadata");
            }
        });
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
    $scope.resolveURI = function (link) {
        if (!link) {
            return false;
        }
        var config = {
            headers: {
                'Access-Control-Allow-Origin': '*'
            }
        };
        $http.get(link).success(function (data) {
            if (angular.isObject(data)) {
                //maybe JSON?
                try {
                    data = JSON.stringify(data);
                } catch (e) {
                    // silent
                }
            }
            $scope.newData.input = data;
            $scope.updatePreview();
        }).error(function (e) {
            $scope.newData.input = "Request failed to connect. "
                + "The hosting server may not have CORS enabled. Please download "
                + "the file and then upload it here.";
            throw e;
        });
    };
})
    .directive('textImport', function () {
        return {
            restrict: 'A',
            controller: 'textInputController',
            link: function (scope, element, attrs) {
                scope.data = scope[attrs.textImport];
            }
        };
    });