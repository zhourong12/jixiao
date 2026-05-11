import { useEffect, useState } from 'react';
import { axiosForBackend } from '@lark-apaas/client-toolkit/utils/getAxiosForBackend';
import type { MenuPermissionKey } from '@shared/api.interface';

export type SessionUserState = {
  loaded: boolean;
  authenticated: boolean;
  user_id?: string;
  name?: string;
  /** 主角色（兼容旧逻辑） */
  role?: string;
  roles?: string[];
  menus?: Record<MenuPermissionKey, boolean>;
};

/**
 * 从后端 Cookie 会话读取当前用户（账密登录、飞书登录均可），
 * 用于补充 `useCurrentUserProfile`（依赖飞书 UDT，本地开发常为空白）。
 */
export function useSessionUser(): SessionUserState {
  const [state, setState] = useState<SessionUserState>({
    loaded: false,
    authenticated: false,
  });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await axiosForBackend({
          url: '/api/session/me',
          method: 'GET',
        });
        const d = res.data as {
          authenticated?: boolean;
          user_id?: string;
          name?: string;
          role?: string;
          roles?: string[];
          menus?: Record<MenuPermissionKey, boolean>;
        };
        if (cancelled) return;
        if (d?.authenticated && d.user_id) {
          setState({
            loaded: true,
            authenticated: true,
            user_id: d.user_id,
            name: d.name,
            role: d.role,
            roles: d.roles,
            menus: d.menus,
          });
        } else {
          setState({ loaded: true, authenticated: false });
        }
      } catch {
        if (!cancelled) {
          setState({ loaded: true, authenticated: false });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return state;
}
