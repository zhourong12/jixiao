import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Users } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Switch } from '@/components/ui/switch';
import { Spinner } from '@/components/ui/spinner';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { getMenuPermissionMatrix, updateMenuPermissionsForRole } from '@client/src/api';
import type { MenuPermissionKey, MenuPermissionMatrixResponse } from '@shared/api.interface';
import { logger } from '@lark-apaas/client-toolkit/logger';

const MENU_LABELS: Record<MenuPermissionKey, string> = {
  todo: '待办',
  home: '工作台',
  performance_list: '绩效列表',
  performance_export: '导出绩效数据',
  performance_list_all: '查看全员绩效（列表/导出范围）',
  performance_batch_create: '批量创建绩效',
  performance_review_admin: '绩效终审与校准',
  admin_performance_calibration: '绩效校准（上级评分队列，仅超管）',
  my_performance: '我的绩效',
  admin_templates: '模板管理',
  admin_notifications: '通知管理',
  admin_employees: '员工管理',
  admin_roles: '角色管理',
  admin_permissions: '权限管理',
  admin_statistics_months: '周期与评选',
  admin_system_config: '系统配置',
};

const MENU_GROUPS: { label: string; keys: MenuPermissionKey[] }[] = [
  {
    label: '绩效相关',
    keys: [
      'todo',
      'my_performance',
      'performance_list',
      'performance_list_all',
      'performance_review_admin',
      'admin_performance_calibration',
    ],
  },
  {
    label: '后台管理',
    keys: [
      'home',
      'admin_templates',
      'admin_notifications',
      'admin_employees',
      'admin_roles',
      'admin_permissions',
      'admin_statistics_months',
      'admin_system_config',
    ],
  },
];

