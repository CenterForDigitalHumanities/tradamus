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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.edition.Edition;
import edu.slu.tradamus.log.Activity;
import edu.slu.tradamus.log.Operation;
import edu.slu.tradamus.publication.Publication;
import edu.slu.tradamus.user.Permission;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import edu.slu.tradamus.user.User;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import edu.slu.tradamus.util.MessageID;
import edu.slu.tradamus.util.MessageUtils;
import static edu.slu.tradamus.util.ServletUtils.*;


/**
 * Servlet which manages the creation and maintenance of our user collection.
 *
 * @author tarkvara
 */
public class UsersServlet extends HttpServlet {

   /**
    * Handles the HTTP <code>GET</code> method, which is used to complete the registration of new
    * users.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp)
           throws ServletException, IOException {
      String mail = req.getParameter("mail");
      if (mail != null) {
         String confirmation = req.getParameter("confirmation");
         if (confirmation != null) {
            try {
               try (Connection conn = getDBConnection()) {
                  User u = User.findConfirmation(conn, mail, confirmation);
                  if (u != null) {
                     conn.setAutoCommit(false);                  
                     u.markPending(conn, false);
                     resp.setHeader("Location", "/user/" + u.getID());
                     resp.setStatus(HttpServletResponse.SC_OK);
                     resp.getWriter().append(MessageUtils.format(MessageID.CONFIRMATION_SUCCESSFUL, u.getMail()));
                     Activity.record(conn, u.getID(), u, Operation.UPDATE, "Account confirmed");
                     conn.commit();
                  } else {
                     resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Confirmation code not found");
                  }
               }
            } catch (NoSuchAlgorithmException | UnsupportedEncodingException | SQLException ex) {
               throw new ServletException(ex);
            }
         } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing \"confirmation=\" parameter.");         
         }
      } else {
         resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing \"mail=\" parameter.");
      }
   }

   /**
    * Handles the HTTP <code>POST</code> method, which is used to create new users.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String contentType = getBaseContentType(req);
      if (contentType.equals("application/json")) {
         User u = getObjectMapper().readValue(req.getInputStream(), User.class);

         try {
            try (Connection conn = getDBConnection()) {
               // Create an unconfirmed user.
               conn.setAutoCommit(false);
               
               if (u.getEdition() > 0 || u.getPublication() > 0) {
                  // We're being invited by another user.
                  User inviter = getUser(req, resp);
                  if (inviter != null) {
                     Entity ent = u.getEdition() > 0 ? new Edition(u.getEdition()) : new Publication(u.getPublication());
                     if (u.getName() == null) {
                        // Invited as an anonymous reviewer.
                        Role r = ent.checkPermission(conn, inviter.getID(), Role.REVIEW_EDITOR);
                        if (r == Role.CONTRIBUTOR || r == Role.EDITOR) {
                           // Contributors and plain editors aren't qualified to invite people.
                           throw new PermissionException(ent, Role.REVIEW_EDITOR);
                        }
                        ent.load(conn, false);

                        String realMail = u.getMail();
                        String name = User.randomName();
                        u.setName(name);
                        u.setMail(name);
                        u.setPassword(name);
                        u.insert(conn);

                        // Grant basic contributor permissions to our new user.
                        Permission perm = new Permission(ent, u.getID(), Role.CONTRIBUTOR);
                        perm.insert(conn);

                        sendMail(realMail, "Tradamus Review Invitation", MessageUtils.format(MessageID.REVIEW_INVITATION, inviter.getName(), ((Publication)ent).getTitle(), req.getScheme(), req.getServerName(), req.getServerPort(), ent.getID(), name));
                     } else {
                        // Invited as a full user.
                        ent.checkPermission(conn, inviter.getID(), Role.OWNER);
                        ent.load(conn, false);
                        String pass = User.randomPassword();
                        u.setPassword(pass);
                        u.insert(conn);

                        // Grant basic contributor permissions to our new user.
                        Permission perm = new Permission(ent, u.getID(), Role.CONTRIBUTOR);
                        perm.insert(conn);

                        String confirmationKey = URLEncoder.encode(u.markPending(conn, true), "UTF-8");
                        if (u.getEdition() > 0) {
                           sendMail(u.getMail(), "Tradamus Invitation", MessageUtils.format(MessageID.INVITATION, inviter.getName(), "edition", ((Edition)ent).getTitle(), pass, req.getScheme(), req.getServerName(), req.getServerPort(), u.getMail(), confirmationKey));
                        } else {
                           sendMail(u.getMail(), "Tradamus Invitation", MessageUtils.format(MessageID.INVITATION, inviter.getName(), "publication", ((Publication)ent).getTitle(), pass, req.getScheme(), req.getServerName(), req.getServerPort(), u.getMail(), confirmationKey));
                        }
                     }
                  } else {
                     // Call to getUser will have set status to 401.
                     return;
                  }
               } else {
                  // User has joined Tradamus on their own initiative.
                  u.setLastLogin(new Date());
                  u.insert(conn);
//                  String confirmationKey = URLEncoder.encode(u.markPending(conn, true), "UTF-8");
//                  sendMail(u.getMail(), "Tradamus Registration", String.format("You have registered with Tradamus as %s.  To confirm this registration request, please visit <%s://%s:%d/Tradamus/users?mail=%s&confirmation=%s>", u.getName(), req.getScheme(), req.getServerName(), req.getServerPort(), u.getMail(), confirmationKey));
               }
               Activity.record(conn, u.getID(), u, (Entity)null, u.getName());
               conn.commit();
            }
            resp.setHeader("Location", "/user/" + u.getID());
            resp.setStatus(HttpServletResponse.SC_CREATED);
         } catch (SQLException | ReflectiveOperationException | NoSuchAlgorithmException | UnsupportedEncodingException | PermissionException ex) {
            reportInternalError(resp, ex);
         }
      } else {
         resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getServletInfo() {
      return "Tradamus User Management Servlet";
   }
}
