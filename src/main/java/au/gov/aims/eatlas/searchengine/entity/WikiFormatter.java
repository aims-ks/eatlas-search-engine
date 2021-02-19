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

import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Parse the "Wiki" text format used in GeoNetwork
 * and by AtlasMapper.
 */
public class WikiFormatter {
    private static final char EMPTY_CHAR = '\0';
    private static final int MAX_URL_LENGTH = 40;

    private static final WikiFormatter instance;

    static {
        instance = new WikiFormatter();
    }

    private WikiFormatter() {}

    protected static WikiFormatter getInstance() {
        return instance;
    }

    public static String getText(String input) {
        String html = WikiFormatter.getHTML(input);

        if (html == null || html.isEmpty()) {
            return null;
        }

        // Extract text from HTML using Jsoup HTML parser
        String text = Jsoup.parse(html).text();

        return (text == null || text.isEmpty()) ? null : text;
    }

    public static String getHTML(String input) {
        if (input == null) {
            return null;
        }

        String html = WikiFormatter.getInstance().format(input);

        return (html == null || html.isEmpty()) ? null : html;
    }

    // Inspired from AtlasMapper:
    //     atlasmapper/clientResources/amc/modules/Utils/WikiFormater.js
    protected String format(String input) {
        if (this.isBlank(input)) {
            return input;
        }

        // Normalize input, to deal only with "\n" for new lines.
        input = input.replace("\r\n", "\n").replace("\r", "\n");

        String output = "", htmlChunk = "";
        char currentChar = EMPTY_CHAR;

        for (int i=0, len=input.length(); i<len; i++) {
            currentChar = this.charAt(input, i);
            htmlChunk = "";

            // ********************
            // * New lines (<br>) *
            // ********************
            if (currentChar == '\n') {
                htmlChunk += "<br/>\n";
            }

            // *******************************
            // * Styles (<b>, <i>, <u>, <s>) *
            // *******************************
            if (this._isStyleChar(currentChar)) {
                Tag openTag = this._getStyleTag(input, i);
                if (openTag != null && openTag.open) {
                    // Look for its close tag
                    Tag closeTag = this._findCloseTag(input, openTag);
                    if (closeTag != null) {
                        htmlChunk += openTag.tag + this.format(input.substring(openTag.index+1, closeTag.index)) + closeTag.tag;
                        i = closeTag.index;
                        currentChar = i < len ? this.charAt(input, i) : EMPTY_CHAR;
                    }
                }
            }

            // *************
            // * Wiki URLs *
            // *************
            if (currentChar == '[' && i+1 < len && this.charAt(input, i+1) == '[') {
                int index = i;
                boolean inURL = true, isURL = false;
                // Find the index of the last URL chars
                // NOTE: URLs can not contains URL, so the first closing tag is for the current URL.
                while (inURL && index < len) {
                    // '\n' is not allow in Wiki URL
                    if (this.charAt(input, index) == '\n') {
                        inURL = false;
                    }
                    if (this.charAt(input, index+1) != ']' && this.charAt(input, index) == ']' && this.charAt(input, index-1) == ']') {
                        inURL = false;
                        isURL = true;
                    }
                    if (inURL) {
                        index++;
                    }
                }
                if (isURL) {
                    String wikiURL = input.substring(i+2, index-1);
                    String urlStr, filename = "", label, dim = "", cssClass = "";
                    String[] wikiURLParts = wikiURL.split("\\|");

                    String type = "URL";
                    if (wikiURLParts.length >= 2) {
                        if ("IMG".equals(wikiURLParts[0])) {
                            // Remove IMG
                            //type = wikiURLParts.shift();
                            type = wikiURLParts[0]; wikiURLParts = this.arrayShift(wikiURLParts);
                            //urlStr = wikiURLParts.shift();
                            urlStr = wikiURLParts[0]; wikiURLParts = this.arrayShift(wikiURLParts);
                            if (wikiURLParts.length > 1) {
                                //dim = wikiURLParts.pop();
                                dim = wikiURLParts[wikiURLParts.length-1]; wikiURLParts = this.arrayPop(wikiURLParts);
                            }
                            if (wikiURLParts.length > 1) {
                                cssClass = dim;
                                //dim = wikiURLParts.pop();
                                dim = wikiURLParts[wikiURLParts.length-1]; wikiURLParts = this.arrayPop(wikiURLParts);
                            }
                            //label = wikiURLParts.join('|');
                            label = String.join("|", wikiURLParts);
                        } else if ("DOWNLOAD".equals(wikiURLParts[0])) {
                            // Remove DOWNLOAD
                            //type = wikiURLParts.shift();
                            type = wikiURLParts[0]; wikiURLParts = this.arrayShift(wikiURLParts);
                            if (wikiURLParts.length >= 2) {
                                //urlStr = wikiURLParts.shift();
                                urlStr = wikiURLParts[0]; wikiURLParts = this.arrayShift(wikiURLParts);
                                //filename = wikiURLParts.shift();
                                filename = wikiURLParts[0]; wikiURLParts = this.arrayShift(wikiURLParts);
                                label = this.format(String.join("|", wikiURLParts));
                                if (this.isBlank(label)) {
                                    label = filename;
                                }
                            } else {
                                urlStr = wikiURLParts[0];
                                filename = urlStr;

                                int lastSlashIndex = filename.lastIndexOf('/');
                                int questionMarkIndex = filename.indexOf('?');
                                if (questionMarkIndex < 0) { questionMarkIndex = filename.length(); }

                                if (lastSlashIndex+1 < questionMarkIndex) {
                                    filename = filename.substring(lastSlashIndex+1, questionMarkIndex);
                                }
                                label = filename;
                            }
                        } else {
                            //urlStr = wikiURLParts.shift();
                            urlStr = wikiURLParts[0]; wikiURLParts = this.arrayShift(wikiURLParts);
                            label = this.format(String.join("|", wikiURLParts));
                        }
                    } else {
                        label = this._truncateURLForDisplay(wikiURL);
                        urlStr = wikiURL;
                    }

                    /*
                    if (urlStr.contains("://")) {
                        target = "_blank";
                    }
                    */

                    if ("IMG".equals(type)) {
                        String style = "";
                        if (!this.isBlank(dim)) {
                            String[] dimArray = dim.split("X");
                            String width = dimArray[0];
                            String height = dimArray.length > 1 ? dimArray[1] : null;
                            if (!this.isBlank(width) && !"?".equals(width)) {
                                style += "width:" + width + (this.isNaN(width) ? "" : "px") + ';';
                            }
                            if (!this.isBlank(height) && !"?".equals(height)) {
                                style += "height:" + height + (this.isNaN(height) ? "" : "px") + ';';
                            }
                        }
                        htmlChunk += "<img src=\""+urlStr+"\""+
                            (!this.isBlank(cssClass) ? " class=\""+cssClass+"\"" : "")+
                            (!this.isBlank(style) ? " style=\""+style+"\"" : "")+
                            " alt=\""+label+"\" title=\""+label+"\"/>";
                    } else if ("DOWNLOAD".equals(type)) {
                        // NOTE: A[DOWNLOAD] is a HTML5 attribute. It's ignored if the browser do not support it.
                        //     http://www.w3.org/html/wg/drafts/html/master/links.html#downloading-resources
                        htmlChunk += "<a href=\""+urlStr+"\" download=\""+filename+"\" target=\"_blank\">"+label+"</a>";
                    } else {
                        String target = "";
                        if (urlStr.contains("://")) {
                            target = "_blank";
                        }
                        htmlChunk += "<a href=\""+urlStr+"\""+
                            (!this.isBlank(target) ? " target=\""+target+"\"" : "")+">"+label+"</a>";
                    }
                    i = index;
                    currentChar = i < len ? this.charAt(input, i) : EMPTY_CHAR;
                }
            }

            // ******************************
            // * Complete URLs (http://...) *
            // ******************************
            if (this._isCompleteURL(input, i)) {
                String urlStr = this._toURL(input, i);
                if (!this.isBlank(urlStr)) {
                    String label = this._truncateURLForDisplay(urlStr);
                    htmlChunk += "<a href=\""+urlStr+"\" target=\"_blank\">"+label+"</a>";
                    i += urlStr.length()-1;
                    currentChar = i < len ? this.charAt(input, i) : EMPTY_CHAR;
                }
            }

            // ****************************
            // * Incomplete URLs (www...) *
            // ****************************
            if (this._isIncompleteURL(input, i)) {
                String urlStr = this._toURL(input, i);
                if (!this.isBlank(urlStr)) {
                    String label = this._truncateURLForDisplay(urlStr);
                    htmlChunk += "<a href=\"http://"+urlStr+"\" target=\"_blank\">"+label+"</a>";
                    i += urlStr.length()-1;
                    currentChar = i < len ? this.charAt(input, i) : EMPTY_CHAR;
                }
            }

            // ***********************************
            // * Headers (<h1>, <h2>, ..., <h6>) *
            // ***********************************
            if (currentChar == '=' && (i <= 0 || this.charAt(input, i-1) == '\n')) {
                // Collect open tag ("\n=====")
                int index = i, lineStartIndex = i, lineEndIndex;
                String openTag = "=", closeTag = "";
                while (index+1 < len && this.charAt(input, index+1) == '=') {
                    index++;
                    openTag += "=";
                }

                // Collect close tag ("=====\n")
                while (index+1 < len && this.charAt(input, index+1) != '\n') {
                    index++;
                    if (this.charAt(input, index) == '=') {
                        closeTag += "=";
                    } else {
                        // reset
                        closeTag = "";
                    }
                }
                lineEndIndex = index;

                int headerTagNumber = Math.min(openTag.length(), closeTag.length()) - 1;
                if (headerTagNumber >= 1 && headerTagNumber <= 6) {
                    htmlChunk += "<h"+headerTagNumber+">" +
                            this.format(input.substring(
                                    lineStartIndex + headerTagNumber + 1,
                                    lineEndIndex - headerTagNumber)) +
                            "</h"+headerTagNumber+">\n";

                    // lineEndIndex   => last '='
                    // lineEndIndex+1 => the '\n'
                    i = lineEndIndex+1;
                    currentChar = i < len ? this.charAt(input, i) : EMPTY_CHAR;
                }
            }

            // ***************
            // * Bullet list *
            // ***************
            if (this._isListLine(input, i)) {
                // Collect all lines that define the list
                int index = i;
                boolean inList = true;
                while (inList && index < len) {
                    if (index > 0 && this.charAt(input, index-1) == '\n') {
                        // The cursor is at the beginning of a new line.
                        // It's time to check if the line is still part
                        // of the bullet list.
                        inList = this._isListLine(input, index);
                    }
                    if (inList) {
                        index++;
                    }
                }
                String listBlock = input.substring(i, index);

                htmlChunk += this._createHTMLList(this._createListObjFromWikiFormat(listBlock));
                i = index-1;
                currentChar = i < len ? this.charAt(input, i) : EMPTY_CHAR;
            }

            // *****************
            // * Numbered list *
            // *****************
            if (this._isListLine(input, i, true)) {
                // Collect all lines that define the list
                int index = i;
                boolean inList = true;
                while (inList && index < len) {
                    if (index > 0 && this.charAt(input, index-1) == '\n') {
                        // The cursor is at the beginning of a new line.
                        // It's time to check if the line is still part
                        // of the bullet list.
                        inList = this._isListLine(input, index, true);
                    }
                    if (inList) {
                        index++;
                    }
                }
                String listBlock = input.substring(i, index);

                htmlChunk += this._createHTMLList(this._createListObjFromWikiFormat(listBlock, true), true);
                i = index-1;
                currentChar = i < len ? this.charAt(input, i) : EMPTY_CHAR;
            }

            // Default
            if ("".equals(htmlChunk) && currentChar != EMPTY_CHAR) {
                htmlChunk = "" + currentChar;
            }

            output += htmlChunk;
        }

        return output;
    }

