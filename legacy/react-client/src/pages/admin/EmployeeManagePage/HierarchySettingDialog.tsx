import React, { useState, useEffect } from 'react';
import { Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { UserSelect } from '@/components/business-ui/user-select';
import { EmployeeSupervisorSelect } from './EmployeeSupervisorSelect';
import { updateEmployeeHierarchy } from '@/api';
import type { EmployeeListItem, UpdateEmployeeHierarchyRequest } from '@shared/api.interface';
import { toast } from 'sonner';
import { logger } from '@lark-apaas/client-toolkit/logger';

interface HierarchySettingDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  employee: EmployeeListItem;
  onSuccess: () => void;
}

const HierarchySettingDialog: React.FC<HierarchySettingDialogProps> = ({
  open,
  onOpenChange,
  employee,
  onSuccess,
}) => {
  const [managerId, setManagerId] = useState<string | null>(null);
  const [dottedManagerId, setDottedManagerId] = useState<string | null>(null);
  const [systemUserId, setSystemUserId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const isTempUser = employee.userId?.startsWith('temp_');

  useEffect(() => {
    if (open && employee) {
      setManagerId(employee.managerId || null);
      setDottedManagerId(employee.dottedManagerId || null);
      setSystemUserId(null);
    }
  }, [open, employee]);

  const handleSave = async () => {
    setSaving(true);
    try {
      // 更新上级关系（所有用户类型都走这个逻辑）
      const body: UpdateEmployeeHierarchyRequest = {
        managerId: managerId || undefined,
        dottedManagerId: dottedManagerId || undefined,
      };
      await updateEmployeeHierarchy(employee.userId, body);

      // 如果是临时用户且选择了系统账号，提示功能开发中
      if (isTempUser && systemUserId) {
        toast.success('上级设置已更新。系统账号关联功能将在后续版本中完善');
      } else {
        toast.success('上级设置已更新');
      }

      onOpenChange(false);
      onSuccess();
    } catch (error: unknown) {
      logger.error('更新员工层级关系失败', error);
      toast.error('保存失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{isTempUser ? '关联系统账号' : '设置上级'}</DialogTitle>
          <DialogDescription>
            {isTempUser
              ? `为员工「${employee.name}」关联飞书系统账号，并设置上级关系`
              : `为员工「${employee.name}」设置直属上级和虚线上级`}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6 py-4">
          {/* 系统账号（仅临时用户显示） */}
          {isTempUser && (
            <div className="space-y-2">
              <Label className="text-primary">系统账号 *</Label>
              <UserSelect
                value={systemUserId}
                onChange={setSystemUserId}
                placeholder="选择飞书系统用户"
              />
              <p className="text-xs text-muted-foreground">
                关联后该员工可使用飞书账号登录并参与绩效流程
              </p>
            </div>
          )}
          {/* 直属上级 */}
          <div className="space-y-2">
            <Label>直属上级</Label>
            <EmployeeSupervisorSelect
              dialogOpen={open}
              resetScope={employee.userId}
              value={managerId}
              onChange={setManagerId}
              nameFromServer={
                employee.managerId === managerId ? employee.managerName : undefined
              }
              excludeUserIds={[employee.userId, dottedManagerId].filter(
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
              resetScope={employee.userId}
              value={dottedManagerId}
              onChange={setDottedManagerId}
              nameFromServer={
                employee.dottedManagerId === dottedManagerId
                  ? employee.dottedManagerName
                  : undefined
              }
              excludeUserIds={[employee.userId, managerId].filter((x): x is string => !!x)}
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

export default HierarchySettingDialog;
