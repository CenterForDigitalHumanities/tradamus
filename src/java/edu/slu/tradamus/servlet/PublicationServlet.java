/*
 * Copyright 2014 Saint Louis University. Licensed under the
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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import edu.slu.tradamus.db.EntityIDSerializer;
import edu.slu.tradamus.edition.Edition;
import edu.slu.tradamus.log.Activity;
import edu.slu.tradamus.log.Operation;
import edu.slu.tradamus.publication.Publication;
import edu.slu.tradamus.publication.Section;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.*;

/**
 * Servlet which retrieves and updates information relating to individual Publications.
 *
 * @author tarkvara
 */
public class PublicationServlet extends HttpServlet {

   /**
    * Delete the specified Publication.
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException
    * @throws IOException 
    */
   @Override
   protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      deleteEntity(req, resp, Publication.class);
   }


   /**
    * Handles the HTTP <code>GET</code> method, returning details of the given Publication.
    *
    * @param request servlet request
    * @param response servlet response
    */
   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      int uID = getUserID(req, resp);
         String[] pathParts = getPathParts(req);
         try {
            int id = Integer.parseInt(pathParts[1]);
            String formatParam = req.getParameter("format");
            if (formatParam != null) {
               // Export edition to JSON.
               switch (formatParam) {
                  case "json":
                     doGetJsonDump(resp, id, uID);
                     break;
                  default:
                     resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unrecognized \"format=%s\" parameter", formatParam));
                     break;
               }
            } else {
               // /publication/<publID> to get the Publication itself.
               SimpleModule mod = new SimpleModule();
               mod.addSerializer(Edition.class, new EntityIDSerializer());
               mod.addSerializer(Section.class, new EntityIDSerializer());
               loadAndSendEntity(req, resp, Publication.class, mod);
            }
         } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | SQLException | PermissionException | ReflectiveOperationException ex) {
            reportInternalError(resp, ex);
         }
   }

   private void doGetJsonDump(HttpServletResponse resp, int id, int uID) throws ServletException, SQLException, PermissionException, ReflectiveOperationException, IOException {
       try (Connection conn = getDBConnection()) {
         Publication ed = new Publication(id);
         ed.checkPermission(conn, uID, Role.VIEWER);
         ed.load(conn, true);

         ObjectMapper mapper = getObjectMapper();
         resp.setContentType("application/json; charset=UTF-8");
         mapper.writer().withDefaultPrettyPrinter().writeValue(resp.getOutputStream(), ed);
      }
   }
      
   
   /**
    * We can POST to <code>/publication/pubID/sections</code> to add a single section.
    * @param req servlet request
    * @param resp servlet response
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String[] pathParts = getPathParts(req);
      try {
         switch (pathParts[2]) {
            case "sections":
               doPostSection(req, resp);
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
    * Parse the request and attach a single POSTed section to the publication.
    * @param req
    * @param resp
    * @throws IOException
    * @throws ServletException 
    */
   private void doPostSection(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
      int uID = getUserID(req, resp);
      if (uID > 0) {
         try (Connection conn = getDBConnection()) {
            int id = Integer.parseInt(getPathParts(req)[1]);
            Publication pub = new Publication(id);
            pub.checkPermission(conn, uID, Role.CONTRIBUTOR);
            ObjectMapper mapper = getObjectMapper();
            Section sect = mapper.readValue(req.getInputStream(), Section.class);
            sect.setPublication(pub);

            conn.setAutoCommit(false);
            sect.insert(conn);
            Activity.record(conn, uID, sect, pub, mapper.writeValueAsString(sect));
            conn.commit();

            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.setHeader("Location", "/section/" + sect.getID());
         } catch (SQLException | ArrayIndexOutOfBoundsException | NumberFormatException | PermissionException ex) {
            reportInternalError(resp, ex);
         }
      }
   }

   /**
    * Implements the <code>PUT</code> method, used to modify permissions and sections, replacing
    * the existing contents.
    * @param req servlet request
    * @param resp servlet response
    */
   @Override
   protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      if (req.getContentLength() <= 0 || getBaseContentType(req).equals("application/json")) {
         String[] pathParts = getPathParts(req);
         if (pathParts.length == 2) {
            // /publication/<pubID> to modify the publication itself.
            modifyEntity(req, resp, Publication.class);
         } else {
            int uID = getUserID(req, resp);
            if (uID > 0) {
               try (Connection conn = getDBConnection()) {
                  boolean replacing = !getBooleanParameter(req, "merge", true);
                  // Expecting /publication/<pubID>/sections or /publication/<pubID>/permissions
                  Publication pub = new Publication(Integer.parseInt(pathParts[1]));
                  switch (pathParts[2]) {
                     case "permissions":
                        putPermissions(req, conn, uID, pub, Publication.SELECT_PERMISSIONS_WITH_USERS, replacing);
                        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        break;
                     case "sections":
                        doPutSections(req, conn, uID, pub, replacing);
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

   private void doPutSections(HttpServletRequest req, Connection conn, int uID, Publication pub, boolean replacing) throws IOException, ServletException, SQLException, PermissionException, ReflectiveOperationException {
      pub.checkPermission(conn, uID, Role.CONTRIBUTOR);

      ObjectMapper mapper = getObjectMapper();
      List<Section> newSects = mapper.readValue(req.getInputStream(), new TypeReference<List<Section>>() {});
      for (Section sect: newSects) {
         sect.setPublicationID(pub.getID());
      }
      List<Section> oldSects = pub.loadChildren(conn, Publication.SELECT_SECTIONS, Section.class, false);

      conn.setAutoCommit(false);
      pub.mergeChildren(conn, oldSects, newSects, Section.getMergingComparator(), null, replacing);
      Activity.record(conn, uID, pub, Operation.UPDATE, mapper.writeValueAsString(newSects));
      conn.commit();
   }

   /**
    * Returns a short description of the servlet.
    *
    * @return a String containing servlet description
    */
   @Override
   public String getServletInfo() {
      return "Tradamus Publication Details Servlet";
   }
   
   private static final Logger LOG = Logger.getLogger(PublicationServlet.class.getName());
}
