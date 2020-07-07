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

import java.util.Map;
import edu.slu.tradamus.text.TextAnchor;


/**
 * Basic unit of collation.  In our current implementation, it corresponds to a single vertex in a CollateX
 * variant graph.
 *
 * @author tarkvara
 */
public class Mote {
   private String text;
   private TextAnchor[] anchors;

   public Mote(String t, Map<Integer, TextAnchor> val) {
      text = t;
      anchors = new TextAnchor[val.size()];
      int i = 0;
      for (Map.Entry<Integer, TextAnchor> e: val.entrySet()) {
         anchors[i++] = e.getValue();
      }
   }

   public String getText() {
      return text;
   }

   public void setText(String t) {
      text = t;
   }

   public TextAnchor[] getAnchors() {
      return anchors;
   }

   public void setAnchors(TextAnchor[] anchs) {
      anchors = anchs;
   }
}
