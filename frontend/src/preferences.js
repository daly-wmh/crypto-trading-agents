import { ref, watch } from 'vue'
import english from './translations-en.js'

function saved(key, fallback, allowed) {
  const value = localStorage.getItem('workbench-' + key)
  return allowed.includes(value) ? value : fallback
}

export const languages = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English' }
]
export const language = ref(saved('language', 'zh-CN', languages.map(option => option.value)))
export const theme = ref(saved('theme', 'day', ['day', 'night']))
export const material = ref(saved('material', 'glass', ['glass', 'solid']))

watch(language, value => {
  document.documentElement.lang = value
  localStorage.setItem('workbench-language', value)
}, { immediate: true })

watch(theme, value => {
  document.documentElement.dataset.theme = value
  document.documentElement.classList.toggle('dark', value === 'night')
  localStorage.setItem('workbench-theme', value)
}, { immediate: true })

watch(material, value => {
  document.documentElement.dataset.material = value
  localStorage.setItem('workbench-material', value)
}, { immediate: true })

export function t(message, values = {}) {
  if (typeof message !== 'string') return message
  let label = language.value === 'en-US' && Object.hasOwn(english, message) ? english[message] : message
  if (language.value === 'en-US' && label === message && message.includes(' · ')) {
    label = message.split(' · ').map(part => t(part)).join(' · ')
  }
  if (language.value === 'en-US' && label === message) {
    const round = message.match(/^(.*) \(第(\d+)轮\)$/)
    if (round) label = t(round[1]) + ' (Round ' + round[2] + ')'
  }
  return label.replace(/\{([a-zA-Z0-9]+)\}/g, (placeholder, key) => values[key] ?? placeholder)
}
