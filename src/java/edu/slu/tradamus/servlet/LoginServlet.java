/*
 * Copyright 2013 Saint Louis University. Licensed under the
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
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.stream.XMLStreamException;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.User;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.*;


/**
 * Servlet which handles logging in to Tradamus.
 *
 * @author tarkvara
 */
@WebServlet(name = "LoginServlet", urlPatterns = {"/login"})
public class LoginServlet extends HttpServlet {
   
   /**
    * Handles the HTTP <code>GET</code> method by checking the JSESSIONID for validity,
    * and returning the current user if appropriate.
    */
   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      int uID = getUserID(req, resp);
      //if (uID > 0) {
      if (uID > -1) { //allows for 0, which is the public user.  this used to be 
         try (Connection conn = getDBConnection()) {
            User u = new User(uID);
            u.load(conn, false);
               
            resp.setContentType("application/json; charset=UTF-8");
            getObjectMapper().writeValue(resp.getOutputStream(), u);
         } catch (SQLException | ReflectiveOperationException ex) {
            reportInternalError(resp, ex);
         }
      }
   }

   /**
    * Handles the HTTP <code>POST</code> method by logging in using the JSON-supplied credentials.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      if (req.getContentLength() > 0) {
         String contentType = getBaseContentType(req);
         if (contentType.equals("application/json")) {
            User u = getObjectMapper().readValue(req.getInputStream(), User.class);

            if (u.getMail() != null && u.hasPassword()) {
               try (Connection conn = getDBConnection()) {
                  switch (u.validate(conn)) {
                     case VALID:
                        u.login(conn);
                        HttpSession sess = req.getSession(true);
                        sess.setAttribute("userID", u.getID());
                        resp.setHeader("Location", "/user/" + u.getID());
                        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        break;
                     case INVALID:
                        resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not logged in");
                        break;
                     case CONFIRMATION_PENDING:
                        resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account awaiting confirmation");
                        break;
                     case DISABLED:
                        resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account disabled");
                        break;
                  }
               } catch (SQLException | ReflectiveOperationException | PermissionException | XMLStreamException ex) {
                  reportInternalError(resp, ex);
               }
            } else {
               resp.sendError(HttpServletResponse.SC_BAD_REQUEST, String.format("Missing %s field in login", u.getMail() == null ? "mail" : "password"));
            }
         } else {
            resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
         }
      } else {
         // Passing null data indicates a logout.
         HttpSession sess = req.getSession(true);
         sess.removeAttribute("userID");
         resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
      }
   }

   /**
    * Returns a short description of the servlet.
    *
    * @return a String containing servlet description
    */
   @Override
   public String getServletInfo() {
      return "Tradamus Login Servlet";
   }
}
