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

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class EntityUtilsTest {

    /**
     * This test rely on an external resource (https://google.com)
     * It might fail if there is no network.
     */
    @Test
    public void testHarvestURL() throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.getInstance();

        Messages messages = Messages.getInstance(null);
        String url = "https://google.com";
        int minimumExpectedFileSize = 10000;

        HttpClient.Response response = httpClient.getRequest(url, messages);
        String htmlContent = response.body();

        Assertions.assertNotNull(htmlContent, "Google page returned null content.");
        Assertions.assertTrue(htmlContent.length() > minimumExpectedFileSize,
                "The HTML document returned by Google.com is smaller than expected.");

        Assertions.assertTrue(htmlContent.contains("Google"),
                "The HTML document do not contain the word \"Google\"");
    }

    @Test
    public void testHarvestURLRedirection() throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.getInstance();

        Messages messages = Messages.getInstance(null);
        String url = "http://google.com"; // This URL redirects to "https://www.google.com"
        int minimumExpectedFileSize = 10000;

        HttpClient.Response response = httpClient.getRequest(url, messages);
        String htmlContent = response.body();

        Assertions.assertNotNull(htmlContent, "Google page returned null content.");
        Assertions.assertTrue(htmlContent.length() > minimumExpectedFileSize,
                "The HTML document returned by Google.com is smaller than expected.");

        Assertions.assertTrue(htmlContent.contains("Google"),
                "The HTML document do not contain the word \"Google\"");
    }

    @Test
    public void testExtractTextContent() {
        String htmlTemplate = "<!DOCTYPE html>%n" +
            "<html>%n" +
            "  <head>%n" +
            "    <title>%s</title>%n" +
            "  </head>%n" +
            "  <body>%s</body>%n" +
            "</html>%n";

        /*
         * expected = 2D array:
         *   [
         *     [HTML document, Expected text],
         *     [HTML document, Expected text],
         *     ...
         *   ]
         */
        String[][] expected = new String[][] {
            {
                String.format(htmlTemplate, "Simple", "<p>Simple doc</p>"),
                "Simple doc"
            }, {
                String.format(htmlTemplate, "Style and script", "<script>let x=\"Bad text\";</script>" +
                    "<style>body { background-color: #FFDDDD; }</style>" +
                    "<p class=\"paragraph\">Actual text.</p>"),
                "Actual text."
            }, {
                String.format(htmlTemplate, "Malformed HTML", "Broken <ul>List</p>"),
                "Broken List"
            }, {
                "Broken <ul>HTML</p>",
                "Broken HTML"
            }, {
                String.format(htmlTemplate, "NBSP", "<p>Non-Breaking&nbsp;Space document</p>"),
                "Non-Breaking Space document"
            }, {
                String.format(htmlTemplate, "Entities", "<div>&lt;P&gt;<p>&amp;&quot;&yen;&#34;</p>&#60;/P&#62;</div>"),
                "<P> &\"¥\" </P>"
            }, {
                String.format(htmlTemplate, "Double encoded entity", "<div>&amp;amp;</div>"),
                "&amp;"
            }, {
                String.format(htmlTemplate, "Malformed entity", "Fish &amp chips"),
                "Fish & chips"
            }, {
                String.format(htmlTemplate, "French entities", "On apprend le Fran&ccedil;ais &agrave; l'&eacute;cole."),
                "On apprend le Français à l'école."
            }, {
                String.format(htmlTemplate, "Chinese entities", "Taipei: &#21488;&#21271;"),
                "Taipei: 台北"
            }
        };

        for (String[] expectedPair : expected) {
            String textContent = HttpClient.extractHTMLTextContent(expectedPair[0]);
            Assertions.assertEquals(expectedPair[1], textContent);
        }
    }


    // Manually run to test the retry feature.
    // It's not an automatic test because it takes about 5 minutes...
    @Disabled
    @Test
    public void testHarvestBrokenURL() {
        Assertions.assertThrows(java.net.UnknownHostException.class, () -> {
            // code that throws UnknownHostException
            HttpClient httpClient = HttpClient.getInstance();

            Messages messages = Messages.getInstance(null);
            String url = "https://bad.url.cef393a8cdff31563033e8b742dcadd5.com";
            httpClient.getRequest(url, messages);
        });
    }

    // Disabled: The old legacy eAtlas GeoServer is retired
    @Disabled
    @Test
    public void testLegacyGeoServer() throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.getInstance();
        Messages messages = Messages.getInstance(null);
        // The legacy GeoServer is very slow to response.
        // This is a good test to test JSoup timeout.
        String url = "http://maps.eatlas.org.au:80/geoserver/wms?REQUEST=GetMap&FORMAT=image%2Fpng&SRS=EPSG%3A4326&CRS=EPSG%3A4326&BBOX=144.406341552734%2C-20.206720352173%2C147.492263793945%2C-14.980480194092&VERSION=1.1.1&STYLES=&SERVICE=WMS&WIDTH=118&HEIGHT=200&TRANSPARENT=true&LAYERS=ea%3AJCU_Vertebrate-Herbert_River_Ringtail_Possum-realized";

        HttpClient.Response response = httpClient.getRequest(url, messages);
        String content = response.body();
        System.out.println(String.format("Content length: %d", content.length()));
    }
}
