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
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.log.Activity;
import edu.slu.tradamus.log.Operation;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import edu.slu.tradamus.user.User;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.LangUtils.buildQuickMap;
import static edu.slu.tradamus.util.ServletUtils.*;


/**
 * Servlet which manages details about a single user.
 *
 * @author tarkvara
 */
public class UserServlet extends HttpServlet {

   /**
    * Handles the HTTP <code>GET</code> method, providing details about the specified user.
    *
    * @param request servlet request
    * @param response servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      int uID = getUserID(req, resp);
      if (uID > 0) {
         String mailParam = req.getParameter("mail");
         if (mailParam != null) {
            try {
               // Looking up a user by email.
               User u = User.findByEmail(mailParam);
               resp.setContentType("application/json; charset=UTF-8");
               resp.setHeader("Location", "/user/" + u.getID());
               getObjectMapper().writeValue(resp.getOutputStream(), u);
            } catch (SQLException ex) {
               reportInternalError(resp, ex);
            }
         } else {
            loadAndSendEntity(req, resp, User.class, null);
         }
      }
   }

   /**
    * Handles the HTTP <code>PUT</code> method, updating details about the specified user.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String resetParam = req.getParameter("reset");
      String resendParam = req.getParameter("resend");
      try (Connection conn = getDBConnection()) {
         if (resetParam != null) {
            User u = User.findByEmail(resetParam);
            String pw = User.randomPassword();
            conn.setAutoCommit(false);
            u.modify(conn, buildQuickMap("password", pw));
            Activity.record(conn, u.getID(), u, Operation.UPDATE, "Password reset");
            conn.commit();
            sendMail(u.getMail(), "Tradamus", String.format("Your Tradamus password has been reset.  Your new temporary password is \"%s\" (no quotes).  Please change it at your soonest convenience.", pw));
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
         } else if (resendParam != null) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT name, pending FROM users WHERE mail = ?")) {
               stmt.setString(1, resendParam);
               ResultSet rs = stmt.executeQuery();
               if (rs.next()) {
                  String name = rs.getString(1);
                  String confirmationKey = rs.getString(2);
                  if (confirmationKey != null) {
                     confirmationKey = URLEncoder.encode(confirmationKey, "UTF-8");
                     sendMail(resendParam, "Tradamus Registration", String.format("You have registered with Tradamus as %s.  To confirm this registration request, please visit <%s://%s:%d/Tradamus/users?mail=%s&confirmation=%s>", name, req.getScheme(), req.getServerName(), req.getServerPort(), resendParam, confirmationKey));
                     resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                  } else {
                     resp.setStatus(HttpServletResponse.SC_GONE);
                  }
               } else {
                  throw new NoSuchEntityException(resendParam);
               }
            }
         } else {
            int uID = getUserID(req, resp);
            if (uID > 0) {
               // Can't use ServerUtils.modifyEntity because that exposes fields in the activity log.
               int id = Integer.parseInt(getPathParts(req)[1]);
               User u = new User(id);
               u.checkPermission(conn, uID, Role.EDITOR);
               conn.setAutoCommit(false);
               ObjectMapper mapper = getObjectMapper();
               Map<String, Object> mods = (Map<String, Object>)mapper.readValue(req.getInputStream(), new TypeReference<Map<String, Object>>() {});
               u.modify(conn, mods);
               if (mods.containsKey("hash")) {
                  mods.remove("hash");
                  mods.put("password", "*changed*");
               }
               Activity.record(conn, uID, u, Operation.UPDATE, mapper.writeValueAsString(mods));
               conn.commit();
            }
         }
      } catch (SQLException | ReflectiveOperationException | PermissionException ex) {
         reportInternalError(resp, ex);
      }
   }

   /**
    * Returns a short description of the servlet.
    *
    * @return a String containing servlet description
    */
   @Override
   public String getServletInfo() {
      return "Tradamus User Details Servlet";
   }
}
