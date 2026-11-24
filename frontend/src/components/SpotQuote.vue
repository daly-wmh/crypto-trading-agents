<template>
  <div class="quote-overview">
    <div class="quote-main">
      <div class="quote-heading">
        <span class="quote-instrument">{{ instrument }}</span>
        <span>{{ tr("最新成交价") }}</span>
      </div>
      <div class="quote-price">
        <strong>{{ formatPrice(ticker?.price) }}</strong>
        <small>USDT</small>
        <span v-if="change !== null" class="quote-change" :class="change >= 0 ? 'is-up' : 'is-down'">
          <span>{{ change >= 0 ? '+' : '' }}{{ change.toFixed(2) }}%</span>
          <small>{{ tr("24 小时") }}</small>
        </span>
      </div>
      <div class="quote-status" :class="`is-${mode}`">
        <span class="quote-status-dot" aria-hidden="true"></span>
        <span>{{ tr(statusLabel[mode]) }}</span>
        <time v-if="timestamp" :datetime="ticker.observedAt">{{ tr("本地") }} {{ clock.format(timestamp) }}</time>
      </div>
    </div>
    <dl v-if="ticker" class="quote-stats">
      <div><dt>{{ tr("24h 最高") }}</dt><dd>{{ formatPrice(ticker.high) }}</dd></div>
      <div><dt>{{ tr("24h 最低") }}</dt><dd>{{ formatPrice(ticker.low) }}</dd></div>
      <div><dt>{{ tr("24h 成交额") }}</dt><dd>{{ formatPrice(ticker.quoteVolume, 0) }} <small>USDT</small></dd></div>
    </dl>
  </div>
</template>

<script setup>
import { language, t as tr } from '../preferences.js'
import { computed } from 'vue'
import { formatPrice } from '../market.js'
import { observationTime } from '../useSpotQuote.js'

const props = defineProps({
  instrument: { type: String, required: true },
  ticker: { type: Object, default: null },
  mode: { type: String, required: true }
})
const clock = computed(() => new Intl.DateTimeFormat(language.value, {
  hour: '2-digit', minute: '2-digit', second: '2-digit'
}))
const statusLabel = {
  live: '实时推送',
  polling: '轮询报价',
  stale: '行情未更新',
  connecting: '正在连接行情'
}
const timestamp = computed(() => observationTime(props.ticker))
const change = computed(() => {
  const value = props.ticker?.changePercent
  return value != null && Number.isFinite(Number(value)) ? Number(value) : null
})
</script>

<style scoped>
.quote-overview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px 24px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.quote-heading { display: flex; align-items: baseline; flex-wrap: wrap; gap: 8px; color: var(--text-3); font-size: 12px; }
.quote-instrument { color: var(--text); font: 500 14px var(--number-font); }
.quote-price {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
  font-variant-numeric: tabular-nums;
}
.quote-price strong { font: 500 2.75rem/1.08 var(--number-font); letter-spacing: -.04em; overflow-wrap: anywhere; }
.quote-price small, .quote-stats small { color: var(--text-3); font: 500 11px var(--ui-font); }
.quote-change { display: inline-flex; align-items: baseline; gap: 4px; font: 500 13px var(--number-font); white-space: nowrap; }
.quote-change.is-up { color: var(--positive); }
.quote-change.is-down { color: var(--negative); }
.quote-stats { display: flex; flex-wrap: wrap; gap: 8px 24px; margin: 0; }
.quote-stats > div + div { border-inline-start: 1px solid var(--border); padding-inline-start: 24px; }
.quote-stats dt { color: var(--text-3); font-size: 12px; }
.quote-stats dd { margin: 4px 0 0; font: 500 15px/1.4 var(--number-font); }
.quote-status { display: flex; align-items: center; gap: 4px; color: var(--text-3); margin-top: 8px; font-size: 12px; }
.quote-status-dot { width: 8px; height: 8px; border: 1px solid currentColor; border-radius: 100%; }
.quote-status.is-live .quote-status-dot { background: currentColor; }
.quote-status time { color: var(--text-3); margin-left: 4px; font-family: var(--number-font); }
.quote-status.is-live { color: var(--positive); }
.quote-status.is-stale { color: var(--negative); }
@media (max-width: 580px) {
  .quote-price strong { font-size: 2.125rem; }
  .quote-stats { gap: 8px 12px; }
  .quote-stats > div + div { padding-inline-start: 0; border: 0; }
  .quote-stats dd { font-size: 13px; }
}
</style>
