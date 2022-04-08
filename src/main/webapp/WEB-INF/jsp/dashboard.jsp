<!DOCTYPE html>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>

<html lang="en">
<head>
    <title>eAtlas Search Engine - Dashboard</title>
    <link rel="stylesheet" href="${pageContext.servletContext.contextPath}/css/admin.css">
</head>

<body>
    <div class="header">
        <h1>eAtlas Search Engine - Dashboard</h1>
        <nav>
            <ul class="user-menu">
                <li><span>Admin</span></li>
                <li><a href="#">Logout</a></li>
            </ul>
        </nav>
    </div>

    <div class="main">
        <nav>
            <ul class="menu">
                <li><a href="#">Overview</a></li> <!-- List indexes in table, each row have: number of doc, edit button (goes to Manage), index button (goes to Index) -->
                <li><a href="#">Manage</a></li> <!-- Create, edit, delete index -->
                <li><a href="#">Reindex</a></li> <!-- Re-index an index or all indexes, with progress bar -->
                <li><a href="#">Search</a></li> <!-- Test the search. Checkbox to choose which index to search from. -->
                <li><a href="#">Log-out</a></li>
            </ul>
        </nav>

        <div class="content">
            <div class="message box">
                <div class="error">Error message</div>
                <div class="info">Info message</div>
                <div class="warning">Warning message</div>
            </div>

            <div class="box">
                <h2>Indexes</h2>

                <table>
                    <tr class="table-header">
                        <th>Index</th>
                        <th>Class</th>
                        <th>Documents</th>
                        <th>Actions</th>
                    </tr>
                    <tr class="odd">
                        <td>eatlas_article</td>
                        <td>DrupalNodeIndexer</td>
                        <td>11</td>
                        <td>[E] [I] [X]</td>
                    </tr>
                    <tr class="even">
                        <td>eatlas_extlink</td>
                        <td>ExternalLinkIndexer</td>
                        <td>6</td>
                        <td>[E] [I] [X]</td>
                    </tr>
                    <tr class="odd">
                        <td>eatlas_metadata</td>
                        <td>GeoNetworkIndexer</td>
                        <td>383</td>
                        <td>[E] [I] [X]</td>
                    </tr>
                    <tr class="even">
                        <td>eatlas_layer</td>
                        <td>AtlasMapperIndexer</td>
                        <td>5000</td>
                        <td>[E] [I] [X]</td>
                    </tr>
                </table>

                <button>Add an index</button>
                <p>Content of the page</p>
            </div>

        </div>

    </div>

    <div class="footer">
        FOOTER
    </div>
</body>

</html>
