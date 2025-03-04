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
package org.terracotta.lease;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LeaseEndpointDelegateTest {
  @Mock
  private LeaseReconnectListener reconnectListener;

  @Mock
  private LeaseReconnectDataSupplier reconnectDataSupplier;

  @Test
  public void messageFromServerInformsAboutReconnectedStatus() {
    LeaseEndpointDelegate delegate = new LeaseEndpointDelegate(reconnectListener, reconnectDataSupplier);
    delegate.handleMessage(new LeaseAcquirerAvailable());
    verify(reconnectListener).reconnected();
  }

  @Test
  public void supplyReconnectDataAndInformReconnectListenerWhenReconnectionStarts() {
    when(reconnectDataSupplier.getReconnectData()).thenReturn(new LeaseReconnectData(1));
    LeaseEndpointDelegate delegate = new LeaseEndpointDelegate(reconnectListener, reconnectDataSupplier);
    byte[] bytes = delegate.createExtendedReconnectData();
    LeaseReconnectData recoveredReconnectData = LeaseReconnectData.decode(bytes);
    assertEquals(1, recoveredReconnectData.getConnectionSequenceNumber());
    verify(reconnectListener).reconnecting();
  }
}
