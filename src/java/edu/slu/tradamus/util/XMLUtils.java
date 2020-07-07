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
package edu.slu.tradamus.util;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.stream.XMLStreamReader;


/**
 * Various utility functions for dealing with XML.
 *
 * @author tarkvara
 */
public class XMLUtils {
   /**
    * Parse an XML tag, extracting the tag's name and attributes.
    * @param rawTag a raw tag like &lt;name attr="val">
    * @param attrMap map to be populated with attributes
    * @param relaxedHTML allow attribute values to be un-quoted
    * @return the tag's name ("name" in this case)
    */
   public static String parseTag(String rawTag, Map<String, String> attrMap, boolean relaxedHTML) {
      StringBuilder tag = new StringBuilder();
      StringBuilder attrName = new StringBuilder();
      StringBuilder attrVal = new StringBuilder();
      TagParsingState state = TagParsingState.LOOKING_FOR_NAME;
      char quoteChar = '"';
      for (int i = 0; i < rawTag.length(); i++) {
         char c = rawTag.charAt(i);
         switch (state) {
            case LOOKING_FOR_NAME:
               if (!Character.isWhitespace(c) && c != '<') {
                  state = TagParsingState.INSIDE_NAME;
                  i--;
               }
               break;
            case INSIDE_NAME:
               if (!Character.isWhitespace(c) && c != '>') {
                  tag.append(c);
               } else {
                  state = TagParsingState.LOOKING_FOR_ATTRIBUTE_NAME;
               }
               break;
            case LOOKING_FOR_ATTRIBUTE_NAME:
               if (!Character.isWhitespace(c)) {
                  state = TagParsingState.INSIDE_ATTRIBUTE_NAME;
                  i--;
               }
               break;
            case INSIDE_ATTRIBUTE_NAME:
               if (!Character.isWhitespace(c) && c != '=') {
                  attrName.append(c);
               } else {
                  state = TagParsingState.LOOKING_FOR_ATTRIBUTE_VALUE;
               }
               break;
            case LOOKING_FOR_ATTRIBUTE_VALUE:
               if (c == quoteChar) {
                  state = TagParsingState.INSIDE_ATTRIBUTE_VALUE;
               } else if (relaxedHTML) {
                  if (c == '\'') {
                     // Single-quotes being used around attribute value.
                     quoteChar = '\'';
                     state = TagParsingState.INSIDE_ATTRIBUTE_VALUE;
                  } else if (!Character.isWhitespace(c)) {
                     // No quotes around attribute value.
                     quoteChar = '\0';
                     state = TagParsingState.INSIDE_ATTRIBUTE_VALUE;
                     i--;
                  }
               }
               break;
            case INSIDE_ATTRIBUTE_VALUE:
               if (c != quoteChar && !(quoteChar == '\0' && (Character.isWhitespace(c) || c == '>'))) {
                  attrVal.append(c);
               } else {
                  // We've reached the end of the attribute.  Store the value in the map.
                  attrMap.put(attrName.toString(), attrVal.toString());
                  attrName.setLength(0);
                  attrVal.setLength(0);
                  state = TagParsingState.LOOKING_FOR_ATTRIBUTE_NAME;
                  
                  // Restore our default quote character.
                  quoteChar = '"';
               }
               break;
         }
      }
      return tag.toString();
   }

   /**
    * For debug purposes, dump all attributes of the current element being read.
    *
    * @param reader stream reader which is currently processing a START_ELEMENT event
    */
   public static Map<String, String> dumpAttributes(XMLStreamReader reader) {
      Map<String, String> result = new LinkedHashMap<>();
      int numAttrs = reader.getAttributeCount();
      for (int i = 0; i < numAttrs; i++) {
         String name = reader.getAttributeLocalName(i);
         String val = reader.getAttributeValue(i);
         result.put(name, val);
      }
      return result;
   }
   /**
    * Keeps track of state inside parseTag.
    */
   private enum TagParsingState {
      LOOKING_FOR_NAME,
      INSIDE_NAME,
      LOOKING_FOR_ATTRIBUTE_NAME,
      INSIDE_ATTRIBUTE_NAME,
      LOOKING_FOR_ATTRIBUTE_VALUE,
      INSIDE_ATTRIBUTE_VALUE
   }
}
