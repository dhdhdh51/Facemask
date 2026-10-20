# JawlineFit

JawlineFit is an Android facial-exercise and jawline-wellness app backed by a framework-free PHP/MySQL service. The Android application ID and namespace are `com.jawlinefit.app` for both debug and release builds. The repository includes the Android client, PHP API, administration and public web pages, database schema, media handling, and the Android GitHub Actions workflow.

## Disclaimer

Results vary. Facial structure is largely genetic. This is not medical advice.

## Repository structure

```text
.
├── .github/workflows/android.yml       # Android CI and optional signed release build
├── android-app/
│   ├── app/
│   │   ├── build.gradle.kts            # SDK, BuildConfig, signing, dependencies
│   │   ├── schemas/                    # Room schema history
│   │   └── src/{main,test}/            # app source and JVM tests
│   ├── fastlane/metadata/android/       # Play Store copy in English and Hindi
│   ├── gradle/libs.versions.toml        # dependency versions
│   ├── gradle/wrapper/                  # Gradle 8.11.1 wrapper
│   ├── gradle.properties                # checked-in Gradle property defaults
│   └── settings.gradle.kts
├── web-backend/
│   ├── admin/index.php                  # compatibility entry point
│   ├── api/index.php                    # compatibility entry point
│   ├── bin/setup.php                    # one-time CLI installer
│   ├── includes/                        # controllers and backend services
│   ├── public/
│   │   ├── index.php                    # application front controller
│   │   └── assets/                      # local web assets
│   ├── uploads/{.htaccess,.gitkeep}     # protected media directory
│   ├── .htaccess                        # Apache routing and access controls
│   ├── composer.json
│   ├── composer.lock
│   ├── config.php                       # safe environment-only loader
│   ├── config.sample.php                # environment configuration definition
│   └── install.sql                      # MySQL schema
├── .gitignore
└── README.md
```

The Android app uses Jetpack Compose, Room, DataStore, WorkManager, CameraX, Media3, Credential Manager, Firebase Authentication/Messaging/Crashlytics/Analytics, Play Billing, Retrofit/OkHttp, Coil, and ML Kit face detection. Progress photos remain in app-private Android storage and face analysis runs on the device.

The backend provides Firebase-authenticated content/account APIs, Google Play subscription verification, authenticated Real-time Developer Notifications (RTDN), an administration console, public policy and account-deletion pages, and protected image/GIF/video processing. `web-backend/public/index.php` is the front controller; Apache rewrites and nginx routing must send application requests to it while serving `/public/assets` and `/uploads` directly.

## Prerequisites

### Android

- JDK 17.
- Android SDK 36 and Build Tools 36.0.0.
- The checked-in Gradle wrapper; a global Gradle installation is not required.
- A Firebase Android configuration at `android-app/app/google-services.json`.
- Android URL properties described below.
- A release/upload keystore and `android-app/signing.properties` only when producing a signed local release.

The app has minimum SDK 24, target/compile SDK 36, Java/Kotlin target 17, version name `1.0.0`, and a positive `BUILD_NUMBER` used as `versionCode`.

### Backend

- PHP 8.1 through 8.4 with `curl`, `fileinfo`, `gd`, `json`, `mbstring`, `openssl`, and `pdo_mysql`; `exif` is recommended for JPEG orientation.
- MySQL 8.0+ or MariaDB 10.6+ with JSON-column support.
- Composer 2.
- Apache 2.4 with `mod_rewrite` and `mod_headers`, or nginx with PHP-FPM.
- `ffmpeg` and `ffprobe` for H.264 validation and video thumbnails.
- HTTPS, outbound access to Google services, and a working PHP `mail()` transport for deletion-request verification.

External console credentials and configuration are still required: Firebase Authentication, Firebase Android registration, Google OAuth, Play Console app/subscriptions/app signing, Google Play Developer API access, Pub/Sub RTDN, repository Actions settings, DNS/TLS, mail delivery, database credentials, and production environment variables are not created by this repository.

## Android configuration

`android-app/app/build.gradle.kts` consumes these Gradle properties:

