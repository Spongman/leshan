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
package org.eclipse.leshan.integration.tests;

import org.eclipse.leshan.core.SecurityMode;
import org.eclipse.leshan.core.endpoint.Protocol;
import org.eclipse.leshan.core.request.ContentFormat;
import org.eclipse.leshan.integration.tests.util.LeshanTestBootstrapServer;
import org.eclipse.leshan.integration.tests.util.LeshanTestServer;
import org.eclipse.leshan.server.bootstrap.BootstrapConfig;
import org.eclipse.leshan.server.bootstrap.BootstrapConfig.ServerConfig;
import org.eclipse.leshan.server.bootstrap.BootstrapConfig.ServerSecurity;

public class BootstrapConfigTestBuilder {

    private final BootstrapConfig bsConfig;

    public BootstrapConfigTestBuilder() {
        bsConfig = new BootstrapConfig();
        bsConfig.autoIdForSecurityObject = false;
        bsConfig.contentFormat = null;
    }

    public BootstrapConfigTestBuilder adding(Protocol protocol, LeshanTestBootstrapServer bootstrapServer) {
        return adding(protocol, bootstrapServer, 0);
    }

    public BootstrapConfigTestBuilder adding(Protocol protocol, LeshanTestBootstrapServer bootstrapServer,
            int bootstrapInstanceId) {

        // security for BS server
        ServerSecurity bsSecurity = new ServerSecurity();
        bsSecurity.bootstrapServer = true;
        bsSecurity.uri = bootstrapServer.getEndpoint(protocol).getURI().toString();
        bsSecurity.securityMode = SecurityMode.NO_SEC;
        bsConfig.security.put(0, bsSecurity);
        return this;
    }

    public BootstrapConfigTestBuilder adding(Protocol protocol, LeshanTestServer server) {

        // security for DM server
        ServerSecurity dmSecurity = new ServerSecurity();
        dmSecurity.uri = server.getEndpoint(protocol).getURI().toString();
        dmSecurity.serverId = 2222;
        dmSecurity.securityMode = SecurityMode.NO_SEC;
        bsConfig.security.put(1, dmSecurity);

        // DM server
        ServerConfig dmConfig = new ServerConfig();
        dmConfig.shortId = 2222;
        bsConfig.servers.put(0, dmConfig);
        return this;
    }

    public BootstrapConfigTestBuilder using(ContentFormat contentFormat) {
        bsConfig.contentFormat = contentFormat;
        return this;
    }

    public BootstrapConfig build() {
        return bsConfig;
    }

    public static BootstrapConfigTestBuilder givenBootstrapConfig() {
        return new BootstrapConfigTestBuilder();
    }

}
