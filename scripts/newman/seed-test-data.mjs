import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const envPath = path.join(root, 'server-java', '.env');
const sqlDir = path.join(root, 'server-java', 'database', 'sql');
const sqlPaths = [
  path.join(sqlDir, 'seed-demo-employees-extra.sql'),
  path.join(sqlDir, 'seed-devteam-test-personas.sql'),
  path.join(sqlDir, 'seed-newman-reset.sql'),
];

function loadEnv(filePath) {
  const out = { ...process.env };
  if (!fs.existsSync(filePath)) return out;
  for (const line of fs.readFileSync(filePath, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const idx = trimmed.indexOf('=');
    if (idx <= 0) continue;
    const key = trimmed.slice(0, idx).trim();
    const value = trimmed.slice(idx + 1).trim();
    if (key) out[key] = value;
  }
  return out;
}

function parseJdbc(url) {
  const m = String(url || '').match(/^jdbc:mysql:\/\/([^:/]+):(\d+)\/([^?]+)/);
  if (!m) throw new Error(`无法解析 JIXIAO2_JDBC_URL: ${url}`);
  return { host: m[1], port: m[2], database: m[3] };
}

const env = loadEnv(envPath);
const jdbcUrl = env.JIXIAO2_JDBC_URL || env.MYSQL_URL;
const user = env.JIXIAO2_JDBC_USER || env.MYSQL_USER;
const password = env.JIXIAO2_JDBC_PASSWORD || env.MYSQL_PASSWORD;
if (!jdbcUrl || !user || password == null) {
  console.error('缺少 JIXIAO2_JDBC_URL / JIXIAO2_JDBC_USER / JIXIAO2_JDBC_PASSWORD（见 server-java/.env）');
  process.exit(1);
}

const { host, port, database } = parseJdbc(jdbcUrl);
const mysqlBin = env.JIXIAO2_MYSQL_BIN || 'mysql';

for (const sqlPath of sqlPaths) {
  if (!fs.existsSync(sqlPath)) {
    console.error(`缺少 SQL 文件: ${sqlPath}`);
    process.exit(1);
  }
  const sql = fs.readFileSync(sqlPath, 'utf8');
  const result = spawnSync(
    mysqlBin,
    ['-h', host, '-P', port, '-u', user, database, '--default-character-set=utf8mb4'],
    {
      input: sql,
      env: { ...process.env, MYSQL_PWD: password },
      encoding: 'utf8',
    },
  );

  if (result.error) {
    if (result.error.code === 'ENOENT') {
      console.error('未找到 mysql 客户端，跳过数据库种子/清理（Newman 将使用独立周期跑批）');
      process.exit(0);
    }
    console.error(`执行 ${mysqlBin} 失败: ${result.error.message}`);
    console.error(`可手动执行: ${sqlPath}`);
    process.exit(1);
  }
  if (result.status !== 0) {
    console.error(result.stderr || result.stdout || `mysql 退出码 ${result.status}`);
    process.exit(result.status || 1);
  }
  console.error(`已执行: ${sqlPath}`);
}
