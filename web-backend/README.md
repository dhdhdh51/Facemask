# JawlineFit PHP backend

Production PHP 8.1+ and MySQL backend for the JawlineFit Android product. It provides the authenticated app API, server-authoritative Google Play entitlement verification, authenticated Pub/Sub RTDN processing, a mobile-first administration console, one-time installation, protected media handling, and public policy/account-deletion pages.

The implementation is framework-free. Database access uses PDO native prepared statements. No account, administrator, content record, plan, credential, or media file is seeded.

## Requirements

- PHP 8.1 through 8.4 with `curl`, `fileinfo`, `gd`, `json`, `mbstring`, `openssl`, and `pdo_mysql`; `exif` is recommended for JPEG orientation.
- MySQL 8.0+ or MariaDB 10.6+ with JSON column support.
- Composer 2.
- Apache 2.4 with `mod_rewrite` and `mod_headers`, or nginx with PHP-FPM.
- `ffprobe` and `ffmpeg` for H.264 validation and video thumbnails.
- An HTTPS hostname.
- A Firebase project, a Google Play service account with Android Publisher access, and authenticated Pub/Sub push for RTDN.
- A working PHP `mail()` transport for web deletion-request ownership verification.

Bootstrap 5 is served locally from `public/assets`. The compiled CSS, compiled JavaScript bundle, and upstream MIT license are included. No admin or public page depends on a CDN.

## Directory map

- `api/index.php` and `admin/index.php`: protected compatibility entry points.
- `public/index.php`: front controller for all pretty URLs.
- `includes/`: application classes and bootstrap code; blocked from HTTP access.
- `public/assets/`: local Bootstrap and product CSS/JavaScript.
- `uploads/`: generated media and thumbnails; script execution is denied by its `.htaccess`.
- `bin/setup.php`: interactive CLI installer.
- `install.sql`: complete strict-mode schema with no data inserts; subscription tokens and authoritative Google responses are stored only in encrypted/hash-backed columns.
- `config.php`: production-safe loader with no embedded secrets.
- `config.sample.php`: environment-backed configuration reference.

Point the virtual-host document root at the `web-backend` directory, not at `public`, because `/uploads` and `/public/assets` are intentionally served directly while every application route is sent to `public/index.php`. Direct access to configuration, source, schema, Composer metadata, and dependencies is denied.

## Configuration

`config.php` intentionally loads `config.sample.php`; both files are safe to keep with the application because secrets come from the process environment. Keep the Google service-account file outside this directory and outside every public document root.

