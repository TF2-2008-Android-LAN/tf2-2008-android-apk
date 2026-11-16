from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
import struct,hashlib,json,re
root=Path(__file__).resolve().parent
base=root.parents[1]/"android-app/assets/overlay.zip"
assert base.exists()
src=root/"source_icons"
cfgs={}
def add(path,b):
 if path in cfgs:raise ValueError("Duplicate "+path)
 cfgs[path]=b
def sha(b):return hashlib.sha256(b).hexdigest()
def mip(w,h,raw):
 nw=max(1,w//2);nh=max(1,h//2)
 dst=bytearray(nw*nh*4)
 for y in range(nh):
  for x in range(nw):
   srcs=[]
   for dy in (0,1):
    for dx in (0,1):
     p=((min(h-1,2*y+dy))*w+min(w-1,2*x+dx))*4
     srcs.append(raw[p:p+4])
   j=(y*nw+x)*4
   al=sum(p[3] for p in srcs)
   for k in range(3):
    dst[j+k]= (sum(p[k]*p[3] for p in srcs)+al//2)//al if al else 255
   dst[j+3]=(al+2)//4
 return nw,nh,bytes(dst)
def vtf256x256(raw):
 assert len(raw)==256*256*4
 levels=[];w,h=256,256
 while True:
  levels.append(raw)
  if w==h==1:break
  w,h,raw=mip(w,h,raw)
 h=bytearray(80);h[:4]=b"VTF\x00"
 struct.pack_into("<II",h,4,7,2)
 struct.pack_into("<I",h,12,80)
 struct.pack_into("<HH",h,16,256,256)
 struct.pack_into("<I",h,20,0x2100)
 struct.pack_into("<H",h,24,1)
 struct.pack_into("<3f",h,32,1,1,1)
 struct.pack_into("<f",h,48,1)
 struct.pack_into("<I",h,52,12)
 h[56]=len(levels)
 struct.pack_into("<I",h,57,13)
 h[61:63]=bytes((16,8))
 struct.pack_into("<H",h,63,1)
 thumb=struct.pack("<HHI",0xe73c,0xe73c,0)*8
 data=bytes(h)+thumb+b''.join(reversed(levels))
 assert data[-256*256*4:]==levels[0]
 return data
names=["voice_tab_z","voice_tab_x","voice_tab_c","voice_close"]+[f"voice_{c}_{i}" for c in "zxc" for i in range(8)]
for name in names:
 raw=(src/(name+".bgra")).read_bytes()
 tex=vtf256x256(raw)
 vmt=('"UnlitGeneric"\n{\n "$basetexture" "vgui/touch/'+name+
      '"\n "$translucent" "1"\n "$ignorez" "1"\n "$vertexcolor" "1"\n "$vertexalpha" "1"\n}\n').encode("ascii")
 add(f"tf_port/materials/vgui/touch/{name}.vtf",tex)
 add(f"tf_port/materials/vgui/touch/{name}.vmt",vmt)
print("CUSTOM_UI_GRAPHICS",len(names),"ASSETS",len(cfgs))
with ZipFile(base) as z:
 touch=z.read("common/tf_port/cfg/touch.cfg").decode("utf-8")
 classreset=z.read("common/tf_port/cfg/tf2008_touch_reset_class.cfg").decode("utf-8")
assert touch.count('touch_addbutton "duck"')==1
assert touch.count('touch_addbutton "more_open"')==1
assert touch.count('touch_addbutton "more_close"')==1
assert 'tf2008_duck_toggle' not in touch
lines=touch.splitlines()
out=[]
count_more=0
for line in lines:
 if line.startswith('touch_addbutton "duck" '):
  assert '"vgui/touch/crouch" "+duck"' in line
  line=line.replace('"vgui/touch/crouch" "+duck"','"vgui/touch/crouch" "tf2008_duck_toggle"')
 if line.startswith('touch_addbutton "more_open" '):
  assert 'touch_hide more_open' in line
  line=line.replace('touch_hide more_open','touch_show aux_voice; touch_hide more_open')
  count_more+=1
 if line.startswith('touch_addbutton "more_close" '):
  assert 'touch_hide more_close' in line
  line=line.replace('touch_hide more_close','touch_hide aux_voice; touch_hide more_close')
  count_more+=1
 out.append(line)
 if line.startswith('touch_addbutton "aux_spray" '):
  out.append('touch_addbutton "aux_voice" "vgui/touch/voicemenu" "exec tf2008_voice_panel_open" 0.493000 0.150000 0.562000 0.245000 255 255 255 180 1 0.620000')
assert count_more==2
addlines=[]
# Voice panel tabs are only shown after pressing VOICES inside MORE.
tab_pos={"z":(0.071,0.160),"x":(0.170,0.259),"c":(0.269,0.358)}
for char,(x1,x2) in tab_pos.items():
 addlines.append(f'touch_addbutton "voice_tab_{char}" "vgui/touch/voice_tab_{char}" "exec tf2008_voice_menu_{char}" {x1:.6f} 0.268000 {x2:.6f} 0.345000 255 255 255 232 1 0.400000')
addlines.append('touch_addbutton "voice_close" "vgui/touch/voice_close" "exec tf2008_voice_panel_close" 0.373000 0.268000 0.487000 0.345000 255 255 255 235 1 0.340000')
for char,menu in (("z",0),("x",1),("c",2)):
 for idx in range(8):
  col=idx%2;row=idx//2
  x1=0.068+col*0.196;x2=x1+0.184;y1=0.370+row*0.089;y2=y1+0.080
  action=f"voicemenu {menu} {idx}; exec tf2008_voice_panel_close"
  addlines.append(f'touch_addbutton "voice_{char}_{idx}" "vgui/touch/voice_{char}_{idx}" "{action}" {x1:.6f} {y1:.6f} {x2:.6f} {y2:.6f} 255 255 255 245 1 0.205000')
assert len(addlines)==28
out.extend(['','// Android voice menus: Z/X/C, 8 stock commands each. Hidden except on request.'])
out.extend(addlines)
out.extend(['',
  '// Toggle crouch on the same large button. Button is never auto-released by touch code.',
  'alias tf2008_duck_on "+duck; touch_setcolor duck 255 223 135 220; alias tf2008_duck_toggle tf2008_duck_off"',
  'alias tf2008_duck_off "-duck; touch_setcolor duck 255 255 255 155; alias tf2008_duck_toggle tf2008_duck_on"',
  'alias tf2008_duck_toggle "tf2008_duck_on"',
  ''])
add("tf_port/cfg/touch.cfg",("\n".join(out)).encode("utf-8"))
# 8 exact voice commands in each menu, no ninth slot in the 2008 script.
for char,menu in (("z",0),("x",1),("c",2)):
 group=[f"voice_{char}_{i}" for i in range(8)]
 assert all(any(f'touch_addbutton "{name}"' in s for s in addlines) for name in group)
add("tf_port/cfg/tf2008_voice_hide_items.cfg",("\n".join(
 ['// Hide 24 distinct 2008 voice actions.']+[f"touch_hide voice_{c}_{i}" for c in "zxc" for i in range(8)])+"\n").encode("utf-8"))
for char in "zxc":
 cat=["// Display one of the 3 original voice menus, hide the other 16 choices.",
      "exec tf2008_voice_hide_items"]
 cat.extend([f"touch_setcolor voice_tab_{c} "+("255 218 140 250" if c==char else "255 255 255 215") for c in "zxc"])
 cat.extend(f"touch_show voice_{char}_{i}" for i in range(8))
 add(f"tf_port/cfg/tf2008_voice_menu_{char}.cfg",("\n".join(cat)+"\n").encode("utf-8"))
extras=["aux_medic","aux_chat","aux_teamchat","aux_taunt","aux_team","aux_spray","aux_voice"]
openlines=["// Voice submenu only while explicitly requested.",
           "exec tf2008_voice_hide_items"]
openlines.extend(f"touch_hide {n}" for n in extras)
openlines.extend(["touch_hide more_open","touch_hide more_close"])
openlines.extend(f"touch_show voice_tab_{c}" for c in "zxc")
openlines+=["touch_show voice_close","exec tf2008_voice_menu_z"]
add("tf_port/cfg/tf2008_voice_panel_open.cfg",("\n".join(openlines)+"\n").encode("utf-8"))
closelines=["// Close voice panel completely after one command or cancel.",
            "exec tf2008_voice_hide_items"]
closelines.extend(f"touch_hide voice_tab_{c}" for c in "zxc")
closelines.append("touch_hide voice_close")
closelines.extend(f"touch_hide {n}" for n in extras)
closelines.extend(["touch_hide more_close","touch_show more_open"])
add("tf_port/cfg/tf2008_voice_panel_close.cfg",("\n".join(closelines)+"\n").encode("utf-8"))
add("tf_port/cfg/tf2008_duck_reset.cfg",
    b'// Reset the crouch toggle whenever the player changes class.\n-duck\ntouch_setcolor duck 255 255 255 155\nalias tf2008_duck_toggle tf2008_duck_on\n')
assert "tf2008_duck_reset" not in classreset
assert "tf2008_voice_panel_close" not in classreset
classreset="exec tf2008_duck_reset\nexec tf2008_voice_panel_close\n"+classreset
add("tf_port/cfg/tf2008_touch_reset_class.cfg",classreset.encode("utf-8"))
archive=root/"TF2_2008_TouchVoiceToggle_v1_QA.zip"
with ZipFile(archive,"w",ZIP_DEFLATED,compresslevel=8) as z:
 for key in sorted(cfgs):z.writestr(key,cfgs[key])
items=[{"path":p,"bytes":len(b),"sha256":sha(b)} for p,b in sorted(cfgs.items())]
manifest={"type":"TF2_2008_TOUCH_VOICE_OVERLAY","version":1,"count":len(items),
          "assets":items,
          "allow_replace_original":{
            "tf_port/cfg/touch.cfg": sha(touch.encode("utf-8")),
            "tf_port/cfg/tf2008_touch_reset_class.cfg":
              sha(classreset.replace("exec tf2008_duck_reset\nexec tf2008_voice_panel_close\n","",1).encode("utf-8"))
          }}
manifest_path=root/"TF2_2008_TouchVoiceToggle_v1_manifest.json"
manifest_path.write_text(json.dumps(manifest,ensure_ascii=True,separators=(",",":")),encoding="utf-8")
with ZipFile(archive) as z:
 assert len(z.namelist())==len(items)
 for item in items:
  assert sha(z.read(item["path"]))==item["sha256"]
print("VOICE_AND_TOGGLE_OVERLAY_READY",len(items),"ZIP_BYTES",archive.stat().st_size,
      "SHA256",sha(archive.read_bytes()))
print("ORIGINAL_TOUCH_CFG_SHA256",manifest["allow_replace_original"]["tf_port/cfg/touch.cfg"])
print("ORIGINAL_CLASS_RESET_SHA256",manifest["allow_replace_original"]["tf_port/cfg/tf2008_touch_reset_class.cfg"])
print("BUTTONS_ADDED",len(addlines),"EXTRA_CONFIGS",len([p for p in cfgs if p.startswith("tf_port/cfg/")]))
(root/"overlay_info.json").write_text(json.dumps(
 {"count":len(items),"zip_size":archive.stat().st_size,"zip_sha256":sha(archive.read_bytes()),
  "manifest":str(manifest_path)},indent=2),encoding="utf-8")
