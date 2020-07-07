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
package edu.slu.tradamus.image;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;


/**
 * Class which represents an actual graphic used by a canvas.  Currently, there is
 * a one-to-one mapping between canvasses and images, but a canvas could potentially
 * have multiple images associated with it.
 *
 * @author tarkvara
 */
public class Image extends Entity {
   /**
    * Canvas to which this image belongs.
    */
   private Canvas canvas;

   /**
    * Index of this image within its canvas.
    */
   private int index;

   /**
    * URI for loading this image.
    */
   private URI uri;

   /**
    * Format (mime-type) of the image.  Typically "image/jpeg".
    */
   private Format format;

   /**
    * Pixel width of the image.
    */
   private int width;
   
   /**
    * Pixel height of the image.
    */
   private int height;

   /**
    * Constructor for instantiating from Json.
    */
   public Image() {
   }

   /**
    * Constructor which creates a new image during importing.
    * 
    * @param m canvas to which this image belongs
    */
   public Image(Canvas canv, URI u, Format fmt, int w, int h) {
      canvas = canv;
      uri = u;
      format = fmt;
      width = w;
      height = h;
   }

   /**
    * Wrap an image around an ID.
    *
    * @param imgID 
    */
   public Image(int imgID) {
      id = imgID;
   }

   @JsonProperty("canvas")
   public int getCanvasID() {
      return canvas.getID();
   }

   @JsonProperty("canvas")
   public void setCanvasID(int canvID) {
      if (canvas == null || canvas.getID() != canvID) {
         canvas = new Canvas(canvID);
      }
   }

   public Format getFormat() {
      return format;
   }

   public void setFormat(Format value) {
      format = value;
   }

   public int getIndex() {
      return index;
   }

   public URI getURI() {
      return uri;
   }

   public void setURI(URI u) {
      uri = u;
   }

   /**
    * Set the index of this image within its canvas.
    * @param val the image's index
    */
   public void setIndex(int val) {
      index = val;
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

   @Override
   public void insert(Connection conn) throws SQLException {
      executeInsert(conn, "INSERT INTO `images` (`" +
            "canvas`, `index`, `uri`, `format`, `width`, `height`" +
            ") VALUES(?, ?, ?, ?, ?, ?)",
            canvas.getID(), index, uri.toString(), format.toString(), width, height);
   }

   @Override
   public void merge(Connection conn, Entity newEnt) throws SQLException {
      Image newImg = (Image)newEnt;
      
      if (index != newImg.index || !Objects.equals(uri, newImg.uri) || format != newImg.format || width != newImg.height) {
         // Update top-level fields.
         index = newImg.index;
         uri = newImg.uri;
         format = newImg.format;
         width = newImg.width;
         height = newImg.height;
         executeUpdate(conn, "UPDATE `images` " +
                 "SET `index` = ?, `uri` = ?, `format` = ?, `width` = ?, `height` = ? " +
                 "WHERE `id` = ?",
                 index, uri.toString(), format.toString(), width, height, id);
      }
      newImg.id = id;
   }

   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareCall("UPDATE editions " +
              "LEFT JOIN witnesses ON edition = editions.id " +
              "LEFT JOIN manifests ON witness = witnesses.id " +
              "LEFT JOIN canvasses ON manifest = manifests.id " +
              "LEFT JOIN images ON canvas = canvasses.id " +
              "SET modification = NOW() " +
              "WHERE images.id = ?")) {
         stmt.setInt(1, id);
         stmt.executeUpdate();
      }
   }

   @Override
   public Role checkPermission(Connection conn, int uID, Role required) throws SQLException, PermissionException {
      if (canvas != null) {
         return canvas.checkPermission(conn, uID, required);
      } else {
         try (PreparedStatement stmt = conn.prepareStatement("SELECT manifest FROM canvasses LEFT JOIN images ON canvas = canvasses.id WHERE images.id = ?")) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
               Manifest man = new Manifest(rs.getInt(1));
               return man.checkPermission(conn, uID, required);
            } else {
               throw new NoSuchEntityException(this);
            }
         }
      }
   }

   
   public static Comparator<Image> getMergingComparator() {
      return new Comparator<Image>() {
         @Override
         public int compare(Image o1, Image o2) {
            if (o1.id == o2.id) {
               return 0;
            }
            return o1.getIndex() - o2.getIndex();
         }
      };
   }
   
   public enum Format {
      JPEG,
      PDF,
      PNG,
      TIFF,
      UNKNOWN;
      
      public String toMimeType() {
         switch (this) {
            case JPEG:
               return "image/jpeg";
            case PDF:
               return "application/pdf";
            case PNG:
               return "image/png";
            case TIFF:
               return "image/tiff";
            default:
               return "application/octet-stream";
         }
      }
      
      public static Format fromMimeType(String mime) {
         switch (mime) {
            case "image/jpeg":
               return JPEG;
            case "application/pdf":
            case "application/x-pdf":
            case "image/pdf":
               return PDF;
            case "image/png":
               return PNG;
            case "image/tiff":
               return TIFF;
            default:
               return UNKNOWN;
         }
      }
      
      public static Format fromExtension(String ext) {
         int dotPos = ext.lastIndexOf('.');
         if (dotPos >= 0) {
            ext = ext.substring(dotPos + 1);
         }
         switch (ext) {
            case "jpeg":
            case "jpg":
               return JPEG;
            case "pdf":
               return PDF;
            case "png":
               return PNG;
            case "tiff":
            case "tif":
               return TIFF;
            default:
               return UNKNOWN;
         }
      }
   }
}
