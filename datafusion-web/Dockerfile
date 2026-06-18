FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/node:22-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

ARG VITE_API_TARGET=http://datafusion-manager:8080
ENV VITE_API_TARGET=${VITE_API_TARGET}

RUN npm run build

FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/nginx:1.28.3

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
