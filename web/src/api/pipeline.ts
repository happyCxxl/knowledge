import { http } from './http';
import type { PageResult } from '@/types/response';
import type { FileResult, Lineage, StageTriggerResult, StrategyBinding } from '@/types/pipeline';

/** 分页查询知识库下的文件结果（每行含各环节最新任务状态） */
export async function getFileResults(
  knowledgeBaseId: string,
  query: { current: number; size: number; stage?: string },
): Promise<PageResult<FileResult>> {
  const response = await http.get<PageResult<FileResult>>(
    `/knowledge-base/${knowledgeBaseId}/file-results`,
    { params: query },
  );
  return response.data;
}

/** 查询某个文件结果的执行树（全部环节节点 + 血缘边） */
export async function getLineage(fileResultId: string): Promise<Lineage> {
  const response = await http.get<Lineage>(`/file-results/${fileResultId}/lineage`);
  return response.data;
}

/** 查询知识库某类策略的当前绑定（未绑定返回仅含 strategyType 的空对象） */
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
 * 触发某个环节执行（手动逐环节）。
 *
 * <p>语义：`upstreamProductId` 指定「以此产物触发下游」，实现分叉——传哪个上游产物，
 * 就基于它产出一条新的分支。不传则由后端按知识库绑定策略 + 同策略最新成功运行解析。
 * 解析环节无上游产物，故不接受该参数。
 */
export async function addStageTrigger(
  fileResultId: string,
  stage: string,
  options: { strategyVersionId?: string | null; upstreamProductId?: string | null } = {},
): Promise<StageTriggerResult> {
  // 解析环节的接口不接受任何可选参数，多余参数会被后端忽略，这里按需要传
  const params: Record<string, string> = {};
  if (options.strategyVersionId) {
    params.strategyVersionId = options.strategyVersionId;
  }
  if (options.upstreamProductId) {
    params.upstreamProductId = options.upstreamProductId;
  }
  const path = `/file-results/${fileResultId}/${stage.toLowerCase()}`;
  const response = await http.post<StageTriggerResult>(path, null, { params });
  return response.data;
}
