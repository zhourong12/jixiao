import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, Pencil, Trash2, Shield } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Spinner } from '@/components/ui/spinner';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { listRbacRoles, createRbacRole, updateRbacRole, deleteRbacRole } from '@/api';
import { showConfirm } from '@lark-apaas/client-toolkit';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { toast } from 'sonner';
import type { RbacRoleItem } from '@shared/api.interface';

const RoleManagePage = () => {
  const [items, setItems] = useState<RbacRoleItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<RbacRoleItem | null>(null);
  const [roleKey, setRoleKey] = useState('');
  const [name, setName] = useState('');
  const [sortOrder, setSortOrder] = useState('');
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listRbacRoles();
      setItems(res.items);
    } catch (e) {
      logger.error('加载角色失败', e);
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    setRoleKey('');
    setName('');
    setSortOrder('');
    setDialogOpen(true);
  };

  const openEdit = (row: RbacRoleItem) => {
    setEditing(row);
    setRoleKey(row.roleKey);
    setName(row.name);
    setSortOrder(String(row.sortOrder));
    setDialogOpen(true);
  };

  const handleSave = async () => {
    const n = name.trim();
    if (!n) {
      toast.error('请填写角色名称');
      return;
    }
    setSaving(true);
    try {
      if (editing) {
        const so = sortOrder.trim() ? parseInt(sortOrder, 10) : undefined;
        await updateRbacRole(editing.roleKey, {
          name: n,
          ...(so != null && !Number.isNaN(so) ? { sortOrder: so } : {}),
        });
        toast.success('角色已更新');
      } else {
        const k = roleKey.trim();
        if (!k) {
          toast.error('请填写角色标识');
          setSaving(false);
          return;
        }
        const so = sortOrder.trim() ? parseInt(sortOrder, 10) : undefined;
        await createRbacRole({
          roleKey: k,
          name: n,
          ...(so != null && !Number.isNaN(so) ? { sortOrder: so } : {}),
        });
        toast.success('角色已创建，请到「权限管理」为该角色分配菜单');
      }
      setDialogOpen(false);
      await load();
      window.dispatchEvent(new Event('menu-permissions-changed'));
    } catch {
      toast.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (row: RbacRoleItem) => {
    if (row.isSystem) {
      toast.error('内置角色不可删除');
      return;
    }
    if (!(await showConfirm(`确定删除角色「${row.name}」？已绑定用户的角色无法删除。`))) return;
    try {
      await deleteRbacRole(row.roleKey);
      toast.success('已删除');
      await load();
      window.dispatchEvent(new Event('menu-permissions-changed'));
    } catch {
      toast.error('删除失败（可能仍有用户使用该角色）');
    }
  };

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner className="h-8 w-8" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">角色管理</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            维护系统角色；菜单访问权限在{' '}
            <Link to="/admin/permissions" className="text-primary underline-offset-4 hover:underline">
              权限管理
            </Link>{' '}
            中按角色配置。
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" asChild>
            <Link to="/admin/permissions">
              <Shield className="mr-2 h-4 w-4" />
              权限管理
            </Link>
          </Button>
          <Button onClick={openCreate}>
            <Plus className="mr-2 h-4 w-4" />
            新增角色
          </Button>
        </div>
      </div>

      <Card className="rounded-md shadow-sm">
        <CardHeader className="pb-3">
          <CardTitle className="text-base font-semibold">角色列表</CardTitle>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <table className="w-full min-w-[480px] border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left">
                <th className="py-3 pr-4 font-semibold">标识</th>
                <th className="py-3 pr-4 font-semibold">名称</th>
                <th className="py-3 pr-4 font-semibold">排序</th>
                <th className="py-3 pr-4 font-semibold">类型</th>
                <th className="py-3 text-right font-semibold">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.roleKey} className="border-b border-border last:border-0">
                  <td className="py-3 pr-4 font-mono text-xs text-muted-foreground">{row.roleKey}</td>
                  <td className="py-3 pr-4">{row.name}</td>
                  <td className="py-3 pr-4">{row.sortOrder}</td>
                  <td className="py-3 pr-4">
                    {row.isSystem ? (
                      <span className="rounded-full bg-accent px-2 py-0.5 text-xs">内置</span>
                    ) : (
                      <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary">自定义</span>
                    )}
                  </td>
                  <td className="py-3 text-right">
                    <Button variant="ghost" size="sm" onClick={() => openEdit(row)}>
                      <Pencil className="mr-1 h-3.5 w-3.5" />
                      编辑
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-danger hover:text-danger"
                      disabled={row.isSystem}
                      onClick={() => void handleDelete(row)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {items.length === 0 && (
            <p className="py-8 text-center text-sm text-muted-foreground">暂无数据或无权访问（仅超级管理员）。</p>
          )}
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? '编辑角色' : '新增角色'}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            {!editing && (
              <div className="space-y-2">
                <Label>角色标识</Label>
                <Input
                  value={roleKey}
                  onChange={(e) => setRoleKey(e.target.value)}
                  placeholder="小写字母开头，如 hr_bp"
                  className="font-mono text-sm"
                />
                <p className="text-xs text-muted-foreground">创建后不可修改，仅含小写字母、数字、下划线。</p>
              </div>
            )}
            <div className="space-y-2">
              <Label>显示名称</Label>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="如 人事 BP" />
            </div>
            <div className="space-y-2">
              <Label>排序号</Label>
              <Input
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value)}
                placeholder="可选，数字越小越靠前"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={() => void handleSave()} disabled={saving}>
              {saving ? '保存中…' : '保存'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default RoleManagePage;
