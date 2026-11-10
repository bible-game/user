#!/usr/bin/env python3
"""Bible Game -> Clerk migration. Planning is read-only; importing is explicit and resumable."""
import argparse
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

API_VERSION = '2026-08-20'
FIELDS = ('id', 'email', 'password', 'firstname', 'lastname', 'clerk_user_id')
ID = re.compile(r'user_[A-Za-z0-9]+\Z')
BCRYPT = re.compile(r'\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}\Z')
# Deliberately no church, metadata, history or verification claims in the source projection.
SOURCE_SQL = """BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL search_path = pg_catalog, public;
SELECT COALESCE(json_agg(s), '[]'::json) FROM (
 SELECT id::text AS id, email, password, COALESCE(firstname, '') AS firstname,
 COALESCE(lastname, '') AS lastname, clerk_user_id,
 md5(json_build_array(id::text,email,password,firstname,lastname)::text) AS row_token
 FROM app_user ORDER BY id
) s;
COMMIT;"""


class MigrationError(Exception):
    """Only static, non-sensitive messages may reach the CLI."""


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=True, separators=(',', ':'))


def digest(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def private_json(path, value, *, exclusive=False):
    path = Path(path)
    if path.is_symlink():
        raise MigrationError('Refusing a symlink for migration output.')
    if exclusive and path.exists():
        raise MigrationError('Output already exists; choose a fresh report path.')
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix='.clerk-migration-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            json.dump(value, stream, indent=2, ensure_ascii=True)
            stream.write('\n')
            stream.flush()
            os.fsync(stream.fileno())
        if exclusive:
            os.link(tmp, path)  # Atomic no-clobber publication.
        else:
            os.replace(tmp, path)
        directory = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)


def read_json(path):
    try:
        with open(path, 'r') as stream:
            return json.load(stream)
    except (OSError, ValueError):
        raise MigrationError('Unable to read migration JSON; no input contents have been logged.') from None


class Postgres:
    """Uses libpq's host-supplied PG* variables; never opens environment files or prints psql errors."""
    def query(self, sql):
        try:
            result = subprocess.run(['psql', '-X', '-w', '-qAt', '-v', 'ON_ERROR_STOP=1'],
                                    input=sql, text=True, capture_output=True, timeout=60, check=False)
        except (OSError, subprocess.TimeoutExpired):
            raise MigrationError('PostgreSQL command unavailable or timed out; inspect locally and rerun.') from None
        if result.returncode:
            raise MigrationError('PostgreSQL rejected the operation; stop and review the source/schema locally.')
        return result.stdout.strip()

    def read(self):
        try:
            return json.loads(self.query(SOURCE_SQL))
        except ValueError:
            raise MigrationError('Unexpected PostgreSQL export format.') from None

    def identity(self):
        try:
            return json.loads(self.query("SELECT json_build_object('database',current_database(),'server',inet_server_addr()::text,'port',inet_server_port());"))
        except ValueError:
            raise MigrationError('Unexpected PostgreSQL identity response.') from None

    def link(self, row, clerk_id):
        if not ID.fullmatch(clerk_id) or not re.fullmatch(r'[0-9a-f]{32}', row.get('row_token', '')):
            raise MigrationError('Invalid mapping or missing database concurrency token.')
        # Values interpolated here are strictly validated identifiers, integers or hex tokens only.
        # No email, name or password digest is embedded in write statements/audit logs.
        sql = f"""BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL search_path = pg_catalog, public;
INSERT INTO clerk_webhook_state (clerk_user_id) VALUES ('{clerk_id}') ON CONFLICT DO NOTHING;
DO $migration$
DECLARE changed integer;
BEGIN
 PERFORM 1 FROM clerk_webhook_state WHERE clerk_user_id = '{clerk_id}' AND deleted = FALSE FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Deleted identity cannot be linked'; END IF;
 UPDATE app_user SET clerk_user_id = '{clerk_id}'
 WHERE id = {int(row['id'])} AND clerk_user_id IS NULL AND clerk_email_verified_at IS NULL
 AND md5(json_build_array(id::text,email,password,firstname,lastname)::text) = '{row['row_token']}';
 GET DIAGNOSTICS changed = ROW_COUNT;
 IF changed <> 1 THEN RAISE EXCEPTION 'Migration source changed'; END IF;
END $migration$;
COMMIT;"""
        self.query(sql)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Clerk:
    def __init__(self, key):
        if not key or not re.match(r'sk_(test|live)_', key):
            raise MigrationError('Set CLERK_SECRET_KEY securely in the host environment.')
        self.key = key
        self.opener = urllib.request.build_opener(NoRedirect())

    def request(self, method, path, payload=None):
        if not path.startswith('/') or path.startswith('//'):
            raise MigrationError('Invalid Clerk API path.')
        req = urllib.request.Request('https://api.clerk.com/v1' + path,
            data=None if payload is None else canonical(payload).encode(), method=method,
            headers={'Authorization': 'Bearer ' + self.key, 'Content-Type': 'application/json',
                     'Clerk-API-Version': API_VERSION})
        for attempt in range(3):
            try:
                with self.opener.open(req, timeout=20) as response:
                    data = json.load(response)
                time.sleep(0.25)  # Below the documented DEV instance limit, including sequential preflight.
                return data
            except urllib.error.HTTPError as error:
                error.close()
                if method == 'GET' and error.code in (429, 502, 503, 504) and attempt < 2:
                    delay = error.headers.get('Retry-After', '')
                    if delay.isdigit() and int(delay) > 30:
                        raise MigrationError('Clerk requires a longer retry delay; stop and rerun later.') from None
                    time.sleep(max(1, int(delay)) if delay.isdigit() else 2 ** attempt)
                    continue
                raise MigrationError(f'Clerk {method} failed (HTTP {error.code}); no response body was logged. Rerun to reconcile before creating anything else.') from None
            except (OSError, ValueError):
                # In particular, never blindly retry a POST whose outcome is unknown.
                raise MigrationError('Clerk request failed or had an unexpected response; rerun to reconcile the outcome.') from None
        raise MigrationError('Clerk request retries exhausted.')

    def verify_target(self, target):
        instance = self.request('GET', '/instance')
        if not isinstance(instance, dict) or instance.get('id') != target['instance_id'] or instance.get('environment_type') != target['environment']:
            raise MigrationError('Clerk key does not match the planned instance/environment.')
        prefix = 'sk_test_' if target['environment'] == 'development' else 'sk_live_'
        if not self.key.startswith(prefix):
            raise MigrationError('Clerk key type does not match the planned environment.')

    def search(self, field, value):
        users = self.request('GET', '/users?' + urllib.parse.urlencode({field: value, 'limit': 2}))
        if not isinstance(users, list):
            raise MigrationError('Unexpected Clerk user-list format; refusing to continue.')
        return users

    def get(self, user_id):
        return self.request('GET', '/users/' + user_id)

    def create(self, payload):
        return self.request('POST', '/users', payload)


