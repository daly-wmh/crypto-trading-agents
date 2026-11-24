<template>
  <div class="pipeline-view">
    <header class="page-heading">
      <h1>{{ tr("分析流水线编排") }}</h1>
      <div class="header-actions">
        <el-button size="default" @click="loadNodes">{{ tr("刷新") }}</el-button>
        <el-button type="primary" size="default" @click="openCreate">{{ tr("新增节点") }}</el-button>
      </div>
    </header>

    <!-- 流水线可视化 -->
    <div class="pipeline-stages">
      <div v-for="(stage, idx) in stages" :key="idx" class="stage-block">
        <div class="stage-label">
          <span class="stage-num">{{ idx + 1 }}</span>
          <span class="stage-type" v-if="stage[0].stageType === 'DEBATE'">{{ tr("辩论 ×") }}{{ stage[0].debateRounds || 2 }}{{ tr("轮") }}</span>
          <span class="stage-type" v-else>{{ stage.length > 1 ? tr("并行") : tr("串行") }}</span>
        </div>
        <div class="stage-nodes" :class="{ parallel: stage.length > 1 }">
          <div v-for="node in stage" :key="node.id"
               class="node-card" :class="{ disabled: !node.enabled }">
            <div class="node-top">
              <span class="node-name">{{ tr(node.displayName) }}</span>
              <el-switch v-model="node.enabled" size="small" @change="toggleNode(node)" />
            </div>
            <div class="node-meta">
              <el-tag size="small" :type="contextTagType(node.contextType)" effect="plain">
                {{ contextLabel(node.contextType) }}
              </el-tag>
              <span class="node-key">{{ node.nodeKey }}</span>
            </div>
            <div class="node-actions">
              <el-button size="small" text type="primary" @click="openEdit(node)">{{ tr("编辑") }}</el-button>
              <el-button size="small" text type="warning" @click="moveNode(node, -1)" :disabled="idx === 0">{{ tr("上移") }}</el-button>
              <el-button size="small" text type="warning" @click="moveNode(node, 1)" :disabled="idx === stages.length - 1">{{ tr("下移") }}</el-button>
              <el-button size="small" text type="danger" @click="deleteNode(node)">{{ tr("删除") }}</el-button>
            </div>
          </div>
        </div>
        <div v-if="idx < stages.length - 1" class="stage-arrow">↓</div>
      </div>
    </div>

    <!-- 编辑/新建对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? tr(&quot;编辑节点&quot;) : tr(&quot;新增节点&quot;)" width="680px" destroy-on-close>
      <el-form :model="dialog.form" label-width="100px" label-position="left">
        <el-form-item :label="tr(&quot;节点标识&quot;)" v-if="!dialog.isEdit">
          <el-input v-model="dialog.form.nodeKey" :placeholder="tr(&quot;如: my_analyst (英文下划线)&quot;)" />
        </el-form-item>
        <el-form-item :label="tr(&quot;显示名称&quot;)">
          <el-input v-model="dialog.form.displayName" :placeholder="tr(&quot;如: 技术面分析师&quot;)" />
        </el-form-item>
        <el-form-item :label="tr(&quot;上下文类型&quot;)">
          <el-select v-model="dialog.form.contextType" style="width: 100%">
            <el-option :label="tr(&quot;技术面数据 (K线+指标)&quot;)" value="ANALYST_MARKET" />
            <el-option :label="tr(&quot;成交与波动数据&quot;)" value="ANALYST_SOCIAL" />
            <el-option :label="tr(&quot;辩论 (研报+辩论历史)&quot;)" value="DEBATE" />
            <el-option :label="tr(&quot;投资决策 (四份研报)&quot;)" value="DECISION" />
            <el-option :label="tr(&quot;交易计划 (决策+技术面)&quot;)" value="TRADER" />
            <el-option :label="tr(&quot;风控决策 (全部上下文)&quot;)" value="RISK" />
            <el-option :label="tr(&quot;自定义 (所有可用数据)&quot;)" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item :label="tr(&quot;阶段序号&quot;)">
          <el-input-number v-model="dialog.form.stageOrder" :min="1" :max="10" />
          <span class="form-hint">{{ tr("相同序号的节点并行执行") }}</span>
        </el-form-item>
        <el-form-item :label="tr(&quot;阶段类型&quot;)">
          <el-select v-model="dialog.form.stageType" style="width: 160px">
            <el-option :label="tr(&quot;自动&quot;)" value="AUTO" />
            <el-option :label="tr(&quot;辩论模式&quot;)" value="DEBATE" />
          </el-select>
          <el-input-number v-if="dialog.form.stageType === 'DEBATE'"
            v-model="dialog.form.debateRounds" :min="1" :max="5" style="margin-left: 12px; width: 100px" />
          <span v-if="dialog.form.stageType === 'DEBATE'" class="form-hint">{{ tr("轮") }}</span>
        </el-form-item>
        <el-form-item :label="tr(&quot;模型源&quot;)">
          <el-select v-model="dialog.form.providerId" clearable :placeholder="tr(&quot;跟随全局&quot;)">
            <el-option :label="tr(&quot;Ollama (本地)&quot;)" value="ollama" />
            <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="String(p.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="System Prompt">
          <el-input v-model="dialog.form.systemPrompt" type="textarea" :rows="14"
                    :placeholder="tr(&quot;定义这个 Agent 的角色、分析方法和输出格式...&quot;)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">{{ tr("取消") }}</el-button>
        <el-button type="primary" @click="saveNode" :loading="dialog.saving">{{ tr("保存") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t as tr } from '../preferences.js'
import { ref, reactive, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

const nodes = ref([])
const providers = ref([])

const dialog = reactive({
  visible: false, isEdit: false, editId: null, saving: false,
  form: { nodeKey: '', displayName: '', contextType: 'CUSTOM', stageOrder: 1, stageType: 'AUTO', debateRounds: 2, providerId: '', modelName: '', systemPrompt: '' }
})

// 按 stageOrder 分组
const stages = computed(() => {
  const map = new Map()
  for (const n of nodes.value) {
    if (!map.has(n.stageOrder)) map.set(n.stageOrder, [])
    map.get(n.stageOrder).push(n)
  }
  return [...map.entries()].sort((a, b) => a[0] - b[0]).map(([, v]) =>
    v.sort((a, b) => a.positionInStage - b.positionInStage)
  )
})

function contextLabel(type) {
  const map = {
    ANALYST_MARKET: tr("技术面"), ANALYST_SOCIAL: tr("成交与波动"),
    DEBATE: tr("辩论"), DECISION: tr("决策"), TRADER: tr("计划"), RISK: tr("风控"), CIO: tr("最终研报"), CUSTOM: tr("自定义")
  }
  return map[type] || type
}

function contextTagType(type) {
  if (type.startsWith('ANALYST')) return 'primary'
  if (type === 'DECISION') return 'success'
  if (type === 'TRADER') return 'warning'
  if (type === 'RISK') return 'danger'
  return 'info'
}

async function loadNodes() {
  try {
    const { data } = await axios.get('/api/pipeline')
    nodes.value = data
  } catch (e) { ElMessage.error(tr("加载流水线失败")) }
}

async function loadProviders() {
  try {
    const { data } = await axios.get('/api/model-providers')
    providers.value = data
  } catch (e) { /* ignore */ }
}

function openCreate() {
  dialog.isEdit = false
  dialog.editId = null
  dialog.form = { nodeKey: '', displayName: '', contextType: 'CUSTOM', stageOrder: stages.value.length + 1, stageType: 'AUTO', debateRounds: 2, providerId: '', modelName: '', systemPrompt: '' }
  dialog.visible = true
}

function openEdit(node) {
  dialog.isEdit = true
  dialog.editId = node.id
  dialog.form = {
    nodeKey: node.nodeKey, displayName: node.displayName, contextType: node.contextType,
    stageOrder: node.stageOrder, stageType: node.stageType || 'AUTO', debateRounds: node.debateRounds || 2,
    providerId: node.providerId || '', modelName: node.modelName || '',
    systemPrompt: node.systemPrompt || ''
  }
  dialog.visible = true
}

async function saveNode() {
  const f = dialog.form
  if (!f.displayName || !f.contextType) {
    ElMessage.warning(tr("名称和上下文类型不能为空"))
    return
  }
  if (!dialog.isEdit && !f.nodeKey) {
    ElMessage.warning(tr("节点标识不能为空"))
    return
  }
  dialog.saving = true
  try {
    if (dialog.isEdit) {
      await axios.put(`/api/pipeline/${dialog.editId}`, f)
      ElMessage.success(tr("已保存"))
    } else {
      await axios.post('/api/pipeline', { ...f, positionInStage: 0, enabled: true })
      ElMessage.success(tr("已添加"))
    }
    dialog.visible = false
    await loadNodes()
  } catch (e) {
    ElMessage.error(tr("保存失败: ") + (e.response?.data?.message || e.message))
  } finally { dialog.saving = false }
}

async function toggleNode(node) {
  try {
    await axios.put(`/api/pipeline/${node.id}/toggle`)
  } catch (e) { ElMessage.error(tr("操作失败")) }
}

async function deleteNode(node) {
  try {
    await ElMessageBox.confirm(tr("确定删除「{0}」？", { 0: node.displayName }), tr("确认"), { type: 'warning' })
    await axios.delete(`/api/pipeline/${node.id}`)
    ElMessage.success(tr("已删除"))
    await loadNodes()
  } catch (e) { if (e !== 'cancel') ElMessage.error(tr("删除失败")) }
}

async function moveNode(node, direction) {
  const newStage = node.stageOrder + direction
  try {
    await axios.put(`/api/pipeline/${node.id}`, { stageOrder: newStage, positionInStage: 0 })
    await loadNodes()
  } catch (e) { ElMessage.error(tr("移动失败")) }
}

onMounted(() => { loadNodes(); loadProviders() })
</script>

<style scoped>
.pipeline-view { display: flex; flex-direction: column; gap: 20px; }
.header-actions { display: flex; gap: 8px; }
.header-actions :deep(.el-button + .el-button) { margin-left: 0; }

.pipeline-stages { display: flex; flex-direction: column; gap: 8px; }
.stage-block { width: 100%; max-width: 810px; }
.stage-label { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.stage-num {
  width: 24px; height: 24px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  background: var(--panel); color: var(--text-2); font: 500 12px var(--number-font);
}
.stage-type { font-size: 12px; color: var(--muted); }

.stage-nodes { display: flex; gap: 12px; flex-wrap: wrap; }
.stage-nodes.parallel { background: var(--surface-2); border: 1px dashed var(--border-strong); border-radius: 12px; padding: 12px; }

.node-card {
  flex: 1 1 220px; min-width: 0; max-width: 390px;
  background: var(--surface); border: 1px solid var(--border); border-radius: 12px;
  padding: 16px; transition: border-color .16s var(--ease-out);
}
.node-card:hover { border-color: var(--border-strong); }
.node-card.disabled { background: var(--surface-2); border-style: dashed; }
.node-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.node-name { font-size: 14px; font-weight: 600; color: var(--text); }
.node-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.node-key { font-size: 11px; color: var(--muted); font-family: monospace; }
.node-actions { display: flex; gap: 2px; flex-wrap: wrap; }

.stage-arrow { width: 24px; text-align: center; color: var(--muted); font-size: 18px; padding: 4px 0; }

.form-hint { font-size: 12px; color: var(--muted); margin-left: 12px; }
</style>
