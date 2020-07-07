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
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.annotation.OwnAnnotationFilter;
import edu.slu.tradamus.edition.Outline;
import edu.slu.tradamus.log.Activity;
import edu.slu.tradamus.log.Operation;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.*;

/**
 * Servlet which provides details of a single Outline.
 *
 * @author tarkvara
 */
public class OutlineServlet extends HttpServlet {

   /**
    * Delete the specified Outline.
    * @param req
    * @param resp
    * @throws ServletException
    * @throws IOException 
    */
   @Override
   protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      deleteEntity(req, resp, Outline.class);
   }

   
   /**
    * Handles the HTTP <code>GET</code> method, returning details of the specified Outline.
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
         // /outline/<outlID> to get the Outline itself.
         loadAndSendEntity(req, resp, Outline.class, null);
      } else {
         try {
            // Expecting /outline/<outlID>/annotations
            switch (pathParts[2]) {
               case "annotations":
                  loadAndSendChildren(req, resp, Outline.class, pathParts[1], Outline.SELECT_ANNOTATIONS, Annotation.class, null);
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
    * POST to the outline's annotations endpoint to create a new annotation targetted at this outline.
    * @param req servlet request
    * @param resp servlet response
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String[] pathParts = getPathParts(req);
      try {
         switch (pathParts[2]) {
            case "annotations":
               postAnnotation(req, resp, Outline.class);
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
    * PUT to /outline/outlID to update the Outline itself, or to /outline/outlID/approval to approve
    * Decisions which have been made.
    */
   @Override
   protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      int uID = getUserID(req, resp);
      if (uID > 0) {
         String[] pathParts = getPathParts(req);
         try (Connection conn = getDBConnection()) {
            Outline oldOutl = new Outline(Integer.parseInt(pathParts[1]));
            oldOutl.load(conn, false);

            if (pathParts.length == 2) {
               // /outline/<outlID> to modify the outline itself.
               doPutOutline(req, conn, uID, oldOutl);
               resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
               // Expecting /outline/*outlID*/annotations or /outline/*outlID*/approval.
               boolean replacing = !getBooleanParameter(req, "merge", true);
               switch (pathParts[2]) {
                  case "annotations":
                     doPutAnnotations(req, conn, uID, oldOutl, replacing);
                     resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                     break;
                  case "approval":
                     putApproval(req, conn, uID, oldOutl, "UPDATE annotations LEFT JOIN parallels ON target = parallels.id " +
                             "SET approved_by = ? WHERE target_type = 'PARALLEL' AND outline = ?");
                     resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                     break;
                  default:
                     resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
                     break;
               }
            }
         } catch (SQLException | ArrayIndexOutOfBoundsException | PermissionException | IllegalArgumentException | ReflectiveOperationException ex) {
            reportInternalError(resp, ex);
         }
      }
   }

   private void doPutOutline(HttpServletRequest req, Connection conn, int uID, Outline outl) throws IOException, ReflectiveOperationException, SQLException, PermissionException {
      Role r = outl.checkPermission(conn, uID, Role.CONTRIBUTOR);
      conn.setAutoCommit(false);

      ObjectMapper mapper = getObjectMapper();
      Map<String, Object> mods = (Map<String, Object>)mapper.readValue(req.getInputStream(), new TypeReference<Map<String, Object>>() {});

      // Slide our modified and approved fields into the modifications.
      mods.put("modifiedBy", uID);
      mods.put("role", r);
      
      outl.modify(conn, mods);
      Activity.record(conn, uID, outl, Operation.UPDATE, mapper.writeValueAsString(mods));
      conn.commit();
   }

   private void doPutAnnotations(HttpServletRequest req, Connection conn, int uID, Outline outl, boolean replacing) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Role r = outl.checkPermission(conn, uID, Role.CONTRIBUTOR);
      ObjectMapper mapper = getObjectMapper();
      List<Annotation> newAnns = mapper.readValue(req.getInputStream(), new TypeReference<List<Annotation>>() {});
      for (Annotation ann: newAnns) {
         ann.setModifiedBy(uID, r);
         ann.setTarget(outl);
      }
      List<Annotation> oldAnns = outl.loadChildren(conn, Outline.SELECT_ANNOTATIONS, Annotation.class, false);

      conn.setAutoCommit(false);
      outl.mergeChildren(conn, oldAnns, newAnns, Annotation.getMergingComparator(), new OwnAnnotationFilter(uID), replacing);
      Activity.record(conn, uID, outl, Operation.UPDATE, mapper.writeValueAsString(newAnns));
      conn.commit();
   }


   /**
    * Returns a short description of the servlet.
    *
    * @return a String containing servlet description
    */
   @Override
   public String getServletInfo() {
      return "Tradamus Outline Details Servlet";
   }
}
