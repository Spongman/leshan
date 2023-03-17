/*******************************************************************************
 * Copyright (c) 2015 Sierra Wireless and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *     Michał Wadowski (Orange) - Improved compliance with rfc6690
 *******************************************************************************/
package org.eclipse.leshan.integration.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.leshan.integration.tests.BootstrapConfigTestBuilder.givenBootstrapConfig;
import static org.eclipse.leshan.integration.tests.util.LeshanTestBootstrapServerBuilder.givenBootstrapServerUsing;
import static org.eclipse.leshan.integration.tests.util.LeshanTestClientBuilder.givenClientUsing;
import static org.eclipse.leshan.integration.tests.util.LeshanTestServerBuilder.givenServerUsing;
import static org.eclipse.leshan.integration.tests.util.assertion.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.leshan.core.endpoint.Protocol;
import org.eclipse.leshan.core.request.ContentFormat;
import org.eclipse.leshan.integration.tests.util.BootstrapRequestChecker;
import org.eclipse.leshan.integration.tests.util.LeshanTestBootstrapServer;
import org.eclipse.leshan.integration.tests.util.LeshanTestBootstrapServerBuilder;
import org.eclipse.leshan.integration.tests.util.LeshanTestClient;
import org.eclipse.leshan.integration.tests.util.LeshanTestClientBuilder;
import org.eclipse.leshan.integration.tests.util.LeshanTestServer;
import org.eclipse.leshan.integration.tests.util.LeshanTestServerBuilder;
import org.eclipse.leshan.integration.tests.util.junit5.extensions.BeforeEachParameterizedResolver;
import org.eclipse.leshan.server.bootstrap.BootstrapSession;
import org.eclipse.leshan.server.bootstrap.InvalidConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(BeforeEachParameterizedResolver.class)
public class BootstrapTest {

    /*---------------------------------/
     *  Parameterized Tests
     * -------------------------------*/
    @ParameterizedTest(name = "{0} - Client using {1} - Server using {2}- BS Server using {3}")
    @MethodSource("transports")
    @Retention(RetentionPolicy.RUNTIME)
    private @interface TestAllTransportLayer {
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> transports() {
        return Stream.of(//
                // ProtocolUsed - Client Endpoint Provider - Server Endpoint Provider - BS Server Endpoint Provider
                arguments(Protocol.COAP, "Californium", "Californium", "Californium"));
    }

    /*---------------------------------/
     *  Set-up and Tear-down Tests
     * -------------------------------*/
    LeshanTestBootstrapServerBuilder givenBootstrapServer;
    LeshanTestBootstrapServer bootstrapServer;
    LeshanTestServerBuilder givenServer;
    LeshanTestServer server;
    LeshanTestClientBuilder givenClient;
    LeshanTestClient client;

    @BeforeEach
    public void start(Protocol givenProtocol, String givenClientEndpointProvider, String givenServerEndpointProvider,
            String givenBootstrapServerEndpointProvider) {
        givenServer = givenServerUsing(givenProtocol).with(givenServerEndpointProvider);
        givenBootstrapServer = givenBootstrapServerUsing(givenProtocol).with(givenBootstrapServerEndpointProvider);
        givenClient = givenClientUsing(givenProtocol).with(givenClientEndpointProvider);
    }

    @AfterEach
    public void stop() throws InterruptedException {
        if (client != null)
            client.destroy(false);
        if (server != null)
            server.destroy();
        if (bootstrapServer != null)
            bootstrapServer.destroy();
    }

    /*---------------------------------/
     *  Tests
     * -------------------------------*/

    @TestAllTransportLayer
    public void bootstrap(Protocol givenProtocol, String givenClientEndpointProvider,
            String givenServerEndpointProvider, String givenBootstrapServerEndpointProvider)
            throws InvalidConfigurationException {
        // Create DM Server without security & start it
        server = givenServer.build();
        server.start();

        // Create and start bootstrap server
        bootstrapServer = givenBootstrapServer.build();
        bootstrapServer.start();

        // Create Client and check it is not already registered
        client = givenClient.connectingTo(bootstrapServer).build();
        assertThat(client).isNotRegisteredAt(server);

        // Add config for this client
        bootstrapServer.getConfigStore().add(client.getEndpointName(), //
                givenBootstrapConfig() //
                        .adding(givenProtocol, bootstrapServer) //
                        .adding(givenProtocol, server) //
                        .build());

        // Start it and wait for registration
        client.start();
        server.waitForNewRegistrationOf(client);

        // check the client is registered
        assertThat(client).isRegisteredAt(server);
    }