    private boolean _isStyleChar(char car) {
        return car == '*' || car == '/' || car == '_' || car == '-';
    }

    private boolean _isListLine(String input, int index) {
        return this._isListLine(input, index, false);
    }
    private boolean _isListLine(String input, int index, boolean numbered) {
        int len = input.length();
        char bulletChar = numbered ? '#' : '*';
        if (this.charAt(input, index) != bulletChar || (index > 0 && this.charAt(input, index-1) != '\n')) {
            return false;
        }
        while (index < len && this.charAt(input, index) == bulletChar) {
            index++;
        }
        // It is a list only if the next character after the stars is a white space.
        // return /\s/.test(input.charAt(index));
        return Character.isWhitespace(this.charAt(input, index));
    }

    private Tag _getStyleTag(String input, int index) {
        Tag tag = new Tag(
            this.charAt(input, index),
            index
        );

        // Delimiter: Allow character before the style char, to be considered as a style char.
        //     Example: "This is *important* " => "important" is considered as bold because it's surrounded by spaces.
        //         "end of -sentence-." => "sentence" is struck out because has a space before and a period after.
        //         "value1|*value2*" => "value2" is bold because it has a pipe before and a end of string at the end.
        //             The pipe and brackets chars are mostly used to detect style inside element, like in a link label,
        //             to not accidentally consider the label style end with the current style end.
        Pattern styleDelimiterInRegex = Pattern.compile("[\\w:\\.,\\[\\]\\(\\){}]");
        Pattern styleDelimiterOutRegex = Pattern.compile("[^\\w:]");

        // Check if the sequence start with white space
        int len = input.length(), startIndex = index, endIndex = index;
        while (startIndex-1 >= 0 && this._isStyleChar(this.charAt(input, startIndex-1))) {
            startIndex--;
        }
        while (endIndex+1 < len && this._isStyleChar(this.charAt(input, endIndex+1))) {
            endIndex++;
        }

        if ((startIndex-1 >= 0 && styleDelimiterInRegex.matcher(""+this.charAt(input, startIndex-1)).find()) &&
                (endIndex+1 >= len || styleDelimiterOutRegex.matcher(""+this.charAt(input, endIndex+1)).find())) {
            //console.log('        CLOSE');
            tag.open = false;
        } else if ((startIndex-1 < 0 || styleDelimiterOutRegex.matcher(""+this.charAt(input, startIndex-1)).find()) &&
                (endIndex+1 <= len && styleDelimiterInRegex.matcher(""+this.charAt(input, endIndex+1)).find())) {
            //console.log('        OPEN');
            tag.open = true;
        } else {
            //console.log('        REJECTED');
            return null;
        }

        if (tag.type == '*') {
            tag.tag = tag.open ? "<b>" : "</b>";
        } else if (tag.type == '/') {
            tag.tag = tag.open ? "<i>" : "</i>";
        } else if (tag.type == '_') {
            tag.tag = tag.open ? "<u>" : "</u>";
        } else if (tag.type == '-') {
            tag.tag = tag.open ? "<s>" : "</s>";
        } else {
            return null;
        }

        return tag;
    }

