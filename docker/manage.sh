#!/bin/bash

# ============================================================
# Docker MySQL Container Management Script
# 用于管理 Docker MySQL 容器的脚本
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
COMPOSE_FILE="docker-compose.yml"
LOG_DIR="./docker/mysql/logs"
INIT_DIR="./docker/mysql/init"

# 显示帮助信息
show_help() {
    cat << EOF
${BLUE}Docker MySQL 容器管理脚本${NC}

用法：$0 <command> [options]

${YELLOW}容器管理命令:${NC}
  start [version]       启动 MySQL 容器 (version: 8.0, 5.7, 8.3, 默认：8.0)
  stop                  停止所有 MySQL 容器
  restart [version]     重启 MySQL 容器
  status                显示所有容器状态
  logs [version]        查看容器日志
  ps                    显示运行中的容器

${YELLOW}数据管理命令:${NC}
  init                  初始化数据库（删除所有数据并重新创建）
  backup                备份数据库
  restore               恢复数据库
  clean                 清理所有数据卷

${YELLOW}测试命令:${NC}
  test-conn [version]   测试数据库连接
  info [version]        显示数据库信息

${YELLOW}配置文件:${NC}
  compose-file:         ${COMPOSE_FILE}
  log-dir:              ${LOG_DIR}
  init-dir:             ${INIT_DIR}

${YELLOW}示例:${NC}
  $0 start              # 启动默认 MySQL 8.0
  $0 start 5.7          # 启动 MySQL 5.7
  $0 init               # 重新初始化数据库
  $0 test-conn          # 测试数据库连接

EOF
}

# 启动容器
start_container() {
    local version=${1:-8.0}
    
    echo -e "${BLUE}启动 MySQL ${version}...${NC}"
    
    case $version in
        5.7)
            docker-compose -f $COMPOSE_FILE --profile mysql-5.7 up -d
            ;;
        8.0)
            docker-compose -f $COMPOSE_FILE up -d
            ;;
        8.3)
            docker-compose -f $COMPOSE_FILE --profile mysql-8.3 up -d
            ;;
        all)
            docker-compose -f $COMPOSE_FILE --profile all up -d
            ;;
        *)
            echo -e "${RED}错误：不支持的 MySQL 版本：$version${NC}"
            echo -e "${YELLOW}支持的版本：5.7, 8.0, 8.3, all${NC}"
            exit 1
            ;;
    esac
    
    # 等待 MySQL 启动
    echo -e "${YELLOW}等待 MySQL 启动...${NC}"
    sleep 10
    
    # 检查健康状态
    if docker-compose -f $COMPOSE_FILE ps | grep -q "healthy"; then
        echo -e "${GREEN}✓ MySQL ${version} 已成功启动${NC}"
    else
        echo -e "${YELLOW}⚠ MySQL 可能还在启动中，请稍后检查状态${NC}"
    fi
}

# 停止容器
stop_container() {
    echo -e "${BLUE}停止所有 MySQL 容器...${NC}"
    docker-compose -f $COMPOSE_FILE down
    echo -e "${GREEN}✓ 所有容器已停止${NC}"
}

# 重启容器
restart_container() {
    local version=${1:-}
    stop_container
    sleep 2
    if [ -n "$version" ]; then
        start_container $version
    else
        start_container 8.0
    fi
}

# 显示容器状态
show_status() {
    echo -e "${BLUE}MySQL 容器状态:${NC}"
    docker-compose -f $COMPOSE_FILE ps
    
    echo -e "\n${BLUE}所有 Docker 容器:${NC}"
    docker ps -a --filter "name=sql-analyzer" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

# 查看日志
view_logs() {
    local version=${1:-8.0}
    local container_name
    
    case $version in
        5.7)
            container_name="sql-analyzer-mysql-5.7"
            ;;
        8.0)
            container_name="sql-analyzer-mysql-8.0"
            ;;
        8.3)
            container_name="sql-analyzer-mysql-8.3"
            ;;
        *)
            container_name="sql-analyzer-mysql-8.0"
            ;;
    esac
    
    echo -e "${BLUE}查看 $container_name 日志...${NC}"
    docker logs -f $container_name
}

