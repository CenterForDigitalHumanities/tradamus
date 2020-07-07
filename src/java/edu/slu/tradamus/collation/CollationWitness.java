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

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import eu.interedition.collatex.Token;
import eu.interedition.collatex.Witness;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.text.Page;
import edu.slu.tradamus.text.TextAnchor;
import edu.slu.tradamus.text.TextRange;
import edu.slu.tradamus.text.Transcription;


/**
 * Class which wraps up a Tradamus transcription to look like a CollateX witness.  The base class is
 * intended to deal with plain-text tokens.
 *
 * @author tarkvara
 */
public class CollationWitness implements Witness, Iterable<Token>, Comparator<Token> {
   private final Transcription transcription;
   protected final List<Token> tokens = new ArrayList<>();
   private final IgnoreLineBreaks ignoringLineBreaks;
   private final boolean ignoringCase;
   private final boolean ignoringPunctuation;
   private final boolean usingTEITags;

   public CollationWitness(Transcription transcr, IgnoreLineBreaks ignoreLineBreaks, boolean ignoreCase, boolean ignorePunct, boolean useTEITags) {
      transcription = transcr;
      ignoringLineBreaks = ignoreLineBreaks;
      ignoringCase = ignoreCase;
      ignoringPunctuation = ignorePunct;
      usingTEITags = useTEITags;
   }

   /**
    * Run through our content and convert it into tokens.
    *
    * @param pg1 starting page index within transcription
    * @param offset1 starting text start within <code>pg1</code>
    * @param pg2 ending page index within transcription
    * @param offset2 ending text start within <code>pg2</code>
    * @throws IOException
    */
   public void tokenise(Connection conn, int pg1, int offset1, int pg2, int offset2) throws IOException, SQLException, ReflectiveOperationException {
      for (int pageNum = pg1; pageNum <= pg2; pageNum++) {
         Page page = transcription.getPage(pageNum);
         String pageText = page.getText();
         int pageStart = pageNum == pg1 ? offset1 : 0;
         int pageEnd = pageNum == pg2 && offset2 >= 0 ? offset2 : pageText.length();
         int tokenStart = -1;
         for (int i = pageStart; i < pageEnd; i++) {
            if (isSeparator(pageText.charAt(i))) {
               if (tokenStart >= 0) {
                  addToken(page, tokenStart, i);
                  tokenStart = -1;
               }
            } else if (tokenStart < 0) {
               tokenStart = i;
            }
         }
         // Take care of the last token on the page.
         if (tokenStart >= 0) {
            addToken(page, tokenStart, pageEnd);
         }
      }
      
      List<Annotation> anns = transcription.loadNonLineAnnotations(conn);

      if (usingTEITags) {
         handleTEITags(anns);
      }

      // If we're ignoring linefeeds, merge any tokens which are separated only by a linefeed.
      if (ignoringLineBreaks != IgnoreLineBreaks.FALSE) {
         for (int i = 0; i < tokens.size() - 1; i++) {
            CollationToken tok = (CollationToken)tokens.get(i);
            CollationToken nextTok = (CollationToken)tokens.get(i + 1);
            TextAnchor gap = TextAnchor.gapBetween(tok, nextTok);
            String gapText = getText(gap);
            if (gapText.charAt(0) == '\n') {
               if (ignoringLineBreaks == IgnoreLineBreaks.TRUE || tok.getText().endsWith("-")) {
                  if (containsBoundary(gap, anns)) {
                     LOG.log(Level.INFO, "Can''t ignore line-break between {0} and {1} due to annotation.", new Object[] { tok, nextTok });
                  } else {
                     mergeTokenWithNext(i);
                  }
               }
            }
            if (ignoringCase) {
               tok.text = tok.text.toLowerCase();
            }
            if (ignoringPunctuation) {
               tok.text = tok.text.replaceAll("\\p{Punct}", "");
            }
         }
      }

      // For debug purposes.
/*      StringBuilder buf = new StringBuilder();
      for (int i = 0; i < Math.min(100, tokens.size()); i++) {
         buf.append(tokens.get(i));
         buf.append('\n');
      }
      LOG.log(Level.INFO, buf.toString());*/
   }

