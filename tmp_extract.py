import zipfile, re, os, sys
from xml.etree import ElementTree as ET
p=r'D:\SmartLab2.0\doc\output\EUROSIM2026_English_v4\EUROSIM2026_Tao_Yu_English_Deck_v4.pptx'
with zipfile.ZipFile(p) as z:
    names=[n for n in z.namelist() if n.startswith('ppt/slides/slide') and n.endswith('.xml')]
    names.sort(key=lambda x:int(re.search(r'slide(\d+)',x).group(1)))
    ns={'a':'http://schemas.openxmlformats.org/drawingml/2006/main'}
    out=[]
    for n in names:
        root=ET.fromstring(z.read(n))
        texts=[t.text for t in root.findall('.//a:t',ns) if t.text]
        out.append('\n---'+n+'---\n'+' | '.join(texts))
print('\n'.join(out))
