# FROM eclipse-temurin:17-jre
FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/eclipse-temurin:17-jre

LABEL org.opencontainers.image.title="datafusion-plugin-datax" \
      org.opencontainers.image.description="DataFusion DataX runtime image for Kubernetes Job mode" \
      org.opencontainers.image.vendor="DataFusion"

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    DATAX_HOME=/opt/datafusion/plugins/datax \
    DATAX_JOB_FILE=/opt/datafusion/plugins/datax/job/job.json \
    DATAX_LOG_FILE=/opt/datafusion/plugins/datax/logs/datax.log \
    DATAX_LOG_LEVEL=INFO \
    DATAX_LOG_MAX_SIZE=100MB \
    DATAX_LOG_MAX_INDEX=100 \
    DATAX_JOB_ID=-1 \
    JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"

WORKDIR /opt/datafusion

RUN mkdir -p /opt/datafusion/plugins/datax \
    /opt/datafusion/plugins/datax/job \
    /opt/datafusion/plugins/datax/logs

COPY src/main/resources/plugins/datax/conf/ /opt/datafusion/plugins/datax/conf/
COPY src/main/resources/plugins/datax/lib/ /opt/datafusion/plugins/datax/lib/
COPY src/main/resources/plugins/datax/plugin/ /opt/datafusion/plugins/datax/plugin/
COPY src/main/resources/plugins/datax/tmp/ /opt/datafusion/plugins/datax/tmp/
COPY src/main/resources/docker/datax-k8s-entrypoint.sh /usr/local/bin/datax-k8s-entrypoint.sh

RUN chmod +x /usr/local/bin/datax-k8s-entrypoint.sh

ENTRYPOINT ["sh", "/usr/local/bin/datax-k8s-entrypoint.sh"]
