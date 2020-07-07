/*
 * Copyright 2013-2015 Saint Louis University. Licensed under the
 *	Educational Community License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package edu.slu.tradamus.user;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.xml.bind.DatatypeConverter;
import javax.xml.stream.XMLStreamException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.util.LangUtils;
import static edu.slu.tradamus.util.ServletUtils.*;
import edu.slu.tradamus.witness.Witness;


/**
 * Represents a single user of Tradamus.
 *
 * @author tarkvara
 */
public class User extends Entity {
   private String mail;
   private String name;
   private byte[] hash;
   private boolean disabled;
   private Date creation;
   private Date lastLogin;

   /** Used when inviting new users. */
   private int edition = -1;
   
   /** Used when inviting new reviewers. */
   private int publication = -1;

   /**
    * Constructor used by JSON deserialisation.
    */
   public User() {
   }

   /**
    * Wrap a bare-bones user around a known user ID.
    */
   public User(int uID) {
      id = uID;
   }

   public String getMail() {
      return mail;
   }

   public void setMail(String m) {
      mail = m;
   }

   public String getName() {
      return name;
   }

   public void setName(String n) {
      name = n;
   }

   public boolean isDisabled() {
      return disabled;
   }

   public Date getCreation() {
      return creation;
   }
   
   public Date getLastLogin() {
      return lastLogin;
   }

   public void setLastLogin(Date log) {
      lastLogin = log;
   }

   public boolean hasPassword() {
      return hash != null;
   }

   public void setPassword(String pw) throws NoSuchAlgorithmException, UnsupportedEncodingException {
      hash = hashPassword(pw);
   }

   @JsonIgnore
   public int getEdition() {
      return edition;
   }

   @JsonProperty("edition")
   public void setEdition(int edID) {
      edition = edID;
   }

   @JsonIgnore
   public int getPublication() {
      return publication;
   }

   @JsonProperty("publication")
   public void setPublication(int pubID) {
      publication = pubID;
   }

   @Override
   public void insert(Connection conn) throws SQLException {
      executeInsert(conn, "INSERT INTO `users` (" +
            "`mail`, `name`, `hash`, `last_login`" +
            ") VALUES(?, ?, ?, ?)",
            mail, name, DatatypeConverter.printBase64Binary(hash), lastLogin);
   }

