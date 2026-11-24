<template>
  <section class="paper-trading">
    <header class="page-heading">
      <div>
        <h1>{{ tr("现货模拟账户") }}</h1>
        <p>{{ tr("按提交时的公开报价模拟成交，手续费 0.1%") }}</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">{{ tr("刷新账户") }}</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="warning" show-icon :closable="false" />
    <dl class="summary-grid glass-panel">
      <div v-for="item in stats" :key="item.label">
        <dt class="metric-label">{{ item.label }}</dt>
        <dd class="metric-value" :class="item.tone">{{ formatPrice(item.value, 2) }} <small>USDT</small></dd>
      </div>
    </dl>

    <div class="trade-grid">
      <el-card shadow="never" class="review-panel market-chart">
        <template #header>
          <div class="review-header">
            <h2 class="panel-heading">{{ tr("成交与盈亏走势") }}</h2>
            <div class="review-controls">
              <el-select v-model="form.pair" class="review-pair" size="small" :aria-label="tr(&quot;图表交易对&quot;)" :disabled="trading">
                <el-option v-for="item in pairs" :key="item.code" :value="item.code" :label="item.name" />
              </el-select>
              <el-radio-group v-model="period" size="small" class="period-select" :aria-label="tr(&quot;模拟交易 K 线周期&quot;)">
                <el-radio-button value="15m">{{ tr("15 分钟") }}</el-radio-button>
                <el-radio-button value="1h">{{ tr("1 小时") }}</el-radio-button>
                <el-radio-button value="4h">{{ tr("4 小时") }}</el-radio-button>
                <el-radio-button value="1d">{{ tr("1 日") }}</el-radio-button>
              </el-radio-group>
            </div>
          </div>
        </template>
        <template v-if="form.pair">
          <dl class="review-metrics">
            <div>
              <dt>{{ tr("最新成交价") }}</dt>
              <dd>{{ formatPrice(spotPrice) }} <small>{{ quoteMode === 'stale' ? tr("行情未更新") : 'USDT' }}</small></dd>
            </div>
            <div>
              <dt>{{ tr("持仓均价") }}</dt>
              <dd>{{ formatPrice(position?.avgCost) }} <small>USDT</small></dd>
            </div>
            <div>
              <dt>{{ tr("浮动盈亏") }}</dt>
              <dd :class="pnlClass">{{ formatPrice(currentPnl, 4) }} <small>USDT</small></dd>
            </div>
          </dl>
          <KlineChart :stock-code="form.pair" :period="period" :spot-price="spotPrice"
                      :trade-fills="fills" :position="position" />
          <p class="review-note">
            {{ pairOrders.length ? tr("历史盈亏按各根 K 线收盘价估算，当前点跟随现价。买入手续费计入成本。") : tr("该交易对暂无模拟成交。") }}
          </p>
        </template>
        <el-empty v-else :description="tr(&quot;选择交易对后查看成交走势&quot;)" :image-size="64" />
      </el-card>

      <el-card shadow="never" class="order-panel">
        <template #header><h2 class="panel-heading">{{ tr("模拟下单") }}</h2></template>
        <el-form label-position="top" @submit.prevent="placeTrade">
          <el-form-item :label="tr(&quot;交易对&quot;)">
            <el-select v-model="form.pair" :aria-label="tr(&quot;交易对&quot;)" style="width: 100%" :disabled="trading">
              <el-option v-for="item in pairs" :key="item.code" :value="item.code" :label="item.name" />
            </el-select>
          </el-form-item>
          <el-form-item :label="tr(&quot;方向&quot;)">
            <el-radio-group v-model="form.action">
              <el-radio-button value="buy">{{ tr("买入") }}</el-radio-button>
              <el-radio-button value="sell">{{ tr("卖出") }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="tr(&quot;数量（最多 8 位小数）&quot;)">
            <el-input v-model.trim="form.quantity" inputmode="decimal" :placeholder="tr(&quot;如 0.001&quot;)" :aria-label="tr(&quot;模拟交易数量&quot;)" />
          </el-form-item>
          <p v-if="spotPrice" class="preview">{{ tr("参考价") }} {{ formatPrice(spotPrice) }} USDT</p>
          <p v-else-if="quoteMode === 'stale'" class="preview">{{ tr("行情未更新") }}</p>
          <el-button type="primary" native-type="submit" :loading="trading" :disabled="!form.pair" class="submit">
            {{ tr("确认模拟") }}{{ form.action === 'buy' ? tr("买入") : tr("卖出") }}
          </el-button>
        </el-form>
      </el-card>

      <el-card shadow="never" class="positions-panel">
        <template #header><h2 class="panel-heading">{{ tr("现货持仓") }}</h2></template>
        <el-table :data="displayPositions" :empty-text="tr(&quot;暂无持仓&quot;)">
          <el-table-column prop="stockCode" :label="tr(&quot;交易对&quot;)" min-width="100" />
          <el-table-column :label="tr(&quot;数量&quot;)" min-width="94">
            <template #default="{ row }">{{ formatPrice(row.quantity) }}</template>
          </el-table-column>
          <el-table-column :label="tr(&quot;平均成本&quot;)" min-width="100">
            <template #default="{ row }">{{ formatPrice(row.avgCost) }}</template>
          </el-table-column>
          <el-table-column :label="tr(&quot;现价&quot;)" min-width="92">
            <template #default="{ row }">{{ formatPrice(row.currentPrice) }}</template>
          </el-table-column>
          <el-table-column :label="tr(&quot;未实现盈亏&quot;)" min-width="112">
            <template #default="{ row }">{{ formatPrice(row.unrealizedPnl, 4) }}</template>
          </el-table-column>
          <el-table-column width="64">
            <template #default="{ row }"><el-button text type="primary" @click="sell(row)">{{ tr("卖出") }}</el-button></template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="activity-panel">
        <template #header><h2 class="panel-heading">{{ tr("模拟成交记录") }}</h2></template>
        <el-table :data="orders" :empty-text="tr(&quot;暂无成交&quot;)">
          <el-table-column prop="stockCode" :label="tr(&quot;交易对&quot;)" min-width="100" />
          <el-table-column :label="tr(&quot;方向&quot;)" width="72">
            <template #default="{ row }">{{ row.direction === 'BUY' ? tr("买入") : tr("卖出") }}</template>
          </el-table-column>
          <el-table-column :label="tr(&quot;成交时间（本地）&quot;)" min-width="170">
            <template #default="{ row }">{{ formatOrderTime(row) }}</template>
          </el-table-column>
          <el-table-column :label="tr(&quot;价格&quot;)" min-width="90">
            <template #default="{ row }">{{ formatPrice(row.price) }}</template>
          </el-table-column>
          <el-table-column :label="tr(&quot;数量&quot;)" min-width="90">
            <template #default="{ row }">{{ formatPrice(row.quantity) }}</template>
          </el-table-column>
          <el-table-column :label="tr(&quot;费用（USDT）&quot;)" min-width="110">
            <template #default="{ row }">{{ formatPrice(row.fee) }}</template>
          </el-table-column>
          <el-table-column prop="remark" :label="tr(&quot;备注&quot;)" min-width="115" />
        </el-table>
      </el-card>
    </div>
  </section>
</template>

<script setup>
import { language, t as tr } from '../preferences.js'
import { computed, onActivated, reactive, ref, toRef } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import KlineChart from '../components/KlineChart.vue'
import { errorMessage, formatPrice, getPairs } from '../market.js'
import { chartFills, unrealizedPnl } from '../paperTradeChart.js'
import { useSpotQuote } from '../useSpotQuote.js'

const accountId = 1
const pairs = ref([])
const summary = ref(null)
const orders = ref([])
const period = ref('15m')
const error = ref('')
const loading = ref(false)
const trading = ref(false)
const form = reactive({ pair: '', action: 'buy', quantity: '' })
const { ticker, mode: quoteMode } = useSpotQuote(toRef(form, 'pair'))
const spotPrice = computed(() => quoteMode.value === 'stale' ? null : ticker.value?.price ?? null)
const position = computed(() => summary.value?.positions?.find(item => item.stockCode === form.pair) ?? null)
const pairOrders = computed(() => orders.value.filter(order => order.stockCode === form.pair))
const fills = computed(() => chartFills(pairOrders.value))
const currentPnl = computed(() => unrealizedPnl(position.value, spotPrice.value))
const pnlClass = computed(() => currentPnl.value > 0 ? 'profit' : currentPnl.value < 0 ? 'loss' : '')
const displayPositions = computed(() => (summary.value?.positions || []).map(item => {
  if (item.stockCode !== form.pair || !spotPrice.value) return item
  return { ...item, currentPrice: spotPrice.value, unrealizedPnl: currentPnl.value }
}))
const orderClock = computed(() => new Intl.DateTimeFormat(language.value, { dateStyle: 'short', timeStyle: 'medium' }))
let loadVersion = 0

const marketAdjustment = computed(() => {
  const selected = position.value
  if (!selected || !spotPrice.value) return 0
  return (Number(spotPrice.value) - Number(selected.currentPrice)) * Number(selected.quantity)
})
const stats = computed(() => {
  const pnl = markAccountValue(summary.value?.totalPnl)
  return [
    { label: tr("总资产"), value: markAccountValue(summary.value?.totalAssets) },
    { label: tr("可用余额"), value: summary.value?.available },
    { label: tr("持仓市值"), value: markAccountValue(summary.value?.totalMarketValue) },
    { label: tr("全部持仓浮盈亏"), value: pnl, tone: pnl > 0 ? 'profit' : pnl < 0 ? 'loss' : '' }
  ]
})

onActivated(load)

function markAccountValue(value) {
  return value == null ? null : Number(value) + marketAdjustment.value
}

function formatOrderTime(order) {
  const time = Date.parse(order.executedAt || order.createdAt)
  return Number.isFinite(time) ? orderClock.value.format(time) : '—'
}

async function load() {
  const version = ++loadVersion
  loading.value = true
  error.value = ''
  try {
    const [instruments, account, recent] = await Promise.all([
      getPairs(),
      axios.get(`/api/trading/account/${accountId}`),
      axios.get(`/api/trading/orders/${accountId}`)
    ])
    if (version !== loadVersion) return
    pairs.value = instruments
    form.pair ||= instruments[0]?.code || ''
    summary.value = account.data
    orders.value = recent.data
  } catch (failure) {
    if (version === loadVersion) error.value = errorMessage(failure, tr("无法加载模拟账户"))
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

function sell(position) {
  form.pair = position.stockCode
  form.action = 'sell'
  form.quantity = position.quantity.toFixed(8).replace(/\.?0+$/, '')
}

async function placeTrade() {
  if (trading.value) return
  if (!/^\d{1,12}(\.\d{1,8})?$/.test(form.quantity) || Number(form.quantity) <= 0) {
    ElMessage.warning(tr("请输入大于零且最多 8 位小数的数量"))
    return
  }

  trading.value = true
  const { pair, action, quantity } = form
  try {
    await axios.post(`/api/trading/${action}`, {
      accountId, stockCode: pair, quantity
    })
    form.quantity = ''
    ElMessage.success(tr("模拟成交已记录"))
    await load()
  } catch (failure) {
    ElMessage.error(errorMessage(failure, tr("模拟成交失败")))
  } finally {
    trading.value = false
  }
}
</script>

<style scoped>
.paper-trading { display: grid; gap: 20px; min-width: 0; }
.metric-label, .preview { color: var(--text-3); }
.metric-label { font-size: 13px; font-weight: 500; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0; }
.summary-grid > div { min-width: 0; padding: 16px 20px; }
.summary-grid > div + div { border-inline-start: 1px solid var(--border); }
.metric-value { margin: 4px 0 0; color: var(--text); font: 500 1.625rem/1.3 var(--number-font); overflow-wrap: anywhere; }
.metric-value small { color: var(--text-3); font: 500 12px var(--ui-font); }
.trade-grid { display: grid; grid-template-columns: minmax(0, 2.3fr) minmax(245px, 1fr); gap: 20px; align-items: start; }
.review-header, .review-controls { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.review-header { justify-content: space-between; }
.review-pair { width: 126px; }
.review-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
  margin: 0 0 8px;
  font-variant-numeric: tabular-nums;
}
.review-metrics dt, .review-note { color: var(--text-3); font-size: 12px; }
.review-metrics dd { margin: 4px 0 0; color: var(--text); font: 500 1.25rem/1.35 var(--number-font); }
.review-metrics small { color: var(--text-3); font: 500 11px var(--ui-font); }
.metric-value.profit, .review-metrics dd.profit { color: var(--positive); }
.metric-value.loss, .review-metrics dd.loss { color: var(--negative); }
.review-note { margin-top: 12px; }
.order-panel :deep(.el-card__header) { background: var(--surface-2); }
.preview { font-size: .86rem; margin-bottom: 12px; }
.submit { width: 100%; }
@media (min-width: 1001px) {
  .order-panel { grid-column: 2; grid-row: 1 / span 2; }
  .activity-panel { grid-column: 1 / -1; }
}
@media (max-width: 1000px) {
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .summary-grid > div:nth-child(odd) { border-inline-start: 0; }
  .summary-grid > div:nth-child(n + 3) { border-top: 1px solid var(--border); }
  .trade-grid { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 580px) {
  .summary-grid > div { padding: 12px 14px; }
  .metric-value, .review-metrics dd { font-size: 1.125rem; }
}
</style>
