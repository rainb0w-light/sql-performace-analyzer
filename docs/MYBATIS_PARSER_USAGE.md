# MyBatis Mapper XML解析器使用指南

## 概述

本项目实现了MyBatis Mapper XML解析器，能够解析动态SQL并提取所有可能的SQL查询组合。

## 功能特性

- 解析MyBatis Mapper XML文件
- 提取所有SQL语句（select/insert/update/delete）
- 处理动态SQL标签（`<if>`, `<choose>`, `<when>`, `<otherwise>`, `<where>`, `<foreach>`, `<trim>`等）
- 生成所有可能的SQL组合（考虑test条件的所有分支）
- 存储解析结果到数据库

## API端点

### 1. 上传并解析MyBatis Mapper XML

**端点：** `POST /api/mybatis/upload`

**请求体：**
```json
{
  "xmlContent": "<mapper namespace=\"com.example.mapper.UserMapper\">...</mapper>",
  "mapperNamespace": "com.example.mapper.UserMapper"
}
```

**响应：**
```json
{
  "success": true,
  "mapperNamespace": "com.example.mapper.UserMapper",
  "queryCount": 10,
  "message": "成功解析 10 个SQL查询"
}
```

### 2. 根据表名查询相关SQL

**端点：** `GET /api/mybatis/queries/table/{tableName}`

**示例：**
```
GET /api/mybatis/queries/table/users
```

**响应：**
```json
{
  "success": true,
  "tableName": "users",
  "count": 5,
  "queries": [
    {
      "id": 1,
      "mapperNamespace": "com.example.mapper.UserMapper",
      "statementId": "selectById",
      "queryType": "select",
      "sql": "SELECT * FROM users WHERE id = ?",
      "tableName": "users",
      "dynamicConditions": "无动态条件"
    }
  ]
}
```

## 支持的动态SQL标签

### `<if>`
```xml
<select id="selectUser">
  SELECT * FROM users
  <if test="id != null">
    WHERE id = #{id}
  </if>
</select>
```

解析器会生成两个SQL变体：
1. `SELECT * FROM users WHERE id = ?` (当id != null时)
2. `SELECT * FROM users` (当id == null时)

### `<choose>`, `<when>`, `<otherwise>`
```xml
<select id="selectUser">
  SELECT * FROM users
  <choose>
    <when test="id != null">
      WHERE id = #{id}
    </when>
    <otherwise>
      WHERE status = 'active'
    </otherwise>
  </choose>
</select>
```

### `<foreach>`
```xml
<select id="selectUsersByIds">
  SELECT * FROM users
  WHERE id IN
  <foreach collection="ids" item="id" open="(" separator="," close=")">
    #{id}
  </foreach>
</select>
```

## 注意事项

1. 解析器会删除同一命名空间的旧数据，然后插入新解析的数据
2. 动态SQL的所有可能组合都会被生成并存储
3. 表名会自动从SQL中提取
4. 动态条件描述会记录生成每个SQL的条件组合

