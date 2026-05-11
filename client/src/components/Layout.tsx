import { Outlet, Link, useLocation, useNavigate } from "react-router-dom";
import { useMemo, useState } from "react";
import {
  SidebarProvider,
  Sidebar,
  SidebarHeader,
  SidebarContent,
  SidebarFooter,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarGroup,
  SidebarGroupContent,
  SidebarTrigger,
} from "@/components/ui/sidebar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { useCurrentUserProfile } from "@lark-apaas/client-toolkit/hooks/useCurrentUserProfile";
import { useAppInfo } from "@lark-apaas/client-toolkit/hooks/useAppInfo";
import { useSessionUser } from "@/hooks/useSessionUser";
import { useMenuPermissions } from "@/hooks/useMenuPermissions";
import type { MenuPermissionKey } from "@shared/api.interface";
import { axiosForBackend } from "@lark-apaas/client-toolkit/utils/getAxiosForBackend";
import {
  LayoutDashboard,
  ClipboardList,
  FileText,
  UserCircle,
  Bell,
  Settings,
  LogOut,
  User,
  ChevronDown,
  Users,
  KeyRound,
  Shield,
  ListTodo,
  BarChart3,
  UserCog,
  Scale,
} from "lucide-react";

interface NavItem {
  path: string;
  label: string;
  icon: React.ElementType;
  menuKey: MenuPermissionKey;
}

const navItems: NavItem[] = [
  { path: "/todo", label: "待办", icon: ListTodo, menuKey: "todo" },
  { path: "/", label: "工作台", icon: LayoutDashboard, menuKey: "home" },
  { path: "/my-performance", label: "我的绩效", icon: UserCircle, menuKey: "my_performance" },
  { path: "/performances", label: "绩效列表", icon: ClipboardList, menuKey: "performance_list" },
  {
    path: "/admin/performance-calibration",
    label: "绩效校准",
    icon: Scale,
    menuKey: "admin_performance_calibration",
  },
  { path: "/admin/templates", label: "模板管理", icon: FileText, menuKey: "admin_templates" },
  { path: "/admin/notifications", label: "通知管理", icon: Bell, menuKey: "admin_notifications" },
  { path: "/admin/employees", label: "员工管理", icon: Users, menuKey: "admin_employees" },
  { path: "/admin/roles", label: "角色管理", icon: UserCog, menuKey: "admin_roles" },
  { path: "/admin/permissions", label: "权限管理", icon: Shield, menuKey: "admin_permissions" },
  { path: "/admin/statistics-months", label: "周期与评选", icon: BarChart3, menuKey: "admin_statistics_months" },
  { path: "/admin/system-config", label: "系统配置", icon: Settings, menuKey: "admin_system_config" },
];

const LayoutContent = () => {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const userInfo = useCurrentUserProfile();
  const sessionUser = useSessionUser();
  const { allow: menuAllow } = useMenuPermissions();
  const { appName } = useAppInfo();
  const [logoutDialogOpen, setLogoutDialogOpen] = useState(false);

  const brandLink = useMemo(() => {
    const first = navItems.find((item) => menuAllow(item.menuKey));
    return first?.path ?? "/";
  }, [menuAllow]);

  const mergedProfile = useMemo(() => {
    if (userInfo?.user_id) {
      return userInfo;
    }
    if (sessionUser.authenticated && sessionUser.user_id) {
      return {
        user_id: sessionUser.user_id,
        name: sessionUser.name,
        avatar: undefined as string | undefined,
      };
    }
    return userInfo;
  }, [userInfo, sessionUser]);

  const isLoggedIn = !!(userInfo?.user_id || sessionUser.authenticated);

  const handleLogout = async () => {
    try {
      await axiosForBackend({ url: "/api/session/logout", method: "POST" });
    } catch {
      /* 忽略网络错误，仍尝试跳转 */
    }
    window.location.href = "/";
  };

  const handleLogin = () => {
    window.location.href = "/auth/feishu/login";
  };

  return (
    <>
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton size="lg" asChild>
                <Link to={brandLink}>
                  <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                    <Settings className="size-4" />
                  </div>
                  <div className="grid flex-1 text-left text-sm leading-tight group-data-[collapsible=icon]:hidden">
                    <span className="truncate font-semibold">{appName || "绩效管理系统"}</span>
                  </div>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarHeader>

        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupContent>
              <SidebarMenu>
                {navItems.filter((item) => menuAllow(item.menuKey)).map((item) => (
                  <SidebarMenuItem key={item.path}>
                    <SidebarMenuButton
                      asChild
                      isActive={
                        pathname === item.path ||
                        (item.path !== "/" && pathname.startsWith(item.path + "/"))
                      }
                      tooltip={item.label}
                    >
                      <Link to={item.path}>
                        <item.icon className="size-4" />
                        <span>{item.label}</span>
                      </Link>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>

        <SidebarFooter>
          <SidebarMenu>
            <SidebarMenuItem>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <SidebarMenuButton
                    size="lg"
                    className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
                  >
                    <Avatar className="h-8 w-8 rounded-lg">
                      <AvatarImage
                        src={mergedProfile?.avatar || ""}
                        alt={mergedProfile?.name || "用户"}
                      />
                      <AvatarFallback className="rounded-lg bg-primary text-primary-foreground">
                        {mergedProfile?.name?.charAt(0) || <User className="size-4" />}
                      </AvatarFallback>
                    </Avatar>
                    <div className="grid flex-1 text-left text-sm leading-tight group-data-[collapsible=icon]:hidden">
                      <span className="truncate font-semibold">
                        {mergedProfile?.name || "游客"}
                      </span>
                      <span className="truncate text-xs text-sidebar-foreground/70">
                        {isLoggedIn ? "已登录" : "未登录"}
                      </span>
                    </div>
                    <ChevronDown className="ml-auto size-4 group-data-[collapsible=icon]:hidden" />
                  </SidebarMenuButton>
                </DropdownMenuTrigger>
                <DropdownMenuContent
                  className="w-[--radix-dropdown-menu-trigger-width] min-w-56 rounded-lg"
                  side="bottom"
                  align="end"
                  sideOffset={4}
                >
                  {isLoggedIn ? (
                    <DropdownMenuItem onClick={() => setLogoutDialogOpen(true)}>
                      <LogOut className="mr-2 size-4" />
                      退出登录
                    </DropdownMenuItem>
                  ) : (
                    <>
                      <DropdownMenuItem onClick={handleLogin}>
                        <User className="mr-2 size-4" />
                        飞书登录
                      </DropdownMenuItem>
                      <DropdownMenuItem onClick={() => navigate("/login")}>
                        <KeyRound className="mr-2 size-4" />
                        账密登录
                      </DropdownMenuItem>
                    </>
                  )}
                </DropdownMenuContent>
              </DropdownMenu>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
      </Sidebar>

      <main className="flex-1 flex flex-col overflow-hidden p-6">
        <header className="flex items-center gap-2 mb-6">
          <SidebarTrigger className="-ml-1" />
        </header>
        <div className="flex-1 overflow-auto">
          <Outlet />
        </div>
      </main>

      <Dialog open={logoutDialogOpen} onOpenChange={setLogoutDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认退出登录</DialogTitle>
            <DialogDescription>
              退出登录后需要重新登录才能访问系统，是否继续？
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setLogoutDialogOpen(false)}>
              取消
            </Button>
            <Button variant="destructive" onClick={handleLogout}>
              退出登录
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
};

const Layout = () => {
  return (
    <SidebarProvider>
      <LayoutContent />
    </SidebarProvider>
  );
};

export default Layout;
