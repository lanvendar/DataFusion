# DataX Runtime Sync

DataX 源码不在 DataFusion 仓库内，当前源码目录为：

```text
/Users/lanvendar/Projects/DataX
```

DataX 编译后的运行产物目录为：

```text
/Users/lanvendar/Projects/DataX/packaging/datax-bundle/target/datax
```

`sync-datax-runtime.sh` 负责把该外部运行产物同步到 DataFusion 的 DataX 插件资源目录：

```text
datafusion-plugin/datafusion-plugin-datax/src/main/resources/plugins/datax
```

## 同步命令

默认路径：

```bash
./datafusion-plugin/datafusion-plugin-datax/src/main/resources/builder/sync-datax-runtime.sh
```

指定外部产物路径：

```bash
./datafusion-plugin/datafusion-plugin-datax/src/main/resources/builder/sync-datax-runtime.sh \
  --source /Users/lanvendar/Projects/DataX/packaging/datax-bundle/target/datax
```

也可以通过环境变量指定：

```bash
DATAX_RUNTIME_SOURCE=/Users/lanvendar/Projects/DataX/packaging/datax-bundle/target/datax \
  ./datafusion-plugin/datafusion-plugin-datax/src/main/resources/builder/sync-datax-runtime.sh
```

## 同步范围

脚本同步以下 DataX runtime 目录：

```text
conf/
lib/
plugin/
tmp/
```

脚本不会同步或删除：

```text
jobs/
```

`jobs/` 由 DataFusion 的 `datafusion-plugin-datax` 模块维护，避免外部 DataX runtime 同步时误删本仓库中的任务样例。

## 发布到 Agent

同步完成后，再使用公共 builder 按 DataX manifest 发布到 agent：

```bash
./datafusion-plugin/build-plugin.sh \
  --manifest datafusion-plugin/datafusion-plugin-datax/src/main/resources/builder/plugin-build-manifest.json \
  --no-maven
```

DataX 的 `plugin-build-manifest.json` 是 copy-only manifest，公共 builder 只负责把
`datafusion-plugin-datax/src/main/resources/plugins/datax` 中声明的资源复制到
`datafusion-agent/src/main/resources/plugins/datax`。