   /**
    * Convenience function to tokenise the entire transcription.
    */
   public void tokenise(Connection conn) throws IOException, SQLException, ReflectiveOperationException {
      tokenise(conn, 0, 0, transcription.getPageCount() - 1, -1);
   }

   @Override
   public String getSigil() {
      return Integer.toString(transcription.getWitness().getID());
   }

   /**
    * Get text for the range identified by the given text anchor.
    *
    * @param anch the range we're interested in
    */
   public String getText(TextAnchor anch) {
      return transcription.getText(anch);
   }

   private boolean isSeparator(char c) {
      return Character.isWhitespace(c);
   }

   private void handleTEITags(List<Annotation> anns) {
      int startIndex = 0;
      
      // First, extract the <choice> annotations because they're important for processing other ones.
      List<Annotation> choices = new ArrayList<>();
      for (Annotation ann: anns) {
         if (ann.getType().equals("choice")) {
            choices.add(ann);
         }
      }

      for (Annotation ann: anns) {
         switch (ann.getType()) {
            case "add":       
            case "supplied":
            case "expan":
            case "corr":
            case "reg":
               // Material added by the editor which should be included within the collation.
               break;
            case "del":
            case "surplus":
               // Erroneous or extra text which shouldn't participate in the collation.
               startIndex = deleteTokensInRange(ann, startIndex);
               break;
            case "abbr":
               // Content of an <abbr> may be replaced by an <expan>.
               startIndex = deleteIfUnchosen(ann, choices, startIndex);
               break;
            case "sic":
               // Content of a <sic> may be replaced by a <corr>.
               startIndex = deleteIfUnchosen(ann, choices, startIndex);
               break;
            case "orig":
               // Content of an <orig> may be replaced by a <reg>.
               startIndex = deleteIfUnchosen(ann, choices, startIndex);
               break;
            case "subst":
            case "gap":
            case "unclear":
               // Nothing needs to be done with subst, gap and unclear tags.
               break;
         }
      }
   }

   /**
    * Handle an &lt;abbr>, &lt;orig>, or &lt;sic> element within a &lt;choice> by deleting its content.
    * @param choices all &lt;choice> annotations in the transcription
    * @param ann annotation to be deleted if it lies within a &lt;choice>
    * @param startIndex start token for tokens
    * @return new value of <code>startIndex</code>
    */
   private int deleteIfUnchosen(Annotation ann, List<Annotation> choices, int startIndex) {
      for (Annotation choice: choices) {
         if (TextAnchor.contains(choice, ann)) {
            startIndex = deleteTokensInRange(ann, startIndex);
            break;
         }
      }
      return startIndex;
   }

   protected void addToken(Page pg, int start, int end) {
      tokens.add(new CollationToken(this, pg.getText().substring(start, end), pg, start));
   }

   /**
    * Given a range (typically an annotation), figure out which tokens lie therein.  May include tokens
    * which straddle the range's boundaries.
    *
    * @param range range to be checked
    * @param startIndex point in <code>tokens</code> array to start looking for a match
    * @return a 2-element array with the start and finish indices of the found tokens
    */
   private int[] findTokensInRange(TextRange range, int startIndex) {
      int[] result = null;
      for (int i = startIndex; i < tokens.size(); i++) {
         CollationToken tok = (CollationToken)tokens.get(i);
         if (TextAnchor.intersects(range, tok)) {
            int j = i;
            while (TextAnchor.intersects(range, (CollationToken)tokens.get(j + 1))) {
               j++;
            }
            // Range i–j now contains all tokens which are wholly or partially contained within the range.
            result = new int[] { i, j };
            break;
         }
      }
      return result;
   }

