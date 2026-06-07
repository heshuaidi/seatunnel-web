# 公司 Kubernetes/Rancher Context Path 部署说明

本文档用于公司局域网 Kubernetes/Rancher 部署，要求前后端都不能部署在根路径 `/`。

- 后端 context path: `/seatunnel-web-api`
- 前端 context path: `/seatunnel-web-ui/`
- 后端 Swagger: `http://<host>/seatunnel-web-api/swagger-ui/index.html`
- 前端入口: `http://<host>/seatunnel-web-ui/`

## 部署拓扑

```text
Browser
  |
  | http://<host>/seatunnel-web-ui/
  | http://<host>/seatunnel-web-api/api/v1/...
  v
Ingress
  |-- /seatunnel-web-ui  --> seatunnel-web-ui Service  --> Nginx static dist
  |-- /seatunnel-web-api --> seatunnel-web-api Service --> Spring Boot API
```

Ingress 不需要 rewrite `/seatunnel-web-api`，因为后端通过 Spring Boot context path 自己处理该前缀。

## 后端 Context Path

后端可以直接使用 Spring Boot 标准配置，不需要修改 Java 代码。

启动参数方式：

```shell
java -jar seatunnel-web-api.jar \
  --server.port=8080 \
  --server.servlet.context-path=/seatunnel-web-api
```

环境变量方式：

```shell
SERVER_PORT=8080
SERVER_SERVLET_CONTEXT_PATH=/seatunnel-web-api
```

## 后端 Jar 构建

在有 Maven 依赖访问能力的机器上执行：

```shell
mvn -pl seatunnel-web-api -am -DskipTests package
```

构建产物：

```text
seatunnel-web-api/target/seatunnel-web-api.jar
```

## 前端外网电脑 Build Dist

前端生产构建必须带上 context path：

```shell
cd seatunnel-web-ui
FRONTEND_BASE=/seatunnel-web-ui/ \
FRONTEND_PUBLIC_PATH=/seatunnel-web-ui/ \
API_BASE=/seatunnel-web-api \
npm run build
```

参数含义：

- `FRONTEND_BASE`: Umi 路由 base，控制页面路由从 `/seatunnel-web-ui/` 开始。
- `FRONTEND_PUBLIC_PATH`: 静态资源 publicPath，控制 JS/CSS 等资源从 `/seatunnel-web-ui/` 加载。
- `API_BASE`: 业务接口请求前缀，生产环境请求会从 `/api/v1/...` 变为 `/seatunnel-web-api/api/v1/...`。

默认值保持本地开发兼容：

- `FRONTEND_BASE=/`
- `FRONTEND_PUBLIC_PATH=/`
- `API_BASE=` 空字符串

## 前端 Dist 打 Nginx 镜像

示例文件：

- `deploy/company-k8s/frontend/Dockerfile`
- `deploy/company-k8s/frontend/nginx.conf`

从仓库根目录执行：

```shell
docker build \
  -f deploy/company-k8s/frontend/Dockerfile \
  -t <harbor-host>/<project>/seatunnel-web-ui:<tag> \
  .
```

该镜像会把 `seatunnel-web-ui/dist/` 复制到：

```text
/usr/share/nginx/html/seatunnel-web-ui/
```

## 后端 Dockerfile 示例

示例文件：

```text
deploy/company-k8s/backend/Dockerfile
```

从仓库根目录执行：

```shell
docker build \
  -f deploy/company-k8s/backend/Dockerfile \
  --build-arg JAR_FILE=seatunnel-web-api/target/seatunnel-web-api.jar \
  -t <harbor-host>/<project>/seatunnel-web-api:<tag> \
  .
```

## Nginx 配置示例

示例文件：

```text
deploy/company-k8s/frontend/nginx.conf
```

关键点：

- `/seatunnel-web-ui/` 使用 SPA fallback 到 `/seatunnel-web-ui/index.html`。
- `/seatunnel-web-api/` 可代理到后端 Service，适用于只把流量打到前端 Nginx 的场景。
- 如果 Ingress 已经把 `/seatunnel-web-api` 直接转发到后端 Service，则 Nginx 中 API proxy 只是备用路径。

## Kubernetes 示例

示例文件：

- `deploy/company-k8s/k8s/seatunnel-web-api.yaml`
- `deploy/company-k8s/k8s/seatunnel-web-ui.yaml`
- `deploy/company-k8s/k8s/ingress.yaml`