| Property | Required behavior |
|---|---|
| `API_BASE_URL` | Absolute HTTPS Retrofit base URL. The build trims trailing `/` characters and writes exactly one trailing `/` to `BuildConfig`. |
| `PRIVACY_URL` | Absolute HTTPS privacy-policy URL. |
| `TERMS_URL` | Absolute HTTPS terms URL. |
| `ACCOUNT_DELETION_URL` | Absolute HTTPS account-deletion URL. |
| `GOOGLE_WEB_CLIENT_ID` | Web OAuth client ID used by Credential Manager. It may be blank, in which case Google sign-in is unavailable. |
| `BUILD_NUMBER` | Positive integer Android `versionCode`; defaults to `1` locally. |

All four URL properties are read with mandatory Gradle property access and validated as absolute HTTPS URLs with a host. Checked-in values live in `android-app/gradle.properties`; override them with `-Pname=value` or CI inputs for the target environment.

The checked-in `android-app/gradle/wrapper/gradle-wrapper.jar` remains trackable. Release signing is enabled when `android-app/signing.properties` exists and supplies `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`; `storeFile` must identify the keystore used for that build.

The file and keystore must remain untracked. Release builds enable code shrinking and resource shrinking; debug builds do not enable minification. Cleartext network traffic is disabled.

## Firebase Phone and Google sign-in

1. Register the Android app `com.jawlinefit.app` in Firebase.
2. In Firebase Authentication, enable both **Phone** and **Google** providers.
3. Register SHA-1 and SHA-256 fingerprints for every signing identity that can install the app:
   - the local debug certificate;
   - the release/upload certificate;
   - the Google Play App Signing certificate.
4. Obtain local certificate fingerprints with `./gradlew :app:signingReport` and inspect the release/upload keystore with `keytool` when needed. Copy the Play App Signing fingerprints from **Play Console → Setup → App integrity**.
5. Download the refreshed Android Firebase configuration to `android-app/app/google-services.json` and base64-encode the same file for GitHub Actions.
6. Configure the Firebase-created web OAuth client ID as `GOOGLE_WEB_CLIENT_ID`.
7. Configure Phone Authentication regions, quotas, abuse controls, and Firebase-managed test-number settings.
8. Verify Phone and Google flows on debug, directly installed release, and Play-distributed builds because their signing certificates can differ.

The app sends Firebase ID tokens in the `Authorization` header using the Bearer scheme. It retries an authenticated request once after a `401` by forcing a token refresh. Debug HTTP logs use BASIC level and redact `Authorization`; release HTTP logging is disabled.

## Exact API contract

Routes below are the implemented Android/PHP contract. Android resolves them relative to `API_BASE_URL`.

### Transport and envelopes

- Every app API route requires an `Authorization` header using the Bearer scheme with a Firebase ID token except `POST /api/play-rtdn`.
- RTDN requires a Google OIDC bearer token whose issuer, signature, audience, verified service-account email, subject, and expiry pass backend validation.
- JSON request bodies must be JSON objects, must use `Content-Type: application/json`, and may not exceed 1,048,576 bytes.
- A success object has exactly two top-level members: `success` is `true`, and `data` contains the result or `null`.
- A failure object has exactly two top-level members: `success` is `false`, and `error` is a human-readable string.
- Responses use `Content-Type: application/json; charset=utf-8`.
- Invalid JSON returns `400`; authentication failures return `401`; authorization failures return `403`; missing records return `404`; wrong request media type returns `415`; validation failures return `422`; rate limiting returns `429` with `Retry-After`; unexpected failures return a sanitized `500` response.
- Dates returned by account and billing operations are RFC 3339 UTC strings or `null`.
- `media_type` is exactly `image`, `gif`, `video`, or `null`. Media and thumbnail URLs are absolute HTTPS URLs or `null`.

### Routes

#### `GET /api/me`

No request body. Exact `data` fields:

| Field | Type |
|---|---|
| `uid` | string |
| `plan` | product ID string, `manual`, or `null` |
| `plan_expiry` | RFC 3339 string or `null` |
| `is_premium` | boolean |

An unexpired manual grant or an unexpired subscription in active, grace-period, or canceled state supplies entitlement. A canceled subscription remains entitled through its recorded expiry. When both sources exist, `plan` and `plan_expiry` describe the later expiry.

#### `POST /api/device`

Exact request field: `fcm_token` string. Unknown fields are rejected. The trimmed token is required and limited to 512 bytes. Exact `data` field: `fcm_token`. The unique token is assigned to the authenticated user.

#### `GET /api/nuskhe`

