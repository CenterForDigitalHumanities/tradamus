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
package edu.slu.tradamus.annotation;

import java.awt.Rectangle;
import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.edition.Edition;
import edu.slu.tradamus.edition.Outline;
import edu.slu.tradamus.edition.Parallel;
import edu.slu.tradamus.image.Canvas;
import edu.slu.tradamus.image.Manifest;
import edu.slu.tradamus.text.Page;
import edu.slu.tradamus.text.TextRange;
import edu.slu.tradamus.text.Transcription;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import static edu.slu.tradamus.util.JsonUtils.formatDate;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.JsonUtils.getXYWH;
import edu.slu.tradamus.witness.Witness;


/**
 * Class which represents a single annotation on some other entity.  May be anchored to a page location,
 * to a canvas location, or to an entity with associated selector.
 * @author tarkvara
 */
public class Annotation extends Entity implements TextRange {
   // Text-based anchor.
   private Page startPage;
   private int startOffset;
   private Page endPage;
   private int endOffset;

   // Image-based anchor.
   private Canvas canvas;
   private String canvasFragment;

   // General entity target.
   private Entity target;
   private String targetFragment;

   /**
    * The type of the annotation, as given by the original source.
    */
   private String type;

   /**
    * The content of the annotation.
    */
   private String content;

   /**
    * Attributes associated with the annotation.
    */
   private Map<String, String> attributes;

   /**
    * Tags associated with the annotation, used for grouping.
    */
   private String tags;

   /** ID of user who last modified this annotation. */
   private int modifiedBy;

   /** ID of user who has granted approval to this annotation. */
   private int approvedBy;

   /** Date/time of this annotation's last modification. */
   private Date modification;

   /**
    * Create a text-anchored annotation.
    *
    * @param pg0
    * @param offset0
    * @param pg1
    * @param offset1 
    */
   public Annotation(String t, String cont, Page pg0, int offset0, Page pg1, int offset1) {
      this(t, cont, pg0, offset0);
      endPage = pg1;
      endOffset = offset1;
   }
   
   /**
    * Constructor to create a text-anchored annotation where we don't yet know the end.
    * Used during XML importing.
    *
    * @param t the type of the annotation
    * @param cont content of the annotation, typically empty for non-Line Annotations
    * @param p the start page of the annotation
    * @param offset the offset of the start of the annotation within that page.
    */
   public Annotation(String t, String cont, Page p, int offset) {
      type = t;
      content = cont;
      startPage = p;
      startOffset = offset;
   }
   
   /**
    * Constructor to create an unanchored annotation which will be anchored later.
    */
   private Annotation(String t) {
      type = t;
   }

   /**
    * Constructor to create an image-anchored annotation.
    * @param t original type of the annotation
    * @param cont content of the annotation
    * @param canv canvas to which this annotation is anchored
    * @param b bounding rectangle of the annotation
    */
   public Annotation(String t, String cont, Canvas canv, String frag) {
      type = t;
      content = cont;
      canvas = canv;
      canvasFragment = frag;
   }

   /**
    * Constructor to anchor an annotation to any kind of entity.  Used for creating witness and edition
    * metadata.
    *
    * @param t original type of the annotation
    * @param cont content of the annotation
    * @param ent entity to which this annotation is anchored
    * @param frag selector specifying a subregion within the entity
    */
   public Annotation(String t, String cont, Entity ent, String frag) {
      type = t;
      content = cont;
      target = ent;
      targetFragment = frag;
   }

   /**
    * Niladic constructor for JSON deserialisation.
    */
   public Annotation() {
   }

   /**
    * Wrap an annotation object around an ID.
    *
    * @param annID annotation ID
    */
   public Annotation(int annID) {
      id = annID;
   }

   /**
    * Retrieve the original type of the annotation, as defined outside Tradamus.
    */
   public String getType() {
      return type;
   }

   /**
    * Assign the original type of the annotation, as defined outside Tradamus.
    * @param t the annotation's type
    */
   public void setType(String t) {
       type = t;
   }

   /**
    * Get the text content of the annotation
    * @return the annotation's text
    */
   public String getContent() {
      return content;
   }

   /**
    * Set the text content of the annotation
    * @param t the new text content
    */
   public void setContent(String t) {
      content = t;
   }

   public int getModifiedBy() {
      return modifiedBy;
   }

   public void setModifiedBy(int u) {
      modifiedBy = u;
   }

