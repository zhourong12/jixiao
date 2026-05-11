import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

export default function LocalLoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch("/auth/password/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ username: username.trim(), password }),
      });
      const data = (await res.json()) as { success?: boolean; message?: string };
      if (!res.ok || !data.success) {
        setError(data.message || "登录失败，请检查用户名与密码");
        return;
      }
      window.location.href = "/";
    } catch {
      setError("网络错误，请稍后重试");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-md shadow-sm">
        <CardHeader>
          <CardTitle className="text-xl">账密登录</CardTitle>
          <CardDescription>
            使用员工编号或姓名登录；演示环境默认密码为 123456。亦可使用下方「飞书登录」跳转授权。
          </CardDescription>
        </CardHeader>
        <form onSubmit={onSubmit}>
          <CardContent className="space-y-4">
            {error ? (
              <p className="text-sm text-destructive" role="alert">
                {error}
              </p>
            ) : null}
            <div className="space-y-2">
              <Label htmlFor="jx-local-user">用户名</Label>
              <Input
                id="jx-local-user"
                autoComplete="username"
                value={username}
                onChange={(ev) => setUsername(ev.target.value)}
                placeholder="员工编号或姓名"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="jx-local-pass">密码</Label>
              <Input
                id="jx-local-pass"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(ev) => setPassword(ev.target.value)}
                placeholder="默认 123456"
              />
            </div>
          </CardContent>
          <CardFooter className="flex flex-col gap-2 sm:flex-row sm:justify-between">
            <Button type="button" variant="outline" asChild>
              <a href="/auth/feishu/login">飞书登录</a>
            </Button>
            <Button type="submit" disabled={loading}>
              {loading ? "登录中…" : "登录"}
            </Button>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}