    private Tag _findCloseTag(String input, Tag openTag) {
        Tag tag, closeTag;
        char currentChar = EMPTY_CHAR;
        for (int i=openTag.index+1, len=input.length(); i<len; i++) {
            currentChar = this.charAt(input, i);
            if (this._isStyleChar(currentChar)) {
                tag = this._getStyleTag(input, i);
                if (tag != null && Objects.equals(tag.type, openTag.type)) {
                    if (tag.open) {
                        // Find the close tag for the new open tag
                        closeTag = this._findCloseTag(input, tag);
                        if (closeTag == null) {
                            return null; // unbalanced
                        }
                        // Continue to look from close this tag.
                        i = closeTag.index+1;
                    } else {
                        // This is the close tag we were looking for.
                        return tag;
                    }
                }
            }
        }

        return null;
    }

    private List<ListItem> _createListObjFromWikiFormat(String listStr) {
        return this._createListObjFromWikiFormat(listStr, false);
    }
    private List<ListItem> _createListObjFromWikiFormat(String listStr, boolean numbered) {
        char bulletChar = numbered ? '#' : '*';
        String valueRegex = numbered ? "^#+\\s+" : "^\\*+\\s+";

        // Split on '\r' (Mac), '\n' (UNIX) or '\r\n' (Windows)
        String[] bulletListItems = listStr.replaceAll("[\n\r]+", "\n").split("\n");

        List<ListItem> bulletListObj = new ArrayList<ListItem>();
        for (int i=0, len=bulletListItems.length; i<len; i++) {
            String itemStr = bulletListItems[i];

            String value = itemStr.replaceAll(valueRegex, "");
            int index = 0;
            List<ListItem> listPtr = bulletListObj;
            while (this.charAt(itemStr, index) == bulletChar) {
                index++;
                if (listPtr.size() == 0) {
                    listPtr.add(new ListItem());
                }
                listPtr = listPtr.get(listPtr.size()-1).children;
            }
            listPtr.add(new ListItem(value));
        }

        // Get ride of the root
        return bulletListObj.get(0).children;
    }

