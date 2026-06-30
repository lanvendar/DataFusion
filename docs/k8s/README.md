# DataFusion Kubernetes 部署说明

本文档配套资源清单拆分在 `docs/k8s/` 下，命名空间固定为 `datafusion`。

## 资源内容

- `datafusion-common.yml`: `Namespace`、公共 `ConfigMap` `datafusion-env`、公共 `Secret` `datafusion-secret`
- `datafusion-common-pvc.yml`: 公共 `StorageClass`、共享 PVC `datafusion-shared-data`
- `datafusion-manager.yml`: Manager 的 `Service`、`Deployment`，服务端口 8080
- `datafusion-agent.yml`: Agent 的 `ServiceAccount`、`Role`、`RoleBinding`、`Service`、`Deployment`，服务端口 8081
- `nacos.yml`: Nacos 的 MySQL/Auth Secret、`Deployment`、`Service`
- `datafusion-web.yml`: Web 前端 `Service`、`Deployment`，服务端口 80
- `datafusion-manager-lb.yml`: Manager 外部访问 `LoadBalancer`，端口 30081
- `datafusion-agent-lb.yml`: Agent 外部访问 `LoadBalancer`，端口 30082
- `datafusion-web-lb.yml`: Web 外部访问 `LoadBalancer`，端口 30080
- `datafusion-manager-ingress.yml`: Manager 外部访问 `Ingress`，域名 `sz-dev-datafusion`
- `datafusion-web-ingress.yml`: Web 外部访问 `Ingress`，域名 `sz-dev-datafusion-web`

## 部署前必须调整

1. 替换镜像：
   - `datafusion-manager:1.0.0`
   - `datafusion-agent:1.0.0`
   - `datafusion-web:1.0.0`

2. 按真实环境调整 `datafusion-env`：
   - `DATAFUSION_ENV` 默认是 `test`，Manager/Agent 会把它映射为 `SPRING_PROFILES_ACTIVE`。
   - `NACOS_SERVER_ADDR` 默认指向 `nacos.datafusion.svc.cluster.local:8848`。
   - `NACOS_NAMESPACE` 默认是 `datafusion-test` 的命名空间 ID: `55af8b89-a24c-421b-9e1f-7c50c26e4901`。
   - `NACOS_GROUP` 需要与目标 Nacos 配置分组一致。
   - `NACOS_CONFIG_DATA_ID` 在 Manager/Agent 的 Deployment 中分别默认为 `datafusion-manager-$(SPRING_PROFILES_ACTIVE)` 和 `datafusion-agent-$(SPRING_PROFILES_ACTIVE)`。
   - `DATAFUSION_MANAGER_URL` 默认指向集群内 Service: `http://datafusion-manager:8080`。
   - `LOG_PATH` 在 Manager/Agent 的 Deployment 中分别设置为 `/opt/datafusion/logs/datafusion-manager` 和 `/opt/datafusion/logs/datafusion-agent`。
   - `DATAFUSION_INIT_OVERWRITE` 默认是 `false`，Agent initContainer 只从镜像内置 `/opt/datafusion-builtin` 补齐 PVC 缺失插件文件；改为 `true` 时会覆盖 PVC 中已有文件。
   - `DATAFUSION_WORKER_PLUGIN_TYPES` 默认空，表示 Agent 上报已加载的全部插件；可设为 `SHELL`、`DATAX` 或逗号分隔列表限制 worker 承接插件。
   - `SKYWALKING_GRAPHQL_URL`、`DATAFUSION_ETL_*` 按实际外部依赖调整。

3. 替换 `datafusion-secret` 中的敏感值：
   - `NACOS_PASSWORD`，当前默认是 `nacos`
   - `DATAFUSION_GIT_URL`
   - `DATAFUSION_GIT_USERNAME`
   - `DATAFUSION_GIT_PASSWORD`

4. 替换 `nacos.yml` 中的敏感值：
   - `nacos-mysql-secret.mysql_user`
   - `nacos-mysql-secret.mysql_password`
   - `nacos-auth-secret.token`
   - `nacos-auth-secret.identity-value`

5. 确认 Manager 的完整运行配置来源：
   - `test` 环境默认从 Nacos 读取 `datafusion-manager-test` 和 `datafusion-agent-test`。
   - `prod` 环境默认从 Nacos 读取 `datafusion-manager-prod` 和 `datafusion-agent-prod`。
   - Manager 预期从 Nacos 获取 `spring.datasource.*`、`datafusion.datasource.*`、OSS、资源同步等配置。
   - 如果不使用 Nacos，需要改为合适的 profile，并通过环境变量或挂载配置文件补齐数据源等 Spring 配置。

6. 确认 PVC 与存储类：
   - `datafusion-common-pvc.yml` 基于 `rook-cephfs` 参数创建 `rook-cephfs-retain`，回收策略为 `Retain`。
   - PVC `datafusion-shared-data` 使用 `ReadWriteMany`，Manager 和 Agent 共同挂载到 `/opt/datafusion/`。

7. 如需外部访问，确认 MetalLB 配置：
   - `datafusion-*-lb.yml` 默认共享 `172.26.185.215`。
   - `metallb.io/allow-shared-ip` 默认是 `dev-db-vip`。
   - 如集群 VIP 或共享 IP 标识不同，需要同步修改三个 LoadBalancer 文件。

8. 如需通过 Ingress 外部访问，确认 Ingress 配置：
   - `datafusion-web-ingress.yml` 默认域名是 `sz-dev-datafusion-web`，后端服务是 `datafusion-web:80`。
   - `datafusion-manager-ingress.yml` 默认域名是 `sz-dev-datafusion`，后端服务是 `datafusion-manager:8080`。
   - `ingressClassName` 默认是 `nginx`。
   - TLS Secret 默认是 `datafusion-web-tls` 和 `datafusion-manager-tls`，需要按集群实际证书修改或删除 `tls` 段。

## DataX K8s 任务权限

Agent 里的 DataX K8s runner 会通过 Fabric8 client 创建 `Secret` 和 `batch/v1 Job`，并查询 Pod 状态与日志。当前 Role 只授权 `datafusion` 命名空间。

如果任务参数中的 Kubernetes namespace 不是 `datafusion`，需要在对应 namespace 额外创建同等权限的 `RoleBinding`，或改成受控的 `ClusterRoleBinding`。

## 应用

```bash
kubectl apply -f docs/k8s/datafusion-common.yml
kubectl apply -f docs/k8s/datafusion-common-pvc.yml
kubectl apply -f docs/k8s/nacos.yml
kubectl apply -f docs/k8s/datafusion-manager.yml
kubectl apply -f docs/k8s/datafusion-agent.yml
kubectl apply -f docs/k8s/datafusion-web.yml
kubectl apply -f docs/k8s/datafusion-manager-lb.yml
kubectl apply -f docs/k8s/datafusion-agent-lb.yml
kubectl apply -f docs/k8s/datafusion-web-lb.yml
kubectl apply -f docs/k8s/datafusion-manager-ingress.yml
kubectl apply -f docs/k8s/datafusion-web-ingress.yml
kubectl -n datafusion get pods,svc,pvc
```

查看启动日志：

```bash
kubectl -n datafusion logs deploy/nacos
kubectl -n datafusion logs deploy/datafusion-manager
kubectl -n datafusion logs deploy/datafusion-agent
kubectl -n datafusion logs deploy/datafusion-web
```
