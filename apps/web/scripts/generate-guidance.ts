// SPDX-License-Identifier: AGPL-3.0-or-later
// Run deliberately with Node 24; never invoked by install, build, or browser code.
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { clips } from '../src/features/guidance/clips.ts'

const version = spawnSync('espeak-ng', ['--version'], { encoding: 'utf8' })
if (version.status !== 0 || !version.stdout.includes('1.52.0')) {
  throw new Error('Guidance generation requires an explicitly installed eSpeak NG 1.52.0.')
}
const directory = new URL('../public/audio/', import.meta.url)
mkdirSync(directory, { recursive: true })
const assets = Object.entries(clips).map(([id, text]) => {
  const path = new URL(`${id}.wav`, directory)
  const result = spawnSync('espeak-ng', ['-v', 'es-419', '-s', '155', '-p', '50', '-w', fileURLToPath(path), text], { encoding: 'utf8' })
  if (result.status !== 0) throw new Error(`Could not synthesize ${id}`)
  const bytes = readFileSync(path)
  return { id, file: `${id}.wav`, text, sha256: createHash('sha256').update(bytes).digest('hex') }
})
writeFileSync(new URL('manifest.json', directory), JSON.stringify({
  generator: 'eSpeak NG 1.52.0', voice: 'es-419', wordsPerMinute: 155, pitch: 50,
  contentLanguage: 'es-CR', license: 'CC0-1.0', assets,
}, null, 2) + '\n')
console.log(`Generated ${assets.length} local synthetic guidance clips.`)
