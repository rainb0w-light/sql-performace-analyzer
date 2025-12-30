import { createRouter, createWebHistory } from 'vue-router'
import SqlAnalysis from '../views/SqlAnalysis.vue'
import TableAnalysis from '../views/TableAnalysis.vue'
import SqlAgentAnalysis from '../views/SqlAgentAnalysis.vue'
import PromptManagement from '../views/PromptManagement.vue'

const routes = [
  {
    path: '/',
    name: 'SqlAnalysis',
    component: SqlAnalysis,
    meta: {
      title: 'SQL 分析',
      breadcrumb: ['首页', 'SQL 性能分析']
    }
  },
  {
    path: '/table-analysis',
    name: 'TableAnalysis',
    component: TableAnalysis,
    meta: {
      title: '表分析',
      breadcrumb: ['首页', '表分析']
    }
  },
  {
    path: '/sql-agent-analysis',
    name: 'SqlAgentAnalysis',
    component: SqlAgentAnalysis,
    meta: {
      title: 'Agent 分析',
      breadcrumb: ['首页', 'SQL Agent 智能分析']
    }
  },
  {
    path: '/prompt-management',
    name: 'PromptManagement',
    component: PromptManagement,
    meta: {
      title: '模板管理',
      breadcrumb: ['首页', 'Prompt 模板管理']
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = `${to.meta.title || 'SQL 性能分析工具'} | SQL 性能分析工具`
  next()
})

export default router


