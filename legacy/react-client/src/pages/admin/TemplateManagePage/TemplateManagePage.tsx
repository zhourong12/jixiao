import React, { useState, useCallback } from 'react';
import { Table, TableProps } from '@lark-apaas/client-toolkit/antd-table';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Plus, Copy, Edit, Power, Trash2 } from 'lucide-react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { toast } from 'sonner';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { axiosForBackend } from '@lark-apaas/client-toolkit/utils/getAxiosForBackend';
import type {
  PerformanceTemplate,
  TemplateListItem,
  PerformanceIndicator,
  CreateTemplateRequest,
  UpdateTemplateRequest,
} from '@shared/api.interface';

const POSITIONS = [
  '前端开发',
  '后端开发',
  '产品经理',
  '设计师',
  '测试工程师',
  '项目经理',
  '运维工程师',
  '数据分析师',
];

const indicatorSchema = z.object({
  name: z.string().min(1, '指标名称不能为空'),
  weight: z.coerce
    .number()
    .min(1, '权重至少为1')
    .max(100, '权重不能超过100'),
  description: z.string().default(''),
});

const templateFormSchema = z.object({
  name: z.string().min(1, '模板名称不能为空'),
  position: z.string().min(1, '请选择适用岗位'),
  indicators: z
    .array(indicatorSchema)
    .min(1, '至少添加一个指标'),
});

type TemplateFormValues = z.infer<typeof templateFormSchema>;

type IndicatorItem = TemplateFormValues['indicators'][number];

async function fetchTemplates(
  page: number,
  pageSize: number,
): Promise<{ items: TemplateListItem[]; total: number }> {
  const response = await axiosForBackend({
    url: '/api/admin/templates',
    method: 'GET',
    params: { page, pageSize },
  });
  if (response.status === 403) {
    throw new Error('无操作权限，请联系管理员分配角色');
  }
  return response.data;
}

async function fetchTemplateDetail(
  id: string,
): Promise<PerformanceTemplate> {
  const response = await axiosForBackend({
    url: `/api/admin/templates/${id}`,
    method: 'GET',
  });
  if (response.status === 403) {
    throw new Error('无操作权限，请联系管理员分配角色');
  }
  return response.data;
}

async function createTemplate(
  body: CreateTemplateRequest,
): Promise<{ id: string }> {
  const response = await axiosForBackend({
    url: '/api/admin/templates',
    method: 'POST',
    data: body,
  });
  if (response.status === 403) {
    throw new Error('无操作权限，请联系管理员分配角色');
  }
  return response.data;
}

async function updateTemplate(
  id: string,
  body: UpdateTemplateRequest,
): Promise<{ success: boolean }> {
  const response = await axiosForBackend({
    url: `/api/admin/templates/${id}`,
    method: 'PATCH',
    data: body,
  });
  if (response.status === 403) {
    throw new Error('无操作权限，请联系管理员分配角色');
  }
  return response.data;
}

async function toggleTemplateStatus(
  id: string,
): Promise<{ success: boolean; newStatus: string }> {
  const response = await axiosForBackend({
    url: `/api/admin/templates/${id}/toggle-status`,
    method: 'POST',
  });
  if (response.status === 403) {
    throw new Error('无操作权限，请联系管理员分配角色');
  }
  return response.data;
}

async function copyTemplate(
  id: string,
): Promise<{ newTemplateId: string }> {
  const response = await axiosForBackend({
    url: `/api/admin/templates/${id}/copy`,
    method: 'POST',
  });
  if (response.status === 403) {
    throw new Error('无操作权限，请联系管理员分配角色');
  }
  return response.data;
}

async function deleteTemplate(id: string): Promise<{ success: boolean }> {
  const response = await axiosForBackend({
    url: `/api/admin/templates/${id}`,
    method: 'DELETE',
  });
  if (response.status === 403) {
    throw new Error('无操作权限，请联系管理员分配角色');
  }
  return response.data;
}

interface TemplateDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editingTemplate: TemplateListItem | null;
  onSuccess: () => void;
}

