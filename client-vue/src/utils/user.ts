export type UserInput = {
  user_id?: string;
  userId?: string;
  name?: string;
  avatar?: string;
  departmentName?: string;
};

export type User = {
  user_id: string;
  name?: string;
  avatar?: string;
  departmentName?: string;
};

export function normalizeUser(input: UserInput): User {
  const user_id = (input.user_id || input.userId || "").trim();
  return {
    user_id,
    name: input.name,
    avatar: input.avatar,
    departmentName: input.departmentName,
  };
}

export function isValidUserId(user_id: string | undefined): boolean {
  if (!user_id) return false;
  const trimmed = user_id.trim();
  if (!trimmed || trimmed === '""') return false;
  return true;
}

export function getUserDisplayName(user: User | undefined): string {
  if (!user) return "";
  return user.name?.trim() || user.user_id || "无效人员";
}

/** 列表展示用：排除 open_id 或与 userId 相同的占位“姓名”。 */
export function isDisplayablePersonName(name?: string | null, userId?: string | null): boolean {
  const n = (name ?? "").trim();
  if (!n) return false;
  const id = (userId ?? "").trim();
  if (id && n === id) return false;
  if (/^ou_|^on_/i.test(n)) return false;
  return true;
}

/**
 * 界面展示用短姓名：去掉层级表里常见的「姓名-部门/后缀」后半段，并合并「陈 陈某某」类重复首字。
 */
export function shortPersonDisplayName(raw: string | undefined | null): string {
  let s = (raw ?? "").trim().replace(/\s+/g, " ");
  if (!s) return "";
  const dup = s.match(/^(.)\s+\1(.+)$/);
  if (dup) {
    s = `${dup[1]}${dup[2]}`;
  }
  const head = s.split(/[-－]/, 2)[0]?.trim() ?? s;
  return head || s;
}
