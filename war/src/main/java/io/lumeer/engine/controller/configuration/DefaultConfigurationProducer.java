/*
 * -----------------------------------------------------------------------\
 * Lumeer
 *  
 * Copyright (C) 2016 - 2017 the original author or authors.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -----------------------------------------------------------------------/
 */
package io.lumeer.engine.controller.configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.context.SessionScoped;

/**
 * Produces default configuration values based on system properties.
 *
 * @author <a href="mailto:marvenec@gmail.com">Martin Večeřa</a>
 */
@SessionScoped
public class DefaultConfigurationProducer implements Serializable {

   private static final long serialVersionUID = -8974156455413284479L;
   private static final String DEFAULT_PROPERTY_FILE = "defaults-dev.properties";

   private Logger log = Logger.getLogger(DefaultConfigurationProducer.class.getName());

   private Map<String, String> defaultConfiguration = null;

   public Map<String, String> getDefaultConfiguration() {
      if (defaultConfiguration == null) {
         defaultConfiguration = new HashMap<>();

         String envDefaults = System.getenv("lumeer.defaults");
         if (envDefaults == null) {
            envDefaults = DEFAULT_PROPERTY_FILE;
         }

         final Properties properties = new Properties();
         try {
            final InputStream input = DefaultConfigurationProducer.class.getResourceAsStream("/" + envDefaults);
            if (input != null) {
               properties.load(new InputStreamReader(input));
               properties.list(System.out);
               properties.forEach((key, value) -> {
                  defaultConfiguration.put(key.toString(), value.toString());
               });
            } else {
               log.log(Level.WARNING, String.format("Default property file %s not found.", envDefaults));
            }
         } catch (IOException e) {
            log.log(Level.SEVERE, "Unable to load default property values: ", e);
         }
      }

      return defaultConfiguration;
   }
}
