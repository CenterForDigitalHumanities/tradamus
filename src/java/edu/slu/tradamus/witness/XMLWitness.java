/*
 * Copyright 2013-2015 Saint Louis University. Licensed under the
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

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.edition.Edition;
import edu.slu.tradamus.image.Canvas;
import edu.slu.tradamus.image.Image;
import edu.slu.tradamus.text.Page;


/**
 * Class for importing Jeffrey Witt-style XML documents as witnesses.
 *
 * @author tarkvara
 */
public class XMLWitness extends Witness {

   /**
    * Reader which we loop over.
    */
   private XMLStreamReader reader;

   /**
    * All surfaces attached to the &lt;facsimile> element of the document.  These will be attached
    * to importedAnnotations on pages when we process the &lt;body>.
    */
   private List<SurfaceDef> surfaces;

   /**
    * If greater than zero, we're inside an &lt;ab>, &lt;p> or &lt;head> tag, so we should collect
    * whitespace characters.
    */
   private int collectingWhitespace;

   /**
    * The page which is currently being processed.
    */
   private Page pendingPage;

   /**
    * Start of the current line relative to the current page.
    */
   private int startOfLine;

   /**
    * Stack of annotations which are currently being processed.
    */
   private Stack<Annotation> pendingAnnotations = new Stack<>();


   /**
    * Construct a witness from a TEI XML document.
    *
    * @param ed edition to which the new witness will belong
    * @param input stream from which we're reading XML data
    * @param imageBase base URL to which image URLs are relative
    */
   public XMLWitness(Edition ed, InputStream input, String imageBase) throws XMLStreamException, URISyntaxException {
      super(ed, null);
      XMLInputFactory factory = XMLInputFactory.newInstance();
      reader = null;

      try {
         reader = factory.createXMLStreamReader(input);
         while (reader.hasNext()) {
            switch (reader.next()) {
               case XMLStreamConstants.START_ELEMENT:
                  switch (reader.getLocalName()) {
                     case "teiHeader":
                        processHeader();
                        break;
                     case "facsimile":
                        processFacsimile(imageBase);
                        break;
                     case "text":
                        processText();
                        break;
                     default:
                        LOG.log(Level.FINE, "Ignoring {0}", reader.getLocalName());
                        break;
                  }
                 break;
            }
         }
      } finally {
         if (reader != null) {
            reader.close();
            reader = null;
         }
      }
   }
   
   private void processHeader() throws XMLStreamException {
      Stack<Annotation> pendingMetadata = new Stack<>();
      String context = "";
      while (reader.hasNext()) {
         String name;
         switch (reader.next()) {
            case XMLStreamConstants.START_ELEMENT:
               name = reader.getLocalName();
               switch (name) {
                  case "title":
                     title = reader.getElementText();
                     break;
                  case "author":
                     author = reader.getElementText();
                     break;
                  case "witness":
                     siglum = reader.getAttributeValue(null, "id");
                     break;
                  default:
                     Annotation ann = new Annotation(context + name, null, this, null);
                     importedAnnotations.add(ann);
                     addAttributes(ann);
                     pendingMetadata.push(ann);
                     context += name + ".";
                     break;
               }
               break;
            case XMLStreamConstants.CHARACTERS:
               // The pending metadatum has some associated content.
               if (!pendingMetadata.isEmpty()) {
                  String text = reader.getText().trim();
                  if (!text.isEmpty()) {
                     pendingMetadata.peek().setContent(text);
                  }
               }
               break;
            case XMLStreamConstants.END_ELEMENT:
               name = reader.getLocalName(); 
               switch (name) {
                  case "teiHeader":
                     // Finished with processing the header; let's bail.
                     return;
                  case "title":
                  case "author":
                  case "witness":
                     // Metadata which get stored directly rather than as an annotation.
                     break;
                  default:
                     // Other metadata which should be stored as annotations.
                     if (!pendingMetadata.isEmpty()) {
                        Annotation ann = pendingMetadata.pop();
                        if (ann.getContent() == null && ann.getAttributes() == null) {
                           // No content or attributes.  It's just one of those structural tags we don't care about.
                           LOG.log(Level.INFO, "Removing {0} because it's empty.", ann);
                           importedAnnotations.remove(ann);
                        }
                     }
                     if (context.endsWith(name + ".")) {
                        context = context.substring(0, context.length() - name.length() - 1);
                     }
                     break;
               }
               break;
         }
      }
   }

