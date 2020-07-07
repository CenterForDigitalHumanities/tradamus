/*
 * Copyright 2014 Saint Louis University. Licensed under the
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
package edu.slu.tradamus.collation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import eu.interedition.collatex.Token;

/**
 * Comparator for doing an orthographically-aware comparison of two tokens.
 *
 * @author tarkvara
 */
public class OrthographicComparator implements Comparator<Token> {
   private final List<Misspelling> rules = new ArrayList<>();
   private final Map<String, Set<String>> corrections = new HashMap<>();

   public OrthographicComparator() {
   }
   
   /**
    * Loads our misspelling rules from the database.
    *
    * @param conn connection to MySQL database
    * @throws SQLException 
    */
   public void loadDictionaries(Connection conn, String... dicts) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement("SELECT `correct`, `incorrect` FROM `misspellings` WHERE `dictionary` = ?")) {
         for (String d: dicts) {
            stmt.setString(1, d);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
               rules.add(new Misspelling(rs.getString(1), rs.getString(2)));
            }
         }
      }
   }

   /**
    * Compare two tokens for possible orthographic identity.
    *
    * @param tok1 first token to be compared
    * @param tok2 second token to be compared
    * @return true if the tokens are identical, or at least some of their potential spellings are
    */
   @Override
   public int compare(Token tok1, Token tok2) {
      String word1 = ((CollationToken)tok1).text;
      String word2 = ((CollationToken)tok2).text;
      
      int result = word1.compareTo(word2);
      if (result != 0) {
         Set<String> corr1 = getCorrections(word1);
         Set<String> corr2 = getCorrections(word2);
         if (!Collections.disjoint(corr1, corr2)) {
            LOG.log(Level.INFO, "{0} ≈ {1}", new Object[] { word1, word2 });
            result = 0;
         }
      }
      return result;
   }
   
   /**
    * Use all rules which apply to make a set of all the possible words of which this is a misspelling.
    * @param word misspelled word to be fixed
    * @param pos position at which to apply the fix
    * @return all possible correct words which might correspond to <c>word</c>
    */
   private Set<String> getCorrections(String word) {
      Set<String> result = corrections.get(word);
      if (result == null) {
         result = new HashSet<>();
         result.add(word);
         for (Misspelling r: rules) {
            r.fixAll(word, result);
         }
         corrections.put(word, result);
      }
      return result;
   }

   private static final Logger LOG = Logger.getLogger(OrthographicComparator.class.getName());
}
