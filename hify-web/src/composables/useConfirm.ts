import { ElMessageBox, ElMessage } from 'element-plus'

export interface ConfirmOptions {
  /** 确认提示文案 */
  message?: string
  /** 弹窗标题 */
  title?: string
  /** 确认按钮文字 */
  confirmText?: string
  /** 取消按钮文字 */
  cancelText?: string
}

/**
 * 删除确认 composable。
 * 一行代码完成「确认删除 → 调接口 → 提示成功」全流程。
 *
 * @example
 * const { confirmDelete } = useConfirm()
 * await confirmDelete('确定删除该 Agent？', () => agentApi.deleteAgent(id))
 */
export function useConfirm() {
  async function confirmDelete(
    messageOrApi: string | (() => Promise<unknown>),
    apiFn?: () => Promise<unknown>,
    options?: ConfirmOptions,
  ): Promise<boolean> {
    // 支持两种调用方式：confirmDelete('文案', apiFn) 或 confirmDelete(apiFn, options?)
    let message: string
    let api: () => Promise<unknown>
    let opts: ConfirmOptions

    if (typeof messageOrApi === 'string') {
      message = messageOrApi
      api = apiFn!
      opts = options || {}
    } else {
      message = '此操作将永久删除该数据，是否继续？'
      api = messageOrApi
      opts = (apiFn as ConfirmOptions) || {}
    }

    const {
      title = '删除确认',
      confirmText = '确认删除',
      cancelText = '取消',
    } = opts

    try {
      await ElMessageBox.confirm(message, title, {
        confirmButtonText: confirmText,
        cancelButtonText: cancelText,
        type: 'warning',
      })
      await api()
      ElMessage.success('删除成功')
      return true
    } catch (e: unknown) {
      // 用户点击取消或关闭弹窗 — ElMessageBox 会 reject 'cancel' 或 'close'
      if (e === 'cancel' || e === 'close') {
        return false
      }
      // API 调用失败 — 错误已在 request 拦截器中提示，这里不再重复
      return false
    }
  }

  return { confirmDelete }
}
