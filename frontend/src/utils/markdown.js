import { marked } from 'marked'

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true,
  headerIds: true,
  mangle: false
})

export function parseMarkdown(markdown) {
  if (!markdown) return ''
  try {
    return marked.parse(markdown)
  } catch (e) {
    console.error('Markdown 解析错误:', e)
    return markdown.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>')
  }
}


