import { ElMessage } from 'element-plus'

const DURATION = 3000

export function notifySuccess(message: string, duration = DURATION): void {
  ElMessage.success({ message, duration })
}

export function notifyError(message: string, duration = DURATION): void {
  ElMessage.error({ message, duration })
}

export function notifyWarning(message: string, duration = DURATION): void {
  ElMessage.warning({ message, duration })
}
