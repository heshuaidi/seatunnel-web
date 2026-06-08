# Company K8s / Rancher Deployment

This document describes the company internal deployment validation flow for SeaTunnel Web on K8s + Rancher + Harbor + Nexus. This is for the company LAN deployment branch, not for a StarRocks open source PR branch.

## 1. Deployment Goal

Deploy SeaTunnel Web behind context paths:

- Frontend: `/seatunnel-web-ui/`
- Backend API: `/seatunnel-web-api/`
- Swagger: `/seatunnel-web-api/swagger-ui/index.html`

Only the frontend nginx container and backend API container are deployed in K8s. MariaDB is external and must not be deployed as a Kubernetes workload by these manifests.

## 2. Deployment Topology

Request flow:

```text
Browser
  -> Ingress host seatunnel-web.example.local
    -> /seatunnel-web-ui  -> Service seatunnel-web-ui  -> nginx frontend Pod
    -> /seatunnel-web-api -> Service seatunnel-web-api -> Spring Boot backend Pod
      -> external MariaDB Linux servers
      -> SeaTunnel Engine endpoint configured for container reachability
```

The local `deploy/local-docker` directory is retained only for home or local Docker rehearsal. It uses an external database mode where the backend container reaches host MySQL through `host.docker.internal:3307`, simulating the company's external MariaDB pattern.

## 3. External MariaDB Requirements

MariaDB is deployed on three independent Linux machines outside K8s. Do not create a MariaDB container, Deployment, or StatefulSet for this deployment.

Before deploying SeaTunnel Web, confirm:

- K8s worker nodes can reach the MariaDB VIP or selected host and port.
- The database name exists, for example `seatunnel_web`.
- The application user has the required schema privileges.
- The JDBC URL is reachable from inside a backend Pod.
- The driver class is `com.mysql.cj.jdbc.Driver`.

Example JDBC URL:

```text
jdbc:mysql://mariadb.example.local:3306/seatunnel_web?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
```

## 4. Nexus Backend Compile

On a machine that can access the company Nexus Maven repository, configure Maven `settings.xml` to use the internal Nexus mirror.

Example compile flow:

```bash
mvn -U clean package -DskipTests
```

The backend Dockerfile expects the jar at:

```text
seatunnel-web-api/target/seatunnel-web-api.jar
```

If the jar name differs, pass `--build-arg JAR_FILE=...` when building the backend image.

## 5. External Network Frontend Build

Build the frontend on a machine that can install npm dependencies. The context path variables must be passed at build time because the frontend dist embeds them.

```bash
cd seatunnel-web-ui
FRONTEND_BASE=/seatunnel-web-ui/ FRONTEND_PUBLIC_PATH=/seatunnel-web-ui/ API_BASE=/seatunnel-web-api npm run build
```

Expected result:

- `seatunnel-web-ui/dist/` exists.
- Built JavaScript contains `/seatunnel-web-api`.
- Built assets use `/seatunnel-web-ui/` public path.

## 6. Bring Frontend dist into Company LAN

After building on the external network machine, transfer only the required frontend build output into the company LAN:

```text
seatunnel-web-ui/dist/
deploy/company-k8s/frontend/Dockerfile
deploy/company-k8s/frontend/nginx.conf
```

Do not commit `dist/`. Treat it as a build artifact used only for image construction.

## 7. Backend Image Build

From the repository root:

```bash
docker build \
  -f deploy/company-k8s/backend/Dockerfile \
  -t harbor.example.com/data/seatunnel-web-api:starrocks-test \
  .
```

The backend image uses a JRE base image and receives runtime configuration from environment variables:

- `SERVER_PORT=9527`
- `SERVER_SERVLET_CONTEXT_PATH=/seatunnel-web-api`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver`

## 8. Frontend Image Build

Ensure `seatunnel-web-ui/dist/` has already been produced with the required context path build command.

From the repository root:

```bash
docker build \
  -f deploy/company-k8s/frontend/Dockerfile \
  -t harbor.example.com/data/seatunnel-web-ui:starrocks-test \
  .
