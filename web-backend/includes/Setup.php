<?php
declare(strict_types=1);

namespace JawlineFit;

use PDO;

final class Setup
{
    public function __construct(private readonly PDO $db, private readonly array $config) {}

    public function isLocked(): bool
    {
        if (is_file($this->config['app']['setup_lock'])) {
            return true;
        }
        try {
            $stmt = $this->db->prepare('SELECT COUNT(*) FROM admin_users');
            $stmt->execute();
            return (int) $stmt->fetchColumn() > 0;
        } catch (\Throwable) {
            return false;
        }
    }

    public function install(string $email, string $password, string $name): void
    {
        if ($this->isLocked()) {
            throw new HttpException(403, 'setup_locked', 'Setup has already been completed');
        }
        $email = valid_email($email);
        $name = mb_substr(trim($name), 0, 120);
        if ($name === '' || strlen($password) < 12) {
            throw new HttpException(422, 'validation_failed', 'A name and password of at least 12 characters are required');
        }
        $sql = file_get_contents(dirname(__DIR__) . '/install.sql');
        if (!is_string($sql) || $sql === '') {
            throw new \RuntimeException('Schema file is unavailable');
        }
        $statements = preg_split('/;\s*(?:\r?\n|$)/', $sql) ?: [];
        foreach ($statements as $statement) {
            $statement = trim($statement);
            if ($statement !== '') {
                $this->db->prepare($statement)->execute();
            }
        }
        $stmt = $this->db->prepare('INSERT INTO admin_users(email,password_hash,display_name) VALUES(?,?,?)');
        $stmt->execute([$email, password_hash($password, PASSWORD_DEFAULT), $name]);

        $lock = $this->config['app']['setup_lock'];
        $directory = dirname($lock);
        if (!is_dir($directory) && !mkdir($directory, 0750, true) && !is_dir($directory)) {
            throw new \RuntimeException('Unable to create setup lock directory');
        }
        if (file_put_contents($lock, "Installed " . gmdate('c') . "\n", LOCK_EX) === false) {
            throw new \RuntimeException('Unable to create setup lock');
        }
        chmod($lock, 0640);
    }
}
