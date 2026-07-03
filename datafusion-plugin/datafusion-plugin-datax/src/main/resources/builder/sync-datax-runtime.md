# DataX Runtime 同步

把外部 DataX runtime 产物同步到 DataFusion 的 DataX 插件目录（`jobs/` 除外）。

执行路径建议：仓库根目录

## 同步产物到插件目录

```bash
./datafusion-plugin/datafusion-plugin-datax/src/main/resources/builder/sync-datax-runtime.sh
```

默认同步 `conf/ lib/ plugin/ tmp/`，`jobs/` 不会被覆盖。

## 指定外部产物路径

```bash
./datafusion-plugin/datafusion-plugin-datax/src/main/resources/builder/sync-datax-runtime.sh \
  --source <datax-runtime-path>
```

也可：

```bash
DATAX_RUNTIME_SOURCE=<datax-runtime-path> ./datafusion-plugin/datafusion-plugin-datax/src/main/resources/builder/sync-datax-runtime.sh
```

## 同步后发布到 Agent

```bash
./datafusion-plugin/build-plugin.sh \
  --manifest datafusion-plugin/datafusion-plugin-datax/src/main/resources/builder/plugin-build-manifest.json \
  --no-maven
```

详见 [build-plugin.md](../../../../build-plugin.md)。
