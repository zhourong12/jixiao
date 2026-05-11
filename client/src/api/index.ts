import { logger } from '@lark-apaas/client-toolkit/logger';
import { axiosForBackend } from '@lark-apaas/client-toolkit/utils/getAxiosForBackend';
import type {
  PerformanceRecord,
  SaveDraftRequest,
  SubmitReviewRequest,
  RejectPerformanceRequest,
  SubmitReviewResponse,
  TodoItem,
  PerformanceOverview,
  HomeActionCounts,
  MenuPermissionsMeResponse,
  MenuPermissionMatrixResponse,
  UpdateMenuPermissionsBody,
  RbacRoleItem,
  CreateRbacRoleRequest,
  UpdateRbacRoleRequest,
  SendNotificationRequest,
  SendNotificationResponse,
  NotificationListItem,
  EmployeeListResponse,
  UpdateEmployeeHierarchyRequest,
  ApproveGoalRequest,
  FinalReviewRequest,
  CreatePerformanceRequest,
  CreatePerformanceResponse,
  CreateEmployeeRequest,
  UpdateEmployeeRequest,
  SelectTemplateRequest,
  CalibrationReviewRequest,
  TemplateListItem,
  PerformanceListParams,
  PerformanceListResponse,
  EvaluationPeriodItem,
  CreateEvaluationPeriodRequest,
  UpdateEvaluationPeriodRequest,
  AwardTypeItem,
  PeriodAwardItem,
  CreatePeriodAwardRequest,
  PerformanceLeaderboardResponse,
  DepartmentOption,
  EmployeeRoleOption,
} from '@shared/api.interface';


// Add more API functions here, use axios instance (`axiosForBackend`) to make requests.
//
// 使用示例：
// export async function getUserData(userId: string) {
//   try {
//     const response = await axiosForBackend({
//       url: `/api/users/${userId}`,
//       method: 'GET'
//     });
//     return response.data;
//   } catch (error) {
//     logger.error('获取用户数据失败', error);
//     throw error;
//   }
// }

// ==================== 绩效详情相关 API ====================

