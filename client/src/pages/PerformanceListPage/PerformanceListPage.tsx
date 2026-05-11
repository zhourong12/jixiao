import { useState, useEffect, useCallback } from 'react';
import { Table, TableProps } from '@lark-apaas/client-toolkit/antd-table';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { UserDisplay } from '@/components/business-ui/user-display';
import { DepartmentSelect } from '@/components/business-ui/department-select';
import type { DepartmentValue as Department } from '@/components/business-ui/department-select';
import { axiosForBackend } from '@lark-apaas/client-toolkit/utils/getAxiosForBackend';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { Download, Search, Eye, Plus } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { PerformanceExportItem, PerformanceListItem, PerformanceStatus } from '@shared/api.interface';
import { toast } from 'sonner';
import CreatePerformanceDialog from './CreatePerformanceDialog';

function escapeCsvCell(v: string): string {
  const s = String(v ?? '');
  if (/[",\r\n]/.test(s)) return `"${s.replace(/"/g, '""')}"`;
  return s;
}

function downloadPerformanceCsv(items: PerformanceExportItem[]) {
  const headers = [
    '员工姓名',
    '部门',
    '周期',
    '状态',
    '总分',
    '自评备注',
    '上级评分备注',
    '虚线上级评分备注',
    '更新时间',
  ];
  const rows = items.map((r) =>
    [
      r.employeeName,
      r.department,
      r.period,
      r.status,
      String(r.totalScore ?? ''),
      r.selfReviewComment,
      r.managerReviewComment,
      r.dottedManagerReviewComment,
      r.updatedAt,
    ]
      .map(escapeCsvCell)
      .join(','),
  );
  const BOM = '\uFEFF';
  const body = BOM + [headers.join(','), ...rows].join('\r\n');
  const blob = new Blob([body], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `绩效导出_${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

const statusConfig: Record<PerformanceStatus, { label: string; variant: 'default' | 'secondary' | 'destructive' }> = {
  template_selection: { label: '待选择模板', variant: 'default' },
  goal_setting: { label: '目标设定中', variant: 'default' },
  goal_pending_review: { label: '待审核目标', variant: 'default' },
  goal_rejected: { label: '目标被驳回', variant: 'destructive' },
  self_review: { label: '自评中', variant: 'default' },
  manager_review: { label: '上级评分中', variant: 'default' },
  dual_manager_review: { label: '上级并行评分中', variant: 'default' },
  dotted_manager_review: { label: '虚线上级评分中', variant: 'default' },
  final_review: { label: '待校准', variant: 'default' },
  completed: { label: '已完成', variant: 'secondary' },
};

const periodOptions = [
  '2024-Q1',
  '2024-Q2',
  '2024-Q3',
  '2024-Q4',
  '2025-Q1',
  '2025-Q2',
  '2025-Q3',
  '2025-Q4',
  '2026-Q1',
  '2026-Q2',
  '2026-Q3',
  '2026-Q4',
];

interface FetchParams {
  status?: string;
  focus?: string;
  period?: string;
  departmentId?: string;
  employeeName?: string;
  page: number;
  pageSize: number;
}

const PerformanceListPage = () => {
  const navigate = useNavigate();
  const [canExport, setCanExport] = useState(false);
  const [canBatchCreate, setCanBatchCreate] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const focusParam =
    searchParams.get('focus') === 'need_score' || searchParams.get('focus') === 'need_approve_goal'
      ? (searchParams.get('focus') as string)
      : '';
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [data, setData] = useState<PerformanceListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const [statusFilter, setStatusFilter] = useState<string>('');
  const [periodFilter, setPeriodFilter] = useState<string>('');
  const [departmentFilter, setDepartmentFilter] = useState<Department | null>(null);
  const [employeeNameFilter, setEmployeeNameFilter] = useState('');

  const handleDepartmentChange = (value: Department | Department[]) => {
    setDepartmentFilter(Array.isArray(value) ? value[0] ?? null : value);
  };

  const [createDialogOpen, setCreateDialogOpen] = useState(false);

  const fetchData = useCallback(async (params: FetchParams) => {
    setLoading(true);
    try {
      const query = new URLSearchParams();
      if (params.focus) query.set('focus', params.focus);
      else if (params.status) query.set('status', params.status);
      if (params.period) query.set('period', params.period);
      if (params.departmentId) query.set('departmentId', params.departmentId);
      if (params.employeeName) query.set('employeeName', params.employeeName);
      query.set('page', String(params.page));
      query.set('pageSize', String(params.pageSize));

      const response = await axiosForBackend({
        url: `/api/performances?${query.toString()}`,
        method: 'GET',
      });
      const result = response.data as {
        items: PerformanceListItem[];
        total: number;
        page: number;
        pageSize: number;
        userRole?: string;
        canBatchCreate?: boolean;
        canExport?: boolean;
      };
      setData(result.items);
      setTotal(result.total);
      setPage(result.page);
      setCanBatchCreate(Boolean(result.canBatchCreate));
      setCanExport(Boolean(result.canExport));
    } catch (error) {
      logger.error('获取绩效列表失败', error);
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData({
      focus: focusParam || undefined,
      status: focusParam ? undefined : statusFilter || undefined,
      period: periodFilter || undefined,
      departmentId: departmentFilter?.raw?.departmentID,
      employeeName: employeeNameFilter || undefined,
      page,
      pageSize,
    });
  }, [fetchData, statusFilter, periodFilter, departmentFilter, employeeNameFilter, page, focusParam]);

  const clearFocusInUrl = () => {
    setSearchParams((prev) => {
      const n = new URLSearchParams(prev);
      n.delete('focus');
      return n;
    });
  };

  const handleSearch = () => {
    setPage(1);
    fetchData({
      focus: focusParam || undefined,
      status: focusParam ? undefined : statusFilter || undefined,
      period: periodFilter || undefined,
      departmentId: departmentFilter?.raw?.departmentID,
      employeeName: employeeNameFilter || undefined,
      page: 1,
      pageSize,
    });
  };

  const handleReset = () => {
    setStatusFilter('');
    setPeriodFilter('');
    setDepartmentFilter(null);
    setEmployeeNameFilter('');
    setPage(1);
    clearFocusInUrl();
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const query = new URLSearchParams();
      if (focusParam) query.set('focus', focusParam);
      else if (statusFilter) query.set('status', statusFilter);
      if (periodFilter) query.set('period', periodFilter);
      if (departmentFilter?.raw?.departmentID) query.set('departmentId', departmentFilter.raw.departmentID);
      if (employeeNameFilter) query.set('employeeName', employeeNameFilter);

      const response = await axiosForBackend({
        url: `/api/performances/export?${query.toString()}`,
        method: 'GET',
      });
      const result = response.data as { items: PerformanceExportItem[] };
      const items = result.items ?? [];
      if (items.length === 0) {
        toast.message('暂无数据可导出', { description: '请调整筛选条件后重试' });
        return;
      }
      downloadPerformanceCsv(items);
      toast.success('导出成功', { description: `已下载 ${items.length} 条记录（与当前列表筛选一致）` });
    } catch (error) {
      logger.error('导出绩效数据失败', error);
      toast.error('导出失败', { description: '仅超级管理员可导出绩效数据' });
    } finally {
      setExporting(false);
    }
  };

  const columns: TableProps<PerformanceListItem>['columns'] = [
    {
      title: '员工姓名',
      dataIndex: 'employeeId',
      fixed: 'left',
      width: 160,
      render: (_: string, record: PerformanceListItem) => (
        <UserDisplay
          value={{
            user_id: record.employeeId,
            name: record.employeeName || record.employeeId,
          }}
          size="small"
        />
      ),
    },
    {
      title: '周期',
      dataIndex: 'period',
      width: 120,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 140,
      render: (status: PerformanceStatus) => {
        const config = statusConfig[status];
        return config ? (
          <Badge variant={config.variant}>{config.label}</Badge>
        ) : (
          <Badge variant="outline">{status}</Badge>
        );
      },
    },
    {
      title: '上级',
      dataIndex: 'managerId',
      width: 160,
      render: (_: string, record: PerformanceListItem) => (
        <UserDisplay
          value={{
            user_id: record.managerId,
            name: record.managerName || record.managerId,
          }}
          size="small"
        />
      ),
    },
    {
      title: '总分',
      dataIndex: 'totalScore',
      width: 100,
      render: (score: number | undefined) => (
        <span className="font-semibold text-foreground">
          {score != null ? score.toFixed(1) : '-'}
        </span>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 180,
      render: (val: string) => {
        if (!val) return '-';
        const d = new Date(val);
        return d.toLocaleString('zh-CN', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
        });
      },
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 200,
      render: (_: unknown, record: PerformanceListItem) => (
        <div className="flex flex-wrap items-center gap-1">
          {record.status === 'template_selection' && (
            <Button
              variant="default"
              size="sm"
              onClick={() => navigate(`/performances/${record.id}`)}
            >
              选择模板
            </Button>
          )}
          <Button
            variant="ghost"
            size="sm"
            className="text-primary"
            onClick={() => navigate(`/performances/${record.id}`)}
          >
            <Eye className="mr-1 size-4" />
            详情
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-foreground">绩效列表</h1>
        {(canBatchCreate || canExport) && (
          <div className="flex gap-2">
            {canBatchCreate && (
              <Button onClick={() => setCreateDialogOpen(true)} data-ai-section-type="button">
                <Plus className="mr-2 size-4" />
                创建绩效
              </Button>
            )}
            {canExport && (
              <Button onClick={handleExport} disabled={exporting} variant="outline" data-ai-section-type="button">
                <Download className="mr-2 size-4" />
                {exporting ? '导出中...' : '导出数据'}
              </Button>
            )}
          </div>
        )}
      </div>

      <div className="rounded-md border border-border bg-card p-4 shadow-sm">
        {focusParam === 'need_score' && (
          <p className="mb-3 rounded-md bg-accent px-3 py-2 text-sm text-foreground">
            当前快捷筛选：<span className="font-medium">需我评分</span>（直属或虚线上级评分中）
          </p>
        )}
        {focusParam === 'need_approve_goal' && (
          <p className="mb-3 rounded-md bg-accent px-3 py-2 text-sm text-foreground">
            当前快捷筛选：<span className="font-medium">需我审核目标</span>
          </p>
        )}
        <div className="flex flex-wrap items-end gap-4">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-foreground">状态</label>
            <Select
              value={statusFilter}
              onValueChange={(v) => {
                setStatusFilter(v);
                clearFocusInUrl();
              }}
            >
              <SelectTrigger className="w-[160px]">
                <SelectValue placeholder="全部状态" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">全部状态</SelectItem>
                {Object.entries(statusConfig).map(([key, cfg]) => (
                  <SelectItem key={key} value={key}>{cfg.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-foreground">周期</label>
            <Select value={periodFilter} onValueChange={setPeriodFilter}>
              <SelectTrigger className="w-[140px]">
                <SelectValue placeholder="全部周期" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">全部周期</SelectItem>
                {periodOptions.map((p) => (
                  <SelectItem key={p} value={p}>{p}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-foreground">部门</label>
            <DepartmentSelect
              value={departmentFilter}
              onChange={handleDepartmentChange}
              placeholder="全部部门"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-foreground">员工姓名</label>
            <Input
              placeholder="请输入员工姓名"
              value={employeeNameFilter}
              onChange={(e) => setEmployeeNameFilter(e.target.value)}
              className="w-[180px]"
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSearch();
              }}
            />
          </div>

          <div className="flex gap-2">
            <Button onClick={handleSearch} data-ai-section-type="button">
              <Search className="mr-1 size-4" />
              查询
            </Button>
            <Button variant="outline" onClick={handleReset}>
              重置
            </Button>
          </div>
        </div>
      </div>

      <div className="rounded-md border border-border bg-card shadow-sm">
        <Table
          columns={columns}
          dataSource={data}
          loading={loading}
          rowKey="id"
          scroll={{ x: 1000, y: 500 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: false,
            showTotal: (t: number) => `共 ${t} 条`,
            onChange: (newPage: number) => setPage(newPage),
          }}
        />
      </div>

      <CreatePerformanceDialog
        open={createDialogOpen}
        onOpenChange={setCreateDialogOpen}
        onSuccess={() => {
          fetchData({
            focus: focusParam || undefined,
            status: focusParam ? undefined : statusFilter || undefined,
            period: periodFilter || undefined,
            departmentId: departmentFilter?.raw?.departmentID,
            employeeName: employeeNameFilter || undefined,
            page,
            pageSize,
          });
        }}
      />
    </div>
  );
};

export default PerformanceListPage;