   protected void copyDecisionFields(Annotation ann0) {
      id = ann0.id;
      content = ann0.content;
      attributes = ann0.attributes;
      tags = ann0.tags;
      approvedBy = ann0.approvedBy;
      modifiedBy = ann0.modifiedBy;
      modification = ann0.modification;
   }

   public String getModification() {
      return formatDate(modification);
   }

   @JsonIgnore
   public Date getModificationDate() {
      return modification;
   }

   /**
    * Mark this Annotation as being modified by the given user.  If they have EDITOR permissions, also
    * record their approval.
    */
   public void setModifiedBy(int uID, Role r) {
      modifiedBy = uID;
      approvedBy = r.ordinal() >= Role.EDITOR.ordinal() ? uID : 0;
      modification = new Date();
   }

   public int getApprovedBy() {
      return approvedBy;
   }

   public void setApprovedBy(int u) {
      approvedBy = u;
   }

   /**
    * Get the attributes of the annotation.  Intended originally for Annotations created from XML tags.
    * @return a JSON string containing the Annotation's attributes
    */
   public Map<String, String> getAttributes() {
      return attributes;
   }

   @JsonProperty("attributes")
   public void setAttributes(Map<String, String> val) {
      attributes = val;
   }
   
   @JsonIgnore
   public void setAttributes(String val) throws IOException {
      if (val != null) {
         attributes = ATTRIBUTE_MAPPER.readValue(val, new TypeReference<Map<String, String>>() {});
      } else {
         attributes = null;
      }
   }

   @JsonIgnore
   @Override
   public Page getStartPage() {
      return startPage;
   }

   @JsonIgnore
   public void setStartPage(Page pg) {
      startPage = pg;
   }

   @JsonProperty("startPage")
   public Integer getStartPageID() {
      return startPage != null ? startPage.getID() : null;
   }
           
   @JsonProperty("startPage")
   public void setStartPageID(Integer pgID) {
      if (pgID != null) {
         if (endPage != null && endPage.getID() == pgID) {
            startPage = endPage;
         } else if (startPage == null || startPage.getID() != pgID) {
            startPage = new Page(pgID);
         }
      } else {
         startPage = null;
      }
   }
   
   @Override
   public int getStartOffset() {
      return startOffset;
   }
   
   public void setStartOffset(int off) {
      startOffset = off;
   }

   @JsonIgnore
   @Override
   public Page getEndPage() {
      return endPage;
   }

   @JsonIgnore
   public void setEndPage(Page pg) {
      endPage = pg;
   }

   @JsonProperty("endPage")
   public Integer getEndPageID() {
      return endPage != null ? endPage.getID() : null;
   }

   @JsonProperty("endPage")
   public void setEndPageID(Integer pgID) {
      if (pgID != null) {
         if (startPage != null && startPage.getID() == pgID) {
            endPage = startPage;
         } else if (endPage == null || endPage.getID() != pgID) {
            endPage = new Page(pgID);
         }
      } else {
         endPage = null;
      }
   }
   
   @Override
   public int getEndOffset() {
      return endOffset;
   }
   
   public void setEndOffset(int off) {
      endOffset = off;
   }

   @JsonIgnore
   public Canvas getCanvas() {
      return canvas;
   }

   @JsonProperty("canvas")
   public String getCanvasURI() {
      String result = null;
      if (canvas != null) {
         result = canvas.getUniqueID();
         if (canvasFragment != null) {
            result += "#" + canvasFragment;
         }
      }
      return result;
   }
   
   @JsonProperty("canvas")
   public void setCanvasURI(String uri) throws NoSuchEntityException {
      if (uri != null) {
         String frag = null;
         int hashPos = uri.indexOf('#');
         if (hashPos >= 0) {
            frag = uri.substring(hashPos + 1);
            uri = uri.substring(0, hashPos);
         }
         canvasFragment = frag;

         String[] pieces = uri.split("/");
         String lastPiece = pieces[pieces.length - 1];
         if (canvas == null || !canvas.getUniqueID().equals(uri)) {
            if (pieces.length >= 2) {
               int canvID = Integer.parseInt(lastPiece);
               switch (pieces[pieces.length - 2]) {
                  case "canvas":
                     canvas = new Canvas(canvID);
                     break;
                  default:
                     throw new NoSuchEntityException(uri);
               }
            }
         }
      } else {
         canvas = null;
         canvasFragment = null;
      }
   }

