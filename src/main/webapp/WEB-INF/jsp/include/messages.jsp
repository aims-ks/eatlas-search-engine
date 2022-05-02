<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<c:if test="${not empty requestScope.messages.messages}">
    <div class="message box">
        <c:set var="messages" value="${requestScope.messages.consume()}"/>
        <c:forEach items="${messages}" var="message">
            <div class="${message.level.cssClass}">
                <c:out value="${message.message}"/>

                <c:if test="${not empty message.exception}">
                    <div class="exception hover">
                        <span class="clickable">
                            <%-- Display the exception class name and message (not all exception have a "message") --%>
                            <c:out value="${message.exception.getClass().name}"/>
                            <c:if test="${not empty message.exception.message}">
                                - <c:out value="${message.exception.message}"/>
                            </c:if>
                        </span>
                        <ul class="stacktrace collapsible">
                            <c:forEach var="stacktraceElement" items="${message.exception.stackTrace}">
                                <li>
                                    <c:out value="${stacktraceElement}"/>
                                </li>
                            </c:forEach>
                        </ul>
                    </div>
                </c:if>

            </div>
        </c:forEach>
    </div>
</c:if>
