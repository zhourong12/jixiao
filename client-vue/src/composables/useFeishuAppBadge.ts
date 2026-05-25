import { onMounted } from "vue";
import {
  getFeishuAppBadgeEnabled,
  getFeishuJssdkConfig,
  postFeishuBadgeClientLog,
  syncFeishuAppBadge,
} from "@/api/feishu";

const SCOPE_BADGE = "scope.appBadge";
const LOG = "[feishu-badge]";

/** 须在 h5sdk.config 的 jsApiList 中声明，否则 getSetting/authorize/updateBadge 会报未授权 */
const BADGE_JS_API_LIST = ["getSetting", "authorize", "updateBadge", "openSetting"];

/** 官方 CDN（feishucdn）；bytegoofy 在企业内网/飞书 WebView 常被拦截 */
const H5_SDK_URLS = [
  "https://lf-scm-cn.feishucdn.com/lark/op/h5-js-sdk-1.5.44.js",
  "https://lf1-cdn-tos.bytegoofy.com/goofy/ee/lark/h5jssdk/h5jssdk-1.5.23.js",
];

type FeishuH5Sdk = {
  ready: (cb: () => void) => void;
  config?: (opts: {
    appId: string;
    timestamp: number;
    nonceStr: string;
    signature: string;
    jsApiList: string[];
    onSuccess?: (res: unknown) => void;
    onFail?: (err: unknown) => void;
  }) => void;
};

function feishuWin(): Window & { tt?: Record<string, (...args: unknown[]) => unknown>; h5sdk?: FeishuH5Sdk } {
  return window as Window & { tt?: Record<string, (...args: unknown[]) => unknown>; h5sdk?: FeishuH5Sdk };
}

function isSdkReady(): boolean {
  const w = feishuWin();
  return Boolean(w.h5sdk?.ready && (w.tt || w.h5sdk.config));
}

const pendingLines: string[] = [];
let flushTimer: ReturnType<typeof setTimeout> | null = null;

function formatErr(e: unknown): string {
  if (e instanceof Error) return e.message;
  return String(e);
}

function scheduleBadgeLogFlush(): void {
  if (flushTimer != null) return;
  flushTimer = setTimeout(() => {
    flushTimer = null;
    void flushBadgeLogs();
  }, 400);
}

async function flushBadgeLogs(): Promise<void> {
  const lines = pendingLines.splice(0, 50);
  if (!lines.length) return;
  try {
    await postFeishuBadgeClientLog(lines);
  } catch {
    // 未登录或网络失败时不影响主流程
  }
}

function badgeLog(message: string): void {
  const line = message.startsWith(LOG) ? message : `${LOG} ${message}`;
  console.info(line);
  pendingLines.push(line);
  scheduleBadgeLogFlush();
}

function badgeWarn(message: string): void {
  const line = message.startsWith(LOG) ? message : `${LOG} ${message}`;
  console.warn(line);
  pendingLines.push(line);
  scheduleBadgeLogFlush();
}

/** 是否应初始化角标（飞书客户端 / 工作台 iframe 内嵌 H5） */
function shouldInitFeishuBadge(): boolean {
  if (typeof navigator === "undefined") return false;
  if (/Lark|Feishu|feishu/i.test(navigator.userAgent)) return true;
  const w = window as Window & { tt?: unknown; h5sdk?: unknown };
  if (w.tt && w.h5sdk) return true;
  try {
    if (window.self !== window.top && /feishu|lark/i.test(document.referrer)) return true;
  } catch {
    // 跨域 iframe 无法读 referrer，仅依赖 UA / 父页面注入的 tt
  }
  return false;
}

function logBadgeEnvironment(): void {
  const w = window as Window & { tt?: unknown; h5sdk?: unknown };
  let inIframe = false;
  try {
    inIframe = window.self !== window.top;
  } catch {
    inIframe = true;
  }
  const ua = navigator.userAgent;
  const uaHasLark = /Lark|Feishu|feishu/i.test(ua);
  const shouldInit = shouldInitFeishuBadge();
  const reasons: string[] = [];
  if (uaHasLark) reasons.push("UA含Lark/Feishu");
  if (w.tt && w.h5sdk) reasons.push("已注入tt+h5sdk");
  if (inIframe && /feishu|lark/i.test(document.referrer)) reasons.push("飞书iframe(referrer)");
  badgeLog(
    `运行环境 shouldInit=${shouldInit} (${reasons.length ? reasons.join(" | ") : "无飞书特征"}) page=${window.location.href}`,
  );
  badgeLog(`  inIframe=${inIframe} hasTt=${Boolean(w.tt)} hasH5sdk=${Boolean(w.h5sdk)} uaHasLark=${uaHasLark}`);
  badgeLog(`  referrer=${document.referrer || "(empty)"}`);
  badgeLog(`  userAgent=${ua}`);
}

