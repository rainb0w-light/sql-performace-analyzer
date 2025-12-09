#!/bin/bash
# 下载 Spring AI 依赖脚本
# 使用方法：在有外网的环境中运行此脚本，然后将下载的文件传输到内网环境

set -e

BASE_URL="https://repo.spring.io/milestone"
GROUP_ID="org.springframework.ai"
#ARTIFACT_ID="spring-ai-openai-spring-boot-starter"
#ARTIFACT_ID="spring-ai-openai"
#ARTIFACT_ID="spring-ai-retry"
#ARTIFACT_ID="spring-ai-spring-boot-autoconfigure"
ARTIFACT_ID="spring-ai-core"


VERSION="1.0.0-M4"

DOWNLOAD_DIR="spring-ai-dependencies"
mkdir -p "$DOWNLOAD_DIR"

echo "开始下载 Spring AI 依赖..."

# 下载 POM 文件
POM_URL="${BASE_URL}/${GROUP_ID//./\/}/${ARTIFACT_ID}/${VERSION}/${ARTIFACT_ID}-${VERSION}.pom"
echo "下载 POM: $POM_URL"
curl -L -o "$DOWNLOAD_DIR/${ARTIFACT_ID}-${VERSION}.pom" "$POM_URL"

# 下载 JAR 文件
JAR_URL="${BASE_URL}/${GROUP_ID//./\/}/${ARTIFACT_ID}/${VERSION}/${ARTIFACT_ID}-${VERSION}.jar"
echo "下载 JAR: $JAR_URL"
curl -L -o "$DOWNLOAD_DIR/${ARTIFACT_ID}-${VERSION}.jar" "$JAR_URL"

echo "下载完成！文件保存在 $DOWNLOAD_DIR 目录"
echo ""
echo "接下来需要："
echo "1. 下载所有传递依赖（使用 Gradle 或 Maven 的依赖树功能）"
echo "2. 将文件安装到本地 Maven 仓库或内网制品库"
echo ""
echo "安装到本地 Maven 仓库的命令："
echo "mvn install:install-file \\"
echo "  -Dfile=$DOWNLOAD_DIR/${ARTIFACT_ID}-${VERSION}.jar \\"
echo "  -DgroupId=${GROUP_ID} \\"
echo "  -DartifactId=${ARTIFACT_ID} \\"
echo "  -Dversion=${VERSION} \\"
echo "  -Dpackaging=jar \\"
echo "  -DpomFile=$DOWNLOAD_DIR/${ARTIFACT_ID}-${VERSION}.pom"

