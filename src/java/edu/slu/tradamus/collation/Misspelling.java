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

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A rule which specifies a common misspelling.
 *
 * @author tarkvara
 */
public class Misspelling {
   String correct;
   Pattern incorrect;
   
   public Misspelling(String corr, String incorr) {
      correct = corr;
      incorrect = Pattern.compile(incorr);
   }

   /**
    * If we have a match, fix it.  Also try to find other matches further in the word and in the fixed word.
    * @param mat matcher being processed
    * @param pos position at which to start matching
    * @param fixes accumulates our fixed forms
    */
   private void tryToFix(Matcher mat, int pos, Set<String> fixes) {
      if (mat.find(pos)) {
         StringBuffer buf = new StringBuffer();
         mat.appendReplacement(buf, correct);
         int pos1 = buf.length();
         mat.appendTail(buf);
         String fixed = buf.toString();
         fixes.add(fixed);
         
         // Look for subsequent matches in the original word.
         pos = mat.end();
         tryToFix(mat, pos, fixes);

         // Look for subsequent matches in the fixed word.
         // We use pos1 rather than pos to avoid tractatus → tracctatus → traccctatus.
         mat = incorrect.matcher(fixed);
         tryToFix(mat, pos1, fixes);
      }
   }
   
   /**
    * Produce all possible fixes of a word using this rule.
    *
    * @param word word to be fixed
    * @param fixes existing fixes introduced by other misspellings
    */
   public void fixAll(String word, Set<String> fixes) {
      Matcher mat = incorrect.matcher(word);
      if (mat.find()) {
         // Misspelling is found in the base word, so run the fix against all the possible forms.
         Set<String> newFixes = new HashSet<>();
         for (String form: fixes) {
            mat = incorrect.matcher(form);
            tryToFix(mat, 0, newFixes);
         }
         fixes.addAll(newFixes);
      }
   }
}
