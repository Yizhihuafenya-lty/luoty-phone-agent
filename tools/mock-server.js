/**
 * 本地 mock：一个最小的 OpenAI 兼容 /chat/completions 流式服务。
 * 用来在模拟器里端到端验证「流式输出 + tool_calls + 多轮工具循环」，不需要真实 API Key。
 *
 * 启动：node tools/mock-server.js
 * App 里 Base URL 填 http://10.0.2.2:18787（模拟器访问宿主机），API Key 随意。
 */
const http = require('http')

const PORT = Number(process.env.PORT || 18787)
const MODEL = 'mock-model'

function sse(res, payload) {
  res.write(`data: ${JSON.stringify(payload)}\n\n`)
}

function base(choices, usage) {
  return {
    id: 'chatcmpl-mock-' + Date.now(),
    object: 'chat.completion.chunk',
    created: Math.floor(Date.now() / 1000),
    model: MODEL,
    choices,
    ...(usage ? { usage } : {}),
  }
}

function textChunk(text, finishReason = null) {
  return base([{ index: 0, delta: { content: text }, finish_reason: finishReason }])
}

function reasonChunk(text) {
  return base([{ index: 0, delta: { reasoning_content: text }, finish_reason: null }])
}

function toolChunk(pieces) {
  return base([
    {
      index: 0,
      delta: {
        tool_calls: pieces.map((p) => ({
          index: p.index,
          id: p.id,
          type: 'function',
          function: { name: p.name || '', arguments: p.args || '' },
        })),
      },
      finish_reason: null,
    },
  ])
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function sendText(res, text, delay = 18) {
  for (const ch of [...text]) {
    sse(res, textChunk(ch))
    await sleep(delay)
  }
}

async function sendToolCall(res, index, id, name, args, split = 2) {
  const step = Math.max(1, Math.ceil(args.length / split))
  sse(res, toolChunk([{ index, id, name, args: '' }]))
  await sleep(60)
  for (let i = 0; i < args.length; i += step) {
    sse(res, toolChunk([{ index, id: '', name: '', args: args.slice(i, i + step) }]))
    await sleep(80)
  }
  await sleep(120)
}

async function handleChat(req, res, body) {
  const messages = body.messages || []
  const toolMessages = messages.filter((m) => m.role === 'tool').length
  const userText = [...messages].reverse().find((m) => m.role === 'user')?.content || ''

  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache, no-transform',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  })
  res.write(': mock stream start\n\n')

  console.log(
    `[mock] request: messages=${messages.length} tools=${toolMessages} user="${String(userText).slice(0, 40)}"`,
  )

  try {
    const lower = String(userText).toLowerCase()

    // 演示分支：验证 run_command
    if (lower.includes('shell') || lower.includes('命令')) {
      if (toolMessages === 0) {
        await sendText(res, '好，我用 shell 看一下工作区。\n\n', 10)
        await sendToolCall(res, 0, 'call_shell_1', 'run_command', '{"command": "ls -la", "timeout_seconds": 15}')
        sse(res, base([{ index: 0, delta: {}, finish_reason: 'tool_calls' }], {
          prompt_tokens: 120,
          completion_tokens: 20,
        }))
      } else {
        await sendText(res, '命令执行完了，输出见上方工具卡片。\n\n可以用 `run_command` 做更多文件操作。', 8)
        sse(res, base([{ index: 0, delta: {}, finish_reason: 'stop' }], {
          prompt_tokens: 200,
          completion_tokens: 40,
        }))
      }
      return
    }

    // 演示分支：验证 edit_file（写入 → 精确替换 → 读回）
    if (lower.includes('edit') || lower.includes('修改')) {
      if (toolMessages === 0) {
        await sendText(res, '先写一个待修改的文件。\n\n', 8)
        await sendToolCall(
          res,
          0,
          'call_edit_write',
          'write_file',
          JSON.stringify({ path: 'demo.txt', content: 'name = "LuoTy"\nversion = 0.1\n' }),
          2,
        )
        sse(res, base([{ index: 0, delta: {}, finish_reason: 'tool_calls' }], {
          prompt_tokens: 120,
          completion_tokens: 30,
        }))
      } else if (toolMessages === 1) {
        await sendText(res, '用 edit_file 把版本号改掉。\n\n', 8)
        await sendToolCall(
          res,
          0,
          'call_edit_1',
          'edit_file',
          JSON.stringify({ path: 'demo.txt', old_string: 'version = 0.1', new_string: 'version = 1.0' }),
          3,
        )
        sse(res, base([{ index: 0, delta: {}, finish_reason: 'tool_calls' }], {
          prompt_tokens: 220,
          completion_tokens: 40,
        }))
      } else {
        await sendText(res, '改完了：`demo.txt` 里 `version = 0.1` 已替换成 `version = 1.0`。', 8)
        sse(res, base([{ index: 0, delta: {}, finish_reason: 'stop' }], {
          prompt_tokens: 300,
          completion_tokens: 50,
        }))
      }
      return
    }

    if (toolMessages === 0) {
      await sendText(res, '我先看一下工作区里有什么文件。\n\n', 10)
      sse(res, reasonChunk('用户想让我操作工作区，'))
      await sleep(200)
      sse(res, reasonChunk('先列目录再决定写什么。'))
      await sleep(150)
      await sendToolCall(res, 0, 'call_list_1', 'list_files', '{"path": ".", "depth": 2}')
      sse(
        res,
        base([{ index: 0, delta: {}, finish_reason: 'tool_calls' }], {
          prompt_tokens: 128,
          completion_tokens: 24,
        }),
      )
    } else if (toolMessages === 1) {
      await sendText(res, '工作区是空的。我创建一个 `hello.txt` 作为示例：\n\n', 10)
      const content = 'Hello from LuoTy Agent!\\n这是一次由手机端 Agent 真实写入的文件。\\n'
      await sendToolCall(
        res,
        0,
        'call_write_1',
        'write_file',
        JSON.stringify({ path: 'hello.txt', content: 'Hello from LuoTy Agent!\n这是一次由手机端 Agent 真实写入的文件。\n' }),
        3,
      )
      void content
      sse(
        res,
        base([{ index: 0, delta: {}, finish_reason: 'tool_calls' }], {
          prompt_tokens: 256,
          completion_tokens: 48,
        }),
      )
    } else if (toolMessages === 2) {
      await sendText(res, '写入成功，再读回来确认一下内容。\n\n', 10)
      await sendToolCall(res, 0, 'call_read_1', 'read_file', '{"path": "hello.txt"}', 2)
      sse(
        res,
        base([{ index: 0, delta: {}, finish_reason: 'tool_calls' }], {
          prompt_tokens: 320,
          completion_tokens: 40,
        }),
      )
    } else {
      const finalText =
        '已完成。工作区现在长这样：\n\n' +
        '- `hello.txt`：由 Agent 通过 `write_file` 创建，并已 `read_file` 回读校验\n' +
        '- 全流程走的是真实的多轮工具调用循环（列目录 → 写文件 → 读回）\n\n' +
        '示例代码（Kotlin）：\n\n' +
        '```kotlin\n' +
        'fun main() {\n' +
        '    val greeting = "Hello from LuoTy Agent!"\n' +
        '    println(greeting)\n' +
        '}\n' +
        '```\n\n' +
        '> 这条回答由本地 mock 服务流式生成，用于验证界面渲染。\n\n' +
        '下一步你可以让我继续改文件，或者去「工作区」里看看实际内容。'
      await sendText(res, finalText, 6)
      sse(
        res,
        base([{ index: 0, delta: {}, finish_reason: 'stop' }], {
          prompt_tokens: 512,
          completion_tokens: 220,
        }),
      )
    }
  } catch (e) {
    console.error('[mock] stream error', e)
  } finally {
    res.write('data: [DONE]\n\n')
    res.end()
  }
}

const server = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url.includes('/models')) {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ object: 'list', data: [{ id: MODEL, object: 'model' }] }))
    return
  }
  if (req.method === 'POST' && req.url.includes('/chat/completions')) {
    let raw = ''
    req.on('data', (c) => (raw += c))
    req.on('end', () => {
      let body = {}
      try {
        body = JSON.parse(raw || '{}')
      } catch {
        /* ignore */
      }
      handleChat(req, res, body).catch((e) => {
        console.error(e)
        res.end()
      })
    })
    return
  }
  res.writeHead(404, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify({ error: { message: 'not found' } }))
})

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[mock] listening on http://0.0.0.0:${PORT}`)
  console.log(`[mock] Android emulator base URL: http://10.0.2.2:${PORT}`)
})
