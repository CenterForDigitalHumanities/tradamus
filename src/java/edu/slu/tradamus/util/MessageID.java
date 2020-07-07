/*
 * Copyright 2015 Saint Louis University. Licensed under the
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

/**
 * Keeps track of all the message IDs available to the program.
 * @author tarkvara
 */
public enum MessageID {
   // Collation-related
   COLLATION_COMPLETE,
   DEFERRED_COLLATION,
   
   // PDF-related
   DEFERRED_PDF,

   // User-related
   CONFIRMATION_SUCCESSFUL,
   INVITATION,
   REVIEW_INVITATION
}
