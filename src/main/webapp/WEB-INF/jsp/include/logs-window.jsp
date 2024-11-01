<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<div class="modal-window">
    <div class="logs-header">
        <h2>Last indexation logs for: <i>${it.index}</i></h2>
        <button class="close-window" name="close-window-button" title="close">Close</button>
    </div>

    <div class=logs>
        <c:if test="${not empty it.fileLogger.messages}">
            <div class="message box">
                <c:set var="messages" value="${it.fileLogger.messages}" />
                <%@ include file="messages.jsp" %>
            </div>
        </c:if>
    </div>
</div>
