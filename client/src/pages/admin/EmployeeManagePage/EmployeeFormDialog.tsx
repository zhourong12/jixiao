import React, { useState, useEffect } from 'react';
import { Loader2 } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { UserSelect } from '@/components/business-ui/user-select';
import { UserDisplay } from '@/components/business-ui/user-display';
import { EmployeeSupervisorSelect } from './EmployeeSupervisorSelect';
import { createEmployee, updateEmployee, getEmployeeRoleOptions } from '@/api';
import { useMenuPermissions } from '@/hooks/useMenuPermissions';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { toast } from 'sonner';
import type { EmployeeListItem, EmployeeRoleOption } from '@shared/api.interface';

interface EmployeeFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  employee?: EmployeeListItem | null;
  onSuccess: () => void;
}

const EMPLOYEE_TYPES = ['正式', '实习', '外包', '劳务', '顾问'];

const EmployeeFormDialog: React.FC<EmployeeFormDialogProps> = ({
  open,
  onOpenChange,
  employee,
  onSuccess,
}) => {
  const isEdit = !!employee;
  const { role: myRole } = useMenuPermissions();
  const canSetSystemRole = myRole === 'admin' || myRole === 'super_admin';

  const [userId, setUserId] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [employeeNo, setEmployeeNo] = useState('');
  const [employeeType, setEmployeeType] = useState('');
  const [departmentName, setDepartmentName] = useState('');
  const [position, setPosition] = useState('');
  const [workLocation, setWorkLocation] = useState('');
  const [joinDate, setJoinDate] = useState('');
  const [managerId, setManagerId] = useState<string | null>(null);
  const [dottedManagerId, setDottedManagerId] = useState<string | null>(null);
  const [roleKey, setRoleKey] = useState<string>('employee');
  const [roleOptions, setRoleOptions] = useState<EmployeeRoleOption[]>([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      if (employee) {
        setUserId(employee.userId || null);
        setName(employee.name || '');
        setPhone(employee.phone || '');
        setEmployeeNo(employee.employeeNo || '');
        setEmployeeType(employee.employeeType || '');
        setDepartmentName(employee.departmentName || '');
        setPosition(employee.position || '');
        setWorkLocation(employee.workLocation || '');
        setJoinDate(employee.joinDate || '');
        setManagerId(employee.managerId || null);
        setDottedManagerId(employee.dottedManagerId || null);
        setRoleKey(employee.roleKey || 'employee');
      } else {
        setUserId(null);
        setName('');
        setPhone('');
        setEmployeeNo('');
        setEmployeeType('');
        setDepartmentName('');
        setPosition('');
        setWorkLocation('');
        setJoinDate('');
        setManagerId(null);
        setDottedManagerId(null);
        setRoleKey('employee');
      }
    }
  }, [open, employee]);

  useEffect(() => {
    if (!open || !canSetSystemRole) return;
    void (async () => {
      try {
        const res = await getEmployeeRoleOptions();
        setRoleOptions(res.items);
      } catch (error: unknown) {
        logger.error('加载系统角色选项失败', error);
      }
    })();
  }, [open, canSetSystemRole]);

  const handleSave = async () => {
    if (!name.trim()) {
      toast.error('请填写姓名');
      return;
    }
    if (!isEdit && !userId) {
      toast.error('请选择员工');
      return;
    }

    setSaving(true);
    try {
      if (isEdit && employee) {
        await updateEmployee(employee.userId, {
          name: name.trim(),
          phone: phone.trim() || undefined,
          employeeNo: employeeNo.trim() || undefined,
          employeeType: employeeType || undefined,
          departmentName: departmentName.trim() || undefined,
          position: position.trim() || undefined,
          workLocation: workLocation.trim() || undefined,
          joinDate: joinDate || undefined,
          managerId: managerId || undefined,
          dottedManagerId: dottedManagerId || undefined,
          ...(canSetSystemRole ? { roleKey } : {}),
        });
        toast.success('员工信息已更新');
      } else {
        await createEmployee({
          userId: userId!,
          name: name.trim(),
          phone: phone.trim() || undefined,
          employeeNo: employeeNo.trim() || undefined,
          employeeType: employeeType || undefined,
          departmentName: departmentName.trim() || undefined,
          position: position.trim() || undefined,
          workLocation: workLocation.trim() || undefined,
          joinDate: joinDate || undefined,
          managerId: managerId || undefined,
          dottedManagerId: dottedManagerId || undefined,
          ...(canSetSystemRole ? { roleKey } : {}),
        });
        toast.success('员工已添加');
      }
      onOpenChange(false);
      onSuccess();
    } catch (error: unknown) {
      logger.error(isEdit ? '更新员工失败' : '创建员工失败', error);
      toast.error('保存失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEdit ? '编辑员工' : '新增员工'}</DialogTitle>
        </DialogHeader>

        <div className="grid grid-cols-2 gap-4 py-4">
          {/* 员工ID - 仅新增时显示 */}
          {!isEdit && (
            <div className="space-y-2">
              <Label>
                员工 <span className="text-danger">*</span>
              </Label>
              <UserSelect
                value={userId}
                onChange={setUserId}
                placeholder="选择员工"
              />
              {userId && (
                <div className="mt-1">
                  <UserDisplay userId={userId} size="small" />
                </div>
              )}
            </div>
          )}

          {/* 姓名 */}
          <div className="space-y-2">
            <Label>
              姓名 <span className="text-danger">*</span>
            </Label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="请输入姓名"
            />
          </div>

          {/* 手机号 */}
          <div className="space-y-2">
            <Label>手机号</Label>
            <Input
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="请输入手机号"
            />
          </div>

          {/* 工号 */}
          <div className="space-y-2">
            <Label>工号</Label>
            <Input
              value={employeeNo}
              onChange={(e) => setEmployeeNo(e.target.value)}
              placeholder="请输入工号"
            />
          </div>

          {/* 系统角色（RBAC，仅管理员可改） */}
          {canSetSystemRole && (
            <div className="space-y-2">
              <Label>系统角色</Label>
              {roleOptions.length > 0 ? (
                <Select value={roleKey} onValueChange={setRoleKey}>
                  <SelectTrigger>
                    <SelectValue placeholder="请选择系统角色" />
                  </SelectTrigger>
                  <SelectContent>
                    {roleOptions.map((opt) => (
                      <SelectItem key={opt.roleKey} value={opt.roleKey}>
                        {opt.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : (
                <p className="text-sm text-muted-foreground py-2">正在加载角色列表…</p>
              )}
            </div>
          )}

          {/* 人员类型 */}
          <div className="space-y-2">
            <Label>人员类型</Label>
            <Select value={employeeType} onValueChange={setEmployeeType}>
              <SelectTrigger>
                <SelectValue placeholder="请选择人员类型" />
              </SelectTrigger>
              <SelectContent>
                {EMPLOYEE_TYPES.map((type) => (
                  <SelectItem key={type} value={type}>
                    {type}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* 部门 */}
          <div className="space-y-2">
            <Label>部门</Label>
            <Input
              value={departmentName}
              onChange={(e) => setDepartmentName(e.target.value)}
              placeholder="请输入部门"
            />
          </div>

          {/* 职务 */}
          <div className="space-y-2">
            <Label>职务</Label>
            <Input
              value={position}
              onChange={(e) => setPosition(e.target.value)}
              placeholder="请输入职务"
            />
          </div>

          {/* 工作地点 */}
          <div className="space-y-2">
            <Label>工作地点</Label>
            <Input
              value={workLocation}
              onChange={(e) => setWorkLocation(e.target.value)}
              placeholder="请输入工作地点"
            />
          </div>

          {/* 入职日期 */}
          <div className="space-y-2">
            <Label>入职日期</Label>
            <Input
              type="date"
              value={joinDate}
              onChange={(e) => setJoinDate(e.target.value)}
            />
          </div>

          {/* 直属上级（本系统员工库，不依赖飞书 runtime 用户搜索） */}
          <div className="space-y-2">
            <Label>直属上级</Label>
            <EmployeeSupervisorSelect
              dialogOpen={open}
              resetScope={employee?.userId ?? 'new'}
              value={managerId}
              onChange={setManagerId}
              nameFromServer={
                isEdit && employee && employee.managerId === managerId
                  ? employee.managerName
                  : undefined
              }
              excludeUserIds={[employee?.userId, dottedManagerId].filter(
                (x): x is string => !!x,
              )}
              placeholder="选择直属上级"
            />
          </div>

          {/* 虚线上级 */}
          <div className="space-y-2">
            <Label>虚线上级</Label>
            <EmployeeSupervisorSelect
              dialogOpen={open}
              resetScope={employee?.userId ?? 'new'}
              value={dottedManagerId}
              onChange={setDottedManagerId}
              nameFromServer={
                isEdit && employee && employee.dottedManagerId === dottedManagerId
                  ? employee.dottedManagerName
                  : undefined
              }
              excludeUserIds={[employee?.userId, managerId].filter((x): x is string => !!x)}
              placeholder="选择虚线上级"
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleSave} disabled={saving}>
            {saving ? (
              <>
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                保存中...
              </>
            ) : (
              '保存'
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default EmployeeFormDialog;
