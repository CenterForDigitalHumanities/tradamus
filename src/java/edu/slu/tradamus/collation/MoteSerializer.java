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
package edu.slu.tradamus.collation;

import edu.slu.tradamus.annotation.*;
import java.io.IOException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Json serialiser which only writes out the relevant fields for a raw mote annotation.
 * 
 * @author tarkvara
 */
public class MoteSerializer extends JsonSerializer<Annotation> {

   @Override
   public void serialize(Annotation ann, JsonGenerator jGen, SerializerProvider prov) throws IOException {
      jGen.writeStartObject();
      jGen.writeStringField("type", "tr-mote");
      jGen.writeNumberField("startPage", ann.getStartPage().getID());
      jGen.writeNumberField("startOffset", ann.getStartOffset());
      jGen.writeNumberField("endPage", ann.getEndPageID());
      jGen.writeNumberField("endOffset", ann.getEndOffset());
      jGen.writeStringField("content", ann.getContent());
      jGen.writeStringField("target", "#" + ann.getTargetFragment());
      jGen.writeEndObject();
   }
}
