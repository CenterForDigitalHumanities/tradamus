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
package edu.slu.tradamus.annotation;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Json serialiser which only writes out the relevant fields for an annotation.
 * 
 * @author tarkvara
 */
public class SimplifiedAnnotationSerializer extends JsonSerializer<Annotation> {

   @Override
   public void serialize(Annotation ann, JsonGenerator jGen, SerializerProvider prov) throws IOException {
      jGen.writeStartObject();
      jGen.writeNumberField("id", ann.getID());
      jGen.writeStringField("type", ann.getType());
      jGen.writeNumberField("approvedBy", ann.getApprovedBy());
      jGen.writeNumberField("modifiedBy", ann.getModifiedBy());
      jGen.writeStringField("modification", ann.getModification());
      
      if (ann.getStartPage() != null) {
         jGen.writeNumberField("startPage", ann.getStartPage().getID());
         jGen.writeNumberField("startOffset", ann.getStartOffset());
         jGen.writeNumberField("endPage", ann.getEndPageID());
         jGen.writeNumberField("endOffset", ann.getEndOffset());
      }
      if (ann.getCanvas() != null) {
         jGen.writeStringField("canvas", ann.getCanvasURI());
      }
      if (ann.getTarget() != null) {
         jGen.writeStringField("target", ann.getTargetURI());
      }      
      jGen.writeStringField("content", ann.getContent());
      if (ann.getAttributes() != null) {
         jGen.writeObjectField("attributes", ann.getAttributes());
      }
      if (ann.getTags() != null) {
         jGen.writeStringField("tags", ann.getTags());
      }
      jGen.writeEndObject();
   }
}
