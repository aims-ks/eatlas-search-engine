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

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class EntityUtilsTest {

    /**
     * This test rely on an external resource (https://google.com)
     * It might fail if there is no network.
     */
    @Test
    public void testHarvestURL() throws IOException {
        String url = "https://google.com";
        int minimumExpectedFileSize = 10000;

        String htmlContent = EntityUtils.harvestURL(url);

        Assert.assertNotNull("Google page returned null content.", htmlContent);
        Assert.assertTrue("The HTML document returned by Google.com is smaller than expected.",
                htmlContent.length() > minimumExpectedFileSize);

        Assert.assertTrue("The HTML document do not contain the word \"Google\"", htmlContent.contains("Google"));
    }
    @Test
    public void testHarvestURLRedirection() throws IOException {
        String url = "http://tiny.cc/f94ysz"; // Tiny URL which redirect to "https://google.com"
        int minimumExpectedFileSize = 10000;

        String htmlContent = EntityUtils.harvestURL(url);

        Assert.assertNotNull("Google page returned null content.", htmlContent);
        Assert.assertTrue("The HTML document returned by Google.com is smaller than expected.",
                htmlContent.length() > minimumExpectedFileSize);

        Assert.assertTrue("The HTML document do not contain the word \"Google\"", htmlContent.contains("Google"));
    }

    @Test (expected = java.net.UnknownHostException.class)
    public void testHarvestBrokenURL() throws IOException {
        String url = "https://bad_url_cef393a8cdff31563033e8b742dcadd5.com";
        EntityUtils.harvestURL(url);
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
            String textContent = EntityUtils.extractHTMLTextContent(expectedPair[0]);
            Assert.assertEquals(expectedPair[1], textContent);
        }
    }
}
