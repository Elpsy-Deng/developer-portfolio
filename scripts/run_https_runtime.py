#!/usr/bin/env python3
"""Real CA/hostname-verified HTTPS acceptance on the CI-owned disposable scope."""
import argparse
import hashlib
import http.cookiejar
import json
import re
from pathlib import Path
import socket
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import portfolio_db as pg
import portfolio_deploy as deploy
import portfolio_https as tls
import showcase_release as release


def main():
 p=argparse.ArgumentParser();p.add_argument('--directory',required=True);p.add_argument('--output',required=True);p.add_argument('--release',default='ci');a=p.parse_args()
 d,e,v=deploy.configuration(a.directory);out=deploy.private(a.output);deploy.require(not out.exists(),'Preserve prior TLS evidence');deploy.stopped(v)
 # This tool only accepts the private run_ci marker, not a persistent human deployment.
 deploy.require((d.parent/'.portfolio-ci-v1').read_text()=='portfolio-ci-v1\n' and d.name=='deploy-db','Owned disposable CI deployment required')
 out.mkdir(mode=0o700);checks=[];result={'outcome':'FAIL','certificateVerification':'ENABLED'};operation=0;ca=out/'certificates';ca.mkdir(mode=0o700)
 def command(argv,label,expected=0):
  with (out/(label+'.log')).open('x') as f:
   Path(f.name).chmod(0o600);r=subprocess.run(argv,cwd=pg.ROOT,env=pg.environment(),stdout=f,stderr=subprocess.STDOUT,timeout=180)
  deploy.require(r.returncode==expected,label+' failed; inspect private log')
 def check(name,value):deploy.require(value,name);checks.append(name);print('PASS HTTPS '+name,flush=True)
 def request(client,path,headers=None,data=None):
  try:r=client.open(urllib.request.Request(base+path,headers=headers or {},data=data),timeout=20)
  except urllib.error.HTTPError as error:r=error
  with r:
   result['lastHttpStatus']=r.status
   return r.status,dict(r.headers),r.read()
 def ready():
  end=time.monotonic()+45
  while True:
   try:
    if request(anonymous,'/api/public/health')[0]==200:return
   except (OSError,ValueError):pass
   deploy.require(time.monotonic()<end,'TLS readiness timeout');time.sleep(.25)
 def hash(value):return hashlib.sha256(value.encode()).hexdigest()
 try:
  command(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','2','-subj','/CN=Portfolio synthetic TLS acceptance CA','-addext','basicConstraints=critical,CA:TRUE','-keyout',str(ca/'ca.key'),'-out',str(ca/'ca.crt')],'ca')
  command(['openssl','req','-newkey','rsa:2048','-nodes','-subj','/CN=localhost','-keyout',str(ca/'leaf.key'),'-out',str(ca/'leaf.csr')],'leaf')
  (ca/'san.conf').write_text('subjectAltName=DNS:localhost\nbasicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\n')
  command(['openssl','x509','-req','-in',str(ca/'leaf.csr'),'-CA',str(ca/'ca.crt'),'-CAkey',str(ca/'ca.key'),'-CAcreateserial','-days','2','-extfile',str(ca/'san.conf'),'-out',str(ca/'leaf.crt')],'sign')
  for f in ca.iterdir():f.chmod(0o600)
  command([sys.executable,'scripts/portfolio_https.py','enable','--directory',str(d),'--hostname','localhost','--certificate',str(ca/'leaf.crt'),'--key',str(ca/'leaf.key')],'tls-enable')
  command([sys.executable,'scripts/portfolio_deploy.py','database','--directory',str(d)],'database')
  check('fresh disposable database before TLS migration',pg.sql(d,v,"SELECT count(*) FROM pg_tables WHERE schemaname='public';")=='0')
  command([sys.executable,'scripts/portfolio_deploy.py','up','--directory',str(d),'--release',a.release],'up')
  settings=json.loads((d/'.portfolio-https-v1').read_text());base='https://localhost:'+str(settings['httpsPort']);anonymous=tls.opener(settings,str(ca/'ca.crt'))
  ready();status,headers,body=request(anonymous,'/api/public/projects')
  check('real verified CA/SAN HTTPS catalog, stateless',status==200 and len(json.loads(body)['items'])==5 and 'Set-Cookie' not in headers)
  try:tls.opener(settings).open(base+'/api/public/health',timeout=10);untrusted=False
  except urllib.error.URLError as error:untrusted=isinstance(error.reason,ssl.SSLCertVerificationError)
  check('untrusted test CA rejected with verification enabled',untrusted)
  try:
   with socket.create_connection(('127.0.0.1',settings['httpsPort']),timeout=5) as s:
    with ssl.create_default_context(cafile=str(ca/'ca.crt')).wrap_socket(s,server_hostname='wrong.example'):pass
   wrong=False
  except ssl.SSLCertVerificationError:wrong=True
  check('wrong hostname rejected by TLS verifier',wrong)
  check('TLS HSTS and content type hardening',headers.get('Strict-Transport-Security')=='max-age=86400' and headers.get('X-Content-Type-Options')=='nosniff')
  check('TLS unknown Host rejected',request(anonymous,'/api/public/projects',{'Host':'evil.example'})[0]==421)
  check('TLS SPA deep links served',all(request(anonymous,path)[0]==200 for path in ('/projects/developer-portfolio','/admin/login','/about')))
  check('TLS missing asset refuses SPA fallback',request(anonymous,'/assets/absent.js')[0]==404)
  class NoRedirect(urllib.request.HTTPRedirectHandler):
   def redirect_request(self,*args,**kwargs):return None
  plain=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
  def plain_request(path,host,data=None):
   try:r=plain.open(urllib.request.Request('http://127.0.0.1:'+v['PORTFOLIO_GATEWAY_PORT']+path,headers={'Host':host},data=data),timeout=10)
   except urllib.error.HTTPError as error:r=error
   with r:return r.status,dict(r.headers),r.read()
  r=plain_request('/projects/developer-portfolio?a=%2F','localhost:15190')
  check('HTTP 308 preserves path/query to canonical HTTPS origin',r[0]==308 and r[1].get('Location')==base+'/projects/developer-portfolio?a=%2F')
  check('HTTP malicious Host rejected, not reflected',plain_request('/admin/login','evil.example')[0]==421)
  n=pg.sql(d,v,'SELECT count(*) FROM spring_session;');r=plain_request('/api/admin/login','localhost:15190',b'username=synthetic&password=synthetic')
  check('HTTP admin body not proxied, no Session/Cookie',r[0]==308 and 'Set-Cookie' not in r[1] and pg.sql(d,v,'SELECT count(*) FROM spring_session;')==n)
  operation=1
  command([sys.executable,'scripts/portfolio_deploy.py','create-account','--directory',str(d),'--username','https_admin'],'admin')
  operation=2
  command([sys.executable,'scripts/portfolio_deploy.py','create-account','--directory',str(d),'--username','https_viewer','--role','VIEWER'],'viewer')
  operation=3
  _,admin=release.connection(d,d/'account-https_admin.env',str(ca/'ca.crt'))
  jar=http.cookiejar.CookieJar();view=tls.opener(settings,str(ca/'ca.crt'),jar)
  operation=4
  csrf=json.loads(request(view,'/api/admin/session')[2])['csrf']['token']
  creds=dict(line.split('=',1) for line in (d/'account-https_viewer.env').read_text().splitlines())
  operation=5
  status,_,_=request(view,'/api/admin/login',{'Content-Type':'application/x-www-form-urlencoded','X-CSRF-TOKEN':csrf},urllib.parse.urlencode({'username':creds['PORTFOLIO_LOGIN_USERNAME'],'password':creds['PORTFOLIO_LOGIN_PASSWORD']}).encode())
  operation=6
  cookies=[c for c in jar if c.name=='PORTFOLIO_SESSION']
  result['cookiePolicyBits']=(16 if status==204 else 0)|(1 if cookies else 0)|(2 if any(c.secure for c in cookies) else 0)|(4 if any(c.has_nonstandard_attr('HttpOnly') for c in cookies) else 0)|(8 if any(c.get_nonstandard_attr('SameSite')=='Strict' for c in cookies) else 0)
  check('TLS login has Secure HttpOnly SameSite Strict Cookie',status==204 and any(c.name=='PORTFOLIO_SESSION' and c.secure and c.has_nonstandard_attr('HttpOnly') and c.get_nonstandard_attr('SameSite')=='Strict' for c in jar))
  operation=0
  current=admin.call('/api/admin/projects/developer-portfolio');body={'content':release.content(current),'expectedVersion':current['version'],'published':current['published'],'sortOrder':current['sortOrder']};body['content']['statusNote']+=' 合成HTTPS驗收'
  check('TLS missing CSRF rejected',request(admin.opener,'/api/admin/projects/developer-portfolio',{'Content-Type':'application/json'},json.dumps(body).encode())[0]==403)
  # PUT through real ADMIN; VIEWER cannot write the same catalog.
  updated=admin.write('/api/admin/projects/developer-portfolio',body)
  check('TLS ADMIN CAS mutation/audit committed',updated['version']==current['version']+1 and len(admin.call('/api/admin/projects/developer-portfolio/audit'))==1)
  try:admin.write('/api/admin/projects/developer-portfolio',body);stale=False
  except RuntimeError as err:stale=str(err)=='API HTTP 409'
  check('TLS stale version rejected',stale)
  csrf=json.loads(request(view,'/api/admin/session')[2])['csrf']['token']
  check('TLS VIEWER cannot mutate',request(view,'/api/admin/projects',{'X-CSRF-TOKEN':csrf,'Content-Type':'application/json'},b'{}')[0]==403)
  token=admin.call('/api/admin/session')['csrf']['token']
  forged=['203.0.113.9','198.51.100.9']
  status,_,_=request(admin.opener,'/api/admin/login',{'Content-Type':'application/x-www-form-urlencoded','X-CSRF-TOKEN':token,'Forwarded':'for='+forged[0],'X-Forwarded-For':forged[0],'X-Portfolio-Peer':forged[1],'X-Portfolio-Gateway':'b'*64},b'username=absent&password=synthetic')
  gateway=deploy.ids(v,'gateway')[0];metadata=json.loads(subprocess.check_output(pg.DOCKER+['inspect',gateway],env=pg.environment(),text=True))[0]
  ownnet=next(iter(metadata['NetworkSettings']['Networks'].values()));actual=ownnet['Gateway'];gateway_ip=ownnet['IPAddress']
  def exists(key):return pg.sql(d,v,"SELECT count(*) FROM admin_login_bucket WHERE bucket_key='"+hash(key)+"';")=='1'
  check('gateway overwrites spoofed claims; actual client peer differs from gateway IP',status==401 and exists('peer:'+actual) and not exists('peer:'+gateway_ip) and all(not exists('peer:'+f) for f in forged))
  api=json.loads(subprocess.check_output(pg.DOCKER+['inspect',deploy.ids(v,'api')[0]],env=pg.environment(),text=True))[0]
  check('TLS key/config only gateway; API no TLS/owner/bootstrap secret',metadata['Config']['User']==v['PORTFOLIO_RUNTIME_UID']+':'+v['PORTFOLIO_RUNTIME_GID'] and metadata['HostConfig']['ReadonlyRootfs'] and all(m['Destination'] not in ('/run/secrets/tls.key','/run/secrets/tls.crt','/etc/nginx/nginx.conf','/run/secrets/migration.properties','/run/secrets/bootstrap.password') for m in api['Mounts']))
  plan=out/'showcase-plan.json'
  for action in ('plan','apply'):
   cmd=[sys.executable,'scripts/showcase_release.py',action,'--directory',str(d),'--credentials',str(d/'account-https_admin.env'),'--cafile',str(ca/'ca.crt'),'--plan',str(plan)]
   if action=='apply':cmd+=['--plan-sha256',deploy.hash_file(plan)]
   command(cmd,'tls-showcase-'+action)
  check('reviewed showcase release and normalized images also work through verified TLS',all(admin.call('/api/admin/projects/'+p['slug'])['project']['evidenceCheckedAt']=='2026-10-13' for p in release.bundle()['projects']))
  command(deploy.compose(d,e,'restart','api'),'restart');ready()
  check('authenticated Session and curated data survive TLS API restart',admin.call('/api/admin/session')['authenticated'] and admin.call('/api/admin/projects/developer-portfolio')['project']['evidenceCheckedAt']=='2026-10-13')
  command([sys.executable,'scripts/portfolio_deploy.py','stop','--directory',str(d)],'stop-for-quota')
  with (d/'runtime.properties').open('a') as f:f.write('portfolio.auth.session-peer-limit=3\nportfolio.auth.session-global-limit=5\n')
  # Explicit own clock fixture: expire existing counters; security stays enabled, with lower limits.
  pg.sql(d,v,'UPDATE admin_login_bucket SET window_start=window_start-301;')
  command([sys.executable,'scripts/portfolio_deploy.py','up','--directory',str(d),'--release',a.release],'up-bounded');ready()
  n=int(pg.sql(d,v,'SELECT count(*) FROM spring_session;'))
  for i in range(3):check('anonymous TLS Session admitted '+str(i+1),request(anonymous,'/api/admin/session')[0]==200)
  r=request(anonymous,'/api/admin/session',{'X-Portfolio-Peer':'203.0.113.20','X-Forwarded-For':'203.0.113.20'})
  check('fourth Session 429/no new Cookie/row, spoofing cannot evade',r[0]==429 and json.loads(r[2])['code']=='SESSION_RATE_LIMITED' and 'Retry-After' in r[1] and 'Set-Cookie' not in r[1] and int(pg.sql(d,v,'SELECT count(*) FROM spring_session;'))==n+3)
  check('authenticated TLS Session unaffected by anonymous quota',admin.call('/api/admin/session')['authenticated'])
  check('fake Session cookie does not evade quota',request(anonymous,'/api/admin/session',{'Cookie':'PORTFOLIO_SESSION=fake'})[0]==429)
  command(deploy.compose(d,e,'restart','api'),'quota-api-restart');ready()
  check('anonymous quota persists API restart',request(anonymous,'/api/admin/session')[0]==429)
  command(deploy.compose(d,e,'restart','postgres'),'quota-pg-restart');ready()
  check('anonymous quota/Session persist same-volume PG restart',request(anonymous,'/api/admin/session')[0]==429 and admin.call('/api/admin/session')['authenticated'])
  check('public TLS catalog unaffected by anonymous quota',request(anonymous,'/api/public/projects')[0]==200)
  pg.sql(d,v,'UPDATE admin_login_bucket SET window_start=window_start-301;')
  check('PG clock expiry permits fresh TLS Session again',request(anonymous,'/api/admin/session')[0]==200)
  result.update(outcome='PASS',checksPassed=len(checks))
 except Exception as error:
  result.update(errorType=type(error).__name__,checksPassed=len(checks),failureOperation=operation)
  result['failureCode']={
   'Own running gateway/API required':1,'Own loopback gateway required':2,
   'Private scope-local credential file required':3,'Own loopback TLS gateway required':4,
   'ADMIN required':5,
  }.get(str(error),0)
  result['failureTypeCode']={'RuntimeError':1,'KeyError':2,'ValueError':3,'URLError':4,'SSLError':5,'TypeError':6,'AttributeError':7,'OSError':8}.get(type(error).__name__,0)
  frames=[];trace=error.__traceback__
  files={'showcase_release.py':1,'portfolio_https.py':2,'portfolio_deploy.py':3,'portfolio_db.py':4,'run_https_runtime.py':5}
  while trace:
   frame=Path(trace.tb_frame.f_code.co_filename)
   if frame.parent==pg.ROOT/'scripts' and frame.name in files:frames.append({'fileCode':files[frame.name],'line':trace.tb_lineno})
   trace=trace.tb_next
  result['sourceFrames']=frames
  status=re.fullmatch(r'API HTTP ([1-5][0-9]{2})',str(error))
  if status:result['failedHttpStatus']=int(status[1])
  raise
 finally:
  command([sys.executable,'scripts/portfolio_deploy.py','down','--directory',str(d),'--purge'],'cleanup')
  remaining={}
  for kind,args in [('containers',['ps','-aq']),('volumes',['volume','ls','-q']),('networks',['network','ls','-q'])]:
   remaining[kind]=len(subprocess.check_output(pg.DOCKER+args+['--filter','label=com.docker.compose.project='+v['COMPOSE_PROJECT_NAME']],env=pg.environment(),text=True).split())
  result.update(cleanup=remaining,checks=checks);deploy.save(out/'result.json',result)
  deploy.require(not any(remaining.values()),'TLS disposable cleanup incomplete')

if __name__=='__main__':
 try:main()
 except Exception as error:print('HTTPS acceptance failed: '+type(error).__name__);sys.exit(1)
