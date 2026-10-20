<?php
declare(strict_types=1);

namespace JawlineFit;

final class Encryption
{
    private const VERSION = "\x01";
    private const NONCE_BYTES = 12;
    private const TAG_BYTES = 16;
    private string $key;

    public function __construct(string $configuredKey)
    {
        $configuredKey = trim($configuredKey);
        $decoded = preg_match('/^[a-f0-9]{64}$/i', $configuredKey) === 1
            ? hex2bin($configuredKey)
            : base64_decode($configuredKey, true);
        if (!is_string($decoded) || strlen($decoded) !== 32) {
            throw new \RuntimeException('APP_ENCRYPTION_KEY must decode to exactly 32 bytes');
        }
        $this->key = $decoded;
    }

    public function encrypt(string $plaintext): string
    {
        $nonce = random_bytes(self::NONCE_BYTES);
        $tag = '';
        $ciphertext = openssl_encrypt($plaintext, 'aes-256-gcm', $this->key, OPENSSL_RAW_DATA, $nonce, $tag, '', self::TAG_BYTES);
        if (!is_string($ciphertext) || strlen($tag) !== self::TAG_BYTES) {
            throw new \RuntimeException('Sensitive data encryption failed');
        }
        return self::base64UrlEncode(self::VERSION . $nonce . $tag . $ciphertext);
    }

    public function decrypt(string $encoded): string
    {
        $payload = self::base64UrlDecode($encoded);
        $minimum = 1 + self::NONCE_BYTES + self::TAG_BYTES;
        if (strlen($payload) < $minimum || $payload[0] !== self::VERSION) {
            throw new \RuntimeException('Encrypted data is invalid');
        }
        $nonce = substr($payload, 1, self::NONCE_BYTES);
        $tag = substr($payload, 1 + self::NONCE_BYTES, self::TAG_BYTES);
        $ciphertext = substr($payload, $minimum);
        $plaintext = openssl_decrypt($ciphertext, 'aes-256-gcm', $this->key, OPENSSL_RAW_DATA, $nonce, $tag);
        if (!is_string($plaintext)) {
            throw new \RuntimeException('Encrypted data authentication failed');
        }
        return $plaintext;
    }

    public function tokenHash(string $purchaseToken): string
    {
        return hash_hmac('sha256', 'purchase-token:' . $purchaseToken, $this->key);
    }

    public function auditHash(string $value): string
    {
        return hash_hmac('sha256', 'deletion-audit:' . $value, $this->key);
    }

    private static function base64UrlEncode(string $value): string
    {
        return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
    }

    private static function base64UrlDecode(string $value): string
    {
        if ($value === '' || preg_match('/^[A-Za-z0-9_-]+$/', $value) !== 1) {
            throw new \RuntimeException('Encrypted data is invalid');
        }
        $decoded = base64_decode(strtr($value, '-_', '+/') . str_repeat('=', (4 - strlen($value) % 4) % 4), true);
        if (!is_string($decoded)) {
            throw new \RuntimeException('Encrypted data is invalid');
        }
        return $decoded;
    }
}
