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
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.image.Image;
import edu.slu.tradamus.image.Image.Format;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import static edu.slu.tradamus.util.ServletUtils.getDBConnection;
import static edu.slu.tradamus.util.ServletUtils.getPathParts;
import static edu.slu.tradamus.util.ServletUtils.getProxiedConnection;
import static edu.slu.tradamus.util.ServletUtils.getUserID;
import static edu.slu.tradamus.util.ServletUtils.reportInternalError;

/**
 * Servlet which fetches image data for the client.  Saves the client from having to deal with
 * authentication from T-PEN or other image repositories.
 * @author tarkvara
 */
public class ImageServlet extends HttpServlet {

   /**
    * Handles the HTTP <code>GET</code> method.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      int uID = getUserID(req, resp);
      if (uID > 0) {
         try (Connection conn = getDBConnection()) {
            int id = Integer.parseInt(getPathParts(req)[1]);
            Image img = new Image(id);
            try (PreparedStatement stmt = conn.prepareStatement(String.format("SELECT uri, format FROM images WHERE id = ?"))) {
               stmt.setInt(1, id);
               ResultSet rs = stmt.executeQuery();
               if (rs.next()) {
                  img.checkPermission(conn, uID, Role.VIEWER);
                  String imgURI = rs.getString(1);
                  Format fmt = Format.valueOf(rs.getString(2));
                  try (PreparedStatement stmt2 = conn.prepareStatement("SELECT type FROM repositories " +
                          "WHERE INSTR(?, prefix) > 0")) {
                     stmt2.setString(1, imgURI);
                     rs = stmt2.executeQuery();
                     if (rs.next()) {
                        switch (rs.getString(1)) {
                           case "TPEN":
                              resp.setContentType(fmt.toMimeType());
                              getProxiedConnection(new URL(imgURI), req, resp, null, false);
                              break;
                           default:
                              // NONE or some unknown type.  Just let the browser fetch the URL itself.
                              resp.setHeader("Location", imgURI);
                              resp.setStatus(HttpServletResponse.SC_FOUND);
                        }
                     } else {
                        // No repository matches this image URL.  Just let the browser fetch the URL itself.
                        resp.setHeader("Location", imgURI);
                        resp.setStatus(HttpServletResponse.SC_FOUND);
                     }
                  }
               } else {
                  throw new NoSuchEntityException(img);
               }
            }
         } catch (SQLException | ArrayIndexOutOfBoundsException | NumberFormatException | PermissionException ex) {
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
      return "Tradamus Image Servlet";
   }
}
