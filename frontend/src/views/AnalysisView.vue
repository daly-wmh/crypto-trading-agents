<template>
  <section class="workbench">
    <header class="page-heading workbench-heading glass-panel">
      <div>
        <h1>{{ tr("加密货币智能分析") }}</h1>
        <p>{{ tr("按阶段查看多空论点、风控审查和最终研报") }}</p>
      </div>
      <div class="controls">
        <el-select v-model="pair" :aria-label="tr(&quot;选择交易对&quot;)" :disabled="running" class="pair-select">
          <el-option v-for="item in pairs" :key="item.code" :value="item.code" :label="item.name" />
        </el-select>
        <el-button type="primary" :icon="CaretRight" :loading="running" :disabled="!pair" @click="start">{{ tr("开始分析") }}</el-button>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div class="research-grid">
      <el-card shadow="never" class="chart-panel market-chart">
        <template #header>
          <div class="chart-heading">
            <h2 class="panel-heading">{{ tr("现货走势") }}</h2>
            <el-radio-group v-model="period" size="small" class="period-select" :aria-label="tr(&quot;K 线周期&quot;)">
              <el-radio-button value="15m">{{ tr("15 分钟") }}</el-radio-button>
              <el-radio-button value="1h">{{ tr("1 小时") }}</el-radio-button>
              <el-radio-button value="4h">{{ tr("4 小时") }}</el-radio-button>
              <el-radio-button value="1d">{{ tr("1 日") }}</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        <SpotQuote v-if="pair" :instrument="activePair?.name || pair" :ticker="ticker" :mode="quoteMode" />
        <KlineChart v-if="pair" :stock-code="pair" :days="120" :period="period"
                    :spot-price="quoteMode === 'stale' ? null : ticker?.price" />
        <el-empty v-else :description="tr(&quot;加载交易对后显示行情&quot;)" :image-size="64" />
      </el-card>

      <el-card shadow="never" class="progress-panel glass-panel">
        <template #header>
          <div class="chart-heading">
            <h2 class="panel-heading">{{ tr("流水线进度") }}</h2>
            <div class="controls">
              <span class="run-status" :class="{ 'is-running': running, 'is-done': result, 'is-interrupted': interrupted }">
                {{ running ? tr("分析中") : result ? tr("已完成") : interrupted ? tr("已中断") : tr("待开始") }}
              </span>
              <el-button v-if="canResume" size="small" @click="resume">{{ tr("续跑") }}</el-button>
            </div>
          </div>
        </template>
        <div class="progress-overview" :class="{ 'has-timing': startedAt }">
          <div class="progress-count">
            <strong class="progress-percent">{{ percent }}<small>%</small></strong>
            <span class="completed-count">{{ completedCount }} / {{ stages.length }} {{ tr("个阶段完成") }}</span>
          </div>
          <dl v-if="startedAt" class="progress-times">
            <div><dt>{{ tr("已用时") }}</dt><dd>{{ formatDuration(elapsed) }}</dd></div>
            <div v-if="running && !interrupted"><dt>{{ tr("预计剩余") }}</dt><dd>{{ remainingLabel }}</dd></div>
          </dl>
          <div class="progress-track" role="progressbar" :aria-label="tr(&quot;流水线阶段完成进度&quot;)"
               :aria-valuenow="percent" :class="{ 'is-interrupted': interrupted }">
            <div class="progress-fill" :style="{ transform: 'scaleX(' + (percent / 100) + ')' }" />
          </div>
        </div>
        <p v-if="activity" class="progress-activity" aria-live="polite">{{ activity }}</p>
        <ol v-if="stages.length" class="stage-plan" :class="{ 'stage-plan--stacked': stages.length > 7 }"
            :aria-label="tr(&quot;分析阶段&quot;)">
          <li v-for="(stage, index) in stages" :key="stage.id" :data-status="stage.status"
              :aria-current="stage.status === 'running' ? 'step' : undefined">
            <span class="stage-icon" aria-hidden="true">
              <el-icon v-if="stage.status === 'done'"><Check /></el-icon>
              <el-icon v-else-if="stage.status === 'running'" class="is-loading"><Loading /></el-icon>
              <el-icon v-else-if="stage.status === 'failed'"><WarningFilled /></el-icon>
              <template v-else>{{ index + 1 }}</template>
            </span>
            <div class="stage-text">
              <strong>{{ tr(stage.name) }}</strong>
              <small>{{ tr(stageLabels[stage.status]) }}<template v-if="stage.duration"> · {{ formatDuration(stage.duration) }}</template></small>
            </div>
          </li>
        </ol>
        <p v-else class="stage-plan-empty">{{ tr("暂无可显示的分析阶段") }}</p>
        <router-link v-if="!running" class="pipeline-link" to="/pipeline">
          {{ tr("配置流水线") }} <el-icon aria-hidden="true"><ArrowRight /></el-icon>
        </router-link>
      </el-card>
    </div>

    <section v-if="reportGroups.length" class="report-panel" aria-labelledby="analysis-results">
      <header class="chart-heading">
        <h2 id="analysis-results" class="panel-heading">{{ result?.stockName || activePair?.name }} {{ tr("分析结果") }}</h2>
        <div v-if="result?.paperSignal" class="controls">
          <el-tag :type="signalType" effect="light">{{ tr("模拟") }}{{ signalLabel }}<template v-if="result.paperSignal.percent"> · {{ result.paperSignal.percent }}%</template></el-tag>
          <el-button v-if="canExecute" :loading="executing" type="primary" @click="executeDecision">
            {{ tr("确认模拟") }}{{ signalLabel }}
          </el-button>
        </div>
      </header>
      <el-card v-for="group in reportGroups" :key="group.id" shadow="never" class="report-stage">
        <template #header><h3 class="panel-heading">{{ tr(group.name) }}</h3></template>
        <article v-for="node in group.reports" :key="node.nodeId + node.nodeName" class="node-report">
          <h4 v-if="node.nodeName !== group.name" class="node-title">{{ tr(node.nodeName) }}</h4>
          <div class="analysis-markdown" v-html="safeMarkdown(node.content)" />
        </article>
      </el-card>
    </section>
  </section>
