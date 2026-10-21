// 申請中の行で「AI 確認」を押し、帯の表示と、押した後も承認できること・承認済の行に AI 確認が出ないことを確かめる。
// 使い方: node scripts/check/e2e_ai_check.cjs <playwright-core のパス> <出力ディレクトリ> [<chromium の実行ファイル>]
// 前提: 画面 http://localhost:5173 とサーバーが起動している。
// ⛔ 接続先の DB に申請を 1 件足して承認する。サーバーに Dify のキーがあれば Dify を 1 回呼ぶ（無料枠のクレジットを使う）。
const { chromium } = require(process.argv[2]);
const out = process.argv[3];
const log = (...a) => console.log(...a);

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.argv[4] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  page.on('console', (m) => { if (m.type() === 'error') log('console-error:', m.text()); });
  page.on('response', (r) => { if (r.url().includes('/ai-check')) log('ai-check-response:', r.status(), r.url()); });
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' });

  const name = `AI確認 ${Date.now() % 100000}`;
  await page.fill('#applicantName', name);
  await page.selectOption('#equipmentId', { label: 'プロジェクター' });
  await page.fill('#startDate', '2026-11-20');
  await page.fill('#dueDate', '2026-11-21');
  await page.fill('#reason', '社外セミナーでの投影に使うため');
  await page.click('button.btn-submit');
  const row = page.locator('.row', { hasText: name });
  await row.waitFor();
  const group = page.locator('.row-group', { has: row });
  log('requested-actions:', (await row.locator('.col-actions').textContent()).trim());

  await row.locator('button', { hasText: 'AI 確認' }).click();
  // WHY: Dify の blocking 呼び出しは最大 100 秒（Dify連携.md §1）。それより少し長く待つ。
  await group.locator('.ai-check-text, .ai-check-error').first().waitFor({ timeout: 110000 });
  log('panel-label:', await group.locator('.ai-check-label').textContent());
  log('panel-text-count:', await group.locator('.ai-check-text').count());
  log('panel-error-count:', await group.locator('.ai-check-error').count());
  log('panel-body:', (await group.locator('.ai-check p').allTextContents()).join(' / '));
  await page.screenshot({ path: `${out}/e2e_ai_1_panel.png`, fullPage: true });

  await row.locator('button', { hasText: '承認' }).click();
  await row.locator('.badge', { hasText: '承認済' }).waitFor();
  log('after-approve-actions:', (await row.locator('.col-actions').textContent()).trim());
  log('after-approve-ai-button-count:', await row.locator('button', { hasText: 'AI 確認' }).count());
  await page.screenshot({ path: `${out}/e2e_ai_2_approved.png`, fullPage: true });

  await browser.close();
})().catch((e) => { console.error('E2E-FAIL:', e.message); process.exit(1); });
