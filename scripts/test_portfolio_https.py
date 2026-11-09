import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import portfolio_https as tls


class HttpsTests(unittest.TestCase):
    def test_hostname_cannot_inject_config_or_redirect(self):
        for value in ('localhost;return 200','EXAMPLE.com','example.com/path','a..b','-bad.test','192.0.2.1','a\nserver'):
            with self.assertRaises(RuntimeError):tls.hostname(value)
        self.assertEqual(tls.hostname('portfolio.example.com'),'portfolio.example.com')

    def test_render_pins_host_origin_and_overwrites_untrusted_claims(self):
        settings={'hostname':'portfolio.example.com','httpsPort':15443,'httpPort':15080}
        config=tls.nginx(settings,'a'*64)
        self.assertIn('return 308 https://portfolio.example.com:15443$request_uri;',config)
        self.assertNotIn('https://$host',config)
        self.assertIn("proxy_set_header Forwarded '';",config)
        self.assertIn('proxy_set_header X-Portfolio-Peer $remote_addr;',config)
        self.assertIn('ssl_protocols TLSv1.2 TLSv1.3;',config)
        self.assertIn('return 421;',config)

    def test_certificate_rejects_wrong_san_mismatched_key_and_public_key_file(self):
        with tempfile.TemporaryDirectory(dir=tls.pg.ROOT/'work') as tmp:
            d=Path(tmp);key=d/'key.pem';cert=d/'cert.pem'
            subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','2','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost','-keyout',str(key),'-out',str(cert)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            self.assertEqual(tls.certificate(cert,key,'localhost'),(cert,key))
            with self.assertRaises(RuntimeError):tls.certificate(cert,key,'wrong.example')
            other=d/'other.pem'
            subprocess.run(['openssl','genrsa','-out',str(other),'2048'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            other.chmod(0o600)
            with self.assertRaises(tls.ssl.SSLError):tls.certificate(cert,other,'localhost')
            key.chmod(0o644)
            with self.assertRaises(RuntimeError):tls.certificate(cert,key,'localhost')

    def test_tls_opener_refuses_other_destinations_before_network(self):
        opener=tls.opener({'hostname':'localhost','httpsPort':15191})
        with self.assertRaises(RuntimeError):opener.open('https://example.com:15191/api/admin/session')


if __name__=='__main__':unittest.main()
