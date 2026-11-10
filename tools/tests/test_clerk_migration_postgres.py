"""Opt-in tests against a disposable PostgreSQL container; no ports, host mounts or Clerk calls."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
import uuid

from test_clerk_migration import m, FakeClerk, TARGET, HASH


class DockerPostgres(m.Postgres):
    def __init__(self, container):
        self.container = container

    def query(self, sql):
        result = subprocess.run(['docker','exec','-i',self.container,'psql','-U','postgres','-X','-w','-qAt','-v','ON_ERROR_STOP=1'],
                                input=sql,text=True,capture_output=True,timeout=30)
        if result.returncode:
            raise m.MigrationError('Disposable PostgreSQL rejected the operation.')
        return result.stdout.strip()


@unittest.skipUnless(os.environ.get('CLERK_MIGRATION_POSTGRES_TEST') == '1', 'Set CLERK_MIGRATION_POSTGRES_TEST=1 for an isolated Docker database')
class PostgresIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container = 'bible-clerk-migration-test-' + uuid.uuid4().hex[:12]
        result = subprocess.run(['docker','run','--rm','--detach','--network','none','--name',cls.container,
            '-e','POSTGRES_PASSWORD=synthetic-fixture-only','postgres:16-alpine'],capture_output=True,text=True,timeout=30)
        if result.returncode:
            raise RuntimeError('Unable to start the disposable PostgreSQL test container.')
        cls.addClassCleanup(lambda: subprocess.run(['docker','stop','--time','1',cls.container],capture_output=True,timeout=15))
        for _ in range(60):
            ready = subprocess.run(['docker','exec',cls.container,'pg_isready','-U','postgres'],capture_output=True,timeout=5)
            if ready.returncode == 0:
                break
            time.sleep(0.2)
        else:
            raise RuntimeError('Disposable PostgreSQL did not become ready.')
        cls.db = DockerPostgres(cls.container)

    def setUp(self):
        self.db.query('''DROP SCHEMA public CASCADE; CREATE SCHEMA public;
          CREATE TABLE app_user(id BIGINT PRIMARY KEY,email TEXT,password TEXT,firstname TEXT,lastname TEXT,church TEXT,
            clerk_user_id VARCHAR(255) UNIQUE,clerk_email_verified_at TIMESTAMP,profile_completed_at TIMESTAMP);
          CREATE TABLE clerk_webhook_state(clerk_user_id VARCHAR(255) PRIMARY KEY,updated_at_ms BIGINT NOT NULL DEFAULT 0,deleted BOOLEAN NOT NULL DEFAULT FALSE);
          CREATE TABLE game(id BIGINT PRIMARY KEY,user_id BIGINT REFERENCES app_user(id),stars INTEGER);
          CREATE TABLE read(id BIGINT PRIMARY KEY,user_id BIGINT REFERENCES app_user(id));
          CREATE TABLE review(id BIGINT PRIMARY KEY,user_id BIGINT REFERENCES app_user(id),stars INTEGER);
        ''')
        self.db.query("INSERT INTO app_user(id,email,password,firstname,lastname,church) VALUES (7,'fixture@example.com','" + HASH + "','Élodie','O’Connor-李','Legacy church'); INSERT INTO game VALUES(1,7,5); INSERT INTO read VALUES(1,7); INSERT INTO review VALUES(1,7,4);")

    def test_source_projection_and_database_identity(self):
        rows = self.db.read()
        self.assertEqual('7',rows[0]['id'])
        self.assertEqual(HASH,rows[0]['password'])
        self.assertNotIn('church',rows[0])
        self.assertEqual(32,len(rows[0]['row_token']))
        self.assertEqual('postgres',self.db.identity()['database'])

    def test_full_apply_and_resume_preserve_credentials_church_and_history(self):
        rows = m.source_rows(self.db.read())
        before = self.db.query("SELECT row_to_json(u) FROM app_user u;")
        client = FakeClerk()
        plan = m.make_plan(rows,TARGET,client)
        plan['source_database'] = self.db.identity()
        with tempfile.TemporaryDirectory() as temp:
            journal = Path(temp)/'journal.json'
            m.apply_plan(plan,rows,client,self.db,journal)
            m.apply_plan(plan,m.source_rows(self.db.read()),client,self.db,journal)
        self.assertEqual(1,len(client.created))
        after = json.loads(self.db.query('SELECT row_to_json(u) FROM app_user u;'))
        before = json.loads(before)
        self.assertEqual('user_fixture0',after.pop('clerk_user_id'))
        before.pop('clerk_user_id')
        self.assertEqual(before,after)
        self.assertEqual('1|5',self.db.query('SELECT count(*),sum(stars) FROM game;'))
        self.assertEqual('1',self.db.query('SELECT count(*) FROM read;'))
        self.assertEqual('1|4',self.db.query('SELECT count(*),sum(stars) FROM review;'))

    def test_changed_password_rolls_back_mapping_and_guard_insert(self):
        source = m.source_rows(self.db.read())[0]
        self.db.query("UPDATE app_user SET password='changed-while-running';")
        with self.assertRaises(m.MigrationError): self.db.link(source,'user_changed')
        self.assertEqual('',self.db.query('SELECT clerk_user_id FROM app_user;'))
        self.assertEqual('0',self.db.query('SELECT count(*) FROM clerk_webhook_state;'))
        self.assertEqual('changed-while-running',self.db.query('SELECT password FROM app_user;'))

    def test_deleted_identity_guard_prevents_mapping(self):
        source = m.source_rows(self.db.read())[0]
        self.db.query("INSERT INTO clerk_webhook_state(clerk_user_id,deleted) VALUES('user_deleted',true);")
        with self.assertRaises(m.MigrationError): self.db.link(source,'user_deleted')
        self.assertEqual('',self.db.query('SELECT clerk_user_id FROM app_user;'))
        self.assertEqual('t',self.db.query("SELECT deleted FROM clerk_webhook_state WHERE clerk_user_id='user_deleted';"))

    def test_existing_mapping_or_verification_is_never_overwritten(self):
        source = m.source_rows(self.db.read())[0]
        self.db.query("UPDATE app_user SET clerk_user_id='user_existing';")
        with self.assertRaises(m.MigrationError): self.db.link(source,'user_other')
        self.assertEqual('user_existing',self.db.query('SELECT clerk_user_id FROM app_user;'))
        self.db.query('UPDATE app_user SET clerk_user_id=NULL,clerk_email_verified_at=now();')
        with self.assertRaises(m.MigrationError): self.db.link(source,'user_other')
        self.assertEqual('0',self.db.query('SELECT count(*) FROM clerk_webhook_state;'))


if __name__ == '__main__':
    unittest.main()
