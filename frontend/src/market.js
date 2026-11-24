import axios from 'axios'
import { language, t } from './preferences.js'

export const marketStreamUrl = 'wss://data-stream.binance.vision/ws'
export const klineIntervalMs = { '15m': 900000, '1h': 3600000, '4h': 14400000, '1d': 86400000 }

let pairsRequest

export function getPairs() {
  pairsRequest ??= axios.get('/api/market/pairs').then(({ data }) => data).catch(error => {
    pairsRequest = undefined
    throw error
  })
  return pairsRequest
}

export function quote(pair, options) {
  return axios.get(`/api/market/ticker/${encodeURIComponent(pair)}`, options).then(({ data }) => data)
}

export function formatPrice(value, max = 8) {
  if (value == null || !Number.isFinite(Number(value))) return '—'
  return Number(value).toLocaleString(language.value, { maximumFractionDigits: max })
}

export function errorMessage(error, fallback) {
  return t(error.response?.data?.message || error.response?.data?.error || fallback)
}
