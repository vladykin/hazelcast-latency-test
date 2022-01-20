package name.vladykin;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class PrometheusExporter {

  private final InetSocketAddress listenAddress;
  private final PrometheusMeterRegistry meterRegistry;

  public PrometheusExporter(InetSocketAddress listenAddress) {
    this.listenAddress = Objects.requireNonNull(listenAddress, "listenAddress");
    this.meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
  }

  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  public void start() {
    try {
      HttpServer server = HttpServer.create(listenAddress, 0);
      server.createContext("/metrics", httpExchange -> {
        byte[] responseBytes = meterRegistry.scrape().getBytes(StandardCharsets.UTF_8);
        httpExchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = httpExchange.getResponseBody()) {
          os.write(responseBytes);
        }
      });
      server.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