Accepted query keys are only `concern`, `skin_type`, and `page`. Filters are optional exact matches; `page` is required by the Android interface, defaults to `1` server-side, and must be an integer from 1 through 1,000,000. Each page contains at most 20 published records ordered by `sort_order`, then `id`. `data` is an array with no pagination metadata.

Each item has exactly:

| Field | Type |
|---|---|
| `id` | integer |
| `title`, `concern`, `skin_type`, `frequency` | string |
| `ingredients`, `steps` | arrays of strings |
| `warning` | string or `null` |
| `is_premium` | boolean |
| `media_type` | media type or `null` |
| `media_url`, `thumbnail_url` | string or `null` |

For premium records without entitlement, `ingredients` and `steps` are empty and `media_url` is `null`; warning and thumbnail fields remain available.

#### `GET /api/routines`

No request body. `data` is an array of published routines ordered by `sort_order`, then `id`. Every item has exactly `id` (integer), `title` (string), `description` (string), `duration_min` (integer), `is_premium` (boolean), `media_type`, `media_url`, and `thumbnail_url`. A locked premium summary has `media_url: null`; the other fields remain present.

#### `GET /api/routines/{id}`

`id` is a numeric routine ID. Missing records return `404`; premium detail without entitlement returns `403`. Exact routine fields are the listing fields plus `steps`.

Each step has exactly `order` (integer), `title` (string), `duration_sec` (integer), `instruction` (string), `media_type`, `media_url`, and `thumbnail_url`. Steps are ordered by `sort_order`, then database ID.

#### `GET /api/tips`

No request body. `data` is an array of published tips ordered by `sort_order`, then `id`. Every item has exactly `id` (integer), `category` (string), `text` (string), `media_type`, `media_url`, and `thumbnail_url`.

#### `GET /api/plans`

No request body. `data` is an array of active plans ordered by `sort_order`, then `id`. Every plan has exactly:

| Field | Type |
|---|---|
| `id` | integer |
| `name` | string |
| `product_id` | string |
| `base_plan_id` | string |
| `duration_days` | integer |

The app deduplicates product IDs, queries Play Billing for SUBS products, and displays an offer only when its base-plan ID matches the backend row and its ISO billing period maps to `duration_days` (`D` exact, `W × 7`, `M × 30`, `Y × 365`). Displayed price and billing period come from Play Billing.

#### `POST /api/billing/verify`

Exact request fields are `product_id` and `purchase_token`; unknown fields are rejected. The package name is never client input. Each string must be non-empty; product ID is limited to 191 bytes and token to 512 bytes.

Exact `data` fields are `is_premium` (boolean) and `plan_expiry` (RFC 3339 string or `null`). The server requires an active local product, prevents one token from belonging to multiple users, calls Android Publisher subscription v2, and selects the latest-expiring matching line item whose `(product_id, base_plan_id)` pair is active locally. Eligible pending purchases are acknowledged server-side. Purchase tokens and complete Google responses are encrypted with AES-256-GCM; token lookup uses HMAC-SHA256.

#### `DELETE /api/me`

No request body. Firebase authentication must be recent according to `DELETE_RECENT_AUTH_SECONDS`, which defaults to 900. The backend deletes the authenticated SQL user in a transaction; foreign-key cascades remove its devices and subscriptions. Success is HTTP `200` with `{"success":true,"data":null}`.

The Android flow first obtains a fresh Firebase ID token and deletes the Firebase identity, then calls this route with the retained bearer token. After backend success it clears reminders, private progress media, completion/cache data, and preferences. The public deletion-request page supports users who cannot sign in.

#### `POST /api/play-rtdn`

This route uses authenticated Pub/Sub push rather than Firebase user authentication. The body must contain `message.messageId` and base64 `message.data`. Decoded data must contain the configured `packageName`; a subscription notification may include `subscriptionNotification.purchaseToken`.

Exact success `data` fields are `accepted: true` and `duplicate` (boolean). Message IDs are deduplicated. A known token causes an Android Publisher refresh; notification content does not directly change entitlement. If refresh fails, the message-ID record is removed so delivery can be retried.

## Backend installation and configuration

`web-backend/config.php` is a safe required loader that returns `config.sample.php`; both read values only from process environment variables. Keep `config.php`, `config.sample.php`, and `composer.lock` tracked. Keep Google credentials, `.env` files, runtime state, `vendor`, and uploaded media untracked. Production credential files and runtime log/cache/setup-lock paths must be absolute, readable or writable as appropriate, and outside `web-backend`.

