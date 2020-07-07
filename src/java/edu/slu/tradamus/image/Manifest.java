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
package edu.slu.tradamus.image;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.text.Page;
import edu.slu.tradamus.user.Permission;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import edu.slu.tradamus.witness.Witness;


/**
 * In our schema, a manifest is a collection of canvasses.  The IIIF manifest entry
 * corresponds to what we call a witness.
 *
 * @author tarkvara
 */
public class Manifest extends Entity {
   /**
    * Witness to which the manifest belongs.
    */
   private Witness witness;
   
   /**
    * Ordered list of canvasses in this manifest.
    */
   private List<Canvas> canvasses = new ArrayList<>();

   /**
    * List of permissions associated with this Transcription; these place additional restrictions on and
    * above the restrictions imposed by the Edition.
    */
   private List<Permission> permissions;

   /**
    * Constructor for instantiating from JSON.
    */
   public Manifest() {
   }

   /**
    * Construct a blank manifest belonging to the given witness.
    *
    * @param w the Witness which owns this Manifest
    */
   public Manifest(Witness w) {
      witness = w;
   }

   /**
    * Wrap a bare-bones manifest around an ID.
    */
   public Manifest(int manID) {
      id = manID;
   }

   public List<Canvas> getCanvasses() {
      return canvasses;
   }
   
   public void setCanvasses(List<Canvas> value) {
      canvasses = value;
   }

   /**
    * Append the given canvas to this manifest
    * @param c the canvas to be added
    */
   public void addCanvas(Canvas c) {
      c.setIndex(canvasses.size());
      canvasses.add(c);
   }

   /**
    * Additional permissions associated with this Manifest, adding restrictions beyond those imposed
    * by the Edition-level permissions.
    */
   public List<Permission> getPermissions() {
      return permissions;
   }

   @JsonProperty("witness")
   public int getWitnessID() {
      return witness.getID();
   }

   @JsonProperty("witness")
   public void setWitnessID(int witID) {
      if (witness == null || witness.getID() != witID) {
         witness = new Witness(witID);
      }
   }

   /**
    * As part of the JSON import process, make sure we link to actual Pages which are part of the
    * import, and not just to place-holder pages which have been wrapped around an ID.
    *
    * @param pages pages which have been imported (with their original IDs)
    */
   public void fixTargets(List<Page> pages) {
      for (Canvas c: canvasses) {
         for (Page p: pages) {
            if (c.getPageID() == p.getID()) {
               c.setPage(p);
               break;
            }
         }
      }
   }

   /**
    * Insert the manifest into the SQL database.
    */
   @Override
   public void insert(Connection conn) throws SQLException {
      executeInsert(conn, "INSERT INTO `manifests` (`witness`) VALUES(?)", witness.getID());

      int i = 0;
      for (Canvas canv: canvasses) {
         canv.setManifestID(id);
         canv.setIndex(i++);
         canv.insert(conn);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void load(Connection conn, boolean deep) throws SQLException, ReflectiveOperationException {
      if (deep) {
         executeLoad(conn, "SELECT witness FROM manifests " +
              "WHERE manifests.id = ?", true);
         canvasses = loadChildren(conn, "SELECT * FROM canvasses WHERE manifest = ?", Canvas.class, true);
      } else {
         executeLoad(conn, "SELECT witness, canvasses.id FROM manifests " +
              "LEFT JOIN canvasses ON manifest = manifests.id " +
              "WHERE manifests.id = ?", false);
      }
      permissions = loadChildren(conn, SELECT_PERMISSIONS_WITH_USERS, Permission.class, deep);
   }

   @Override
   public void loadFields(ResultSet rs, boolean deep) throws SQLException, ReflectiveOperationException {
      super.loadFields(rs, deep);
      if (!deep) {
         // If we're doing a shallow load, we can just pull the canvas IDs out of the join
         canvasses = new ArrayList<>();
         do {
            int canvID = rs.getInt("canvasses.id");
            if (canvID > 0) {
               canvasses.add(new Canvas(canvID));
            }
         } while (rs.next());
      }
   }

   /**
    * Merge the given manifest and its constituents into the database.
    * @param conn connection to SQL database
    * @param man manifest being merged
    */
   @Override
   public void merge(Connection conn, Entity newEnt) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Manifest newMan = (Manifest)newEnt;
      
      // This is where we would modify the top-level fields, but currently Manifests have none.
      newMan.id = id;

      // Merge all our canvasses.
      mergeChildren(conn, canvasses, newMan.canvasses, Canvas.getIndexComparator(), null, true);
   }

   @Override
   public Role checkPermission(Connection conn, int uID, Role required) throws SQLException, PermissionException {
      // Check edition-level permissions
      Role r = executeGetPermission(conn, SELECT_EDITION_LEVEL_ROLE, uID);
      if (r == null || r.ordinal() < required.ordinal()) {
         throw new PermissionException(this, required);
      }
      // Passed the Edition-level check, so verify the Manifest permissions.
      Role r2 = getManifestPermission(conn, uID);
      if (r2 != null) {
         // Manifest-level role supersedes edition-level role.
         if (r2.ordinal() < required.ordinal()) {
            throw new PermissionException(this, required);
         }
         if (r2.ordinal() < r.ordinal()) {
            r = r2;
         }
      }
      return r;
   }
   
   /**
    * Called to retrieve the manifest-level permissions.
    * @param conn connection to SQL database
    * @param uID user whose access is being checked
    */
   public Role getManifestPermission(Connection conn, int uID) throws SQLException {
      if (permissions == null) {
         try {
            permissions = loadChildren(conn, SELECT_PERMISSIONS_WITH_USERS, Permission.class, false);
         } catch (ReflectiveOperationException ex) {
            LOG.log(Level.WARNING, "Should never happen, since loading of Permissions works.", ex);
         }
      }
      return getBestPermission(permissions, uID, false);
   }

   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareCall("UPDATE editions " +
              "LEFT JOIN witnesses ON edition = editions.id " +
              "LEFT JOIN manifests ON witness = witnesses.id " +
              "SET modification = NOW() " +
              "WHERE manifests.id = ?")) {
         stmt.setInt(1, id);
         stmt.executeUpdate();
      }
   }

   private static final Logger LOG = Logger.getLogger(Manifest.class.getName());

   public static final String SELECT_CANVASSES_WITH_IMAGES = "SELECT * FROM canvasses " +
         "LEFT JOIN images ON canvas = canvasses.id " +
         "WHERE manifest = ? ORDER BY canvasses.index, images.index";

   public static final String SELECT_EDITION_LEVEL_ROLE = "SELECT role FROM permissions " +
         "LEFT JOIN witnesses ON witnesses.edition = permissions.target " +
         "LEFT JOIN manifests ON manifests.witness = witnesses.id " +
         "WHERE target_type = 'EDITION' AND manifests.id = ? AND (user = ? OR user = 0)";

   /**
    * Query for loading Manifest permissions along with user name and email.
    */
   public static final String SELECT_PERMISSIONS_WITH_USERS = "SELECT permissions.id AS id, target_type, target, role, " +
         "user, name, mail FROM permissions " +
         "JOIN users ON user = users.id " +
         "WHERE target_type = 'MANIFEST' AND target = ?";   
}
