import { onActivated, onBeforeUnmount, onDeactivated, onMounted, shallowRef, watch } from 'vue'
import { marketStreamUrl, quote } from './market.js'

const streamTimeout = 6500
const refreshInterval = 5000
const reconnectInterval = 10000
const maximumQuoteAge = 15000

export function useSpotQuote(pair) {
  const ticker = shallowRef(null)
  const mode = shallowRef('connecting')
  let active = false
  let stop

  function sync() {
    stop?.()
    stop = undefined
    const code = pair.value
    if (ticker.value?.code !== code) ticker.value = null
    mode.value = ticker.value ? 'stale' : 'connecting'
    if (active && code && !document.hidden) stop = subscribe(code)
  }

  function activate() {
    if (active) return
    active = true
    document.addEventListener('visibilitychange', sync)
    sync()
  }

  function deactivate() {
    active = false
    document.removeEventListener('visibilitychange', sync)
    stop?.()
    stop = undefined
  }

  function subscribe(code) {
    const controller = new AbortController()
    let connection
    let expiry
    let reconnect
    let refresh
    let polling = false

    function update(next) {
      if (observationTime(next) >= observationTime(ticker.value)) ticker.value = next
    }

    async function poll() {
      if (controller.signal.aborted || polling || mode.value === 'live') return
      clearTimeout(refresh)
      polling = true
      try {
        const snapshot = await quote(code, { signal: controller.signal, timeout: 10000 })
        if (controller.signal.aborted || mode.value === 'live') return
        update(snapshot)
        const timestamp = observationTime(ticker.value)
        mode.value = timestamp && Date.now() - timestamp > maximumQuoteAge ? 'stale' : 'polling'
      } catch {
        if (!controller.signal.aborted && mode.value !== 'live') mode.value = 'stale'
      } finally {
        polling = false
        if (!controller.signal.aborted && mode.value !== 'live') refresh = setTimeout(poll, refreshInterval)
      }
    }

    function connect() {
      let socket
      try {
        socket = new WebSocket(`${marketStreamUrl}/${code.toLowerCase()}@ticker`)
      } catch {
        reconnect = setTimeout(connect, reconnectInterval)
        return
      }
      connection = socket

      function disconnect() {
        if (socket !== connection) return
        connection = undefined
        clearTimeout(expiry)
        socket.close()
        if (mode.value === 'live') mode.value = 'stale'
        poll()
        reconnect = setTimeout(connect, reconnectInterval)
      }

      socket.onclose = disconnect
      socket.onerror = disconnect
      socket.onmessage = ({ data }) => {
        if (socket !== connection) return
        const next = streamTicker(data, code)
        if (!next) return
        update(next)
        mode.value = 'live'
        clearTimeout(refresh)
        clearTimeout(expiry)
        expiry = setTimeout(disconnect, streamTimeout)
      }
      expiry = setTimeout(disconnect, streamTimeout)
    }

    poll()
    connect()
    return () => {
      controller.abort()
      clearTimeout(expiry)
      clearTimeout(reconnect)
      clearTimeout(refresh)
      const socket = connection
      connection = undefined
      socket?.close()
    }
  }

  watch(pair, sync)
  onMounted(activate)
  onActivated(activate)
  onDeactivated(deactivate)
  onBeforeUnmount(deactivate)

  return { ticker, mode }
}

export function observationTime(ticker) {
  return Date.parse(ticker?.observedAt) || 0
}

function streamTicker(message, code) {
  let data
  try {
    data = JSON.parse(message)
  } catch {
    return null
  }
  const time = data?.E
  const price = Number(data?.c)
  if (data?.e !== '24hrTicker' || data.s !== code || !Number.isFinite(time) ||
      Math.abs(Date.now() - time) > maximumQuoteAge || !Number.isFinite(price) || price <= 0) return null

  return {
    code,
    price: data.c,
    changePercent: data.P,
    high: data.h,
    low: data.l,
    quoteVolume: data.q,
    observedAt: new Date(time).toISOString()
  }
}
