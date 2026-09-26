import { http } from './http';
import type { PageResult } from '@/types/response';
import type { FileResult, Lineage, StrategyBinding } from '@/types/pipeline';

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
