# SQL 性能分析工具 - 前端

基于 Vite + Vue 3 + Element Plus 的前端应用。

## 技术栈

- **构建工具**: Vite 5.x
- **框架**: Vue 3 (Composition API)
- **UI 组件库**: Element Plus 2.x
- **路由**: Vue Router 4.x
- **Markdown**: marked
- **PDF 导出**: html2pdf.js

## 开发

### 安装依赖

```bash
cd frontend
npm install
```

### 启动开发服务器

```bash
npm run dev
```

开发服务器将在 `http://localhost:3000` 启动，并自动代理 API 请求到后端服务器（`http://localhost:8080`）。

### 构建生产版本

```bash
npm run build
```

构建产物将输出到 `../src/main/resources/static/` 目录，供 Spring Boot 使用。

## 项目结构

```
frontend/
├── src/
│   ├── main.js              # 应用入口
│   ├── App.vue              # 根组件
│   ├── router/              # 路由配置
│   ├── components/          # 组件
│   │   ├── common/          # 公共组件
│   │   ├── sql-analysis/    # SQL 分析组件
│   │   ├── table-analysis/  # 表分析组件
│   │   ├── sql-agent/       # SQL Agent 组件
│   │   └── prompt-management/ # Prompt 管理组件
│   ├── views/               # 页面视图
│   ├── api/                 # API 封装
│   ├── utils/               # 工具函数
│   └── styles/              # 样式文件
├── public/                  # 静态资源
├── package.json
└── vite.config.js
```

## 功能模块

1. **SQL 分析**: 单条 SQL 性能分析
2. **表分析**: 表结构分析和查询优化建议
3. **SQL Agent 分析**: 基于 AI 的智能 SQL 分析
4. **Prompt 管理**: Prompt 模板管理

## 注意事项

1. 确保后端服务运行在 `http://localhost:8080`
2. 开发时使用 Vite 代理解决 CORS 问题
3. 构建后的文件会自动部署到 Spring Boot static 目录
