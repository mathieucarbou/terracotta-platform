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
package org.terracotta.dynamic_config.cli.config_tool.restart;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.terracotta.common.struct.TimeUnit;
import org.terracotta.diagnostic.client.DiagnosticOperationTimeoutException;
import org.terracotta.diagnostic.client.DiagnosticService;
import org.terracotta.diagnostic.client.connection.ConcurrencySizing;
import org.terracotta.diagnostic.client.connection.DiagnosticServiceProviderException;
import org.terracotta.diagnostic.model.LogicalServerState;
import org.terracotta.dynamic_config.api.model.Cluster;
import org.terracotta.dynamic_config.api.model.Stripe;
import org.terracotta.dynamic_config.api.service.DynamicConfigService;
import org.terracotta.dynamic_config.cli.config_tool.BaseTest;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.terracotta.common.struct.TimeUnit.SECONDS;
import static org.terracotta.diagnostic.model.LogicalServerState.ACTIVE;
import static org.terracotta.diagnostic.model.LogicalServerState.ACTIVE_SUSPENDED;
import static org.terracotta.diagnostic.model.LogicalServerState.PASSIVE;
import static org.terracotta.diagnostic.model.LogicalServerState.STARTING;
import static org.terracotta.diagnostic.model.LogicalServerState.SYNCHRONIZING;
import static org.terracotta.diagnostic.model.LogicalServerState.UNINITIALIZED;
import static org.terracotta.diagnostic.model.LogicalServerState.UNKNOWN;
import static org.terracotta.diagnostic.model.LogicalServerState.UNREACHABLE;
import static org.terracotta.dynamic_config.api.model.Node.newDefaultNode;

/**
 * @author Mathieu Carbou
 */
@RunWith(MockitoJUnitRunner.class)
public class RestartServiceTest extends BaseTest {

  private static final int[] PORTS = {9411, 9412, 9413, 9421, 9422, 9423};