    @TestAllTransportLayer
    public void bootstrap_tlv_only(Protocol givenProtocol, String givenClientEndpointProvider,
            String givenServerEndpointProvider, String givenBootstrapServerEndpointProvider)
            throws InvalidConfigurationException {
        // Create DM Server without security & start it
        server = givenServer.build();
        server.start();

        // Create and start bootstrap server
        bootstrapServer = givenBootstrapServer.build();
        bootstrapServer.start();

        // Create Client and check it is not already registered
        client = givenClient.connectingTo(bootstrapServer)//
                // if no preferred content format server should use TLV
                .preferring(null)//
                .supporting(ContentFormat.TLV) //
                .build();
        assertThat(client).isNotRegisteredAt(server);

        // TODO this should be replace by mockito ArgumentCaptor
        BootstrapRequestChecker contentFormatChecker = BootstrapRequestChecker.contentFormatChecker(ContentFormat.TLV);
        bootstrapServer.addListener(contentFormatChecker);

        // Add config for this client
        bootstrapServer.getConfigStore().add(client.getEndpointName(), //
                givenBootstrapConfig() //
                        .adding(givenProtocol, bootstrapServer) //
                        .adding(givenProtocol, server) //
                        .build());

        // Start it and wait for registration
        client.start();
        server.waitForNewRegistrationOf(client);

        // check the client is registered
        assertThat(client).isRegisteredAt(server);
        assertTrue(contentFormatChecker.isValid(), "not expected content format used");
    }

    @TestAllTransportLayer
    public void bootstrap_senmlcbor_only(Protocol givenProtocol, String givenClientEndpointProvider,
            String givenServerEndpointProvider, String givenBootstrapServerEndpointProvider)
            throws InvalidConfigurationException {
        // Create DM Server without security & start it
        server = givenServer.build();
        server.start();

        // Create and start bootstrap server
        bootstrapServer = givenBootstrapServer.build();
        bootstrapServer.start();

        // Create Client and check it is not already registered
        client = givenClient.connectingTo(bootstrapServer)//
                // if no preferred content format server should use TLV
                .preferring(ContentFormat.SENML_CBOR)//
                .supporting(ContentFormat.SENML_CBOR) //
                .build();
        assertThat(client).isNotRegisteredAt(server);

        // TODO this should be replace by mockito ArgumentCaptor
        BootstrapRequestChecker contentFormatChecker = BootstrapRequestChecker
                .contentFormatChecker(ContentFormat.SENML_CBOR);
        bootstrapServer.addListener(contentFormatChecker);

        // Add config for this client
        bootstrapServer.getConfigStore().add(client.getEndpointName(), //
                givenBootstrapConfig() //
                        .adding(givenProtocol, bootstrapServer) //
                        .adding(givenProtocol, server) //
                        .build());

        // Start it and wait for registration
        client.start();
        server.waitForNewRegistrationOf(client);

        // check the client is registered
        assertThat(client).isRegisteredAt(server);
        assertTrue(contentFormatChecker.isValid(), "not expected content format used");
    }

    @TestAllTransportLayer
    public void bootstrap_contentformat_from_config(Protocol givenProtocol, String givenClientEndpointProvider,
            String givenServerEndpointProvider, String givenBootstrapServerEndpointProvider)
            throws InvalidConfigurationException {
        // Create DM Server without security & start it
        server = givenServer.build();
        server.start();

        // Create and start bootstrap server
        bootstrapServer = givenBootstrapServer.build();
        bootstrapServer.start();

        // Create Client and check it is not already registered
        client = givenClient.connectingTo(bootstrapServer)//
                // if no preferred content format server should use TLV
                .preferring(ContentFormat.SENML_CBOR)//
                .build();
        assertThat(client).isNotRegisteredAt(server);

        // Add config for this client
        bootstrapServer.getConfigStore().add(client.getEndpointName(), //
                givenBootstrapConfig() //
                        .adding(givenProtocol, bootstrapServer) //
                        .adding(givenProtocol, server) //
                        .using(ContentFormat.SENML_JSON) //
                        .build());

        // TODO this should be replace by mockito ArgumentCaptor
        BootstrapRequestChecker contentFormatChecker = BootstrapRequestChecker
                .contentFormatChecker(ContentFormat.SENML_JSON);
        bootstrapServer.addListener(contentFormatChecker);

        // Start it and wait for registration
        client.start();
        server.waitForNewRegistrationOf(client);

        // check the client is registered
        assertThat(client).isRegisteredAt(server);
        assertTrue(contentFormatChecker.isValid(), "not expected content format used");
    }

