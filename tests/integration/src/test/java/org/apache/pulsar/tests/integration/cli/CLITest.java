/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.tests.integration.cli;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.functions.api.examples.pojo.Tick;
import org.apache.pulsar.tests.integration.containers.BrokerContainer;
import org.apache.pulsar.tests.integration.docker.ContainerExecException;
import org.apache.pulsar.tests.integration.docker.ContainerExecResult;
import org.apache.pulsar.tests.integration.suites.PulsarTestSuite;
import org.apache.pulsar.tests.integration.topologies.PulsarCluster;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Test Pulsar CLI.
 */
public class CLITest extends PulsarTestSuite {

    @Test
    public void testDeprecatedCommands() throws Exception {
        String tenantName = "test-deprecated-commands";

        ContainerExecResult result = pulsarCluster.runAdminCommandOnAnyBroker("--help");
        assertFalse(result.getStdout().isEmpty());
        assertFalse(result.getStdout().contains("Usage: properties "));
        result = pulsarCluster.runAdminCommandOnAnyBroker(
            "properties", "create", tenantName,
            "--allowed-clusters", pulsarCluster.getClusterName(),
            "--admin-roles", "admin"
        );
        assertTrue(result.getStderr().contains("deprecated"));

        result = pulsarCluster.runAdminCommandOnAnyBroker(
            "properties", "list");
        assertTrue(result.getStdout().contains(tenantName));
        result = pulsarCluster.runAdminCommandOnAnyBroker(
            "tenants", "list");
        assertTrue(result.getStdout().contains(tenantName));
    }

    @Test
    public void testCreateSubscriptionCommand() throws Exception {
        String topic = "testCreateSubscriptionCommmand";

        String subscriptionPrefix = "subscription-";

        int i = 0;
        for (BrokerContainer container : pulsarCluster.getBrokers()) {
            ContainerExecResult result = container.execCmd(
                PulsarCluster.ADMIN_SCRIPT,
                "topics",
                "create-subscription",
                "persistent://public/default/" + topic,
                "--subscription",
                "" + subscriptionPrefix + i
            );
            assertTrue(result.getStdout().isEmpty());
            assertTrue(result.getStderr().isEmpty());
            i++;
        }
    }

    @Test
    public void testTopicTerminationOnTopicsWithoutConnectedConsumers() throws Exception {
        String topicName = "persistent://public/default/test-topic-termination";
        BrokerContainer container = pulsarCluster.getAnyBroker();
        container.execCmd(
                PulsarCluster.ADMIN_SCRIPT,
                "topics",
                "create",
                topicName);

        ContainerExecResult result = container.execCmd(
            PulsarCluster.CLIENT_SCRIPT,
            "produce",
            "-m",
            "\"test topic termination\"",
            "-n",
            "1",
            topicName);

        assertTrue(result.getStdout().contains("1 messages successfully produced"));

        // terminate the topic
        result = container.execCmd(
            PulsarCluster.ADMIN_SCRIPT,
            "topics",
            "terminate",
            topicName);
        assertTrue(result.getStdout().contains("Topic succesfully terminated at"));

        // try to produce should fail
        try {
            pulsarCluster.getAnyBroker().execCmd(PulsarCluster.CLIENT_SCRIPT,
                                                 "produce",
                                                 "-m",
                                                 "\"test topic termination\"",
                                                 "-n",
                                                 "1",
                                                 topicName);
            fail("Command should have exited with non-zero");
        } catch (ContainerExecException e) {
            assertTrue(e.getResult().getStdout().contains("Topic was already terminated"));
        }
    }

    @Test
    public void testSchemaCLI() throws Exception {
        BrokerContainer container = pulsarCluster.getAnyBroker();
        String topicName = "persistent://public/default/test-schema-cli";

        ContainerExecResult result = container.execCmd(
            PulsarCluster.CLIENT_SCRIPT,
            "produce",
            "-m",
            "\"test topic schema\"",
            "-n",
            "1",
            topicName);
        assertTrue(result.getStdout().contains("1 messages successfully produced"));

        result = container.execCmd(
            PulsarCluster.ADMIN_SCRIPT,
            "schemas",
            "upload",
            topicName,
            "-f",
            "/pulsar/conf/schema_example.conf"
        );
        assertTrue(result.getStdout().isEmpty());
        assertTrue(result.getStderr().isEmpty());

        // get schema
        result = container.execCmd(
            PulsarCluster.ADMIN_SCRIPT,
            "schemas",
            "get",
            topicName);
        assertTrue(result.getStdout().contains("\"type\": \"STRING\""));

        // delete the schema
        result = container.execCmd(
            PulsarCluster.ADMIN_SCRIPT,
            "schemas",
            "delete",
            topicName);
        assertTrue(result.getStdout().isEmpty());
        assertTrue(result.getStderr().isEmpty());

        // get schema again
        try {
            container.execCmd(PulsarCluster.ADMIN_SCRIPT,
                              "schemas",
                              "get",
                              "persistent://public/default/test-schema-cli"
                              );
            fail("Command should have exited with non-zero");
        } catch (ContainerExecException e) {
            assertTrue(e.getResult().getStderr().contains("Reason: HTTP 404 Not Found"));
        }
    }

