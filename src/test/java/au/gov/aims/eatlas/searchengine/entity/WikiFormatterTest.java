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
package au.gov.aims.eatlas.searchengine.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WikiFormatterTest {

    @Test
    public void testArrayShift() {
        WikiFormatter wikiFormatter = WikiFormatter.getInstance();

        String[] array = new String[]{"One", "Two", "Three", "Four"};

        String expectedElement = "One";
        String[] expectedArray = new String[]{"Two", "Three", "Four"};

        // Equivalent to JavaScript:
        //     element = array.shift();
        String element = array[0]; array = wikiFormatter.arrayShift(array);

        //System.out.println(Arrays.toString(array));
        Assertions.assertEquals(expectedElement, element, "arrayShift didn't return the first element of the array");
        Assertions.assertArrayEquals(expectedArray, array, "arrayShift didn't modify the array as expected");
    }

    @Test
    public void testArrayPop() {
        WikiFormatter wikiFormatter = WikiFormatter.getInstance();

        String[] array = new String[]{"One", "Two", "Three", "Four"};

        String expectedElement = "Four";
        String[] expectedArray = new String[]{"One", "Two", "Three"};

        // Equivalent to JavaScript:
        //     element = array.pop();
        String element = array[array.length-1]; array = wikiFormatter.arrayPop(array);

        //System.out.println(Arrays.toString(array));
        Assertions.assertEquals(expectedElement, element, "arrayPop didn't return the last element of the array");
        Assertions.assertArrayEquals(expectedArray, array, "arrayPop didn't modify the array as expected");
    }

    @Test
    public void testGetText() {
        String wikiText = "==Test multi <line> /Wiki/ format.==\n" +
                "This *star is never closed so it must not be apply.\n" +
                "This /slash is closed at the end of the document so everything from here should be italic.\n" +
                "*Shipping list:*\n" +
                "* Milk\n" +
                "* Bread\n" +
                "** _Raisin bread_\n" +
                "** English muffins\n" +
                "* Sweets\n" +
                "** -Lolly pops-\n" +
                "** Chocolate\n" +
                "*** [[http://en.wikipedia.org/wiki/Caramel|*Caramel* chocolate]]\n" +
                "*** [[http://en.wikipedia.org/wiki/Chocolate|Normal /chocolate/]]\n" +
                "*** [[http://fr.wikipedia.org/wiki/Fichier:Vegan_Chocolate_Fudge.jpg|[[IMG|http://upload.wikimedia.org/wikipedia/commons/thumb/4/4c/Vegan_Chocolate_Fudge.jpg/100px-Vegan_Chocolate_Fudge.jpg|*Fudge* chocolate]]]]\n" +
                "\n" +
                "List of endangered species\n" +
                "# Abbott\n" +
                "## Abbott's Booby\n" +
                "## Abbott's Duiker\n" +
                "## Abbott's Starling\n" +
                "# Abor\n" +
                "## Abor Bug-eyed Frog\n" +
                "# Abra\n" +
                "## Abra Acanacu\n" +
                "### Abra Acanacu Marsupial Frog [[IMG|http://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Gastrotheca_excubitor.jpg/100px-Gastrotheca_excubitor.jpg|Abra Acanacu Marsupial Frog]]\n" +
                "## Abra Malaga Toad\n" +
                "# Coral\n" +
                "## Acropora\n" +
                "### Acropora abrolhosensis\n" +
                "### Acropora aculeus\n" +
                "### Acropora acuminata\n" +
                "### ...\n" +
                "# African Viviparous Toad\n" +
                "## Nectophrynoides\n" +
                "### Nectophrynoides asperginis\n" +
                "### Nectophrynoides cryptus\n" +
                "### Nectophrynoides laticeps\n" +
                "### ...\n" +
                "End of italic./\n" +
                "\n";

        String expected = "Test multi <line> Wiki format. " +
                "This *star is never closed so it must not be apply. " +
                "This slash is closed at the end of the document so everything from here should be italic. " +
                "Shipping list: " +
                "Milk " +
                "Bread " +
                "Raisin bread " +
                "English muffins " +
                "Sweets " +
                "Lolly pops " +
                "Chocolate " +
                "Caramel chocolate " +
                "Normal chocolate " +
                "List of endangered species " +
                "Abbott " +
                "Abbott's Booby " +
                "Abbott's Duiker " +
                "Abbott's Starling " +
                "Abor " +
                "Abor Bug-eyed Frog " +
                "Abra " +
                "Abra Acanacu " +
                "Abra Acanacu Marsupial Frog " +
                "Abra Malaga Toad " +
                "Coral " +
                "Acropora " +
                "Acropora abrolhosensis " +
                "Acropora aculeus " +
                "Acropora acuminata " +
                "... " +
                "African Viviparous Toad " +
                "Nectophrynoides " +
                "Nectophrynoides asperginis " +
                "Nectophrynoides cryptus " +
                "Nectophrynoides laticeps " +
                "... " +
                "End of italic.";

        String actual = WikiFormatter.getText(wikiText);

        Assertions.assertEquals(expected, actual, "GetText return unexpected output");
    }

    @Test
    public void testStyles() {
        WikiFormatter wikiFormatter = WikiFormatter.getInstance();

        String[][] wikiTexts = {
            {
                "Bold: *expression*",
                "Bold: <b>expression</b>"
            }, {
                "Italic: /expression/",
                "Italic: <i>expression</i>"
            }, {
                "Underline: _expression_",
                "Underline: <u>expression</u>"
            }, {
                "Strike: -expression-",
                "Strike: <s>expression</s>"
            }, {
                "All 4, nested: */_-expression-_/*",
                "All 4, nested: <b><i><u><s>expression</s></u></i></b>"
            }, {
                "All 4, mixed: /*Mix* _of -different-_ *styles*/",
                "All 4, mixed: <i><b>Mix</b> <u>of <s>different</s></u> <b>styles</b></i>"
            }, {
                "_*Nested bold and underlined*_ (Valid)",
                "<u><b>Nested bold and underlined</b></u> (Valid)"
            }, {
                "*_Overlapping bold and underlined*_ (Invalid, only the first style, bold, is applied)",
                "<b>_Overlapping bold and underlined</b>_ (Invalid, only the first style, bold, is applied)"
            }, {
                "*not bo*ld (The end of the bold style is inside a word)",
                "*not bo*ld (The end of the bold style is inside a word)"
            }, {
                "/Complete italic sentence ending with a dot./",
                "<i>Complete italic sentence ending with a dot.</i>"
            }, {
                "/Two lines italic sentence\n" +
                "ending with a dot./",

                "<i>Two lines italic sentence<br/>\n" +
                "ending with a dot.</i>"
            }, {
                "/Two lines italic *sentence*\n" +
                "ending with a dot./",

                "<i>Two lines italic <b>sentence</b><br/>\n" +
                "ending with a dot.</i>"
            }, {
                "/Two lines /italic sentence/\n" +
                "ending with a dot./",

                "<i>Two lines <i>italic sentence</i><br/>\n" +
                "ending with a dot.</i>"
            }, {
                "Sentence ending with a word in /italic/.",
                "Sentence ending with a word in <i>italic</i>."
            }, {
                "Carrots, /apples,/ bananas, /oranges/, pears.",
                "Carrots, <i>apples,</i> bananas, <i>oranges</i>, pears."
            }, {
                "Carrots|*apples,* bananas, [*oranges*], *[pears]*.",
                "Carrots|<b>apples,</b> bananas, [<b>oranges</b>], <b>[pears]</b>."
            }, {
                "Should be (*bold* and -_underline_ and /italic/)",
                "Should be (<b>bold</b> and -<u>underline</u> and <i>italic</i>)"
            }
        };

        for (String[] wikiText : wikiTexts) {
            String expected = wikiText[1];
            String actual = wikiFormatter.format(wikiText[0]);

            Assertions.assertEquals(expected, actual, "Style parsing failed");
        }
    }

    @Test
    public void testHeaders() {
        WikiFormatter wikiFormatter = WikiFormatter.getInstance();

        String[][] wikiTexts = {
            {
                "==Heading 1==",
                "<h1>Heading 1</h1>"
            }, {
                "===Heading 2===",
                "<h2>Heading 2</h2>"
            }, {
                "====Heading 3====",
                "<h3>Heading 3</h3>"
            }, {
                "=====Heading 4=====",
                "<h4>Heading 4</h4>"
            }, {
                "======Heading 5======",
                "<h5>Heading 5</h5>"
            }, {
                "=======Heading 6=======",
                "<h6>Heading 6</h6>"
            }, {
                "========Too many \"equals\"========",
                "========Too many \"equals\"========"
            }, {
                "=======Unbalanced (use the minimum between the start and the end)====",
                "<h3>===Unbalanced (use the minimum between the start and the end)</h3>"
            }, {
                "====Unbalanced (use the minimum between the start and the end)=======",
                "<h3>Unbalanced (use the minimum between the start and the end)===</h3>"
            }
        };

        for (String[] wikiText : wikiTexts) {
            String expected = wikiText[1];
            String actual = wikiFormatter.format(wikiText[0]).trim();

            Assertions.assertEquals(expected, actual, "Header parsing failed");
        }
    }

    @Test
    public void testBulletLists() {
        WikiFormatter wikiFormatter = WikiFormatter.getInstance();

        String[][] wikiTexts = {
            {
                "* First element\n" +
                "** /Sub/ element\n" +
                "* *2nd* element",

                "<ul>\n" +
                    "<li>First element\n" +
                        "<ul>\n" +
                            "<li><i>Sub</i> element</li>\n" +
                        "</ul>\n" +
                    "</li>\n" +
                    "<li><b>2nd</b> element</li>\n" +
                "</ul>"
            },

            // Numbered list
            {
                "# First element\n" +
                "## /Sub/ element\n" +
                "# *2nd* element",

                "<ol>\n" +
                    "<li>First element\n" +
                        "<ol>\n" +
                            "<li><i>Sub</i> element</li>\n" +
                        "</ol>\n" +
                    "</li>\n" +
                    "<li><b>2nd</b> element</li>\n" +
                "</ol>"
            }
        };

        for (String[] wikiText : wikiTexts) {
            String expected = wikiText[1];
            String actual = wikiFormatter.format(wikiText[0]).trim();

            Assertions.assertEquals(expected, actual, "Bullet list parsing failed");
        }
    }

    @Test
    public void testUrls() {
        WikiFormatter wikiFormatter = WikiFormatter.getInstance();

        String[][] wikiTexts = {
            {
                "http://www.google.com/",
                "<a href=\"http://www.google.com/\" target=\"_blank\">http://www.google.com/</a>"
            }, {
                "https://www.google.com/",
                "<a href=\"https://www.google.com/\" target=\"_blank\">https://www.google.com/</a>"
            }, {
                "ftp://www.google.com/",
                "<a href=\"ftp://www.google.com/\" target=\"_blank\">ftp://www.google.com/</a>"
            }, {
                "sftp://www.google.com/",
                "<a href=\"sftp://www.google.com/\" target=\"_blank\">sftp://www.google.com/</a>"
            }, {
                "file:///usr/bin/bash",
                "<a href=\"file:///usr/bin/bash\" target=\"_blank\">file:///usr/bin/bash</a>"
            }, {
                // Unknown protocol - not considered as a URL
                "gopher://gopher.hprc.utoronto.ca/",
                "gopher://gopher.hprc.utoronto.ca/"
            }, {
                // Mailto should be avoid!
                "mailto:someone@example.com",
                "mailto:someone@example.com"
            }, {
                "http://google.com/",
                "<a href=\"http://google.com/\" target=\"_blank\">http://google.com/</a>"
            }, {
                "www.google.com",
                "<a href=\"http://www.google.com\" target=\"_blank\">www.google.com</a>"
            }, {
                // This is not reconized as a URL
                "google.com",
                "google.com"
            }, {
                "[[http://google.com/]]",
                "<a href=\"http://google.com/\" target=\"_blank\">http://google.com/</a>"
            }, {
                "[[http://www.google.com/|Google]]",
                "<a href=\"http://www.google.com/\" target=\"_blank\">Google</a>"
            }, {
                "[[page2.html|*Page /2/*]]",
                "<a href=\"page2.html\"><b>Page <i>2</i></b></a>"
            }, {
                "[[IMG|../resources/images/maps-small.jpg|Maps]]",
                "<img src=\"../resources/images/maps-small.jpg\" alt=\"Maps\" title=\"Maps\"/>"
            }, {
                "[[IMG|../resources/images/maps-small.jpg|Maps||imgClass]]",
                "<img src=\"../resources/images/maps-small.jpg\" class=\"imgClass\" alt=\"Maps\" title=\"Maps\"/>"
            }, {
                "[[IMG|../resources/images/maps-small.jpg|Maps|?X100]]",
                "<img src=\"../resources/images/maps-small.jpg\" style=\"height:100px;\" alt=\"Maps\" title=\"Maps\"/>"
            }, {
                "[[IMG|../resources/images/maps-small.jpg|Maps|100]]",
                "<img src=\"../resources/images/maps-small.jpg\" style=\"width:100px;\" alt=\"Maps\" title=\"Maps\"/>"
            }, {
                "[[IMG|../resources/images/maps-small.jpg|Maps|100X?]]",
                "<img src=\"../resources/images/maps-small.jpg\" style=\"width:100px;\" alt=\"Maps\" title=\"Maps\"/>"
            }, {
                "[[IMG|../resources/images/maps-small.jpg|Maps|10%X?]]",
                "<img src=\"../resources/images/maps-small.jpg\" style=\"width:10%;\" alt=\"Maps\" title=\"Maps\"/>"
            }, {
                "[[IMG|../resources/images/maps-small.jpg|Maps|10%X?|imgClass]]",
                "<img src=\"../resources/images/maps-small.jpg\" class=\"imgClass\" style=\"width:10%;\" alt=\"Maps\" title=\"Maps\"/>"
            }, {
                "[[IMG|../resources/images/maps-small.jpg|Maps|100X100]]",
                "<img src=\"../resources/images/maps-small.jpg\" style=\"width:100px;height:100px;\" alt=\"Maps\" title=\"Maps\"/>"
            }, {
                "[[DOWNLOAD|../resources/images/maps-small.jpg]]",
                "<a href=\"../resources/images/maps-small.jpg\" download=\"maps-small.jpg\" target=\"_blank\">maps-small.jpg</a>"
            }, {
                "[[DOWNLOAD|../resources/images/maps-small.jpg|maps.jpg]]",
                "<a href=\"../resources/images/maps-small.jpg\" download=\"maps.jpg\" target=\"_blank\">maps.jpg</a>"
            }, {
                "[[DOWNLOAD|../resources/images/maps-small.jpg|mapsSmall.jpg|Maps]]",
                "<a href=\"../resources/images/maps-small.jpg\" download=\"mapsSmall.jpg\" target=\"_blank\">Maps</a>"
            }, {
                "[[DOWNLOAD|../resources/images/maps-small.jpg|maps-small.jpg|[[IMG|../resources/images/maps-small.jpg|Maps]]]]",
                "<a href=\"../resources/images/maps-small.jpg\" download=\"maps-small.jpg\" target=\"_blank\"><img src=\"../resources/images/maps-small.jpg\" alt=\"Maps\" title=\"Maps\"/></a>"
            }
        };

        for (String[] wikiText : wikiTexts) {
            String expected = wikiText[1];
            String actual = wikiFormatter.format(wikiText[0]).trim();

            Assertions.assertEquals(expected, actual, "URL parsing failed");
        }
    }

    @Test
    public void testMultilineText() {
        WikiFormatter wikiFormatter = WikiFormatter.getInstance();

        String[][] wikiTexts = {
            {
                "==Test multi line /Wiki/ format.==\n" +
                "This *star is never closed so it must not be apply.\n" +
                "This /slash is closed at the end of the document so everything from here should be italic.\n" +
                "*Shipping list:*\n" +
                "* Milk\n" +
                "* Bread\n" +
                "** _Raisin bread_\n" +
                "** English muffins\n" +
                "* Sweets\n" +
                "** -Lolly pops-\n" +
                "** Chocolate\n" +
                "*** [[http://en.wikipedia.org/wiki/Caramel|*Caramel* chocolate]]\n" +
                "*** [[http://en.wikipedia.org/wiki/Chocolate|Normal /chocolate/]]\n" +
                "*** [[http://fr.wikipedia.org/wiki/Fichier:Vegan_Chocolate_Fudge.jpg|[[IMG|http://upload.wikimedia.org/wikipedia/commons/thumb/4/4c/Vegan_Chocolate_Fudge.jpg/100px-Vegan_Chocolate_Fudge.jpg|*Fudge* chocolate]]]]\n" +
                "\n" +
                "List of endangered species\n" +
                "# Abbott\n" +
                "## Abbott's Booby\n" +
                "## Abbott's Duiker\n" +
                "## Abbott's Starling\n" +
                "# Abor\n" +
                "## Abor Bug-eyed Frog\n" +
                "# Abra\n" +
                "## Abra Acanacu\n" +
                "### Abra Acanacu Marsupial Frog [[IMG|http://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Gastrotheca_excubitor.jpg/100px-Gastrotheca_excubitor.jpg|Abra Acanacu Marsupial Frog]]\n" +
                "## Abra Malaga Toad\n" +
                "# Coral\n" +
                "## Acropora\n" +
                "### Acropora abrolhosensis\n" +
                "### Acropora aculeus\n" +
                "### Acropora acuminata\n" +
                "### ...\n" +
                "# African Viviparous Toad\n" +
                "## Nectophrynoides\n" +
                "### Nectophrynoides asperginis\n" +
                "### Nectophrynoides cryptus\n" +
                "### Nectophrynoides laticeps\n" +
                "### ...\n" +
                "End of italic./\n" +
                "\n",

                "<h1>Test multi line <i>Wiki</i> format.</h1>\n" +
                "This *star is never closed so it must not be apply.<br/>\n" +
                "This <i>slash is closed at the end of the document so everything from here should be italic.<br/>\n" +
                "<b>Shipping list:</b><br/>\n" +
                "<ul>\n" +
                    "<li>Milk</li>\n" +
                    "<li>Bread\n" +
                        "<ul>\n" +
                            "<li><u>Raisin bread</u></li>\n" +
                            "<li>English muffins</li>\n" +
                        "</ul>\n" +
                    "</li>\n" +
                    "<li>Sweets\n" +
                        "<ul>\n" +
                            "<li><s>Lolly pops</s></li>\n" +
                            "<li>Chocolate\n" +
                                "<ul>\n" +
                                    "<li><a href=\"http://en.wikipedia.org/wiki/Caramel\" target=\"_blank\"><b>Caramel</b> chocolate</a></li>\n" +
                                    "<li><a href=\"http://en.wikipedia.org/wiki/Chocolate\" target=\"_blank\">Normal <i>chocolate</i></a></li>\n" +
                                    "<li><a href=\"http://fr.wikipedia.org/wiki/Fichier:Vegan_Chocolate_Fudge.jpg\" target=\"_blank\"><img src=\"http://upload.wikimedia.org/wikipedia/commons/thumb/4/4c/Vegan_Chocolate_Fudge.jpg/100px-Vegan_Chocolate_Fudge.jpg\" alt=\"*Fudge* chocolate\" title=\"*Fudge* chocolate\"/></a></li>\n" +
                                "</ul>\n" +
                            "</li>\n" +
                        "</ul>\n" +
                    "</li>\n" +
                "</ul>\n" +
                "<br/>\n" +
                "List of endangered species<br/>\n" +
                "<ol>\n" +
                    "<li>Abbott\n" +
                        "<ol>\n" +
                            "<li>Abbott's Booby</li>\n" +
                            "<li>Abbott's Duiker</li>\n" +
                            "<li>Abbott's Starling</li>\n" +
                        "</ol>\n" +
                    "</li>\n" +
                    "<li>Abor\n" +
                        "<ol>\n" +
                            "<li>Abor Bug-eyed Frog</li>\n" +
                        "</ol>\n" +
                    "</li>\n" +
                    "<li>Abra\n" +
                        "<ol>\n" +
                            "<li>Abra Acanacu\n" +
                                "<ol>\n" +
                                    "<li>Abra Acanacu Marsupial Frog <img src=\"http://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Gastrotheca_excubitor.jpg/100px-Gastrotheca_excubitor.jpg\" alt=\"Abra Acanacu Marsupial Frog\" title=\"Abra Acanacu Marsupial Frog\"/></li>\n" +
                                "</ol>\n" +
                            "</li>\n" +
                            "<li>Abra Malaga Toad</li>\n" +
                        "</ol>\n" +
                    "</li>\n" +
                    "<li>Coral\n" +
                        "<ol>\n" +
                            "<li>Acropora\n" +
                                "<ol>\n" +
                                    "<li>Acropora abrolhosensis</li>\n" +
                                    "<li>Acropora aculeus</li>\n" +
                                    "<li>Acropora acuminata</li>\n" +
                                    "<li>...</li>\n" +
                                "</ol>\n" +
                            "</li>\n" +
                        "</ol>\n" +
                    "</li>\n" +
                    "<li>African Viviparous Toad\n" +
                        "<ol>\n" +
                            "<li>Nectophrynoides\n" +
                                "<ol>\n" +
                                    "<li>Nectophrynoides asperginis</li>\n" +
                                    "<li>Nectophrynoides cryptus</li>\n" +
                                    "<li>Nectophrynoides laticeps</li>\n" +
                                    "<li>...</li>\n" +
                                "</ol>\n" +
                            "</li>\n" +
                        "</ol>\n" +
                    "</li>\n" +
                "</ol>\n" +
                "End of italic.</i><br/>\n" +
                "<br/>"
            }
        };

        for (String[] wikiText : wikiTexts) {
            String expected = wikiText[1];
            String actual = wikiFormatter.format(wikiText[0]).trim();

            Assertions.assertEquals(expected, actual, "Multiline text parsing failed");
        }
    }
}
