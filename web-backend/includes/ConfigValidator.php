<?php
declare(strict_types=1);

namespace JawlineFit;

final class ConfigValidator
{
    public static function validate(array $config, string $webRoot): void
    {
        $production = strtolower((string) ($config['app']['env'] ?? 'production')) === 'production';
        if (!$production) {
            return;
        }
        $required = [
            'APP_BASE_URL' => $config['app']['base_url'] ?? '',
            'PUBLIC_EMAIL_FROM' => $config['app']['public_email_from'] ?? '',
            'PUBLIC_OPERATOR_NAME' => $config['legal']['operator_name'] ?? '',
            'PUBLIC_SUPPORT_EMAIL' => $config['legal']['support_email'] ?? '',
            'DB_USER' => $config['db']['user'] ?? '',
            'DB_PASSWORD' => $config['db']['password'] ?? '',
            'FIREBASE_PROJECT_ID' => $config['firebase']['project_id'] ?? '',
            'GOOGLE_PLAY_PACKAGE_NAME' => $config['google_play']['package_name'] ?? '',
            'GOOGLE_SERVICE_ACCOUNT_JSON' => $config['google_play']['service_account_json'] ?? '',
            'GOOGLE_RTDN_AUDIENCE' => $config['google_play']['rtdn_audience'] ?? '',
            'GOOGLE_RTDN_SERVICE_ACCOUNT_EMAIL' => $config['google_play']['rtdn_service_account_email'] ?? '',
            'APP_ENCRYPTION_KEY' => $config['security']['encryption_key'] ?? '',
        ];
        foreach ($required as $name => $value) {
            if (trim((string) $value) === '') {
                throw new \RuntimeException($name . ' is required in production');
            }
        }
        $baseUrl = (string) $config['app']['base_url'];
        $parts = parse_url($baseUrl);
        if (!is_array($parts) || ($parts['scheme'] ?? '') !== 'https' || empty($parts['host'])) {
            throw new \RuntimeException('APP_BASE_URL must be an absolute HTTPS URL in production');
        }
        if (!(bool) ($config['app']['force_https'] ?? false)) {
            throw new \RuntimeException('FORCE_HTTPS must be enabled in production');
        }
        $dsn = (string) ($config['db']['dsn'] ?? '');
        if (!str_starts_with($dsn, 'mysql:')) {
            throw new \RuntimeException('DB_DSN must use the mysql PDO driver in production');
        }
        foreach (['public_email_from' => 'PUBLIC_EMAIL_FROM'] as $key => $name) {
            if (!filter_var($config['app'][$key], FILTER_VALIDATE_EMAIL)) {
                throw new \RuntimeException($name . ' must be a valid email address');
            }
        }
        if (!filter_var($config['legal']['support_email'], FILTER_VALIDATE_EMAIL)) {
            throw new \RuntimeException('PUBLIC_SUPPORT_EMAIL must be a valid email address');
        }
        new Encryption((string) $config['security']['encryption_key']);
        $positive = [
            'DELETE_VERIFICATION_TTL' => $config['app']['delete_verification_ttl'] ?? 0,
            'PUBLIC_DELETE_RATE_LIMIT' => $config['app']['delete_request_limit'] ?? 0,
            'PUBLIC_DELETE_RATE_WINDOW' => $config['app']['delete_request_window'] ?? 0,
            'CHILDREN_MINIMUM_AGE' => $config['legal']['children_minimum_age'] ?? 0,
            'ACCOUNT_RETENTION_DAYS' => $config['legal']['account_retention_days'] ?? 0,
            'DELETE_AUDIT_RETENTION_DAYS' => $config['legal']['request_audit_retention_days'] ?? 0,
            'IMAGE_MAX_MB' => $config['uploads']['image_max_bytes'] ?? 0,
            'GIF_MAX_MB' => $config['uploads']['gif_max_bytes'] ?? 0,
            'THUMBNAIL_MAX_MB' => $config['uploads']['thumbnail_max_bytes'] ?? 0,
            'VIDEO_MAX_MB' => $config['uploads']['video_max_bytes'] ?? 0,
            'ADMIN_SESSION_LIFETIME' => $config['security']['session_lifetime'] ?? 0,
            'ADMIN_LOGIN_ATTEMPTS' => $config['security']['login_attempts'] ?? 0,
            'ADMIN_LOGIN_WINDOW' => $config['security']['login_window'] ?? 0,
            'API_RATE_LIMIT' => $config['security']['api_rate_limit'] ?? 0,
            'API_RATE_WINDOW' => $config['security']['api_rate_window'] ?? 0,
            'RTDN_RATE_LIMIT' => $config['security']['rtdn_rate_limit'] ?? 0,
            'RTDN_RATE_WINDOW' => $config['security']['rtdn_rate_window'] ?? 0,
            'BILLING_RATE_LIMIT' => $config['security']['billing_rate_limit'] ?? 0,
            'BILLING_RATE_WINDOW' => $config['security']['billing_rate_window'] ?? 0,
            'DELETE_RATE_LIMIT' => $config['security']['delete_rate_limit'] ?? 0,
            'DELETE_RATE_WINDOW' => $config['security']['delete_rate_window'] ?? 0,
            'DELETE_RECENT_AUTH_SECONDS' => $config['security']['delete_recent_auth_seconds'] ?? 0,
        ];
        foreach ($positive as $name => $value) {
            if (!is_int($value) || $value < 1) {
                throw new \RuntimeException($name . ' must be a positive integer in production');
            }
        }
        if ((int) $config['legal']['children_minimum_age'] > 18) {
            throw new \RuntimeException('CHILDREN_MINIMUM_AGE must not exceed 18');
        }
        foreach (['log_path' => 'APP_LOG_PATH', 'cache_path' => 'APP_CACHE_PATH', 'setup_lock' => 'SETUP_LOCK_PATH'] as $key => $name) {
            $path = (string) ($config['app'][$key] ?? '');
            self::assertOutsideWebRoot($path, $webRoot, $name, false);
            self::assertCreatableRuntimePath($path, $name, $key === 'cache_path');
        }
        $credential = trim((string) $config['google_play']['service_account_json']);
        if (!str_starts_with($credential, '{')) {
            self::assertOutsideWebRoot($credential, $webRoot, 'GOOGLE_SERVICE_ACCOUNT_JSON', true);
        }
        if ((bool) ($config['db']['tls_required'] ?? false)) {
            self::assertOutsideWebRoot((string) ($config['db']['ssl_ca'] ?? ''), $webRoot, 'DB_SSL_CA', true);
        }
    }

