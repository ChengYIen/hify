export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}