def source_rows(raw):
    if not isinstance(raw, list):
        raise MigrationError('Source must be a JSON array.')
    result = []
    for source in raw:
        if not isinstance(source, dict):
            raise MigrationError('Each source row must be an object.')
        row = {key: source.get(key) for key in FIELDS}
        identity = row['id']
        if isinstance(identity, bool) or not re.fullmatch(r'[1-9][0-9]*', str(identity)) or int(identity) > 9223372036854775807:
            raise MigrationError('Source contains an invalid numeric user ID.')
        row['id'] = str(identity)
        row['row_token'] = source.get('row_token')
        result.append(row)
    return sorted(result, key=lambda row: int(row['id']))


def identity_digest(row):
    return digest({key: row[key] for key in ('id', 'email', 'password', 'firstname', 'lastname')})


def source_digest(rows):
    # Mapping/verification changes from this import do not invalidate its resume fingerprint.
    return digest([identity_digest(row) for row in rows])


def source_issues(rows):
    ids, emails, linked = {}, {}, {}
    for row in rows:
        ids[row['id']] = ids.get(row['id'], 0) + 1
        email = row['email']
        if isinstance(email, str):
            normal = email.strip().casefold()
            emails[normal] = emails.get(normal, 0) + 1
        if row['clerk_user_id']:
            linked[row['clerk_user_id']] = linked.get(row['clerk_user_id'], 0) + 1
    results = []
    for row in rows:
        errors, warnings = [], []
        if ids[row['id']] > 1:
            errors.append('duplicate_user_id')
        email = row['email']
        if not isinstance(email, str) or len(email) > 254 or not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', email) or email != email.strip():
            errors.append('invalid_email')
        elif emails[email.casefold()] > 1:
            errors.append('duplicate_email_case_insensitive')
        cid = row['clerk_user_id']
        if cid is not None and (not isinstance(cid, str) or not ID.fullmatch(cid)):
            errors.append('invalid_clerk_link')
        elif cid and linked[cid] > 1:
            errors.append('duplicate_clerk_link')
        if not cid and (not isinstance(row['password'], str) or not BCRYPT.fullmatch(row['password'])):
            errors.append('unsupported_password_hash')
        for key in ('firstname', 'lastname'):
            value = row[key]
            if not isinstance(value, str) or len(value) > 255:
                errors.append('invalid_' + key)
            elif not value.strip():
                warnings.append('missing_' + key + '_complete_after_verification')
        results.append({'id': row['id'], 'action': 'already_linked' if cid else 'create', 'errors': errors, 'warnings': warnings})
    return results


