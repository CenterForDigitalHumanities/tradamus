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
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import edu.slu.tradamus.annotation.AnnotationSnarfingDeserializerModifier;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.edition.Edition;
import edu.slu.tradamus.log.Activity;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.*;


/**
 * Servlet which manages the import of editions.
 *
 * @author tarkvara
 */
public class EditionsServlet extends HttpServlet {

   /**
    * Handles the HTTP <code>GET</code> method to retrieve a list of all editions.
    *
    * @param req servlet request
    * @param response servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      int uID = getUserID(req, resp);
         resp.setContentType("application/json; charset=UTF8");
         List<Map<String, Object>> values;
         try (Connection conn = getDBConnection()) {
             if(uID > -1){ //allows for 0, which is the public user.
                 values = Edition.listWhere(conn, Edition.SELECT_VIEWABLE, uID);
             }
             else{
                 values = Edition.listWhere(conn, Edition.SELECT_VIEWABLE_NO_USER);
             }
            
            getObjectMapper().writeValue(resp.getOutputStream(), values);
         } catch (SQLException ex) {
            reportInternalError(resp, ex);
         }
   }

   /**
    * Handles the HTTP <code>POST</code> method to create a new edition.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if something other than an I/O error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      int uID = getUserID(req, resp);
      if (uID > 0) {
         String contentType = getBaseContentType(req);
         if (contentType.equals("application/json")) {
            try (Connection conn = getDBConnection()) {
               ObjectMapper mapper = getObjectMapper();
               SimpleModule mod = new SimpleModule();
               AnnotationSnarfingDeserializerModifier modifier = new AnnotationSnarfingDeserializerModifier();
               mod.setDeserializerModifier(modifier);
               mapper.registerModule(mod);

               Edition ed = mapper.readValue(req.getInputStream(), Edition.class);
               ed.setCreator(uID);
               ed.fixTargets(ed.getPageWitnesses());

               conn.setAutoCommit(false);
               ed.insert(conn);
               Activity.record(conn, uID, ed, (Entity)null, ed.getTitle());
               conn.commit();
               resp.setHeader("Location", "/edition/" + ed.getID());
               resp.setStatus(HttpServletResponse.SC_CREATED);
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
      return "Tradamus Edition Management Servlet";
   }
}
