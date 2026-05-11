import { useEffect, useState, useMemo } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@client/src/components/ui/card';
import { Button } from '@client/src/components/ui/button';
import { Badge } from '@client/src/components/ui/badge';
import { Spinner } from '@client/src/components/ui/spinner';
import { UserDisplay } from '@client/src/components/business-ui/user-display';
import { useCurrentUserProfile } from '@lark-apaas/client-toolkit/hooks/useCurrentUserProfile';
import { useMenuPermissions } from '@client/src/hooks/useMenuPermissions';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { CheckCircle2, XCircle, ChevronRight, FileText, ListTodo } from 'lucide-react';
import { getTodos, getOverview } from '@client/src/api';
import type { TodoItem, PerformanceOverview } from '@shared/api.interface';

const statusConfig: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' }> = {
  template_selection: { label: '选择模板', variant: 'default' },
  goal_setting: { label: '目标设定', variant: 'default' },
  goal_rejected: { label: '目标驳回', variant: 'destructive' },
  goal_pending_review: { label: '审核目标', variant: 'secondary' },
  self_review: { label: '自评', variant: 'default' },
  manager_review: { label: '主管评分', variant: 'secondary' },
  dual_manager_review: { label: '主管并行评分', variant: 'secondary' },
  dotted_manager_review: { label: '虚线主管评分', variant: 'secondary' },
  final_review: { label: '终审', variant: 'secondary' },
};

const PENDING_TODO_TYPES = new Set<string>([
  'template_selection',
  'goal_setting',
  'goal_rejected',
  'goal_pending_review',
  'self_review',
  'manager_review',
  'dual_manager_review',
  'dotted_manager_review',
  'final_review',
]);

function ymToParts(ym: string): { year: number; month: number } {
  const [ys, ms] = ym.split('-');
  const year = parseInt(ys, 10);
  const month = parseInt(ms, 10);
  if (!Number.isFinite(year) || !Number.isFinite(month)) {
    const n = new Date();
    return { year: n.getFullYear(), month: n.getMonth() + 1 };
  }
  return { year, month };
}

