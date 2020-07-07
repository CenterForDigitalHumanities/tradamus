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

import edu.slu.tradamus.db.PermissionFilter;
import edu.slu.tradamus.user.PermissionException;
import edu.slu.tradamus.user.Role;


/**
 * Filter which ensures that users with CONTRIBUTOR access can only modify their own annotations.
 *
 * @author tarkvara
 */
public class OwnAnnotationFilter implements PermissionFilter<Annotation> {
   private final int userID;

   /**
    * Construct a filter which only grants permission to modify the user's own annotations.
    * @param uID contributor's user ID
    */
   public OwnAnnotationFilter(int uID) {
      userID = uID;
   }

   /**
    * If we're not permitted to modify this annotation, abort the whole transaction.
    * @param t annotation whose access we're checking
    * @return true
    * @throws PermissionException if we are a contributor, and we're trying to modify another user's annotation
    */
   @Override
   public boolean canModify(Annotation t) throws PermissionException {
      if (t.getModifiedBy() != userID) {
         throw new PermissionException(t, Role.EDITOR);
      }
      return true;
   }
}
