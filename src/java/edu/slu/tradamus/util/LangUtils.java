/*
 * Copyright 2013 Saint Louis University. Licensed under the
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

import java.io.FileNotFoundException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Various utility methods to fill in for limitations in the Java language.  Similar in purpose to the
 * Apache Commons Lang3 library.
 *
 * @author tarkvara
 */
public class LangUtils {

   /**
    * Use reflection to get the names of all settable fields associated with this class.  We
    * recognise a field X by the presence of a setX() method in the class.  Alternatively, we can use the
    * JsonProperty annotation to indicate the JSON name of a particular setter.
    *
    * @return 
    */
   public static Map<String, Method> getSetters(Class clazz) {
      Map<String, Method> result = new HashMap<>();
      Method[] methods = clazz.getMethods();
      for (Method m: methods) {
         String methName = m.getName();
         if (methName.startsWith("set") && m.getParameterTypes().length == 1) {
            String colName;
            Annotation[] annots = m.getDeclaredAnnotations();
            if (annots.length == 0 || !(annots[0] instanceof JsonIgnore)) {
               if (annots.length > 0 && annots[0] instanceof JsonProperty) {
                  colName = ((JsonProperty)annots[0]).value();
               } else {
                  StringBuilder buf = new StringBuilder();
                  for (int i = 3; i < methName.length(); i++) {
                     char c = methName.charAt(i);
                     if (Character.isLowerCase(c)) {
                        buf.append(methName.substring(i));
                        break;
                     }
                     buf.append(Character.toLowerCase(c));
                  }
                  colName = buf.toString();
               }
               result.put(colName, m);
            }
         }
      }
      return result;
   }

   /**
    * Utility function to convert a camel-case string like "startOffset" to a underscore-separated string
    * like "start_offset".  This does not currently copy with strings like getHTTPServer, which would be
    * mis-expanded as "get_h_t_t_p_server".
    * @param s camel-case string
    * @return equivalent string with word elements separated by underscores
    */
   public static String camelCaseToUnderscores(String s) {
      for (int i = 1; i < s.length(); i++) {
         char c = s.charAt(i);
         if (Character.isUpperCase(c)) {
            s = s.substring(0, i) + "_" + Character.toLowerCase(c) + s.substring(i + 1);
         }
      }
      return s;
   }

   public static String elideString(String s, int len) {
      if (s.length() <= len) {
         return s;
      }
      return s.substring(0, len) + "...";
   }

   /**
    * Sometimes Throwable.getMessage() returns a useless string (e.g. "null" for a NullPointerException),
    * and we want to return a string which is more meaningful to the end-user.
    */
   public static String getMessage(Throwable t) {
      if (t instanceof NullPointerException) {
         return "Null pointer exception";
      } else if (t instanceof FileNotFoundException) {
         return String.format("File %s not found", t.getMessage());
      } else if (t instanceof ArrayIndexOutOfBoundsException) {
         return "Array index out of bounds: " + t.getMessage();
      } else if (t instanceof OutOfMemoryError) {
         return "Out of memory: " + t.getMessage();
      } else if (t instanceof NumberFormatException) {
         String msg = t.getMessage();
         int quotePos = msg.indexOf('\"');
         if (quotePos > 0) {
            // Exception message is of form "For input string: \"foo\"".
            return String.format("Unable to interpret %s as a number", msg.substring(quotePos));
         }
         return msg;
      } else {
         if (t.getMessage() != null) {
            return t.getMessage();
         } else {
            return t.toString();
         }
      }
   }

   /**
    * Utility function for creating and populating a map using var-args.
    * @param args list of key/value pairs
    * @return a map suitable for serialising to JSON
    */
   public static Map<String, Object> buildQuickMap(Object... args) {
      Map<String, Object> result = new LinkedHashMap<>();
      for (int i = 0; i < args.length; i += 2) {
         result.put(args[i].toString(), args[i + 1]);
      }
      return result;
   }
   
   public static final Charset UTF8 = Charset.forName("UTF-8");
}
