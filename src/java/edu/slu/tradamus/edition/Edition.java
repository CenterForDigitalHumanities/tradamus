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
package edu.slu.tradamus.edition;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.text.Page;
import edu.slu.tradamus.text.Transcription;
import edu.slu.tradamus.user.Permission;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import edu.slu.tradamus.user.User;
import static edu.slu.tradamus.util.JsonUtils.formatDate;
import edu.slu.tradamus.witness.Witness;


/**
 * Umbrella class which gathers together a group of witnesses.
 *
 * @author tarkvara
 */
public class Edition extends Entity {
   /**
    * Title of the edition.
    */
   private String title;
   
   /**
    * User responsible for creating this edition.
    */
   private User creator;

   private Date creation;
   
   private Date modification;

   /**
    * List of witnesses associated with this edition.
    */
   private List<Witness> witnesses;

   private List<Annotation> metadata;
   
   private List<Permission> permissions;
   
   private List<Outline> outlines;

   /**
    * Constructor used by JSON deserialisation.
    */
   public Edition() {
   }

   /**
    * Construct a blank edition with the given title.
    *
    * @param t title
    */
   public Edition(String t) {
      title = t;
   }

   /**
    * Wrap a bare-bones edition around an ID.
    *
    * @param edID 
    */
   public Edition(int edID) {
      id = edID;
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String t) {
      title = t;
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

   public List<Annotation> getMetadata() {
       return metadata;
   }

   public String getModification() {
      return formatDate(modification);
   }
   
   public List<Outline> getOutlines() {
      return outlines;
   }
   
   public void setOutlines(List<Outline> value) {
      outlines = value;
   }

   public List<Permission> getPermissions() {
      return permissions;
   }

   public List<Witness> getWitnesses() {
      return witnesses;
   }

   public void setWitnesses(List<Witness> value) {
      witnesses = value;
   }

   /**
    * As part of fixing fragments for parallels during a JSON import, we need to track the mapping between
    * page IDs and witnesses.  This version is used when we are processing an edition which is being
    * deserialised from JSON.
    *
    * @return mapping of page IDs to Witness objects for this edition
    */
   @JsonIgnore
   public Map<Integer, Witness> getPageWitnesses() throws SQLException {
      Map<Integer, Witness> result = new HashMap<>();
      if (witnesses != null) {
         for (Witness wit: witnesses) {
            Transcription transcr = wit.getTranscription();
            if (transcr != null) {
               List<Page> pages = transcr.getPages();
               if (pages != null) {
                  for (Page pg: pages) {
                     result.put(pg.getID(), wit);
                  }
               }
            }
         }
      }
      return result;
   }

   /**
    * As part of fixing fragments for parallels, we need to retrieve the mapping between page IDs and
    * witnesses.  This version is used when the edition has been loaded from the database.
    *
    * @param conn connection to SQL database
    * @return mapping of page IDs to Witness objects for this edition
    */
   public Map<Integer, Witness> getPageWitnesses(Connection conn) throws SQLException {
      Map<Integer, Witness> result = new HashMap<>();
      try (PreparedStatement stmt = conn.prepareStatement(SELECT_PAGES_AND_WITNESSES)) {
         stmt.setInt(1, id);
         ResultSet rs = stmt.executeQuery();
         Witness lastWit = null;
         while (rs.next()) {
            int witID = rs.getInt("witness");
            if (lastWit == null || lastWit.getID() != witID) {
               lastWit = new Witness(witID);
            }
            result.put(rs.getInt("pages.id"), lastWit);
         }
      }
      return result;
   }

   /**
    * As part of importing an edition from an external source, make sure any internal links point to real
    * objects and not to place-holders which have been wrapped around an ID.
    */
   public void fixTargets(Map<Integer, Witness> pageWits) {
      List<Parallel> knownPars = new ArrayList<>();
      if (outlines != null) {
         for (Outline outl: outlines) {
            outl.fixSources(knownPars, pageWits);
         }
      }
      if (witnesses != null) {
         for (Witness w: witnesses) {
            w.fixTargets();
         }
      }
   }

   /**
    * We explicitly delete the witnesses first, so that their annotations get cleaned up.
    * @param conn connection to SQL database
    * @return true if something was deleted
    * @throws SQLException 
    */
   @Override
   public boolean delete(Connection conn) throws SQLException {
      int deletions = 0;

      // Fetch our witness IDs and delete any intersecting annotations.
      List<Integer> witIDs = new ArrayList<>();
      try (PreparedStatement stmt = conn.prepareStatement("SELECT `id` FROM witnesses " +
              "WHERE `edition` = ?")) {
         stmt.setInt(1, id);
         ResultSet rs = stmt.executeQuery();
         while (rs.next()) {
            witIDs.add(rs.getInt(1));
         }
      }

      if (witIDs.size() > 0) {
         for (int witID: witIDs) {
            if (new Witness(witID).delete(conn)) {
               deletions++;
            }
         }
      }

      // Delete edition metadata.
      try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM annotations WHERE target = ? AND target_type = 'EDITION'")) {
         stmt.setInt(1, id);
         deletions += stmt.executeUpdate();
      }
      
      // Delete edition permissions.
      try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM permissions WHERE target = ? AND target_type = 'EDITION'")) {
         stmt.setInt(1, id);
         deletions += stmt.executeUpdate();
      }
      
