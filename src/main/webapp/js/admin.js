
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
