import { klineIntervalMs } from './market.js'

export function chartFills(orders) {
  return orders
    .filter(order => (!order.status || order.status === 'FILLED') && ['BUY', 'SELL'].includes(order.direction))
    .map(order => ({
      id: order.id,
      time: Date.parse(order.executedAt || order.createdAt),
      direction: order.direction,
      price: Number(order.price),
      quantity: Number(order.quantity),
      cost: Number(order.amount ?? Number(order.price) * Number(order.quantity)) + Number(order.fee || 0)
    }))
    .filter(fill => Number.isFinite(fill.time) && Number.isFinite(fill.price) && fill.price > 0 &&
      Number.isFinite(fill.quantity) && fill.quantity > 0 && Number.isFinite(fill.cost))
    .sort((a, b) => a.time - b.time || Number(a.id) - Number(b.id))
}

export function unrealizedPnl(position, price) {
  const current = Number(price)
  if (!position) return 0
  if (!Number.isFinite(current) || current <= 0) return position.unrealizedPnl
  return (current - Number(position.avgCost)) * Number(position.quantity)
}

export function tradePnlHistory({ dates, ohlc }, fills, period, position, spotPrice, now = Date.now()) {
  let nextFill = 0
  let quantity = 0
  let avgCost = 0
  return dates.map((date, index) => {
    const start = Date.parse(date)
    const end = start + klineIntervalMs[period]
    while (nextFill < fills.length && fills[nextFill].time < end) {
      const fill = fills[nextFill++]
      if (fill.direction === 'BUY') {
        const newQuantity = roundToScale(quantity + fill.quantity)
        avgCost = roundToScale((quantity * avgCost + fill.cost) / newQuantity)
        quantity = newQuantity
      } else {
        quantity = Math.max(0, roundToScale(quantity - fill.quantity))
        if (!quantity) avgCost = 0
      }
    }

    const current = index === dates.length - 1 && now >= start && now < end
    if (current && position) return unrealizedPnl(position, spotPrice ?? ohlc[index][1])
    if (!nextFill) return null
    if (current && !position) return 0
    return quantity * (Number(ohlc[index][1]) - avgCost)
  })
}

function roundToScale(value) {
  return Number(value.toFixed(8))
}