   /**
    * Given a range (typically an annotation), delete any tokens which lie therein.
    *
    * @param ann annotation whose contents need to be deleted
    * @param startIndex
    * @return 
    */
   private int deleteTokensInRange(TextRange ann, int startIndex) {
      int[] range = findTokensInRange(ann, startIndex);
      if (range != null) {
         // Future searches can start here.
         startIndex = range[0];         

         // Deal with any tokens which may straddle the range boundary.
         CollationToken firstTok = (CollationToken)tokens.get(range[0]);
         if (!TextAnchor.contains(ann, firstTok.getStartPage(), firstTok.getStartOffset())) {
            // Tail of first token is part of the deletion.
            String newText = firstTok.text.substring(0, ann.getStartOffset() - firstTok.getStartOffset());
            LOG.log(Level.INFO, "{0} truncating \"{1}\" to \"{2}\"", new Object[] { ann, firstTok.text, newText });
            firstTok.text = newText;
            range[0]++;
         }
         CollationToken lastTok = (CollationToken)tokens.get(range[1]);
         if (!TextAnchor.contains(ann, lastTok.getEndPage(), lastTok.getEndOffset())) {
            // Head of the last token is part of the deletion.
            String newText = lastTok.text.substring(lastTok.text.length() - (lastTok.getEndOffset() - ann.getEndOffset()));
            LOG.log(Level.INFO, "{0} truncating \"{1}\" to \"{2}\"", new Object[] { ann, lastTok.text, newText });
            lastTok.text = newText;
            range[1]--;
         }

         if (range[1] >= range[0]) {
            tokens.subList(range[0], range[1]).clear();
         }
      }
      return startIndex;
   }

   /**
    * A straight merge of two tokens.
    * @param i the first of the tokens to be merged
    * @param trimHyphens if true, any final hyphen will be trimmed from the first token before merging
    */
   private void mergeTokenWithNext(int i) {
      CollationToken tok = (CollationToken)tokens.get(i);
      if (i < tokens.size() - 1) {
         CollationToken nextTok = (CollationToken)tokens.get(i + 1);
         if (ignoringLineBreaks == IgnoreLineBreaks.HYPHENS && tok.text.endsWith("-")) {
            tok.text = tok.text.substring(0, tok.text.length() - 1) + nextTok.text;
         } else {
            tok.text += nextTok.text;
         }
         tok.setEndPage(nextTok.getEndPage());
         tok.setEndOffset(nextTok.getEndOffset());
         tokens.remove(i + 1);
      }
   }

   /**
    * Does the given text anchor contain an annotation boundary, preventing line-break suppression from
    * occurring.
    * @param anch the range being evaluated
    * @param anns all annotations associated with this transcription
    * @return true if the range contains an annotation boundary
    */
   private boolean containsBoundary(TextAnchor anch, List<Annotation> anns) {
      for (Annotation ann: anns) {
         if (anch.contains(ann.getEndPage(), ann.getEndOffset())) {
            LOG.log(Level.INFO, "{0} contains end of {1}", new Object[] { getText(anch), ann });
            return true;
         }
      }
      return false;
   }

   @Override
   public Iterator<Token> iterator() {
      return tokens.iterator();
   }

   /**
    * This comparator is intended purely for sorting; it just compares two tokens based on their position
    * within this witness.
    */
   @Override
   public int compare(Token o1, Token o2) {
      final int o1Index = tokens.indexOf(o1);
      final int o2Index = tokens.indexOf(o2);
      return o1Index - o2Index;
   }
   
   private static final Logger LOG = Logger.getLogger(CollationWitness.class.getName());

   /**
    * Three different ways we can do the comparison between tokens.
    */
   public static enum Comparison {
      PLAIN,
      ORTH,
      MORPH;
      
      public static Comparison fromString(String s) {
         if (s == null) {
            return PLAIN;
         }
         return Enum.valueOf(Comparison.class, s.toUpperCase());
      }
   }

   /**
    * Three different ways we can deal with line-breaks when tokenising.
    */
   public static enum IgnoreLineBreaks {
      TRUE,
      HYPHENS,
      FALSE;
      
      public static IgnoreLineBreaks fromString(String s) {
         if (s == null) {
            return FALSE;
         }
         return Enum.valueOf(IgnoreLineBreaks.class, s.toUpperCase());
      }
   }
}