def validate_remote(user, row, *, recovery=False):
    if not isinstance(user, dict) or not isinstance(user.get('id'), str) or not ID.fullmatch(user['id']):
        raise MigrationError('Clerk returned an invalid user identity.')
    if user.get('external_id') != row['id'] or (row['clerk_user_id'] and user['id'] != row['clerk_user_id']):
        raise MigrationError('Clerk identity mapping conflict; manual review required.')
    primary = next((email for email in user.get('email_addresses', []) if email.get('id') == user.get('primary_email_address_id')), None)
    if primary is None or str(primary.get('email_address', '')).casefold() != row['email'].casefold() or user.get('deleted'):
        raise MigrationError('Clerk primary email or deletion state conflicts with the source.')
    if recovery and user.get('private_metadata', {}).get('bible_game_migration', {}).get('source_digest') != identity_digest(row):
        raise MigrationError('Existing external ID was not created from this source by this migration tool.')
    if not row['clerk_user_id'] and not user.get('password_enabled'):
        raise MigrationError('Imported Clerk identity has no password; manual review required.')
    if not row['clerk_user_id'] and primary.get('verification', {}).get('status') != 'unverified':
        raise MigrationError('Clerk did not return an unverified imported email; stop and review before linking.')
    if user.get('banned') or user.get('locked'):
        raise MigrationError('Existing Clerk identity is banned/locked; manual review required.')
    return user['id']


def remote_action(client, row):
    if row['clerk_user_id']:
        return 'already_linked', validate_remote(client.get(row['clerk_user_id']), row)
    matches = client.search('external_id', row['id'])
    if len(matches) > 1:
        raise MigrationError('Multiple Clerk external-ID matches; manual review required.')
    if matches:
        return 'recover', validate_remote(matches[0], row, recovery=True)
    if client.search('email_address', row['email']):
        raise MigrationError('Email already exists in Clerk without this migration mapping; never auto-link it.')
    return 'create', None


def make_plan(rows, target, client=None):
    results = source_issues(rows)
    if client:
        client.verify_target(target)
        for row, result in zip(rows, results):
            if not result['errors']:
                try:
                    result['action'], _ = remote_action(client, row)
                except MigrationError as error:
                    result['errors'].append('clerk_conflict_or_unavailable_review_required')
                    result['remote_detail'] = str(error)
    return {'version': 1, 'api_version': API_VERSION, 'target': target, 'source_digest': source_digest(rows),
            'target_checked': client is not None, 'rows': results,
            'summary': {'total': len(rows), 'blocked': sum(bool(r['errors']) for r in results),
                        'warnings': sum(bool(r['warnings']) for r in results)}}


def payload_for(row):
    return {'external_id': row['id'], 'email_address': [row['email']],
            'email_address_identification_status': ['reserved'],
            'password_digest': row['password'], 'password_hasher': 'bcrypt',
            'first_name': row['firstname'].strip() or None, 'last_name': row['lastname'].strip() or None,
            'private_metadata': {'bible_game_migration': {'version': 1, 'source_digest': identity_digest(row)}}}


