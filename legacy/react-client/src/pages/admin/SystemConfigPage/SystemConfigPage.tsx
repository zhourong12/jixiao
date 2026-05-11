import { useCallback, useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Spinner } from '@/components/ui/spinner';
import { Save } from 'lucide-react';
import { toast } from 'sonner';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { getSystemConfig, updateSystemConfig, type SystemConfigItem } from '@client/src/api';

const SystemConfigPage = () => {
  const [items, setItems] = useState<SystemConfigItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editValues, setEditValues] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getSystemConfig();
      const list = data?.items;
      if (!Array.isArray(list)) {
        logger.error('系统配置返回格式异常', data);
        toast.error('加载系统配置失败：返回数据异常');
        setItems([]);
        return;
      }
      setItems(list);
      const vals: Record<string, string> = {};
      for (const item of list) {
        vals[item.key] = item.value;
      }
      setEditValues(vals);
    } catch (e: unknown) {
      logger.error('加载系统配置失败', e);
      const data = (e as { response?: { data?: { error?: { message?: string } } } })?.response?.data;
      const msg =
        typeof data?.error?.message === 'string'
          ? data.error.message
          : '加载系统配置失败（请确认已登录且具备「系统配置」菜单权限）';
      toast.error(msg);
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleChange = (key: string, value: string) => {
    setEditValues((prev) => ({ ...prev, [key]: value }));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const configs = items.map((item) => ({
        key: item.key,
        value: editValues[item.key] ?? item.value,
      }));
      await updateSystemConfig({ configs });
      toast.success('系统配置已保存');
      await load();
    } catch (e: unknown) {
      logger.error('保存系统配置失败', e);
      const data = (e as { response?: { data?: { error?: { message?: string } } } })?.response?.data;
      const msg =
        typeof data?.error?.message === 'string' ? data.error.message : '保存失败，请检查权限';
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner className="h-8 w-8" />
      </div>
    );
  }

  const hasChanges = items.some((item) => (editValues[item.key] ?? '') !== item.value);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">系统配置</h1>
          <p className="text-sm text-muted-foreground mt-1">
            管理绩效系统的全局参数配置
          </p>
        </div>
        <Button onClick={handleSave} disabled={saving || !hasChanges || items.length === 0}>
          <Save className="mr-2 size-4" />
          {saving ? '保存中...' : '保存配置'}
        </Button>
      </div>

      {items.length === 0 ? (
        <div className="rounded-md border border-border bg-card p-8 text-center text-sm text-muted-foreground">
          暂无可编辑的配置项，或当前账号无「系统配置」权限。请使用具备该菜单权限的管理员账号，或在「权限管理」中为当前角色开启「系统配置」。
        </div>
      ) : (
        <Card className="rounded-md shadow-sm">
          <CardHeader>
            <CardTitle className="text-base font-semibold">评审权重配置</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-6">
              {items.map((item) => (
                <div key={item.key} className="space-y-2">
                  <label className="text-sm font-medium text-foreground">
                    {item.label}
                  </label>
                  <p className="text-xs text-muted-foreground">{item.description}</p>
                  <Input
                    type={item.type === 'number' ? 'number' : 'text'}
                    step={item.type === 'number' ? '0.01' : undefined}
                    min={item.type === 'number' ? '0' : undefined}
                    max={item.type === 'number' ? '1' : undefined}
                    value={editValues[item.key] ?? ''}
                    onChange={(e) => handleChange(item.key, e.target.value)}
                    className="max-w-xs"
                  />
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
};

export default SystemConfigPage;
