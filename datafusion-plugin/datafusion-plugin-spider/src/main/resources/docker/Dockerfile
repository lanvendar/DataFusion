# syntax=docker/dockerfile:1.7

# Build example:
# docker buildx build --network none --platform linux/amd64 --load \
#   --build-context browser-agent-dist=/Users/lanvendar/PycharmProjects/browser-agent/dist \
#   --build-context sh-web-spider-dist=/Users/lanvendar/PycharmProjects/sh-web-spider/dist \
#   --build-context datafusion-agent=/Users/lanvendar/Projects/DataFusion/datafusion-agent \
#   --build-context amd64-debs=/Users/lanvendar/Projects/DockerImagesBuilder/amd64-debs \
#   -f datafusion-plugin/datafusion-plugin-spider/src/main/resources/docker/Dockerfile \
#   -t datafusion-agent-spider:v1.0.0-20260706 \
#   datafusion-plugin/datafusion-plugin-spider/src/main/resources/docker

ARG BASE_IMAGE=amd64-python312-jre17:v1.0.0-20260606
FROM ${BASE_IMAGE}

ENV DEBIAN_FRONTEND=noninteractive

RUN --mount=type=bind,from=amd64-debs,source=.,target=/tmp/amd64-debs,readonly \
    apt-get install -y --no-install-recommends /tmp/amd64-debs/*.deb \
    && ln -sf /usr/bin/chromium /usr/bin/chromium-browser \
    && rm -rf /var/lib/apt/lists/* /var/cache/apt/* /usr/share/doc /usr/share/man /usr/share/info

ENV TZ=Asia/Shanghai \
    DATAFUSION_MODULES=/opt/datafusion \
    DATAFUSION_PLUGINS_ROOT_DIR=/opt/datafusion/plugins \
    DATAFUSION_WORKER_PLUGIN_TYPES=SPIDER \
    DATAFUSION_WORKER_LOG_DIR=/opt/datafusion/logs/datafusion-agent-spider \
    SPRING_PROFILES_ACTIVE=standalone,local \
    JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8" \
    APP_ENV=dev \
    CHROME_BIN=/usr/bin/chromium \
    CHROMIUM_BIN=/usr/bin/chromium \
    BROWSER_EXECUTABLE_PATH=/usr/bin/chromium \
    OTEL_EXPORTER_OTLP_ENDPOINT="" \
    OTEL_EXPORTER_OTLP_HEADERS="" \
    OTEL_LOGS_EXPORTER=none \
    OTEL_TRACES_EXPORTER=none \
    OTEL_METRICS_EXPORTER=none

WORKDIR /opt/browser-agent
RUN --mount=type=bind,from=browser-agent-dist,source=browser-agent-linux-amd64-runtime.tar.gz,target=/tmp/browser-agent-linux-amd64-runtime.tar.gz,readonly \
    tar -xzf /tmp/browser-agent-linux-amd64-runtime.tar.gz -C /opt/browser-agent \
    && if [ -f /opt/browser-agent/run-browser-agent.sh ]; then chmod +x /opt/browser-agent/run-browser-agent.sh; fi

WORKDIR /opt/sh-web-spider
RUN --mount=type=bind,from=sh-web-spider-dist,source=sh-web-spider-linux-amd64-runtime.tar.gz,target=/tmp/sh-web-spider-linux-amd64-runtime.tar.gz,readonly \
    tar -xzf /tmp/sh-web-spider-linux-amd64-runtime.tar.gz -C /opt/sh-web-spider \
    && chmod +x /opt/sh-web-spider/run-spider.sh

WORKDIR /opt/datafusion-agent
RUN mkdir -p \
    /opt/datafusion/logs/datafusion-agent-spider \
    /opt/datafusion-builtin/plugins \
    /opt/datafusion/plugins \
    /opt/datafusion/task-runtime

COPY --from=datafusion-agent src/main/resources/plugins/ /opt/datafusion-builtin/plugins/
COPY --from=datafusion-agent target/datafusion-agent-*.jar /opt/datafusion-agent/datafusion-agent.jar
COPY start-datafusion-agent-spider.sh /usr/local/bin/start-datafusion-agent-spider.sh
RUN chmod +x /usr/local/bin/start-datafusion-agent-spider.sh

EXPOSE 8000 8081

VOLUME ["/opt/datafusion"]

ENTRYPOINT ["/usr/local/bin/start-datafusion-agent-spider.sh"]
