# API 插件构建与发布

当前清单：`datafusion-plugin/datafusion-plugin-api/src/main/resources/builder/plugin-build-manifest.json`

执行路径建议：项目根目录

### 1) 打包发布 fat（默认）

适用于生产环境：构建并发布可直接运行的 fat jar 到 Agent 插件目录。

```bash
./datafusion-plugin/build-plugin.sh \
  --manifest datafusion-plugin/datafusion-plugin-api/src/main/resources/builder/plugin-build-manifest.json \
  --mode fat
```

### 2) 打包发布 thin

适用于轻量发布：发布普通 jar，并同步运行时依赖到 `lib`。

```bash
./datafusion-plugin/build-plugin.sh \
  --manifest datafusion-plugin/datafusion-plugin-api/src/main/resources/builder/plugin-build-manifest.json \
  --mode thin
```

### 3) 无 Maven 发布（纯拷贝）

适用于已构建产物场景：跳过 Maven，直接复用现有 `target` 产物和资源做发布。

```bash
./datafusion-plugin/build-plugin.sh \
  --manifest datafusion-plugin/datafusion-plugin-api/src/main/resources/builder/plugin-build-manifest.json \
  --no-maven
```

更多参数和行为请看 [build-plugin.md](../../../../build-plugin.md)。