| Variable | Required | Description |
|---|---:|---|
| `APP_ENV` | no | Runtime name; defaults to `production`. |
| `APP_BASE_URL` | yes | Public HTTPS origin without a trailing slash. Used in deletion verification links. |
| `APP_TIMEZONE` | no | PHP display timezone; defaults to UTC. Database timestamps remain UTC. |
| `FORCE_HTTPS` | production | Defaults to `1`. Redirects unencrypted requests with 308. |
| `TRUSTED_PROXIES` | when proxied | Comma-separated exact proxy IPs permitted to supply forwarding headers. |
| `PUBLIC_EMAIL_FROM` | yes | Valid sender used by deletion ownership email. Configure SPF/DKIM for its domain. |
| `DELETE_VERIFICATION_TTL` | no | Verification-link lifetime in seconds; defaults to 86400. |
| `PUBLIC_DELETE_RATE_LIMIT`, `PUBLIC_DELETE_RATE_WINDOW` | no | Public deletion-form limit; defaults to 3 requests per 3600 seconds per IP. |
| `APP_LOG_PATH` | production | Error log file outside the web root. Production entries contain only time, exception class, and a random reference. |
| `APP_CACHE_PATH` | production | Firebase and Google public-key cache directory outside the web root. |
| `SETUP_LOCK_PATH` | production | Permanent setup lock outside the web root. |
| `PUBLIC_OPERATOR_NAME`, `PUBLIC_SUPPORT_EMAIL` | yes | Operator identity and support address rendered on public pages. |
| `CHILDREN_MINIMUM_AGE` | no | Public minimum-age policy; defaults to 13. |
| `ACCOUNT_RETENTION_DAYS` | no | Scheduled maximum residual-backup retention after account deletion; defaults to 30 days. |
| `DELETE_AUDIT_RETENTION_DAYS` | no | Required deletion-request audit retention; defaults to 1095 days. |
| `DB_DSN` | yes | Example: `mysql:host=127.0.0.1;port=3306;dbname=jawlinefit;charset=utf8mb4`. |
| `DB_USER`, `DB_PASSWORD` | yes | Least-privilege MySQL credentials. |
| `FIREBASE_PROJECT_ID` | yes | Exact Firebase project ID used as token audience and issuer suffix. |
| `GOOGLE_PLAY_PACKAGE_NAME` | yes | Exact Android application ID. It is never accepted from the app request. |
| `GOOGLE_SERVICE_ACCOUNT_JSON` | yes | Absolute path to service-account JSON, or complete JSON injected by a secrets manager. |
| `GOOGLE_RTDN_AUDIENCE` | yes | Exact Pub/Sub OIDC audience, normally `https://host/api/play-rtdn`. |
| `GOOGLE_RTDN_SERVICE_ACCOUNT_EMAIL` | yes | Exact authenticated-push service-account email. |
| `IMAGE_MAX_MB` | no | JPEG, PNG, and WebP limit; defaults to 5 MB. |
| `GIF_MAX_MB` | no | GIF limit; defaults to 10 MB. |
| `THUMBNAIL_MAX_MB` | no | Manual thumbnail limit; defaults to 10 MB. |
| `VIDEO_MAX_MB` | no | Video limit; defaults to 50 MB. |
| `FFMPEG_PATH`, `FFPROBE_PATH` | no | Binary paths; default to names on `PATH`. |
| `ADMIN_SESSION_NAME` | no | Separate admin cookie name. |
| `ADMIN_SESSION_LIFETIME` | no | Admin inactivity timeout; defaults to 7200 seconds. |
| `ADMIN_LOGIN_ATTEMPTS`, `ADMIN_LOGIN_WINDOW` | no | Defaults to 5 attempts per 900 seconds per IP and normalized email. |
| `API_RATE_LIMIT`, `API_RATE_WINDOW` | no | General API limit; defaults to 120 requests per 60 seconds per client IP. |
| `RTDN_RATE_LIMIT`, `RTDN_RATE_WINDOW` | no | RTDN limit; defaults to 300 requests per 60 seconds per client IP. |
| `BILLING_RATE_LIMIT`, `BILLING_RATE_WINDOW` | no | Additional billing verification limit per IP and authenticated user; defaults to 10 per 300 seconds. |
| `DELETE_RATE_LIMIT`, `DELETE_RATE_WINDOW` | no | Additional signed-in account-deletion limit per IP and authenticated user; defaults to 3 per 3600 seconds. |
| `DELETE_RECENT_AUTH_SECONDS` | no | Maximum Firebase authentication age for account deletion; defaults to 900 seconds. |

For a reverse proxy, set `TRUSTED_PROXIES` only to the proxy addresses controlled by the operator. The backend ignores forwarding headers from every other source.

## Installation

### Common steps

1. Create an empty database with `utf8mb4` support and a temporary installer user with schema-creation privileges.
2. Set all required environment variables in the web-server or PHP-FPM service configuration.
3. Run `composer install --no-dev --classmap-authoritative` from this directory.
4. Make `uploads` writable by the PHP user. Make the parent directories of `APP_LOG_PATH`, `APP_CACHE_PATH`, and `SETUP_LOCK_PATH` writable by PHP. Keep those runtime paths outside this directory.
5. Confirm `ffprobe -version` and `ffmpeg -version` work as the PHP service user.
6. Install once with the CLI or web installer. Both require an administrator display name, email, and password of at least 12 characters. No default administrator exists.
7. Remove schema-altering privileges from the runtime database user after installation.
8. Test HTTPS redirection, HSTS, Firebase authentication, a Google Play license-test purchase, acknowledgement, RTDN renewal/cancellation/expiry updates, deletion email delivery, and upload processing in staging.

### CLI installer

Run:

```bash
php bin/setup.php
```

The password is read interactively with terminal echo disabled when the platform supports it. The installer executes `install.sql`, inserts only the supplied administrator, and writes the permanent setup lock.

### Web installer

Open `https://your-host/setup`. The form is protected by a secure session and CSRF token. Setup refuses to run if its lock exists or any administrator already exists. After a successful install, `/setup` is permanently locked.

Do not remove the lock to reset a live system. Use normal admin account management instead.

## API contract

Every API response has exactly one of these envelopes:

```json
{"success":true,"data":null}
```

```json
{"success":false,"error":"Human-readable error"}
```

