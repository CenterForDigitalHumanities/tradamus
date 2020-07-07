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

import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.xml.stream.XMLStreamException;
import com.fasterxml.jackson.core.type.TypeReference;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.db.NoSuchEntityException;
import edu.slu.tradamus.edition.Edition;
import edu.slu.tradamus.image.Canvas;
import edu.slu.tradamus.image.Image;
import edu.slu.tradamus.text.Page;
import static edu.slu.tradamus.util.JsonUtils.*;



/**
 * Witness which is built from IIIF metadata stored as JSON-LD.
 *
 * @author tarkvara
 */
public class JsonLDWitness extends Witness {

   /**
    * Keeps track of which prefixes are defined in the context, and need to be expanded.
    */
   private final Map<String, String> prefixes = new HashMap<>();

   /**
    * Keeps track of the JSON-LD "on" entries for all annotations so we can resolve them later.
    */
   private final Map<Annotation, List> importedOns = new HashMap<>();

   /**
    * Keeps track of the JSON-LD canvasses and pages so we can resolve the "on" entries from importedOns.
    */
   private final Map<String, Entity> importedTargets = new HashMap<>();

   /**
    * Instantiate a witness from a stream of JSON-LD data.
    * @param ed Edition to which this witness will belong
    * @param input servlet's input stream
    * @param tpenURI if non-null, link to T-PEN repository
    * @throws IOException 
    */
   public JsonLDWitness(Edition ed, InputStream input, String tpenURI) throws IOException, XMLStreamException, NoSuchEntityException {
      super(ed, tpenURI);
      
      // Process the @context object.
      Map<String, Object> root = getObjectMapper().readValue(input, new TypeReference<Map<String, Object>>() {});
      loadContext(root.get("@context"));
      root.remove("@context");
      expandAllPrefixes(root);

      // Load the pages containing canvas and annotation data.  We only care about 1 sequence.
      List<Object> sequences = getArray(root, SEQUENCES, true);
      if (sequences.size() >= 1) {
         Object sequence = sequences.get(0);
         if (sequence != null) {
            if (!(sequence instanceof Map)) {
               throw new IOException("Malformed JSON-LD input: first sequence entry was not an object.");
            }
            loadSequence((Map<String, Object>)sequence);
         }
      }

      // Load the associations between Tradamus pages and Tradamus canvasses.
      List<Object> structures = getArray(root, STRUCTURES, false);
      if (structures != null) {
         for (Object str: structures) {
            loadStructure((Map<String, Object>)str);
         }
      }

      // Make sure any imported annotations have appopriate targets.
      attachAnnotations();

      // Once we've loaded all the lines, we have to do secondary processing to extract any embedded XML
      // tags.  As a side-effect, this may adjust any line offsets/lengths and page lengths.
      importedAnnotations.addAll(transcription.extractTagAnnotations());

//      uri = new URI((String)root.get("@id"));
      // This format doesn't have a siglum per se, so label is the closest we've got.
      siglum = (String)root.get(LABEL);
      title = (String)root.get(DESCRIPTION);
      if (title == null) {
         title = siglum;
      }
      
      // Remaining strings are treated as uninterpreted witness-level metadata.
   }

   /**
    * Load the prefixes defined in the file's context entry.  These will be used
    * later in <code>expandPrefixes()</code>.
    * @param context a map containing the JSON deserialisation of the context object
    * @throws IOException if the context is missing or malformed
    */
   private void loadContext(Object contObj) throws IOException {
      if (contObj != null) {
         if (contObj instanceof Map) {
            // @context has been resolved (possibly stored inline).
            Map<String, Object> contMap = (Map<String, Object>)contObj;
            for (Map.Entry<String, Object> ent: contMap.entrySet()) {
               if (ent.getValue() instanceof String) {
                  prefixes.put(ent.getKey(), (String)ent.getValue());
               } else if (ent.getValue() instanceof Map) {
                  // May be a {"@type":"@id","@id":"foo:bar"} definition.
                  Map<String, Object> littleMap = (Map<String, Object>)ent.getValue();
                  Object littleValue = littleMap.get("@id");
                  if (littleValue != null && littleValue instanceof String) {
                     prefixes.put(ent.getKey(), (String)littleValue);
                  }
               }
            }
         } else if (contObj instanceof String) {
            // @context was an URL to a remote resource.  It is expected to be an anonymous object,
            // with one field, which contains either an @context object or an array of strings
            // and a context object.
            try (InputStream input = new URL((String)contObj).openStream()) {
               Map<String, Object> contMap = getObjectMapper().readValue(input, new TypeReference<Map<String, Object>>() {});
               loadContext(getObject(contMap, "@context", true));
            }
         } else if (contObj instanceof List) {
            // @context was an array of strings and/or objects.
            for (Object obj: (List)contObj) {
               loadContext(obj);
            }
         }
      }
   }

