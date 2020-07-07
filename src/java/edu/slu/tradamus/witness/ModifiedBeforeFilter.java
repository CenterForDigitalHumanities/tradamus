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

package edu.slu.tradamus.witness;

import java.util.Date;
import edu.slu.tradamus.annotation.Annotation;
import edu.slu.tradamus.db.PermissionFilter;
import edu.slu.tradamus.user.PermissionException;

/**
 * Filter which makes sure that we don't touch annotations newer than the last T-PEN update.
 *
 * @author tarkvara
 */
public class ModifiedBeforeFilter implements PermissionFilter<Annotation> {
   private final Date cutoff;

   public ModifiedBeforeFilter(Date d) {
      cutoff = d;
   }
   @Override
   public boolean canModify(Annotation t) throws PermissionException {
      return !t.getModificationDate().after(cutoff);
   }   
}
