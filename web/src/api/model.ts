import { http } from './http';
import type {
  ModelCallLog,
  ModelCallLogSummary,
  ModelItem,
  ModelQuery,
  ModelSavePayload,
  ModelUsageQuery,
} from '@/types/model';

/** 模型管理接口：模型目录的清单与写入、启停，以及用量明细 */

/** 查询模型清单：支持按能力 / 状态 / 关键字过滤，返回目录视图字段 */
export async function getModelList(query: ModelQuery = {}): Promise<ModelItem[]> {
  const response = await http.get<ModelItem[]>('/model/models', { params: query });
  return response.data;
}

/** 新增模型：厂商码必须在配置里，唯一键不冲突，价格与限额非负，能力与状态取值在字典内 */
export async function addModel(payload: ModelSavePayload): Promise<ModelItem> {
  const response = await http.post<ModelItem>('/model/models', payload);
  return response.data;
}

/** 编辑模型：覆盖写业务列，价格与限额清空即写空 */
export async function updateModel(id: string, payload: ModelSavePayload): Promise<ModelItem> {
  const response = await http.put<ModelItem>(`/model/models/${id}`, payload);
  return response.data;
}

/** 启停模型：只改状态列（取值 ENABLED / DISABLED） */
export async function updateModelStatus(id: string, status: string): Promise<ModelItem> {
  const response = await http.patch<ModelItem>(`/model/models/${id}/status`, { status });
  return response.data;
}

/** 查询用量与成本概览：时间范围必填（from 含 / to 不含） */
export async function getModelUsageSummary(query: ModelUsageQuery): Promise<ModelCallLogSummary> {
  const response = await http.get<ModelCallLogSummary>('/model/usage/summary', {
    params: query,
  });
  return response.data;
}

/** 查询调用明细：一次调用一行，按调用时间倒序，条数由 limit 收口（未传 500、上限 2000） */
export async function getModelUsageCalls(query: ModelUsageQuery): Promise<ModelCallLog[]> {
  const response = await http.get<ModelCallLog[]>('/model/usage/calls', { params: query });
  return response.data;
}
