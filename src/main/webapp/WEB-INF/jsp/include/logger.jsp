<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<c:if test="${not empty it.logger.messages}">
    <div class="message box">
        <c:set var="messages" value="${it.logger.consume()}" />
        <%@ include file="messages.jsp" %>
    </div>
</c:if>
