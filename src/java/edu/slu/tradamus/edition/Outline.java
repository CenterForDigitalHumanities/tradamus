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
package edu.slu.tradamus.edition;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import edu.slu.tradamus.witness.Witness;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;


/**
 * Aggregation class which organises a set of decision annotations.
 *
 * @author tarkvara
 */
public class Outline extends Entity {
   /** Edition to which this Outline is attached. */
   private Edition edition;
   
   /** Position of Outline within the Edition. */
   private int index;

   /** User-friendly title for this Outline. */
   private String title;

   /** Collation bounds for which this Outline was created. */
   private Parallel bounds;
   
   /** Decisions associated with this Outline. */
   private List<Decision> decisions;

   /**
    * Constructor for wrapping an Outline around an ID.
    *
    * @param outID outline ID
    */
   public Outline(int outID) {
      id = outID;
   }

   /**
    * Constructor for instantiating from JSON.
    */
   public Outline() {
   }

   
   /**
    * {@inheritDoc}
    */
   @Override
   public Role checkPermission(Connection conn, int uID, Role required) throws SQLException, PermissionException {
      Role r = executeGetPermission(conn, "SELECT role FROM permissions " +
              "LEFT JOIN outlines ON outlines.edition = permissions.target " +
              "WHERE outlines.id = ? AND (user = ? OR user = 0) AND target_type = 'EDITION'", uID);
      if (r == null || r.ordinal() < required.ordinal()) {
         throw new PermissionException(this, required);
      }
      return r;
   }

   /**
    * In addition to the Parallels, which are automatically deleted with the deletion of the Outline, we
    * also need to delete any Annotations attached to those Parallels.
    * @param conn connection to SQL database
    * @return true if anything was deleted
    * @throws SQLException 
    */
   @Override
   public boolean delete(Connection conn) throws SQLException {
      int deletions = 0;
   
      // Clean up any annotations which may be hanging off our parallels.
      try (PreparedStatement stmt = conn.prepareStatement("DELETE annotations FROM annotations " +
              "LEFT JOIN parallels ON target = parallels.id " +
              "WHERE outline = ? AND target_type = 'PARALLEL'")) {
         stmt.setInt(1, id);
         deletions += stmt.executeUpdate();
      }

      // Delete the witness, manifest, transcription, pages, and canvasses.
      return super.delete(conn) | deletions > 0;
   }

