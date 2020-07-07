/*
 * Copyright 2013-2014 Saint Louis University. Licensed under the
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
package edu.slu.tradamus.user;

import edu.slu.tradamus.db.Entity;


/**
 * Exception which is thrown when a user doesn't have permissions to view a particular resource.
 *
 * @author tarkvara
 */
public class PermissionException extends Exception {
   public PermissionException(Entity ent, Role r) {
      super(String.format("User does not have %s permission for %s", r, ent.getUniqueID()));
   }
}