All routes require `Authorization: Bearer <Firebase ID token>` except `POST /api/play-rtdn`, which requires the Google Pub/Sub OIDC bearer token. POST bodies must use `Content-Type: application/json`. Dates are RFC 3339 UTC strings. Invalid authentication returns 401, invalid authorization returns 403, missing records return 404, wrong body media type returns 415, invalid values return 422, throttling returns 429 with `Retry-After`, and unexpected failures return a sanitized 500 with a log reference.

Firebase ID tokens are accepted only after RS256 verification against Google's official Secure Token certificates. Certificates are cached according to Google's cache lifetime. Exact issuer, audience, expiry, issued-at time, positive authentication time, and non-empty bounded subject claims are validated. If a later valid token omits the optional email claim, the stored email is preserved.

### `GET /api/me`

Exact data fields: `uid` (string), `plan` (product ID, `manual`, or null), `plan_expiry` (RFC 3339 string or null), and `is_premium` (boolean). State comes only from an unexpired verified Google Play row or an unexpired administrator grant.

### `POST /api/device`

Exact request body:

```json
{"fcm_token":"opaque Firebase Cloud Messaging token"}
```

Exact data field: `fcm_token`. The authenticated user owns the token after registration; no client user ID or platform value is trusted.

### `GET /api/nuskhe?concern=&skin_type=&page=`

`concern` and `skin_type` are optional exact-match filters. `page` is a positive integer; pages contain 20 records.

Data is an array. Each item contains exactly `id`, `title`, `concern`, `skin_type`, `ingredients`, `steps`, `frequency`, `warning`, `is_premium`, `media_type`, `media_url`, and `thumbnail_url`. `ingredients` and `steps` are arrays of strings; they are empty for locked premium content.

### `GET /api/routines`

Data is an array. Each item contains exactly `id`, `title`, `description`, `duration_min`, `is_premium`, `media_type`, `media_url`, and `thumbnail_url`.

### `GET /api/routines/{id}`

Data contains exactly `id`, `title`, `description`, `duration_min`, `is_premium`, `media_type`, `media_url`, `thumbnail_url`, and `steps`. Premium detail is rejected with 403 when the server entitlement is inactive.

Steps are ordered by `sort_order`, then ID. Each step contains exactly `order`, `title`, `duration_sec`, `instruction`, `media_type`, `media_url`, and `thumbnail_url`.

### `GET /api/tips`

Data is an array. Each item contains exactly `id`, `category`, `text`, `media_type`, `media_url`, and `thumbnail_url`.

### `GET /api/plans`

Data is an array. Each active plan contains exactly `id`, `name`, `product_id`, `base_plan_id`, and `duration_days`. A plan is identified by the `(product_id, base_plan_id)` pair.

### `POST /api/billing/verify`

Exact request body fields are `product_id` and `purchase_token`. The package name is configuration, not client input.

The server confirms the supplied product has an active local plan, rejects a token already owned by another user, calls Android Publisher `purchases.subscriptionsv2.get`, and requires a returned line item whose `productId` equals the supplied product and whose `offerDetails.basePlanId` completes an active local `(product_id, base_plan_id)` pair. It stores the complete authoritative response encrypted and acknowledges an eligible pending purchase server-side. Exact data fields remain `is_premium` and `plan_expiry`.

### `POST /api/play-rtdn`

Configure Pub/Sub authenticated push with the exact audience and service account from configuration. The endpoint verifies Google's RS256 signature and issuer, audience, expiry, subject, verified email, and exact service-account email against Google's cached public keys. It validates the configured package, deduplicates `messageId`, hashes a known local purchase token, and refreshes it against Android Publisher so renewal, cancellation, and expiry notifications update the authoritative row. Notification contents never directly grant or revoke access. If authoritative refresh fails, the deduplication claim is removed so Pub/Sub can retry.

### `DELETE /api/me`

Deletes the authenticated local user. Foreign-key cascades remove devices, subscriptions, purchase tokens, and stored Google responses in the same database transaction. Data is null in the success envelope. Firebase Authentication identity deletion remains the app/Firebase responsibility. The public ownership-verification flow supports users who cannot sign in.

## Administration

Open `/admin`. The interface is mobile-first and uses only local Bootstrap assets. The administrator session uses an HTTP-only, SameSite Strict cookie scoped to `/admin`, strict cookie mode, inactivity expiry, user-agent binding, and ID rotation after authentication and password changes. Login attempts are limited. All state-changing forms use CSRF tokens and all rendered database/user values are escaped.

