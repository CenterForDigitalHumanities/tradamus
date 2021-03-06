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
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import edu.slu.tradamus.user.User;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.getProxiedConnection;
import static edu.slu.tradamus.util.ServletUtils.getUser;
import static edu.slu.tradamus.util.ServletUtils.getUserID;


/**
 * Servlet which proxies requests over to a specific T-PEN instance.
 *
 * @author tarkvara
 */
public class TPenProxyServlet extends HttpServlet {

   /**
    * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   protected void processRequest(HttpServletRequest req, HttpServletResponse resp, boolean output) throws ServletException, IOException {
      User u = getUser(req, resp);
      if (u != null) {
         HttpSession sess = req.getSession();
         List<String> cookies = (List<String>)sess.getAttribute("TPenCookies");

         String context = req.getPathInfo();

         String proxiedPath = getTPenAddress() + context;
         if (req.getQueryString() != null) {
            proxiedPath += "?" + req.getQueryString();
         }
         
         URL proxiedURL = new URL(proxiedPath);
         getProxiedConnection(proxiedURL, req, resp, cookies, output);
      }
   }

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
      if (req.getParameter("config") != null) {
         int uID = getUserID(req, resp);
         if (uID > 0) {
            resp.setContentType("application/json; charset=UTF-8");

            Map<String, Object> config = new HashMap<>();
            config.put("server", getTPenAddress());

            getObjectMapper().writeValue(resp.getOutputStream(), config);
         }
      } else {
         processRequest(req, resp, false);
      }
   }

   /**
    * Handles the HTTP <code>POST</code> method.
    *
    * @param request servlet request
    * @param response servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doPost(HttpServletRequest request, HttpServletResponse response)
           throws ServletException, IOException {
      processRequest(request, response, true);
   }

   /**
    * Handles the HTTP <code>POST</code> method.
    *
    * @param request servlet request
    * @param response servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      processRequest(request, response, true);
   }

   /**
    * Returns a short description of the servlet.
    *
    * @return a String containing servlet description
    */
   @Override
   public String getServletInfo() {
      return "Tradamus T-PEN Proxy Servlet";
   }
   
   private String getTPenAddress() throws ServletException {
      try {
         InitialContext initCtx = new InitialContext();            
         return (String)initCtx.lookup("java:comp/env/tpen");
      } catch (NamingException ex) {
         throw new ServletException(ex);
      }
   }
}
