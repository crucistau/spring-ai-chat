import assert from 'node:assert/strict'
import { appendMessageContent } from './chatMessages.js'

const original = [
  { id: 1, role: 'user', content: 'hello' },
  { id: 2, role: 'assistant', content: '' }
]

const updated = appendMessageContent(original, 2, 'stream')

assert.equal(updated[1].content, 'stream')
assert.notEqual(updated, original)
assert.notEqual(updated[1], original[1])
assert.equal(original[1].content, '')
