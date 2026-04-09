# MySQL Initialization Scripts

This directory contains SQL initialization scripts that are automatically executed when the MySQL container is first started.

## Files

### 01-init-database.sql
Basic test database setup with:
- `users` table - 示例用户数据
- `orders` table - 订单数据  
- `order_items` table - 订单项数据

Suitable for basic SQL analysis and optimization testing.

### 02-goldendb-simulation.sql
GoldenDB distributed database simulation with:
- **Global Tables**: `global_org`, `global_param`
- **Sharded Tables**: `customer_shard_0`, `account_shard_0`, `transaction_log_shard_0`
- **Views**: `v_customer_all`, `v_account_all`, `v_transaction_all`, `v_table_stats`, `v_index_stats`
- **Analysis Tables**: `slow_query_log`
- Sample financial institution data

Suitable for distributed database scenarios and complex SQL optimization testing.

## How It Works

When you start the MySQL container for the first time:

```bash
docker-compose up -d
```

Docker automatically:
1. Creates the MySQL container
2. Executes all `.sql` files in `/docker-entrypoint-initdb.d/` (mounted from this directory)
3. Creates the database, tables, and inserts sample data

## Re-initialization

To re-run the initialization scripts (WARNING: deletes all data):

```bash
# Stop and remove containers + volumes
docker-compose down -v

# Start fresh
docker-compose up -d
```

## Management

Use the management script for common operations:

```bash
# View database info
../../manage.sh info

# Test connection
../../manage.sh test-conn

# Re-initialize (deletes all data)
../../manage.sh init
```

## Data Volumes

Data is persisted in Docker volumes:
- `mysql_8.0_data` - MySQL 8.0 data
- `mysql_5.7_data` - MySQL 5.7 data  
- `mysql_8.3_data` - MySQL 8.3 data

To remove all data: `docker-compose down -v`