</template>

<script setup>
import { t as tr } from '../preferences.js'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, CaretRight, Check, Loading, WarningFilled } from '@element-plus/icons-vue'
import axios from 'axios'
import KlineChart from '../components/KlineChart.vue'
import SpotQuote from '../components/SpotQuote.vue'
import { errorMessage, getPairs } from '../market.js'
import { useSpotQuote } from '../useSpotQuote.js'
import { safeMarkdown } from '../markdown.js'

const route = useRoute()
const pairs = ref([])
const pair = ref('')
const { ticker, mode: quoteMode } = useSpotQuote(pair)
const period = ref('1h')
const configuredStages = ref([])
const runStages = ref(null)
const stageProgress = ref({})
const nodeReports = ref([])
const startedAt = ref(0)
const clock = ref(0)
const interrupted = ref(false)
const reportId = ref(null)
const result = ref(null)
const error = ref('')
const running = ref(false)
const executing = ref(false)
const executed = ref(false)

const activePair = computed(() => pairs.value.find(item => item.code === pair.value))
const canResume = computed(() => !running.value && error.value && reportId.value && !result.value)
const canExecute = computed(() => !executed.value && ['BUY', 'SELL'].includes(result.value?.paperSignal?.action))
const signalLabel = computed(() => ({ BUY: tr("买入"), SELL: tr("卖出"), HOLD: tr("观望") }[result.value?.paperSignal?.action]))
const signalType = computed(() => ({ BUY: 'success', SELL: 'warning', HOLD: 'info' }[result.value?.paperSignal?.action]))
const stageLabels = { waiting: '待执行', running: '执行中', done: '已完成', failed: '未完成' }
const stages = computed(() => (runStages.value ?? configuredStages.value).map(stage => ({
  ...stage, status: 'waiting', ...stageProgress.value[stage.id]
})))
const completedCount = computed(() => stages.value.filter(stage => stage.status === 'done').length)
const percent = computed(() => stages.value.length ? Math.floor(100 * completedCount.value / stages.value.length) : 0)
const activeStage = computed(() => stages.value.find(stage => stage.status === 'running'))
const elapsed = computed(() => startedAt.value ? clock.value - startedAt.value : 0)
const activity = computed(() => {
  if (!running.value || interrupted.value) return ''
  if (activeStage.value) {
    return activeStage.value.activeNodes.length
      ? activeStage.value.activeNodes.map(name => tr(name)).join(' · ')
      : tr(activeStage.value.name)
  }
  return percent.value === 100 ? tr("正在整理研报") : tr("正在准备分析数据")
})
const remainingLabel = computed(() => {
  const completed = stages.value.filter(stage => stage.duration)
  const durationPerUnit = (75_000 + completed.reduce((total, stage) => total + stage.duration, 0)) /
    (1 + completed.reduce((total, stage) => total + stage.workUnits, 0))
  const pendingUnits = stages.value.filter(stage => stage.status === 'waiting')
    .reduce((total, stage) => total + stage.workUnits, 0)
  const current = activeStage.value
  const remainingForStage = current ? current.workUnits * durationPerUnit - (clock.value - current.started) : 0
  if (current && remainingForStage <= 0) return tr("当前阶段耗时超出估算")
  const remaining = remainingForStage + pendingUnits * durationPerUnit
  return remaining > 0 ? tr("约 ") + formatDuration(remaining, true) : tr("估算中")
})
const reportGroups = computed(() => {
  const reports = [...nodeReports.value]
  const archived = [
    ['market_analyst', tr("趋势与技术面"), result.value?.marketReport],
    ['social_analyst', tr("成交与波动"), result.value?.socialReport],
    ['bull_researcher', tr("多空辩论"), result.value?.debate, 'bear_researcher'],
    ['investment_decision', tr("研究裁定"), result.value?.researchDecision],
    ['risk_control', tr("模拟风控"), result.value?.tradeDecision],
    ['cio', tr("最终研报"), result.value?.finalDecision]
  ]
  for (const [nodeId, nodeName, content, alternate] of archived) {
    if (!content || reports.some(report => report.content === content || report.nodeId === nodeId || report.nodeId === alternate)) continue
    const stageId = stages.value.find(stage => stage.nodeIds.includes(nodeId))?.id ?? 'archived'
    reports.push({ stageId, nodeId, nodeName, content })
  }
  const groups = stages.value.map(stage => ({ id: stage.id, name: stage.name, reports: [] }))
  const archive = { id: 'archived', name: tr("已归档分析"), reports: [] }
  for (const report of reports) {
    (groups.find(group => group.id === report.stageId) || archive).reports.push(report)
  }
  return [...groups, archive].filter(group => group.reports.length)
})

