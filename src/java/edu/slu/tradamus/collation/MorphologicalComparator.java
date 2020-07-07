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
package edu.slu.tradamus.collation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import eu.interedition.collatex.Token;
import edu.slu.tradamus.util.LangUtils;

/**
 * Comparator for collating tokens using morphological data from the Tufts Morphology service.
 *
 * @author tarkvara
 */
public class MorphologicalComparator implements Comparator<Token> {

   /** Parses output of morphology service. */
   private XMLStreamReader reader;

   /** For extracting the entry from an rdf:about attribute. */
   private final Pattern rdfAboutPattern = Pattern.compile("urn:TuftsMorphologyService:(.*):morpheuslat");

   /** Morphemes looked up from Perseus. */
   private final Map<String, List<String>> morphemes = new HashMap<>();

   public MorphologicalComparator() {
   }
   
   /**
    * Assemble the contents of our witnesses into a request for the Perseus morphology service, and process
    * the results.
    * 
    * @param wits witnesses being collated
    * @throws IOException
    * @throws XMLStreamException 
    */
   public void loadMorphology(List<CollationWitness> wits) throws IOException, XMLStreamException {
      // Build a map containing all the tokens we want to look up.
      Set<String> lookups = new HashSet<>();
      for (CollationWitness wit: wits) {
         Iterator<Token> tokenIter = wit.iterator();
         while (tokenIter.hasNext()) {
            lookups.add(((CollationToken)tokenIter.next()).getText());
         }
      }
      
      LOG.log(Level.INFO, "Preparing {0} tokens to Perseus for processing.", lookups.size());
      
      Iterator<String> lookupIter = lookups.iterator();
      for (int i = 0; i < lookups.size(); i += PERSEUS_CHUNK) {
         URL morphURL = new URL("http://services.perseids.org/bsp/morphologyservice/analysis/text");
         HttpURLConnection morphConn = (HttpURLConnection)morphURL.openConnection();
         morphConn.setRequestMethod("POST");
         morphConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
         morphConn.setDoOutput(true);

         OutputStream contStrm = morphConn.getOutputStream();
         contStrm.write("lang=lat&engine=morpheuslat&mime_type=text/plain&wait=true&text=".getBytes(LangUtils.UTF8));
      
         for (int j = 0; j < PERSEUS_CHUNK && lookupIter.hasNext(); j++) {
            byte[] tokBytes = lookupIter.next().getBytes(LangUtils.UTF8);
            contStrm.write(tokBytes);
            contStrm.write(' ');
         }
         contStrm.flush();

         morphConn.connect();
      
         parsePerseusOutput(morphConn.getInputStream());
         LOG.log(Level.INFO, "{0} morphemes received from Perseus.", morphemes.size());
      }
   }

   /**
    * Parse the output we get back from the Perseus morphology service.  It's a big mass of OAC-style XML.
    * The results for each word come back as an OAC:Annotation tag, containing a number of dict entries.
    * Within the dict entries, we are currently only interested in the hdwd tags.
    * @param input stream of XML output from Perseus
    * @throws XMLStreamException 
    */
   private void parsePerseusOutput(InputStream input) throws XMLStreamException {
      XMLInputFactory factory = XMLInputFactory.newInstance();
      reader = null;

      try {
         reader = factory.createXMLStreamReader(input);
         String entry = null;
         List<String> headwords = new ArrayList<>();
         while (reader.hasNext()) {
            switch (reader.next()) {
               case XMLStreamConstants.START_ELEMENT:
                  switch (reader.getLocalName()) {
                     case "Annotation":
                        entry = processEntry();
                        break;
                     case "hdwd":
                        if (entry != null) {
                           processHeadword(headwords);
                        }
                        break;
                     default:
                        LOG.log(Level.FINE, "Ignoring {0}", reader.getLocalName());
                        break;
                  }
                  break;
               case XMLStreamConstants.END_ELEMENT:
                  switch (reader.getLocalName()) {
                     case "Annotation":
                        if (headwords.size() > 0) {
                           morphemes.put(entry, headwords);
                           headwords = new ArrayList<>();
                        }
                        entry = null;
                        break;
                  }
            }
         }
      } finally {
         if (reader != null) {
            reader.close();
            reader = null;
         }
      }
   }

   /**
    * Process an oac:Annotation element which represents a single looked-up entry.
    *
    * @return the entry being processed
    */
   private String processEntry() {
      String about = reader.getAttributeValue(null, "about");
      Matcher matcher = rdfAboutPattern.matcher(about);
      if (matcher.find()) {
         return matcher.group(1);
      }
      return null;
   }

   /**
    * Process a &lt;hdwd> element for the current entry.
    * @param results headwords being accumulated for the current entry
    */
   private void processHeadword(List<String> results) throws XMLStreamException {
      results.add(reader.getElementText());
   }

   /**
    * This comparator compares to tokens based on their content.
    * @param tok1 first token to be compared
    * @param tok2 second token to be compared
    */
   @Override
   public int compare(Token tok1, Token tok2) {
      int result = ((CollationToken)tok1).text.compareTo(((CollationToken)tok2).text);
      if (result != 0) {
         // Text match failed, so let's try morpheme match.
         List<String> morph1 = morphemes.get(((CollationToken)tok1).text);
         List<String> morph2 = morphemes.get(((CollationToken)tok2).text);
         if (morph1 != null && morph2 != null && !Collections.disjoint(morph1, morph2)) {
            result = 0;
         }
      }
      return result;
   }

   /**
    * If we send too much to Perseus in one request, it blows up with a 500 error. Limit our calls to
    * 1000 tokens at a time.
    */
   private static final int PERSEUS_CHUNK = 1000;

   private static final Logger LOG = Logger.getLogger(MorphologicalComparator.class.getName());
}
