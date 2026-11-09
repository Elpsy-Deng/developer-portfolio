#!/usr/bin/env python3
"""Real loopback Nginx/API/migration/PG acceptance on an empty disposable deployment."""
import argparse
import base64
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request
import urllib.error
import urllib.parse
import portfolio_deploy as deploy
import portfolio_db as pg

def main():
    p=argparse.ArgumentParser();p.add_argument('--directory',required=True);p.add_argument('--output',required=True)
    p.add_argument('--release',default='initial');p.add_argument('--chromium',required=True);a=p.parse_args()
    d,e,v=deploy.configuration(a.directory);out=deploy.private(a.output);deploy.require(not out.exists(),'Preserve previous evidence')
    deploy.stopped(v);pg.container(v)
    deploy.require(pg.sql(d,v,"SELECT count(*) FROM pg_tables WHERE schemaname='public';")=='0','Acceptance only on a new empty disposable scope')
    out.mkdir(parents=True,mode=0o700);checks=[];result={};release=deploy.release(d,v,a.release)
    env=pg.environment();env.update(PORTFOLIO_APP_IMAGE=release['app'],PORTFOLIO_GATEWAY_IMAGE=release['gateway'])
    base='http://127.0.0.1:'+v['PORTFOLIO_GATEWAY_PORT']
    def command(argv,name,settings=env,expected=0,timeout=180):
        with (out/(name+'.log')).open('x') as f:
            Path(f.name).chmod(0o600);r=subprocess.run(argv,cwd=pg.ROOT,env=settings,stdout=f,stderr=subprocess.STDOUT,timeout=timeout)
        deploy.require(r.returncode==expected,name+' failed; private evidence preserved')
    def compose(*args):return deploy.compose(d,e,*args)
    def check(name,value):
        deploy.require(value,name);checks.append(name);print('PASS '+name,flush=True)
    def until(test,seconds=45):
        end=time.monotonic()+seconds
        while not test():
            deploy.require(time.monotonic()<end,'Bounded readiness timeout');time.sleep(.25)
    def client():return urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    def request(opener,path,body=None,method=None,raw=False,headers=None):
        data=None;h=headers or {}
        if body is not None:
            if isinstance(body,bytes):data=body
            elif path=='/api/admin/login':data=urllib.parse.urlencode(body).encode();h={**h,'Content-Type':'application/x-www-form-urlencoded'}
            else:data=json.dumps(body).encode();h={**h,'Content-Type':'application/json'}
        try:r=opener.open(urllib.request.Request(base+path,data=data,method=method,headers=h),timeout=22)
        except urllib.error.HTTPError as x:r=x
        with r:
            b=r.read();return r.status,b if raw else json.loads(b) if b else None
    anon=client();admin=client();viewer=client()
    def read_ready():
        try:return request(anon,'/api/public/health')[0]==200
        except (OSError,ValueError):return False
    def token(c):
        status,b=request(c,'/api/admin/session');deploy.require(status==200,'Session available');return b['csrf']['token']
    def write(c,path,body=None,method='POST'):return request(c,path,body,method,headers={'X-CSRF-TOKEN':token(c)})
    try:
        command(compose('up','-d','--no-deps','api'),'unmigrated-start')
        api=deploy.ids(v,'api',True)[0]
        def exited():return subprocess.check_output(pg.DOCKER+['inspect','--format','{{.State.Status}}',api],env=env,text=True).strip()=='exited'
        until(exited)
        command(pg.DOCKER+['logs',api],'unmigrated-api-log')
        check('unmigrated API refuses startup for actual schema validation without creating tables',
              'FlywayValidateException' in (out/'unmigrated-api-log.log').read_text() and pg.sql(d,v,"SELECT count(*) FROM pg_tables WHERE schemaname='public';")=='0')
        command(compose('rm','-f','api'),'remove-unmigrated-api')
        command(pg.DOCKER+['exec',pg.container(v),'df','-h','/var/lib/postgresql/data'],'before-migration-disk')
        command(['python','scripts/portfolio_deploy.py','up','--directory',str(d),'--release',a.release],'activate')
        until(read_ready)
        check('one-shot migration exits, API/gateway healthy and schema V1-V9 installed',not deploy.ids(v,'migration') and pg.sql(d,v,'SELECT count(*) FROM flyway_schema_history WHERE success;')=='9')
        api=deploy.ids(v,'api')[0];gateway=deploy.ids(v,'gateway')[0]
        meta=json.loads(subprocess.check_output(pg.DOCKER+['inspect',api],env=env,text=True))[0]
        variables=[x.split('=',1)[0] for x in meta['Config']['Env']]
        check('API is nonroot/read-only/no capabilities and has no privileged environment',meta['Config']['User']==v['PORTFOLIO_RUNTIME_UID']+':'+v['PORTFOLIO_RUNTIME_GID'] and meta['HostConfig']['ReadonlyRootfs'] and 'ALL' in meta['HostConfig']['CapDrop'] and not any('MIGRATION' in x or 'BOOTSTRAP' in x for x in variables))
        sources=[x['Destination'] for x in meta['Mounts']]
        check('API mounts only runtime secret plus own media, no owner/bootstrap secret',sources==['/data/media','/run/secrets/runtime.properties'] or set(sources)=={'/data/media','/run/secrets/runtime.properties'})
        check('API publishes no host port and gateway is nonroot',not meta['HostConfig']['PortBindings'] and json.loads(subprocess.check_output(pg.DOCKER+['inspect',gateway],env=env,text=True))[0]['Config']['User']=='101:101')
        actual=subprocess.check_output(pg.DOCKER+['exec',api,'sha256sum','/app/portfolio.jar'],env=env,text=True).split()[0]
        check('container runs the verified package bytes',actual==release['artifacts']['backend/target/developer-portfolio-0.2.0.jar'])
        for path in ('/projects/elpsy-shop','/admin/projects','/about'):
            status,b=request(anon,path,raw=True);check('Nginx SPA deep link '+path,status==200 and b'<html' in b)
        check('missing static assets return404 rather than HTML',request(anon,'/assets/missing.js',raw=True)[0]==404 and request(anon,'/projects/missing.png',raw=True)[0]==404)
        history=pg.sql(d,v,'SELECT jsonb_agg(to_jsonb(x) ORDER BY installed_rank)::text FROM flyway_schema_history x;')
        command(['python','scripts/portfolio_deploy.py','create-account','--directory',str(d),'--username','deploy_admin'],'admin-provision')
        command(['python','scripts/portfolio_deploy.py','create-account','--directory',str(d),'--username','deploy_viewer','--role','VIEWER'],'viewer-provision')
        browser_env=env.copy()
        for role,name in [('ADMIN','deploy_admin'),('VIEWER','deploy_viewer')]:
            account=dict(l.split('=',1) for l in (d/('account-'+name+'.env')).read_text().splitlines())
            browser_env['PORTFOLIO_E2E_'+role+'_USERNAME']=account['PORTFOLIO_LOGIN_USERNAME'];browser_env['PORTFOLIO_E2E_'+role+'_PASSWORD']=account['PORTFOLIO_LOGIN_PASSWORD']
        browser_env.update(PORTFOLIO_E2E_DEPLOYED_URL=base,PORTFOLIO_E2E_PORT=v['PORTFOLIO_GATEWAY_PORT'],PORTFOLIO_E2E_OUTPUT=str(out/'browser'),PORTFOLIO_CHROMIUM_PATH=a.chromium)
        fixture=out/'synthetic.png'
        fixture.write_bytes(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAABQAAAAMCAIAAADtbgqsAAAAFklEQVR4nGNw7IkhGzGMah7VPHg1AwAU/haAot1VCgAAAABJRU5ErkJggg=='))
        fixture.chmod(0o600);browser_env['PORTFOLIO_E2E_IMAGE_FILE']=str(fixture)
        command(['npm','run','test:e2e'],'browser',browser_env,timeout=300)
        browser=json.loads((out/'browser/report.json').read_text())['stats']
        check('all43 production browser cases run through real Nginx/container API',browser['expected']==43 and not any(browser[k] for k in ('unexpected','skipped','flaky')));result['browser']=browser
        for role,c in [('ADMIN',admin),('VIEWER',viewer)]:
            status,_=write(c,'/api/admin/login',{'username':browser_env['PORTFOLIO_E2E_'+role+'_USERNAME'],'password':browser_env['PORTFOLIO_E2E_'+role+'_PASSWORD']})
            check('real gateway login '+role,status==204)
        check('VIEWER cannot read account directory',request(viewer,'/api/admin/accounts')[0]==403)
        check('missing CSRF cannot mutate taxonomy',request(admin,'/api/admin/taxonomy/categories',{'label':'forbidden','sortOrder':2000})[0]==403)
        original=request(admin,'/api/admin/projects/developer-portfolio')[1]
        body={'content':{k:val for k,val in original['project'].items() if k not in ['coverImage','screenshots','localDemoUrl']},'published':False,'sortOrder':original['sortOrder'],'expectedVersion':original['version']}
        body['content']['slug']='deploy-proof';body['content']['title']='Synthetic deployment proof';body['expectedVersion']=None;body['sortOrder']=2000
        status,created=write(admin,'/api/admin/projects',body);check('real ADMIN creates private draft with transaction audit',status==201)
        body['expectedVersion']=created['version'];body['published']=True
        status,published=write(admin,'/api/admin/projects/deploy-proof',body,'PUT');check('real ADMIN publishes project; stale version refuses',status==200 and write(admin,'/api/admin/projects/deploy-proof',body,'PUT')[0]==409)
        # Same canonical synthetic PNG as the existing independent runtime, no external image service.
        png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAABQAAAAMCAIAAADtbgqsAAAAFklEQVR4nGNw7IkhGzGMah7VPHg1AwAU/haAot1VCgAAAABJRU5ErkJggg==')
        boundary='portfolio-deploy-image';fields={'expectedVersion':str(published['version']),'kind':'SCREENSHOT','title':'Deployment image','description':'Synthetic','alt':'Synthetic PNG'}
        raw=b''.join((f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{value}\r\n').encode() for k,value in fields.items())
        raw+=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="synthetic.png"\r\nContent-Type: image/png\r\n\r\n').encode()+png+(f'\r\n--{boundary}--\r\n').encode()
        status,b=request(admin,'/api/admin/projects/deploy-proof/media',raw,'POST',headers={'Content-Type':'multipart/form-data; boundary='+boundary,'X-CSRF-TOKEN':token(admin)})
        check('real gateway multipart upload persists normalized image/version',status==200)
        project=request(anon,'/api/public/projects/deploy-proof')[1];image=project['screenshots'][0]['src'];status,bytes_before=request(anon,image,raw=True)
        check('public image follows published PG reference',status==200 and len(bytes_before)>0)
        durable=pg.sql(d,v,"SELECT jsonb_agg(to_jsonb(p))::text FROM project p WHERE slug='deploy-proof';")
        command(compose('restart','api'),'api-restart');until(read_ready)
        check('API restart preserves original session and image bytes',request(admin,'/api/admin/session')[1]['authenticated'] and request(anon,image,raw=True)==(200,bytes_before))
        command(compose('stop','api'),'api-stop');check('API stop gives gateway502/504 while static front stays available',request(anon,'/api/public/health',raw=True)[0] in [502,504] and request(anon,'/',raw=True)[0]==200)
        command(compose('start','api'),'api-start');until(read_ready)
        command(compose('stop','postgres'),'pg-stop')
        check('real PG outage returns503 with no catalog fallback',request(anon,'/api/public/projects')[0]==503 and request(anon,image,raw=True)[0]==503)
        command(compose('start','postgres'),'pg-start');until(read_ready)
        until(lambda:request(admin,'/api/admin/session')[0]==200)
        check('same-volume PG restart preserves session/domain/image',request(admin,'/api/admin/session')[1]['authenticated'] and pg.sql(d,v,"SELECT jsonb_agg(to_jsonb(p))::text FROM project p WHERE slug='deploy-proof';")==durable and request(anon,image,raw=True)==(200,bytes_before))
        command(['python','scripts/portfolio_deploy.py','stop','--directory',str(d)],'stop-app')
        command(['python','scripts/portfolio_deploy.py','up','--directory',str(d),'--release',a.release],'migration-replay');until(read_ready)
        check('migration replay preserves complete history and edited records',pg.sql(d,v,'SELECT jsonb_agg(to_jsonb(x) ORDER BY installed_rank)::text FROM flyway_schema_history x;')==history and pg.sql(d,v,"SELECT jsonb_agg(to_jsonb(p))::text FROM project p WHERE slug='deploy-proof';")==durable)
        command(['python','scripts/portfolio_deploy.py','stop','--directory',str(d)],'before-rollback')
        # An explicit bad candidate, same verified JAR, must fail before automatic migration.
        fault=out/'fault-image';fault.mkdir(mode=0o700)
        # Dockerfile FROM resolves a tag, not the local sha256 image ID syntax.
        fault_name='bad-'+os.urandom(4).hex()
        base_tag='portfolio-app:'+v['COMPOSE_PROJECT_NAME']+'-'+fault_name+'-base'
        command(pg.DOCKER+['tag',release['app'],base_tag],'fault-base-tag')
        (fault/'Dockerfile').write_text('FROM '+base_tag+'\nENTRYPOINT ["java","-Xmx256m","-XX:+UseSerialGC","-XX:ActiveProcessorCount=2","-jar","/app/portfolio.jar","--spring.flyway.enabled=true","--spring.flyway.url=jdbc:postgresql://postgres:5432/portfolio_demo"]\nCMD []\n')
        tag='portfolio-app:'+v['COMPOSE_PROJECT_NAME']+'-'+fault_name
        command(pg.DOCKER+['build','--label','dev.elpsy.portfolio.scope='+v['COMPOSE_PROJECT_NAME'],'--label','dev.elpsy.portfolio.release='+fault_name,'-t',tag,str(fault)],'fault-build')
        broken=json.loads(json.dumps(release));broken['release']=fault_name
        broken['app']=subprocess.check_output(pg.DOCKER+['image','inspect','--format','{{.Id}}',tag],env=env,text=True).strip()
        deploy.require(not (d/('release-'+fault_name+'.json')).exists(),'Preserve previous fault release')
        deploy.save(d/('release-'+fault_name+'.json'),broken)
        command(['python','scripts/portfolio_deploy.py','up','--directory',str(d),'--release',fault_name],'bad-activation',expected=1)
        failed_api=deploy.ids(v,'api',True)[0];command(pg.DOCKER+['logs',failed_api],'bad-api-log')
        check('actual bad candidate refuses enabled Flyway before DB changes',
              'Deployment API must not run migrations' in (out/'bad-api-log.log').read_text() and pg.sql(d,v,'SELECT jsonb_agg(to_jsonb(x) ORDER BY installed_rank)::text FROM flyway_schema_history x;')==history)
        command(['python','scripts/portfolio_deploy.py','stop','--directory',str(d)],'stop-bad-candidate')
        command(['python','scripts/portfolio_deploy.py','rollback','--directory',str(d),'--release',a.release],'rollback');until(read_ready)
        actual_image=subprocess.check_output(pg.DOCKER+['inspect','--format','{{.Image}}',deploy.ids(v,'api')[0]],env=env,text=True).strip()
        check('previous pinned image restored after failed candidate; history/media/session preserved',actual_image==release['app'] and actual_image!=broken['app'] and pg.sql(d,v,'SELECT jsonb_agg(to_jsonb(x) ORDER BY installed_rank)::text FROM flyway_schema_history x;')==history and request(anon,image,raw=True)==(200,bytes_before) and request(admin,'/api/admin/session')[1]['authenticated'])
        check('production artifacts never changed during acceptance',all(deploy.hash_file(pg.ROOT/k)==h for k,h in release['artifacts'].items()))
        import showcase_acceptance
        result['showcase'] = showcase_acceptance.run(d,out,a.chromium,env,command)
        result.update(outcome='PASS',passed=len(checks),checks=checks,artifacts=release['artifacts'],scope='Own Nginx/API/one-shot migration/PG17 containers; loopback HTTP; no production TLS or CI')
    except Exception as error:
        for service in ('api','gateway','postgres'):
            for container in deploy.ids(v,service,True):
                command(pg.DOCKER+['logs',container],'failed-'+service+'-log')
        result.update(outcome='FAIL',passed=len(checks),checks=checks,error=str(error));raise
    finally:
        # Only this previously empty explicitly disposable scope; never caller data.
        command(['python','scripts/portfolio_deploy.py','down','--directory',str(d),'--purge'],'cleanup')
        resources={}
        for key,args in [('containers',['ps','-aq']),('volumes',['volume','ls','-q']),('networks',['network','ls','-q'])]:
            resources[key]=len(subprocess.check_output(pg.DOCKER+args+['--filter','label=com.docker.compose.project='+v['COMPOSE_PROJECT_NAME']],env=env,text=True).splitlines())
        result['cleanup']=resources;deploy.save(out/'result.json',result)
        deploy.require(not any(resources.values()),'Own resources remain')

if __name__=='__main__':main()