    @TestAllTransportLayer
    public void bootstrapWithAdditionalAttributes(Protocol givenProtocol, String givenClientEndpointProvider,
            String givenServerEndpointProvider, String givenBootstrapServerEndpointProvider)
            throws InvalidConfigurationException {
        // Create DM Server without security & start it
        server = givenServer.build();
        server.start();

        // Create and start bootstrap server
        bootstrapServer = givenBootstrapServer.build();
        bootstrapServer.start();

        // Create Client with additional attributes and check it is not already registered
        Map<String, String> additionalAttributes = new HashMap<>();
        additionalAttributes.put("key1", "value1");
        additionalAttributes.put("imei", "2136872368");

        client = givenClient.connectingTo(bootstrapServer)//
                .withBootstrap(additionalAttributes)//
                .build();
        assertThat(client).isNotRegisteredAt(server);

        // Add config for this client
        bootstrapServer.getConfigStore().add(client.getEndpointName(), //
                givenBootstrapConfig() //
                        .adding(givenProtocol, bootstrapServer) //
                        .adding(givenProtocol, server) //
                        .build());

        // Start it and wait for registration
        client.start();
        server.waitForNewRegistrationOf(client);

        // check the client is registered
        assertThat(client).isRegisteredAt(server);

        // assert session contains additional attributes
        BootstrapSession bootstrapSession = bootstrapServer.verifyForSuccessfullBootstrap();

        assertThat(bootstrapSession.getBootstrapRequest().getAdditionalAttributes())
                .containsAllEntriesOf(additionalAttributes);
    }

//    @TestAllTransportLayer
//    public void bootstrapWithDiscoverOnRoot(Protocol givenProtocol, String givenClientEndpointProvider,
//            String givenServerEndpointProvider, String givenBootstrapServerEndpointProvider)
//            throws InvalidConfigurationException {
//        // Create DM Server without security & start it
//        server = givenServer.build();
//        server.start();
//
//        // Create and start bootstrap server
//        bootstrapServer = givenBootstrapServer.build();
//        bootstrapServer.start();
//
//        // Create Client and check it is not already registered
//        client = givenClient.connectingTo(bootstrapServer).build();
//        assertThat(client).isNotRegisteredAt(server);
//
//        // Add config for this client
//        bootstrapServer.getConfigStore().add(client.getEndpointName(), //
//                givenBootstrapConfig() //
//                        .adding(givenProtocol, bootstrapServer) //
//                        .adding(givenProtocol, server) //
//                        .build());
//        helper.createBootstrapServer(null, null, new BootstrapDiscoverRequest());
//
//        // Start it and wait for registration
//        client.start();
//        server.waitForNewRegistrationOf(client);
//
//        // check the client is registered
//        assertThat(client).isRegisteredAt(server);
//
//        // check the client is registered
//        helper.assertClientRegisterered();
//        assertNotNull(helper.lastCustomResponse);
//        assertTrue(helper.lastCustomResponse instanceof BootstrapDiscoverResponse);
//        BootstrapDiscoverResponse lastDiscoverAnswer = (BootstrapDiscoverResponse) helper.lastCustomResponse;
//        assertEquals(ResponseCode.CONTENT, lastDiscoverAnswer.getCode());
//        assertEquals(String.format(
//                "</>;lwm2m=1.0,</0>;ver=1.1,</0/0>;uri=\"coap://%s:%d\",</1>;ver=1.1,</3>;ver=1.1,</3/0>,</21>;ver=2.0",
//                helper.bootstrapServer.getEndpoint(Protocol.COAP).getURI().getHost(),
//                helper.bootstrapServer.getEndpoint(Protocol.COAP).getURI().getPort()),
//                linkSerializer.serializeCoreLinkFormat(lastDiscoverAnswer.getObjectLinks()));
//    }

//    @Test
//    public void bootstrapWithDiscoverOnRootThenRebootstrap() throws InvalidRequestException, InterruptedException {
//        // Create DM Server without security & start it
//        helper.createServer();
//        helper.server.start();
//
//        // Create and start bootstrap server
//        helper.createBootstrapServer(null, null, new BootstrapDiscoverRequest());
//        helper.bootstrapServer.start();
//
//        // Create Client and check it is not already registered
//        helper.createClient();
//        helper.assertClientNotRegisterered();
//
//        // Start it and wait for registration
//        helper.client.start();
//        helper.waitForBootstrapFinishedAtClientSide(1);
//        helper.waitForRegistrationAtServerSide(1);
//
//        // check the client is registered
//        helper.assertClientRegisterered();
//        assertTrue(helper.lastCustomResponse instanceof BootstrapDiscoverResponse);
//        BootstrapDiscoverResponse lastDiscoverAnswer = (BootstrapDiscoverResponse) helper.lastCustomResponse;
//        assertEquals(ResponseCode.CONTENT, lastDiscoverAnswer.getCode());
//        assertEquals(String.format(
//                "</>;lwm2m=1.0,</0>;ver=1.1,</0/0>;uri=\"coap://%s:%d\",</1>;ver=1.1,</3>;ver=1.1,</3/0>,</21>;ver=2.0",
//                helper.bootstrapServer.getEndpoint(Protocol.COAP).getURI().getHost(),
//                helper.bootstrapServer.getEndpoint(Protocol.COAP).getURI().getPort()),
//                linkSerializer.serializeCoreLinkFormat(lastDiscoverAnswer.getObjectLinks()));
//
//        // re-bootstrap
//        try {
//            ExecuteResponse response = helper.server.send(helper.getCurrentRegistration(),
//                    new ExecuteRequest("/1/0/9"));
//            assertTrue(response.isSuccess());
//        } catch (RequestCanceledException e) {
//            // request can be cancelled if server does not received the execute response before the de-registration
//            // so we just ignore this error.
//        }
//
//        // wait bootstrap finished
//        helper.waitForBootstrapFinishedAtClientSide(1);
//
//        // check last discover response
//        assertTrue(helper.lastCustomResponse instanceof BootstrapDiscoverResponse);
//        lastDiscoverAnswer = (BootstrapDiscoverResponse) helper.lastCustomResponse;
//        assertEquals(ResponseCode.CONTENT, lastDiscoverAnswer.getCode());
//        assertEquals(String.format(
//                "</>;lwm2m=1.0,</0>;ver=1.1,</0/0>;uri=\"coap://%s:%d\",</0/1>;ssid=2222;uri=\"coap://%s:%d\",</1>;ver=1.1,</1/0>;ssid=2222,</3>;ver=1.1,</3/0>,</21>;ver=2.0",
//                helper.bootstrapServer.getEndpoint(Protocol.COAP).getURI().getHost(),
//                helper.bootstrapServer.getEndpoint(Protocol.COAP).getURI().getPort(),
//                helper.server.getEndpoint(Protocol.COAP).getURI().getHost(),
//                helper.server.getEndpoint(Protocol.COAP).getURI().getPort()),
//                linkSerializer.serializeCoreLinkFormat(lastDiscoverAnswer.getObjectLinks()));
//
//    }
//
//    @Test
//    public void bootstrapWithDiscoverOnDevice() {
//        // Create DM Server without security & start it
//        helper.createServer();
//        helper.server.start();
//
//        // Create and start bootstrap server
//        helper.createBootstrapServer(null, null, new BootstrapDiscoverRequest(3));
//        helper.bootstrapServer.start();
//
//        // Create Client and check it is not already registered
//        helper.createClient();
//        helper.assertClientNotRegisterered();
//
//        // Start it and wait for registration
//        helper.client.start();
//        helper.waitForRegistrationAtServerSide(1);
//
//        // check the client is registered
//        helper.assertClientRegisterered();
//        assertTrue(helper.lastCustomResponse instanceof BootstrapDiscoverResponse);
//        BootstrapDiscoverResponse lastDiscoverAnswer = (BootstrapDiscoverResponse) helper.lastCustomResponse;
//        assertEquals(ResponseCode.CONTENT, lastDiscoverAnswer.getCode());
//        assertEquals("</>;lwm2m=1.0,</3>;ver=1.1,</3/0>",
//                linkSerializer.serializeCoreLinkFormat(lastDiscoverAnswer.getObjectLinks()));
//    }
//
//    @Test
//    public void bootstrapWithReadOnServerThenRebootstrap() throws InvalidRequestException, InterruptedException {
//        // Create DM Server without security & start it
//        helper.createServer();
//        helper.server.start();
//
//        // Create and start bootstrap server
//        helper.createBootstrapServer(null, null, new BootstrapReadRequest(1));
//        helper.bootstrapServer.start();
//
//        // Create Client and check it is not already registered
//        helper.createClient();
//        helper.assertClientNotRegisterered();
//
//        // Start it and wait for registration
//        helper.client.start();
//        helper.waitForBootstrapFinishedAtClientSide(1);
//        helper.waitForRegistrationAtServerSide(1);
//
//        // check the client is registered
//        helper.assertClientRegisterered();
//        assertTrue(helper.lastCustomResponse instanceof BootstrapReadResponse);
//        BootstrapReadResponse lastReadResponse = (BootstrapReadResponse) helper.lastCustomResponse;
//        assertEquals(ResponseCode.CONTENT, lastReadResponse.getCode());
//        assertEquals(new LwM2mObject(1), lastReadResponse.getContent());
//
//        // re-bootstrap
//        try {
//            ExecuteResponse response = helper.server.send(helper.getCurrentRegistration(),
//                    new ExecuteRequest("/1/0/9"));
//            assertTrue(response.isSuccess());
//        } catch (RequestCanceledException e) {
//            // request can be cancelled if server does not received the execute response before the de-registration
//            // so we just ignore this error.
//        }
//
//        // wait bootstrap finished
//        helper.waitForBootstrapFinishedAtClientSide(5);
//
//        // check last discover response
//        assertTrue(helper.lastCustomResponse instanceof BootstrapReadResponse);
//        lastReadResponse = (BootstrapReadResponse) helper.lastCustomResponse;
//        assertEquals(ResponseCode.CONTENT, lastReadResponse.getCode());
//    }
//
//    @Test
//    public void bootstrap_create_2_bsserver() {
//        // Create DM Server without security & start it
//        helper.createServer();
//        helper.server.start();
//
//        // Create and start bootstrap server
//        helper.createBootstrapServer(null, helper.unsecuredBootstrapStoreWithBsSecurityInstanceIdAt(0));
//        helper.bootstrapServer.start();
//
//        // Create Client with bootstrap server config at /0/10
//        helper.createClient(helper.withoutSecurityAndInstanceId(10), null);
//        helper.assertClientNotRegisterered();
//
//        // Start BS.
//        // Server will delete /0 but Client will not delete /0/10 instance (because bs server is not deletable)
//        // Then a new BS server will be written at /0/0
//        //
//        // So bootstrap should failed because 2 bs server in Security Object is not a valid state.
//        // see https://github.com/OpenMobileAlliance/OMA_LwM2M_for_Developers/issues/523
//        helper.client.start();
//
//        // ensure bootstrap session failed because of invalid state
//        helper.waitForInconsistentStateAtClientSide(1);
//        helper.waitForBootstrapFailureAtServerSide(1);
//        assertEquals(BootstrapFailureCause.FINISH_FAILED, helper.getLastCauseOfBootstrapFailure());
//    }
//
//    @Test
//    public void bootstrap_with_auto_id_for_security_object() {
//        // Create DM Server without security & start it
//        helper.createServer();
//        helper.server.start();
//
//        // Create and start bootstrap server
//        helper.createBootstrapServer(null, helper.unsecuredBootstrapWithAutoID());
//        helper.bootstrapServer.start();
//
//        // Create Client with bootstrap server config at /0/10
//        helper.createClient(helper.withoutSecurityAndInstanceId(10), null);
//        helper.assertClientNotRegisterered();
//
//        // Start BS.
//        // Server will delete /0 but Client will not delete /0/10 instance (because bs server is not deletable)
//        // Then a new BS server will be written at /0/0
//        //
//        // So bootstrap should failed because 2 bs server in Security Object is not a valid state.
//        // see https://github.com/OpenMobileAlliance/OMA_LwM2M_for_Developers/issues/523
//        helper.client.start();
//
//        // ensure bootstrap session succeed
//        helper.waitForBootstrapFinishedAtClientSide(1);
//        helper.waitForBootstrapSuccessAtServerSide(1);
//        helper.waitForRegistrationAtServerSide(1);
//        helper.assertClientRegisterered();
//    }
//
//    @Test
//    public void bootstrap_delete_access_control_and_connectivity_statistics() {
//        // Create DM Server without security & start it
//        helper.createServer();
//        helper.server.start();
//
//        // Create and start bootstrap server
//        helper.createBootstrapServer(null,
//                helper.deleteSecurityStore(LwM2mId.ACCESS_CONTROL, LwM2mId.CONNECTIVITY_STATISTICS));
//        helper.bootstrapServer.start();
//
//        // Create Client and check it is not already registered
//        ObjectsInitializer initializer = new TestObjectsInitializer();
//        initializer.setInstancesForObject(LwM2mId.ACCESS_CONTROL, new SimpleInstanceEnabler());
//        initializer.setInstancesForObject(LwM2mId.CONNECTIVITY_STATISTICS, new SimpleInstanceEnabler());
//        initializer.setInstancesForObject(LwM2mId.LOCATION, new SimpleInstanceEnabler());
//        helper.createClient(helper.withoutSecurity(), initializer);
//        helper.assertClientNotRegisterered();
//
//        // Start it and wait for bootstrap finished
//        helper.client.start();
//        helper.waitForBootstrapFinishedAtClientSide(1);
//
//        // ensure instances are deleted
//        ReadResponse response = helper.client.getObjectTree().getObjectEnabler(LwM2mId.ACCESS_CONTROL)
//                .read(ServerIdentity.SYSTEM, new ReadRequest(LwM2mId.ACCESS_CONTROL));
//        assertTrue(((LwM2mObject) response.getContent()).getInstances().isEmpty(), "ACL instance is not deleted");
//
//        response = helper.client.getObjectTree().getObjectEnabler(LwM2mId.CONNECTIVITY_STATISTICS)
//                .read(ServerIdentity.SYSTEM, new ReadRequest(LwM2mId.CONNECTIVITY_STATISTICS));
//        assertTrue(((LwM2mObject) response.getContent()).getInstances().isEmpty(),
//                "Connectvity instance is not deleted");
//
//        // ensure other instances are not deleted.
//        response = helper.client.getObjectTree().getObjectEnabler(LwM2mId.DEVICE).read(ServerIdentity.SYSTEM,
//                new ReadRequest(LwM2mId.DEVICE));
//        assertFalse(((LwM2mObject) response.getContent()).getInstances().isEmpty(), "Device instance is deleted");
//
//        response = helper.client.getObjectTree().getObjectEnabler(LwM2mId.SECURITY).read(ServerIdentity.SYSTEM,
//                new ReadRequest(LwM2mId.SECURITY));
//        assertFalse(((LwM2mObject) response.getContent()).getInstances().isEmpty(), "Security instance is deleted");
//
//        response = helper.client.getObjectTree().getObjectEnabler(LwM2mId.LOCATION).read(ServerIdentity.SYSTEM,
//                new ReadRequest(LwM2mId.LOCATION));
//        assertFalse(((LwM2mObject) response.getContent()).getInstances().isEmpty(), "Location instance is deleted");
//    }
//
//    @Test
//    public void bootstrapDeleteAll() {
//        // Create DM Server without security & start it
//        helper.createServer();
//        helper.server.start();
//
//        // Create and start bootstrap server
//        helper.createBootstrapServer(null, helper.deleteSecurityStore("/"));
//        helper.bootstrapServer.start();
//
//        // Create Client and check it is not already registered
//        ObjectsInitializer initializer = new TestObjectsInitializer();
//        initializer.setInstancesForObject(LwM2mId.ACCESS_CONTROL, new SimpleInstanceEnabler());
//        initializer.setInstancesForObject(LwM2mId.CONNECTIVITY_STATISTICS, new SimpleInstanceEnabler());
//        helper.createClient(helper.withoutSecurity(), initializer);
//        helper.assertClientNotRegisterered();
//
//        // Start it and wait for bootstrap finished
//        helper.client.start();
//        helper.waitForBootstrapFinishedAtClientSide(1);
//
//        // ensure instances are deleted except device instance and bootstrap server
//        for (LwM2mObjectEnabler enabler : helper.client.getObjectTree().getObjectEnablers().values()) {
//            ReadResponse response = enabler.read(ServerIdentity.SYSTEM, new ReadRequest(enabler.getId()));
//            LwM2mObject responseValue = (LwM2mObject) response.getContent();
//            if (enabler.getId() == LwM2mId.DEVICE) {
//                assertTrue(responseValue.getInstances().size() == 1, "The Device instance should still be here");
//            } else if (enabler.getId() == LwM2mId.SECURITY) {
//                assertTrue(((LwM2mObject) response.getContent()).getInstances().size() == 1,
//                        "Only bootstrap security instance should be here");
//                LwM2mObjectInstance securityInstance = responseValue.getInstances().values().iterator().next();
//                assertTrue(securityInstance.getResource(1).getValue() == Boolean.TRUE,
//                        "Only bootstrap security instance should be here");
//            } else {
//                assertTrue(responseValue.getInstances().isEmpty(),
//                        enabler.getObjectModel().name + " instance is not deleted");
//            }
//        }
//    }
//
//    @Test
//    public void bootstrapWithAcl() {
//        // Create DM Server without security & start it
//        helper.createServer();
//        helper.server.start();
//
//        // Create and start bootstrap server
//        helper.createBootstrapServer(null, helper.unsecuredWithAclBootstrapStore());
//        helper.bootstrapServer.start();
//
//        // Create Client and check it is not already registered
//        ObjectsInitializer initializer = new TestObjectsInitializer();
//        initializer.setInstancesForObject(LwM2mId.ACCESS_CONTROL, new SimpleInstanceEnabler());
//        helper.createClient(helper.withoutSecurity(), initializer);
//        helper.assertClientNotRegisterered();
//
//        // Start it and wait for registration
//        helper.client.start();
//        helper.waitForRegistrationAtServerSide(1);
//
//        // check the client is registered
//        helper.assertClientRegisterered();
//
//        // ensure ACL is correctly set
//        ReadResponse response = helper.client.getObjectTree().getObjectEnabler(2).read(ServerIdentity.SYSTEM,
//                new ReadRequest(2));
//        LwM2mObject acl = (LwM2mObject) response.getContent();
//        assertThat(acl.getInstances().keySet(), hasItems(0, 1));
//        LwM2mObjectInstance instance = acl.getInstance(0);
//        assertEquals(3l, instance.getResource(0).getValue());
//        assertEquals(0l, instance.getResource(1).getValue());
//        assertEquals(1l, instance.getResource(2).getValue(3333));
//        assertEquals(2222l, instance.getResource(3).getValue());
//        instance = acl.getInstance(1);
//        assertEquals(4l, instance.getResource(0).getValue());
//        assertEquals(0l, instance.getResource(1).getValue());
//        assertEquals(2222l, instance.getResource(3).getValue());
//    }
//
//    @Test
//    public void bootstrapSecureWithPSK() {
//        // Create DM Server without security & start it
//        helper.createServer();
//        helper.server.start();
//
//        // Create and start bootstrap server
//        helper.createBootstrapServer(helper.bsSecurityStore(SecurityMode.PSK));
//        helper.bootstrapServer.start();
//
//        // Create PSK Client and check it is not already registered
//        helper.createPSKClient(GOOD_PSK_ID, GOOD_PSK_KEY);
//        helper.assertClientNotRegisterered();
//
//        // Start it and wait for registration
//        helper.client.start();
//        helper.waitForRegistrationAtServerSide(1);
//
//        // check the client is registered
//        helper.assertClientRegisterered();
//    }
//
//    @Test
//    public void bootstrapSecureWithBadPSKKey() {
//        // Create DM Server without security & start it
//        helper.createServer();
//        helper.server.start();
//
//        // Create and start bootstrap server
//        helper.createBootstrapServer(helper.bsSecurityStore(SecurityMode.PSK));
//        helper.bootstrapServer.start();
//
//        // Create PSK Client with bad credentials and check it is not already registered
//        helper.createRPKClient();
//        helper.assertClientNotRegisterered();
//
//        // Start it and wait for registration
//        helper.client.start();
//        helper.ensureNoRegistration(1);
//
//        // check the client is not registered
//        helper.assertClientNotRegisterered();
//    }
//
//    @Test
//    public void bootstrapSecureWithRPK() {
//        // Create DM Server without security & start it
//        helper.createServer();
//        helper.server.start();
//
//        // Create and start bootstrap server
//        helper.createBootstrapServer(helper.bsSecurityStore(SecurityMode.RPK));
//        helper.bootstrapServer.start();
//
//        // Create RPK Client and check it is not already registered
//        helper.createRPKClient();
//        helper.assertClientNotRegisterered();
//
//        // Start it and wait for registration
//        helper.client.start();
//        helper.waitForRegistrationAtServerSide(1);
//
//        // check the client is registered
//        helper.assertClientRegisterered();
//    }
//
//    @Test
//    public void bootstrapToPSKServer() throws NonUniqueSecurityInfoException {
//        // Create DM Server & start it
//        helper.createServer(); // default server support PSK
//        helper.server.start();
//
//        // Create and start bootstrap server
//        helper.createBootstrapServer(null, helper.pskBootstrapStore());
//        helper.bootstrapServer.start();
//
//        // Create Client and check it is not already registered
//        helper.createClient();
//        helper.assertClientNotRegisterered();
//
//        // Add client credentials to the server
//        helper.getSecurityStore()
//                .add(SecurityInfo.newPreSharedKeyInfo(helper.getCurrentEndpoint(), GOOD_PSK_ID, GOOD_PSK_KEY));
//
//        // Start it and wait for registration
//        helper.client.start();
//        helper.waitForRegistrationAtServerSide(1);
//
//        // check the client is registered
//        helper.assertClientRegisterered();
//    }
//
//    @Test
//    public void bootstrapToRPKServer() throws NonUniqueSecurityInfoException {
//        // Create DM Server with RPK support & start it
//        helper.createServerWithRPK();
//        helper.server.start();
//
//        // Create and start bootstrap server
//        helper.createBootstrapServer(null, helper.rpkBootstrapStore());
//        helper.bootstrapServer.start();
//
//        // Create Client and check it is not already registered
//        helper.createClient();
//        helper.assertClientNotRegisterered();
//
//        // Add client credentials to the server
//        helper.getSecurityStore()
//                .add(SecurityInfo.newRawPublicKeyInfo(helper.getCurrentEndpoint(), helper.clientPublicKey));
//
//        // Start it and wait for registration
//        helper.client.start();
//        helper.waitForRegistrationAtServerSide(1);
//
//        // check the client is registered
//        helper.assertClientRegisterered();
//    }
//
//    @Test
//    public void bootstrapUnsecuredToServerWithOscore() throws NonUniqueSecurityInfoException {
//        helper.createOscoreServer();
//        helper.server.start();
//
//        helper.createBootstrapServer(null, helper.unsecuredBootstrapStoreWithOscoreServer());
//        helper.bootstrapServer.start();
//
//        // Check client is not registered
//        helper.createClient();
//        helper.assertClientNotRegisterered();
//
//        helper.getSecurityStore()
//                .add(SecurityInfo.newOscoreInfo(helper.getCurrentEndpoint(), getServerOscoreSetting()));
//
//        // Start it and wait for registration
//        helper.client.start();
//
//        helper.waitForRegistrationAtServerSide(1);
//
//        // Check client is well registered
//        helper.assertClientRegisterered();
//    }
//
//    @Test
//    public void bootstrapViaOscoreToServerWithOscore() throws NonUniqueSecurityInfoException {
//        helper.createOscoreServer();
//        helper.server.start();
//
//        helper.createOscoreBootstrapServer(helper.bsOscoreSecurityStore(),
//                helper.oscoreBootstrapStoreWithOscoreServer());
//        helper.bootstrapServer.start();
//
//        // Check client is not registered
//        helper.createOscoreOnlyBootstrapClient();
//        helper.assertClientNotRegisterered();
//
//        helper.getSecurityStore()
//                .add(SecurityInfo.newOscoreInfo(helper.getCurrentEndpoint(), getServerOscoreSetting()));
//
//        // Start it and wait for registration
//        helper.client.start();
//
//        helper.waitForRegistrationAtServerSide(1);
//
//        // Check client is well registered
//        helper.assertClientRegisterered();
//    }
//
//    @Test
//    public void bootstrapViaOscoreToUnsecuredServer() throws OSException {
//        helper.createServer();
//        helper.server.start();
//
//        helper.createOscoreBootstrapServer(helper.bsOscoreSecurityStore(),
//                helper.oscoreBootstrapStoreWithUnsecuredServer());
//        helper.bootstrapServer.start();
//
//        // Check client is not registered
//        helper.createOscoreOnlyBootstrapClient();
//        helper.assertClientNotRegisterered();
//
//        // Start it and wait for registration
//        helper.client.start();
//
//        helper.waitForRegistrationAtServerSide(1);
//
//        // Check client is well registered
//        helper.assertClientRegisterered();
//    }
}