### Production-required environment

| Variable | Requirement |
|---|---|
| `APP_BASE_URL` | Absolute public HTTPS origin, without a trailing slash. |
| `PUBLIC_EMAIL_FROM` | Valid sender address for deletion verification mail. |
| `PUBLIC_OPERATOR_NAME` | Operator identity rendered on public pages. |
| `PUBLIC_SUPPORT_EMAIL` | Valid support address rendered on public pages. |
| `DB_USER`, `DB_PASSWORD` | MySQL credentials. |
| `FIREBASE_PROJECT_ID` | Exact project used for token issuer/audience checks. |
| `GOOGLE_PLAY_PACKAGE_NAME` | Must be `com.jawlinefit.app`. |
| `GOOGLE_SERVICE_ACCOUNT_JSON` | Absolute path outside the web root or complete JSON supplied by a secret manager. |
| `GOOGLE_RTDN_AUDIENCE` | Exact audience configured on the authenticated Pub/Sub push subscription. |
| `GOOGLE_RTDN_SERVICE_ACCOUNT_EMAIL` | Exact verified email allowed to invoke RTDN. |
| `APP_ENCRYPTION_KEY` | Either 64 hexadecimal characters or base64 that decodes to exactly 32 bytes. |

Production also requires `FORCE_HTTPS=1`, a `mysql:` PDO DSN, valid email values, positive limits, and external runtime paths. `DB_DSN` defaults to a local MySQL DSN but should be set explicitly for the deployment.

### Other supported environment variables

| Group | Variables and defaults |
|---|---|
| Application | `APP_ENV=production`, `APP_TIMEZONE=UTC`, `FORCE_HTTPS=1`, `TRUSTED_PROXIES`, `DELETE_VERIFICATION_TTL=86400`, `PUBLIC_DELETE_RATE_LIMIT=3`, `PUBLIC_DELETE_RATE_WINDOW=3600`, `APP_LOG_PATH`, `APP_CACHE_PATH`, `SETUP_LOCK_PATH` |
| Database TLS | `DB_DSN`, `DB_TLS_REQUIRED=0`, `DB_SSL_CA`, `DB_SSL_CERT`, `DB_SSL_KEY`, `DB_SSL_VERIFY_SERVER_CERT=1` |
| Public policy | `CHILDREN_MINIMUM_AGE=13`, `ACCOUNT_RETENTION_DAYS=30`, `DELETE_AUDIT_RETENTION_DAYS=1095` |
| Media | `IMAGE_MAX_MB=5`, `GIF_MAX_MB=10`, `THUMBNAIL_MAX_MB=10`, `VIDEO_MAX_MB=50`, `FFMPEG_PATH=ffmpeg`, `FFPROBE_PATH=ffprobe` |
| Admin | `ADMIN_SESSION_NAME=jawlinefit_admin`, `ADMIN_SESSION_LIFETIME=7200`, `ADMIN_LOGIN_ATTEMPTS=5`, `ADMIN_LOGIN_WINDOW=900` |
| API limits | `API_RATE_LIMIT=120`, `API_RATE_WINDOW=60`, `RTDN_RATE_LIMIT=300`, `RTDN_RATE_WINDOW=60`, `BILLING_RATE_LIMIT=10`, `BILLING_RATE_WINDOW=300`, `DELETE_RATE_LIMIT=3`, `DELETE_RATE_WINDOW=3600`, `DELETE_RECENT_AUTH_SECONDS=900` |

Set `TRUSTED_PROXIES` only to operator-controlled proxy IP addresses. If `DB_TLS_REQUIRED=1`, `DB_SSL_CA` must refer to a readable CA file outside the web root; optional client certificate/key paths are `DB_SSL_CERT` and `DB_SSL_KEY`.

### Install

From the backend directory:

```bash
cd "$BACKEND_ROOT"
composer install --no-dev --classmap-authoritative
php bin/setup.php
```

Before setup, create an empty `utf8mb4` database, set the environment, make `uploads` writable by PHP, and make the external log/cache/setup-lock paths writable. The CLI installer runs `install.sql`, creates the supplied first administrator with a password of at least 12 characters, and writes the permanent setup lock. The CSRF-protected `/setup` web installer is an alternative. Setup refuses to run after an administrator exists or the lock is present.