Functions provided:

- dashboard totals;
- login, POST logout, and password change;
- searchable, paginated Nuskhe, Routine, Tip, Plan, and User lists;
- create, read, update, and delete content and plans;
- routine step create, edit, delete, and drag/touch reorder;
- manual premium extension and revocation without changing authoritative Play rows;
- searchable, paginated administrator accounts with create, edit-name/email, optional password replacement, enable/disable, and delete; self and last-active guards apply only to unsafe disable/delete actions and are enforced transactionally;
- ownership state and processing for deletion requests. Completion is blocked until email ownership is verified; completion deletes matched user data while retaining the legally required request audit separately.

On desktop the console uses a fixed sidebar. At 767 pixels and below it uses a fixed bottom navigation bar, converts content, user, administrator, and deletion-request tables into stacked cards without horizontal scrolling, and enlarges form/action controls for touch. Routine steps wrap at 360 pixels.

Content and step forms provide phone camera/gallery selection, immediate image/video preview, immediate manual-thumbnail preview, XHR upload progress, current-media links, replacement, deletion, and optional video thumbnail upload.

## Media security and processing

The upload service verifies PHP upload status and `finfo` MIME, ignores client filenames/extensions, and generates 40-character random names. Accepted types are JPEG, PNG, WebP, GIF, and MP4 with an H.264 primary video stream confirmed by `ffprobe`.

- JPEG, PNG, and WebP are decoded and re-encoded, stripping metadata. JPEG EXIF orientation is applied when available.
- Raster dimensions are constrained to 1280 pixels on the longest side.
- GIFs must decode successfully; the first frame generates the thumbnail while the validated animation is retained.
- Thumbnails are JPEG, 400 pixels wide, and generated server-side.
- Videos use a validated manual image, an `ffmpeg` frame, or a generated neutral image when frame extraction is unavailable.
- New files are removed if persistence fails. Old media is removed only after its database replacement/delete succeeds.
- `uploads/.htaccess` disables indexes, CGI, PHP handlers, and common script extensions.

The application limits are 5 MB for JPEG/PNG/WebP images, 10 MB for GIF, 10 MB for optional manual thumbnails, and 50 MB for video by default. Configure them with `IMAGE_MAX_MB`, `GIF_MAX_MB`, `THUMBNAIL_MAX_MB`, and `VIDEO_MAX_MB`. The web server and PHP limits must be slightly higher than the application limit so the backend can return useful validation errors.

Recommended `php.ini` or PHP-FPM pool settings:

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

Restart PHP-FPM or Apache after changing PHP settings. On shared hosting, use the provider's PHP options screen when `.user.ini` is supported; the included Apache block applies equivalent upload sizes only when `mod_php` is in use.

## Public pages and claims policy

`/privacy`, `/terms`, `/disclaimer`, and `/delete-account` are responsive. Every public page states: results vary, facial structure is largely genetic, and this is not medical advice. Product copy does not make prohibited outcome promises.

The deletion form uses a CSRF token, honeypot, configurable IP limit, generic submission response, random single-use token, hashed token storage, hashed requester IP, and emailed ownership link. Completed and rejected requests are terminal. The admin cannot complete an unverified request. A request with a Firebase UID must match that UID and verified email exactly; a request without a UID must match exactly one account by email, otherwise it remains pending for operator follow-up. Completion deletes that one user and cascaded device/subscription data, clears matching and anti-abuse identifiers that are no longer needed, and retains only the minimum request audit for the configured legal period. Recurring Google Play billing must be cancelled separately by the user.

## Apache

Enable `mod_rewrite` and `mod_headers`, permit overrides for this directory, and point the HTTPS virtual host at `web-backend`. The root `.htaccess` routes pretty URLs and blocks private paths. Preserve `uploads/.htaccess`.

Example virtual host core:

```apache
<VirtualHost *:443>
    ServerName jawlinefit.example
    DocumentRoot /var/www/jawlinefit/web-backend
    <Directory /var/www/jawlinefit/web-backend>
        AllowOverride All
        Require all granted
    </Directory>
    SSLEngine on
</VirtualHost>
```

Create a separate port-80 virtual host that redirects to HTTPS.

## nginx and PHP-FPM

Adjust host, TLS, filesystem, and socket paths:

