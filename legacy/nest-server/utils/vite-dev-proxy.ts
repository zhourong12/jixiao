import httpProxy from 'http-proxy';
import type { Server } from 'http';

let proxy: httpProxy | null = null;

export function isViteDevPath(pathname: string): boolean {
  if (!pathname) return false;
  if (pathname === '/ws' || pathname.startsWith('/ws?')) return true;
  if (pathname.includes('.hot-update')) return true;
  if (pathname.startsWith('/@')) return true;
  if (pathname.startsWith('/__vite')) return true;
  if (pathname.startsWith('/.vite/')) return true;
  if (pathname.startsWith('/node_modules')) return true;
  if (pathname.startsWith('/client/')) return true;
  if (pathname.startsWith('/src/')) return true;
  if (pathname.startsWith('/dev/')) return true;
  return false;
}

export function getViteProxy(): httpProxy {
  if (!proxy) {
    const port = Number(process.env.CLIENT_DEV_PORT || 5174);
    proxy = httpProxy.createProxyServer({
      target: `http://localhost:${port}`,
      ws: true,
      changeOrigin: true,
    });
  }
  return proxy;
}

export function attachViteProxyUpgrade(server: Server): void {
  if (process.env.NODE_ENV !== 'development') return;
  server.on('upgrade', (req, socket, head) => {
    const raw = req.url || '';
    const path = raw.split('?')[0] || '';
    if (!isViteDevPath(path)) return;
    getViteProxy().ws(req, socket, head);
  });
}
