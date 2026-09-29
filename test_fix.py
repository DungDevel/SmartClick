path = 'app/src/main/res/values-vi/strings.xml'
with open(path, 'rb') as f:
    content = f.read()

text = content.decode('utf-8-sig') 
lines = text.split('\n')
for i, line in enumerate(lines):
    if "name=\"selected\"" in line:
        print(f"Line {i}: {repr(line)}")
