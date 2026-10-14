import { http } from './http';
import type {
  ModelCallLog,
  ModelCallLogSummary,
  ModelItem,
  ModelProbeResult,
  ModelQuery,
  ModelSavePayload,
  ModelUsageQuery,
  ModelVendor,
} from '@/types/model';

/** 模型管理接口（仅管理员）：模型目录、厂商只读清单、连通性测试与用量明细 */

/** 查询模型清单：支持按能力 / 状态 / 关键字过滤，返回目录视图字段 */
export async function getModelList(query: ModelQuery = {}): Promise<ModelItem[]> {
  const response = await http.get<ModelItem[]>('/admin/model/models', { params: query });
  return response.data;
}

/** 新增模型：厂商码必须在配置里，唯一键不冲突，价格与限额非负，能力与状态取值在字典内 */
export async function addModel(payload: ModelSavePayload): Promise<ModelItem> {
  const response = await http.post<ModelItem>('/admin/model/models', payload);
  return response.data;
}

/** 编辑模型：覆盖写业务列，价格与限额清空即写空 */
export async function updateModel(id: string, payload: ModelSavePayload): Promise<ModelItem> {
  const response = await http.put<ModelItem>(`/admin/model/models/${id}`, payload);
  return response.data;
}

/** 启停模型：只改状态列（取值 ENABLED / DISABLED） */
export async function updateModelStatus(id: string, status: string): Promise<ModelItem> {
  const response = await http.patch<ModelItem>(`/admin/model/models/${id}/status`, { status });
  return response.data;
}

/**
 * 连通性测试：按模型声明的能力发一次最小真实请求。
 *
 * <p>成功与失败都由后端写回结论两列，失败原因在返回体的 `errorCode` / `message` 里，HTTP 语义仍是成功。
 */
export async function updateModelProbe(id: string): Promise<ModelProbeResult> {
  const response = await http.post<ModelProbeResult>(`/admin/model/models/${id}/probe`);
  return response.data;
}

/** 查询厂商清单：只读，选项来自配置；只下发「是否已配密钥」，密钥内容不下发 */
export async function getModelVendorList(): Promise<ModelVendor[]> {
  const response = await http.get<ModelVendor[]>('/admin/model/vendors');
  return response.data;
}

/** 查询用量与成本概览：时间范围必填（from 含 / to 不含） */
export async function getModelUsageSummary(query: ModelUsageQuery): Promise<ModelCallLogSummary> {
  const response = await http.get<ModelCallLogSummary>('/admin/model/usage/summary', {
    params: query,
  });
  return response.data;
}

/** 查询调用明细：一次调用一行，按调用时间倒序，条数由 limit 收口（未传 500、上限 2000） */
export async function getModelUsageCalls(query: ModelUsageQuery): Promise<ModelCallLog[]> {
  const response = await http.get<ModelCallLog[]>('/admin/model/usage/calls', { params: query });
  return response.data;
}
