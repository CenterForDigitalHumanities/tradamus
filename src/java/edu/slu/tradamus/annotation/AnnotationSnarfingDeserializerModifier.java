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
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer;
import edu.slu.tradamus.witness.Witness;

/**
 * Modifier which observes Annotations as they're being deserialised, and snarfs them into the
 * importedAnnotations array.
 */
public class AnnotationSnarfingDeserializerModifier extends BeanDeserializerModifier {
   private JsonDeserializer<Annotation> annotationDeserializer;
   private JsonDeserializer<Witness> witnessDeserializer;
   private List<Annotation> importedAnnotations = new ArrayList<>();

   @Override
   public JsonDeserializer<?> modifyDeserializer(DeserializationConfig dc, BeanDescription bd, JsonDeserializer<?> jd) {
      Class beanClass = bd.getBeanClass();
      if (beanClass == Annotation.class) {
         if (annotationDeserializer == null) {
            annotationDeserializer = new AnnotationSnarfingDeserializer(jd);
         }
         return annotationDeserializer;
      } else if (beanClass == Witness.class) {
         if (witnessDeserializer == null) {
            witnessDeserializer = new WitnessSnarfingDeserializer(jd);
         }
         return witnessDeserializer;
      } else {
         return jd;
      }
   }

   private class AnnotationSnarfingDeserializer extends JsonDeserializer<Annotation> implements ResolvableDeserializer {
      private final JsonDeserializer baseDeserializer;

      public AnnotationSnarfingDeserializer(JsonDeserializer bd) {
         baseDeserializer = bd;
      }

      @Override
      public Annotation deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
         Annotation ann = (Annotation)baseDeserializer.deserialize(jp, dc);
         importedAnnotations.add(ann);
         return ann;
      }

      @Override
      public void resolve(DeserializationContext dc) throws JsonMappingException {
         ((ResolvableDeserializer)baseDeserializer).resolve(dc);
      }
   }

   private class WitnessSnarfingDeserializer extends JsonDeserializer<Witness> implements ResolvableDeserializer {
      private final JsonDeserializer baseDeserializer;

      public WitnessSnarfingDeserializer(JsonDeserializer bd) {
         baseDeserializer = bd;
      }

      @Override
      public Witness deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
         Witness wit = (Witness)baseDeserializer.deserialize(jp, dc);
         wit.setImportedAnnotations(importedAnnotations);
         importedAnnotations = new ArrayList<>();
         return wit;
      }

      @Override
      public void resolve(DeserializationContext dc) throws JsonMappingException {
         ((ResolvableDeserializer)baseDeserializer).resolve(dc);
      }
   }
}
