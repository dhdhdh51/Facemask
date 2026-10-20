<?php
declare(strict_types=1);

use JawlineFit\ConfigValidator;
use JawlineFit\Database;
use JawlineFit\Http;
use JawlineFit\HttpException;

$root = dirname(__DIR__);
$configFile = $root . '/config.php';
if (!is_file($configFile)) {
    http_response_code(503);
    exit('Configuration is missing.');
}
$config = require $configFile;
date_default_timezone_set((string) ($config['app']['timezone'] ?? 'UTC'));

$autoload = $root . '/vendor/autoload.php';
if (!is_file($autoload)) {
    http_response_code(503);
    exit('Dependencies are missing.');
}
require $autoload;
require_once __DIR__ . '/functions.php';
ConfigValidator::validate($config, $root);

$secure = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off');
if (!$secure && in_array($_SERVER['REMOTE_ADDR'] ?? '', $config['app']['trusted_proxies'], true)) {
    $secure = strtolower($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https';
}
if ($config['app']['force_https'] && !$secure && PHP_SAPI !== 'cli') {
    $baseUrl = rtrim((string) ($config['app']['base_url'] ?? ''), '/');
    if (filter_var($baseUrl, FILTER_VALIDATE_URL) && str_starts_with($baseUrl, 'https://')) {
        header('Location: ' . $baseUrl . ($_SERVER['REQUEST_URI'] ?? '/'), true, 308);
        exit;
    }
    http_response_code(503);
    exit('HTTPS origin is not configured.');
}
$GLOBALS['jawlinefit_secure_request'] = $secure;

header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header('Referrer-Policy: strict-origin-when-cross-origin');
header("Permissions-Policy: camera=(self), microphone=(), geolocation=()");
header("Content-Security-Policy: default-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'self'");
if (str_starts_with(parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/', '/admin')) {
    header('Cache-Control: no-store, private');
    header('Pragma: no-cache');
}
if ($secure) {
    header('Strict-Transport-Security: max-age=31536000; includeSubDomains');
}

set_exception_handler(static function (Throwable $error) use ($config): void {
    $requestId = bin2hex(random_bytes(8));
    $isApi = str_starts_with(parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/', '/api/');
    $isExpectedClientError = $error instanceof HttpException && $error->status >= 400 && $error->status < 500;
    if (!$isExpectedClientError) {
        $path = (string) $config['app']['log_path'];
        $directory = dirname($path);
        if (!is_dir($directory)) {
            @mkdir($directory, 0750, true);
        }
        $line = sprintf("[%s] reference=%s class=%s\n", gmdate('c'), $requestId, $error::class);
        @error_log($line, 3, $path);
    }
    if ($error instanceof HttpException) {
        if ($isApi) {
            Http::error($error->status, $error->getMessage());
        }
        http_response_code($error->status);
        echo '<!doctype html><meta charset="utf-8"><title>Error</title><h1>' . e($error->getMessage()) . '</h1>';
        exit;
    }
    if ($isApi) {
        Http::error(500, 'An unexpected error occurred. Reference: ' . $requestId);
    }
    http_response_code(500);
    echo '<!doctype html><meta charset="utf-8"><title>Error</title><h1>An unexpected error occurred.</h1><p>Reference: ' . e($requestId) . '</p>';
});

$db = Database::connect($config['db']);
