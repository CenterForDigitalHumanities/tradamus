var Range = {};

Range.getUserSelection = function() {
  var userSelection;
  if (window.getSelection) {
	userSelection = window.getSelection();
  }
  else if (document.selection) { // should come last; Opera!
    userSelection = document.selection.createRange();
  }
  console.log(userSelection);
  return userSelection;
};
Range.getSelectedText = function() {
  var selectedText = this.getUserSelection();
  if (selectedText.text)
	selectedText = selectedText.text;
  return selectedText;
};
Range.getTextAnnotation = function(r) {
  // Discover start element
  // Find Decision containing element
  // Discover end element
  // Find Decision containing element
  var textAnnotation = {
    startDecision: -1,
    startOffset: 0,
    endDecision: -1,
    endOffset: 0,
    content: ""
  }
  return textAnnotation;
}
Range.getLength = function(r) {
  
}