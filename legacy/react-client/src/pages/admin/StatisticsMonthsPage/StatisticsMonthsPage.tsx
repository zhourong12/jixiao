import React, { useCallback, useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import {
  Award,
  CalendarRange,
  Pencil,
  Plus,
  RefreshCw,
  Trash2,
  Trophy,
  UserPlus,
} from 'lucide-react';
import { toast } from 'sonner';
import { showConfirm } from '@lark-apaas/client-toolkit';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Spinner } from '@/components/ui/spinner';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useMenuPermissions } from '@/hooks/useMenuPermissions';
import type {
  EvaluationPeriodItem,
  DepartmentOption,
  PerformanceLeaderboardItem,
  AwardTypeItem,
  PeriodAwardItem,
  EvaluationPeriodType,
  AwardTypeScope,
} from '@shared/api.interface';
import {
  getEvaluationPeriods,
  createEvaluationPeriod,
  updateEvaluationPeriod,
  deleteEvaluationPeriod,
  getEvaluationLeaderboard,
  getPerformancePeriods,
  getDepartmentOptions,
  getAwardTypes,
  getPeriodAwards,
  createPeriodAward,
  deletePeriodAward,
  getEmployees,
} from '@/api';

function awardVisibleForPeriod(scope: AwardTypeScope, periodType: EvaluationPeriodType): boolean {
  if (scope === 'both') return true;
  return scope === periodType;
}

