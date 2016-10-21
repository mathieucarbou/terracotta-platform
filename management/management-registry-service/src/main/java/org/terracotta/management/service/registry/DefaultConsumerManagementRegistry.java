/*
 * Copyright Terracotta, Inc.
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
package org.terracotta.management.service.registry;

import org.terracotta.management.model.capabilities.Capability;
import org.terracotta.management.model.context.ContextContainer;
import org.terracotta.management.registry.AbstractManagementRegistry;
import org.terracotta.management.registry.ManagementProvider;
import org.terracotta.management.service.monitoring.MonitoringService;
import org.terracotta.management.service.registry.provider.ConsumerManagementProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mathieu Carbou
 */
class DefaultConsumerManagementRegistry extends AbstractManagementRegistry implements ConsumerManagementRegistry {

  private static final Logger LOGGER = Logger.getLogger(ConsumerManagementRegistryProvider.class.getName());

  private final MonitoringService monitoringService;
  private final ContextContainer contextContainer;
  private final MonitoringResolver resolver;

  private Collection<Capability> previouslyExposed = Collections.emptyList();

  DefaultConsumerManagementRegistry(MonitoringService monitoringService) {
    this.monitoringService = Objects.requireNonNull(monitoringService);
    this.resolver = new DefaultMonitoringResolver(monitoringService);
    this.contextContainer = new ContextContainer("consumerId", String.valueOf(this.monitoringService.getConsumerId()));
  }

  @Override
  public void close() {
    managementProviders.forEach(ManagementProvider::close);
    managementProviders.clear();
  }

  @Override
  public void addManagementProvider(ManagementProvider<?> provider) {
    if (provider instanceof ConsumerManagementProvider<?>) {
      ((ConsumerManagementProvider<?>) provider).accept(resolver);
    }
    super.addManagementProvider(provider);
  }

  @Override
  public synchronized void refresh() {
    if (LOGGER.isLoggable(Level.FINEST)) {
      LOGGER.finest("refresh(): " + contextContainer);
    }
    Collection<Capability> capabilities = getCapabilities();
    if (!previouslyExposed.equals(capabilities)) {
      Capability[] capabilitiesArray = capabilities.toArray(new Capability[capabilities.size()]);
      // confirm with server team, this call won't throw because monitoringProducer.addNode() won't throw.
      monitoringService.exposeServerEntityManagementRegistry(contextContainer, capabilitiesArray);
      previouslyExposed = capabilities;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean pushServerEntityNotification(Object managedObjectSource, String type, Map<String, String> attrs) {
    for (ManagementProvider managementProvider : managementProviders) {
      if (managementProvider instanceof ConsumerManagementProvider && managementProvider.getManagedType().isInstance(managedObjectSource)) {
        if (((ConsumerManagementProvider<Object>) managementProvider).pushServerEntityNotification(managedObjectSource, type, attrs)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public ContextContainer getContextContainer() {
    return contextContainer;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("DefaultConsumerManagementRegistry{");
    sb.append("contextContainer=").append(contextContainer);
    sb.append(", monitoringService=").append(monitoringService);
    sb.append('}');
    return sb.toString();
  }

}
