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
package org.terracotta.dynamic_config.cli.api.command;

import org.terracotta.diagnostic.model.LogicalServerState;
import org.terracotta.dynamic_config.api.model.Cluster;
import org.terracotta.dynamic_config.api.model.Node.Endpoint;
import org.terracotta.inet.HostPort;

import java.util.LinkedHashMap;
import java.util.Map;

public class UnlockConfigAction extends RemoteAction {

  private HostPort node;

  public void setNode(HostPort node) {
    this.node = node;
  }

  @Override
  public final void run() {
    Map<Endpoint, LogicalServerState> allNodes = findRuntimePeersStatus(node);
    LinkedHashMap<Endpoint, LogicalServerState> onlineNodes = filterOnlineNodes(allNodes);
    Cluster cluster = getRuntimeCluster(node);
    unlock(cluster, onlineNodes);
  }
}