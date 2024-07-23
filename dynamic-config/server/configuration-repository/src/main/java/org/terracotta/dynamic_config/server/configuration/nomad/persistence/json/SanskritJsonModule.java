/*
 * Copyright Terracotta, Inc.
 * Copyright Super iPaaS Integration LLC, an IBM Company 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terracotta.dynamic_config.server.configuration.nomad.persistence.json;

import org.terracotta.dynamic_config.server.configuration.nomad.persistence.sanskrit.SanskritMapVisitor;
import org.terracotta.json.gson.GsonConfig;
import org.terracotta.json.gson.GsonModule;

/**
 * @author Mathieu Carbou
 */
public class SanskritJsonModule implements GsonModule {
  @Override
  public void configure(GsonConfig config)   {
    config.writeNull(SanskritMapVisitor.Null.class);
  }
}
