import React, { useState, useRef } from 'react';
import { Loader2, Upload, X, FileSpreadsheet } from 'lucide-react';
import * as XLSX from 'xlsx';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Table } from '@lark-apaas/client-toolkit/antd-table';
import { createEmployee } from '@/api';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { toast } from 'sonner';
import type { CreateEmployeeRequest } from '@shared/api.interface';

interface ExcelImportDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

interface RosterRow {
  name: string;
  phone?: string;
  employeeNo?: string;
  employeeType?: string;
  departmentName?: string;
  position?: string;
  workLocation?: string;
  joinDate?: string;
  managerName?: string;
  dottedManagerName?: string;
}

interface OpenIdRow {
  name: string;
  openId: string;
}

interface MatchedRow extends RosterRow {
  openId: string;
  matchStatus: 'matched' | 'unmatched';
}

const ExcelImportDialog: React.FC<ExcelImportDialogProps> = ({
  open,
  onOpenChange,
  onSuccess,
}) => {
  const rosterInputRef = useRef<HTMLInputElement>(null);
  const openIdInputRef = useRef<HTMLInputElement>(null);

  const [rosterFileName, setRosterFileName] = useState('');
  const [openIdFileName, setOpenIdFileName] = useState('');
  const [rosterData, setRosterData] = useState<RosterRow[]>([]);
  const [openIdData, setOpenIdData] = useState<OpenIdRow[]>([]);
  const [matchedData, setMatchedData] = useState<MatchedRow[]>([]);
  const [importing, setImporting] = useState(false);
  const [importProgress, setImportProgress] = useState({ current: 0, total: 0 });

  // 解析花名册 Excel
  const handleRosterFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setRosterFileName(file.name);
    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const data = event.target?.result;
        const workbook = XLSX.read(data, { type: 'array' });
        const firstSheet = workbook.Sheets[workbook.SheetNames[0]];
        const jsonData: Record<string, unknown>[] = XLSX.utils.sheet_to_json(firstSheet);

        const mapped: RosterRow[] = jsonData
          .map((row: Record<string, unknown>) => {
            const result: Partial<RosterRow> = {};

            // 字段映射
            const mappings: Record<string, keyof RosterRow> = {
              '姓名': 'name',
              '手机号码': 'phone',
              '工号': 'employeeNo',
              '人员类型': 'employeeType',
              '部门': 'departmentName',
              '职务': 'position',
              '工作地点': 'workLocation',
              '入职日期': 'joinDate',
              '直属上级': 'managerName',
              '虚线上级': 'dottedManagerName',
            };

            for (const [cnKey, enKey] of Object.entries(mappings)) {
              const val = row[cnKey];
              if (val !== undefined && val !== null) {
                let strVal = String(val).trim();
                // 处理 Excel 日期序列号
                if (enKey === 'joinDate' && typeof val === 'number') {
                  const date = XLSX.SSF.parse_date_code(val);
                  if (date) {
                    strVal = `${date.y}-${String(date.m).padStart(2, '0')}-${String(date.d).padStart(2, '0')}`;
                  }
                }
                (result as Record<string, string>)[enKey] = strVal;
              }
            }
            return result as RosterRow;
          })
          .filter((row) => row.name);

        setRosterData(mapped);
        toast.success(`花名册解析成功，共 ${mapped.length} 条数据`);

        // 如果 open_id 文件已上传，自动匹配
        if (openIdData.length > 0) {
          performMatching(mapped, openIdData);
        }
      } catch (error: unknown) {
        logger.error('解析花名册失败', error);
        toast.error('解析花名册失败，请检查文件格式');
        setRosterData([]);
      }
    };
    reader.readAsArrayBuffer(file);
  };

  // 解析 open_id 映射 Excel
  const handleOpenIdFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setOpenIdFileName(file.name);
    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const data = event.target?.result;
        const workbook = XLSX.read(data, { type: 'array' });
        const firstSheet = workbook.Sheets[workbook.SheetNames[0]];
        const jsonData: Record<string, unknown>[] = XLSX.utils.sheet_to_json(firstSheet);

        const mapped: OpenIdRow[] = jsonData
          .map((row: Record<string, unknown>) => ({
            name: String(row['姓名'] || row['name'] || '').trim(),
            openId: String(row['飞书open_id'] || row['open_id'] || row['openId'] || '').trim(),
          }))
          .filter((row) => row.name && row.openId);

        setOpenIdData(mapped);
        toast.success(`OpenID 清单解析成功，共 ${mapped.length} 条数据`);

        // 如果花名册已上传，自动匹配
        if (rosterData.length > 0) {
          performMatching(rosterData, mapped);
        }
      } catch (error: unknown) {
        logger.error('解析 OpenID 清单失败', error);
        toast.error('解析 OpenID 清单失败，请检查文件格式');
        setOpenIdData([]);
      }
    };
    reader.readAsArrayBuffer(file);
  };

  // 执行匹配
  const performMatching = (roster: RosterRow[], openIds: OpenIdRow[]) => {
    const openIdMap = new Map<string, string>();
    openIds.forEach((item) => {
      openIdMap.set(item.name, item.openId);
    });

    const matched: MatchedRow[] = roster.map((row) => {
      const openId = openIdMap.get(row.name);
      return {
        ...row,
        openId: openId || '',
        matchStatus: openId ? 'matched' : 'unmatched',
      };
    });

    setMatchedData(matched);

    const matchedCount = matched.filter((m) => m.matchStatus === 'matched').length;
    const unmatchedCount = matched.length - matchedCount;

    toast.success(
      `匹配完成：成功 ${matchedCount} 条，未匹配 ${unmatchedCount} 条`
    );
  };

  // 导入数据
  const handleImport = async () => {
    if (matchedData.length === 0) {
      toast.error('没有可导入的数据');
      return;
    }

    const matchedOnly = matchedData.filter((m) => m.matchStatus === 'matched');
    if (matchedOnly.length === 0) {
      toast.error('没有成功匹配的数据可导入');
      return;
    }

    setImporting(true);
    setImportProgress({ current: 0, total: matchedOnly.length });
    let successCount = 0;
    let failCount = 0;

    // 构建姓名到 openId 的映射（用于上级匹配）
    const nameToOpenId = new Map<string, string>();
    matchedData.forEach((row) => {
      if (row.openId) {
        nameToOpenId.set(row.name, row.openId);
      }
    });

    for (let i = 0; i < matchedOnly.length; i++) {
      const row = matchedOnly[i];
      try {
        // 查找直属上级的 openId
        let managerId: string | undefined;
        if (row.managerName) {
          managerId = nameToOpenId.get(row.managerName);
        }

        // 查找虚线上级的 openId
        let dottedManagerId: string | undefined;
        if (row.dottedManagerName) {
          dottedManagerId = nameToOpenId.get(row.dottedManagerName);
        }

        await createEmployee({
          userId: row.openId,
          name: row.name,
          phone: row.phone || undefined,
          employeeNo: row.employeeNo || undefined,
          employeeType: row.employeeType || undefined,
          departmentName: row.departmentName || undefined,
          position: row.position || undefined,
          workLocation: row.workLocation || undefined,
          joinDate: row.joinDate || undefined,
          managerId,
          dottedManagerId,
        });
        successCount++;
      } catch (error: unknown) {
        const errMsg = error instanceof Error ? error.message : String(error);
        logger.error(`导入第 ${i + 1} 条数据失败: ${row.name}`, error);
        failCount++;
      }
      setImportProgress({ current: i + 1, total: matchedOnly.length });
    }

    setImporting(false);
    if (failCount === 0) {
      toast.success(`全部导入成功，共 ${successCount} 条`);
    } else {
      toast.warning(
        `导入完成：成功 ${successCount} 条，失败 ${failCount} 条`
      );
    }
    onOpenChange(false);
    onSuccess();
  };

  const handleReset = () => {
    setRosterFileName('');
    setOpenIdFileName('');
    setRosterData([]);
    setOpenIdData([]);
    setMatchedData([]);
    setImportProgress({ current: 0, total: 0 });
    if (rosterInputRef.current) {
      rosterInputRef.current.value = '';
    }
    if (openIdInputRef.current) {
      openIdInputRef.current.value = '';
    }
  };

  const previewColumns = [
    {
      title: '匹配状态',
      key: 'matchStatus',
      width: 90,
      render: (status: string) => (
        status === 'matched' ? (
          <span className="px-2 py-0.5 text-xs bg-success/10 text-success rounded-full">已匹配</span>
        ) : (
          <span className="px-2 py-0.5 text-xs bg-warning/10 text-warning rounded-full">未匹配</span>
        )
      ),
    },
    { title: '姓名', dataIndex: 'name', key: 'name', width: 100 },
    { title: '飞书OpenID', dataIndex: 'openId', key: 'openId', width: 200, ellipsis: true },
    { title: '手机号', dataIndex: 'phone', key: 'phone', width: 120 },
    { title: '工号', dataIndex: 'employeeNo', key: 'employeeNo', width: 80 },
    { title: '人员类型', dataIndex: 'employeeType', key: 'employeeType', width: 80 },
    { title: '部门', dataIndex: 'departmentName', key: 'departmentName', width: 150, ellipsis: true },
    { title: '职务', dataIndex: 'position', key: 'position', width: 120, ellipsis: true },
    { title: '直属上级', dataIndex: 'managerName', key: 'managerName', width: 100 },
    { title: '虚线上级', dataIndex: 'dottedManagerName', key: 'dottedManagerName', width: 100 },
    { title: '工作地点', dataIndex: 'workLocation', key: 'workLocation', width: 120 },
    { title: '入职日期', dataIndex: 'joinDate', key: 'joinDate', width: 100 },
  ];

  const matchedCount = matchedData.filter((m) => m.matchStatus === 'matched').length;
  const unmatchedCount = matchedData.length - matchedCount;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-5xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>导入花名册（匹配飞书 OpenID）</DialogTitle>
        </DialogHeader>

        <div className="space-y-6 py-4">
          {/* 文件上传区域 */}
          <div className="grid grid-cols-2 gap-4">
            {/* 花名册上传 */}
            <div className="border rounded-lg p-4 space-y-3">
              <div className="flex items-center gap-2 text-sm font-medium">
                <FileSpreadsheet className="w-4 h-4 text-primary" />
                1. 花名册文件
              </div>
              <input
                ref={rosterInputRef}
                type="file"
                accept=".xlsx,.xls"
                onChange={handleRosterFileSelect}
                className="hidden"
              />
              <Button
                variant="outline"
                size="sm"
                onClick={() => rosterInputRef.current?.click()}
                className="w-full"
              >
                <Upload className="w-4 h-4 mr-2" />
                {rosterFileName ? '重新选择' : '选择花名册'}
              </Button>
              {rosterFileName && (
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <span className="truncate">{rosterFileName}</span>
                  <span className="text-success">({rosterData.length} 条)</span>
                </div>
              )}
            </div>

            {/* OpenID 清单上传 */}
            <div className="border rounded-lg p-4 space-y-3">
              <div className="flex items-center gap-2 text-sm font-medium">
                <FileSpreadsheet className="w-4 h-4 text-primary" />
                2. 飞书 OpenID 清单
              </div>
              <input
                ref={openIdInputRef}
                type="file"
                accept=".xlsx,.xls"
                onChange={handleOpenIdFileSelect}
                className="hidden"
              />
              <Button
                variant="outline"
                size="sm"
                onClick={() => openIdInputRef.current?.click()}
                className="w-full"
              >
                <Upload className="w-4 h-4 mr-2" />
                {openIdFileName ? '重新选择' : '选择 OpenID 清单'}
              </Button>
              {openIdFileName && (
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <span className="truncate">{openIdFileName}</span>
                  <span className="text-success">({openIdData.length} 条)</span>
                </div>
              )}
            </div>
          </div>

          {/* 匹配结果统计 */}
          {matchedData.length > 0 && (
            <div className="flex items-center gap-4 text-sm">
              <span className="font-medium">匹配结果：</span>
              <span className="text-success">已匹配 {matchedCount} 条</span>
              <span className="text-warning">未匹配 {unmatchedCount} 条</span>
            </div>
          )}

          {/* 数据预览 */}
          {matchedData.length > 0 && (
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">
                  数据预览（共 {matchedData.length} 条，显示前 10 条）
                </span>
                {importing && (
                  <span className="text-sm text-muted-foreground">
                    导入中 {importProgress.current}/{importProgress.total}
                  </span>
                )}
              </div>
              <Table
                columns={previewColumns}
                dataSource={matchedData.slice(0, 10)}
                rowKey={(record, index) => String(index)}
                pagination={false}
                size="small"
                scroll={{ x: 1200, y: 300 }}
              />
            </div>
          )}
        </div>

        <DialogFooter className="gap-2">
          {matchedData.length > 0 && (
            <Button variant="outline" onClick={handleReset}>
              重置
            </Button>
          )}
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button
            onClick={handleImport}
            disabled={importing || matchedCount === 0}
          >
            {importing ? (
              <>
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                导入中 {importProgress.current}/{importProgress.total}
              </>
            ) : (
              `导入已匹配数据 (${matchedCount})`
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default ExcelImportDialog;
