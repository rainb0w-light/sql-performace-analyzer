const API_BASE = '/api'

async function request(url, options = {}) {
  const response = await fetch(`${API_BASE}${url}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options.headers
    },
    ...options
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({ message: '请求失败' }))
    throw new Error(errorData.message || errorData.error || `HTTP ${response.status}: ${response.statusText}`)
  }

  return response.json()
}

// 数据源相关
export async function getDatasources() {
  return request('/sql/datasources')
}

// LLM 相关
export async function getLlms() {
  return request('/sql/llms')
}

// SQL 分析
export async function analyzeSql(data) {
  return request('/sql/analyze', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

// 下载 Markdown 报告
export async function downloadSqlReport(data) {
  const response = await fetch(`${API_BASE}/sql/reports/download`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(data)
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({ message: '下载失败' }))
    throw new Error(errorData.message || `HTTP ${response.status}: ${response.statusText}`)
  }

  return response
}

// 表分析
export async function analyzeTable(tableName, datasourceName) {
  const url = `/analysis/table/${encodeURIComponent(tableName)}${datasourceName ? `?datasourceName=${encodeURIComponent(datasourceName)}` : ''}`
  return request(url)
}

// MyBatis Mapper 上传
export async function uploadMapperXml(data) {
  return request('/mybatis/upload', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

// 获取所有命名空间
export async function getNamespaces() {
  const data = await request('/mybatis/namespaces')
  return data.namespaces || []
}

// 根据命名空间获取查询列表
export async function getQueriesByNamespace(namespace) {
  const data = await request(`/mybatis/queries/namespace/${encodeURIComponent(namespace)}`)
  return data.queries || []
}

// 根据命名空间获取参数列表
export async function getParametersByNamespace(namespace) {
  const data = await request(`/mybatis/parameters/namespace/${encodeURIComponent(namespace)}`)
  return data.parameters || []
}

// 参数管理
export async function getParameters() {
  const data = await request('/mybatis/parameters')
  return data.parameters || []
}

export async function saveParameter(data) {
  return request('/mybatis/parameters', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export async function deleteParameter(mapperId) {
  return request(`/mybatis/parameters/${encodeURIComponent(mapperId)}`, {
    method: 'DELETE'
  })
}

// 更新SQL查询
export async function updateQuery(id, data) {
  return request(`/mybatis/queries/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data)
  })
}

// 删除SQL查询
export async function deleteQuery(id) {
  return request(`/mybatis/queries/${id}`, {
    method: 'DELETE'
  })
}

// 批量删除SQL查询
export async function deleteQueries(ids) {
  return request('/mybatis/queries', {
    method: 'DELETE',
    body: JSON.stringify(ids)
  })
}

// 更新Mapper参数
export async function updateParameter(id, data) {
  return request(`/mybatis/parameters/id/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data)
  })
}

// 删除Mapper参数（根据ID）
export async function deleteParameterById(id) {
  return request(`/mybatis/parameters/id/${id}`, {
    method: 'DELETE'
  })
}

// 批量删除Mapper参数
export async function deleteParameters(ids) {
  return request('/mybatis/parameters/batch', {
    method: 'DELETE',
    body: JSON.stringify(ids)
  })
}

// SQL Agent 分析
export async function analyzeAgent(data) {
  return request('/sql-agent/analyze', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export async function analyzeMapper(data) {
  return request('/sql-agent/analyze-mapper', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

// Prompt 模板管理
export async function getPrompts() {
  return request('/prompts')
}

export async function updatePrompt(templateType, content) {
  return request(`/prompts/${templateType}`, {
    method: 'PUT',
    body: JSON.stringify({ content })
  })
}

export async function createPrompt(data) {
  return request('/prompts', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}


