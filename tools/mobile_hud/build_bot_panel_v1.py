from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib, json, struct, re
root=Path(__file__).resolve().parent
voice=root/"TF2_2008_TouchVoiceToggle_v1_QA.zip"
assert voice.exists()
with ZipFile(voice) as z:
    files={f:z.read(f) for f in z.namelist() if not f.endswith("/")}
    assert "tf_port/cfg/touch.cfg" in files
    assert "tf_port/cfg/tf2008_voice_panel_open.cfg" in files
assert len(files)==65
baseline_sha={key:hashlib.sha256(data).hexdigest() for key,data in files.items()}
source=root/"source_icons"
names=["bot_menu","bot_close","bot_title","bot_add","bot_minus",
       "bot_q0","bot_q4","bot_q8","bot_q12","bot_q16","bot_all"]
def sha(b):return hashlib.sha256(b).hexdigest()
def put(key,value):
    if key in files:raise RuntimeError("duplicate "+key)
    files[key]=value if isinstance(value,bytes) else value.encode("utf-8")
def mip(w,h,p):
    nw,nh=max(1,w//2),max(1,h//2)
    r=bytearray(nw*nh*4)
    for y in range(nh):
        for x in range(nw):
            pix=[p[4*(min(h-1,2*y+dy)*w+min(w-1,2*x+dx)):
                   4*(min(h-1,2*y+dy)*w+min(w-1,2*x+dx))+4]
                 for dy in (0,1) for dx in (0,1)]
            a=sum(t[3] for t in pix)
            idx=4*(y*nw+x)
            for ch in (0,1,2):
                r[idx+ch]=(sum(t[ch]*t[3] for t in pix)+a//2)//a if a else 255
            r[idx+3]=(a+2)//4
    return nw,nh,bytes(r)
def makevtf(raw):
    assert len(raw)==256*256*4
    mips=[];w=h=256
    while True:
        mips.append(raw)
        if w==1 and h==1:break
        w,h,raw=mip(w,h,raw)
    hdr=bytearray(80);hdr[:4]=b"VTF\x00"
    struct.pack_into("<II",hdr,4,7,2)
    struct.pack_into("<I",hdr,12,80)
    struct.pack_into("<HH",hdr,16,256,256)
    struct.pack_into("<I",hdr,20,0x2100)
    struct.pack_into("<H",hdr,24,1)
    struct.pack_into("<3f",hdr,32,1.,1.,1.)
    struct.pack_into("<f",hdr,48,1.)
    struct.pack_into("<I",hdr,52,12)
    hdr[56]=len(mips)
    struct.pack_into("<I",hdr,57,13)
    hdr[61:63]=bytes((16,8))
    struct.pack_into("<H",hdr,63,1)
    low=struct.pack("<HHI",0xe73c,0xe73c,0)*8
    b=bytes(hdr)+low+b"".join(reversed(mips))
    assert b[-256*256*4:]==mips[0]
    return b
for name in names:
    raw=(source/(name+".bgra")).read_bytes()
    tex=makevtf(raw)
    put("tf_port/materials/vgui/touch/"+name+".vtf",tex)
    put("tf_port/materials/vgui/touch/"+name+".vmt",
        '"UnlitGeneric"\n{\n "$basetexture" "vgui/touch/'+name+
        '"\n "$translucent" "1"\n "$ignorez" "1"\n "$vertexcolor" "1"\n "$vertexalpha" "1"\n}\n')
print("BOT_UI_GRAPHICS",len(names),"VMT+VTF",len(names)*2)
def edit(key,old,new):
    s=files[key].decode("utf-8")
    assert s.count(old)==1,(key,old,s.count(old))
    files[key]=s.replace(old,new).encode("utf-8")
touch="tf_port/cfg/touch.cfg"
edit(touch,"touch_show aux_voice; touch_hide more_open",
     "touch_show aux_voice; touch_show aux_bots; touch_hide more_open")
edit(touch,"touch_hide aux_voice; touch_hide more_close",
     "touch_hide aux_voice; touch_hide aux_bots; touch_hide more_close")
voicebutton='touch_addbutton "aux_voice" "vgui/touch/voicemenu" "exec tf2008_voice_panel_open"'
s=files[touch].decode("utf-8")
original=[line for line in s.splitlines() if line.startswith(voicebutton)]
assert len(original)==1
launcher='touch_addbutton "aux_bots" "vgui/touch/bot_menu" "exec tf2008_bots_panel_open" 0.576000 0.150000 0.644000 0.245000 255 255 255 205 1 0.620000'
s=s.replace(original[0],original[0]+"\n"+launcher)
# All bot settings are hidden by default.
pos={"bot_title":(.071,.268,.358,.345),"bot_close":(.373,.268,.487,.345)}
for row,names_in_row in enumerate([
    ("bot_add","bot_minus"),("bot_q0","bot_q4"),
    ("bot_q8","bot_q12"),("bot_q16","bot_all")
]):
    y=.370+row*.089
    for col,name in enumerate(names_in_row):
        x=.068+col*.196
        pos[name]=(x,y,x+.184,y+.080)
cmd={
 "bot_title":"echo BOT PANEL - HOST LOCAL ONLY",
 "bot_close":"exec tf2008_bots_panel_close",
 "bot_add":"tf2008_bots_plus",
 "bot_minus":"tf2008_bots_minus",
 "bot_q0":"exec tf2008_botquota_0",
 "bot_q4":"exec tf2008_botquota_4",
 "bot_q8":"exec tf2008_botquota_8",
 "bot_q12":"exec tf2008_botquota_12",
 "bot_q16":"exec tf2008_botquota_16",
 "bot_all":"exec tf2008_bots_kickall",
}
for name,(x1,y1,x2,y2) in pos.items():
    command=cmd[name]
    s+=(f'\ntouch_addbutton "{name}" "vgui/touch/{name}" "{command}" '
        f'{x1:.6f} {y1:.6f} {x2:.6f} {y2:.6f} 255 255 255 235 1 0.205000')
s+='\n\n// Quota state machine: +/- is capped safely between 0 and 16, never wraps around.\n'
s+='alias tf2008_bots_plus "exec tf2008_botquota_1"\n'
s+='alias tf2008_bots_minus "exec tf2008_botquota_0"\n'
files[touch]=s.encode("utf-8")
# Voice and bot panels should not steer the character as the finger taps the left HUD.
edit("tf_port/cfg/tf2008_voice_panel_open.cfg",
     "touch_hide aux_voice\n","touch_hide aux_voice\ntouch_hide aux_bots\ntouch_hide move\n")
edit("tf_port/cfg/tf2008_voice_panel_close.cfg",
     "touch_hide aux_voice\n","touch_hide aux_voice\ntouch_hide aux_bots\n")
voice_close=files["tf_port/cfg/tf2008_voice_panel_close.cfg"].decode("utf-8")
voice_close+="touch_show move\n"
files["tf_port/cfg/tf2008_voice_panel_close.cfg"]=voice_close.encode("utf-8")
extra=["aux_medic","aux_chat","aux_teamchat","aux_taunt","aux_team","aux_spray","aux_voice","aux_bots"]
bots_ui=list(pos)
bot_open=["// Host controls (local LAN or offline), tucked inside MORE.",
          "exec tf2008_voice_panel_close","touch_hide move"]
bot_open +=[f"touch_hide {n}" for n in extra]
bot_open+=["touch_hide more_open","touch_hide more_close"]
bot_open +=[f"touch_show {n}" for n in bots_ui]
put("tf_port/cfg/tf2008_bots_panel_open.cfg","\n".join(bot_open)+"\n")
bot_close=["// Re-enable free movement after closing bot controls."]
bot_close +=[f"touch_hide {n}" for n in bots_ui]
bot_close +=[f"touch_hide {n}" for n in extra]
bot_close+=["touch_hide more_close","touch_show more_open","touch_show move"]
put("tf_port/cfg/tf2008_bots_panel_close.cfg","\n".join(bot_close)+"\n")
# The user will supply their customized bot skill profile later. Leave NO skill defaults.
put("tf_port/cfg/tf2008_bot_difficulty_base.cfg",
    "// Custom bot difficulty pending user-supplied settings.\n"
    "// This file intentionally does not change tf_bot_difficulty or RCBot values.\n"
    "// After user provides the original profile, implement it here where supported.\n")
for target in range(17):
    prv=max(0,target-1)
    nxt=min(16,target+1)
    lines=[f"// Set exactly {target} native TF bots; clamp +/- at limits.",
           "exec tf2008_bot_difficulty_base",
           "tf_bot_quota_mode normal",
           f"tf_bot_quota {target}"]
    if target==0:lines+=["tf_bot_kick all"]
    lines+= [f'alias tf2008_bots_plus "exec tf2008_botquota_{nxt}"',
             f'alias tf2008_bots_minus "exec tf2008_botquota_{prv}"',
             "exec tf2008_bots_panel_close"]
    put(f"tf_port/cfg/tf2008_botquota_{target}.cfg","\n".join(lines)+"\n")
put("tf_port/cfg/tf2008_bots_kickall.cfg",
    "tf_bot_quota 0\n"
    "tf_bot_kick all\n"
    "exec tf2008_botquota_0\n")
reset="tf_port/cfg/tf2008_touch_reset_class.cfg"
edit(reset,"exec tf2008_voice_panel_close\n",
     "exec tf2008_voice_panel_close\nexec tf2008_bots_panel_close\n")
assert len([x for x in files if x.endswith(".vtf") and "/bot_" in x])==len(names)
assert len([x for x in files if x.startswith("tf_port/cfg/tf2008_botquota_")])==17
# Source touch button buffer is 256 bytes. Reject dangerously long touch commands.
for line in files[touch].decode("utf-8").splitlines():
    if line.startswith("touch_addbutton"):
        split=re.match(r'touch_addbutton\s+"[^"]*"\s+"[^"]*"\s+"([^"]*)"',line)
        if split:assert len(split.group(1).encode("utf-8"))<255,(len(split.group(1)),line[:80])
# Never overwrite the previous voice-only QA ZIP; this is a distinct combined package.
bundle=root/"TF2_2008_HUD_Toggle_Voices_Bots_v2_QA.zip"
with ZipFile(bundle,"w",ZIP_DEFLATED,compresslevel=6) as z:
    for key,data in sorted(files.items()):z.writestr(key,data)
with ZipFile(bundle) as test:
    assert set(test.namelist())==set(files)
    for key,data in files.items():assert test.read(key)==data
allow={}
for key in ("tf_port/cfg/touch.cfg","tf_port/cfg/tf2008_touch_reset_class.cfg"):
    allow[key]=[baseline_sha[key]]
# Add hashes of original overlay configs, for safe migration from older official app.
original=root.parents[1]/"android-app/assets/overlay.zip"
with ZipFile(original) as z:
    for key,name in [
        ("tf_port/cfg/touch.cfg","common/tf_port/cfg/touch.cfg"),
        ("tf_port/cfg/tf2008_touch_reset_class.cfg","common/tf_port/cfg/tf2008_touch_reset_class.cfg")]:
        allow[key].append(sha(z.read(name)))
for key in ["tf_port/cfg/tf2008_voice_panel_open.cfg","tf_port/cfg/tf2008_voice_panel_close.cfg"]:
    allow[key]=[baseline_sha[key]]
manifest={
 "product":"TF2_2008_MOBILE_TOUCH_CONTROLS_V2",
 "version":2,
 "total":len(files),
 "files":[{"path":key,"bytes":len(data),"sha256":sha(data),
          "allowed_original_sha256":sorted(set(allow.get(key,[])))}
          for key,data in sorted(files.items())],
}
mfile=root/"TF2_2008_HUD_Toggle_Voices_Bots_v2_manifest.json"
mfile.write_text(json.dumps(manifest,separators=(",",":"),ensure_ascii=True),encoding="utf-8")
assert len(files)==108, len(files)
assert len([key for key in files if key.endswith(".vtf")])==39
print("COMPLETE_BOT_MENU_QA",len(files),"FILES")
print("ZIP_BYTES",bundle.stat().st_size,"SHA256",sha(bundle.read_bytes()))
print("MANIFEST_BYTES",mfile.stat().st_size,"BOT_QUOTA_CONFIGS",17,
      "VOICE_MENUS","Z/X/C","ICON_GRAPHICS","28 voice + 11 bots","TOGGLE","same large button")
(root/"combined_info.json").write_text(json.dumps({
    "zip":str(bundle),"size":bundle.stat().st_size,"sha256":sha(bundle.read_bytes()),
    "manifest":str(mfile),"count":len(files)},indent=2),encoding="utf-8")
