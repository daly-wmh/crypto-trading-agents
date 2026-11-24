<template>
  <div class="settings-view">
    <header class="page-heading"><h1>{{ tr("系统设置") }}</h1></header>
    <!-- 模型源管理 -->
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <h2 class="panel-heading">{{ tr("模型源管理") }}</h2>
          <el-button type="primary" size="default" @click="openProviderDialog(null)">{{ tr("新增模型源") }}</el-button>
        </div>
      </template>
      <el-table :data="providers" size="default" border stripe>
        <el-table-column :label="tr(&quot;名称&quot;)" prop="name" width="140" />
        <el-table-column :label="tr(&quot;类型&quot;)" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'ollama' ? 'success' : 'primary'" size="default">
              {{ row.type === 'ollama' ? 'Ollama' : 'API' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Base URL" prop="baseUrl" min-width="200" show-overflow-tooltip />
        <el-table-column :label="tr(&quot;API Key 环境变量&quot;)" prop="apiKeyEnvVar" width="180" show-overflow-tooltip />
        <el-table-column :label="tr(&quot;默认模型&quot;)" prop="modelName" width="160" show-overflow-tooltip />
        <el-table-column :label="tr(&quot;操作&quot;)" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="default" link type="primary" @click="openProviderDialog(row)">{{ tr("编辑") }}</el-button>
            <el-button size="default" link type="primary" @click="duplicateProvider(row)">{{ tr("复制") }}</el-button>
            <el-button size="default" link type="danger" @click="deleteProvider(row)">{{ tr("删除") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="form-tip" style="margin-top: 8px">
        {{ tr("Ollama 为内置本地模型源（自动拉取已安装模型），此处可添加其他 OpenAI 兼容的 API 模型源") }}
      </div>
    </el-card>

    <!-- 全局默认 -->
    <el-card shadow="hover">
      <template #header><h2 class="panel-heading">{{ tr("全局默认模型") }}</h2></template>
      <el-form label-width="100px" class="settings-form">
        <el-form-item :label="tr(&quot;模型源&quot;)">
          <el-select v-model="globalDefault.providerId" size="default" @change="onGlobalProviderChange">
            <el-option :label="tr(&quot;Ollama (本地)&quot;)" value="ollama" />
            <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="String(p.id)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="tr(&quot;模型&quot;)">
          <el-select v-if="globalDefault.providerId === 'ollama'" v-model="globalDefault.modelName"
                     filterable allow-create clearable :placeholder="tr(&quot;留空=使用Ollama默认模型&quot;)">
            <el-option v-for="m in ollamaModels" :key="m" :label="m" :value="m" />
          </el-select>
          <el-input v-else v-model="globalDefault.modelName" :placeholder="globalProviderDefaultModel" />
        </el-form-item>
      </el-form>
      <div class="form-tip">{{ tr("流水线中未单独配置模型源的节点将使用此全局默认") }}</div>
      <div style="margin-top: 12px; text-align: right">
        <el-button type="primary" size="default" @click="saveGlobalDefault" :loading="saving">{{ tr("保存") }}</el-button>
      </div>
    </el-card>

    <!-- 投研顾问模型配置 -->
    <el-card shadow="hover">
      <template #header><h2 class="panel-heading">{{ tr("投研顾问模型") }}</h2></template>
      <el-form label-width="100px" class="settings-form">
        <el-form-item :label="tr(&quot;模型源&quot;)">
          <el-select v-model="advisorConfig.providerId" size="default" @change="advisorConfig.modelName = ''">
            <el-option :label="tr(&quot;全局默认&quot;)" value="" />
            <el-option :label="tr(&quot;Ollama (本地)&quot;)" value="ollama" />
            <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="String(p.id)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="tr(&quot;模型&quot;)">
          <el-select v-if="advisorConfig.providerId === 'ollama'" v-model="advisorConfig.modelName"
                     filterable allow-create clearable :placeholder="tr(&quot;留空=使用Ollama默认模型&quot;)">
            <el-option v-for="m in ollamaModels" :key="m" :label="m" :value="m" />
          </el-select>
          <el-input v-else-if="advisorConfig.providerId" v-model="advisorConfig.modelName"
                    :placeholder="advisorProviderDefaultModel" />
          <span v-else class="use-default">{{ tr("跟随全局默认") }}</span>
        </el-form-item>
      </el-form>
      <div class="form-tip">{{ tr("投研顾问是独立的对话式 AI，不参与分析流水线") }}</div>
      <div style="margin-top: 12px; text-align: right">
        <el-button type="primary" size="default" @click="saveAdvisorConfig" :loading="advisorSaving">{{ tr("保存") }}</el-button>
      </div>
    </el-card>

    <!-- 模型源编辑对话框 -->
    <el-dialog v-model="providerDialog.visible"
               :title="providerDialog.isEdit ? tr(&quot;编辑模型源&quot;) : providerDialog.isCopy ? tr(&quot;复制模型源&quot;) : tr(&quot;新增模型源&quot;)"
               width="500px" destroy-on-close>
      <el-form :model="providerDialog.form" label-width="140px">
        <el-form-item :label="tr(&quot;名称&quot;)" required>
          <el-input v-model="providerDialog.form.name" :placeholder="tr(&quot;如: 阿里云百炼&quot;)" />
        </el-form-item>
        <el-form-item :label="tr(&quot;类型&quot;)" required>
          <el-select v-model="providerDialog.form.type" @change="onProviderTypeChange">
            <el-option :label="tr(&quot;Ollama (本地)&quot;)" value="ollama" />
            <el-option :label="tr(&quot;OpenAI 兼容 API&quot;)" value="openai" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL" v-if="providerDialog.form.type === 'openai'">
          <el-input v-model="providerDialog.form.baseUrl" placeholder="https://dashscope.aliyuncs.com/compatible-mode" />
        </el-form-item>
        <el-form-item :label="tr(&quot;API Key 环境变量&quot;)" v-if="providerDialog.form.type === 'openai'">
          <el-input v-model="providerDialog.form.apiKeyEnvVar" :placeholder="tr(&quot;如: AI_BAILIAN_API_KEY&quot;)" />
          <div class="form-tip">{{ tr("系统将从此环境变量读取 API Key") }}</div>
        </el-form-item>
        <el-form-item :label="tr(&quot;默认模型&quot;)" required>
          <el-input v-model="providerDialog.form.modelName"
                    :placeholder="providerDialog.form.type === 'ollama' ? tr(&quot;如: qwen3.5:9b&quot;) : tr(&quot;如: qwen3.7-plus&quot;)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="providerDialog.visible = false">{{ tr("取消") }}</el-button>
        <el-button type="primary" @click="saveProvider" :loading="providerDialog.saving">{{ tr("保存") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t as tr } from '../preferences.js'
import { reactive, ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

// ===== 模型源 =====
const providers = ref([])
const providerDialog = reactive({
  visible: false,
  isEdit: false,
  isCopy: false,
  editId: null,
  saving: false,
  form: { name: '', type: 'openai', baseUrl: '', apiKeyEnvVar: '', modelName: '' }
})

// ===== 全局默认 =====
const globalDefault = reactive({ providerId: 'ollama', modelName: '' })
const ollamaModels = ref([])

// ===== 投研顾问 =====
const advisorConfig = reactive({ providerId: '', modelName: '' })
const advisorSaving = ref(false)

const advisorProviderDefaultModel = computed(() => {
  const p = providers.value.find(p => String(p.id) === advisorConfig.providerId)
  return p ? tr("留空={0}", { 0: p.modelName }) : tr("留空=提供商默认")
})

const globalProviderDefaultModel = computed(() => {
  if (globalDefault.providerId === 'ollama') return tr("留空=使用Ollama默认模型")
  const p = providers.value.find(p => String(p.id) === globalDefault.providerId)
  return p ? tr("留空={0}", { 0: p.modelName }) : tr("留空=使用提供商默认模型")
})

// ===== 加载数据 =====
async function loadAll() {
  await Promise.all([loadProviders(), loadOllamaModels(), loadGlobalDefault(), loadAdvisorConfig()])
}

async function loadProviders() {
  try {
    const { data } = await axios.get('/api/model-providers')
    providers.value = data
  } catch (e) { /* ignore */ }
}

async function loadOllamaModels() {
  try {
    const { data } = await axios.get('/api/agent-config/ollama-models')
    ollamaModels.value = data
  } catch (e) { /* ignore */ }
}

async function loadGlobalDefault() {
  try {
    const { data } = await axios.get('/api/agent-config/global-default')
    globalDefault.providerId = data.providerId || 'ollama'
    globalDefault.modelName = data.modelName || ''
  } catch (e) { /* ignore */ }
}

async function loadAdvisorConfig() {
  try {
    const { data } = await axios.get('/api/agent-config')
    const advisor = data.find(c => c.agentId === 'advisor')
    if (advisor) {
      advisorConfig.providerId = advisor.providerId || ''
      advisorConfig.modelName = advisor.modelName || ''
    }
  } catch (e) { /* ignore */ }
}

function onGlobalProviderChange() {
  globalDefault.modelName = ''
}

function onProviderTypeChange() {
  const f = providerDialog.form
  if (f.type === 'ollama') {
    f.baseUrl = 'http://localhost:11434'
    f.apiKeyEnvVar = ''
  } else {
    f.baseUrl = ''
    f.apiKeyEnvVar = ''
  }
}

// ===== 模型源 CRUD =====
function openProviderDialog(provider) {
  if (provider) {
    providerDialog.isEdit = true
    providerDialog.isCopy = false
    providerDialog.editId = provider.id
    providerDialog.form = {
      name: provider.name,
      type: provider.type,
      baseUrl: provider.baseUrl || '',
      apiKeyEnvVar: provider.apiKeyEnvVar || '',
      modelName: provider.modelName || ''
    }
  } else {
    providerDialog.isEdit = false
    providerDialog.isCopy = false
    providerDialog.editId = null
    providerDialog.form = { name: '', type: 'openai', baseUrl: '', apiKeyEnvVar: '', modelName: '' }
  }
  providerDialog.visible = true
}

function duplicateProvider(provider) {
  providerDialog.isEdit = false
  providerDialog.isCopy = true
  providerDialog.editId = null
  providerDialog.form = {
    name: provider.name + tr(" (副本)"),
    type: provider.type,
    baseUrl: provider.baseUrl || '',
    apiKeyEnvVar: provider.apiKeyEnvVar || '',
    modelName: provider.modelName || ''
  }
  providerDialog.visible = true
}

async function saveProvider() {
  const f = providerDialog.form
  if (!f.name || !f.modelName) {
    ElMessage.warning(tr("名称和默认模型不能为空"))
    return
  }
  providerDialog.saving = true
  try {
    if (providerDialog.isEdit) {
      await axios.put(`/api/model-providers/${providerDialog.editId}`, f)
      ElMessage.success(tr("模型源已更新"))
    } else {
      await axios.post('/api/model-providers', f)
      ElMessage.success(tr("模型源已添加"))
    }
    providerDialog.visible = false
    await loadProviders()
  } catch (e) {
    ElMessage.error(tr("保存失败: ") + (e.response?.data?.error || e.message))
  } finally {
    providerDialog.saving = false
  }
}

async function deleteProvider(provider) {
  try {
    await ElMessageBox.confirm(tr("确定删除模型源「{0}」？", { 0: provider.name }), tr("确认删除"), { type: 'warning' })
    await axios.delete(`/api/model-providers/${provider.id}`)
    ElMessage.success(tr("已删除"))
    await loadProviders()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(tr("删除失败: ") + (e.response?.data?.error || e.message))
    }
  }
}

// ===== 保存全局默认 =====
const saving = ref(false)
async function saveGlobalDefault() {
  saving.value = true
  try {
    await axios.put('/api/agent-config/global-default', {
      providerId: globalDefault.providerId,
      modelName: globalDefault.modelName || ''
    })
    ElMessage.success(tr("全局默认已保存"))
  } catch (e) {
    ElMessage.error(tr("保存失败"))
  } finally {
    saving.value = false
  }
}

async function saveAdvisorConfig() {
  advisorSaving.value = true
  try {
    await axios.put('/api/agent-config/batch', [{
      agentId: 'advisor',
      providerId: advisorConfig.providerId || '',
      modelName: advisorConfig.modelName || ''
    }])
    ElMessage.success(tr("投研顾问模型已保存"))
  } catch (e) {
    ElMessage.error(tr("保存失败"))
  } finally {
    advisorSaving.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.settings-view { display: flex; flex-direction: column; gap: 20px; min-width: 0; }
.settings-form { max-width: 500px; }
.form-tip { font-size: 12px; color: var(--muted); margin-top: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
.use-default { font-size: 12px; color: var(--muted); }
</style>