function formatDuration(ms, approximate = false) {
  const seconds = approximate ? Math.ceil(ms / 15_000) * 15 : Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  return minutes ? minutes + tr(" 分 ") + String(seconds % 60).padStart(2, '0') + tr(" 秒") : seconds + tr(" 秒")
}

onMounted(async () => {
  try {
    pairs.value = await getPairs()
    pair.value = pairs.value[0]?.code || ''
    const { data } = await axios.get('/api/pipeline')
    const stages = new Map()
    for (const node of data.filter(node => node.enabled)) {
      if (!stages.has(node.stageOrder)) stages.set(node.stageOrder, [])
      stages.get(node.stageOrder).push(node)
    }
    configuredStages.value = [...stages].map(([order, nodes]) => ({
      id: 'stage_' + order,
      name: nodes.map(node => node.displayName).join(' · '),
      workUnits: nodes.length * (nodes.length > 1 && nodes[0].stageType === 'DEBATE' ? nodes[0].debateRounds || 2 : 1),
      nodeIds: nodes.map(node => node.nodeKey)
    }))
  } catch (failure) {
    error.value = errorMessage(failure, tr("无法连接后端，请确认服务已启动"))
  } finally { syncRoute() }
})

watch(() => [route.name, route.query.stock, route.query.resume], syncRoute)

