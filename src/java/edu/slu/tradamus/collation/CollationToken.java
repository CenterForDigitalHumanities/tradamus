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
package edu.slu.tradamus.collation;

import eu.interedition.collatex.Token;
import eu.interedition.collatex.Witness;
import edu.slu.tradamus.text.Page;
import edu.slu.tradamus.text.TextAnchor;


/**
 * Token suitable for passing Tradamus data to CollateX' algorithms.
 *
 * @author tarkvara
 */
class CollationToken extends TextAnchor implements Token {
   private final CollationWitness witness;
   String text;

   /**
    * Construct a new token for analysis by CollateX.
    *
    * @param w source Witness (in the CollateX sense) for this token
    * @param t text of the token
    * @param p page index on which token occurs
    * @param o start of token within page
    */
   public CollationToken(CollationWitness w, String t, Page pg, int o) {
      super(pg, o, pg, o + t.length());
      witness = w;
      text = t;
   }

   /**
    * For debug purposes, a string representation of our token.
    */
   @Override
   public String toString() {
      return String.format("%s:%s\'%s\'", witness.getSigil(), super.toString(), text);
   }

   public String getText() {
      return text;
   }

   @Override
   public Witness getWitness() {
      return witness;
   }
}
