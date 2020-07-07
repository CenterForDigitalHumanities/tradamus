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
package edu.slu.tradamus.servlet;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLStreamException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import eu.interedition.collatex.Token;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.collation.*;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.edition.Edition;
import edu.slu.tradamus.text.Page;
import edu.slu.tradamus.text.TextAnchor;
import edu.slu.tradamus.text.Transcription;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import edu.slu.tradamus.util.DeferredDeliverable;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import edu.slu.tradamus.util.MessageID;
import edu.slu.tradamus.util.MessageUtils;
import static edu.slu.tradamus.util.ServletUtils.*;


/**
 * Servlet which provides collations for a given edition.
 *
 * @author tarkvara
 */
public class CollationServlet extends HttpServlet {

   /**
    * Handles the HTTP <code>POST</code> method by building a collation structure for the given edition, and returning it to
    * the caller as a STOA.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      final int uID = getUserID(req, resp);
      if (uID > 0) {
         try (Connection conn = getDBConnection()) {
            // Basic parameters
            CollationWitness.IgnoreLineBreaks ignoreLineBreaks = CollationWitness.IgnoreLineBreaks.fromString(req.getParameter("ignoreLineBreaks"));
            boolean ignoreCase = "true".equals(req.getParameter("ignoreCase"));
            boolean ignorePunct = "true".equals(req.getParameter("ignorePunctuation"));
            boolean useTEITags = "true".equals(req.getParameter("useTEITags"));
            boolean deferred = "true".equals(req.getParameter("deferred"));

            // Comparison type.
            final CollationWitness.Comparison comparison = CollationWitness.Comparison.fromString(req.getParameter("comparison"));
            final String[] dicts = req.getParameterValues("dict");   // For orthographic comparisons.

            final List<CollationWitness> collWits;
            String[] pathParts = getPathParts(req);
            if (pathParts.length >= 2) {
               // Full-edition collation
               collWits = getFullCollationWitnesses(conn, new Edition(Integer.parseInt(pathParts[1])), uID, ignoreLineBreaks, ignoreCase, ignorePunct, useTEITags);
            } else {
               // Partial collation of the text ranges specified in the request body.
               collWits = getPartialCollationWitnesses(conn, req, uID, ignoreLineBreaks, ignoreCase, ignorePunct, useTEITags);
            }

            if (deferred) {
               new DeferredDeliverable(req) {
                  @Override
                  public void run() {
                     try (Connection conn = getDBConnection()) {
                        ByteArrayOutputStream jsonBytes = new ByteArrayOutputStream();
                        writeCollationResults(conn, collWits, getComparator(conn, collWits, comparison, dicts), jsonBytes);
                        conn.setAutoCommit(false);
                        try (PreparedStatement stmt = conn.prepareStatement("UPDATE `deliverables` SET `body` = ? WHERE `id` = ?")) {
                           stmt.setCharacterStream(1, new InputStreamReader(new ByteArrayInputStream(jsonBytes.toByteArray())));
                           stmt.setInt(2, deliverableID);
                           stmt.executeUpdate();
                        }
                        conn.commit();

                        sendMail(conn, uID, "Collation Complete", MessageUtils.format(MessageID.COLLATION_COMPLETE, fullURL, serverURL, deliverableID));
                     } catch (IOException | SQLException | ServletException | XMLStreamException ex) {
                        LOG.log(Level.SEVERE, "Error saving collation results", ex);
                     }
                  }
               }.deferredRequest(conn, resp, MessageID.DEFERRED_COLLATION);
            } else {
               // Perform the collation and write the results to the response stream.
               resp.setContentType("application/json; charset=UTF-8");
               writeCollationResults(conn, collWits, getComparator(conn, collWits, comparison, dicts), resp.getOutputStream());
            }
         } catch (SQLException | ArrayIndexOutOfBoundsException | NumberFormatException | ReflectiveOperationException | PermissionException | XMLStreamException ex) {
            reportInternalError(resp, ex);
         }
      }
   }

   /**
    * Returns a short description of the servlet.
    *
    * @return a String containing servlet description
    */
   @Override
   public String getServletInfo() {
      return "Tradamus Collation Servlet";
   }
   
   /**
    * Create the collation witnesses when doing a full-edition collation.
    * @param conn connection to SQL database
    * @param ed edition whose witnesses are being collated
    * @param uID ID of user who made the request
    * @return collation witnesses suitable for feeding to CollateX
    */
   private List<CollationWitness> getFullCollationWitnesses(Connection conn, Edition ed, int uID, CollationWitness.IgnoreLineBreaks ignoreLineBreaks, boolean ignoreCase, boolean ignorePunct, boolean useTEITags) throws SQLException, PermissionException, IOException, ReflectiveOperationException {
      List<CollationWitness> result = new ArrayList<>();

      ed.checkPermission(conn, uID, Role.VIEWER);
      List<Transcription> transcrs = ed.loadTranscriptions(conn, uID);
      for (Transcription t: transcrs) {
         try {
            t.checkPermission(conn, uID, Role.VIEWER);
            CollationWitness w = new CollationWitness(t, ignoreLineBreaks, ignoreCase, ignorePunct, useTEITags);
            w.tokenise(conn);
            result.add(w);
         } catch (PermissionException ex) {
            // No harm done.  The collation will skip this transcription because we aren't allowed to read it.
         }
      }
      return result;
   }