The schema creates `users`, `devices`, `nuskhe`, `routines`, `routine_steps`, `tips`, `plans`, `subscriptions`, `admin_users`, `delete_requests`, `rate_limits`, and `rtdn_events`. It inserts no accounts, plans, content, purchases, or administrators. Remove schema-changing privileges from the runtime database identity after installation.

## Media and PHP limits

Backend application limits are 5 MiB for JPEG/PNG/WebP images, 10 MiB for GIFs, 10 MiB for manual thumbnails, and 50 MiB for H.264 MP4 video. Admin uploads are MIME-checked; images are decoded and re-encoded, long dimensions are limited to 1280 pixels, thumbnails are 400×225 JPEG, filenames are random, and script execution is blocked under `uploads`.

Set equivalent values through cPanel MultiPHP INI Editor, `.user.ini`, `php.ini`, or the PHP-FPM pool. PHP request limits must sit above the 50 MiB application maximum:

```ini
file_uploads = On
upload_max_filesize = 52M
post_max_size = 64M
max_file_uploads = 5
max_execution_time = 120
max_input_time = 120
memory_limit = 256M
display_errors = Off
log_errors = On
session.use_strict_mode = 1
session.use_only_cookies = 1
```

Restart PHP-FPM or Apache after changing server-level settings. The checked-in Apache `mod_php` block applies the 52M/64M/time/memory values when `mod_php` is active. JSON API bodies retain their independent 1 MiB limit.

## Web-server deployment

Use a dedicated HTTPS origin and point the site root at `web-backend`, not `web-backend/public`, because `/public/assets` and `/uploads` are direct static paths.

### Apache

- Enable `mod_rewrite` and `mod_headers` and permit `.htaccess` overrides for `$BACKEND_ROOT`.
- Preserve `web-backend/.htaccess` and `web-backend/uploads/.htaccess`.
- Serve `$BACKEND_ROOT` as the HTTPS `DocumentRoot` and redirect the port-80 virtual host to HTTPS.
- Ensure authorization headers reach PHP.
- Keep configuration, source, schema, Composer metadata, dependencies, runtime directories, and executable uploads inaccessible over HTTP.

### nginx with PHP-FPM

- Set `root` to `$BACKEND_ROOT`, `index` to `public/index.php`, and `client_max_body_size` to at least `64m`.
- Serve only `/public/assets/` and validated `/uploads/` files directly; deny executable extensions under uploads.
- Deny direct access to configuration, `includes`, `vendor`, `bin`, `storage`, `install.sql`, and Composer metadata.
- Route application requests to `/public/index.php`, set `SCRIPT_FILENAME` to `$BACKEND_ROOT/public/index.php`, and forward `HTTP_AUTHORIZATION`.
- Deny every other PHP file and redirect HTTP to HTTPS with a separate server block.

### cPanel

1. Select PHP 8.1+ and enable all required extensions.
2. Create the MySQL database and installation identity.
3. Assign a dedicated domain document root to the backend directory; keep runtime and credential paths outside it.
4. Configure environment variables using the host’s protected application/environment facility.
5. Apply the PHP/media limits through MultiPHP INI Editor or `.user.ini`.
6. Run Composer and `php bin/setup.php` from Terminal. If Terminal is unavailable, install Composer dependencies before upload and run `/setup` once.
7. Configure AutoSSL, HTTPS redirect, `.htaccess` support, `PUBLIC_EMAIL_FROM`, and authenticated mail delivery.

### VPS

1. Install Apache or nginx, PHP-FPM 8.1+, PHP extensions, MySQL/MariaDB, Composer, ffmpeg, and mail transport.
2. Use a dedicated Unix user and PHP-FPM pool. Keep source read-only to PHP except `uploads` and external runtime paths.
3. Inject environment through a root-readable service environment file or secret manager.
4. Run Composer and CLI setup, configure TLS and firewall rules, keep MySQL private, and reload services.
5. Configure log rotation, monitoring, database/upload backups, restore checks, and security updates.

### AWS EC2

1. Use a supported Linux image and a security group exposing 80/443 plus restricted operator SSH; keep port 3306 private.
2. Prefer private RDS with TLS, automated backups, and a least-privilege application identity.
3. Install the VPS dependencies or deploy an immutable image.
4. Store secrets in AWS Secrets Manager or SSM Parameter Store and inject them into PHP-FPM at service start.
5. Terminate TLS at an Application Load Balancer or the instance. When proxied, trust only controlled proxy addresses and preserve the original HTTPS scheme.
6. Back up uploads consistently with the database and send operational logs/alarms without exposing identity or billing secrets.

