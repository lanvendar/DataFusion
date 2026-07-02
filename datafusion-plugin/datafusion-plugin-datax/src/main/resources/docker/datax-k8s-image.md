# DataX K8S Image

本文档只说明 `datafusion-plugin-datax` 的 K8S 运行镜像制作方式。该镜像用于 Agent 的 DataX K8S 提交模式。

## 输入目录

Dockerfile 位于：

```text
datafusion-plugin/datafusion-plugin-datax/src/main/resources/docker/Dockerfile
```

镜像构建上下文使用 `datafusion-plugin-datax` 模块根目录。

`.dockerignore` 也放在构建上下文根目录：

```text
datafusion-plugin/datafusion-plugin-datax/.dockerignore
```

不要把 `.dockerignore` 移到 Dockerfile 同级目录。当前构建命令使用 `-f src/main/resources/docker/Dockerfile`
指定 Dockerfile，但最后的 `.` 才是 build context，Docker 会读取 context 根目录下的 `.dockerignore`。

镜像内置 DataX runtime：

```text
src/main/resources/plugins/datax/conf/
src/main/resources/plugins/datax/lib/
src/main/resources/plugins/datax/plugin/
src/main/resources/plugins/datax/tmp/
```

镜像不内置本地 job 示例：

```text
src/main/resources/plugins/datax/jobs/
```

K8S 提交时，Agent 会把本次任务的 DataX job JSON 写入 Secret，并挂载到容器：

```text
/opt/datafusion/plugins/datax/job/job.json
```

## 刷新 Runtime

如果 DataX 外部工程重新编译过，先同步 runtime：

```bash
./datafusion-plugin/datafusion-plugin-datax/src/main/resources/builder/sync-datax-runtime.sh
```

默认外部产物路径：

```text
/Users/lanvendar/Projects/DataX/packaging/datax-bundle/target/datax
```

## 构建镜像

在仓库根目录执行：

```bash
cd datafusion-plugin/datafusion-plugin-datax
docker build \
  -f src/main/resources/docker/Dockerfile \
  -t jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/datawarehouse:datax-runtime-v1.0.0 .
```

如需 linux/amd64 镜像：

```bash
cd datafusion-plugin/datafusion-plugin-datax
docker buildx build \
  --platform=linux/amd64 \
  -f src/main/resources/docker/Dockerfile \
  -t jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/datawarehouse:datax-runtime-v1.0.0 \
  --load .
```

## 运行约定

镜像内默认环境变量：

```text
DATAX_HOME=/opt/datafusion/plugins/datax
DATAX_JOB_FILE=/opt/datafusion/plugins/datax/job/job.json
DATAX_LOG_FILE=/opt/datafusion/plugins/datax/logs/datax.log
DATAX_LOG_LEVEL=INFO
DATAX_LOG_MAX_SIZE=100MB
DATAX_LOG_MAX_INDEX=100
DATAX_JOB_ID=-1
```

entrypoint：

```text
/usr/local/bin/datax-k8s-entrypoint.sh
```

Agent 渲染 K8S Job 时会通过环境变量覆盖日志、jobId 和 job 文件路径等运行参数。

## 快速本地检查

构建完成后，可用一个本地 job 文件临时挂载测试：

```bash
docker run --rm \
  -v "$PWD/src/main/resources/plugins/datax/jobs/shcw/ods_shcw_scux_shsh_budget_subject.json:/opt/datafusion/plugins/datax/job/job.json:ro" \
  jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/datawarehouse:datax-runtime-v1.0.0
```

本地检查只用于验证镜像入口和 DataX runtime 是否完整；正式任务由 Agent 通过 K8S Secret 提交。
