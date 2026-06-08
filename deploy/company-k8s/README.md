# Company K8s / Rancher Deployment

This directory contains example files for the company internal K8s, Rancher, Harbor, and Nexus deployment validation branch.

It deploys only:

- `seatunnel-web-api`
- `seatunnel-web-ui`

It does not deploy MariaDB. The production database is an external MariaDB cluster or standalone servers managed outside Kubernetes.

## Paths

- Frontend: `/seatunnel-web-ui/`
- Backend: `/seatunnel-web-api/`
- Swagger: `/seatunnel-web-api/swagger-ui/index.html`

## Files

- `backend/Dockerfile`: backend JRE image example.
- `backend/application-prod.yml.example`: backend production datasource/context-path example.
- `frontend/Dockerfile`: nginx image that serves `seatunnel-web-ui/dist`.
- `frontend/nginx.conf`: verified SPA nginx config for `/seatunnel-web-ui/`.
- `k8s/*.yaml`: namespace, database Secret/ConfigMap examples, Deployments, Services, and Ingress.

See `docs/deploy/company-k8s-rancher.md` for the full workflow.

