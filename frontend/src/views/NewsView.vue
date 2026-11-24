<template>
  <section class="news-view">
    <header class="page-heading glass-panel">
      <div>
        <h1>{{ tr("新闻快讯") }}</h1>
        <p class="feed-status">
          <span>{{ tr("加密市场相关事件") }}</span>
          <span>{{ tr("每分钟更新") }}</span>
          <span v-if="feed?.updatedAt">{{ tr("最近更新") }} {{ formatTime(feed.updatedAt) }}</span>
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load()">{{ tr("刷新") }}</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="feed?.unavailableSources.length" type="warning" show-icon :closable="false"
              :title="feed.unavailableSources.map(source => tr(source)).join(', ') + tr('暂未更新')" />
    <el-skeleton v-if="!feed && loading" :rows="6" animated />
    <el-card v-if="feed" shadow="never" class="news-feed">
      <el-empty v-if="!feed.items.length" :image-size="65" :description="tr(&quot;暂无符合条件的快讯&quot;)" />
      <ul v-else class="news-list" :aria-label="tr(&quot;最新快讯&quot;)">
        <li v-for="article in feed.items" :key="article.id" :ref="element => bindRow(article.id, element)"
            :data-article-id="article.id" class="news-row">
          <div class="article-meta">
            <time v-if="article.publishedAt" :datetime="article.publishedAt" class="article-time">
              <span>{{ formatTime(article.publishedAt, publicationTime) }}</span>
              <small>{{ formatTime(article.publishedAt, publicationDate) }}</small>
            </time>
            <span class="source">{{ tr(article.source) }}</span>
          </div>
          <div class="article-content">
            <h2>
              {{ visibleBriefs[article.id]?.headline || article.title }}
            </h2>
            <ul v-if="article.topic || article.assets?.length || article.keywords?.length" class="article-tags" :aria-label="tr('新闻关键词')">
              <li v-if="article.topic" class="topic">{{ tr(article.topic) }}</li>
              <li v-for="asset in article.assets" :key="asset" class="asset-tag">{{ asset }}</li>
              <li v-for="keyword in article.keywords" :key="keyword">{{ tr(keyword) }}</li>
            </ul>
            <template v-if="visibleBriefs[article.id]">
              <p class="article-summary">{{ visibleBriefs[article.id].summary }}</p>
              <div class="news-analysis">
                <strong>{{ tr('AI 解读') }}</strong>
                <p>{{ visibleBriefs[article.id].commentary }}</p>
              </div>
            </template>
            <p v-else-if="article.summary" class="article-summary source-preview">{{ article.summary }}</p>
            <template v-if="!currentBriefs[article.id]">
              <div v-if="currentFailures[article.id]" class="brief-status">
                <span>{{ tr('快讯生成失败，请检查模型服务后重试') }}</span>
                <el-button link type="primary" @click="retry(article.id)">{{ tr('重试') }}</el-button>
              </div>
              <p v-else class="brief-status" :role="runningId === article.id ? 'status' : undefined">
                {{ tr(visibleBriefs[article.id] ? '正在补充摘要' : runningId === article.id ? '正在生成快讯…' : '快讯待生成') }}
              </p>
            </template>

            <details class="source-details">
              <summary>{{ tr('来源摘要') }}</summary>
              <p v-if="visibleBriefs[article.id]">{{ article.title }}</p>
              <p>{{ article.summary || tr('来源只提供了标题。') }}</p>
              <a :href="article.url" target="_blank" rel="noopener noreferrer">{{ tr('原文来源') }}</a>
            </details>
          </div>
        </li>
      </ul>
    </el-card>

  </section>
</template>