# 初始化数据库
init_database() {
    echo -e "${RED}⚠ 警告：此操作将删除所有 MySQL 数据并重新初始化！${NC}"
    read -p "确定要继续吗？(yes/no): " -r
    echo
    if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
        echo -e "${YELLOW}操作已取消${NC}"
        exit 0
    fi
    
    echo -e "${BLUE}停止容器...${NC}"
    docker-compose -f $COMPOSE_FILE down
    
    echo -e "${BLUE}删除数据卷...${NC}"
    docker volume rm $(docker volume ls -q | grep sql-performace-analyzer) 2>/dev/null || true
    
    echo -e "${BLUE}清理日志...${NC}"
    rm -rf ${LOG_DIR}/*
    
    echo -e "${GREEN}✓ 清理完成，现在可以重新启动容器${NC}"
    echo -e "${YELLOW}运行：$0 start${NC}"
}

# 备份数据库
backup_database() {
    local backup_file="mysql_backup_$(date +%Y%m%d_%H%M%S).sql"
    local backup_path="./backups/${backup_file}"
    
    mkdir -p ./backups
    
    echo -e "${BLUE}备份数据库到 ${backup_path}...${NC}"
    docker exec sql-analyzer-mysql-8.0 mysqldump -u root -ppassword --all-databases > $backup_path
    
    if [ -f "$backup_path" ]; then
        echo -e "${GREEN}✓ 备份成功：$backup_path${NC}"
    else
        echo -e "${RED}✗ 备份失败${NC}"
        exit 1
    fi
}

# 恢复数据库
restore_database() {
    local backup_file=$1
    
    if [ -z "$backup_file" ]; then
        echo -e "${RED}错误：请提供备份文件路径${NC}"
        echo -e "${YELLOW}用法：$0 restore <backup_file>${NC}"
        exit 1
    fi
    
    if [ ! -f "$backup_file" ]; then
        echo -e "${RED}错误：备份文件不存在：$backup_file${NC}"
        exit 1
    fi
    
    echo -e "${RED}⚠ 警告：此操作将覆盖当前数据库数据！${NC}"
    read -p "确定要继续吗？(yes/no): " -r
    echo
    
    if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
        echo -e "${YELLOW}操作已取消${NC}"
        exit 0
    fi
    
    echo -e "${BLUE}恢复数据库...${NC}"
    cat $backup_file | docker exec -i sql-analyzer-mysql-8.0 mysql -u root -ppassword
    
    echo -e "${GREEN}✓ 数据库恢复成功${NC}"
}

# 清理数据卷
clean_volumes() {
    echo -e "${RED}⚠ 警告：此操作将删除所有 Docker 数据卷！${NC}"
    read -p "确定要继续吗？(yes/no): " -r
    echo
    
    if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
        echo -e "${YELLOW}操作已取消${NC}"
        exit 0
    fi
    
    echo -e "${BLUE}停止所有容器...${NC}"
    docker-compose -f $COMPOSE_FILE down -v
    
    echo -e "${GREEN}✓ 数据卷已清理${NC}"
}

# 测试数据库连接
test_connection() {
    local version=${1:-8.0}
    local port
    local container_name
    
    case $version in
        5.7)
            port=3307
            container_name="sql-analyzer-mysql-5.7"
            ;;
        8.0)
            port=3306
            container_name="sql-analyzer-mysql-8.0"
            ;;
        8.3)
            port=3308
            container_name="sql-analyzer-mysql-8.3"
            ;;
        *)
            port=3306
            container_name="sql-analyzer-mysql-8.0"
            ;;
    esac
    
    echo -e "${BLUE}测试连接到 MySQL ${version} (端口：$port)...${NC}"
    
    # 检查容器是否运行
    if ! docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
        echo -e "${RED}✗ 容器未运行：$container_name${NC}"
        exit 1
    fi
    
    # 使用 mysql 客户端测试连接
    if command -v mysql &> /dev/null; then
        if mysql -h 127.0.0.1 -P $port -u root -ppassword -e "SELECT 1;" &> /dev/null; then
            echo -e "${GREEN}✓ 连接成功！${NC}"
        else
            echo -e "${RED}✗ 连接失败${NC}"
            exit 1
        fi
    else
        # 如果没有 mysql 客户端，使用 docker exec 测试
        if docker exec $container_name mysqladmin -u root -ppassword ping &> /dev/null; then
            echo -e "${GREEN}✓ 连接成功！${NC}"
        else
            echo -e "${RED}✗ 连接失败${NC}"
            exit 1
        fi
    fi
}

# 显示数据库信息
show_info() {
    local version=${1:-8.0}
    local port
    local container_name
    
    case $version in
        5.7)
            port=3307
            container_name="sql-analyzer-mysql-5.7"
            ;;
        8.0)
            port=3306
            container_name="sql-analyzer-mysql-8.0"
            ;;
        8.3)
            port=3308
            container_name="sql-analyzer-mysql-8.3"
            ;;
        *)
            port=3306
            container_name="sql-analyzer-mysql-8.0"
            ;;
    esac
    
    echo -e "${BLUE}=== MySQL ${version} 信息 ===${NC}"
    echo -e "容器名称：${container_name}"
    echo -e "端口：${port}"
    echo -e "\n${YELLOW}数据库列表:${NC}"
    docker exec $container_name mysql -u root -ppassword -e "SHOW DATABASES;"
    
    echo -e "\n${YELLOW}test_db 表:${NC}"
    docker exec $container_name mysql -u root -ppassword test_db -e "SHOW TABLES;"
    
    echo -e "\n${YELLOW}表统计信息:${NC}"
    docker exec $container_name mysql -u root -ppassword test_db -e "SELECT * FROM v_table_stats;" 2>/dev/null || \
    docker exec $container_name mysql -u root -ppassword test_db -e "SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA='test_db';"
}

# 主函数
main() {
    local command=$1
    shift
    
    case $command in
        start)
            start_container "$@"
            ;;
        stop)
            stop_container
            ;;
        restart)
            restart_container "$@"
            ;;
        status|ps)
            show_status
            ;;
        logs)
            view_logs "$@"
            ;;
        init)
            init_database
            ;;
        backup)
            backup_database
            ;;
        restore)
            restore_database "$@"
            ;;
        clean)
            clean_volumes
            ;;
        test-conn)
            test_connection "$@"
            ;;
        info)
            show_info "$@"
            ;;
        help|-h|--help)
            show_help
            ;;
        "")
            show_help
            ;;
        *)
            echo -e "${RED}错误：未知命令：$command${NC}"
            echo -e "${YELLOW}使用 '$0 help' 查看帮助${NC}"
            exit 1
            ;;
    esac
}

main "$@"
