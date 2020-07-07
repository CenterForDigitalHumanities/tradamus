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
package edu.slu.tradamus.text;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLStreamException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.image.Canvas;
import edu.slu.tradamus.user.Permission;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import edu.slu.tradamus.witness.Witness;
import static edu.slu.tradamus.util.LangUtils.elideString;
import static edu.slu.tradamus.util.XMLUtils.parseTag;


/**
 * All the text associated with a given witness.
 *
 * @author tarkvara
 */
public class Transcription extends Entity {
   /**
    * Witness to which the transcription belongs.
    */
   private Witness witness;
   
   /**
    * Human who is responsible for editing the transcription.
    */
   private String editor;
   
   /**
    * Ordered list of pages in this transcription.
    */
   private List<Page> pages = new ArrayList<>();

   /**
    * List of permissions associated with this Transcription; these place additional restrictions on and
    * above the restrictions imposed by the Edition.
    */
   private List<Permission> permissions;

   /**
    * During import, keeps track of tags being parsed.
    */
   private Stack<Annotation> pendingTags;

   /**
    * During import, keeps track of the current line number for error-reporting purposes.
    */
   private int currentPage, currentLine;

   /**
    * Relaxed processing for HTML.  Allows for unclosed tags and such.
    */
   private boolean relaxedHTML;

   /**
    * Constructor for instantiating from JSON.
    */
   public Transcription() {
   }

   /**
    * Create a blank transcription; used as part of import process.
    *
    * @param w the Witness to which this Transcription belongs
    */
   public Transcription(Witness w) {
      witness = w;
   }

   /**
    * Instantiate a transcription from the database as part of loading a Witness.
    *
    * @param w the Witness to which this Transcription belongs
    * @param transcrID the transcription's ID
    */
   public Transcription(Witness w, int transcrID) {
      id = transcrID;
      witness = w;
   }

   /**
    * Wrap a bare-bones transcription around an ID.
    *
    * @param transcrID the transcription's ID
    */
   public Transcription(int transcrID) {
      id = transcrID;
   }

   /**
    * The human who is responsible for editing this transcription.
    */
   public String getEditor() {
      return editor;
   }

   public void setEditor(String ed) {
      editor = ed;
   }

