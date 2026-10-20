<?php
declare(strict_types=1);

namespace JawlineFit;

use Firebase\JWT\JWK;
use Firebase\JWT\JWT;

final class GoogleOidc
{
    public function __construct(private readonly array $config) {}

    public function verify(string $token): array
    {
        if ($token === '' || strlen($token) > 16384) {
            throw new HttpException(401, 'invalid_push_token', 'Pub/Sub push token is invalid');
        }
        $audience = trim((string) $this->config['google_play']['rtdn_audience']);
        $expectedEmail = trim((string) $this->config['google_play']['rtdn_service_account_email']);
        if ($audience === '' || $expectedEmail === '') {
            throw new \RuntimeException('RTDN verification is not configured');
        }
        $cacheDir = $this->config['app']['cache_path'];
        if (!is_dir($cacheDir) && !mkdir($cacheDir, 0750, true) && !is_dir($cacheDir)) {
            throw new \RuntimeException('Cannot create Google key cache');
        }
        $file = $cacheDir . '/google-oidc-jwks.json';
        $jwks = null;
        if (is_file($file)) {
            $saved = json_decode((string) file_get_contents($file), true);
            if (is_array($saved) && ($saved['expires'] ?? 0) > time()) {
                $jwks = $saved['jwks'] ?? null;
            }
        }
        if (!is_array($jwks)) {
            $headers = '';
            $ch = curl_init('https://www.googleapis.com/oauth2/v3/certs');
            curl_setopt_array($ch, [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 10, CURLOPT_CONNECTTIMEOUT => 5, CURLOPT_SSL_VERIFYPEER => true, CURLOPT_SSL_VERIFYHOST => 2, CURLOPT_HEADER => true]);
            $response = curl_exec($ch);
            $status = curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
            $headerSize = curl_getinfo($ch, CURLINFO_HEADER_SIZE);
            if (!is_string($response) || $status !== 200) {
                throw new \RuntimeException('Unable to retrieve Google OIDC keys');
            }
            $headers = substr($response, 0, $headerSize);
            $jwks = json_decode(substr($response, $headerSize), true, 32, JSON_THROW_ON_ERROR);
            $ttl = 3600;
            if (preg_match('/(?:^|\r\n)cache-control:[^\r\n]*max-age=(\d+)/i', $headers, $match)) {
                $ttl = max(300, min(86400, (int) $match[1]));
            }
            if (file_put_contents($file, json_encode(['expires' => time() + $ttl, 'jwks' => $jwks], JSON_THROW_ON_ERROR), LOCK_EX) === false) {
                throw new \RuntimeException('Unable to cache Google OIDC keys');
            }
            @chmod($file, 0640);
        }
        try {
            $claims = (array) JWT::decode($token, JWK::parseKeySet($jwks));
        } catch (\Throwable) {
            throw new HttpException(401, 'invalid_push_token', 'Pub/Sub push token is invalid');
        }
        $iss = $claims['iss'] ?? '';
        $aud = $claims['aud'] ?? '';
        $audienceMatches = is_string($aud) && hash_equals($audience, $aud);
        $email = $claims['email'] ?? '';
        $emailVerified = $claims['email_verified'] ?? false;
        if (!in_array($iss, ['accounts.google.com', 'https://accounts.google.com'], true) || !$audienceMatches || !is_string($email) || !hash_equals($expectedEmail, $email) || !in_array($emailVerified, [true, 'true'], true) || (int) ($claims['exp'] ?? 0) <= time() || !is_string($claims['sub'] ?? null) || $claims['sub'] === '') {
            throw new HttpException(403, 'invalid_push_identity', 'Pub/Sub push identity is not authorized');
        }
        return $claims;
    }
}
