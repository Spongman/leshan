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

import org.eclipse.leshan.core.endpoint.Protocol;
import org.eclipse.leshan.integration.tests.util.LeshanTestServerBuilder;
import org.eclipse.leshan.server.redis.RedisRegistrationStore;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.util.Pool;

public abstract class RedisRegistrationTest3 extends RegistrationTest3 {

    public RedisRegistrationTest3(Protocol protocol, String serverProvider, String clientProvider) {
        super(protocol, serverProvider, clientProvider);
    }

    public static class CoAPCaliforniumCalifornium extends RedisRegistrationTest3 {
        public CoAPCaliforniumCalifornium() {
            super(Protocol.COAP, "Californium", "Californium");
        }
    }

    public static class CoAPCaliforniumJavaCoap extends RedisRegistrationTest3 {
        public CoAPCaliforniumJavaCoap() {
            super(Protocol.COAP, "Californium", "java-coap");
        }
    }

    @Override
    protected LeshanTestServerBuilder givenServerUsing(Protocol givenProtocol) {
        LeshanTestServerBuilder builder = super.givenServerUsing(givenProtocol);

        // Create redis store
        Pool<Jedis> jedis = createJedisPool();
        builder.setRegistrationStore(new RedisRegistrationStore(jedis));

        return builder;
    }

    private Pool<Jedis> createJedisPool() {
        String redisURI = System.getenv("REDIS_URI");
        if (redisURI != null && !redisURI.isEmpty()) {
            return new JedisPool(redisURI);
        } else {
            return new JedisPool();
        }
    }
}
