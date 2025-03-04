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
package org.terracotta.dynamic_config.system_tests.activated;

import org.junit.Before;
import org.junit.Test;
import org.terracotta.dynamic_config.api.model.FailoverPriority;
import org.terracotta.dynamic_config.test_support.ClusterDefinition;
import org.terracotta.dynamic_config.test_support.DynamicConfigIT;
import org.terracotta.dynamic_config.test_support.InlineServers;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.terracotta.angela.client.support.hamcrest.AngelaMatchers.containsOutput;
import static org.terracotta.angela.client.support.hamcrest.AngelaMatchers.successful;

@ClusterDefinition(nodesPerStripe = 3, autoStart = false)
public class AttachInConsistency1x3IT extends DynamicConfigIT {

  @Override
  protected FailoverPriority getFailoverPriority() {
    return FailoverPriority.consistency();
  }

  @Before
  public void setup() throws Exception {
    startNode(1, 1);
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 1)).getNodeCount(), is(equalTo(1)));

    // start the second node
    startNode(1, 2);
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 2)).getNodeCount(), is(equalTo(1)));

    //attach the second node
    assertThat(configTool("attach", "-d", "localhost:" + getNodePort(1, 1), "-s", "localhost:" + getNodePort(1, 2)), is(successful()));

    //Activate cluster
    activateCluster();
  }

  @Test
  public void testAttachNodeFailAtPrepare() throws Exception {
    //create prepare failure on active
    assertThat(configTool("set", "-s", "localhost:" + getNodePort(1, 1), "-c", "stripe.1.node.1.tc-properties.attachStatus=prepareAddition-failure"), is(successful()));

    startNode(1, 3);
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(1)));

    // attach failure (forcing attach otherwise we have to restart cluster)
    assertThat(
        configTool("attach", "-f", "-d", "localhost:" + getNodePort(1, 1), "-s", "localhost:" + getNodePort(1, 3)),
        containsOutput("Two-Phase commit failed"));

    assertThat(getUpcomingCluster("localhost", getNodePort(1, 1)).getNodeCount(), is(equalTo(2)));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 2)).getNodeCount(), is(equalTo(2)));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(1)));

    withTopologyService(1, 1, topologyService -> assertTrue(topologyService.isActivated()));
    withTopologyService(1, 2, topologyService -> assertTrue(topologyService.isActivated()));
    withTopologyService(1, 3, topologyService -> assertFalse(topologyService.isActivated()));
  }

  @Test
  @InlineServers(false)
  public void attachNodeFailingBecauseOfNodeGoingDownInPreparePhase() throws Exception {
    int activeId = waitForActive(1);
    int passiveId = waitForNPassives(1, 1)[0];

    startNode(1, 3);
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(1)));

    //create failover in prepare phase for active
    String propertySettingString = "stripe.1.node." + activeId + ".tc-properties.failoverAddition=killAddition-prepare";
    assertThat(configTool("set", "-s", "localhost:" + getNodePort(1, 1), "-c", propertySettingString), is(successful()));

    assertThat(
        configTool("attach", "-f", "-d", "localhost:" + getNodePort(1, activeId), "-s", "localhost:" + getNodePort(1, 3)),
        containsOutput("Two-Phase commit failed"));

    assertThat(getUpcomingCluster("localhost", getNodePort(1, passiveId)).getNodeCount(), is(equalTo(2)));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(1)));

    withTopologyService(1, passiveId, topologyService -> assertTrue(topologyService.isActivated()));
    withTopologyService(1, 3, topologyService -> assertFalse(topologyService.isActivated()));


    // Ensure that earlier stopped active now restarts as passive and sync the config from current active
    startNode(1, activeId, "-r", getNode(1, activeId).getConfigRepo());
    waitForActive(1, passiveId);
    waitForPassive(1, activeId);
    withTopologyService(1, activeId, topologyService -> assertTrue(topologyService.isActivated()));
  }

  @Test
  @InlineServers(false)
  public void testFailoverDuringNomadCommitForPassiveAddition() throws Exception {
    int activeId = waitForActive(1);
    int passiveId = waitForNPassives(1, 1)[0];
    startNode(1, 3);
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(1)));

    //setup for failover in commit phase on active
    String propertySettingString = "stripe.1.node." + activeId + ".tc-properties.failoverAddition=killAddition-commit";
    assertThat(configTool("set", "-s", "localhost:" + getNodePort(1, 1), "-c", propertySettingString), is(successful()));

    // active died and passive can't become active
    entityOperationTimeout = Duration.ofSeconds(5); // to not be stuck in failover
    assertThat(
        configTool("attach", "-f", "-d", "localhost:" + getNodePort(1, activeId), "-s", "localhost:" + getNodePort(1, 3)),
        containsOutput("Two-Phase commit failed"));

    //start the old active and verify it is in passive state
    startNode(1, activeId, "-r", getNode(1, activeId).getConfigRepo());
    waitForActive(1, passiveId);
    waitForPassive(1, activeId);

    withTopologyService(1, passiveId, topologyService -> assertTrue(topologyService.isActivated()));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, passiveId)).getNodeCount(), is(equalTo(3)));
    assertThat(getRuntimeCluster("localhost", getNodePort(1, passiveId)).getNodeCount(), is(equalTo(3)));

    withTopologyService(1, activeId, topologyService -> assertTrue(topologyService.isActivated()));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, activeId)).getNodeCount(), is(equalTo(3)));
    assertThat(getRuntimeCluster("localhost", getNodePort(1, activeId)).getNodeCount(), is(equalTo(3)));
  }
}