## GitHub Actions

`.github/workflows/android.yml` runs on pushes to `main` and manual `workflow_dispatch`; it does not declare a pull-request trigger. It grants `contents: read`, uses one Ubuntu job with a 45-minute timeout, and cancels an older in-progress run for the same `android-${{ github.ref }}` concurrency group.

### Repository secrets

| Secret | Use |
|---|---|
| `GOOGLE_SERVICES_JSON_BASE64` | Required base64 Firebase Android configuration, decoded to `app/google-services.json`. |
| `KEYSTORE_BASE64` | Base64 release/upload keystore. |
| `KEYSTORE_PASSWORD` | Keystore password. |
| `KEY_ALIAS` | Release/upload alias. |
| `KEY_PASSWORD` | Private-key password. |

Signing is optional, but the four signing secrets must be either all present or all absent. Reconstructed files use owner-only permissions and are deleted in an `always()` cleanup step.

### Repository variables

| Variable | Use |
|---|---|
| `API_BASE_URL` | Android API base URL. |
| `PRIVACY_URL` | Privacy-policy URL. |
| `TERMS_URL` | Terms URL. |
| `ACCOUNT_DELETION_URL` | External account-deletion URL. |
| `GOOGLE_WEB_CLIENT_ID` | Web OAuth client ID for Google sign-in. |

The current workflow expressions also accept same-name secret fallbacks for `API_BASE_URL` and `GOOGLE_WEB_CLIENT_ID`; repository variables are the primary configuration. The workflow requires all four URL values to be non-empty and start with `https://`. It sets `BUILD_NUMBER=${{ github.run_number }}`, so Android `versionCode` equals the GitHub run number; a rerun retains that run number.

### CI behavior

1. Check out source, install Temurin JDK 17, validate the Gradle wrapper, and enable Gradle caching.
2. Validate URL inputs.
3. Decode and JSON-validate `GOOGLE_SERVICES_JSON_BASE64`.
4. Configure release signing only when all four signing secrets exist.
5. Always run `testDebugUnitTest`, `lintDebug`, and `assembleDebug` with the five configured properties plus `BUILD_NUMBER`.
6. Run `bundleRelease` and `assembleRelease` only when signing is configured.
7. Always remove reconstructed Firebase/signing files.
8. Always upload available debug/release APKs, release AABs, JVM test reports, and debug lint HTML/XML as `jawlinefit-android-${{ github.run_number }}` for 14 days; absence produces a warning.

This workflow builds Android artifacts. It does not publish to Play or deploy the PHP backend.

## Google Play and RTDN checklist

- Create the Play app with package `com.jawlinefit.app` and complete Play App Signing setup.
- Add the Play App Signing SHA-1/SHA-256 certificate to Firebase in addition to debug and release/upload certificates.
- Create subscription products and base plans in Play Console, then create matching active plan rows through the backend administration console. Product IDs are operator-defined and are not hardcoded in the Android or backend source. Each `(product_id, base_plan_id)` and `duration_days` value must match the Play offer used by the app.
- Activate each base plan, configure regional availability/pricing, and verify purchases with license-test accounts on a Play-distributed build.
- Enable the Google Play Android Developer API, link the Google Cloud project, and grant the backend service account the Play Console permissions needed to read and acknowledge subscriptions. Configure `GOOGLE_SERVICE_ACCOUNT_JSON` outside the web root.
- Create the RTDN Pub/Sub topic and grant `google-play-developer-notifications@system.gserviceaccount.com` Pub/Sub Publisher access. Link that topic in Play Console.
- Create an authenticated push subscription targeting the public HTTPS `/api/play-rtdn` route. Configure OIDC with the service account and exact audience represented by `GOOGLE_RTDN_SERVICE_ACCOUNT_EMAIL` and `GOOGLE_RTDN_AUDIENCE`; configure delivery retries, monitoring, and dead-letter handling.
- Publish the privacy, terms, and account-deletion pages. Set the Play account-deletion URL to the deployed `ACCOUNT_DELETION_URL` and verify both the signed-in app flow and public ownership-verification flow.
- Complete Data Safety accurately: face/progress photos are stored and analyzed on device and are not collected by the backend. Review and disclose other transmitted data, including authentication identifiers, FCM token, subscription verification data, diagnostics, and analytics, according to the enabled services and release behavior.
- Complete the Play Console **Health apps declaration** for the app’s wellness/exercise functionality.
- Complete the content-rating questionnaire, target-audience settings, ads declaration, app-access instructions, privacy disclosures, and store listing.
- Upload a signed AAB whose `versionCode` is greater than every prior release and verify package name, signing certificate, Firebase sign-in, billing, RTDN, and policy links in internal testing before broader rollout.

