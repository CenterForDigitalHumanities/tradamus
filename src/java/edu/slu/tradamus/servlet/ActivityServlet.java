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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import edu.slu.tradamus.db.Entity;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.*;


/**
 * Servlet which lets users query the activity log.
 *
 * @author tarkvara
 */
public class ActivityServlet extends HttpServlet {

   /**
    * Processes HTTP <code>GET</code> requests, sending back the requested log information.
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
            String user = req.getParameter("user");
            String table = req.getParameter("table");
            String id = req.getParameter("id");
            String limit = req.getParameter("limit");
            StringBuilder stmtBuf = new StringBuilder("SELECT * FROM activities");
            List<String> args = new ArrayList<>();
            if (user != null) {
               stmtBuf.append(" WHERE user = ?");
               args.add(user);
            }
            if (table != null) {
               stmtBuf.append(user == null ? " WHERE " : " AND ");
               if (id != null) {
                  stmtBuf.append(String.format("entity = '%s/%s'", singular(table), id));
               } else {
                  stmtBuf.append(String.format("entity LIKE '%s/%%'", singular(table)));
               }
            }
            stmtBuf.append(" ORDER BY time DESC");
            if (limit != null) {
               stmtBuf.append(" LIMIT ").append(limit);
            }
            List<Map<String, Object>> result = Entity.listWhere(conn, stmtBuf.toString(), (Object[])args.toArray(new String[0]));

            // The caller has no interest in the activity as an entity, we tidy this up to make more sense
            // to the outside world.
            for (Map<String, Object> act: result) {
               act.remove("id");
            }

            resp.setContentType("application/json; charset=UTF-8");
            getObjectMapper().writeValue(resp.getOutputStream(), result);
         } catch (SQLException ex) {
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
      return "Tradamus Activity Servlet";
   }
   
   /**
    * The servlet expects the "table" parameter to contain the name of the table, which is generally the
    * plural of what we actually want to use in the query.
    * @param table
    * @return 
    */
   private static String singular(String table) {
      switch (table) {
         case "canvasses":
            return "canvas";
         case "witnesses":
            return "witness";
         default:
            // Strip off final 's'.
            return table.substring(0, table.length() - 1);
      }
   }
}
