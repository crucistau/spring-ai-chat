export function createStreamChunkParser(onChunk) {
    let buffer = ''
    let dataLines = []

    function emitDataLines() {
        if (dataLines.length === 0) return
        onChunk(dataLines.join('\n'))
        dataLines = []
    }

    function processLine(line) {
        const normalized = line.endsWith('\r') ? line.slice(0, -1) : line

        if (normalized === '') {
            emitDataLines()
            return
        }

        if (normalized.startsWith(':')) return

        if (normalized.startsWith('data:')) {
            const value = normalized.slice(5)
            dataLines.push(value.startsWith(' ') ? value.slice(1) : value)
            return
        }

        onChunk(normalized)
    }

    return {
        feed(text) {
            buffer += text
            const lines = buffer.split('\n')
            buffer = lines.pop() ?? ''
            lines.forEach(processLine)
        },
        flush() {
            if (buffer) {
                processLine(buffer)
                buffer = ''
            }
            emitDataLines()
        }
    }
}
