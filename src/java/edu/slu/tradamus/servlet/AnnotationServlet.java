/*
 * Copyright 2014-2015 Saint Louis University. Licensed under the
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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.annotation.OwnAnnotationFilter;
import edu.slu.tradamus.log.Activity;
import edu.slu.tradamus.log.Operation;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.*;

/**
 * Servlet for directly retrieving and modifying individual annotations.
 *
 * @author tarkvara
 */
public class AnnotationServlet extends HttpServlet {

   /**
    * Handles the HTTP <code>GET</code> method to retrieve the annotation's details.
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
         // /annotation/<annID> to get the canvas itself.
         loadAndSendEntity(req, resp, Annotation.class, null);
      } else {
         try {
            // Expecting /annotation/<annID>/annotations.
            switch (pathParts[2]) {
               case "annotations":
                  loadAndSendChildren(req, resp, Annotation.class, pathParts[1], "SELECT * FROM `annotations` WHERE `target` = ? AND `target_type` = 'ANNOTATION'", Annotation.class, null);
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
    * Post to the annotation/&lt;annID>/annotations endpoint to create a new annotation.
    *
    * @param req servlet request
    * @param resp servlet response
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String[] pathParts = getPathParts(req);
      if (pathParts.length == 3) {
         switch (pathParts[2]) {
            case "annotations":
               postAnnotation(req, resp, Annotation.class);
               break;
            default:
               resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
               break;
         }
      }
   }

   
   /**
    * Handles the HTTP <code>PUT</code> method to update the given annotation.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      int uID = getUserID(req, resp);
      if (uID > 0) {
         String[] pathParts = getPathParts(req);
         try (Connection conn = getDBConnection()) {
            Annotation ann = new Annotation(Integer.parseInt(pathParts[1]));
            ann.load(conn, false);

            if (pathParts.length == 2) {
               // Just /annotation/*annID*
               doPutAnnotation(req, conn, uID, ann);
               resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
               // Expecting /annotation/*annID*/approval or /annotation/*annID*/annotations.
               boolean replacing = !getBooleanParameter(req, "merge", true);
               switch (pathParts[2]) {
                  case "approval":
                     doPutApproval(conn, uID, ann);
                     resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                     break;
                  case "annotations":
                     doPutSubAnnotations(req, conn, uID, ann, replacing);
                     resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                     break;
                  default:
                     resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
                     break;
               }
            }
         } catch (SQLException | ArrayIndexOutOfBoundsException | NumberFormatException | ReflectiveOperationException | PermissionException ex) {
            reportInternalError(resp, ex);
         }
      }
   }

   private void doPutAnnotation(HttpServletRequest req, Connection conn, int uID, Annotation ann) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Role r = ann.checkPermission(conn, uID, Role.CONTRIBUTOR);
      if (r == Role.CONTRIBUTOR) {
         // Check to see if we're a contributor trying to modify somebody else's annotation.
         new OwnAnnotationFilter(uID).canModify(ann);
      }
      conn.setAutoCommit(false);

      ObjectMapper mapper = getObjectMapper();
      Map<String, Object> mods = (Map<String, Object>)mapper.readValue(req.getInputStream(), new TypeReference<Map<String, Object>>() {});

      // Slide our modified and approved fields into the modifications.
      mods.put("modifiedBy", uID);
      mods.put("approvedBy", r.ordinal() >= Role.EDITOR.ordinal() ? uID : 0);
      mods.put("modification", new Date());
      
      ann.modify(conn, mods);
      Activity.record(conn, uID, ann, Operation.UPDATE, mapper.writeValueAsString(mods));
      conn.commit();
   }

   private void doPutSubAnnotations(HttpServletRequest req, Connection conn, int uID, Annotation ann, boolean replacing) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Role r = ann.checkPermission(conn, uID, Role.CONTRIBUTOR);
      ObjectMapper mapper = getObjectMapper();
      List<Annotation> newAnns = mapper.readValue(req.getInputStream(), new TypeReference<List<Annotation>>() {});
      for (Annotation ann2: newAnns) {
         ann2.setModifiedBy(uID, r);
         ann2.setTarget(ann);
      }
      List<Annotation> oldAnns = ann.loadChildren(conn, Annotation.SELECT_SUB_ANNOTATIONS, Annotation.class, false);

      conn.setAutoCommit(false);
      ann.mergeChildren(conn, oldAnns, newAnns, Annotation.getMergingComparator(), r == Role.CONTRIBUTOR ? new OwnAnnotationFilter(uID) : null, replacing);
      Activity.record(conn, uID, ann, Operation.UPDATE, mapper.writeValueAsString(newAnns));
      conn.commit();
   }


   /**
    * Mark the given Annotation as being approved by the current User.
    *
    * @param conn connection to SQL database
    * @param uID EDITOR-level User granting the approval
    * @param ann Annotation being approved
    */
   private void doPutApproval(Connection conn, int uID, Annotation ann) throws SQLException, PermissionException {
      ann.checkPermission(conn, uID, Role.EDITOR);

      conn.setAutoCommit(false);
      try (PreparedStatement stmt = conn.prepareStatement("UPDATE annotations SET approved_by = ? " +
              "WHERE id = ?")) {
         stmt.setInt(1, uID);
         stmt.setInt(2, ann.getID());
      }
      Activity.record(conn, uID, ann, Operation.UPDATE, String.format("Approved [%d]", ann.getID()));
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
      deleteEntity(req, resp, Annotation.class);
   }

   /**
    * Returns a short description of the servlet.
    *
    * @return a String containing servlet description
    */
   @Override
   public String getServletInfo() {
      return "Tradamus Individual Annotation Servlet";
   }
}
