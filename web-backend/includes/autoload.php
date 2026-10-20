<?php
declare(strict_types=1);

// Lightweight fallback for setup before Composer has installed dependencies.
spl_autoload_register(static function (string $class): void {
    $prefix = 'JawlineFit\\';
    if (str_starts_with($class, $prefix)) {
        $path = __DIR__ . '/' . substr($class, strlen($prefix)) . '.php';
        if (is_file($path)) {
            require $path;
        }
    }
});
