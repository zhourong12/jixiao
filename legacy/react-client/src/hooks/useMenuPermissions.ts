import { useCallback, useEffect, useState } from 'react';
import { useCurrentUserProfile } from '@lark-apaas/client-toolkit/hooks/useCurrentUserProfile';
import { getMenuPermissionsMe } from '@client/src/api';
import type { MenuPermissionKey } from '@shared/api.interface';
import { useSessionUser } from './useSessionUser';

const guestAllow = new Set<MenuPermissionKey>([
  'todo',
  'performance_list',
  'performance_export',
  'my_performance',
]);

export function useMenuPermissions() {
  const userInfo = useCurrentUserProfile();
  const sessionUser = useSessionUser();
  const loggedIn = !!(userInfo?.user_id || sessionUser.authenticated);

  const [menus, setMenus] = useState<Partial<Record<MenuPermissionKey, boolean>> | null>(null);
  const [role, setRole] = useState<string>('employee');
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(
    async (opts?: { forceApi?: boolean }) => {
      if (!loggedIn) {
        setMenus(null);
        setRole('employee');
        setLoading(false);
        return;
      }
      setLoading(true);
      try {
        const fromSession =
          !opts?.forceApi &&
          sessionUser.authenticated &&
          sessionUser.loaded &&
          sessionUser.menus != null;
        if (fromSession) {
          setMenus(sessionUser.menus);
          setRole(sessionUser.role ?? 'employee');
          setLoading(false);
          return;
        }
        const res = await getMenuPermissionsMe();
        setMenus(res.menus);
        setRole(res.role);
      } catch {
        setMenus(null);
      } finally {
        setLoading(false);
      }
    },
    [loggedIn, sessionUser.authenticated, sessionUser.loaded, sessionUser.menus, sessionUser.role],
  );

  useEffect(() => {
    void refresh();
  }, [refresh, userInfo?.user_id, sessionUser.authenticated, sessionUser.loaded, sessionUser.menus]);

  useEffect(() => {
    const onChanged = () => {
      void refresh({ forceApi: true });
    };
    window.addEventListener('menu-permissions-changed', onChanged);
    return () => window.removeEventListener('menu-permissions-changed', onChanged);
  }, [refresh]);

  const allow = useCallback(
    (key: MenuPermissionKey) => {
      if (!loggedIn) {
        return guestAllow.has(key);
      }
      if (!menus) {
        return !key.startsWith('admin_');
      }
      return menus[key] !== false;
    },
    [loggedIn, menus],
  );

  return { allow, loading, role, menus, refresh };
}
