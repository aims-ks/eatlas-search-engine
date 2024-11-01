/*
 *  Copyright (C) 2022 Australian Institute of Marine Science
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

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.logger.Level;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class User {
    private static final int RANDOM_SALT_LENGTH = 6;

    private String username;
    private String encryptedPassword;
    private String salt;

    private String firstName;
    private String lastName;
    private String email;

    private boolean modified = false;

    private User() {}

    public User(JSONObject json, AbstractLogger logger) {
        this.loadJSON(json, logger);
    }

    public User(String username, String salt, String encryptedPassword) {
        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.setSalt(salt);
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password, AbstractLogger logger) {
        this.encryptedPassword = User.encrypt(this.salt, password, logger);
    }

    public String getSalt() {
        return this.salt;
    }

    public void setSalt(String salt) {
        if (salt == null) {
            this.salt = SearchEngineConfig.generateRandomToken(RANDOM_SALT_LENGTH);
            this.modified = true;
        } else {
            this.salt = salt;
        }
    }

    public String getFirstName() {
        return this.firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return this.lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isModified() {
        return this.modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public String display() {
        List<String> displayUser = new ArrayList<>();
        if (this.firstName != null && !this.firstName.isEmpty()) {
            displayUser.add(this.firstName);
        }
        if (this.lastName != null && !this.lastName.isEmpty()) {
            displayUser.add(this.lastName);
        }

        if (displayUser.isEmpty()) {
            if (this.username != null && !this.username.isEmpty()) {
                displayUser.add(this.username);
            } else {
                // This should not happen
                displayUser.add("Admin");
            }
        }

        return String.join(" ", displayUser);
    }

    public boolean validate() {
        if (this.username == null || this.username.isEmpty()) {
            return false;
        }
        return true;
    }

    public JSONObject toJSON() {
        return new JSONObject()
                .put("username", this.username)
                .put("encryptedPassword", this.encryptedPassword)
                .put("salt", this.salt)

                .put("firstName", this.firstName)
                .put("lastName", this.lastName)
                .put("email", this.email);
    }

    public void loadJSON(JSONObject json, AbstractLogger logger) {
        String newPassword = null;
        if (json == null || json.isEmpty()) {
            // Default admin user
            this.username = "admin";
            newPassword = "admin";
            this.setSalt(null); // Random salt

            // Let the user set firstName, lastName, etc.
            logger.addMessage(Level.WARNING, "New admin user created. " +
                    "Click the admin user on the top right corner of the page and change the admin password.");
        } else {
            this.username = json.optString("username", null);
            this.encryptedPassword = json.optString("encryptedPassword", null);
            this.setSalt(json.optString("salt", null));

            this.firstName = json.optString("firstName", null);
            this.lastName = json.optString("lastName", null);
            this.email = json.optString("email", null);
            newPassword = json.optString("password", null);
        }

        // Change password
        // The admin password can be changed by removing the "encryptedPassword" from the config file
        // and replacing with a clear text "password". On next reload of the config, the system
        // replace the clear text "password" with an encrypted password.
        if (this.encryptedPassword == null && newPassword != null) {
            this.encryptedPassword = User.encrypt(this.salt, newPassword, logger);
            this.modified = true;
        }
    }

    public boolean verifyPassword(String password, AbstractLogger logger) {
        if (this.encryptedPassword == null) {
            return password == null;
        }

        return this.encryptedPassword.equals(
                User.encrypt(this.salt, password, logger));
    }

    /**
     * Return a Base64 encoding of the MD5 of the password.
     * @param pass Unencrypted password
     * @return Encrypted password (Base 64 or MD5).
     */
    public static String encrypt(String salt, String pass, AbstractLogger logger) {
        try {
            byte[] encryptPass = md5sum(salt + pass);
            return toHex(encryptPass);
        } catch (NoSuchAlgorithmException ex) {
            logger.addMessage(Level.ERROR, "Can not encrypt the password.", ex);
        }

        // Return un-encrypted password. Unlikely to append
        return pass;
    }

    public static byte[] md5sum(String data) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("MD5").digest(data.getBytes());
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte abyte : bytes) {
            sb.append(String.format("%02X", abyte));
        }
        return sb.toString();
    }
}