   /**
    * Create the collation witnesses when doing a partial-edition collation.
    * @param conn connection to SQL database
    * @param req servlet request whose body contains the text ranges to be processed
    * @param uID ID of user who made the request
    * @return collation witnesses suitable for feeding to CollateX
    */
   private List<CollationWitness> getPartialCollationWitnesses(Connection conn, HttpServletRequest req, int uID, CollationWitness.IgnoreLineBreaks ignoreLineBreaks, boolean ignoreCase, boolean ignorePunct, boolean useTEITags) throws IOException, ReflectiveOperationException, SQLException, PermissionException {
      List<CollationWitness> result = new ArrayList<>();

      // Because we want allow annotations to be passed here, we don't deserialise directly to
      // List<TextAnchor>.  Instead we deserialise into a Map and pull out the relevant fields.
      ObjectMapper mapper = getObjectMapper();
      List<Map<String, Object>> rawRanges = (List<Map<String, Object>>)mapper.readValue(req.getInputStream(), new TypeReference<List<Map<String, Object>>>() {});
      try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM `transcriptions` JOIN `pages` ON `transcription` = transcriptions.id WHERE pages.id = ?")) {
         for (Map<String, Object> rawRange: rawRanges) {
            TextAnchor r = new TextAnchor(new Page((int)rawRange.get("startPage")), (int)rawRange.get("startOffset"), new Page((int)rawRange.get("endPage")), (int)rawRange.get("endOffset"));
            stmt.setInt(1, r.getStartPage().getID());
            ResultSet rs1 = stmt.executeQuery();
            if (!rs1.next()) {
               throw new NoSuchEntityException(r.getStartPage());
            }
            int transcrID1 = rs1.getInt("transcriptions.id");
            int index1 = rs1.getInt("index");

            // Now check that the end page is also on the same page, and that the order is reasonable.
            stmt.setInt(1, r.getEndPage().getID());
            ResultSet rs2 = stmt.executeQuery();
            if (!rs2.next()) {
               throw new NoSuchEntityException(r.getEndPage());
            }
            int transcrID2 = rs2.getInt("transcriptions.id");
            int index2 = rs2.getInt("index");
            if (transcrID1 != transcrID2) {
               throw new IllegalArgumentException(String.format("Start page (%d) and end page (%d) are from different witnesses.", r.getStartPage().getID(), r.getEndPage().getID()));
            }
            if (index1 > index2) {
               throw new IllegalArgumentException(String.format("Start page (%d) is after end page (%d).", r.getStartPage().getID(), r.getEndPage().getID()));
            }
            if (index1 == index2 && r.getStartOffset() > r.getEndOffset()) {
               throw new IllegalArgumentException(String.format("Start offset (%d) is after end offset (%d).", r.getStartOffset(), r.getEndOffset()));
            }
            Transcription transcr = new Transcription(transcrID1);
            transcr.checkPermission(conn, uID, Role.VIEWER);
            transcr.load(conn, false);
            CollationWitness w = new CollationWitness(transcr, ignoreLineBreaks, ignoreCase, ignorePunct, useTEITags);
            w.tokenise(conn, index1, r.getStartOffset(), index2, r.getEndOffset());
            result.add(w);
         }
      }
      return result;
   }

   private Comparator<Token> getComparator(Connection conn, List<CollationWitness> collWits, CollationWitness.Comparison comparison, String[] dicts) throws SQLException, IOException, XMLStreamException {
      Comparator<Token> result;
      switch (comparison) {
         case PLAIN:
         default:
            result = new PlainComparator();
            break;
         case ORTH:
            result = new OrthographicComparator();
            if (dicts == null) {
               dicts = new String[] { "lat" };
            }
            ((OrthographicComparator)result).loadDictionaries(conn, dicts);
            break;
         case MORPH:
            result = new MorphologicalComparator();
            ((MorphologicalComparator)result).loadMorphology(collWits);
            break;
      }
      return result;
   }

   /**
    * Perform the actual collation and write the results to the output stream.
    * @param collWits CollateX witnesses
    * @param comp comparator appropriate for collation type
    * @param dest stream to which results will be written
    */
   private static void writeCollationResults(Connection conn, List<CollationWitness> collWits, Comparator<Token> comp, OutputStream dest) throws IOException {
      List<Mote> motes = new CollateXEngine().collate(collWits, comp);
      List<List<Annotation>> moteAnns = createMoteAnnotations(motes);

      ObjectMapper mapper = getObjectMapper();
      SimpleModule mod = new SimpleModule();
      mod.addSerializer(Annotation.class, new MoteSerializer());
      mapper.registerModule(mod);
      
      mapper.writeValue(dest, moteAnns);
   }

   /**
    * Take the motes returned by the collator and turn them into a list of "tr-mote" annotations.
    * @param motes list of motes from collator
    * @return corresponding list of "tr-mote" type annotations
    */
   private static List<List<Annotation>> createMoteAnnotations(List<Mote> motes) {

      // Create the new Parallels based on the request body.
      List<List<Annotation>> newPars = new ArrayList<>();
      for (Mote m: motes) {
         List<Annotation> newAnns = new ArrayList<>();
         for (TextAnchor anch: m.getAnchors()) {
            Transcription t = anch.getStartPage().getTranscription();
            Annotation ann = new Annotation();
            ann.setType("tr-mote");
            ann.setContent(t.getText(anch));
            ann.setStartPageID(anch.getStartPage().getID());
            ann.setStartOffset(anch.getStartOffset());
            ann.setEndPageID(anch.getEndPage().getID());
            ann.setEndOffset(anch.getEndOffset());
            ann.setTargetFragment(Integer.toString(t.getWitnessID()));
            newAnns.add(ann);
         }
         newPars.add(newAnns);
      }
      return newPars;
   }
   
   private static final Logger LOG = Logger.getLogger(CollationServlet.class.getName());
}
