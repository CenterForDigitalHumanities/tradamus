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
package edu.slu.tradamus.witness;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLStreamException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.db.PermissionFilter;
import edu.slu.tradamus.edition.Edition;
import edu.slu.tradamus.image.Manifest;
import edu.slu.tradamus.log.Activity;
import edu.slu.tradamus.log.Operation;
import edu.slu.tradamus.text.Page;
import edu.slu.tradamus.text.Transcription;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.*;


/**
 * A single witness within an edition.  Includes a transcription and/or a manifest.
 *
 * @author tarkvara
 */
public class Witness extends Entity {
   /**
    * Edition to which this witness belongs
    */
   protected Edition edition;
   
   protected String title;
   protected String author;
   protected String siglum;
   
   /** URI of associated TPEN repository. */
   private String tpen;
   
   /** Date of last TPEN update. */
   private Date tpenUpdate;

   protected List<Annotation> metadata = new ArrayList<>();
   
   protected Transcription transcription;

   protected Manifest manifest;

   /**
    * During import, keeps track of all annotations which have been added to this witness.
    */
   protected List<Annotation> importedAnnotations = new ArrayList<>();

   /**
    * Construct a blank witness as part of the import process.
    * @param ed edition to which this witness belongs
    * @param link URI of link to T-PEN (if any)
    */
   public Witness(Edition ed, String link) {
      edition = ed;
      transcription = new Transcription(this);
      manifest = new Manifest(this);
      tpen = link;
   }

   /**
    * Create a bare-bones witness by instantiating it from the database.  Used during collation process.
    * 
    * @param ed edition to which this witness belongs
    * @param witID witness ID
    * @param sig siglum 
    */
   public Witness(Edition ed, int witID, String sig) {
      id = witID;
      edition = ed;
      siglum = sig;
   }

   /**
    * Create a bare bones witness wrapped around an ID.
    *
    * @param witID 
    */
   public Witness(int witID) {
      id = witID;
   }

   /**
    * Constructor for instantiating from JSON.
    */
   public Witness() {
      transcription = new Transcription(this);
      manifest = new Manifest(this);
   }

