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
package edu.slu.tradamus.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Session;
import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;
import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.log.Activity;
import edu.slu.tradamus.log.Operation;
import edu.slu.tradamus.user.Permission;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;
import edu.slu.tradamus.user.User;
import static edu.slu.tradamus.util.JsonUtils.getObjectMapper;


/**
 * Various utility methods used by our servlet classes.
 *
 * @author tarkvara
 */
public class ServletUtils {
   /**
    * Get the base content type without any trailing optional elements like charset.
    */
   public static String getBaseContentType(String contentType) {
      int semiPos = contentType.indexOf(';');
      if (semiPos > 0) {
         contentType = contentType.substring(0, semiPos);
      }
      return contentType;
   }

   /**
    * Get the base content type without any trailing optional elements like charset.
    */
   public static String getBaseContentType(HttpServletRequest req) {
      return getBaseContentType(req.getContentType());
   }

   /**
    * Get the charset if it's included as part of the content type.
    */
   public static String getCharset(String contentType) {
      String enc = "UTF-8";
      Matcher m = CHARSET_PATTERN.matcher(contentType);
      if (m.find()) {
         enc = m.group(1).trim().toUpperCase();
      }
      return enc;
   }

   /**
    * Get the full URL for this request (including parameters).
    * @param req servlet request
    * @return the full URL
    */
   public static String getFullURL(HttpServletRequest req) {
      StringBuffer url = req.getRequestURL();
      String query = req.getQueryString();
      if (query == null) {
         return url.toString();
      } else {
         return url.append('?').append(query).toString();
      }
   }

   /**
    * Get the input stream for a servlet, allowing for the possibility that we might have a
    * <code>src=</code> parameter.
    * @param req servlet request
    * @param cont buffer to receive content type
    * @return input stream for the request or its associated <code>src</code> param
    */
   public static InputStream getInputStream(HttpServletRequest req, String[] cont) throws IOException {
      String srcParam = req.getParameter("src");

      if (srcParam != null) {
         URL srcURL = new URL(srcParam);
         HttpURLConnection srcConn = (HttpURLConnection)srcURL.openConnection();
         srcConn.connect();
         if (cont != null) {
            cont[0] = getBaseContentType(srcConn.getContentType());
         }
         return srcConn.getInputStream();
      } else {
         if (cont != null) {
            cont[0] = getBaseContentType(req.getContentType());
         }
         return req.getInputStream();
      }
   }

   public static byte[] readInputFully(HttpServletRequest req) throws IOException {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      IOTools.flow(getInputStream(req, null), output);
      return output.toByteArray();
   }

   /**
    * Try to parse the parameter as a boolean value.  Interpret "0" or "false" as <code>false</code> and
    * "1" or "true" as <code>true</code>.  Throw <code>IllegalArgumentException</code> if the parameter
    * doesn't look like a boolean value.
    * @param req servlet request
    * @param param name of parameter
    * @param dflt default value if parameter is omitted
    * @return <code>tr
    */
   public static boolean getBooleanParameter(HttpServletRequest req, String param, boolean dflt) {
      String val = req.getParameter(param);
      if (val != null) {
         if (val.equals("0") || val.equalsIgnoreCase("false")) {
            return false;
         }
         if (val.equals("1") || val.equalsIgnoreCase("true")) {
            return true;
         }
         throw new IllegalArgumentException(String.format("Unable to parse \"%s\" as boolean", val));
      }
      return dflt;
   }

   /**
    * Parse the servlet request path, returning a meaningful error.  We do it this way because if the path
    * is too short, we get a NullPointerException which gets sent back to the client as an unhelpful 500.
    */
   public static String[] getPathParts(HttpServletRequest req) {
      String pathInfo = req.getPathInfo();
      if (pathInfo == null) {
         return new String[0];
      }
      return pathInfo.split("/");
   }

   /**
    * I thought there was a method for this, but I can't find it.  Quicker just to write our own.
    * @param req servlet request
    * @return URL representing the server for that request
    */
   public static URL getServerURL(HttpServletRequest req) {
      URL result = null;
      LOG.log(Level.INFO, "pathInfo={0}, requestURI={1}, contextPath={2}", new Object[] { req.getPathInfo(), req.getRequestURI(), req.getContextPath() });
      try {
         int port = req.getServerPort();
         if (req.getScheme().equals("http") && port == 80) {
             port = -1;
         } else if (req.getScheme().equals("https") && port == 443) {
             port = -1;
         }
         result = new URL(req.getScheme(), req.getServerName(), port, req.getContextPath());
      } catch (MalformedURLException ignored) {
         LOG.log(Level.WARNING, "MalformedURLException should never happen here.");
      }
      return result;
   }