   /**
    * Given a key from a JSON file, expand any prefixes which have been defined in
    * the context.
    *
    * @param key the key which may need expanding
    * @return <code>key</code> with its prefix expanded
    */
   private String expandKey(String key) {
      String keyStr = (String)key;
      int colonPos = keyStr.indexOf(':');
      if (colonPos > 1) {
         String prefix = keyStr.substring(0, colonPos);
         String expansion = prefixes.get(prefix);
         if (expansion != null) {
            keyStr = expansion + keyStr.substring(colonPos + 1);
            return expandKey(keyStr);
         }
      } else {
         // An exact match for the key also gets expanded.
         if (prefixes.containsKey(keyStr)) {
            keyStr = prefixes.get(keyStr);
            return expandKey(keyStr);
         }
      }
      return keyStr;
   }

   /**
    * Traverse the entire structure which has been loaded from JSON, and expand any field names.
    * @param obj structure to be expanded
    */
   private void expandAllPrefixes(Object obj) throws IOException {
      if (obj instanceof Map) {
         Map<String, Object> map = (Map<String, Object>)obj;
         List<Map.Entry<String, Object>> entries = new ArrayList<>(map.entrySet());
         for (Map.Entry<String, Object> ent: entries) {
            String oldKey = ent.getKey();
            Object val = ent.getValue();
            String newKey = expandKey(oldKey);
            if (!newKey.equals(oldKey)) {
               map.put(newKey, val);
               map.remove(oldKey);
            }
            expandAllPrefixes(val);
         }
      } else if (obj instanceof List) {
         for (Object subObj: (List)obj) {
            expandAllPrefixes(subObj);
         }
      }
   }

   /**
    * Load the canvasses and importedAnnotations from the first sequence of the pages array.
    *
    * @param seq a List containing sc:Sequence objects
    * @throws IOException if the JSON input is malformed
    */
   private void loadSequence(Map<String, Object> seq) throws IOException, XMLStreamException {
      
      if (!SEQUENCE.equals(expandKey(getString(seq, "@type", true)))) {
         throw new IOException("Malformed JSON-LD input: @type of sequence was not sc:Sequence.");
      }
      
      // We now have a single array of canvasses.  We use to have separate first and rest entries.
      List<Object> canvasses = getArray(seq, CANVASES, false);
      for (Object o: canvasses) {
         if (!(o instanceof Map)) {
            throw new IOException("Malformed JSON-LD input: canvas entry is not an object.");
         }
         loadCanvas((Map<String, Object>)o);
      }
   }
   
   /**
    * Load a canvas along with its associated image and text annotation resources.
    * @param canvas map containing JSON data
    * @throws IOException 
    */
   private void loadCanvas(Map<String, Object> canvas) throws IOException {
      if (!CANVAS.equals(expandKey(getString(canvas, "@type", true)))) {
         throw new IOException("Malformed JSON-LD input: @type of canvas was not sc:Canvas.");
      }
      
      // Create the canvas which anchors the geometry for importedAnnotations.
      int h = -1, w = -1;
      if (canvas.containsKey(WIDTH)) {
         w = (Integer)canvas.get(WIDTH); // FIXME: java.lang.String cannot be cast to java.lang.Integer "height":"4889"
      }
      if (canvas.containsKey(HEIGHT)) {
         h = (Integer)canvas.get(HEIGHT);
      }
      Canvas canv = new Canvas(manifest, w, h);
      canv.setTitle((String)canvas.get(LABEL));
      importedTargets.put(getString(canvas, "@id", true), canv);
      
      List<Object> images = getArray(canvas, IMAGES, false);
      if (images != null) {
         for (Object o: images) {
            if (!(o instanceof Map)) {
               throw new IOException("Malformed JSON-LD input: images entry is not an object.");
            }
            loadImageAnnotation((Map<String, Object>)o, canv);
         }
      }

      Map<String, Annotation> lines = new LinkedHashMap<>();
      List<Annotation> notes = new ArrayList<>();
      List<Object> otherContent = getArray(canvas, OTHER_CONTENT, false);
      if (otherContent != null) {
         for (Object o: otherContent) {
            if (!(o instanceof Map)) {
               throw new IOException("Malformed JSON-LD input: otherContent entry is not an object.");
            }
            loadOtherContentAnnotationList((Map<String, Object>)o, canv, lines, notes);
         }
      }

      Page pg = new Page(transcription);
      canv.bindPage(pg, notes);
      manifest.addCanvas(canv);
      transcription.addPage(pg);
   }