   /**
    * When deleting a witness, we also have to explicitly delete any importedAnnotations, since these are not
    * handled by foreign-key cascading.
    * 
    * @param conn connection to SQL database
    * @return true if any records were deleted
    */
   @Override
   public boolean delete(Connection conn) throws SQLException {
      int deletions = 0;

      // Fetch our page IDs and delete any intersecting annotations.
      List<Integer> pgIDs = new ArrayList<>();
      try (PreparedStatement stmt = conn.prepareStatement("SELECT pages.id FROM pages " +
              "JOIN transcriptions ON transcription = transcriptions.id " +
              "WHERE witness = ?")) {
         stmt.setInt(1, id);
         ResultSet rs = stmt.executeQuery();
         while (rs.next()) {
            pgIDs.add(rs.getInt(1));
         }
      }

      if (pgIDs.size() > 0) {
         try (PreparedStatement stmt = conn.prepareStatement(Page.DELETE_ANNOTATIONS_ON_PAGE)) {
            for (int pgID: pgIDs) {
               stmt.setInt(1, pgID);
               deletions += stmt.executeUpdate();
            }
         }
      }

      // Delete any canvas-anchored annotations.
      try (PreparedStatement stmt = conn.prepareStatement("DELETE annotations FROM annotations " +
              "JOIN canvasses ON canvas = canvasses.id " +
              "JOIN manifests ON manifest = manifests.id " +
              "JOIN witnesses ON witness = witnesses.id " +
              "WHERE witnesses.id = ?")) {
         stmt.setInt(1, id);
         deletions += stmt.executeUpdate();
      }

      try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM annotations " +
              "WHERE `target_type` = 'WITNESS' AND `target` = ?")) {
         stmt.setInt(1, id);
         deletions += stmt.executeUpdate();
      }

      // Delete permissions associated with the witness' manifest.
      try (PreparedStatement stmt = conn.prepareStatement("DELETE permissions FROM permissions " +
              "LEFT JOIN manifests ON target = manifests.id " +
              "WHERE target_type = 'MANIFEST' AND witness = ?")) {
         stmt.setInt(1, id);
         deletions += stmt.executeUpdate();
      }
   
      // Delete permissions associated with the witness' transcription.
      try (PreparedStatement stmt = conn.prepareStatement("DELETE permissions FROM permissions " +
              "LEFT JOIN transcriptions ON target = transcriptions.id " +
              "WHERE target_type = 'TRANSCRIPTION' AND witness = ?")) {
         stmt.setInt(1, id);
         deletions += stmt.executeUpdate();
      }
   
      // Delete the witness, manifest, transcription, pages, and canvasses.
      return super.delete(conn) | deletions > 0;
   }


   /**
    * Add newly-imported witness to the database.
    */
   @Override
   public void insert(Connection conn) throws IOException, SQLException {
      executeInsert(conn, "INSERT INTO witnesses (" +
            "edition, title, author, siglum, tpen" +
            ") VALUES(?, ?, ?, ?, ?)",
            edition.getID(), title, author, siglum, tpen);

      // Because canvasses and pages may refer to each other, the ordering here is important.
      // Pages are stored first, with their canvas field left as null.  Inserting the
      // manifest generates all the canvas IDs which are then updated in the pages table.
      transcription.setWitnessID(id);
      transcription.insert(conn);
      manifest.setWitnessID(id);
      manifest.insert(conn);
      transcription.assignCanvasIDs(conn);
      for (Annotation a: importedAnnotations) {
         a.insert(conn);
      }
      if (tpen != null) {
         // Make sure our T-PEN update timestamp is *after* any of the annotations.
         executeUpdate(conn, UPDATE_TPEN_TIMESTAMP, id);
      }
   }

   /**
    * Update the database representation of a witness, including all its constituents.
    *
    * @param conn connection to SQL database
    * @param newEnt witness to be merged with this one
    */
   @Override
   public void merge(Connection conn, Entity newEnt) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Witness newWit = (Witness)newEnt;

      // Update our top-level fields.  Note that tpen and tpen_update are deliberately left unchanged by a
      // merge operation.
      if (!Objects.equals(title, newWit.title) || !Objects.equals(author, newWit.author) || !Objects.equals(siglum, newWit.siglum)) {
         title = newWit.title;
         author = newWit.author;
         siglum = newWit.siglum;
         executeUpdate(conn, "UPDATE witnesses SET title = ?, author = ?, siglum = ? " +
                 "WHERE id = ?", title, author, siglum, id);
      }
      newWit.id = id;

      metadata = loadChildren(conn, SELECT_METADATA, Annotation.class, true);
      mergeChildren(conn, metadata, newWit.metadata, Annotation.getMergingComparator(), null, true);

      transcription.merge(conn, newWit.transcription);
      manifest.merge(conn, newWit.manifest);

      List<Annotation> oldAnns = loadAllAnnotations(conn);
      PermissionFilter<Annotation> filt = null;
      if (tpen != null) {
         filt = new ModifiedBeforeFilter(tpenUpdate);
      }
      mergeChildren(conn, oldAnns, newWit.importedAnnotations, Annotation.getMergingComparator(), filt, true);
      
      if (tpen != null) {
         executeUpdate(conn, UPDATE_TPEN_TIMESTAMP, id);
      }
   }

   @Override
   public void load(Connection conn, boolean deep) throws SQLException, ReflectiveOperationException {
      if (deep) {
         executeLoad(conn, SELECT_FOR_DEEP_LOAD, false);
         transcription = loadChildren(conn, "SELECT * FROM transcriptions WHERE witness = ? LIMIT 1", Transcription.class, true).get(0);
         manifest = loadChildren(conn, "SELECT * FROM manifests WHERE witness = ? LIMIT 1", Manifest.class, true).get(0);
      } else {
         executeLoad(conn, SELECT_FOR_SHALLOW_LOAD, false);
      }
      metadata = loadChildren(conn, SELECT_METADATA, Annotation.class, deep);
   }
   
   /**
    * Override <code>loadFields</code> to set the TPEN update field.
    * @param rs result set positioned to the row for this annotation
    * @param deep if true, children will be fully loaded
    */
   @Override
   public void loadFields(ResultSet rs, boolean deep) throws SQLException, ReflectiveOperationException {
      super.loadFields(rs, deep);
      tpenUpdate = rs.getTimestamp("tpen_update");
   }

   /**
    * Update this witness based on a stream of JSON-LD data.  Used by the witness servlet and by the 
    * updateFromTpen method
    * @param conn connection to SQL database
    * @param uID user ID
    * @param input input stream containing JSON-LD data
    */
   public void updateFromJsonLD(Connection conn, int uID, InputStream input) throws SQLException, PermissionException, IOException, XMLStreamException, ReflectiveOperationException {
      checkPermission(conn, uID, Role.EDITOR);

      Witness newWit = new JsonLDWitness(getEdition(), input, null);
      newWit.fixAttributions(uID, Role.EDITOR);
      load(conn, true);
      conn.setAutoCommit(false);
      merge(conn, newWit);
      Activity.record(conn, uID, this, Operation.UPDATE, getObjectMapper().writeValueAsString(newWit));
      conn.commit();
   }

   /**
    * Retrieve all the existing annotations for this witness, including those attached to pages and
    * canvasses.  Included for support of the /witness/id/annotations endpoint.
    * @param conn connection to SQL database
    * @return list of all annotations associated with this witness, its pages and canvasses
    * @throws SQLException 
    */
   public List<Annotation> loadAllAnnotations(Connection conn) throws SQLException, ReflectiveOperationException {
      List<Annotation> result = loadChildren(conn, "SELECT * from annotations " +
                                       "LEFT JOIN pages ON start_page = pages.id " +
                                       "LEFT JOIN transcriptions on transcription = transcriptions.id " +
                                       "WHERE witness = ?", Annotation.class, true);
      result.addAll(loadChildren(conn, "SELECT * from annotations " +
                                       "LEFT JOIN canvasses ON canvas = canvasses.id " +
                                       "LEFT JOIN manifests on manifest = manifests.id " +
                                       "WHERE witness = ? AND start_page IS NULL", Annotation.class, true));
      result.addAll(loadChildren(conn, "SELECT * FROM `annotations` " +
                                       "WHERE `start_page` IS NULL AND `canvas` IS NULL AND `target_type` = 'WITNESS' AND `target` = ?", Annotation.class, true));
      return result;
   }

   /**
    * Called by the Json importer to record that annotations have been added to the witness.
    *
    * @param anns annotations which have been deserialised
    */
   public void setImportedAnnotations(List<Annotation> value) {
      importedAnnotations = value;
   }

   /**
    * Make sure all annotations have the correct attribution.
    */
   public void fixAttributions(int uID, Role r) throws SQLException {
      for (Annotation ann: importedAnnotations) {
         ann.setModifiedBy(uID, r);
      }
   }

   /**
    * Make sure all internally-targetted links are updated to reflect any new IDs.
    */
   public void fixTargets() {
      // Fix pages which point at canvasses.
      transcription.fixTargets(manifest.getCanvasses());
   
      // Fix canvasses which point at pages.
      manifest.fixTargets(transcription.getPages());
      
      // Fix annotations which point at pages or canvasses.
      for (Annotation ann: importedAnnotations) {
         ann.fixTargets(transcription.getPages(), manifest.getCanvasses());
      }
   }

   /**
    * Override <code>Entity.getEntityType</code> because we want <code>JsonLDWitness</code> and 
    * <code>XMLWitness</code> to both be stored as "WITNESS".
    * @return 
    */
   @Override
   public String getEntityType() {
      return "WITNESS";
   }

   /**
    * Get a URI fragment which uniquely locates this entity.  These URIs are generally relative to the
    * server URI.  Witness overrides the default behaviour for the benefit of JsonLDWitness and XMLWitness.
    */
   @Override
   public String getUniqueID() {
      return "witness/" + id;
   }


   @Override
   public Role checkPermission(Connection conn, int uID, Role required) throws SQLException, PermissionException {
      Role r = executeGetPermission(conn, "SELECT role FROM permissions " +
            "LEFT JOIN witnesses ON witnesses.edition = permissions.target " +
            "WHERE witnesses.id = ? AND (user = ? OR user = 0) AND target_type = 'EDITION'", uID);
      if (r == null || r.ordinal() < required.ordinal()) {
         throw new PermissionException(this, required);
      }
      return r;
   }

   /**
    * For activity-logging purposes, fetch the human-readable description of this witness.
    * @return the witness' title
    * @param conn connection to database
    * @throws SQLException 
    */
   @Override
   public String fetchDescription(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement("SELECT title FROM witnesses WHERE id = ?")) {
         stmt.setInt(1, id);
         ResultSet rs = stmt.executeQuery();
         rs.next();
         return rs.getString(1);
      }
   }

   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareCall("UPDATE editions " +
              "LEFT JOIN witnesses ON edition = editions.id " +
              "SET modification = NOW() " +
              "WHERE witnesses.id = ?")) {
         stmt.setInt(1, id);
         stmt.executeUpdate();
      }
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
      if (edition == null || edition.getID() != edID) {
         edition = new Edition(edID);
      }
   }
   
   public String getAuthor() {
      return author;
   }

   public void setAuthor(String val) {
      author = val;
   }

   public String getSiglum() {
      return siglum;
   }

   public void setSiglum(String val) {
      siglum = val;
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String t) {
      title = t;
   }

   public String getTpen() {
      return tpen;
   }

   public void setTpen(String value) {
      tpen = value;
   }

   public static void synchAllWithTpen(Connection conn, int uID) throws SQLException, ReflectiveOperationException, IOException, ServletException, PermissionException, XMLStreamException {
      try (PreparedStatement stmt = conn.prepareStatement(SELECT_FOR_TPEN_UPDATE)) {
         stmt.setInt(1, uID);
         ResultSet rs = stmt.executeQuery();
         while (rs.next()) {
            int witID = rs.getInt("witnesses.id");
            Witness wit = new Witness(witID);
            wit.loadFields(rs, false);
            wit.synchWithTpen(conn, uID);
         }
      }
   }

   private void synchWithTpen(Connection conn, int uID) throws IOException, ServletException, SQLException, PermissionException, XMLStreamException, ReflectiveOperationException {
      if (tpen != null) {
         URL srcURL = new URL(tpen);
         HttpURLConnection srcConn = (HttpURLConnection)srcURL.openConnection();
         if (tpenUpdate != null) {
            srcConn.addRequestProperty(IF_MODIFIED_SINCE_HEADER, RFC1123_FORMAT.format(tpenUpdate));
         }
         srcConn.connect();
   
         switch (srcConn.getResponseCode()) {
            case HttpServletResponse.SC_OK:
               updateFromJsonLD(conn, uID, srcConn.getInputStream());
               break;
            case HttpServletResponse.SC_NOT_MODIFIED:
               LOG.log(Level.INFO, "{0} not modified since {1}", new Object[] { tpen, tpenUpdate });
               break;
         }
      }
   }

   public Transcription getTranscription() {
      return transcription;
   }

   @JsonIgnore
   public void setTranscriptionID(int transcrID) {
      if (transcription == null || transcription.getID() != transcrID) {
         transcription = new Transcription(transcrID);
      }
   }

   public Manifest getManifest() {
      return manifest;
   }

   public void setManifest(Manifest man) {
      manifest = man;
   }

   @JsonIgnore
   public void setManifestID(int manID) {
      if (manifest == null || manifest.getID() != manID) {
         manifest = new Manifest(manID);
      }
   }

   public List<Annotation> getMetadata() {
       return metadata;
   }
   
   public void setMetadata(List<Annotation> md) {
      metadata = md;
   }

   @Override
   public Map<String, Method> getSetters() {
      Map<String, Method> result = super.getSetters();
      Class clazz = getClass();
      try {
         result.put("manifest", clazz.getMethod("setManifestID", Integer.TYPE));
         result.put("transcription", clazz.getMethod("setTranscriptionID", Integer.TYPE));
      } catch (NoSuchMethodException ex) {
         LOG.log(Level.WARNING, "Should never happen, since Witness has setManifestID and setTranscriptionID methods.", ex);
      }
      return result;
   }

   private static final Logger LOG = Logger.getLogger(Witness.class.getName());

   public static final String SELECT_FOR_DEEP_LOAD = "SELECT witnesses.*, -1 AS transcription, -1 AS manifest FROM witnesses " +
         "WHERE witnesses.id = ?";

   public static final String SELECT_FOR_SHALLOW_LOAD = "SELECT witnesses.*, transcriptions.id AS transcription, manifests.id AS manifest FROM witnesses " +
         "LEFT JOIN transcriptions ON transcriptions.witness = witnesses.id " +
         "LEFT JOIN manifests ON manifests.witness = witnesses.id " +
         "WHERE witnesses.id = ?";

   public static final String SELECT_FOR_TPEN_UPDATE = "SELECT witnesses.*, transcriptions.id AS transcription, manifests.id AS manifest FROM witnesses " +
         "LEFT JOIN permissions ON witnesses.edition = permissions.target " +
         "LEFT JOIN transcriptions ON transcriptions.witness = witnesses.id " +
         "LEFT JOIN manifests ON manifests.witness = witnesses.id " +
         "WHERE tpen IS NOT NULL AND target_type = 'EDITION' AND role >= 'EDITOR' AND user = ?";

   public static final String SELECT_METADATA = "SELECT * FROM annotations " +
         "WHERE target_type = 'WITNESS' AND target = ? AND start_page IS NULL AND end_page IS NULL AND canvas IS NULL";
   
   public static final String UPDATE_TPEN_TIMESTAMP = "UPDATE witnesses SET tpen_update = NOW() WHERE id = ?";
}
