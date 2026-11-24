<template>
  <div class="kline-chart" :class="{ 'has-trades': tradeFills !== null }">
    <el-skeleton v-if="!lastUpdated && !error" animated :rows="7" />
    <el-empty v-else-if="!lastUpdated" :description="tr(error)" :image-size="65">
      <el-button size="small" @click="retry">{{ tr("重试") }}</el-button>
    </el-empty>
    <v-chart ref="chart" v-show="lastUpdated" manual-update autoresize class="chart" />
    <div v-if="lastUpdated" class="refresh-status" :class="{ 'refresh-error': error, 'refresh-live': live }">
      <span>{{ error ? tr("K 线暂未更新 · 保留上次数据") : live ? tr("K 线实时推送") : tr("K 线每 5 秒轮询") }}</span>
      <time :datetime="lastUpdated.toISOString()">{{ tr("本地") }} {{ refreshTime.format(lastUpdated) }}</time>
    </div>
  </div>
</template>

<script setup>
import { language, theme, t as tr } from '../preferences.js'
import { computed, ref, watch } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { CandlestickChart, BarChart, LineChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, GridComponent,
  DataZoomComponent, LegendComponent, MarkLineComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import { formatPrice } from '../market.js'
import { tradePnlHistory } from '../paperTradeChart.js'
import { useKlineFeed } from '../useKlineFeed.js'

use([
  CanvasRenderer, CandlestickChart, BarChart, LineChart,
  TitleComponent, TooltipComponent, GridComponent,
  DataZoomComponent, LegendComponent, MarkLineComponent
])

const props = defineProps({
  stockCode: { type: String, required: true },
  days: { type: Number, default: 120 },
  period: { type: String, default: '1h' },
  spotPrice: { type: [Number, String], default: null },
  tradeFills: { type: Array, default: null },
  position: { type: Object, default: null }
})
const chart = ref(null)
const { candles, lastUpdated, live, error, retry } = useKlineFeed(props)
const refreshTime = computed(() => new Intl.DateTimeFormat(language.value, { hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }))
const tradingAxisTime = computed(() => new Intl.DateTimeFormat(language.value, {
  month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
}))
const tradingAxisDay = computed(() => new Intl.DateTimeFormat(language.value, { year: 'numeric', month: '2-digit', day: '2-digit' }))
let lastData
let chartColors = readColors()

function readColors() {
  const style = getComputedStyle(document.documentElement)
  const color = name => style.getPropertyValue('--' + name).trim()
  return {
    rise: color('chart-rise'), fall: color('chart-fall'),
    shortAverage: color('chart-short'), mediumAverage: color('chart-medium'), longAverage: color('chart-long'),
    spotPrice: color('chart-spot'), spotInk: color('chart-spot-ink'), pnl: color('chart-pnl'),
    axis: color('chart-axis'), grid: color('chart-grid'),
    text: color('chart-text'), surface: color('chart-surface'), panel: color('chart-panel'),
    border: color('chart-border'), font: color('number-font'), uiFont: color('ui-font')
  }
}
const axisNumber = new Intl.NumberFormat('en-US', { maximumFractionDigits: 8 })

function spotAxisBounds() {
  const price = Number(props.spotPrice)
  if (props.tradeFills === null || !Number.isFinite(price) || price <= 0) return { min: null, max: null }
  return {
    min: ({ min, max }) => price < min ? Math.max(0, price - (max - price) * .04) : null,
    max: ({ min, max }) => price > max ? price + (price - min) * .04 : null
  }
}

function updateChart(data, refreshPresentation = false) {
  const { dates, ohlc, volumes } = data
  const series = [
    { id: 'candles', data: ohlc },
    { data: calcMA(ohlc, 5) },
    { data: calcMA(ohlc, 10) },
    { data: calcMA(ohlc, 20) },
    {
      data: volumes,
      itemStyle: {
        color: ({ dataIndex }) => (ohlc[dataIndex][1] >= ohlc[dataIndex][0] ? chartColors.rise : chartColors.fall) + '62'
      }
    },
    ...overlaySeries(data)
  ]
  if (!lastData || refreshPresentation) {
    const option = chartLayout(dates, series)
    if (lastData) {
      const previous = chart.value.getOption()
      option.dataZoom.forEach((zoom, index) => {
        const { start, end } = previous.dataZoom[index]
        Object.assign(zoom, { start, end })
      })
      option.legend.selected = Object.fromEntries(option.legend.data.map((label, index) => [
        label, previous.legend[0].selected?.[previous.legend[0].data[index]] ?? true
      ]))
    }
    chart.value.setOption(option, { notMerge: true })
  } else {
    chart.value.setOption({
      xAxis: chartAxes().map(() => ({ data: dates })),
      series,
      ...historicalWindow(dates)
    })
  }
  lastData = data
}

function historicalWindow(dates) {
  const previousDates = lastData?.dates ?? []
  if (previousDates.length < 2 || dates.length < 2 ||
      (previousDates.length === dates.length && previousDates[0] === dates[0])) return {}
  const zoom = chart.value.getOption().dataZoom[0]
  if (zoom.end >= 100) return {}

  // Preserve the viewed candle times when the oldest candle rolls out.
  const previousLast = previousDates.length - 1
  const currentLast = dates.length - 1
  const start = dates.indexOf(previousDates[Math.round(zoom.start / 100 * previousLast)])
  const end = dates.indexOf(previousDates[Math.round(zoom.end / 100 * previousLast)])
  if (end <= Math.max(0, start)) return {}

  const window = { start: 100 * Math.max(0, start) / currentLast, end: 100 * end / currentLast }
  return { dataZoom: [{ ...window }, { ...window }] }
}

function spotPriceLine() {
  const price = Number(props.spotPrice)
  const hasSpot = Number.isFinite(price) && price > 0
  return {
    animation: false,
    silent: true,
    symbol: 'none',
    precision: 8,
    lineStyle: { color: chartColors.spotPrice, type: props.tradeFills === null ? 'dashed' : 'dotted', width: 1 },
    label: {
      position: 'insideEndTop',
      formatter: () => axisNumber.format(price),
      color: chartColors.spotInk,
      backgroundColor: chartColors.spotPrice,
      borderRadius: 4,
      padding: [4, 8],
      distance: 4,
      fontSize: 11,
      fontFamily: chartColors.font
    },
    data: hasSpot ? [{ name: tr("现价"), yAxis: price }] : []
  }
}

function chartAxes() {
  return props.tradeFills === null ? [0, 1] : [0, 1, 2]
}

function overlaySeries(data) {
  if (props.tradeFills === null) return []
  return [{
    id: 'position-pnl', name: tr("未实现盈亏"), type: 'line',
    data: tradePnlHistory(data, props.tradeFills, props.period, props.position, props.spotPrice),
    xAxisIndex: 2, yAxisIndex: 2, symbol: 'circle', symbolSize: 4,
    lineStyle: { color: chartColors.pnl, width: 1.7 },
    itemStyle: { color: chartColors.pnl },
    areaStyle: { color: chartColors.pnl + '1c' },
    tooltip: { valueFormatter: value => formatPrice(value, 4) + ' USDT' },
    markLine: {
      silent: true, symbol: 'none',
      lineStyle: { color: chartColors.axis, width: 1, type: 'dashed' },
      label: { show: false },
      data: [{ yAxis: 0 }]
    }
  }]
}

function chartLayout(dates, series) {
  const withTrades = props.tradeFills !== null
  const axes = chartAxes()
  return {
    animation: false,
    backgroundColor: 'transparent',
    textStyle: { fontFamily: chartColors.uiFont },
    tooltip: {
      trigger: 'axis',
      confine: true,
      axisPointer: {
        type: 'cross',
        crossStyle: { color: chartColors.axis, width: 0.75 },
        lineStyle: { color: chartColors.axis, width: 0.75, type: 'dashed' }
      },
      backgroundColor: chartColors.surface,
      borderColor: chartColors.border,
      borderWidth: 1,
      padding: [8, 12],
      textStyle: { fontSize: 12, color: chartColors.text },
      extraCssText: 'border-radius: 8px; box-shadow: 0 8px 24px #10110f44;'
    },
    axisPointer: { link: [{ xAxisIndex: axes }] },
    legend: {
      data: withTrades ? [tr("K线"), 'MA5', 'MA10', 'MA20', tr("未实现盈亏")] : [tr("K线"), 'MA5', 'MA10', 'MA20'],
      top: 3,
      left: 1,
      textStyle: { fontSize: 11, color: chartColors.axis },
      itemWidth: 13, itemHeight: 8, itemGap: withTrades ? 9 : 13
    },
    grid: withTrades
      ? [
          { left: 6, right: 76, top: '12%', height: '43%' },
          { left: 6, right: 76, top: '59%', height: '10%' },
          { left: 6, right: 76, top: '75%', height: '15%' }
        ]
      : [
          { left: 6, right: 76, top: 38, height: '58%' },
          { left: 6, right: 76, top: '73%', height: '13%' }
        ],
    xAxis: axes.map(gridIndex => ({
      type: 'category', data: dates, gridIndex,
      axisLabel: gridIndex === axes.at(-1)
        ? {
          fontSize: 11,
          fontFamily: chartColors.font,
          color: chartColors.axis,
          hideOverlap: true,
          showMinLabel: false,
          formatter: formatAxisTime
        }
        : { show: false },
      axisLine: { lineStyle: { color: chartColors.grid } },
      axisTick: { show: false },
      boundaryGap: true
    })),
    yAxis: [
      {
        scale: true, gridIndex: 0, position: 'right',
        ...spotAxisBounds(),
        splitLine: { lineStyle: { color: chartColors.grid } },
        axisLabel: { fontSize: 11, fontFamily: chartColors.font, color: chartColors.axis, formatter: value => axisNumber.format(value) },
        axisLine: { show: false },
        axisTick: { show: false }
      },
      {
        scale: true, gridIndex: 1,
        splitLine: { show: false },
        axisLabel: { show: false },
        axisLine: { show: false },
        axisTick: { show: false }
      },
      ...(withTrades ? [{
        gridIndex: 2, position: 'right', splitNumber: 2,
        name: tr("未实现盈亏（USDT）"), nameLocation: 'end', nameGap: 10,
        nameTextStyle: { color: chartColors.axis, align: 'right', fontSize: 11 },
        axisLabel: { color: chartColors.axis, fontSize: 11, fontFamily: chartColors.font, formatter: value => formatPrice(value, 4) },
        splitLine: { lineStyle: { color: chartColors.grid, type: 'dashed' } }
      }] : [])
    ],
    dataZoom: [
      {
        type: 'inside', xAxisIndex: axes, start: withTrades ? 0 : 50, end: 100
      },
      {
        type: 'slider', xAxisIndex: axes, start: withTrades ? 0 : 50, end: 100,
        bottom: 1, height: 16, left: 6, right: 76,
        borderColor: chartColors.border,
        backgroundColor: chartColors.surface,
        fillerColor: chartColors.axis + '20',
        handleStyle: { color: chartColors.panel, borderColor: chartColors.axis, borderWidth: 1 },
        moveHandleStyle: { color: chartColors.border },
        textStyle: { fontSize: 11, fontFamily: chartColors.font, color: chartColors.axis },
        dataBackground: { lineStyle: { color: chartColors.axis }, areaStyle: { color: chartColors.panel } },
        selectedDataBackground: { lineStyle: { color: chartColors.axis }, areaStyle: { color: chartColors.panel } }
      }
    ],
    series: [
      {
        ...series[0], name: tr("K线"), type: 'candlestick', xAxisIndex: 0, yAxisIndex: 0,
        markLine: spotPriceLine(),
        itemStyle: {
          color: chartColors.rise, color0: chartColors.fall,
          borderColor: chartColors.rise, borderColor0: chartColors.fall,
          borderWidth: 1
        },
        barWidth: '60%'
      },
      {
        ...series[1], name: 'MA5', type: 'line',
        smooth: true, lineStyle: { width: 1.2, color: chartColors.shortAverage },
        itemStyle: { color: chartColors.shortAverage },
        symbol: 'none', xAxisIndex: 0, yAxisIndex: 0
      },
      {
        ...series[2], name: 'MA10', type: 'line',
        smooth: true, lineStyle: { width: 1.2, color: chartColors.mediumAverage },
        itemStyle: { color: chartColors.mediumAverage },
        symbol: 'none', xAxisIndex: 0, yAxisIndex: 0
      },
      {
        ...series[3], name: 'MA20', type: 'line',
        smooth: true, lineStyle: { width: 1.2, color: chartColors.longAverage },
        itemStyle: { color: chartColors.longAverage },
        symbol: 'none', xAxisIndex: 0, yAxisIndex: 0
      },
      {
        ...series[4], name: tr("成交量"), type: 'bar', xAxisIndex: 1, yAxisIndex: 1,
        barWidth: '60%',
        itemStyle: {
          ...series[4].itemStyle,
          borderRadius: [1, 1, 0, 0]
        }
      },
      ...series.slice(5)
    ]
  }
}

function formatAxisTime(value) {
  const daily = props.period === '1d'
  if (props.tradeFills !== null) {
    return (daily ? tradingAxisDay.value : tradingAxisTime.value).format(Date.parse(value))
  }
  return daily ? value.slice(0, 10) : value.slice(5, 16).replace('T', ' ')
}

function calcMA(ohlc, period) {
  const result = []
  for (let i = 0; i < ohlc.length; i++) {
    if (i < period - 1) { result.push('-'); continue }
    let sum = 0
    for (let j = 0; j < period; j++) sum += ohlc[i - j][1] // close
    result.push(sum / period)
  }
  return result
}

watch(candles, data => {
  if (data) updateChart(data)
  else lastData = undefined
})

watch([theme, language], () => {
  chartColors = readColors()
  if (lastData && chart.value) updateChart(lastData, true)
}, { flush: 'post' })

watch(() => [props.spotPrice, props.tradeFills, props.position], () => {
  if (lastData && lastData === candles.value) {
    chart.value.setOption({
      yAxis: [spotAxisBounds()],
      series: [{ id: 'candles', markLine: spotPriceLine() }, ...overlaySeries(lastData)]
    })
  }
})

</script>

<style scoped>
.kline-chart { width: 100%; min-height: 324px; }
.chart { width: 100%; height: clamp(304px, 34vh, 388px); }
.has-trades { min-height: 510px; }
.has-trades .chart { height: clamp(490px, 56vh, 550px); }
.refresh-status { display: flex; justify-content: space-between; gap: 8px; margin-top: 8px; color: var(--text-3); font-size: 12px; }
.refresh-status time { font-family: var(--number-font); }
.refresh-live > span { color: var(--positive); }
.refresh-error { color: var(--negative); }
@media (max-width: 580px) {
  .kline-chart { min-height: 330px; }
  .chart { height: 305px; }
  .has-trades { min-height: 440px; }
  .has-trades .chart { height: 420px; }
}
</style>
