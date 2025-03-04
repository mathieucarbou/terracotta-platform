/*
 * Copyright Terracotta, Inc.
 * Copyright IBM Corp. 2024, 2025
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
package org.terracotta.dynamic_config.api.server;

import org.terracotta.dynamic_config.api.model.NodeContext;
import org.terracotta.dynamic_config.api.service.NomadChangeInfo;
import org.terracotta.nomad.client.change.NomadChange;
import org.terracotta.nomad.server.ChangeApplicator;
import org.terracotta.nomad.server.NomadException;
import org.terracotta.nomad.server.NomadServer;
import org.terracotta.nomad.server.NomadServerMode;
import org.terracotta.nomad.server.UncheckedNomadException;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

public interface DynamicConfigNomadServer extends NomadServer<NodeContext> {
  void setChangeApplicator(ChangeApplicator<NodeContext> changeApplicator);

  ChangeApplicator<NodeContext> getChangeApplicator();

  List<NomadChangeInfo> getChangeHistory() throws NomadException;

  /**
   * Forces the sync of a stream of changes in a node's append log.
   * <p>
   * To construct the configurations to write, a function must be passed,
   * which will return a new configuration from 2 parameters: the change and
   * the previous configuration, which might be null at the beginning.
   */
  void forceSync(Collection<NomadChangeInfo> changes, BiFunction<NodeContext, NomadChange, NodeContext> fn) throws NomadException;

  static DynamicConfigNomadServer empty() {
    return new EmptyDynamicConfigNomadServer();
  }

  default NomadServerMode getNomadServerMode() throws UncheckedNomadException {
    try {
      return discover().getMode();
    } catch (NomadException e) {
      throw new UncheckedNomadException(e);
    }
  }
}
