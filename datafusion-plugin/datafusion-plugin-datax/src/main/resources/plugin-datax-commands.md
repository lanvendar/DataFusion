# DataX Local Test Commands

本文档只用于 `datafusion-plugin-datax` 模块的本地 LOCAL 测试，不描述 agent 发布、镜像构建或 K8S 提交流程。

## 前置条件

先确认本模块已包含 DataX runtime：

```text
datafusion-plugin/datafusion-plugin-datax/src/main/resources/plugins/datax
```

如果需要从外部 DataX 工程刷新 runtime，先执行：

```bash
./datafusion-plugin/datafusion-plugin-datax/src/main/resources/builder/sync-datax-runtime.sh
```

外部 DataX 默认产物路径：

```text
/Users/lanvendar/Projects/DataX/packaging/datax-bundle/target/datax
```

## 单个 Job

推荐在仓库根目录执行：

```bash
./datafusion-plugin/datafusion-plugin-datax/src/main/resources/run-datax-job.sh \
  shys/ods_shys_gb_account_td.json
```

也可以传完整 job 文件路径：

```bash
./datafusion-plugin/datafusion-plugin-datax/src/main/resources/run-datax-job.sh \
  /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/plugins/datax/jobs/shys/ods_shys_gb_account_td.json
```

只传文件名时，脚本会在 `plugins/datax/jobs` 下递归查找唯一匹配：

```bash
./datafusion-plugin/datafusion-plugin-datax/src/main/resources/run-datax-job.sh \
  ods_shys_gb_account_td.json
```

## 日志

默认日志目录：

```text
datafusion-plugin/datafusion-plugin-datax/target/datax-logs/{yyyyMMdd}/
```

可通过第二个参数指定日志根目录：

```bash
./datafusion-plugin/datafusion-plugin-datax/src/main/resources/run-datax-job.sh \
  shys/ods_shys_gb_account_td.json \
  /tmp/datax-logs
```

日志参数可通过环境变量覆盖：

```bash
DATAX_LOG_LEVEL=INFO \
DATAX_LOG_MAX_SIZE=100MB \
DATAX_LOG_MAX_INDEX=100 \
./datafusion-plugin/datafusion-plugin-datax/src/main/resources/run-datax-job.sh \
  shys/ods_shys_gb_account_td.json
```

## 批量本地测试

运行 `shys` 目录下全部 job：

```bash
for job_file in datafusion-plugin/datafusion-plugin-datax/src/main/resources/plugins/datax/jobs/shys/*.json; do
  ./datafusion-plugin/datafusion-plugin-datax/src/main/resources/run-datax-job.sh \
    "shys/$(basename "${job_file}")"
done
```

运行指定前缀的 job：

```bash
for job_file in datafusion-plugin/datafusion-plugin-datax/src/main/resources/plugins/datax/jobs/shys/ods_shys_jc_*.json; do
  ./datafusion-plugin/datafusion-plugin-datax/src/main/resources/run-datax-job.sh \
    "shys/$(basename "${job_file}")"
done
```

## 注意事项

- 本地测试脚本固定以 `standalone` 模式启动 DataX Engine。
- 脚本内置 Java 17 所需的 `--add-opens java.base/java.lang=ALL-UNNAMED`。
- `jobs/` 由 DataFusion 插件模块维护，不由外部 DataX runtime 同步脚本覆盖。
