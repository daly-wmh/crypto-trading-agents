<template>
  <el-config-provider :locale="language === 'en-US' ? en : zhCn">
    <div class="app-shell">
      <header ref="header" class="app-header" @keydown.esc="closeNavigation(true)">
        <div class="header-inner">
          <router-link to="/" class="app-brand" @click="closeNavigation()">
            <span class="brand-mark" aria-hidden="true"><el-icon><TrendCharts /></el-icon></span>
            <span>{{ t('加密投研') }}</span>
          </router-link>
          <button ref="toggle" class="navigation-toggle" type="button" aria-controls="workbench-navigation"
                  :aria-expanded="navigationOpen" :aria-label="t(navigationOpen ? '关闭功能导航' : '打开功能导航')"
                  @click="navigationOpen ? closeNavigation() : navigationOpen = true">
            <el-icon><component :is="navigationOpen ? Close : Menu" /></el-icon>
          </button>
          <nav id="workbench-navigation" class="app-nav" :class="{ 'is-open': navigationOpen }"
               :aria-label="t('功能导航')">
            <div v-for="group in navigation" :key="group.id" class="nav-group"
                 :class="{ 'nav-utility': group.id === 'language' || group.id === 'settings' }">
              <router-link v-if="group.path" :to="group.path" class="nav-trigger nav-direct"
                           :class="{ 'is-active': route.path === group.path }"
                           :aria-current="route.path === group.path ? 'page' : undefined" @click="closeNavigation()">
                <el-icon aria-hidden="true"><component :is="group.icon" /></el-icon>
                <span>{{ t(group.label) }}</span>
              </router-link>
              <template v-else>
                <button :id="'nav-' + group.id" type="button" class="nav-trigger"
                        :class="{ 'is-active': currentGroup?.id === group.id }"
                        :aria-expanded="openGroup === group.id" :aria-controls="'links-' + group.id"
                        @click="openGroup = openGroup === group.id ? null : group.id">
                  <el-icon aria-hidden="true"><component :is="group.id === 'settings' && theme === 'night' ? Moon : group.icon" /></el-icon>
                  <span>{{ t(group.label) }}</span>
                  <el-icon class="nav-chevron" aria-hidden="true"><ArrowDown /></el-icon>
                </button>
                <ul v-show="openGroup === group.id" :id="'links-' + group.id" class="nav-popover">
                  <li v-for="option in group.choices" :key="option.value">
                    <button class="nav-link nav-choice" type="button" :lang="option.value"
                            :aria-pressed="language === option.value" @click="selectLanguage(option.value)">
                      {{ option.label }}
                      <el-icon v-if="language === option.value" aria-hidden="true"><Check /></el-icon>
                    </button>
                  </li>
                  <li v-for="item in group.items" :key="item.path">
                    <router-link :to="item.path" class="nav-link" :aria-current="route.path === item.path ? 'page' : undefined"
                                 @click="closeNavigation()">
                      <el-icon aria-hidden="true"><component :is="item.icon" /></el-icon>
                      <span>{{ t(item.label) }}</span>
                    </router-link>
                  </li>
                </ul>
              </template>
            </div>
          </nav>
        </div>
      </header>

      <main class="app-main">
        <router-view v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </main>
    </div>
  </el-config-provider>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import {
  ArrowDown, ChatDotRound, ChatLineRound, Check, Close, Coin, Collection, DataAnalysis, Document,
  Histogram, Menu, Moon, Operation, Reading, Setting, Timer, TrendCharts
} from '@element-plus/icons-vue'
import en from 'element-plus/dist/locale/en.mjs'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import { language, languages, theme, t } from './preferences.js'

const route = useRoute()
const header = ref(null)
const toggle = ref(null)
const navigationOpen = ref(false)
const openGroup = ref(null)
const navigation = [
  {
    id: 'research', label: '市场与研究', icon: TrendCharts,
    items: [
      { path: '/', label: '智能分析', icon: DataAnalysis },
      { path: '/market', label: '现货行情', icon: Histogram }
    ]
  },
  { id: 'news', path: '/news', label: '新闻快讯', icon: Reading },
  { id: 'trading', path: '/trading', label: '模拟交易', icon: Coin },
  {
    id: 'library', label: '研究资产', icon: Collection,
    items: [
      { path: '/reports', label: '历史报告', icon: Document },
      { path: '/memories', label: '经验库', icon: Collection },
      { path: '/chat', label: '投研顾问', icon: ChatDotRound }
    ]
  },
  {
    id: 'operations', label: '工作流', icon: Operation,
    items: [
      { path: '/pipeline', label: '流水线', icon: Operation },
      { path: '/tasks', label: '定时任务', icon: Timer }
    ]
  },
  {
    id: 'language', label: '语言', icon: ChatLineRound, choices: languages
  },
  {
    id: 'settings', label: '设置', icon: Setting,
    items: [
      { path: '/preferences', label: '个性化', icon: Moon },
      { path: '/settings', label: '模型设置', icon: Setting }
    ]
  }
]
const currentGroup = computed(() => navigation.find(group =>
  group.path === route.path || group.items?.some(item => item.path === route.path)))
const currentPage = computed(() => currentGroup.value?.items
  ? currentGroup.value.items.find(item => item.path === route.path)
  : currentGroup.value)

function closeNavigation(restoreFocus = false) {
  if (restoreFocus) {
    const trigger = navigationOpen.value ? toggle.value : document.getElementById('nav-' + openGroup.value)
    trigger?.focus()
  }
  openGroup.value = null
  navigationOpen.value = false
}

function closeOutside(event) {
  if (!header.value?.contains(event.target)) closeNavigation()
}

function selectLanguage(value) {
  language.value = value
  closeNavigation(true)
}

watch(() => route.path, () => closeNavigation())
watchEffect(() => {
  const page = currentPage.value?.label
  document.title = page ? t(page) + ' · ' + t('加密投研') : t('加密投研')
})
onMounted(() => document.addEventListener('pointerdown', closeOutside))
onUnmounted(() => document.removeEventListener('pointerdown', closeOutside))
</script>
