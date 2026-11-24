<template>
  <div class="memory-view">
    <header class="page-heading">
      <h1>{{ tr("历史经验记忆") }}</h1>
    </header>
    <div class="memory-toolbar">
      <div class="header-actions">
        <el-input v-model="searchQuery" :placeholder="tr(&quot;语义搜索经验&quot;)" :aria-label="tr(&quot;语义搜索经验&quot;)" size="default" class="memory-search"
                  @keyup.enter="doSearch" clearable />
        <el-button type="primary" size="default" @click="doSearch">{{ tr("搜索") }}</el-button>
        <el-button size="default" @click="openAdd">{{ tr("手动添加") }}</el-button>
        <el-button size="default" @click="loadMemories">{{ tr("刷新") }}</el-button>
      </div>
    </div>

    <!-- 统计 -->
    <div class="stats-row" v-if="stats">
      <div class="stat-chip">{{ tr("总计") }} {{ stats.total }} {{ tr("条") }}</div>
      <div class="stat-chip">{{ tr("情境记忆") }} {{ stats.situations }}</div>
      <div class="stat-chip">{{ tr("反思教训") }} {{ stats.reflections }}</div>
    </div>

    <!-- 筛选 -->
    <div class="filter-row">
      <el-select v-model="filterStock" :placeholder="tr(&quot;按交易对筛选&quot;)" clearable size="default" style="width: 160px" @change="loadMemories">
        <el-option v-for="s in stockList" :key="s.code" :label="s.code" :value="s.code" />
      </el-select>
      <el-select v-model="filterType" :placeholder="tr(&quot;按类型筛选&quot;)" clearable size="default" style="width: 160px" @change="loadMemories">
        <el-option :label="tr(&quot;情境记忆&quot;)" value="situation_memory" />
        <el-option :label="tr(&quot;反思教训&quot;)" value="reflection_memory" />
      </el-select>
      <el-button v-if="filterStock" type="danger" size="small" text @click="deleteByStock">
        {{ tr("清空") }} {{ filterStock }} {{ tr("全部记忆") }}
      </el-button>
    </div>

    <!-- 记忆列表 -->
    <div class="memory-list">
      <div v-for="m in memories" :key="m.id" class="memory-card">
        <div class="memory-meta">
          <el-tag size="small" :type="getTypeTag(m)" effect="plain">{{ getTypeLabel(m) }}</el-tag>
          <span class="memory-stock">{{ getStock(m) }}</span>
          <el-button size="small" text type="danger" @click="deleteMemory(m.id)">{{ tr("删除") }}</el-button>
        </div>
        <div class="memory-content">{{ getContent(m) }}</div>
      </div>
      <el-empty v-if="memories.length === 0" :description="tr(&quot;暂无记忆数据&quot;)" :image-size="60" />
    </div>

    <!-- 添加对话框 -->
    <el-dialog v-model="addDialog.visible" :title="tr(&quot;添加经验&quot;)" width="560px" destroy-on-close>
      <el-form :model="addDialog.form" label-width="80px">
        <el-form-item :label="tr(&quot;交易对&quot;)">
          <el-input v-model="addDialog.form.stockCode" :placeholder="tr(&quot;如 300502，通用经验填 GENERAL&quot;)" />
        </el-form-item>
        <el-form-item :label="tr(&quot;类型&quot;)">
          <el-select v-model="addDialog.form.type" style="width: 100%">
            <el-option :label="tr(&quot;反思教训&quot;)" value="reflection_memory" />
            <el-option :label="tr(&quot;情境记忆&quot;)" value="situation_memory" />
          </el-select>
        </el-form-item>
        <el-form-item :label="tr(&quot;内容&quot;)">
          <el-input v-model="addDialog.form.content" type="textarea" :rows="6"
                    :placeholder="tr(&quot;写下经验教训，如：300502 在月线高位放量滞涨时不应抄底，需等日线底背驰确认...&quot;)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog.visible = false">{{ tr("取消") }}</el-button>
        <el-button type="primary" @click="saveMemory" :loading="addDialog.saving">{{ tr("保存") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t as tr } from '../preferences.js'
import { ref, reactive, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

const memories = ref([])
const stats = ref(null)
const stockList = ref([])
const searchQuery = ref('')
const filterStock = ref('')
const filterType = ref('')

const addDialog = reactive({
  visible: false, saving: false,
  form: { stockCode: '', type: 'reflection_memory', content: '' }
})

function getContent(m) {
  return m.content || m.text || ''
}
function getStock(m) {
  const meta = m.metadata || m
  return meta.stock_code || ''
}
function getTypeLabel(m) {
  const meta = m.metadata || m
  const t = meta.type || ''
  return t === 'reflection_memory' ? tr("反思") : tr("情境")
}
function getTypeTag(m) {
  const meta = m.metadata || m
  return meta.type === 'reflection_memory' ? 'warning' : 'info'
}

async function loadMemories() {
  try {
    const params = { limit: 50 }
    if (filterStock.value) params.stockCode = filterStock.value
    if (filterType.value) params.type = filterType.value
    const { data } = await axios.get('/api/memories', { params })
    memories.value = data
  } catch (e) { ElMessage.error(tr("加载失败")) }
}

async function loadStats() {
  try {
    const { data } = await axios.get('/api/memories/stats')
    stats.value = data
    stockList.value = data.stocks || []
  } catch (e) { /* ignore */ }
}

async function doSearch() {
  if (!searchQuery.value.trim()) { loadMemories(); return }
  try {
    const { data } = await axios.get('/api/memories/search', { params: { query: searchQuery.value, topK: 20 } })
    memories.value = data
  } catch (e) { ElMessage.error(tr("搜索失败")) }
}

function openAdd() {
  addDialog.form = { stockCode: filterStock.value || '', type: 'reflection_memory', content: '' }
  addDialog.visible = true
}

async function saveMemory() {
  if (!addDialog.form.content.trim()) { ElMessage.warning(tr("内容不能为空")); return }
  addDialog.saving = true
  try {
    await axios.post('/api/memories', addDialog.form)
    ElMessage.success(tr("已添加"))
    addDialog.visible = false
    await Promise.all([loadMemories(), loadStats()])
  } catch (e) { ElMessage.error(tr("添加失败")) } finally { addDialog.saving = false }
}

async function deleteMemory(id) {
  try {
    await ElMessageBox.confirm(tr("确定删除这条记忆？"), tr("确认"), { type: 'warning' })
    await axios.delete(`/api/memories/${id}`)
    ElMessage.success(tr("已删除"))
    await Promise.all([loadMemories(), loadStats()])
  } catch (e) { if (e !== 'cancel') ElMessage.error(tr("删除失败")) }
}

async function deleteByStock() {
  try {
    await ElMessageBox.confirm(tr("确定清空 {0} 的全部记忆？不可恢复。", { 0: filterStock.value }), tr("确认"), { type: 'warning' })
    await axios.delete(`/api/memories/by-stock/${filterStock.value}`)
    ElMessage.success(tr("已清空"))
    filterStock.value = ''
    await Promise.all([loadMemories(), loadStats()])
  } catch (e) { if (e !== 'cancel') ElMessage.error(tr("操作失败")) }
}

onMounted(() => { loadMemories(); loadStats() })
</script>

<style scoped>
.memory-view { display: flex; flex-direction: column; gap: 16px; }
.memory-toolbar { padding: 14px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); }
.header-actions, .stats-row, .filter-row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.header-actions :deep(.el-button + .el-button) { margin-left: 0; }
.memory-search { width: min(265px, 100%); }
.stat-chip { font-size: 13px; font-weight: 500; color: var(--text-2); background: var(--surface); border: 1px solid var(--border); padding: 8px 12px; border-radius: 8px; }
.filter-row :deep(.el-select) { max-width: calc(50% - 4px); }
.memory-list { display: flex; flex-direction: column; gap: 10px; }
.memory-card { background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 14px 16px; }
.memory-card:hover { border-color: var(--border-strong); }
.memory-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 8px; }
.memory-stock { font-size: 12px; color: var(--muted); font-family: monospace; }
.memory-content { font-size: 13px; color: var(--text-2); line-height: 1.6; white-space: pre-wrap; max-height: 120px; overflow-y: auto; }
</style>
