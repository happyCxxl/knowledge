import { http } from './http';
import type { StrategyVersion } from '@/types/strategy';

/** 查询策略版本列表；type 按环节过滤（PREPROCESS/CHUNK/EMBED） */
export async function getStrategyVersions(
  type: string,
  includeInactive = false,
): Promise<StrategyVersion[]> {
  const response = await http.get<StrategyVersion[]>('/strategy-versions', {
    params: { type, includeInactive },
  });
  return response.data;
}
