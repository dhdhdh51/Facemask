<?php
declare(strict_types=1);

namespace JawlineFit;

use PDO;
use PDOException;
use RuntimeException;

final class Database
{
    private static ?PDO $pdo = null;

    public static function connect(array $config): PDO
    {
        if (self::$pdo instanceof PDO) {
            return self::$pdo;
        }
        $options = [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
            PDO::ATTR_STRINGIFY_FETCHES => false,
        ];
        self::addTlsOption($options, 'PDO::MYSQL_ATTR_SSL_CA', $config['ssl_ca'] ?? '');
        self::addTlsOption($options, 'PDO::MYSQL_ATTR_SSL_CERT', $config['ssl_cert'] ?? '');
        self::addTlsOption($options, 'PDO::MYSQL_ATTR_SSL_KEY', $config['ssl_key'] ?? '');
        if (defined('PDO::MYSQL_ATTR_SSL_VERIFY_SERVER_CERT')) {
            $options[constant('PDO::MYSQL_ATTR_SSL_VERIFY_SERVER_CERT')] = (bool) ($config['ssl_verify_server_cert'] ?? true);
        }
        if ((bool) ($config['tls_required'] ?? false) && trim((string) ($config['ssl_ca'] ?? '')) === '') {
            throw new RuntimeException('Database TLS is required but DB_SSL_CA is not configured');
        }
        try {
            self::$pdo = new PDO((string) $config['dsn'], (string) $config['user'], (string) $config['password'], $options);
            self::$pdo->prepare("SET SESSION time_zone = '+00:00', SESSION sql_mode = 'STRICT_TRANS_TABLES,ONLY_FULL_GROUP_BY,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'")->execute();
            return self::$pdo;
        } catch (PDOException $e) {
            throw new RuntimeException('Database connection failed', 0, $e);
        }
    }

    private static function addTlsOption(array &$options, string $constant, mixed $value): void
    {
        $value = trim((string) $value);
        if ($value !== '' && defined($constant)) {
            $options[constant($constant)] = $value;
        }
    }
}
