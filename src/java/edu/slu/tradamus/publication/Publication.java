/*
 * Copyright 2014-2015 Saint Louis University. Licensed under the
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
package edu.slu.tradamus.publication;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.edition.Edition;
import edu.slu.tradamus.user.Permission;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import edu.slu.tradamus.user.User;
import static edu.slu.tradamus.util.JsonUtils.formatDate;

/**
 * Class which represents the published version of an Edition.
 *
 * @author tarkvara
 */
public class Publication extends Entity {

   /**
    * Edition which is being published.
    */
   private Edition edition;

   /**
    * Title of the publication.
    */
   private String title;
   
   /**
    * User responsible for creating this publication.
    */
   private User creator;

   private Date creation;
   
   private Date modification;
   
   /**
    * Type of publication being produced.
    */
   private Type type;

   /**
    * List of sections associated with this publication.
    */
   private List<Section> sections = new ArrayList<>();

   /**
    * User-by-user permissions for accessing this publication.
    */
   private List<Permission> permissions;

   /**
    * Constructor used by JSON deserialisation.
    */
   public Publication() {
   }

   /**
    * Wrap a bare-bones publication around an ID.
    *
    * @param publID ID from publications table
    */
   public Publication(int publID) {
      id = publID;
   }

   public String getCreation() {
      return formatDate(creation);
   }

   public int getCreator() {
      return creator.getID();
   }

   public void setCreator(int id) {
      creator = new User(id);
   }

   @JsonIgnore
   public Edition getEdition() {
      return edition;
   }

   @JsonProperty("edition")
   public int getEditionID() {
      return edition.getID();
   }

   @JsonProperty("edition")
   public void setEditionID(int edID) {
      edition = new Edition(edID);
   }

   public String getModification() {
      return formatDate(modification);
   }

   public List<Permission> getPermissions() {
      return permissions;
   }

   public List<Section> getSections() {
      return sections;
   }

   public void setSections(List<Section> sects) {
      sections = sects;
   }
   
   public String getTitle() {
      return title;
   }

   public void setTitle(String t) {
      title = t;
   }

   public Type getType() {
      return type;
   }

   public void setType(String value) {
      type = (Type)Enum.valueOf(Type.class, value);
   }

   /**
    * Make sure that the permissions are deleted when the publication is.
    * @param conn connection to SQL database
    * @return true if something was deleted
    * @throws SQLException 
    */
   @Override
   public boolean delete(Connection conn) throws SQLException {
      // Sections will be deleted by cascade, but we will need to explicitly delete permissions.
      int deletions = 0;
      try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM `permissions` WHERE `target` = ? AND `target_type` = 'PUBLICATION'")) {
         stmt.setInt(1, id);
         deletions += stmt.executeUpdate();
      }
      return super.delete(conn) || deletions > 0;
   }

   @Override
   public void insert(Connection conn) throws SQLException {
      executeInsert(conn, "INSERT INTO publications (" +
            "edition, title, type, creator, modification" +
            ") VALUES (?, ?, ?, ?, NOW())",
            edition.getID(), title, type.toString(), creator.getID());
      try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO permissions (" +
            "target_type, target, user, role" +
            ") VALUES ('PUBLICATION', ?, ?, 'OWNER')")) {
         stmt.setInt(1, id);
         stmt.setInt(2, creator.getID());
         stmt.executeUpdate();
      }
      
      int i = 0;
      for (Section sect: sections) {
         sect.setPublicationID(id);
         sect.setIndex(i++);
         sect.insert(conn);
      }
   }

   @Override
   public void load(Connection conn, boolean deep) throws SQLException, ReflectiveOperationException {
      executeLoad(conn, "SELECT * FROM publications " +
           "WHERE publications.id = ?", true);
      sections = loadChildren(conn, "SELECT * FROM sections WHERE publication = ?", Section.class, deep);
      if (permissions == null) {
         permissions = loadChildren(conn, SELECT_PERMISSIONS_WITH_USERS, Permission.class, false);
      }
   }

   @Override
   public void loadFields(ResultSet rs, boolean deep) throws SQLException, ReflectiveOperationException {
      super.loadFields(rs, deep);
      
      // creation_time and modification_time don't have setters so we have to load them explicitly.
      creation = rs.getTimestamp("creation");
      modification = rs.getTimestamp("modification");
   }

   @Override
   public void merge(Connection conn, Entity newEnt) throws SQLException, PermissionException, ReflectiveOperationException {
      throw new UnsupportedOperationException("Publication.merge not supported.");
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Role checkPermission(Connection conn, int uID, Role required) throws SQLException, PermissionException {
      if (permissions == null) {
         try {
            permissions = loadChildren(conn, SELECT_PERMISSIONS_WITH_USERS, Permission.class, false);
            if (permissions.isEmpty() && !exists(conn)) {
               throw new NoSuchEntityException(this);
            }
         } catch (ReflectiveOperationException ex) {
            LOG.log(Level.WARNING, "Should never happen, since loading of Permissions works.", ex);
         }
      }
      Role r = getBestPermission(permissions, uID, true);
      if (r == null || r.ordinal() < required.ordinal()) {
         throw new PermissionException(this, required);
      }
      return r;
   }

   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement("UPDATE publications SET modification = NOW() WHERE id = ?")) {
         stmt.setInt(1, id);
         stmt.executeUpdate();
      }
   }
   
   public static final String SELECT_VIEWABLE = "SELECT publications.id, title FROM publications " +
         "LEFT JOIN permissions ON target = publications.id " +
         "WHERE target_type = 'PUBLICATION' AND permissions.user = ? AND permissions.role IN ('VIEWER', 'REVIEW_EDITOR', 'CONTRIBUTOR', 'EDITOR', 'OWNER')";
   
   /**
    * Query for loading publication permissions along with user name and email.
    */
   public static final String SELECT_PERMISSIONS_WITH_USERS = "SELECT permissions.id AS id, target_type, target, role, " +
         "user, name, mail FROM permissions " +
         "JOIN users ON user = users.id " +
         "WHERE target_type = 'PUBLICATION' AND target = ?";

   /**
    * Query for loading all sections of a given publication.
    */
   public static final String SELECT_SECTIONS = "SELECT * FROM sections WHERE publication = ?";

   private static final Logger LOG = Logger.getLogger(Publication.class.getName());

   public enum Type {
      PDF,
      TEI,
      DYNAMIC,
      OAC,
      XML
   }
}
