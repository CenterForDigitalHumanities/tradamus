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

import java.awt.Rectangle;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.text.Page;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import edu.slu.tradamus.util.JsonUtils;


/**
 * A logical surface onto which annotations can be positioned.
 *
 * @author tarkvara
 */
public class Canvas extends Entity {
   /**
    * Manifest to which this canvas belongs.
    */
   private Manifest manifest;

   /**
    * Index of this canvas within its manifest.
    */
   private int index;

   /**
    * Page (if any) which corresponds to this canvas.
    */
   private Page page;

   /**
    * Title of this canvas.
    */
   private String title;

   /**
    * Logical width of the canvas.  May be different from image width.
    */
   private int width;
   
   /**
    * Logical height of the canvas.  May be different from image height.
    */
   private int height;

   /**
    * For now, we only allow one image per canvas, but in theory there could be more.
    */
   private List<Image> images = new ArrayList<>();

   /**
    * Line annotations associated with this canvas.
    */
   private List<Annotation> lines = new ArrayList<>();

   /**
    * Constructor for instantiating from Json.
    */
   public Canvas() {
   }

   /**
    * Constructor which creates a new canvas during importing.
    * 
    * @param m Manifest to which this Canvas belongs
    * @param w logical width of canvas
    * @param h logical height of canvas
    */
   public Canvas(Manifest m, int w, int h) {
      manifest = m;
      width = w;
      height = h;
   }
   
   /**
    * Wrap a bare-bones canvas object around an ID.
    *
    * @param canvID canvas ID
    */
   public Canvas(int canvID) {
      id = canvID;
   }

   @JsonProperty("manifest")
   public int getManifestID() {
      return manifest.getID();
   }

   @JsonProperty("manifest")
   public void setManifestID(int manID) {
      if (manifest == null || manifest.getID() != manID) {
         manifest = new Manifest(manID);
      }
   }

   public List<Image> getImages() {
      return images;
   }

   /**
    * Retrieve the index of this canvas within its manifest.
    * @return the canvas' index
    */
   public int getIndex() {
      return index;
   }

   /**
    * Set the index of this canvas within its manifest.
    * @param val the canvas' index
    */
   public void setIndex(int val) {
      index = val;
   }

   /**
    * Retrieve the page which corresponds to this canvas.
    * @return page (if any) corresponding to this canvas
    */
   @JsonIgnore
   public Page getPage() {
      return page;
   }

   /**
    * Set the page which corresponds to this canvas.
    *
    * @param pg page corresponding to this canvas
    * @param notes list of T-PEN notes attached to the lines (possibly null)
    */
   public void bindPage(Page pg, List<Annotation> notes) {
      page = pg;
      page.setCanvas(this);
      page.setTitle(title);
      
      int start = 0;
      for (Annotation line: lines) {
         String content = line.getContent();
         line.setTextAnchor(page, start, page, start + content.length());
         page.addLine(line);
         page.appendText(content + '\n');
         
         if (notes != null) {
            // If we have any T-PEN notes, make sure they have the same text anchor as the associated line.
            for (Annotation n: notes) {
               if (n.getTarget() == line) {
                  n.setTextAnchor(page, start, page, start + content.length());
               }
            }
         }
         start += content.length() + 1;
      }
   }

   /**
    * Used as part of JSON deserialisation when we want to make sure our Canvas links to an actual Page.
    * @param pg real page being linked to
    */
   void setPage(Page pg) {
      page = pg;
   }

   @JsonProperty("page")
   public Integer getPageID() {
      return page != null ? page.getID() : null;
   }

   @JsonProperty("page")
   public void setPageID(Integer pgID) {
      if (pgID != null) {
         if (page == null || page.getID() != pgID) {
            page = new Page(pgID);
         }
      } else {
         page = null;
      }
   }

   /**
    * Add a line annotation to the canvas.
    *
    * @param cont content of the line being added
    * @param frag fragment describing the bounds of the line being added
    */
   public Annotation createLine(String cont, String frag) {
      Annotation l = new Annotation("line", cont, this, frag);
      lines.add(l);
      return l;
   }

   /**
    * Add an image to the canvas.
    *
    * @param img the image to be added
    */
   public void addImage(Image img) {
      img.setIndex(images.size());
      images.add(img);
   }

   /**
    * Lines which appear on this canvas.  Not used internally, but part of our JSON representation.
    * @return the canvas' lines
    */
   public List<Annotation> getLines() {
      return lines;
   }

   /**
    * Set the lines which should appear on this canvas.  Used for JSON deserialisation.
    * @param value new lines
    */
   public void setLines(List<Annotation> value) {
      lines = value;
   }

   /**
    * Retrieve the human-readable title of this canvas.
    * @return the canvas' title
    */
   public String getTitle() {
      return title;
   }

   /**
    * Set the human-readable title of this canvas.
    * @param t the canvas' new title
    */
   public void setTitle(String t) {
      title = t;
   }

   
   public int getWidth() {
      return width;
   }
   
