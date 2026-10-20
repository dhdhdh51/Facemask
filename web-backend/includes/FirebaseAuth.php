<?php
declare(strict_types=1);

namespace JawlineFit;

use Firebase\JWT\JWT;
use Firebase\JWT\Key;
use PDO;

final class FirebaseAuth
{
    public function __construct(private readonly PDO $db, private readonly array $config) {}

    public function authenticate(string $token): array
    {
        if ($token === '' || strlen($token) > 16384) {
            throw new HttpException(401, 'invalid_token', 'The Firebase token is invalid or expired');
        }
        $project = trim((string) $this->config['firebase']['project_id']);
        if ($project === '') {
            throw new \RuntimeException('Firebase project is not configured');
        }
        try {
            $claims = (array) JWT::decode($token, $this->firebaseKeys());
        } catch (\Throwable) {
            throw new HttpException(401, 'invalid_token', 'The Firebase token is invalid or expired');
        }
        $issuer = 'https://securetoken.google.com/' . $project;
        if (($claims['iss'] ?? null) !== $issuer || ($claims['aud'] ?? null) !== $project) {
            throw new HttpException(401, 'invalid_token', 'The Firebase token has invalid claims');
        }
        $uid = $claims['sub'] ?? '';
        $issuedAt = (int) ($claims['iat'] ?? 0);
        $authenticatedAt = (int) ($claims['auth_time'] ?? 0);
        if (!is_string($uid) || $uid === '' || strlen($uid) > 128 || (int) ($claims['exp'] ?? 0) <= time() || $issuedAt <= 0 || $issuedAt > time() + 300 || $authenticatedAt <= 0 || $authenticatedAt > time() + 300) {
            throw new HttpException(401, 'invalid_token', 'The Firebase token has invalid claims');
        }
        $email = null;
        if (array_key_exists('email', $claims)) {
            if (!is_string($claims['email']) || strlen($claims['email']) > 320 || !filter_var($claims['email'], FILTER_VALIDATE_EMAIL)) {
                throw new HttpException(401, 'invalid_token', 'The Firebase token has invalid claims');
            }
            $email = mb_strtolower(trim($claims['email']));
        }
        $name = isset($claims['name']) && is_string($claims['name']) ? substr($claims['name'], 0, 150) : null;
        $picture = isset($claims['picture']) && is_string($claims['picture']) ? substr($claims['picture'], 0, 2048) : null;
        $stmt = $this->db->prepare("INSERT INTO users (firebase_uid,email,display_name,photo_url,last_seen_at) VALUES (?,?,?,?,UTC_TIMESTAMP())
            ON DUPLICATE KEY UPDATE email=COALESCE(VALUES(email),email),display_name=COALESCE(VALUES(display_name),display_name),photo_url=COALESCE(VALUES(photo_url),photo_url),last_seen_at=UTC_TIMESTAMP()");
        $stmt->execute([$uid, $email, $name, $picture]);
        $stmt = $this->db->prepare('SELECT * FROM users WHERE firebase_uid=?');
        $stmt->execute([$uid]);
        $user = $stmt->fetch();
        if (!$user) {
            throw new \RuntimeException('Failed to create authenticated user');
        }
        $user['_auth_time'] = $authenticatedAt;
        return $user;
    }

    public function isPremium(int $userId, ?string $manualUntil): array
    {
        $manual = $manualUntil !== null && strtotime($manualUntil . ' UTC') > time();
        $stmt = $this->db->prepare("SELECT MAX(expires_at) AS expires_at FROM subscriptions
            WHERE user_id=? AND expires_at > UTC_TIMESTAMP() AND state IN ('SUBSCRIPTION_STATE_ACTIVE','SUBSCRIPTION_STATE_IN_GRACE_PERIOD','SUBSCRIPTION_STATE_CANCELED')");
        $stmt->execute([$userId]);
        $subscriptionUntil = $stmt->fetchColumn() ?: null;
        $premium = $manual || $subscriptionUntil !== null;
        $until = null;
        foreach ([$manual ? $manualUntil : null, $subscriptionUntil] as $candidate) {
            if ($candidate !== null && ($until === null || strtotime($candidate) > strtotime($until))) {
                $until = $candidate;
            }
        }
        return ['is_premium' => $premium, 'premium_until' => $until === null ? null : gmdate('c', strtotime($until . ' UTC'))];
    }

    private function firebaseKeys(): array
    {
        $cacheDir = $this->config['app']['cache_path'];
        if (!is_dir($cacheDir) && !mkdir($cacheDir, 0750, true) && !is_dir($cacheDir)) {
            throw new \RuntimeException('Cannot create token key cache');
        }
        $cache = $cacheDir . '/firebase-keys.json';
        $certs = null;
        if (is_file($cache)) {
            $saved = json_decode((string) file_get_contents($cache), true);
            if (is_array($saved) && (int) ($saved['expires'] ?? 0) > time() && is_array($saved['keys'] ?? null)) {
                $certs = $saved['keys'];
            }
        }
        if ($certs === null) {
            [$certs, $ttl] = $this->downloadJson('https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com');
            if (file_put_contents($cache, json_encode(['expires' => time() + $ttl, 'keys' => $certs], JSON_THROW_ON_ERROR), LOCK_EX) === false) {
                throw new \RuntimeException('Unable to cache Firebase verification keys');
            }
            @chmod($cache, 0640);
        }
        $keys = [];
        foreach ($certs as $kid => $certificate) {
            if (is_string($kid) && is_string($certificate)) {
                $keys[$kid] = new Key($certificate, 'RS256');
            }
        }
        if ($keys === []) {
            throw new \RuntimeException('No Firebase verification keys are available');
        }
        return $keys;
    }

    private function downloadJson(string $url): array
    {
        $ch = curl_init($url);
        curl_setopt_array($ch, [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 10, CURLOPT_CONNECTTIMEOUT => 5, CURLOPT_SSL_VERIFYPEER => true, CURLOPT_SSL_VERIFYHOST => 2, CURLOPT_HEADER => true]);
        $response = curl_exec($ch);
        $status = curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
        $headerSize = curl_getinfo($ch, CURLINFO_HEADER_SIZE);
        if (!is_string($response) || $status !== 200) {
            throw new \RuntimeException('Unable to retrieve Google public keys');
        }
        $headers = substr($response, 0, $headerSize);
        $body = json_decode(substr($response, $headerSize), true, 32, JSON_THROW_ON_ERROR);
        $ttl = 3600;
        if (preg_match('/(?:^|\r\n)cache-control:[^\r\n]*max-age=(\d+)/i', $headers, $m)) {
            $ttl = max(300, min(86400, (int) $m[1]));
        }
        return [$body, $ttl];
    }
}
