/** 部门树筛选值：空=全部；subject:code=主体；dept:id=部门 */
export type DepartmentFilterValue = string;

export function parseDepartmentFilter(value: DepartmentFilterValue): {
  subjectCode?: string;
  departmentId?: string;
} {
  const v = value.trim();
  if (!v) return {};
  if (v.startsWith("subject:")) {
    const code = v.slice("subject:".length).trim();
    return code ? { subjectCode: code } : {};
  }
  if (v.startsWith("dept:")) {
    const rest = v.slice("dept:".length).trim();
    if (!rest) return {};
    const sep = rest.indexOf(":");
    if (sep > 0) {
      const subjectCode = rest.slice(0, sep).trim();
      const departmentId = rest.slice(sep + 1).trim();
      if (!departmentId) return subjectCode ? { subjectCode } : {};
      return subjectCode
        ? { subjectCode, departmentId }
        : { departmentId };
    }
    return { departmentId: rest };
  }
  return { departmentId: v };
}

export function subjectFilterValue(code: string) {
  return `subject:${code}`;
}

export function deptFilterValue(subjectCode: string, id: string) {
  return `dept:${subjectCode}:${id}`;
}

export type DepartmentTreeSubjectLike = {
  subjectCode: string;
  departments: Array<{ id: string; name?: string | null }>;
};

/** 从树形筛选值解析出保存员工用的 departmentId / departmentName */
export function departmentFieldsFromFilter(
  value: string,
  tree: DepartmentTreeSubjectLike[],
): { departmentId?: string; departmentName?: string } {
  const parsed = parseDepartmentFilter(value);
  if (!parsed.departmentId) return {};
  const deptId = parsed.departmentId;
  for (const sub of tree) {
    const dept = sub.departments.find((d) => d.id === deptId);
    if (dept) {
      return {
        departmentId: dept.id,
        departmentName: dept.name?.trim() || dept.id,
      };
    }
  }
  return { departmentId: deptId };
}

/** 员工行数据 → 树形选择器初始值（需已加载 tree） */
export function employeeRowToDepartmentFilter(
  row: { feishuSubjectCode?: string; departmentId?: string | null; departmentName?: string },
  tree: DepartmentTreeSubjectLike[],
): string {
  const code = row.feishuSubjectCode?.trim();
  if (!code) return "";
  const id = row.departmentId?.trim();
  const name = row.departmentName?.trim();
  for (const sub of tree) {
    if (sub.subjectCode !== code) continue;
    if (id) {
      const byId = sub.departments.find((d) => d.id === id);
      if (byId) return deptFilterValue(code, byId.id);
    }
    if (name) {
      const byName = sub.departments.find((d) => (d.name?.trim() || d.id) === name);
      if (byName) return deptFilterValue(code, byName.id);
    }
  }
  return "";
}

/** 飞书选人返回的部门名 → 树形值（同主体下按名称匹配） */
export function matchDepartmentFilterByName(
  subjectCode: string,
  departmentName: string,
  tree: DepartmentTreeSubjectLike[],
): string {
  const code = subjectCode.trim();
  const name = departmentName.trim();
  if (!code || !name) return "";
  const sub = tree.find((s) => s.subjectCode === code);
  if (!sub) return "";
  const dept = sub.departments.find((d) => (d.name?.trim() || d.id) === name);
  return dept ? deptFilterValue(code, dept.id) : "";
}