function waitForSdkReady(timeoutMs: number): Promise<boolean> {
  if (isSdkReady()) return Promise.resolve(true);
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve) => {
    const tick = () => {
      if (isSdkReady()) {
        resolve(true);
        return;
      }
      if (Date.now() >= deadline) {
        resolve(false);
        return;
      }
      setTimeout(tick, 200);
    };
    tick();
  });
}

function loadScriptUrl(src: string): Promise<void> {
  const existing = document.querySelector(`script[data-feishu-h5sdk-src="${src}"]`);
  if (existing) {
    return waitForSdkReady(8000).then((ok) => {
      if (!ok) throw new Error(`SDK 未就绪: ${src}`);
    });
  }
  return new Promise((resolve, reject) => {
    const s = document.createElement("script");
    s.src = src;
    s.async = true;
    s.crossOrigin = "anonymous";
    s.dataset.feishuH5sdk = "1";
    s.dataset.feishuH5sdkSrc = src;
    s.onload = () => {
      void waitForSdkReady(8000).then((ok) => (ok ? resolve() : reject(new Error(`SDK 未就绪: ${src}`))));
    };
    s.onerror = () => reject(new Error(`脚本加载失败: ${src}`));
    document.head.appendChild(s);
  });
}

async function ensureFeishuH5Sdk(): Promise<void> {
  if (typeof window === "undefined") return;
  if (await waitForSdkReady(2500)) {
    badgeLog("飞书 H5 SDK 已就绪（页面注入或 index.html）");
    return;
  }
  for (const url of H5_SDK_URLS) {
    try {
      badgeLog(`尝试加载 H5 SDK url=${url}`);
      await loadScriptUrl(url);
      badgeLog(`H5 SDK 加载成功 url=${url}`);
      return;
    } catch (e) {
      badgeWarn(`H5 SDK 加载失败 url=${url} err=${formatErr(e)}`);
    }
  }
  throw new Error("飞书 SDK 加载失败（请确认可访问 feishucdn.com）");
}

function getTt(): Record<string, (...args: unknown[]) => unknown> | null {
  return feishuWin().tt ?? null;
}

function getH5sdk(): FeishuH5Sdk | null {
  return feishuWin().h5sdk ?? null;
}

async function runJssdkConfig(attempt: number): Promise<void> {
  const h5sdk = getH5sdk();
  const tt = getTt();
  if (!h5sdk?.ready) {
    throw new Error("h5sdk.ready 不可用");
  }
  const url = window.location.href.split("#")[0] ?? window.location.href;
  badgeLog(`请求 JSSDK 鉴权 attempt=${attempt} url=${url}`);
  const cfg = await getFeishuJssdkConfig(url);
  await new Promise<void>((resolve, reject) => {
    const onSuccess = () => {
      badgeLog(`JSSDK config 成功 appId=${cfg.appId} jsApiList=${BADGE_JS_API_LIST.join(",")}`);
      resolve();
    };
    const onFail = (err: unknown) => {
      const errObj = err && typeof err === "object" ? (err as { errorCode?: number }) : null;
      if (errObj?.errorCode === 333444) {
        reject(new Error("JSSDK 鉴权失败: signature expired，将重试"));
        return;
      }
      reject(err instanceof Error ? err : new Error(`JSSDK 鉴权失败: ${JSON.stringify(err)}`));
    };
    const payload = {
      appId: cfg.appId,
      timestamp: cfg.timestamp,
      nonceStr: cfg.nonceStr,
      signature: cfg.signature,
      jsApiList: BADGE_JS_API_LIST,
      onSuccess,
      onFail,
    };
    h5sdk.ready(() => {
      try {
        if (h5sdk.config) {
          h5sdk.config(payload);
        } else if (tt?.config) {
          tt.config(payload);
        } else {
          reject(new Error("无 h5sdk.config / tt.config"));
        }
      } catch (e) {
        reject(e instanceof Error ? e : new Error(formatErr(e)));
      }
    });
  });
}

