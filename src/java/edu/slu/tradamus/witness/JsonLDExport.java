/*
 * Copyright 2015 Saint Louis University. Licensed under the
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
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.ServletException;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.image.Canvas;
import edu.slu.tradamus.image.Image;
import edu.slu.tradamus.text.Page;
import static edu.slu.tradamus.util.LangUtils.buildQuickMap;
import static edu.slu.tradamus.util.ServletUtils.getDBConnection;

/**
 * Class for exporting a Tradamus witness to JSON-LD.  Builds a Map containing the
 * Witness' data, suitable for serialising to JSON.
 *
 * @author tarkvara
 */
public class JsonLDExport extends LinkedHashMap<String, Object> {

   /**
    * Witness being exported.
    */
   private final Witness witness;

   /**
    * Responsible for properly formatting annotation attributes.
    */
   private final ObjectMapper objectMapper = new ObjectMapper();

   /**
    * Populate a map which will contain all the relevant project information.
    *
    * @param serverURL URL of Tradamus server, used for making resource URIs
    * @param wit the project to be exported
    */
   public JsonLDExport(URL serverURL, Witness wit) throws SQLException, IOException, ServletException, ReflectiveOperationException {
      witness = wit;

      String hostPrefix = serverURL.toString() + "/";
      String witName = hostPrefix + wit.getUniqueID();
      put("@context", "http://www.shared-canvas.org/ns/context.json");
      put("@id", witName + "/manifest.json");
      put("@type", "sc:Manifest");
      put("label", wit.getSiglum());
      put("description", wit.getTitle());
      
      try (Connection conn = getDBConnection()) {
         List<Annotation> witAnns = wit.loadAllAnnotations(conn);
         witAnns.removeAll(wit.getMetadata());

         List<Map<String, Object>> metadata = new ArrayList<>();
         for (Annotation md: wit.getMetadata()) {
            metadata.add(buildQuickMap("label", md.getType(), "value", md.getContent()));
         }
         put("metadata", metadata);

         Map<String, Object> sequence = new LinkedHashMap<>();
         sequence.put("@id", witName + "/sequence/normal");
         sequence.put("@type", "sc:Sequence");
         sequence.put("label", "Current Page Order");

         List<Map<String, Object>> canvasses = new ArrayList<>();
         for (Canvas c: wit.getManifest().getCanvasses()) {
            canvasses.add(buildCanvas(hostPrefix, c, witAnns));
         }
         sequence.put("canvases", canvasses);      
         put("sequences", new Object[] { sequence });

         // Now add the transcription pages.
         List<Map<String, Object>> pages = new ArrayList<>();
         for (Page p: wit.getTranscription().getPages()) {
            pages.add(buildPage(hostPrefix, p, witAnns));
         }
         // Any leftover annotations also get their own ranges.
         if (pages.size() > 0) {
            put("structures", pages);
         }
      }
   }

