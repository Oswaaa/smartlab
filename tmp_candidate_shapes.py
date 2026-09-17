import zipfile,re
from xml.etree import ElementTree as ET
p=r'D:\SmartLab2.0\doc\_tmp\translation_v5\candidate.pptx'
with zipfile.ZipFile(p) as z:
 ns={'a':'http://schemas.openxmlformats.org/drawingml/2006/main','p':'http://schemas.openxmlformats.org/presentationml/2006/main'}
 for sn in [3,4,5,8]:
  print('---',sn)
  root=ET.fromstring(z.read(f'ppt/slides/slide{sn}.xml'))
  for sp in root.findall('.//p:sp',ns):
   ts=[t.text for t in sp.findall('.//a:t',ns) if t.text]
   if ts: print(repr(''.join(ts)))

