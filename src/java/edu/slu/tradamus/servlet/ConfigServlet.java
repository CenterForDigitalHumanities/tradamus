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
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;
import static edu.slu.tradamus.util.ServletUtils.getDBConnection;

 
/**
 * Servlet for getting and setting Tradamus configuration information.
 * @author tarkvara
 */
public class ConfigServlet extends HttpServlet {

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
      resp.setContentType("application/json; charset=UTF-8");

      Attributes attrs = getManifestAttributes();
      Map<String, Object> config = new LinkedHashMap<>();
      config.put("version", attrs.getValue("Implementation-Version"));
      config.put("revision", attrs.getValue("Bundle-Revision"));

      String dbVersion = "unavailable";
      try (Connection conn = getDBConnection()) {
         try (PreparedStatement stmt = conn.prepareStatement("SELECT value FROM config WHERE setting = 'dbVersion'")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
               dbVersion = rs.getString(1);
            }
            if (Integer.parseInt(dbVersion) < DB_VERSION) {
               throw new ServletException(String.format("Expected database version %d, but found %s.  Contact your system administrator", DB_VERSION, dbVersion));
            }
         }
      } catch (SQLException ex) {
         LOG.log(Level.WARNING, "Should never happen, since our error will be reported by saying the dbVersion is unavailable.", ex);
      }
      config.put("dbVersion", dbVersion);
      getObjectMapper().writeValue(resp.getOutputStream(), config);
   }

   /**
    * Returns a short description of the servlet.
    *
    * @return a String containing servlet description
    */
   @Override
   public String getServletInfo() {
      return "Tradamus configuration servlet";
   }
   
   private Attributes getManifestAttributes() throws IOException {
      ServletContext context = getServletContext();
      try (InputStream manifestStream = context.getResourceAsStream("/META-INF/MANIFEST.MF")) {
         Manifest manifest = new Manifest(manifestStream);
         return manifest.getMainAttributes();
      }
   }

   /** Compatible database version. */
   public final int DB_VERSION = 14;

   private static final Logger LOG = Logger.getLogger(ConfigServlet.class.getName());
}
