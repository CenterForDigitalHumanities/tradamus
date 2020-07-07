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
package edu.slu.tradamus.text;


/**
 * Class which marks off a section of text within a transcription.
 *
 * @author tarkvara
 */
public class TextAnchor implements TextRange {
   Page startPage;
   int startOffset;
   Page endPage;
   int endOffset;
   
   public TextAnchor(Page p0, int off0, Page p1, int off1) {
      startPage = p0;
      startOffset = off0;
      endPage = p1;
      endOffset = off1;
   }

   @Override
   public Page getStartPage() {
      return startPage;
   }

   public void setStartPage(Page pg) {
      startPage = pg;
   }

   @Override
   public int getStartOffset() {
      return startOffset;
   }

   public void setStartOffset(int off) {
      startOffset = off;
   }

   @Override
   public Page getEndPage() {
      return endPage;
   }

   public void setEndPage(Page pg) {
      endPage = pg;
   }

   @Override
   public int getEndOffset() {
      return endOffset;
   }

   public void setEndOffset(int off) {
      endOffset = off;
   }

   @Override
   public String toString() {
      return String.format("[%s:%d–%s:%d]", startPage, startOffset, endPage, endOffset);
   }

   /**
    * Convenience method to call the static <code>contains</code> method.
    * @param pg page of location being checked for containment
    * @param offset offset of location being checked for containment
    * @return true if this anchor contains the given page/offset
    */
   public boolean contains(Page pg, int offset) {
      return contains(this, pg, offset);
   }

   /**
    * Utility method for checking a particular text location for containment in an anchor.
    * @param anch anchor to be checked
    * @param pg page of location which may be contained
    * @param offset offset which may be contained
    * @return true if <code>anch</code> contains the given page/offset
    */
   public static boolean contains(TextRange anch, Page pg, int offset) {
      if (anch == null || anch.getStartPage().getIndex() > pg.getIndex() || anch.getEndPage().getIndex() < pg.getIndex()) {
         return false;
      }

      if (anch.getStartPage().getIndex() == pg.getIndex()) {
         if (anch.getStartOffset() > offset) {
            // Point is on the first page, before start of range.
            return false;
         }
      }
      if (anch.getEndPage().getIndex() == pg.getIndex()) {
         if (anch.getEndOffset() < offset) {
            // Point is on the last page, after start of range.
            return false;
         }
      }
      return true;
   }

   /**
    * Check to see if the first text range contains the second.
    * @param anch1 the potentially-containing text range
    * @param anch2 the potentially-contained text range
    * @return true if <code>anch1</code> contains <code>anch2</code>
    */
   public static boolean contains(TextRange anch1, TextRange anch2) {
      boolean result = false;
      if (anch1 != null && anch2 != null) {
         result = contains(anch1, anch2.getStartPage(), anch2.getStartOffset()) && contains(anch1, anch2.getEndPage(), anch2.getEndOffset());
      }
      return result;
   }

   /**
    * Determine whether two ranges intersect.
    * @param anch1 first range to be checked
    * @param anch2 second range to be checked
    * @return true if the intersection is non-empty
    */
   public static boolean intersects(TextRange anch1, TextRange anch2) {
      boolean result = false;
      if (anch1 != null && anch2 != null) {
         result = contains(anch1, anch2.getStartPage(), anch2.getStartOffset()) || contains(anch1, anch2.getEndPage(), anch2.getEndOffset());
      }
      return result;
   }

   /**
    * Return a text anchor which represents the range encompassing two anchors.
    *
    * @param anch0 start anchor
    * @param anch1 end anchor
    * @return range which goes from start of anch0 to end of anch1
    */
   public static TextAnchor union(TextRange anch0, TextRange anch1) {
      return new TextAnchor(anch0.getStartPage(), anch0.getStartOffset(), anch1.getEndPage(), anch1.getEndOffset());
   }
   
   /**
    * Return a text anchor which represents the space between two anchors.
    * @param anch0 first anchor
    * @param anch1 second anchor
    * @return the space after the end of anch0 and before the start of anch1
    */
   public static TextAnchor gapBetween(TextRange anch0, TextRange anch1) {
      if (anch0.getEndPage() == anch1.getStartPage()) {
         // Easy case, all on one page.
         if (anch0.getEndOffset() < anch1.getStartOffset()) {
            return new TextAnchor(anch0.getEndPage(), anch0.getEndOffset(), anch1.getStartPage(), anch1.getStartOffset());
         }
      } else {
         return new TextAnchor(anch0.getEndPage(), anch0.getEndOffset(), anch1.getStartPage(), anch1.getStartOffset());
      }
      return null;
   }
}