const TemplateDialog: React.FC<TemplateDialogProps> = ({
  open,
  onOpenChange,
  editingTemplate,
  onSuccess,
}) => {
  const form = useForm<TemplateFormValues>({
    resolver: zodResolver(templateFormSchema),
    defaultValues: {
      name: '',
      position: '',
      indicators: [{ name: '', weight: 0, description: '' }],
    },
  });

  const [submitting, setSubmitting] = useState(false);

  React.useEffect(() => {
    if (open && editingTemplate) {
      fetchTemplateDetail(editingTemplate.id)
        .then((tpl: PerformanceTemplate) => {
          form.reset({
            name: tpl.name,
            position: tpl.position,
            indicators: tpl.indicators.map((ind: PerformanceIndicator) => ({
              name: ind.name,
              weight: ind.weight,
              description: ind.description,
            })),
          });
        })
        .catch((err: unknown) => {
          logger.error('加载模板详情失败', err);
          toast.error('加载模板详情失败');
        });
    } else if (open && !editingTemplate) {
      form.reset({
        name: '',
        position: '',
        indicators: [{ name: '', weight: 0, description: '' }],
      });
    }
  }, [open, editingTemplate, form]);

  const indicators = form.watch('indicators');
  const totalWeight = indicators.reduce(
    (sum: number, ind: IndicatorItem) => sum + (ind.weight || 0),
    0,
  );

  const addIndicator = useCallback(() => {
    const current = form.getValues('indicators');
    form.setValue('indicators', [
      ...current,
      { name: '', weight: 0, description: '' },
    ]);
  }, [form]);

  const removeIndicator = useCallback(
    (index: number) => {
      const current = form.getValues('indicators');
      if (current.length <= 1) {
        toast.warning('至少保留一个指标');
        return;
      }
      form.setValue(
        'indicators',
        current.filter((_ind: IndicatorItem, i: number) => i !== index),
      );
    },
    [form],
  );

  const onSubmit = useCallback(
    async (data: TemplateFormValues) => {
      if (totalWeight !== 100) {
        toast.error('权重总和必须等于100');
        return;
      }
      setSubmitting(true);
      try {
        if (editingTemplate) {
          await updateTemplate(editingTemplate.id, {
            name: data.name,
            position: data.position,
            indicators: data.indicators as PerformanceIndicator[],
          });
          toast.success('模板已更新');
        } else {
          await createTemplate({
            name: data.name,
            position: data.position,
            indicators: data.indicators as PerformanceIndicator[],
          });
          toast.success('模板已创建');
        }
        onSuccess();
      } catch (error: unknown) {
        logger.error('保存模板失败', error);
        toast.error('保存失败');
      } finally {
        setSubmitting(false);
      }
    },
    [editingTemplate, totalWeight, onSuccess],
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {editingTemplate ? '编辑模板' : '新建模板'}
          </DialogTitle>
          <DialogDescription>
            {editingTemplate
              ? '修改绩效模板信息'
              : '创建新的岗位绩效模板'}
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form
            onSubmit={form.handleSubmit(onSubmit)}
            className="space-y-6"
          >
            <div className="flex flex-wrap gap-4">
              <FormField
                control={form.control}
                name="name"
                render={({ field }) => (
                  <FormItem className="flex-1 min-w-[200px]">
                    <FormLabel>
                      模板名称 <span className="text-destructive">*</span>
                    </FormLabel>
                    <FormControl>
                      <Input placeholder="请输入模板名称" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="position"
                render={({ field }) => (
                  <FormItem className="flex-1 min-w-[200px]">
                    <FormLabel>
                      适用岗位 <span className="text-destructive">*</span>
                    </FormLabel>
                    <Select
                      onValueChange={field.onChange}
                      value={field.value}
                    >
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder="请选择岗位" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {POSITIONS.map((pos: string) => (
                          <SelectItem key={pos} value={pos}>
                            {pos}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <div>
              <div className="flex items-center justify-between mb-3">
                <FormLabel>
                  绩效指标 <span className="text-destructive">*</span>
                </FormLabel>
                <div className="flex items-center gap-3">
                  <span
                    className={
                      totalWeight === 100
                        ? 'text-sm text-success'
                        : 'text-sm text-destructive'
                    }
                  >
                    权重总和: {totalWeight}/100
                  </span>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={addIndicator}
                  >
                    <Plus className="size-4 mr-1" />
                    添加指标
                  </Button>
                </div>
              </div>

              <div className="space-y-3">
                {indicators.map((_ind: IndicatorItem, index: number) => (
                  <div
                    key={index}
                    className="flex flex-wrap gap-3 items-start rounded-md border p-3 bg-accent/30"
                  >
                    <FormField
                      control={form.control}
                      name={`indicators.${index}.name`}
                      render={({ field }) => (
                        <FormItem className="flex-1 min-w-[150px]">
                          <FormLabel>指标名称</FormLabel>
                          <FormControl>
                            <Input placeholder="指标名称" {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name={`indicators.${index}.weight`}
                      render={({ field }) => (
                        <FormItem className="w-24">
                          <FormLabel>权重(%)</FormLabel>
                          <FormControl>
                            <Input
                              type="number"
                              min={0}
                              max={100}
                              {...field}
                              onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                                field.onChange(
                                  e.target.value
                                    ? Number(e.target.value)
                                    : 0,
                                )
                              }
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name={`indicators.${index}.description`}
                      render={({ field }) => (
                        <FormItem className="flex-1 min-w-[200px]">
                          <FormLabel>说明</FormLabel>
                          <FormControl>
                            <Textarea
                              placeholder="指标说明"
                              className="resize-none"
                              rows={1}
                              {...field}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="mt-6 text-destructive hover:text-destructive"
                      onClick={() => removeIndicator(index)}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                ))}
              </div>
            </div>

            <div className="flex justify-end gap-3">
              <Button
                type="button"
                variant="outline"
                onClick={() => onOpenChange(false)}
              >
                取消
              </Button>
              <Button type="submit" disabled={submitting}>
                {submitting ? '保存中...' : '保存'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
};

const TemplateManagePage: React.FC = () => {
  const [templates, setTemplates] = useState<TemplateListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<TemplateListItem | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingTemplate, setDeletingTemplate] = useState<TemplateListItem | null>(null);
  const [deleting, setDeleting] = useState(false);

  const loadTemplates = useCallback(async () => {
    setLoading(true);
    try {
      const result = await fetchTemplates(page, pageSize);
      setTemplates(result.items);
      setTotal(result.total);
    } catch (error: unknown) {
      logger.error('加载模板列表失败', error);
      toast.error('加载模板列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  React.useEffect(() => {
    loadTemplates();
  }, [loadTemplates]);

  const handleEdit = useCallback((record: TemplateListItem) => {
    setEditingTemplate(record);
    setDialogOpen(true);
  }, []);

  const handleToggle = useCallback(
    async (record: TemplateListItem) => {
      try {
        await toggleTemplateStatus(record.id);
        toast.success(
          record.status === 'enabled' ? '已停用模板' : '已启用模板',
        );
        loadTemplates();
      } catch (error: unknown) {
        logger.error('切换状态失败', error);
        toast.error('操作失败');
      }
    },
    [loadTemplates],
  );

  const handleCopy = useCallback(
    async (record: TemplateListItem) => {
      try {
        await copyTemplate(record.id);
        toast.success('模板已复制');
        loadTemplates();
      } catch (error: unknown) {
        logger.error('复制模板失败', error);
        toast.error('复制失败');
      }
    },
    [loadTemplates],
  );

  const handleDeleteClick = useCallback((record: TemplateListItem) => {
    setDeletingTemplate(record);
    setDeleteDialogOpen(true);
  }, []);

  const handleDeleteConfirm = useCallback(async () => {
    if (!deletingTemplate) return;
    setDeleting(true);
    try {
      await deleteTemplate(deletingTemplate.id);
      toast.success('模板已删除');
      setDeleteDialogOpen(false);
      setDeletingTemplate(null);
      loadTemplates();
    } catch (error: unknown) {
      logger.error('删除模板失败', error);
      toast.error('删除失败');
    } finally {
      setDeleting(false);
    }
  }, [deletingTemplate, loadTemplates]);

  const handleCreateNew = useCallback(() => {
    setEditingTemplate(null);
    setDialogOpen(true);
  }, []);

  const handlePageChange = useCallback((newPage: number) => {
    setPage(newPage);
  }, []);

  const actionColumn: TableProps<TemplateListItem>['columns'][number] = {
    title: '操作',
    key: 'action',
    fixed: 'right',
    width: 200,
    render: (_text: unknown, record: TemplateListItem) => (
      <div className="flex gap-2">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => handleEdit(record)}
        >
          <Edit className="size-4" />
          编辑
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => handleToggle(record)}
        >
          <Power className="size-4" />
          {record.status === 'enabled' ? '停用' : '启用'}
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => handleCopy(record)}
        >
          <Copy className="size-4" />
          复制
        </Button>
        <Button
          variant="ghost"
          size="sm"
          className="text-destructive hover:text-destructive"
          onClick={() => handleDeleteClick(record)}
        >
          <Trash2 className="size-4" />
          删除
        </Button>
      </div>
    ),
  };

  const tableColumns: TableProps<TemplateListItem>['columns'] = [
    {
      title: '模板名称',
      dataIndex: 'name',
      fixed: 'left',
      width: 180,
    },
    {
      title: '适用岗位',
      dataIndex: 'position',
      width: 140,
    },
    {
      title: '指标数',
      dataIndex: 'indicatorCount',
      width: 80,
      align: 'center',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      align: 'center',
      render: (status: 'enabled' | 'disabled') => (
        <Badge variant={status === 'enabled' ? 'default' : 'secondary'}>
          {status === 'enabled' ? '启用' : '停用'}
        </Badge>
      ),
    },
    {
      title: '版本',
      dataIndex: 'version',
      width: 80,
      align: 'center',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (val: string) => new Date(val).toLocaleString('zh-CN'),
    },
    actionColumn,
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">模板管理</h1>
          <p className="text-sm text-muted-foreground mt-1">
            配置不同岗位的绩效模板
          </p>
        </div>
        <Button onClick={handleCreateNew}>
          <Plus className="size-4 mr-1" />
          新建模板
        </Button>
      </div>

      <div className="rounded-md border bg-card">
        <Table<TemplateListItem>
          columns={tableColumns}
          dataSource={templates}
          loading={loading}
          rowKey="id"
          scroll={{ x: 1000, y: 500 }}
          pagination={{
            current: page,
            pageSize,
            total,
            onChange: handlePageChange,
          }}
        />
      </div>

      <TemplateDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        editingTemplate={editingTemplate}
        onSuccess={() => {
          setDialogOpen(false);
          loadTemplates();
        }}
      />

      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认删除</DialogTitle>
            <DialogDescription>
              确定要删除模板"{deletingTemplate?.name}"吗？此操作不可恢复。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setDeleteDialogOpen(false)}
              disabled={deleting}
            >
              取消
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteConfirm}
              disabled={deleting}
            >
              {deleting ? '删除中...' : '删除'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default TemplateManagePage;