  private RestartService restartService;
  private Cluster cluster;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    restartService = new RestartService(diagnosticServiceProvider, new ConcurrencySizing());
    cluster = Cluster.newDefaultCluster(
        "my-cluster",
        new Stripe(
            newDefaultNode("node1", "localhost", PORTS[0]),
            newDefaultNode("node2", "localhost", PORTS[1]),
            newDefaultNode("node3", "localhost", PORTS[2])
        ),
        new Stripe(
            newDefaultNode("node1", "localhost", PORTS[3]),
            newDefaultNode("node2", "localhost", PORTS[4]),
            newDefaultNode("node3", "localhost", PORTS[5])
        ));
  }

  @Test
  public void test_restart() throws InterruptedException {
    mockSuccessfulServerRestart();

    RestartProgress restartProgress = restartService.restartNodes(cluster.getNodeAddresses(), Duration.ofSeconds(2));
    assertThat(restartProgress.getErrors().size(), is(equalTo(0)));

    Map<InetSocketAddress, LogicalServerState> restarted = restartProgress.await(Duration.ofSeconds(10));
    assertThat(restarted.toString(), restarted.size(), is(equalTo(6)));

    IntStream.of(PORTS).forEach(port -> {
      verify(diagnosticServiceMock("localhost", port)).getProxy(DynamicConfigService.class);
      verify(dynamicConfigServiceMock("localhost", port)).restart(any());
      verify(diagnosticServiceMock("localhost", port)).getLogicalServerState();
    });
  }

  @Test
  public void test_restart_call_throws_DiagnosticOperationTimeoutException() throws InterruptedException {
    mockSuccessfulServerRestart();

    IntStream.of(PORTS).forEach(port -> {
      DynamicConfigService dynamicConfigService = dynamicConfigServiceMock("localhost", port);
      doThrow(new DiagnosticOperationTimeoutException("")).when(dynamicConfigService).restart(any());
    });

    RestartProgress restartProgress = restartService.restartNodes(cluster.getNodeAddresses(), Duration.ofSeconds(2));
    assertThat(restartProgress.getErrors().size(), is(equalTo(6)));

    Map<InetSocketAddress, LogicalServerState> restarted = restartProgress.await(Duration.ofSeconds(10));
    assertThat(restarted.toString(), restarted.size(), is(equalTo(0)));

    IntStream.of(PORTS).forEach(port -> {
      verify(diagnosticServiceMock("localhost", port)).getProxy(DynamicConfigService.class);
      verify(dynamicConfigServiceMock("localhost", port)).restart(any());
    });
  }

  @Test
  public void test_restart_call_fails() throws InterruptedException {
    IntStream.of(PORTS).forEach(port -> {
      DynamicConfigService dynamicConfigService = dynamicConfigServiceMock("localhost", port);
      doThrow(new DiagnosticServiceProviderException("error")).when(dynamicConfigService).restart(any());
    });

    RestartProgress restartProgress = restartService.restartNodes(cluster.getNodeAddresses(), Duration.ofSeconds(2));
    assertThat(restartProgress.getErrors().size(), is(equalTo(6)));

    Map<InetSocketAddress, LogicalServerState> restarted = restartProgress.await(Duration.ofSeconds(10));
    assertThat(restarted.toString(), restarted.size(), is(equalTo(0)));

    IntStream.of(PORTS).forEach(port -> {
      verify(diagnosticServiceMock("localhost", port)).getProxy(DynamicConfigService.class);
      verify(dynamicConfigServiceMock("localhost", port)).restart(any());
    });
  }

  @Test
  public void test_stats_call_times_out() throws InterruptedException {
    mockSuccessfulServerRestart();

    when(diagnosticServiceMock("localhost", 9411).getLogicalServerState()).thenAnswer(sleep(SYNCHRONIZING, 60, SECONDS));

    RestartProgress restartProgress = restartService.restartNodes(cluster.getNodeAddresses(), Duration.ofSeconds(2));
    assertThat(restartProgress.getErrors().size(), is(equalTo(0)));

    Map<InetSocketAddress, LogicalServerState> restarted = restartProgress.await(Duration.ofSeconds(8));
    assertThat(restarted.toString(), restarted.size(), is(equalTo(5)));

    IntStream.of(PORTS).forEach(port -> {
      verify(dynamicConfigServiceMock("localhost", port)).restart(any());
      verify(diagnosticServiceMock("localhost", port), atLeast(1)).getLogicalServerState();
    });
  }

  @Test
  @SuppressWarnings("unchecked")
  public void test_server_restart_with_unexpected_state() throws InterruptedException {
    mockSuccessfulServerRestart();

    when(diagnosticServiceMock("localhost", 9411).getLogicalServerState()).thenReturn(null);
    when(diagnosticServiceMock("localhost", 9412).getLogicalServerState()).thenReturn(UNREACHABLE);
    when(diagnosticServiceMock("localhost", 9413).getLogicalServerState()).thenReturn(UNKNOWN);
    when(diagnosticServiceMock("localhost", 9421).getLogicalServerState()).thenReturn(STARTING);
    when(diagnosticServiceMock("localhost", 9422).getLogicalServerState()).thenReturn(UNINITIALIZED);
    when(diagnosticServiceMock("localhost", 9423).getLogicalServerState()).thenReturn(ACTIVE_SUSPENDED);

    RestartProgress restartProgress = restartService.restartNodes(cluster.getNodeAddresses(), Duration.ofSeconds(2));
    assertThat(restartProgress.getErrors().size(), is(equalTo(0)));

    Map<InetSocketAddress, LogicalServerState> restarted = restartProgress.await(Duration.ofSeconds(8));
    assertThat(restarted.toString(), restarted.size(), is(equalTo(0)));

    IntStream.of(PORTS).forEach(port -> {
      verify(diagnosticServiceMock("localhost", port)).getProxy(DynamicConfigService.class);
      verify(dynamicConfigServiceMock("localhost", port)).restart(any());
      verify(diagnosticServiceMock("localhost", port), atLeast(1)).getLogicalServerState();
    });
  }

  private void mockSuccessfulServerRestart() {
    DiagnosticService diagnosticService11 = diagnosticServiceMock("localhost", 9411);
    DiagnosticService diagnosticService12 = diagnosticServiceMock("localhost", 9412);
    DiagnosticService diagnosticService13 = diagnosticServiceMock("localhost", 9413);
    DiagnosticService diagnosticService21 = diagnosticServiceMock("localhost", 9421);
    DiagnosticService diagnosticService22 = diagnosticServiceMock("localhost", 9422);
    DiagnosticService diagnosticService23 = diagnosticServiceMock("localhost", 9423);

    when(diagnosticService11.getLogicalServerState()).thenReturn(ACTIVE);
    when(diagnosticService12.getLogicalServerState()).thenReturn(PASSIVE);
    when(diagnosticService13.getLogicalServerState()).thenReturn(PASSIVE);
    when(diagnosticService21.getLogicalServerState()).thenReturn(ACTIVE);
    when(diagnosticService22.getLogicalServerState()).thenReturn(PASSIVE);
    when(diagnosticService23.getLogicalServerState()).thenReturn(PASSIVE);

    IntStream.of(PORTS).forEach(port -> {
      DynamicConfigService dynamicConfigService = dynamicConfigServiceMock("localhost", port);
      doNothing().when(dynamicConfigService).restart(any());
    });
  }

  public static <T> Answer<T> sleep(long time, TimeUnit unit) {
    return sleep(null, time, unit);
  }

  public static <T> Answer<T> sleep(T delayedReturn, long time, TimeUnit unit) {
    return invocation -> {
      Thread.sleep(unit.toMillis(time));
      return delayedReturn;
    };
  }
}
