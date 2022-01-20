# Hazelcast Load Generator

A small tool to generate a stream of requests to Hazelcast cluster
and measure their throughput and latency.

Compared to the official [Hazelcast simulator](https://github.com/hazelcast/hazelcast-simulator), this one is more lightweight,
does not require installation, exposes realtime metrics, and can be easily
packed into a docker image.

## Running the Load Generator

* Set `JAVA_HOME` environment variable to point to JDK 8 or later
* Use the provided `loadgen.sh` script to start the generator
```
Usage: HazelcastLoadGenerator [-hV] [--cluster=<clusterAddress>]
                              [--map=<mapName>] [--metrics-port=<metricsPort>]
                              --op=<operation> [--rate=<rate>]
                              [--write-size=<writeSize>]
      --cluster=<clusterAddress>
                         Hazelcast cluster address
  -h, --help             Show this help message and exit.
      --map=<mapName>    Hazelcast map name
      --metrics-port=<metricsPort>
                         Port where metrics will be exposed for scraping by
                           Prometheus (path is `/metrics`)
      --op=<operation>   Type of Hazelcast operation to perform: READ or WRITE
      --rate=<rate>      Number of Hazelcast operations to perform per second.
                           May be fractional, e.g. use 0.5 to get 1 operation
                           per 2 seconds.
  -V, --version          Print version information and exit.
      --write-size=<writeSize>
                         Size of data written by single write, in bytes
```
* Example: `loadgen.sh --cluster=localhost:5701 --op=WRITE --rate=1000 --write-size=102400`
* While the load generator is running, connect to its HTTP `/metrics` endpoint
  (by default exposed on port 8080) to see the count and the total time of
  completed operations

### Running in Container

* Use the provided [Dockerfile](src/main/docker/Dockerfile) to build a docker image
* Example of running the generator locally with docker:
  ```
  docker run -it --rm -p 8080:8080 \
    hazelcast-load-generator:latest \
    --cluster=localhost:5701 --op=WRITE --rate=1000 --write-size=10240
  ```

The docker image can be used to run the load generator in Kubernetes.
See the provided [loadgen.yaml](src/main/scripts/hazelcast.yaml) as an example.