    @Test
    public void testSetInfiniteRetention() throws Exception {
        ContainerExecResult result;

        String namespace = "get-and-set-retention" + randomName(8);
        pulsarCluster.createNamespace(namespace);

        String[] setCommand = {
            "namespaces", "set-retention", "public/" + namespace,
            "--size", "-1",
            "--time", "-1"
        };

        result = pulsarCluster.runAdminCommandOnAnyBroker(setCommand);
        assertTrue(
            result.getStdout().isEmpty(),
            result.getStdout()
        );
        assertTrue(
            result.getStderr().isEmpty(),
            result.getStdout()
        );

        String[] getCommand = {
            "namespaces", "get-retention", "public/" + namespace
        };

        result = pulsarCluster.runAdminCommandOnAnyBroker(getCommand);
        assertTrue(
            result.getStdout().contains("\"retentionTimeInMinutes\" : -1"),
            result.getStdout());
        assertTrue(
            result.getStdout().contains("\"retentionSizeInMB\" : -1"),
            result.getStdout());
    }

    // authorization related tests

    @Test
    public void testGrantPermissionsAuthorizationDisabled() throws Exception {
        ContainerExecResult result;

        String namespace = "grant-permissions-" + randomName(8);
        result = pulsarCluster.createNamespace(namespace);
        assertEquals(0, result.getExitCode());

        String[] grantCommand = {
            "namespaces", "grant-permission", "public/" + namespace,
            "--actions", "produce",
            "--role", "test-role"
        };
        try {
            pulsarCluster.runAdminCommandOnAnyBroker(grantCommand);
        } catch (ContainerExecException cee) {
            result = cee.getResult();
            assertTrue(result.getStderr().contains("HTTP 501 Not Implemented"), result.getStderr());
        }
    }

    @Test
    public void testJarPojoSchemaUploadAvro() throws Exception {

        ContainerExecResult containerExecResult = pulsarCluster.runAdminCommandOnAnyBroker(
                "schemas",
                "extract", "--jar", "/pulsar/examples/api-examples.jar", "--type", "avro",
                "--classname", "org.apache.pulsar.functions.api.examples.pojo.Tick",
                "persistent://public/default/pojo-avro");

        Assert.assertEquals(containerExecResult.getExitCode(), 0);
        testPublishAndConsume("persistent://public/default/pojo-avro", "avro", Schema.AVRO(Tick.class));
    }

    @Test
    public void testJarPojoSchemaUploadJson() throws Exception {

        ContainerExecResult containerExecResult = pulsarCluster.runAdminCommandOnAnyBroker(
                "schemas",
                "extract", "--jar", "/pulsar/examples/api-examples.jar", "--type", "json",
                "--classname", "org.apache.pulsar.functions.api.examples.pojo.Tick",
                "persistent://public/default/pojo-json");

        Assert.assertEquals(containerExecResult.getExitCode(), 0);
        testPublishAndConsume("persistent://public/default/pojo-json", "json", Schema.JSON(Tick.class));
    }

    private void testPublishAndConsume(String topic, String sub, Schema type) throws PulsarClientException {

        PulsarClient client = PulsarClient.builder().serviceUrl(pulsarCluster.getPlainTextServiceUrl()).build();

        Producer<Tick> producer = client.newProducer(type)
                .topic(topic + "-message")
                .create();

        Consumer<Tick> consumer = client.newConsumer(type)
                .topic(topic + "-message")
                .subscriptionName(sub)
                .subscribe();

        final int numOfMessages = 10;

        for (int i = 1; i < numOfMessages; ++i) {
            producer.send(new Tick(i, "Stock_" + i, 100 + i, 110 + i));
        }

        for (int i = 1; i < numOfMessages; ++i) {
            Tick expected = new Tick(i, "Stock_" + i, 100 + i, 110 + i);
            Message<Tick> receive = consumer.receive(5, TimeUnit.SECONDS);
            Assert.assertEquals(receive.getValue(), expected);
        }

        producer.close();
        consumer.close();
        client.close();
    }

}
