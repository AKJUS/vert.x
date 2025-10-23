package io.vertx.core.http.impl;


import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;
import io.vertx.core.net.endpoint.EndpointResolver;
import io.vertx.core.net.impl.tcp.NetClientBuilder;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.function.Function;

public final class HttpClientBuilderInternal implements HttpClientBuilder {

  private final VertxInternal vertx;
  private HttpClientOptions clientOptions;
  private PoolOptions poolOptions;
  private Handler<HttpConnection> connectHandler;
  private Function<HttpClientResponse, Future<RequestOptions>> redirectHandler;
  private AddressResolver<?> addressResolver;
  private LoadBalancer loadBalancer = null;

  public HttpClientBuilderInternal(VertxInternal vertx) {
    this.vertx = vertx;
  }

  @Override
  public HttpClientBuilder with(HttpClientOptions options) {
    this.clientOptions = options;
    return this;
  }

  @Override
  public HttpClientBuilder with(PoolOptions options) {
    this.poolOptions = options;
    return this;
  }

  @Override
  public HttpClientBuilder withConnectHandler(Handler<HttpConnection> handler) {
    this.connectHandler = handler;
    return this;
  }

  @Override
  public HttpClientBuilder withRedirectHandler(Function<HttpClientResponse, Future<RequestOptions>> handler) {
    this.redirectHandler = handler;
    return this;
  }

  @Override
  public HttpClientBuilder withAddressResolver(AddressResolver<?> resolver) {
    this.addressResolver = resolver;
    return this;
  }

  @Override
  public HttpClientBuilder withLoadBalancer(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
    return this;
  }

  private CloseFuture resolveCloseFuture() {
    ContextInternal context = vertx.getContext();
    return context != null ? context.closeFuture() : vertx.closeFuture();
  }

  private EndpointResolver endpointResolver(HttpClientOptions co) {
    LoadBalancer _loadBalancer = loadBalancer;
    AddressResolver<?> _addressResolver = addressResolver;
    if (_loadBalancer != null) {
      if (_addressResolver == null) {
        _addressResolver = vertx.nameResolver();
      }
    } else {
      if (_addressResolver != null) {
        _loadBalancer = LoadBalancer.ROUND_ROBIN;
      }
    }
    if (_addressResolver != null) {
      return new EndpointResolverImpl<>(vertx, _addressResolver.endpointResolver(vertx), _loadBalancer, co.getKeepAliveTimeout() * 1000);
    }
    return null;
  }

  private HttpClientImpl createHttpClientImpl(EndpointResolver resolver,
                                              Handler<HttpConnection> connectionHandler,
                                              Function<HttpClientResponse, Future<RequestOptions>> redirectHandler,
                                              HttpClientOptions co,
                                              PoolOptions po) {
    HttpClientMetrics<?, ?, ?> metrics = vertx.metrics() != null ? vertx.metrics().createHttpClientMetrics(co) : null;
    NetClientInternal tcpClient = new NetClientBuilder(vertx, new NetClientOptions(co).setProxyOptions(null)).metrics(metrics).build();
    HttpChannelConnector channelConnector = new Http1xOrH2ChannelConnector(tcpClient, co, metrics);
    HttpClientImpl.Config config = new HttpClientImpl.Config();
    config.nonProxyHosts = co.getNonProxyHosts();
    config.verifyHost = co.isVerifyHost();
    config.defaultSsl = co.isSsl();
    config.defaultHost = co.getDefaultHost();
    config.defaultPort = co.getDefaultPort();
    config.maxRedirects = co.getMaxRedirects();
    config.initialPoolKind = co.getProtocolVersion() == HttpVersion.HTTP_2 ? 1 : 0;
    return new HttpClientImpl(vertx, connectionHandler, redirectHandler, channelConnector, metrics, resolver, po, co.getProxyOptions(), co.getSslOptions(), config);
  }

  private Handler<HttpConnection> connectionHandler(HttpClientOptions options) {
    int windowSize = options.getHttp2ConnectionWindowSize();
    Handler<HttpConnection> handler = connectHandler;
    if (windowSize > 0) {
      return connection -> {
        connection.setWindowSize(windowSize);
        if (handler != null) {
          handler.handle(connection);
        }
      };
    }
    return handler;
  }

  @Override
  public HttpClientAgent build() {
    // Copy options here ????
    HttpClientOptions co = clientOptions != null ? clientOptions : new HttpClientOptions();
    PoolOptions po = poolOptions != null ? poolOptions : new PoolOptions();
    CloseFuture cf = resolveCloseFuture();
    HttpClientAgent client;
    Closeable closeable;
    EndpointResolver resolver = endpointResolver(co);
    Handler<HttpConnection> connectHandler = connectionHandler(co);
    if (co.isShared()) {
      CloseFuture closeFuture = new CloseFuture();
      client = vertx.createSharedResource("__vertx.shared.httpClients", co.getName(), closeFuture, cf_ -> {
        HttpClientImpl impl = createHttpClientImpl(resolver, connectHandler, redirectHandler, co, po);
        cf_.add(completion -> impl.close().onComplete(completion));
        return impl;
      });
      client = new CleanableHttpClient((HttpClientInternal) client, vertx.cleaner(), (timeout, timeunit) -> closeFuture.close());
      closeable = closeFuture;
    } else {
      HttpClientImpl impl = createHttpClientImpl(resolver, connectHandler, redirectHandler, co, po);
      closeable = impl;
      client = new CleanableHttpClient(impl, vertx.cleaner(), impl::shutdown);
    }
    cf.add(closeable);
    return client;
  }
}
