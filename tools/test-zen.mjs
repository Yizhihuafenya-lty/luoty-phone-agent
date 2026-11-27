// 一次性连通性测试：验证 OpenCode Zen 的流式输出与 tool_calls 增量格式
const KEY = process.env.ZEN_KEY || ''
const BASE = process.env.ZEN_BASE || 'https://opencode.ai/zen/v1'
const MODEL = process.env.ZEN_MODEL || 'deepseek-v4.1-flash'

const body = {
  model: MODEL,
  stream: true,
  temperature: 0.2,
  stream_options: { include_usage: true },
  messages: [
    { role: 'system', content: '你是编码助手。需要读文件时调用工具。' },
    { role: 'user', content: '请调用 list_files 工具查看工作区，然后告诉我结果。' },
  ],
  tools: [
    {
      type: 'function',
      function: {
        name: 'list_files',
        description: '列出工作区文件',
        parameters: {
          type: 'object',
          properties: { path: { type: 'string', description: '目录' } },
          required: [],
        },
      },
    },
  ],
}

const res = await fetch(`${BASE}/chat/completions`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${KEY}`,
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    'x-opencode-session': process.env.ZEN_SESSION || crypto.randomUUID(),
  },
  body: JSON.stringify(body),
})

console.log('HTTP', res.status, res.headers.get('content-type'))
if (!res.ok) {
  console.log(await res.text())
  process.exit(1)
}

let text = ''
const toolCalls = new Map()
let finish = null

const decoder = new TextDecoder()
let buffer = ''
for await (const chunk of res.body) {
  buffer += decoder.decode(chunk, { stream: true })
  const lines = buffer.split('\n')
  buffer = lines.pop()
  for (const line of lines) {
    if (!line.startsWith('data:')) continue
    const data = line.slice(5).trim()
    if (!data || data === '[DONE]') continue
    let json
    try {
      json = JSON.parse(data)
    } catch {
      continue
    }
    const choice = json.choices?.[0]
    if (!choice) continue
    if (choice.delta?.content) text += choice.delta.content
    if (choice.delta?.tool_calls) {
      for (const tc of choice.delta.tool_calls) {
        const cur = toolCalls.get(tc.index) || { id: '', name: '', args: '' }
        if (tc.id) cur.id = tc.id
        if (tc.function?.name) cur.name += tc.function.name
        if (tc.function?.arguments) cur.args += tc.function.arguments
        toolCalls.set(tc.index, cur)
      }
    }
    if (choice.finish_reason) finish = choice.finish_reason
  }
}

console.log('finish_reason =', finish)
console.log('text =', JSON.stringify(text.slice(0, 300)))
console.log('tool_calls =', JSON.stringify([...toolCalls.values()], null, 2))