```

The image copies the dist directory to:

```text
/usr/share/nginx/html/seatunnel-web-ui/
```

The nginx SPA config uses:

```nginx
absolute_redirect off;
server_name_in_redirect off;
port_in_redirect off;
location ^~ /seatunnel-web-ui/ {
    root /usr/share/nginx/html;
    try_files $uri /seatunnel-web-ui/index.html;
}
```

This avoids sub-route refresh failures and avoids redirects that drop the browser port.

## 9. Harbor Push

Log in to Harbor and push both images:

```bash
docker login harbor.example.com
docker push harbor.example.com/data/seatunnel-web-api:starrocks-test
docker push harbor.example.com/data/seatunnel-web-ui:starrocks-test
```

Use project names, credentials, and image retention rules approved by the company platform team. Do not hardcode real internal Harbor URLs in this repository.

## 10. Rancher Deployment Steps

1. Import or select the target K8s cluster in Rancher.
2. Create or select namespace `seatunnel-web`.
3. Create the database Secret from `deploy/company-k8s/k8s/01-secret-example.yaml`, replacing `username` and `password`.
4. Create the ConfigMap from the same file, replacing `SPRING_DATASOURCE_URL`.
5. Deploy the backend Deployment and Service.
6. Deploy the frontend Deployment and Service.
7. Deploy the Ingress with host `seatunnel-web.example.local` or the company-approved internal DNS name.
8. Confirm the Ingress controller is the expected nginx ingress class.
9. Check backend Pod logs for datasource and context path startup.
10. Check frontend nginx Pod logs for static asset and route requests.

## 11. K8s YAML Deployment Steps

From the repository root:

```bash
kubectl apply -f deploy/company-k8s/k8s/00-namespace.yaml
kubectl apply -f deploy/company-k8s/k8s/01-secret-example.yaml
kubectl apply -f deploy/company-k8s/k8s/02-seatunnel-web-api-deployment.yaml
kubectl apply -f deploy/company-k8s/k8s/03-seatunnel-web-api-service.yaml
kubectl apply -f deploy/company-k8s/k8s/04-seatunnel-web-ui-deployment.yaml
kubectl apply -f deploy/company-k8s/k8s/05-seatunnel-web-ui-service.yaml
kubectl apply -f deploy/company-k8s/k8s/06-ingress.yaml
```

Check resources:

```bash
kubectl -n seatunnel-web get pods,svc,ingress
kubectl -n seatunnel-web logs deploy/seatunnel-web-api
kubectl -n seatunnel-web logs deploy/seatunnel-web-ui
```

## 12. Access Verification URLs

Replace `seatunnel-web.example.local` with the company internal host configured in Ingress.

- Frontend: `http://seatunnel-web.example.local/seatunnel-web-ui/`
- Swagger: `http://seatunnel-web.example.local/seatunnel-web-api/swagger-ui/index.html`
- Current user API: `http://seatunnel-web.example.local/seatunnel-web-api/api/v1/users/currentUser`

After login, the browser should remain under `/seatunnel-web-ui/`, for example:

- `/seatunnel-web-ui/`
- `/seatunnel-web-ui/data-source`

Refreshing `/seatunnel-web-ui/data-source` should return the frontend app and keep the original host and port.

## 13. Common Issues

### Frontend White Screen

Confirm the frontend was built with:

```bash
FRONTEND_BASE=/seatunnel-web-ui/ FRONTEND_PUBLIC_PATH=/seatunnel-web-ui/ API_BASE=/seatunnel-web-api npm run build
```

If the app was built with root public path, browser requests for JS and CSS may go to `/umi...js` instead of `/seatunnel-web-ui/umi...js`.

### Static Assets 404

Check nginx has copied dist into:

```text
/usr/share/nginx/html/seatunnel-web-ui/
```

Check `frontend/nginx.conf` uses:

```nginx
location ^~ /seatunnel-web-ui/ {
    root /usr/share/nginx/html;
    try_files $uri /seatunnel-web-ui/index.html;
}
```

Do not use `try_files $uri $uri/ ...` for this context path deployment because it can trigger redirects that lose the original port.

### API Requests Still Go to `/api/v1`

The frontend dist was probably built without `API_BASE=/seatunnel-web-api`. Rebuild the frontend and recreate the frontend image.

Verify the dist contains `/seatunnel-web-api` before building the image.

### Login Redirects to `/`

The frontend dist must include the context-path login redirect fix and `FRONTEND_BASE=/seatunnel-web-ui/`. Rebuild the frontend image from the latest branch.

### Refreshing Sub-Routes Drops Port or 404s

Use the verified nginx options:

```nginx
absolute_redirect off;
server_name_in_redirect off;
port_in_redirect off;
try_files $uri /seatunnel-web-ui/index.html;
```

Avoid `try_files $uri $uri/ /seatunnel-web-ui/index.html`.

### Swagger 404

Confirm the backend started with:

```text
SERVER_SERVLET_CONTEXT_PATH=/seatunnel-web-api
SERVER_PORT=9527
```

Then open:

```text
/seatunnel-web-api/swagger-ui/index.html
```

If Ingress routes `/seatunnel-web-api` incorrectly, check that the path is routed to `seatunnel-web-api` Service and the Service target port is `9527`.

### MariaDB Connection Fails

Check:

- JDBC URL host and port are reachable from inside the backend Pod.
- Secret username and password are correct.
- MariaDB user allows connections from K8s node or Pod network source addresses.
- Database name exists.
- Network policy or firewall allows traffic from K8s nodes to the MariaDB Linux servers.

Useful test:

```bash
kubectl -n seatunnel-web exec -it deploy/seatunnel-web-api -- sh
```

Then test DNS and TCP reachability using tools available in the image or a temporary debug Pod.

### SeaTunnel Engine Address Is Not Reachable in Container

An engine address that works on a developer laptop may not work from inside K8s. Configure the SeaTunnel Engine host and port to an address reachable from the backend Pod network, not `localhost` or a desktop-only IP.

