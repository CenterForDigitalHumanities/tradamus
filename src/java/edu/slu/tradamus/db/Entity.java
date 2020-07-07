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
package edu.slu.tradamus.db;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.slu.tradamus.user.Permission;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import static edu.slu.tradamus.util.LangUtils.*;


/**
 * Base class for all entities which can be stored in our database.
 *
 * @author tarkvara
 */
public abstract class Entity {
   /**
    * All entities have an ID column whose value is assigned by MySQL.
    */
   protected int id;

   /**
    * The unique auto-generated ID associated with this entity.
    * @return 
    */
   public int getID() {
      return id;
   }

   /**
    * Get a URI fragment which uniquely locates this entity.  These URIs are generally relative to the
    * server URI.
    */
   @JsonIgnore
   public String getUniqueID() {
      return getClass().getSimpleName().toLowerCase() + "/" + id;
   }

   /**
    * Used for targets whose type is stored in a text field.
    */
   @JsonIgnore
   public String getEntityType() {
      return getClass().getSimpleName().toUpperCase();
   }

   /**
    * Utility function which allows us to pass varargs to an SQL statement which is
    * being used to create an entity.  The entity's ID value will be set to the returned
    * auto-generated primary key.
    *
    * @param stmtText text of prepared statement with place-holders
    * @param args list of arguments to be stuffed into the prepared statement
    * @throws SQLException 
    */
   protected final void executeInsert(Connection conn, String stmtText, Object... args) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement(stmtText, Statement.RETURN_GENERATED_KEYS)) {
         int i = 1;
         for (Object arg: args) {
            stmt.setObject(i++, arg);
         }
         stmt.executeUpdate();

         ResultSet rs = stmt.getGeneratedKeys();
         if (rs.next()) {
            id = rs.getInt(1);
         }
      }
   }

   /**
    * Utility function which uses reflection to load an entity's fields from the specified SQL statement.
    */
   protected final void executeLoad(Connection conn, String stmtText, boolean deep) throws SQLException, ReflectiveOperationException {
      try (PreparedStatement stmt = conn.prepareStatement(stmtText)) {
         stmt.setInt(1, id);
         ResultSet rs = stmt.executeQuery();
         if (!rs.next()) {
            throw new NoSuchEntityException(this);
         }
         loadFields(rs, deep);
      }
   }

   /**
    * Utility method for populating an UPDATE or DELETE statement using varargs.
    * @param conn connection to SQL database
    * @param stmtText text of UPDATE statement
    * @param args values for placeholders
    * @return true if any rows were updated
    */
   public static boolean executeUpdate(Connection conn, String stmtText, Object... args) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement(stmtText)) {
         int i = 1;
         for (Object arg: args) {
            stmt.setObject(i++, arg);
         }
         return stmt.executeUpdate() > 0;
      }
   }

   /**
    * Derived classes must have a load method to populate their contents from the database.  Default
    * behaviour is just to select all columns from the table and depend on loadFields to pull it together.
    *
    * @param conn database connection
    * @param deep if true, the load operation should also load all constituents
    */
   public void load(Connection conn, boolean deep) throws SQLException, ReflectiveOperationException {
      executeLoad(conn, String.format("SELECT * from `%s` WHERE id = ?", TABLE_NAMES.get(getClass().getSimpleName())), deep);
   }

   public void loadFields(ResultSet rs, boolean deep) throws SQLException, ReflectiveOperationException {
      Map<String, Method> setters = getSetters();
		setters.remove("id");	// Don't want setID to load a field.
      for (String key: setters.keySet()) {
         Method meth = setters.get(key);
         String col = camelCaseToUnderscores(key);
         Class argClazz = meth.getParameterTypes()[0];
         if (argClazz.isEnum()) {
            // Special treatment for enums which have to be parsed from string values.
            Method valueMeth = argClazz.getMethod("valueOf", String.class);
            meth.invoke(this, valueMeth.invoke(null, rs.getString(col)));
         } else if (argClazz == Integer.TYPE) {
            meth.invoke(this, rs.getInt(col));
         } else if (argClazz == String.class) {
            meth.invoke(this, rs.getString(col));
         } else if (argClazz == Integer.class) {
            meth.invoke(this, (Integer)rs.getObject(col));
         } else if (argClazz.isPrimitive()) {
            meth.invoke(this, rs.getObject(col));
         } else if (argClazz == List.class || argClazz == Map.class) {
            // We have some collection setters, but these only work for JSON, not for SQL.  The
            // derived class will have to overload loadFields to provide the correct behaviour.
         } else {
            // Class is something like URI, which will need to be constructed.
            // For now, we assume that the object will have a one-argument string constructor.
            Constructor argConstr = argClazz.getConstructor(String.class);
            meth.invoke(this, argConstr.newInstance(rs.getString(col)));
         }
      }
   }

   /**
    * Load a list of child objects.
    * @param <T> type of entity
    * @param conn connection to SQL database
    * @param childTable name of table containing child entities
    * @param parentCol column in child table which links to ID column of parent
    * @param childClazz class of child entity
    * @return a list of child entities associated with this entity
    * @throws SQLException
    */
   public <T extends Entity> List<T> loadChildren(Connection conn, String stmtText, Class<T> childClazz, boolean deep) throws SQLException, ReflectiveOperationException {
      List<T> result = new ArrayList<>();
      try (PreparedStatement stmt = conn.prepareStatement(stmtText)) {
         stmt.setInt(1, id);
         ResultSet rs = stmt.executeQuery();
         while (rs.next()) {
            // Find the constructor which takes an ID as its only parameter.
            Constructor constr = childClazz.getConstructor(Integer.TYPE);
            T t = (T)constr.newInstance(rs.getInt(TABLE_NAMES.get(childClazz.getSimpleName()) + ".id"));
            if (deep) {
               t.load(conn, true);
            } else {
               t.loadFields(rs, false);
            }
            result.add(t);
         }
      }
      return result;
   }

   /**
    * Merge another entity with this one.
    * @param conn connection to database
    * @param newEnt another entity of the same type as the object implementing this method
    */
   public abstract void merge(Connection conn, Entity newEnt) throws IOException, SQLException, PermissionException, ReflectiveOperationException;

   /**
    * Merge two lists of child entities, adding/deleting/updating the database as necessary.
    * @param <T> type of children being merged
    * @param conn connection to SQL database
    * @param oldOnes existing children
    * @param newOnes new children to replace
    * @param comp method for comparing children
    * @param filt filter for making sure we only modify children we're allowed to
    * @param replacing if <code>true</code>, we're overwriting all children
    */
   public <T extends Entity> void mergeChildren(Connection conn, List<T> oldOnes, List<T> newOnes, Comparator<T> comp, PermissionFilter<T> filt, boolean replacing) throws IOException, SQLException, PermissionException, ReflectiveOperationException, IOException {
      int added = 0, deleted = 0, modified = 0, rejected = 0;
      List<T> addedOnes = new ArrayList<>();
      Collections.sort(oldOnes, comp);
      for (T t: newOnes) {
         int oldIndex = Collections.binarySearch(oldOnes, t, comp);
         if (oldIndex >= 0) {
            // Found a match, so call the child's merge method.
            T oldT = oldOnes.get(oldIndex);
            boolean permitted = true;
            if (filt != null) {
               permitted = filt.canModify(oldT);
            }
            if (permitted) {
               oldT.merge(conn, t);
               modified++;
            } else {
               rejected++;
            }
            oldOnes.remove(oldIndex);
         } else {
            addedOnes.add(t);
         }
      }

      if (replacing) {
         // If we're overwriting (rather than just merging), we need to remove the obsolete children.
         for (T t: oldOnes) {
            boolean permitted = true;
            if (filt != null) {
               permitted = filt.canModify(t);
            }
            if (permitted) {
               t.delete(conn);
               deleted++;
            }
         }
      }
      
      // Insert the newly-added children.
      for (T t: addedOnes) {
         t.insert(conn);
      }
      added += addedOnes.size();
      LOG.log(Level.INFO, "{0}.mergeChildren added {1}, deleted {2}, modified {3}, and rejected {4}.", new Object[] { getClass().getName(), added, deleted, modified, rejected });
   }
   
   /**
    * Entity subclasses are free to override this method if they need to do extra cleanup on deletion.
    * In many cases, the clean-up is taken care of by cascaded deletes on foreign keys.
    */
   public boolean delete(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement(String.format("DELETE FROM `%s` WHERE id = ?", TABLE_NAMES.get(getClass().getSimpleName())))) {
         stmt.setInt(1, id);
         return stmt.executeUpdate() > 0;
      }
   }

   /**
    * Every entity subclass must implement an insert method.
    */
   public abstract void insert(Connection conn) throws SQLException, IOException;

   /**
    * Modify an entity based on a map of values, typically drawn from a JSON object.
    * @param conn connection to SQL database
    * @param mods map of column names and values to be modified
    * @return usually null, but derived classes could send back some data as the response
    */
   public Object modify(Connection conn, Map<String, Object> mods) throws SQLException, ReflectiveOperationException, IOException, PermissionException {
      String stmtText = String.format("UPDATE `%s` SET ", TABLE_NAMES.get(getClass().getSimpleName()));
      boolean first = true;

      // We never want to set the object's ID field.
      mods.remove("id");

      for (Map.Entry<String, Object> ent: mods.entrySet()) {
         // We can only process primitive types, not JSON arrays or objects.
         if (!(ent.getValue() instanceof List) && !(ent.getValue() instanceof Map)) {
            if (!first) {
               stmtText += ", ";
            }
            stmtText += String.format("`%s` = ?", camelCaseToUnderscores(ent.getKey()));
            first = false;
         }
      }
      
      // It's possible that there are no primitive fields in the mods, in which case there is nothing to update.
      if (!first) {
         stmtText += " WHERE `id` = ?";
         try (PreparedStatement stmt = conn.prepareStatement(stmtText)) {
            int i = 1;
            for (Map.Entry<String, Object> ent: mods.entrySet()) {
               if (!(ent.getValue() instanceof List) && !(ent.getValue() instanceof Map)) {
                  stmt.setObject(i++, ent.getValue());
               }
            }
            stmt.setInt(i, id);
            stmt.executeUpdate();
         }
      }
      // Base class has nothing to return in the response.
      return null;
   }

   /**
    * Check to see if the given entity already exists in the database.
    *
    * @return true if an entity with the given ID already exists
    * @throws SQLException 
    */
   public boolean exists(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement(String.format("SELECT count(*) from `%s` WHERE id = ?", TABLE_NAMES.get(getClass().getSimpleName())))) {
         stmt.setInt(1, id);
         ResultSet rs = stmt.executeQuery();
         if (rs.next()) {
            return rs.getInt(1) > 0;
         }
      }
      return false;
   }

   /**
    * For activity-logging purposes, fetch the human-readable description of this entity.
    * @return the default, which is just the unique ID
    * @param conn connection to database
    * @throws SQLException 
    */
   public String fetchDescription(Connection conn) throws SQLException {
      return getUniqueID();
   }

   /**
    * Validate this entity for access by the given user.
    * @param conn connection to database
    * @param uID user being validated
    */
   public Role checkPermission(Connection conn, int uID, Role required) throws SQLException, PermissionException {
      throw new PermissionException(this, required);
   }

   /**
    * Utility method which derived classes can use to retrieve the best relevant permission from the given array.
    *
    * @param permissions list of permissions
    * @param uID ID of user to be validated
    * @param checkPublic if true, also check for public user permissions
    * @return the Role which the given user has
    */
   protected Role getBestPermission(List<Permission> permissions, int uID, boolean checkPublic) {
      Role result = null;
      for (Permission perm: permissions) {
         if (perm.getUserID() == uID || (checkPublic && perm.getUserID() == 0)) {
            // Found a permission entry.
            if (result == null || perm.getRole().ordinal() > result.ordinal()) {
               result = perm.getRole();
            }
         }
      }
      return result;
   }

   /**
    * Helper function which derived classes can call if they have an SQL statement which can determine the
    * role for the entity's edition.
    * @param conn connection to database
    * @param stmtText statement to be executed, containing two params:  id and user
    * @param uID user to be validated
    * @return the role which the user has on this entity
    * @throws SQLException 
    */
   protected Role executeGetPermission(Connection conn, String stmtText, int uID) throws SQLException {
      Role result = null;
      try (PreparedStatement stmt = conn.prepareStatement(stmtText)) {
         stmt.setInt(1, id);
         stmt.setInt(2, uID);
         ResultSet rs = stmt.executeQuery();
         while (rs.next()) {
            Role r = Role.valueOf(rs.getString(1));
            if (result == null || r.ordinal() > result.ordinal()) {
               result = r;
            }
         }
      }

      // If query failed to turn up a permission object, that may be because the entity itself didn't exist.
      if (result == null && !exists(conn)) {
         // Entity does not exist.
         throw new NoSuchEntityException(this);
      }
   
      return result;
   }

   /**
    * Whenever an entity is modified, the modification time of the containing Edition should be updated.
    * @param conn connection to database
    */
   public abstract void markTopLevelModified(Connection conn) throws SQLException;

   /**
    * Default behaviour is to get all setters based on reflection and Jackson annotations.
    * @return setter methods for all relevant fields
    */
   @JsonIgnore
   public Map<String, Method> getSetters() {
      return edu.slu.tradamus.util.LangUtils.getSetters(getClass());
   }

   @Override
   public String toString() {
      return getUniqueID();
   }

   protected static final Map<String, String> TABLE_NAMES = new HashMap<>();

   static {
      TABLE_NAMES.put("Activity", "activities");
      TABLE_NAMES.put("Annotation", "annotations");
      TABLE_NAMES.put("Canvas", "canvasses");
      TABLE_NAMES.put("Decision", "annotations");
      TABLE_NAMES.put("Edition", "editions");
      TABLE_NAMES.put("Image", "images");
      TABLE_NAMES.put("Manifest", "manifests");
      TABLE_NAMES.put("Outline", "outlines");
      TABLE_NAMES.put("Page", "pages");
      TABLE_NAMES.put("Parallel", "parallels");
      TABLE_NAMES.put("Permission", "permissions");
      TABLE_NAMES.put("Publication", "publications");
      TABLE_NAMES.put("Rule", "rules");
      TABLE_NAMES.put("Section", "sections");
      TABLE_NAMES.put("Transcription", "transcriptions");
      TABLE_NAMES.put("User", "users");
      TABLE_NAMES.put("Witness", "witnesses");
   }

   /**
    * Do a query against the given entity type.
    *
    * @param conn connection to MySQL
    * @param tbl table name to be enumerated
    * @param fieldNames fields to be retrieved
    * @return map containing retrieved values for <code>fieldNames</code>
    * @throws SQLException 
    */
   public static List<Map<String, Object>> listAll(Connection conn, String tbl, String... fieldNames) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement(String.format("SELECT %s from `%s`", concatenateFieldNames(fieldNames), tbl))) {
         ResultSet rs = stmt.executeQuery();
         List<Map<String, Object>> result = new ArrayList<>();
         while (rs.next()) {
            result.add(extractFields(rs, fieldNames));
         }
         return result;
      }
   }
   
   /**
    * Do a query against the given entity type using a simple <code>WHERE</code> condition.
    *
    * @param conn connection to MySQL
    * @param tbl table name to be enumerated
    * @param whereField field to be used for <code>WHERE</code> condition
    * @param whereValue value of <code>whereField</code> to be used
    * @param fieldNames fields to be retrieved
    * @return maps containing retrieved values for <code>fieldNames</code> for each matching entity
    * @throws SQLException 
    */
   public static List<Map<String, Object>> listWhere(Connection conn, String tbl, String whereField, Object whereValue, String... fieldNames) throws SQLException {
       try (PreparedStatement stmt = conn.prepareStatement(String.format("SELECT %s from `%s` WHERE `%s`=?", concatenateFieldNames(fieldNames), tbl, whereField))) {
         stmt.setObject(1, whereValue);
         ResultSet rs = stmt.executeQuery();
         List<Map<String, Object>> result = new ArrayList<>();
         while (rs.next()) {
            result.add(extractFields(rs, fieldNames));
         }
         return result;
      }
   }
   
   /**
    * Do a query against the given entity type using the specified SQL query.
    *
    * @param conn connection to MySQL
    * @param stmtText query to be executed
    * @param args parameters for prepared statement
    * @return maps containing retrieved values for <code>fieldNames</code> for each matching entity
    * @throws SQLException 
    */
   public static List<Map<String, Object>> listWhere(Connection conn, String stmtText, Object... args) throws SQLException {
       try (PreparedStatement stmt = conn.prepareStatement(stmtText)) {
         for (int i = 0; i < args.length; i++) {
            stmt.setObject(i + 1, args[i]);
         }
         ResultSet rs = stmt.executeQuery();
         String[] fieldNames = getFieldNames(rs);
         List<Map<String, Object>> result = new ArrayList<>();
         while (rs.next()) {
            result.add(extractFields(rs, fieldNames));
         }
         return result;
      }
   }
   
   private static String concatenateFieldNames(String[] fieldNames) {
      return concatenateFieldNames(Arrays.asList(fieldNames));
   }
   
   private static String concatenateFieldNames(Collection<String> fieldNames) {
      boolean first = true;
      StringBuilder buf = new StringBuilder("`");
      for (String f: fieldNames) {
         if (first) {
            first = false;
         } else {
            buf.append(", `");
         }
         buf.append(f);
         buf.append('`');
      }
      return buf.toString();
   }

   /**
    * Extract fields from the result set.  These are should be the same field names which were passed
    * to <code>concatenateFieldNames</code> to specify the columns being <code>SELECT</code>ed.  Since some
    * of the fields may include table specifications, the map may need some cleanup afterwards.
    * map.
    * @param rs results of SQL query (one row assumed)
    * @param fieldNames names of SQL columns which were <code>SELECT</code>ed
    * @return
    * @throws SQLException 
    */
   protected static Map<String, Object> extractFields(ResultSet rs, String... fieldNames) throws SQLException {
      Map<String, Object> result = new HashMap<>();
      for (int i = 0; i < fieldNames.length; i++) {
         result.put(fieldNames[i], rs.getObject(i + 1));
      }
      return result;
   }
   
   private static String[] getFieldNames(ResultSet rs) throws SQLException {
      ResultSetMetaData md = rs.getMetaData();
      String[] result = new String[md.getColumnCount()];
      for (int i = 0; i < result.length; i++) {
         result[i] = md.getColumnLabel(i + 1);
      }
      return result;
   }

   protected static boolean compareIDs(Entity ent1, Entity ent2) {
      if (ent1 == null) {
         return ent2 == null;
      }
      if (ent2 == null) {
         return false;
      }
      return ent1.id == ent2.id;
   }

   private static final Logger LOG = Logger.getLogger(Entity.class.getName());
}