export async function getPerformanceDetail(id: string): Promise<PerformanceRecord> {
  try {
    const response = await axiosForBackend({
      url: `/api/performances/${id}`,
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取绩效详情失败', error);
    throw error;
  }
}

export async function getSupervisorCalibrationQueue(
  params: PerformanceListParams,
): Promise<PerformanceListResponse> {
  const query = new URLSearchParams();
  if (params.period) query.set('period', params.period);
  if (params.departmentId) query.set('departmentId', params.departmentId);
  if (params.employeeName) query.set('employeeName', params.employeeName);
  query.set('page', String(params.page));
  query.set('pageSize', String(params.pageSize));
  const response = await axiosForBackend({
    url: `/api/performances/calibration/supervisor-queue?${query.toString()}`,
    method: 'GET',
  });
  return response.data;
}

export async function savePerformanceDraft(
  id: string,
  body: SaveDraftRequest,
): Promise<{ success: boolean }> {
  try {
    const response = await axiosForBackend({
      url: `/api/performances/${id}`,
      method: 'PATCH',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('保存绩效草稿失败', error);
    throw error;
  }
}

export async function submitPerformanceReview(
  id: string,
  body: SubmitReviewRequest,
): Promise<SubmitReviewResponse> {
  try {
    const response = await axiosForBackend({
      url: `/api/performances/${id}/submit`,
      method: 'POST',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('提交绩效评分失败', error);
    throw error;
  }
}

export async function rejectPerformance(
  id: string,
  body: RejectPerformanceRequest,
): Promise<{ success: boolean }> {
  try {
    const response = await axiosForBackend({
      url: `/api/performances/${id}/reject`,
      method: 'POST',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('驳回绩效失败', error);
    throw error;
  }
}

export async function approveGoal(
  id: string,
  body: ApproveGoalRequest,
): Promise<SubmitReviewResponse> {
  try {
    const response = await axiosForBackend({
      url: `/api/performances/${id}/approve-goal`,
      method: 'POST',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('审核目标失败', error);
    throw error;
  }
}

export async function finalReview(
  id: string,
  body: FinalReviewRequest,
): Promise<SubmitReviewResponse> {
  try {
    const response = await axiosForBackend({
      url: `/api/performances/${id}/final-review`,
      method: 'POST',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('终审失败', error);
    throw error;
  }
}

export async function calibratePerformance(
  id: string,
  body: CalibrationReviewRequest,
): Promise<SubmitReviewResponse> {
  try {
    const response = await axiosForBackend({
      url: `/api/performances/${id}/calibrate`,
      method: 'POST',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('绩效校准失败', error);
    throw error;
  }
}

export async function selectTemplate(
  id: string,
  body: SelectTemplateRequest,
): Promise<SubmitReviewResponse> {
  try {
    const response = await axiosForBackend({
      url: `/api/performances/${id}/select-template`,
      method: 'POST',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('选择模板失败', error);
    throw error;
  }
}

export async function getTemplates(): Promise<TemplateListItem[]> {
  try {
    const response = await axiosForBackend({
      url: '/api/admin/templates',
      method: 'GET',
    });
    return response.data.items || [];
  } catch (error) {
    logger.error('获取模板列表失败', error);
    throw error;
  }
}

export async function createPerformance(
  body: CreatePerformanceRequest,
): Promise<CreatePerformanceResponse> {
  try {
    const response = await axiosForBackend({
      url: '/api/performances',
      method: 'POST',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('创建绩效失败', error);
    throw error;
  }
}

function homeMonthQuery(params?: { year?: number; month?: number }): string {
  if (params?.year == null || params?.month == null) return '';
  const q = new URLSearchParams();
  q.set('year', String(params.year));
  q.set('month', String(params.month));
  return `?${q.toString()}`;
}

export async function getTodos(params?: { year?: number; month?: number }): Promise<{ items: TodoItem[] }> {
  try {
    const response = await axiosForBackend({
      url: `/api/home/todos${homeMonthQuery(params)}`,
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取待办列表失败', error);
    throw error;
  }
}

export async function getOverview(params?: { year?: number; month?: number }): Promise<PerformanceOverview> {
  try {
    const response = await axiosForBackend({
      url: `/api/home/overview${homeMonthQuery(params)}`,
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取绩效概览失败', error);
    throw error;
  }
}

export async function getHomeActionCounts(
  params?: { year?: number; month?: number },
): Promise<HomeActionCounts> {
  try {
    const response = await axiosForBackend({
      url: `/api/home/action-counts${homeMonthQuery(params)}`,
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取工作台快捷数量失败', error);
    throw error;
  }
}

export async function getMenuPermissionsMe(): Promise<MenuPermissionsMeResponse> {
  try {
    const response = await axiosForBackend({
      url: '/api/menu-permissions/me',
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取菜单权限失败', error);
    throw error;
  }
}

export async function getMenuPermissionMatrix(): Promise<MenuPermissionMatrixResponse> {
  try {
    const response = await axiosForBackend({
      url: '/api/admin/menu-permissions/matrix',
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取权限矩阵失败', error);
    throw error;
  }
}

export async function updateMenuPermissionsForRole(
  body: UpdateMenuPermissionsBody,
): Promise<{ success: boolean }> {
  try {
    const response = await axiosForBackend({
      url: '/api/admin/menu-permissions',
      method: 'PUT',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('更新菜单权限失败', error);
    throw error;
  }
}

export async function listRbacRoles(): Promise<{ items: RbacRoleItem[] }> {
  try {
    const response = await axiosForBackend({
      url: '/api/admin/menu-permissions/roles',
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取角色列表失败', error);
    throw error;
  }
}

export async function createRbacRole(body: CreateRbacRoleRequest): Promise<{ success: boolean }> {
  try {
    const response = await axiosForBackend({
      url: '/api/admin/menu-permissions/roles',
      method: 'POST',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('创建角色失败', error);
    throw error;
  }
}

export async function updateRbacRole(
  roleKey: string,
  body: UpdateRbacRoleRequest,
): Promise<{ success: boolean }> {
  try {
    const response = await axiosForBackend({
      url: `/api/admin/menu-permissions/roles/${encodeURIComponent(roleKey)}`,
      method: 'PATCH',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('更新角色失败', error);
    throw error;
  }
}

export async function deleteRbacRole(roleKey: string): Promise<{ success: boolean }> {
  try {
    const response = await axiosForBackend({
      url: `/api/admin/menu-permissions/roles/${encodeURIComponent(roleKey)}`,
      method: 'DELETE',
    });
    return response.data;
  } catch (error) {
    logger.error('删除角色失败', error);
    throw error;
  }
}

// ==================== 通知管理相关 API ====================

export async function getNotifications(params: { page?: number; pageSize?: number }): Promise<{
  items: NotificationListItem[];
  total: number;
  page: number;
  pageSize: number;
}> {
  try {
    const response = await axiosForBackend({
      url: '/api/admin/notifications',
      method: 'GET',
      params,
    });
    return response.data;
  } catch (error) {
    logger.error('获取通知列表失败', error);
    throw error;
  }
}

export async function sendNotification(body: SendNotificationRequest): Promise<SendNotificationResponse> {
  try {
    const response = await axiosForBackend({
      url: '/api/admin/notifications',
      method: 'POST',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('发送通知失败', error);
    throw error;
  }
}

// ==================== 员工管理相关 API ====================

export async function getEmployees(params: { page?: number; pageSize?: number; keyword?: string }): Promise<EmployeeListResponse> {
  try {
    const response = await axiosForBackend({
      url: '/api/employees',
      method: 'GET',
      params,
    });
    return response.data;
  } catch (error) {
    logger.error('获取员工列表失败', error);
    throw error;
  }
}

export async function getEmployeeRoleOptions(): Promise<{ items: EmployeeRoleOption[] }> {
  try {
    const response = await axiosForBackend({
      url: '/api/employees/role-options',
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取员工角色选项失败', error);
    throw error;
  }
}

export async function updateEmployeeHierarchy(
  employeeId: string,
  body: UpdateEmployeeHierarchyRequest,
): Promise<{ success: boolean }> {
  try {
    const response = await axiosForBackend({
      url: `/api/employees/${employeeId}/hierarchy`,
      method: 'PUT',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('更新员工层级关系失败', error);
    throw error;
  }
}

export async function createEmployee(
  body: CreateEmployeeRequest,
): Promise<{ id: string }> {
  try {
    const response = await axiosForBackend({
      url: '/api/employees',
      method: 'POST',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('创建员工失败', error);
    throw error;
  }
}

export async function updateEmployee(
  employeeId: string,
  body: UpdateEmployeeRequest,
): Promise<{ success: boolean }> {
  try {
    const response = await axiosForBackend({
      url: `/api/employees/${employeeId}`,
      method: 'PUT',
      data: body,
    });
    return response.data;
  } catch (error) {
    logger.error('更新员工失败', error);
    throw error;
  }
}

export async function deleteEmployee(
  employeeId: string,
): Promise<{ success: boolean }> {
  try {
    const response = await axiosForBackend({
      url: `/api/employees/${employeeId}`,
      method: 'DELETE',
    });
    return response.data;
  } catch (error) {
    logger.error('删除员工失败', error);
    throw error;
  }
}

export interface SystemConfigItem {
  key: string;
  value: string;
  label: string;
  description: string;
  type: 'number' | 'string';
}

export async function getSystemConfig(): Promise<{ items: SystemConfigItem[] }> {
  try {
    const response = await axiosForBackend({
      url: '/api/admin/system-config',
      method: 'GET',
    });
    return response.data as { items: SystemConfigItem[] };
  } catch (error) {
    logger.error('获取系统配置失败', error);
    throw error;
  }
}

export async function updateSystemConfig(body: {
  configs: Array<{ key: string; value: string }>;
}): Promise<{ success: boolean }> {
  try {
    const response = await axiosForBackend({
      url: '/api/admin/system-config',
      method: 'PATCH',
      data: body,
    });
    return response.data as { success: boolean };
  } catch (error) {
    logger.error('更新系统配置失败', error);
    throw error;
  }
}

export async function getEvaluationPeriods(
  periodType?: 'month' | 'quarter',
): Promise<{ items: EvaluationPeriodItem[] }> {
  const response = await axiosForBackend({
    url: '/api/admin/evaluation-periods',
    method: 'GET',
    params: periodType ? { period_type: periodType } : {},
  });
  return response.data as { items: EvaluationPeriodItem[] };
}

export async function createEvaluationPeriod(
  body: CreateEvaluationPeriodRequest,
): Promise<EvaluationPeriodItem> {
  const response = await axiosForBackend({
    url: '/api/admin/evaluation-periods',
    method: 'POST',
    data: body,
  });
  return response.data as EvaluationPeriodItem;
}

export async function updateEvaluationPeriod(
  id: string,
  body: UpdateEvaluationPeriodRequest,
): Promise<EvaluationPeriodItem> {
  const response = await axiosForBackend({
    url: `/api/admin/evaluation-periods/${id}`,
    method: 'PUT',
    data: body,
  });
  return response.data as EvaluationPeriodItem;
}

export async function deleteEvaluationPeriod(id: string): Promise<void> {
  await axiosForBackend({
    url: `/api/admin/evaluation-periods/${id}`,
    method: 'DELETE',
  });
}

export async function getAwardTypes(): Promise<{ items: AwardTypeItem[] }> {
  const response = await axiosForBackend({
    url: '/api/admin/award-types',
    method: 'GET',
  });
  return response.data as { items: AwardTypeItem[] };
}

export async function getPerformancePeriods(): Promise<{ items: string[] }> {
  const response = await axiosForBackend({
    url: '/api/admin/evaluation/performance-periods',
    method: 'GET',
  });
  return response.data as { items: string[] };
}

export async function getEvaluationLeaderboard(params: {
  scope: 'month' | 'quarter';
  key: string;
  departmentIds?: string[];
}): Promise<PerformanceLeaderboardResponse> {
  const search = new URLSearchParams();
  search.set('scope', params.scope);
  search.set('key', params.key);
  if (params.departmentIds?.length) {
    search.set('departmentIds', params.departmentIds.join(','));
  }
  const response = await axiosForBackend({
    url: `/api/admin/evaluation/leaderboard?${search.toString()}`,
    method: 'GET',
  });
  return response.data as PerformanceLeaderboardResponse;
}

export async function getPeriodAwards(periodId: string): Promise<{ items: PeriodAwardItem[] }> {
  const response = await axiosForBackend({
    url: '/api/admin/evaluation/awards',
    method: 'GET',
    params: { periodId },
  });
  return response.data as { items: PeriodAwardItem[] };
}

export async function createPeriodAward(body: CreatePeriodAwardRequest): Promise<PeriodAwardItem> {
  const response = await axiosForBackend({
    url: '/api/admin/evaluation/awards',
    method: 'POST',
    data: body,
  });
  return response.data as PeriodAwardItem;
}

export async function deletePeriodAward(id: string): Promise<void> {
  await axiosForBackend({
    url: `/api/admin/evaluation/awards/${id}`,
    method: 'DELETE',
  });
}

export async function getDepartmentOptions(): Promise<{ items: DepartmentOption[] }> {
  const response = await axiosForBackend({
    url: '/api/employees/department-options',
    method: 'GET',
  });
  return response.data as { items: DepartmentOption[] };
}

export async function syncEmployeesFromLark(
  clearExisting: boolean,
): Promise<import('@shared/api.interface').SyncEmployeesResponse> {
  try {
    const response = await axiosForBackend({
      url: '/api/employees/sync-from-lark',
      method: 'POST',
      data: { clearExisting },
    });
    // 确保返回有效的响应数据
    const data = response.data as import('@shared/api.interface').SyncEmployeesResponse;
    if (!data || typeof data.success !== 'boolean') {
      logger.error('同步飞书返回无效数据', data);
      return {
        success: false,
        syncedCount: 0,
        message: '同步返回无效数据，请稍后重试',
      };
    }
    return data;
  } catch (error: unknown) {
    logger.error('同步飞书员工失败', error);
    // 如果是 axios 错误且包含响应数据，返回错误数据
    const axiosError = error as { response?: { data?: import('@shared/api.interface').SyncEmployeesResponse } };
    if (axiosError.response?.data) {
      return axiosError.response.data;
    }
    // 返回一个默认的错误响应，而不是抛出
    return {
      success: false,
      syncedCount: 0,
      message: error instanceof Error ? error.message : '同步失败，请检查网络或飞书配置',
    };
  }
}
