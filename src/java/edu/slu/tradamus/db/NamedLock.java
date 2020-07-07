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
package edu.slu.tradamus.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Class for doing named MySQL locks
 * @author tarkvara
 */
public class NamedLock implements AutoCloseable {
   /** Connection to MySQL database. */
   private final Connection connection;
   
   /** Name of database lock. */
   private final String name;

   /**
    * Create the given named MySQL lock with a default timeout.
    *
    * @param conn connection to MySQL
    * @param lockName name of lock
    * @throws SQLException
    */
   public NamedLock(Connection conn, String lockName) throws SQLException {
      connection = conn;
      name = lockName;
      PreparedStatement stmt = conn.prepareStatement("SELECT GET_LOCK(?, ?)");
      stmt.setString(1, lockName);
      stmt.setInt(2, DB_LOCK_TIMEOUT);
      ResultSet rs = stmt.executeQuery();
      rs.next();
      boolean locked = rs.getBoolean(1);
      if (locked) {
         conn.setAutoCommit(false);
      } else {
         throw new SQLException(String.format("Unable to get lock \"%s\".", name));
      }
   }

   /**
    * Release a MySQL lock which was acquired by constructor.
    *
    * @throws SQLException 
    */
   @Override
   public void close() throws SQLException {
      connection.commit();
      PreparedStatement stmt = connection.prepareStatement("SELECT RELEASE_LOCK(?)");
      stmt.setString(1, name);
      stmt.executeQuery();
   }

   /** Default timeout (in seconds) for database lock attempts. */
   private static final int DB_LOCK_TIMEOUT = 10;
}
