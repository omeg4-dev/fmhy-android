import glob,re
t=f=e=s=0
for x in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    m=re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"',open(x).read())
    a,b,c,d=map(int,m.groups()); t+=a;s+=b;f+=c;e+=d
print(f"tests={t} failed={f} errors={e} skipped={s}")
