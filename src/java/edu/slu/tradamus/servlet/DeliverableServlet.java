/*
 * Copyright 2015 Saint Louis University. Licensed under the
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.catalina.util.IOTools;
import static edu.slu.tradamus.util.ServletUtils.*;


/**
 * Servlet for returning the results from long-running processes such as collations.
 *
 * @author tarkvara
 */
public class DeliverableServlet extends HttpServlet {

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
         String[] pathParts = getPathParts(req);
         try (Connection conn = getDBConnection()) {
            int id = Integer.parseInt(pathParts[1]);
            try (PreparedStatement stmt = conn.prepareStatement("SELECT body, content_type FROM deliverables WHERE id = ?")) {
               stmt.setInt(1, id);
               ResultSet rs = stmt.executeQuery();
               if (rs.next()) {
                  IOTools.flow(rs.getCharacterStream(1), resp.getWriter());
                  resp.setContentType(rs.getString(2));
                  resp.setStatus(HttpServletResponse.SC_OK);
                  
                  try (PreparedStatement stmt2 = conn.prepareStatement("UPDATE deliverables SET accessed = CURRENT_TIMESTAMP() WHERE id = ?")) {
                     stmt2.setInt(1, id);
                     stmt2.executeUpdate();
                  }
               } else {
                  resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
               }
            }
         } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | SQLException ex) {
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
      return "Tradamus Deliverable Servlet";
   }
   
   private static final Logger LOG = Logger.getLogger(DeliverableServlet.class.getName());
}
