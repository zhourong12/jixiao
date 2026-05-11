import { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  CheckCircle,
  Clock,
  AlertCircle,
  XCircle,
  FileText,
  ArrowLeft,
  Save,
  Send,
  X,
  ThumbsUp,
  ThumbsDown,
  LayoutTemplate,
} from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@client/src/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@client/src/components/ui/card';
import { Badge } from '@client/src/components/ui/badge';
import { Input } from '@client/src/components/ui/input';
import { Textarea } from '@client/src/components/ui/textarea';
import { Progress } from '@client/src/components/ui/progress';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from '@client/src/components/ui/dialog';
import { TiptapEditorComplete } from '@client/src/components/business-ui/tiptap-editor';
import { UserDisplay } from '@client/src/components/business-ui/user-display';
import { useCurrentUserProfile } from '@lark-apaas/client-toolkit/hooks/useCurrentUserProfile';
import { useSessionUser } from '@/hooks/useSessionUser';
import { useMenuPermissions } from '@/hooks/useMenuPermissions';

import {
  getPerformanceDetail,
  savePerformanceDraft,
  submitPerformanceReview,
  rejectPerformance,
  approveGoal,
  finalReview,
  calibratePerformance,
  selectTemplate,
  getTemplates,
} from '@client/src/api';
import type {
  PerformanceRecord,
  PerformanceStatus,
  ReviewItem,
  GoalSettingItem,
  TemplateListItem,
} from '@shared/api.interface';

/** 表单未填评分不设默认 0，避免与真实 0 分混淆 */
type ReviewFormRow = {
  indicatorName: string;
  score?: number;
  comment: string;
};

type PerformanceStatusWithDescription = {
  label: string;
  description: string;
  color: string;
};

const statusConfig: Record<PerformanceStatus, PerformanceStatusWithDescription> = {
  template_selection: {
    label: '待选择模板',
    description: '请选择绩效模板',
    color: 'bg-gray-100 text-gray-800',
  },
  goal_setting: {
    label: '目标设定中',
    description: '请填写绩效目标',
    color: 'bg-blue-100 text-blue-800',
  },
  goal_pending_review: {
    label: '待审核目标',
    description: '等待上级审核目标',
    color: 'bg-yellow-100 text-yellow-800',
  },
  goal_rejected: {
    label: '目标被驳回',
    description: '目标需修改后重新提交',
    color: 'bg-red-100 text-red-800',
  },
  self_review: {
    label: '自评中',
    description: '请完成自评',
    color: 'bg-blue-100 text-blue-800',
  },
  manager_review: {
    label: '上级评分中',
    description: '等待直属上级评分（无虚线上级时仅此环节）',
    color: 'bg-purple-100 text-purple-800',
  },
  dual_manager_review: {
    label: '上级评分中',
    description: '直属与虚线上级并行评分，双方均提交后进入校准',
    color: 'bg-purple-100 text-purple-800',
  },
  dotted_manager_review: {
    label: '虚线上级评分中',
    description: '等待虚线上级评分',
    color: 'bg-purple-100 text-purple-800',
  },
  final_review: {
    label: '待校准',
    description: '等待管理员绩效校准；校准前直属/虚线上级仍可修改评分并提交',
    color: 'bg-orange-100 text-orange-800',
  },
  completed: {
    label: '已完成',
    description: '绩效评估已完成',
    color: 'bg-green-100 text-green-800',
  },
};

const steps = [
  { key: 'template', label: '选择模板', statuses: ['template_selection'] as PerformanceStatus[] },
  { key: 'goal', label: '目标设定', statuses: ['goal_setting', 'goal_pending_review', 'goal_rejected'] as PerformanceStatus[] },
  { key: 'self', label: '员工自评', statuses: ['self_review'] as PerformanceStatus[] },
  {
    key: 'manager',
    label: '上级评分',
    statuses: ['manager_review', 'dotted_manager_review', 'dual_manager_review'] as PerformanceStatus[],
  },
  { key: 'final', label: '绩效校准', statuses: ['final_review'] as PerformanceStatus[] },
  { key: 'done', label: '已完成', statuses: ['completed'] as PerformanceStatus[] },
];

const PerformanceDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const userInfo = useCurrentUserProfile();
  const sessionUser = useSessionUser();
  const { allow: menuAllow } = useMenuPermissions();
  const isSuperAdminUser = sessionUser.role === 'super_admin';
  const canFinalCalibrationUser =
    menuAllow('performance_review_admin') || isSuperAdminUser;
  const currentUserId = useMemo(
    () => userInfo?.user_id || sessionUser.user_id || undefined,
    [userInfo?.user_id, sessionUser.user_id],
  );

  const [performance, setPerformance] = useState<PerformanceRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [goalSettings, setGoalSettings] = useState<GoalSettingItem[]>([]);
  const [formContent, setFormContent] = useState<ReviewFormRow[]>([]);
  const [rejectReason, setRejectReason] = useState('');
  const [rejectOpen, setRejectOpen] = useState(false);
  const [finalRejectOpen, setFinalRejectOpen] = useState(false);
  const [finalReturnToStage, setFinalReturnToStage] = useState<PerformanceStatus>('self_review');
  const [templates, setTemplates] = useState<TemplateListItem[]>([]);
  const [templateLoading, setTemplateLoading] = useState(false);
  const [selectingTemplate, setSelectingTemplate] = useState(false);
  const [calibrationScore, setCalibrationScore] = useState<string>('');
  const [personalSummary, setPersonalSummary] = useState<string>('');

  // 获取绩效详情
  useEffect(() => {
    if (!id) return;
    const fetchDetail = async () => {
      try {
        setLoading(true);
        const data = await getPerformanceDetail(id);
        setPerformance(data);
      } catch {
        toast.error('获取绩效详情失败');
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [id]);

  // 获取可用模板列表
  useEffect(() => {
    if (performance?.status !== 'template_selection') return;
    const fetchTemplates = async () => {
      try {
        setTemplateLoading(true);
        const data = await getTemplates();
        setTemplates(data.filter((t) => t.status === 'enabled'));
      } catch {
        toast.error('获取模板列表失败');
      } finally {
        setTemplateLoading(false);
      }
    };
    fetchTemplates();
  }, [performance?.status]);

  // 初始化目标设定
  useEffect(() => {
    if (!performance?.indicators) return;
    const initial: GoalSettingItem[] = performance.indicators.map((ind) => ({
      indicatorName: ind.name,
      weight: ind.weight,
    }));
    setGoalSettings(initial);
  }, [performance]);

  // 初始化表单内容（自评/上级评分/虚线上级评分）
  useEffect(() => {
    if (!performance?.indicators) return;
    const isEmployee = currentUserId === performance.employeeId;
    const isManager = currentUserId === performance.managerId;
    const isDottedManager = currentUserId === performance.dottedManagerId;

    let existingContent: ReviewItem[] | undefined;
    if (isEmployee) {
      existingContent = performance.selfReview;
    } else if (isManager) {
      existingContent = performance.managerReview;
    } else if (isDottedManager) {
      existingContent = performance.dottedManagerReview;
    }

    const initial: ReviewFormRow[] = performance.indicators.map((ind) => {
      const existing = existingContent?.find((item) => item.indicatorName === ind.name);
      const hasScore =
        existing != null &&
        typeof existing.score === 'number' &&
        !Number.isNaN(existing.score);
      return {
        indicatorName: ind.name,
        ...(hasScore ? { score: existing!.score } : {}),
        comment: existing?.comment ?? '',
      };
    });
    setFormContent(initial);
    setPersonalSummary(performance.personalSummary ?? '');
  }, [performance, currentUserId]);

  const handleScoreChange = useCallback((index: number, value: string) => {
    setFormContent((prev) => {
      const next = [...prev];
      if (value === '') {
        next[index] = { ...next[index]!, score: undefined };
      } else {
        const n = parseFloat(value);
        next[index] = {
          ...next[index]!,
          score: Number.isFinite(n) ? n : undefined,
        };
      }
      return next;
    });
  }, []);

  const handleCommentChange = useCallback((index: number, value: string) => {
    setFormContent((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], comment: value };
      return next;
    });
  }, []);

  // 保存草稿
  const handleSaveDraft = async () => {
    if (!id || !performance) return;
    try {
      setSubmitting(true);
      const isGoalStage = performance.status === 'goal_setting' || performance.status === 'goal_rejected';
      let reviewType: 'goal' | 'self' | 'manager' | 'dotted_manager';
      let content: ReviewItem[] | GoalSettingItem[];
      if (isGoalStage) {
        reviewType = 'goal';
        content = goalSettings;
      } else if (performance.status === 'self_review') {
        reviewType = 'self';
        content = JSON.parse(JSON.stringify(formContent)) as ReviewItem[];
      } else if (performance.status === 'manager_review') {
        reviewType = 'manager';
        content = JSON.parse(JSON.stringify(formContent)) as ReviewItem[];
      } else if (performance.status === 'dual_manager_review') {
        reviewType = currentUserId === performance.managerId ? 'manager' : 'dotted_manager';
        content = JSON.parse(JSON.stringify(formContent)) as ReviewItem[];
      } else if (performance.status === 'dotted_manager_review') {
        reviewType = 'dotted_manager';
        content = JSON.parse(JSON.stringify(formContent)) as ReviewItem[];
      } else if (performance.status === 'final_review') {
        reviewType = currentUserId === performance.managerId ? 'manager' : 'dotted_manager';
        content = JSON.parse(JSON.stringify(formContent)) as ReviewItem[];
      } else {
        reviewType = 'self';
        content = JSON.parse(JSON.stringify(formContent)) as ReviewItem[];
      }
      await savePerformanceDraft(id, {
        reviewType,
        content,
        ...(reviewType === 'self' ? { personalSummary } : {}),
      });
      toast.success('草稿已保存');
    } catch {
      toast.error('保存草稿失败');
    } finally {
      setSubmitting(false);
    }
  };

  // 提交目标
  const handleSubmitGoal = async () => {
    if (!id) return;
    try {
      setSubmitting(true);
      await submitPerformanceReview(id, { reviewType: 'goal', content: goalSettings });
      toast.success('目标已提交');
      const data = await getPerformanceDetail(id);
      setPerformance(data);
    } catch {
      toast.error('提交目标失败');
    } finally {
      setSubmitting(false);
    }
  };

  // 审核目标（批准/驳回）
  const handleApproveGoal = async (approved: boolean) => {
    if (!id) return;
    if (!approved && !rejectReason.trim()) {
      toast.error('请输入驳回原因');
      return;
    }
    try {
      setSubmitting(true);
      await approveGoal(id, { approved, rejectionReason: approved ? undefined : rejectReason.trim() });
      toast.success(approved ? '已批准目标' : '已驳回目标');
      setRejectOpen(false);
      setRejectReason('');
      const data = await getPerformanceDetail(id);
      setPerformance(data);
    } catch {
      toast.error(approved ? '批准目标失败' : '驳回目标失败');
    } finally {
      setSubmitting(false);
    }
  };

  // 提交自评/上级评分/虚线上级评分
  const handleSubmitReview = async () => {
    if (!id || !performance) return;
    const incomplete = formContent.filter(
      (item) => item.score === undefined || item.score === null || Number.isNaN(item.score),
    );
    if (incomplete.length > 0) {
      toast.error('请为所有指标填写评分');
      return;
    }
    const outOfRange = formContent.filter((item) => (item.score as number) < 0 || (item.score as number) > 100);
    if (outOfRange.length > 0) {
      toast.error('评分需在 0～100 之间');
      return;
    }
    const reviewType: 'self' | 'manager' | 'dotted_manager' =
      performance.status === 'self_review'
        ? 'self'
        : performance.status === 'manager_review'
          ? 'manager'
          : performance.status === 'dual_manager_review'
            ? currentUserId === performance.managerId
              ? 'manager'
              : 'dotted_manager'
            : performance.status === 'dotted_manager_review'
              ? 'dotted_manager'
              : performance.status === 'final_review'
                ? currentUserId === performance.managerId
                  ? 'manager'
                  : 'dotted_manager'
                : 'dotted_manager';
    try {
      setSubmitting(true);
      const content: ReviewItem[] = formContent.map((c) => ({
        indicatorName: c.indicatorName,
        score: c.score!,
        comment: c.comment ?? '',
      }));
      await submitPerformanceReview(id, {
        reviewType,
        content,
        ...(reviewType === 'self' ? { personalSummary } : {}),
      });
      toast.success('提交成功');
      const data = await getPerformanceDetail(id);
      setPerformance(data);
    } catch {
      toast.error('提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  // 选择模板
  const handleSelectTemplate = async (templateId: string) => {
    if (!id) return;
    try {
      setSelectingTemplate(true);
      await selectTemplate(id, { templateId });
      toast.success('模板选择成功');
      const data = await getPerformanceDetail(id);
      setPerformance(data);
    } catch {
      toast.error('选择模板失败');
    } finally {
      setSelectingTemplate(false);
    }
  };

  // 绩效校准
  const handleCalibrate = async (approved: boolean) => {
    if (!id) return;
    if (!approved && !rejectReason.trim()) {
      toast.error('请输入驳回原因');
      return;
    }
    try {
      setSubmitting(true);
      const score = calibrationScore.trim() ? parseFloat(calibrationScore) : undefined;
      await calibratePerformance(id, {
        approved,
        finalScore: score,
        rejectionReason: approved ? undefined : rejectReason.trim(),
        returnToStage: approved ? undefined : finalReturnToStage,
      });
      toast.success(approved ? '校准通过' : '已驳回');
      setFinalRejectOpen(false);
      setRejectReason('');
      setCalibrationScore('');
      const data = await getPerformanceDetail(id);
      setPerformance(data);
    } catch {
      toast.error(approved ? '校准通过失败' : '驳回失败');
    } finally {
      setSubmitting(false);
    }
  };

  // 终审（保留兼容）
  const handleFinalReview = async (approved: boolean) => {
    if (!id) return;
    if (!approved && !rejectReason.trim()) {
      toast.error('请输入驳回原因');
      return;
    }
    try {
      setSubmitting(true);
      await finalReview(id, {
        approved,
        rejectionReason: approved ? undefined : rejectReason.trim(),
        returnToStage: approved ? undefined : finalReturnToStage,
      });
      toast.success(approved ? '终审已通过' : '已驳回');
      setFinalRejectOpen(false);
      setRejectReason('');
      const data = await getPerformanceDetail(id);
      setPerformance(data);
    } catch {
      toast.error(approved ? '终审通过失败' : '驳回失败');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">加载中...</p>
      </div>
    );
  }

  if (!performance) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">未找到绩效记录</p>
      </div>
    );
  }

  const statusInfo = statusConfig[performance.status];
  const indicators = performance.indicators || [];

  // 权限判断
  const isEmployee = currentUserId === performance.employeeId;
  const isManager = currentUserId === performance.managerId;
  const isDottedManager = currentUserId === performance.dottedManagerId;

  // 根据状态和角色确定可编辑性
  const canEditFinalReviewManager =
    performance.status === 'final_review' &&
    (isManager || (isDottedManager && !!performance.dottedManagerId));

  const canEdit =
    (performance.status === 'goal_setting' && isEmployee) ||
    (performance.status === 'goal_rejected' && isEmployee) ||
    (performance.status === 'self_review' && isEmployee) ||
    (performance.status === 'manager_review' && isManager) ||
    (performance.status === 'dual_manager_review' && (isManager || isDottedManager)) ||
    (performance.status === 'dotted_manager_review' && isDottedManager) ||
    canEditFinalReviewManager;

  const canSubmit =
    (performance.status === 'goal_setting' && isEmployee) ||
    (performance.status === 'goal_rejected' && isEmployee) ||
    (performance.status === 'self_review' && isEmployee) ||
    (performance.status === 'manager_review' && isManager) ||
    (performance.status === 'dual_manager_review' && (isManager || isDottedManager)) ||
    (performance.status === 'dotted_manager_review' && isDottedManager) ||
    canEditFinalReviewManager;

  const canGoalApprove =
    (performance.status === 'goal_pending_review') && (isManager || isDottedManager);

  const canFinalApprove =
    performance.status === 'final_review' && canFinalCalibrationUser;

  // 校准阶段评分可见性：具备终审/校准权限或超管
  const showScoresInFinalReview =
    performance.status === 'final_review' && canFinalCalibrationUser;

  // 计算当前步骤索引
  const currentStepIndex = steps.findIndex((s) => s.statuses.includes(performance.status));
  const progressValue = currentStepIndex >= 0 ? ((currentStepIndex + 1) / steps.length) * 100 : 0;

  // 渲染目标设定表单
  const renderGoalSettingForm = (editable: boolean) => (
    <Card>
      <CardHeader>
        <CardTitle>{editable ? '设定绩效目标' : '已提交目标'}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {indicators.map((indicator, index) => (
            <div key={indicator.name} className="p-4 border rounded-lg space-y-3">
              <div className="flex items-center justify-between">
                <div>
                  <h4 className="font-medium">{indicator.name}</h4>
                  {indicator.description && (
                    <p className="text-sm text-muted-foreground mt-1">{indicator.description}</p>
                  )}
                </div>
                <Badge variant="outline">权重 {indicator.weight}%</Badge>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );

  // 渲染评分表单（自评/上级评分/虚线上级评分）
  const renderReviewForm = (
    title: string,
    editable: boolean,
    showPreviousReviews: boolean,
  ) => (
    <>
      {showPreviousReviews && performance.selfReview && (
        <Card>
          <CardHeader>
            <CardTitle>员工自评</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {performance.selfReview.map((item) => (
                <div key={item.indicatorName} className="p-4 border rounded-lg bg-accent/50">
                  <h4 className="font-medium">{item.indicatorName}</h4>
                  <div className="flex items-center gap-4 mt-2">
                    <span className="text-sm text-muted-foreground">
                      自评分数: <span className="font-semibold text-primary">{item.score}</span>
                    </span>
                  </div>
                  {item.comment && (
                    <p className="text-sm text-muted-foreground mt-2">{item.comment}</p>
                  )}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {showPreviousReviews && (
        <Card>
          <CardHeader>
            <CardTitle>自我总结</CardTitle>
            <CardDescription>员工在自评阶段填写，与模板指标一并流转可见</CardDescription>
          </CardHeader>
          <CardContent>
            {performance.personalSummary?.trim() ? (
              <p className="text-sm text-foreground whitespace-pre-wrap">{performance.personalSummary}</p>
            ) : (
              <p className="text-sm text-muted-foreground">未填写</p>
            )}
          </CardContent>
        </Card>
      )}

      {performance.managerReview && performance.managerReview.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>直属上级评分</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {performance.managerReview.map((item) => (
                <div key={item.indicatorName} className="p-4 border rounded-lg bg-accent/50">
                  <h4 className="font-medium">{item.indicatorName}</h4>
                  <div className="flex items-center gap-4 mt-2">
                    <span className="text-sm text-muted-foreground">
                      评分: <span className="font-semibold text-purple-600">{item.score}</span>
                    </span>
                  </div>
                  {item.comment && (
                    <p className="text-sm text-muted-foreground mt-2">{item.comment}</p>
                  )}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {performance.dottedManagerId &&
        performance.dottedManagerReview &&
        performance.dottedManagerReview.length > 0 && (
          <Card>
            <CardHeader>
              <CardTitle>虚线上级评分</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                {performance.dottedManagerReview.map((item) => (
                  <div key={item.indicatorName} className="p-4 border rounded-lg bg-accent/50">
                    <h4 className="font-medium">{item.indicatorName}</h4>
                    <div className="flex items-center gap-4 mt-2">
                      <span className="text-sm text-muted-foreground">
                        评分: <span className="font-semibold text-purple-600">{item.score}</span>
                      </span>
                    </div>
                    {item.comment && (
                      <p className="text-sm text-muted-foreground mt-2">{item.comment}</p>
                    )}
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        )}

      {showPreviousReviews &&
        performance.dottedManagerId &&
        performance.reviewMergedIndicators &&
        performance.reviewMergedIndicators.length > 0 && (
          <Card className="border-primary/20">
            <CardHeader>
              <CardTitle>按角色权重的分项合成</CardTitle>
              <CardDescription>
                {performance.reviewRoleWeights
                  ? `直属上级权重 ${(performance.reviewRoleWeights.managerWeight * 100).toFixed(0)}%，虚线上级权重 ${(performance.reviewRoleWeights.dottedWeight * 100).toFixed(0)}%。分项合成 = 直属分×直属权重 + 虚线分×虚线权重；总分由系统按模板指标权重汇总后再按角色权重合成（与列表总分口径一致）。`
                  : '分项合成 = 直属分×直属权重 + 虚线分×虚线权重'}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="rounded-md border overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-muted/50">
                      <th className="text-left p-2 font-medium">指标</th>
                      <th className="text-right p-2 font-medium">直属</th>
                      <th className="text-right p-2 font-medium">虚线</th>
                      <th className="text-right p-2 font-medium">分项合成</th>
                    </tr>
                  </thead>
                  <tbody>
                    {performance.reviewMergedIndicators.map((row) => (
                      <tr key={row.indicatorName} className="border-b last:border-0">
                        <td className="p-2">{row.indicatorName}</td>
                        <td className="p-2 text-right text-muted-foreground">
                          {row.managerScore ?? '—'}
                        </td>
                        <td className="p-2 text-right text-muted-foreground">
                          {row.dottedScore ?? '—'}
                        </td>
                        <td className="p-2 text-right font-medium text-primary">
                          {row.mergedScore ?? '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="text-sm">
                <span className="text-muted-foreground">合成总分（双方提交后）:</span>{' '}
                <span className="font-semibold text-primary">
                  {performance.reviewMergedTotal ?? '待双方提交完整评分'}
                </span>
              </p>
            </CardContent>
          </Card>
        )}

      <Card>
        <CardHeader>
          <CardTitle>{title}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {indicators.map((indicator, index) => (
              <div key={indicator.name} className="p-4 border rounded-lg space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <h4 className="font-medium">{indicator.name}</h4>
                    {indicator.description && (
                      <p className="text-sm text-muted-foreground mt-1">{indicator.description}</p>
                    )}
                  </div>
                  <Badge variant="outline">权重 {indicator.weight}%</Badge>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="text-sm font-medium mb-1 block">评分</label>
                    <Input
                      type="number"
                      min={0}
                      max={100}
                      value={
                        formContent[index]?.score === undefined ? '' : String(formContent[index]!.score)
                      }
                      onChange={(e) => handleScoreChange(index, e.target.value)}
                      disabled={!editable}
                      placeholder="请输入评分"
                      className="w-full"
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium mb-1 block">评价说明</label>
                    <Textarea
                      value={formContent[index]?.comment ?? ''}
                      onChange={(e) => handleCommentChange(index, e.target.value)}
                      disabled={!editable}
                      placeholder="请输入评价说明"
                      className="w-full min-h-[80px]"
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {!showPreviousReviews && (
        <Card>
          <CardHeader>
            <CardTitle>自我总结</CardTitle>
            <CardDescription>
              除以上指标外，请对本周期工作做整体总结（提交后上级评分、校准及归档均可见）
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Textarea
              value={personalSummary}
              onChange={(e) => setPersonalSummary(e.target.value)}
              disabled={!editable}
              placeholder="请填写自我总结，如主要产出、不足与改进计划等"
              rows={5}
              className="w-full min-h-[120px]"
            />
          </CardContent>
        </Card>
      )}
    </>
  );

  /** 员工在上级评分及之后节点查看已提交的自评与自我总结（只读） */
  const renderEmployeeReadonlySelfAndSummary = () => (
    <>
      {performance.selfReview && performance.selfReview.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>员工自评</CardTitle>
            <CardDescription>您已提交的自评内容，上级评分及后续环节均可查看</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {performance.selfReview.map((item) => (
                <div key={item.indicatorName} className="rounded-lg border bg-accent/50 p-4">
                  <h4 className="font-medium">{item.indicatorName}</h4>
                  <div className="mt-2 flex items-center gap-4">
                    <span className="text-sm text-muted-foreground">
                      自评分数: <span className="font-semibold text-primary">{item.score}</span>
                    </span>
                  </div>
                  {item.comment && (
                    <p className="mt-2 text-sm text-muted-foreground">{item.comment}</p>
                  )}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
      <Card>
        <CardHeader>
          <CardTitle>自我总结</CardTitle>
          <CardDescription>您已提交的内容，上级评分及后续环节均可查看</CardDescription>
        </CardHeader>
        <CardContent>
          {performance.personalSummary?.trim() ? (
            <p className="whitespace-pre-wrap text-sm text-foreground">{performance.personalSummary}</p>
          ) : (
            <p className="text-sm text-muted-foreground">未填写</p>
          )}
        </CardContent>
      </Card>
    </>
  );

  return (
    <div className="space-y-6">
      {/* 顶部操作栏 */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="sm" onClick={() => navigate(-1)}>
            <ArrowLeft className="w-4 h-4 mr-1" />
            返回
          </Button>
          <h1 className="text-2xl font-bold">绩效详情</h1>
        </div>
        <Badge className={statusInfo.color}>
          <Clock className="w-3 h-3 mr-1" />
          {statusInfo.label}
        </Badge>
      </div>

      {/* 状态描述 */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <FileText className="w-4 h-4" />
            <span>{statusInfo.description}</span>
          </div>
        </CardContent>
      </Card>

      {isSuperAdminUser &&
        ['manager_review', 'dual_manager_review', 'dotted_manager_review'].includes(performance.status) &&
        performance.indicators.length > 0 && (
          <Card className="border-primary/25 shadow-sm">
            <CardHeader>
              <CardTitle>上级评分监控（超管）</CardTitle>
              <CardDescription>
                直属上级{performance.managerName ? `（${performance.managerName}）` : ''}
                {performance.dottedManagerId
                  ? ` · 虚线上级${performance.dottedManagerName ? `（${performance.dottedManagerName}）` : ''}`
                  : ' · 无虚线上级'}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {performance.reviewMergedIndicators && performance.reviewMergedIndicators.length > 0 ? (
                <div className="overflow-x-auto rounded-md border">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b bg-muted/50">
                        <th className="p-2 text-left font-medium">指标</th>
                        <th className="p-2 text-right font-medium">直属评分</th>
                        <th className="p-2 text-right font-medium">虚线评分</th>
                        <th className="p-2 text-right font-medium">分项合成</th>
                      </tr>
                    </thead>
                    <tbody>
                      {performance.reviewMergedIndicators.map((row) => (
                        <tr key={row.indicatorName} className="border-b last:border-0">
                          <td className="p-2">{row.indicatorName}</td>
                          <td className="p-2 text-right text-muted-foreground">
                            {row.managerScore ?? '—'}
                          </td>
                          <td className="p-2 text-right text-muted-foreground">
                            {row.dottedScore ?? '—'}
                          </td>
                          <td className="p-2 text-right font-medium text-primary">
                            {row.mergedScore ?? '—'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">
                  暂无按模板指标汇总的评分表（可能未选模板或尚无草稿）。可在下方流程中查看已填写的直属/虚线评分卡片。
                </p>
              )}
              {performance.reviewRoleWeights && (
                <p className="text-xs text-muted-foreground">
                  角色权重：直属 {Math.round(performance.reviewRoleWeights.managerWeight * 100)}% / 虚线{' '}
                  {Math.round(performance.reviewRoleWeights.dottedWeight * 100)}%
                </p>
              )}
              <p className="text-xs text-muted-foreground">
                合成总分（双方均提交完整评分后）：{performance.reviewMergedTotal ?? '—'}
              </p>
            </CardContent>
          </Card>
        )}

      {/* 基本信息 */}
      <Card>
        <CardHeader>
          <CardTitle>基本信息</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div>
              <p className="text-sm text-muted-foreground">考核周期</p>
              <p className="font-medium">{performance.period}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">考核模板</p>
              <p className="font-medium">{performance.templateName}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">被考核人</p>
              <UserDisplay
                value={{
                  user_id: performance.employeeId,
                  name: performance.employeeName || performance.employeeId,
                }}
                size="small"
              />
            </div>
            <div>
              <p className="text-sm text-muted-foreground">总分</p>
              <p className="font-medium text-lg text-primary">
                {performance.totalScore ?? '-'}
              </p>
              {isEmployee && performance.status !== 'completed' && (
                <p className="mt-1 text-xs text-muted-foreground">
                  流程中会随自评、上级评分与校准更新；下方为各方按模板权重的加权分
                </p>
              )}
            </div>
          </div>
          {['manager_review', 'dual_manager_review', 'dotted_manager_review', 'final_review', 'completed'].includes(
            performance.status,
          ) && (
            <div className="mt-4 grid grid-cols-2 md:grid-cols-4 gap-4 border-t border-border pt-4">
              <div>
                <p className="text-sm text-muted-foreground">直属上级加权分</p>
                <p className="font-medium text-lg">{performance.managerWeightedTotal ?? '-'}</p>
              </div>
              {performance.dottedManagerId ? (
                <div>
                  <p className="text-sm text-muted-foreground">虚线上级加权分</p>
                  <p className="font-medium text-lg">{performance.dottedManagerWeightedTotal ?? '-'}</p>
                </div>
              ) : null}
            </div>
          )}
        </CardContent>
      </Card>

      {/* 流程进度 */}
      <Card>
        <CardHeader>
          <CardTitle>考核流程</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            <Progress value={progressValue} className="h-2" />
            <div className="flex justify-between">
              {steps.map((step, index) => {
                const isActive = index <= currentStepIndex;
                const isCurrent = index === currentStepIndex;
                return (
                  <div
                    key={step.key}
                    className={`flex flex-col items-center text-xs ${
                      isCurrent
                        ? 'text-primary font-semibold'
                        : isActive
                          ? 'text-foreground'
                          : 'text-muted-foreground'
                    }`}
                  >
                    <div
                      className={`w-8 h-8 rounded-full flex items-center justify-center mb-1 ${
                        isCurrent
                          ? 'bg-primary text-primary-foreground'
                          : isActive
                            ? 'bg-primary/20 text-primary'
                            : 'bg-muted text-muted-foreground'
                      }`}
                    >
                      {index + 1}
                    </div>
                    <span>{step.label}</span>
                  </div>
                );
              })}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 驳回原因 */}
      {performance.rejectionReason && (
        <Card className="border-danger/30">
          <CardHeader>
            <CardTitle className="text-danger flex items-center gap-2">
              <AlertCircle className="w-4 h-4" />
              驳回原因
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm">{performance.rejectionReason}</p>
          </CardContent>
        </Card>
      )}

      {/* 根据状态渲染不同内容 */}
      {performance.status === 'template_selection' && isEmployee && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <LayoutTemplate className="w-5 h-5" />
              请选择绩效模板
            </CardTitle>
          </CardHeader>
          <CardContent>
            {templateLoading ? (
              <div className="flex items-center justify-center py-8">
                <p className="text-muted-foreground">模板加载中...</p>
              </div>
            ) : templates.length === 0 ? (
              <div className="flex items-center justify-center py-8">
                <p className="text-muted-foreground">暂无可用模板，请联系管理员</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {templates.map((t) => (
                  <Card
                    key={t.id}
                    className="cursor-pointer hover:border-primary transition-colors border-2"
                    onClick={() => handleSelectTemplate(t.id)}
                  >
                    <CardHeader className="pb-3">
                      <CardTitle className="text-base">{t.name}</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <p className="text-sm text-muted-foreground mb-2">{t.position}</p>
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-muted-foreground">
                          {t.indicatorCount} 项指标
                        </span>
                        <Badge variant="outline">v{t.version}</Badge>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {performance.status === 'goal_setting' && renderGoalSettingForm(isEmployee)}

      {performance.status === 'goal_pending_review' && (
        <>
          {renderGoalSettingForm(false)}
          {canGoalApprove && (
            <div className="flex items-center justify-end gap-3">
              <Button
                variant="outline"
                onClick={() => setRejectOpen(true)}
                disabled={submitting}
              >
                <ThumbsDown className="w-4 h-4 mr-1" />
                驳回
              </Button>
              <Button
                onClick={() => handleApproveGoal(true)}
                disabled={submitting}
              >
                <ThumbsUp className="w-4 h-4 mr-1" />
                批准
              </Button>
            </div>
          )}
        </>
      )}

      {performance.status === 'goal_rejected' && renderGoalSettingForm(isEmployee)}

      {performance.status === 'self_review' && renderReviewForm('员工自评', isEmployee, false)}

      {performance.status === 'manager_review' && isManager && renderReviewForm('直属上级评分', true, true)}

      {performance.status === 'manager_review' && isEmployee && (
        <div className="space-y-4">
          {renderEmployeeReadonlySelfAndSummary()}
          <Card>
            <CardContent className="pt-6">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Clock className="h-4 w-4" />
                <span>您的自评已提交，正在等待直属上级评分。</span>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {performance.status === 'dual_manager_review' && (isManager || isDottedManager) && (
        renderReviewForm(isManager ? '直属上级评分' : '虚线上级评分', isManager || isDottedManager, true)
      )}

      {performance.status === 'dual_manager_review' && isEmployee && (
        <div className="space-y-4">
          {renderEmployeeReadonlySelfAndSummary()}
          <Card>
            <CardContent className="pt-6">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Clock className="h-4 w-4" />
                <span>您的自评已提交，正在由直属上级与虚线上级并行评分，完成后将进入绩效校准。</span>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {performance.status === 'dotted_manager_review' && isDottedManager && renderReviewForm('虚线上级评分', true, true)}

      {performance.status === 'dotted_manager_review' && isEmployee && (
        <div className="space-y-4">
          {renderEmployeeReadonlySelfAndSummary()}
          <Card>
            <CardContent className="pt-6">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Clock className="h-4 w-4" />
                <span>您的自评已提交，正在等待虚线上级评分。</span>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {performance.status === 'final_review' && (
        <>
          {(isManager || (isDottedManager && performance.dottedManagerId)) &&
            renderReviewForm(
              isManager ? '直属上级评分（校准前可修改）' : '虚线上级评分（校准前可修改）',
              true,
              true,
            )}

          {/* 管理员校准区域 */}
          {canFinalCalibrationUser && (
            <Card className="border-primary/30">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <CheckCircle className="w-5 h-5 text-primary" />
                  绩效校准
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  <div className="flex items-center gap-4">
                    <span className="text-sm text-muted-foreground">当前总分:</span>
                    <span className="text-lg font-semibold text-primary">
                      {performance.totalScore ?? '-'}
                    </span>
                  </div>
                  <div>
                    <label className="text-sm font-medium mb-1 block">
                      校准后分数（可选，不填则保持原分数）
                    </label>
                    <Input
                      type="number"
                      min={0}
                      max={100}
                      placeholder="请输入校准后分数"
                      value={calibrationScore}
                      onChange={(e) => setCalibrationScore(e.target.value)}
                      className="w-full max-w-[200px]"
                    />
                  </div>
                  <div className="flex items-center gap-3">
                    <Button
                      onClick={() => handleCalibrate(true)}
                      disabled={submitting}
                    >
                      <ThumbsUp className="w-4 h-4 mr-1" />
                      校准通过
                    </Button>
                    <Button
                      variant="outline"
                      onClick={() => setFinalRejectOpen(true)}
                      disabled={submitting}
                    >
                      <ThumbsDown className="w-4 h-4 mr-1" />
                      校准驳回
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          {/* 非管理员：员工仍可查看本人已提交的自评与自我总结；他人显示待校准提示（上级改分见上方表单） */}
          {!showScoresInFinalReview &&
            (isEmployee || !(isManager || (isDottedManager && performance.dottedManagerId))) && (
              <div className="space-y-4">
                {isEmployee && renderEmployeeReadonlySelfAndSummary()}
                {!(isManager || (isDottedManager && performance.dottedManagerId)) && (
                  <Card>
                    <CardContent className="pt-6">
                      <div className="flex items-center gap-2 text-muted-foreground">
                        <Clock className="h-4 w-4" />
                        <span>评分待校准，校准完成后可见</span>
                      </div>
                    </CardContent>
                  </Card>
                )}
              </div>
            )}

          {/* 管理员可见所有评分 */}
          {showScoresInFinalReview && performance.selfReview && (
            <Card>
              <CardHeader>
                <CardTitle>员工自评</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {performance.selfReview.map((item) => (
                    <div key={item.indicatorName} className="p-4 border rounded-lg bg-accent/50">
                      <h4 className="font-medium">{item.indicatorName}</h4>
                      <div className="flex items-center gap-4 mt-2">
                        <span className="text-sm text-muted-foreground">
                          自评分数: <span className="font-semibold text-primary">{item.score}</span>
                        </span>
                      </div>
                      {item.comment && (
                        <p className="text-sm text-muted-foreground mt-2">{item.comment}</p>
                      )}
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
          {showScoresInFinalReview && (
            <Card>
              <CardHeader>
                <CardTitle>自我总结</CardTitle>
                <CardDescription>员工在自评阶段填写</CardDescription>
              </CardHeader>
              <CardContent>
                {performance.personalSummary?.trim() ? (
                  <p className="text-sm text-foreground whitespace-pre-wrap">{performance.personalSummary}</p>
                ) : (
                  <p className="text-sm text-muted-foreground">未填写</p>
                )}
              </CardContent>
            </Card>
          )}
          {showScoresInFinalReview && performance.managerReview && (
            <Card>
              <CardHeader>
                <CardTitle>直属上级评分</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {performance.managerReview.map((item) => (
                    <div key={item.indicatorName} className="p-4 border rounded-lg bg-accent/50">
                      <h4 className="font-medium">{item.indicatorName}</h4>
                      <div className="flex items-center gap-4 mt-2">
                        <span className="text-sm text-muted-foreground">
                          评分: <span className="font-semibold text-purple-600">{item.score}</span>
                        </span>
                      </div>
                      {item.comment && (
                        <p className="text-sm text-muted-foreground mt-2">{item.comment}</p>
                      )}
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
          {showScoresInFinalReview && performance.dottedManagerReview && (
            <Card>
              <CardHeader>
                <CardTitle>虚线上级评分</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {performance.dottedManagerReview.map((item) => (
                    <div key={item.indicatorName} className="p-4 border rounded-lg bg-accent/50">
                      <h4 className="font-medium">{item.indicatorName}</h4>
                      <div className="flex items-center gap-4 mt-2">
                        <span className="text-sm text-muted-foreground">
                          评分: <span className="font-semibold text-purple-600">{item.score}</span>
                        </span>
                      </div>
                      {item.comment && (
                        <p className="text-sm text-muted-foreground mt-2">{item.comment}</p>
                      )}
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
        </>
      )}

      {performance.status === 'completed' && (
        <>
          {performance.goalSetting && (
            <Card>
              <CardHeader>
                <CardTitle>绩效目标</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {performance.goalSetting.map((item) => (
                    <div key={item.indicatorName} className="p-4 border rounded-lg">
                      <div className="flex items-center justify-between">
                        <h4 className="font-medium">{item.indicatorName}</h4>
                        <Badge variant="outline">权重 {item.weight}%</Badge>
                      </div>
                      <p className="text-sm text-muted-foreground mt-1">目标值: {item.target}</p>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
          {performance.selfReview && (
            <Card>
              <CardHeader>
                <CardTitle>员工自评</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {performance.selfReview.map((item) => (
                    <div key={item.indicatorName} className="p-4 border rounded-lg bg-accent/50">
                      <h4 className="font-medium">{item.indicatorName}</h4>
                      <div className="flex items-center gap-4 mt-2">
                        <span className="text-sm text-muted-foreground">
                          自评分数: <span className="font-semibold text-primary">{item.score}</span>
                        </span>
                      </div>
                      {item.comment && (
                        <p className="text-sm text-muted-foreground mt-2">{item.comment}</p>
                      )}
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
          <Card>
            <CardHeader>
              <CardTitle>自我总结</CardTitle>
              <CardDescription>员工在自评阶段填写</CardDescription>
            </CardHeader>
            <CardContent>
              {performance.personalSummary?.trim() ? (
                <p className="text-sm text-foreground whitespace-pre-wrap">{performance.personalSummary}</p>
              ) : (
                <p className="text-sm text-muted-foreground">未填写</p>
              )}
            </CardContent>
          </Card>
          {performance.managerReview && (
            <Card>
              <CardHeader>
                <CardTitle>直属上级评分</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {performance.managerReview.map((item) => (
                    <div key={item.indicatorName} className="p-4 border rounded-lg bg-accent/50">
                      <h4 className="font-medium">{item.indicatorName}</h4>
                      <div className="flex items-center gap-4 mt-2">
                        <span className="text-sm text-muted-foreground">
                          评分: <span className="font-semibold text-purple-600">{item.score}</span>
                        </span>
                      </div>
                      {item.comment && (
                        <p className="text-sm text-muted-foreground mt-2">{item.comment}</p>
                      )}
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
          {performance.dottedManagerReview && (
            <Card>
              <CardHeader>
                <CardTitle>虚线上级评分</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {performance.dottedManagerReview.map((item) => (
                    <div key={item.indicatorName} className="p-4 border rounded-lg bg-accent/50">
                      <h4 className="font-medium">{item.indicatorName}</h4>
                      <div className="flex items-center gap-4 mt-2">
                        <span className="text-sm text-muted-foreground">
                          评分: <span className="font-semibold text-purple-600">{item.score}</span>
                        </span>
                      </div>
                      {item.comment && (
                        <p className="text-sm text-muted-foreground mt-2">{item.comment}</p>
                      )}
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
        </>
      )}

      {/* 操作按钮 */}
      {(canEdit || canGoalApprove || canFinalApprove || (performance.status === 'template_selection' && isEmployee)) && (
        <div className="flex items-center justify-end gap-3">
          {canEdit && performance.status !== 'goal_pending_review' && (
            <Button variant="outline" onClick={handleSaveDraft} disabled={submitting}>
              <Save className="w-4 h-4 mr-1" />
              保存草稿
            </Button>
          )}
          {canSubmit && (
            <Button onClick={handleSubmitReview} disabled={submitting}>
              <Send className="w-4 h-4 mr-1" />
              提交
            </Button>
          )}
          {(performance.status === 'goal_setting' || performance.status === 'goal_rejected') && isEmployee && (
            <Button onClick={handleSubmitGoal} disabled={submitting}>
              <Send className="w-4 h-4 mr-1" />
              提交目标
            </Button>
          )}
          {canFinalApprove && (
             <>
               <Button
                 variant="outline"
                 onClick={() => setFinalRejectOpen(true)}
                 disabled={submitting}
               >
                 <ThumbsDown className="w-4 h-4 mr-1" />
                 校准驳回
               </Button>
               <Button
                 onClick={() => handleCalibrate(true)}
                 disabled={submitting}
               >
                 <ThumbsUp className="w-4 h-4 mr-1" />
                 校准通过
               </Button>
             </>
           )}
        </div>
      )}

      {/* 已完成状态提示 */}
      {performance.status === 'completed' && (
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center gap-2 text-muted-foreground">
              <CheckCircle className="w-4 h-4 text-success" />
              <span>该绩效考核已完成</span>
            </div>
          </CardContent>
        </Card>
      )}

      {/* 驳回对话框（目标审核驳回） */}
      <Dialog open={rejectOpen} onOpenChange={setRejectOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>驳回目标</DialogTitle>
            <DialogDescription>请输入驳回原因，员工将收到通知并修改后重新提交。</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div>
              <label className="text-sm font-medium mb-1 block">驳回原因</label>
              <Textarea
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                placeholder="请输入驳回原因"
                className="w-full min-h-[100px]"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRejectOpen(false)}>
              取消
            </Button>
            <Button variant="destructive" onClick={() => handleApproveGoal(false)} disabled={submitting}>
              确认驳回
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 终审驳回对话框（绩效校准驳回） */}
      <Dialog open={finalRejectOpen} onOpenChange={setFinalRejectOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>校准驳回</DialogTitle>
            <DialogDescription>请输入驳回原因，并选择驳回到哪个阶段。</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div>
              <label className="text-sm font-medium mb-1 block">驳回原因</label>
              <Textarea
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                placeholder="请输入驳回原因"
                className="w-full min-h-[100px]"
              />
            </div>
            <div>
              <label className="text-sm font-medium mb-1 block">驳回到</label>
              <select
                value={finalReturnToStage}
                onChange={(e) => setFinalReturnToStage(e.target.value as PerformanceStatus)}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <option value="self_review">员工自评</option>
                <option value="dual_manager_review">直属与虚线上级评分（并行）</option>
                <option value="manager_review">直属上级评分（仅直属）</option>
                <option value="dotted_manager_review">虚线上级评分（旧流程）</option>
              </select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setFinalRejectOpen(false)}>
              取消
            </Button>
            <Button variant="destructive" onClick={() => handleCalibrate(false)} disabled={submitting}>
              确认驳回
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default PerformanceDetailPage;
