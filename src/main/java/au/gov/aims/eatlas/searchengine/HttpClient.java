package au.gov.aims.eatlas.searchengine;

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.Consts;
import org.apache.http.entity.ContentType;
import org.apache.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Locale;

public class HttpClient {
    private static final Logger LOGGER = Logger.getLogger(HttpClient.class.getName());

    private static final int JSOUP_RETRY = 2;//5;
    // NOTE: The delay is incremental: 5, 10, 20, 40, 80...
    private static final int JSOUP_RETRY_INITIAL_DELAY = 5; // In seconds

    private static HttpClient instance;

    public static HttpClient getInstance() {
        if (instance == null) {
            instance = new HttpClient();
        }
        return instance;
    }

    public Response getRequest(String url, Messages messages) throws IOException, InterruptedException {
        Connection jsoupConnection = this.getJsoupConnection(url, messages);
        return this.request(url, jsoupConnection, messages);
    }

    public Response postXmlRequest(String url, String requestBody, Messages messages) throws IOException, InterruptedException {
        Connection jsoupConnection = this.getJsoupConnection(url, messages)
                .method(Connection.Method.POST)
                .header("Content-Type", "application/xml")
                .requestBody(requestBody);

        return this.request(url, jsoupConnection, messages);
    }

    private Response request(String url, Connection jsoupConnection, Messages messages) throws IOException, InterruptedException {
        IOException lastException = null;
        int delay = JSOUP_RETRY_INITIAL_DELAY;

        for (int i=0; i<JSOUP_RETRY; i++) {
            try {
                return new Response(jsoupConnection.execute());
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

    private Connection getJsoupConnection(String url, Messages messages) {
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
        //     We use "ignoreContentType" to work around this issue.
        // maxBodySize(0)
        //     Default to 2MB. 0 = Infinite.
        //     AtlasMapper layer list for the eAtlas is larger than 2MB.
        // sslSocketFactory(...)
        //     To deal with dodgy SSL certificates.
        Connection connection = Jsoup
                .connect(url)
                .timeout(120000)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .maxBodySize(0);

        SSLSocketFactory sslSocketFactory = this.socketFactory(messages);
        if (sslSocketFactory != null) {
            connection = connection.sslSocketFactory(sslSocketFactory);
        }

        return connection;
    }

    private SSLSocketFactory socketFactory(Messages messages) {
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

    public static class Response {
        private final byte[] bodyBytes;
        private final ContentType contentType;
        private final int statusCode;
        private final Long lastModified;

        // HttpConnection.Response
        public Response(org.jsoup.Connection.Response jsoupResponse) {
            this.bodyBytes = jsoupResponse.bodyAsBytes();
            this.statusCode = jsoupResponse.statusCode();
            this.lastModified = HttpClient.parseHttpLastModifiedHeader(jsoupResponse.header("Last-Modified"));

            String contentTypeStr = jsoupResponse.contentType();
            this.contentType = contentTypeStr == null? null : ContentType.parse(contentTypeStr);
        }

        public Response(
                int statusCode,
                byte[] bodyBytes,
                ContentType contentType,
                Long lastModified) {

            this.statusCode = statusCode;
            this.bodyBytes = bodyBytes;
            this.contentType = contentType;
            this.lastModified = lastModified;
        }

        public byte[] bodyAsBytes() {
            return this.bodyBytes;
        }

        public String body() {
            return this.bodyBytes == null ? null :
                    new String(this.bodyBytes, StandardCharsets.UTF_8);
        }

        public JSONObject jsonBody() {
            String body = this.body();
            return body == null ? null : new JSONObject(body);
        }

        public InputStream bodyStream() {
            return new ByteArrayInputStream(this.bodyBytes == null ? new byte[0] : this.bodyBytes);
        }

        public int statusCode() {
            return this.statusCode;
        }

        public ContentType contentType() {
            return this.contentType;
        }

        public String getFileExtension() {
            return HttpClient.getFileExtension(this.contentType());
        }

        public Long lastModified() {
            return this.lastModified;
        }

        public String extractText() throws IOException {
            ContentType contentType = this.contentType();
            String mimeType = contentType.getMimeType();

            if (ContentType.TEXT_HTML.equals(contentType) ||
                    ContentType.APPLICATION_XHTML_XML.equals(contentType)) {
                return HttpClient.extractHTMLTextContent(this.body());
            }
            if ("application/pdf".equals(mimeType)) {
                return HttpClient.extractPDFTextContent(this.bodyAsBytes());
            }

            return this.body();
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

    public static String getFileExtension(ContentType contentType) {
        if (contentType == null) {
            return null;
        }
        String mimeType = contentType.getMimeType();
        if (mimeType == null || mimeType.isEmpty()) {
            return null;
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
        return null;
    }

    public static Long parseHttpLastModifiedHeader(String lastModifiedHeader) {
        if (lastModifiedHeader == null || lastModifiedHeader.isEmpty()) {
            return null;
        }

        DateTime lastModifiedDate = null;
        try {
            lastModifiedDate = DateTimeFormat.forPattern("E, dd MMM yyyy HH:mm:ss z")
                    .withLocale(Locale.ENGLISH)
                    .parseDateTime(lastModifiedHeader);
        } catch(Exception ex) {
            System.err.printf("Exception occurred while parsing the HTTP header Last-Modified date: %s%n", lastModifiedHeader);
            ex.printStackTrace();
        }

        return lastModifiedDate == null ? null : lastModifiedDate.getMillis();
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
