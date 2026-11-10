# Clerk backend rollout

The user service supports an opt-in Clerk mode and defaults to legacy authentication until cutover. The webapp now uses Clerk throughout: the old forms/actions, webapp mode switch and design-preview gallery have been removed. Normal localhost:3000 account routes show the agreed screens. This does not activate the user service; configure/migrate it before deploying the Clerk-only webapp.

## Deploy preparation

1. Apply the additive `1.2 Clerk identity mapping` SQL in `deployment/database/patch.sql` **before deploying the updated user-service model**, including when initially leaving Clerk mode disabled. It adds `clerk_user_id`, its unique index, `clerk_email_verified_at`, and `profile_completed_at`, plus the `clerk_webhook_state` table; it retains all numeric IDs, passwords and history. Do not rerun unrelated historical statements in `patch.sql` indiscriminately.
2. Set these values in the user service's server environment, using the same Clerk instance as the webapp:

   | Variable | Value |
   | --- | --- |
   | `CLERK_ENABLED` | `true` when ready for the coordinated cutover; default `false` |
   | `CLERK_ISSUER` | The exact HTTPS Clerk Frontend API origin, without a trailing slash |
   | `CLERK_SECRET_KEY` | That instance's backend secret, stored in the service's secret manager; never in source, client code or chat |
   | `CLERK_WEBHOOK_SIGNING_SECRET` | This endpoint’s `whsec_...` signing secret, stored only in the user-service secret manager |
   | `CLERK_INSTANCE_ID` | The exact `ins_...` instance delivering these events; separate DEV/production values |
   | `CLERK_AUTHORIZED_PARTIES` | Comma-separated exact webapp origins, e.g. `http://localhost:3000,https://dev.bible.game`; no wildcard |

3. Before importing production users, complete the manual bot cleanup. Import with bcrypt hashes and set Clerk's backend-only `external_id` to the original numeric `app_user.id` as a string. Record the returned Clerk ID in `app_user.clerk_user_id`. Exclude church from Clerk import payloads, metadata and exports prepared for Clerk. Leave `clerk_email_verified_at` null. Do not mark legacy addresses verified merely to simplify import.
4. Configure the webapp with the same Clerk instance keys and the intended user-service URL. It no longer reads `CLERK_ENABLED`: login/sign-up and the later account steps are the normal `/account` routes. The backend schema, issuer/keys/origins and `CLERK_ENABLED=true` are required before account continuation can complete. Run the manual DEV journeys before deploying this webapp to players. No deployed database or environment was changed by this implementation. A frontend rollback uses the previous release, not a runtime legacy-view flag.

## Migration tooling

The read-only planner and explicit resumable importer are implemented in `tools/clerk_migration.py`. See [the migration runbook](clerk-migration.md) for offline/GET-only plans, reserved unverified emails, database/instance binding, journals, DEV acceptance and separately approved apply commands. No real players have been imported.

## Identity and provisioning

- The decoder obtains JWKS from the configured issuer, accepts RS256 only, and checks issuer, timestamps, a session ID, a `user_` subject, an allowed `azp`, and active session status. Signature verification and key rotation use Spring Security's Nimbus JWT decoder.
- First access fetches the user from Clerk's authenticated Backend API. The primary email must be verified. Imported records with a linked Clerk ID still undergo this check until `clerk_email_verified_at` is recorded.
- A verified identity resolves to the existing numeric user ID. Controllers, games, reads, reviews and rankings continue to use that ID. `/info` includes `id` and `profileCompleted` alongside the existing name fields; church is excluded from the active account contract.
- A backend-provided `external_id` can link an imported record; conflicting links or missing migration targets fail closed. Email equality alone never links an account. If a legacy record has the same email but no explicit migration mapping, access returns `409 account_migration_required`.
- Provisioning is transactional and repeatable. The unique Clerk-ID constraint handles concurrent first requests; the resolver re-reads a committed mapping after a duplicate-insert rollback.
- Unverified primary email returns `403 email_verification_required`; invalid tokens return `401`; Clerk outages return `503 identity_provider_unavailable`. Client retries must preserve guest progress.
- Once verified and provisioned, requests use the local mapping. Signed `user.updated` and `user.deleted` events synchronize profile changes and account deletion (see below). Updates are eventually consistent: a delayed or failed delivery leaves the last local state until delivery/replay succeeds. Session-only revocation still relies on JWT expiry (including clock skew); session webhooks/introspection are not implemented.

## Required names; church collection deferred

`PUT /info/profile` fills missing first/last names only. Both effective names must be nonblank before completion. Church collection and mapping are deferred beyond 1.2.0; old church/skip request fields are ignored and existing church records are preserved. Existing social names can be omitted from the request because they are retained from the account. Older completed markers with blank names are treated as incomplete and can be repaired. The operation locks the user row; retries preserve a completed profile with valid names. The endpoint is authenticated and exists only in Clerk mode. The webapp also updates missing Clerk names using its server SDK.

Primary-email verification now has a code-entry screen at `/account/verify-email`. Users without inbox access are directed to support; a formal recovery/ownership-check procedure remains a release decision.

## Legacy routes and rollout

In Clerk mode, legacy auth controllers and the legacy token/session filters are not enabled. `/auth/**` returns 404 for normal route requests, and security also denies that path family. Public health/leaderboard reads remain available; private requests require a Clerk session.