   /**
    * Get a URI fragment which represents our text range within the transcription.
    * @return a fragment of the form #startPageID:startOffset-endPageID:endOffset
    */
   @JsonIgnore
   public String getTranscriptionFragment() {
      String result = null;
      if (startPage != null) {
         result = String.format("#%d:%d-%d:%d", startPage.getID(), startOffset, endPage.getID(), endOffset);
      }
      return result;
   }

   public String getTags() {
      return tags;
   }

   public void setTags(String value) {
      tags = value;
   }

   @JsonIgnore
   public Entity getTarget() {
      return target;
   }

   @JsonIgnore
   public void setTarget(Entity targ) {
      if (targ != null) {
         if (target == null || !target.getUniqueID().equals(targ.getUniqueID())) {
            if (targ instanceof Canvas) {
               canvas = (Canvas)targ;
            } else if (targ instanceof Page) {
               startPage = endPage = (Page)targ;
            } else if (targ instanceof Transcription || targ instanceof Manifest) {
               // Transcription targets are just page ranges; Manifest targets are just Canvas regions.
            } else {
               target = targ;
            }
         }
      } else {
         target = null;
      }
   }

   @JsonIgnore
   public String getTargetFragment() {
      return targetFragment;
   }

   @JsonIgnore
   public void setTargetFragment(String frag) {
      targetFragment = frag;
   }

   @JsonProperty("target")
   public String getTargetURI() {
      String result = null;
      if (target != null) {
         result = target.getUniqueID();
         if (targetFragment != null) {
            result += "#" + targetFragment;
         }
      }
      return result;
   }

   @JsonProperty("target")
   public void setTargetURI(String uri) throws NoSuchEntityException {
      if (uri != null) {
         String frag = null;
         int hashPos = uri.indexOf('#');
         if (hashPos >= 0) {
            frag = uri.substring(hashPos + 1);
            uri = uri.substring(0, hashPos);
         }
         targetFragment = frag;

         String[] pieces = uri.split("/");
         String lastPiece = pieces[pieces.length - 1];
         if (target == null || !target.getUniqueID().equals(uri)) {
            if (pieces.length >= 2) {
               int targID = Integer.parseInt(lastPiece);
               switch (pieces[pieces.length - 2]) {
                  case "annotation":
                     target = new Annotation(targID);
                     break;
                  case "edition":
                     target = new Edition(targID);
                     break;
                  case "outline":
                     target = new Outline(targID);
                     break;
                  case "parallel":
                     target = new Parallel(targID);
                     break;
                  case "witness":
                     target = new Witness(targID);
                     break;
                  default:
                     throw new NoSuchEntityException(uri);
               }
            }
         }
      } else {
         target = null;
         targetFragment = null;
      }
   }

   public void fixTargets(List<Page> pages, List<Canvas> canvasses) {
      if (startPage != null) {
         for (Page p: pages) {
            if (startPage.getID() == p.getID()) {
               startPage = p;
               break;
            }
         }
         for (Page p: pages) {
            if (endPage.getID() == p.getID()) {
               endPage = p;
               break;
            }
         }
      }
      if (canvas != null) {
         for (Canvas c: canvasses) {
            if (canvas.getID() == c.getID()) {
               canvas = c;
               break;
            }
         }
      }
   }

   public String getMotivation() {
      switch (type) {
         case "line":
            return "sc:painting";
         default:
            return "oa:Describing";
      }
   }

   /**
    * Motivation is now derived from the annotation's type, but we need a setter to keep JSON happy.
    */
   @JsonIgnore
   public void setMotivation(String ignored) {
   }

   /**
    * For an existing canvas-targetted annotation, target it relative to its page's
    * text.
    *
    * @param pg0 start page of the annotation
    * @param offset0 start offset of the annotation within <code>pg0<code>
    * @param pg1 end page of the annotation, typically equal to <code>pg0</code>
    * @param offset1 end offset of the annotation within <code>pg1<code>
    */
   public void setTextAnchor(Page pg0, int offset0, Page pg1, int offset1) {
      startPage = pg0;
      startOffset = offset0;
      endPage = pg1;
      endOffset = offset1;
   }

   /**
    * For an existing canvas-targetted annotation, target it relative to its page's
    * text.
    *
    * @param pg0 start page of the annotation
    * @param offset0 start offset of the annotation within <code>pg0<code>
    * @param pg1 end page of the annotation, typically equal to <code>pg0</code>
    * @param offset1 end offset of the annotation within <code>pg1<code>
    */
   public void copyTextTarget(Annotation ann) {
      startPage = ann.startPage;
      startOffset = ann.startOffset;
      endPage = ann.endPage;
      endOffset = ann.endOffset;
   }
   
