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
package edu.slu.tradamus.publication;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.slu.tradamus.db.Entity;
import static edu.slu.tradamus.db.Entity.executeUpdate;
import edu.slu.tradamus.user.PermissionException;

/**
 * Class which specifies a publication rule.
 *
 * @author tarkvara
 */
public class Rule extends Entity {

   /**
    * Section to which this rule belongs.
    */
   private Section section;

   /**
    * Either DECORATION or LAYOUT.
    */
   private Type type;

   /**
    * Selector which determines when this rule should apply.
    */
   private String selector;

   /**
    * Formatting action which is applied when determined by selector.
    */
   private String action;

   /**
    * Constructor which wraps a Rule object around an ID.
    *
    * @param rulID entity ID
    */
   public Rule(int rulID) {
      id = rulID;
   }

   /**
    * Niladic constructor for deserialising from JSON.
    */
   public Rule() {
   }

   @JsonIgnore
   public Section getSection() {
      return section;
   }

   @JsonIgnore
   public void setSection(Section sect) {
      section = sect;
   }

   @JsonProperty("section")
   public int getSectionID() {
      return section.getID();
   }

   @JsonProperty("section")
   public void setEditionID(int sectID) {
      if (section == null || section.getID() != sectID) {
         section = new Section(sectID);
      }
   }

   public Type getType() {
      return type;
   }

   public void setType(Type t) {
      type = t;
   }

   public String getSelector() {
      return selector;
   }

   public void setSelector(String sel) {
      selector = sel;
   }

   public String getAction() {
      return action;
   }

   public void setAction(String act) {
      action = act;
   }
   
   @Override
   public void insert(Connection conn) throws SQLException {
      executeInsert(conn, "INSERT INTO rules (" +
            "section, type, selector, action" +
            ") VALUES (?, ?, ?, ?)",
            section.getID(), type.toString(), selector, action);
   }

   @Override
   public void merge(Connection conn, Entity newEnt) throws SQLException, PermissionException, ReflectiveOperationException {
      Rule newRule = (Rule)newEnt;

      if (!Objects.equals(selector, newRule.selector) || !Objects.equals(action, newRule.action)) {
         selector = newRule.selector;
         action = newRule.action;
         executeUpdate(conn, "UPDATE `rules` SET `selector` = ?, `action` = ? WHERE `id` = ?", selector, action, id);
      }
      newRule.id = id;
   }

   @Override
   public void markTopLevelModified(Connection conn) throws SQLException {
      throw new UnsupportedOperationException("Rules can't be modified directly.");
   }
   
   public static Comparator<Rule> getMergingComparator() {
      return new Comparator<Rule>() {
         /**
          * Rules are judged to be mergeable based on their selector within the publication.
          *
          * @param o1 first rule to compare
          * @param o2 second rule to compare
          * @return 0 if the two rules are deemed equivalent for the purposes of merging
          */
         @Override
         public int compare(Rule o1, Rule o2) {
            if (o1.id == o2.id) {
               return 0;
            }
            int diff = o1.type.ordinal() - o2.type.ordinal();
            if (diff == 0) {
               diff = o1.selector.compareTo(o2.selector);
            }
            return diff;
         }
      };
   }

   public enum Type {
      DECORATION,
      LAYOUT
   }
}
