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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.module.SimpleModule;
import edu.slu.tradamus.db.EntityIDSerializer;
import edu.slu.tradamus.image.Canvas;
import edu.slu.tradamus.image.Manifest;
import edu.slu.tradamus.user.PermissionException;
import static edu.slu.tradamus.util.ServletUtils.*;


/**
 * Servlet which provides access to the contents of a single Manifest.
 *
 * @author tarkvara
 */
public class ManifestServlet extends HttpServlet {

   /**
    * Handles the HTTP <code>GET</code> method, returning details of the specified manifest.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      int uID = getUserID(req, resp);
         String[] pathParts = getPathParts(req);
         if (pathParts.length == 2) {
            SimpleModule mod = new SimpleModule();
            mod.addSerializer(Canvas.class, new EntityIDSerializer());
            loadAndSendEntity(req, resp, Manifest.class, mod);
         } else {
            try {
               // Expecting /manifest/<manID>/pages.
               // returned as part of the witness details.
               switch (pathParts[2]) {
                  case "canvasses":
                     loadAndSendChildren(req, resp, Manifest.class, pathParts[1], Manifest.SELECT_CANVASSES_WITH_IMAGES, Canvas.class, null);
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
    * Implements the <code>POST</code> method, used to add new annotations.
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException
    * @throws IOException 
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String[] pathParts = getPathParts(req);
      if (pathParts.length == 3) {
         switch (pathParts[2]) {
            case "annotations":
               postAnnotation(req, resp, Manifest.class);
               break;
            default:
               resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unknown path element: %s", pathParts[2]));
               break;
         }
      }
   }

   /**
    * Implements the <code>PUT</code> method, used to modify permissions.
    * @param req servlet request
    * @param resp servlet response
    */
   @Override
   protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      if (getBaseContentType(req).equals("application/json")) {
         String[] pathParts = getPathParts(req);
         if (pathParts.length == 2) {
            // /manifest/<manID> to modify the Manifest itself.
            modifyEntity(req, resp, Manifest.class);
         } else {
            int uID = getUserID(req, resp);
            if (uID > 0) {
               try (Connection conn = getDBConnection()) {
                  boolean replacing = !getBooleanParameter(req, "merge", true);
                  // Expecting /transcription/<transcrID>/permissions
                  Manifest man = new Manifest(Integer.parseInt(pathParts[1]));
                  switch (pathParts[2]) {
                     case "permissions":
                        putPermissions(req, conn, uID, man, Manifest.SELECT_PERMISSIONS_WITH_USERS, replacing);
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
      } else {
         resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Expecting application/json");
      }
   }

   /**
    * Returns a short description of the servlet.
    *
    * @return a String containing servlet description
    */
   @Override
   public String getServletInfo() {
      return "Tradamus Manifest Details Servlet";
   }
}
