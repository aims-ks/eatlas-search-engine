
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
  // Find forms with attribute "data-progress-url"
  const formEls = document.getElementsByTagName("form");
  for (let i=0; i<formEls.length; i++) {
    const dataProgressUrl = formEls[i].getAttribute("data-progress-url");
    if (dataProgressUrl) {
      refreshProgressBars(dataProgressUrl);
    }
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

function refreshProgressBars(dataProgressUrl, lastRunningCount = 0) {
  const httpRequest = new XMLHttpRequest();

  // 1. Request indexation progress.

  httpRequest.open("GET", dataProgressUrl);

  httpRequest.onreadystatechange = function(httpRequest, dataProgressUrl) {
    return function(e) {
      if (this.readyState === XMLHttpRequest.DONE && this.status === 200) {

        // The response will be empty if the page reloads before the request is done.
        let runningCount = 0;
        if (httpRequest.responseText) {
          let jsonResponse = JSON.parse(httpRequest.responseText);
          if (jsonResponse !== null) {

            // 2. Set progress bars with progress info.

            runningCount = jsonResponse.runningCount;
            let indexes = jsonResponse.indexes;
            if (indexes) {
              for (const index in indexes) {
                const progressBarEl = document.getElementById("progress_" + index);
                if (progressBarEl) {
                  const jsonProgress = indexes[index];

                  const running = jsonProgress.running;
                  const progress = jsonProgress.progress;
                  setProgressBar(progressBarEl, progress, running);
                }
              }
            }
          }
        }

        // 3. Call this function again in 1 second.

        // If it was indexing, and it finished the last one,
        // reload the page to refresh the stats and display messages.
        if (lastRunningCount > 0 && runningCount === 0) {
          // Reload the page without re-submitting the form.
          window.location.href = window.location.href;
        } else {
          // NOTE: Check every seconds even when no indexation is running,
          //     in case someone else starts an indexation.
          window.setTimeout(function(dataProgressUrl, lastRunningCount) {
            return function() {
              refreshProgressBars(dataProgressUrl, lastRunningCount);
            }
          }(dataProgressUrl, runningCount), 1000);
        }
      }
    }
  }(httpRequest, dataProgressUrl);

  httpRequest.send();
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
