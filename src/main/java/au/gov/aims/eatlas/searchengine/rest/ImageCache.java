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

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.ImageResizer;
import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.logger.Level;
import au.gov.aims.eatlas.searchengine.logger.SessionLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.entity.ContentType;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Class used to cache preview images, thumbnails, etc.
 * Input: Image URL and a search index (string representing the subfolder where the image will be saved on disk).
 * Process: Download the image, saved it on the server.
 * Output: File of the cache image, or null if the image could not be downloaded.
 */
@Path("/img/v1")
public class ImageCache {
    private static final Logger LOGGER = LogManager.getLogger(ImageCache.class.getName());
    private static final float BASE_LAYER_ALPHA = 0.4f;
    private static final String DEFAULT_IMAGE_CACHE_DIR = "/tmp/eatlas-search-engine-cache";
    private static File imageCacheDir = null;

    // /img/v1/{index}/{filename}
    // Example:
    //     /img/v1/eatlas_article/coral.png
    @GET
    @Path("{index}/{filename}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCachedImage(
            @Context HttpServletRequest httpRequest,
            @PathParam("index") String index,
            @PathParam("filename") String filename,
            @QueryParam("crop") String crop // Optional query parameter. Example: 300x200
    ) {
        HttpSession session = httpRequest.getSession(true);
        AbstractLogger logger = SessionLogger.getInstance(session);

        File cachedFile = getCachedFile(index, filename, logger);
        if (cachedFile == null) {
            logger.addMessage(Level.WARNING, String.format("Cached image not found: %s/%s", index, filename));
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!cachedFile.canRead()) {
            logger.addMessage(Level.WARNING, String.format("Cached image not found: %s", cachedFile.toString()));
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        try {
            byte[] responseBytes = null;

            if (crop != null) {
                String[] dimensions = crop.split("x");
                if (dimensions.length == 2) {
                    int width = -1;
                    int height = -1;
                    try {
                        width = Integer.parseInt(dimensions[0]);
                        height = Integer.parseInt(dimensions[1]);
                    } catch (NumberFormatException e) {
                        logger.addMessage(Level.WARNING, String.format("Invalid crop dimensions: %s", crop));
                        return Response.status(Response.Status.BAD_REQUEST).build();
                    }

                    if (width <= 0 || width > 500 || height <= 0 || height > 500) {
                        logger.addMessage(Level.WARNING, String.format("Invalid crop dimensions: %s", crop));
                        return Response.status(Response.Status.BAD_REQUEST).build();
                    }

                    // Resize the image and get its content
                    BufferedImage originalImage = ImageIO.read(cachedFile);
                    BufferedImage resizedImage = ImageResizer.resizeCropImage(originalImage, width, height);
                    responseBytes = ImageResizer.getImageData(resizedImage);
                }
            }

            // Get the original image content
            if (responseBytes == null) {
                responseBytes = FileUtils.readFileToByteArray(cachedFile);
            }

            // Return the JSON array with an OK status.
            ContentType contentType = HttpClient.getContentType(filename);
            return Response.ok(responseBytes, contentType.toString()).build();
        } catch(Exception ex) {
            logger.addMessage(Level.ERROR, String.format("Server error: %s", ex.getMessage()), ex);
            return Response.serverError().entity(String.format("Server error: %s", ex.getMessage())).build();
        }
    }

    public static File getCachedFile(String index, String filename, AbstractLogger logger) {
        File cacheDir = getCacheDirectory(index, logger);
        if (cacheDir == null) {
            return null;
        }

        return new File(cacheDir, filename);
    }

    public static File getCacheDirectory(String index, AbstractLogger logger) {
        if (index == null || index.isEmpty()) {
            return null;
        }
        String safeIndex = safeString(index);

        if (imageCacheDir == null) {
            SearchEngineConfig config = SearchEngineConfig.getInstance();
            if (config != null) {
                String imageCacheDirStr = config.getImageCacheDirectory();
                if (imageCacheDirStr != null) {
                    imageCacheDir = new File(imageCacheDirStr);
                }
            }
            if (imageCacheDir == null) {
                logger.addMessage(Level.WARNING, String.format("Missing configuration property \"imageCacheDirectory\". Using default image cache directory: %s",
                        DEFAULT_IMAGE_CACHE_DIR));
                imageCacheDir = new File(DEFAULT_IMAGE_CACHE_DIR);
            }
        }
        File indexCacheDir = new File(imageCacheDir, safeIndex);
        indexCacheDir.mkdirs();
        if (!indexCacheDir.isDirectory()) {
            logger.addMessage(Level.ERROR, String.format("The image cache directory %s doesn't exist and can not be created.", indexCacheDir));
            return null;
        }
        if (!indexCacheDir.canRead()) {
            logger.addMessage(Level.ERROR, String.format("The image cache directory %s exists but is not readable.", indexCacheDir));
            return null;
        }
        return indexCacheDir;
    }

    public static File cache(HttpClient httpClient, URL imageUrl, String index, String filenamePrefix, AbstractLogger logger) throws IOException, InterruptedException {
        return cache(httpClient, imageUrl, null, index, filenamePrefix, logger);
    }

    public static File cache(HttpClient httpClient, URL imageUrl, Integer timeout, String index, String filenamePrefix, AbstractLogger logger) throws IOException, InterruptedException {
        if (imageUrl == null || index == null) {
            return null;
        }

        File cacheDir = getCacheDirectory(index, logger);
        if (cacheDir == null) {
            return null;
        }
        if (!cacheDir.canWrite()) {
            logger.addMessage(Level.ERROR, String.format("The image cache directory %s exists but is not writable.", cacheDir.toString()));
            return null;
        }

        String urlStr = imageUrl.toString();
        HttpClient.Response imageResponse = httpClient.getRequest(urlStr, timeout, logger);
        if (imageResponse == null) {
            return null;
        }
        int statusCode = imageResponse.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            logger.addMessage(Level.WARNING, String.format("Invalid image URL: %s status code: %d", urlStr, statusCode));
            return null;
        }

        String extension = imageResponse.getFileExtension();
        if (extension == null) {
            extension = "bin";
        }
        File cacheFile = getUniqueFile(cacheDir, imageUrl, filenamePrefix, extension);

        byte[] imageBytes = imageResponse.bodyAsBytes();
        if (imageBytes == null || imageBytes.length <= 0) {
            return null;
        }

        // Cache image to disk
        LOGGER.debug(String.format("Caching preview image %s to %s", urlStr, cacheFile));
        FileUtils.writeByteArrayToFile(cacheFile, imageBytes);

        return cacheFile;
    }

