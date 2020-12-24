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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

public class EntityUtils {

    public static String harvestURL(String url) throws IOException {
        // Get a HTML document.
        // NOTE: JSoup takes care of following redirections.
        //     IOUtils.toString(URL, Charset) does not.
        // NOTE2: Body in this case is the body of the response.
        //     It's the entire HTML document, not just the content
        //     of the HTML body element.
        return Jsoup.connect(url).execute().body();
    }

    public static String extractTextContent(String htmlDocumentStr) {
        // JSoup is a HTML parsing library made to work with real life web documents.
        //     The parser handle broken HTML document the same way a browser would.
        Document document = Jsoup.parse(htmlDocumentStr);

        // Load the text content of the HTML body element.
        // NOTE: JSoup take care of converting HTML entities into UTF-8 characters.
        String bodyText = document.body().text();

        return bodyText;
    }
}
