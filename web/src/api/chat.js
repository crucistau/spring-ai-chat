import http from './http'
import { createStreamChunkParser } from './sseParser'

const API_BASE = '/api/chat'
const CONV_BASE = '/api/conversations'

export async function sendMessageStream(message, conversationId, onChunk) {
    const token = localStorage.getItem('token')
    const response = await fetch(`${API_BASE}/stream`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            ...(token ? { 'Authorization': `Bearer ${token}` } : {})
        },
        body: JSON.stringify({ message, conversationId })
    })

    if (!response.ok) {
        const errorText = await response.text().catch(() => '')
        throw new Error(errorText || `Request failed: ${response.status}`)
    }

    if (!response.body) {
        throw new Error('Streaming response is not supported by this browser')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    const contentType = response.headers.get('content-type') || ''
    let isEventStream = contentType.includes('text/event-stream')
    const parser = createStreamChunkParser(onChunk)

    while (true) {
        const { done, value } = await reader.read()
        if (done) break

        const text = decoder.decode(value, { stream: true })
        if (!isEventStream && text.includes('data:')) {
            isEventStream = true
        }
        if (isEventStream) {
            parser.feed(text)
        } else {
            onChunk(text)
        }
    }

    const tail = decoder.decode()
    if (tail) {
        if (!isEventStream && tail.includes('data:')) {
            isEventStream = true
        }
        if (isEventStream) {
            parser.feed(tail)
        } else {
            onChunk(tail)
        }
    }

    if (isEventStream) parser.flush()
}

export async function listConversations() {
    const response = await http.get(CONV_BASE)
    return response.data
}

export async function getMessages(conversationId) {
    const response = await http.get(`${CONV_BASE}/${conversationId}/messages`)
    return response.data
}

export async function deleteConversation(conversationId) {
    await http.delete(`${CONV_BASE}/${conversationId}`)
}

export async function renameConversation(conversationId, title) {
    const response = await http.put(`${CONV_BASE}/${conversationId}/title`, { title })
    return response.data
}
