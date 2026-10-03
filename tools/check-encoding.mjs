#!/usr/bin/env node
/**
 * 文件编码门禁：扫出被"用系统编码误读/误写"破坏过的源码与文档。
 *
 * 触发场景：用 PowerShell 的文本写入命令（Set-Content / Out-File 等）改 UTF-8 源码时，
 * 系统编码会把中文读成 GBK 再写回，文件里的中文变成 `锛?` / `鏂囦欢` 这类字符序列，
 * 或直接出现替换字符 U+FFFD。这类破坏不影响编译（字符串仍是合法内容），
 * 类型检查与 lint 都看不出来，只有人眼看界面或日志才会发现。
 *
 * 判定：出现替换字符 U+FFFD，或命中文档化了的 GBK 误读序列，即报 ERROR。
 * 确需在文档里举例写出这些序列时，在该行写 `编码豁免：<理由>`。
 *
 * 退出码：有命中 → 1；否则 0。
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = process.cwd();

/** 扫描根：与注释门禁同一批根，另加 SQL 与容器编排目录 */
const SCAN_ROOTS = [
  'knowledge-common/src',
  'knowledge-infra/src',
  'knowledge-auth/src',
  'knowledge-file-center/src',
  'knowledge-model/src',
  'knowledge-vector/src',
  'knowledge-worker/src',
  'knowledge-biz/src',
  'web/src',
  'web/docs',
  'docs',
  'tools',
  'docker',
  'sql',
  'README.md',
  '.husky',
];

/** 只看文本类文件 */
const TEXT_EXT = new Set([
  '.java', '.ts', '.vue', '.css', '.mjs', '.js', '.md', '.sql', '.yml', '.yaml',
  '.json', '.html', '.sh', '.xml', '.properties',
]);

/**
 * GBK 误读序列：正常中文写作里不会连着出现的组合（单字如「涓」「鏄」可能是真字，故用多字组合判定）。
 */
const MOJIBAKE = [
  '锛?', '锛屛', '鐨勶', '鏂囦欢', '涓€', '鍜屛', '鏄剧ず', '绗?', '娆℃', '鍏抽棴',
  '寮€濮', '鑰楁椂', '浠诲姟', '鏃堕棿', '澶辫触', '鎻愮ず', '瀵硅薄', '缁撴灉',
  '鏌ヨ', '杈撳叆', '杈撳嚭', '鍒嗘瀽', '鐭ヨ瘑', '搴旂敤', '鏈嶅姟', '鎺ュ彛',
  '澶勭悊', '鏋勫缓', '绱㈠紩', '鍚戦噺', '鍒囩墖', '棰勫', '瀛楁', '鏂囨。',
];

/** 本文件自己必须写出这些序列作为判定依据，故跳过自身 */
const SELF = 'tools/check-encoding.mjs';

const HITS = [];
let scanned = 0;

function isExempt(line) {
  return line.includes('编码豁免：');
}

function scanFile(absPath) {
  const rel = relative(ROOT, absPath).split('\\').join('/');
  if (rel === SELF) {
    return;
  }
  let text;
  try {
    text = readFileSync(absPath, 'utf8');
  } catch {
    return;
  }
  scanned += 1;
  const lines = text.split(/\r?\n/);
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    if (isExempt(line)) {
      continue;
    }
    if (line.includes('\uFFFD')) {
      HITS.push({ file: relative(ROOT, absPath), line: i + 1, kind: '替换字符 U+FFFD' });
      continue;
    }
    const token = MOJIBAKE.find((m) => line.includes(m));
    if (token) {
      HITS.push({ file: relative(ROOT, absPath), line: i + 1, kind: `GBK 误读「${token}」` });
    }
  }
}

function walk(target) {
  let st;
  try {
    st = statSync(target);
  } catch {
    return;
  }
  if (st.isFile()) {
    if (TEXT_EXT.has(target.slice(target.lastIndexOf('.')))) {
      scanFile(target);
    }
    return;
  }
  for (const entry of readdirSync(target)) {
    if (entry === 'node_modules' || entry === 'target' || entry === 'dist' || entry === '.git') {
      continue;
    }
    walk(join(target, entry));
  }
}

for (const root of SCAN_ROOTS) {
  walk(join(ROOT, root));
}

if (HITS.length === 0) {
  console.log(`扫描文件 ${scanned} 个；未发现编码破坏 ✓`);
  process.exit(0);
}

console.log(`编码门禁命中 ${HITS.length} 处（文件被系统编码误读/误写过）：`);
for (const hit of HITS) {
  console.log(`  ${hit.file}:${hit.line}: ${hit.kind}`);
}
console.log('  修法：用 UTF-8 工具（编辑器 / edit 工具）重写受影响的行；不要用 PowerShell 的 Set-Content 改源码。');
process.exit(1);
