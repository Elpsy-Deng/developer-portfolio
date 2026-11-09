#!/usr/bin/env python3
"""Real offline backup / new PostgreSQL / packaged HTTP restore acceptance."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
import uuid
import portfolio_db as pg
import portfolio_backup as backup
import run_persistence_runtime as rt


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--output', required=True); parser.add_argument('--java-home', required=True)
    args = parser.parse_args(); output = backup.private_path(args.output)
    backup.require(not output.exists(), 'Preserve previous drill; use a new output directory')
    for port in (19186, 25487, 25488, 25489): backup.port_unused(port)
    output.mkdir(parents=True, mode=0o700)
    source = output / 'source'; target = output / 'restored'; bad_target = output / 'quarantined'
    pg.initialize(source, 25487, 'portfolio_demo'); d, envfile, values = pg.configuration(source)
    result = {'outcome':'RUNNING','checks':[],'automaticRetries':0,'transport':'PG17 custom dump / own fresh scope / packaged JAR / canonical PNG',
              'artifactSha256':hashlib.sha256((pg.ROOT/'backend/target/developer-portfolio-0.2.0.jar').read_bytes()).hexdigest()}
    processes = []; handles = []
    def check(name, condition):
        backup.require(condition, name); result['checks'].append(name)
    def stop(process):
        if process.poll() is None:
            process.terminate()
            try: process.wait(timeout=8)
            except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=5)
    def start(scope, name):
        _, _, config = pg.configuration(scope); env = pg.environment(); env.update(config)
        env.update(PORTFOLIO_PORT='19186', PORTFOLIO_COOKIE_SECURE='false')
        log=(output/(name+'.log')).open('xb');os.chmod(log.name,0o600);handles.append(log)
        process=subprocess.Popen([str(Path(args.java_home)/'bin/java'),'-Xmx256m','-XX:+UseSerialGC','-jar','backend/target/developer-portfolio-0.2.0.jar','--portfolio.media.scheduled-cleanup=false'],cwd=pg.ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
        processes.append(process);rt.ready(process);return process
    def reject(name, action):
        try: action()
        except backup.BackupProblem: check(name, True)
        else: raise RuntimeError(name)
    def credentials(scope, name):
        return dict(line.split('=',1) for line in (scope/('account-'+name+'.env')).read_text().splitlines())
    def login(client, account):
        return client.call('POST','login',{'username':account['PORTFOLIO_LOGIN_USERNAME'],'password':account['PORTFOLIO_LOGIN_PASSWORD']},client.session()['csrf']['token'])[0]
    def mutate(client, method, path, body): return client.call(method,path,body,client.session()['csrf']['token'],True)
    def clone_bundle(name):
        path=output/name;shutil.copytree(bundle,path);return path
    try:
        pg.compose(envfile,'up','-d','--wait');pg.prepare_roles(d,values)
        first=start(source,'api-source')
        for name, role in [('restore_admin','ADMIN'),('restore_viewer','VIEWER')]: pg.provision_account(d,values,name,role)
        administrator=credentials(source,'restore_admin');viewer_account=credentials(source,'restore_viewer')
        auth=rt.AuthClient();check('source real ADMIN login succeeds',login(auth,administrator)==204)
        viewer=rt.AuthClient();check('source real VIEWER login succeeds',login(viewer,viewer_account)==204)
        status, disabled, _=mutate(auth,'PUT','accounts/restore_viewer',{'expectedVersion':1,'role':'VIEWER','enabled':False})
        check('source governed identity audit/version and prior VIEWER revocation exist',status==200 and disabled['version']==2 and viewer.call('GET','overview')[0]==401)
        status, _, _=mutate(auth,'POST','accounts',{'username':'restore_reader','password':viewer_account['PORTFOLIO_LOGIN_PASSWORD'],'role':'VIEWER','enabled':True})
        check('source enabled VIEWER with safe CREATE audit exists',status==201)
        status, category, _=mutate(auth,'POST','taxonomy/categories',{'label':'Backup own category','sortOrder':100});check('source dynamic taxonomy created',status==201)
        status, tech, _=mutate(auth,'POST','taxonomy/technologies',{'label':'Backup own technology'});backup.require(status==201,'Source technology must be created')
        core=auth.call('GET','projects/developer-portfolio')[1]['project']
        for key in ['coverImage','screenshots','localDemoUrl']:core.pop(key,None)
        core.update(category=category['label'],featured=False,technologies=[tech['label']])
        fixture=output/'synthetic.png';fixture.write_bytes(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAABQAAAAMCAIAAADtbgqsAAAAFklEQVR4nGNw7IkhGzGMah7VPHg1AwAU/haAot1VCgAAAABJRU5ErkJggg=='));fixture.chmod(0o600)
        saved={};images={}
        for slug,published in [('restore-public-proof',True),('restore-draft-proof',False),('restore-retired-proof',False)]:
            content={**core,'slug':slug,'title':slug};status, row, _=mutate(auth,'POST','projects',{'content':content,'published':published})
            backup.require(status==201,'Own project fixture must be created')
            status,row=rt.upload_image(auth,slug,1,fixture);backup.require(status==200,'Own real PNG upload must succeed')
            saved[slug]=row;images[slug]=row['project']['screenshots'][0]['src']
        check('source canonical managed PNGs include public and private draft',rt.binary_image(images['restore-public-proof'])[0]==200 and rt.binary_image(images['restore-draft-proof'])[0]==404)
        status,_,_=mutate(auth,'DELETE','projects/restore-retired-proof',{'expectedVersion':2})
        check('source pending retirement and delete audit retained',status==204 and len(auth.call('GET','projects/restore-retired-proof/audit')[1])==3)
        status,category,_=mutate(auth,'PUT','taxonomy/categories/'+category['id'],{'label':'Backup renamed category','sortOrder':100,'expectedVersion':1});backup.require(status==200,'Source category rename must succeed')
        status,tech,_=mutate(auth,'PUT','taxonomy/technologies/'+tech['id'],{'label':'Backup renamed technology','expectedVersion':1});backup.require(status==200,'Source technology rename must succeed')
        for slug in ['restore-public-proof','restore-draft-proof']:saved[slug]=auth.call('GET','projects/'+slug)[1]
        check('source taxonomy rename records parent version4 and complete snapshots',all(row['version']==4 for row in saved.values() if row['project']['slug']!='restore-retired-proof') and len(auth.call('GET','projects/restore-public-proof/audit')[1])==4)
        status,_,_=mutate(auth,'POST','password',{'expectedVersion':1,'currentPassword':'wrong-current','newPassword':'private-fixture-new-password'})
        check('source wrong proof consumes durable password quota without changing account',status==400)
        original_cookie=auth.cookie
        previous_roles=backup.roles_state(d,values)
        reject('running API backup is refused without touching roles',lambda:backup.backup(source,output/'running-refused',19186))
        check('running refusal leaves no output or role changes',not (output/'running-refused').exists() and backup.roles_state(d,values)==previous_roles)
        stop(first)
        before=backup.database_state(d,values);grants=backup.permissions(d,values)
        backup.write_json(output/'source-permissions.json',backup.permission_inventory(d,values))
        check('source sessions and both quota namespaces exist before backup',before['spring_session']['rows']>0 and before['spring_session_attributes']['rows']>0 and before['admin_login_bucket']['rows']>0)
        # A deterministic active DB client proves refusal after NOLOGIN and restoration in finally.
        log=(output/'own-client.log').open('xb');os.chmod(log.name,0o600);handles.append(log)
        client=subprocess.Popen(pg.DOCKER+['exec',pg.container(values),'psql','-U','portfolio_bootstrap','-d',values['PORTFOLIO_DATABASE'],'-XAt','-c','SELECT pg_sleep(5);'],env=pg.environment(),stdout=log,stderr=subprocess.STDOUT);processes.append(client)
        deadline=time.monotonic()+3
        while time.monotonic()<deadline:
            if pg.sql(d,values,"SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND query='SELECT pg_sleep(5);' AND pid<>pg_backend_pid();")=='1':break
            time.sleep(.05)
        else: raise RuntimeError('Own active client did not appear')
        reject('connected DB client prevents backup without terminating it',lambda:backup.backup(source,output/'connected-refused',19186))
        check('failed quiescence restores all original LOGIN flags and removes journal',backup.roles_state(d,values)==previous_roles and not (source/'.backup-access.json').exists())
        backup.require(client.wait(timeout=8)==0,'Own read-only client must exit normally')
        asset_name=images['restore-public-proof'].rsplit('/',1)[1]+'.png';asset=source/'media/objects'/asset_name;hidden=output/'temporarily-hidden.png'
        asset.rename(hidden)
        try:reject('missing active image prevents coordinated backup',lambda:backup.backup(source,output/'missing-refused',19186))
        finally:hidden.rename(asset)
        check('image failure restores source access and never marks backup complete',backup.roles_state(d,values)==previous_roles and not (output/'missing-refused/COMPLETE').exists())
        asset.rename(hidden);asset.symlink_to(hidden)
        try:reject('symbolic image file is refused before copying',lambda:backup.backup(source,output/'symlink-refused',19186))
        finally:asset.unlink();hidden.rename(asset)
        check('symbolic refusal preserves all source tables and grants',backup.database_state(d,values)==before and backup.permissions(d,values)==grants)
        backup.write_json(source/'.backup-access.json',{'scope':values['COMPOSE_PROJECT_NAME'],'roles':previous_roles});backup.set_access(d,values,dict.fromkeys(backup.ROLES,False));backup.recover_access(source)
        check('simulated interrupted access journal can safely recover original flags',backup.roles_state(d,values)==previous_roles and not (source/'.backup-access.json').exists())
        # Canonical old orphan has no DB reference. Preserve mtime so normal GC stays valid after restore.
        orphan=source/'media/objects'/(str(uuid.uuid4())+'.png');orphan.write_bytes(fixture.read_bytes());orphan.chmod(0o600);os.utime(orphan,(time.time()-90000,time.time()-90000))
        bundle=output/'backup';manifest=backup.backup(source,bundle,19186)
        check('completed backup verifies custom archive and all four managed/orphan files',backup.verify_backup(bundle)==manifest and len(manifest['objects'])==4 and manifest['dump']['bytes']>0)
        check('successful backup leaves source tables/grants/access unchanged',backup.database_state(d,values)==before and backup.permissions(d,values)==grants and backup.roles_state(d,values)==previous_roles)
        check('backup excludes session data but preserves identity/quota fingerprints',all(manifest['tables'][t]['rows']==0 for t in backup.SESSIONS) and manifest['tables']['admin_account']==before['admin_account'] and manifest['tables']['admin_login_bucket']==before['admin_login_bucket'])
        reject('restore into existing source scope is refused',lambda:backup.restore(bundle,source,25487))
        corrupt=clone_bundle('corrupt-dump');(corrupt/'database.dump').write_bytes(b'corrupt')
        reject('corrupt dump is rejected before creating any target',lambda:backup.restore(corrupt,output/'corrupt-target',25488));check('corrupt preflight creates no target directory',not (output/'corrupt-target').exists())
        corrupt=clone_bundle('corrupt-image');name=manifest['objects'][0]['name'];(corrupt/'objects'/name).write_bytes(b'corrupt')
        reject('corrupt image is rejected before target creation',lambda:backup.restore(corrupt,output/'image-target',25488));check('image preflight creates no target directory',not (output/'image-target').exists())
        # A resealed but invalid pg_dump archive must fail real pg_restore and quarantine a fresh target.
        invalid=clone_bundle('invalid-archive');dump=invalid/'database.dump';dump.write_bytes(b'PGDMP-invalid-fixture');m=json.loads((invalid/'manifest.json').read_text());m['dump']={'bytes':dump.stat().st_size,'sha256':backup.digest(dump)}
        (invalid/'manifest.json').unlink();backup.write_json(invalid/'manifest.json',m);(invalid/'COMPLETE').write_text(backup.digest(invalid/'manifest.json')+'\n')
        reject('invalid but resealed archive fails real pg_restore',lambda:backup.restore(invalid,bad_target,25489))
        bd,_,bv=pg.configuration(bad_target)
        check('failed restore target remains quarantined without ready marker or partially restored tables',not (bad_target/'RESTORE_READY.json').exists() and not any(backup.roles_state(bd,bv).values()) and pg.sql(bd,bv,"SELECT count(*) FROM pg_tables WHERE schemaname='public';")=='0')
        backup.restore(bundle,target,25488);td,te,tv=pg.configuration(target)
        check('fresh restored scope uses independent DB volume and new private connection passwords',tv['COMPOSE_PROJECT_NAME']!=values['COMPOSE_PROJECT_NAME'] and all(tv[k]!=values[k] for k in values if k.endswith('_PASSWORD')))
        check('restored data/audits/identities/quota/migrations match backup exactly',backup.database_state(td,tv)==manifest['tables'])
        check('restored table/column/schema/default/DB grants and role restrictions match',backup.permissions(td,tv)==grants)
        check('all copied image bytes, retired queue and orphan modification time match',backup.media_entries(target/'media')==manifest['objects'] and (target/'media/objects'/orphan.name).stat().st_mtime_ns==orphan.stat().st_mtime_ns)
        recovered=start(target,'api-restored')
        stale=rt.AuthClient();stale.cookie=original_cookie
        check('old backup-era session cookie cannot authenticate after restore',stale.call('GET','overview')[0]==401)
        restored=rt.AuthClient();check('restored ADMIN hash authenticates original account password',login(restored,administrator)==204)
        failed=rt.AuthClient();check('disabled identity is still disabled after restore',login(failed,viewer_account)==401)
        read=rt.AuthClient();check('enabled VIEWER hash/role authenticate after restore',login(read,{'PORTFOLIO_LOGIN_USERNAME':'restore_reader','PORTFOLIO_LOGIN_PASSWORD':viewer_account['PORTFOLIO_LOGIN_PASSWORD']})==204)
        status,_,_=mutate(read,'DELETE','projects/restore-public-proof',{'expectedVersion':4});check('restored VIEWER still cannot mutate catalog',status==403)
        check('restored public image serves exact canonical bytes and draft stays private',rt.binary_image(images['restore-public-proof'])==(200,asset.read_bytes(),'image/png') and rt.binary_image(images['restore-draft-proof'])[0]==404)
        check('restored managed versions and project/taxonomy/identity audits remain readable',all(restored.call('GET','projects/'+slug)[1]==saved[slug] for slug in ['restore-public-proof','restore-draft-proof']) and len(restored.call('GET','taxonomy/audit')[1])==4 and len(restored.call('GET','accounts/audit')[1])==2)
        status,_,_=mutate(restored,'DELETE','taxonomy/categories/'+category['id'],{'expectedVersion':2});check('restored reference protection still rejects taxonomy deletion',status==409)
        row=saved['restore-public-proof'];content=dict(row['project']);[content.pop(key,None) for key in ['coverImage','screenshots','localDemoUrl']];content['title']='Isolated restored edit'
        status,updated,_=mutate(restored,'PUT','projects/restore-public-proof',{'content':content,'published':True,'sortOrder':row['sortOrder'],'expectedVersion':4})
        check('restored writer can commit version5 and matching audit',status==200 and updated['version']==5 and len(restored.call('GET','projects/restore-public-proof/audit')[1])==5)
        status,cleanup,_=restored.call('POST','media/cleanup',csrf=restored.session()['csrf']['token'])
        check('restored pending retirement and old orphan remain reclaimable',status==200 and cleanup['retired']==1 and cleanup['orphaned']==1 and not (target/'media/objects'/orphan.name).exists())
        check('restored edits/cleanup never mutate source tables or source media',backup.database_state(d,values)==before and len(backup.media_entries(source/'media'))==4 and rt.get('projects/restore-public-proof')[1]['title']=='Isolated restored edit')
        stop(recovered)
        # Restart restored PostgreSQL/JAR and verify no reseed or stale-cookie resurrection.
        changed=backup.database_state(td,tv);pg.compose(te,'stop','postgres');pg.compose(te,'start','postgres');pg.sql_ready(td,tv)
        recovered=start(target,'api-restored-restart')
        check('restored same-volume restart keeps written rows/version/audit',backup.database_state(td,tv)==changed and rt.get('projects/restore-public-proof')[1]['title']=='Isolated restored edit')
        check('fresh post-restore session survives restart without resurrecting old session',restored.call('GET','overview')[0]==200 and stale.call('GET','overview')[0]==401)
        stop(recovered)
        resumed=start(source,'api-source-resumed')
        check('original source session still works after backup and isolated restore',auth.call('GET','overview')[0]==200 and rt.get('projects/restore-public-proof')[1]['title']=='restore-public-proof')
        stop(resumed)
        check('packaged artifact unchanged through backup and restore drill',hashlib.sha256((pg.ROOT/'backend/target/developer-portfolio-0.2.0.jar').read_bytes()).hexdigest()==result['artifactSha256'])
        result['backup']={'objects':len(manifest['objects']),'dumpBytes':manifest['dump']['bytes'],'dumpSha256':manifest['dump']['sha256'],'manifestSha256':backup.digest(bundle/'manifest.json'),'sessionsExcluded':manifest['sessionRowsExcluded'],'sourceAccessRestored':True,'retention':'private artifacts remain in own ignored work; not remote/offsite/encrypted'}
        result['passed']=len(result['checks']);result['outcome']='PASS'
    finally:
        for process in processes:stop(process)
        for handle in handles:handle.close()
        cleanup=[]
        for path in [source,target,bad_target]:
            if (path/'runtime.env').exists():
                _,e,v=pg.configuration(path);pg.compose(e,'down','-v')
                if (path/'media').exists():shutil.rmtree(path/'media')  # Only generated disposable fixture stores.
                cleanup.append(str(path.relative_to(pg.ROOT)))
        result['cleanup']={'scopes':cleanup,'ownProcessesStopped':all(p.poll() is not None for p in processes),'fixtureMediaRemoved':True}
        if result['outcome']!='PASS':result['outcome']='FAIL'
        backup.write_json(output/'result.json',result)
    print('Own backup/restore checks passed:',result['passed'])

if __name__=='__main__':main()
