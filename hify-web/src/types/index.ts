export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页响应体 —— code=0，data 为当前页列表（见后端 PageResult） */
export interface PageResult<T = unknown> {
  code: number
  message: string
  data: T[]
  total: number
  page: number
  size: number
}
