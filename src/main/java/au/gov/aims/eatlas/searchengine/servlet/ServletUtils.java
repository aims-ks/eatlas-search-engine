/*
 *  This file is part of AtlasMapper server and clients.
 *
 *  Copyright (C) 2011 Australian Institute of Marine Science
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

package au.gov.aims.eatlas.searchengine.servlet;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.xml.sax.SAXParseException;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This utility class provide tools to simplify some operation related
 * to servlets. Unfortunately, this class can not be test since the servlet
 * libraries can not be load in the context of a Test Case.
 *
 * @author glafond
 */
public class ServletUtils {
	private static final Logger LOGGER = Logger.getLogger(ServletUtils.class.getName());

	public static void sendResponse(
			HttpServletResponse response,
			File file) throws IOException {

		if (response == null || file == null) {
			return;
		}

		InputStream responseStream = null;
		try {
			responseStream = new FileInputStream(file);
			ServletUtils.sendResponse(response, responseStream);
		} finally {
			if (responseStream != null) {
				try {
					responseStream.close();
				} catch (Exception ex) {
					LOGGER.log(Level.WARN, String.format("Cant close the FileInputStream: %s", ServletUtils.getExceptionMessage(ex)), ex);
				}
			}
		}
	}

	public static void sendResponse(
			HttpServletResponse response,
			String responseTxt) throws IOException {

		if (response == null || responseTxt == null) {
			return;
		}

		InputStream responseStream = null;
		try {
			responseStream = new ByteArrayInputStream(responseTxt.getBytes());
			ServletUtils.sendResponse(response, responseStream);
		} finally {
			if (responseStream != null) {
				try {
					responseStream.close();
				} catch (Exception ex) {
					LOGGER.log(Level.WARN, String.format("Cant close the ByteArrayInputStream: %s", ServletUtils.getExceptionMessage(ex)), ex);
				}
			}
		}
	}

	public static void sendResponse(
			HttpServletResponse response,
			InputStream responseStream) throws IOException {

		if (response == null || responseStream == null) {
			return;
		}

		OutputStream out = null;

		try {
			out = response.getOutputStream();

			ServletUtils.binaryCopy(responseStream, out);
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch(Exception e) {
					LOGGER.log(Level.ERROR, String.format("Cant close the output: %s", ServletUtils.getExceptionMessage(e)), e);
				}
			}
		}
	}

    public static String getExceptionMessage(Throwable ex) {
        String defaultMsg = ex == null ? "No message available" : ex.getClass().getName();
        return getExceptionMessage(ex, defaultMsg);
    }
    public static String getExceptionMessage(Throwable ex, String defaultMsg) {
        String msg = null;
        if (ex != null) {
            msg = ex.getMessage();

            // SAXParseException has handy values that do not shows on getMessage.
            if (ex instanceof SAXParseException) {
                SAXParseException saxEx = (SAXParseException)ex;
                if (ServletUtils.isBlank(msg)) {
                    // That should not happen
                    msg = "Can not parse the XML document.";
                }
                msg += "\nline: " + saxEx.getLineNumber() + ", character: " + saxEx.getColumnNumber();
            }

            if (ServletUtils.isBlank(msg)) {
                msg = getExceptionMessage(ex.getCause(), defaultMsg);
            }
        }
        if (ServletUtils.isBlank(msg)) {
            msg = defaultMsg;
        }
        return msg;
    }

    public static boolean isBlank(String str) {
        return str==null || str.trim().isEmpty();
    }
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static void binaryCopy(InputStream in, OutputStream out) throws IOException {
        binaryCopy(in, out, -1);
    }

    public static void binaryCopy(InputStream in, OutputStream out, long maxBytesFileSize) throws IOException {
        if (in == null || out == null) {
            return;
        }

        long totalBytesRead = 0;

        try {
            byte[] buf = new byte[32 * 1024];  // 32K buffer
            int bytesRead;
            while ((bytesRead = in.read(buf)) != -1) {
                if (maxBytesFileSize >= 0) {
                    totalBytesRead += bytesRead;
                    if (totalBytesRead > maxBytesFileSize) {
                        throw new IOException(String.format(
                            "File size exceeded. The maximum size allowed is %d bytes.", maxBytesFileSize));
                    }
                }
                out.write(buf, 0, bytesRead);
            }
        } finally {
            try {
                out.flush();
            } catch (Exception ex) {
                LOGGER.log(Level.ERROR, String.format("Cant flush the output: %s", ServletUtils.getExceptionMessage(ex)), ex);
            }
        }
    }
}
