/*
 * Copyright 2015 Saint Louis University. Licensed under the
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.tool.xml.XMLWorkerHelper;
import edu.slu.tradamus.util.DeferredDeliverable;
import edu.slu.tradamus.util.MessageID;
import static edu.slu.tradamus.util.ServletUtils.getDBConnection;
import static edu.slu.tradamus.util.ServletUtils.readInputFully;
import static edu.slu.tradamus.util.ServletUtils.reportInternalError;

/**
 * Servlet for generating PDF output from XHTML.
 *
 * @author tarkvara
 */
public class PDFsServlet extends HttpServlet {


   /**
    * Generate a PDF file based on XHTML request body.
    *
    * @param req servlet request
    * @param resp servlet response
    */
   @Override
   protected void doPost(final HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      final byte[] reqBytes = readInputFully(req);

      try (Connection conn = getDBConnection()) {
         new DeferredDeliverable(req) {
            
            @Override
            public void run() {
               try (Connection conn = getDBConnection()) {
                  Document document = new Document();
                  ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
                  PdfWriter writer = PdfWriter.getInstance(document, pdfBytes);
                  document.open();
                  XMLWorkerHelper.getInstance().parseXHtml(writer, document, new ByteArrayInputStream(reqBytes));
                  document.close();
                  conn.setAutoCommit(false);
                  try (PreparedStatement stmt = conn.prepareStatement("UPDATE deliverables SET body = ?, content_type = 'application/pdf' WHERE id = ?")) {
                     stmt.setBinaryStream(1, new ByteArrayInputStream(pdfBytes.toByteArray()));
                     stmt.setInt(2, deliverableID);
                     stmt.executeUpdate();
                  }
                  conn.commit();
               } catch (SQLException | ServletException | IOException | DocumentException ex) {
                  LOG.log(Level.SEVERE, "Error generating PDF", ex);
               }
            }
         }.deferredRequest(conn, resp, MessageID.DEFERRED_PDF);
      } catch (SQLException ex) {
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
      return "PDF Generation Servlet";
   }
   
   private static final Logger LOG = Logger.getLogger(PDFsServlet.class.getName());
}
