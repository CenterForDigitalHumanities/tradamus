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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLStreamException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.annotation.OwnAnnotationFilter;
import edu.slu.tradamus.annotation.SimplifiedAnnotationSerializer;
import edu.slu.tradamus.db.EntityIDSerializer;
import edu.slu.tradamus.db.LinesSuppressedMixIn;
import edu.slu.tradamus.image.Canvas;
import edu.slu.tradamus.image.Manifest;
import edu.slu.tradamus.log.Activity;
import edu.slu.tradamus.log.Operation;
import edu.slu.tradamus.text.Transcription;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.*;
import edu.slu.tradamus.witness.JsonLDExport;
import edu.slu.tradamus.witness.Witness;


/**
 * Servlet which deals directly with individual witness entities.
 *
 * @author tarkvara
 */
public class WitnessServlet extends HttpServlet {

   /**
    * Retrieve details of the specified witness from the database.
    *
    * @param req
    * @param resp 
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
                  switch (formatParam) {
                     case "json":
                        doGetJsonDump(resp, id, uID);
                        break;
                     case "ld+json":
                     case "json-ld":   // Incorrect, but we should accept it.
                     case "jsonld":    // Incorrect, but we should accept it.
                        doGetJsonLDDump(getServerURL(req), resp, id, uID);
                        break;
                     default:
                        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unrecognized \"format=%s\" parameter", formatParam));
                        break;
                  }
               } else {
                  SimpleModule mod = new SimpleModule();
                  mod.addSerializer(Manifest.class, new EntityIDSerializer());
                  mod.addSerializer(Transcription.class, new EntityIDSerializer());
                  mod.addSerializer(Annotation.class, new SimplifiedAnnotationSerializer());
                  loadAndSendEntity(req, resp, Witness.class, mod);
               }
            } else {
               // Expecting /witness/<witID>/annotations.  Used to handle metadata here, but that is now
               // returned as part of the witness details.
               switch (pathParts[2]) {
                  case "annotations":
                     doGetAnnotations(resp, id, uID);
                     break;
                  default:
                     resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
                     break;
               }
            }
         } catch (ArrayIndexOutOfBoundsException | NumberFormatException | SQLException | PermissionException | ReflectiveOperationException ex) {
            reportInternalError(resp, ex);
         }
   }

   private void doGetJsonDump(HttpServletResponse resp, int id, int uID) throws IOException, SQLException, PermissionException, ReflectiveOperationException, ServletException {
      try (Connection conn = getDBConnection()) {
         Witness wit = new Witness(id);
         wit.checkPermission(conn, uID, Role.VIEWER);
         wit.load(conn, true);

         // Suppress getLines() on the canvasses because we're already getting lines on the pages.
         ObjectMapper mapper = getObjectMapper();
         SimpleModule mod = new SimpleModule();
         mod.setMixInAnnotation(Canvas.class, LinesSuppressedMixIn.class);
         mapper.registerModule(mod);

         resp.setContentType("application/json; charset=UTF-8");
         // Suppress getLines() on the canvasses because we're already getting lines on the pages.
         mapper.writer().withDefaultPrettyPrinter().writeValue(resp.getOutputStream(), wit);
      }
   }

   private void doGetJsonLDDump(URL serverURL, HttpServletResponse resp, int id, int uID) throws IOException, SQLException, PermissionException, ReflectiveOperationException, ServletException {
      try (Connection conn = getDBConnection()) {
         Witness wit = new Witness(id);
         wit.checkPermission(conn, uID, Role.VIEWER);
         wit.load(conn, true);

         resp.setContentType("application/ld+json; charset=UTF-8");
         getObjectMapper().writer().withDefaultPrettyPrinter().writeValue(resp.getOutputStream(), new JsonLDExport(serverURL, wit));
      }
   }

   private void doGetAnnotations(HttpServletResponse resp, int id, int uID) throws ReflectiveOperationException, IOException, SQLException, ServletException, PermissionException {
      try (Connection conn = getDBConnection()) {
         Witness wit = new Witness(id);
         wit.checkPermission(conn, uID, Role.VIEWER);
         List<Annotation> anns = wit.loadAllAnnotations(conn);
         
         ObjectMapper mapper = new ObjectMapper();
         SimpleModule mod = new SimpleModule();
         mod.addSerializer(Annotation.class, new SimplifiedAnnotationSerializer());
         mapper.registerModule(mod);

         resp.setContentType("application/json; charset=UTF-8");
         mapper.writer().withDefaultPrettyPrinter().writeValue(resp.getOutputStream(), anns);
      }
   }

   /**
    * We can POST to <code>/witness/witID/metadata</code> to add a single metadatum.
    * @param req servlet request
    * @param resp servlet response
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String[] pathParts = getPathParts(req);
      if (pathParts.length == 3) {
         switch (pathParts[2]) {
            case "metadata":
               postAnnotation(req, resp, Witness.class);
               break;
            default:
               resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
               break;
         }
      }
   }


   /**
    * Handles the <code>PUT</code> to modify fields within the witness.
    * @param req
    * @param resp
    * @throws ServletException
    * @throws IOException 
    */
   @Override
   protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      int uID = getUserID(req, resp);
      if (uID > 0) {
         try (Connection conn = getDBConnection()) {
            String[] pathParts = getPathParts(req);
            Witness wit = new Witness(Integer.parseInt(pathParts[1]));
            if (pathParts.length == 2) {
               // /witness/<witID> to modify the witness itself, either with JSON or JSON-LD.
               String[] contentType = new String[1];
               InputStream input = getInputStream(req, contentType);
               switch (contentType[0]) {
                  case "application/json":
                     modifyEntity(wit, conn, input, resp, uID);
                     break;
                  case "application/ld+json":
                     wit.updateFromJsonLD(conn, uID, input);
                     resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                     break;
                  default:
                     resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Expecting application/json or application/ld+json content");
               }
            } else {
               // Expecting /witness/<witID>/approval or /witness/<witID>/metadata
               boolean replacing = !getBooleanParameter(req, "merge", true);
               switch (pathParts[2]) {
                  case "approval":
                     putApproval(req, conn, uID, wit, "UPDATE annotations SET approved_by = ? " +
                        "WHERE target_type = 'WITNESS' AND target = ?");
                     resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                     break;
                  case "metadata":
                     doPutMetadata(req, conn, uID, wit, replacing);
                     resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                     break;
                  default:
                     resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
                     break;
               }
            }
         } catch (SQLException | ArrayIndexOutOfBoundsException | NumberFormatException | PermissionException | ReflectiveOperationException | XMLStreamException ex) {
            reportInternalError(resp, ex);               
         }
      }
   }

   private void doPutMetadata(HttpServletRequest req, Connection conn, int uID, Witness wit, boolean replacing) throws IOException, SQLException, ServletException, PermissionException, ReflectiveOperationException {
      Role r = wit.checkPermission(conn, uID, Role.EDITOR);
      ObjectMapper mapper = getObjectMapper();
      List<Annotation> newMetas = mapper.readValue(req.getInputStream(), new TypeReference<List<Annotation>>() {});
      for (Annotation meta: newMetas) {
         meta.setTarget(wit);
         meta.setModifiedBy(uID, r);
      }
      List<Annotation> oldMetas = wit.loadChildren(conn, Witness.SELECT_METADATA, Annotation.class, false);
      
      conn.setAutoCommit(false);
      wit.mergeChildren(conn, oldMetas, newMetas, Annotation.getIDComparator(), new OwnAnnotationFilter(uID), replacing);
      Activity.record(conn, uID, wit, Operation.UPDATE, mapper.writeValueAsString(newMetas));
      conn.commit();
   }
   
   /**
    * Remove the specified witness from the database.
    *
    * @param request
    * @param response 
    */
   @Override
   protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
      deleteEntity(req, resp, Witness.class);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getServletInfo() {
      return "Tradamus Witness Details Servlet";
   }
}