const PermissionManagePage = () => {
  const [matrix, setMatrix] = useState<MenuPermissionMatrixResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<string | null>(null);
  const [activeRole, setActiveRole] = useState<string>('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getMenuPermissionMatrix();
      setMatrix(data);
      if (data.roles.length > 0) {
        setActiveRole((prev) => (prev && data.roles.includes(prev) ? prev : data.roles[0]!));
      }
    } catch (e) {
      logger.error('加载权限矩阵失败', e);
      setMatrix(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleToggle = async (role: string, menuKey: MenuPermissionKey, checked: boolean) => {
    if (role === 'super_admin') {
      return;
    }
    setSaving(`${role}:${menuKey}`);
    try {
      await updateMenuPermissionsForRole({ role, menus: { [menuKey]: checked } });
      await load();
      window.dispatchEvent(new Event('menu-permissions-changed'));
    } catch (e) {
      logger.error('保存权限失败', e);
    } finally {
      setSaving(null);
    }
  };

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner className="h-8 w-8" />
      </div>
    );
  }

  if (!matrix || matrix.roles.length === 0) {
    return (
      <div className="rounded-md border border-border bg-card p-8 text-center text-sm text-muted-foreground">
        仅超级管理员可访问此页，或暂无权限数据。
      </div>
    );
  }

  const roleInfos =
    matrix.roleInfos ??
    matrix.roles.map((roleKey) => ({ roleKey, name: roleKey, isSystem: true }));

  const roleLabel = (roleKey: string) => roleInfos.find((r) => r.roleKey === roleKey)?.name ?? roleKey;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">权限管理</h1>
          <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
            按「角色」切换页签，为该角色打开或关闭菜单与能力项。侧栏仅展示已授权的菜单；导出绩效与批量创建仅超级管理员可用。绩效相关能力（全员列表、终审）可单独授权给自定义角色。
            新建角色请在{' '}
            <Link to="/admin/roles" className="text-primary underline-offset-4 hover:underline">
              角色管理
            </Link>{' '}
            中操作。
          </p>
        </div>
        <Button variant="outline" asChild>
          <Link to="/admin/roles">
            <Users className="mr-2 h-4 w-4" />
            角色管理
          </Link>
        </Button>
      </div>

      <Card className="rounded-md shadow-sm">
        <CardHeader className="pb-3">
          <CardTitle className="text-base font-semibold">按角色配置</CardTitle>
        </CardHeader>
        <CardContent>
          <Tabs value={activeRole} onValueChange={setActiveRole} className="w-full">
            <TabsList className="mb-4 flex h-auto min-h-10 w-full flex-wrap justify-start gap-1 bg-accent/50 p-1">
              {matrix.roles.map((roleKey) => {
                const info = roleInfos.find((r) => r.roleKey === roleKey);
                return (
                  <TabsTrigger
                    key={roleKey}
                    value={roleKey}
                    className="shrink-0 data-[state=active]:bg-card"
                  >
                    <span>{info?.name ?? roleKey}</span>
                    {info?.isSystem && (
                      <span className="ml-1.5 rounded bg-muted px-1.5 py-0 text-[10px] text-muted-foreground">
                        内置
                      </span>
                    )}
                  </TabsTrigger>
                );
              })}
            </TabsList>

            {matrix.roles.map((roleKey) => (
              <TabsContent key={roleKey} value={roleKey} className="mt-0 space-y-4">
                {roleKey === 'super_admin' ? (
                  <p className="rounded-md border border-border bg-accent/30 px-4 py-3 text-sm text-foreground">
                    <span className="font-medium">{roleLabel(roleKey)}</span>{' '}
                    登录后始终拥有全部菜单与能力，此处无需配置。
                  </p>
                ) : (
                  <div className="rounded-md border border-border">
                    <table className="w-full border-collapse text-sm">
                      <thead>
                        <tr className="border-b border-border bg-accent/30">
                          <th className="px-4 py-3 text-left font-semibold">菜单 / 能力</th>
                          <th className="w-28 px-4 py-3 text-center font-semibold">允许</th>
                        </tr>
                      </thead>
                      <tbody>
                        {MENU_GROUPS.map((group) => {
                          const visibleKeys = group.keys.filter((k) => matrix.menus.includes(k));
                          if (visibleKeys.length === 0) return null;
                          return (
                            <RoleMenuGroupRows
                              key={group.label}
                              groupLabel={group.label}
                              menuKeys={visibleKeys}
                              role={roleKey}
                              matrix={matrix.matrix}
                              saving={saving}
                              onToggle={handleToggle}
                            />
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </TabsContent>
            ))}
          </Tabs>
        </CardContent>
      </Card>
    </div>
  );
};

interface RoleMenuGroupRowsProps {
  groupLabel: string;
  menuKeys: MenuPermissionKey[];
  role: string;
  matrix: MenuPermissionMatrixResponse['matrix'];
  saving: string | null;
  onToggle: (role: string, menuKey: MenuPermissionKey, checked: boolean) => void;
}

function RoleMenuGroupRows({
  groupLabel,
  menuKeys,
  role,
  matrix,
  saving,
  onToggle,
}: RoleMenuGroupRowsProps) {
  return (
  <>
    <tr className="border-b border-border bg-accent/40">
      <td colSpan={2} className="px-4 py-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        {groupLabel}
      </td>
    </tr>
    {menuKeys.map((menuKey) => {
      const on = matrix[role]?.[menuKey] !== false;
      const busy = saving === `${role}:${menuKey}`;
      return (
        <tr key={menuKey} className="border-b border-border last:border-0">
          <td className="px-6 py-3 text-foreground">{MENU_LABELS[menuKey]}</td>
          <td className="px-4 py-2 text-center">
            <div className="flex justify-center">
              <Switch
                checked={on}
                disabled={busy}
                onCheckedChange={(v) => void onToggle(role, menuKey, v)}
                aria-label={`${role} ${menuKey}`}
              />
            </div>
          </td>
        </tr>
      );
    })}
  </>
  );
}

export default PermissionManagePage;
