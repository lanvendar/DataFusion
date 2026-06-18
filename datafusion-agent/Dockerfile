# FROM eclipse-temurin:17-jre
FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/java_17:latest

ENV TZ=Asia/Shanghai \
    DATAFUSION_MODULES=/opt/datafusion \
    SPRING_PROFILES_ACTIVE=standalone,local \
    JAVA_OPTS=""

WORKDIR /opt/datafusion-agent

RUN mkdir -p /opt/datafusion/logs/datafusion-agent \
    /opt/datafusion/plugins/datax \
    /opt/datafusion/plugins/shell \
    /opt/datafusion/task-runtime \
    /opt/datafusion-agent

COPY src/main/resources/plugins/datax/ /opt/datafusion/plugins/datax/
COPY src/main/resources/plugins/shell/ /opt/datafusion/plugins/shell/
COPY target/datafusion-agent-*.jar /opt/datafusion-agent/datafusion-agent.jar

EXPOSE 8081

VOLUME ["/opt/datafusion"]

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE -jar /opt/datafusion-agent/datafusion-agent.jar"]