   public void setWidth(int w) {
      width = w;      
   }
   
   public int getHeight() {
      return height;
   }
   
   public void setHeight(int h) {
      height = h;
   }

   /**
    * Insert the data for this canvas into the database.  Assumes that the associated page (if
    * any) has already been inserted.  The current code in Witness.insert enforces this by
    * inserting the transcription before the manifest.
    *
    * @param conn connection to our database
    */
   @Override
   public void insert(Connection conn) throws SQLException {
      id = manifest.getID() * 1000 + index;
      executeInsert(conn, "INSERT INTO `canvasses` (" +
            "`id`, `manifest`, `index`, `page`, `title`, `width`, `height`" +
            ") VALUES(?, ?, ?, ?, ?, ?, ?)",
            id, manifest.getID(), index, getPageID(), title, width, height);

      int i = 0;
      for (Image img: images) {
         img.setCanvasID(id);
         img.setIndex(i++);
         img.insert(conn);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void load(Connection conn, boolean deep) throws SQLException, ReflectiveOperationException {
      executeLoad(conn, "SELECT * FROM `canvasses` " +
         "WHERE canvasses.id = ?", deep);

      lines = loadChildren(conn, SELECT_LINES, Annotation.class, false);
      images = loadChildren(conn, SELECT_IMAGES, Image.class, false);
   }

   @Override
   public void merge(Connection conn, Entity newEnt) throws IOException, SQLException, ReflectiveOperationException, PermissionException {
      Canvas newCanv = (Canvas)newEnt;

      // Update top-level fields.
      if (index != newCanv.index || !Objects.equals(page, newCanv.page) || !Objects.equals(title, newCanv.title) || width != newCanv.width || height != newCanv.height) {
         index = newCanv.index;
         page = newCanv.page;
         title = newCanv.title;
         width = newCanv.width;
         height = newCanv.height;

         executeUpdate(conn, "UPDATE `canvasses` " +
                 "SET `index` = ?, `page` = ?, `title` = ?, `width` = ?, `height` = ? " +
                 "WHERE `id` = ?",
                 index, page.getID(), title, width, height, id);
      }
      newCanv.id = id;

      // Merge our images.
      mergeChildren(conn, images, newCanv.images, Image.getMergingComparator(), null, true);
   }

   /**
    * Check the user's access to this canvas, which is based on the user's access to the manifest and
    * edition.
    * @param conn connection to SQL database
    * @param uID user whose access is being checked
    * @param r role which is being required
    */
   @Override
   public Role checkPermission(Connection conn, int uID, Role r) throws SQLException, PermissionException {
      if (manifest == null) {
         try (PreparedStatement stmt = conn.prepareStatement("SELECT manifest FROM canvasses WHERE id = ?")) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
               manifest = new Manifest(rs.getInt(1));
            } else {
               throw new NoSuchEntityException(this);
            }
         }
      }
      return manifest.checkPermission(conn, uID, r);
   }

   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareCall("UPDATE editions " +
              "LEFT JOIN witnesses ON edition = editions.id " +
              "LEFT JOIN manifests ON witness = witnesses.id " +
              "LEFT JOIN canvasses ON manifest = manifests.id " +
              "SET modification = NOW() " +
              "WHERE canvasses.id = ?")) {
         stmt.setInt(1, id);
         stmt.executeUpdate();
      }
   }

   public static final String SELECT_ANNOTATIONS = "SELECT * FROM annotations " +
           "WHERE canvas = ? " +
           "ORDER BY start_offset";

   public static final String DELETE_ANNOTATIONS = "DELETE FROM `annotations` " +
           "WHERE canvas = ?";

   public static final String SELECT_IMAGES = "SELECT * FROM `images` WHERE `canvas` = ?";

   public static final String SELECT_LINES = "SELECT * FROM `annotations` " +
           "WHERE canvas = ? AND type = 'line' " +
           "ORDER BY start_page, start_offset";

   public static Comparator<Canvas> getIndexComparator() {
      return new Comparator<Canvas>() {
         @Override
         public int compare(Canvas o1, Canvas o2) {
            return o1.getIndex() - o2.getIndex();
         }
      };
   }

   public static Comparator<Annotation> getRowComparator() {
      return new Comparator<Annotation>() {
         @Override
         public int compare(Annotation o1, Annotation o2) {
            Canvas canv1 = o1.getCanvas();
            Canvas canv2 = o2.getCanvas();
            int diff = canv1.getID() - canv2.getID();
            if (diff == 0) {
               Rectangle bounds1 = JsonUtils.getXYWH(o1.getCanvasURI());
               Rectangle bounds2 = JsonUtils.getXYWH(o2.getCanvasURI());
               diff = bounds1.x - bounds2.x;
               if (diff == 0) {
                  // Column positions are the same.  Sort based on y-positions.
                  diff = bounds1.y - bounds2.y;
               }
            }
            return diff;
         }
      };
   }
}
