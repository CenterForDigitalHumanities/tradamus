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
package edu.slu.tradamus.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLStreamException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.annotation.AnnotationSnarfingDeserializerModifier;
import edu.slu.tradamus.annotation.OwnAnnotationFilter;
import edu.slu.tradamus.annotation.SimplifiedAnnotationSerializer;
import edu.slu.tradamus.db.EntityIDSerializer;
import edu.slu.tradamus.db.LinesSuppressedMixIn;
import edu.slu.tradamus.db.NamedLock;
import edu.slu.tradamus.edition.Edition;
import edu.slu.tradamus.edition.Outline;
import edu.slu.tradamus.edition.Parallel;
import edu.slu.tradamus.image.Canvas;
import edu.slu.tradamus.log.Activity;
import edu.slu.tradamus.log.Operation;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.*;
import edu.slu.tradamus.witness.*;


/**
 * Servlet which provides details about individual editions.
 *
 * @author tarkvara
 */
public class EditionServlet extends HttpServlet {

   /**
    * Delete the specified edition.
    * @param req
    * @param resp
    * @throws ServletException
    * @throws IOException 
    */
   @Override
   protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      deleteEntity(req, resp, Edition.class);
   }

   
   /**
    * Handles the HTTP <code>GET</code> method, returning details of the given edition.
    *
    * @param request servlet request
    * @param response servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      int uID = getUserID(req, resp);
         String[] pathParts = getPathParts(req);
         try {
            int id = Integer.parseInt(pathParts[1]);
            if (pathParts.length == 2) {
               String formatParam = req.getParameter("format");
               if (formatParam != null) {
                  // Export edition to JSON, IIIF JSON-LD or TEI.  For now, only JSON dump is supported.
                  switch (formatParam) {
                     case "json":
                        doGetJsonDump(resp, id, uID);
                        break;
                     default:
                        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unrecognized \"format=%s\" parameter", formatParam));
                        break;
                  }
               } else {
                  // /edition/<edID> to get the edition itself.
                  SimpleModule mod = new SimpleModule();
                  mod.addSerializer(Annotation.class, new SimplifiedAnnotationSerializer());
                  mod.addSerializer(Outline.class, new EntityIDSerializer());
                  mod.addSerializer(Witness.class, new SimplifiedWitnessSerializer());
                  loadAndSendEntity(req, resp, Edition.class, mod);
               }
            } else {
               // Expecting /edition/<edID>/annotations.
               switch (pathParts[2]) {
                  case "annotations":
                     doGetAnnotations(resp, id, uID);
                     break;
                  default:
                     resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
                     break;
               }
            }
         } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | SQLException | PermissionException | ReflectiveOperationException ex) {
            reportInternalError(resp, ex);
         }
   }

   private void doGetJsonDump(HttpServletResponse resp, int id, int uID) throws ServletException, SQLException, PermissionException, ReflectiveOperationException, IOException {
      try (Connection conn = getDBConnection()) {
         Edition ed = new Edition(id);
         ed.checkPermission(conn, uID, Role.VIEWER);
         ed.load(conn, true);

         // Suppress getLines() on the canvasses because we're already getting lines on the pages.
         ObjectMapper mapper = getObjectMapper();
         SimpleModule mod = new SimpleModule();
         mod.setMixInAnnotation(Canvas.class, LinesSuppressedMixIn.class);
         mapper.registerModule(mod);

         resp.setContentType("application/json; charset=UTF-8");
         mapper.writer().withDefaultPrettyPrinter().writeValue(resp.getOutputStream(), ed);
      }
   }
      
   private void doGetAnnotations(HttpServletResponse resp, int id, int uID) throws ServletException, SQLException, PermissionException, ReflectiveOperationException, IOException {
      Edition ed = new Edition(id);
      try (Connection conn = getDBConnection()) {
         ed.checkPermission(conn, uID, Role.VIEWER);
         List<Annotation> anns = ed.loadAllAnnotations(conn);

         ObjectMapper mapper = getObjectMapper();
         SimpleModule mod = new SimpleModule();
         mod.addSerializer(Annotation.class, new SimplifiedAnnotationSerializer());
         mapper.registerModule(mod);

         resp.setContentType("application/json; charset=UTF-8");
         mapper.writer().withDefaultPrettyPrinter().writeValue(resp.getOutputStream(), anns);
      }
   }
  

   /**
    * We can POST to <code>/edition/edID/whatever</code> to add a single metadatum, outline, or witness.
    * @param req servlet request
    * @param resp servlet response
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String[] pathParts = getPathParts(req);
      try {
         switch (pathParts[2]) {
            case "metadata":
               postAnnotation(req, resp, Edition.class);
               break;
            case "outlines":
               doPostOutline(req, resp);
               break;
            case "witnesses":
               doPostWitness(req, resp, Integer.parseInt(pathParts[1]));
               break;
            default:
               resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
               break;
         }
      } catch (ArrayIndexOutOfBoundsException | NumberFormatException ex) {
         reportInternalError(resp, ex);
      }
   }

   /**
    * Parse the request and attach a single POSTed outline to the edition.
    * @param req
    * @param resp
    * @throws IOException
    * @throws ServletException 
    */
   private void doPostOutline(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
      int uID = getUserID(req, resp);
      if (uID > 0) {
         try (Connection conn = getDBConnection()) {
            int id = Integer.parseInt(getPathParts(req)[1]);
            Edition ed = new Edition(id);
            Role r = ed.checkPermission(conn, uID, Role.CONTRIBUTOR);
            ObjectMapper mapper = getObjectMapper();
            Outline outl = mapper.readValue(req.getInputStream(), Outline.class);
            outl.setEdition(ed);
            outl.setDecisionsModifiedBy(uID, r);
            outl.fixSources(new ArrayList<Parallel>(), ed.getPageWitnesses(conn));
            try (NamedLock lock = new NamedLock(conn, POST_OUTLINE_LOCK)) {
               outl.insert(conn);
               Activity.record(conn, uID, outl, ed, mapper.writeValueAsString(outl));
               resp.setStatus(HttpServletResponse.SC_CREATED);
               resp.setHeader("Location", "/outline/" + outl.getID());
            }
         } catch (SQLException | ArrayIndexOutOfBoundsException | NumberFormatException | PermissionException ex) {
            reportInternalError(resp, ex);
         }
      }
   }

   public static void doPostWitness(HttpServletRequest req, HttpServletResponse resp, int edID) throws IOException, ServletException {
      int uID = getUserID(req, resp);
         try (Connection conn = getDBConnection()) {
            Edition ed = new Edition(edID);
            ed.load(conn, false);
            ed.checkPermission(conn, uID, Role.EDITOR);

            String[] contentTypeBuf = new String[1];
            InputStream input = getInputStream(req, contentTypeBuf);
            String contentType = getBaseContentType(contentTypeBuf[0]);
            Witness wit;
            switch (contentType) {
               case "text/html":
               case "text/plain":
                  wit = new PlainTextWitness(ed, input, req.getParameter("lineBreak"), req.getParameter("pageBreak"), getCharset(contentTypeBuf[0]), req.getParameter("title"), req.getParameter("siglum"));
                  break;
               case "text/xml":
               case "application/tei+xml":
                  String imageBase = req.getParameter("imageBase");
                  if (imageBase == null) {
                     imageBase = "";
                  } else if (imageBase.charAt(imageBase.length() - 1) != '/') {
                     imageBase += '/';
                  }
                  wit = new XMLWitness(ed, input, imageBase);
                  break;
               case "application/json":
                  wit = deserializeJsonWitness(ed, input);
                  break;
               case "application/ld+json":
                  wit = new JsonLDWitness(ed, input, req.getParameter("src"));
                  break;
               default:
                  resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
                  return;
            }

            try (NamedLock lock = new NamedLock(conn, POST_WITNESS_LOCK)) {
               wit.fixAttributions(uID, Role.EDITOR);
               wit.insert(conn);
               Activity.record(conn, uID, wit, ed, wit.getTitle());
               resp.setHeader("Location", "/witness/" + wit.getID());
               resp.setStatus(HttpServletResponse.SC_CREATED);
            }
         } catch (SQLException | PermissionException | ReflectiveOperationException ex) {
            reportInternalError(resp, ex);
         } catch (URISyntaxException | XMLStreamException ex) {
            throw new ServletException(ex);
         }
   }

   private static Witness deserializeJsonWitness(Edition ed, InputStream input) throws IOException {
      ObjectMapper mapper = getObjectMapper();
      SimpleModule mod = new SimpleModule();
      AnnotationSnarfingDeserializerModifier modifier = new AnnotationSnarfingDeserializerModifier();
      mod.setDeserializerModifier(modifier);
      mapper.registerModule(mod);

      Witness wit = mapper.readValue(input, Witness.class);
      wit.setEditionID(ed.getID());
      wit.fixTargets();
      return wit;
   }

   /**
    * Implements the <code>PUT</code> method, used to modify metadata and permissions, replacing
    * the existing contents.
    * @param req servlet request
    * @param resp servlet response
    */
   @Override
   protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      if (req.getContentLength() <= 0 || getBaseContentType(req).equals("application/json")) {
         String[] pathParts = getPathParts(req);
         if (pathParts.length == 2) {
            // /edition/<editionID> to modify the edition itself.
            modifyEntity(req, resp, Edition.class);
         } else {
            int uID = getUserID(req, resp);
            if (uID > 0) {
               try (Connection conn = getDBConnection()) {
                  boolean replacing = !getBooleanParameter(req, "merge", true);
                  // Expecting /edition/<edID>/decisions, /edition/<edID>/permissions, or
                  // /edition/<edID>/motes
                  Edition ed = new Edition(Integer.parseInt(pathParts[1]));
                  switch (pathParts[2]) {
                     case "approval":
                        putApproval(req, conn, uID, ed, "UPDATE annotations SET approved_by = ? " +
                           "WHERE target_type = 'EDITION' AND target = ?");
                        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        break;
                     case "metadata":
                        doPutMetadata(req, conn, uID, ed, replacing);
                        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        break;
                     case "outlines":
                        doPutOutlines(req, conn, uID, ed, replacing);
                        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        break;
                     case "permissions":
                        putPermissions(req, conn, uID, ed, Edition.SELECT_PERMISSIONS_WITH_USERS, replacing);
                        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        break;
                     default:
                        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
                        break;
                  }
               } catch (SQLException | ArrayIndexOutOfBoundsException | NumberFormatException | PermissionException | JsonProcessingException | ReflectiveOperationException ex) {
                  reportInternalError(resp, ex);
               }
            }
         }
      } else {
         resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Expecting application/json");
      }
   }

   /**
    * Update the metadata Annotations associated with this Edition.
    * @param req servlet request
    * @param conn connection to SQL database
    * @param uID User ID
    * @param ed Edition to be modified
    * @param replacing if true, the request body will complete replace the edition's metadata; if false, the new annotations will be merged with the existing ones
    */
   private void doPutMetadata(HttpServletRequest req, Connection conn, int uID, Edition ed, boolean replacing) throws IOException, ServletException, SQLException, PermissionException, ReflectiveOperationException {
      Role r = ed.checkPermission(conn, uID, Role.CONTRIBUTOR);

      ObjectMapper mapper = getObjectMapper();
      List<Annotation> newMetas = mapper.readValue(req.getInputStream(), new TypeReference<List<Annotation>>() {});
      for (Annotation meta: newMetas) {
         meta.setTarget(ed);
         meta.setModifiedBy(uID, r);
      }
      List<Annotation> oldMetas = ed.loadChildren(conn, Edition.SELECT_METADATA, Annotation.class, false);
      
      conn.setAutoCommit(false);
      ed.mergeChildren(conn, oldMetas, newMetas, Annotation.getIDComparator(), r == Role.CONTRIBUTOR ? new OwnAnnotationFilter(uID) : null, replacing);
      Activity.record(conn, uID, ed, Operation.UPDATE, mapper.writeValueAsString(newMetas));
      conn.commit();
   }

   private void doPutOutlines(HttpServletRequest req, Connection conn, int uID, Edition ed, boolean replacing) throws IOException, ServletException, SQLException, PermissionException, ReflectiveOperationException {
      Role r = ed.checkPermission(conn, uID, Role.CONTRIBUTOR);

      ObjectMapper mapper = getObjectMapper();
      List<Outline> newOutls = mapper.readValue(req.getInputStream(), new TypeReference<List<Outline>>() {});
      Map<Integer, Witness> pageWits = ed.getPageWitnesses(conn);
      List<Parallel> knownPars = new ArrayList<>();
      for (Outline outl: newOutls) {
         outl.setEditionID(ed.getID());
         outl.setDecisionsModifiedBy(uID, r);
         outl.fixSources(knownPars, pageWits);
      }
      List<Outline> oldOutls = ed.loadChildren(conn, Edition.SELECT_OUTLINES, Outline.class, false);

      conn.setAutoCommit(false);
      ed.mergeChildren(conn, oldOutls, newOutls, Outline.getMergingComparator(), null, replacing);
      Activity.record(conn, uID, ed, Operation.UPDATE, mapper.writeValueAsString(newOutls));
      conn.commit();
   }

   /**
    * Returns a short description of the servlet.
    *
    * @return a String containing servlet description
    */
   @Override
   public String getServletInfo() {
      return "Tradamus Edition Details Servlet";
   }

   private static final String POST_OUTLINE_LOCK = "tradamus.post_outline";
   private static final String POST_WITNESS_LOCK = "tradamus.post_witness";

   private static final Logger LOG = Logger.getLogger(EditionServlet.class.getName());
}
