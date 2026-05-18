import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const serverDir = path.join(root, "server-java");
const mvnwCmd = path.join(serverDir, "mvnw.cmd");
const args = ["clean", "-DskipTests", "package"];

let command = "mvn";
let commandArgs = args;
let options = { cwd: serverDir, stdio: "inherit", shell: false };

if (process.platform === "win32" && fs.existsSync(mvnwCmd)) {
  command = mvnwCmd;
  options = { cwd: serverDir, stdio: "inherit", shell: true };
}

const result = spawnSync(command, commandArgs, options);
if (result.error) {
  console.error(result.error.message);
  process.exit(1);
}
if ((result.status ?? 1) !== 0) {
  process.exit(result.status ?? 1);
}

const copyScript = path.join(root, "scripts", "copy-server-jar-to-dist.mjs");
const copyResult = spawnSync(process.execPath, [copyScript], { cwd: root, stdio: "inherit" });
if (copyResult.error) {
  console.error(copyResult.error.message);
  process.exit(1);
}
process.exit(copyResult.status ?? 1);