```nginx
server {
    listen 443 ssl http2;
    server_name jawlinefit.example;
    root /var/www/jawlinefit/web-backend;
    index public/index.php;
    client_max_body_size 64m;

    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header Content-Security-Policy "default-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'self'" always;

    location ^~ /public/assets/ { try_files $uri =404; access_log off; }
    location ^~ /uploads/ {
        try_files $uri =404;
        location ~* \.(php|phtml|phar|cgi|pl|py|sh)$ { deny all; }
    }
    location ~ ^/(config|includes|vendor|bin|storage)/ { deny all; }
    location ~ ^/(install\.sql|composer\.(json|lock)|config(?:\.sample)?\.php)$ { deny all; }

    location / { try_files $uri /public/index.php?$query_string; }
    location = /public/index.php {
        include fastcgi_params;
        fastcgi_param SCRIPT_FILENAME $document_root/public/index.php;
        fastcgi_param HTTP_AUTHORIZATION $http_authorization;
        fastcgi_pass unix:/run/php/php8.3-fpm.sock;
    }
    location ~ \.php$ { deny all; }
}
```

Use a separate server block on port 80 with `return 308 https://$host$request_uri;`.

## cPanel deployment

1. Create the database/user with the MySQL Database Wizard and assign all privileges for installation.
2. Upload this directory outside `public_html` when the host permits an addon-domain document root there. Otherwise place only this dedicated domain's document root at the backend directory; do not mix it with another site's files.
3. Select PHP 8.1+ and enable the required extensions in Select PHP Version.
4. Set `upload_max_filesize`, `post_max_size`, time limits, and memory limit in MultiPHP INI Editor.
5. Use cPanel Terminal to run Composer and `php bin/setup.php`. If Terminal is unavailable, run Composer during build, upload `vendor`, then use `/setup` exactly once.
6. Configure environment variables through the host's application/environment facility. Do not put credentials in publicly readable Apache configuration.
7. Configure a real mailbox sender for `PUBLIC_EMAIL_FROM`, then verify mail delivery and DNS authentication.
8. Confirm the domain has AutoSSL, HTTPS redirect, and `.htaccess` override support.
9. Remove database schema privileges after setup if the host supports granular privilege changes.

## VPS deployment

1. Install nginx or Apache, PHP-FPM 8.1+, required PHP extensions, MySQL/MariaDB, Composer, ffmpeg, and a mail transport.
2. Create a dedicated Unix user and PHP-FPM pool. The code should be read-only to the PHP worker except `uploads` and the external runtime directories.
3. Store environment variables in a root-readable systemd environment file or secret manager, not shell history.
4. Run Composer and CLI setup as the deployment user.
5. Configure TLS with the provider of choice, redirect port 80, and reload the web server/PHP-FPM.
6. Configure firewall access only for SSH, HTTP redirect, and HTTPS. Keep MySQL private.
7. Configure log rotation for `APP_LOG_PATH`, database backups, upload backups, monitoring, and security updates.

## EC2 deployment

1. Use a current supported Linux AMI, an instance role only where appropriate, and a security group allowing SSH from operator IPs plus public 80/443. Keep database port 3306 private.
2. Prefer RDS with TLS, automated backups, and a private subnet. Use a least-privilege application database user.
3. Install the VPS dependencies or build an immutable image. Put secrets in AWS Secrets Manager or SSM Parameter Store and inject them into PHP-FPM at service start.
4. Keep Google credentials outside the document root with restrictive ownership. If JSON content is injected directly, ensure process/environment access is restricted.
5. Put TLS at an Application Load Balancer or the instance. When using a load balancer, trust only its controlled proxy addresses and preserve the original HTTPS scheme.
6. Use EBS snapshots or object-storage synchronization for uploads together with consistent database backups. Test restores.
7. Send operational logs and alarms to the selected monitoring service without exposing tokens or purchase data.

## Operations and verification

Back up the database and uploaded media as one logical dataset. Rotate service-account keys, monitor application logs and Pub/Sub dead-letter delivery, prune completed request audits according to legal policy, test restore procedures, and apply PHP/Composer/OS updates.

Recommended release checks:

```bash
composer validate --strict
composer install --no-dev --classmap-authoritative
find . -path './vendor' -prune -o -path './.verification' -prune -o -name '*.php' -print0 | xargs -0 -n1 php -l
```

Also test Firebase issuer/audience rejection, expired ID tokens, OIDC audience/email rejection, duplicate RTDN messages, ownership conflicts for purchase tokens, expired subscription state, all API envelopes, admin login limits, CSRF rejection, content pagination/search, upload limits/types, media replacement cleanup, ownership email links, and signed-in account deletion.
