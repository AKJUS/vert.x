/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.pool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A connection pool.
 */
public interface ConnectionPool<C> {

  /**
   * This provider reuse the context argument when it can be cast or unwrapped to an event-loop context, otherwise
   * it returns a new event-loop context that reuses the Netty event-loop of the context argument.
   */
  Function<ContextInternal, ContextInternal> EVENT_LOOP_CONTEXT_PROVIDER = ctx -> {
    VertxInternal vertx = ctx.owner();
    return vertx.contextBuilder()
      .withEventLoop(ctx.nettyEventLoop())
      .withWorkerPool(vertx.workerPool())
      .build();
  };

  static <C> ConnectionPool<C> pool(PoolConnector<C> connector, int[] maxSizes) {
    return new SimpleConnectionPool<>(connector, maxSizes);
  }

  static <C> ConnectionPool<C> pool(PoolConnector<C> connector, int[] maxSizes, int maxWaiters) {
    return new SimpleConnectionPool<>(connector, maxSizes, maxWaiters);
  }

  /**
   * Set a {@code selector} function that decides the best connection to use.
   *
   * <p> The selector is called with the waiter and a list of candidate connections,
   * the selector must return a connection with a positive {@link PoolConnection#available()}.
   *
   * <p>The selector can return {@code null} if no suitable connection is found. Then the pool will
   * attempt to create a new connection or chose another available connection.
   *
   * @param selector the selector function
   * @return a reference to this, so the API can be used fluently
   */
  ConnectionPool<C> connectionSelector(BiFunction<PoolWaiter<C>, List<PoolConnection<C>>, PoolConnection<C>> selector);

  /**
   * Set a function that provides an event-loop context out of the specified context. The pool will use the provider
   * when an event-loop context is required for creating a new connection.
   *
   * <p> The default provider is {@link #EVENT_LOOP_CONTEXT_PROVIDER}.
   *
   * @param contextProvider the provider to be used by the pool
   * @return a reference to this, so the API can be used fluently
   */
  ConnectionPool<C> contextProvider(Function<ContextInternal, ContextInternal> contextProvider);

  /**
   * Acquire a connection from the pool.
   *
   * @param context the context
   * @param kind the connection kind wanted which is an index in the max size array provided when constructing the pool
   */
  void acquire(ContextInternal context, int kind, Completable<Lease<C>> handler);

  /**
   * Acquire a connection from the pool.
   *
   * @param context the context
   * @param listener the waiter event listener
   * @param kind the connection kind wanted which is an index in the max size array provided when constructing the pool
   */
  void acquire(ContextInternal context, PoolWaiter.Listener<C> listener, int kind, Completable<Lease<C>> handler);

  /**
   * Cancel a waiter.
   *
   * <p> The completion {@code handler} receives {@code true} when the waiter
   * has been cancelled successfully (e.g the waiter was in the wait queue or
   * waiting for a connection), {@code false} when the waiter has been already
   * notified with a result.
   *
   * @param waiter the waiter to cancel
   */
  void cancel(PoolWaiter<C> waiter, Completable<Boolean> handler);

  /**
   * <p> Evict connections from the pool with a {@code predicate}, only unused connection can be evicted.
   *
   * <p> The operation returns the list of connections evicted from the pool as is.
   *
   * @param predicate to determine whether a connection should be evicted
   */
  void evict(Predicate<C> predicate, Completable<List<C>> handler);

  /**
   * Close the pool.
   *
   * <p> This will not close the connections, instead a list of connections to be closed is returned
   * to the completion {@code handler}.
   */
  void close(Completable<List<Future<C>>> handler);

  /**
   * @return the number of managed connections - the program should not use the value
   *         to take decisions, this can be used for statistic or testing purpose
   */
  int size();

  /**
   * @return the number of waiters - the program should not use the value
   *         to take decisions, this can be used for statistic or testing purpose
   */
  int waiters();

  /**
   * @return the pool capacity  - the program should not use the value
   *         to take decisions, this can be used for statistic or testing purpose
   */
  int capacity();

  /**
   * @return the number of in-flight requests - the program should not use the value
   *         to take decisions, this can be used for statistic or testing purpose
   */
  int requests();
}
