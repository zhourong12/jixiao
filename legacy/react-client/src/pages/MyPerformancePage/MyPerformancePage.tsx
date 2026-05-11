import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { FileText, Clock, CheckCircle, AlertCircle, ChevronRight } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@client/src/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@client/src/components/ui/card';
import { Badge } from '@client/src/components/ui/badge';
import { useCurrentUserProfile } from '@lark-apaas/client-toolkit/hooks/useCurrentUserProfile';
import { useSessionUser } from '@/hooks/useSessionUser';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { axiosForBackend } from '@lark-apaas/client-toolkit/utils/getAxiosForBackend';

import type { PerformanceStatus } from '@shared/api.interface';

type PerformanceStatusConfig = {
  label: string;
  color: string;
  icon: React.ReactNode;
};

const statusConfig: Record<PerformanceStatus, PerformanceStatusConfig> = {
  template_selection: {
    label: '待选择模板',
    color: 'bg-gray-100 text-gray-800',
    icon: <Clock className="h-4 w-4" />,
  },
  goal_setting: {
    label: '目标设定中',
    color: 'bg-blue-100 text-blue-800',
    icon: <Clock className="h-4 w-4" />,
  },
  goal_pending_review: {
    label: '待审核目标',
    color: 'bg-yellow-100 text-yellow-800',
    icon: <AlertCircle className="h-4 w-4" />,
  },
  goal_rejected: {
    label: '目标被驳回',
    color: 'bg-red-100 text-red-800',
    icon: <AlertCircle className="h-4 w-4" />,
  },
  self_review: {
    label: '自评中',
    color: 'bg-blue-100 text-blue-800',
    icon: <Clock className="h-4 w-4" />,
  },
  manager_review: {
    label: '上级评分中',
    color: 'bg-purple-100 text-purple-800',
    icon: <Clock className="h-4 w-4" />,
  },
  dual_manager_review: {
    label: '上级评分中',
    color: 'bg-purple-100 text-purple-800',
    icon: <Clock className="h-4 w-4" />,
  },
  dotted_manager_review: {
    label: '虚线上级评分中',
    color: 'bg-purple-100 text-purple-800',
    icon: <Clock className="h-4 w-4" />,
  },
  final_review: {
    label: '待校准',
    color: 'bg-orange-100 text-orange-800',
    icon: <Clock className="h-4 w-4" />,
  },
  completed: {
    label: '已完成',
    color: 'bg-green-100 text-green-800',
    icon: <CheckCircle className="h-4 w-4" />,
  },
};

interface PerformanceListItem {
  id: string;
  employeeId: string;
  period: string;
  status: PerformanceStatus;
  totalScore?: number;
  createdAt: string;
}

export default function MyPerformancePage() {
  const navigate = useNavigate();
  const { user_id: profileUserId } = useCurrentUserProfile();
  const sessionUser = useSessionUser();
  const myUserId = profileUserId || sessionUser.user_id;
  const [records, setRecords] = useState<PerformanceListItem[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchMyPerformances = useCallback(async () => {
    try {
      setLoading(true);
      if (!myUserId) {
        setRecords([]);
        return;
      }
      const response = await axiosForBackend({
        url: '/api/performances',
        method: 'GET',
        params: {
          page: 1,
          pageSize: 100,
        },
      });

      const data = response.data as { items: PerformanceListItem[] };
      const items = data.items || [];
      // 仅展示「我作为被考核人」的绩效，不含待我评分 / 待我审核目标等上级视角记录
      setRecords(items.filter((item) => item.employeeId === myUserId));
    } catch (error) {
      logger.error('获取我的绩效列表失败', error);
      toast.error('获取绩效列表失败');
    } finally {
      setLoading(false);
    }
  }, [myUserId]);

  useEffect(() => {
    if (!sessionUser.loaded) return;
    void fetchMyPerformances();
  }, [sessionUser.loaded, fetchMyPerformances]);

  const handleViewDetail = (id: string) => {
    navigate(`/performances/${id}`);
  };

  const getActionText = (status: PerformanceStatus): string => {
    switch (status) {
      case 'goal_setting':
        return '去设定目标';
      case 'goal_rejected':
        return '去修改目标';
      case 'self_review':
        return '去自评';
      case 'completed':
        return '查看详情';
      default:
        return '查看进度';
    }
  };

  if (loading) {
    return (
      <div className="p-6">
        <div className="mb-6">
          <h1 className="text-2xl font-bold">我的绩效</h1>
          <p className="text-muted-foreground">查看和管理您的绩效考核</p>
        </div>
        <div className="flex items-center justify-center h-64">
          <div className="text-muted-foreground">加载中...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold">我的绩效</h1>
        <p className="text-muted-foreground">查看和管理您的绩效考核</p>
      </div>

      {records.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <FileText className="h-12 w-12 text-muted-foreground mb-4" />
            <p className="text-muted-foreground">暂无绩效记录</p>
            <p className="text-sm text-muted-foreground mt-1">
              请联系管理员为您创建绩效考核
            </p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {records.map((record) => {
            const config = statusConfig[record.status];
            return (
              <Card
                key={record.id}
                className="cursor-pointer hover:shadow-md transition-shadow"
                onClick={() => handleViewDetail(record.id)}
              >
                <CardContent className="p-6">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-4">
                      <div className={`p-2 rounded-lg ${config.color}`}>
                        {config.icon}
                      </div>
                      <div>
                        <h3 className="font-semibold text-lg">
                          {record.period} 绩效考核
                        </h3>
                        <div className="flex items-center gap-2 mt-1">
                          <Badge variant="secondary" className={config.color}>
                            {config.label}
                          </Badge>
                          {record.totalScore !== undefined && record.totalScore !== null && (
                            <span className="text-sm font-medium text-green-600">
                              总分: {record.totalScore}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-4">
                      <span className="text-sm text-muted-foreground">
                        创建时间:{' '}
                        {(() => {
                          const d = record.createdAt ? new Date(record.createdAt) : null;
                          return d && !Number.isNaN(d.getTime())
                            ? d.toLocaleString('zh-CN')
                            : '—';
                        })()}
                      </span>
                      <Button variant="ghost" size="sm">
                        {getActionText(record.status)}
                        <ChevronRight className="h-4 w-4 ml-1" />
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