const HomePage = () => {
  const navigate = useNavigate();
  const userInfo = useCurrentUserProfile();
  const { allow, loading: permLoading } = useMenuPermissions();
  const [todos, setTodos] = useState<TodoItem[]>([]);
  const [overview, setOverview] = useState<PerformanceOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [ym, setYm] = useState(() => {
    const n = new Date();
    return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}`;
  });
  const [tab, setTab] = useState('total');

  const { year, month } = useMemo(() => ymToParts(ym), [ym]);

  useEffect(() => {
    async function fetchData() {
      setLoading(true);
      try {
        const [todosRes, overviewRes] = await Promise.all([
          getTodos({ year, month }),
          getOverview({ year, month }),
        ]);
        setTodos(todosRes.items);
        setOverview(overviewRes);
      } catch {
        /* API 层已打日志 */
      } finally {
        setLoading(false);
      }
    }
    void fetchData();
  }, [year, month]);

  const displayedTodos = useMemo(() => {
    if (tab === 'pending') {
      return todos.filter((t) => PENDING_TODO_TYPES.has(t.type));
    }
    if (tab === 'total') {
      return todos;
    }
    return [];
  }, [todos, tab]);

  if (permLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner className="h-8 w-8" />
      </div>
    );
  }

  if (!allow('home')) {
    return <Navigate to="/todo" replace />;
  }

  if (loading && !overview) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner className="h-8 w-8" />
      </div>
    );
  }

  const o = overview ?? { total: 0, pending: 0, completed: 0, rejected: 0 };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">绩效工作台</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            管理员视图 · 欢迎，
            {userInfo?.user_id ? (
              <UserDisplay userId={userInfo.user_id} size="small" />
            ) : (
              '加载中...'
            )}
          </p>
        </div>
        <div className="flex flex-col gap-1.5 sm:w-56">
          <label className="text-sm font-medium text-foreground">统计月份</label>
          <input
            type="month"
            value={ym}
            onChange={(e) => setYm(e.target.value)}
            className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
          <p className="text-xs text-muted-foreground">四个指标与下方待办均按该月内「最后更新时间」筛选</p>
        </div>
      </div>

      <Tabs value={tab} onValueChange={setTab} className="w-full">
        <TabsList className="grid h-auto w-full grid-cols-2 gap-2 p-2 sm:grid-cols-4">
          <TabsTrigger value="total" className="flex flex-col gap-0.5 py-2">
            <span className="text-xs text-muted-foreground">总绩效</span>
            <span className="text-lg font-bold tabular-nums">{o.total}</span>
          </TabsTrigger>
          <TabsTrigger value="pending" className="flex flex-col gap-0.5 py-2">
            <span className="text-xs text-muted-foreground">待处理</span>
            <span className="text-lg font-bold tabular-nums">{o.pending}</span>
          </TabsTrigger>
          <TabsTrigger value="completed" className="flex flex-col gap-0.5 py-2">
            <span className="text-xs text-muted-foreground">已完成</span>
            <span className="text-lg font-bold tabular-nums">{o.completed}</span>
          </TabsTrigger>
          <TabsTrigger value="rejected" className="flex flex-col gap-0.5 py-2">
            <span className="text-xs text-muted-foreground">已驳回</span>
            <span className="text-lg font-bold tabular-nums">{o.rejected}</span>
          </TabsTrigger>
        </TabsList>

        <TabsContent value="total" className="mt-4">
          <TodoPanel
            todos={displayedTodos}
            emptyHint="本月无待办事项"
            navigate={navigate}
          />
        </TabsContent>
        <TabsContent value="pending" className="mt-4">
          <TodoPanel
            todos={displayedTodos}
            emptyHint="本月无处理中待办"
            navigate={navigate}
          />
        </TabsContent>
        <TabsContent value="completed" className="mt-4">
          <Card className="rounded-md border border-border shadow-sm">
            <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
              <CheckCircle2 className="h-10 w-10 text-success" />
              <p className="text-sm text-muted-foreground">
                本月已完成 <span className="font-semibold text-foreground">{o.completed}</span> 条（以更新时间计）
              </p>
              <Button variant="outline" size="sm" onClick={() => navigate('/performances?status=completed')}>
                在绩效列表中查看
              </Button>
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="rejected" className="mt-4">
          <Card className="rounded-md border border-border shadow-sm">
            <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
              <XCircle className="h-10 w-10 text-destructive" />
              <p className="text-sm text-muted-foreground">
                本月目标被驳回 <span className="font-semibold text-foreground">{o.rejected}</span> 条
              </p>
              <Button variant="outline" size="sm" onClick={() => navigate('/performances?status=goal_rejected')}>
                在绩效列表中查看
              </Button>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
};

function TodoPanel({
  todos,
  emptyHint,
  navigate,
}: {
  todos: TodoItem[];
  emptyHint: string;
  navigate: ReturnType<typeof useNavigate>;
}) {
  return (
    <Card className="rounded-md shadow-sm">
      <CardHeader className="pb-3">
        <CardTitle className="text-base font-semibold">待办任务</CardTitle>
      </CardHeader>
      <CardContent>
        {todos.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <CheckCircle2 className="mb-3 h-12 w-12 text-muted-foreground/40" />
            <p className="text-sm text-muted-foreground">{emptyHint}</p>
          </div>
        ) : (
          <div className="space-y-3">
            <div
              className="flex cursor-pointer items-center justify-between rounded-md border border-primary/30 bg-primary/5 p-4 transition-colors hover:bg-primary/10"
              onClick={() => navigate(`/performances/${todos[0]!.id}`)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  navigate(`/performances/${todos[0]!.id}`);
                }
              }}
            >
              <div className="flex items-center gap-3">
                <ListTodo className="h-5 w-5 shrink-0 text-primary" />
                <div>
                  <p className="text-sm font-medium text-foreground">待办汇总</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    本月共 {todos.length} 项，点击进入最近更新的一条绩效详情
                  </p>
                </div>
              </div>
              <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" />
            </div>
            {todos.map((todo) => {
              const config = statusConfig[todo.type];
              return (
                <div
                  key={todo.id}
                  className="flex cursor-pointer items-center justify-between rounded-md border border-border p-4 transition-colors hover:bg-accent"
                  onClick={() => navigate(`/performances/${todo.id}`)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      navigate(`/performances/${todo.id}`);
                    }
                  }}
                >
                  <div className="flex items-center gap-3">
                    <FileText className="h-5 w-5 text-muted-foreground" />
                    <div>
                      <p className="text-sm font-medium text-foreground">{todo.title}</p>
                      <p className="mt-0.5 text-xs text-muted-foreground">周期：{todo.period}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant={config?.variant ?? 'default'} className="rounded-full text-xs">
                      {config?.label ?? todo.type}
                    </Badge>
                    <ChevronRight className="h-4 w-4 text-muted-foreground" />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

export default HomePage;