    private String _createHTMLList(List<ListItem> list) {
        return this._createHTMLList(list, false);
    }
    private String _createHTMLList(List<ListItem> list, boolean numbered) {
        String listTagName = numbered ? "ol" : "ul";

        String htmlList = "<"+listTagName+">\n";
        for (int i=0, len=list.size(); i<len; i++) {
            ListItem listItem = list.get(i);
            htmlList += "<li>" + this.format(listItem.value);
            if (!listItem.children.isEmpty()) {
                htmlList += '\n' + this._createHTMLList(listItem.children, numbered);
            }
            htmlList += "</li>\n";
        }
        htmlList += "</"+listTagName+">\n";

        return htmlList;
    }

    private String _truncateURLForDisplay(String url) {
        int maxUrlLength = MAX_URL_LENGTH;

        if (maxUrlLength == 1) {
            return ".";
        }

        if (maxUrlLength == 2) {
            return "..";
        }

        if (maxUrlLength > 0 && maxUrlLength < url.length()) {
            int beginningLength = (int)Math.round((maxUrlLength-3) * 3.0/4);
            int endingLength = maxUrlLength - beginningLength - 3; // 3 is for the "..."
            if (beginningLength > 1 && endingLength == 0) {
                beginningLength--;
                endingLength = 1;
            }
            return url.substring(0, beginningLength) + "..." + url.substring(url.length() - endingLength);
        }

        return url;
    }

