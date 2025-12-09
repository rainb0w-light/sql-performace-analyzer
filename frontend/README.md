# SQL 性能分析工具 - 前端

简单的 Vue 3 前端页面，用于 SQL 性能分析。

## 功能特性

- ✅ 输入 SQL 语句
- ✅ 调用后端 API 进行分析
- ✅ Markdown 格式报告展示
- ✅ 导出 PDF 功能

## 使用方法

### 方式一：直接打开 HTML 文件

1. 确保后端服务已启动（默认运行在 `http://localhost:8080`）
2. 直接用浏览器打开 `index.html` 文件

### 方式二：使用简单的 HTTP 服务器

由于浏览器的 CORS 限制，建议使用本地服务器：

```bash
# 使用 Python 3
cd frontend
python -m http.server 8000

# 或使用 Node.js http-server
npx http-server -p 8000

# 或使用 PHP
php -S localhost:8000
```

然后在浏览器中访问 `http://localhost:8000`

## 配置

如果需要修改后端 API 地址，编辑 `index.html` 中的 `apiUrl` 变量：

```javascript
apiUrl: 'http://localhost:8080/api/sql/analyze'
```

## 依赖说明

所有依赖都通过 CDN 加载，无需安装：

- **Vue 3**: 前端框架
- **Marked**: Markdown 解析和渲染
- **html2pdf.js**: PDF 导出功能

## 浏览器兼容性

- Chrome/Edge (推荐)
- Firefox
- Safari

## 注意事项

1. 如果遇到 CORS 错误，需要配置后端允许跨域请求
2. PDF 导出功能需要浏览器支持 Canvas API
3. 建议使用现代浏览器以获得最佳体验





