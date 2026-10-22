#!/usr/bin/env python3
"""usage: setpos.py TOP|BOTTOM  (emulator only; edits datastore preferences protobuf, app must be stopped)"""
import subprocess,sys
pos=sys.argv[1]; f='/data/data/io.github.omeg4dev.fmhy/files/datastore/fmhy_settings.preferences_pb'
A=['adb','-s','emulator-5554']
subprocess.run(A+['root'],capture_output=True); subprocess.run(A+['shell','am','force-stop','io.github.omeg4dev.fmhy'])
data=subprocess.run(A+['exec-out','cat',f],capture_output=True).stdout
def rv(b,i):
    n=0;s=0
    while True:
        c=b[i];i+=1;n|=(c&0x7f)<<s;s+=7
        if c<0x80:return n,i
def wv(n):
    o=bytearray()
    while True:
        c=n&0x7f;n>>=7
        if n:o.append(c|0x80)
        else:o.append(c);return bytes(o)
i=0;out=bytearray();found=False
while i<len(data):
    tag,j=rv(data,i);ln,k=rv(data,j);ent=data[k:k+ln];i=k+ln
    # entry: 0a klen key 12 vlen (2a slen str)
    kl,p=rv(ent,1);key=ent[p:p+kl];p+=kl
    if key==b'browser_toolbar_position':
        val=pos.encode();v=b'\x2a'+wv(len(val))+val
        ent=b'\x0a'+wv(kl)+key+b'\x12'+wv(len(v))+v;found=True
    out+=wv(tag)+wv(len(ent))+ent
print('found' if found else 'key absent (default TOP)')
if not found: sys.exit(1)
open('/tmp/claude-1000/pos.pb','wb').write(out)
subprocess.run(A+['push','/tmp/claude-1000/pos.pb',f],check=True)