   @Override
   public void insert(Connection conn) throws SQLException, IOException {
      executeInsert(conn, "INSERT INTO outlines (" +
            "edition, `index`, title" +
            ") VALUES(?, ?, ?)",
            edition.getID(), index, title);

      if (bounds != null) {
         bounds.setIndex(-1);
         bounds.setOutline(this);
         bounds.insert(conn);

         executeUpdate(conn, "UPDATE outlines SET bounds = ? WHERE id = ?", bounds.getID(), id);
      }

      if (decisions != null) {
         for (Decision dec: decisions) {
            dec.setOutline(this);
            dec.insert(conn);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void load(Connection conn, boolean deep) throws SQLException, ReflectiveOperationException {
      executeLoad(conn, "SELECT * FROM outlines WHERE id = ?", deep);
      
      if (bounds != null) {
         bounds.load(conn, deep);
      }
      decisions = new ArrayList<>();
      try (PreparedStatement stmt = conn.prepareStatement(SELECT_DECISIONS_AND_MOTES)) {
         stmt.setInt(1, id);
         ResultSet rs = stmt.executeQuery();
         while (rs.next()) {
            // Load the Parallel and all adjoined annotations.
            Parallel par = new Parallel(rs.getInt("parallels.id"));
            par.loadFields(rs, deep);
            
            decisions.add(new Decision(par));
         }
      }
   }

   @Override
   public void loadFields(ResultSet rs, boolean deep) throws SQLException, ReflectiveOperationException {
      super.loadFields(rs, deep);
      
      int boundsID = rs.getInt("bounds");
      if (!rs.wasNull()) {
         bounds = new Parallel(boundsID);
      }
   }

   @Override
   public void merge(Connection conn, Entity newEnt) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Outline newOutl = (Outline)newEnt;

      // Update our top-level fields.
      if (index != newOutl.index || !Objects.equals(title, newOutl.title)) {
         index = newOutl.index;
         title = newOutl.title;
         executeUpdate(conn, "UPDATE outlines SET `index` = ?, `title` = ? " +
                 "WHERE id = ?", index, title, id);
      }
      newOutl.id = id;

      if (newOutl.bounds != null) {
         if (bounds != null) {
            bounds.merge(conn, newOutl.bounds);
         } else {
            bounds = newOutl.bounds;
         }
      }
      
      if (newOutl.decisions != null) {
         if (decisions != null) {
            mergeChildren(conn, decisions, newOutl.decisions, Decision.getDecisionMergingComparator(), null, true);
         } else {
            decisions = newOutl.decisions;
         }
      }
   }

   /**
    * Override modify to properly handle "bounds" and "decisions" if they're supplied in the request body.
    * @param conn connection to MySQL database
    * @param mods modifications from request body
    * @return 
    * @throws SQLException
    * @throws ReflectiveOperationException 
    */
   @Override
   public Object modify(Connection conn, Map<String, Object> mods) throws IOException, ReflectiveOperationException, SQLException, PermissionException {
      Object boundsObj = mods.get("bounds");
      Object decsObj = mods.get("decisions");
      
      // We've tucked in some extra fields so we get correct attribution on any modifications by this operation.
      Integer modifiedBy = (Integer)mods.get("modifiedBy");
      Role r = (Role)mods.get("role");
      mods.remove("modifiedBy");
      mods.remove("role");
      
      if (boundsObj != null || decsObj != null) {
         ObjectMapper mapper = getObjectMapper();
         if (boundsObj != null) {
            List<Annotation> newSources = mapper.convertValue(boundsObj, new TypeReference<List<Annotation>>() {});
            Annotation.markAnnotationsModified(newSources, modifiedBy, r);
            Parallel newBounds = new Parallel(this, -1, newSources);
            bounds.merge(conn, newBounds);
         }
         if (decsObj != null) {
            List<Decision> newDecs = mapper.convertValue(decsObj, new TypeReference<List<Decision>>() {});
            Annotation.markAnnotationsModified(newDecs, modifiedBy, r);
            for (int i = 0; i < newDecs.size(); i++) {
               newDecs.get(i).setIndex(i);
            }
            mergeChildren(conn, decisions, newDecs, Decision.getDecisionMergingComparator(), null, true);
         }
      }
      return super.modify(conn, mods);
   }
   
   
   public List<Annotation> getBounds() {
      if (bounds != null) {
         return bounds.getSources();
      }
      return null;
   }

   public void setBounds(List<Annotation> anns) {
      if (anns != null) {
         if (bounds == null) {
            bounds = new Parallel();
         }
         bounds.setSources(anns);
         bounds.setIndex(-1);
      } else {
         bounds = null;
      }
   }

   @JsonIgnore
   public Edition getEdition() {
      return edition;
   }

   @JsonIgnore
   public void setEdition(Edition ed) {
      edition = ed;
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

   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareCall("UPDATE editions " +
              "LEFT JOIN outlines ON edition = editions.id " +
              "SET modification = NOW() " +
              "WHERE outlines.id = ?")) {
         stmt.setInt(1, id);
         stmt.executeUpdate();
      }
   }

   public int getIndex() {
      return index;
   }

   public void setIndex(int i) {
      index = i;
   }
   
   public List<Decision> getDecisions() {
      return decisions;
   }

   public void setDecisions(List<Decision> decs) {
      int i = 0;
      for (Decision dec: decs) {
         dec.setIndex(i++);
         dec.setOutline(this);
      }
      decisions = decs;
   }

   /**
    * Make sure this Outline's Decisions (and any associated motes) are marked as being modified by the
    * correct user.
    * @param uID user modifying the Outline
    * @param r user's Role, to determine whether auto-approval applies
    */
   public void setDecisionsModifiedBy(int uID, Role r) {
      if (bounds != null) {
         Annotation.markAnnotationsModified(bounds.getSources(), uID, r);
      }
      if (decisions != null) {
         Annotation.markAnnotationsModified(decisions, uID, r);
      }
   }

   public String getTitle() {
      return title;
   }
   
   public void setTitle(String t) {
      title = t;
   }

   /**
    * Fixes the target fragments of all sources attached to this Outline and its Decisions.  Also, keeps
    * track of Parallels to ensure that identical Parallels point to the same object.
    *
    * @param knownPars accumulated list of known Parallels
    * @param pageWits mapping between page
    */
   public void fixSources(List<Parallel> knownPars, Map<Integer, Witness> pageWits) {
      if (bounds != null) {
         int pos = knownPars.indexOf(bounds);
         if (pos >= 0) {
            bounds = knownPars.get(pos);
         } else {
            knownPars.add(bounds);
         }
         bounds.fixSources(pageWits);
      }
      if (decisions != null) {
         for (Decision dec: decisions) {
            int pos = knownPars.indexOf(dec.parallel);
            if (pos >= 0) {
               dec.parallel = knownPars.get(pos);
            } else {
               knownPars.add(dec.parallel);
            }
            dec.parallel.fixSources(pageWits);
         }
      }
   }

   /**
    * For now, Outlines are just compared on the basis of IDs.
    * TODO: Proper comparison of Outline contents
    */
   public static Comparator<Outline> getMergingComparator() {
      return new Comparator<Outline>() {
         @Override
         public int compare(Outline o1, Outline o2) {
            return o1.id - o2.id;
         }
      };
   }

   public static final String SELECT_DECISIONS_AND_MOTES = "SELECT * FROM annotations " +
         "LEFT JOIN parallels ON target = parallels.id " +
         "LEFT JOIN outlines ON outline = outlines.id " +
         "WHERE outline = ? AND target_type = 'PARALLEL' AND parallels.index >= 0 AND type IN ('tr-decision', 'tr-mote') " +
         "ORDER BY parallels.index, type, annotations.id";
   
   public static final String SELECT_ANNOTATIONS = "SELECT * FROM `annotations` " +
         "WHERE `target_type` = 'OUTLINE' AND `target` = ?";
}
