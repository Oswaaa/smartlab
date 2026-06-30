import re

with open('D:/SmartLab2.0/Frontend/src/views/device/DeviceModelManagement.vue', 'r', encoding='utf-8') as f:
    content = f.read()

template_match = re.search(r'<template>(.*?)</template>', content, re.DOTALL)
script_match = re.search(r'<script setup[^>]*>(.*?)</script>', content, re.DOTALL)

if not template_match or not script_match:
    print('Could not parse template or script')
    exit(1)

template = template_match.group(1)
script = script_match.group(1)

# Extract event handlers
event_handlers = re.findall(r'@\w+="([^"]+)"', template)
functions_in_template = set()
for handler in event_handlers:
    func_name = handler.split('(')[0].strip()
    if '=' not in func_name and func_name:
        functions_in_template.add(func_name)

# Extract method calls
calls = re.findall(r'\b([a-zA-Z_]\w*)\s*\(', template)
for call in calls:
    functions_in_template.add(call)

# Extract variables from directives like v-model, :data, etc.
# Directives start with v- or :
# Example: v-model="someVar" or :disabled="someVar"
directives = re.findall(r'(?:v-\w+|:[a-zA-Z0-9_\-]+)="([^"]+)"', template)
for dr in directives:
    # Just a simple extraction of word-like things
    words = re.findall(r'\b([a-zA-Z_]\w*)\b', dr)
    for w in words:
        functions_in_template.add(w)

missing = []
builtins = ['String', 'Number', 'Boolean', 'Object', 'Array', 'console', 'JSON', 'window', 'document', 'Date', 'Math', 'row', 'mapping', 'param', 'attr', 'capability', 'cmd', 'index', '$index', 'state', 'name', 'val', 'item', 'event', 'index_0', 'true', 'false']

for func in sorted(functions_in_template):
    if func in builtins: continue
    
    # Check if func is defined in script
    # Matches:
    # function func(
    # const func =
    # let func =
    # var func =
    # func: 
    # const { func } =
    # import { func }
    def_regex = r'(?:function\s+' + func + r'\b|const\s+' + func + r'\s*=|let\s+' + func + r'\s*=|var\s+' + func + r'\s*=|const\s+\{[^\}]*\b' + func + r'\b[^\}]*\}|import\s+\{[^\}]*\b' + func + r'\b[^\}]*\})'
    
    if not re.search(def_regex, script):
        missing.append(func)

print('Potentially Missing:')
for m in missing:
    print(m)
