/*
 * Copyright 2014-2015 Saint Louis University. Licensed under the
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
package edu.slu.tradamus.witness;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.stream.XMLStreamException;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.edition.Edition;
import edu.slu.tradamus.text.Page;

/**
 * Simple witness just drawn from a text file with no internal structure.
 *
 * @author tarkvara
 */
public class PlainTextWitness extends Witness {
   /**
    * Construct a witness from a TEI XML document.
    *
    * @param ed edition to which the new witness will belong
    * @param input stream from which we're reading text data
    * @param lineBreak character string which is used to indicate line breaks
    * @param pageBreak character string which is used to indicate page breaks
    * @param encoding character encoding
    * @param t title
    * @param s siglum
    */
   public PlainTextWitness(Edition ed, InputStream input, String lineBreak, String pageBreak, String encoding, String t, String s) throws IOException, XMLStreamException {
      super(ed, null);
      
      title = t;
      siglum = s;
      String allText = Streams.asString(input, encoding);
      String[] pages;
      if (pageBreak != null) {
         pages = allText.split(pageBreak);
      } else {
         pages = new String[] { allText };
      }

      for (String p: pages) {
         processPage(p, lineBreak);
      }

      // Once we've loaded all the lines, we have to do secondary processing to extract any embedded XML
      // tags.  As a side-effect, this may adjust any line offsets/lengths and page lengths.
      importedAnnotations.addAll(transcription.extractTagAnnotations());
   }

   private void processPage(String pageText, String lineBreak) {
      Page p = new Page(transcription);
      p.setText(pageText);

      String[] lines;
      if (lineBreak != null) {
         lines = pageText.split(lineBreak);
      } else {
         // No line-break specified, so the page is treated as one huge line.
         lines = new String[] { pageText };
      }
      int i = 0;
      for (String l: lines) {
         Annotation ann = p.createLine(i, i + l.length());
         importedAnnotations.add(ann);
      }
      transcription.addPage(p);
   }
}
