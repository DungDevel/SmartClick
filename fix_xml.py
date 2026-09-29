with open("app/src/main/res/values/strings.xml", "r", encoding="utf-8") as f:
    en_content = f.read()

en_content = en_content.replace('Effortlessly</string>', 'Effortlessly\n</string>')
en_content = en_content.replace('Control Your TV\nEffortlessly', 'Control Your TV\nEffortlessly')

with open("app/src/main/res/values-vi/strings.xml", "r", encoding="utf-8") as f:
    vi_content = f.read()
    
vi_content = vi_content.replace('mới cách dễ dàng</string>', 'mới cách dễ dàng\n</string>')
vi_content = vi_content.replace('Điều khiển TV của bạn\nmột cách dễ dàng', 'Điều khiển TV của bạn\nmột cách dễ dàng')

if '<string name="remove">Gỡ bỏ</string>' not in vi_content:
    vi_content = vi_content.replace('</resources>', '    <string name="remove">Gỡ bỏ</string>\n</resources>')

with open("app/src/main/res/values/strings.xml", "w", encoding="utf-8") as f:
    f.write(en_content)
with open("app/src/main/res/values-vi/strings.xml", "w", encoding="utf-8") as f:
    f.write(vi_content)
