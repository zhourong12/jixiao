'use client';

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { X } from 'lucide-react';

import { getEmployees } from '@/api';
import { BaseCombobox } from '@client/src/components/business-ui/entity-combobox/base-combobox';
import { BaseComboboxItem } from '@client/src/components/business-ui/entity-combobox/base-combobox-item';
import {
  ItemPill,
  renderPillAvatar,
} from '@client/src/components/business-ui/entity-combobox/item-pill';
import { useEntityComboboxContext } from '@client/src/components/business-ui/entity-combobox/context';
import { HighlightText } from '@client/src/components/business-ui/entity-combobox/highlight-text';
import type { ItemValue } from '@client/src/components/business-ui/entity-combobox/shared-types';
import {
  listItemNameVariants,
  listItemSubTextVariants,
  listItemVariants,
  tagCloseIconVariants,
  type ComboboxSize,
} from '@client/src/components/business-ui/entity-combobox/size-variants';
import { cn } from '@/lib/utils';
import type { EmployeeListItem } from '@shared/api.interface';

type TValue = ItemValue<EmployeeListItem>;

function EmployeeOptionLabel({ name, size }: { name: string; size: ComboboxSize }) {
  const { debouncedSearch } = useEntityComboboxContext();
  return (
    <span className={listItemNameVariants({ size })}>
      <HighlightText text={name} highlight={debouncedSearch} />
    </span>
  );
}

export interface EmployeeSupervisorSelectProps {
  /** 与表单弹层同步：打开或切换员工时清空本地缓存的展示名 */
  dialogOpen: boolean;
  resetScope: string;
  value: string | null;
  onChange: (userId: string | null) => void;
  /** 后端列表带回的姓名（仅当 value 仍为该员工当前上级 id 时由父组件传入） */
  nameFromServer?: string | null;
  excludeUserIds?: string[];
  placeholder?: string;
  disabled?: boolean;
  size?: ComboboxSize;
}

export const EmployeeSupervisorSelect: React.FC<EmployeeSupervisorSelectProps> = ({
  dialogOpen,
  resetScope,
  value,
  onChange,
  nameFromServer,
  excludeUserIds = [],
  placeholder = '选择员工',
  disabled,
  size = 'medium',
}) => {
  const [localName, setLocalName] = useState<string | undefined>();

  useEffect(() => {
    if (!dialogOpen) return;
    setLocalName(undefined);
  }, [dialogOpen, resetScope]);

  const exclude = useMemo(() => new Set(excludeUserIds.filter(Boolean)), [excludeUserIds]);

  const displayName = localName ?? (nameFromServer?.trim() || value || '');

  const internalValue: TValue | null = useMemo(() => {
    if (!value) return null;
    const name = displayName || value;
    const row: EmployeeListItem = {
      id: value,
      userId: value,
      name,
    };
    return {
      id: value,
      name,
      avatar: undefined,
      raw: row,
    };
  }, [value, displayName]);

  const fetchFn = useCallback(
    async (search: string) => {
      const res = await getEmployees({
        page: 1,
        pageSize: 100,
        keyword: search.trim() || undefined,
      });
      const items: TValue[] = res.items
        .filter((e) => !exclude.has(e.userId))
        .map((e) => ({
          id: e.userId,
          name: (e.name || e.userId).trim() || e.userId,
          avatar: e.avatar,
          raw: e,
        }));
      return { items };
    },
    [exclude],
  );

  const handleChange = useCallback(
    (v: TValue | TValue[] | null) => {
      if (Array.isArray(v)) return;
      if (!v) {
        onChange(null);
        setLocalName(undefined);
        return;
      }
      onChange(v.id);
      setLocalName(v.name);
    },
    [onChange],
  );

  return (
    <BaseCombobox<TValue, EmployeeListItem, TValue>
      size={size}
      fetchFn={fetchFn}
      value={internalValue}
      onChange={handleChange}
      disabled={disabled}
      placeholder={placeholder}
      emptyText="没有匹配的员工，换个关键词试试"
      getItemValue={(item) => item}
      getOptionDisabled={(opt) => exclude.has(opt.id)}
      renderItem={(item, isSelected, itemClassName, itemDisabled) => (
        <BaseComboboxItem
          item={item}
          getItemValue={(i) => i}
          isSelected={isSelected}
          className={itemClassName}
          disabled={itemDisabled}
        >
          <div className={cn(listItemVariants({ size }), 'min-w-0 flex-1')}>
            <EmployeeOptionLabel name={item.name} size={size} />
            {item.raw?.departmentName ? (
              <span className={listItemSubTextVariants({ size })}>{item.raw.departmentName}</span>
            ) : null}
          </div>
        </BaseComboboxItem>
      )}
      renderTag={(val, onClose, tagDisabled) => {
        const handleClose = (e: React.MouseEvent) => {
          e.stopPropagation();
          onClose(val, e);
        };
        const closeBtn = !tagDisabled ? (
          <button
            type="button"
            className="rounded-sm text-foreground opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none"
            onClick={handleClose}
          >
            <X className={tagCloseIconVariants({ size })} />
            <span className="sr-only">移除 {val.name}</span>
          </button>
        ) : null;
        return (
          <ItemPill
            key={val.id}
            label={val.name}
            avatar={renderPillAvatar({
              avatarUrl: val.avatar,
              label: val.name,
              size,
              avatarFallback: true,
            })}
            size={size}
            disabled={tagDisabled}
            suffix={closeBtn}
          />
        );
      }}
    />
  );
};
