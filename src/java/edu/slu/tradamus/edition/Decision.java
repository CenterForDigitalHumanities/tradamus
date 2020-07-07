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
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;

/**
 * A Decision is just an Annotation which optionally has an array of mote Annotations serving as its
 * sources.  Internally, this is managed using Parallels, but these are not exposed to the front-end.
 *
 * @author tarkvara
 */
public class Decision extends Annotation {
   /** Data structure which anchors us back to the witness. */
   Parallel parallel;

   /**
    * Constructor to pull apart a Parallel and set its contents as a Decision.
    *
    * @param par Parallel whose sources support the Decision; may contain Decision itself as 0'th source
    */
   Decision(Parallel par) {
      Annotation ann0 = par.getSources().get(0);
      if (ann0.getType().equals("tr-decision")) {
         par.getSources().remove(0);
         copyDecisionFields(ann0);
      }      
      setType("tr-decision");
      setTarget(par);
      parallel = par;
   }

   /**
    * Constructor for deserialising from Json.
    */
   public Decision() {
      parallel = new Parallel();
      setType("tr-decision");
   }

   @Override
   public boolean delete(Connection conn) throws SQLException {
      int deletions = 0;
      
      try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM annotations " +
              "WHERE target_type = 'PARALLEL' AND target = ?")) {
         stmt.setInt(1, parallel.getID());
         deletions += stmt.executeUpdate();
      }
      if (parallel.delete(conn)) {
         deletions++;
      }
      return deletions > 0;
   }

   
   /**
    * Override <code>Annotation.insert</code> to take care of inserting the Parallels and motes.
    * @param conn
    * @throws SQLException 
    */
   @Override
   public void insert(Connection conn) throws IOException, SQLException {
      parallel.insert(conn);
      setTarget(parallel);
      super.insert(conn);
   }

   /**
    * In addition to merging the Decision <i>qua</i> Annotation, the Decision is also responsible for
    * merging the Parallel and associated mote Annotations.
    * 
    * @param conn connection to SQL database
    * @param newEnt updated Decision received from JSON
    */
   @Override
   public void merge(Connection conn, Entity newEnt) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      super.merge(conn, newEnt);
      
      parallel.merge(conn, ((Decision)newEnt).parallel);
   }

   
   void setIndex(int i) {
      parallel.setIndex(i);
   }

   /**
    * Mark this Annotation as being modified by the given user.  If they have EDITOR permissions, also
    * record their approval.
    */
   @Override
   public void setModifiedBy(int uID, Role r) {
      super.setModifiedBy(uID, r);
      for (Annotation ann: parallel.getSources()) {
         ann.setModifiedBy(uID, r);
      }
   }


   public List<Annotation> getMotes() {
      return parallel.getSources();
   }

   /**
    * Set all the mote annotations for this decision; used when deserialising from JSON.
    * @param motes source motes for this decision
    */
   public void setMotes(List<Annotation> motes) {
      for (Annotation ann: motes) {
         ann.setTarget(parallel);
         ann.setType("tr-mote");
      }
      parallel.setSources(motes);
   }

   void setOutline(Outline outl) {
      parallel.setOutline(outl);
   }

   /**
    * For merging, we mainly rely on the Annotation comparator.
    */
   public static Comparator<Decision> getDecisionMergingComparator() {
      return new Comparator<Decision>() {

         @Override
         public int compare(Decision o1, Decision o2) {
            // If their IDs are identical, these Decisions are considered mergeable.
            if (o1.id == o2.id) {
               return 0;
            }
            // If their parallel IDs are identical, these Decisions are considered mergeable.
            if (o1.parallel.getID() == o2.parallel.getID()) {
               return 0;
            }

            List<Annotation> sources1 = o1.parallel.getSources();
            List<Annotation> sources2 = o2.parallel.getSources();
            int diff = sources1.size() - sources2.size();
            if (diff == 0) {
               for (int i = 0; i < sources1.size(); i++) {
                  Annotation mote1 = sources1.get(i);
                  Annotation mote2 = sources2.get(i);
                  if (mote1.getID() == mote2.getID()) {
                     continue;
                  }
                  // Try comparing motes on page ID.
                  diff = mote1.getStartPageID() - mote2.getStartPageID();
                  if (diff == 0) {
                     // Motes on same page; try comparing on text offset.
                     diff = mote1.getStartOffset() - mote2.getStartOffset();
                  }
                  if (diff != 0) {
                     break;
                  }
               }
            }
            return diff;
         }
      };
   }
}
