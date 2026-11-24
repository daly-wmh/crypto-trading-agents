import { onActivated, onBeforeUnmount, onDeactivated, onMounted, shallowRef, watch } from 'vue'
import axios from 'axios'
import { errorMessage, klineIntervalMs, marketStreamUrl } from './market.js'

const pollInterval = 5000
const reconnectInterval = 10000
const streamTimeout = 9000

export function useKlineFeed(props) {
  const candles = shallowRef(null)
  const lastUpdated = shallowRef(null)
  const error = shallowRef('')
  const live = shallowRef(false)
  let active = false
  let session

  function sync() {
    session?.stop()
    session = undefined
    live.value = false
    if (active && !document.hidden && props.stockCode) {
      session = subscribe(props.stockCode, props.period, props.days)
    }
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
    sync()
  }

  function publish(data, time) {
    candles.value = data
    lastUpdated.value = new Date(time)
    error.value = ''
  }

  function subscribe(code, period, count) {
    const controller = new AbortController()
    const buffered = new Map()
    const interval = klineIntervalMs[period]
    let socket
    let streamTimer
    let pollTimer
    let reconnectTimer
    let loading = false
    let needsHistory = true
    let lastClosed = 0

    function cancelPolling() {
      clearTimeout(pollTimer)
      pollTimer = undefined
    }

    function schedulePoll() {
      if (!controller.signal.aborted && !live.value && !pollTimer) {
        pollTimer = setTimeout(load, pollInterval)
      }
    }

    async function load() {
      cancelPolling()
      if (loading || controller.signal.aborted || live.value) return
      loading = true
      if (!candles.value) error.value = ''
      try {
        const { data } = await axios.get('/api/kline/' + encodeURIComponent(code), {
          params: { days: count, period },
          signal: controller.signal,
          timeout: 15000
        })
        if (controller.signal.aborted) return

        const snapshot = normalizeSnapshot(data)
        let history = snapshot
        const previous = candles.value
        if (previous) {
          for (let index = Math.max(previous.dates.length - 2, 0); index < previous.dates.length; index++) {
            const date = previous.dates[index]
            const snapshotIndex = history.dates.lastIndexOf(date)
            const volume = previous.volumes[index]
            if (snapshotIndex < 0 || volume > history.volumes[snapshotIndex]) {
              history = mergeFrame(history, {
                open: Date.parse(date), prices: previous.ohlc[index], volume
              }, interval, count) ?? history
            }
          }
        }

        const updates = [...buffered.values()].sort((a, b) => a.open - b.open)
        for (const frame of updates) {
          const merged = mergeFrame(history, frame, interval, count)
          if (!merged) break
          history = merged
          if (frame.closed) lastClosed = Math.max(lastClosed, frame.open)
        }
        const latest = updates.at(-1)
        // On reconnect the snapshot must cover the current bar so intervening bars are complete.
        needsHistory = Boolean(latest && latest.open > lastOpen(snapshot))
        live.value = Boolean(socket && latest && !needsHistory)
        if (!needsHistory) buffered.clear()
        publish(history, live.value ? latest.eventTime : Date.now())
      } catch (failure) {
        if (!controller.signal.aborted) error.value = errorMessage(failure, 'K 线暂不可用')
      } finally {
        loading = false
        schedulePoll()
      }
    }

    function connect() {
      let connection
      try {
        connection = new WebSocket(`${marketStreamUrl}/${code.toLowerCase()}@kline_${period}`)
      } catch {
        reconnectTimer = setTimeout(connect, reconnectInterval)
        return
      }
      socket = connection
      needsHistory = true
      let latestEvent = 0

      function disconnect() {
        if (socket !== connection) return
        socket = undefined
        clearTimeout(streamTimer)
        connection.close()
        buffered.clear()
        lastClosed = 0
        const wasLive = live.value
        live.value = false
        needsHistory = true
        if (wasLive) load()
        else schedulePoll()
        reconnectTimer = setTimeout(connect, reconnectInterval)
      }

      connection.onopen = () => {
        if (socket === connection && needsHistory && !loading) load()
      }
      connection.onclose = disconnect
      connection.onerror = disconnect
      connection.onmessage = ({ data }) => {
        if (socket !== connection) return
        const frame = readFrame(data, code, period)
        if (!frame || frame.eventTime < latestEvent) return
        latestEvent = frame.eventTime
        clearTimeout(streamTimer)
        streamTimer = setTimeout(disconnect, streamTimeout)

        const history = candles.value
        if (history && !needsHistory) {
          const tail = lastOpen(history)
          // If a new bar arrived without the old bar's close, recover the final OHLC from history.
          const missingClose = frame.open > tail && lastClosed !== tail
          const merged = missingClose ? null : mergeFrame(history, frame, interval, count)
          if (merged) {
            if (frame.closed) lastClosed = Math.max(lastClosed, frame.open)
            live.value = true
            cancelPolling()
            publish(merged, frame.eventTime)
            return
          }
        }
        needsHistory = true
        live.value = false
        buffered.set(frame.open, frame)
        if (buffered.size > count) buffered.delete(buffered.keys().next().value)
        if (!loading && !pollTimer) load()
      }
      streamTimer = setTimeout(disconnect, streamTimeout)
    }

    load()
    connect()

    return {
      load,
      stop() {
        controller.abort()
        cancelPolling()
        clearTimeout(streamTimer)
        clearTimeout(reconnectTimer)
        const connection = socket
        socket = undefined
        connection?.close()
      }
    }
  }

  watch(() => [props.stockCode, props.period, props.days], () => {
    candles.value = null
    lastUpdated.value = null
    error.value = ''
    sync()
  })
  onMounted(activate)
  onActivated(activate)
  onDeactivated(deactivate)
  onBeforeUnmount(deactivate)

  return { candles, lastUpdated, live, error, retry: () => session?.load() }
}

