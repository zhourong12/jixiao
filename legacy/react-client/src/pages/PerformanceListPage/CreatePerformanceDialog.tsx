import { useState, useEffect, useMemo } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import { createPerformance } from '@/api';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { axiosForBackend } from '@lark-apaas/client-toolkit/utils/getAxiosForBackend';
import type { CreatePerformanceResponse } from '@shared/api.interface';
import { toast } from 'sonner';
import { Checkbox } from '@/components/ui/checkbox';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Input } from '@/components/ui/input';
import { ChevronsUpDown, Search } from 'lucide-react';
import { cn } from '@/lib/utils';

type TargetType = 'all' | 'department' | 'employee';

interface Department {
  id: string;
  name: string;
  memberCount?: number;
}

interface Employee {
  userId: string;
  name: string;
  departmentName?: string;
}

interface MonthPeriodOption {
  periodKey: string;
  name: string;
}

interface CreatePerformanceDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

const CreatePerformanceDialog = ({ open, onOpenChange, onSuccess }: CreatePerformanceDialogProps) => {
  const [departments, setDepartments] = useState<Department[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [targetType, setTargetType] = useState<TargetType>('employee');
  const [selectedDepartmentIds, setSelectedDepartmentIds] = useState<string[]>([]);
  const [selectedEmployees, setSelectedEmployees] = useState<string[]>([]);
  const [selectedPeriod, setSelectedPeriod] = useState<string>('');
  const [monthPeriodOptions, setMonthPeriodOptions] = useState<MonthPeriodOption[]>([]);
  const [periodPopoverOpen, setPeriodPopoverOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [showResult, setShowResult] = useState(false);
  const [result, setResult] = useState<CreatePerformanceResponse | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  // 加载部门和员工数据
  useEffect(() => {
    if (open) {
      loadDepartmentsAndEmployees();
    }
  }, [open]);

  // 重置搜索词当目标类型变化时
  useEffect(() => {
    setSearchQuery('');
    setSelectedDepartmentIds([]);
    setSelectedEmployees([]);
  }, [targetType]);

  const loadDepartmentsAndEmployees = async () => {
    setLoading(true);
    try {
      const [empRes, periodsRes] = await Promise.all([
        axiosForBackend({ url: '/api/employees/all', method: 'GET' }),
        axiosForBackend({ url: '/api/performances/create/month-periods', method: 'GET' }).catch(
          (e) => {
            logger.error('加载考核月度失败', e);
            toast.error('加载考核月度列表失败，请确认已维护「周期与评选」中的月度周期');
            return { data: { items: [] as MonthPeriodOption[] } };
          },
        ),
      ]);
      const empData = empRes.data as { items: Employee[] };
      const empList = empData.items || [];
      setEmployees(empList);

      const periodData = periodsRes.data as { items: MonthPeriodOption[] };
      setMonthPeriodOptions(periodData.items ?? []);

      // 从员工数据中提取部门
      const deptMap = new Map<string, Department>();
      empList.forEach((emp) => {
        if (emp.departmentName) {
          const current = deptMap.get(emp.departmentName);
          if (current) {
            current.memberCount = (current.memberCount || 0) + 1;
          } else {
            deptMap.set(emp.departmentName, {
              id: emp.departmentName,
              name: emp.departmentName,
              memberCount: 1,
            });
          }
        }
      });
      setDepartments(Array.from(deptMap.values()));
    } catch (error) {
      logger.error('加载数据失败', error);
      toast.error('加载数据失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  // 根据搜索词筛选员工
  const filteredEmployees = useMemo(() => {
    if (!searchQuery.trim()) return employees;
    const query = searchQuery.toLowerCase();
    return employees.filter(
      (e) =>
        e.name.toLowerCase().includes(query) ||
        (e.departmentName && e.departmentName.toLowerCase().includes(query))
    );
  }, [employees, searchQuery]);

  // 根据搜索词筛选部门
  const filteredDepartments = useMemo(() => {
    if (!searchQuery.trim()) return departments;
    const query = searchQuery.toLowerCase();
    return departments.filter((d) => d.name.toLowerCase().includes(query));
  }, [departments, searchQuery]);

  // 获取已选部门的所有员工姓名
  const selectedDeptEmployeeNames = useMemo(() => {
    const selectedDeptNames = selectedDepartmentIds;
    return employees
      .filter((e) => e.departmentName && selectedDeptNames.includes(e.departmentName))
      .map((e) => e.name);
  }, [employees, selectedDepartmentIds]);

  // 计算总选中人数
  const totalSelectedCount = useMemo(() => {
    if (targetType === 'all') return employees.length;
    if (targetType === 'department') {
      // 去重计数
      const uniqueNames = new Set(selectedDeptEmployeeNames);
      return uniqueNames.size;
    }
    return selectedEmployees.length;
  }, [targetType, employees.length, selectedDeptEmployeeNames, selectedEmployees]);

  // 切换部门选择
  const toggleDepartment = (deptId: string) => {
    setSelectedDepartmentIds((prev) =>
      prev.includes(deptId) ? prev.filter((id) => id !== deptId) : [...prev, deptId]
    );
  };

  // 切换员工选择
  const toggleEmployee = (name: string) => {
    setSelectedEmployees((prev) =>
      prev.includes(name) ? prev.filter((n) => n !== name) : [...prev, name]
    );
  };

  // 全选/取消全选员工
  const toggleSelectAllEmployees = () => {
    const allNames = filteredEmployees.map((e) => e.name);
    const allSelected = allNames.every((name) => selectedEmployees.includes(name));
    if (allSelected) {
      setSelectedEmployees((prev) => prev.filter((name) => !allNames.includes(name)));
    } else {
      setSelectedEmployees((prev) => Array.from(new Set([...prev, ...allNames])));
    }
  };

  // 全选/取消全选部门
  const toggleSelectAllDepartments = () => {
    const allIds = filteredDepartments.map((d) => d.id);
    const allSelected = allIds.every((id) => selectedDepartmentIds.includes(id));
    if (allSelected) {
      setSelectedDepartmentIds((prev) => prev.filter((id) => !allIds.includes(id)));
    } else {
      setSelectedDepartmentIds((prev) => Array.from(new Set([...prev, ...allIds])));
    }
  };

  const resetForm = () => {
    setTargetType('employee');
    setSelectedDepartmentIds([]);
    setSelectedEmployees([]);
    setSelectedPeriod('');
    setSearchQuery('');
    setShowResult(false);
    setResult(null);
  };

  const handleOpenChange = (newOpen: boolean) => {
    if (!newOpen) {
      resetForm();
    }
    onOpenChange(newOpen);
  };

  const getSelectedEmployeeNames = (): string[] => {
    if (targetType === 'all') {
      return employees.map((e) => e.name);
    }
    if (targetType === 'department') {
      return Array.from(new Set(selectedDeptEmployeeNames));
    }
    return selectedEmployees;
  };

  const handleSubmit = async () => {
    const employeeNames = getSelectedEmployeeNames();
    if (employeeNames.length === 0) {
      toast.error('请至少选择一名员工');
      return;
    }
    if (!selectedPeriod) {
      toast.error('请选择考核月度');
      return;
    }

    setSubmitting(true);
    try {
      const res = await createPerformance({
        employeeNames,
        period: selectedPeriod,
      });
      setResult(res);
      setShowResult(true);
    } catch (error) {
      logger.error('创建绩效失败', error);
      toast.error('创建绩效失败，请重试');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSuccess = () => {
    handleOpenChange(false);
    onSuccess();
  };

  const failedResults = result?.results.filter((r) => !r.success) ?? [];

  // 判断是否全选
  const isAllEmployeesSelected =
    filteredEmployees.length > 0 &&
    filteredEmployees.every((e) => selectedEmployees.includes(e.name));
  const isAllDepartmentsSelected =
    filteredDepartments.length > 0 &&
    filteredDepartments.every((d) => selectedDepartmentIds.includes(d.id));

  // 获取目标类型标签
  const getTargetTypeLabel = (type: TargetType) => {
    switch (type) {
      case 'all':
        return '全员';
      case 'department':
        return '部门';
      case 'employee':
        return '员工';
    }
  };

  const selectedMonthLabel = useMemo(() => {
    const m = monthPeriodOptions.find((p) => p.periodKey === selectedPeriod);
    if (!selectedPeriod) return null;
    if (m?.name) return `${selectedPeriod} · ${m.name}`;
    return selectedPeriod;
  }, [monthPeriodOptions, selectedPeriod]);

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[600px] max-h-[85vh]">
        <DialogHeader>
          <DialogTitle>创建绩效评估</DialogTitle>
        </DialogHeader>

        {loading ? (
          <div className="py-8 text-center text-muted-foreground">加载中...</div>
        ) : showResult && result ? (
          <div className="space-y-4 py-4">
            <div>
              <h3 className="text-base font-semibold">创建结果</h3>
              <div className="mt-2 space-y-1 text-sm">
                <p>总计: {result.total} 条</p>
                <p className="text-success">成功: {result.successCount} 条</p>
                {result.failCount > 0 && (
                  <p className="text-danger">失败: {result.failCount} 条</p>
                )}
              </div>
            </div>

            {failedResults.length > 0 && (
              <div className="space-y-2">
                <h4 className="text-sm font-semibold text-danger">失败详情</h4>
                <div className="max-h-40 overflow-y-auto space-y-1 rounded-md border border-border p-3">
                  {failedResults.map((r, idx) => (
                    <div key={idx} className="text-sm text-danger">
                      {r.employeeName || r.employeeId}: {r.error}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="space-y-4 py-4">
            {/* 评估对象类型选择 */}
            <div className="space-y-2">
              <Label>
                评估对象类型 <span className="text-danger">*</span>
              </Label>
              <Select value={targetType} onValueChange={(v) => setTargetType(v as TargetType)}>
                <SelectTrigger>
                  <SelectValue placeholder="选择评估对象类型" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全员</SelectItem>
                  <SelectItem value="department">部门</SelectItem>
                  <SelectItem value="employee">员工</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* 根据类型显示对应列表 */}
            {targetType === 'all' && (
              <div className="space-y-2">
                <Label>已选人员</Label>
                <div className="rounded-md border border-border bg-accent/30 p-3">
                  <p className="text-sm">
                    已选择 <span className="font-semibold text-primary">{employees.length}</span> 人
                    （全员）
                  </p>
                </div>
              </div>
            )}

            {targetType === 'department' && (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label>
                    选择部门 <span className="text-danger">*</span>
                    <span className="ml-2 text-xs text-muted-foreground">
                      已选择 {selectedDepartmentIds.length} 个部门
                    </span>
                  </Label>
                  <Button variant="ghost" size="sm" onClick={toggleSelectAllDepartments}>
                    {isAllDepartmentsSelected ? '取消全选' : '全选'}
                  </Button>
                </div>

                {/* 搜索框 */}
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    placeholder="搜索部门名称..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="pl-9"
                  />
                </div>

                <div className="border rounded-md p-2">
                  <ScrollArea className="h-[200px]">
                    <div className="space-y-1">
                      {filteredDepartments.length === 0 ? (
                        <div className="text-center text-sm text-muted-foreground py-4">
                          暂无部门数据
                        </div>
                      ) : (
                        filteredDepartments.map((dept) => (
                          <div
                            key={dept.id}
                            className="flex items-center space-x-2 p-2 hover:bg-accent rounded-sm cursor-pointer"
                            onClick={() => toggleDepartment(dept.id)}
                          >
                            <Checkbox
                              checked={selectedDepartmentIds.includes(dept.id)}
                              onCheckedChange={() => toggleDepartment(dept.id)}
                            />
                            <span className="text-sm flex-1">{dept.name}</span>
                            {dept.memberCount !== undefined && (
                              <span className="text-xs text-muted-foreground">
                                {dept.memberCount} 人
                              </span>
                            )}
                          </div>
                        ))
                      )}
                    </div>
                  </ScrollArea>
                </div>

                {selectedDepartmentIds.length > 0 && (
                  <div className="text-xs text-muted-foreground">
                    预计覆盖 <span className="font-medium text-foreground">{totalSelectedCount}</span> 人
                  </div>
                )}
              </div>
            )}

            {targetType === 'employee' && (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label>
                    选择员工 <span className="text-danger">*</span>
                    <span className="ml-2 text-xs text-muted-foreground">
                      已选择 {selectedEmployees.length} 人
                    </span>
                  </Label>
                  <Button variant="ghost" size="sm" onClick={toggleSelectAllEmployees}>
                    {isAllEmployeesSelected ? '取消全选' : '全选'}
                  </Button>
                </div>

                {/* 搜索框 */}
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    placeholder="搜索姓名或部门..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="pl-9"
                  />
                </div>

                <div className="border rounded-md p-2">
                  <ScrollArea className="h-[200px]">
                    <div className="space-y-1">
                      {filteredEmployees.length === 0 ? (
                        <div className="text-center text-sm text-muted-foreground py-4">
                          暂无员工数据
                        </div>
                      ) : (
                        filteredEmployees.map((emp) => (
                          <div
                            key={emp.userId}
                            className="flex items-center space-x-2 p-2 hover:bg-accent rounded-sm cursor-pointer"
                            onClick={() => toggleEmployee(emp.name)}
                          >
                            <Checkbox
                              checked={selectedEmployees.includes(emp.name)}
                              onCheckedChange={() => toggleEmployee(emp.name)}
                            />
                            <span className="text-sm flex-1">{emp.name}</span>
                            {emp.departmentName && (
                              <span className="text-xs text-muted-foreground">
                                {emp.departmentName}
                              </span>
                            )}
                          </div>
                        ))
                      )}
                    </div>
                  </ScrollArea>
                </div>
              </div>
            )}

            {/* 考核月度（可搜索） */}
            <div className="space-y-2">
              <Label>
                考核月度 <span className="text-danger">*</span>
              </Label>
              <Popover open={periodPopoverOpen} onOpenChange={setPeriodPopoverOpen}>
                <PopoverTrigger asChild>
                  <Button
                    type="button"
                    variant="outline"
                    role="combobox"
                    aria-expanded={periodPopoverOpen}
                    className={cn(
                      'w-full justify-between font-normal',
                      !selectedPeriod && 'text-muted-foreground',
                    )}
                  >
                    <span className="truncate text-left">
                      {selectedMonthLabel ?? '请选择考核月度（YYYY-MM）'}
                    </span>
                    <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-[var(--radix-popover-trigger-width)] p-0" align="start">
                  <Command>
                    <CommandInput placeholder="搜索 YYYY-MM 或名称…" />
                    <CommandList>
                      <CommandEmpty>
                        {monthPeriodOptions.length === 0
                          ? '暂无月度周期，请在「周期与评选」中新增'
                          : '无匹配项'}
                      </CommandEmpty>
                      <CommandGroup>
                        {monthPeriodOptions.map((p) => (
                          <CommandItem
                            key={p.periodKey}
                            value={`${p.periodKey} ${p.name || ''}`}
                            onSelect={() => {
                              setSelectedPeriod(p.periodKey);
                              setPeriodPopoverOpen(false);
                            }}
                          >
                            <span className="font-medium">{p.periodKey}</span>
                            {p.name ? (
                              <span className="text-muted-foreground truncate">{p.name}</span>
                            ) : null}
                          </CommandItem>
                        ))}
                      </CommandGroup>
                    </CommandList>
                  </Command>
                </PopoverContent>
              </Popover>
            </div>
          </div>
        )}

        <DialogFooter>
          {showResult ? (
            <Button onClick={handleSuccess}>确定</Button>
          ) : (
            <>
              <Button variant="outline" onClick={() => handleOpenChange(false)}>
                取消
              </Button>
              <Button
                onClick={handleSubmit}
                disabled={
                  submitting ||
                  totalSelectedCount === 0 ||
                  (targetType === 'department' && selectedDepartmentIds.length === 0) ||
                  (targetType === 'employee' && selectedEmployees.length === 0)
                }
              >
                {submitting
                  ? '创建中...'
                  : `创建 (${getTargetTypeLabel(targetType)} ${totalSelectedCount}人)`}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default CreatePerformanceDialog;
