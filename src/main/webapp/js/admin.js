
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

    // If the form contains required fields which are not filled, do not collapse the edit form,
    // and do not make it collapsable.
    // This will allow the browser to position the window to the first required field when a submit button is pressed,
    // if the user forgot to fill one.
    if (!editFormRowEl.classList.contains("invalid")) {
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
  }

  // Progress bars
  const progressBarEls = document.getElementsByClassName("index-progress");
  for (let i=0; i<progressBarEls.length; i++) {
    refreshProgressBar(progressBarEls[i]);
  }

  // Change password - Validate password and repeat password
  const passwordEl = document.getElementById("password");
  const repasswordEl = document.getElementById("repassword");
  if (passwordEl !== null) {
    passwordEl.addEventListener("change", confirmPassword);
  }
  if (repasswordEl !== null) {
    repasswordEl.addEventListener("keyup", confirmPassword);
  }
});

function confirmPassword() {
  const passwordEl = document.getElementById("password");
  const repasswordEl = document.getElementById("repassword");

  // When setCustomValidity is set to a none-empty value,
  // the value is displayed on the form and the form does not submit.
  if (passwordEl.value !== repasswordEl.value) {
    repasswordEl.setCustomValidity("Passwords do not match.");
  } else {
    repasswordEl.setCustomValidity("");
  }
}

function refreshProgressBar(progressBarEl) {
  const Http = new XMLHttpRequest();
  const apiUrl = progressBarEl.getAttribute("data-progress-url");

  // 1. Request indexation progress for that index.

  Http.open("GET", apiUrl);
  Http.send();

  Http.onreadystatechange = function(progressBarEl) {
    return function(e) {
      if (this.readyState == 4 && this.status == 200) {
        let jsonResponse = JSON.parse(Http.responseText);
        if (jsonResponse !== null) {

          // 2. Set progress bar with progress info.

          let index = jsonResponse.index;
          let running = jsonResponse.running;
          let progress = jsonResponse.progress;
          setProgressBar(progressBarEl, progress, running);


          // 3. Call this function again in 1 second.
          // NOTE: Check every seconds even when it is not running,
          //     in case someone else starts the indexation.

          window.setTimeout(function() {
            refreshProgressBar(progressBarEl);
          }, 1000);
        }
      }
    };
  }(progressBarEl);
}

function setProgressBar(progressBarEl, progress, running) {
  if (progress === null || progress === undefined) {
    progressBarEl.removeAttribute('value');
    progressBarEl.title = "Processing...";
    progressBarEl.innerHTML = "Processing...";
  } else {
    const percent = Math.floor(progress * 100);
    progressBarEl.value = percent;
    progressBarEl.title = percent + "%";
    progressBarEl.innerHTML = percent + "%";
  }

  if (running) {
    if (progressBarEl.classList.contains("disabled")) {
      progressBarEl.classList.remove("disabled");
    }
  } else {
    if (!progressBarEl.classList.contains("disabled")) {
      progressBarEl.classList.add("disabled");
    }
  }
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

function validateNotEmpty(elementId) {
  const element = document.getElementById(elementId);

  // When setCustomValidity is set to a none-empty value,
  // the value is displayed on the form and the form does not submit.
  if (element != null && (element.value === null || element.value === "")) {
    element.setCustomValidity("You must enter a value.");
  } else {
    element.setCustomValidity("");
  }
}
