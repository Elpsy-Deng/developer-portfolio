ARG NGINX_IMAGE=nginx:1.28-alpine@sha256:a8b39bd9cf0f83869a2162827a0caf6137ddf759d50a171451b335cecc87d236
FROM ${NGINX_IMAGE}
COPY infra/nginx.conf /etc/nginx/nginx.conf
COPY dist/ /usr/share/nginx/html/
RUN chmod 0444 /etc/nginx/nginx.conf && chmod -R a+rX /usr/share/nginx/html
USER 101:101
ENTRYPOINT ["nginx", "-g", "daemon off;"]
CMD []