   /**
    * Process the facsimile section of a TEI document.
    * 
    * This code is based on the structure from Jeffrey Witt's Petrus Plaoul manuscripts.
    * Jeffrey defines a separate surface for each paragraph on a page.  Tradamus treats each
    * graphic as a canvas, and notes the coordinates of the paragraphs.
    * 
    * We will likely need additional work to support all flavours of TEI.
    */
   private void processFacsimile(String imageBase) throws XMLStreamException, URISyntaxException {
      surfaces = new ArrayList<>();
      Map<String, List<SurfaceDef>> graphicSurfaces = new LinkedHashMap<>();
      String curGraphic = null;
      SurfaceDef curSurf = null;
      
      while (reader.hasNext()) {
         switch (reader.next()) {
            case XMLStreamConstants.START_ELEMENT:
               switch (reader.getLocalName()) {
                  case "graphic":
                     curGraphic = imageBase + reader.getAttributeValue(null, "url");
                     if (!graphicSurfaces.containsKey(curGraphic)) {
                        graphicSurfaces.put(curGraphic, new ArrayList<SurfaceDef>());
                     }
                     break;
                  case "surface":
                     curSurf = new SurfaceDef(reader);
                     break;
               }
               break;
            case XMLStreamConstants.END_ELEMENT:
               switch (reader.getLocalName()) {
                  case "surface":
                     List<SurfaceDef> curSurfaces = graphicSurfaces.get(curGraphic);
                     if (curSurfaces != null) {
                        curSurfaces.add(curSurf);
                     } else {
                        throw new XMLStreamException("XML input error: <surface> tag without matching <graphic>.");
                     }
                     break;
                  case "facsimile":
                     // We're done.  Interpret the graphics we've encountered as canvasses.
                     for (Map.Entry<String, List<SurfaceDef>> entry: graphicSurfaces.entrySet()) {
                        Dimension canvSize = getCanvasSize(entry.getValue());
                        Canvas canv = new Canvas(manifest, canvSize.width, canvSize.height);
                        canv.setTitle(entry.getKey());
                        manifest.addCanvas(canv);
                        
                        // The Petrus Plaoul files don't include image dimensions, so we'll go out
                        // on a limb and guess that they're the same size as the images.
                        canv.addImage(new Image(canv, new URI(entry.getKey()), Image.Format.fromExtension(entry.getKey()), canvSize.width, canvSize.height));
                     
                        // Make sure the surfaces know which canvas they belong to.
                        for (SurfaceDef surf: entry.getValue()) {
                           surf.canvas = canv;
                        }
                        surfaces.addAll(entry.getValue());
                     }
                     return;
               }
               break;
         }
      }
   }


   private void processText() throws XMLStreamException, URISyntaxException {
      pendingPage = new Page(transcription);
      startOfLine = 0;

      while (reader.hasNext()) {
         switch (reader.next()) {
            case XMLStreamConstants.START_ELEMENT:
               String openingTag = reader.getLocalName();
               switch (openingTag) {
                  case "ab":
                  case "head":
                  case "p":
                     openAnnotation(openingTag);
                     collectingWhitespace++;
                     break;
                  case "lb":
                  case "cb":
                  case "pb":
                     break;
                  default:
                     openAnnotation(openingTag);
                     break;
               }
               break;
            case XMLStreamConstants.CHARACTERS:
               String t = reader.getText();
               if (collectingWhitespace <= 0) {
                  t = reader.getText().trim();
               }
               if (!t.isEmpty()) {
                  t = t.replaceAll("\\s+"," ");
                  pendingPage.appendText(t);
               }
               break;
            case XMLStreamConstants.END_ELEMENT:
               String closingTag = reader.getLocalName();
               switch (closingTag) {
                  case "ab":
                  case "head":
                  case "p":
                     closeAnnotation(closingTag);
                     collectingWhitespace--;
                     startLine();
                     break;
                  case "lb":
                  case "cb":
                     startLine();
                     break;
                  case "pb":
                     transcription.addPage(pendingPage);
                     pendingPage = new Page(transcription);
                     startOfLine = 0;
                     break;
                  case "text":
                     // Check that everything was closed off properly.
                     transcription.addPage(pendingPage);

                     if (!pendingAnnotations.isEmpty()) {
                        throw new XMLStreamException(String.format("XML input error: Tag <%s> not closed.", pendingAnnotations.peek().getType()));
                     }
                     // We're done!
                     return;
                  default:
                     closeAnnotation(closingTag);
                     break;
               }
               break;
         }
      }
   }

