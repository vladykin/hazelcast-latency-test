#!/bin/sh

"${JAVA_HOME}/bin/java" \
  ${JAVA_OPTS:-} \
  -jar "$(dirname $0)"/hazelcast-load-generator.jar "$@"
