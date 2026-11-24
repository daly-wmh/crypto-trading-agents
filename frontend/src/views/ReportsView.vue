<template>
  <div class="reports-view">
    <header class="page-heading"><h1>{{ tr("历史报告") }}</h1></header>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <h2 class="panel-heading">{{ tr("分析记录") }}</h2>
          <el-button size="default" @click="loadReports" :loading="loading">{{ tr("刷新") }}</el-button>
        </div>
      </template>

      <el-table :data="reports" stripe v-loading="loading" :empty-text="tr(&quot;暂无分析报告，完成一次智能分析后将自动保存&quot;)">
        <el-table-column prop="stockCode" :label="tr(&quot;代码&quot;)" min-width="105" />
        <el-table-column prop="stockName" :label="tr(&quot;名称&quot;)" min-width="105" />
        <el-table-column :label="tr(&quot;状态&quot;)" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'COMPLETED'" type="success" size="default">{{ tr("完成") }}</el-tag>
            <el-tag v-else-if="['FAILED', 'INTERRUPTED'].includes(row.status)" type="danger" size="default">{{ tr("失败") }}</el-tag>
            <el-tag v-else-if="row.status === 'RUNNING'" type="warning" size="default">{{ tr("运行中") }}</el-tag>
            <el-tag v-else size="default">{{ row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="tr(&quot;最终决策&quot;)" min-width="200">
          <template #default="{ row }">
            <div class="decision-preview">{{ extractDecision(row.finalDecision) }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="tr(&quot;耗时&quot;)" width="90">
          <template #default="{ row }">{{ row.durationMs ? (row.durationMs / 1000).toFixed(1) + 's' : '-' }}</template>
        </el-table-column>
        <el-table-column :label="tr(&quot;时间&quot;)" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="tr(&quot;操作&quot;)" width="170">
          <template #default="{ row }">
            <el-button size="default" type="primary" text @click="viewDetail(row)">{{ tr("详情") }}</el-button>
            <el-button v-if="['FAILED', 'INTERRUPTED'].includes(row.status)" size="default" type="warning" text @click="resumeAnalysis(row)">{{ tr("继续") }}</el-button>
            <el-button size="default" type="danger" text @click="deleteReport(row)">{{ tr("删除") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 报告详情弹窗 -->
    <el-dialog v-model="showDetail" :title="tr(&quot;分析报告详情&quot;)" width="80%" top="5vh">
      <div v-if="currentReport" class="report-detail">
        <el-descriptions :column="3" border size="default" class="report-meta">
          <el-descriptions-item :label="tr(&quot;交易对代码&quot;)">{{ currentReport.stockCode }}</el-descriptions-item>
          <el-descriptions-item :label="tr(&quot;交易对名称&quot;)">{{ currentReport.stockName }}</el-descriptions-item>
          <el-descriptions-item :label="tr(&quot;分析时间&quot;)">{{ formatTime(currentReport.createdAt) }}</el-descriptions-item>
          <el-descriptions-item :label="tr(&quot;总耗时&quot;)">{{ currentReport.durationMs ? (currentReport.durationMs / 1000).toFixed(1) + 's' : '-' }}</el-descriptions-item>
          <el-descriptions-item :label="tr(&quot;Token消耗&quot;)">{{ currentReport.totalTokens }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs class="report-tabs">
          <el-tab-pane :label="tr(&quot;最终决策&quot;)">
            <div class="markdown-body" v-html="renderMd(currentReport.finalDecision)"></div>
          </el-tab-pane>
          <el-tab-pane :label="tr(&quot;技术面&quot;)">
            <div class="markdown-body" v-html="renderMd(currentReport.marketReport)"></div>
          </el-tab-pane>
          <el-tab-pane :label="tr(&quot;消息面&quot;)">
            <div class="markdown-body" v-html="renderMd(currentReport.newsReport)"></div>
          </el-tab-pane>
          <el-tab-pane :label="tr(&quot;情绪面&quot;)">
            <div class="markdown-body" v-html="renderMd(currentReport.socialReport)"></div>
          </el-tab-pane>
          <el-tab-pane :label="tr(&quot;基本面&quot;)">
            <div class="markdown-body" v-html="renderMd(currentReport.fundamentalReport)"></div>
          </el-tab-pane>
          <el-tab-pane :label="tr(&quot;投资决策&quot;)">
            <div class="markdown-body" v-html="renderMd(currentReport.researchDecision)"></div>
          </el-tab-pane>
          <el-tab-pane :label="tr(&quot;交易计划&quot;)">
            <div class="markdown-body" v-html="renderMd(currentReport.tradeDecision)"></div>
          </el-tab-pane>
          <el-tab-pane v-if="currentReport.bullArguments" :label="tr(&quot;多空辩论(旧)&quot;)">
            <div class="markdown-body" v-html="renderMd(currentReport.bullArguments)"></div>
          </el-tab-pane>
          <el-tab-pane v-if="currentReport.riskDebate" :label="tr(&quot;风控辩论(旧)&quot;)">
            <div class="markdown-body" v-html="renderMd(currentReport.riskDebate)"></div>
          </el-tab-pane>
          <el-tab-pane :label="tr(&quot;经验记忆&quot;)">
            <div class="markdown-body" v-html="renderMd(currentReport.recalledMemories)"></div>
          </el-tab-pane>
        </el-tabs>

        <!-- 反思评估 -->
        <div class="reflect-section">
          <el-button type="warning" plain size="default" @click="showReflect = true">
            {{ tr("反思评估这次决策") }}
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 反思弹窗 -->
    <el-dialog v-model="showReflect" :title="tr(&quot;反思评估&quot;)" width="500px">
      <el-form label-position="top">
        <el-form-item :label="tr(&quot;决策结果&quot;)">
          <el-radio-group v-model="reflectForm.outcome">
            <el-radio value="正确">{{ tr("正确") }}</el-radio>
            <el-radio value="错误">{{ tr("错误") }}</el-radio>
            <el-radio value="部分正确">{{ tr("部分正确") }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="tr(&quot;经验教训&quot;)">
          <el-input v-model="reflectForm.lesson" type="textarea" :rows="4"
            :placeholder="tr(&quot;写下你的反思：为什么对了/错了？下次遇到类似情况应该注意什么？&quot;)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReflect = false">{{ tr("取消") }}</el-button>
        <el-button type="primary" @click="submitReflect" :loading="reflectLoading">{{ tr("提交反思") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { language, t as tr } from '../preferences.js'
import { ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { safeMarkdown } from '../markdown.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

const router = useRouter()

const reports = ref([])
const loading = ref(false)
const showDetail = ref(false)
const currentReport = ref(null)

async function loadReports() {
  loading.value = true
  try {
    const { data } = await axios.get('/api/reports')
    reports.value = data
  } catch (e) {
    console.error('Failed to load reports', e)
    reports.value = []
  } finally {
    loading.value = false
  }
}

function viewDetail(row) {
  currentReport.value = row
  showDetail.value = true
}

function resumeAnalysis(row) {
  router.push({ path: '/', query: { resume: row.id, stock: row.stockCode } })
}

async function deleteReport(row) {
  try {
    await ElMessageBox.confirm(
      tr("确定删除 {0} 的分析报告吗？删除后不可恢复。", { 0: row.stockName || row.stockCode }),
      tr("删除确认"),
      { confirmButtonText: tr("删除"), cancelButtonText: tr("取消"), type: 'warning' }
    )
    await axios.delete(`/api/reports/${row.id}`)
    ElMessage.success(tr("已删除"))
    loadReports()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(tr("删除失败: ") + (e.response?.data?.message || e.message))
    }
  }
}

function extractDecision(text) {
  if (!text) return tr("暂无")
  const plain = text.replace(/[#*\-\n]/g, ' ').trim()
  return plain.substring(0, 80) + (plain.length > 80 ? '...' : '')
}

function renderMd(text) {
  if (!text) return '<p class="empty-report">' + tr('暂无数据') + '</p>'
  return safeMarkdown(text)
}

function formatTime(ts) {
  if (!ts) return ''
  return new Date(ts).toLocaleString(language.value)
}

// ===== 反思评估 =====
const showReflect = ref(false)
const reflectLoading = ref(false)
const reflectForm = ref({ outcome: '正确', lesson: '' })

async function submitReflect() {
  if (!currentReport.value) return
  reflectLoading.value = true
  try {
    await axios.post(`/api/reports/${currentReport.value.id}/reflect`, {
      outcome: reflectForm.value.outcome,
      lesson: reflectForm.value.lesson
    })
    ElMessage.success(tr("反思已保存，下次分析将召回此经验"))
    showReflect.value = false
    reflectForm.value = { outcome: '正确', lesson: '' }
  } catch (e) {
    ElMessage.error(tr("提交失败: ") + (e.response?.data?.message || e.message))
  } finally {
    reflectLoading.value = false
  }
}

onMounted(loadReports)
onActivated(loadReports)
</script>

<style scoped>
.markdown-body :deep(.empty-report) { color: var(--text-3); }
.reports-view { display: grid; gap: 20px; min-width: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
.decision-preview { font-size: 13px; color: var(--text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.report-meta { margin-bottom: 16px; }
.report-tabs { margin-top: 12px; }
.markdown-body { font-size: 14px; line-height: 1.7; max-height: 500px; overflow-y: auto; padding: 8px; color: var(--text-2); }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) { margin: 12px 0 8px; color: var(--text); }
.markdown-body :deep(table) { border-collapse: collapse; width: 100%; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid var(--border); padding: 6px 10px; font-size: 13px; }
.markdown-body :deep(th) { background: var(--surface-2); font-weight: 600; color: var(--text-3); }
.reflect-section { margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--border); }
</style>