   private void openAnnotation(String openingTag) {
      if (checkForSurfaceChange()) {
         startOfLine = 0; 
      }
      Annotation ann = new Annotation(openingTag, null, pendingPage, pendingPage.getText().length());
      pendingAnnotations.push(ann);
      addAttributes(ann);
   }

   private void startLine() {
      int endOfLine = pendingPage.getText().length();
      Annotation l = pendingPage.createLine(startOfLine, endOfLine);
      importedAnnotations.add(l);
      pendingPage.appendText("\n");
      startOfLine = endOfLine + 1;
   }

   /**
    * If the element being read has any attributes, add them to the given Annotation.
    * @param ann annotation to which attributes are being added
    */
   private void addAttributes(Annotation ann) {
      if (reader.getAttributeCount() > 0) {
         Map<String, String> attrMap = new HashMap<>();
         for (int i = 0; i < reader.getAttributeCount(); i++) {
            attrMap.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
         }
         ann.setAttributes(attrMap);
      }
   }

   /**
    * Examine the <code>id</code> attribute of the element which is under the reader, and check to see if it
    * corresponds to a new surface.  If it does, we may need to insert a page-break by updating the pending
    * page.
    * 
    * @return <code>true</code> if the current annotation corresponds to a new surface/page
    */
   private boolean checkForSurfaceChange() {
      boolean result = false;
      if (surfaces != null) {
         String anchor = reader.getAttributeValue(null, "id");
         if (anchor != null) {
            anchor = "#" + anchor;
            for (SurfaceDef s: surfaces) {
               if (anchor.equals(s.name)) {
                  if (s.canvas != pendingPage.getCanvas()) {
                     if (pendingPage.getCanvas() != null) {
                        // Page already has a canvas.  So we must have landed on a new page.
                        transcription.addPage(pendingPage);
                        pendingPage = new Page(transcription);
                        result = true;
                     }
                     s.canvas.bindPage(pendingPage, null);
                     surfaces.remove(s);
                     break;
                  }
               }
            }
         }
      }
      return result;
   }

   /**
    * Close the current tag which is expected to be an annotation of some sort.
    * @param closingTag XML tag which is currently being closed
    * @throws XMLStreamException if the tags don't match
    */
   private void closeAnnotation(String closingTag) throws XMLStreamException {
      String workingTag = pendingAnnotations.peek().getType();
      if (workingTag.equals(closingTag)) {
         Annotation a = pendingAnnotations.pop();
         a.complete(pendingPage, pendingPage.getText().length());
         importedAnnotations.add(a);
      } else {
         throw new XMLStreamException(String.format("XML input error: Expecting </%s>, but found </%s>.", workingTag, closingTag));
      }
   }
   
   /**
    * The bounds of a canvas is based on the union of the bounds of all surfaces associated with
    * the image.
    *
    * @param surfaces list of SurfaceDefs associated with this canvas
    * @return the size of the canvas
    */
   private static Dimension getCanvasSize(List<SurfaceDef> surfaces) {
      if (surfaces.size() > 0) {
         Rectangle totalBounds = surfaces.get(0).bounds;
         for (int i = 1; i < surfaces.size(); i++) {
            totalBounds = totalBounds.union(surfaces.get(i).bounds);
         }
         return totalBounds.getSize();
      }
      // Apparently there are no surfaces associated with this canvas.
      return new Dimension();
   }

   /**
    * Provides information retrieved from a &lt;surface> element.
    */
   private static class SurfaceDef {
      String name;
      Rectangle bounds;
      Canvas canvas;

      /**
       * Construct a surface definition from the tag at which the reader is currently positioned.
       * @param reader XML reader which is currently processing a &lt;surface> tag
       */
      SurfaceDef(XMLStreamReader reader) {
         name = reader.getAttributeValue(null, "start");

         int x = Integer.parseInt(reader.getAttributeValue(null, "ulx"));
         int y = Integer.parseInt(reader.getAttributeValue(null, "uly"));
         int w = Integer.parseInt(reader.getAttributeValue(null, "lrx")) - x;
         int h = Integer.parseInt(reader.getAttributeValue(null, "lrx")) - y;
         bounds = new Rectangle(x, y, w, h);
      }
   }

   private static final Logger LOG = Logger.getLogger(XMLWitness.class.getName());   
}
