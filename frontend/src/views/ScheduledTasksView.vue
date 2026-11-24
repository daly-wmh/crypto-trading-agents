<template>
  <section class="scheduled-tasks">
    <header class="page-heading">
      <h1>{{ tr("定时任务") }}</h1>
      <el-button :icon="Refresh" :loading="loading" :disabled="saving || starting" @click="load()">
        {{ tr("刷新状态") }}
      </el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-skeleton v-if="!task && loading" :rows="5" animated />
    <template v-if="task">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <h2 class="panel-heading">{{ tr("自动持仓复盘") }}</h2>
            <el-tag :type="scheduleStatus.type" effect="light">{{ scheduleStatus.label }}</el-tag>
          </div>
        </template>

        <p class="description">{{ tr("评估有研报的模拟持仓，将有价值的反思写入经验库。") }}</p>
        <el-alert v-if="!task.available" :title="tr(task.unavailableReason)" type="warning" show-icon :closable="false" />
        <div class="schedule-body">
          <dl class="next-run">
            <dt>{{ tr("下次运行（本地时间）") }}</dt>
            <dd v-if="task.nextRunAt"><time :datetime="task.nextRunAt">{{ formatTime(task.nextRunAt) }}</time></dd>
            <dd v-else>{{ task.available && !task.enabled ? tr("已暂停") : '—' }}</dd>
          </dl>
          <el-form label-position="top" class="schedule-form" @submit.prevent="save">
            <el-form-item :label="tr(&quot;自动执行&quot;)" for="reflection-enabled">
              <el-switch id="reflection-enabled" v-model="draft.enabled" :aria-label="tr(&quot;自动执行&quot;)" :disabled="saving" />
            </el-form-item>
            <el-form-item :label="tr(&quot;Cron 表达式（UTC）&quot;)" for="reflection-cron">
              <el-input id="reflection-cron" v-model.trim="draft.cron" maxlength="120"
                        aria-describedby="cron-help" :disabled="saving" />
              <p id="cron-help" class="field-help">{{ tr("字段顺序为秒、分、时、日、月、星期。") }}<code>0 0 1 * * *</code> {{ tr("表示每天 UTC 01:00。") }}</p>
            </el-form-item>
            <div class="actions">
              <el-button :loading="starting" :disabled="!task.available || task.running || saving || loading" @click="run">
                {{ tr("立即复盘") }}
              </el-button>
              <el-button type="primary" native-type="submit" :loading="saving"
                         :disabled="!dirty || !draft.cron || starting || loading">
                {{ tr("保存计划") }}
              </el-button>
            </div>
          </el-form>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header><h2 class="panel-heading">{{ tr("最近 30 次执行") }}</h2></template>
        <el-empty v-if="!task.runs.length" :description="tr(&quot;暂无执行记录&quot;)" :image-size="65" />
        <ul v-else class="run-list" :aria-label="tr(&quot;执行记录&quot;)">
          <li v-for="entry in task.runs" :key="entry.id" class="run-row">
            <div class="run-result">
              <el-tag :type="runStatuses[entry.status]?.type ?? 'info'" size="small" effect="light">
                {{ tr(runStatuses[entry.status]?.label ?? entry.status) }}
              </el-tag>
              <span>{{ entry.trigger === 'MANUAL' ? tr("手动触发") : tr("定时触发") }}</span>
            </div>
            <p class="run-summary">{{ tr(entry.summary) }}</p>
            <dl class="run-times">
              <div><dt>{{ tr("开始") }}</dt><dd><time :datetime="entry.startedAt">{{ formatTime(entry.startedAt) }}</time></dd></div>
              <div v-if="entry.finishedAt">
                <dt>{{ tr("结束") }}</dt><dd><time :datetime="entry.finishedAt">{{ formatTime(entry.finishedAt) }}</time></dd>
              </div>
            </dl>
          </li>
        </ul>
      </el-card>
    </template>
  </section>
</template>

