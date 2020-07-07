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
package edu.slu.tradamus.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.servlet.ServletException;
import static edu.slu.tradamus.util.ServletUtils.getDBConnection;

/**
 * Class for managing the contents of database-driven email messages and such.
 *
 * @author tarkvara
 */
public class MessageUtils {
   /**
    * Retrieve a message from the database and format it for the user.
    * @param id message ID
    * @param args arguments for String.format
    * @return the formatted message
    */
   public static String format(MessageID id, Object... args) throws SQLException, ServletException {
      String result = id.toString();
      try (Connection conn = getDBConnection()) {
         try (PreparedStatement stmt = conn.prepareStatement("SELECT `english` FROM `messages` WHERE `id` = ?")) {
            stmt.setString(1, id.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
               String format = rs.getString(1);
               result = String.format(format, args);
            }
         }
      }
      return result;
   }
}
