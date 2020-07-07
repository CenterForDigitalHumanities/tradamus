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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import edu.slu.tradamus.witness.Witness;


/**
 * Aggregation class which serves to organise parallel content from multiple witnesses.
 *
 * @author tarkvara
 */
public class Parallel extends Entity {

   /** Outline to which this Parallel belongs. */
   private Outline outline;

   /** This Parallel's index within the Outline. */
   private int index;

   /** Witness-level sources for this Parallel. */
   private List<Annotation> sources;

   /**
    * Constructor for wrapping a Parallel around an ID.
    *
    * @param parID parallel ID
    */
   public Parallel(int parID) {
      id = parID;
   }

   /**
    * Constructor for an uninitialised Parallel.
    */
   public Parallel() {
   }

   /**
    * Constructor for wrapping a Parallel around a list of Annotations.  Called when deserialising an
    * Outline's motes.
    */
   public Parallel(Outline outl, int i, List<Annotation> anns) {
      outline = outl;
      index = i;
      setSources(anns);
   }

   @Override
   public Role checkPermission(Connection conn, int uID, Role required) throws SQLException, PermissionException {
      Role r = executeGetPermission(conn, "SELECT role FROM permissions " +
            "LEFT JOIN outlines ON outlines.edition = target " +
            "LEFT JOIN parallels ON outline = outlines.id " +
            "WHERE target_type = 'EDITION' AND parallels.id = ? AND (user = ? OR user = 0)", uID);
      if (r == null || r.ordinal() < required.ordinal()) {
         throw new PermissionException(this, required);
      }
      return r;
   }

   @Override
   public void insert(Connection conn) throws IOException, SQLException {
      executeInsert(conn, "INSERT INTO parallels (" +
            "outline, `index`" +
            ") VALUES(?, ?)",
            outline.getID(), index);
      if (sources != null) {
         for (Annotation ann: sources) {
            if (ann.getID() != 0) {
               // Using an already-existent annotation for bounds.  Make sure it's targetted at this parallel.
               executeUpdate(conn, "UPDATE `annotations` SET `target` = ?, `target_type` = 'PARALLEL', `target_fragment` = ? " +
                       "WHERE `id` = ?", id, ann.getTargetFragment(), ann.getID());
            } else {
               if (ann.getType() == null) {
                  ann.setType("tr-mote");
               }
               ann.setTarget(this);
               ann.insert(conn);
            }
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void load(Connection conn, boolean deep) throws SQLException, ReflectiveOperationException {
      try {
         executeLoad(conn, "SELECT * FROM `parallels` " +
                 "LEFT JOIN `annotations` ON parallels.id = `target` " +
                 "WHERE parallels.id = ? AND `target_type` = 'PARALLEL' " +
                 "ORDER BY annotations.type, annotations.id", deep);
      } catch (NoSuchEntityException ex) {
         // The join with no bounds may throw an exception even though the parallel exists.
         if (exists(conn)) {
            sources = new ArrayList<>();
         } else {
            throw new NoSuchEntityException(this);
         }
      }
   }

   @Override
   public void merge(Connection conn, Entity newEnt) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Parallel newPar = (Parallel)newEnt;
      
      // Update top-level fields.  Index is the only one.
      if (index != newPar.index) {
         index = newPar.index;

         executeUpdate(conn, "UPDATE parallels " +
                 "SET `index` = ? " +
                 "WHERE `id` = ?",
                 index, id);
      }
      newPar.id = id;

      for (Annotation ann: newPar.sources) {
         ann.setTarget(newPar);
         if (ann.getType() == null) {
            ann.setType("tr-mote");
         }
      }

      // Merge our motes.
      mergeChildren(conn, sources, newPar.sources, Annotation.getMergingComparator(), null, true);
   }

   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareCall("UPDATE editions " +
              "LEFT JOIN outlines ON edition = editions.id " +
              "LEFT JOIN parallels ON outline = outlines.id " +
              "SET modification = NOW() " +
              "WHERE parallels.id = ?")) {
         stmt.setInt(1, id);
         stmt.executeUpdate();
      }
   }

   @Override
   public void loadFields(ResultSet rs, boolean deep) throws SQLException, ReflectiveOperationException {
      super.loadFields(rs, deep);
      sources = new ArrayList<>();
      do {
         int parID = rs.getInt("parallels.id");
         if (parID != id) {
            // Have rolled into the next parallel.
            rs.previous();
            break;
         }
         int annID = rs.getInt("annotations.id");
         if (annID > 0) {
            Annotation ann = new Annotation(annID);
            ann.loadFields(rs, false);
            sources.add(ann);
         }
      } while (rs.next());
   }

   /**
    * Get the index of this Parallel within its containing Outline.
    */
   public int getIndex() {
      return index;
   }

   /**
    * Set the index of this Parallel within its containing Outline.
    * @param i new index
    */
   public void setIndex(int i) {
      index = i;
   }

   /**
    * Set the Outline to which this Parallel belongs.
    * @param outl owner of this Parallel
    */
   @JsonIgnore
   public void setOutline(Outline outl) {
      outline = outl;
   }
   
   @JsonProperty("outline")
   public void setOutlineID(int outlID) {
      if (outline == null || outline.getID() != outlID) {
         outline = new Outline(outlID);
      }
   }
   
   public List<Annotation> getSources() {
      return sources;
   }

   public final void setSources(List<Annotation> anns) {
      sources = anns;
      if (sources != null) {
         for (Annotation ann: sources) {
            ann.setTarget(this);
         }
      }
   }
   
   /**
    * Make sure parallels are all connected up with the proper witness fragments
    * @param knownPars list of known parallels
    * @param pageWits witnesses belonging to this edition
    */
   public void fixSources(Map<Integer, Witness> pageWits) {
      if (sources != null) {
         for (Annotation ann: sources) {
            Integer pageID = ann.getStartPageID();
            
            // Page will be null if it's an edition-level annotation section.
            if (pageID != null) {
               Witness wit = pageWits.get(pageID);
               if (wit != null) {
                  ann.setTargetFragment(Integer.toString(wit.getID()));
               } else {
                  LOG.log(Level.WARNING, "Annotation on page/{0}, which is not part of edition", pageID);
               }
            }
         }
      }
   }
   
   private static final Logger LOG = Logger.getLogger(Parallel.class.getName());
}