   /**
    * Override <code>loadFields</code> to pick up the canvas and target fragments which don't have setters
    * of their own.
    * @param rs result set positioned to the row for this annotation
    * @param deep ignored, since annotations don't currently have children
    */
   @Override
   public void loadFields(ResultSet rs, boolean deep) throws SQLException, ReflectiveOperationException {
      super.loadFields(rs, deep);
      modification = rs.getTimestamp("modification");
      int canvID = rs.getInt("canvas");
      if (!rs.wasNull()) {
         String canvURI = "canvas/" + canvID;
         String canvFrag = rs.getString("canvas_fragment");
         if (canvFrag != null) {
            canvURI += "#" + canvFrag;
         }
         setCanvasURI(canvURI);
      }
      int targID = rs.getInt("target");
      if (!rs.wasNull()) {
         String targURI = rs.getString("target_type").toLowerCase() + "/" + targID;
         String targFrag = rs.getString("target_fragment");
         if (targFrag != null) {
            targURI += "#" + targFrag;
         }
         setTargetURI(targURI);
      }
   }
   
   @Override
   @JsonIgnore
   public Map<String, Method> getSetters() {
      Map<String, Method> result = super.getSetters();
      result.remove("target");
      try {
         result.put("attributes", getClass().getMethod("setAttributes", String.class));
      } catch (NoSuchMethodException ex) {
          LOG.log(Level.WARNING, "Should never happen, since Annotation has a setAttributes(String) method.", ex);
      }
      return result;
   }

   /**
    * Close off a text-based annotation which is being imported from XML.
    *
    * @param p the end page of the annotation
    * @param offset the offset of the end of the annotation within that page.
    */
   public void complete(Page p, int offset) {
      endPage = p;
      endOffset = offset;
   }

   /**
    * Execute the SQL commands to insert this annotation into the database.
    */
   @Override
   public void insert(Connection conn) throws IOException, SQLException {
      Integer startPg = null, startOff = null, endPg = null, endOff = null;
      Integer canv = null;
      String targType = null;
      Integer targID = null;
      String attrs = null;
      if (startPage != null) {
         startPg = startPage.getID();
         startOff = startOffset;
         endPg = endPage.getID();
         endOff = endOffset;
      }
      if (canvas != null) {
         canv = canvas.getID();
      }
      if (target != null) {
         targType = target.getEntityType();
         targID = target.getID();
      }
      if (attributes != null) {
         attrs = ATTRIBUTE_MAPPER.writeValueAsString(attributes);
      }
      executeInsert(conn, "INSERT INTO `annotations` (" +
            "`start_page`, `start_offset`, `end_page`, `end_offset`, " +
            "`canvas`, `canvas_fragment`, " +
            "`target_type`, `target`, `target_fragment`, " +
            "`type`, `content`, `attributes`, `tags`, `modified_by`, `approved_by`" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            startPg, startOff, endPg, endOff,
            canv, canvasFragment,
            targType, targID, targetFragment,
            type, content, attrs, tags, modifiedBy, approvedBy);
   }

