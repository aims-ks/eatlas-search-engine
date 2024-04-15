/*
 *  Copyright (C) 2020 Australian Institute of Marine Science
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
package au.gov.aims.eatlas.searchengine.entity;

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.Consts;
import org.apache.http.entity.ContentType;
import org.apache.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;

public class EntityUtils {
    private static final Logger LOGGER = Logger.getLogger(EntityUtils.class.getName());

    private static final int JSOUP_RETRY = 2;//5;
    // NOTE: The delay is incremental: 5, 10, 20, 40, 80...
    private static final int JSOUP_RETRY_INITIAL_DELAY = 5; // In seconds

    public static String harvestGetURL(String url, Messages messages) throws IOException, InterruptedException {
        LOGGER.debug(String.format("Harvesting GET URL body: %s", url));

        // Get a HTTP document.
        // NOTE: Body in this case is the body of the response.
        //     It's the entire HTML document, not just the content
        //     of the HTML body element.
        return jsoupExecuteWithRetry(url, messages)
                .body();
    }

    public static String harvestPostURL(String url, Map<String, String> dataMap, Messages messages) throws IOException, InterruptedException {
        LOGGER.debug(String.format("Harvesting POST URL body: %s%n%s", url, mapToString(dataMap)));

        return jsoupExecuteWithRetry(EntityUtils.getJsoupConnection(url, messages)
                    .data(dataMap)
                    .method(Connection.Method.POST), url)
                .body();
    }

    public static String harvestURLText(String url, Messages messages) throws IOException, InterruptedException {
        LOGGER.debug(String.format("Harvesting URL text: %s", url));

        Connection.Response response = jsoupExecuteWithRetry(url, messages);

        String contentTypeStr = response.contentType();
        if (contentTypeStr == null) {
            return response.body();
        }

        try {
            ContentType contentType = ContentType.parse(contentTypeStr);
            String extension = getExtension(contentType);

            if ("html".equals(extension)) {
                return EntityUtils.extractHTMLTextContent(response.body());
            }
            if ("pdf".equals(extension)) {
                return EntityUtils.extractPDFTextContent(response.bodyAsBytes());
            }
            messages.addMessage(Messages.Level.WARNING, String.format("Unsupported mime type: %s", contentType.getMimeType()));

        } catch (Exception ex) {
            messages.addMessage(Messages.Level.WARNING, String.format("Unsupported content type: %s", contentTypeStr), ex);
        }

        return response.body();
    }

    public static Connection.Response jsoupExecuteWithRetry(String url, Messages messages) throws IOException, InterruptedException {
        return jsoupExecuteWithRetry(EntityUtils.getJsoupConnection(url, messages), url);
    }

    private static Connection.Response jsoupExecuteWithRetry(Connection jsoupConnection, String url) throws IOException, InterruptedException {
        IOException lastException = null;
        int delay = JSOUP_RETRY_INITIAL_DELAY;

        for (int i=0; i<JSOUP_RETRY; i++) {
            try {
                return jsoupConnection.execute();
            } catch (IOException ex) {
                // The following IOException (and maybe more) may occur when the computer lose network connection:
                //     SocketTimeoutException, ConnectException, UnknownHostException
                LOGGER.debug(String.format("Connection timed out while requesting URL: %s%nWait for %d seconds before re-trying [%d/%d].",
                        url, delay, i+1, JSOUP_RETRY));
                lastException = ex;
                Thread.sleep(delay * 1000L);
                delay *= 2;
            }
        }
        LOGGER.debug(String.format("Connection timed out %d times while requesting URL: %s", JSOUP_RETRY, url));

        throw lastException;
    }

    private static Connection getJsoupConnection(String url, Messages messages) {
        // JSoup takes care of following redirections.
        //     IOUtils.toString(URL, Charset) does not.
        //
        // timeout(120000)
        //     Socket timeout, in ms. Set to 120,000 (2 minutes).
        //     Some services (i.e. Legacy GeoServer) take almost 2 minutes to respond.
        //     Default: 30000 (30 seconds)
        // ignoreHttpErrors(true)
        //     To make it more robust
        // ignoreContentType(true)
        //     JSoup is quite picky with content types (aka mimetype).
        //     It only allows text/*, application/xml, or application/*+xml
        //     Some websites could be setup with wrong content type.
        //     We use "ignoreContentType" to workaround this issue.
        // maxBodySize(0)
        //     Default to 2MB. 0 = Infinite.
        //     AtlasMapper layer list for the eAtlas is larger than 2MB.
        // sslSocketFactory(...)
        //     To deal with dodgy SSL certificates.
        return Jsoup
                .connect(url)
                .timeout(120000)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .maxBodySize(0)
                .sslSocketFactory(EntityUtils.socketFactory(messages));
    }

    private static SSLSocketFactory socketFactory(Messages messages) {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
        }};

        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception ex) {
            messages.addMessage(Messages.Level.ERROR, "Failed to create a SSL socket factory.", ex);
            return null;
        }
    }

    public static String extractHTMLTextContent(String htmlDocumentStr) {
        // JSoup is a HTML parsing library made to work with real life web documents.
        //     The parser handle broken HTML document the same way a browser would.
        Document document = Jsoup.parse(htmlDocumentStr);

        // Load the text content of the HTML body element.
        // NOTE: JSoup take care of converting HTML entities into UTF-8 characters.
        return document.body().text();
    }

    public static String extractPDFTextContent(byte[] documentBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(documentBytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    public static String mapToString(Map<?, ?> map) {
        if (map == null) {
            return "NULL";
        }
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("{%n"));
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            String keyStr = key == null ? "NULL" : String.format("\"%s\"", key.toString());

            Object value = entry.getValue();
            String valueStr = value == null ? "NULL" : String.format("\"%s\"", value.toString());

            sb.append(String.format("    %s: %s%n", keyStr, valueStr));
        }
        sb.append("}");

        return sb.toString();
    }

    public static String getExtension(ContentType contentType) {
        return getExtension(contentType, null);
    }
    public static String getExtension(ContentType contentType, String defaultExtension) {
        if (contentType == null) {
            return defaultExtension;
        }
        String mimeType = contentType.getMimeType();
        if (mimeType == null || mimeType.isEmpty()) {
            return defaultExtension;
        }

        // HTML
        if (
            ContentType.TEXT_HTML.getMimeType().equalsIgnoreCase(mimeType) ||
            ContentType.APPLICATION_XHTML_XML.getMimeType().equalsIgnoreCase(mimeType)
        ) {
            return "html";
        }

        // PDF
        if ("application/pdf".equalsIgnoreCase(mimeType)) {
            return "pdf";
        }

        // XML
        if (
            ContentType.TEXT_XML.getMimeType().equalsIgnoreCase(mimeType) ||
            ContentType.APPLICATION_XML.getMimeType().equalsIgnoreCase(mimeType) ||
            ContentType.APPLICATION_ATOM_XML.getMimeType().equalsIgnoreCase(mimeType) ||
            ContentType.APPLICATION_SOAP_XML.getMimeType().equalsIgnoreCase(mimeType)
        ) {
            return "xml";
        }

        // JSON
        if (ContentType.APPLICATION_JSON.getMimeType().equalsIgnoreCase(mimeType)) {
            return "json";
        }

        // Text
        if (ContentType.TEXT_PLAIN.getMimeType().equalsIgnoreCase(mimeType)) {
            return "txt";
        }

        // Images
        if (ContentType.IMAGE_BMP.getMimeType().equalsIgnoreCase(mimeType)) {
            return "bmp";
        }
        if (ContentType.IMAGE_GIF.getMimeType().equalsIgnoreCase(mimeType)) {
            return "gif";
        }
        if (ContentType.IMAGE_JPEG.getMimeType().equalsIgnoreCase(mimeType)) {
            return "jpg";
        }
        if (
            ContentType.IMAGE_PNG.getMimeType().equalsIgnoreCase(mimeType) ||
            "application/png".equalsIgnoreCase(mimeType)
        ) {
            return "png";
        }
        if (
            ContentType.IMAGE_SVG.getMimeType().equalsIgnoreCase(mimeType) ||
            ContentType.APPLICATION_SVG_XML.getMimeType().equalsIgnoreCase(mimeType)
        ) {
            return "svg";
        }
        if (ContentType.IMAGE_TIFF.getMimeType().equalsIgnoreCase(mimeType)) {
            return "tiff";
        }
        if (ContentType.IMAGE_WEBP.getMimeType().equalsIgnoreCase(mimeType)) {
            return "webp";
        }

        // Use default extension with:
        //     APPLICATION_FORM_URLENCODED,
        //     MULTIPART_FORM_DATA,
        //     APPLICATION_OCTET_STREAM
        return defaultExtension;
    }

    public static ContentType getContentType(String filename) {
        return getContentType(filename, ContentType.DEFAULT_BINARY);
    }
    public static ContentType getContentType(String filename, ContentType defaultContentType) {
        String extension = FilenameUtils.getExtension(filename);
        if (extension == null || extension.isEmpty()) {
            return defaultContentType;
        }

        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types
        switch (extension.toLowerCase()) {
            case "html": return ContentType.create("text/html", Consts.UTF_8);
            case "xml": return ContentType.create("text/xml", Consts.UTF_8);
            case "pdf": return ContentType.create("application/pdf");
            case "json": return ContentType.APPLICATION_JSON;
            case "txt": return ContentType.create("text/plain", Consts.UTF_8);

            // Images
            case "bmp": return ContentType.IMAGE_BMP;
            case "gif": return ContentType.IMAGE_GIF;
            case "jpg": return ContentType.IMAGE_JPEG;
            case "png": return ContentType.IMAGE_PNG;
            case "svg": return ContentType.IMAGE_SVG;
            case "tiff": return ContentType.IMAGE_TIFF;
            case "webp": return ContentType.IMAGE_WEBP;

            default: return defaultContentType;
        }
    }

}
