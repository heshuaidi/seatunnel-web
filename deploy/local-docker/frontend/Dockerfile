FROM nginx:1.25-alpine

COPY deploy/local-docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY seatunnel-web-ui/dist/ /usr/share/nginx/html/seatunnel-web-ui/