Do not enable Clerk mode while a client still sends legacy JWTs. The `common` library remains unchanged: this decoder is isolated to the only service that needs it, avoiding a shared-library release and preserving the staged legacy path. Remove legacy auth code, email/reset dependencies and password columns after migration and the rollback window, as a separate cleanup.

## Verification

Run from the `user` repository:

```sh
./gradlew :service:test --tests '*clerk.*' --tests '*SessionFilterSpec' --tests '*StateServiceSpec'
```

Coverage includes real RSA/JWKS verification and key rotation, malformed/expired/wrong-origin tokens, verified primary email and migration IDs, preservation of app data, refusal to link by email, first-request provisioning, protected HTTP routes, legacy registration closure, and merging guest progress through the existing API.

The full suite had three failures before this change: two password-reset email assertions in `AuthControllerIT`, and `NotificationServiceSpec`'s reset-email test. Do not treat those existing failures as successful validation.


## Webapp verification

From `webapp`, run `npm run test:onboarding`, `npx tsc --noEmit`, `npm run lint`, and `npm run build`. The browser storage tests use in-memory data, and the backend profile tests use H2. No real Clerk users are created.

Each Clerk account and the guest have separate browser storage namespaces. Transfers use Web Locks; unavailable locks preserve guest data and display a notice. Old unscoped caches on devices known to have held an account are retained as backups and are not automatically attributed to another identity. Imported users regain their account history from the server; reviewing any unattributed local-only legacy progress remains part of the migration work.

## Legacy church data

Church is not collected by the new onboarding flow, returned by `/info`, or copied into Clerk. New Clerk users leave the legacy column empty. Keep `app_user.church` and the disabled legacy registration model during the staged rollout/rollback window. Existing values are not modified by profile completion or identity linking. Review retention separately before deleting historical data and dropping the column and legacy references in a dedicated cleanup migration. The additive Clerk schema migration and numeric-ID/progress mappings are unchanged.

## Signed lifecycle webhooks

Implemented locally at `POST /webhooks/clerk` on the **user service**, only in Clerk mode. This is not a Next.js route. It bypasses player bearer authentication and requires the Svix HMAC signature over the original request bytes. Verification accepts any valid full-length v1 signature, rejects delivery timestamps outside a five-minute past/future window, and checks the configured Clerk instance before processing. The endpoint rejects bodies larger than 256 KiB. Missing/invalid signing configuration or missing instance configuration fails closed; this must be configured and exercised before cutover.

Setup for each environment:

1. Apply the additive `clerk_webhook_state` table before deploying the model, even if Clerk remains disabled. Do not apply unrelated historical SQL.
2. In the intended Clerk instance, create an endpoint pointing at `https://<user-service-host>/webhooks/clerk`, subscribing to `user.updated` and `user.deleted`. Keep DEV and production endpoints/secrets separate. Configure `CLERK_WEBHOOK_SIGNING_SECRET` and `CLERK_INSTANCE_ID` securely in the user service; do not paste secret values into chat or source.
3. Ensure ingress forwards the original body and `svix-id`, `svix-timestamp`, `svix-signature` headers unchanged, with no player login or bearer requirement on this exact POST route. Keep all account/state routes protected. A local test tunnel must expose only the needed webhook route, not the whole service.
4. After coordinated DEV activation, use a disposable, user-approved account to check name changes, verified-email changes and deletion with progress attached. Inspect delivery status in Clerk. This implementation has only used synthetic signed events and disposable database fixtures, not live Clerk test users.
5. Monitor failed deliveries and replay them from Clerk after correcting configuration or data conflicts. Non-2xx responses are failures, not acknowledgments. Do not ignore a persistent failure queue.

Behavior:

- Valid handled, duplicate, stale and unneeded event types return 204. Invalid signatures/envelopes return 400; oversized requests return 413. Database/transient errors fail the request so the sender can retry; email collisions with another local user return 409 for migration review. Error responses do not contain webhook payloads or signing secrets.
- `user.updated` updates names and a verified primary email only for an already linked Clerk ID. It does not claim by email/external ID or create accounts. Unverified primary email, ban or lock clears the local verification marker so the next account request must pass a fresh Backend API check. Missing names clear profile completion. Church is neither imported nor updated.
- `user.deleted` removes the mapped user, password-reset tokens, games/guesses, reads, reviews/answers and grades in one transaction. An unknown deleted ID still receives a deletion guard. Existing devices' browser caches and any backups are not erased remotely by this service; their retention needs the separate privacy/deletion policy.
- Ordering uses the user object's `updated_at` milliseconds; older/equal updates are ignored. Deletion always wins. A shared per-Clerk-ID database row serializes webhook events and synchronous provisioning across service instances. Concurrent first inserts may fail one delivery; retry is safe. A stale Backend API result cannot overwrite a newer event or bypass its verification restriction.
- The guard table retains only Clerk ID, last update version and a deletion flag; no names, emails or payloads. Deleted IDs cannot be recreated by late updates or in-flight provisioning. This minimal anti-replay marker remains after deletion; document its purpose/retention and do not purge it blindly while old events or migration mappings can be replayed.
- `user.created` is intentionally not used: first verified sign-in provisions through the authenticated Backend API. Onboarding never waits for webhook delivery.

Sources: [Clerk delivery, ordering and retry guidance](https://clerk.com/docs/guides/development/webhooks/overview), [Svix raw-body verification protocol](https://www.svix.com/guides/receiving/receive-webhooks-with-java/).
