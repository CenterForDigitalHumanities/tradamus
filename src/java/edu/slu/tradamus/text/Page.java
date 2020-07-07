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
package edu.slu.tradamus.text;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import name.fraser.neil.plaintext.*;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.image.Canvas;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;


/**
 * A page full of text.
 *
 * @author tarkvara
 */
public class Page extends Entity {
   /**
    * Transcription to which this page belongs.
    */
   private Transcription transcription;

   /**
    * Index of this page within the transcription.
    */
   private int index;

   /**
    * Canvas which corresponds to the text on this page.  May be null.
    */
   private Canvas canvas;

   /**
    * The human-friendly title of the page.
    */
   private String title;

   /**
    * The entire text of the page.
    */
   private String text = "";

   /**
    * List of all lines stored on this page.
    */
   List<Annotation> lines = new ArrayList<>();

   /**
    * Constructor for instantiating from Json.
    */
   public Page() {
   }

   /**
    * Constructor which creates a new page during importing of text-anchored content.
    * 
    * @param transcr the transcription to which this page belongs
    */
   public Page(Transcription transcr) {
      transcription = transcr;
   }
   
   /**
    * Construct a page with all its fields populated.
    *
    * @param transcr transcription to which this page belongs (possibly null)
    * @param pgID the page's ID
    * @param i page's index within transcription
    * @param t title
    * @param cont text content
    */
   public Page(Transcription transcr, int pgID, int i, String t, String cont) {
      transcription = transcr;
      id = pgID;
      index = i;
      title = t;
      text = cont;
   }

   /**
    * Wrap a bare-bones page around a page ID.
    */
   public Page(int pgID) {
      id = pgID;
   }

   /**
    * Public access to the transcription which owns this page.
    */
   @JsonIgnore
   public Transcription getTranscription() {
      return transcription;
   }

   /**
    * Retrieve ID of the transcription which owns this page.  Typically used when serialising to JSON.
    */
   @JsonProperty("transcription")
   public int getTranscriptionID() {
      return transcription.getID();
   }

   /**
    * Assign ID of the transcription which owns this page.  Typically used when deserialising from JSON.
    */
   @JsonProperty("transcription")
   public void setTranscriptionID(int transcrID) {
      if (transcription == null || transcription.getID() != transcrID) {
         transcription = new Transcription(transcrID);
      }
   }

   /**
    * Retrieve the index of this page within the transcription.
    */
   public int getIndex() {
      return index;
   }

   /**
    * Assign the index of this page within the transcription.
    */
   public void setIndex(int val) {
      index = val;
   }

   /**
    * Retrieve the full text of this page.
    */
   public String getText() {
      return text;
   }

   /**
    * Assign the full text of this page.
    */
   public void setText(String val) {
      text = val;
   }

   /**
    * During import, append text to this page.
    *
    * @param s the text to be appended
    */
   public void appendText(String s) {
      text += s;
   }

   /**
    * Retrieve the title of this page.
    */
   public String getTitle() {
      return title;
   }

   /**
    * Assign the title of this page.
    */
   public void setTitle(String val) {
      title = val;
   }

   public List<Annotation> getLines() {
      return lines;
   }

   public void setLines(List<Annotation> value) {
      lines = value;
   }

   /**
    * Add a line (and its associated annotation) to the page.
    *
    * @param l 
    */
   public void addLine(Annotation l) {
      lines.add(l);
   }

   /**
    * Create a line annotation and add it to this page.
    *
    * @param start line's start offset in the page's text
    * @paran end line's end offset in the page's text
    */
   public Annotation createLine(int start, int end) {
      Annotation l = new Annotation("line", getText().substring(start, end), this, start, this, end);
      addLine(l);
      return l;
   }

   @JsonIgnore
   public Canvas getCanvas() {
       return canvas;
   }

   @JsonIgnore
   public void setCanvas(Canvas canv) {
      canvas = canv;
   }

   @JsonProperty("canvas")
   public Integer getCanvasID() {
      return canvas != null ? canvas.getID() : null;
   }

   @JsonProperty("canvas")
   public void setCanvasID(Integer canvID) {
      if (canvID != null) {
         if (canvas == null || canvas.getID() != canvID) {
            canvas = new Canvas(canvID);
         }
      } else {
         canvas = null;
      }
   }

