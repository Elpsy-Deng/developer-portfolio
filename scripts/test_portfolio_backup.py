"""Artifact validation without a database; real SQL/HTTP is verified by the restore drill."""
import hashlib
import json
from pathlib import Path
import socket
import tempfile
import unittest
import portfolio_backup as backup

class ArtifactTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='backup-unit-', dir=backup.pg.ROOT / 'work')
        self.root = Path(self.temp.name)
        self.bundle = self.root / 'bundle'; self.bundle.mkdir(mode=0o700)
        (self.bundle / 'objects').mkdir(mode=0o700)
        dump = self.bundle / 'database.dump'; dump.write_bytes(b'PGDMP-own-synthetic-artifact'); dump.chmod(0o600)
        self.manifest = {'format':'portfolio-offline-v1','createdAt':'2026-10-13T00:00:00Z','sourceScope':'portfolio-0123456789ab',
                         'postgresMajor':17,'migrations':backup.migrations(),'roles':dict.fromkeys(backup.ROLES,True),
                         'tables':dict.fromkeys(backup.TABLES,{'rows':0,'sha256':hashlib.sha256(b'[]').hexdigest()}),
                         'permissionsSha256':'a'*64,'sessionRowsExcluded':dict.fromkeys(backup.SESSIONS,0),'objects':[],
                         'dump':{'bytes':dump.stat().st_size,'sha256':backup.digest(dump)}}
        self.seal()
    def tearDown(self): self.temp.cleanup()
    def seal(self):
        m = self.bundle / 'manifest.json'; m.write_text(json.dumps(self.manifest)); m.chmod(0o600)
        marker = self.bundle / 'COMPLETE'; marker.write_text(backup.digest(m)+'\n'); marker.chmod(0o600)
    def test_valid_inventory_and_dump_corruption(self):
        self.assertEqual(backup.verify_backup(self.bundle),self.manifest)
        (self.bundle/'database.dump').write_bytes(b'PGDMP-corrupted-same-size!!')
        with self.assertRaises(backup.BackupProblem): backup.verify_backup(self.bundle)
    def test_incomplete_seal_manifest_corruption_and_private_permissions(self):
        marker = self.bundle/'COMPLETE'; marker.unlink()
        with self.assertRaises(backup.BackupProblem): backup.verify_backup(self.bundle)
        self.seal(); (self.bundle/'manifest.json').write_text('{}')
        with self.assertRaises(backup.BackupProblem): backup.verify_backup(self.bundle)
        self.seal(); (self.bundle/'database.dump').chmod(0o644)
        with self.assertRaises(backup.BackupProblem): backup.verify_backup(self.bundle)
    def test_traversal_and_duplicate_names_even_with_resealed_manifest(self):
        self.manifest['objects']=[{'name':'../database.dump','bytes':0,'sha256':'a'*64}]; self.seal()
        with self.assertRaises(backup.BackupProblem): backup.verify_backup(self.bundle)
        name='12345678-1234-1234-1234-123456789abc.png'; path=self.bundle/'objects'/name; path.write_bytes(b'image');path.chmod(0o600)
        item={'name':name,'bytes':5,'sha256':backup.digest(path)}; self.manifest['objects']=[item,item];self.seal()
        with self.assertRaises(backup.BackupProblem): backup.verify_backup(self.bundle)
    def test_symlink_ancestry_objects_and_outside_work_are_refused(self):
        alias=self.root/'alias';alias.symlink_to(self.bundle,target_is_directory=True)
        with self.assertRaises(backup.BackupProblem): backup.verify_backup(alias)
        name='12345678-1234-1234-1234-123456789abc.png';path=self.bundle/'objects'/name;path.symlink_to(self.bundle/'database.dump')
        self.manifest['objects']=[{'name':name,'bytes':0,'sha256':'a'*64}];self.seal()
        with self.assertRaises(backup.BackupProblem): backup.verify_backup(self.bundle)
        with self.assertRaises(RuntimeError): backup.private_path('/tmp/portfolio-backup')
    def test_migration_drift_sessions_and_unknown_role_are_refused(self):
        original=self.manifest['migrations'];self.manifest['migrations']={};self.seal()
        with self.assertRaises(backup.BackupProblem): backup.verify_backup(self.bundle)
        self.manifest['migrations']=original;self.manifest['roles']['other_owner']=True;self.seal()
        with self.assertRaises(backup.BackupProblem): backup.verify_backup(self.bundle)
        self.manifest['roles']=dict.fromkeys(backup.ROLES,True);self.manifest['tables']=dict(self.manifest['tables']);self.manifest['tables']['spring_session']={'rows':1,'sha256':'a'*64};self.seal()
        with self.assertRaises(backup.BackupProblem): backup.verify_backup(self.bundle)
    def test_occupied_port_and_scope_lock_are_refused_without_stopping_listener(self):
        with socket.socket() as server:
            server.bind(('127.0.0.1',0));server.listen()
            with self.assertRaises(backup.BackupProblem): backup.port_unused(server.getsockname()[1])
            self.assertGreater(server.fileno(),0)
        with backup.scope_lock(self.root):
            with self.assertRaises(backup.BackupProblem):
                with backup.scope_lock(self.root): pass
    def test_referenced_missing_and_mismatched_asset_bytes_are_refused(self):
        name='12345678-1234-1234-1234-123456789abc.png'
        row={'name':name,'bytes':5,'sha256':'a'*64,'required':True,'referenced':True}
        with self.assertRaises(backup.BackupProblem): backup.check_assets([row],[])
        with self.assertRaises(backup.BackupProblem): backup.check_assets([row],[{'name':name,'bytes':5,'sha256':'b'*64}])
        backup.check_assets([{**row,'required':False,'referenced':False}],[])
        with self.assertRaises(backup.BackupProblem): backup.check_assets([{**row,'required':False}],[])

if __name__ == '__main__': unittest.main()