@contextlib.contextmanager
def journal_lock(path):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(str(path) + '.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise MigrationError('Another process is using this migration journal.') from None
        yield


def apply_plan(plan, rows, client, database, journal_path):
    if plan.get('version') != 1 or plan.get('api_version') != API_VERSION or not plan.get('target_checked'):
        raise MigrationError('Apply requires a reviewed online preflight plan.')
    if plan.get('source_digest') != source_digest(rows):
        raise MigrationError('Source has changed since planning; produce and review a fresh plan.')
    if any(r.get('errors') for r in plan.get('rows', [])):
        raise MigrationError('The reviewed plan has blockers; resolve them and re-plan.')
    if not plan.get('source_database') or database.identity() != plan['source_database']:
        raise MigrationError('Apply requires a reviewed plan for this exact source database.')
    target = plan['target']
    fresh = make_plan(rows, target, client)
    if fresh['summary']['blocked']:
        raise MigrationError('Fresh preflight found blockers; no users were created or linked.')
    path = Path(journal_path)
    if path.is_symlink():
        raise MigrationError('Refusing a symlink journal.')
    if path.exists() and stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise MigrationError('Journal must have private (0600) permissions.')
    journal = read_json(path) if path.exists() else {'target': target, 'source_digest': plan['source_digest'], 'mappings': {}}
    if journal.get('target') != target or journal.get('source_digest') != plan['source_digest']:
        raise MigrationError('Journal belongs to a different source or target.')
    if not isinstance(journal.get('mappings'), dict):
        raise MigrationError('Invalid journal format; no records have been changed.')
    private_json(path, journal)
    for row in rows:
        # Reconcile before every create, including after lost responses or interrupted runs.
        action, cid = remote_action(client, row)
        previous = journal['mappings'].get(row['id'], {})
        if previous.get('status') == 'review_required' or (previous.get('status') == 'creating' and action == 'create'):
            raise MigrationError('A previous create has an unresolved outcome; manual review is required before any new create.')
        recorded = previous.get('clerk_id')
        if recorded and recorded != cid:
            raise MigrationError('Journal mapping disappeared or changed; never recreate it automatically.')
        if action == 'already_linked':
            journal['mappings'][row['id']] = {'clerk_id': cid, 'status': 'linked'}
        else:
            if action == 'create':
                journal['mappings'][row['id']] = {'status': 'creating'}
                private_json(path, journal)
                created = client.create(payload_for(row))
                try:
                    cid = validate_remote(created, row, recovery=True)
                except MigrationError:
                    journal['mappings'][row['id']]['status'] = 'review_required'
                    private_json(path, journal)
                    raise
            journal['mappings'][row['id']] = {'clerk_id': cid, 'status': 'created_pending_link'}
            private_json(path, journal)  # Durable recovery point before touching the source mapping.
            database.link(row, cid)
            journal['mappings'][row['id']]['status'] = 'linked'
        private_json(path, journal)
    return {'linked': len(journal['mappings']), 'target': target}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    plan = sub.add_parser('plan', help='Read-only validation; no Clerk users or DB mappings are written')
    source = plan.add_mutually_exclusive_group(required=True)
    source.add_argument('--input', type=Path, help='Private JSON export; never an environment file')
    source.add_argument('--source-db', action='store_true', help='Read source using host libpq PG* configuration')
    plan.add_argument('--instance-id', required=True)
    plan.add_argument('--environment', choices=['development', 'production'], required=True)
    plan.add_argument('--check-clerk', action='store_true', help='GET-only target preflight using CLERK_SECRET_KEY')
    plan.add_argument('--report', type=Path, required=True)
    apply = sub.add_parser('apply', help='Explicit live import + DB mapping; run only after DEV acceptance and approval')
    apply.add_argument('--plan', type=Path, required=True)
    apply.add_argument('--journal', type=Path, required=True)
    apply.add_argument('--confirm-instance', required=True)
    apply.add_argument('--writes-frozen', action='store_true', required=True)
    apply.add_argument('--bot-cleanup-complete', action='store_true', required=True)
    apply.add_argument('--allow-production', action='store_true')
    args = parser.parse_args(argv)
    database = Postgres()
    if args.command == 'plan':
        if not re.fullmatch(r'ins_[A-Za-z0-9]+', args.instance_id):
            raise MigrationError('Invalid target instance ID.')
        rows = source_rows(read_json(args.input) if args.input else database.read())
        client = Clerk(os.environ.get('CLERK_SECRET_KEY')) if args.check_clerk else None
        report = make_plan(rows, {'instance_id': args.instance_id, 'environment': args.environment}, client)
        report['source_database'] = database.identity() if args.source_db else None
        private_json(args.report, report, exclusive=True)
        print(canonical({'mode': 'read-only-plan', 'target_checked': report['target_checked'], 'summary': report['summary']}))
        return 2 if report['summary']['blocked'] else 0
    if args.plan.resolve() == args.journal.resolve():
        raise MigrationError('The plan and journal must be different files.')
    report = read_json(args.plan)
    target = report.get('target', {})
    if target.get('instance_id') != args.confirm_instance or target.get('environment') not in ('development', 'production'):
        raise MigrationError('Confirmation does not match the reviewed target.')
    if target['environment'] == 'production' and not args.allow_production:
        raise MigrationError('Production apply requires --allow-production after DEV acceptance.')
    with journal_lock(args.journal):
        result = apply_plan(report, source_rows(database.read()), Clerk(os.environ.get('CLERK_SECRET_KEY')), database, args.journal)
    print(canonical({'mode': 'applied', **result}))
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except MigrationError as error:
        print('Migration stopped: ' + str(error), file=sys.stderr)
        sys.exit(1)
    except (OSError, ValueError, TypeError, KeyError):
        print('Migration stopped: unexpected input or local I/O failure; no sensitive details were logged.', file=sys.stderr)
        sys.exit(1)