   /**
    * Get the map which contains the serialisable information for the given canvas.
    *
    * @param hostPrefix host name with trailing slash
    * @param canv the canvas to be exported
    * @param witAnns all annotations for the witness
    * @return a map containing the relevant info, suitable for going into the canvases section of a sequence
    */
   private Map<String, Object> buildCanvas(String hostPrefix, Canvas canv, List<Annotation> witAnns) throws IOException {

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("@id", hostPrefix + canv.getUniqueID());
      result.put("@type", "sc:Canvas");
      result.put("label", canv.getTitle());
      result.put("height", canv.getHeight());
      result.put("width", canv.getWidth());

      List<Object> images = new ArrayList<>();
      for (Image im: canv.getImages()) {
         Map<String, Object> imageAnnot = new LinkedHashMap<>();
         String imID = hostPrefix + im.getUniqueID();
         imageAnnot.put("@id", imID);
         imageAnnot.put("@type", "oa:Annotation");
         imageAnnot.put("motivation", "sc:painting");
      
         Map<String, Object> imageResource = buildQuickMap("@id", imID, "@type", "dctypes:Image", "format", im.getFormat().toMimeType());
         imageResource.put("height", im.getHeight());
         imageResource.put("width", im.getWidth());
         imageAnnot.put("resource", imageResource);

         imageAnnot.put("on", hostPrefix + canv.getUniqueID());
         images.add(imageAnnot);
      }
      result.put("images", images);
      
      List<Map<String, Object>> resources = null;
      for (int i = 0; i < witAnns.size(); i++) {
         Annotation ann = witAnns.get(i);
         if (ann.getCanvas() != null && ann.getCanvas().getID() == canv.getID()) {
            witAnns.remove(i);
            i--;
            if (resources == null) {
               resources = new ArrayList<>();
            }
            Map<String, Object> annResource = buildAnnotation(hostPrefix, ann);
            if (ann.getStartPage() != null) {
               annResource.put("on", new Object[] { hostPrefix + ann.getCanvasURI(), hostPrefix + witness.getTranscription().getUniqueID() + ann.getTranscriptionFragment() });
            } else {
               annResource.put("on", hostPrefix + ann.getCanvasURI());
            }
            resources.add(annResource);
         }
      }
      if (resources != null) {
         Map<String, Object> otherContent = buildQuickMap("@id", hostPrefix + canv.getUniqueID(), "@type", "sc:AnnotationList", "resources", resources);
         result.put("otherContent", new Object[] { otherContent });
      }
      return result;
   }
   
   /**
    * Get the map which contains the serialisable information for the given page.
    *
    * @param hostPrefix host name with trailing slash
    * @param pg the page to be exported
    * @param witAnns all annotations for the witness
    * @return a map containing the relevant info, suitable for going into the canvases section of a sequence
    */
   private Map<String, Object> buildPage(String hostPrefix, Page pg, List<Annotation> witAnns) throws IOException {

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("@id", hostPrefix + pg.getUniqueID());
      result.put("@type", "sc:Range");
      result.put("label", pg.getTitle());

      List<Map<String, Object>> resources = null;
      for (int i = 0; i < witAnns.size(); i++) {
         Annotation ann = witAnns.get(i);
         if (ann.getStartPage() != null && ann.getStartPage().getID() == pg.getID() && ann.getEndPage().getID() == pg.getID()) {
            witAnns.remove(i);
            i--;
            if (resources == null) {
               resources = new ArrayList<>();
            }
            Map<String, Object> annResource = buildAnnotation(hostPrefix, ann);
            annResource.put("on", hostPrefix + witness.getTranscription().getUniqueID() + ann.getTranscriptionFragment());
            resources.add(annResource);
         }
      }
      if (resources != null) {
         Map<String, Object> otherContent = buildQuickMap("@id", hostPrefix + pg.getUniqueID(), "@type", "sc:AnnotationList", "resources", resources);
         result.put("otherContent", new Object[] { otherContent });
      }
      if (pg.getCanvas() != null) {
         result.put("canvases", new Object[] { hostPrefix + pg.getCanvas().getUniqueID() });
      }
      
      return result;
   }

   private Map<String, Object> buildAnnotation(String hostPrefix, Annotation ann) throws IOException {
      Map<String, Object> annResource = buildQuickMap("@id", hostPrefix + ann.getUniqueID(), "@type", "oa:Annotation");
      annResource.put("motivation", ann.getMotivation());
      annResource.put("annotatedAt", ann.getModificationDate());
      annResource.put("annotatedBy", hostPrefix + "user/" + ann.getModifiedBy());
      annResource.put("moderatedBy", hostPrefix + "user/" + ann.getApprovedBy());
      if (ann.getContent() != null) {
         annResource.put("resource", buildQuickMap("@type", "cnt:ContentAsText", "chars", ann.getContent()));
      }
      
      // Extra fields which aren't part of OA standard.
      annResource.put("type", ann.getType());
      if (ann.getAttributes() != null) {
         annResource.put("attributes", ann.getAttributes());
      }
      if (ann.getTags() != null) {
         annResource.put("tags", ann.getTags());
      }
      return annResource;
   }

   private static final Logger LOG = Logger.getLogger(JsonLDExport.class.getName());
}
