// 画面から 空送信の 400 → 申請→承認→貸出→返却 → 絞り込み を操作し、各段の状態とスクリーンショットを出す。
// 使い方: node scripts/check/e2e_flow.cjs <playwright-core のパス> <出力ディレクトリ> [<chromium の実行ファイル>]
// 前提: 画面 http://localhost:5173 とサーバーが起動している。⛔ 接続先の DB に申請を 1 件足して返却まで進める。
const { chromium } = require(process.argv[2]);
const out = process.argv[3];
const log = (...a) => console.log(...a);

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.argv[4] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  page.on('console', (m) => { if (m.type() === 'error') log('console-error:', m.text()); });
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' });
  log('title:', await page.title());
  log('equipment-options:', (await page.locator('#equipmentId option').allTextContents()).join(' | '));
  await page.screenshot({ path: `${out}/e2e_0_initial.png`, fullPage: true });

  // 異常系: 空のまま送信 → サーバーの 400 の details が画面に出るか
  await page.click('button.btn-submit');
  await page.waitForSelector('.errors li');
  log('empty-submit-errors:', (await page.locator('.errors li').allTextContents()).join(' | '));
  await page.screenshot({ path: `${out}/e2e_1_errors.png`, fullPage: true });

  // 正常系: 申請
  const name = `画面確認 ${Date.now() % 100000}`;
  await page.fill('#applicantName', name);
  await page.selectOption('#equipmentId', { label: 'ノート PC' });
  await page.fill('#startDate', '2026-11-10');
  await page.fill('#dueDate', '2026-11-12');
  await page.fill('#reason', '画面からの動作確認');
  await page.click('button.btn-submit');
  const row = page.locator('.row', { hasText: name });
  await row.waitFor();
  log('errors-after-valid-submit:', await page.locator('.errors').count());
  const state = async (label) => {
    const status = await row.locator('.badge').textContent();
    const buttons = (await row.locator('.col-actions').textContent()).trim();
    log(`${label}: status=${status} actions=${buttons} period=${(await row.locator('.col-period').textContent()).trim()}`);
  };
  await state('after-create');
  await page.screenshot({ path: `${out}/e2e_2_requested.png`, fullPage: true });

  for (const [btn, expect, shot] of [['承認', '承認済', 3], ['貸出', '貸出中', 4], ['返却', '返却済', 5]]) {
    await row.locator('button', { hasText: btn }).click();
    await row.locator('.badge', { hasText: expect }).waitFor();
    await state(`after-${btn}`);
    await page.screenshot({ path: `${out}/e2e_${shot}_${btn}.png`, fullPage: true });
  }

  // 絞り込み: 返却済 を押すとその行が残り、申請中 を押すと消えるか
  await page.click('.chip:has-text("返却済")');
  await page.waitForTimeout(500);
  log('filter-返却済-has-row:', await row.count());
  await page.click('.chip:has-text("申請中")');
  await page.waitForTimeout(500);
  log('filter-申請中-has-row:', await row.count());
  await page.screenshot({ path: `${out}/e2e_6_filter.png`, fullPage: true });

  await browser.close();
})().catch((e) => { console.error('E2E-FAIL:', e.message); process.exit(1); });