## Local builds and tests

From the Android project directory with JDK 17 and `app/google-services.json` present:

```bash
cd "$ANDROID_ROOT"
./gradlew testDebugUnitTest lintDebug assembleDebug \
  -PAPI_BASE_URL="$API_BASE_URL" \
  -PPRIVACY_URL="$PRIVACY_URL" \
  -PTERMS_URL="$TERMS_URL" \
  -PACCOUNT_DELETION_URL="$ACCOUNT_DELETION_URL" \
  -PGOOGLE_WEB_CLIENT_ID="$GOOGLE_WEB_CLIENT_ID" \
  -PBUILD_NUMBER="$BUILD_NUMBER"
```

With `signing.properties` configured, build release artifacts with:

```bash
./gradlew bundleRelease assembleRelease \
  -PAPI_BASE_URL="$API_BASE_URL" \
  -PPRIVACY_URL="$PRIVACY_URL" \
  -PTERMS_URL="$TERMS_URL" \
  -PACCOUNT_DELETION_URL="$ACCOUNT_DELETION_URL" \
  -PGOOGLE_WEB_CLIENT_ID="$GOOGLE_WEB_CLIENT_ID" \
  -PBUILD_NUMBER="$BUILD_NUMBER"
```

The JVM suite covers jaw-estimator behavior, billing-period conversion, completion streaks, scoped content caching/filtering, the exact HTTP contract, and civil-time reminder behavior. There are no connected-device tests in the current source tree.

Validate the backend from `web-backend`:

```bash
composer validate --strict
composer install --no-dev --classmap-authoritative
find . -path './vendor' -prune -o -path './.verification' -prune -o -name '*.php' -print0 | xargs -0 -n1 php -l
```

The backend currently has no repository-defined automated test suite. Exercise authentication rejection, API envelopes, rate limits, subscription ownership/state transitions, authenticated and duplicate RTDN delivery, admin authentication/CSRF, media limits/types, public deletion verification, and signed-in deletion in a staging environment.

On GitHub, configure the five variables and `GOOGLE_SERVICES_JSON_BASE64`, then run **Android** manually or push to `main`. Add all four signing secrets to receive signed release AAB/APK artifacts.

## Release checklist

1. Review the diff and confirm only intended source/configuration is tracked; keep Firebase JSON, keystores, credentials, `vendor`, runtime state, and uploaded media out of Git.
2. Set production Android URLs and Google web client ID; verify all four URLs over HTTPS.
3. Enable Firebase Phone and Google providers and register SHA-1/SHA-256 for debug, release/upload, and Play App Signing certificates.
4. Configure the production backend environment, including operator/support identity, database, Firebase project, Play service account, authenticated RTDN values, external runtime paths, and `APP_ENCRYPTION_KEY`.
5. Install Composer dependencies, run setup once, remove schema privileges, and verify the setup lock.
6. Apply PHP upload limits, HTTPS routing, authorization-header forwarding, private-path denial, upload script denial, mail delivery, backups, monitoring, and restore procedures.
7. Create and activate Play subscription products/base plans through Play Console and matching plan rows through backend admin; verify billing and entitlement expiry with license testers.
8. Complete RTDN, Data Safety, deletion URL, Health apps declaration, content rating, app access, target audience, and store listing requirements.
9. Run Android JVM tests, lint, debug build, signed release AAB/APK build, Composer validation, and PHP syntax checks from a clean checkout.
10. Inspect the signed AAB for package `com.jawlinefit.app`, increasing `versionCode`, expected certificate, production URLs, and absence of embedded private credentials.
11. Validate Phone/Google sign-in, content locks, purchase/restore, acknowledgement, renewal/cancellation/expiry refresh, FCM registration, policy links, and both deletion paths in staging/internal testing.
12. Upload the signed AAB to an internal Play track, complete review gates, monitor crash/ANR/backend/RTDN signals, and promote through controlled rollout stages.