    private static function assertOutsideWebRoot(string $path, string $webRoot, string $name, bool $mustExist): void
    {
        if ($path === '' || !self::isAbsolute($path)) {
            throw new \RuntimeException($name . ' must be an absolute path outside the web root');
        }
        $normalizedRoot = rtrim(str_replace('\\', '/', realpath($webRoot) ?: $webRoot), '/');
        $normalizedPath = str_replace('\\', '/', $mustExist ? (realpath($path) ?: '') : self::normalize($path));
        if ($normalizedPath === '' || $normalizedPath === $normalizedRoot || str_starts_with($normalizedPath, $normalizedRoot . '/')) {
            throw new \RuntimeException($name . ' must be outside the web root');
        }
        if ($mustExist && (!is_file($normalizedPath) || !is_readable($normalizedPath))) {
            throw new \RuntimeException($name . ' must reference a readable file');
        }
    }

    private static function assertCreatableRuntimePath(string $path, string $name, bool $directoryPath): void
    {
        if (($directoryPath && is_file($path)) || (!$directoryPath && is_dir($path))) {
            throw new \RuntimeException($name . ' has the wrong filesystem type');
        }
        if (!$directoryPath && is_file($path) && !is_writable($path)) {
            throw new \RuntimeException($name . ' must be writable by PHP');
        }
        $parent = $directoryPath && is_dir($path) ? $path : ($directoryPath ? $path : dirname($path));
        while (!is_dir($parent)) {
            $next = dirname($parent);
            if ($next === $parent) {
                throw new \RuntimeException($name . ' has no accessible parent directory');
            }
            $parent = $next;
        }
        if (!is_writable($parent)) {
            throw new \RuntimeException($name . ' parent directory must be writable by PHP');
        }
    }

    private static function isAbsolute(string $path): bool
    {
        return str_starts_with($path, '/') || preg_match('/^[A-Za-z]:[\\\\\/]/', $path) === 1;
    }

    private static function normalize(string $path): string
    {
        $parts = [];
        foreach (explode('/', str_replace('\\', '/', $path)) as $part) {
            if ($part === '' || $part === '.') {
                continue;
            }
            if ($part === '..') {
                array_pop($parts);
                continue;
            }
            $parts[] = $part;
        }
        return '/' . implode('/', $parts);
    }
}
