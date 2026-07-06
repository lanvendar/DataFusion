# SPIDER 插件构建与发布

当前清单：`datafusion-plugin/datafusion-plugin-spider/src/main/resources/builder/plugin-build-manifest.json`

执行路径建议：仓库根目录。

## 1) 同步运行时 tar.gz（每次发布前）

先把 runtime 包同步到插件资源目录：

```bash
./datafusion-plugin/datafusion-plugin-spider/src/main/resources/plugins/spider/sync-spider-runtime.sh \
  --source /Users/lanvendar/PycharmProjects/browser-agent/dist/browser-agent-linux-amd64-runtime.tar.gz \
  --source /Users/lanvendar/PycharmProjects/sh-web-spider/dist/sh-web-spider-linux-amd64-runtime.tar.gz
```

## 2) 发布到 datafusion-agent 插件目录

```bash
./datafusion-plugin/build-plugin.sh \
  --manifest datafusion-plugin/datafusion-plugin-spider/src/main/resources/builder/plugin-build-manifest.json \
  --mode fat
```

`artifactMode=none` 时，`--mode` 不会改变发布结果，只发布资源文件。

发布后目录结构：

```text
datafusion-agent/src/main/resources/plugins/spider/
  browser-agent/
    browser-agent-linux-amd64-runtime.tar.gz
  sh-web-spider/
    sh-web-spider-linux-amd64-runtime.tar.gz
```

Agent 镜像内置通用解压脚本：

```text
datafusion-agent/src/main/resources/plugins/
  init-runtime-unpack.sh
```

## 3) 打包校验

```bash
tar -tzf datafusion-agent/src/main/resources/plugins/spider/browser-agent/browser-agent-linux-amd64-runtime.tar.gz | head
tar -tzf datafusion-agent/src/main/resources/plugins/spider/sh-web-spider/sh-web-spider-linux-amd64-runtime.tar.gz | head
```

每个 tar 都应包含运行目录可直接启动的内容（`run-spider.sh`、`.env`、`.venv` 或者兼容的可执行结构），
Agent 启动解压后应可直接调度。
