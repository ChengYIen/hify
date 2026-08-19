# ---------- 构建阶段 ----------
FROM maven:3.9.16-eclipse-temurin-17-alpine AS build

WORKDIR /workspace

# 先复制 POM，利用 Docker 层缓存复用 Maven 依赖。
COPY pom.xml ./
COPY hify-common/pom.xml hify-common/pom.xml
COPY hify-shared/pom.xml hify-shared/pom.xml
COPY hify-module-provider/pom.xml hify-module-provider/pom.xml
COPY hify-module-agent/pom.xml hify-module-agent/pom.xml
COPY hify-module-conversation/pom.xml hify-module-conversation/pom.xml
COPY hify-module-knowledge/pom.xml hify-module-knowledge/pom.xml
COPY hify-module-workflow/pom.xml hify-module-workflow/pom.xml
COPY hify-module-mcp/pom.xml hify-module-mcp/pom.xml
COPY hify-app/pom.xml hify-app/pom.xml
RUN mvn -B -DskipTests dependency:go-offline

# 再复制源码，源码变更不会导致依赖重新下载。
COPY hify-common hify-common
COPY hify-shared hify-shared
COPY hify-module-provider hify-module-provider
COPY hify-module-agent hify-module-agent
COPY hify-module-conversation hify-module-conversation
COPY hify-module-knowledge hify-module-knowledge
COPY hify-module-workflow hify-module-workflow
COPY hify-module-mcp hify-module-mcp
COPY hify-app hify-app
RUN mvn -B -pl hify-app -am clean package -DskipTests

# ---------- Spring Boot 分层 ----------
# Layertools 将依赖和业务代码拆开，业务代码变更时只重建 application 层。
FROM build AS layers

WORKDIR /workspace/layers
RUN java -Djarmode=layertools \
    -jar /workspace/hify-app/target/hify-app-*.jar extract

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S -g 10001 hify \
    && adduser -S -D -H -s /sbin/nologin -G hify -u 10001 hify

WORKDIR /app

# 顺序对应 Spring Boot layertools 的四层。
COPY --from=layers /workspace/layers/dependencies/ ./
COPY --from=layers /workspace/layers/spring-boot-loader/ ./
COPY --from=layers /workspace/layers/snapshot-dependencies/ ./
COPY --from=layers /workspace/layers/application/ ./

ENV SERVER_PORT=8080 \
    HIFY_UPLOAD_DIR=/var/lib/hify/upload

RUN mkdir -p /app/config /app/logs /var/lib/hify/upload \
    && chown -R hify:hify /app /var/lib/hify

USER hify
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O - http://127.0.0.1:8080/api/v1/health >/dev/null || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher", "--spring.config.additional-location=optional:file:/app/config/"]