    private boolean _isCompleteURL(String input, int i) {
        //var inputChunk = input.substr(i, 10);
        //return /^(sftp|ftp|http|https|file):\/\//.test(inputChunk);
        return input.startsWith("sftp://", i) ||
            input.startsWith("ftp://", i) ||
            input.startsWith("http://", i) ||
            input.startsWith("https://", i) ||
            input.startsWith("file://", i);
    }

    private boolean _isIncompleteURL(String input, int i) {
        //var inputChunk = input.substr(i, 5);
        //return /^www\.\S/.test(inputChunk);
        return input.startsWith("www.", i);
    }

    private String _toURL(String input, int i) {
        Pattern urlCharRegex = Pattern.compile("[a-zA-Z0-9\\$\\-_\\.\\+\\!\\*'\\(\\),;\\/\\?:@=&#%]");
        Pattern urlEndingCharRegex = Pattern.compile("[a-zA-Z0-9\\/]");
        int len = input.length(), index = i;

        while (index < len && urlCharRegex.matcher(""+this.charAt(input, index)).find()) {
            index++;
        }
        index--;
        while (index >= 0 && !urlEndingCharRegex.matcher(""+this.charAt(input, index)).find()) {
            index--;
        }

        return input.substring(i, index+1);
    }

    /**
     * Attempt at replicated JavaScript String.charAt()
     * JavaScript return an empty character when the index is out of bounds.
     * Java crash with an "StringIndexOutOfBoundsException" exception.
     */
    protected char charAt(String str, int index) {
        if (index < 0 || index >= str.length()) {
            return EMPTY_CHAR;
        }
        return str.charAt(index);
    }

    /**
     * Attempt at replicated JavaScript array.shift()
     * Usage:
     *     String element = array[0]; array = this.arrayShift(array);
     */
    protected String[] arrayShift(String[] array) {
        if (array.length <= 0) {
            return null;
        }

        return Arrays.copyOfRange(array, 1, array.length);
    }

    /**
     * Attempt at replicated JavaScript array.pop()
     * Usage:
     *     String element = array[array.length-1]; array = this.arrayPop(array);
     */
    protected String[] arrayPop(String[] array) {
        if (array.length <= 0) {
            return null;
        }

        return Arrays.copyOfRange(array, 0, array.length - 1);
    }

    protected boolean isBlank(String str) {
        return (str == null || str.isEmpty());
    }

    protected boolean isNaN(String str) {
        try {
            double value = Double.parseDouble(str);
            return Double.isNaN(value);
        } catch(Exception ex) {
            return true;
        }
    }

    private static class Tag {
        public char type;
        public int index;
        public boolean open;
        public String tag;

        public Tag(char type, int index) {
            this.type = type;
            this.index = index;
        }
    }

    private static class ListItem {
        public String value;
        public List<ListItem> children;

        public ListItem() {
            this.children = new ArrayList<ListItem>();
        }
        public ListItem(String value) {
            this();
            this.value = value;
        }
    }
}
