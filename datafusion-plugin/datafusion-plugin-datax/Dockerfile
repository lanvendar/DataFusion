FROM eclipse-temurin:17-jre-jammy

LABEL org.opencontainers.image.title="datafusion-plugin-datax" \
      org.opencontainers.image.description="DataFusion DataX runtime image for Kubernetes Job mode" \
      org.opencontainers.image.vendor="DataFusion"

ENV DATAX_HOME=/opt/datafusion/datax \
    DATAX_JOB_FILE=/datafusion/job/job.json \
    DATAX_LOG_FILE=/datafusion/logs/datax.log \
    DATAX_LOG_LEVEL=INFO \
    DATAX_LOG_MAX_SIZE=100MB \
    DATAX_LOG_MAX_INDEX=100 \
    DATAX_JOB_ID=-1 \
    JAVA_OPTS=""

WORKDIR /opt/datafusion

COPY src/main/resources/datax/ /opt/datafusion/datax/
COPY src/main/docker/datax-k8s-entrypoint.sh /usr/local/bin/datax-k8s-entrypoint.sh

RUN groupadd --system datafusion \
    && useradd --system --gid datafusion --home-dir /opt/datafusion --shell /usr/sbin/nologin datafusion \
    && mkdir -p /datafusion/job /datafusion/logs \
    && chown -R datafusion:datafusion /opt/datafusion /datafusion \
    && chmod +x /usr/local/bin/datax-k8s-entrypoint.sh

USER datafusion

ENTRYPOINT ["/usr/local/bin/datax-k8s-entrypoint.sh"]
