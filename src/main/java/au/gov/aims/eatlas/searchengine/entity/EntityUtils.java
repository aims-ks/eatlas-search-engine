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

import org.apache.commons.io.IOUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class EntityUtils {

    // You need to edit the /conf/catalina.policy file to grant java.net.SocketPermission
    // https://stackoverflow.com/questions/37483618/how-to-solve-java-security-accesscontrolexception
    // https://stackoverflow.com/questions/35891684/rmi-server-throws-accesscontrolexception
    public static String harvestURL(String url) throws IOException {
        //Connection.Response response = Jsoup.connect(url).execute();
        //return response.body();
        // OR
        //return IOUtils.toString(new URL(url), StandardCharsets.UTF_8);

        // TODO Load HTML document using a HTTP Client
        if ("http://www.coralsoftheworld.org/".equals(url)) {
            return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "    <head>\n" +
                "        <meta charset=\"utf-8\">\n" +
                "        <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\n" +
                "        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                "\n" +
                "        <title>Corals of the World</title>\n" +
                "    </head>\n" +
                "    <body role=\"document\">\n" +
                "        <!-- include jquery at the top as some of the embedded templates may rely on it -->\n" +
                "        <script type=\"text/javascript\" src=\"/static/jquery/dist/jquery.min.js\"></script>\n" +
                "        <div id=\"content_container\" class=\"container\">\n" +
                "            <div class=\"row header\">\n" +
                "                <div class=\"col-md-12\">\n" +
                "                    <div class=\"masthead\">\n" +
                "                        <div id=\"banner\">\n" +
                "                            <div class=\"donate-wrapper\">\n" +
                "                                <img src=\"/static/img/Coral_Reef_Research.png\" alt=\"Coral Reef Research\" />\n" +
                "                                <a href=\"/page/donate/\" class=\"btn btn-sm donate_banner_btn\" role=\"button\">Donate</a>\n" +
                "                            </div>\n" +
                "                            <div class=\"search-wrapper\">\n" +
                "                                <form action=\"/search-results\" class=\"input-group\">\n" +
                "                                    <input type=\"text\" name=\"q\" class=\"form-control input-sm\" placeholder=\"Search\">\n" +
                "                                    <div class=\"input-group-btn\">\n" +
                "                                        <button type=\"submit\" value=\"Search\" class=\"btn btn-sm\">Go</button>\n" +
                "                                    </div>\n" +
                "                                </form>\n" +
                "                            </div>\n" +
                "                        </div>\n" +
                "                        <nav class=\"navbar navbar-default\" role=\"navigation\">\n" +
                "                            <div class=\"navbar-header\">\n" +
                "                                <button type=\"button\" class=\"navbar-toggle collapsed\" data-toggle=\"collapse\" data-target=\"#navbar\">\n" +
                "                                    <span class=\"sr-only\">Toggle navigation</span>\n" +
                "                                    <span class=\"icon-bar\"></span>\n" +
                "                                    <span class=\"icon-bar\"></span>\n" +
                "                                    <span class=\"icon-bar\"></span>\n" +
                "                                </button>\n" +
                "                                <a class=\"cotw_brand navbar-brand\" href=\"/\">Corals of the World</a>\n" +
                "                            </div>\n" +
                "                            <div class=\"collapse navbar-collapse\" id=\"navbar\">\n" +
                "                                <ul class=\"nav navbar-nav white-hover\">\n" +
                "                                    <li>\n" +
                "                                        \n" +
                "                                            \n" +
                "                                                <li class=\"dropdown\">\n" +
                "                                                    <a href=\"#\" class=\"dropdown-toggle nav coral-nav\" data-toggle=\"dropdown\">\n" +
                "                                                        <span>\n" +
                "                                                            \n" +
                "                                                            Home\n" +
                "                                                        </span>\n" +
                "                                                    </a>\n" +
                "                                                    <ul class=\"dropdown-menu\">\n" +
                "                                                        \n" +
                "                                                            <li >\n" +
                "                                                                <a href=\"/page/home/\">Home</a>\n" +
                "                                                                <ul class=\"dropdown-menu\">\n" +
                "                                                                    \n" +
                "                                                                </ul>\n" +
                "                                                            </li>\n" +
                "                                                        \n" +
                "                                                            <li >\n" +
                "                                                                <a href=\"/page/video-tour/\">Video tour</a>\n" +
                "                                                                <ul class=\"dropdown-menu\">\n" +
                "                                                                    \n" +
                "                                                                </ul>\n" +
                "                                                            </li>\n" +
                "                                                        \n" +
                "                                                            <li >\n" +
                "                                                                <a href=\"/page/history-and-scope/\">History and scope</a>\n" +
                "                                                                <ul class=\"dropdown-menu\">\n" +
                "                                                                    \n" +
                "                                                                </ul>\n" +
                "                                                            </li>\n" +
                "                                                        \n" +
                "                                                            <li >\n" +
                "                                                                <a href=\"/page/whats-new/\">What&#39;s new</a>\n" +
                "                                                                <ul class=\"dropdown-menu\">\n" +
                "                                                                    \n" +
                "                                                                </ul>\n" +
                "                                                            </li>\n" +
                "                                                        \n" +
                "                                                            <li >\n" +
                "                                                                <a href=\"/page/future-directions/\">Future directions</a>\n" +
                "                                                                <ul class=\"dropdown-menu\">\n" +
                "                                                                    \n" +
                "                                                                </ul>\n" +
                "                                                            </li>\n" +
                "                                                        \n" +
                "                                                            <li >\n" +
                "                                                                <a href=\"/page/website-policy/\">Website policy</a>\n" +
                "                                                                <ul class=\"dropdown-menu\">\n" +
                "                                                                    \n" +
                "                                                                </ul>\n" +
                "                                                            </li>\n" +
                "                                                        \n" +
                "                                                            <li >\n" +
                "                                                                <a href=\"/page/versioning/\">Versioning</a>\n" +
                "                                                                <ul class=\"dropdown-menu\">\n" +
                "                                                                    \n" +
                "                                                                </ul>\n" +
                "                                                            </li>\n" +
                "                                                        \n" +
                "                                                            <li >\n" +
                "                                                                <a href=\"/page/authors/\">Authors</a>\n" +
                "                                                                <ul class=\"dropdown-menu\">\n" +
                "                                                                    \n" +
                "                                                                </ul>\n" +
                "                                                            </li>\n" +
                "                                                        \n" +
                "                                                            <li >\n" +
                "                                                                <a href=\"/page/citation-guide/\">Citation guide</a>\n" +
                "                                                                <ul class=\"dropdown-menu\">\n" +
                "                                                                    \n" +
                "                                                                </ul>\n" +
                "                                                            </li>\n" +
                "                                                        \n" +
                "                                                            <li >\n" +
                "                                                                <a href=\"/page/acknowledgements/\">Acknowledgements</a>\n" +
                "                                                                <ul class=\"dropdown-menu\">\n" +
                "                                                                    \n" +
                "                                                                </ul>\n" +
                "                                                            </li>\n" +
                "                                                        \n" +
                "                                                            <li >\n" +
                "                                                                <a href=\"/page/donate/\">Support this website</a>\n" +
                "                                                                <ul class=\"dropdown-menu\">\n" +
                "                                                                    \n" +
                "                                                                </ul>\n" +
                "                                                            </li>\n" +
                "                                                        \n" +
                "                                                    </ul>\n" +
                "                                                </li>\n" +
                "                                    </li>\n" +
                "                                </ul>\n" +
                "                            </div>\n" +
                "                        </nav>\n" +
                "                    </div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </body>\n" +
                "</html>\n";
        }
        return "<!DOCTYPE html>\n" +
            "<html lang=\"en-GB\">\n" +
            "<head>\n" +
            "<meta charset=\"UTF-8\">\n" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
            "<link rel=\"profile\" href=\"https://gmpg.org/xfn/11\">\n" +
            "</head>\n" +
            "<body data-rsssl=1 itemtype='https://schema.org/WebPage' itemscope='itemscope' class=\"page-template-default page page-id-835 wp-custom-logo theme-astra checkout courses profile become_a_teacher astra learnpress learnpress-page locale-en-gb has-navmenu has-megamenu woocommerce-no-js ehf-footer ehf-template-astra ehf-stylesheet-astra ast-desktop ast-page-builder-template ast-no-sidebar astra-2.5.5 ast-header-custom-item-inside ast-full-width-primary-header ast-single-post ast-mobile-inherit-site-logo ast-inherit-site-logo-transparent ast-theme-transparent-header elementor-default elementor-kit-7158 elementor-page elementor-page-835\">\n" +
            "\n" +
            "<div class=\"hfeed site\" id=\"page\">\n" +
            "    <p>Debate continues in the literature and between seagrass taxonomists on the details (particularly below sub class) on the correct classification. For example, the Angiosperm Phylogeny Group published several papers recommending angiosperm classification, and is considered by many to represent the &#8220;standard&#8221;. However many prominent seagrass taxonomists disagree. From the advice of Dr Don Les (University of Connecticut) and Assoc. Prof Michelle Waycott (The University of Adelaide), this is the best we have been able to compile:</p>\n" +
            "</div>\n" +
            "</body>\n" +
            "</html>";
    }

    public static String extractTextContent(String htmlDocumentStr) {
        Document document = Jsoup.parse(htmlDocumentStr);
        return document.body().text();

        // TODO Load the text content of the body element using a XML parser
        // TODO Replace "&nbsp;" with space and other HTML Entities with UTF8 equivalent
        //return "Debate continues in the literature and between seagrass taxonomists on the details (particularly below sub class) on the correct classification. For example, the Angiosperm Phylogeny Group published several papers recommending angiosperm classification, and is considered by many to represent the &#8220;standard&#8221;. However many prominent seagrass taxonomists disagree. From the advice of Dr Don Les (University of Connecticut) and Assoc. Prof Michelle Waycott (The University of Adelaide), this is the best we have been able to compile:";
    }
}