部署前替换占位符：

- `<harbor-host>`
- `<project>`
- `<tag>`
- `<mysql-host>`
- `<seatunnel-web-host>`

数据库账号密码建议使用 Secret：

```shell
kubectl create secret generic seatunnel-web-db \
  --from-literal=username='<db-user>' \
  --from-literal=password='<db-password>'
```

应用资源：

```shell
kubectl apply -f deploy/company-k8s/k8s/seatunnel-web-api.yaml
kubectl apply -f deploy/company-k8s/k8s/seatunnel-web-ui.yaml
kubectl apply -f deploy/company-k8s/k8s/ingress.yaml
```

## Rancher 配置要点

- Workload 镜像使用 Harbor 内网地址，例如 `<harbor-host>/<project>/seatunnel-web-api:<tag>`。
- 后端容器环境变量必须包含 `SERVER_SERVLET_CONTEXT_PATH=/seatunnel-web-api`。
- 前端镜像必须使用带 context path 的 dist 构建结果。
- Ingress path 使用 Prefix：
  - `/seatunnel-web-api` -> `seatunnel-web-api` Service
  - `/seatunnel-web-ui` -> `seatunnel-web-ui` Service
- Ingress 不要配置 rewrite-target，否则后端 context path 会被剥离导致 Swagger 或 API 404。
- 如需上传驱动包，确认 Ingress/Nginx body size 足够大，示例中为 `200m`。

## Nexus Maven settings.xml 示例

将占位符替换为公司 Nexus 信息：

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>company-nexus</id>
      <mirrorOf>*</mirrorOf>
      <url>http://<nexus-host>/repository/maven-public/</url>
    </mirror>
  </mirrors>

  <servers>
    <server>
      <id>company-nexus</id>
      <username>&lt;nexus-user&gt;</username>
      <password>&lt;nexus-password&gt;</password>
    </server>
  </servers>

  <profiles>
    <profile>
      <id>company</id>
      <repositories>
        <repository>
          <id>company-nexus</id>
          <url>http://<nexus-host>/repository/maven-public/</url>
          <releases>
            <enabled>true</enabled>
          </releases>
          <snapshots>
            <enabled>true</enabled>
          </snapshots>
        </repository>
      </repositories>
    </profile>
  </profiles>

  <activeProfiles>
    <activeProfile>company</activeProfile>
  </activeProfiles>
</settings>
```

使用方式：

```shell
mvn -s settings.xml -pl seatunnel-web-api -am -DskipTests package
```

## Harbor 镜像推送流程

```shell
docker login <harbor-host>

docker push <harbor-host>/<project>/seatunnel-web-api:<tag>
docker push <harbor-host>/<project>/seatunnel-web-ui:<tag>
```

在 Rancher 中引用同一组镜像地址和 tag。

## 验证 URL

```text
http://<host>/seatunnel-web-ui/
http://<host>/seatunnel-web-api/swagger-ui/index.html
http://<host>/seatunnel-web-api/api/v1/users/currentUser
```

浏览器 Network 面板中，前端 API 请求应该是：

```text
/seatunnel-web-api/api/v1/...
```

不应该再请求：

```text
/api/v1/...
```

## 常见问题

### 前端白屏

检查 `FRONTEND_BASE` 和 `FRONTEND_PUBLIC_PATH` 是否都设置为 `/seatunnel-web-ui/` 后重新 build。只改 Nginx 或 Ingress，不能修复已经 build 到产物里的静态资源路径。

### 静态资源 404

检查浏览器是否请求 `/seatunnel-web-ui/*.js`、`/seatunnel-web-ui/*.css`。如果请求的是根路径下的 `/*.js`，说明前端 build 时 `FRONTEND_PUBLIC_PATH` 没有设置正确。

### API 404

检查前端 build 时是否设置 `API_BASE=/seatunnel-web-api`。生产环境应请求 `/seatunnel-web-api/api/v1/...`。

### Swagger 路径错误

检查后端容器是否设置：

```shell
SERVER_SERVLET_CONTEXT_PATH=/seatunnel-web-api
```

正确访问路径是：

```text
http://<host>/seatunnel-web-api/swagger-ui/index.html
```

### Ingress 配置后仍然 404

确认 Ingress 没有 rewrite `/seatunnel-web-api`。后端启用 context path 后，需要完整保留该路径前缀转发到后端。
