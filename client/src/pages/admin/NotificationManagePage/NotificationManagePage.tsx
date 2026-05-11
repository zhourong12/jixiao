import { useState, useEffect } from 'react';
import { toast } from 'sonner';
import { Send, Users, Building2, Loader2 } from 'lucide-react';
import { Button } from '@client/src/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@client/src/components/ui/card';
import { Input } from '@client/src/components/ui/input';
import { Label } from '@client/src/components/ui/label';
import { Textarea } from '@client/src/components/ui/textarea';
import { Badge } from '@client/src/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@client/src/components/ui/tabs';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@client/src/components/ui/select';
import { UserSelect } from '@client/src/components/business-ui/user-select';
import { DepartmentSelect } from '@client/src/components/business-ui/department-select';
import { Table } from '@lark-apaas/client-toolkit/antd-table';
import { getNotifications, sendNotification } from '@client/src/api';
import type { NotificationListItem } from '@shared/api.interface';
import type { Department } from '@client/src/components/business-ui/department-select/types';

const NotificationManagePage = () => {
  const [activeTab, setActiveTab] = useState('send');
  const [loading, setLoading] = useState(false);
  const [notifications, setNotifications] = useState<NotificationListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(10);

  // 发送表单
  const [sendType, setSendType] = useState<'all' | 'department' | 'specified'>('all');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [selectedUsers, setSelectedUsers] = useState<string[]>([]);
  const [selectedDepartments, setSelectedDepartments] = useState<Department[]>([]);
  const [sending, setSending] = useState(false);

  const fetchNotifications = async () => {
    setLoading(true);
    try {
      const result = await getNotifications({ page, pageSize });
      setNotifications(result.items);
      setTotal(result.total);
    } catch (error) {
      toast.error('获取通知列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'history') {
      fetchNotifications();
    }
  }, [activeTab, page, pageSize]);

  const handleSend = async () => {
    if (!title.trim()) {
      toast.error('请输入通知标题');
      return;
    }
    if (!content.trim()) {
      toast.error('请输入通知内容');
      return;
    }

    let targetIds: string[] = [];
    if (sendType === 'specified') {
      if (selectedUsers.length === 0) {
        toast.error('请选择接收人员');
        return;
      }
      targetIds = selectedUsers;
    } else if (sendType === 'department') {
      if (selectedDepartments.length === 0) {
        toast.error('请选择接收部门');
        return;
      }
      targetIds = selectedDepartments.map((d) => d.id);
    } else {
      targetIds = ['all'];
    }

    setSending(true);
    try {
      await sendNotification({
        title: title.trim(),
        content: content.trim(),
        sendType,
        targetIds,
      });
      toast.success('通知发送成功');
      // 重置表单
      setTitle('');
      setContent('');
      setSelectedUsers([]);
      setSelectedDepartments([]);
    } catch (error) {
      toast.error('通知发送失败');
    } finally {
      setSending(false);
    }
  };

  const columns = [
    {
      title: '标题',
      dataIndex: 'title',
      key: 'title',
      width: 250,
    },
    {
      title: '发送范围',
      dataIndex: 'sendType',
      key: 'sendType',
      width: 120,
      render: (type: string) => {
        const config: Record<string, { label: string; color: string }> = {
          all: { label: '全员', color: 'bg-blue-100 text-blue-800' },
          department: { label: '按部门', color: 'bg-purple-100 text-purple-800' },
          specified: { label: '指定人员', color: 'bg-orange-100 text-orange-800' },
        };
        const { label, color } = config[type] || { label: type, color: '' };
        return <Badge className={color}>{label}</Badge>;
      },
    },
    {
      title: '发送时间',
      dataIndex: 'sendTime',
      key: 'sendTime',
      width: 180,
      render: (time: string) => new Date(time).toLocaleString('zh-CN'),
    },
    {
      title: '发送人',
      dataIndex: 'senderName',
      key: 'senderName',
      width: 120,
    },
    {
      title: '已读/总数',
      key: 'readStatus',
      width: 120,
      render: (_, record) => (
        <span className="text-sm">
          {record.readCount} / {record.totalCount}
        </span>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">通知管理</h1>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="send">
            <Send className="w-4 h-4 mr-2" />
            发送通知
          </TabsTrigger>
          <TabsTrigger value="history">
            <Users className="w-4 h-4 mr-2" />
            发送记录
          </TabsTrigger>
        </TabsList>

        <TabsContent value="send" className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>发送绩效通知</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              {/* 发送范围选择 */}
              <div className="space-y-2">
                <Label>发送范围</Label>
                <Select
                  value={sendType}
                  onValueChange={(v: 'all' | 'department' | 'specified') => {
                    setSendType(v);
                    setSelectedUsers([]);
                    setSelectedDepartments([]);
                  }}
                >
                  <SelectTrigger className="w-[200px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">
                      <div className="flex items-center">
                        <Users className="w-4 h-4 mr-2" />
                        全员发送
                      </div>
                    </SelectItem>
                    <SelectItem value="department">
                      <div className="flex items-center">
                        <Building2 className="w-4 h-4 mr-2" />
                        按部门发送
                      </div>
                    </SelectItem>
                    <SelectItem value="specified">
                      <div className="flex items-center">
                        <Users className="w-4 h-4 mr-2" />
                        指定人员
                      </div>
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {/* 部门选择 */}
              {sendType === 'department' && (
                <div className="space-y-2">
                  <Label>选择部门</Label>
                  <DepartmentSelect
                    value={selectedDepartments}
                    onChange={(value) => setSelectedDepartments(Array.isArray(value) ? value : [value])}
                    placeholder="请选择接收部门"
                    multiple
                  />
                </div>
              )}

              {/* 人员选择 */}
              {sendType === 'specified' && (
                <div className="space-y-2">
                  <Label>选择人员</Label>
                  <UserSelect
                    value={selectedUsers}
                    onChange={setSelectedUsers}
                    placeholder="请选择接收人员"
                  />
                </div>
              )}

              {/* 通知标题 */}
              <div className="space-y-2">
                <Label>通知标题</Label>
                <Input
                  placeholder="请输入通知标题"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  maxLength={100}
                />
              </div>

              {/* 通知内容 */}
              <div className="space-y-2">
                <Label>通知内容</Label>
                <Textarea
                  placeholder="请输入通知内容"
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  rows={6}
                  maxLength={1000}
                />
                <p className="text-xs text-muted-foreground text-right">
                  {content.length} / 1000
                </p>
              </div>

              {/* 发送按钮 */}
              <div className="flex justify-end">
                <Button onClick={handleSend} disabled={sending}>
                  {sending ? (
                    <>
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                      发送中...
                    </>
                  ) : (
                    <>
                      <Send className="w-4 h-4 mr-2" />
                      发送通知
                    </>
                  )}
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="history">
          <Card>
            <CardHeader>
              <CardTitle>发送记录</CardTitle>
            </CardHeader>
            <CardContent>
              <Table
                columns={columns}
                dataSource={notifications}
                rowKey="id"
                loading={loading}
                pagination={{
                  current: page,
                  pageSize,
                  total,
                  onChange: (p) => setPage(p),
                  showSizeChanger: false,
                  showTotal: (t) => `共 ${t} 条`,
                }}
              />
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
};

export default NotificationManagePage;
