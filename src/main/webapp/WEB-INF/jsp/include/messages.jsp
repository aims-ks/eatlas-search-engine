<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<c:if test="${not empty requestScope.messages.messages}">
    <div class="message box">
        <c:set var="messages" value="${requestScope.messages.consume()}"/>
        <c:forEach items="${messages}" var="message">
            <div class="${message.level.cssClass}">

                <c:if test="${empty message.details}">
                    <c:out value="${message.message}"/>
                </c:if>

                <c:if test="${not empty message.details}">
                    <div class="details hover">
                        <span class="clickable"><c:out value="${message.message}"/></span>
                        <ul class="detailList collapsible">
                            <c:if test="${not empty message.details}">
                                <c:forEach var="detail" items="${message.details}">
                                    <li><c:out value="${detail}"/></li>
                                </c:forEach>
                            </c:if>
                        </ul>
                    </div>
                </c:if>

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
                                <li><c:out value="${stacktraceElement}"/></li>
                            </c:forEach>
                            <c:forEach var="cause" items="${message.causes}">
                                <li>
                                    Caused by: <c:out value="${cause.getClass().name}"/>
                                    <c:if test="${not empty cause.message}">
                                        - <c:out value="${cause.message}"/>
                                    </c:if>
                                    <ul class="stacktrace">
                                        <c:forEach var="causeStacktraceElement" items="${cause.stackTrace}">
                                            <li>
                                                <c:out value="${causeStacktraceElement}"/>
                                            </li>
                                        </c:forEach>
                                    </ul>
                                </li>
                            </c:forEach>
                        </ul>
                    </div>
                </c:if>

            </div>
        </c:forEach>
    </div>
</c:if>
