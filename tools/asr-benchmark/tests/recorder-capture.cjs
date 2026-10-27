// Offline UI regression: Chromium synthetic microphone, in-memory checkpoint fixture, no ASR/files.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(path.resolve(__dirname, '../../../apps/web/node_modules/@playwright/test'));

(async () => {
  const browser = await chromium.launch({ executablePath: chromium.executablePath(), headless: true,
    args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] });
  try {
    const context = await browser.newContext({ permissions: ['microphone'] });
    await context.addInitScript(() => {
      window.__faroMicTracks = [];
      window.__faroHoldMic = false;
      const getMedia = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
      navigator.mediaDevices.getUserMedia = async options => {
        const media = await getMedia(options);
        window.__faroMicTracks.push(...media.getTracks());
        if (window.__faroHoldMic) await new Promise(resolve => { window.__faroReleaseMic = resolve; });
        return media;
      };
    });
    const page = await context.newPage(), errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const root = path.resolve(__dirname, '..'), url = 'http://127.0.0.1:8768/synthetic-capture-test/';
    const samples = require('../corpus.json').samples;
    let session = 'synthetic-fixture', restores = 0, rejectedSaves = 0, clearArrived, releaseClear;
    const clips = [], info = () => ({ enabled: true, session_id: session,
      clip_count: clips.length, ttl_hours: 24, expires_at_utc: '2099-01-01T00:00:00Z' });
    const clearSeen = new Promise(resolve => { clearArrived = resolve; });
    // Fulfill every request locally: no running guide or private recovery directory is accessed.
    await page.route('**/*', async route => {
      const request = route.request(), name = request.url().slice(url.length);
      assert.ok(request.url().startsWith(url), 'No request may leave the synthetic fixture');
      if (name === '' || name === 'record.js') {
        await route.fulfill({ contentType: name ? 'text/javascript' : 'text/html',
          body: fs.readFileSync(path.join(root, name || 'recorder.html')) });
      } else if (name === 'corpus') {
        await route.fulfill({ json: require('../corpus.json').samples });
      } else if (name === 'settings') {
        await route.fulfill({ json: { compare_small: false, recovery: info() } });
      } else if (name === 'recovery-save') {
        const body = request.postDataJSON();
        if (body.recovery_session_id !== session) {
          rejectedSaves++;
          await route.fulfill({ status: 409, json: { code: 'RECOVERY_UNAVAILABLE' } });
        } else {
          const index = clips.findIndex(clip => clip.id === body.clip.id);
          if (index < 0) clips.push(body.clip); else clips[index] = body.clip;
          await route.fulfill({ json: info() });
        }
      } else if (name === 'recovery-clear') {
        clearArrived();
        await new Promise(resolve => { releaseClear = resolve; });
        clips.length = 0; session = 'cleared-session';
        await route.fulfill({ json: info() });
      } else if (name === 'recovery') {
        restores++;
        await route.fulfill({ json: { ...info(), clips } });
      } else {
        throw new Error(`Unexpected synthetic fixture route: ${name}`);
      }
    });
    await page.goto(url);
    await page.waitForFunction(count => document.getElementById('progress').max === count, samples.length);
    await page.locator('#adult').check();
    const start = async () => {
      await page.locator('#record').click();
      await page.waitForFunction(() => !document.getElementById('stop').disabled);
    };
    const released = async () => {
      await page.waitForFunction(() => window.__faroMicTracks.length > 0
        && window.__faroMicTracks.every(track => track.readyState === 'ended'), null, { timeout: 3000 });
      assert.equal(await page.evaluate(() => recorder === null && stream === null), true);
    };
    const attemptRestore = async () => {
      const before = await page.evaluate(() => generation), requests = restores;
      // Bypass the disabled button to verify the handler also protects its generation.
      await page.evaluate(() => document.getElementById('restore').onclick());
      assert.equal(await page.evaluate(() => generation), before, 'Blocked recovery must not invalidate capture');
      assert.equal(restores, requests, 'Blocked recovery must not request checkpoints');
      assert.equal(await page.locator('#restore').isDisabled(), true);
    };
    await start();
    await page.waitForTimeout(400);
    await page.locator('#stop').click();
    await released();
    await page.locator('#confirmed').check();
    await page.locator('#accept').click();
    await page.waitForFunction(() => document.getElementById('step').textContent === 'Frase 2 de 12');
    assert.equal(clips.length, 1);
    assert.equal(await page.locator('#restore').isEnabled(), true);

    // Recovery must be blocked while permission is pending, as well as after capture starts.
    await page.evaluate(() => { window.__faroHoldMic = true; });
    await page.locator('#record').click();
    await page.waitForFunction(() => typeof window.__faroReleaseMic === 'function');
    await attemptRestore();
    await page.evaluate(() => { window.__faroHoldMic = false; window.__faroReleaseMic(); });
    await page.waitForFunction(() => !document.getElementById('stop').disabled);
    await attemptRestore();
    await page.waitForTimeout(400);
    await page.locator('#stop').click();
    await released();
    await page.waitForFunction(() => !document.getElementById('confirmed').disabled);

    // A later recording must still be possible, and cancel must release its new stream.
    await start();
    await attemptRestore();
    await page.locator('#cancel').click();
    await released();
    assert.equal(await page.locator('#record').isEnabled(), true);

    // Exercise the actual 30-second timer with synthetic audio; no shortened timer or ASR.
    await start();
    await attemptRestore();
    await page.waitForFunction(() => !document.getElementById('confirmed').disabled, null, { timeout: 40000 });
    await released();
    assert.equal(await page.locator('#record').isEnabled(), true);

    // When idle, recovery still works and never reopens the microphone.
    const tracks = await page.evaluate(() => window.__faroMicTracks.length);
    await page.locator('#restore').click();
    await page.waitForFunction(() => document.getElementById('status').textContent.startsWith('1 grabaciones recuperadas'));
    assert.equal(restores, 1);
    assert.equal(await page.evaluate(() => window.__faroMicTracks.length), tracks);
    assert.equal(await page.evaluate(() => clips.length), 1);

    // A server-side 24-hour expiry rotates the session and drops its audio: the next accept
    // adopts the new session and re-checkpoints this page's accepted clips before its own.
    session = 'rotated-session'; clips.length = 0;
    await start();
    await page.waitForTimeout(400);
    await page.locator('#stop').click();
    await released();
    await page.waitForFunction(() => !document.getElementById('confirmed').disabled);
    await page.locator('#confirmed').check();
    await page.locator('#accept').click();
    await page.waitForFunction(() => document.getElementById('step').textContent === 'Frase 3 de 12');
    assert.equal(rejectedSaves, 1);
    assert.deepEqual(clips.map(clip => clip.id), [samples[0].id, samples[1].id]);
    assert.equal(await page.evaluate(() => recovery.session_id), 'rotated-session');

    // Cancel cannot interrupt a pending backup deletion, and the deletion result always applies.
    await page.locator('#clear').click();
    await clearSeen;
    assert.equal(await page.locator('#cancel').isDisabled(), true);
    const beforeCancel = await page.evaluate(() => generation);
    await page.evaluate(() => document.getElementById('cancel').onclick());
    assert.equal(await page.evaluate(() => generation), beforeCancel, 'Cancel must not orphan the deletion');
    releaseClear();
    await page.waitForFunction(() => recovery.clip_count === 0 && !busy);
    assert.equal(await page.locator('#record').isEnabled(), true);
    assert.equal(await page.locator('#restore').isDisabled(), true);
    assert.deepEqual(errors, []);
    console.log('PASS: recovery blocked during permission/capture; Stop, Cancel and 30-second limit release synthetic tracks; '
      + 'idle recovery works; rotated sessions are re-checkpointed; deletion cannot be cancelled.');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
