import assert from 'node:assert/strict'
import { createStreamChunkParser } from './sseParser.js'

const received = []
const parse = createStreamChunkParser(chunk => received.push(chunk))

parse.feed('data: 你好\n\n')
parse.feed('data:世')
parse.feed('界\n\nplain')
parse.feed(' text')
parse.flush()

assert.deepEqual(received, ['你好', '世界', 'plain text'])
