<template>
  <section class="market-page">
    <header class="page-heading">
      <div>
        <h1>{{ tr("现货行情") }}</h1>
        <p>{{ tr("Binance · USDT 计价 · K 线坐标 UTC") }}</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="refresh">{{ tr("刷新报价") }}</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="warning" show-icon :closable="false" />

    <div class="market-grid">
      <el-card shadow="never" class="pair-panel">
        <template #header>
          <h2 class="panel-heading">{{ tr("交易对") }}</h2>
        </template>
        <div v-if="pairs.length" class="market-columns" aria-hidden="true">
          <span>{{ tr("交易对") }}</span><span>{{ tr("参考价") }}</span><span>{{ tr("24h 涨跌") }}</span>
        </div>
        <button v-for="item in pairs" :key="item.code" class="market-row" type="button"
                :class="{ selected: code === item.code }" :aria-pressed="code === item.code"
                @click="code = item.code">
          <strong>{{ item.name }}</strong>
          <span>{{ formatPrice(tickers[item.code]?.price) }}</span>
          <span :class="changeClass(tickers[item.code]?.changePercent)">{{ percent(tickers[item.code]?.changePercent) }}</span>
        </button>
      </el-card>

      <el-card shadow="never" class="market-chart">
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
        <SpotQuote v-if="code" :instrument="pair?.name || code" :ticker="ticker" :mode="quoteMode" />
        <KlineChart v-if="code" :stock-code="code" :days="120" :period="period"
                    :spot-price="quoteMode === 'stale' ? null : ticker?.price" />
        <el-empty v-else :description="tr(&quot;暂无交易对&quot;)" :image-size="64" />
        <el-button v-if="code" :icon="ArrowRight" class="analyze-pair" link type="primary" @click="router.push({ path: '/', query: { stock: code } })">
          {{ tr("分析") }} {{ pair?.name }}
        </el-button>
      </el-card>
    </div>
  </section>
</template>

<script setup>
import { t as tr } from '../preferences.js'
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Refresh } from '@element-plus/icons-vue'
import KlineChart from '../components/KlineChart.vue'
import SpotQuote from '../components/SpotQuote.vue'
import { errorMessage, formatPrice, getPairs, quote } from '../market.js'
import { observationTime, useSpotQuote } from '../useSpotQuote.js'

const router = useRouter()
const pairs = ref([])
const code = ref('')
const period = ref('1h')
const tickers = ref({})
const loading = ref(false)
const error = ref('')
const pair = computed(() => pairs.value.find(item => item.code === code.value))
const { ticker, mode: quoteMode } = useSpotQuote(code)

watch(ticker, rememberQuote)

onMounted(refresh)

function rememberQuote(latest) {
  if (latest && observationTime(latest) >= observationTime(tickers.value[latest.code])) {
    tickers.value[latest.code] = latest
  }
}

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    pairs.value = await getPairs()
    code.value ||= pairs.value[0]?.code || ''
    const fetched = await Promise.allSettled(pairs.value.map(item => quote(item.code)))
    for (const item of fetched) {
      if (item.status === 'fulfilled') rememberQuote(item.value)
    }
    if (fetched.some(item => item.status === 'rejected')) error.value = tr("部分公开报价暂时不可用")
  } catch (failure) {
    error.value = errorMessage(failure, tr("行情服务暂不可用"))
  } finally {
    loading.value = false
  }
}

function changeClass(value) {
  return value == null ? '' : Number(value) >= 0 ? 'positive' : 'negative'
}

function percent(value) {
  if (value == null) return '—'
  return `${Number(value) >= 0 ? '+' : ''}${Number(value).toFixed(2)}%`
}
</script>

<style scoped>
.market-page, .market-grid { display: grid; gap: 20px; min-width: 0; }
.market-grid { grid-template-columns: minmax(265px, 1fr) minmax(0, 2.3fr); align-items: start; }
.chart-heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; font-size: 16px; }
.pair-panel :deep(.el-card__body) { padding: 12px; }
.market-columns, .market-row {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(66px, 1fr) minmax(74px, 1fr) minmax(52px, .75fr);
  align-items: center;
  gap: 8px;
  padding: 12px 8px;
  text-align: left;
  border: 1px solid transparent;
  font-variant-numeric: tabular-nums;
}
.market-columns { color: var(--text-3); font-size: 12px; font-weight: 500; }
.market-row { border-radius: 4px; background: transparent; color: var(--text-2); cursor: pointer; font-size: 12px; }
.market-row strong { color: var(--text); }
.market-columns span:not(:first-child), .market-row span { text-align: right; }
.market-row span { font-family: var(--number-font); }
.market-row + .market-row { border-top-color: var(--border); }
.market-row:hover { background: var(--surface-2); }
.market-row.selected { background: var(--accent-soft); border-color: var(--accent-border); }
.market-row.selected strong { color: var(--accent); }
.positive { color: var(--positive); }
.negative { color: var(--negative); }
.analyze-pair { margin-top: 12px; }
@media (max-width: 980px) { .market-grid { grid-template-columns: minmax(0, 1fr); gap: 15px; } }
</style>
