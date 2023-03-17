/*******************************************************************************
 * Copyright (c) 2023 Sierra Wireless and others.
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
 *******************************************************************************/
package org.eclipse.leshan.integration.tests.util;

import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;

import org.eclipse.leshan.core.link.lwm2m.LwM2mLinkParser;
import org.eclipse.leshan.core.node.codec.LwM2mDecoder;
import org.eclipse.leshan.core.node.codec.LwM2mEncoder;
import org.eclipse.leshan.server.bootstrap.BootstrapHandlerFactory;
import org.eclipse.leshan.server.bootstrap.BootstrapSession;
import org.eclipse.leshan.server.bootstrap.BootstrapSessionListener;
import org.eclipse.leshan.server.bootstrap.BootstrapSessionManager;
import org.eclipse.leshan.server.bootstrap.EditableBootstrapConfigStore;
import org.eclipse.leshan.server.bootstrap.LeshanBootstrapServer;
import org.eclipse.leshan.server.bootstrap.endpoint.LwM2mBootstrapServerEndpointsProvider;
import org.eclipse.leshan.server.security.BootstrapSecurityStore;
import org.eclipse.leshan.server.security.ServerSecurityInfo;
import org.mockito.ArgumentCaptor;

public class LeshanTestBootstrapServer extends LeshanBootstrapServer {

    private final EditableBootstrapConfigStore configStore;
    private final BootstrapSessionListener bootstrapSession;

    public LeshanTestBootstrapServer(LwM2mBootstrapServerEndpointsProvider endpointsProvider,
            BootstrapSessionManager bsSessionManager, BootstrapHandlerFactory bsHandlerFactory, LwM2mEncoder encoder,
            LwM2mDecoder decoder, LwM2mLinkParser linkParser, BootstrapSecurityStore securityStore,
            ServerSecurityInfo serverSecurityInfo, //
            // arguments only needed for LeshanTestBootstrapServer
            EditableBootstrapConfigStore configStore) {

        super(endpointsProvider, bsSessionManager, bsHandlerFactory, encoder, decoder, linkParser, securityStore,
                serverSecurityInfo);
        // keep store reference for getter.
        this.configStore = configStore;

        // add mocked listener
        bootstrapSession = mock(BootstrapSessionListener.class);
        addListener(bootstrapSession);
    }

    public EditableBootstrapConfigStore getConfigStore() {
        return configStore;
    }

    public BootstrapSession waitForSuccessfullBootstrap(int timeout, TimeUnit unit) {
        final ArgumentCaptor<BootstrapSession> c = ArgumentCaptor.forClass(BootstrapSession.class);
        verify(bootstrapSession, timeout(unit.toMillis(timeout)).times(1)).sessionInitiated(notNull(), notNull());
        verify(bootstrapSession, timeout(unit.toMillis(timeout)).times(1)).authorized(c.capture());
        verify(bootstrapSession, timeout(unit.toMillis(timeout)).times(1)).end(c.getValue());
        return c.getValue();
    }

    public BootstrapSession verifyForSuccessfullBootstrap() {
        return waitForSuccessfullBootstrap(0, TimeUnit.SECONDS);
    }
}
