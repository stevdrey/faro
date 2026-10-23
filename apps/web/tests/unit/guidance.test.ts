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
  assert.equal(clipSource('next', '/faro/'), '/faro/audio/next.ogg')
})

test('unknown operands never select a recording naming a different exercise', () => {
  assert.equal(exerciseClip({ ...demoExercise, operands: { knownAddend: 10, total: 18 } }), undefined)
  assert.equal(exerciseClip({ ...subtractionExercise, operands: { minuend: 54, subtrahend: 28 } }), undefined)
  assert.equal(exerciseClip({ ...ascendingExercise, operands: { terms: [10, 12, 14] } }), undefined)
  assert.equal(exerciseClip({ ...ascendingExercise, operands: { terms: [10, 13] } }), undefined)
})

test('all shipped guidance has matching transcripts, checksums and bounded nonempty mono Ogg Opus audio', () => {
  const directory = new URL('../../public/audio/', import.meta.url)
  const manifest = JSON.parse(readFileSync(new URL('manifest.json', directory), 'utf8')) as {
    assets: { id: keyof typeof clips; file: string; text: string; sha256: string }[]
  }
  assert.deepEqual(manifest.assets.map(asset => asset.id).sort(), Object.keys(clips).sort())
  const files = readdirSync(directory).filter(name => /\.(ogg|wav)$/.test(name)).sort()
  assert.deepEqual(files, manifest.assets.map(asset => asset.file).sort())
  for (const asset of manifest.assets) {
    assert.equal(asset.text, clips[asset.id])
    assert.equal(asset.file, `${asset.id}.ogg`)
    const bytes = readFileSync(new URL(asset.file, directory))
    assert.equal(createHash('sha256').update(bytes).digest('hex'), asset.sha256)
    assert.ok(bytes.length <= 64 * 1024, `${asset.id} exceeds 64 KiB`)
    let preSkip = -1
    let granule = 0n
    let endOfStream = false
    for (let offset = 0; offset < bytes.length;) {
      assert.equal(bytes.toString('ascii', offset, offset + 4), 'OggS')
      assert.ok(!endOfStream, 'no pages after end of stream')
      const segments = bytes[offset + 26]
      const header = 27 + segments
      let length = 0
      for (let index = 0; index < segments; index++) length += bytes[offset + 27 + index]
      assert.ok(offset + header + length <= bytes.length)
      if (preSkip < 0) {
        assert.equal(bytes.toString('ascii', offset + header, offset + header + 8), 'OpusHead')
        assert.equal(bytes[offset + header + 9], 1, 'mono')
        preSkip = bytes.readUInt16LE(offset + header + 10)
      }
      granule = bytes.readBigInt64LE(offset + 6)
      endOfStream = (bytes[offset + 5] & 4) !== 0
      offset += header + length
    }
    assert.ok(endOfStream, `${asset.id} has an end-of-stream page`)
    const seconds = Number(granule - BigInt(preSkip)) / 48000
    assert.ok(seconds > 0 && seconds <= 15, `${asset.id} lasts ${seconds} seconds`)
  }
})
