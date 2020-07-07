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
package edu.slu.tradamus.log;

import java.sql.Connection;
import java.sql.SQLException;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.user.User;


/**
 * Class which represents a single activity being logged.
 *
 * @author tarkvara
 */
public class Activity extends Entity {

   private final User user;

   private final Entity entity;

   private final Entity parent;

   private final Operation operation;

   private final String content;

   /**
    * Constructor for updates, deletions, and views.
    * @param uID user whose activity is being stored
    * @param ent entity affected
    * @param op operation
    * @param cont string describing the nature of the operation
    */
   private Activity(int uID, Entity ent, Operation op, String cont) {
      user = new User(uID);
      entity = ent;
      parent = null;
      operation = op;
      content = cont;
   }

   /**
    * Constructor for recording insertions.
    * @param uID user whose activity is being stored
    * @param ent entity affected
    * @param par location where <code>ent</code> is being inserted
    * @param cont content being inserted
    */
   private Activity(int uID, Entity ent, Entity par, String cont) {
      user = new User(uID);
      entity = ent;
      parent = par;
      operation = Operation.INSERT;
      content = cont;
   }

   @Override
   public void insert(Connection conn) throws SQLException {
      executeInsert(conn, "INSERT INTO `activities` (" +
        "`user`, `entity`, `parent`, `operation`, `content`" +
        ") VALUES (?, ?, ?, ?, ?)",
        user.getID(), entity.getUniqueID(), parent != null ? parent.getUniqueID() : null, operation.toString(), content);
   }

   @Override
   public void merge(Connection conn, Entity newEnt) throws SQLException, ReflectiveOperationException {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public String toString() {
      return String.format("activity/%d (%s)", id, operation);
   }

   /**
    * Modifying an activity has no effect on any edition.
    * @param conn connection to database
    */
   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
   }
   
   public static void record(Connection conn, int uID, Entity ent, Operation op, String cont) throws SQLException {
      Activity act = new Activity(uID, ent, op, cont);
      act.insert(conn);
      ent.markTopLevelModified(conn);
   }

   public static void record(Connection conn, int uID, Entity ent, Entity par, String cont) throws SQLException {
      Activity act = new Activity(uID, ent, par, cont);
      act.insert(conn);
      if (par != null) {
         par.markTopLevelModified(conn);
      } else {
         ent.markTopLevelModified(conn);
      }
   }
}