    public static File cacheLayer(HttpClient httpClient, URL baseLayerImageUrl, URL layerImageUrl, String index, String filenamePrefix, AbstractLogger logger) throws IOException, InterruptedException {
        return cacheLayer(httpClient, baseLayerImageUrl, layerImageUrl, null, index, filenamePrefix, logger);
    }

    public static File cacheLayer(HttpClient httpClient, URL baseLayerImageUrl, URL layerImageUrl, Integer timeout, String index, String filenamePrefix, AbstractLogger logger) throws IOException, InterruptedException {
        if (layerImageUrl == null || index == null) {
            return null;
        }

        File cacheDir = getCacheDirectory(index, logger);
        if (cacheDir == null) {
            return null;
        }
        if (!cacheDir.canWrite()) {
            logger.addMessage(Level.ERROR, String.format("The image cache directory %s exists but is not writable.", cacheDir.toString()));
            return null;
        }

        // Get layer image (using JSoup to benefit from the retry feature)
        String layerUrlStr = layerImageUrl.toString();
        HttpClient.Response layerImageResponse = httpClient.getRequest(layerUrlStr, timeout, logger);
        if (layerImageResponse == null) {
            return null;
        }
        int layerStatusCode = layerImageResponse.statusCode();
        if (layerStatusCode < 200 || layerStatusCode >= 300) {
            logger.addMessage(Level.WARNING, String.format("Invalid layer URL: %s status code: %d", layerUrlStr, layerStatusCode));
            return null;
        }

        // Get base layer image (using JSoup to benefit from the retry feature)
        BufferedImage baseLayerImage = null;
        if (baseLayerImageUrl != null) {
            String baseLayerUrlStr = baseLayerImageUrl.toString();
            HttpClient.Response baseLayerImageResponse = httpClient.getRequest(baseLayerUrlStr, timeout, logger);
            if (baseLayerImageResponse == null) {
                return null;
            }

            int baseLayerStatusCode = baseLayerImageResponse.statusCode();
            if (baseLayerStatusCode < 200 || baseLayerStatusCode >= 300) {
                logger.addMessage(Level.WARNING, String.format("Invalid base layer URL: %s status code: %d", baseLayerUrlStr, baseLayerStatusCode));
                return null;
            }

            baseLayerImage = createImageFromResponse(baseLayerImageResponse);
        }

        BufferedImage layerImage = createImageFromResponse(layerImageResponse);

        BufferedImage combined = layerImage;
        if (baseLayerImage != null) {
            combined = new BufferedImage(baseLayerImage.getWidth(), baseLayerImage.getHeight(), BufferedImage.TYPE_INT_RGB);

            Graphics2D graphics = (Graphics2D)combined.getGraphics();
            // Fill the image with white paint
            graphics.setColor(new Color(255, 255, 255));
            graphics.fillRect(0, 0, combined.getWidth(), combined.getHeight());

            // Draw the background image (50% opacity)
            Composite defaultComposite = graphics.getComposite();
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, BASE_LAYER_ALPHA));
            graphics.drawImage(baseLayerImage, 0, 0, null);
            graphics.setComposite(defaultComposite);

            // Draw the layer on top
            graphics.drawImage(layerImage, 0, 0, null);
        }

        // Saved image to disk
        String extension = "jpg";
        File cacheFile = getUniqueFile(cacheDir, layerImageUrl, filenamePrefix, extension);

        LOGGER.debug(String.format("Caching layer preview image %s to %s", layerImageUrl, cacheFile));
        ImageIO.write(combined, "jpg", cacheFile);

        return cacheFile;
    }

    private static BufferedImage createImageFromResponse(HttpClient.Response response) throws IOException {
        try (InputStream inputStream = response.bodyStream()) {
            return ImageIO.read(inputStream);
        }
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
        return urlPath == null ? null : urlPath.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
