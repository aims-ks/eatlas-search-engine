<c:forEach items="${messages}" var="message">
    <div class="${message.level.cssClass}">

        <div class="date">
            <c:if test="${not empty message.date}">
                <fmt:formatDate value="${message.date}" pattern="yyyy-MM-dd HH:mm" />
            </c:if>
        </div>

        <div class="message-wrapper">
            <!-- If no details, just show the message -->
            <c:if test="${empty message.details}">
                <c:out value="${message.message}"/>
            </c:if>

            <!-- If details, show the message with collapsible details -->
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
                        <c:out value="${message.exception.className}"/>
                        <c:if test="${not empty message.exception.message}">
                            - <c:out value="${message.exception.message}"/>
                        </c:if>
                    </span>
                    <div class="stacktrace-box collapsible">
                        <ul class="stacktrace">
                            <c:forEach var="stacktraceElement" items="${message.exception.stackTrace}">
                                <li><c:out value="${stacktraceElement}"/></li>
                            </c:forEach>
                            <c:forEach var="cause" items="${message.causes}">
                                <li>
                                    Caused by: <c:out value="${cause.className}"/>
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
                </div>
            </c:if>
        </div>

    </div>
</c:forEach>
