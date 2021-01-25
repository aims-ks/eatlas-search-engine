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

import org.apache.log4j.Logger;
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
import java.util.Locale;
import java.util.Map;

public class EntityUtils {
    private static final Logger LOGGER = Logger.getLogger(EntityUtils.class.getName());

    private static final int JSOUP_RETRY = 5;
    // NOTE: The delay is incremental: 5, 10, 20, 40, 80...
    private static final int JSOUP_RETRY_INITIAL_DELAY = 5; // In seconds

    public static String harvestGetURL(String url) throws IOException, InterruptedException {
        LOGGER.debug(String.format("Harvesting GET URL body: %s", url));

        // Get a HTTP document.
        // NOTE: Body in this case is the body of the response.
        //     It's the entire HTML document, not just the content
        //     of the HTML body element.
        return jsoupExecuteWithRetry(EntityUtils.getJsoupConnection(url), url)
                .body();
    }

    public static String harvestPostURL(String url, Map<String, String> dataMap) throws IOException, InterruptedException {
        LOGGER.debug(String.format("Harvesting POST URL body: %s%n%s", url, mapToString(dataMap)));

        return jsoupExecuteWithRetry(EntityUtils.getJsoupConnection(url)
                    .data(dataMap)
                    .method(Connection.Method.POST), url)
                .body();
    }

    public static String harvestURLText(String url) throws IOException, InterruptedException {
        LOGGER.debug(String.format("Harvesting URL text: %s", url));

        Connection.Response response = jsoupExecuteWithRetry(EntityUtils.getJsoupConnection(url), url);

        String mimetype = response.contentType();
        if (mimetype == null) {
            return response.body();
        }

        mimetype = mimetype.trim().toLowerCase(Locale.ENGLISH);

        if (mimetype.contains("text/html")) {
            return EntityUtils.extractHTMLTextContent(response.body());
        }

        if (mimetype.contains("application/pdf")) {
            return EntityUtils.extractPDFTextContent(response.bodyAsBytes());
        }

        LOGGER.warn(String.format("Unsupported mimetype: %s", mimetype));
        return response.body();
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
                LOGGER.warn(String.format("Connection timed out while requesting URL: %s%nWait for %d seconds before re-trying [%d/%d].",
                        url, delay, i+1, JSOUP_RETRY));
                lastException = ex;
                Thread.sleep(delay * 1000L);
                delay *= 2;
            }
        }
        LOGGER.error(String.format("Connection timed out %d times while requesting URL: %s", JSOUP_RETRY, url));

        throw lastException;
    }

    private static Connection getJsoupConnection(String url) {
        // JSoup takes care of following redirections.
        //     IOUtils.toString(URL, Charset) does not.
        //
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
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .maxBodySize(0)
                .sslSocketFactory(EntityUtils.socketFactory());
    }

    private static SSLSocketFactory socketFactory() {
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
            LOGGER.error("Failed to create a SSL socket factory.", ex);
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
        try (PDDocument document = PDDocument.load(documentBytes)) {
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
}
