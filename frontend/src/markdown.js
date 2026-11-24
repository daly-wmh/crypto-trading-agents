import DOMPurify from 'dompurify'
import { marked } from 'marked'

export function safeMarkdown(value) {
  return value ? DOMPurify.sanitize(marked.parse(value), { USE_PROFILES: { html: true }, FORBID_TAGS: ['img'] }) : ''
}