   /**
    * Load an image annotation from the images section of the canvas.
    * @param ann image annotation
    * @param canv Canvas which is being built
    * @throws IOException 
    */
   private void loadImageAnnotation(Map<String, Object> ann, Canvas canv) throws IOException {
      Object type = expandKey(getString(ann, "@type", true));
      if (ANNOTATION.equals(type)) {
         try {
            Map<String, Object> resource = getObject(ann, RESOURCE, true);
            URI u = new URI(getString(resource, "@id", true));
            String fmt = getString(resource, FORMAT, true);
            int w = -1, h = -1;
            if (resource.containsKey(WIDTH)) {
               w = (Integer)resource.get(WIDTH);
            }
            if (resource.containsKey(HEIGHT)) {
               h = (Integer)resource.get(HEIGHT);
            }
            canv.addImage(new Image(canv, u, Image.Format.fromMimeType(fmt), w, h));
         } catch (URISyntaxException ex) {
            throw new IOException("Unable to parse image URI.");
         }
      }
   }

   /**
    * Load otherContent (i.e. non-image) annotations for the given canvas.  Typically, we expect to have an
    * annotation list containing a large number of lines and notes, all of which are text annotations.
    * 
    * @param annsAsMap map describing the annotation list to be loaded
    * @param canv canvas to which the resources will be attached
    * @param lines keeps track of lines we've added to this page, so we can hook up notes
    * @param notes keeps track of notes we've added to this page
    */
   private void loadOtherContentAnnotationList(Map<String, Object> annsAsMap, Canvas canv, Map<String, Annotation> lines, List<Annotation> notes) throws IOException {
      Object type = expandKey(getString(annsAsMap, "@type", true));
      if (ANNOTATION_LIST.equals(type)) {
         List<Object> resources = getArray(annsAsMap, RESOURCES, false);
         if (resources != null) {
            for (Object res: resources) {
               if (!(res instanceof Map)) {
                  throw new IOException("Malformed JSON-LD input: otherContent resource is not an object.");
               }
               loadOtherContentAnnotation((Map<String, Object>)res, canv, lines, notes);
            }
         }
      }
   }
   
   /**
    * Load one of the annotations from the otherContent annotation list.  These will all be either lines
    * or notes.
    * @param annAsMap annotation to be processed
    * @param canv canvas to which the resources will be attached
    * @param lines keeps track of lines we've added to this page, so we can hook up notes
    * @param notes keeps track of notes we've added to this page
    * @throws IOException 
    */
   private void loadOtherContentAnnotation(Map<String, Object> annAsMap, Canvas canv, Map<String, Annotation> lines, List<Annotation> notes) throws IOException {
      Object type = expandKey(getString(annAsMap, "@type", true));
      if (ANNOTATION.equals(type)) {
         Map<String, Object> resource = getObject(annAsMap, RESOURCE, false);
         String cont = null;
         if (resource != null) {
            type = expandKey(getString(resource, "@type", true));
            if (CONTENT_AS_TEXT.equals(type)) {
               cont = getString(resource, CHARS, false);
            } else {
               LOG.log(Level.WARNING, "Don't know what to do with {0} in otherContent resource.", type);
            }
         }
         String annID = getString(annAsMap, "@id", true);
         int linePos = annID.lastIndexOf(LINE);
         if (linePos >= 0) {
            String on = getString(annAsMap, ON, true);
            int hashPos = on.indexOf('#');
            String frag = hashPos >= 0 ? on.substring(hashPos + 1) : on;
            Annotation line = canv.createLine(cont, frag);
            importedAnnotations.add(line);
            lines.put(annID.substring(linePos + LINE.length()), line);
         } else {
            int notePos = annID.lastIndexOf(NOTE);
            if (notePos >= 0) {
               // We can only properly anchor the note if we've already extracted the line (no
               // forward references).
               String lineID = getString(annAsMap, ON, true);
               if (lineID != null) {
                  linePos = lineID.lastIndexOf(LINE);
                  if (linePos >= 0) {
                     Annotation line = lines.get(lineID.substring(linePos + LINE.length()));
                     if (line != null) {
                        Annotation note = new Annotation("note", cont, line, null);
                        note.setTarget(line);
                        importedAnnotations.add(note);
                        notes.add(note);
                     } else {
                        LOG.log(Level.WARNING, "For note {0}, unresolved line {1}", new Object[] { annID, lineID });
                     }
                  } else {
                     LOG.log(Level.WARNING, "Note {0} is not targetted on a line.", annID);
                  }
               } else {
                  LOG.log(Level.WARNING, "Note {0} is missing hasTarget entry.", annID);
               }
            } else {
               // Some other sort of annotation attached here.  Possibly a Tradamus line.
               Object ons = annAsMap.get(ON);
               if (ons != null) {
                  List onsList;
                  if (ons instanceof List) {
                     onsList = (List)ons;
                  } else {
                     onsList = Collections.singletonList(ons);
                  }
                  Annotation newAnn = new Annotation(getString(annAsMap, "type", true), cont, null, null);
                  importedAnnotations.add(newAnn);
                  importedOns.put(newAnn, onsList);
               } else {
                  throw new IOException(String.format("Malformed JSON-LD input: missing or invalid \"%s\" entry.", ON));
               }
            }
         }
      } else {
         LOG.log(Level.WARNING, "Malformed JSON-LD input: otherContent resource is not an annotation.");
      }
   }

