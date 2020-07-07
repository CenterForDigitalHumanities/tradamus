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

import java.util.*;
import java.util.logging.Logger;
import eu.interedition.collatex.*;
import eu.interedition.collatex.jung.JungVariantGraph;
import edu.slu.tradamus.text.TextAnchor;


/**
 * Wrapper around CollateX collation engine.
 *
 * @author tarkvara
 */
public class CollateXEngine {
   
   /**
    * Wrapper around the CollateX engine.
    *
    * @param wits witnesses to be collated
    * @param comp comparator object to make the comparisons between tokens
    * @return list of motes which represent the collation
    */
   public List<Mote> collate(List<CollationWitness> wits, Comparator<Token> comp) {
      CollationAlgorithm collationAlgorithm = CollationAlgorithmFactory.dekker(comp);
      VariantGraph variantGraph = new JungVariantGraph();
      for (CollationWitness w: wits) {
         collationAlgorithm.collate(variantGraph, w);
      }
      
      // Merge sequences of matches into a single edge.
      VariantGraph.JOIN.apply(variantGraph);

      List<Mote> result = new ArrayList<>();
      
      for (VariantGraph.Vertex v: variantGraph.vertices()) {
         Map<Integer, TextAnchor> moteContents = new HashMap<>(v.witnesses().size());
         String moteText = null;
         for (Witness w: v.witnesses()) {
            List<Token> sortedToks = new ArrayList<>(v.tokens(Collections.singleton(w)));
            if (sortedToks.size() > 0) {
               Collections.sort(sortedToks, (CollationWitness)w);
               CollationToken firstTok = (CollationToken)sortedToks.get(0);
               CollationToken lastTok = (CollationToken)sortedToks.get(sortedToks.size() - 1);
               TextAnchor anch = new TextAnchor(firstTok.getStartPage(), firstTok.getStartOffset(), lastTok.getEndPage(), lastTok.getEndOffset());
               moteContents.put(Integer.parseInt(w.getSigil()), anch);
               if (moteText == null) {
                  moteText = ((CollationWitness)w).getText(anch);
               }
            }
         }
         if (!moteContents.isEmpty()) {
           result.add(new Mote(moteText, moteContents));
         }
      }
      return result;
   }

   /**
    * For debug purposes, make a string representation of a vertex' contents.
    * @param v vertex to be dumped
    */
   private String dumpVertex(VariantGraph.Vertex v) {
      StringBuilder buf = new StringBuilder("[");
      String sep = "";
      for (Witness w: v.witnesses()) {
         buf.append(sep);
         buf.append(w.getSigil());
         sep = ",";
      }
      buf.append("]=\"");
      
      Iterator<Token> iter = v.tokens().iterator();
      while (iter.hasNext()) {
         buf.append(iter.next());
      }
      buf.append("\"");
      return buf.toString();
   }
   
   private static final Logger LOG = Logger.getLogger(CollateXEngine.class.getName());
}
