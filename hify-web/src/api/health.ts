import { get } from '@/utils/request'

export const getHealth = () => {
  return get<string>('/v1/health')
}
