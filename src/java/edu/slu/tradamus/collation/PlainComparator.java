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

import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import eu.interedition.collatex.Token;

/**
 * Comparator for collating plain-text tokens.
 *
 * @author tarkvara
 */
public class PlainComparator implements Comparator<Token> {
   /**
    * This comparator compares to tokens based on their content.
    */
   @Override
   public int compare(Token base, Token witness) {
      int result = ((CollationToken)base).text.compareTo(((CollationToken)witness).text);
      if (result == 0) {
         LOG.log(Level.FINE, "{0} and {1} were equal", new Object[] { base, witness });
      }
      return result;
   }
      
   private static final Logger LOG = Logger.getLogger(PlainComparator.class.getName());
}
