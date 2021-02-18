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

/**
 * Parse the "Wiki" text format used in GeoNetwork
 * and by AtlasMapper.
 */
public class WikiFormatter {
    private static final WikiFormatter instance;
    static {
        instance = new WikiFormatter();
    }

    private WikiFormatter() {}

    private static WikiFormatter getInstance() {
        return instance;
    }

    public static String getText(String input) {
        if (input == null) {
            return null;
        }

        String html = WikiFormatter.getInstance().format(input);

        if (html.isEmpty()) {
            return null;
        }

        // Extract text from HTML
    }

    // Inspired from AtlasMapper:
    //     atlasmapper/clientResources/amc/modules/Utils/WikiFormater.js
    private String format(String input) {
        if (input == null) {
            return null;
        }

        input = input.trim();
        if (input.isEmpty()) {
            return input;
        }

        // Normalize input, to deal only with "\n" for new lines.
        input = input.replace("\r\n", "\n").replace("\r", "\n");

        String output = "", htmlChunk = "";
        char currentChar = '\0';

        for (int i=0, len=input.length(); i<len; i++) {
            currentChar = input.charAt(i);
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
                        currentChar = i < len ? input.charAt(i) : '\0';
                    }
                }
            }

            // *************
            // * Wiki URLs *
            // *************
            if (currentChar === '[' && i+1 < len && input.charAt(i+1) === '[') {
                var index = i, inURL = true, isURL = false;
                // Find the index of the last URL chars
                // NOTE: URLs can not contains URL, so the first closing tag is for the current URL.
                while (inURL && index < len) {
                    // '\n' is not allow in Wiki URL
                    if (input.charAt(index) === '\n') {
                        inURL = false;
                    }
                    if (input.charAt(index+1) !== ']' && input.charAt(index) === ']' && input.charAt(index-1) === ']') {
                        inURL = false;
                        isURL = true;
                    }
                    if (inURL) {
                        index++;
                    }
                }
                if (isURL) {
                    var wikiURL = input.substring(i+2, index-1);
                    var urlStr, filename, label, dim, cssClass, wikiURLParts = wikiURL.split('|');

                    var type = 'URL';
                    if (wikiURLParts.length >= 2) {
                        if (wikiURLParts[0] === 'IMG') {
                            // Remove IMG
                            type = wikiURLParts.shift();
                            urlStr = wikiURLParts.shift();
                            if (wikiURLParts.length > 1) {
                                dim = wikiURLParts.pop();
                            }
                            if (wikiURLParts.length > 1) {
                                cssClass = dim;
                                dim = wikiURLParts.pop();
                            }
                            label = wikiURLParts.join('|');
                        } else if (wikiURLParts[0] === 'DOWNLOAD') {
                            // Remove DOWNLOAD
                            type = wikiURLParts.shift();
                            if (wikiURLParts.length >= 2) {
                                urlStr = wikiURLParts.shift();
                                filename = wikiURLParts.shift();
                                label = this.format(wikiURLParts.join('|'));
                                if (!label) {
                                    label = filename;
                                }
                            } else {
                                urlStr = wikiURLParts[0];
                                filename = urlStr;

                                var lastSlashIndex = filename.lastIndexOf('/');
                                var questionMarkIndex = filename.indexOf('?');
                                if (questionMarkIndex < 0) { questionMarkIndex = filename.length; }

                                if (lastSlashIndex+1 < questionMarkIndex) {
                                    filename = filename.substring(lastSlashIndex+1, questionMarkIndex);
                                }
                                label = filename;
                            }
                        } else {
                            urlStr = wikiURLParts.shift();
                            label = this.format(wikiURLParts.join('|'));
                        }
                    } else {
                        label = this._truncateURLForDisplay(wikiURL);
                        urlStr = wikiURL;
                    }

                    if (urlStr.indexOf('://') !== -1) {
                        target = '_blank';
                    }

                    if (type === 'IMG') {
                        var style = '';
                        if (dim) {
                            dim = dim.split('X');
                            var width = dim[0];
                            var height = dim.length > 1 ? dim[1] : null;
                            if (width && width != '?') {
                                style += 'width:' + width + (isNaN(width) ? '' : 'px') + ';';
                            }
                            if (height && height != '?') {
                                style += 'height:' + height + (isNaN(height) ? '' : 'px') + ';';
                            }
                        }
                        htmlChunk += '<img src="'+urlStr+'"'+(cssClass ? ' class="'+cssClass+'"' : '')+(style ? ' style="'+style+'"' : '')+' alt="'+label+'" title="'+label+'"/>';
                    } else if (type === 'DOWNLOAD') {
                        // NOTE: A[DOWNLOAD] is a HTML5 attribute. It's ignored if the browser do not support it.
                        //     http://www.w3.org/html/wg/drafts/html/master/links.html#downloading-resources
                        htmlChunk += '<a href="'+urlStr+'" download="'+filename+'" target="_blank">'+label+'</a>';
                    } else {
                        var target = '';
                        if (urlStr.indexOf('://') !== -1) {
                            target = '_blank';
                        }
                        htmlChunk += '<a href="'+urlStr+'"'+(target ? ' target="'+target+'"' : '')+'>'+label+'</a>';
                    }
                    i = index;
                    currentChar = i < len ? input.charAt(i) : '';
                }
            }

            // ******************************
            // * Complete URLs (http://...) *
            // ******************************
            if (this._isCompleteURL(input, i)) {
                var urlStr = this._toURL(input, i);
                if (urlStr) {
                    var label = this._truncateURLForDisplay(urlStr);
                    htmlChunk += '<a href="'+urlStr+'" target="_blank">'+label+'</a>';
                    i += urlStr.length-1;
                    currentChar = i < len ? input.charAt(i) : '';
                }
            }

            // ****************************
            // * Incomplete URLs (www...) *
            // ****************************
            if (this._isIncompleteURL(input, i)) {
                var urlStr = this._toURL(input, i);
                if (urlStr) {
                    var label = this._truncateURLForDisplay(urlStr);
                    htmlChunk += '<a href="http://'+urlStr+'" target="_blank">'+label+'</a>';
                    i += urlStr.length-1;
                    currentChar = i < len ? input.charAt(i) : '';
                }
            }

            // ***********************************
            // * Headers (<h1>, <h2>, ..., <h6>) *
            // ***********************************
            if (currentChar === '=' && (i <= 0 || input.charAt(i-1) === '\n')) {
                // Collect open tag ("\n=====")
                var index = i, openTag = '=', closeTag = '',
                    lineStartIndex = i, lineEndIndex;
                while (index+1 < len && input.charAt(index+1) === '=') {
                    index++;
                    openTag += '=';
                }

                // Collect close tag ("=====\n")
                while (index+1 < len && input.charAt(index+1) !== '\n') {
                    index++;
                    if (input.charAt(index) === '=') {
                        closeTag += '=';
                    } else {
                        // reset
                        closeTag = '';
                    }
                }
                lineEndIndex = index;

                var headerTagNumber = Math.min(openTag.length, closeTag.length) - 1;
                if (headerTagNumber >= 1 && headerTagNumber <= 6) {
                    htmlChunk += '<h'+headerTagNumber+'>' +
                            this.format(input.substring(
                                    lineStartIndex + headerTagNumber + 1,
                                    lineEndIndex - headerTagNumber)) +
                            '</h'+headerTagNumber+'>\n';

                    // lineEndIndex   => last '='
                    // lineEndIndex+1 => the '\n'
                    i = lineEndIndex+1;
                    currentChar = i < len ? input.charAt(i) : '';
                }
            }

            // ***************
            // * Bullet list *
            // ***************
            if (this._isListLine(input, i)) {
                // Collect all lines that define the list
                var index = i, inList = true;
                while (inList && index < len) {
                    if (index > 0 && input.charAt(index-1) === '\n') {
                        // The cursor is at the beginning of a new line.
                        // It's time to check if the line is still part
                        // of the bullet list.
                        inList = this._isListLine(input, index);
                    }
                    if (inList) {
                        index++;
                    }
                }
                var listBlock = input.substring(i, index);

                htmlChunk += this._createHTMLList(this._createListObjFromWikiFormat(listBlock));
                i = index-1;
                currentChar = i < len ? input.charAt(i) : '';
            }

            // *****************
            // * Numbered list *
            // *****************
            if (this._isListLine(input, i, true)) {
                // Collect all lines that define the list
                var index = i, inList = true;
                while (inList && index < len) {
                    if (index > 0 && input.charAt(index-1) === '\n') {
                        // The cursor is at the beginning of a new line.
                        // It's time to check if the line is still part
                        // of the bullet list.
                        inList = this._isListLine(input, index, true);
                    }
                    if (inList) {
                        index++;
                    }
                }
                var listBlock = input.substring(i, index);

                htmlChunk += this._createHTMLList(this._createListObjFromWikiFormat(listBlock, true), true);
                i = index-1;
                currentChar = i < len ? input.charAt(i) : '';
            }

            // Default
            if (htmlChunk == '') {
                htmlChunk = currentChar;
            }

            output += htmlChunk;
        }

        return output;
    }

    private boolean _isStyleChar(char car) {
        return car == '*' || car == '/' || car == '_' || car == '-';
    }

    private boolean _isListLine(input, index, numbered) {
        var len = input.length, bulletChar = numbered ? '#' : '*';
        if (input.charAt(index) !== bulletChar || (index > 0 && input.charAt(index-1) !== '\n')) {
            return false;
        }
        while (index < len && input.charAt(index) === bulletChar) {
            index++;
        }
        // It is a list only if the next character after the stars is a white space.
        return /\s/.test(input.charAt(index));
    }

    private Tag _getStyleTag(String input, int index) {
        Tag tag = new Tag(
            input.charAt(index),
            index
        );

        // Delimiter: Allow caracter before the style char, to be considered as a style char.
        //     Example: "This is *important* " => "important" is considered as bold because it's surrounded by spaces.
        //         "end of -sentence-." => "sentence" is striked out because has a space before and a period after.
        //         "value1|*value2*" => "value2" is bold because it has a pipe before and a end of string at the end.
        //             The pipe and brakets chars are mostly used to detect style inside element, like in a link label,
        //             to not accidently consider the label style end with the current style end.
        var styleDelimiterInRegex = /[\w:\.,\[\]\(\){}]/;
        var styleDelimiterOutRegex = /[^\w:]/;

        // Check if the sequence start with white space
        int len = input.length(), startIndex = index, endIndex = index;
        while (startIndex-1 >= 0 && this._isStyleChar(input.charAt(startIndex-1))) {
            startIndex--;
        }
        while (endIndex+1 < len && this._isStyleChar(input.charAt(endIndex+1))) {
            endIndex++;
        }

        if ((startIndex-1 >= 0 && styleDelimiterInRegex.test(input.charAt(startIndex-1))) &&
                (endIndex+1 >= len || styleDelimiterOutRegex.test(input.charAt(endIndex+1)))) {
            //console.log('        CLOSE');
            tag.open = false;
        } else if ((startIndex-1 < 0 || styleDelimiterOutRegex.test(input.charAt(startIndex-1))) &&
                (endIndex+1 <= len && styleDelimiterInRegex.test(input.charAt(endIndex+1)))) {
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
        var tag, closeTag, currentChar = '';
        for (var i=openTag.index+1, len=input.length; i<len; i++) {
            currentChar = input.charAt(i);
            if (this._isStyleChar(currentChar)) {
                tag = this._getStyleTag(input, i);
                if (tag && tag.type === openTag.type) {
                    if (tag.open) {
                        // Find the close tag for the new open tag
                        closeTag = this._findCloseTag(input, tag);
                        if (!closeTag) {
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
    }

    private String _createListObjFromWikiFormat(String listStr, boolean numbered) {
        var bulletChar = numbered ? '#' : '*';
        var valueRegex = numbered ? /^#+\s+/ : /^\*+\s+/;

        // Split on '\r' (Mac), '\n' (UNIX) or '\r\n' (Windows)
        var bulletListItems = listStr.replace(/[\n\r]+/g, '\n').split('\n');

        var bulletListObj = [];
        for (var i=0, len=bulletListItems.length; i<len; i++) {
            var itemStr = bulletListItems[i];

            var value = itemStr.replace(valueRegex, '');
            var index = 0;
            var listPtr = bulletListObj;
            while (itemStr.charAt(index) === bulletChar) {
                index++;
                if (listPtr.length === 0) {
                    listPtr.push({
                        children: []
                    });
                }
                listPtr = listPtr[listPtr.length-1].children;
            }
            listPtr.push({
                value: value,
                children: []
            });
        }

        // Get ride of the root
        return bulletListObj[0].children;
    }

    private String _createHTMLList(list, boolean numbered) {
        var listTagName = numbered ? 'ol' : 'ul';

        var htmlList = '<'+listTagName+'>\n';
        for (var i=0, len=list.length; i<len; i++) {
            htmlList += '<li>' + this.format(list[i].value);
            if (list[i].children && list[i].children.length > 0) {
                htmlList += '\n' + this._createHTMLList(list[i].children, numbered);
            }
            htmlList += '</li>\n';
        }
        htmlList += '</'+listTagName+'>\n';

        return htmlList;
    }

    private String _truncateURLForDisplay(String url) {
        var maxUrlLength = this.MAX_URL_LENGTH || 40;

        if (maxUrlLength == 1) {
            return ".";
        }

        if (maxUrlLength == 2) {
            return "..";
        }

        if (maxUrlLength > 0 && maxUrlLength < url.length) {
            var beginningLength = Math.round((maxUrlLength-3) * 3.0/4);
            var endingLength = maxUrlLength - beginningLength - 3; // 3 is for the "..."
            if (beginningLength > 1 && endingLength == 0) {
                beginningLength--;
                endingLength = 1;
            }
            return url.substring(0, beginningLength) + "..." + url.substring(url.length - endingLength);
        }

        return url;
    }

    private boolean _isCompleteURL(String input, int i) {
        var inputChunk = input.substr(i, 10);
        return /^(sftp|ftp|http|https|file):\/\//.test(inputChunk);
    }

    private boolean _isIncompleteURL(String input, int i) {
        var inputChunk = input.substr(i, 5);
        return /^www\.\S/.test(inputChunk);
    }

    private String _toURL(String input, int i) {
        var urlCharRegex = /[a-zA-Z0-9\$\-_\.\+\!\*'\(\),;\/\?:@=&#%]/,
            urlEndingCharRegex = /[a-zA-Z0-9\/]/,
            len = input.length, index = i;

        while (index < len && urlCharRegex.test(input.charAt(index))) {
            index++;
        }
        index--;
        while (index >= 0 && !urlEndingCharRegex.test(input.charAt(index))) {
            index--;
        }

        return input.substring(i, index+1);
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
}
