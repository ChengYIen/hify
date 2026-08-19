# ============================================================
# Hify Makefile
# ============================================================

SHELL := bash
.SHELLFLAGS := -euo pipefail -c

# ---------- 可配置项 ----------
BACKEND_PORT  ?= 8080
FRONTEND_PORT ?= 5173
HEALTH_URL    ?= http://localhost:$(BACKEND_PORT)/api/v1/health
VERSION       := $(shell grep '<version>' pom.xml | head -1 | sed 's/.*<version>\(.*\)<\/version>/\1/')
PACKAGE_DIR   := target/package/hify-$(VERSION)
TARBALL       := target/hify-$(VERSION).tar.gz
MVNW          := ./mvnw
NPM           := npm

# 颜色
GREEN  := \033[0;32m
YELLOW := \033[1;33m
NC     := \033[0m

# ---------- 默认目标 ----------
.DEFAULT_GOAL := help

help: ## 显示帮助
	@echo "Hify 可用命令:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-14s$(NC) %s\n", $$1, $$2}'

# ============================================================
# 服务控制
# ============================================================

start: ## 启动所有服务（外部依赖检查 → 后端 → 前端）
	@bash start.sh

stop: ## 停止所有服务（优雅退出 → 超时强制终止）
	@bash stop.sh

restart: stop start ## 重启所有服务

# ============================================================
# 构建
# ============================================================

build-backend: ## 构建后端（Maven 全量打包，跳过测试）
	@echo -e "$(GREEN)[BUILD]$(NC) 构建后端 ..."
	@$(MVNW) clean package -DskipTests -q

build-frontend: ## 构建前端（TypeScript 检查 + Vite 打包）
	@echo -e "$(GREEN)[BUILD]$(NC) 构建前端 ..."
	@cd hify-web && $(NPM) run build

build: build-backend build-frontend ## 构建后端 + 前端

# ============================================================
# 清理
# ============================================================

clean-maven: ## 清理 Maven 构建产物
	@echo -e "$(GREEN)[CLEAN]$(NC) 清理 Maven target 目录 ..."
	@$(MVNW) clean -q

clean-frontend: ## 清理前端构建产物
	@echo -e "$(GREEN)[CLEAN]$(NC) 清理前端构建产物 ..."
	@rm -rf hify-web/dist hify-web/node_modules/.vite

clean-pids: ## 清理 PID 文件
	@rm -rf .pids

clean: clean-maven clean-frontend clean-pids ## 清理所有构建产物

# ============================================================
# 打包
# ============================================================

package: build ## 打包本地部署 tar.gz（不包含 JDK、MySQL、Redis 或 pgvector）
	@echo -e "$(GREEN)[PACKAGE]$(NC) 打包 $(TARBALL) ..."
	@rm -rf $(PACKAGE_DIR)
	@mkdir -p $(PACKAGE_DIR)/backend $(PACKAGE_DIR)/frontend
	@JAR_FILE=$$(find hify-app/target -maxdepth 1 -type f -name 'hify-app-*.jar' ! -name '*.original' | head -1); \
	  test -n "$$JAR_FILE" || { echo "未找到后端 Jar"; exit 1; }; \
	  cp "$$JAR_FILE" $(PACKAGE_DIR)/backend/hify-app.jar
	@cp -r hify-web/dist $(PACKAGE_DIR)/frontend/
	@cp deploy/local/application.yml $(PACKAGE_DIR)/application.yml
	@cp deploy/local/start.sh $(PACKAGE_DIR)/start.sh
	@cp deploy/local/stop.sh $(PACKAGE_DIR)/stop.sh
	@chmod +x $(PACKAGE_DIR)/start.sh $(PACKAGE_DIR)/stop.sh
	@mkdir -p target
	@rm -f $(TARBALL)
	@tar -czf $(TARBALL) -C target/package hify-$(VERSION)
	@rm -rf target/package
	@echo -e "$(GREEN)[PACKAGE]$(NC) 打包完成 → $(TARBALL)"

# ============================================================
# JDK 版本修复
# ============================================================

fix-jdk: ## 修复 JDK 25+ 启动参数
	@echo -e "$(GREEN)[FIX]$(NC) 创建 .mvn/jvm.config ..."
	@mkdir -p .mvn
	@echo "--add-opens java.base/java.lang=ALL-UNNAMED" > .mvn/jvm.config
	@echo "--add-opens java.base/java.util=ALL-UNNAMED" >> .mvn/jvm.config

.PHONY: help start stop restart build build-backend build-frontend clean clean-maven clean-frontend clean-pids package fix-jdk
