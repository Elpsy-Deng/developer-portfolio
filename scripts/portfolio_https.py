#!/usr/bin/env python3
"""Create an explicitly owned TLS gateway scope; never provision DNS/ACME or publish it automatically."""
import argparse
import http.client
import http.cookiejar
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import socket
import ssl
import subprocess
import sys
import urllib.request
import portfolio_db as pg
import portfolio_deploy as deploy


def hostname(value):
    deploy.require(len(value)<=253 and re.fullmatch(r'[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?',value)
                   and all(re.fullmatch(r'[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?',p) for p in value.split('.')),
                   'A canonical lowercase DNS hostname is required')
    try: ipaddress.ip_address(value)
    except ValueError: return value
    raise RuntimeError('DNS hostname required, not an IP address')


def certificate(cert,key,host):
    cert=deploy.private(cert);key=deploy.private(key)
    deploy.require(cert.is_file() and key.is_file() and key.stat().st_uid==os.getuid() and not key.stat().st_mode & 0o077,
                   'Owned private regular TLS key and certificate required')
    context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version=ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(cert,key)  # Key pair, PEM and cryptographic defaults, without dumping key bytes.
    decoded=ssl._ssl._test_decode_cert(str(cert))
    deploy.require(('DNS',host) in decoded.get('subjectAltName',()),'Certificate SAN must explicitly include canonical hostname')
    result=subprocess.run(['openssl','x509','-in',str(cert),'-checkend','86400','-noout'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    deploy.require(result.returncode==0,'Certificate must remain valid for at least 24 hours')
    return cert,key


def nginx(settings,key):
    host=settings['hostname'];port=settings['httpsPort'];origin='https://'+host+(':'+str(port) if port!=443 else '')
    prefix=(pg.ROOT/'infra/nginx.conf').read_text().split('    server {',1)[0]
    locations='''
        location ^~ /api/ {
            proxy_pass http://api:19186;
            proxy_set_header Host HOST;
            proxy_set_header Forwarded '';
            proxy_set_header X-Forwarded-For '';
            proxy_set_header X-Forwarded-Host '';
            proxy_set_header X-Forwarded-Proto '';
            proxy_set_header X-Real-IP '';
            proxy_set_header X-Portfolio-Gateway KEY;
            proxy_set_header X-Portfolio-Peer $remote_addr;
            proxy_connect_timeout 2s;
            proxy_read_timeout 15s;
            proxy_send_timeout 15s;
            proxy_next_upstream off;
        }
        location /assets/ { try_files $uri =404; }
        location ~* \\.(png|jpe?g|svg|ico|gif|webp)$ { try_files $uri =404; }
        location /projects/ { try_files $uri /index.html; }
        location / { try_files $uri $uri/ /index.html; }
'''.replace('HOST',host+(':'+str(port) if port!=443 else '')).replace('KEY',key)
    return prefix+f'''    map $http_host $tls_host {{ default 0; "{host}" 1; "{host}:{port}" 1; }}
    map $http_host $http_host_ok {{ default 0; "{host}" 1; "{host}:{settings['httpPort']}" 1; "{host}:{port}" 1; }}
    server {{
        listen 8080;
        server_name {host};
        location = /healthz {{ access_log off; return 200 'gateway\\n'; }}
        location / {{ if ($http_host_ok = 0) {{ return 421; }} return 308 {origin}$request_uri; }}
    }}
    server {{
        listen 8443 ssl;
        server_name {host};
        if ($tls_host = 0) {{ return 421; }}
        ssl_certificate /run/secrets/tls.crt;
        ssl_certificate_key /run/secrets/tls.key;
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_session_tickets off;
        root /usr/share/nginx/html;
        absolute_redirect off;
        client_max_body_size 6m;
        add_header X-Content-Type-Options nosniff always;
        add_header Strict-Transport-Security "max-age=86400" always;
        add_header Referrer-Policy strict-origin-when-cross-origin always;
        location = /healthz {{ access_log off; return 200 'gateway TLS\\n'; }}
'''+locations+'    }\n}\n'


def initialize(args):
    host=hostname(args.hostname);cert,key=certificate(args.certificate,args.key,host)
    deploy.require(args.https_port not in (args.pg_port,args.gateway_port) and 1024<=args.https_port<=65535,
                   'Distinct nonprivileged HTTPS port required')
    with socket.socket() as s:s.bind((args.bind_address,args.https_port))
    if args.action=='init':deploy.initialize(args.directory,args.pg_port,args.gateway_port,False)
    d,_,v=deploy.configuration(args.directory)
    deploy.stopped(v)
    deploy.require(not (d/'.portfolio-https-v1').exists(),'Preserve existing TLS configuration')
    deploy.require(int(v['PORTFOLIO_GATEWAY_PORT'])==args.gateway_port and int(v['PORTFOLIO_PG_PORT'])==args.pg_port,'Existing scope ports must match')
    settings={'schema':1,'scope':v['COMPOSE_PROJECT_NAME'],'hostname':host,'httpsPort':args.https_port,
              'httpPort':args.gateway_port,'bindAddress':args.bind_address}
    for name,source in (('tls.crt',cert),('tls.key',key)):
        with (d/name).open('xb') as f:os.chmod(f.name,0o600);f.write(source.read_bytes())
    (d/'proxy.key').write_text(secrets.token_hex(32));(d/'proxy.key').chmod(0o600)
    # !override replaces only our gateway ports, never PG/API bindings.
    overlay=f'''services:
  gateway:
    user: '{os.getuid()}:{os.getgid()}'
    ports: !override
      - '{args.bind_address}:{args.gateway_port}:8080'
      - '{args.bind_address}:{args.https_port}:8443'
    volumes:
      - '{d}/https-nginx.conf:/etc/nginx/nginx.conf:ro'
    secrets:
      - {{source: tls_certificate, target: tls.crt}}
      - {{source: tls_key, target: tls.key}}
secrets:
  tls_certificate: {{file: '{d}/tls.crt'}}
  tls_key: {{file: '{d}/tls.key'}}
'''
    # Reject YAML-ambiguous paths before writing config; scopes already reject symlinks and external roots.
    deploy.require(re.fullmatch(r'[A-Za-z0-9/_.-]+',str(d)),'Simple owned scope path required')
    (d/'https-compose.yml').write_text(overlay);(d/'https-compose.yml').chmod(0o600)
    deploy.save(d/'.portfolio-https-v1',settings)
    runtime=d/'runtime.properties';text=runtime.read_text();runtime.write_text(text.replace('portfolio.auth.cookie-secure=false\n','portfolio.auth.cookie-secure=true\n'))
    print('TLS scope prepared; certificate/key remain private; no public service started')


def configure(d,e,v):
    p=deploy.private(d/'.portfolio-https-v1');settings=json.loads(p.read_text())
    deploy.require(settings['schema']==1 and settings['scope']==v['COMPOSE_PROJECT_NAME'],'Own TLS scope required')
    hostname(settings['hostname']);certificate(d/'tls.crt',d/'tls.key',settings['hostname'])
    ids=subprocess.check_output(pg.DOCKER+['network','ls','-q','--filter','label=com.docker.compose.project='+v['COMPOSE_PROJECT_NAME']],env=pg.environment(),text=True).split()
    deploy.require(len(ids)==1,'Exactly one private owned deployment network required')
    network=json.loads(subprocess.check_output(pg.DOCKER+['network','inspect',ids[0]],env=pg.environment(),text=True))[0]
    subnets=network['IPAM']['Config'];deploy.require(len(subnets)==1,'Single IPv4 owned deployment subnet required')
    subnet=ipaddress.ip_network(subnets[0]['Subnet']);deploy.require(subnet.version==4 and subnet.is_private and subnet.prefixlen>=16,'Narrow private deployment network required')
    key_path=deploy.private(d/'proxy.key');deploy.require(not key_path.stat().st_mode & 0o077,'Private proxy key required')
    key=key_path.read_text();deploy.require(re.fullmatch('[a-f0-9]{64}',key),'Invalid private gateway key')
    runtime=d/'runtime.properties';old=runtime.read_text()
    deploy.require('portfolio.auth.cookie-secure=true\n' in old,'TLS scope requires Secure cookies')
    lines=[line for line in old.splitlines() if not line.startswith('portfolio.proxy.')]
    runtime.write_text('\n'.join(lines)+'\nportfolio.proxy.network='+str(subnet)+'\nportfolio.proxy.key='+key+'\n')
    config=d/'https-nginx.conf';config.write_text(nginx(settings,key));config.chmod(0o600)


def opener(settings,cafile=None,cookies=None):
    """Connect only to our loopback mapping, with real SNI/hostname/CA validation."""
    context=ssl.create_default_context(cafile=cafile);context.minimum_version=ssl.TLSVersion.TLSv1_2
    class LocalConnection(http.client.HTTPSConnection):
        def connect(self):
            self.sock=socket.create_connection(('127.0.0.1',settings['httpsPort']),self.timeout)
            self.sock=context.wrap_socket(self.sock,server_hostname=self.host)
    class LocalHandler(urllib.request.HTTPSHandler):
        def https_open(self,request):
            deploy.require(request.host==settings['hostname']+':'+str(settings['httpsPort']),'Own TLS origin required')
            return self.do_open(LocalConnection,request,context=context)
    handlers=[urllib.request.ProxyHandler({}),LocalHandler(context=context)]
    if cookies is not None:handlers.append(urllib.request.HTTPCookieProcessor(cookies))
    return urllib.request.build_opener(*handlers)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['init','enable'])
    p.add_argument('--directory',required=True);p.add_argument('--hostname',required=True)
    p.add_argument('--certificate',required=True);p.add_argument('--key',required=True)
    p.add_argument('--pg-port',type=int,default=25490);p.add_argument('--gateway-port',type=int,default=15190)
    p.add_argument('--https-port',type=int,default=15191);p.add_argument('--bind-address',choices=['127.0.0.1','0.0.0.0'],default='127.0.0.1')
    args=p.parse_args()
    if args.action=='enable':
        d,_,_=deploy.configuration(args.directory)
        with deploy.lock(d):initialize(args)
    else:initialize(args)

if __name__=='__main__':
    try:main()
    except Exception as error:
        print('TLS preparation refused: '+(str(error) if type(error) is RuntimeError else type(error).__name__));sys.exit(1)
