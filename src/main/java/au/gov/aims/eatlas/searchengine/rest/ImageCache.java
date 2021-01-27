/*
 *  Copyright (C) 2021 Australian Institute of Marine Science
 *
 *  Contact: Gael Lafond <g.lafond@aims.gov.au>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package au.gov.aims.eatlas.searchengine.rest;

import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.entity.ContentType;
import org.apache.log4j.Logger;
import org.jsoup.Connection;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Class used to cache preview images, thumbnails, etc.
 * Input: Image URL and a search index (string representing the subfolder where the image will be saved on disk).
 * Process: Download the image, saved in on the server.
 * Output: File of the cache image, or null if the image could not be downloaded.
 */
@Path("/img/v1")
public class ImageCache {
    private static final Logger LOGGER = Logger.getLogger(ImageCache.class.getName());
    private static File imageCacheDir = null;

    // /img/v1/{index}/{filename}
    // Example:
    //     /img/v1/eatlas_article/coral.png
    @GET
    @Path("{index}/{filename}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCachedImage(
            @Context HttpServletRequest servletRequest,
            @PathParam("index") String index,
            @PathParam("filename") String filename
    ) {
        String safeIndex = safeString(index);
        File cacheDir = getCacheDirectory(safeIndex);
        if (cacheDir == null) {
            return null;
        }

        File cachedFile = new File(cacheDir, filename);
        if (!cachedFile.canRead()) {
            LOGGER.warn(String.format("Cached image not found: %s", cachedFile.toString()));
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ContentType contentType = EntityUtils.getContentType(filename);

        try {
            byte[] responseBytes = FileUtils.readFileToByteArray(cachedFile);

            // Disable cache DURING DEVELOPMENT!
            CacheControl noCache = new CacheControl();
            noCache.setNoCache(true);

            // Return the JSON array with a OK status.
            return Response.ok(responseBytes, contentType.toString()).cacheControl(noCache).build();
        } catch(Exception ex) {
            LOGGER.error(String.format("Server error: %s", ex.getMessage()), ex);
            return Response.serverError().entity(String.format("Server error: %s", ex.getMessage())).build();
        }
    }

    protected static File getCacheDirectory(String index) {
        if (index == null || index.isEmpty()) {
            return null;
        }
        if (imageCacheDir == null) {
            // TODO: Get / save image cache path in config!
            imageCacheDir = new File("/home/glafond/Desktop/TMP_INPUT/imageCache/");
        }
        File indexCacheDir = new File(imageCacheDir, index);
        indexCacheDir.mkdirs();
        if (!indexCacheDir.isDirectory()) {
            LOGGER.error(String.format("The image cache directory %s doesn't exist and can not be created.", indexCacheDir));
            return null;
        }
        if (!indexCacheDir.canRead()) {
            LOGGER.error(String.format("The image cache directory %s exists but is not readable.", indexCacheDir));
            return null;
        }
        return indexCacheDir;
    }

    public static File cache(URL imageUrl, String index, String filenamePrefix) throws IOException, InterruptedException {
        if (imageUrl == null || index == null) {
            return null;
        }

        String urlStr = imageUrl.toString();
        Connection.Response imageResponse = EntityUtils.jsoupExecuteWithRetry(urlStr);
        if (imageResponse == null) {
            return null;
        }
        int statusCode = imageResponse.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            LOGGER.warn(String.format("Invalid image URL: %s status code: %d", urlStr, statusCode));
            return null;
        }

        String safeIndex = safeString(index);

        File cacheDir = getCacheDirectory(safeIndex);
        if (cacheDir == null) {
            return null;
        }
        if (!cacheDir.canWrite()) {
            LOGGER.error(String.format("The image cache directory %s exists but is not writable.", cacheDir.toString()));
            return null;
        }

        String contentTypeStr = imageResponse.contentType();
        if (contentTypeStr == null || contentTypeStr.isEmpty()) {
            LOGGER.warn(String.format("Image content type is null: %s", urlStr));
        }
        String extension = getFileExtension(contentTypeStr);
        File cacheFile = getUniqueFile(cacheDir, imageUrl, filenamePrefix, extension);

        byte[] imageBytes = imageResponse.bodyAsBytes();
        if (imageBytes == null || imageBytes.length <= 0) {
            return null;
        }

        // Cache image to disk
        FileUtils.writeByteArrayToFile(cacheFile, imageBytes);

        return cacheFile;

        /*
        // TODO Get base URL from config!
        String searchEngineBaseUrl = "http://localhost:8080/eatlas-search-engine";

        String cachedImageUrlStr = String.format("%s/public/img/v1/%s/%s", searchEngineBaseUrl, safeIndex, cacheFile.getName());
System.out.println(cachedImageUrlStr);
        return cachedImageUrlStr;
        */
    }

    private static String getFileExtension(String contentTypeStr) throws IOException {
        ContentType contentType = null;
        if (contentTypeStr != null && !contentTypeStr.isEmpty()) {
            try {
                contentType = ContentType.parse(contentTypeStr);
            } catch (Exception ex) {
                LOGGER.warn(String.format("Unsupported image content type: %s", contentTypeStr), ex);
            }
        }
        return EntityUtils.getExtension(contentType, "bin");
    }

    private static File getUniqueFile(File cacheDir, URL imageUrl, String filenamePrefix, String extension) throws IOException {
        String filename = filenamePrefix;

        // Extract filename from URL
        if (filename == null || filename.isEmpty()) {
            filename = FilenameUtils.getBaseName(imageUrl.getPath());
        }

        // Fallback
        if (filename == null || filename.isEmpty()) {
            filename = "unnamed";
        }

        // NOTE: File.createTempFile can be used to create unique file anywhere.
        //     The file is created in "/tmp/" if the 3rd argument is not supplied.
        //     In this case, the file created is actually not temporary.
        return File.createTempFile(safeString(filename) + "_", "." + extension, cacheDir);
    }

    public static String safeString(String urlPath) {
        return urlPath.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