   /**
    * Load a structure.  This is a JSON-LD sc:Range object which describes a page and associated annotations.
    * @param structAsMap
    * @throws IOException 
    */
   private void loadStructure(Map<String, Object> structAsMap) throws IOException {
      String atID = getString(structAsMap, "@id", true);
              
      if (!STRUCTURE.equals(expandKey(getString(structAsMap, "@type", true)))) {
         throw new IOException(String.format("Malformed JSON-LD input in structure %s: @type of structure was not sc:Range.", atID));
      }
      
      List<Object> canvases = getArray(structAsMap, CANVASES, true);
      if (canvases.size() < 1) {
         throw new IOException(String.format("Malformed JSON-LD input in structure %s: canvases array was empty.", atID));
      }
      String onCanvas = (String)canvases.get(0);
      if (!importedTargets.containsKey(onCanvas) || !(importedTargets.get(onCanvas) instanceof Canvas)) {
         throw new IOException(String.format("Malformed JSON-LD input in structure %s: %s was not a known canvas.", atID, onCanvas));
      }
      Canvas canv = (Canvas)importedTargets.get(onCanvas);
      Page p = canv.getPage();
      importedTargets.put(atID, p);
      
      String label = getString(structAsMap, LABEL, false);
      if (label != null) {
         p.setTitle(label);
      }

      List<Object> otherContent = getArray(structAsMap, OTHER_CONTENT, false);
      if (otherContent != null) {
         for (Object o: otherContent) {
            if (!(o instanceof Map)) {
               throw new IOException(String.format("Malformed JSON-LD input in structure %s: otherContent entry is not an object.", atID));
            }
            loadOtherContentAnnotationList((Map<String, Object>)o, canv, null, null);
         }
      }
   }