function readFrame(message, code, period) {
  let event
  try {
    event = JSON.parse(message)
  } catch {
    return null
  }
  const bar = event?.k
  if (event?.e !== 'kline' || event.s !== code || bar?.i !== period ||
      !Number.isFinite(event.E) || Math.abs(Date.now() - event.E) > 30000 ||
      !Number.isFinite(bar.t)) return null
  const prices = [bar.o, bar.c, bar.l, bar.h].map(Number)
  const volume = Number(bar.v)
  if (!prices.every(price => Number.isFinite(price) && price > 0) ||
      !Number.isFinite(volume) || volume < 0) return null
  return { open: bar.t, prices, volume, closed: bar.x === true, eventTime: event.E }
}

function normalizeSnapshot({ dates, ohlc, volumes }) {
  if (!dates?.length || ohlc?.length !== dates.length || volumes?.length !== dates.length) {
    throw new Error('K 线数据暂不可用')
  }
  return { dates: dates.map(date => new Date(date).toISOString()), ohlc, volumes }
}

function lastOpen(history) {
  return Date.parse(history.dates.at(-1))
}

function mergeFrame(history, frame, interval, count) {
  const tail = lastOpen(history)
  if (frame.open > tail + interval) return null
  const date = new Date(frame.open).toISOString()
  const index = history.dates.lastIndexOf(date)
  if (index < 0) {
    if (frame.open <= tail) return history
    return {
      dates: [...history.dates, date].slice(-count),
      ohlc: [...history.ohlc, frame.prices].slice(-count),
      volumes: [...history.volumes, frame.volume].slice(-count)
    }
  }
  // A cached snapshot or older stream frame must not undo more recent trading within the same bar.
  if (frame.volume < history.volumes[index]) return history
  const ohlc = [...history.ohlc]
  const volumes = [...history.volumes]
  ohlc[index] = frame.prices
  volumes[index] = frame.volume
  return { dates: history.dates, ohlc, volumes }
}
