import pathlib
import re

path = pathlib.Path(r"C:\项目\luoty-agent\app\src\main\java\com\luoty\agent\ui\SessionDrawer.kt")
lines = path.read_text(encoding="utf-8").splitlines()
STR = re.compile(r'"(?:\\.|[^"\\])*"')


def delta(line):
    clean = STR.sub('""', line)
    return clean.count("{") - clean.count("}")


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
    return len(lines) - 1


# 删除顶部的 projects.forEach 列表（保留 LazyColumn 里的树）
start = next(i for i, l in enumerate(lines) if l.strip() == "projects.forEach { project ->")
end = match_block(start)
del lines[start:end + 1]

# 删除“会话记录”标签
for i, l in enumerate(lines):
    if l.strip() == '"会话记录",':
        begin = i - 2  # Text(
        while begin >= 0 and lines[begin].strip() != "Text(":
            begin -= 1
        close = i + 4
        while close < len(lines) and lines[close].strip() != ")":
            close += 1
        del lines[begin:close + 1]
        break

path.write_text("\n".join(lines) + "\n", encoding="utf-8")
print("ok", len(lines))