   /**
    * Go through our imported annotations and make sure everybody is targetted according to their "on" entries.  Important
    * for JSON-LD generated by Tradamus, but not used for JSON-LD brought in from T-PEN.
    */
   @SuppressWarnings("null")
   private void attachAnnotations() throws IOException, NoSuchEntityException {
      Map<Page, List<Annotation>> lines = new HashMap<>();
      for (Annotation ann: importedOns.keySet()) {
         List<Object> ons = importedOns.get(ann);
         
         for (Object o: ons) {
            String on = (String)o;
            int hashPos = on.indexOf('#');
            String frag = null;
            if (hashPos >= 0) {
               frag = on.substring(hashPos + 1);
               on = on.substring(0, hashPos);
            }
            Entity targ = importedTargets.get(on);
            if (targ != null) {
               // Simple case, an xywh fragment on a canvas.
               ann.setTarget(targ);
               ann.setCanvasURI(targ.getUniqueID() + "#" + frag);
            } else {
               // A text anchor.  The body will be a transcription and the fragment will be of the form startPage:startOffset-endPage:endOffset.
               try {
                  // The "on" string here will be a transcription.  We want to find the word "/transcription" and use it as the base
                  // for our page URIs.
                  int transcrPos = on.indexOf("/transcription");
                  on = on.substring(0, transcrPos);
                  String[] frags = frag.split("[:-]");
                  int startPageID, startOffset, endPageID, endOffset;
                  startPageID = Integer.parseInt(frags[0]);
                  startOffset = Integer.parseInt(frags[1]);
                  endPageID = Integer.parseInt(frags[2]);
                  endOffset = Integer.parseInt(frags[3]);

                  Page startPage = (Page)importedTargets.get(String.format("%s/page/%d", on, startPageID));
                  Page endPage = (Page)importedTargets.get(String.format("%s/page/%d", on, endPageID));
                  
                  ann.setTextAnchor(startPage, startOffset, endPage, endOffset);
                  if (ann.getType().equals("line")) {
                     if (startPageID != endPageID) {
                        throw new IOException(String.format("Malformed JSON-LD input: line extends across pages %d and %d", startPageID, endPageID));
                     }
                     if (!lines.containsKey(startPage)) {                        
                        lines.put(startPage, new ArrayList<Annotation>());
                     }
                     lines.get(startPage).add(ann);
                  }
               } catch (ArrayIndexOutOfBoundsException | ClassCastException | NumberFormatException | NullPointerException ex) {
                  throw new IOException(String.format("Malformed JSON-LD input: unable to interpret %s as a page range.", frag));
               }
            }
         }
      }
      
      // Now make sure all our pages have the associated text.
      for (Page p: lines.keySet()) {
         List<Annotation> pageLines = lines.get(p);
         Collections.sort(pageLines, new Comparator<Annotation>() {
            @Override
            public int compare(Annotation o1, Annotation o2) {
               return o1.getStartOffset() - o2.getStartOffset();
            }
         });
         int start = 0;
         for (Annotation line: pageLines) {
            String content = line.getContent();
            if (start != line.getStartOffset()) {
               // Start position of the text doesn't match what we're expecting.
               throw new IOException(String.format("Malformed JSON-LD input: line \"%s\" started at %d; was expecting %d.", content, line.getStartOffset(), start));
            }
            if (start + content.length() != line.getEndOffset()) {
               // End position of the text doesn't match what we're expecting.
               throw new IOException(String.format("Malformed JSON-LD input: line \"%s\" ended at %d; was expecting %d.", content, line.getEndOffset(), start + content.length()));
            }
            p.addLine(line);
            p.appendText(content + '\n');
            start += content.length() + 1;
         }
      }
   }

   private static final String IMAGE = "http://purl.org/dc/dcmitype/Image";
   private static final String DESCRIPTION = "http://purl.org/dc/elements/1.1/description";
   private static final String FORMAT = "http://purl.org/dc/elements/1.1/format";
   private static final String ANNOTATION_LIST = "http://www.shared-canvas.org/ns/AnnotationList";
   private static final String CANVAS = "http://www.shared-canvas.org/ns/Canvas";
   private static final String CANVASES = "http://www.shared-canvas.org/ns/hasCanvases";
   private static final String IMAGES = "http://www.shared-canvas.org/ns/hasImageAnnotations";
   private static final String OTHER_CONTENT = "http://www.shared-canvas.org/ns/hasLists";
   private static final String RESOURCES = "http://www.shared-canvas.org/ns/hasAnnotations";
   private static final String SEQUENCES = "http://www.shared-canvas.org/ns/hasSequences";
   private static final String SEQUENCE = "http://www.shared-canvas.org/ns/Sequence";        // In spec, but not in context.json
   private static final String STRUCTURES = "http://www.shared-canvas.org/ns/hasRanges";
   private static final String STRUCTURE = "http://www.shared-canvas.org/ns/Range";          // In spec, but not in context.json
   private static final String LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
   private static final String HEIGHT = "http://www.w3.org/2003/12/exif/ns#height";
   private static final String WIDTH = "http://www.w3.org/2003/12/exif/ns#width";
   private static final String CONTENT_AS_TEXT = "http://www.w3.org/2011/content#ContentAsText";
   private static final String CHARS = "http://www.w3.org/2011/content#chars";
   private static final String ANNOTATION = "http://www.w3.org/ns/oa#Annotation";
   private static final String RESOURCE = "http://www.w3.org/ns/oa#hasBody";
   private static final String ON = "http://www.w3.org/ns/oa#hasTarget";
   
   private static final String LINE = "/line/";
   private static final String NOTE = "/note/";

   private static final Logger LOG = Logger.getLogger(JsonLDWitness.class.getName());
}
