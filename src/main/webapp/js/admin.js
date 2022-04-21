
docReady(function() {
  const collapsibleEls = document.getElementsByClassName("collapsible");
  for (let i=0; i<collapsibleEls.length; i++) {
    let collapsibleEl = collapsibleEls[i];
    let parentEl = collapsibleEl.parentNode;

    collapsibleEl.style.display = "none";

    // Remove the "hover" CSS class, which provides a mouse hover behaviour when JS is disabled.
    parentEl.classList.remove("hover");

    parentEl.addEventListener("click", function(event) {
      const childCollapsibleEls = this.getElementsByClassName("collapsible");
      for (let i=0; i<childCollapsibleEls.length; i++) {
        let childCollapsibleEl = childCollapsibleEls[i];
        if (childCollapsibleEl.style.display === "none") {
          childCollapsibleEl.style.display = "block";
        } else {
          childCollapsibleEl.style.display = "none";
        }
      }
    });
  }

  // Toggle index edit forms
  const editFormButtonEls = document.getElementsByClassName("editFormButton");
  for (let i=0; i<editFormButtonEls.length; i++) {
    let editFormButtonEl = editFormButtonEls[i];

    let index = editFormButtonEl.parentNode.id;
    let editFormRowId = "formRow_" + index;

    let editFormRowEl = document.getElementById(editFormRowId);
    editFormRowEl.style.display = "none";

    editFormButtonEl.addEventListener("click", function(event) {
      let index = this.parentNode.id;
      let editFormRowId = "formRow_" + index;
      let editFormRowEl = document.getElementById(editFormRowId);

      if (editFormRowEl.style.display === "none") {
        editFormRowEl.style.display = "table-row";
      } else {
        editFormRowEl.style.display = "none";
      }
    });
  }

  // Delete index button
  const deleteButtonEls = document.getElementsByClassName("deleteButton");
  for (let i=0; i<deleteButtonEls.length; i++) {
    let deleteButtonEl = deleteButtonEls[i];

    deleteButtonEl.addEventListener("click", function(event) {
      let index = this.parentNode.id;
      let form = this.form;

      if (window.confirm("Are you sure you want to delete the index: " + index + "?")) {
        // Set index ID in hidden form field
        form.deleteIndex.value = index;

        // Submit the form
        form.submit();

        return true;
      }

      return false;
    });
  }

});

// Equivalent to JQuery.ready().
// Copied from:
//     https://stackoverflow.com/questions/9899372/pure-javascript-equivalent-of-jquerys-ready-how-to-call-a-function-when-t#9899701
function docReady(fn) {
    // see if DOM is already available
    if (document.readyState === "complete" || document.readyState === "interactive") {
        // call on next available tick
        setTimeout(fn, 1);
    } else {
        document.addEventListener("DOMContentLoaded", fn);
    }
}

function validateNotEmpty(elementId, humanReadableFieldName) {
  const element = document.getElementById(elementId);
  if (element == null) {
    alert("Validation failed. Element " + elementId + " does not exists.");
    return false;
  }

  if (!element.value) {
    alert("Validation failed. You must enter a value for: " + humanReadableFieldName);
    return false;
  }

  return true;
}
