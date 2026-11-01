#!/usr/bin/env node
// Runs translated web pages in headless Chrome and checks what they print.
//
// Usage: node scripts/run_browser_test.mjs <directory> <page.html> [<page.html> ...]
//
// The script serves the directory on a free localhost port (WebGPU needs a secure context, and localhost counts as
// one), opens each page in a fresh headless Chrome and reads the page's console output. A page must print the line
// "JWEBGPU_DONE" when it has finished, and it passes when it printed "RESULT all PASS" before that. The output of
// the page is echoed, so the pixels that the sample reads back are visible in the log.
//
// The browser is found in this order: the JWEBGPU_CHROME environment variable, then google-chrome,
// google-chrome-stable, chromium, chromium-browser and chrome on the PATH. When there is none, the script prints
// "SKIPPED" and exits with 0, so a machine without a browser does not fail the build.
//
// The default flags select a software WebGPU adapter (SwiftShader on Vulkan), so no graphics card is needed.
// Override them with JWEBGPU_CHROME_FLAGS (a space separated list). "--no-sandbox" is added when running as root.
//
// Exit codes: 0 passed or skipped, 1 a page failed, 2 bad usage.

import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';

const TIMEOUT_MS = Number(process.env.JWEBGPU_BROWSER_TIMEOUT_MS ?? 60000);
const DEFAULT_FLAGS = [
  '--headless=new',
  '--enable-unsafe-webgpu',
  '--enable-features=Vulkan',
  '--use-vulkan=swiftshader',
  '--use-angle=swiftshader',
  '--enable-unsafe-swiftshader',
  '--disable-vulkan-surface',
  '--disable-gpu-sandbox',
];
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.wasm': 'application/wasm' };

function findChrome() {
  if (process.env.JWEBGPU_CHROME) {
    return process.env.JWEBGPU_CHROME;
  }
  for (const name of ['google-chrome', 'google-chrome-stable', 'chromium', 'chromium-browser', 'chrome']) {
    const found = spawnSync(process.platform === 'win32' ? 'where' : 'which', [name], { encoding: 'utf8' });
    if (found.status === 0 && found.stdout.trim()) {
      return found.stdout.trim().split(/\r?\n/)[0];
    }
  }
  return null;
}

function serve(dir) {
  const server = http.createServer((request, response) => {
    const file = path.join(dir, decodeURIComponent(request.url.split('?')[0]));
    if (!file.startsWith(dir)) {
      response.writeHead(403).end();
      return;
    }
    fs.readFile(file, (error, data) => {
      if (error) {
        response.writeHead(404).end();
        return;
      }
      response.writeHead(200, { 'Content-Type': MIME[path.extname(file)] ?? 'application/octet-stream' });
      response.end(data);
    });
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)));
}

async function runPage(chrome, flags, port, page) {
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jwebgpu-chrome-'));
  const args = [...flags, `--user-data-dir=${profile}`, '--enable-logging=stderr', '--v=0',
    `http://127.0.0.1:${port}/${page}`];
  const browser = spawn(chrome, args, { stdio: ['ignore', 'pipe', 'pipe'] });
  const lines = [];
  let passed = false;
  const finished = new Promise((resolve) => {
    let buffer = '';
    const onData = (chunk) => {
      buffer += chunk.toString();
      let at;
      while ((at = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, at);
        buffer = buffer.slice(at + 1);
        const match = line.match(/CONSOLE[^\]]*\]\s*"(.*)", source: /);
        if (match) {
          lines.push(match[1]);
          console.log(`  [${page}] ${match[1]}`);
          if (match[1] === 'RESULT all PASS') {
            passed = true;
          }
          if (match[1] === 'JWEBGPU_DONE') {
            resolve(true);
          }
        }
      }
    };
    browser.stderr.on('data', onData);
    browser.stdout.on('data', onData);
    browser.on('exit', () => resolve(false));
  });
  const timer = new Promise((resolve) => setTimeout(() => resolve(false), TIMEOUT_MS));
  const done = await Promise.race([finished, timer]);
  const exited = new Promise((resolve) => browser.once('exit', resolve));
  browser.kill('SIGKILL');
  await Promise.race([exited, new Promise((resolve) => setTimeout(resolve, 5000))]);
  try {
    fs.rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
  } catch {
    // Chrome's helper processes may still hold the folder. It is a temporary folder, so leaving it is harmless.
  }
  if (!done) {
    console.log(`  [${page}] the page did not finish within ${TIMEOUT_MS / 1000} seconds`);
  }
  return done && passed;
}

async function main() {
  const [dirArg, ...pages] = process.argv.slice(2);
  if (!dirArg || pages.length === 0) {
    console.error('usage: node run_browser_test.mjs <directory> <page.html> [<page.html> ...]');
    process.exit(2);
  }
  const chrome = findChrome();
  if (!chrome) {
    console.log('SKIPPED: no Chrome or Chromium found (set JWEBGPU_CHROME to run the browser test).');
    return;
  }
  const flags = process.env.JWEBGPU_CHROME_FLAGS ? process.env.JWEBGPU_CHROME_FLAGS.split(/\s+/) : [...DEFAULT_FLAGS];
  if (typeof process.getuid === 'function' && process.getuid() === 0) {
    flags.push('--no-sandbox');
  }
  const dir = path.resolve(dirArg);
  const server = await serve(dir);
  const port = server.address().port;
  console.log(`Browser: ${chrome}`);
  let failed = 0;
  for (const page of pages) {
    console.log(`Running ${page}`);
    const ok = await runPage(chrome, flags, port, page);
    console.log(`${ok ? 'PASSED' : 'FAILED'}: ${page}`);
    failed += ok ? 0 : 1;
  }
  server.close();
  process.exit(failed === 0 ? 0 : 1);
}

main();
