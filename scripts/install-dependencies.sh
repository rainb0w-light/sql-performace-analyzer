#!/bin/bash
# Bash 脚本：批量安装 Spring AI 依赖到本地 Maven 仓库
# 使用方法：在包含下载的依赖文件的目录中运行此脚本

DEPENDENCIES_DIR="${1:-spring-ai-dependencies}"
GROUP_ID="${2:-org.springframework.ai}"

echo "开始安装依赖到本地 Maven 仓库..."
echo "依赖目录: $DEPENDENCIES_DIR"
echo "Group ID: $GROUP_ID"
echo ""

if [ ! -d "$DEPENDENCIES_DIR" ]; then
    echo "错误: 目录 $DEPENDENCIES_DIR 不存在！"
    exit 1
fi

# 检查是否找到 JAR 文件
jar_count=$(find "$DEPENDENCIES_DIR" -name "*.jar" | wc -l)
if [ "$jar_count" -eq 0 ]; then
    echo "错误: 在 $DEPENDENCIES_DIR 中未找到 JAR 文件！"
    exit 1
fi

success_count=0
fail_count=0

# 查找所有 JAR 文件并安装
find "$DEPENDENCIES_DIR" -name "*.jar" | while read jar_file; do
    pom_file="${jar_file%.jar}.pom"
    
    if [ ! -f "$pom_file" ]; then
        echo "警告: 未找到对应的 POM 文件: $pom_file"
        echo "跳过: $(basename "$jar_file")"
        fail_count=$((fail_count + 1))
        continue
    fi
    
    filename=$(basename "$jar_file" .jar)
    
    # 从文件名解析 artifactId 和 version
    # 格式：artifactId-version.jar
    # 例如：spring-ai-openai-spring-boot-starter-1.0.0-M4.jar
    if [[ $filename =~ ^(.+)-([0-9]+\.[0-9]+\.[0-9]+-M[0-9]+)$ ]]; then
        artifact_id="${BASH_REMATCH[1]}"
        version="${BASH_REMATCH[2]}"
        
        echo "安装 $artifact_id:$version..."
        
        if mvn install:install-file \
            -Dfile="$jar_file" \
            -DgroupId="$GROUP_ID" \
            -DartifactId="$artifact_id" \
            -Dversion="$version" \
            -Dpackaging=jar \
            -DpomFile="$pom_file" \
            -DgeneratePom=false > /dev/null 2>&1; then
            echo "  ✓ 成功安装 $artifact_id:$version"
            success_count=$((success_count + 1))
        else
            echo "  ✗ 安装失败: $artifact_id:$version"
            fail_count=$((fail_count + 1))
        fi
    else
        echo "警告: 无法解析文件名格式: $filename"
        echo "  期望格式: artifactId-version.jar"
        fail_count=$((fail_count + 1))
    fi
    
    echo ""
done

echo "=================================================="
echo "安装完成！"
echo "成功: $success_count"
echo "失败: $fail_count"
echo "=================================================="

if [ $fail_count -gt 0 ]; then
    echo ""
    echo "提示: 如果安装失败，请检查："
    echo "1. Maven 是否正确安装并配置在 PATH 中"
    echo "2. POM 文件是否存在且格式正确"
    echo "3. 文件名是否符合 artifactId-version.jar 格式"
    exit 1
fi