   @JsonIgnore
   public Witness getWitness() {
      return witness;
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

   public void addPage(Page p) {
      p.setIndex(pages.size());
      pages.add(p);
   }

   /**
    * Look for the given page (which may be just a wrapped ID) in the pages array (which should contain
    * fully-loaded pages).
    * @param p page to be located
    * @return the "real" page corresponding to <c>p</c>
    */
   public Page findPage(Page p) {
      for (Page p2: pages) {
         if (p.getID() == p2.getID()) {
            return p2;
         }
      }
      return p;
   }

   public Page getPage(int index) {
      return pages.get(index);
   }

   @JsonIgnore
   public int getPageCount() {
      return pages.size();
   }

   public List<Page> getPages() {
      return pages;
   }

   /**
    * Additional permissions associated with this Transcription, adding restrictions beyond those imposed
    * by the Edition-level permissions.
    */
   public List<Permission> getPermissions() {
      return permissions;
   }

   public String getText(TextAnchor anch) {
      int startPage = anch.getStartPage().getIndex();
      int endPage = anch.getEndPage().getIndex();
      if (startPage == endPage) {
         // In the most common case, all the text is anchored to a single page.
         return getPage(startPage).getText().substring(anch.getStartOffset(), anch.getEndOffset());
      }
      // Text anchored over multiple pages, so we have to assemble it.
      StringBuilder buf = new StringBuilder(getPage(startPage).getText().substring(anch.getStartOffset()));
      for (int i = startPage + 1; i < endPage - 1; i++) {
         buf.append(getPage(i).getText());
      }
      buf.append(getPage(endPage).getText().substring(0, anch.getEndOffset()));
      return buf.toString();
   }

   /**
    * Insert the transcription into the SQL database.
    */
   @Override
   public void insert(Connection conn) throws SQLException {
      executeInsert(conn, "INSERT INTO transcriptions (" +
            "witness, editor" +
            ") VALUES(?, ?)",
            witness.getID(), editor);

      int i = 0;
      for (Page p: pages) {
         p.setTranscriptionID(id);
         p.setIndex(i++);
         p.insert(conn);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void load(Connection conn, boolean deep) throws SQLException, ReflectiveOperationException {
      if (deep) {
         executeLoad(conn, "SELECT witness, editor FROM transcriptions " +
              "WHERE transcriptions.id = ?", true);
         pages = loadChildren(conn, SELECT_PAGES, Page.class, true);
      } else {
         executeLoad(conn, "SELECT witness, editor, pages.id, pages.title, text FROM transcriptions " +
              "LEFT JOIN pages ON transcription = transcriptions.id " +
              "WHERE transcriptions.id = ?", false);
      }
      permissions = loadChildren(conn, SELECT_PERMISSIONS_WITH_USERS, Permission.class, false);
   }

   @Override
   public void loadFields(ResultSet rs, boolean deep) throws SQLException, ReflectiveOperationException {
      super.loadFields(rs, deep);
      if (!deep) {
         pages = new ArrayList<>();
         do {
            int pgID = rs.getInt("pages.id");
            if (pgID > 0) {
               Page pg = new Page(this, pgID, pages.size(), rs.getString("pages.title"), rs.getString("text"));
               pages.add(pg);
            }
         } while (rs.next());
      }
   }

   /**
    * Merge this transcription and its constituents into the database.
    * @param conn 
    */
   @Override
   public void merge(Connection conn, Entity newEnt) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Transcription newTranscr = (Transcription)newEnt;

      // Update the top-level fields.  For now, there's just editor.
      if (!Objects.equals(editor, newTranscr.editor)) {
         editor = newTranscr.editor;
         executeUpdate(conn, "UPDATE transcriptions SET editor = ? " +
           "WHERE id = ?", editor, id);
      }
      newTranscr.id = id;
      
      mergeChildren(conn, pages, newTranscr.pages, Page.getIndexComparator(), null, true);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Role checkPermission(Connection conn, int uID, Role required) throws SQLException, PermissionException {
      Role r = executeGetPermission(conn, SELECT_EDITION_LEVEL_ROLE, uID);
      if (r == null || r.ordinal() < required.ordinal()) {
         throw new PermissionException(this, required);
      }
      
      // Passed the Edition-level check, so verify the Transcription permissions.
      Role r2 = getTranscriptionPermission(conn, uID);
      if (r2 != null) {
         // Transcription-level role supersedes edition-level role.
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
    * Called to check the transcription-level permissions, without reference to the edition-level permissions.
    * @param conn connection to SQL database
    * @param uID user whose access is being checked
    */
   public Role getTranscriptionPermission(Connection conn, int uID) throws SQLException {
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
              "LEFT JOIN transcriptions ON witness = witnesses.id " +
              "SET modification = NOW() " +
              "WHERE transcriptions.id = ?")) {
         stmt.setInt(1, id);
         stmt.executeUpdate();
      }
   }

   
   /**
    * Since the transcription is inserted before the manifest, any canvasses which
    * are associated with pages won't yet have IDs assigned.  This connects them
    * together.
    * @param conn connection to our SQL database
    */
   public void assignCanvasIDs(Connection conn) throws SQLException {
      for (Page p: pages) {
         p.updateCanvasID(conn);
      }
   }

   /**
    * As part of the JSON import process, make sure we link to actual Canvasses which are part of the
    * import, and not just to place-holder canvasses which have been wrapped around an ID.
    * @param canvs canvasses which have been imported (with their original IDs)
    */
   public void fixTargets(List<Canvas> canvs) {
      for (Page p: pages) {
         for (Canvas c: canvs) {
            if (p.getCanvasID() == c.getID()) {
               p.setCanvas(c);
               break;
            }
         }
      }
   }

   /**
    * An imported witness may contain XML or XMLish markup which we want to convert into Tradamus
    * annotations.  Among other things, this may readjust the text lengths and offsets of all the lines and
    * pages in the transcription.
    * @return 
    */
   public List<Annotation> extractTagAnnotations() throws XMLStreamException {
      List<Annotation> result = new ArrayList<>();
      pendingTags = new Stack<>();

      // For debug purposes.
      StringBuilder rawBuf = new StringBuilder();
      StringBuilder cookedBuf = new StringBuilder();

      currentPage = 0;
      for (Page p: pages) {
         int delta = 0;    // Change in offset as a result of stripping out XML tags.
         currentLine = 0;
         for (Annotation l: p.lines) {
            String lineContent = l.getContent();
            rawBuf.append(lineContent);
            l.setStartOffset(l.getStartOffset() + delta);
            int tagStart = -1;
            boolean insideComment = false;
            boolean insidePI = false;
            for (int i = 0; i < lineContent.length(); i++) {
               switch (lineContent.charAt(i)) {
                  case '<':
                     if (!insideComment && !insidePI) {
                        if (tagStart >= 0) {
                           throw new XMLStreamException(formatXMLError("found '<' while inside tag."));
                        } else {
                           tagStart = i;
                           String tag = lineContent.substring(i);
                           if (tag.startsWith("<!--")) {
                              insideComment = true;
                           } else if (tag.startsWith("<?")) {
                              insidePI = true;
                           }
                        }
                     }
                     break;
                  case '>':
                     if (tagStart >= 0) {
                        String tag = lineContent.substring(tagStart, i + 1);
                        if (insideComment) {
                           if (tag.endsWith("-->")) {
                              insideComment = false;
                           } else {
                              break;
                           }
                        } else if (insidePI) {
                           if (tag.endsWith("?>")) {
                              insidePI = false;
                           } else {
                              break;
                           }
                        } else {
                           Annotation a = processTag(tag, p, l.getStartOffset() + i + 1 - tag.length());
                           if (a != null) {
                              result.add(a);
                           }
                        }
                        lineContent = lineContent.substring(0, tagStart) + lineContent.substring(i + 1);
                        delta -= tag.length();
                        i -= tag.length();
                        tagStart = -1;
                     }
                     break;
               }
            }
            l.setContent(lineContent);
            l.setEndOffset(l.getStartOffset() + lineContent.length());
            cookedBuf.append(lineContent).append('\n');
            currentLine++;
         }
         p.setText(cookedBuf.toString());
         cookedBuf.setLength(0);
         currentPage++;
      }
      pendingTags = null;
      return result;
   }
      
   private Annotation processTag(String tag, Page p, int offset) throws XMLStreamException {      
      Annotation result = null;
      if (tag.charAt(1) == '/') {
         // Closing tag for an element.
         result = closeAnnotation(tag.substring(2, tag.length() - 1), p, offset);
      } else {
         if (tag.startsWith("<![endif")) {
            closeConditionalComment(tag);
         } else {
            result = openAnnotation(tag, p, offset);
         }
      }
      return result;
   }
   
   /**
    * Open an annotation for processing.  It may end up being self-closed.
    * @param fullTag the tag from the left angle to the right, including all attributes
    * @param p page being processed
    * @param offset offset within page being processed
    * @return the new tag (if self-closed) or null (if not)
    * @throws XMLStreamException 
    */
   private Annotation openAnnotation(String fullTag, Page p, int offset) throws XMLStreamException {
      Map<String, String> attrMap = new HashMap<>();
      String tag = parseTag(fullTag, attrMap, relaxedHTML);
      if (tag.equalsIgnoreCase("html")) {
         relaxedHTML = true;
      }

      Annotation a = new Annotation(tag, null, p, offset);
      a.setAttributes(attrMap);
      pendingTags.push(a);

      // If it's self-closing, return the new annotation right now.
      if (fullTag.endsWith("/>") || (relaxedHTML && SELF_CLOSING_HTML_TAGS.contains(tag))) {
         return closeAnnotation(tag, p, offset);
      }
      return null;
   }
   
   /**
    * Close the current tag which is expected to be an annotation of some sort.
    * @param closingTag XML tag which is currently being closed
    * @throws XMLStreamException if the tags don't match
    */
   private Annotation closeAnnotation(String closingTag, Page p, int offset) throws XMLStreamException {
      String workingTag = pendingTags.peek().getType();
      if (workingTag.equals(closingTag)) {
         Annotation a = pendingTags.pop();
         a.complete(p, offset);
         LOG.log(Level.INFO, "Anchored {0} at ?:{1}-{2}:{3} \"{4}\"", new Object[] { a.getType(), a.getStartOffset(), p.getIndex(), a.getEndOffset(), a.getContent() });
         return a;
      } else {
         throw new XMLStreamException(formatXMLError("Expecting </%s>, but found </%s>.", workingTag, closingTag));
      }
   }

   private void closeConditionalComment(String closingTag) throws XMLStreamException {
      String workingTag = pendingTags.peek().getType();
      if (workingTag.startsWith("![if")) {
         Annotation a = pendingTags.pop();
         LOG.log(Level.INFO, "Discarding conditional comment {0} at ?:{1} \"{4}\"", new Object[] { a.getType(), a.getStartOffset(), a.getContent() });
      } else {
         throw new XMLStreamException(formatXMLError("Expecting </%s>, but found </%s>.", workingTag, closingTag));
      }
   }

   public List<Annotation> loadNonLineAnnotations(Connection conn) throws SQLException, ReflectiveOperationException {
      List<Annotation> result = new ArrayList<>();
      try (PreparedStatement stmt = conn.prepareStatement(SELECT_NON_LINE_ANNOTATIONS)) {
         stmt.setInt(1, id);
         ResultSet rs = stmt.executeQuery();
         while (rs.next()) {
            // Find the constructor which takes an ID as its only parameter.
            Annotation ann = new Annotation(rs.getInt("annotations.id"));
            ann.loadFields(rs, false);
            
            // Make sure the annotation points at fully-loaded pages, not just wrapped IDs.
            ann.setStartPage(findPage(ann.getStartPage()));
            ann.setEndPage(findPage(ann.getEndPage()));
            
            result.add(ann);
         }
      }
      return result;
   }

   private String formatXMLError(String fmt, Object... args) {
      return String.format("XML input error at page %d, line %d \"%s\": %s", currentPage, currentLine, elideString(pages.get(currentPage).lines.get(currentLine).getContent(), 30), String.format(fmt, args));
   }

   public static final String SELECT_EDITION_LEVEL_ROLE = "SELECT role FROM permissions " +
            "LEFT JOIN witnesses ON witnesses.edition = target " +
            "LEFT JOIN transcriptions ON transcriptions.witness = witnesses.id " +
            "WHERE target_type = 'EDITION' AND transcriptions.id = ? AND (user = ? OR user = 0)";

   public static final String SELECT_PAGES = "SELECT * FROM pages WHERE transcription = ?";

   public static final String SELECT_PAGES_WITH_LINES = "SELECT pages.id, transcription, `index`, title, text, pages.canvas, annotations.id FROM pages "  +
         "LEFT JOIN `annotations` ON start_page = pages.id " +
         "WHERE transcription = ? AND type = 'line' " +
         "ORDER BY `index`";

   /**
    * Query for loading Transcription permissions along with user name and email.
    */
   public static final String SELECT_PERMISSIONS_WITH_USERS = "SELECT permissions.id AS id, target_type, target, role, " +
         "user, name, mail FROM permissions " +
         "JOIN users ON user = users.id " +
         "WHERE target_type = 'TRANSCRIPTION' AND target = ?";

   /**
    * Used during collation to retrieve all annotations associated with this transcription's pages.  This is
    * used to ensure that we don't remove a line-break which is occurring at the end of something like a
    * &lt;head> tag.
    */
   private static final String SELECT_NON_LINE_ANNOTATIONS = "SELECT * FROM `annotations` " +
         "LEFT JOIN `pages` ON `start_page` = pages.id " +
         "WHERE `transcription` = ? AND `type` <> 'line' AND `type` <> 'note'";

   private static final Set<String> SELF_CLOSING_HTML_TAGS = new HashSet<>(Arrays.asList("area", "base", "br", "col", "command",
           "embed", "hr", "img", "input", "keygen", "link", "meta", "param", "source", "track", "wbr"));

   private static final Logger LOG = Logger.getLogger(Transcription.class.getName());
}
