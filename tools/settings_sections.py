import pathlib
import re

path = pathlib.Path(r"C:\项目\luoty-agent\app\src\main\java\com\luoty\agent\ui\SettingsScreen.kt")
backup = path.with_suffix(".kt.single")
if not backup.exists():
    backup.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

lines = path.read_text(encoding="utf-8").splitlines()
STR = re.compile(r'"(?:\\.|[^"\\])*"')


def delta(line):
    return STR.sub('""', line).count("{") - STR.sub('""', line).count("}")


def match_block(start):
    depth = 0
    opened = False
    for index in range(start, len(lines)):
        step = delta(lines[index])
        if not opened:
            if step > 0:
                opened = True
                depth = step
            continue
        depth += step
        if depth <= 0:
            return index
    raise RuntimeError("unmatched")


func_start = next(i for i, l in enumerate(lines) if l.startswith("fun SettingsScreen("))
col = None
for i in range(func_start, len(lines)):
    if lines[i].strip() == "Column(" and any(
        "verticalScroll(rememberScrollState())" in lines[j] for j in range(i, min(i + 8, len(lines)))
    ):
        col = i
        break
if col is None:
    raise SystemExit("column not found")
col_end = match_block(col)

content = lines[col + 1:col_end]

# 找出所有顶层 SettingsCard 块
cards = []
i = 0
pre = []
while i < len(content):
    if content[i].strip().startswith('SettingsCard("'):
        title = content[i].strip().split('"')[1]
        depth = 0
        end = i
        while end < len(content):
            depth += delta(content[end])
            if depth == 0 and end > i:
                break
            end += 1
        cards.append((title, content[i:end + 1]))
        i = end + 1
    else:
        if not cards:
            pre.append(content[i])
        i += 1

if not cards:
    raise SystemExit("no cards")

used = {title for title, _ in cards}
# 最后一张卡之后的页脚（关于）
last_title, last_block = cards[-1]
tail_index = None
for idx in range(col + 1, col_end):
    if lines[idx].strip().startswith('SettingsCard("' + last_title + '"'):
        block_end = match_block(idx)
        tail_index = block_end + 1
        break
footer = lines[tail_index:col_end] if tail_index else []

section_of = {
    "模型提供方": "MODELS",
    "接口": "MODELS",
    "服务器": "SERVERS",
    "联网搜索": "SEARCH",
    "生成参数": "GENERATION",
    "行为": "GENERATION",
    "权限与上下文": "CONTEXT",
    "系统提示词": "PROMPT",
    "工作区": "WORKSPACE",
}

order = [
    ("MODELS", ["模型提供方", "接口"]),
    ("SERVERS", ["服务器"]),
    ("SEARCH", ["联网搜索"]),
    ("GENERATION", ["生成参数", "行为"]),
    ("CONTEXT", ["权限与上下文"]),
    ("PROMPT", ["系统提示词"]),
    ("WORKSPACE", ["工作区"]),
]

by_title = {title: block for title, block in cards}


def render(block):
    return ["    " + line if line.strip() else line for line in block]


new_content = [
    "            if (section == null) {",
    "                SettingsMenu(",
    "                    providerCount = providers.size,",
    "                    serverCount = servers.size,",
    "                    onSelect = { section = it },",
    "                )",
    "            }",
]
for name, titles in order:
    blocks = [by_title[t] for t in titles if t in by_title]
    if not blocks:
        continue
    new_content.append(f"            if (section == SettingsSection.{name}) {{")
    for block in blocks:
        new_content.extend(render(block))
    new_content.append("            }")
if footer:
    new_content.append("            if (section == SettingsSection.ABOUT) {")
    new_content.extend(render(footer))
    new_content.append("            }")

new_lines = lines[:col + 1] + new_content + lines[col_end:]
path.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
print("cards:", [t for t, _ in cards])
print("footer lines:", len(footer))
