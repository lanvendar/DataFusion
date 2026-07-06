# datafusion-agent-spider 镜像构建与部署

`datafusion-agent-spider` 镜像同时启动两个服务：

- `browser-agent`：`/opt/browser-agent`，端口 `8000`
- `datafusion-agent`：`/opt/datafusion-agent/datafusion-agent.jar`，端口 `8081`

`sh-web-spider` 以可执行运行目录形式放在 `/opt/sh-web-spider`，由 SPIDER 任务命令调用。

## 运行包约定

镜像构建直接使用两份“依赖 + app 一体”的运行包：

```text
/Users/lanvendar/PycharmProjects/browser-agent/dist/browser-agent-linux-amd64-runtime.tar.gz
/Users/lanvendar/PycharmProjects/sh-web-spider/dist/sh-web-spider-linux-amd64-runtime.tar.gz
```

这两份包都应将可运行内容平铺在 tar 根目录。解压后目录示例：

```text
/opt/browser-agent/
  .venv/
  main.py
  run-browser-agent.sh
  browser_agent/

/opt/sh-web-spider/
  .venv/
  run-spider.sh
  app/
```

## 构建前准备

构建 `datafusion-agent` jar：

```bash
cd /Users/lanvendar/Projects/DataFusion
mvn -q -pl datafusion-agent -am clean package -DskipTests
```

同步 SPIDER 运行包到插件资源目录：

```bash
cd /Users/lanvendar/Projects/DataFusion
./datafusion-plugin/datafusion-plugin-spider/src/main/resources/docker/sync-spider-runtime.sh
```

准备 Chromium 离线 deb 包：

```bash
mkdir -p /Users/lanvendar/Projects/DockerImagesBuilder/amd64-debs

docker run --rm --platform linux/amd64 \
  -v /Users/lanvendar/Projects/DockerImagesBuilder/amd64-debs:/debs \
  amd64-python312-jre17:v1.0.0-20260606 \
  /bin/bash -lc 'apt-get update \
    && apt-get install -y --no-install-recommends --download-only \
      -o Dir::Cache::archives=/debs chromium \
    && find /debs -type f ! -name "*.deb" -delete \
    && ls -lh /debs'
```

## 构建镜像

```bash
cd /Users/lanvendar/Projects/DataFusion

docker buildx build --network none --platform linux/amd64 --load \
  --build-context browser-agent-dist=/Users/lanvendar/PycharmProjects/browser-agent/dist \
  --build-context sh-web-spider-dist=/Users/lanvendar/PycharmProjects/sh-web-spider/dist \
  --build-context datafusion-agent=/Users/lanvendar/Projects/DataFusion/datafusion-agent \
  --build-context amd64-debs=/Users/lanvendar/Projects/DockerImagesBuilder/amd64-debs \
  -f datafusion-plugin/datafusion-plugin-spider/src/main/resources/docker/Dockerfile \
  -t datafusion-agent-spider:v1.0.0-20260706 \
  datafusion-plugin/datafusion-plugin-spider/src/main/resources/docker
```

## 本地校验

```bash
docker run --rm --platform linux/amd64 \
  --entrypoint /bin/bash \
  datafusion-agent-spider:v1.0.0-20260706 \
  -lc 'set -e
java -version
python3.12 --version
chromium --version
test -x /opt/browser-agent/.venv/bin/python
test -f /opt/browser-agent/main.py -o -f /opt/browser-agent/src/main.py
test -f /opt/datafusion-agent/datafusion-agent.jar
test -x /opt/sh-web-spider/run-spider.sh
/opt/sh-web-spider/.venv/bin/python3.12 -c "from sh_web_spider.cli import main; print(\"sh-web-spider-ok\")"
echo image-smoke-ok'
```

## 推送镜像

```bash
docker tag \
  datafusion-agent-spider:v1.0.0-20260706 \
  jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/datawarehouse:datafusion-agent-spider-v1.0.0-20260706

docker push \
  jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/datawarehouse:datafusion-agent-spider-v1.0.0-20260706
```

## K8S 部署

部署文件：

```text
datafusion-plugin/datafusion-plugin-spider/src/main/resources/docker/datafusion-agent-spider.yml
```

部署前需要补齐 `datafusion-agent-spider-secret` 中的 Redis、LLM、Kafka、Nacos 等敏感配置。

```bash
kubectl --kubeconfig /Users/lanvendar/vsCode/tmp/rke2.yaml \
  apply -f datafusion-plugin/datafusion-plugin-spider/src/main/resources/docker/datafusion-agent-spider.yml
```

查看状态：

```bash
kubectl --kubeconfig /Users/lanvendar/vsCode/tmp/rke2.yaml \
  -n datafusion get deploy,svc,pod -l app.kubernetes.io/component=agent-spider -o wide

kubectl --kubeconfig /Users/lanvendar/vsCode/tmp/rke2.yaml \
  -n datafusion rollout status deployment/datafusion-agent-spider --timeout=180s

kubectl --kubeconfig /Users/lanvendar/vsCode/tmp/rke2.yaml \
  -n datafusion logs deploy/datafusion-agent-spider -f
```

## 任务命令示例

```text
cd /opt/sh-web-spider && ./run-spider.sh --site oilchem --date-range '#day(_biz_date_, -1D, yyyyMMdd)-#day(_biz_date_, 0D, yyyyMMdd)'
```

SPIDER worker 通过 `DATAFUSION_WORKER_PLUGIN_TYPES=SPIDER` 只上报 SPIDER 插件能力。