function syncRoute() {
  if (route.name !== 'Analysis' || running.value || !pairs.value.length) return
  const code = String(route.query.stock || '').toUpperCase()
  if (pairs.value.some(item => item.code === code)) pair.value = code

  const id = String(route.query.resume || '')
  if (!/^\d+$/.test(id)) return
  reportId.value = Number(id)
  result.value = null
  executed.value = false
  resume()
}

function start() {
  result.value = null
  reportId.value = null
  executed.value = false
  return run('/api/analysis/stream', { stockCode: pair.value })
}

function resume() {
  return run(`/api/analysis/resume/${reportId.value}/stream`)
}

async function run(url, body) {
  running.value = true
  interrupted.value = false
  error.value = ''
  runStages.value = null
  stageProgress.value = {}
  nodeReports.value = []
  clock.value = startedAt.value = Date.now()
  const timer = setInterval(() => { clock.value = Date.now() }, 1000)
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined
    })
    if (!response.ok) {
      const failure = await response.json().catch(() => ({}))
      throw new Error(failure.message || tr("无法启动分析"))
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      const blocks = buffer.split(/\r?\n\r?\n/)
      buffer = blocks.pop()
      for (const block of blocks) processEvent(block)
      if (done) break
    }
    if (!result.value && !error.value) error.value = tr("连接已结束。请查看历史报告或续跑。")
  } catch (failure) {
    error.value ||= failure.message || tr("分析中断")
  } finally {
    clearInterval(timer)
    clock.value = Date.now()
    if (!result.value) {
      interrupted.value = true
      if (activeStage.value) stageProgress.value[activeStage.value.id].status = 'failed'
    }
    running.value = false
  }
}

function processEvent(block) {
  let type = ''
  const lines = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) type = line.slice(6).trim()
    if (line.startsWith('data:')) lines.push(line.slice(5).trimStart())
  }
  if (!lines.length) return
  const data = JSON.parse(lines.join('\n'))
  if (type === 'REPORT_CREATED') reportId.value = Number(data.data)
  if (type === 'RESULT') {
    result.value = data
    for (const stage of stages.value) {
      if (stage.status !== 'done') stageProgress.value[stage.id] = { status: 'done' }
    }
  }
  if (type === 'GRAPH_ERROR') error.value = tr(data.data || '分析失败')
  if (type === 'PIPELINE_PLAN') {
    const plan = JSON.parse(data.data)
    runStages.value = plan.stages
    stageProgress.value = Object.fromEntries(plan.stages.slice(0, plan.completedStages)
      .map(stage => [stage.id, { status: 'done' }]))
    nodeReports.value = plan.nodeReports
  }
  const stageId = data.stageId || activeStage.value?.id ||
    stages.value.find(stage => stage.status === 'waiting')?.id
  if (!stageId) return
  if (type === 'PHASE_START') {
    clock.value = Date.now()
    stageProgress.value[stageId] = { status: 'running', started: clock.value, activeNodes: [] }
  }
  const stage = stageProgress.value[stageId]
  if (!stage) return
  if (type === 'NODE_START') stage.activeNodes.push(data.nodeName)
  if (type === 'NODE_COMPLETE') {
    stage.activeNodes = stage.activeNodes.filter(name => name !== data.nodeName)
    if (data.data && data.data !== '[已跳过]') {
      nodeReports.value.push({ stageId, nodeId: data.nodeId, nodeName: data.nodeName, content: data.data })
    }
  }
  if (type === 'PHASE_COMPLETE') {
    stage.status = 'done'
    stage.duration = Date.now() - stage.started
  }
  if (type === 'NODE_ERROR') {
    stage.status = 'failed'
    interrupted.value = true
    error.value = tr(data.data || '模型节点执行失败')
  }
}

