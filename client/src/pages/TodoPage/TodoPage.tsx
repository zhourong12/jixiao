import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@client/src/components/ui/card';
import { Button } from '@client/src/components/ui/button';
import { Badge } from '@client/src/components/ui/badge';
import { Spinner } from '@client/src/components/ui/spinner';
import { UserDisplay } from '@client/src/components/business-ui/user-display';
import { useCurrentUserProfile } from '@lark-apaas/client-toolkit/hooks/useCurrentUserProfile';
import {
  ClipboardList,
  CheckCircle2,
  ChevronRight,
  FileText,
  PenLine,
  ClipboardCheck,
  ListTodo,
} from 'lucide-react';
import { getTodos, getHomeActionCounts } from '@client/src/api';
import type { TodoItem, HomeActionCounts } from '@shared/api.interface';

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

const TodoPage = () => {
  const navigate = useNavigate();
  const userInfo = useCurrentUserProfile();
  const [todos, setTodos] = useState<TodoItem[]>([]);
  const [actionCounts, setActionCounts] = useState<HomeActionCounts | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const now = new Date();
        const y = now.getFullYear();
        const m = now.getMonth() + 1;
        const [todosRes, countsRes] = await Promise.all([
          getTodos({ year: y, month: m }),
          getHomeActionCounts({ year: y, month: m }),
        ]);
        setTodos(todosRes.items);
        setActionCounts(countsRes);
      } catch {
        /* API 层已打日志 */
      } finally {
        setLoading(false);
      }
    }
    void load();
  }, []);

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner className="h-8 w-8" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">待办</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          你好，
          {userInfo?.user_id ? (
            <UserDisplay userId={userInfo.user_id} size="small" />
          ) : (
            '加载中...'
          )}
          · 以下为与当前自然月相关的待办与快捷入口
        </p>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Button
          variant="default"
          className="h-auto justify-between rounded-md py-4"
          onClick={() => navigate('/my-performance')}
        >
          <span className="flex items-center gap-2">
            <FileText className="h-4 w-4" />
            我的绩效
          </span>
          <ChevronRight className="h-4 w-4 opacity-70" />
        </Button>
        <Button
          variant="outline"
          className="h-auto justify-between rounded-md py-4"
          onClick={() => navigate('/performances?focus=need_score')}
        >
          <span className="flex items-center gap-2">
            <PenLine className="h-4 w-4" />
            需评分
          </span>
          <Badge variant="secondary" className="rounded-full tabular-nums">
            {actionCounts?.needScore ?? 0}
          </Badge>
        </Button>
        <Button
          variant="outline"
          className="h-auto justify-between rounded-md py-4"
          onClick={() => navigate('/performances?focus=need_approve_goal')}
        >
          <span className="flex items-center gap-2">
            <ClipboardCheck className="h-4 w-4" />
            需审核
          </span>
          <Badge variant="secondary" className="rounded-full tabular-nums">
            {actionCounts?.needApproveGoal ?? 0}
          </Badge>
        </Button>
      </div>

      <Card className="rounded-md shadow-sm">
        <CardHeader className="pb-3">
          <CardTitle className="text-base font-semibold">待办任务</CardTitle>
        </CardHeader>
        <CardContent>
          {todos.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <CheckCircle2 className="mb-3 h-12 w-12 text-muted-foreground/40" />
              <p className="text-sm text-muted-foreground">本月暂无待办</p>
              <Button variant="ghost" className="mt-2 text-primary hover:text-primary" onClick={() => navigate('/performances')}>
                <ClipboardList className="mr-2 h-4 w-4" />
                查看绩效列表
              </Button>
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
    </div>
  );
};

export default TodoPage;