      // Delete the edition itself.
      return super.delete(conn) | deletions > 0;
   }

   
   @Override
   public void insert(Connection conn) throws IOException, SQLException {
      executeInsert(conn, "INSERT INTO editions (" +
            "title, creator, modification" +
            ") VALUES (?, ?, NOW())",
            title, creator.getID());
      try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO permissions (" +
            "target_type, target, user, role" +
            ") VALUES ('EDITION', ?, ?, 'OWNER')")) {
         stmt.setInt(1, id);
         stmt.setInt(2, creator.getID());
         stmt.executeUpdate();
      }
      
      if (witnesses != null) {
         for (Witness wit: witnesses) {
            wit.setEditionID(id);
            wit.insert(conn);
         }
      }
      
      if (outlines != null) {
         for (Outline outl: outlines) {
            outl.setEditionID(id);
            outl.insert(conn);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void load(Connection conn, boolean deep) throws SQLException, ReflectiveOperationException {
      if (deep) {
         executeLoad(conn, "SELECT editions.title, creator, creation, modification FROM editions " +
                 "WHERE editions.id = ?", true);
         witnesses = loadChildren(conn, "SELECT * FROM witnesses WHERE edition = ?", Witness.class, true);
      } else {
         executeLoad(conn, "SELECT editions.title, creator, creation, modification, witnesses.id, witnesses.title, siglum FROM editions " +
                 "LEFT JOIN witnesses ON edition = editions.id WHERE editions.id = ?", false);
      }
      metadata = loadChildren(conn, SELECT_METADATA, Annotation.class, deep);
      outlines = loadChildren(conn, SELECT_OUTLINES, Outline.class, deep);
      permissions = loadChildren(conn, SELECT_PERMISSIONS_WITH_USERS, Permission.class, false);
   }

   @Override
   public void loadFields(ResultSet rs, boolean deep) throws SQLException, ReflectiveOperationException {
      super.loadFields(rs, deep);
      
      // creation_time and modification_time don't have setters so we have to load them explicitly.
      creation = rs.getTimestamp("creation");
      modification = rs.getTimestamp("modification");

      if (!deep) {
         witnesses = new ArrayList<>();
         do {
            int witID = rs.getInt("witnesses.id");
            if (witID > 0) {
               Witness wit = new Witness(witID);
               wit.setTitle(rs.getString("witnesses.title"));
               wit.setSiglum(rs.getString("siglum"));
               witnesses.add(wit);
            }
         } while (rs.next());
      }
   }

   /**
    * Merging editions isn't something meaningful.  Could it be someday?
    * @param conn
    * @param newEnt 
    */
   @Override
   public void merge(Connection conn, Entity newEnt) {
      throw new UnsupportedOperationException("Edition.merge not supported.");
   }

   /**
    * Retrieve all the transcriptions of all the witnesses associated with this edition.  Used as input for
    * the collator.
    * 
    * @param conn connection to SQL database
    * @return list of all transcriptions for this edition
    * @throws SQLException 
    */
    public List<Transcription> loadTranscriptions(Connection conn, int uID) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement("SELECT `index`, pages.title, pages.id, text, transcription, witness, siglum FROM pages " +
              "LEFT JOIN transcriptions ON transcription = transcriptions.id " +
              "LEFT JOIN witnesses ON witness = witnesses.id WHERE edition = ? AND siglum IS NOT NULL " +
              "ORDER BY witnesses.id, pages.index")) {
         stmt.setInt(1, id);
         ResultSet rs = stmt.executeQuery();
         List<Transcription> result = new ArrayList<>();
         Transcription lastTrans = null;
         boolean skipping = false;
         while (rs.next()) {
            String witSig = rs.getString("siglum");
            if (lastTrans == null || !witSig.equals(lastTrans.getWitness().getSiglum())) {
               // We've transitioned to a fresh witness.
               Witness wit = new Witness(this, rs.getInt("witness"), witSig);
               lastTrans = new Transcription(wit, rs.getInt("transcription"));
               
               Role r = lastTrans.getTranscriptionPermission(conn, uID);
               if (r == null || r.ordinal() >= Role.VIEWER.ordinal()) {
                  result.add(lastTrans);
                  skipping = false;
               } else {
                  // User doesn't have access to this transcription.  Log it and skip.
                  LOG.log(Level.INFO, "Skipping {0} because user/{1} does not have VIEWER access.", new Object[] { lastTrans, uID });
                  skipping = true;
               }
            }
            if (!skipping) {
               lastTrans.addPage(new Page(lastTrans, rs.getInt("pages.id"), rs.getInt("index"), rs.getString("pages.title"), rs.getString("text")));
            }
         }
         return result;
      }
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

   /**
    * For activity-logging purposes, fetch the human-readable description of this edition.
    * @return the edition's title
    * @param conn connection to database
    * @throws SQLException 
    */
   @Override
   public String fetchDescription(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement("SELECT title FROM editions WHERE id = ?")) {
         stmt.setInt(1, id);
         ResultSet rs = stmt.executeQuery();
         rs.next();
         return rs.getString(1);
      }
   }

   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement("UPDATE editions SET modification = NOW() WHERE id = ?")) {
         stmt.setInt(1, id);
         stmt.executeUpdate();
      }
   }
   
   /**
    * Provides a streamlined annotation summary suitable for returning in the /edition endpoint; the
    * default JSON serialisation produces a spew of extraneous fields.
    * @return list of maps containing the relevant data for annotations
    */
   public List<Annotation> loadAllAnnotations(Connection conn) throws SQLException, ReflectiveOperationException {
      
      // Edition-level annotations (i.e. metadata).
      List<Annotation> result = new ArrayList<>(loadChildren(conn, SELECT_METADATA, Annotation.class, false));

      // Outline-level annotations (i.e. annotations on collated text).
      result.addAll(loadChildren(conn, SELECT_OUTLINE_ANNOTATIONS, Annotation.class, false));

      return result;
   }

   public static final String SELECT_VIEWABLE = "SELECT editions.id, title FROM editions " +
         "LEFT JOIN permissions ON target = editions.id " +
         "WHERE target_type = 'EDITION' AND permissions.user = ? AND permissions.role IN ('VIEWER', 'REVIEW_EDITOR', 'CONTRIBUTOR', 'EDITOR', 'OWNER')";
   
   public static final String SELECT_VIEWABLE_NO_USER = "SELECT editions.id, title FROM editions";

   public static final String SELECT_METADATA = "SELECT * FROM annotations " +
         "WHERE `target_type` = 'EDITION' AND `target` = ? AND start_page IS NULL AND end_page IS NULL AND canvas IS NULL";

   public static final String SELECT_OUTLINES = "SELECT * FROM outlines " +
         "WHERE edition = ? ORDER BY outlines.index";

   public static final String SELECT_OUTLINE_ANNOTATIONS = "SELECT * FROM annotations " +
         "LEFT JOIN outlines ON target = outlines.id " +
         "WHERE target_type = 'OUTLINE' AND edition = ? ORDER BY outlines.index";

   private static final String SELECT_PAGES_AND_WITNESSES = "SELECT * FROM pages " +
         "LEFT JOIN transcriptions ON transcription = transcriptions.id " +
         "LEFT JOIN witnesses ON witness = witnesses.id " +
         "WHERE edition = ? ORDER BY witnesses.id";

   /**
    * Query for loading edition permissions along with user name and email.
    */
   public static final String SELECT_PERMISSIONS_WITH_USERS = "SELECT permissions.id AS id, target_type, target, role, " +
         "user, name, mail FROM permissions " +
         "JOIN users ON user = users.id " +
         "WHERE target_type = 'EDITION' AND target = ?";

   private static final Logger LOG = Logger.getLogger(Edition.class.getName());
}
