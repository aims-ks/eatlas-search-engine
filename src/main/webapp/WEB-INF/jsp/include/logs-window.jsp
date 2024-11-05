<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<div class="modal-window">
    <div class="logs-header">
        <h2>Last indexation logs for: <i>${it.index}</i></h2>

        <div class=filters>
            <input type="hidden" name="view-log-button" value="${it.index}" />
            <input type="hidden" name="show-log-info" value="${it.showLogInfo}" />
            <input type="hidden" name="show-log-warning" value="${it.showLogWarning}" />
            <input type="hidden" name="show-log-error" value="${it.showLogError}" />
            <button class="show-log-info ${it.showLogInfo ? "show-logs" : "hide-logs"}" value="${it.showLogInfo ? "filter-off" : "filter-on"}" name="show-log-info-button" title="Show info logs">Info</button>
            <button class="show-log-warning ${it.showLogWarning ? "show-logs" : "hide-logs"}" value="${it.showLogWarning ? "filter-off" : "filter-on"}" name="show-log-warning-button" title="Show warning logs">Warning</button>
            <button class="show-log-error ${it.showLogError ? "show-logs" : "hide-logs"}" value="${it.showLogError ? "filter-off" : "filter-on"}" name="show-log-error-button" title="Show error logs">Error</button>
        </div>

        <button class="close-window" name="close-window-button" title="Close">Close</button>
    </div>

    <div class=logs>
        <div class="message box">
            <c:set var="messages" value="${it.logs}" />
            <%@ include file="messages.jsp" %>
        </div>
    </div>
</div>
