import React, { useState, useEffect, useCallback } from 'react';
import { Plus, Upload, Search, Settings2, Pencil, Trash2, RefreshCw } from 'lucide-react';
import { getEmployees, deleteEmployee, syncEmployeesFromLark } from '@/api';
import { Table } from '@lark-apaas/client-toolkit/antd-table';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { UserDisplay } from '@/components/business-ui/user-display';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { toast } from 'sonner';
import type { EmployeeListItem } from '@shared/api.interface';
import HierarchySettingDialog from './HierarchySettingDialog.tsx';
import EmployeeFormDialog from './EmployeeFormDialog';
import ExcelImportDialog from './ExcelImportDialog';
import { showConfirm } from '@lark-apaas/client-toolkit';

const PAGE_SIZE = 10;

const EmployeeManagePage: React.FC = () => {
  const [employees, setEmployees] = useState<EmployeeListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);

  // 设置上级弹窗
  const [dialogOpen, setDialogOpen] = useState(false);
  const [currentEmployee, setCurrentEmployee] = useState<EmployeeListItem | null>(null);

  // 新增/编辑员工弹窗
  const [formDialogOpen, setFormDialogOpen] = useState(false);
  const [editingEmployee, setEditingEmployee] = useState<EmployeeListItem | null>(null);

  // Excel导入弹窗
  const [importDialogOpen, setImportDialogOpen] = useState(false);

  // 同步飞书 loading
  const [syncing, setSyncing] = useState(false);

  const fetchEmployees = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getEmployees({ page, pageSize: PAGE_SIZE, keyword: keyword || undefined });
      setEmployees(result.items);
      setTotal(result.total);
    } catch {
      // Error handled in API layer
    } finally {
      setLoading(false);
    }
  }, [page, keyword]);

  useEffect(() => {
    fetchEmployees();
  }, [fetchEmployees]);

  const handleSearch = () => {
    setPage(1);
    fetchEmployees();
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handleOpenHierarchyDialog = (employee: EmployeeListItem) => {
    setCurrentEmployee(employee);
    setDialogOpen(true);
  };

  const handleOpenEditDialog = (employee: EmployeeListItem) => {
    setEditingEmployee(employee);
    setFormDialogOpen(true);
  };

  const handleOpenAddDialog = () => {
    setEditingEmployee(null);
    setFormDialogOpen(true);
  };

  const handleDialogSuccess = () => {
    fetchEmployees();
  };

  const handleDelete = async (employee: EmployeeListItem) => {
    if (!await showConfirm(`确定要删除员工「${employee.name}」吗？`)) {
      return;
    }
    try {
      await deleteEmployee(employee.userId);
      toast.success('员工已删除');
      fetchEmployees();
    } catch (error: unknown) {
      logger.error('删除员工失败', error);
      toast.error('删除失败，请重试');
    }
  };

  const isTempUserId = (userId: string | undefined) => userId?.startsWith('temp_');

  const handleSyncFromLark = async () => {
    // 确认是否清空现有数据
    const clearExisting = await showConfirm(
      '同步飞书员工数据\n\n是否清空现有数据后再同步？\n\n选择"确定"将清空后同步，选择"取消"将增量同步（保留现有数据）。',
    );

    setSyncing(true);
    try {
      const result = await syncEmployeesFromLark(clearExisting);

      if (!result.success) {
        // 后端返回错误
        toast.error(result.message || '同步失败');
        return;
      }

      if (result.syncedCount === 0) {
        if (result.totalCount === 0) {
          // 飞书返回 0 个用户，可能是权限问题
          toast.error(
            <div className="space-y-2">
              <div className="font-medium">未能从飞书获取到用户数据</div>
              <div className="text-sm">请检查以下配置：</div>
              <div className="text-sm">
                <div>1. 飞书应用是否已开通通讯录权限？</div>
                <div className="pl-4 text-muted-foreground">
                  • contact:user.base:readonly<br />
                  • contact:contact.base:readonly<br />
                  • contact:department.base:readonly
                </div>
              </div>
              <div className="text-sm">
                2. 应用的通讯录权限范围是否包含需要同步的用户？<br />
                <span className="text-muted-foreground">在飞书开放平台 → 权限管理 → 通讯录权限范围 中设置</span>
              </div>
            </div>,
            { duration: 15000 }
          );
        } else if (result.validCount === 0) {
          // 飞书有数据但没有有效用户ID
          toast.error(
            <div className="space-y-2">
              <div>飞书返回 {result.totalCount} 个用户，但无法提取用户ID</div>
              <div className="text-sm text-muted-foreground">
                请检查飞书应用是否申请了以下权限：<br />
                • contact:user.user_id:readonly<br />
                • contact:user.department:readonly
              </div>
            </div>,
            { duration: 10000 }
          );
        } else if (result.skippedCount && result.skippedCount > 0) {
          toast.info(`飞书通讯录有 ${result.validCount} 个有效用户，均已存在，无需同步`);
        } else {
          toast.warning('未能从飞书获取到用户数据，请检查飞书应用权限设置');
        }
      } else {
        toast.success(`成功同步 ${result.syncedCount} 个员工`);
        fetchEmployees();
      }
    } catch (error: unknown) {
      logger.error('同步飞书员工失败', error);
      toast.error('同步失败，请检查网络连接或飞书应用配置');
    } finally {
      setSyncing(false);
    }
  };

  const columns = [
    {
      title: '姓名',
      key: 'name',
      width: 180,
      render: (_: unknown, record: EmployeeListItem) => (
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium">{record.name || '-'}</span>
          {isTempUserId(record.userId) && (
            <span className="px-1.5 py-0.5 text-xs bg-warning/10 text-warning rounded">未关联</span>
          )}
        </div>
      ),
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      key: 'phone',
      width: 130,
      render: (phone: string | undefined) => (
        <span className="text-sm">{phone || '-'}</span>
      ),
    },
    {
      title: '工号',
      dataIndex: 'employeeNo',
      key: 'employeeNo',
      width: 100,
      render: (employeeNo: string | undefined) => (
        <span className="text-sm">{employeeNo || '-'}</span>
      ),
    },
    {
      title: '部门',
      dataIndex: 'departmentName',
      key: 'departmentName',
      width: 180,
      render: (departmentName: string | undefined) => (
        <span className="text-sm">{departmentName || '-'}</span>
      ),
    },
    {
      title: '系统角色',
      key: 'roleName',
      width: 110,
      render: (_: unknown, record: EmployeeListItem) => (
        <span className="text-sm">{record.roleName || record.roleKey || '-'}</span>
      ),
    },
    {
      title: '职务',
      dataIndex: 'position',
      key: 'position',
      width: 120,
      render: (position: string | undefined) => (
        <span className="text-sm">{position || '-'}</span>
      ),
    },
    {
      title: '工作地点',
      dataIndex: 'workLocation',
      key: 'workLocation',
      width: 120,
      render: (workLocation: string | undefined) => (
        <span className="text-sm">{workLocation || '-'}</span>
      ),
    },
    {
      title: '入职日期',
      dataIndex: 'joinDate',
      key: 'joinDate',
      width: 110,
      render: (joinDate: string | undefined) => (
        <span className="text-sm">{joinDate || '-'}</span>
      ),
    },
    {
      title: '直属上级',
      dataIndex: 'managerId',
      key: 'managerId',
      width: 150,
      render: (_: unknown, record: EmployeeListItem) =>
        record.managerId ? (
          <span className="text-sm">{record.managerName || record.managerId}</span>
        ) : (
          <span className="text-muted-foreground text-sm">未设置</span>
        ),
    },
    {
      title: '虚线上级',
      dataIndex: 'dottedManagerId',
      key: 'dottedManagerId',
      width: 150,
      render: (_: unknown, record: EmployeeListItem) =>
        record.dottedManagerId ? (
          <span className="text-sm">{record.dottedManagerName || record.dottedManagerId}</span>
        ) : (
          <span className="text-muted-foreground text-sm">未设置</span>
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      fixed: 'right' as const,
      render: (_: unknown, record: EmployeeListItem) => (
        <div className="flex items-center gap-1">
          {isTempUserId(record.userId) && (
            <Button
              variant="ghost"
              size="sm"
              className="text-primary"
              onClick={() => handleOpenHierarchyDialog(record)}
            >
              关联账号
            </Button>
          )}
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleOpenEditDialog(record)}
          >
            <Pencil className="w-3.5 h-3.5 mr-1" />
            编辑
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleOpenHierarchyDialog(record)}
          >
            <Settings2 className="w-3.5 h-3.5 mr-1" />
            上级
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="text-danger hover:text-danger"
            onClick={() => handleDelete(record)}
          >
            <Trash2 className="w-3.5 h-3.5" />
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">员工管理</h1>
        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            onClick={handleSyncFromLark}
            disabled={syncing}
          >
            {syncing ? (
              <RefreshCw className="w-4 h-4 mr-2 animate-spin" />
            ) : (
              <RefreshCw className="w-4 h-4 mr-2" />
            )}
            同步飞书
          </Button>
          <Button variant="outline" onClick={() => setImportDialogOpen(true)}>
            <Upload className="w-4 h-4 mr-2" />
            导入花名册
          </Button>
          <Button onClick={handleOpenAddDialog}>
            <Plus className="w-4 h-4 mr-2" />
            新增员工
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>员工列表</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* 搜索栏 */}
          <div className="flex items-center gap-3">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <Input
                placeholder="搜索姓名/手机号/工号"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onKeyDown={handleKeyDown}
                className="pl-9"
              />
            </div>
            <Button onClick={handleSearch}>搜索</Button>
          </div>

          {/* 表格 */}
          <Table
            columns={columns}
            dataSource={employees}
            rowKey="userId"
            loading={loading}
            pagination={{
              current: page,
              pageSize: PAGE_SIZE,
              total,
              onChange: (p) => setPage(p),
              showSizeChanger: false,
              showTotal: (t) => `共 ${t} 条`,
            }}
            scroll={{ x: 1520 }}
          />
        </CardContent>
      </Card>

      {/* 设置上级弹窗 */}
      {currentEmployee && (
        <HierarchySettingDialog
          open={dialogOpen}
          onOpenChange={setDialogOpen}
          employee={currentEmployee}
          onSuccess={handleDialogSuccess}
        />
      )}

      {/* 新增/编辑员工弹窗 */}
      <EmployeeFormDialog
        open={formDialogOpen}
        onOpenChange={setFormDialogOpen}
        employee={editingEmployee}
        onSuccess={handleDialogSuccess}
      />

      {/* Excel导入弹窗 */}
      <ExcelImportDialog
        open={importDialogOpen}
        onOpenChange={setImportDialogOpen}
        onSuccess={handleDialogSuccess}
      />
    </div>
  );
};

export default EmployeeManagePage;
