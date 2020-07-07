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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.annotation.OwnAnnotationFilter;
import edu.slu.tradamus.db.LinesSuppressedMixIn;
import edu.slu.tradamus.log.Activity;
import edu.slu.tradamus.log.Operation;
import edu.slu.tradamus.text.Page;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.*;


/**
 * Servlet which provides access to the contents of a single page.
 *
 * @author tarkvara
 */
public class PageServlet extends HttpServlet {

   /**
    * Handles the HTTP <code>GET</code> method, returning details of the specified page.
    *
    * @param request servlet request
    * @param response servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String[] pathParts = getPathParts(req);
      if (pathParts.length == 2) {
         // /page/<pgID> to get the page itself.
         SimpleModule mod = new SimpleModule();
         mod.setMixInAnnotation(Page.class, LinesSuppressedMixIn.class);
         loadAndSendEntity(req, resp, Page.class, mod);
      } else {
         try {
            // Expecting /page/<pgID>/annotations or /page/<pgID>/lines
            switch (pathParts[2]) {
               case "annotations":
                  loadAndSendChildren(req, resp, Page.class, pathParts[1], Page.SELECT_ANNOTATIONS_ON_PAGE, Annotation.class, null);
                  break;
               case "lines":
                  loadAndSendChildren(req, resp, Page.class, pathParts[1], Page.SELECT_LINES, Annotation.class, null);
                  break;
               default:
                  resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
                  break;
            }
         } catch (ArrayIndexOutOfBoundsException ex) {
            reportInternalError(resp, ex);
         }
      }
   }

   /**
    * We can POST to <code>/page/pgID/annotations</code> to add a single annotation.
    * @param req servlet request
    * @param resp servlet response
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String[] pathParts = getPathParts(req);
      if (pathParts.length == 3) {
         switch (pathParts[2]) {
            case "annotations":
               postAnnotation(req, resp, Page.class);
               break;
            default:
               resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
               break;
         }
      }
   }

   /**
    * Handles the HTTP <code>PUT</code> method, updating details of the specified page.
    *
    * @param request servlet request
    * @param response servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String[] pathParts = getPathParts(req);
      if (pathParts.length == 2) {
         // /page/<pgID> to modify the witness itself.
         modifyEntity(req, resp, Page.class);
      } else {
         int uID = getUserID(req, resp);
         if (uID > 0) {
            try (Connection conn = getDBConnection()) {
               boolean replacing = !getBooleanParameter(req, "merge", true);
               // Expecting /page/<pgID>/annotations or /page/<pgID>/lines.
               Page pg = new Page(Integer.parseInt(pathParts[1]));
               switch (pathParts[2]) {
                  case "annotations":
                     doPutAnnotations(req, conn, uID, pg, replacing);
                     resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                     break;
                  case "lines":
                     doPutLines(req, conn, uID, pg, replacing);
                     resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                     break;
                  default:
                     resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
                     break;
               }
            } catch (SQLException | ArrayIndexOutOfBoundsException | NumberFormatException | PermissionException | ReflectiveOperationException ex) {
               reportInternalError(resp, ex);
            }
         }
      }
   }

   /**
    * Update the annotations for the given page.
    * @param req
    * @param pg 
    */
   private void doPutAnnotations(HttpServletRequest req, Connection conn, int uID, Page pg, boolean replacing) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Role r = pg.checkPermission(conn, uID, Role.CONTRIBUTOR);
      ObjectMapper mapper = getObjectMapper();
      List<Annotation> newAnns = mapper.readValue(req.getInputStream(), new TypeReference<List<Annotation>>() {});
      for (Annotation ann: newAnns) {
         ann.setModifiedBy(uID, r);
      }
      List<Annotation> oldAnns = pg.loadChildren(conn, Page.SELECT_ANNOTATIONS_ON_PAGE, Annotation.class, false);
      
      conn.setAutoCommit(false);
      pg.mergeChildren(conn, oldAnns, newAnns, Annotation.getMergingComparator(), new OwnAnnotationFilter(uID), replacing);
      Activity.record(conn, uID, pg, Operation.UPDATE, mapper.writeValueAsString(newAnns));
      conn.commit();
   }

   /**
    * Update the lines for the given page.
    * @param req
    * @param pg 
    */
   private void doPutLines(HttpServletRequest req, Connection conn, int uID, Page pg, boolean replacing) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Role r = pg.checkPermission(conn, uID, Role.CONTRIBUTOR);
      ObjectMapper mapper = getObjectMapper();
      List<Annotation> newLines = mapper.readValue(req.getInputStream(), new TypeReference<List<Annotation>>() {});
      for (Annotation line: newLines) {
         line.setModifiedBy(uID, r);
      }
      List<Annotation> oldLines = pg.loadChildren(conn, Page.SELECT_LINES, Annotation.class, false);
      
      conn.setAutoCommit(false);
      pg.mergeChildren(conn, oldLines, newLines, Annotation.getMergingComparator(), null, replacing);
      Activity.record(conn, uID, pg, Operation.UPDATE, mapper.writeValueAsString(newLines));
      conn.commit();
   }

   /**
    * Returns a short description of the servlet.
    *
    * @return a String containing servlet description
    */
   @Override
   public String getServletInfo() {
      return "Tradamus Page Details Servlet";
   }
}
