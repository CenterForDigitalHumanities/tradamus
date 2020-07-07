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
 * Servlet which proxies requests over to SourceForge to get project data.
 *
 * @author cubap
 */
public class SFProxyServlet extends HttpServlet {

   /**
    * Processes requests for HTTP <code>GET</code> methods.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   protected void processRequest(HttpServletRequest req, HttpServletResponse resp, boolean output) throws ServletException, IOException {
         HttpSession sess = req.getSession();
         String context = req.getPathInfo();

         String proxiedPath = "http://sourceforge.net/rest/p/tradamus" + context;
         URL proxiedURL = new URL(proxiedPath);
         getProxiedConnection(proxiedURL, req, resp, null, output);
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
            resp.setContentType("application/json; charset=UTF-8");
            Map<String, Object> config = new HashMap<>();
            getObjectMapper().writeValue(resp.getOutputStream(), config);
      } else {
         processRequest(req, resp, false);
      }
   }

   /**
    * Returns a short description of the servlet.
    *
    * @return a String containing servlet description
    */
   @Override
   public String getServletInfo() {
      return "Tradamus SourceForge Proxy Servlet";
   }
   }
