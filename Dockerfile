# Where the NeonBee jars come from: "builder" builds them from source, "prebuilt" takes them from an
# existing ./build directory (used by the publish workflow, which has already built and tested them).
ARG JAR_SOURCE=builder

FROM gradle:8.14.5-jdk17 AS builder

RUN mkdir app
WORKDIR /app
COPY . /app
RUN gradle -x test -x spotlessJava \
    -x spotlessCheck -x spotlessJavaCheck \
    -x spotbugsMain -x spotbugsTest \
    -x pmdMain -x pmdTest \
    -x checkstyleMain -x checkstyleTest \
    -x violations \
    -x reportCoverage \
    -x testJar -x testSourcesJar \
    -x javadoc -x javadocJar -x testJavadoc -x testJavadocJar \
    --no-daemon clean build

FROM scratch AS prebuilt
COPY build/neonbee-core-*-shadow.jar /app/build/
COPY resources/config /app/resources/config

FROM ${JAR_SOURCE} AS jars

FROM sapmachine:17.0.21.0.2

# Creates app working directory and a system user (r) with
# no password, no home directory, no shell.
RUN mkdir -p /opt/neonbee/working_dir/config && \
    groupadd -r bee && useradd -r -s /bin/false -g bee bee && \
    chown -R bee /opt/neonbee

COPY --chown=bee:bee --from=jars /app/build/neonbee-core-*-shadow.jar /opt/neonbee/neonbee-core.jar
COPY --chown=bee:bee --from=jars /app/resources/config /opt/neonbee/

USER bee

ENTRYPOINT ["java","-jar","/opt/neonbee/neonbee-core.jar","-cwd","/opt/neonbee/working_dir"]
CMD ["-port","8080"]