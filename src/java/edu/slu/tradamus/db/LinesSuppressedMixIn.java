/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.slu.tradamus.db;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.slu.tradamus.annotation.Annotation;

/**
 * Json serialisation mixin which keeps lines from being serialised.  Used by the /page and /canvas
 * servlets.
 *
 * @author tarkvara
 */
public interface LinesSuppressedMixIn {
   @JsonIgnore
   List<Annotation> getLines();
}
