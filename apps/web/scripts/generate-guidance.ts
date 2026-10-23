// SPDX-License-Identifier: AGPL-3.0-or-later
// Run deliberately with Node 24; never invoked by install, build, or browser code.
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { copyFileSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { clips } from '../src/features/guidance/clips.ts'

/** Single source for both the tool invocations and the provenance recorded in the manifest. */
const settings = {
  generator: 'eSpeak NG 1.52.0', voice: 'es-419', wordsPerMinute: 155, pitch: 50,
  encoder: 'FFmpeg 8.1.3 libopus', container: 'Ogg Opus', bitrateKbps: 24, channels: 1,
}

function requireVersion(command: string, version: string) {
  const result = spawnSync(command, command === 'ffmpeg' ? ['-version'] : ['--version'], { encoding: 'utf8' })
  if (result.status !== 0 || !result.stdout.includes(version)) {
    throw new Error(`Guidance generation requires an explicitly installed ${command} ${version}.`)
  }
}
requireVersion('espeak-ng', '1.52.0')
requireVersion('ffmpeg', '8.1.3')

function run(command: string, args: string[], id: string) {
  const result = spawnSync(command, args, { encoding: 'utf8' })
  if (result.status !== 0) throw new Error(`Could not ${command === 'ffmpeg' ? 'encode' : 'synthesize'} ${id}: ${result.stderr}`)
}

const directory = fileURLToPath(new URL('../public/audio/', import.meta.url))
const staging = mkdtempSync(join(tmpdir(), 'faro-guidance-'))
try {
  // Every clip is produced in staging first, so a failure never leaves a half-replaced directory.
  const assets = Object.entries(clips).map(([id, text]) => {
    const wav = join(staging, `${id}.wav`)
    const file = `${id}.ogg`
    run('espeak-ng', ['-v', settings.voice, '-s', String(settings.wordsPerMinute), '-p', String(settings.pitch), '-w', wav, text], id)
    run('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-i', wav, '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact',
      '-c:a', 'libopus', '-b:a', `${settings.bitrateKbps}k`, '-ac', String(settings.channels), '-application', 'voip', join(staging, file)], id)
    const sha256 = createHash('sha256').update(readFileSync(join(staging, file))).digest('hex')
    return { id, file, text, sha256 }
  })
  mkdirSync(directory, { recursive: true })
  for (const name of readdirSync(directory)) {
    if (name.endsWith('.wav') || name.endsWith('.ogg')) rmSync(join(directory, name))
  }
  for (const { file } of assets) copyFileSync(join(staging, file), join(directory, file))
  writeFileSync(join(directory, 'manifest.json'), JSON.stringify({
    ...settings, contentLanguage: 'es-CR', license: 'CC0-1.0', assets,
  }, null, 2) + '\n')
  console.log(`Generated ${assets.length} local synthetic guidance clips.`)
} finally {
  rmSync(staging, { recursive: true, force: true })
}
