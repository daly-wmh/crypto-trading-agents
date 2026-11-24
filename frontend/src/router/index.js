import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Analysis',
    component: () => import('../views/AnalysisView.vue')
  },
  {
    path: '/market',
    name: 'MarketData',
    component: () => import('../views/MarketDataView.vue')
  },
  {
    path: '/news',
    name: 'News',
    component: () => import('../views/NewsView.vue')
  },
  {
    path: '/trading',
    name: 'PaperTrading',
    component: () => import('../views/PaperTradingView.vue')
  },
  {
    path: '/reports',
    name: 'Reports',
    component: () => import('../views/ReportsView.vue')
  },
  {
    path: '/pipeline',
    name: 'Pipeline',
    component: () => import('../views/PipelineView.vue')
  },
  {
    path: '/memories',
    name: 'Memories',
    component: () => import('../views/MemoryView.vue')
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('../views/ChatView.vue')
  },
  {
    path: '/tasks',
    name: 'ScheduledTasks',
    component: () => import('../views/ScheduledTasksView.vue')
  },
  {
    path: '/preferences',
    name: 'Appearance',
    component: () => import('../views/AppearanceView.vue')
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('../views/SettingsView.vue')
  }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
