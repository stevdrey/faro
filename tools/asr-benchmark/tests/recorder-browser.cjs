// Optional local integration check; always uses Chromium's synthetic microphone.
const assert = require('node:assert/strict');
const path = require('node:path');
const url = process.argv[2];
if (!url || new URL(url).hostname !== '127.0.0.1') throw new Error('Pass the recorder loopback URL');
const {chromium} = require(path.resolve(__dirname, '../../../apps/web/node_modules/@playwright/test'));
(async()=>{
 const browser=await chromium.launch({executablePath:chromium.executablePath(),headless:true,
  args:['--use-fake-ui-for-media-stream','--use-fake-device-for-media-stream']});
 try {
 const context=await browser.newContext({permissions:['microphone'],viewport:{width:1000,height:1000}});
 await context.addInitScript(()=>{
  const create=URL.createObjectURL.bind(URL), revoke=URL.revokeObjectURL.bind(URL);
  window.__faroLiveUrls=new Set();
  URL.createObjectURL=value=>{const url=create(value);window.__faroLiveUrls.add(url);return url;};
  URL.revokeObjectURL=url=>{window.__faroLiveUrls.delete(url);return revoke(url);};
  window.__faroMicRequests=0;
  const getMedia=navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
  navigator.mediaDevices.getUserMedia=options=>{window.__faroMicRequests++;return getMedia(options);};
 });
 const page=await context.newPage(); const errors=[]; page.on('pageerror', e=>errors.push(e.message));
 page.on('crash',()=>console.error('Synthetic test page crashed'));
 await page.goto(url);
 await page.waitForFunction(()=>document.querySelector('#step').textContent==='Frase 1 de 12');
 assert.equal(await page.locator('#record').isDisabled(),true);
 await page.locator('#adult').check();
 for(let i=0;i<12;i++){
  await page.locator('#record').click();
  await page.waitForFunction(()=>!document.querySelector('#stop').disabled);
  await page.waitForTimeout(400);
  await page.locator('#stop').click();
  await page.waitForFunction(()=>!document.querySelector('#confirmed').disabled);
  assert.equal(await page.locator('#accept').isDisabled(),true);
  await page.locator('#confirmed').check(); await page.locator('#accept').click();
 }
 const settings=await (await page.request.get(new URL('settings',url).href)).json();
 if(settings.recovery.enabled){
  assert.equal(settings.recovery.clip_count,12);
  const hashes=()=>page.evaluate(async()=>Promise.all(clips.map(async clip=>
   Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',clip.wav))).join(','))));
  const before=await hashes();
  await page.reload(); await page.locator('#adult').check(); await page.locator('#restore').click();
  await page.waitForFunction(()=>document.querySelector('#step').textContent==='Las 12 frases están listas.');
  assert.deepEqual(await hashes(),before);
  assert.equal(await page.evaluate(()=>window.__faroMicRequests),0);
  console.log('Recovered 12 identical synthetic WAVs after reload, without reopening the microphone.');
 }
 assert.equal(await page.locator('#measure').isEnabled(),true);
 console.log('Recorded 12 synthetic clips; measuring locally.');
 await page.locator('#measure').click();
 await page.waitForFunction(()=>!document.querySelector('#results').hidden,{},{timeout:420000});
 const report=JSON.parse(await page.locator('#details').textContent());
 assert.equal(report.dataset.kind,'adult-own-voice');
 assert.equal(report.candidates[0].scores.speech_clips,12);
 assert.equal(report.candidates[0].requests,39);
 assert.equal(report.candidates[0].technical_failures_all_passes,0);
 assert.equal('audio_sha256' in report.dataset,false);
 assert.equal('phrase_comparisons' in report,false);
 assert.equal('small_phrase_comparisons' in report,false);
 if(settings.compare_small){
  assert.equal(report.candidates[1].model_id,'small-multilingual');
  assert.equal(report.candidates[1].requests,39);
  assert.equal(report.candidates[1].technical_failures_all_passes,0);
  assert.equal(report.comparison.parallel_inference,false);
  await page.locator('#model-choice').selectOption('small-multilingual');
  assert.equal(await page.locator('#phrase-comparisons article').count(),13);
  await page.locator('#model-choice').selectOption('base-multilingual');
 }
 assert.equal(await page.locator('#phrase-comparisons article').count(),13);
 assert.equal(await page.locator('#phrase-comparisons audio').count(),12);
 assert.equal(await page.getByText('Números extraídos por el evaluador',{exact:true}).count(),13);
 assert.equal(await page.getByRole('heading',{name:'Control de silencio generado',exact:true}).count(),1);
 assert.equal(await page.locator('#phrase-comparisons script').count(),0);
 assert.equal(await page.evaluate(()=>window.__faroLiveUrls.size),12);
 console.log('Verified real comparison cards; checking rendering and erasure.');
 const fixture=require('../corpus.json');
 const stub={...structuredClone(report),phrase_comparisons:[...fixture.samples.map((sample,index)=>({
  ordinal:index+1,kind:'speech',reference_text:sample.text,transcript:'seis',status:'transcribed',
  expected_numbers:sample.critical_numbers,extracted_numbers:[6],numbers_match:false,
  transcript_diagnostics:{version:1,content_kind:'speech_or_unclassified',nonverbal_labels:[],numeric_ambiguities:[]}})),{
  ordinal:13,kind:'silence',reference_text:'',transcript:'[Música]',status:'transcribed',expected_numbers:[],extracted_numbers:[],numbers_match:null,
  transcript_diagnostics:{version:1,content_kind:'nonverbal_only',nonverbal_labels:['[Música]'],numeric_ambiguities:[]}}]};
 stub.phrase_comparisons[0].transcript='<img src=x onerror="alert(1)">';
 stub.phrase_comparisons[0].extracted_numbers=[]; stub.phrase_comparisons[0].numbers_match=false;
 Object.assign(stub.phrase_comparisons[1],{status:'technical_failure',transcript:null,extracted_numbers:null,numbers_match:null,transcript_diagnostics:null});
 Object.assign(stub.phrase_comparisons[2],{status:'empty',transcript:' ',extracted_numbers:[],numbers_match:false,
  transcript_diagnostics:{version:1,content_kind:'empty',nonverbal_labels:[],numeric_ambiguities:[]}});
 Object.assign(stub.phrase_comparisons[3],{transcript:'[Risas] seis',
  transcript_diagnostics:{version:1,content_kind:'mixed',nonverbal_labels:['[Risas]'],numeric_ambiguities:[]}});
 Object.assign(stub.phrase_comparisons[11],{transcript:'10 y 6 más 1 da 10 y 7',extracted_numbers:[10,6,1,10,7],
  transcript_diagnostics:{version:1,content_kind:'speech_or_unclassified',nonverbal_labels:[],numeric_ambiguities:[
   {text:'10 y 6',components:[10,6]},{text:'10 y 7',components:[10,7]}]}});
 await page.route('**/measure',route=>route.fulfill({json:stub}));
 await page.locator('#measure').click();
 await page.waitForFunction(()=>!document.querySelector('#results').hidden);
 assert.equal(await page.getByText(stub.phrase_comparisons[0].transcript,{exact:true}).count(),1);
 assert.equal(await page.locator('#phrase-comparisons img').count(),0);
 assert.equal(await page.getByText('No disponibles por fallo técnico.',{exact:true}).count(),1);
 assert.equal(await page.getByText('Respuesta sin texto.',{exact:true}).count()>=1,true);
 assert.equal(await page.getByText('Solo etiquetas no verbales',{exact:true}).count(),1);
 assert.equal(await page.getByText('Texto con etiquetas no verbales',{exact:true}).count(),1);
 assert.equal(await page.getByText('Solo hay etiquetas no verbales; no hay palabras fuera de esas etiquetas.',{exact:true}).count(),1);
 assert.equal(await page.getByText('Variantes numéricas ambiguas',{exact:true}).count(),1);
 const ambiguousCard=await page.locator('#phrase-comparisons article').nth(11).textContent();
 assert.ok(ambiguousCard.includes('«10 y 6» → componentes [10,6]'));
 assert.ok(ambiguousCard.includes('«10 y 7» → componentes [10,7]'));
 assert.ok(ambiguousCard.includes('[10,6,1,10,7]'));
 assert.ok(ambiguousCard.includes('No se sumaron ni corrigieron automáticamente.'));
 assert.equal(await page.evaluate(()=>window.__faroLiveUrls.size),12); // Replaced results revoke prior URLs.
 await page.unroute('**/measure');
 let release, started;
 const held=new Promise(resolve=>release=resolve), began=new Promise(resolve=>started=resolve);
 await page.route('**/measure',async route=>{started();await held;try{await route.fulfill({json:stub});}catch{}});
 await page.locator('#measure').click(); await began;
 await page.locator('#clear').click();
 release(); await page.waitForTimeout(150);
 assert.equal(await page.locator('#results').isHidden(),true);
 assert.equal(await page.locator('#details').textContent(),'');
 assert.equal(await page.locator('#phrase-comparisons').textContent(),'');
 assert.equal(await page.locator('#phrase-comparisons audio').count(),0);
 assert.equal(await page.evaluate(()=>window.__faroLiveUrls.size),0);
 assert.equal(await page.locator('#measure').isDisabled(),true);
 if(settings.recovery.enabled){
  await page.waitForFunction(()=>document.querySelector('#recovery-info').textContent.startsWith('0 grabaciones'));
  const restored=await (await page.request.get(new URL('recovery',url).href)).json();
  assert.deepEqual(restored.clips,[]);
 }
 assert.deepEqual(errors,[]);
 console.log('PASS: matched local inference, comparison cards, optional WAV recovery, annotations and late-response-safe erasure; no page errors.');
 } finally {await browser.close();}
})().catch(error=>{console.error('FAIL: synthetic recorder browser verification',error.stack);process.exitCode=1;});
