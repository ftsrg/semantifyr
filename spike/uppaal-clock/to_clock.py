import re
import sys

source, target, mode = sys.argv[1], sys.argv[2], sys.argv[3]
text = open(source).read()

timers = re.findall(r"int\[0,(\d+)\] (\w+)__remainingTime = 0;", text)
assert timers
print("timers:", len(timers))

for maximum, name in timers:
    remaining = name + "__remainingTime"
    clock, bound, pending = name + "__clock", name + "__bound", name + "__pending"
    text = text.replace(
        f"int[0,{maximum}] {remaining} = 0;",
        f"clock {clock};\nint[0,{maximum}] {bound} = 0;\nbool {pending} = false;",
    )
    text = re.sub(
        rf"{remaining} = (\d+)</label>",
        rf"{clock} = 0, {bound} = \1, {pending} = true</label>",
        text,
    )
    text = text.replace(
        f'<label kind="assignment">{remaining} = ({remaining} - 1)</label>',
        "",
    )
    text = text.replace(
        f'<label kind="guard">!(({remaining} &gt; 0))</label>',
        f'<label kind="guard">{clock} &gt;= {bound}</label>\n            <label kind="assignment">{pending} = false</label>',
    )
    text = text.replace(f"({remaining} &lt;= 0)", f"({clock} &gt;= {bound})")
    text = text.replace(f"({remaining} &gt; 0)", f"({clock} &lt; {bound})")

disjunction = re.compile(
    r'(        <transition>\n            <source ref="\w+"/>\n            <target ref="\w+"/>\n)'
    r'            <label kind="guard">\(\((\w+__clock &lt; \w+__bound)\) \|\| (\(.*?\))\)</label>\n'
    r"        </transition>\n"
)


def split(match):
    head, clock_part, data_part = match.group(1), match.group(2), match.group(3)
    first = f'{head}            <label kind="guard">{clock_part}</label>\n        </transition>\n'
    second = f'{head}            <label kind="guard">{data_part}</label>\n        </transition>\n'
    return first + second


text, count = disjunction.subn(split, text)
print("split disjunctions:", count)
assert "remainingTime" not in text
assert not re.search(r"__clock[^<]*\|\|", text)

invariant = " &amp;&amp; ".join(
    f"({name}__pending imply {name}__clock &lt;= {name}__bound)" for _, name in timers
)
if mode == "fixed":
    text = text.replace("int[0,", "clock stepClock;\nint[0,", 1)
    invariant += " &amp;&amp; stepClock &lt;= 1"
    text, count = re.subn(
        r'(<source ref="running"/>\n            <target ref="\w+"/>\n)            <label kind="assignment">',
        r'\1            <label kind="guard">stepClock == 1</label>\n            <label kind="assignment">stepClock = 0, ',
        text,
    )
    assert count == 1
text = text.replace(
    "<name>Running</name>\n",
    f'<name>Running</name>\n            <label kind="invariant">{invariant}</label>\n',
)
open(target, "w").write(text)
