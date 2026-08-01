import { get } from '@/utils/request'

export function getHealth() {
  return get<string>('/v1/health')
}
