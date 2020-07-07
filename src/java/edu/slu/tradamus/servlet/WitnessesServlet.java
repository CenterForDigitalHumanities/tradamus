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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static edu.slu.tradamus.util.ServletUtils.reportInternalError;


/**
 * Servlet which manages the import of witnesses.
 *
 * @author tarkvara
 * @deprecated Replaced by POST to /edition/edID/witnesses.
 */
public class WitnessesServlet extends HttpServlet {

   /**
    * Handles the HTTP <code>POST</code> method.
    *
    * @param req servlet request
    * @param resp servlet response
    * @throws ServletException if a servlet-specific error occurs
    * @throws IOException if an I/O error occurs
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String edParam = req.getParameter("edition");
      if (edParam != null) {
         try {
            EditionServlet.doPostWitness(req, resp, Integer.parseInt(edParam));
         } catch (NumberFormatException ex) {
            reportInternalError(resp, ex);
         }
      } else {
         // Required edition parameter is missing.
         resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing \"edition\" parameter.");
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getServletInfo() {
      return "Tradamus Witness Management Servlet";
   }
}