const PeriodCrudSection: React.FC<{
  periodType: EvaluationPeriodType;
  title: string;
  description: string;
  keyPlaceholder: string;
}> = ({
  periodType,
  title,
  description,
  keyPlaceholder,
}) => {
  const [items, setItems] = useState<EvaluationPeriodItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<EvaluationPeriodItem | null>(null);
  const [formKey, setFormKey] = useState('');
  const [formName, setFormName] = useState('');
  const [formSort, setFormSort] = useState('0');
  const [formStatus, setFormStatus] = useState('open');
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getEvaluationPeriods(periodType);
      setItems(res.items);
    } catch (e) {
      logger.error('加载评选周期失败', e);
      toast.error('加载评选周期失败');
    } finally {
      setLoading(false);
    }
  }, [periodType]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    setFormKey('');
    setFormName('');
    setFormSort('0');
    setFormStatus('open');
    setDialogOpen(true);
  };

  const openEdit = (m: EvaluationPeriodItem) => {
    setEditing(m);
    setFormKey(m.periodKey);
    setFormName(m.name);
    setFormSort(String(m.sortOrder));
    setFormStatus(m.status || 'open');
    setDialogOpen(true);
  };

  const validateKey = (k: string): boolean => {
    const t = k.trim();
    if (periodType === 'month') {
      if (!/^\d{4}-\d{2}$/.test(t)) {
        toast.error('月度 key 须为 YYYY-MM');
        return false;
      }
      return true;
    }
    if (!/^\d{4}-Q[1-4]$/.test(t)) {
      toast.error('季度 key 须为 YYYY-Q1～Q4');
      return false;
    }
    return true;
  };

  const handleSave = async () => {
    if (!validateKey(formKey)) return;
    setSaving(true);
    try {
      const sortOrder = Number.parseInt(formSort, 10);
      const so = Number.isFinite(sortOrder) ? sortOrder : 0;
      if (editing) {
        await updateEvaluationPeriod(editing.id, {
          periodKey: formKey.trim(),
          name: formName.trim(),
          sortOrder: so,
          status: formStatus,
        });
        toast.success('已更新');
      } else {
        await createEvaluationPeriod({
          periodType,
          periodKey: formKey.trim(),
          name: formName.trim(),
          sortOrder: so,
          status: formStatus,
        });
        toast.success('已添加');
      }
      setDialogOpen(false);
      await load();
    } catch (e) {
      logger.error('保存评选周期失败', e);
      toast.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (m: EvaluationPeriodItem) => {
    if (!(await showConfirm(`确定删除周期「${m.periodKey}」吗？`))) return;
    try {
      await deleteEvaluationPeriod(m.id);
      toast.success('已删除');
      await load();
    } catch (e) {
      logger.error('删除失败', e);
      toast.error('删除失败');
    }
  };

  return (
    <>
      <Card className="shadow-sm">
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <div>
            <CardTitle className="text-base font-semibold">{title}</CardTitle>
            <CardDescription>{description}</CardDescription>
          </div>
          <div className="flex gap-2">
            <Button type="button" variant="outline" size="sm" onClick={() => void load()}>
              <RefreshCw className="mr-1 h-4 w-4" />
              刷新
            </Button>
            <Button type="button" size="sm" onClick={openCreate}>
              <Plus className="mr-1 h-4 w-4" />
              新增
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="flex justify-center py-12">
              <Spinner className="h-8 w-8" />
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>排序</TableHead>
                  <TableHead>周期 key</TableHead>
                  <TableHead>名称</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead className="w-[140px] text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center text-muted-foreground">
                      暂无数据，请点击「新增」
                    </TableCell>
                  </TableRow>
                ) : (
                  items.map((m) => (
                    <TableRow key={m.id}>
                      <TableCell>{m.sortOrder}</TableCell>
                      <TableCell className="font-medium">{m.periodKey}</TableCell>
                      <TableCell>{m.name || '—'}</TableCell>
                      <TableCell>{m.status || '—'}</TableCell>
                      <TableCell className="text-right">
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8"
                          onClick={() => openEdit(m)}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8 text-destructive"
                          onClick={() => void handleDelete(m)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? '编辑周期' : '新增周期'}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-3 py-2">
            <div className="space-y-2">
              <Label>{periodType === 'month' ? 'YYYY-MM' : 'YYYY-Qn'}</Label>
              <Input
                value={formKey}
                onChange={(e) => setFormKey(e.target.value)}
                placeholder={keyPlaceholder}
              />
            </div>
            <div className="space-y-2">
              <Label>显示名称（可选）</Label>
              <Input value={formName} onChange={(e) => setFormName(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>排序（越小越靠前）</Label>
              <Input value={formSort} onChange={(e) => setFormSort(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>状态</Label>
              <Select value={formStatus} onValueChange={setFormStatus}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="open">open</SelectItem>
                  <SelectItem value="draft">draft</SelectItem>
                  <SelectItem value="closed">closed</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
              取消
            </Button>
            <Button type="button" disabled={saving} onClick={() => void handleSave()}>
              {saving ? '保存中…' : '保存'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
};

const StatisticsMonthsPage: React.FC = () => {
  const { allow, loading: permLoading } = useMenuPermissions();

  const [mainTab, setMainTab] = useState('months');

  const [monthPeriods, setMonthPeriods] = useState<EvaluationPeriodItem[]>([]);
  const [quarterPeriods, setQuarterPeriods] = useState<EvaluationPeriodItem[]>([]);
  const [performancePeriods, setPerformancePeriods] = useState<string[]>([]);

  const [departments, setDepartments] = useState<DepartmentOption[]>([]);
  const [selectedDeptIds, setSelectedDeptIds] = useState<Set<string>>(new Set());

  const [lbScope, setLbScope] = useState<'month' | 'quarter' | ''>('');
  const [lbSelectKey, setLbSelectKey] = useState('');
  const [lbCustomKey, setLbCustomKey] = useState('');
  const [leaderboard, setLeaderboard] = useState<PerformanceLeaderboardItem[]>([]);
  const [leaderLoading, setLeaderLoading] = useState(false);

  const [awardTypes, setAwardTypes] = useState<AwardTypeItem[]>([]);
  const [awardPeriodType, setAwardPeriodType] = useState<EvaluationPeriodType>('month');
  const [awardPeriodId, setAwardPeriodId] = useState('');
  const [periodAwards, setPeriodAwards] = useState<PeriodAwardItem[]>([]);
  const [awardsLoading, setAwardsLoading] = useState(false);
  const [addAwardCode, setAddAwardCode] = useState('');
  const [empKeyword, setEmpKeyword] = useState('');
  const [empPickId, setEmpPickId] = useState('');
  const [empPickName, setEmpPickName] = useState('');
  const [addRemark, setAddRemark] = useState('');
  const [addingAward, setAddingAward] = useState(false);
  const [leaderboardPrefill, setLeaderboardPrefill] = useState<{
    employeeId: string;
    recordId?: string;
  } | null>(null);

  const loadPeriodLists = useCallback(async () => {
    try {
      const [m, q] = await Promise.all([
        getEvaluationPeriods('month'),
        getEvaluationPeriods('quarter'),
      ]);
      setMonthPeriods(m.items);
      setQuarterPeriods(q.items);
    } catch (e) {
      logger.error('加载周期列表失败', e);
    }
  }, []);

  const loadPerformancePeriods = useCallback(async () => {
    try {
      const res = await getPerformancePeriods();
      setPerformancePeriods(res.items);
    } catch (e) {
      logger.error('加载绩效周期失败', e);
    }
  }, []);

  useEffect(() => {
    void loadPeriodLists();
    void loadPerformancePeriods();
  }, [loadPeriodLists, loadPerformancePeriods]);

  useEffect(() => {
    (async () => {
      try {
        const res = await getDepartmentOptions();
        setDepartments(res.items);
      } catch (e) {
        logger.error('加载部门列表失败', e);
      }
    })();
  }, []);

  useEffect(() => {
    (async () => {
      try {
        const res = await getAwardTypes();
        setAwardTypes(res.items);
      } catch (e) {
        logger.error('加载奖项类型失败', e);
      }
    })();
  }, []);

  const periodsForAward = awardPeriodType === 'month' ? monthPeriods : quarterPeriods;

  useEffect(() => {
    if (!awardPeriodId && periodsForAward.length) {
      setAwardPeriodId(periodsForAward[0]!.id);
    }
  }, [awardPeriodType, periodsForAward, awardPeriodId]);

  useEffect(() => {
    if (mainTab !== 'awards' || !leaderboardPrefill) return;
    setEmpPickId(leaderboardPrefill.employeeId);
    setEmpPickName('');
    setAddRemark('');
  }, [mainTab, leaderboardPrefill]);

  const loadPeriodAwardsList = async (pid: string) => {
    if (!pid) return;
    setAwardsLoading(true);
    try {
      const res = await getPeriodAwards(pid);
      setPeriodAwards(res.items);
    } catch (e) {
      logger.error('加载获奖记录失败', e);
      toast.error('加载获奖记录失败');
    } finally {
      setAwardsLoading(false);
    }
  };

  useEffect(() => {
    if (mainTab === 'awards' && awardPeriodId) {
      void loadPeriodAwardsList(awardPeriodId);
    }
  }, [mainTab, awardPeriodId]);

  const effectiveLbKey = (lbCustomKey.trim() || lbSelectKey).trim();

  const loadLeaderboard = async () => {
    if (!lbScope) {
      toast.error('请先选择季度或月度');
      return;
    }
    const k = effectiveLbKey;
    if (lbScope === 'month') {
      if (!/^\d{4}-\d{2}$/.test(k)) {
        toast.error('月度请选择或输入 YYYY-MM');
        return;
      }
    } else if (!/^\d{4}-Q[1-4]$/.test(k)) {
      toast.error('季度请选择或输入 YYYY-Q1～Q4');
      return;
    }
    setLeaderLoading(true);
    try {
      const res = await getEvaluationLeaderboard({
        scope: lbScope,
        key: k,
        departmentIds: selectedDeptIds.size ? [...selectedDeptIds] : undefined,
      });
      setLeaderboard(res.items);
    } catch (e) {
      logger.error('加载排行榜失败', e);
      toast.error('加载排行榜失败');
    } finally {
      setLeaderLoading(false);
    }
  };

  const toggleDept = (id: string) => {
    setSelectedDeptIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const filteredAwardCodes = awardTypes.filter((a) =>
    awardVisibleForPeriod(a.scope, awardPeriodType),
  );

  const handleAddAward = async () => {
    if (!awardPeriodId) {
      toast.error('请先选择评选周期');
      return;
    }
    if (!addAwardCode) {
      toast.error('请选择奖项');
      return;
    }
    if (!empPickId.trim()) {
      toast.error('请填写或选择员工');
      return;
    }
    setAddingAward(true);
    try {
      await createPeriodAward({
        periodId: awardPeriodId,
        awardCode: addAwardCode,
        employeeId: empPickId.trim(),
        performanceRecordId:
          leaderboardPrefill?.recordId &&
          leaderboardPrefill.employeeId === empPickId.trim()
            ? leaderboardPrefill.recordId
            : undefined,
        remark: addRemark.trim() || undefined,
      });
      toast.success('已添加获奖记录');
      setLeaderboardPrefill(null);
      setAddRemark('');
      await loadPeriodAwardsList(awardPeriodId);
    } catch (e) {
      logger.error('添加获奖失败', e);
      toast.error('添加失败');
    } finally {
      setAddingAward(false);
    }
  };

  const handleDeleteAward = async (row: PeriodAwardItem) => {
    if (!(await showConfirm('确定删除该条获奖记录吗？'))) return;
    try {
      await deletePeriodAward(row.id);
      toast.success('已删除');
      await loadPeriodAwardsList(awardPeriodId);
    } catch (e) {
      logger.error('删除失败', e);
      toast.error('删除失败');
    }
  };

  const searchEmployees = async () => {
    const kw = empKeyword.trim();
    if (!kw) {
      toast.error('请输入关键词');
      return;
    }
    try {
      const res = await getEmployees({ page: 1, pageSize: 20, keyword: kw });
      if (!res.items.length) {
        toast.message('未找到员工');
        return;
      }
      const first = res.items[0]!;
      setEmpPickId(first.userId);
      setEmpPickName(first.name);
      toast.success(`已选用：${first.name}（${first.userId}）`);
    } catch (e) {
      logger.error('搜索员工失败', e);
      toast.error('搜索员工失败');
    }
  };

  if (permLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner className="h-8 w-8" />
      </div>
    );
  }

  if (!allow('admin_statistics_months')) {
    return <Navigate to="/" replace />;
  }

  const periodOptionsForLb =
    lbScope === 'month'
      ? monthPeriods
      : lbScope === 'quarter'
        ? quarterPeriods.length
          ? quarterPeriods
          : performancePeriods.map((p) => ({
              id: `__virt__${p}`,
              periodType: 'quarter' as const,
              periodKey: p,
              name: '',
              sortOrder: 0,
              status: 'open',
              createdAt: '',
              updatedAt: '',
            }))
        : [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">周期与评选</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          维护<strong>自然月</strong>与<strong>绩效季度</strong>周期；<strong>排行参考</strong>支持按月度或按季度筛选；<strong>评选录入</strong>将员工挂到具体奖项，与排行解耦。
        </p>
      </div>

      <Tabs value={mainTab} onValueChange={setMainTab} className="w-full">
        <TabsList className="flex flex-wrap h-auto gap-1">
          <TabsTrigger value="months" className="gap-1.5">
            <CalendarRange className="h-4 w-4" />
            月度周期
          </TabsTrigger>
          <TabsTrigger value="quarters" className="gap-1.5">
            <CalendarRange className="h-4 w-4" />
            季度周期
          </TabsTrigger>
          <TabsTrigger value="rank" className="gap-1.5">
            <Trophy className="h-4 w-4" />
            排行参考
          </TabsTrigger>
          <TabsTrigger value="awards" className="gap-1.5">
            <Award className="h-4 w-4" />
            评选录入
          </TabsTrigger>
        </TabsList>

        <TabsContent value="months" className="mt-4 space-y-4">
          <PeriodCrudSection
            periodType="month"
            title="月度周期"
            description="自然月维度（YYYY-MM），可与工作台月度统计口径对齐"
            keyPlaceholder="2026-05"
          />
        </TabsContent>

        <TabsContent value="quarters" className="mt-4 space-y-4">
          <PeriodCrudSection
            periodType="quarter"
            title="季度周期"
            description="与绩效记录中的季度 period 一致，如 2026-Q1"
            keyPlaceholder="2026-Q1"
          />
        </TabsContent>

        <TabsContent value="rank" className="mt-4 space-y-4">
          <Card className="shadow-sm">
            <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-2 space-y-0">
              <div>
                <CardTitle className="text-base font-semibold">筛选</CardTitle>
                <CardDescription>
                  选择<strong>月度 / 季度</strong>与周期 key，可按部门多选筛选；季度下拉优先使用已维护的季度周期，若无则列出绩效记录中出现过的
                  period。
                </CardDescription>
              </div>
              <Button type="button" variant="outline" size="sm" onClick={() => void loadPeriodLists()}>
                <RefreshCw className="mr-1 h-4 w-4" />
                刷新周期
              </Button>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <div className="space-y-2">
                  <Label>统计方式</Label>
                  <Select
                    value={lbScope || undefined}
                    onValueChange={(v) => {
                      setLbScope(v as 'month' | 'quarter');
                      setLbSelectKey('');
                      setLbCustomKey('');
                    }}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="请选择" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="month">按月度</SelectItem>
                      <SelectItem value="quarter">按季度</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>
                    {lbScope === 'month' ? '月度选择' : lbScope === 'quarter' ? '季度选择' : '周期'}
                  </Label>
                  <Select
                    disabled={!lbScope}
                    value={lbSelectKey || undefined}
                    onValueChange={(v) => {
                      setLbSelectKey(v);
                      setLbCustomKey('');
                    }}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="请选择" />
                    </SelectTrigger>
                    <SelectContent>
                      {lbScope === 'month'
                        ? monthPeriods.map((p) => (
                            <SelectItem key={p.id} value={p.periodKey}>
                              {p.periodKey}
                            </SelectItem>
                          ))
                        : periodOptionsForLb.map((p) => (
                            <SelectItem
                              key={p.id}
                              value={p.periodKey}
                            >
                              {p.periodKey}
                            </SelectItem>
                          ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>或手动输入 key</Label>
                  <Input
                    placeholder={
                      !lbScope ? '请先选择季度或月度' : lbScope === 'month' ? '2026-05' : '2026-Q1'
                    }
                    value={lbCustomKey}
                    disabled={!lbScope}
                    onChange={(e) => {
                      setLbCustomKey(e.target.value);
                      if (e.target.value.trim()) setLbSelectKey('');
                    }}
                  />
                </div>
                <div className="flex items-end">
                  <Button type="button" className="w-full sm:w-auto" onClick={() => void loadLeaderboard()}>
                    查询排名
                  </Button>
                </div>
              </div>

              {lbScope === 'quarter' && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="h-auto p-0 text-xs text-muted-foreground"
                  onClick={() => void loadPerformancePeriods()}
                >
                  刷新绩效记录中的季度列表
                </Button>
              )}

              <div className="space-y-2">
                <Label>部门（多选，不选表示全部）</Label>
                <div className="max-h-40 overflow-y-auto rounded-md border border-border p-3">
                  {departments.length === 0 ? (
                    <p className="text-sm text-muted-foreground">暂无部门数据</p>
                  ) : (
                    <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                      {departments.map((d) => (
                        <label
                          key={d.id}
                          className="flex cursor-pointer items-center gap-2 text-sm"
                        >
                          <Checkbox
                            checked={selectedDeptIds.has(d.id)}
                            onCheckedChange={() => toggleDept(d.id)}
                          />
                          <span className="truncate">{d.name || d.id}</span>
                        </label>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {leaderLoading ? (
                <div className="flex justify-center py-12">
                  <Spinner className="h-8 w-8" />
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-14">排名</TableHead>
                      <TableHead>员工</TableHead>
                      <TableHead>部门</TableHead>
                      <TableHead>总分</TableHead>
                      <TableHead>绩效周期</TableHead>
                      <TableHead className="w-52">操作</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {leaderboard.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={6} className="text-center text-muted-foreground">
                          暂无数据，请选择条件后查询
                        </TableCell>
                      </TableRow>
                    ) : (
                      leaderboard.map((row) => (
                        <TableRow key={`${row.employeeId}-${row.recordId}`}>
                          <TableCell className="font-medium">{row.rank}</TableCell>
                          <TableCell>{row.employeeName}</TableCell>
                          <TableCell>{row.departmentName || row.departmentId || '—'}</TableCell>
                          <TableCell>{row.totalScore.toFixed(2)}</TableCell>
                          <TableCell>{row.performancePeriod}</TableCell>
                          <TableCell className="space-x-2">
                            <Link
                              to={`/performances/${row.recordId}`}
                              className="text-sm font-medium text-primary underline-offset-4 hover:underline"
                            >
                              查看
                            </Link>
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              className="ml-2 h-8"
                              onClick={() => {
                                if (!lbScope) {
                                  toast.error('请先选择统计方式与周期');
                                  return;
                                }
                                setLeaderboardPrefill({
                                  employeeId: row.employeeId,
                                  recordId: row.recordId,
                                });
                                setAwardPeriodType(lbScope);
                                const match = (lbScope === 'month' ? monthPeriods : quarterPeriods).find(
                                  (p) => p.periodKey === effectiveLbKey,
                                );
                                if (match) {
                                  setAwardPeriodId(match.id);
                                } else {
                                  setAwardPeriodId('');
                                  toast.message('请先在「评选录入」中选择或新建对应周期');
                                }
                                setMainTab('awards');
                              }}
                            >
                              <UserPlus className="mr-1 h-3 w-3" />
                              录入奖项
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="awards" className="mt-4 space-y-4">
          <Card className="shadow-sm">
            <CardHeader>
              <CardTitle className="text-base font-semibold">评选周期与获奖名单</CardTitle>
              <CardDescription>
                选择周期后维护各奖项下的员工；奖项与周期类型需匹配（如「季度之星」仅用于季度周期）。
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label>周期类型</Label>
                  <Select
                    value={awardPeriodType}
                    onValueChange={(v) => {
                      setAwardPeriodType(v as EvaluationPeriodType);
                      setAwardPeriodId('');
                    }}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="month">月度</SelectItem>
                      <SelectItem value="quarter">季度</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>具体周期</Label>
                  <Select
                    value={awardPeriodId || undefined}
                    onValueChange={(v) => setAwardPeriodId(v)}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="请选择" />
                    </SelectTrigger>
                    <SelectContent>
                      {periodsForAward.map((p) => (
                        <SelectItem key={p.id} value={p.id}>
                          {p.periodKey} {p.name ? `· ${p.name}` : ''}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="rounded-md border border-border p-4 space-y-3">
                <div className="font-medium text-sm">添加获奖</div>
                {leaderboardPrefill && (
                  <p className="text-xs text-muted-foreground">
                    已从排行带入员工 {leaderboardPrefill.employeeId}
                    {leaderboardPrefill.recordId ? ' 及绩效记录' : ''}，请选择奖项后提交。
                  </p>
                )}
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label>奖项</Label>
                    <Select value={addAwardCode || undefined} onValueChange={setAddAwardCode}>
                      <SelectTrigger>
                        <SelectValue placeholder="选择奖项" />
                      </SelectTrigger>
                      <SelectContent>
                        {filteredAwardCodes.map((a) => (
                          <SelectItem key={a.code} value={a.code}>
                            {a.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label>员工 userId</Label>
                    <Input
                      value={empPickId}
                      onChange={(e) => setEmpPickId(e.target.value)}
                      placeholder="open_id / user_id"
                    />
                    {empPickName ? (
                      <p className="text-xs text-muted-foreground">已选：{empPickName}</p>
                    ) : null}
                  </div>
                </div>
                <div className="flex flex-wrap gap-2 items-end">
                  <div className="space-y-2 flex-1 min-w-[160px]">
                    <Label>按关键词搜索员工</Label>
                    <Input
                      value={empKeyword}
                      onChange={(e) => setEmpKeyword(e.target.value)}
                      placeholder="姓名等"
                    />
                  </div>
                  <Button type="button" variant="secondary" onClick={() => void searchEmployees()}>
                    搜索并选用首条
                  </Button>
                </div>
                <div className="space-y-2">
                  <Label>备注（可选）</Label>
                  <Input value={addRemark} onChange={(e) => setAddRemark(e.target.value)} />
                </div>
                <Button type="button" disabled={addingAward} onClick={() => void handleAddAward()}>
                  {addingAward ? '提交中…' : '添加获奖记录'}
                </Button>
              </div>

              {awardsLoading ? (
                <div className="flex justify-center py-12">
                  <Spinner className="h-8 w-8" />
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>奖项</TableHead>
                      <TableHead>员工</TableHead>
                      <TableHead>备注</TableHead>
                      <TableHead>创建人</TableHead>
                      <TableHead className="w-24">操作</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {periodAwards.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={5} className="text-center text-muted-foreground">
                          暂无获奖记录
                        </TableCell>
                      </TableRow>
                    ) : (
                      periodAwards.map((r) => (
                        <TableRow key={r.id}>
                          <TableCell>
                            {r.awardName}
                            <span className="ml-1 text-xs text-muted-foreground">({r.awardCode})</span>
                          </TableCell>
                          <TableCell>
                            {r.employeeName || r.employeeId}
                            <span className="block text-xs text-muted-foreground">{r.employeeId}</span>
                          </TableCell>
                          <TableCell>{r.remark || '—'}</TableCell>
                          <TableCell>{r.createdBy || '—'}</TableCell>
                          <TableCell>
                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              className="text-destructive"
                              onClick={() => void handleDeleteAward(r)}
                            >
                              删除
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
};

export default StatisticsMonthsPage;
