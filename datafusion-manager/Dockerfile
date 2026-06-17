# FROM eclipse-temurin:17-jre
FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/java_17:latest

ENV TZ=Asia/Shanghai \
    DATAFUSION_MODULES=/opt/datafusion \
    SPRING_PROFILES_ACTIVE=standalone,local \
    JAVA_OPTS=""

WORKDIR /opt/datafusion-manager

RUN mkdir -p /opt/datafusion/logs \
    /opt/datafusion/task-runtime \
    /opt/datafusion-manager

COPY target/datafusion-manager-*.jar /opt/datafusion-manager/datafusion-manager.jar

EXPOSE 8080

VOLUME ["/opt/datafusion"]

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE -jar /opt/datafusion-manager/datafusion-manager.jar"]
