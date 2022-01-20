package name.vladykin;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine;

@CommandLine.Command(name = "HazelcastLoadGenerator", mixinStandardHelpOptions = true)
public class HazelcastLoadGenerator implements Callable<Integer> {

  @CommandLine.Option(
      names = "--cluster",
      description = "Hazelcast cluster address")
  private String clusterAddress;

  @CommandLine.Option(
      names = "--map",
      description = "Hazelcast map name")
  private String mapName = "test-map";

  @CommandLine.Option(
      names = "--op",
      required = true,
      description = "Type of Hazelcast operation to perform: READ or WRITE"
  )
  private OperationType operation;

  @CommandLine.Option(
      names = "--rate",
      description = "Number of Hazelcast operations to perform per second."
          + "May be fractional, e.g. use 0.5 to get 1 operation per 2 seconds."
  )
  private double rate = 1;

  @CommandLine.Option(
      names = "--write-size",
      description = "Size of data written by single write, in bytes"
  )
  private int writeSize = 1024;

  @CommandLine.Option(
      names = "--metrics-port",
      description = "Port where metrics will be exposed for scraping by Prometheus (path is `/metrics`)")
  private int metricsPort = 8080;


  private IMap<String, byte[]> map;
  private Timer operationsCompleted;
  private Counter operationsScheduled;
  private Counter operationsFailed;


  @Override
  public Integer call() throws InterruptedException {
    HazelcastInstance hazelcastInstance = connectToHazelcastCluster();
    this.map = hazelcastInstance.getMap(mapName);

    MeterRegistry meterRegistry = startPrometheusExporter().getMeterRegistry();
    this.operationsCompleted = Timer.builder("operations.completed")
        .tag("type", operation.name())
        .publishPercentiles(0.5, 0.7, 0.9, 0.99, 1)
        .register(meterRegistry);
    this.operationsScheduled = meterRegistry.counter("operations.scheduled", "type", operation.name());
    this.operationsFailed = meterRegistry.counter("operations.failed", "type", operation.name());

    Runnable task = null;
    switch (operation) {
      case READ:
        System.out.printf("Starting load generator for reads (rate=%.3f rps)%n", rate);
        task = new ReadTask();
        break;
      case WRITE:
        System.out.printf("Starting load generator for writes (rate=%.3f rps, write-size=%d)%n", rate, writeSize);
        task = new WriteTask();
        break;
    }

    ScheduledExecutorService executorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    executorService.scheduleAtFixedRate(task, 0, (long) (1_000_000_000.0 / rate), TimeUnit.NANOSECONDS);
    executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

    return 0;
  }

  private class ReadTask implements Runnable {
    private final Random random = new Random();

    @Override
    public void run() {
      String key = Integer.toString(random.nextInt(Integer.MAX_VALUE));
      Sample sample = Timer.start();
      map.getAsync(key)
          .whenComplete((value, throwable) -> {
            if (throwable == null) {
              sample.stop(operationsCompleted);
            } else {
              operationsFailed.increment();
              throwable.printStackTrace();
            }
          });
      operationsScheduled.increment();
    }
  }

  private class WriteTask implements Runnable {
    private final Random random = new Random();
    private final byte[] data = new byte[writeSize];

    @Override
    public void run() {
      String key = Integer.toString(random.nextInt(Integer.MAX_VALUE));
      Sample sample = Timer.start();
      map.setAsync(key, data)
          .whenComplete((value, throwable) -> {
            if (throwable == null) {
              sample.stop(operationsCompleted);
            } else {
              operationsFailed.increment();
              throwable.printStackTrace();
            }
          });
      operationsScheduled.increment();
    }
  }

  private PrometheusExporter startPrometheusExporter() {
    InetSocketAddress listenAddress = new InetSocketAddress(metricsPort);
    System.out.println("Exposing metrics on " + listenAddress + "/metrics");
    PrometheusExporter prometheusExporter = new PrometheusExporter(listenAddress);
    prometheusExporter.start();
    return prometheusExporter;
  }

  private HazelcastInstance connectToHazelcastCluster() {
    ClientConfig config = ClientConfig.load();
    if (clusterAddress != null && !clusterAddress.isEmpty()) {
      config.getNetworkConfig().setAddresses(Collections.singletonList(clusterAddress));
    }
    return HazelcastClient.newHazelcastClient(config);
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(HazelcastLoadGenerator.class).execute(args);
    System.exit(exitCode);
  }


  private enum OperationType {
    READ,
    WRITE
  }
}
