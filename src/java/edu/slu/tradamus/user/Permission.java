/*
 * Copyright 2013-2014 Saint Louis University. Licensed under the
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.edition.Edition;
import edu.slu.tradamus.image.Manifest;
import edu.slu.tradamus.publication.Publication;
import edu.slu.tradamus.text.Transcription;


/**
 * Object which represents the permissions for a single user against a particular edition.
 *
 * @author tarkvara
 */
public class Permission extends Entity {
   
   private Entity target;
   private User user;
   private Role role;

   /**
    * Wrap a permission object around an edition/user combo.
    *
    * @param edID edition we're interested in
    * @param uID user whose permissions we're interested in
    */
   public Permission(Entity targ, int uID, Role r) {
      target = targ;
      user = new User(uID);
      role = r;
   }

   /**
    * Constructor for instantiating from JSON.
    */
   public Permission() {
   }

   /**
    * Wrap a bare-bones permission object around an ID.
    *
    * @param permID permission ID
    */
   public Permission(int permID) {
      id = permID;
   }

   @JsonIgnore
   public void setTarget(Entity targ) {
      target = targ;
   }

   @JsonProperty("target")
   public String getTargetURI() {
      return target.getUniqueID();
   }

   @JsonProperty("target")
   public void setTargetURI(String targ) throws NoSuchEntityException {
      String[] pieces = targ.split("/");
      String lastPiece = pieces[pieces.length - 1];
      if (target == null || !target.getUniqueID().equals(targ)) {
         if (pieces.length >= 2) {
            int targID = Integer.parseInt(lastPiece);
            switch (pieces[pieces.length - 2]) {
               case "edition":
                  target = new Edition(targID);
                  break;
               case "manifest":
                  target = new Manifest(targID);
                  break;
               case "transcription":
                  target = new Transcription(targID);
                  break;
               case "publication":
                  target = new Publication(targID);
                  break;
               default:
                  throw new NoSuchEntityException(targ);
            }
         }
      }
   }

   public int getUserID() {
      return user.getID();
   }

   @JsonProperty("user")
   public void setUserID(int uID) {
      if (user == null || user.getID() != uID) {
         user = new User(uID);
      }
   }

   /**
    * Permissions exposed through the GET /edition endpoint are expected to show the user's email address.
    * @return email address of user associated with this permission
    */
   @JsonProperty("mail")
   public String getUserMail() {
      return user.getMail();
   }

   /**
    * Sometimes we receive JSON with a permission object which has the user's email filled in… ignore it.
    */
   @JsonProperty("mail")
   public void setUserMail(String value) {
   }

   /**
    * Permissions exposed through the GET /edition endpoint are expected to show the user's name.
    * @return name of user associated with this permission
    */
   @JsonProperty("name")
   public String getUserName() {
      return user.getName();
   }

   /**
    * Sometimes we receive JSON with a permission object which has the user name filled in… ignore it.
    */
   @JsonProperty("name")
   public void setUserName(String value) {
   }

   public Role getRole() {
      return role;
   }

   public void setRole(Role val) {
      role = val;
   }

   @Override
   public void insert(Connection conn) throws SQLException {
      executeInsert(conn, "INSERT INTO `permissions` (" +
            "`target_type`, `target`, `user`, `role`" +
            ") VALUES(?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE `role` = VALUES(`role`)",
            target.getEntityType(), target.getID(), user.getID(), role.toString());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void load(Connection conn, boolean deep) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement("SELECT role FROM permissions " +
           "WHERE target_type = ? AND target = ? AND user = ?")) {
         stmt.setString(1, target.getEntityType());
         stmt.setInt(2, target.getID());
         stmt.setInt(3, user.getID());
         ResultSet rs = stmt.executeQuery();
         if (rs.next()) {
            role = Enum.valueOf(Role.class, rs.getString(1));
         }
         role = Role.NONE;
      }
   }

   /**
    * Override loadFields so that we can pull in the permission and its associated users with a single
    * query.
    * @param rs results of <code>Edition.SELECT_PERMISSIONS_WITH_USERS</code> query
    * @param deep false
    * @throws SQLException
    * @throws ReflectiveOperationException 
    */
   @Override
   public void loadFields(ResultSet rs, boolean deep) throws SQLException, ReflectiveOperationException {
      role = Role.valueOf(rs.getString("role"));
      setTargetURI(rs.getString("target_type").toLowerCase() + "/" + rs.getInt("target"));
      user = new User(rs.getInt("user"));
      if (!deep) {
         user.setName(rs.getString("name"));
         user.setMail(rs.getString("mail"));
      }
   }

   /**
    * Merging permissions isn't something meaningful.  Could it be someday?
    * @param conn connection to SQL database
    * @param newEnt new permission to replace the existing one
    */
   @Override
   public void merge(Connection conn, Entity newEnt) throws SQLException {
      Permission newPerm = (Permission)newEnt;
      
      // Update our top-level fields (of which role is the only one).
      if (role != newPerm.role) {
         role = newPerm.role;
         executeUpdate(conn, "UPDATE permissions SET `role` = ? " +
                 "WHERE id = ?", role.toString(), id);
      }
      newPerm.id = id;
   }

   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
      target.markTopLevelModified(conn);
   }

   /**
    * Permissions are checked for uniqueness on the combination of target and user.
    * @return 
    */
   public static Comparator<Permission> getMergingComparator() {
      return new Comparator<Permission>() {
         @Override
         public int compare(Permission o1, Permission o2) {
            if (o1.id == o2.id) {
               return 0;
            }
            int diff = o1.target.getUniqueID().compareTo(o2.target.getUniqueID());
            if (diff == 0) {
               diff = o1.user.getID()- o2.user.getID();
            }
            return diff;
         }
      };
   }
}
