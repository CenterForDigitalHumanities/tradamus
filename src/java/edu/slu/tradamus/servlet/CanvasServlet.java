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
import edu.slu.tradamus.image.Canvas;
import edu.slu.tradamus.image.Image;
import edu.slu.tradamus.log.Activity;
import edu.slu.tradamus.log.Operation;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.*;


/**
 * Servlet which provides access to the contents of a single canvas.
 *
 * @author tarkvara
 */
public class CanvasServlet extends HttpServlet {

   /**
    * Handles the HTTP <code>GET</code> method, returning details of the specified canvas.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String[] pathParts = getPathParts(req);
      if (pathParts.length == 2) {
         // /canvas/<canvID> to get the canvas itself.
         SimpleModule mod = new SimpleModule();
         mod.setMixInAnnotation(Canvas.class, LinesSuppressedMixIn.class);
         loadAndSendEntity(req, resp, Canvas.class, mod);
      } else {
         try {
            // Expecting /canvas/<canvID>/annotations or /canvas/<canvID>/lines
            switch (pathParts[2]) {
               case "annotations":
                  loadAndSendChildren(req, resp, Canvas.class, pathParts[1], "SELECT * FROM annotations WHERE canvas = ?", Annotation.class, null);
                  break;
               case "lines":
                  loadAndSendChildren(req, resp, Canvas.class, pathParts[1], Canvas.SELECT_LINES, Annotation.class, null);
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
    * We can POST to <code>/canvas/canvID/annotations</code> to add a single annotation.
    * @param req servlet request
    * @param resp servlet response
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String[] pathParts = getPathParts(req);
      if (pathParts.length == 3) {
         switch (pathParts[2]) {
            case "annotations":
               postAnnotation(req, resp, Canvas.class);
               break;
            default:
               resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
               break;
         }
      }
   }

   
   /**
    * The <code>PUT</code> method can be used either to update the canvas itself, or its associated
    * annotations.
    */
   @Override
   protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String[] pathParts = getPathParts(req);
      if (pathParts.length == 2) {
         // /canvas/<canvID> to modify the canvas itself.
         modifyEntity(req, resp, Canvas.class);
      } else {
         int uID = getUserID(req, resp);
         if (uID > 0) {
            try (Connection conn = getDBConnection()) {
               boolean replacing = !getBooleanParameter(req, "merge", true);
               // Expecting /canvas/<canvID>/annotations or /canvas/<canvID>/lines.
               Canvas canv = new Canvas(Integer.parseInt(pathParts[1]));
               switch (pathParts[2]) {
                  case "annotations":
                     doPutAnnotations(req, conn, uID, canv, replacing);
                     resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                     break;
                  case "images":
                     doPutImages(req, conn, uID, canv, replacing);
                     resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                     break;
                  case "lines":
                     doPutLines(req, conn, uID, canv, replacing);
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

   private void doPutAnnotations(HttpServletRequest req, Connection conn, int uID,  Canvas canv, boolean replacing) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Role r = canv.checkPermission(conn, uID, Role.CONTRIBUTOR);
      ObjectMapper mapper = getObjectMapper();
      List<Annotation> newAnns = mapper.readValue(req.getInputStream(), new TypeReference<List<Annotation>>() {});
      for (Annotation ann: newAnns) {
         ann.setModifiedBy(uID, r);
         ann.setTarget(canv);
      }
      List<Annotation> oldAnns = canv.loadChildren(conn, Canvas.SELECT_ANNOTATIONS, Annotation.class, false);

      conn.setAutoCommit(false);
      canv.mergeChildren(conn, oldAnns, newAnns, Annotation.getMergingComparator(), r == Role.CONTRIBUTOR ? new OwnAnnotationFilter(uID) : null, replacing);
      Activity.record(conn, uID, canv, Operation.UPDATE, mapper.writeValueAsString(newAnns));
      conn.commit();
   }

   private void doPutImages(HttpServletRequest req, Connection conn, int uID,  Canvas canv, boolean replacing) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Role r = canv.checkPermission(conn, uID, Role.EDITOR);
      ObjectMapper mapper = getObjectMapper();
      List<Image> newIms = mapper.readValue(req.getInputStream(), new TypeReference<List<Image>>() {});
      List<Image> oldIms = canv.loadChildren(conn, Canvas.SELECT_IMAGES, Image.class, false);

      conn.setAutoCommit(false);
      canv.mergeChildren(conn, oldIms, newIms, Image.getMergingComparator(), null, replacing);
      Activity.record(conn, uID, canv, Operation.UPDATE, mapper.writeValueAsString(newIms));
      conn.commit();
   }

   private void doPutLines(HttpServletRequest req, Connection conn, int uID, Canvas canv, boolean replacing) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Role r = canv.checkPermission(conn, uID, Role.CONTRIBUTOR);
      ObjectMapper mapper = getObjectMapper();
      List<Annotation> newLines = mapper.readValue(req.getInputStream(), new TypeReference<List<Annotation>>() {});
      for (Annotation line: newLines) {
         line.setModifiedBy(uID, r);
      }
      List<Annotation> oldLines = canv.loadChildren(conn, Canvas.SELECT_LINES, Annotation.class, false);
      
      conn.setAutoCommit(false);
      canv.mergeChildren(conn, oldLines, newLines, Annotation.getMergingComparator(), new OwnAnnotationFilter(uID), replacing);
      Activity.record(conn, uID, canv, Operation.UPDATE, mapper.writeValueAsString(newLines));
      conn.commit();
   }

   /**
    * Returns a short description of the servlet.
    *
    * @return a String containing servlet description
    */
   @Override
   public String getServletInfo() {
      return "Tradamus Canvas Details Servlet";
   }
}
