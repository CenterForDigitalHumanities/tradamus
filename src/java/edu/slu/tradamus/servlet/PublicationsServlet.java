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
import com.fasterxml.jackson.core.JsonProcessingException;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.log.Activity;
import edu.slu.tradamus.publication.Publication;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.getBaseContentType;
import static edu.slu.tradamus.util.ServletUtils.getDBConnection;
import static edu.slu.tradamus.util.ServletUtils.getUserID;
import static edu.slu.tradamus.util.ServletUtils.reportInternalError;

/**
 * Servlet which is responsible for dealing with the whole collection of Publication objects.
 * @author tarkvara
 */
public class PublicationsServlet extends HttpServlet {

   /**
    * Handles the HTTP <code>GET</code> method to retrieve a list of all publications.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      int uID;
      if ("true".equals(req.getParameter("public"))) {
         uID = 0;
      } else {
         uID = getUserID(req, resp);
      }
      if (uID >= 0) {
         resp.setContentType("application/json; charset=UTF8");
         try (Connection conn = getDBConnection()) {
            List<Map<String, Object>> values = Publication.listWhere(conn, Publication.SELECT_VIEWABLE, uID);
            getObjectMapper().writeValue(resp.getOutputStream(), values);
         } catch (SQLException ex) {
            reportInternalError(resp, ex);
         }
      }
   }

   /**
    * Handles the HTTP <code>POST</code> method to create a new publication.
    *
    * @param request servlet request
    * @param response servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      int uID = getUserID(req, resp);
      if (uID > 0) {
         String contentType = getBaseContentType(req);
         if (contentType.equals("application/json")) {
            try (Connection conn = getDBConnection()) {
               Publication publ = getObjectMapper().readValue(req.getInputStream(), Publication.class);
               if (publ.getType() == null) {
                  resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Required \"type\" field is missing.");
               } else {
                  publ.setCreator(uID);

                  conn.setAutoCommit(false);
                  publ.insert(conn);
                  Activity.record(conn, uID, publ, (Entity)null, publ.getTitle());
                  conn.commit();
                  resp.setHeader("Location", "/publication/" + publ.getID());
                  resp.setStatus(HttpServletResponse.SC_CREATED);
               }
            } catch (SQLException | JsonProcessingException ex) {
               reportInternalError(resp, ex);
            }
         } else {
            resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getServletInfo() {
      return "Tradamus Publication Management Servlet";
   }
}
