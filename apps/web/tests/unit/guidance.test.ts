// SPDX-License-Identifier: AGPL-3.0-or-later
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFileSync, readdirSync } from 'node:fs'
import test from 'node:test'
import { clips, clipSource, exerciseClip } from '../../src/features/guidance/clips.ts'
import { demoExercise } from '../../src/features/missing-addend/exercise.ts'
import { ascendingExercise, descendingExercise } from '../../src/features/sequences/exercise.ts'
import { subtractionExercise } from '../../src/features/subtraction/exercise.ts'

test('each current exercise selects its own fixed instruction, independent of equation layout', () => {
  assert.equal(exerciseClip(demoExercise), 'blocks')
  assert.equal(exerciseClip({ ...demoExercise, presentation: 'MISSING_PLUS_KNOWN_EQUALS_TOTAL' }), 'blocks')
  assert.equal(exerciseClip(ascendingExercise), 'ascending')
  assert.equal(exerciseClip(descendingExercise), 'descending')
  assert.equal(exerciseClip(subtractionExercise), 'subtraction')
  assert.equal(clipSource('next', '/faro/'), '/faro/audio/next.wav')
})

test('unknown operands never select a recording naming a different exercise', () => {
  assert.equal(exerciseClip({ ...demoExercise, operands: { knownAddend: 10, total: 18 } }), undefined)
  assert.equal(exerciseClip({ ...subtractionExercise, operands: { minuend: 54, subtrahend: 28 } }), undefined)
  assert.equal(exerciseClip({ ...ascendingExercise, operands: { terms: [10, 12, 14] } }), undefined)
  assert.equal(exerciseClip({ ...ascendingExercise, operands: { terms: [10, 13] } }), undefined)
})

test('all shipped guidance has matching transcripts, checksums and bounded nonempty PCM audio', () => {
  const directory = new URL('../../public/audio/', import.meta.url)
  const manifest = JSON.parse(readFileSync(new URL('manifest.json', directory), 'utf8')) as {
    assets: { id: keyof typeof clips; file: string; text: string; sha256: string }[]
  }
  assert.deepEqual(manifest.assets.map(asset => asset.id).sort(), Object.keys(clips).sort())
  assert.deepEqual(readdirSync(directory).filter(name => name.endsWith('.wav')).sort(), manifest.assets.map(asset => asset.file).sort())
  for (const asset of manifest.assets) {
    assert.equal(asset.text, clips[asset.id])
    assert.equal(asset.file, `${asset.id}.wav`)
    const bytes = readFileSync(new URL(asset.file, directory))
    assert.equal(createHash('sha256').update(bytes).digest('hex'), asset.sha256)
    assert.equal(bytes.toString('ascii', 0, 4), 'RIFF')
    assert.equal(bytes.toString('ascii', 8, 12), 'WAVE')
    let byteRate = 0
    let dataSize = 0
    for (let offset = 12; offset + 8 <= bytes.length;) {
      const chunk = bytes.toString('ascii', offset, offset + 4)
      const length = bytes.readUInt32LE(offset + 4)
      assert.ok(offset + 8 + length <= bytes.length)
      if (chunk === 'fmt ') {
        assert.equal(bytes.readUInt16LE(offset + 8), 1, 'PCM')
        assert.equal(bytes.readUInt16LE(offset + 10), 1, 'mono')
        assert.equal(bytes.readUInt16LE(offset + 22), 16, '16 bit')
        byteRate = bytes.readUInt32LE(offset + 16)
      }
      if (chunk === 'data') dataSize = length
      offset += 8 + length + length % 2
    }
    assert.ok(byteRate > 0 && dataSize > 0)
    assert.ok(dataSize / byteRate <= 15, `${asset.id} exceeds 15 seconds`)
  }
})
