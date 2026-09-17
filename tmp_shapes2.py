import zipfile,re
from xml.etree import ElementTree as ET
p=r'D:\SmartLab2.0\doc\output\EUROSIM2026_English_v4\EUROSIM2026_Tao_Yu_English_Deck_v4.pptx'
with zipfile.ZipFile(p) as z:
 ns={'a':'http://schemas.openxmlformats.org/drawingml/2006/main','p':'http://schemas.openxmlformats.org/presentationml/2006/main'}
 for sn in [4,5,8]:
  print('---',sn)
  root=ET.fromstring(z.read(f'ppt/slides/slide{sn}.xml'))
  for sp in root.findall('.//p:sp',ns):
   ts=[t.text for t in sp.findall('.//a:t',ns) if t.text]
   if ts: print(repr(''.join(ts)))
