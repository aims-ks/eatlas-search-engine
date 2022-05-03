<!DOCTYPE html>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<c:set var="messages" value="${it.messages}" scope="request"/>

<html lang="en">
<head>
    <title>eAtlas Search Engine - Login</title>
    <link rel="icon" href="<c:url value="/img/favicon.svg" />" type="image/svg+xml">
    <link rel="stylesheet" href="<c:url value="/css/admin.css" />">
    <link rel="stylesheet" href="<c:url value="/css/login.css" />">
</head>

<body class="login-page">
    <div class="content">
        <div class="box">
            <h2>eAtlas Search Engine</h2>
        </div>

        <c:import url="include/messages.jsp"/>

        <form method="post">
            <div class="box">
                <h2>Login</h2>

                <div class="field">
                    <label for="username">
                        <span class="label required">Username</span>
                        <input type="text"
                            name="username"
                            id="username"
                            required="required" />
                    </label>
                </div>

                <div class="field">
                    <label for="password">
                        <span class="label required">Password</span>
                        <input type="password"
                            name="password"
                            id="password"
                            required="required" />
                    </label>
                </div>

                <div class="submit">
                    <button class="login" name="login-button" value="login" title="Login">Login</button>
                </div>

            </div>
        </form>
    </div>
</body>

</html>
