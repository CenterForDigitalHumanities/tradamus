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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.slu.tradamus.db.NamedLock;
import edu.slu.tradamus.log.Activity;
import edu.slu.tradamus.log.Operation;
import edu.slu.tradamus.publication.Section;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.*;

/**
 * Servlet which allows for storing and retrieving details of a single section.
 *
 * @author tarkvara
 */
public class SectionServlet extends HttpServlet {

   @Override
   protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      deleteEntity(req, resp, Section.class);
   }

   /**
    * Handles the HTTP <code>GET</code> method, providing details about the specified section.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      loadAndSendEntity(req, resp, Section.class, null);
   }

   @Override
   protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      LOG.log(Level.INFO, "Putting section");
      int uID = getUserID(req, resp);
      if (uID > 0) {
         String[] pathParts = getPathParts(req);
         try (Connection conn = getDBConnection()) {
            Section oldSect = new Section(Integer.parseInt(pathParts[1]));
            oldSect.load(conn, false);
            oldSect.checkPermission(conn, uID, Role.CONTRIBUTOR);
            ObjectMapper mapper = getObjectMapper();
            Section newSect = mapper.readValue(req.getInputStream(), Section.class);
            newSect.fixRuleTypes();

            try (NamedLock lock = new NamedLock(conn, PUT_SECTION_LOCK)) {
               oldSect.merge(conn, newSect);
               Activity.record(conn, uID, oldSect, Operation.UPDATE, mapper.writeValueAsString(newSect));
               resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
         } catch (SQLException | ArrayIndexOutOfBoundsException | PermissionException | IllegalArgumentException | ReflectiveOperationException ex) {
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
      return "Tradamus Publication Section Servlet";
   }
   
   private static final String PUT_SECTION_LOCK = "tradamus.put_section";
   private static final Logger LOG = Logger.getLogger(SectionServlet.class.getName());
}
