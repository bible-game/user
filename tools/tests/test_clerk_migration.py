import copy
import importlib.util
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

TOOL = Path(__file__).resolve().parents[1] / 'clerk_migration.py'
spec = importlib.util.spec_from_file_location('clerk_migration', TOOL)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
TARGET = {'instance_id': 'ins_fixture', 'environment': 'development'}
DB_ID = {'database': 'fixture', 'server': '127.0.0.1', 'port': 55432}
HASH = '$2a$10$' + 'a' * 53


def row(identity='7', **changes):
    return dict(id=identity, email='fixture@example.com', password=HASH, firstname='Élodie',
                lastname='O’Connor-李', clerk_user_id=None, row_token='a' * 32, **changes)


class FakeClerk:
    def __init__(self, users=None):
        self.users = copy.deepcopy(users or [])
        self.created = []
        self.checked = []
        self.fail_after_create = False
        self.verify_by_default = False

    def verify_target(self, target):
        if target != TARGET:
            raise m.MigrationError('Wrong target.')
        self.checked.append(target)

    def search(self, field, value):
        if field == 'external_id':
            return [u for u in self.users if u['external_id'] == value]
        return [u for u in self.users if any(e['email_address'].casefold() == value.casefold() for e in u['email_addresses'])]

    def get(self, cid):
        return next(u for u in self.users if u['id'] == cid)

    def create(self, payload):
        self.created.append(copy.deepcopy(payload))
        user = {'id': 'user_fixture' + str(len(self.users)), 'external_id': payload['external_id'],
                'primary_email_address_id': 'idn_primary', 'password_enabled': True,
                'email_addresses': [{'id': 'idn_primary', 'email_address': payload['email_address'][0],
                                     'verification': {'status': 'verified' if self.verify_by_default else 'unverified'}}],
                'private_metadata': copy.deepcopy(payload.get('private_metadata', {}))}
        self.users.append(user)
        if self.fail_after_create:
            raise m.MigrationError('Synthetic lost response.')
        return user


class FakeDatabase:
    def __init__(self, rows):
        self.rows = copy.deepcopy(rows)
        self.fail = False
        self.links = []

    def identity(self):
        return DB_ID

    def link(self, source, cid):
        if self.fail:
            raise m.MigrationError('Synthetic DB mapping failure.')
        self.links.append((source['id'], cid))
        next(r for r in self.rows if r['id'] == source['id'])['clerk_user_id'] = cid


class MigrationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.journal = self.directory / 'journal.json'
        self.rows = m.source_rows([row()])
        self.client = FakeClerk()
        self.db = FakeDatabase(self.rows)
        self.plan = m.make_plan(self.rows, TARGET, self.client)
        self.plan['source_database'] = DB_ID

    def apply(self, rows=None, client=None, plan=None):
        return m.apply_plan(plan or self.plan, self.db.rows if rows is None else rows,
                            client or self.client, self.db, self.journal)

    def test_offline_plan_is_read_only_and_contains_no_personal_details_or_hashes(self):
        fixture = self.directory / 'source.json'
        fixture.write_text(json.dumps([dict(row(), church='Private church', private_metadata={'secret': 'not copied'})]))
        report = self.directory / 'plan.json'
        result = subprocess.run([sys.executable, str(TOOL), 'plan', '--input', str(fixture),
            '--instance-id', 'ins_fixture', '--environment', 'development', '--report', str(report)],
            text=True, capture_output=True, env={**os.environ, 'CLERK_SECRET_KEY': 'must_not_be_used'})
        self.assertEqual(0, result.returncode, result.stderr)
        output = report.read_text() + result.stdout + result.stderr
        for value in [HASH, 'fixture@example.com', 'Élodie', 'Private church', 'not copied', 'must_not_be_used']:
            self.assertNotIn(value, output)
        self.assertFalse(json.loads(report.read_text())['target_checked'])
        self.assertEqual(0o600, stat.S_IMODE(report.stat().st_mode))
        self.assertEqual([fixture, report], sorted(self.directory.iterdir(), key=lambda p: p.name, reverse=True))

    def test_duplicate_ids_emails_and_links_block_preflight(self):
        rows = m.source_rows([row(), dict(row(), id='8', email='FIXTURE@example.com')])
        self.assertEqual(2, m.make_plan(rows, TARGET)['summary']['blocked'])
        self.assertIn('duplicate_email_case_insensitive', m.source_issues(rows)[0]['errors'])
        duplicate = m.source_rows([row(), row()])
        self.assertIn('duplicate_user_id', m.source_issues(duplicate)[0]['errors'])
        links = m.source_rows([dict(row(), clerk_user_id='user_same'), dict(row(), id='8', email='other@example.com', clerk_user_id='user_same')])
        self.assertIn('duplicate_clerk_link', m.source_issues(links)[0]['errors'])

    def test_bcrypt_versions_and_unicode_names_are_preserved(self):
        for version in ['2a', '2b', '2y']:
            rows = m.source_rows([dict(row(), password=HASH.replace('2a', version))])
            self.assertEqual([], m.source_issues(rows)[0]['errors'])
        for password in ['', 'plaintext', '{bcrypt}' + HASH, HASH.replace('10$', '99$')]:
            self.assertIn('unsupported_password_hash', m.source_issues(m.source_rows([dict(row(), password=password)]))[0]['errors'])
        issue = m.source_issues(m.source_rows([dict(row(), firstname='', lastname=' ')]))[0]
        self.assertFalse(issue['errors'])
        self.assertEqual(2, len(issue['warnings']))

    def test_invalid_id_and_email_never_reach_remote_preflight(self):
        for identity in [True, -1, '007', '1; DROP TABLE app_user', 2**63]:
            with self.assertRaises(m.MigrationError):
                m.source_rows([dict(row(), id=identity)])
        for email in ['not an email', ' fixture@example.com', 'a@b']:
            issue = m.source_issues(m.source_rows([dict(row(), email=email)]))[0]
            self.assertIn('invalid_email', issue['errors'])

    def test_payload_has_allowlisted_fields_reserved_email_and_original_bcrypt(self):
        source = m.source_rows([dict(row(), church='Secret', public_metadata={'unsafe': True})])[0]
        payload = m.payload_for(source)
        self.assertEqual({'external_id','email_address','email_address_identification_status','password_digest','password_hasher','first_name','last_name','private_metadata'}, set(payload))
        self.assertEqual(['reserved'], payload['email_address_identification_status'])
        self.assertEqual(HASH, payload['password_digest'])
        self.assertEqual('bcrypt', payload['password_hasher'])
        self.assertEqual('7', payload['external_id'])
        self.assertEqual('O’Connor-李', payload['last_name'])
        self.assertNotIn('Secret', json.dumps(payload))
        self.assertNotIn('skip_restriction_checks', payload)
        self.assertNotIn('skip_legal_checks', payload)

    def test_source_changes_and_wrong_database_abort_before_mutation(self):
        for key, value in [('email','changed@example.com'),('password',HASH.replace('2a','2b')),('firstname','Changed'),('lastname','Changed'),('id','9')]:
            changed = copy.deepcopy(self.rows)
            changed[0][key] = value
            with self.assertRaises(m.MigrationError): self.apply(rows=changed)
        with patch.object(self.db, 'identity', return_value={'database':'wrong'}):
            with self.assertRaises(m.MigrationError): self.apply()
        self.assertFalse(self.client.created)
        self.assertFalse(self.db.links)

    def test_offline_or_blocked_plan_cannot_apply(self):
        for plan in [m.make_plan(self.rows,TARGET), dict(self.plan, rows=[{'errors':['blocked']}])]:
            with self.assertRaises(m.MigrationError): self.apply(plan=plan)
        self.assertFalse(self.client.created)
        self.assertFalse(self.db.links)

    def test_import_and_resume_do_not_duplicate_users_or_rewrite_other_fields(self):
        self.apply()
        original_password = self.db.rows[0]['password']
        self.apply()
        self.assertEqual(1, len(self.client.created))
        self.assertEqual(1, len(self.db.links))
        self.assertEqual(original_password, self.db.rows[0]['password'])
        self.assertEqual('7', self.db.rows[0]['id'])
        journal = json.loads(self.journal.read_text())
        self.assertEqual('linked', journal['mappings']['7']['status'])
        self.assertNotIn(HASH, self.journal.read_text())
        self.assertEqual(0o600, stat.S_IMODE(self.journal.stat().st_mode))

    def test_database_failure_records_created_identity_and_rerun_recovers(self):
        self.db.fail = True
        with self.assertRaises(m.MigrationError): self.apply()
        self.assertEqual('created_pending_link', json.loads(self.journal.read_text())['mappings']['7']['status'])
        self.db.fail = False
        self.apply()
        self.assertEqual(1, len(self.client.created))
        self.assertEqual(1, len(self.db.links))

    def test_lost_create_response_is_reconciled_by_external_id_and_provenance(self):
        self.client.fail_after_create = True
        with self.assertRaises(m.MigrationError): self.apply()
        self.assertEqual('creating', json.loads(self.journal.read_text())['mappings']['7']['status'])
        self.client.fail_after_create = False
        self.apply()
        self.assertEqual(1, len(self.client.created))
        self.assertEqual(1, len(self.db.links))

    def test_uncertain_create_without_remote_match_never_automatically_recreates(self):
        m.private_json(self.journal, {'target':TARGET, 'source_digest':self.plan['source_digest'], 'mappings':{'7':{'status':'creating'}}})
        with self.assertRaises(m.MigrationError): self.apply()
        self.assertFalse(self.client.created)

    def test_deleted_created_account_or_changed_journal_mapping_is_not_recreated(self):
        self.db.fail = True
        with self.assertRaises(m.MigrationError): self.apply()
        self.db.fail = False
        self.client.users.clear()
        with self.assertRaises(m.MigrationError): self.apply()
        self.assertEqual(1, len(self.client.created))
        self.assertFalse(self.db.links)

    def test_matching_email_without_explicit_mapping_is_a_blocker(self):
        user = self.client.create(m.payload_for(self.rows[0]))
        user['external_id'] = 'someone_else'
        plan = m.make_plan(self.rows,TARGET,self.client)
        self.assertEqual(1,plan['summary']['blocked'])
        with self.assertRaises(m.MigrationError): self.apply()
        self.assertFalse(self.db.links)

    def test_external_id_without_matching_source_provenance_is_blocked(self):
        user = self.client.create(m.payload_for(self.rows[0]))
        user['private_metadata'] = {}
        self.assertEqual(1,m.make_plan(self.rows,TARGET,self.client)['summary']['blocked'])
        with self.assertRaises(m.MigrationError): self.apply()
        self.assertFalse(self.db.links)

    def test_auto_verified_email_response_is_not_linked_even_on_resume(self):
        self.client.verify_by_default = True
        with self.assertRaises(m.MigrationError): self.apply()
        with self.assertRaises(m.MigrationError): self.apply()
        self.assertEqual(1,len(self.client.created))
        self.assertFalse(self.db.links)

    def test_wrong_target_or_resume_journal_is_blocked(self):
        plan = dict(self.plan,target={'instance_id':'ins_wrong','environment':'development'})
        with self.assertRaises(m.MigrationError): self.apply(plan=plan)
        m.private_json(self.journal, {'target': {'instance_id':'ins_other'}, 'source_digest':self.plan['source_digest']})
        with self.assertRaises(m.MigrationError): self.apply()
        self.assertFalse(self.client.created)
        self.assertFalse(self.db.links)

    def test_postgres_write_is_guarded_and_contains_no_profile_or_credentials(self):
        database = m.Postgres()
        with patch.object(database,'query') as query:
            database.link(self.rows[0], 'user_fixture')
        sql = query.call_args.args[0]
        for value in [HASH,self.rows[0]['email'],self.rows[0]['firstname'],'church',' SET password']:
            self.assertNotIn(value,sql)
        for value in ['clerk_email_verified_at IS NULL','clerk_user_id IS NULL','deleted = FALSE FOR UPDATE','ROW_COUNT','md5(json_build_array','SET clerk_user_id']:
            self.assertIn(value,sql)
        self.assertNotIn('church',m.SOURCE_SQL)
        self.assertIn('READ ONLY',m.SOURCE_SQL)
        self.assertNotIn('SELECT *',m.SOURCE_SQL)
        with self.assertRaises(m.MigrationError): database.link(self.rows[0], "user_bad'; DROP TABLE app_user;")

    def test_file_no_clobber_symlinks_and_parallel_runs_are_rejected(self):
        path = self.directory/'report.json'
        m.private_json(path, {'safe':True}, exclusive=True)
        with self.assertRaises(m.MigrationError): m.private_json(path, {'unsafe':True}, exclusive=True)
        symlink = self.directory/'link.json'
        symlink.symlink_to(path)
        with self.assertRaises(m.MigrationError): m.private_json(symlink, {})
        with m.journal_lock(self.journal):
            with self.assertRaises(m.MigrationError):
                with m.journal_lock(self.journal): pass
        self.assertEqual({'safe':True},json.loads(path.read_text()))

    def test_psql_errors_do_not_disclose_stdout_stderr_or_credentials(self):
        with patch.object(m.subprocess,'run',return_value=subprocess.CompletedProcess([],1,HASH,'password=secret')):
            with self.assertRaises(m.MigrationError) as context: m.Postgres().read()
        self.assertNotIn(HASH,str(context.exception))
        self.assertNotIn('secret',str(context.exception))

    def test_http_pins_version_origin_and_never_blindly_retries_post(self):
        client = m.Clerk('sk_test_synthetic')
        error = m.urllib.error.HTTPError('https://api.clerk.com/v1/users',503,'sensitive body',{},None)
        with patch.object(client.opener,'open',side_effect=error) as call:
            with self.assertRaises(m.MigrationError) as context: client.create(m.payload_for(self.rows[0]))
        self.assertEqual(1,call.call_count)
        request = call.call_args.args[0]
        self.assertEqual('https://api.clerk.com/v1/users',request.full_url)
        self.assertEqual(m.API_VERSION,request.get_header('Clerk-api-version'))
        self.assertNotIn('sensitive body',str(context.exception))
        self.assertNotIn('sk_test_synthetic',str(context.exception))

    def test_instance_and_environment_must_match_actual_api_response(self):
        client = m.Clerk('sk_test_synthetic')
        for instance in [{'id':'ins_other','environment_type':'development'}, {'id':'ins_fixture','environment_type':'production'}]:
            with patch.object(client,'request',return_value=instance):
                with self.assertRaises(m.MigrationError): client.verify_target(TARGET)

    def test_redirection_and_long_retry_after_are_not_followed(self):
        self.assertIsNone(m.NoRedirect().redirect_request(None,None,302,'',{},'https://untrusted.example'))
        client = m.Clerk('sk_test_synthetic')
        error = m.urllib.error.HTTPError('https://api.clerk.com/v1/users',429,'',{'Retry-After':'100'},None)
        with patch.object(client.opener,'open',side_effect=error) as call, patch.object(m.time,'sleep') as sleep:
            with self.assertRaises(m.MigrationError): client.search('external_id','7')
        self.assertEqual(1,call.call_count)
        sleep.assert_not_called()


if __name__ == '__main__':
    unittest.main()
