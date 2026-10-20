#!/usr/bin/env php
<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}
$root = dirname(__DIR__);
$config = require $root . '/config.php';
require $root . '/includes/autoload.php';
require $root . '/includes/functions.php';

use JawlineFit\ConfigValidator;
use JawlineFit\Database;
use JawlineFit\Setup;

ConfigValidator::validate($config, $root);

try {
    $setup = new Setup(Database::connect($config['db']), $config);
    if ($setup->isLocked()) {
        throw new RuntimeException('Setup is locked because an administrator already exists.');
    }
    fwrite(STDOUT, "JawlineFit one-time setup\nDisplay name: ");
    $name = trim((string) fgets(STDIN));
    fwrite(STDOUT, 'Admin email: ');
    $email = trim((string) fgets(STDIN));
    fwrite(STDOUT, 'Password (12+ characters): ');
    $password = '';
    if (function_exists('shell_exec')) {
        shell_exec('stty -echo');
        $password = rtrim((string) fgets(STDIN), "\r\n");
        shell_exec('stty echo');
        fwrite(STDOUT, "\n");
    } else {
        $password = rtrim((string) fgets(STDIN), "\r\n");
    }
    $setup->install($email, $password, $name);
    fwrite(STDOUT, "Installation complete. Setup is now locked.\n");
} catch (Throwable $e) {
    fwrite(STDERR, 'Setup failed: ' . $e->getMessage() . "\n");
    exit(1);
}
