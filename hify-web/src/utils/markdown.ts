import { marked } from 'marked'

/**
 * Markdown → 安全 HTML 渲染（marked）。
 *
 * 安全约定：LLM 输出属于半可信输入（可能被 prompt 注入），因此：
 *  - 原始 HTML 一律转义成纯文本展示，阻止注入 <script>/<iframe>/事件属性
 *  - 链接只放行 http/https/mailto 协议，封堵 javascript: 等危险协议
 *  - breaks: true，让单个换行渲染为 <br>，与原先 pre-wrap 纯文本展示的换行习惯一致
 */

const ESCAPE_REPLACES: Array<[RegExp, string]> = [
  [/&/g, '&amp;'],
  [/</g, '&lt;'],
  [/>/g, '&gt;'],
  [/"/g, '&quot;'],
]

function escapeHtml(value: string): string {
  let out = value
  for (const [re, rep] of ESCAPE_REPLACES) out = out.replace(re, rep)
  return out
}

/** 允许的链接协议白名单 */
function isSafeHref(href: string): boolean {
  return /^(https?:|mailto:)/i.test(href)
}

marked.use({
  gfm: true,
  breaks: true,
  renderer: {
    // 原始 HTML 当作纯文本展示（转义），阻止 LLM 输出注入脚本
    html({ text }) {
      return escapeHtml(text)
    },
    // 链接：白名单协议 + 新窗口打开；危险协议降级为纯文本
    link({ href, title, tokens }) {
      if (!isSafeHref(href)) {
        return this.parser.parseInline(tokens)
      }
      const attrs = [`href="${escapeHtml(href)}"`, 'target="_blank"', 'rel="noopener noreferrer"']
      if (title) attrs.push(`title="${escapeHtml(title)}"`)
      return `<a ${attrs.join(' ')}>${this.parser.parseInline(tokens)}</a>`
    },
  },
})

/** 把 Markdown 渲染成安全 HTML（纯字符串，调用方用 v-html 展示） */
export function renderMarkdown(content: string): string {
  return marked.parse(content) as string
}
