import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const jarSource = path.join(root, "server-java", "target", "server-0.0.1-SNAPSHOT.jar");
const serverDir = path.join(root, "dist", "server");
const jarTarget = path.join(serverDir, "server-0.0.1-SNAPSHOT.jar");

if (!fs.existsSync(jarSource)) {
  console.error(`未找到后端 JAR：${jarSource}`);
  process.exit(1);
}
fs.mkdirSync(serverDir, { recursive: true });
fs.copyFileSync(jarSource, jarTarget);
console.log(`后端 JAR 已复制到 ${jarTarget}`);
