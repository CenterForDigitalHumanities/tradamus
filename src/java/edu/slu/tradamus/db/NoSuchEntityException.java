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
package edu.slu.tradamus.db;

import java.sql.SQLException;


/**
 * Exception thrown when we try to load an entity from the database, and it doesn't exist.
 *
 * @author tarkvara
 */
public class NoSuchEntityException extends SQLException {
   /**
    * Construct an exception indicating the the given entity was not found in the database.
    *
    * @param uri description of entity we were looking for.
    */
   public NoSuchEntityException(String uri) {
      super(String.format("Unable to find %s", uri));
   }

   /**
    * Construct an exception indicating the the given entity was not found in the database.
    *
    * @param tableName SQL table name
    * @param id entity ID
    */
   public NoSuchEntityException(Entity ent) {
      this(ent.getUniqueID());
   }
}