<script setup>
import { language, t as tr } from '../preferences.js'
import { computed, onActivated, onDeactivated, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { errorMessage } from '../market.js'

const endpoint = '/api/tasks/portfolio-reflection'
const task = ref(null)
const draft = reactive({ enabled: false, cron: '' })
const loading = ref(false)
const saving = ref(false)
const starting = ref(false)
const error = ref('')
let pollTimer
let requestVersion = 0

const runStatuses = {
  RUNNING: { label: '运行中', type: 'primary' },
  SUCCESS: { label: '已完成', type: 'success' },
  SKIPPED: { label: '已跳过', type: 'info' },
  PARTIAL_FAILURE: { label: '部分失败', type: 'warning' },
  FAILED: { label: '失败', type: 'danger' },
  INTERRUPTED: { label: '已中断', type: 'warning' }
}
const scheduleStatus = computed(() => {
  if (!task.value?.available) return { label: tr("暂不可用"), type: 'warning' }
  if (task.value.running) return { ...runStatuses.RUNNING, label: tr(runStatuses.RUNNING.label) }
  return task.value.enabled ? { label: tr("已启用"), type: 'success' } : { label: tr("已暂停"), type: 'info' }
})
const dirty = computed(() => task.value &&
  (draft.enabled !== task.value.enabled || draft.cron !== task.value.cron))

function formatTime(value) {
  return new Date(value).toLocaleString(language.value, { dateStyle: 'short', timeStyle: 'medium', hourCycle: 'h23' })
}

function showTask(data, resetDraft = false) {
  if (resetDraft || !dirty.value) {
    draft.enabled = data.enabled
    draft.cron = data.cron
  }
  task.value = data
}

async function load(quiet = false) {
  if (loading.value || saving.value || starting.value) return
  const version = ++requestVersion
  if (!quiet) {
    loading.value = true
    error.value = ''
  }
  try {
    const { data } = await axios.get(endpoint)
    if (version === requestVersion) showTask(data)
  } catch (failure) {
    if (version === requestVersion && !quiet) error.value = errorMessage(failure, tr("无法加载定时任务"))
  } finally {
    if (!quiet) loading.value = false
  }
}

async function save() {
  if (!dirty.value || !draft.cron || loading.value || starting.value || saving.value) return
  requestVersion++
  saving.value = true
  error.value = ''
  try {
    const { data } = await axios.put(endpoint, { enabled: draft.enabled, cron: draft.cron })
    showTask(data, true)
    ElMessage.success(tr("运行计划已保存"))
  } catch (failure) {
    error.value = errorMessage(failure, tr("无法保存运行计划"))
  } finally {
    saving.value = false
  }
}

async function run() {
  if (!task.value?.available || task.value.running || loading.value || saving.value || starting.value) return
  requestVersion++
  starting.value = true
  error.value = ''
  try {
    const { data } = await axios.post(endpoint + '/run')
    showTask(data)
    ElMessage.success(tr("持仓复盘已启动"))
  } catch (failure) {
    error.value = errorMessage(failure, tr("无法启动持仓复盘"))
  } finally {
    starting.value = false
  }
}

onActivated(() => {
  load()
  pollTimer = setInterval(() => load(true), 5000)
})
onDeactivated(() => clearInterval(pollTimer))
</script>

<style scoped>
.scheduled-tasks { display: grid; gap: 20px; min-width: 0; }
.card-header, .actions { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; }
.description { color: var(--text-3); }
.description + .el-alert { margin-top: 14px; }
.schedule-body { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr); gap: 24px; margin-top: 20px; }
.next-run { margin: 0; padding: 16px; background: var(--surface-2); border: 1px solid var(--border); border-radius: 10px; align-self: start; }
.next-run dt, .field-help, .run-times, .run-result > span:last-child { color: var(--text-3); font-size: 12px; }
.next-run dd { margin: 4px 0 0; font: 500 18px/1.5 var(--number-font); }
.field-help { margin-top: 6px; line-height: 1.5; }
.field-help code { white-space: nowrap; }
.actions { justify-content: flex-end; }
.actions .el-button + .el-button { margin-left: 0; }
.run-list { margin: 0; padding: 0; list-style: none; }
.run-row { display: grid; grid-template-columns: minmax(112px, auto) minmax(0, 1fr) auto; align-items: start; gap: 12px 20px; padding: 14px 0; }
.run-row + .run-row { border-top: 1px solid var(--border); }
.run-row:first-child { padding-top: 0; }
.run-row:last-child { padding-bottom: 0; }
.run-result { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; }
.run-summary { overflow-wrap: anywhere; }
.run-times { margin: 0; font-variant-numeric: tabular-nums; }
.run-times > div { display: flex; gap: 8px; }
.run-times dd { margin: 0; }
@media (max-width: 660px) {
  .scheduled-tasks > .page-heading { flex-wrap: wrap; }
  .schedule-body, .run-row { grid-template-columns: minmax(0, 1fr); gap: 10px; }
}
</style>