   /**
    * Insert the data for this page into the database.
    *
    * @param conn connection to our database
    */
   @Override
   public void insert(Connection conn) throws SQLException {
      id = transcription.getID() * 1000 + index;
      executeInsert(conn, "INSERT INTO `pages` (" +
           "`id`, `transcription`, `index`, `title`, `text`" +
           ") VALUES(?, ?, ?, ?, ?)",
           id, transcription.getID(), index, title, text);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void load(Connection conn, boolean deep) throws SQLException, ReflectiveOperationException {
      // Full load, lines and all.  We avoid the join because we'll be doing a join in the next line.
      executeLoad(conn, "SELECT transcription, pages.index, title, text, canvas FROM pages " +
         "WHERE pages.id = ?", true);
      lines = loadChildren(conn, SELECT_LINES, Annotation.class, false);
   }

   /**
    * Merge the contents of a new page with this one.
    * @param conn connection to database
    * @param newEnt page being merged
    */
   @Override
   public void merge(Connection conn, Entity newEnt) throws SQLException, ReflectiveOperationException, IOException, PermissionException {
      Page newPg = (Page)newEnt;

      // Update the top-level fields.
      if (!Objects.equals(title, newPg.title) || !Objects.equals(text, newPg.text)) {
         adjustAnnotations(conn, newPg.text);
         title = newPg.title;
         text = newPg.text;
         executeUpdate(conn, "UPDATE `pages` SET `title` = ?, `text` = ? " +
           "WHERE id = ?", title, text, id);
      }
      newPg.id = id;
   }

   @Override
   public Object modify(Connection conn, Map<String, Object> mods) throws SQLException, ReflectiveOperationException, IOException, PermissionException {
      load(conn, false);
      Object response = null;
      if (!Objects.equals(text, mods.get("text"))) {
         // Text has changed, which means we may need to adjust our annotations.
         response = adjustAnnotations(conn, (String)mods.get("text"));
      }

      super.modify(conn, mods);
      return response;
   }

   @Override
   public void loadFields(ResultSet rs, boolean deep) throws SQLException, ReflectiveOperationException {
      super.loadFields(rs, deep);
      if (!deep) {
         // If we're just loading line IDs, we pull them out of the join.
         lines = new ArrayList<>();
         do {
            int pgID = rs.getInt("pages.id");
            if (pgID != id) {
               // Have rolled into the next Page.
               rs.previous();
               break;
            }
            int lID = rs.getInt("annotations.id");
            if (lID > 0) {
               lines.add(new Annotation(lID));
            }
         } while (rs.next());
      }
   }

   @Override
   public Role checkPermission(Connection conn, int uID, Role r) throws SQLException, PermissionException {
      if (transcription == null) {
         try (PreparedStatement stmt = conn.prepareStatement("SELECT transcription FROM pages WHERE id = ?")) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
               transcription = new Transcription(rs.getInt(1));
            } else {
               throw new NoSuchEntityException(this);
            }
         }
      }
      return transcription.checkPermission(conn, uID, r);
   }

   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareCall("UPDATE editions " +
              "LEFT JOIN witnesses ON edition = editions.id " +
              "LEFT JOIN transcriptions ON witness = witnesses.id " +
              "LEFT JOIN pages ON transcription = transcriptions.id " +
              "SET modification = NOW() " +
              "WHERE pages.id = ?")) {
         stmt.setInt(1, id);
         stmt.executeUpdate();
      }
   }

   /**
    * When the page's text is going to change, we need to modify all annotations whose offsets may have
    * changed.
    * @param newText new text of the page
    */
   private List<Map<String, Object>> adjustAnnotations(Connection conn, String newText) throws SQLException, ReflectiveOperationException, IOException, PermissionException {
      List<Map<String, Object>> changedAnns = new ArrayList<>();
      List<Annotation> oldAnns = loadChildren(conn, "SELECT * FROM annotations " +
              "WHERE ? IN (start_page, end_page) ORDER BY start_page, start_offset",
              Annotation.class, false);
      diff_match_patch differ = new diff_match_patch();
      List<Diff> diffs = differ.diff_main(text, newText);
      
      for (Annotation ann: oldAnns) {
         Map<String, Object> adjustments = getAnnotationAdjustments(ann, diffs);
         String oldContent = ann.getContent();
         int oldStart = ann.getStartOffset();
         int oldEnd = ann.getEndOffset();
         String newContent = null;
         int newStart = oldStart;
         if (adjustments.containsKey("startOffset")) {
            newStart = (Integer)adjustments.get("startOffset");
         }
         int newEnd = oldEnd;
         if (adjustments.containsKey("endOffset")) {
            newEnd = (Integer)adjustments.get("endOffset");
         }
         if (ann.getStartPageID() == id) {
            if (ann.getEndPageID() == id) {
               // Annotation is entirely on this page.
               newContent = newText.substring(newStart, newEnd);
            } else {
               // Annotation starts on this page, but ends on a subsequent one.
               if (oldContent != null) {
                  newContent = newText.substring(newStart) + oldContent.substring(text.length() - oldStart);
               }
            }
         } else {
            // Annotation ends on this page, but starts on a preceding one.
            if (oldContent != null) {
               newContent = oldContent.substring(0, oldContent.length() - oldEnd) + newText.substring(0, newEnd);
            }
         }
         if (!Objects.equals(oldContent, newContent)) {
            adjustments.put("content", newContent);
         }
         if (!adjustments.isEmpty()) {
            ann.modify(conn, adjustments);
            adjustments.put("id", ann.getID());
            changedAnns.add(adjustments);
         }
      }
      return changedAnns;
   }
   
   /**
    * 
    * @param ann the annotation to be adjusted
    * @param diffs list of diffs returned by diff_match_patch class
    * @return map indicating the fields which need to be adjusted
    */
   private Map<String, Object> getAnnotationAdjustments(Annotation ann, List<Diff> diffs) {
      Map<String, Object> result = new HashMap<>();
      int newStart = 0, newEnd = 0;
      int oldStart, oldEnd;

      if (ann.getStartPageID() == id) {
         // Annotation starts part-way up this page.
         newStart = ann.getStartOffset();
         oldStart = newStart;
      } else {
         // Annotation starts on a preceding page.
         oldStart = Integer.MIN_VALUE;
      }
      if (ann.getEndPageID() == id) {
         // Annotation ends on this page.
         newEnd = ann.getEndOffset();
         oldEnd = newEnd;
      } else {
         // Annotation ends on a subsequent page.
         oldEnd = Integer.MAX_VALUE;
      }

      // Position of diff relative to old text.
      int diffPos = 0;

      // True if diffPos is before start of the annotation.
      boolean beforeStart = oldStart > 0;

      for (Diff d: diffs) {
         int len = d.text.length();
         switch (d.operation) {
            case DELETE:
               if (beforeStart) {
                  newStart -= len;
               }
               newEnd -= len;
               diffPos += len;
               break;
            case EQUAL:
               diffPos += len;
               break;
            case INSERT:
               if (beforeStart) {
                  newStart += len;
               }
               newEnd += len;
               break;
         }
         if (diffPos >= oldEnd || (!beforeStart && oldEnd == Integer.MAX_VALUE)) {
            // Subsequent diffs fall after the end of the annotation.  We can break out of the loop.
            break;
         }
      }
      if (oldStart != Integer.MIN_VALUE) {
         if (newStart != oldStart) {
            result.put("startOffset", newStart);
         }
      }
      if (oldEnd != Integer.MAX_VALUE) {
         if (newEnd != oldEnd) {
            result.put("endOffset", newEnd);
         }
      }
      return result;
   }

   /**
    * Since the transcription is inserted before the manifest, any canvasses which
    * are associated with pages won't yet have IDs assigned.  This connects them
    * together.
    * @param conn connection to our SQL database
    */
   public void updateCanvasID(Connection conn) throws SQLException {
      if (canvas != null) {
         try (PreparedStatement stmt = conn.prepareStatement("UPDATE `pages` SET `canvas` = ? WHERE `id` = ?")) {
            stmt.setInt(1, canvas.getID());
            stmt.setInt(2, id);
            stmt.executeUpdate();
         }
         try (PreparedStatement stmt = conn.prepareStatement("UPDATE `annotations` SET `canvas` = ? WHERE `start_page` = ?")) {
            stmt.setInt(1, canvas.getID());
            stmt.setInt(2, id);
            stmt.executeUpdate();
         }
      }
   }

   public static final String SELECT_LINES = "SELECT * FROM `annotations` " +
                 "WHERE `start_page` = ? AND `type` = 'line' " +
                 "ORDER BY `start_offset`";
   
   public static final String SELECT_ANNOTATIONS_ON_PAGE = "SELECT * FROM annotations " +
           "WHERE ? BETWEEN start_page AND end_page";

   public static final String DELETE_ANNOTATIONS_ON_PAGE = "DELETE FROM annotations " +
           "WHERE ? BETWEEN start_page AND end_page";

   private static final Logger LOG = Logger.getLogger(Page.class.getName());
   
   public static Comparator<Page> getIndexComparator() {
      return new Comparator<Page>() {
         @Override
         public int compare(Page o1, Page o2) {
            return o1.getIndex() - o2.getIndex();
         }
      };
   }
}
