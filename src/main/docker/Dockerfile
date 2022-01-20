FROM azul/zulu-openjdk-centos:11.0.12

WORKDIR /loadgen
COPY loadgen.sh .
COPY hazelcast-load-generator.jar .

ENTRYPOINT ["./loadgen.sh"]