async function executeDecision() {
  const signal = result.value.paperSignal
  const budget = signal.action === 'BUY' ? tr("可用 USDT") : tr("该币持仓")
  try {
    await ElMessageBox.confirm(
      tr("按当前公开报价，使用 {0} 的 {1}% 模拟{2} {3}？", { 0: budget, 1: signal.percent, 2: signalLabel.value, 3: result.value.stockName }),
      tr("确认模拟成交"),
      { confirmButtonText: tr("确认"), cancelButtonText: tr("取消") }
    )
  } catch {
    return
  }

  executing.value = true
  try {
    await axios.post(`/api/trading/execute-decision/${result.value.reportId}`)
    executed.value = true
    ElMessage.success(tr("模拟成交已记录"))
  } catch (failure) {
    ElMessage.error(errorMessage(failure, tr("模拟成交失败")))
  } finally {
    executing.value = false
  }
}
</script>

<style scoped>
.workbench { display: grid; gap: 20px; min-width: 0; }
.chart-heading, .controls { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.workbench-heading .controls { gap: 8px; }
.workbench-heading .el-button { min-height: 40px; padding-inline: 16px; white-space: nowrap; }
.pair-select { width: 136px; }
.pair-select :deep(.el-select__wrapper) { min-height: 40px; }
.research-grid { display: grid; grid-template-columns: minmax(0, 1fr); gap: 24px; }
.chart-heading { flex-wrap: wrap; }
.run-status { font-size: 13px; font-weight: 500; color: var(--text-3); }
.run-status.is-running { color: var(--accent); }
.run-status.is-done { color: var(--positive); }
.run-status.is-interrupted { color: var(--negative); }
.progress-overview { display: grid; grid-template-columns: auto minmax(64px, 1fr); align-items: center; gap: 12px 20px; }
.progress-overview.has-timing { grid-template-columns: auto minmax(64px, 1fr) auto; }
.progress-count { display: flex; align-items: baseline; flex-wrap: wrap; gap: 8px; }
.progress-percent { font: 500 28px/1 var(--number-font); }
.progress-percent small { font-size: .6em; }
.completed-count, .progress-times dt { color: var(--text-3); font-size: 12px; }
.progress-times { grid-column: 3; grid-row: 1; display: flex; flex-wrap: wrap; gap: 8px 20px; margin: 0; }
.progress-times dd { margin: 0; font-weight: 600; font-variant-numeric: tabular-nums; }
.progress-track { grid-column: 2; grid-row: 1; height: 4px; overflow: hidden; border-radius: 4px; background: var(--panel); }
.progress-fill { height: 100%; background: var(--accent); transform-origin: left; transition: transform .24s var(--ease-out); }
.progress-track.is-interrupted .progress-fill { background: var(--negative); }
.progress-activity { margin-top: 12px; color: var(--accent); font-size: 13px; font-weight: 600; }
.stage-plan {
  --step-size: 28px;
  display: grid;
  margin: 20px 0 0;
  padding: 0;
  list-style: none;
}
.stage-plan li {
  position: relative;
  display: grid;
  grid-template-columns: var(--step-size) minmax(0, 1fr);
  align-items: start;
  gap: 12px;
  min-width: 0;
  padding-bottom: 12px;
  color: var(--text-2);
}
.stage-plan li:last-child { padding-bottom: 0; }
.stage-plan li:not(:last-child)::before {
  content: '';
  position: absolute;
  top: calc(var(--step-size) / 2);
  bottom: calc(var(--step-size) / -2);
  inset-inline-start: calc(var(--step-size) / 2);
  width: 1px;
  background: var(--border);
}
.stage-plan li[data-status="done"]::before {
  background: var(--accent-border);
}
.stage-icon {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: var(--step-size);
  height: var(--step-size);
  border: 1px solid var(--border);
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--text-3);
  font-size: 12px;
  font-family: var(--number-font);
  font-weight: 500;
}
.stage-text { min-width: 0; overflow-wrap: anywhere; }
.stage-text strong, .stage-text small { display: block; }
.stage-text strong { font-size: 13px; line-height: 1.45; }
.stage-text small { margin-top: 3px; color: var(--text-3); font-size: 12px; }
.stage-plan [data-status="running"] strong { color: var(--accent); }
.stage-plan [data-status="running"] .stage-icon { border-color: var(--accent); background: var(--accent); color: var(--accent-ink); font-size: 16px; }
.stage-plan [data-status="done"] .stage-icon { background: var(--positive-bg); color: var(--positive); font-size: 16px; }
.stage-plan [data-status="failed"] strong { color: var(--negative); }
.stage-plan [data-status="failed"] .stage-icon { background: var(--el-color-error-light-9); color: var(--negative); font-size: 16px; }
.stage-plan-empty { margin-top: 12px; color: var(--text-3); }
.pipeline-link {
  display: flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  margin-top: 16px;
  min-height: 28px;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
}
.pipeline-link:hover { text-decoration: underline; }
.report-panel { display: grid; gap: 14px; min-width: 0; }
.report-stage :deep(.el-card__header) { background: var(--surface-2); }
.node-report + .node-report { margin-top: 18px; padding-top: 18px; border-top: 1px solid var(--border); }
.node-title { margin-bottom: 9px; color: var(--text); font-size: 15px; }
.analysis-markdown { max-width: 76ch; line-height: 1.75; overflow-wrap: anywhere; }
.analysis-markdown :deep(p), .analysis-markdown :deep(ul), .analysis-markdown :deep(ol) { margin: 0 0 10px; }
.analysis-markdown :deep(ul), .analysis-markdown :deep(ol) { padding-left: 1.5rem; }
.analysis-markdown :deep(h1), .analysis-markdown :deep(h2), .analysis-markdown :deep(h3), .analysis-markdown :deep(h4) { margin: 14px 0 6px; color: var(--accent); font-size: 1.08em; }
.analysis-markdown :deep(strong) { color: var(--accent-hover); font-weight: 600; }
.analysis-markdown :deep(blockquote) { margin: 10px 0; padding: 10px 12px; border-radius: 6px; background: var(--accent-soft); }
.analysis-markdown :deep(pre) { max-width: 100%; white-space: pre-wrap; }
.analysis-markdown :deep(table) { display: block; max-width: 100%; overflow-x: auto; }
.analysis-markdown :deep(:first-child) { margin-top: 0; }
.analysis-markdown :deep(:last-child) { margin-bottom: 0; }
@media (prefers-reduced-motion: reduce) {
  .progress-fill { transition: none; }
  .stage-icon .is-loading { animation: none; }
}
@media (max-width: 740px) {
  .workbench, .research-grid { gap: 16px; }
  .progress-overview, .progress-overview.has-timing { display: flex; align-items: center; flex-wrap: wrap; justify-content: space-between; }
  .progress-track { flex: 0 0 100%; }
}
@media (min-width: 741px) {
  .stage-plan--stacked { max-width: 44rem; }
  .stage-plan:not(.stage-plan--stacked) {
    grid-auto-flow: column;
    grid-auto-columns: minmax(0, 1fr);
  }
  .stage-plan:not(.stage-plan--stacked) li {
    grid-template-columns: minmax(0, 1fr);
    align-content: start;
    justify-items: center;
    gap: 8px;
    padding: 0 8px;
    text-align: center;
  }
  .stage-plan:not(.stage-plan--stacked) li:not(:last-child)::before {
    bottom: auto;
    inset-inline-start: 50%;
    width: 100%;
    height: 1px;
  }
}
</style>
