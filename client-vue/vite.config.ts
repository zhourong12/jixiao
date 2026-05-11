import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

const rootDir = fileURLToPath(new URL(".", import.meta.url));

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, rootDir, "");
  const target = env.VITE_API_TARGET || env.VITE_JAVA_API || "http://127.0.0.1:8081";
  const outDir = env.VITE_OUT_DIR
    ? path.resolve(rootDir, env.VITE_OUT_DIR)
    : path.resolve(rootDir, "../dist/client");
  return {
    root: rootDir,
    plugins: [vue()],
    resolve: {
      alias: {
        "@": path.resolve(rootDir, "src"),
      },
    },
    server: {
      port: Number(process.env.CLIENT_DEV_PORT) || 5174,
      proxy: {
        "/auth": { target, changeOrigin: true },
        "/api": { target, changeOrigin: true },
      },
    },
    build: {
      outDir,
      emptyOutDir: true,
      reportCompressedSize: false,
    },
  };
});
