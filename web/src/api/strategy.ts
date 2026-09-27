import { http } from './http';
import type { StrategyBinding } from '@/types/pipeline';
import type { StrategyVersion } from '@/types/strategy';

/**
 * 策略版本接口。
 *
 * <p>注意后端语义（`StrategyVersionServiceImpl`）：
 *
 * <ul>
 *   <li>**策略版本行不可修改**：`updateStrategyVersion` 的语义是**复制新行**，
 *       旧行原样保留，返回的是**新行**；</li>
 *   <li>新版本默认 ACTIVE；</li>
 *   <li>撞唯一键（同类型 + 同名 + 同版本）→ 后端报 `40001`「同环节同名版本已存在」；</li>
 *   <li>删除被知识库绑定的版本 → 后端报 `40452`，应引导用户改用停用。</li>
 * </ul>
 */

/** 查询策略版本列表；type 按环节过滤（PREPROCESS/CHUNK/EMBED/RETRIEVAL） */
export async function getStrategyVersions(
  type: string,
  includeInactive = false,
): Promise<StrategyVersion[]> {
  const response = await http.get<StrategyVersion[]>('/strategy-versions', {
    params: { type, includeInactive },
  });
  return response.data;
}

/** 创建策略版本的入参 */
export interface StrategyVersionDraft {
  type: string;
  name: string;
  version: string;
  configSnapshot: string;
}

/**
 * 创建策略版本，返回新建的行。
 *
 * <p>类型只在创建时确定，复制时沿用旧行的类型（后端不允许改类型）。
 */
export async function addStrategyVersion(draft: StrategyVersionDraft): Promise<StrategyVersion> {
  const response = await http.post<StrategyVersion>('/strategy-versions', draft);
  return response.data;
}

/**
 * 复制为新版本（后端把「编辑」实现为复制新行）。
 *
 * @param id 要复制的源版本 ID
 * @param draft name/version/configSnapshot；type 不由前端传，后端沿用源行的类型
 * @returns 新建的版本行
 */
export async function updateStrategyVersion(
  id: string,
  draft: Omit<StrategyVersionDraft, 'type'>,
): Promise<StrategyVersion> {
  const response = await http.put<StrategyVersion>(`/strategy-versions/${id}`, draft);
  return response.data;
}

/** 启用策略版本（幂等） */
export async function updateStrategyVersionEnable(id: string): Promise<StrategyVersion> {
  const response = await http.post<StrategyVersion>(`/strategy-versions/${id}/enable`);
  return response.data;
}

/** 停用策略版本（幂等） */
export async function updateStrategyVersionDisable(id: string): Promise<StrategyVersion> {
  const response = await http.post<StrategyVersion>(`/strategy-versions/${id}/disable`);
  return response.data;
}

/**
 * 删除策略版本（物理删）。
 *
 * <p>被知识库绑定时后端报 `40452`，调用方应捕获并提示改用停用。
 */
export async function deleteStrategyVersion(id: string): Promise<boolean> {
  const response = await http.delete<boolean>(`/strategy-versions/${id}`);
  return response.data;
}

/**
 * 查询某知识库在某类型的策略绑定；未绑定时只有 strategyType、其余字段为 null。
 *
 * <p>用 GET 而不是列表接口：策略管理页要展示的是「这个版本被谁绑了」，
 * 而绑定是知识库维度的，只能按知识库逐个查。
 */
export async function getStrategyBinding(
  knowledgeBaseId: string,
  strategyType: string,
): Promise<StrategyBinding> {
  const response = await http.get<StrategyBinding>(
    `/knowledge-base/${knowledgeBaseId}/strategy-binding`,
    { params: { strategyType } },
  );
  return response.data;
}

/**
 * 绑定 / 解绑知识库策略。
 *
 * @param strategyVersionId 传 null 表示解绑
 */
export async function updateStrategyBinding(
  knowledgeBaseId: string,
  strategyType: string,
  strategyVersionId: string | null,
): Promise<boolean> {
  const response = await http.put<boolean>(`/knowledge-base/${knowledgeBaseId}/strategy-binding`, {
    strategyType,
    strategyVersionId,
  });
  return response.data;
}
