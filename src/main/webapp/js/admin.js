
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

  // Progress bars
  const progressBarEls = document.getElementsByClassName("progressbar");
  for (let i=0; i<progressBarEls.length; i++) {
    refreshProgressBar(progressBarEls[i]);
  }
});

function refreshProgressBar(progressBarEl) {
  const Http = new XMLHttpRequest();
  const apiUrl = progressBarEl.getAttribute("data-progress-url");

  // 1. Request indexation progress for that index

  Http.open("GET", apiUrl);
  Http.send();

  Http.onreadystatechange = function(progressBarEl) {
    return function(e) {
      if (this.readyState == 4 && this.status == 200) {

        // 2. Set progress bar with progress info

        let progress = parseFloat(Http.responseText);
        setProgressBar(progressBarEl, progress);

        // 3. If progress is less than 100%, call this function again in 1 second.

        if (progress < 1) {
          window.setTimeout(function() {
            refreshProgressBar(progressBarEl);
          }, 1000);
        }
      }
    };
  }(progressBarEl);
}

function setProgressBar(progressBarEl, progress) {
  const percent = Math.floor(progress * 100);
  if (progress >= 1) {
    // Set the progress bar background to be all green. Works with every browser
    progressBarEl.style.background = "#00FF00";
  } else {
    // Set the progress bar background to use 2 colours. Only works with modern browser.
    // Old browsers will see a white bar that turns green when it reaches 100%
    progressBarEl.style.background = "linear-gradient(90deg, #00FF00 " + percent + "%, #FFFFFF " + percent + "%)";
  }
  progressBarEl.innerHTML = percent + "%";
}

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