async function configJssdk(): Promise<void> {
  if (!getH5sdk()?.ready) {
    badgeWarn("跳过 JSSDK 鉴权：h5sdk.ready 不可用");
    return;
  }
  try {
    await runJssdkConfig(1);
  } catch (e) {
    if (formatErr(e).includes("expired") || formatErr(e).includes("333444")) {
      badgeWarn("JSSDK 签名过期，重新拉取鉴权参数后重试");
      await runJssdkConfig(2);
      return;
    }
    throw e;
  }
}

async function requestBadgeAuth(): Promise<boolean> {
  const tt = getTt();
  if (!tt?.getSetting || !tt.authorize) {
    badgeWarn("跳过角标授权：getSetting/authorize 不可用");
    return false;
  }
  await new Promise((r) => setTimeout(r, 1000));
  const setting = await new Promise<{ authSetting?: Record<string, boolean> }>((resolve, reject) => {
    tt.getSetting!({
      success: (res: unknown) => resolve((res as { authSetting?: Record<string, boolean> }) ?? {}),
      fail: (err: unknown) => reject(err),
    });
  });
  const auth = setting.authSetting ?? {};
  const hasScope = Object.prototype.hasOwnProperty.call(auth, SCOPE_BADGE);
  badgeLog(`getSetting hasScope=${hasScope} appBadge=${String(auth[SCOPE_BADGE])}`);
  if (!hasScope) {
    badgeLog("首次申请 scope.appBadge");
    await new Promise<void>((resolve, reject) => {
      tt.authorize!({
        scope: SCOPE_BADGE,
        success: () => resolve(),
        fail: (err: unknown) => reject(err),
      });
    });
    badgeLog("authorize scope.appBadge 成功");
    return true;
  }
  const granted = auth[SCOPE_BADGE] === true;
  if (!granted) {
    badgeWarn("用户已拒绝 scope.appBadge，请到 关于 中开启应用角标");
  }
  return granted;
}

export async function refreshFeishuAppBadge(): Promise<number> {
  if (!shouldInitFeishuBadge()) return 0;
  try {
    const res = await syncFeishuAppBadge();
    if (res.skipped) {
      badgeLog("角标同步已跳过（系统配置已关闭飞书应用角标）");
      return 0;
    }
    badgeLog(`服务端角标同步 badgeNum=${res.badgeNum} success=${String(res.success)}`);
    const tt = getTt();
    if (tt?.updateBadge && res.badgeNum >= 0) {
      await new Promise<void>((resolve, reject) => {
        tt.updateBadge!({
          badgeNum: res.badgeNum,
          success: () => resolve(),
          fail: (err: unknown) => reject(err),
        });
      });
      badgeLog(`客户端 updateBadge 成功 badgeNum=${res.badgeNum}`);
    } else {
      badgeWarn("跳过 updateBadge：tt.updateBadge 不可用");
    }
    return res.badgeNum;
  } catch (e) {
    badgeWarn(`角标刷新失败 ${formatErr(e)}`);
    return 0;
  }
}

export function useFeishuAppBadgeOnMount() {
  onMounted(() => {
    logBadgeEnvironment();
    if (!shouldInitFeishuBadge()) {
      badgeLog(
        "跳过角标：当前为普通浏览器或非飞书内嵌页，不加载 H5 JSSDK。请用飞书客户端从「我的常用」打开本应用（勿用系统浏览器/新标签页）",
      );
      void flushBadgeLogs();
      return;
    }
    badgeLog("开始角标初始化（飞书内嵌环境）");
    void (async () => {
      try {
        const { enabled } = await getFeishuAppBadgeEnabled();
        if (!enabled) {
          badgeLog("跳过角标：系统配置已关闭飞书应用角标");
          return;
        }
        await ensureFeishuH5Sdk();
        await configJssdk();
        const authed = await requestBadgeAuth();
        badgeLog(`角标授权结果 authed=${authed}`);
        const num = await refreshFeishuAppBadge();
        badgeLog(`角标初始化完成 badgeNum=${num}`);
      } catch (e) {
        badgeWarn(`角标初始化失败 ${formatErr(e)}`);
      } finally {
        await flushBadgeLogs();
      }
    })();
  });
}
