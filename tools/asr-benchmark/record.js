"use strict";
const $ = id => document.getElementById(id);
let samples = [], clips = [], pending = null, previewUrl = null;
let recorder = null, stream = null, timer = null, tick = null, generation = 0, busy = false, abort = null, capturing = false;
let comparisonUrls = [];
let settingsLoaded = false, compareSmall = false, recovery = { enabled: false }, comparisonSets = {};

function status(message) { $("status").textContent = message; }
function stopTracks() { stream?.getTracks().forEach(track => track.stop()); stream = null; clearTimeout(timer); clearInterval(tick); }
function discardPending() {
  pending = null; $("preview").pause(); $("preview").removeAttribute("src"); $("preview").hidden = true;
  if (previewUrl) URL.revokeObjectURL(previewUrl);
  previewUrl = null; $("confirmed").checked = false;
}
function render() {
  const complete = samples.length > 0 && clips.length === samples.length;
  $("step").textContent = complete ? `Las ${samples.length} frases están listas.` : `Frase ${clips.length + 1} de ${samples.length}`;
  $("phrase").textContent = complete ? "Ya podés medir tus grabaciones." : samples[clips.length]?.text ?? "";
  $("progress").value = clips.length;
  $("record").disabled = busy || !!recorder || complete || !$("adult").checked || !samples.length || !settingsLoaded
    || (recovery.enabled && recovery.clip_count > 0 && clips.length === 0);
  $("record").textContent = pending ? "Grabar de nuevo" : "Grabar";
  $("stop").disabled = !recorder || recorder.state !== "recording";
  $("cancel").disabled = !capturing; // Only permission, recording or audio preparation; never saves/deletes.
  $("confirmed").disabled = !pending || busy;
  $("accept").disabled = !pending || busy || !$("confirmed").checked;
  $("measure").disabled = !complete || busy || !$("adult").checked;
  $("adult").disabled = busy || !!recorder;
  $("restore").disabled = busy || !!recorder || !$("adult").checked || !recovery.enabled || !recovery.clip_count || !samples.length || !settingsLoaded;
  $("model-choice").disabled = busy;
}
function cancelCapture() {
  generation++; if (recorder?.state === "recording") recorder.stop(); recorder = null;
  stopTracks(); busy = false; capturing = false; discardPending(); render();
}
function resetComparisons() {
  clearComparisons(); comparisonSets = {}; $("model-choice").replaceChildren();
  $("model-choice-label").hidden = true; $("model-summary").replaceChildren();
}
function clearAll({ deleteRecovery = false } = {}) {
  const measuring = !!abort;
  abort?.abort(); abort = null; cancelCapture(); clips = []; $("results").hidden = true;
  $("details").textContent = ""; $("summary").textContent = "";
  resetComparisons();
  status(measuring ? "Grabaciones borradas de esta página. La prueba ya enviada termina localmente y libera sus audios al finalizar."
    : recovery.enabled ? "Grabaciones borradas de esta página. El respaldo temporal sigue disponible."
      : "Grabaciones y resultados borrados de esta página."); render();
  if (deleteRecovery && recovery.enabled) {
    const ownGeneration = generation; busy = true; render();
    localPost("recovery-clear", {}).then(info => {
      updateRecovery(info); // Server state is authoritative even if the page moved on meanwhile.
      if (ownGeneration === generation) status("Grabaciones, respaldo y resultados borrados.");
    }).catch(() => { if (ownGeneration === generation) status("No se pudo borrar el respaldo del disco. Podés volver a intentar Borrar todo."); })
      .finally(() => { if (ownGeneration === generation) { busy = false; render(); } });
  }
}
async function localPost(path, payload, signal) {
  const response = await fetch(path, { method: "POST", headers: { "Content-Type": "application/json" },
    signal, body: JSON.stringify(payload) });
  if (!response.ok) {
    const error = new Error(); error.code = await response.json().then(body => body.code, () => undefined);
    throw error;
  }
  return response.json();
}
function checkpoint(clip) {
  return localPost("recovery-save", { adult_own_voice: true, references_confirmed: true,
    recovery_session_id: recovery.session_id, clip: { id: clip.id, wav_base64: base64(clip.wav) } });
}
async function resyncRecovery(ownGeneration) {
  // A 24-hour expiry rotates the server session: adopt it and re-checkpoint accepted clips once.
  const previous = recovery.session_id, response = await fetch("settings");
  if (!response.ok) throw new Error();
  const settings = await response.json();
  if (ownGeneration !== generation) return false;
  updateRecovery(settings.recovery);
  if (!recovery.enabled || recovery.session_id === previous) return false;
  for (const clip of clips) {
    const info = await checkpoint(clip);
    if (ownGeneration !== generation) return false;
    updateRecovery(info);
  }
  return true;
}
async function retryAfterRecoveryRotation(ownGeneration, request) {
  try { return await request(); }
  catch (error) {
    if (error.code !== "RECOVERY_UNAVAILABLE" || !(await resyncRecovery(ownGeneration))) throw error;
    return request();
  }
}
function updateRecovery(info) {
  recovery = { enabled: info.enabled, session_id: info.session_id, clip_count: info.clip_count,
    ttl_hours: info.ttl_hours, expires_at_utc: info.expires_at_utc };
  $("recovery-panel").hidden = !info.enabled;
  if (info.enabled) {
    $("recovery-info").textContent = `${info.clip_count} grabaciones respaldadas. Vencen el ${new Date(info.expires_at_utc).toLocaleString("es-CR")}. ` +
      "Carpeta local: tools/benchmark-results/private/voice-recovery/.";
  }
  render();
}
async function refreshSettings() {
  try {
    const response = await fetch("settings"); if (!response.ok) throw new Error();
    const settings = await response.json(); compareSmall = settings.compare_small; settingsLoaded = true;
    updateRecovery(settings.recovery);
    $("measure").textContent = compareSmall ? "Comparar base y small" : "Medir con mi voz";
    $("clear").textContent = recovery.enabled ? "Borrar todo, incluido el respaldo" : "Borrar todo";
    $("privacy-note").textContent = recovery.enabled
      ? "Respaldo temporal habilitado por solicitud del adulto. Cada grabación aceptada se guarda como WAV privado por hasta 24 horas. Las transcripciones quedan solo en memoria. El micrófono se activa solo al elegir Grabar; no se usan servicios en la nube."
      : "El micrófono se activa solo al elegir Grabar. Los audios y transcripciones quedan en memoria y se procesan solo en este equipo. No se guardan archivos ni se usan servicios en la nube.";
    $("visibility-note").textContent = recovery.enabled
      ? "Al ocultar o cerrar la página se borra su memoria; podés recuperar las grabaciones aceptadas desde el respaldo temporal. Borrar todo también elimina el respaldo."
      : "Mantené esta página visible: al ocultarla o cerrarla se borran tus grabaciones.";
    render();
  } catch { status("No se pudo comprobar la configuración local. Recargá la guía."); }
}
function clearComparisons() {
  $("phrase-comparisons").querySelectorAll("audio").forEach(audio => {
    audio.pause(); audio.removeAttribute("src"); audio.load();
  });
  $("phrase-comparisons").replaceChildren();
  comparisonUrls.forEach(url => URL.revokeObjectURL(url)); comparisonUrls = [];
}
function showComparisons(rows) {
  clearComparisons();
  for (const row of rows) {
    const card = document.createElement("article"); card.className = "comparison";
    const heading = document.createElement("h3");
    heading.textContent = row.kind === "speech" ? `Frase ${row.ordinal}` : "Control de silencio generado";
    card.append(heading);
    if (row.kind === "speech" && clips[row.ordinal - 1]) {
      const audio = document.createElement("audio"); audio.controls = true;
      const url = URL.createObjectURL(new Blob([clips[row.ordinal - 1].wav], { type: "audio/wav" }));
      comparisonUrls.push(url); audio.src = url;
      audio.setAttribute("aria-label", `Escuchar mi grabación de la frase ${row.ordinal}`);
      audio.onplay = () => $("phrase-comparisons").querySelectorAll("audio").forEach(other => {
        if (other !== audio) other.pause();
      });
      card.append(audio);
    }
    const fields = document.createElement("dl");
    const field = (label, value) => {
      const name = document.createElement("dt"), content = document.createElement("dd");
      name.textContent = label; content.textContent = value; fields.append(name, content);
    };
    field("Referencia confirmada", row.reference_text || "Silencio generado, sin habla.");
    field("Whisper transcribió (hipótesis)", row.status === "technical_failure" ? "Sin resultado: fallo técnico."
      : row.transcript.trim() ? row.transcript : "Respuesta sin texto.");
    field("Números esperados", JSON.stringify(row.expected_numbers));
    field("Números extraídos por el evaluador", row.extracted_numbers === null ? "No disponibles por fallo técnico."
      : JSON.stringify(row.extracted_numbers));
    const diagnostic = row.transcript_diagnostics;
    const contentNames = { empty: "Sin texto", nonverbal_only: "Solo etiquetas no verbales",
      mixed: "Texto con etiquetas no verbales", speech_or_unclassified: "Texto o contenido sin clasificar" };
    field("Tipo de salida", row.status === "technical_failure" ? "No disponible por fallo técnico."
      : contentNames[diagnostic?.content_kind] ?? "Clasificación no disponible.");
    if (diagnostic?.nonverbal_labels.length) {
      field("Etiquetas no verbales", diagnostic.nonverbal_labels.join(" · "));
    }
    if (diagnostic?.numeric_ambiguities.length) {
      field("Variantes numéricas ambiguas", diagnostic.numeric_ambiguities.map(item =>
        `«${item.text}» → componentes ${JSON.stringify(item.components)}`).join("\n") +
        "\nNo se sumaron ni corrigieron automáticamente. Revisá el audio para interpretar la expresión.");
    }
    card.append(fields);
    const outcome = document.createElement("p"); outcome.className = "outcome";
    outcome.textContent = row.status === "technical_failure" ? "Fallo técnico: no se compara la lista de números."
      : row.kind === "silence" ? (row.status === "empty" ? "Sin texto en el control de silencio."
        : diagnostic?.content_kind === "nonverbal_only" ? "Solo hay etiquetas no verbales; no hay palabras fuera de esas etiquetas."
        : diagnostic?.content_kind === "mixed" ? "El control recibió etiquetas no verbales y otro texto."
        : "El control recibió texto o contenido sin clasificar.")
      : row.numbers_match ? "Las listas de números coinciden." : "Las listas de números son distintas.";
    card.append(outcome); $("phrase-comparisons").append(card);
  }
}
function encodeWav(pcm) {
  const buffer = new ArrayBuffer(44 + pcm.length * 2), view = new DataView(buffer);
  const text = (offset, value) => [...value].forEach((c, i) => view.setUint8(offset + i, c.charCodeAt(0)));
  text(0, "RIFF"); view.setUint32(4, buffer.byteLength - 8, true); text(8, "WAVE"); text(12, "fmt ");
  view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true);
  view.setUint32(24, 16000, true); view.setUint32(28, 32000, true); view.setUint16(32, 2, true);
  view.setUint16(34, 16, true); text(36, "data"); view.setUint32(40, pcm.length * 2, true);
  for (let i = 0; i < pcm.length; i++) {
    const value = Math.max(-1, Math.min(1, pcm[i]));
    view.setInt16(44 + i * 2, Math.round(value * (value < 0 ? 32768 : 32767)), true);
  }
  return new Uint8Array(buffer);
}
async function normalize(blob) {
  if (!blob.size || blob.size > 8 * 1024 * 1024) throw new Error();
  const context = new AudioContext();
  let decoded;
  try { decoded = await context.decodeAudioData(await blob.arrayBuffer()); }
  finally { await context.close(); }
  if (!decoded.length || decoded.duration > 31) throw new Error();
  const frames = Math.min(480000, Math.floor(decoded.duration * 16000));
  if (!frames) throw new Error();
  const offline = new OfflineAudioContext(1, frames, 16000);
  const mono = offline.createBuffer(1, decoded.length, decoded.sampleRate), channel = mono.getChannelData(0);
  for (let c = 0; c < decoded.numberOfChannels; c++) {
    const input = decoded.getChannelData(c);
    for (let i = 0; i < channel.length; i++) channel[i] += input[i] / decoded.numberOfChannels;
  }
  const source = offline.createBufferSource(); source.buffer = mono; source.connect(offline.destination); source.start();
  return encodeWav((await offline.startRendering()).getChannelData(0));
}
$("record").onclick = async () => {
  const ownGeneration = ++generation; discardPending(); busy = true; capturing = true; render(); status("Esperando permiso del micrófono…");
  try {
    const granted = await navigator.mediaDevices.getUserMedia({ audio: true });
    if (ownGeneration !== generation) { granted.getTracks().forEach(track => track.stop()); return; }
    stream = granted; const chunks = []; const active = new MediaRecorder(stream); recorder = active;
    active.ondataavailable = event => { if (ownGeneration === generation && event.data.size) chunks.push(event.data); };
    active.onerror = () => { if (ownGeneration === generation) { cancelCapture(); status("No se pudo grabar. Podés intentar de nuevo."); } };
    active.onstop = async () => {
      if (ownGeneration !== generation) return;
      recorder = null; stopTracks(); busy = true; render(); status("Preparando tu grabación…");
      try {
        const wav = await normalize(new Blob(chunks, { type: active.mimeType }));
        if (ownGeneration !== generation) return;
        capturing = false;
        pending = wav; previewUrl = URL.createObjectURL(new Blob([wav], { type: "audio/wav" }));
        $("preview").src = previewUrl; $("preview").hidden = false;
        status("Escuchá la grabación. Si cambiaste alguna palabra, grabala de nuevo.");
      } catch { if (ownGeneration === generation) status("No se pudo preparar el audio. Grabá de nuevo."); }
      finally { if (ownGeneration === generation) { busy = false; capturing = false; render(); } }
    };
    active.start(); busy = false; const start = Date.now();
    status("Grabando · 0 s"); tick = setInterval(() => status(`Grabando · ${Math.floor((Date.now() - start) / 1000)} s`), 500);
    timer = setTimeout(() => { if (active.state === "recording") active.stop(); }, 30000); render();
  } catch {
    if (ownGeneration === generation) { cancelCapture(); status("No se pudo acceder al micrófono. Revisá el permiso y volvé a intentar."); }
  }
};
$("stop").onclick = () => { if (recorder?.state === "recording") recorder.stop(); };
$("cancel").onclick = () => { if (!capturing) return; cancelCapture(); status("Grabación cancelada."); };
$("adult").onchange = () => { if (!$("adult").checked) clearAll({ deleteRecovery: true }); render(); };
$("confirmed").onchange = render;
$("accept").onclick = async () => {
  if (!pending || !$("confirmed").checked) return;
  const ownGeneration = generation, clip = { id: samples[clips.length].id, wav: pending };
  busy = true; render();
  try {
    if (recovery.enabled) {
      status("Guardando respaldo privado…");
      const info = await retryAfterRecoveryRotation(ownGeneration, () => checkpoint(clip));
      if (ownGeneration !== generation) return;
      updateRecovery(info);
    }
    if (ownGeneration !== generation) return;
    clips.push(clip); discardPending(); status(recovery.enabled ? "Grabación aceptada y respaldada." : "");
  } catch { if (ownGeneration === generation) status("No se pudo guardar el respaldo. La grabación sigue aquí: volvé a intentar aceptarla."); }
  finally { if (ownGeneration === generation) { busy = false; render(); } }
};
$("clear").onclick = () => clearAll({ deleteRecovery: true });
$("restore").onclick = async () => {
  if (!$("adult").checked || busy || recorder) return;
  const ownGeneration = ++generation; busy = true; render();
  try {
    const response = await fetch("recovery"); if (!response.ok) throw new Error();
    const data = await response.json();
    if (ownGeneration !== generation) return;
    if (data.clips.length > samples.length || data.clips.some((item, index) => item.id !== samples[index].id)) throw new Error();
    clips = data.clips.map(item => ({ id: item.id, wav: Uint8Array.from(atob(item.wav_base64), c => c.charCodeAt(0)) }));
    updateRecovery(data); resetComparisons(); $("results").hidden = true;
    status(`${clips.length} grabaciones recuperadas, sin activar el micrófono.`);
  } catch { if (ownGeneration === generation) status("No se pudo recuperar el respaldo. No se borraron sus archivos."); }
  finally { if (ownGeneration === generation) { busy = false; render(); } }
};
function base64(bytes) {
  let binary = ""; for (let i = 0; i < bytes.length; i += 8192) binary += String.fromCharCode(...bytes.subarray(i, i + 8192));
  return btoa(binary);
}
$("measure").onclick = async () => {
  if (busy || clips.length !== samples.length || !$("adult").checked) return;
  const ownGeneration = ++generation; $("preview").pause(); busy = true;
  resetComparisons(); $("details").textContent = ""; $("summary").textContent = ""; $("results").hidden = true;
  const controller = new AbortController(); abort = controller; render();
  status(compareSmall ? "Comparando base y small con los mismos audios. Puede tardar varios minutos…"
    : "Midiendo con Whisper en tu equipo. Puede tardar alrededor de un minuto…");
  try {
    const report = await retryAfterRecoveryRotation(ownGeneration, () => localPost("measure", {
      adult_own_voice: true, references_confirmed: true,
      recovery_session_id: recovery.enabled ? recovery.session_id : undefined,
      clips: clips.map(c => ({ id: c.id, wav_base64: base64(c.wav) })) }, controller.signal));
    if (ownGeneration !== generation) return;
    const result = report.candidates[0], score = result.scores;
    $("summary").textContent = `Números críticos: ${score.all_critical_numbers_correct} de ${score.speech_clips} frases. ` +
      `Latencia p50: ${(result.latency_ms.p50 / 1000).toFixed(2)} s; p95: ${(result.latency_ms.p95 / 1000).toFixed(2)} s. ` +
      `Fallos técnicos: ${result.technical_failures_all_passes}.`;
    const { phrase_comparisons = [], small_phrase_comparisons = [], ...aggregate } = report;
    comparisonSets = { "base-multilingual": phrase_comparisons };
    if (small_phrase_comparisons.length) comparisonSets["small-multilingual"] = small_phrase_comparisons;
    const measured = report.candidates.filter(item => item.status === "measured");
    if (measured.length > 1) $("summary").textContent = measured.map(model =>
      `${model.model_id}: ${model.scores.all_critical_numbers_correct}/${model.scores.speech_clips} frases con todos los números; ` +
      `${model.technical_failures_all_passes} fallos técnicos`).join(". ");
    showModelSummary(measured);
    for (const model of measured) {
      const option = document.createElement("option"); option.value = model.model_id; option.textContent = model.model_id;
      $("model-choice").append(option);
    }
    $("model-choice-label").hidden = measured.length < 2;
    showComparisons(phrase_comparisons);
    if (report.recovery) updateRecovery(report.recovery);
    $("details").textContent = JSON.stringify(aggregate, null, 2); $("results").hidden = false;
    status("Prueba terminada. Podés borrar tus grabaciones con Borrar todo.");
  } catch { if (ownGeneration === generation) status("No se pudo completar la prueba local. Conservamos tus grabaciones para reintentar."); }
  finally { if (ownGeneration === generation) { abort = null; busy = false; render(); } }
};
function showModelSummary(models) {
  const table = document.createElement("table"), head = document.createElement("tr");
  for (const text of ["Modelo", "Números correctos", "WER", "p50", "p95", "Pico de memoria", "Fallos técnicos"]) {
    const cell = document.createElement("th"); cell.scope = "col"; cell.textContent = text; head.append(cell);
  }
  table.append(head);
  for (const model of models) {
    const row = document.createElement("tr");
    const values = [model.model_id, `${model.scores.all_critical_numbers_correct}/${model.scores.speech_clips}`,
      model.scores.wer === null ? "No disponible" : `${(model.scores.wer * 100).toFixed(1)} %`,
      `${(model.latency_ms.p50 / 1000).toFixed(2)} s`, `${(model.latency_ms.p95 / 1000).toFixed(2)} s`,
      `${(model.resources.container_lifetime_peak_bytes / 1048576).toFixed(1)} MiB`, String(model.technical_failures_all_passes)];
    for (const text of values) { const cell = document.createElement("td"); cell.textContent = text; row.append(cell); }
    table.append(row);
  }
  $("model-summary").replaceChildren(table);
}
$("model-choice").onchange = () => showComparisons(comparisonSets[$("model-choice").value] ?? []);
document.addEventListener("visibilitychange", () => { if (document.hidden) clearAll(); });
document.addEventListener("visibilitychange", () => { if (!document.hidden) refreshSettings(); });
window.addEventListener("pagehide", clearAll);
fetch("corpus").then(r => { if (!r.ok) throw new Error(); return r.json(); }).then(data => { samples = data; $("progress").max = data.length; render(); })
  .catch(() => status("No se pudieron cargar las frases. Recargá esta página."));
refreshSettings();
