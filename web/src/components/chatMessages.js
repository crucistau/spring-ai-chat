export function appendMessageContent(messages, messageId, chunk) {
  return messages.map(message => {
    if (message.id !== messageId) return message
    return {
      ...message,
      content: message.content + chunk
    }
  })
}