   @Override
   public Object modify(Connection conn, Map<String, Object> values) throws SQLException, ReflectiveOperationException, IOException, PermissionException {
      try {
         if (values.containsKey("password")) {
            values.put("hash", DatatypeConverter.printBase64Binary(hashPassword((String)values.remove("password"))));
         }
         super.modify(conn, values);
      } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
      }
      return null;
   }

   @Override
   public void loadFields(ResultSet rs, boolean deep) throws SQLException {
      mail = rs.getString("mail");
      name = rs.getString("name");
      disabled = rs.getBoolean("disabled");
      creation = rs.getTimestamp("creation");
      lastLogin = rs.getTimestamp("last_login");
      hash = DatatypeConverter.parseBase64Binary(rs.getString("hash"));
   }

   /**
    * Merging users isn't something meaningful.  Could it be someday?
    * @param conn
    * @param newEnt 
    */
   @Override
   public void merge(Connection conn, Entity newEnt) {
      throw new UnsupportedOperationException("User.merge not supported.");
   }

   @Override
   public Role checkPermission(Connection conn, int uID, Role required) throws SQLException, PermissionException {
      if (required.ordinal() >= Role.EDITOR.ordinal()) {
         if (id != uID) {
            throw new PermissionException(this, required);
         }
      }
      return required;
   }

   /**
    * Does nothing, since users don't belong to any edition.
    * @param conn ignored
    */
   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
   }

   /**
    * Set the pending field for this user.
    * @param conn connection to MySQL database
    * @param pending are we marking or clearing the pending field?
    * @return the confirmation key (possibly null)
    * @throws SQLException
    * @throws NoSuchAlgorithmException
    * @throws UnsupportedEncodingException 
    */
   public String markPending(Connection conn, boolean pending) throws SQLException, NoSuchAlgorithmException, UnsupportedEncodingException {
      String confirmationKey = null;
      if (pending) {
         confirmationKey = DatatypeConverter.printBase64Binary(hashPassword(name + new Date()));
      }
      try (PreparedStatement stmt = conn.prepareStatement("UPDATE `users` SET `pending` = ? WHERE `id` = ?")) {
         stmt.setString(1, confirmationKey);
         stmt.setInt(2, id);
         stmt.executeUpdate();
      }
      return confirmationKey;
   }

   /**
    * Validate the user to determine whether the user ID and password match one in the database.
    * Will set the User's id and lastLogin fields if found.
    *
    * @return <code>true</code> if the user ID and password are found
    */
   public Status validate(Connection conn) throws SQLException {
      Status stat = Status.INVALID;
      try (PreparedStatement stmt = conn.prepareStatement("SELECT id, disabled, pending, last_login from `users` WHERE mail = ? AND hash = ?")) {
         stmt.setString(1, mail);
         stmt.setString(2, DatatypeConverter.printBase64Binary(hash));
         ResultSet rs = stmt.executeQuery();
         if (rs.next()) {
            id = rs.getInt(1);
            if (rs.getBoolean(2)) {
               stat = Status.DISABLED;
            } else {
               stat = rs.getString(3) == null ? Status.VALID : Status.CONFIRMATION_PENDING;
               lastLogin = rs.getTimestamp(4);
            }
         }
      }
      return stat;
   }

   /**
    * Records the current time as the user's last login and checks to see if any linked T-PEN projects
    * need to be updated.
    *
    * @param conn connection to database
    */
   public void login(Connection conn) throws SQLException, ReflectiveOperationException, IOException, ServletException, PermissionException, XMLStreamException {
      try {
         Witness.synchAllWithTpen(conn, id);
      } catch (Throwable t) {
         // If something goes wrong with the T-PEN synch, we don't want it to trash the login.
         LOG.log(Level.WARNING, "T-PEN synch failed.", t);
      }
      try (PreparedStatement updateStmt = conn.prepareStatement("UPDATE `users` SET last_login = NOW() WHERE id = ?")) {
         updateStmt.setInt(1, id);
         updateStmt.executeUpdate();
      }
   }

   public static User findConfirmation(Connection conn, String mail, String confKey) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement("SELECT * from `users` WHERE mail = ? AND pending = ?")) {
         stmt.setString(1, mail);
         stmt.setString(2, confKey);
         ResultSet rs = stmt.executeQuery();
         if (rs.next()) {
            User u = new User(rs.getInt("id"));
            u.loadFields(rs, false);
            return u;
         }
      }
      return null;
   }

   public static User findByEmail(String mail) throws ServletException, SQLException {
      try (Connection conn = getDBConnection()) {
         try (PreparedStatement stmt = conn.prepareStatement("SELECT * from `users` WHERE mail = ?")) {
            stmt.setString(1, mail);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
               User u = new User(rs.getInt("id"));
               u.loadFields(rs, false);
               return u;
            } else {
               throw new NoSuchEntityException(mail);
            }
         }
      }
   }
 
   public enum Status {
      VALID,
      INVALID,
      CONFIRMATION_PENDING,
      DISABLED
   }
   /**
    * Number of times to hash.  More iterations makes it harder.
    */
   private static final int HASH_ITERATIONS = 3;

   /**
    * Salt used for hash algorithm.
    */
   private static byte[] SALT;

   // We avoid 0, 1, O, and l for clarity's sake.
   private static final String PASSWORD_CHARS = "23456789ABCDEFGHIJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
   private static final int PASSWORD_LENGTH = 8;
   
   private static final Logger LOG = Logger.getLogger(User.class.getName());

   static {
      SALT = "In nomine Patris et Filii et Spiritus Sancti.".getBytes(LangUtils.UTF8);
   }

   /**
    * Create the SHA-256 hash for the given password.
    * @param pw password to be hashed
    * @return the SHA-256 hash of <code>pw</code>
    */
   private static byte[] hashPassword(String pw) throws NoSuchAlgorithmException, UnsupportedEncodingException {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.reset();
      digest.update(SALT);

      byte[] pwBytes = digest.digest(pw.getBytes(LangUtils.UTF8));
      for (int i = 0; i < HASH_ITERATIONS; i++) {
         digest.reset();
         pwBytes = digest.digest(pwBytes);
      }
      return pwBytes;
   }

   /**
    * Generate a random-looking name for anonymous users.
    * @return a random-looking user name
    */
   public static String randomName() {
      return Long.toString(System.currentTimeMillis(), 36);
   }

   public static String randomPassword() {
      Random random = new Random(System.currentTimeMillis());
      char[] passwordChars = new char[PASSWORD_LENGTH];
      for (int i = 0; i < PASSWORD_LENGTH; i++) {
         passwordChars[i] = PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length()));
      }
      return new String(passwordChars);
   }
}