   /**
    * Get the userID associated with the give servlet request.  If it can't be found, return -1
    * and set the status code to 401.
    * @param req the servlet request
    * @param resp the servlet response, so we can set the status
    * @return the userID associated with this session
    */
   public static int getUserID(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      HttpSession sess = req.getSession();
      if (sess != null) {
         Integer userID = (Integer)sess.getAttribute("userID");
         if (userID != null) {
            return userID;
         }
         else{
             return 0; // 0 is the public user, they will have approriate permissions for our view situation.
         }
      }
      else{
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return -2;
      }
      
      
   }
   
   /**
    * Get the user associated with the give servlet request.  If it can't be found, return <code>null</code>
    * and set the status code to 401.
    * @param req the servlet request
    * @param resp the servlet response, so we can set the status
    * @return the user associated with this session
    */
   public static User getUser(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException  {
      HttpSession sess = req.getSession();
      if (sess != null) {
         Integer uID = (Integer)sess.getAttribute("userID");
         if (uID != null) {
            try {
               User u = new User(uID);
               u.load(getDBConnection(), false);
               return u;
            } catch (SQLException | ReflectiveOperationException ex) {
               reportInternalError(resp, ex);
            }
         }
      }
      resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return null;
   }
   
   /**
    * Get a MySQL connection, as configured in web.xml.
    */
   public static Connection getDBConnection() throws ServletException, SQLException {
      try {
         InitialContext initCtx = new InitialContext();
         DataSource dataSource = (DataSource)initCtx.lookup("java:comp/env/jdbc/Tradamus");
         return dataSource.getConnection();
      } catch (NamingException ex) {
         throw new ServletException(ex);
      }
   }

   /**
    * Send an email message to the given user.
    * @param to ID of destination user
    * @param subj subject of email
    * @param body content of email
    */
   public static void sendMail(Connection conn, int uID, String subj, String body) throws SQLException, ServletException {
      try (PreparedStatement stmt = conn.prepareStatement("SELECT `mail` FROM `users` WHERE `id` = ?")) {
         stmt.setInt(1, uID);
         ResultSet rs = stmt.executeQuery();
         if (rs.next()) {
            sendMail(rs.getString(1), subj, body);
         }
      }
   }

   /**
    * Send an email message to the given email address.
    * @param to destination email address
    * @param subj subject of email
    * @param body content of email
    */
   public static void sendMail(String to, String subj, String body) throws ServletException {
      try {    
         InitialContext initCtx = new InitialContext();
         Session session = (Session)initCtx.lookup("java:comp/env/mail/Tradamus");
         Message message = new MimeMessage(session);
         InternetAddress toAddresses[] = new InternetAddress[] { new InternetAddress(to) };
         message.setRecipients(Message.RecipientType.TO, toAddresses);
         message.setSubject(subj);
         message.setContent(body, body.startsWith("<!DOCTYPE html>") ? HTML_CONTENT_TYPE : TEXT_CONTENT_TYPE);
         Transport.send(message);
      } catch (NamingException | javax.mail.MessagingException ex) {
         throw new ServletException(ex);
      }
   }
   
   public static void getProxiedConnection(URL proxiedURL, HttpServletRequest req, HttpServletResponse resp, List<String> cookies, boolean output) throws IOException {
      HttpURLConnection proxiedConn = (HttpURLConnection)proxiedURL.openConnection();
      proxiedConn.setRequestMethod(req.getMethod());
      if (cookies != null) {
         for (String cookie: cookies) {
            proxiedConn.addRequestProperty(COOKIE_HEADER, cookie);
         }
      }
      if (output) {
         proxiedConn.setRequestProperty(CONTENT_TYPE_HEADER, req.getContentType());
         proxiedConn.setDoOutput(true);
         IOTools.flow(req.getInputStream(), proxiedConn.getOutputStream());
      }
      
      // Copy any interesting headers.
      long modSince = req.getDateHeader(IF_MODIFIED_SINCE_HEADER);
      if (modSince > 0) {
         proxiedConn.addRequestProperty(IF_MODIFIED_SINCE_HEADER, RFC1123_FORMAT.format(new Date(modSince)));
      }
      proxiedConn.connect();

      // Copy the interesting parts of the proxied response.
      copyProxyStatus(proxiedConn, resp);
      if (resp.getStatus() >= 400) {
         IOTools.flow(proxiedConn.getErrorStream(), resp.getOutputStream());
      } else {
         IOTools.flow(proxiedConn.getInputStream(), resp.getOutputStream());
      }

      // Extract any new cookies.
      String headerName;
      cookies = new ArrayList<>();
      for (int i = 1; (headerName = proxiedConn.getHeaderFieldKey(i)) !=null; i++) {
         if (headerName.equals(SET_COOKIE_HEADER)) {
            cookies.add(proxiedConn.getHeaderField(i));
         }
      }
   }
 
   private static void copyProxyStatus(HttpURLConnection conn, HttpServletResponse resp) throws IOException {
      int code = conn.getResponseCode();
      if (code < 400) {
         resp.setStatus(code);
      } else {
         resp.sendError(code, conn.getResponseMessage());
      }
   }

   /**
    * Parse the request and attach a single POSTed annotation to the given entity.
    * @param req
    * @param resp
    * @param entityClazz
    * @throws IOException
    * @throws ServletException 
    */
   public static void postAnnotation(HttpServletRequest req, HttpServletResponse resp, Class entityClazz) throws IOException, ServletException {
      int uID = getUserID(req, resp);
      if (uID > 0) {
         try (Connection conn = getDBConnection()) {
            int id = Integer.parseInt(getPathParts(req)[1]);
            Constructor constr = entityClazz.getConstructor(Integer.TYPE);
            Entity ent = (Entity)constr.newInstance(id);
            Role r = ent.checkPermission(conn, uID, Role.CONTRIBUTOR);
            ObjectMapper mapper = getObjectMapper();
            Annotation ann = mapper.readValue(req.getInputStream(), Annotation.class);
            ann.setModifiedBy(uID, r);
            ann.setTarget(ent);

            conn.setAutoCommit(false);
            ann.insert(conn);
            Activity.record(conn, uID, ann, ent, mapper.writeValueAsString(ann));
            conn.commit();
            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.setHeader("Location", "/annotation/" + ann.getID());
         } catch (SQLException | ArrayIndexOutOfBoundsException | NumberFormatException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException | PermissionException ex) {
            reportInternalError(resp, ex);
         }
      }
   }

   /**
    * Utility method to delete a specified entity from the database.
    *
    * @param req
    * @param resp
    * @param entityClass class being instantiated
    * @throws IOException
    * @throws ServletException 
    */
   public static void deleteEntity(HttpServletRequest req, HttpServletResponse resp, Class entityClazz) throws IOException, ServletException {
      int uID = getUserID(req, resp);
      if (uID > 0) {
         try (Connection conn = getDBConnection()) {
            int id = Integer.parseInt(getPathParts(req)[1]);
            Constructor constr = entityClazz.getConstructor(Integer.TYPE);
            Entity ent = (Entity)constr.newInstance(id);
            ent.checkPermission(conn, uID, Role.OWNER);
            conn.setAutoCommit(false);
            String desc = ent.fetchDescription(conn);
            ent.delete(conn);
            Activity.record(conn, uID, ent, Operation.DELETE, desc);
            conn.commit();
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
         } catch (SQLException | ArrayIndexOutOfBoundsException | NumberFormatException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException | PermissionException ex) {
            reportInternalError(resp, ex);
         }
      }
   }

   /**
    * Utility method to retrieve a specified entity from the database using the class' (Connection, int)
    * constructor and send it to the client.
    *
    * @param conn connection to database
    * @param resp response to client
    * @param ent entity being loaded (consists just of an ID)
    * @param uID ID of current user
    * @throws IOException
    * @throws ServletException 
    */
   public static void loadAndSendEntity(HttpServletRequest req, HttpServletResponse resp, Class entityClazz, Module mod) throws IOException, ServletException {
      int uID = getUserID(req, resp);
      if (uID > -1) {
         try (Connection conn = getDBConnection()) {
            int id = Integer.parseInt(getPathParts(req)[1]);
            Constructor constr = entityClazz.getConstructor(Integer.TYPE);
            Entity ent = (Entity)constr.newInstance(id);
            //ent.checkPermission(conn, uID, Role.VIEWER); BH, PC, 4-29-16
            //do not check here because this is only called for /GET 
            ent.load(conn, false);

            resp.setContentType(JSON_CONTENT_TYPE);
            ObjectMapper mapper = getObjectMapper();
            if (mod != null) {
               mapper.registerModule(mod);
            }
            mapper.writeValue(resp.getOutputStream(), ent);
         } catch (SQLException | ArrayIndexOutOfBoundsException | NumberFormatException | ReflectiveOperationException ex) { //| PermissionException ex
            reportInternalError(resp, ex);
         }
      }
   }
   
   /**
    * Utility method to retrieve the children of a specified entity.
    *
    * @param conn connection to database
    * @param resp response to client
    * @param ent entity being loaded (consists just of an ID)
    * @param uID ID of current user
    * @throws IOException
    * @throws ServletException 
    */
   public static void loadAndSendChildren(HttpServletRequest req, HttpServletResponse resp, Class entClazz, String entID, String stmtText, Class childClazz, Module mod) throws IOException, ServletException {
      int uID = getUserID(req, resp);
      if (uID > -1) {
         try (Connection conn = getDBConnection()) {
            int id = Integer.parseInt(entID);
            Constructor constr = entClazz.getConstructor(Integer.TYPE);
            Entity ent = (Entity)constr.newInstance(id);
            ent.checkPermission(conn, uID, Role.VIEWER);
            List children = ent.loadChildren(conn, stmtText, childClazz, false);

            resp.setContentType(JSON_CONTENT_TYPE);
            ObjectMapper mapper = getObjectMapper();
            if (mod != null) {
               mapper.registerModule(mod);
            }
            mapper.writeValue(resp.getOutputStream(), children);
         } catch (SQLException | NumberFormatException | ReflectiveOperationException | PermissionException ex) {
            reportInternalError(resp, ex);
         }
      }
   }
   
   /**
    * Update the specified entity in the database with the modifications listed in the request body.  Any
    * fields which are not mentioned in the request body remain unchanged.  This is a convenience function
    * which calls the other version of modifyEntity
    *
    * @param req Json request body specifying the modifications to be made
    * @param resp so we can send error messages back to the caller
    * @param entityClazz class of entity being modified
    * @throws IOException
    * @throws ServletException 
    */
   public static void modifyEntity(HttpServletRequest req, HttpServletResponse resp, Class entityClazz) throws IOException, ServletException {
      int uID = getUserID(req, resp);
      if (uID > 0) {
         try (Connection conn = getDBConnection()) {
            int id = Integer.parseInt(getPathParts(req)[1]);
            Constructor constr = entityClazz.getConstructor(Integer.TYPE);
            Entity ent = (Entity)constr.newInstance(id);
            modifyEntity(ent, conn, req.getInputStream(), resp, uID);
         } catch (SQLException | ArrayIndexOutOfBoundsException | NumberFormatException | ReflectiveOperationException | PermissionException ex) {
            reportInternalError(resp, ex);
         }
      }
   }

   /**
    * Version of <code>modifyEntity</code> which does the actual work.
    *
    * @param ent entity being modified
    * @param conn connection to database
    * @param input stream containing Json data to be modified
    * @param uID ID of user making request
    */
   public static void modifyEntity(Entity ent, Connection conn, InputStream input, HttpServletResponse resp, int uID) throws SQLException, PermissionException, IOException, ReflectiveOperationException {
      ent.checkPermission(conn, uID, Role.EDITOR);
      conn.setAutoCommit(false);
      ObjectMapper mapper = getObjectMapper();
      Map<String, Object> mods = (Map<String, Object>)mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
      Object respData = ent.modify(conn, mods);
      Activity.record(conn, uID, ent, Operation.UPDATE, mapper.writeValueAsString(mods));
      conn.commit();
      if (respData != null) {
         mapper.writeValue(resp.getOutputStream(), respData);
         resp.setStatus(HttpServletResponse.SC_OK);
      } else {
         resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
      }
   }

   /**
    * Update the approvals of all or some of the Annotations targetted at the given Entity.
    * @param req servlet request which optionally contains array of Annotation IDs
    * @param conn connection to SQL database
    * @param uID EDITOR-level User who's granting the approval
    * @param ent Edition or Witness whose Annotations are being approved
    */
   public static void putApproval(HttpServletRequest req, Connection conn, int uID, Entity ent, String stmtText) throws SQLException, PermissionException, IOException {
      ent.checkPermission(conn, uID, Role.EDITOR);

      StringBuilder inClause = null;
      if (req.getContentLength() > 0) {
         // Request has supplied a list of Annotation IDs.
         int[] annIDs = getObjectMapper().readValue(req.getInputStream(), int[].class);
         for (int annID: annIDs) {
            if (inClause == null) {
               inClause = new StringBuilder().append(annID);
            } else {
               inClause.append(", ").append(annID);
            }
         }
         if (inClause == null) {
            // Caller passed us an empty array.  Nothing to update.
            return;
         }
         stmtText += " AND annotations.id IN (" + inClause + ")";
      }
      
      conn.setAutoCommit(false);
      try (PreparedStatement stmt = conn.prepareStatement(stmtText)) {
         stmt.setInt(1, uID);
         stmt.setInt(2, ent.getID());
         if (stmt.executeUpdate() == 0) {
            // Nothing modified by update.
            return;
         }
      }
      Activity.record(conn, uID, ent, Operation.UPDATE, String.format("Approved [%s]", inClause));
      conn.commit();
   }

   public static void putPermissions(HttpServletRequest req, Connection conn, int uID, Entity ent, String select, boolean replacing) throws IOException, ServletException, SQLException, ReflectiveOperationException, PermissionException {
      ObjectMapper mapper = getObjectMapper();

      List<Permission> newPerms = mapper.readValue(req.getInputStream(), new TypeReference<List<Permission>>() {});
      for (Permission perm: newPerms) {
         perm.setTarget(ent);
      }

      ent.checkPermission(conn, uID, Role.OWNER);
      List<Permission> oldPerms = ent.loadChildren(conn, select, Permission.class, false);

      conn.setAutoCommit(false);
      ent.mergeChildren(conn, oldPerms, newPerms, Permission.getMergingComparator(), null, replacing);
      Activity.record(conn, uID, ent, Operation.UPDATE, String.format("permissions?merge=%s %s", !replacing, mapper.writeValueAsString(newPerms)));
      conn.commit();
   }

   /**
    * Report a servlet-related error to the client.
    * @param resp response for passing stuff back to the client
    * @param code HTTP error code
    * @param ex exception which was caught
    * @param msg human-friendly error message
    * @throws IOException 
    */
   public static void reportError(HttpServletResponse resp, int code, Throwable ex, String msg) throws IOException {
      LOG.log(Level.SEVERE, msg, ex);
      resp.sendError(code, String.format("%s: %s", msg, LangUtils.getMessage(ex)));
   }

   /**
    * Handle some commonly thrown internal exceptions.
    * @param resp
    * @param ex
    * @throws IOException 
    */
   public static void reportInternalError(HttpServletResponse resp, Throwable ex) throws IOException {
      if (ex instanceof InvocationTargetException) {
         reportInternalError(resp, ex.getCause());
      } else if (ex instanceof MySQLIntegrityConstraintViolationException) {
         reportError(resp, HttpServletResponse.SC_CONFLICT, ex, "Database integrity violation");
      } else if (ex instanceof NoSuchEntityException) {
         resp.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
      } else if (ex instanceof SQLException) {
         reportError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex, "Database error");
      } else if (ex instanceof JsonProcessingException) {
         reportError(resp, HttpServletResponse.SC_BAD_REQUEST, ex, "Unable to process JSON input");
      } else if (ex instanceof NumberFormatException) {
         reportError(resp, HttpServletResponse.SC_BAD_REQUEST, ex, "Unable to parse ID");
      } else if (ex instanceof ArrayIndexOutOfBoundsException) {
         reportError(resp, HttpServletResponse.SC_BAD_REQUEST, ex, "Not enough path elements");
      } else if (ex instanceof IllegalArgumentException) {
         // Unable to parse an enum value.
         reportError(resp, HttpServletResponse.SC_BAD_REQUEST, ex, "Bad request");
      } else if (ex instanceof PermissionException) {
         resp.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
      } else if (ex instanceof NoSuchMethodException) {
         reportError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex, "No such method: " + ex.getMessage());
      } else {
         reportError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex, "Internal server error");
      }
   }

   private static final Logger LOG = Logger.getLogger(ServletUtils.class.getName());

   public static final SimpleDateFormat RFC1123_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

   // HTTP headers
   public static final String SET_COOKIE_HEADER = "Set-Cookie";
   public static final String COOKIE_HEADER = "Cookie";
   public static final String CONTENT_TYPE_HEADER = "Content-Type";
   public static final String IF_MODIFIED_SINCE_HEADER = "If-Modified-Since";

   // Content types
   public static final String HTML_CONTENT_TYPE = "text/html; charset=UTF-8";
   public static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";
   public static final String TEXT_CONTENT_TYPE = "text/plain; charset=UTF-8";
   
   /** Regex pattern for extracting charset from a Content-Type header. */
   private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)\\bcharset=\\s*\"?([^\\s;\"]*)");
}
