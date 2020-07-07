tradamus.value('User',{
    name    : "",
    mail    : "",
    activity: [],
        id: false
    });

tradamus.value('Witness', {
    title: "",
    siglum: "",
    metadata: []
  });

tradamus.value('Transcription', {
    id: -1,
    editor: "",
    pages: []
});

tradamus.value('Page', {
    id: -1,
    index: -1,
    title: "",
    text: "",
    annotations: []
});

tradamus.value('Manifest', {
    id: -1,
    canvases: []
});

tradamus.value('Canvas', {
    id: -1,
    title: "",
    annotations: []
});

tradamus.value('Edition', {
    id: 0,
    title: "",
    witnesses: [],
    metadata: [],
    permissions:[],
    creator: -1,
    siglum:""
});
tradamus.value('Outlines', {});
tradamus.value('Annotations', {});
tradamus.value('Materials', {});
tradamus.value('Section', {
        id: 0,
        publication: 0,
        title: "",
        type: "", //"TEXT", "ENDNOTE", "FOOTNOTE", "INDEX", or "TABLE_OF_CONTENTS"
        index: 0, // ordering withing publication
        decoration: [], //Rules
        layout: [], //Rules
        sources: [], // Outline IDs
        template: ""
});
tradamus.value('Sections', {});

tradamus.factory('Rules', function () {
    return {
        id: 0,
        section: 0,
        selector: "",
        action: ""
    };
});
tradamus.factory('Publication', function () {

    var publication = {
        id: 0,
        edition: 0,
        type: "DYNAMIC", //"PDF", "TEI", "DYNAMIC", "OAC", or "XML"
        permissions: [],
        title: "",
        sections: [] //Sections
//   "creator": User ID,
//   "creation": timestamp,
//   "modification": timestamp,
    };
    return publication;
});

tradamus.factory('Annotation',function(){
  var annotation = {
    startOffset:-1,
    endOffset:-1,
    type:"",
    purpose:"",
    content:"",
    motivation:"",
    canvas:-1,
    bounds: {},
    startPage:-1,
    endPage:-1,
    id: -1
  };
  return annotation;
});

tradamus.factory('Selection', function ($rootScope) {
    var s = {
    // mote, tag, witness, etc.
    };
    $rootScope.$on('logout', function () {
        s = {};
    });
    return s;
});