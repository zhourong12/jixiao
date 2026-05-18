import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const distDir = path.join(root, "dist");
const clientDir = path.join(distDir, "client");
const serverDir = path.join(distDir, "server");
const jarSource = path.join(root, "server-java", "target", "server-0.0.1-SNAPSHOT.jar");
const jarTarget = path.join(serverDir, "server-0.0.1-SNAPSHOT.jar");

function run(command, args, options) {
  const result = spawnSync(command, args, { stdio: "inherit", ...options });
  if (result.error) {
    console.error(result.error.message);
    process.exit(1);
  }
  if ((result.status ?? 1) !== 0) {
    process.exit(result.status ?? 1);
  }
}

run(process.execPath, [path.join(root, "scripts", "build-server-java.mjs")]);
run("npm", ["run", "build", "--prefix", "client-vue"], { shell: process.platform === "win32" });

if (!fs.existsSync(clientDir)) {
  console.error(`未找到前端 dist：${clientDir}`);
  process.exit(1);
}

run(process.execPath, [path.join(root, "scripts", "copy-server-jar-to-dist.mjs")]);

console.log(`前端静态资源：${clientDir}`);
console.log(`后端 JAR：${jarTarget}`);
