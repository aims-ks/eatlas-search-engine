<!DOCTYPE html>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>

<%-- Variables accessible in templates --%>
<c:set var="title" value="User" scope="request"/>
<c:set var="userActive" value="active" scope="request"/>
<c:set var="messages" value="${it.messages}" scope="request"/>

<html lang="en">
<head>
    <title>eAtlas Search Engine - ${title}</title>
    <link rel="icon" href="<c:url value="/img/favicon.svg" />" type="image/svg+xml">
    <script src="<c:url value="/js/admin.js" />"></script>
    <link rel="stylesheet" href="<c:url value="/css/admin.css" />">
</head>

<body>
    <c:import url="include/header.jsp"/>

    <form method="post">
        <div class="box">
            <h2>User page</h2>

            <div class="field">
                <label for="username">
                    <span class="label required">Username</span>
                    <input type="text"
                        id="username"
                        name="username"
                        data-lpignore="true"
                        required="required"
                        value="<c:out value="${it.config.user.username}" />" />
                </label>

                <div class="desc">Admin username.</div>
            </div>

            <div class="field">
                <label for="password">
                    <span class="label">New password</span>
                    <input type="password"
                        id="password"
                        name="password"
                        data-lpignore="true" />
                </label>
                <label for="repassword">
                    <span class="label">Repeat password</span>
                    <input type="password"
                        id="repassword"
                        name="repassword"
                        data-lpignore="true" />
                </label>

                <div class="desc">Set to change password.</div>
            </div>

            <%--
                Salt can only be changed by modifying the config file manually.
                The encrypted password will need to be changed at the same time,
                by removing it and setting a clear text "password".
            --%>

            <div class="field">
                <label for="first-name">
                    <span class="label">First name</span>
                    <input type="text"
                        id="first-name"
                        name="first-name"
                        data-lpignore="true"
                        value="<c:out value="${it.config.user.firstName}" />" />
                </label>

                <div class="desc">User's first name. Displayed in the admin interface.</div>
            </div>

            <div class="field">
                <label for="last-name">
                    <span class="label">Last name</span>
                    <input type="text"
                        id="last-name"
                        name="last-name"
                        data-lpignore="true"
                        value="<c:out value="${it.config.user.lastName}" />" />
                </label>

                <div class="desc">User's last name. Displayed in the admin interface.</div>
            </div>

            <div class="field">
                <label for="email">
                    <span class="label">Email</span>
                    <input type="text"
                        id="email"
                        name="email"
                        data-lpignore="true"
                        value="<c:out value="${it.config.user.email}" />" />
                </label>

                <div class="desc">User's email address. This attribute is currently unused.</div>
            </div>

            <div class="submit">
                <button class="save" name="save-button" value="save" title="save">Save</button>
            </div>

        </div>
    </form>

    <c:import url="include/footer.jsp"/>
</body>

</html>
