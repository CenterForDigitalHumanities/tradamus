/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.slu.tradamus.util;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static edu.slu.tradamus.util.ServletUtils.getFullURL;
import static edu.slu.tradamus.util.ServletUtils.getServerURL;

/**
 * Class which represents the deliverable for a long-running process, such as collation or PDF generation.
 *
 * @author tarkvara
 */
public abstract class DeferredDeliverable implements Runnable {

   /** Full URL, including parameter string. */
   protected final String fullURL;

   /** URL of server. */
   protected final URL serverURL;
   
   /** ID of deliverable for this request. */
   protected int deliverableID;

   /**
    * Construct a <code>DeferredDeliverable</code> for this servlet request
    * @param req servlet request
    */
   public DeferredDeliverable(HttpServletRequest req) {
      fullURL = getFullURL(req);
      serverURL = getServerURL(req);
   }

   /**
    * Fire off the request for the long-running process.  The method returns immediately with a redirect indicating where
    * the results will be found.
    * @param conn connection to database
    * @param resp servlet response
    * @param msgID message to indicate that process has been launched
    */
   public void deferredRequest(Connection conn, HttpServletResponse resp, MessageID msgID) throws SQLException, IOException, ServletException {
      try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO `deliverables` (`url`) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
         stmt.setString(1, fullURL);
         stmt.executeUpdate();
         ResultSet rs = stmt.getGeneratedKeys();
         if (rs.next()) {
            deliverableID = rs.getInt(1);
            
            // Start the request.
            new Thread(this).start();

            // Indicate where the results are going to be found.
            resp.setContentType("text/html; charset=UTF-8");
            resp.getWriter().append(MessageUtils.format(msgID, fullURL, serverURL, deliverableID));
            resp.addHeader("Location", "/deliverable/" + deliverableID);
            resp.setStatus(HttpServletResponse.SC_CREATED);
         }
      }
   }
}
