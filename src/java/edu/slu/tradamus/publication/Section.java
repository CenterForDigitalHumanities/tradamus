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
package edu.slu.tradamus.publication;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.slu.tradamus.db.Entity;
import edu.slu.tradamus.edition.Outline;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;

/**
 * Class which represents a particular section of a publication.
 *
 * @author tarkvara
 */
public class Section extends Entity {

   /** Publication to which this section belongs. */
   private Publication publication;

   /** Human-friendly title of this section. */
   private String title;

   private Type type;
   
   private int index;

   private String template;
   
   private List<Rule> decoration = new ArrayList<>();
   
   private List<Rule> layout = new ArrayList<>();
   
   private List<Outline> sources = new ArrayList<>();

   /**
    * Constructor for wrapping a Section around an ID.
    * @param sectID ID of Section to be created
    */
   public Section(int sectID) {
      id = sectID;
   }

   /**
    * Constructor for deserialising from JSON
    */
   public Section() {
      type = Type.TEXT;
   }

   public int getIndex() {
      return index;
   }

   public void setIndex(int i) {
      index = i;
   }

   @JsonIgnore
   public Publication getPublication() {
      return publication;
   }

   @JsonIgnore
   public void setPublication(Publication pub) {
      publication = pub;
   }

   @JsonProperty("publication")
   public int getPublicationID() {
      return publication.getID();
   }

   @JsonProperty("publication")
   public void setPublicationID(int pubID) {
      if (publication == null || publication.getID() != pubID) {
         publication = new Publication(pubID);
      }
   }

   public String getTemplate() {
      return template;
   }

   public void setTemplate(String t) {
      template = t;
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String t) {
      title = t;
   }

   public Type getType() {
      return type;
   }

   public void setType(Type t) {
      type = t;
   }

   public List<Rule> getDecoration() {
      return decoration;
   }

   public void setDecoration(List<Rule> rules) {
      decoration = rules;
   }

   public List<Rule> getLayout() {
      return layout;
   }

   public void setLayout(List<Rule> rules) {
      layout = rules;
   }

   @JsonIgnore
   public List<Outline> getSources() {
      return sources;
   }

   @JsonIgnore
   public void setSources(List<Outline> outls) {
      sources = outls;
   }

   @JsonProperty("sources")
   public int[] getSourceIDs() {
      int[] result = new int[sources.size()];
      for (int i = 0; i < result.length; i++) {
         result[i] = sources.get(i).getID();
      }
      return result;
   }

   @JsonProperty("sources")
   public void setSourceIDs(List<Integer> ids) {
      sources = new ArrayList<>(ids.size());
      for (Integer i: ids) {
         sources.add(new Outline(i));
      }
   }

   /**
    * After deserialising a Section from JSON, Rules will be placed in the decoration or layout lists, but
    * won't necessarily have their types set properly.
    */
   public void fixRuleTypes() {
      for (Rule r: decoration) {
         r.setSection(this);
         r.setType(Rule.Type.DECORATION);
      }
      for (Rule r: layout) {
         r.setSection(this);
         r.setType(Rule.Type.LAYOUT);
      }
   }

   @Override
   public void insert(Connection conn) throws SQLException {
      executeInsert(conn, "INSERT INTO sections (" +
            "publication, title, type, template, `index`" +
            ") VALUES (?, ?, ?, ?, ?)",
            publication.getID(), title, type.toString(), template, index);
      
      for (Rule r: decoration) {
         r.setSection(this);
         r.setType(Rule.Type.DECORATION);
         r.insert(conn);
      }
      
      for (Rule r: layout) {
         r.setSection(this);
         r.setType(Rule.Type.LAYOUT);
         r.insert(conn);
      }

      for (Outline outl: sources) {
         executeInsert(conn, "INSERT INTO sources (section, outline) VALUES (?, ?)", id, outl.getID());
      }
   }

   @Override
   public void load(Connection conn, boolean deep) throws SQLException, ReflectiveOperationException {
      super.load(conn, deep);
      
      decoration = loadChildren(conn, SELECT_DECORATION_RULES, Rule.class, deep);
      layout = loadChildren(conn, SELECT_LAYOUT_RULES, Rule.class, deep);
      sources = loadChildren(conn, SELECT_SOURCES, Outline.class, deep);
   }

   @Override
   public void merge(Connection conn, Entity newEnt) throws IOException, SQLException, PermissionException, ReflectiveOperationException {
      Section newSect = (Section)newEnt;

      // Update top-level fields.
      if (!Objects.equals(title, newSect.title) || type != newSect.type || index != newSect.index || !Objects.equals(template, newSect.template)) {
         title = newSect.title;
         type = newSect.type;
         index = newSect.index;
         template = newSect.template;
         executeUpdate(conn, "UPDATE sections " +
                 "SET title = ?, type = ?, `index` = ?, template = ?" +
                 "WHERE id = ?",
                 title, type.toString(), index, template, id);
      }
      newSect.id = id;
      newSect.publication = publication;

      // Merge our rules.
      mergeChildren(conn, decoration, newSect.decoration, Rule.getMergingComparator(), null, true);
      mergeChildren(conn, layout, newSect.layout, Rule.getMergingComparator(), null, true);
      
      // Merge our sources
      executeUpdate(conn, "DELETE FROM `sources` WHERE `section` = ?", id);
      try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO `sources` (`section`, `outline`) VALUES (?, ?)")) {
         stmt.setInt(1, id);
         for (Outline outl: newSect.sources) {
            stmt.setInt(2, outl.getID());
            stmt.executeUpdate();
         }
      }
      sources = newSect.sources;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Role checkPermission(Connection conn, int uID, Role required) throws SQLException, PermissionException {
      Role r = executeGetPermission(conn, "SELECT role FROM permissions " +
              "LEFT JOIN sections ON publication = permissions.target " +
              "WHERE sections.id = ? AND (user = ? OR user = 0) AND target_type = 'PUBLICATION'", uID);
      if (r == null || r.ordinal() < required.ordinal()) {
         throw new PermissionException(this, required);
      }
      return r;
   }


   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareCall("UPDATE publications " +
              "LEFT JOIN sections ON publication = publications.id " +
              "SET modification = NOW() " +
              "WHERE sections.id = ?")) {
         stmt.setInt(1, id);
         stmt.executeUpdate();
      }
   }
   
   private static final String SELECT_DECORATION_RULES = "SELECT * FROM rules " +
           "WHERE type = 'DECORATION' AND section = ?";

   private static final String SELECT_LAYOUT_RULES = "SELECT * FROM rules " +
           "WHERE type = 'LAYOUT' AND section = ?";

   private static final String SELECT_SOURCES = "SELECT * FROM sources " +
           "LEFT JOIN outlines ON outline = outlines.id " +
           "WHERE section = ?";

   public static Comparator<Section> getMergingComparator() {
      return new Comparator<Section>() {
         /**
          * Sections are judged to be mergeable based on their index within the publication.
          *
          * @param o1 first section to compare
          * @param o2 second section to compare
          * @return 0 if the two sections are deemed equivalent for the purposes of merging
          */
         @Override
         public int compare(Section o1, Section o2) {
            if (o1.id == o2.id) {
               return 0;
            }
            return o1.getIndex() - o2.getIndex();
         }
      };
   }

   /**
    * Possible section types.
    */
   public enum Type {
      TEXT,
      ENDNOTE,
      FOOTNOTE,
      INDEX,
      TABLE_OF_CONTENTS
   }
}
