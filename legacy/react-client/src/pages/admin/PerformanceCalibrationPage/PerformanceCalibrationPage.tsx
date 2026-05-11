import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
import { getSupervisorCalibrationQueue } from '@client/src/api';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { Search, Eye } from 'lucide-react';
import type { PerformanceListItem, PerformanceStatus } from '@shared/api.interface';
import { useMenuPermissions } from '@/hooks/useMenuPermissions';

const statusLabels: Record<string, string> = {
  manager_review: '上级评分中',
  dual_manager_review: '上级并行评分中',
  dotted_manager_review: '虚线上级评分中',
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

const PerformanceCalibrationPage = () => {
  const navigate = useNavigate();
  const { allow, role, loading: permLoading } = useMenuPermissions();
  const allowed = allow('admin_performance_calibration') && role === 'super_admin';

  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<PerformanceListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const [periodFilter, setPeriodFilter] = useState<string>('');
  const [departmentFilter, setDepartmentFilter] = useState<Department | null>(null);
  const [employeeNameFilter, setEmployeeNameFilter] = useState('');

  const handleDepartmentChange = (value: Department | Department[]) => {
    setDepartmentFilter(Array.isArray(value) ? value[0] ?? null : value);
  };

  const fetchData = useCallback(async () => {
    if (!allowed) return;
    setLoading(true);
    try {
      const result = await getSupervisorCalibrationQueue({
        period: periodFilter || undefined,
        departmentId: departmentFilter?.raw?.departmentID,
        employeeName: employeeNameFilter || undefined,
        page,
        pageSize,
      });
      setData(result.items);
      setTotal(result.total);
    } catch (e) {
      logger.error('加载上级评分校准队列失败', e);
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [allowed, periodFilter, departmentFilter, employeeNameFilter, page, pageSize]);

  useEffect(() => {
    void fetchData();
  }, [fetchData]);

  const handleSearch = () => {
    setPage(1);
    void fetchData();
  };

  const handleReset = () => {
    setPeriodFilter('');
    setDepartmentFilter(null);
    setEmployeeNameFilter('');
    setPage(1);
  };

  const columns: TableProps<PerformanceListItem>['columns'] = [
    {
      title: '员工',
      dataIndex: 'employeeId',
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
    { title: '周期', dataIndex: 'period', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 160,
      render: (s: PerformanceStatus) => (
        <Badge variant="secondary">{statusLabels[s] ?? s}</Badge>
      ),
    },
    {
      title: '直属上级',
      dataIndex: 'managerId',
      width: 140,
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
      title: '虚线上级',
      dataIndex: 'dottedManagerId',
      width: 140,
      render: (_: string, record: PerformanceListItem) =>
        record.dottedManagerId ? (
          <UserDisplay
            value={{
              user_id: record.dottedManagerId,
              name: record.dottedManagerName || record.dottedManagerId,
            }}
            size="small"
          />
        ) : (
          <span className="text-sm text-muted-foreground">—</span>
        ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 170,
      render: (val: string) => {
        if (!val) return '—';
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
      width: 100,
      render: (_: unknown, record: PerformanceListItem) => (
        <Button
          variant="ghost"
          size="sm"
          className="text-primary"
          onClick={() => navigate(`/performances/${record.id}`)}
        >
          <Eye className="mr-1 size-4" />
          详情
        </Button>
      ),
    },
  ];

  if (!permLoading && !allowed) {
    return (
      <div className="rounded-md border border-border bg-card p-8 text-center text-sm text-muted-foreground">
        仅超级管理员可访问「绩效校准（上级评分）」。
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">绩效校准（上级评分）</h1>
        <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
          查看处于直属/虚线上级评分环节的全部绩效；进入详情可查看双方当前评分与分项合成（超管专属）。
        </p>
      </div>

      <div className="rounded-md border border-border bg-card p-4 shadow-sm">
        <div className="flex flex-wrap items-end gap-4">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-foreground">周期</label>
            <Select value={periodFilter} onValueChange={setPeriodFilter}>
              <SelectTrigger className="w-[140px]">
                <SelectValue placeholder="全部周期" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">全部周期</SelectItem>
                {periodOptions.map((p) => (
                  <SelectItem key={p} value={p}>
                    {p}
                  </SelectItem>
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
            <Button onClick={handleSearch}>
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
        <Table<PerformanceListItem>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={data}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: false,
            onChange: (p) => setPage(p),
          }}
        />
      </div>
    </div>
  );
};

export default PerformanceCalibrationPage;
