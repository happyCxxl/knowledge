import { http } from './http';
import type {
  HomeActivity,
  HomeActivityQuery,
  HomeRecentSubmit,
  HomeRecentSubmitQuery,
  HomeSummary,
} from '@/types/home';
import type { PageResult } from '@/types/response';

/**
 * 首页接口。
 *
 * <p>三个接口对应首页的三块内容：**有多少资产**、**最近提交了什么**、**管理员做了什么**。
 * 刻意没有"环节分布""待处理清单"这类聚合 —— 那些在各业务页有更准的口径。
 */

/** 资产速览：知识库 / 策略版本（四类细分）/ 索引版本 / 文档提交 */
export async function getHomeSummary(): Promise<HomeSummary> {
  const response = await http.get<HomeSummary>('/home/summary');
  return response.data;
}

/**
 * 最近提交：全库混合的文件提交记录（新→旧）。
 *
 * <p>`status` 是**提交校验结果**（PASS/FAIL），不是处理链进度。
 */
export async function getHomeRecentSubmits(
  query: HomeRecentSubmitQuery,
): Promise<PageResult<HomeRecentSubmit>> {
  const response = await http.get<PageResult<HomeRecentSubmit>>('/home/recent-submits', {
    params: query,
  });
  return response.data;
}

/**
 * 行为记录：分页查询审计动作（新→旧）。
 *
 * <p>参数收成一个对象（与 `getFileResults` 同一口径），避免调用处摊开五六个实参。
 * 条件都可省略，省略即不过滤；时间格式 `yyyy-MM-dd HH:mm:ss`。
 */
export async function getHomeActivities(
  query: HomeActivityQuery,
): Promise<PageResult<HomeActivity>> {
  const response = await http.get<PageResult<HomeActivity>>('/home/activities', {
    params: query,
  });
  return response.data;
}
