FROM gradle:7.2-jdk11 AS builder

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

FROM sapmachine:11.0.20

# Creates app working directory and a system user (r) with
# no password, no home directory, no shell.
RUN mkdir -p /opt/neonbee/working_dir/config && \
    groupadd -r bee && useradd -r -s /bin/false -g bee bee && \
    chown -R bee /opt/neonbee

COPY --chown=bee:bee --from=builder /app/build/neonbee-core-*-shadow.jar /opt/neonbee/neonbee-core.jar
COPY --chown=bee:bee --from=builder /app/resources/config /opt/neonbee/

USER bee

ENTRYPOINT ["java","-jar","/opt/neonbee/neonbee-core.jar","-cwd","/opt/neonbee/working_dir"]
CMD ["-port","8080"]