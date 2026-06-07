FROM nginx:1.27-alpine

COPY deploy/company-k8s/frontend/nginx.conf /etc/nginx/nginx.conf
COPY seatunnel-web-ui/dist/ /usr/share/nginx/html/seatunnel-web-ui/

EXPOSE 80
