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
package edu.slu.tradamus.db;

import edu.slu.tradamus.user.PermissionException;

/**
 * Interface which allows us to check permissions during an <code>Entity.mergeChildren</code> call so that
 * the merge only modifies children which we have permission to modify.
 *
 * @author tarkvara
 */
public interface PermissionFilter<T extends Entity> {
   /**
    * Determine whether we have permission to modify the given entity.
    *
    * @param t entity to be checked
    * @return <code>true</code> if modification is permitted
    * @throws PermissionException if the implementation decides that the permission problem warrants aborting the whole transaction
    */
   public boolean canModify(T t) throws PermissionException;
}