<script setup>
import { language, t as tr } from '../preferences.js'
import { computed, onActivated, onDeactivated, reactive, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import axios from 'axios'
import { errorMessage } from '../market.js'

const feed = ref(null)
const loading = ref(false)
const error = ref('')
const editions = reactive({})
const failuresByLanguage = reactive({ 'zh-CN': {}, 'en-US': {} })
const running = ref(null)
const currentBriefs = computed(() => editions[language.value]?.briefs ?? {})
const visibleBriefs = computed(() => ({ ...editions[language.value]?.previous, ...currentBriefs.value }))
const currentFailures = computed(() => failuresByLanguage[language.value])
const runningId = computed(() => running.value?.language === language.value ? running.value.id : null)
const publicationTime = computed(() => new Intl.DateTimeFormat(language.value, {
  hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
}))
const publicationDate = computed(() => new Intl.DateTimeFormat(language.value, { dateStyle: 'short' }))
const rows = new Map()
const nearViewport = new Set()
let timer
let observer
let nextAttempt
let active = false
let loadingLanguage
let loadVersion = 0

function formatTime(value, formatter) {
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return '—'
  return formatter?.format(date) ?? date.toLocaleString(language.value, { dateStyle: 'short', timeStyle: 'short' })
}

async function load() {
  const selected = language.value
  if (loading.value && loadingLanguage === selected) return
  const version = ++loadVersion
  loadingLanguage = selected
  loading.value = true
  error.value = ''
  try {
    const { data } = await axios.get('/api/news', { params: { language: selected } })
    if (version !== loadVersion) return
    editions[selected] = { briefs: data.briefs ?? {}, previous: data.previousBriefs ?? {} }
    feed.value = data
    drain()
  } catch (failure) {
    if (version === loadVersion) error.value = errorMessage(failure, tr("无法更新新闻快讯"))
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

function bindRow(id, element) {
  const previous = rows.get(id)
  if (previous === element) return
  if (previous) observer?.unobserve(previous)
  if (element) {
    rows.set(id, element)
    observer?.observe(element)
  } else {
    rows.delete(id)
    nearViewport.delete(id)
  }
}

function onIntersections(entries) {
  for (const entry of entries) {
    const id = entry.target.dataset.articleId
    if (entry.isIntersecting) nearViewport.add(id)
    else nearViewport.delete(id)
  }
  drain()
}

function retry(id) {
  delete currentFailures.value[id]
  resumeGeneration()
}

async function drain() {
  const selected = language.value
  if (!active || document.hidden || running.value || nextAttempt || !feed.value || !editions[selected]) return
  const article = feed.value.items.find(item =>
    nearViewport.has(item.id) && !currentBriefs.value[item.id] && !currentFailures.value[item.id])
  if (!article) return
  const { id } = article
  running.value = { id, language: selected }
  try {
    const { data } = await axios.post('/api/news/' + id + '/analysis', null, { params: { language: selected } })
    if (data.articleId !== id || !data.brief?.headline) throw new Error('Invalid news brief response')
    editions[selected].briefs[id] = data.brief
  } catch (failure) {
    const busy = failure.response?.status === 429
    if (!busy) failuresByLanguage[selected][id] = true
    if (active && selected === language.value) {
      nextAttempt = setTimeout(() => {
        nextAttempt = undefined
        drain()
      }, busy ? 3_000 : 20_000)
    }
  } finally {
    running.value = null
    drain()
  }
}

function resumeGeneration() {
  clearTimeout(nextAttempt)
  nextAttempt = undefined
  drain()
}

watch(language, () => {
  if (!active) return
  resumeGeneration()
  load()
})

onActivated(() => {
  active = true
  observer = new IntersectionObserver(onIntersections, { rootMargin: '160px 0px' })
  for (const row of rows.values()) observer.observe(row)
  document.addEventListener('visibilitychange', drain)
  load()
  timer = setInterval(load, 60_000)
})
onDeactivated(() => {
  active = false
  clearInterval(timer)
  clearTimeout(nextAttempt)
  nextAttempt = undefined
  observer.disconnect()
  observer = undefined
  nearViewport.clear()
  document.removeEventListener('visibilitychange', drain)
})
</script>

<style scoped>
.news-view { display: grid; gap: 20px; min-width: 0; }
.news-view .page-heading { display: grid; grid-template-columns: minmax(0, 1fr) auto; }
.feed-status { display: flex; align-items: center; flex-wrap: wrap; gap: 4px 14px; font-size: 13px; }
.feed-status span + span { position: relative; padding-inline-start: 12px; }
.feed-status span + span::before { content: ''; position: absolute; inset-inline-start: 0; top: .7em; width: 4px; height: 4px; border-radius: 50%; background: var(--text-3); }
.news-feed { border: 0; border-top: 1px solid var(--border-strong); border-radius: 0; background: transparent; }
.news-feed :deep(.el-card__body) { padding: 0; }
.news-feed :deep(.el-empty) { padding-block: 28px; }
.news-list { margin: 0; padding: 0; list-style: none; }
.news-row { display: grid; grid-template-columns: clamp(136px, 14vw, 200px) minmax(0, 1fr); gap: 24px; min-width: 0; padding: 24px 0; }
.news-row + .news-row { border-top: 1px solid var(--border); }
.article-meta { display: flex; align-items: flex-start; flex-direction: column; gap: 8px; color: var(--text-3); }
.article-time { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 8px; font: 500 16px/1.5 var(--number-font); color: var(--text-2); }
.article-time small { font-size: 12px; color: var(--text-3); }
.source { color: var(--text-2); font-size: 13px; font-weight: 600; }
.article-tags { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 8px; margin: 8px 0 0; padding: 0; list-style: none; }
.article-tags li { color: var(--text-3); font-size: 12px; }
.article-tags .topic, .article-tags .asset-tag { border: 1px solid var(--border); border-radius: 4px; padding-inline: 8px; color: var(--text-2); }
.article-tags .topic { background: var(--panel); }
.article-tags .asset-tag { font: 500 12px/1.7 var(--number-font); }
.article-content { min-width: 0; max-width: 56rem; }
.article-content h2 { font-size: 22px; line-height: 1.4; overflow-wrap: anywhere; }
.article-summary { max-width: 78ch; margin-top: 12px; color: var(--text); line-height: 1.75; overflow-wrap: anywhere; }
.source-preview { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; }
.brief-status { display: flex; align-items: baseline; flex-wrap: wrap; gap: 8px; margin-top: 8px; color: var(--text-3); font-size: 13px; }
.news-analysis { max-width: 78ch; margin-top: 8px; color: var(--text-2); line-height: 1.75; overflow-wrap: anywhere; }
.news-analysis strong { color: var(--accent); font-size: 13px; margin-inline-end: 8px; white-space: nowrap; }
.news-analysis p { display: inline; }
.source-details { margin-top: 8px; color: var(--text-3); font-size: 13px; overflow-wrap: anywhere; }
.source-details summary { width: fit-content; padding-block: 4px; cursor: pointer; }
.source-details summary:hover, .source-details[open] summary { color: var(--accent); }
.source-details p, .source-details a { margin: 4px 0 0 16px; }
@media (max-width: 740px) {
  .news-view .page-heading { align-items: start; }
  .news-row { grid-template-columns: minmax(0, 1fr); gap: 8px; padding-block: 16px; }
  .article-meta { flex-flow: row wrap; align-items: baseline; gap: 4px 12px; }
  .article-content h2 { font-size: 19px; }
}
</style>
