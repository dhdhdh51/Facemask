<?php
declare(strict_types=1);

require dirname(__DIR__) . '/includes/bootstrap.php';

use JawlineFit\AdminController;
use JawlineFit\ApiController;
use JawlineFit\Http;
use JawlineFit\HttpException;
use JawlineFit\PublicController;
use JawlineFit\Setup;

$method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');
$allowedMethods = ['GET','POST','DELETE','HEAD'];
if (!in_array($method, $allowedMethods, true)) {
    header('Allow: GET, POST, DELETE, HEAD');
    throw new HttpException(405, 'method_not_allowed', 'HTTP method is not allowed');
}
$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$path = '/' . ltrim(rawurldecode($path), '/');
if (str_contains($path, "\0") || str_contains($path, '..')) {
    throw new HttpException(400, 'invalid_path', 'Request path is invalid');
}
$path = $path !== '/' ? rtrim($path, '/') : $path;
if ($method === 'HEAD') {
    ob_start(static fn(string $buffer): string => '');
    $method = 'GET';
}

if ($path === '/setup') {
    if (session_status() !== PHP_SESSION_ACTIVE) {
        session_name('jawlinefit_setup');
        session_set_cookie_params(['lifetime'=>0,'path'=>'/setup','secure'=>(bool) ($GLOBALS['jawlinefit_secure_request'] ?? false),'httponly'=>true,'samesite'=>'Strict']);
        session_start();
    }
    $setup = new Setup($db, $config);
    if ($setup->isLocked()) {
        http_response_code(403);
        echo '<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Setup locked</title><h1>Setup is locked</h1><p>An administrator already exists.</p>';
        exit;
    }
    if ($method === 'POST') {
        verify_csrf();
        $setup->install((string)($_POST['email']??''),(string)($_POST['password']??''),(string)($_POST['display_name']??''));
        Http::redirect('/admin/login');
    }
    echo '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>JawlineFit setup</title><link rel="stylesheet" href="/public/assets/bootstrap.min.css"></head><body><main class="container py-5" style="max-width:640px"><h1>One-time setup</h1><p>Creates the first administrator and permanently locks setup.</p><form method="post"><input type="hidden" name="_csrf" value="'.e(csrf_token()).'"><div class="mb-3"><label class="form-label">Display name</label><input class="form-control" name="display_name" required maxlength="120"></div><div class="mb-3"><label class="form-label">Email</label><input class="form-control" type="email" name="email" required></div><div class="mb-3"><label class="form-label">Password</label><input class="form-control" type="password" name="password" minlength="12" required><div class="form-text">At least 12 characters.</div></div><button class="btn btn-primary">Install JawlineFit</button></form></main></body></html>';
    exit;
}
if (str_starts_with($path, '/api/')) {
    (new ApiController($db, $config))->dispatch($method, $path);
}
if ($path === '/admin' || str_starts_with($path, '/admin/')) {
    (new AdminController($db, $config))->dispatch($method, $path);
}
(new PublicController($db, $config))->dispatch($method, $path);
