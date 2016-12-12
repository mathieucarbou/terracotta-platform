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
package org.terracotta.management.entity.sample.ha;

import org.junit.Test;
import org.terracotta.management.model.cluster.Server;
import org.terracotta.management.model.message.Message;
import org.terracotta.management.model.notification.ContextualNotification;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * @author Mathieu Carbou
 */
public class HAFeaturesTest extends AbstractHaTest {

  @Test
  public void get_notifications_when_passive_joins() throws Exception {
    // clear
    tmsAgentService.readMessages();

    // connect passive
    stripeControl.startOneServer();
    stripeControl.waitForRunningPassivesInStandby();

    // read messages
    List<Message> messages = tmsAgentService.readMessages();
    assertThat(messages.size(), equalTo(6));
    assertThat(
        IntStream.range(0, 6).mapToObj(i -> messages.get(i).getType()).collect(Collectors.toList()),
        equalTo(Arrays.asList("NOTIFICATION", "NOTIFICATION", "NOTIFICATION", "NOTIFICATION", "NOTIFICATION", "TOPOLOGY")));

    List<ContextualNotification> notifs = messages.stream()
        .filter(message -> message.getType().equals("NOTIFICATION"))
        .flatMap(message -> message.unwrap(ContextualNotification.class).stream())
        .collect(Collectors.toList());

    assertThat(
        IntStream.range(0, 5).mapToObj(i -> notifs.get(i).getType()).collect(Collectors.toList()),
        equalTo(Arrays.asList("SERVER_JOINED", "SERVER_STATE_CHANGED", "SERVER_STATE_CHANGED", "SERVER_ENTITY_CREATED", "SERVER_STATE_CHANGED")));

//    assertThat(
//        IntStream.range(0, 5).mapToObj(i -> notifs.get(i).getContext().get(Server.NAME_KEY)).collect(Collectors.toList()),
//        equalTo(Arrays.asList("server2", "server2", "server2", "server2", "server2")));
//  The state of the server is not in the context
//    assertThat(
//        IntStream.of(1, 2, 4).mapToObj(i -> notifs.get(i).getContext().get(Server.NAME_KEY)).collect(Collectors.toList()),
//        equalTo(Arrays.asList("UNINITIALIZED", "SYNCHRONIZING", "PASSIVE")));
  }

  @Test
  public void passives_can_sync_data_on_start() throws Exception {
    put(0, "clients", "client1", "Mathieu");

    assertThat(get(0, "clients", "client1"), equalTo("Mathieu"));
    assertThat(get(1, "clients", "client1"), equalTo("Mathieu"));

    tmsAgentService.readMessages();

    stripeControl.startOneServer();
    stripeControl.waitForRunningPassivesInStandby();

    List<Message> messages = tmsAgentService.readMessages();
    messages.forEach(System.out::println);

    List<ContextualNotification> notifs = messages.stream()
        .filter(message -> message.getType().equals("NOTIFICATION"))
        .flatMap(message -> message.unwrap(ContextualNotification.class).stream())
        .collect(Collectors.toList());    
    assertThat(notifs.size(), equalTo(5));

    assertThat(
        IntStream.range(0, 5).mapToObj(i -> notifs.get(i).getType()).collect(Collectors.toList()),
        equalTo(Arrays.asList("SERVER_JOINED", "SERVER_STATE_CHANGED", "SERVER_STATE_CHANGED", "SERVER_ENTITY_CREATED", "SERVER_STATE_CHANGED")));
  }

}
