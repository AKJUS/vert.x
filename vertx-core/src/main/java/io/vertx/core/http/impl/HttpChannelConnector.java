/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.ClientMetrics;

import java.time.Duration;

/**
 * Performs the channel configuration and connection according to the client options and the protocol version.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpChannelConnector {

  Future<HttpClientConnection> httpConnect(ContextInternal context,
                                           SocketAddress server,
                                           HostAndPort authority,
                                           HttpConnectParams params,
                                           long maxLifetimeMillis,
                                           ClientMetrics<?, ?, ?> metrics);

  Future<Void> shutdown(Duration timeout);

  Future<Void> close();

}