   @Override
   public void merge(Connection conn, Entity newEnt) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Annotation newAnn = (Annotation)newEnt;
      if (!isEquivalentTo(newAnn)) {
         startPage = newAnn.startPage;
         startOffset = newAnn.startOffset;
         endPage = newAnn.endPage;
         endOffset = newAnn.endOffset;

         canvas = newAnn.canvas;
         canvasFragment = newAnn.canvasFragment;

         target = newAnn.target;
         targetFragment = newAnn.targetFragment;

         type = newAnn.type;
         content = newAnn.content;
         attributes = newAnn.attributes;
         tags = newAnn.tags;

         newAnn.id = id;

         Integer startPg = null, startOff = null, endPg = null, endOff = null;
         Integer canv = null;
         String targType = null;
         Integer targID = null;
         String attrs = null;
         if (startPage != null) {
            startPg = startPage.getID();
            startOff = startOffset;
            endPg = endPage.getID();
            endOff = endOffset;
         }
         if (canvas != null) {
            canv = canvas.getID();
         }
         if (target != null) {
            targType = target.getEntityType();
            targID = target.getID();
         }
         if (attributes != null) {
            attrs = ATTRIBUTE_MAPPER.writeValueAsString(attributes);
         }
         modifiedBy = newAnn.modifiedBy;
         approvedBy = newAnn.approvedBy;
         executeUpdate(conn, "UPDATE `annotations` " +
               "SET `start_page` = ?, `start_offset` = ?, `end_page` = ?, `end_offset` = ?, " +
               "`canvas` = ?, `canvas_fragment` = ?, " +
               "`target_type` = ?, `target` = ?, `target_fragment` = ?, " +
               "`type` = ?, `content` = ?, `attributes` = ?, `tags` = ?, " +
               "`modified_by` = ?, `approved_by` = ? " +
               "WHERE `id` = ?",
               startPg, startOff, endPg, endOff,
               canv, canvasFragment,
               targType, targID, targetFragment,
               type, content, attrs, tags,
               modifiedBy, approvedBy, id);
      }
   }

   /**
    * Modify method for Annotations has to accommodate the fact that we're storing attributes as a JSON
    * string.
    * @param conn connection to SQL database
    * @param mods modified fields
    * @return modified version of Annotation
    * @throws SQLException
    * @throws ReflectiveOperationException 
    */
   @Override
   public Object modify(Connection conn, Map<String, Object> mods) throws SQLException, ReflectiveOperationException, IOException, PermissionException {
      Object attrs = mods.get("attributes");
      if (attrs != null) {
         try {
            mods.put("attributes", ATTRIBUTE_MAPPER.writeValueAsString(attrs));
         } catch (JsonProcessingException ex) {
            LOG.log(Level.WARNING, "Should never happen, since we're just writing a Map out as a JSON string", ex);
         }
      }
      Object canvURI = mods.get("canvas");
      if (canvURI != null) {
         mods.remove("canvas");
         if (!canvURI.equals(getCanvasURI())) {
            // Breaks the target up into target and fragment and checks for validity.
            setCanvasURI(canvURI.toString());
            if (canvas != null) {
               mods.put("canvas", canvas.getID());
            }
            if (canvasFragment != null) {
               mods.put("canvasFragment", canvasFragment);
            }
         }
      }
      Object targURI = mods.get("target");
      if (targURI != null) {
         mods.remove("target");
         
         if (!targURI.equals(getTargetURI())) {
            // Breaks the target up into target and fragment and checks for validity.
            setTargetURI(targURI.toString());
            if (target != null) {
               mods.put("target", target.getID());
               mods.put("targetType", target.getEntityType());
            }
            if (targetFragment != null) {
               mods.put("targetFragment", targetFragment);
            }
         }
      }
      return super.modify(conn, mods);
   }

   
   /**
    * Check permissions for this annotation.  Assumes that the annotation's fields have already been loaded
    * so that we have access to startPage, canvas, and anchor (if any).
    * @param conn connection to the SQL database
    * @param uID ID of user being validated
    * @param required role required for given access
    * @return the Role for which the user actually has access, possibly higher than <code>required</code>
    * @throws SQLException
    * @throws PermissionException 
    */
   @Override
   public Role checkPermission(Connection conn, int uID, Role required) throws SQLException, PermissionException {
      if (target == null && startPage == null && canvas == null) {
         // We may need to load the annotation in order to figure out what we're anchored to.
         try {
            load(conn, false);
         } catch (ReflectiveOperationException ex) {
             LOG.log(Level.WARNING, "Should never happen, since Annotation.load works.", ex);
         }
      }
      Role r = Role.OWNER;
      if (target != null) {
         Role r2 = target.checkPermission(conn, uID, required);
         if (r2.ordinal() < r.ordinal()) {
            r = r2;
         }
      }
      if (startPage != null) {
         Role r2 = startPage.checkPermission(conn, uID, required);
         if (r2.ordinal() < r.ordinal()) {
            r = r2;
         }
      }
      if (canvas != null) {
         Role r2 = canvas.checkPermission(conn, uID, required);
         if (r2.ordinal() < r.ordinal()) {
            r = r2;
         }
      }
      return r;
   }

   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
      if (target != null) {
         target.markTopLevelModified(conn);
      } else if (startPage != null) {
         startPage.markTopLevelModified(conn);
      } else if (canvas != null) {
         canvas.markTopLevelModified(conn);
      } else {
         LOG.log(Level.WARNING, "{0} not attached to any edition.", getUniqueID());
      }
   }

   @Override
   public String toString() {
      return String.format("%s %s:%d-%s:%d/%d", type, startPage, startOffset, endPage, endOffset, id);
   }
   
   public boolean isEquivalentTo(Annotation other) throws IOException {
      if (!compareIDs(this.startPage, other.startPage)) {
         return false;
      }
      if (this.startOffset != other.startOffset) {
         return false;
      }
      if (!compareIDs(this.endPage, other.endPage)) {
         return false;
      }
      if (this.endOffset != other.endOffset) {
         return false;
      }
      if (!compareIDs(this.canvas, other.canvas)) {
         return false;
      }
      if (!Objects.equals(this.canvasFragment, other.canvasFragment)) {
         return false;
      }
      if (Objects.equals(this.target, other.target)) {
         return false;
      }
      if (!Objects.equals(this.targetFragment, other.targetFragment)) {
         return false;
      }
      if (!Objects.equals(this.type, other.type)) {
         return false;
      }
      if (!Objects.equals(this.content, other.content)) {
         return false;
      }
      if (this.attributes == null) {
         if (other.attributes != null) {
            return false;
         }
      } else {
         if (other.attributes == null) {
            return false;
         }
         // Attributes for both are non-null, so lets compare their string representations.
         if (!ATTRIBUTE_MAPPER.writeValueAsString(this.attributes).equals(ATTRIBUTE_MAPPER.writeValueAsString(other.attributes))) {
            return false;
         }
      }
      if (!Objects.equals(this.attributes, other.attributes)) {
         return false;
      }
      if (!Objects.equals(this.tags, other.tags)) {
         return false;
      }
      // Changes to modifiedBy and modification fields are not sufficient to deem two annotations non-equivalent, but changes to
      // approvedBy are.
      return approvedBy == other.approvedBy;
   }
   
   /**
    * Mark a whole list of annotations as being modified by the given user.  Also approve as appropriate.
    * @param anns annotations to be marked
    * @param uID user doing the change
    * @param r user's role
    */
   public static <T extends Annotation> void markAnnotationsModified(List<T> anns, int uID, Role r) {
      for (Annotation ann: anns) {
         ann.setModifiedBy(uID, r);
      }
   }

   public static Comparator<Annotation> getMergingComparator() {
      return new Comparator<Annotation>() {
         @Override
         public int compare(Annotation o1, Annotation o2) {
            if (o1.id == o2.id) {
               return 0;
            }
            int diff = o1.type.compareTo(o2.type);
            if (diff == 0) {
               if (o1.canvas != null && o2.canvas != null) {
                  // Try comparing on canvas index.
                  diff = o1.canvas.getID() - o2.canvas.getID();

                  if (diff == 0) {
                     // Same canvas; try comparing on y-position.
                     Rectangle bounds1 = getXYWH(o1.getCanvasURI());
                     Rectangle bounds2 = getXYWH(o2.getCanvasURI());

                     if (o1.type.equals("line") && bounds1.equals(bounds2)) {
                        // Special case.  If we're a T-PEN line and we have the exact same coordinates,
                        // we must be the same annotation.
                        return 0;
                     }
                     diff = bounds1.x - bounds2.x;
                     if (diff == 0) {
                        // Column positions are the same.  Sort based on y-positions.
                        diff = bounds1.y - bounds2.y;
                        
                        if (diff == 0 && o1.type.equals("line")) {
                           return 0;
                        }
                     }
                  }
               }
            }
            if (diff == 0) {
               if (o1.startPage != null && o2.startPage != null) {
                  // Try comparing on page ID.
                  diff = o1.startPage.getID() - o2.startPage.getID();
                  if (diff == 0) {
                     // Same page; try comparing on text offset.
                     diff = o1.startOffset - o2.startOffset;
                  }
               }
               if (diff == 0) {
                  if (o1.target != null && o2.target != null) {
                     // Try comparing on anchor entity index.
                     diff = o1.target.getID() - o2.target.getID();
                  }
               }
            }
            return diff;
         }
      };
   }

   /**
    * Comparison for metadata is a little simpler, since we only judge equality based on IDs.
    */
   public static Comparator<Annotation> getIDComparator() {
      return new Comparator<Annotation>() {
         @Override
         public int compare(Annotation o1, Annotation o2) {
            return o1.id - o2.id;
         }
      };
   }

   public static final String SELECT_SUB_ANNOTATIONS = "SELECT * FROM `annotations` " +
         "WHERE `target_type` = 'ANNOTATION' AND `target` = ?";

   /**
    * Keep an ObjectMapper around for stringifying/destringifying attributes.
    */
   private static final ObjectMapper ATTRIBUTE_MAPPER = getObjectMapper();

   private static final Logger LOG = Logger.getLogger(Annotation.class.getName());
}
