import { http } from './http';
import type { PageResult } from '@/types/response';
import type { FileSubmitResult, SubmitLog, SubmitLogQuery } from '@/types/file';

/** 上传文件到文件中心，返回 fileId（建档时用） */
export async function addFile(file: File, onProgress?: (percent: number) => void): Promise<string> {
  const form = new FormData();
  // 后端取的是 @RequestParam("file")，字段名必须是 file
  form.append('file', file);
  const response = await http.post<string>('/files', form, {
    onUploadProgress: (event) => {
      if (onProgress && event.total) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    },
  });
  return response.data;
}

/**
 * 提交文件引用到知识库：建档三写（源文件 / 文件结果 / 提交日志）。
 *
 * <p>只建档，不创建处理任务——解析由环节页手动触发（手动逐环节口径）。
 *
 * @param requestId 幂等键，同一文件的重试要沿用同一个值，避免重复建档
 */
export async function addFileSubmit(
  knowledgeBaseId: string,
  fileId: string,
  requestId: string,
): Promise<FileSubmitResult> {
  const response = await http.post<FileSubmitResult>(`/knowledge-base/${knowledgeBaseId}/submit`, {
    fileId,
    requestId,
  });
  return response.data;
}

/** 分页查询知识库的提交记录（含失败原因） */
export async function getSubmitLogs(
  knowledgeBaseId: string,
  query: SubmitLogQuery,
): Promise<PageResult<SubmitLog>> {
  const response = await http.get<PageResult<SubmitLog>>(
    `/knowledge-base/${knowledgeBaseId}/submit-logs`,
    { params: query },
  );
  return response.data;
}
