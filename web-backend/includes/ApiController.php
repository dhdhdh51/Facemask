<?php
declare(strict_types=1);

namespace JawlineFit;

use PDO;

final class ApiController
{
    private FirebaseAuth $auth;
    private RateLimiter $limiter;

    public function __construct(private readonly PDO $db, private readonly array $config)
    {
        $this->auth = new FirebaseAuth($db, $config);
        $this->limiter = new RateLimiter($db);
    }

    public function dispatch(string $method, string $path): never
    {
        $ip = Http::clientIp($this->config['app']['trusted_proxies']);
        if ($method === 'POST' && $path === '/api/play-rtdn') {
            $this->limiter->check('rtdn:' . $ip, (int) $this->config['security']['rtdn_rate_limit'], (int) $this->config['security']['rtdn_rate_window']);
            $this->rtdn();
        }

        $this->limiter->check('api:' . $ip, (int) $this->config['security']['api_rate_limit'], (int) $this->config['security']['api_rate_window']);
        $user = $this->auth->authenticate(Http::bearer());
        $entitlement = $this->entitlement((int) $user['id'], $user['manual_premium_until']);

        if ($method === 'GET' && $path === '/api/me') {
            Http::success([
                'uid' => $user['firebase_uid'],
                'plan' => $entitlement['plan'],
                'plan_expiry' => $entitlement['plan_expiry'],
                'is_premium' => $entitlement['is_premium'],
            ]);
        }
        if ($method === 'POST' && $path === '/api/device') {
            $this->device((int) $user['id']);
        }
        if ($method === 'GET' && $path === '/api/nuskhe') {
            $this->nuskhe((bool) $entitlement['is_premium']);
        }
        if ($method === 'GET' && $path === '/api/routines') {
            $this->routines((bool) $entitlement['is_premium']);
        }
        if ($method === 'GET' && preg_match('#^/api/routines/(\d+)$#', $path, $match)) {
            $this->routine((int) $match[1], (bool) $entitlement['is_premium']);
        }
        if ($method === 'GET' && $path === '/api/tips') {
            $this->tips();
        }
        if ($method === 'GET' && $path === '/api/plans') {
            $this->plans();
        }
        if ($method === 'POST' && $path === '/api/billing/verify') {
            $this->limiter->check('billing:ip:' . $ip, (int) $this->config['security']['billing_rate_limit'], (int) $this->config['security']['billing_rate_window']);
            $this->limiter->check('billing:user:' . (int) $user['id'], (int) $this->config['security']['billing_rate_limit'], (int) $this->config['security']['billing_rate_window']);
            $this->billing((int) $user['id']);
        }
        if ($method === 'DELETE' && $path === '/api/me') {
            $this->limiter->check('delete:ip:' . $ip, (int) $this->config['security']['delete_rate_limit'], (int) $this->config['security']['delete_rate_window']);
            $this->limiter->check('delete:user:' . (int) $user['id'], (int) $this->config['security']['delete_rate_limit'], (int) $this->config['security']['delete_rate_window']);
            $this->deleteAccount((int) $user['id'], (int) $user['_auth_time']);
        }
        throw new HttpException(404, 'not_found', 'Endpoint not found');
    }

    private function entitlement(int $userId, ?string $manualUntil): array
    {
        $stmt = $this->db->prepare("SELECT product_id,expires_at FROM subscriptions WHERE user_id=? AND expires_at>UTC_TIMESTAMP() AND state IN ('SUBSCRIPTION_STATE_ACTIVE','SUBSCRIPTION_STATE_IN_GRACE_PERIOD','SUBSCRIPTION_STATE_CANCELED') ORDER BY expires_at DESC LIMIT 1");
        $stmt->execute([$userId]);
        $subscription = $stmt->fetch() ?: null;
        $manualActive = $manualUntil !== null && strtotime($manualUntil . ' UTC') > time();
        $manualLater = $manualActive && ($subscription === null || strtotime($manualUntil . ' UTC') > strtotime($subscription['expires_at'] . ' UTC'));
        $plan = $manualLater ? 'manual' : ($subscription['product_id'] ?? null);
        $expiry = $manualLater ? $manualUntil : ($subscription['expires_at'] ?? null);
        return [
            'plan' => $plan,
            'plan_expiry' => $expiry === null ? null : gmdate('c', strtotime($expiry . ' UTC')),
            'is_premium' => $manualActive || $subscription !== null,
        ];
    }

    private function device(int $userId): never
    {
        $body = Http::body();
        $this->assertExactKeys($body, ['fcm_token']);
        $token = trim((string) ($body['fcm_token'] ?? ''));
        if ($token === '' || strlen($token) > 512) {
            throw new HttpException(422, 'validation_failed', 'fcm_token is required and must not exceed 512 characters');
        }
        $stmt = $this->db->prepare('INSERT INTO devices (user_id,fcm_token) VALUES (?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),updated_at=UTC_TIMESTAMP()');
        $stmt->execute([$userId, $token]);
        Http::success(['fcm_token' => $token]);
    }

    private function nuskhe(bool $isPremium): never
    {
        $unknown = array_diff(array_keys($_GET), ['concern', 'skin_type', 'page']);
        if ($unknown !== []) {
            throw new HttpException(422, 'validation_failed', 'Query contains unsupported fields');
        }
        $page = $this->page();
        $where = ['is_published=1'];
        $params = [];
        foreach (['concern', 'skin_type'] as $filter) {
            $value = mb_substr(trim((string) ($_GET[$filter] ?? '')), 0, 100);
            if ($value !== '') {
                $where[] = $filter . '=?';
                $params[] = $value;
            }
        }
        $stmt = $this->db->prepare('SELECT id,title,concern,skin_type,ingredients_json,steps_json,frequency,warning,is_premium,media_path,media_type,thumbnail_path FROM nuskhe WHERE ' . implode(' AND ', $where) . ' ORDER BY sort_order,id LIMIT ? OFFSET ?');
        $position = 1;
        foreach ($params as $value) {
            $stmt->bindValue($position++, $value, PDO::PARAM_STR);
        }
        $stmt->bindValue($position++, 20, PDO::PARAM_INT);
        $stmt->bindValue($position, ($page - 1) * 20, PDO::PARAM_INT);
        $stmt->execute();
        $items = [];
        foreach ($stmt->fetchAll() as $row) {
            $locked = (bool) $row['is_premium'] && !$isPremium;
            $items[] = [
                'id' => (int) $row['id'],
                'title' => $row['title'],
                'concern' => $row['concern'],
                'skin_type' => $row['skin_type'],
                'ingredients' => $locked ? [] : $this->decodeStringList($row['ingredients_json']),
                'steps' => $locked ? [] : $this->decodeStringList($row['steps_json']),
                'frequency' => $row['frequency'],
                'warning' => $row['warning'],
                'is_premium' => (bool) $row['is_premium'],
                'media_type' => $row['media_type'],
                'media_url' => $locked ? null : $this->mediaUrl($row['media_path']),
                'thumbnail_url' => $this->mediaUrl($row['thumbnail_path']),
            ];
        }
        Http::success($items);
    }

    private function routines(bool $isPremium): never
    {
        $stmt = $this->db->prepare('SELECT id,title,description,duration_min,is_premium,media_path,media_type,thumbnail_path FROM routines WHERE is_published=1 ORDER BY sort_order,id');
        $stmt->execute();
        $items = [];
        foreach ($stmt->fetchAll() as $row) {
            $items[] = $this->routineFields($row, (bool) $row['is_premium'] && !$isPremium);
        }
        Http::success($items);
    }

    private function routine(int $routineId, bool $isPremium): never
    {
        $stmt = $this->db->prepare('SELECT id,title,description,duration_min,is_premium,media_path,media_type,thumbnail_path FROM routines WHERE id=? AND is_published=1');
        $stmt->execute([$routineId]);
        $routine = $stmt->fetch();
        if (!$routine) {
            throw new HttpException(404, 'not_found', 'Routine not found');
        }
        if ((bool) $routine['is_premium'] && !$isPremium) {
            throw new HttpException(403, 'premium_required', 'An active premium plan is required');
        }
        $stepStmt = $this->db->prepare('SELECT title,duration_sec,instruction,media_path,media_type,thumbnail_path,sort_order FROM routine_steps WHERE routine_id=? ORDER BY sort_order,id');
        $stepStmt->execute([$routineId]);
        $steps = [];
        foreach ($stepStmt->fetchAll() as $step) {
            $steps[] = [
                'order' => (int) $step['sort_order'],
                'title' => $step['title'],
                'duration_sec' => (int) $step['duration_sec'],
                'instruction' => $step['instruction'],
                'media_type' => $step['media_type'],
                'media_url' => $this->mediaUrl($step['media_path']),
                'thumbnail_url' => $this->mediaUrl($step['thumbnail_path']),
            ];
        }
        Http::success([...$this->routineFields($routine), 'steps' => $steps]);
    }

    private function routineFields(array $row, bool $locked = false): array
    {
        return [
            'id' => (int) $row['id'],
            'title' => $row['title'],
            'description' => $row['description'],
            'duration_min' => (int) $row['duration_min'],
            'is_premium' => (bool) $row['is_premium'],
            'media_type' => $row['media_type'],
            'media_url' => $locked ? null : $this->mediaUrl($row['media_path']),
            'thumbnail_url' => $this->mediaUrl($row['thumbnail_path']),
        ];
    }

    private function tips(): never
    {
        $stmt = $this->db->prepare('SELECT id,category,text,media_path,media_type,thumbnail_path FROM tips WHERE is_published=1 ORDER BY sort_order,id');
        $stmt->execute();
        $items = [];
        foreach ($stmt->fetchAll() as $row) {
            $items[] = [
                'id' => (int) $row['id'],
                'category' => $row['category'],
                'text' => $row['text'],
                'media_type' => $row['media_type'],
                'media_url' => $this->mediaUrl($row['media_path']),
                'thumbnail_url' => $this->mediaUrl($row['thumbnail_path']),
            ];
        }
        Http::success($items);
    }

    private function plans(): never
    {
        $stmt = $this->db->prepare('SELECT id,name,product_id,base_plan_id,duration_days FROM plans WHERE is_active=1 ORDER BY sort_order,id');
        $stmt->execute();
        $items = [];
        foreach ($stmt->fetchAll() as $row) {
            $items[] = [
                'id' => (int) $row['id'],
                'name' => $row['name'],
                'product_id' => $row['product_id'],
                'base_plan_id' => $row['base_plan_id'],
                'duration_days' => (int) $row['duration_days'],
            ];
        }
        Http::success($items);
    }

    private function billing(int $userId): never
    {
        $body = Http::body();
        $this->assertExactKeys($body, ['product_id', 'purchase_token']);
        $productId = trim((string) ($body['product_id'] ?? ''));
        $purchaseToken = trim((string) ($body['purchase_token'] ?? ''));
        Http::success((new GooglePlay($this->db, $this->config))->verifyForUser($userId, $productId, $purchaseToken));
    }

    private function deleteAccount(int $userId, int $authenticatedAt): never
    {
        if ($authenticatedAt <= 0 || time() - $authenticatedAt > (int) $this->config['security']['delete_recent_auth_seconds']) {
            throw new HttpException(401, 'recent_auth_required', 'Recent authentication is required to delete the account');
        }
        $this->db->beginTransaction();
        try {
            $stmt = $this->db->prepare('DELETE FROM users WHERE id=?');
            $stmt->execute([$userId]);
            if ($stmt->rowCount() !== 1) {
                throw new HttpException(404, 'not_found', 'Account not found');
            }
            $this->db->commit();
        } catch (\Throwable $error) {
            if ($this->db->inTransaction()) {
                $this->db->rollBack();
            }
            throw $error;
        }
        Http::success(null);
    }

    private function rtdn(): never
    {
        (new GoogleOidc($this->config))->verify(Http::bearer());
        $body = Http::body();
        $message = $body['message'] ?? null;
        if (!is_array($message) || !isset($message['messageId'], $message['data'])) {
            throw new HttpException(400, 'invalid_push', 'Pub/Sub message is malformed');
        }
        $messageId = (string) $message['messageId'];
        if ($messageId === '' || strlen($messageId) > 191) {
            throw new HttpException(400, 'invalid_push', 'Pub/Sub message ID is invalid');
        }
        $decoded = base64_decode((string) $message['data'], true);
        try {
            $payload = is_string($decoded) ? json_decode($decoded, true, 32, JSON_THROW_ON_ERROR) : null;
        } catch (\JsonException) {
            $payload = null;
        }
        if (!is_array($payload)) {
            throw new HttpException(400, 'invalid_push', 'Pub/Sub data is malformed');
        }
        $configuredPackage = (string) $this->config['google_play']['package_name'];
        if (!is_string($payload['packageName'] ?? null) || !hash_equals($configuredPackage, $payload['packageName'])) {
            throw new HttpException(403, 'invalid_push', 'Pub/Sub package is not authorized');
        }
        $notification = is_array($payload['subscriptionNotification'] ?? null) ? $payload['subscriptionNotification'] : null;
        $eventAudit = [
            'packageName' => $payload['packageName'],
            'eventType' => $notification === null ? 'non_subscription' : 'subscription',
            'notificationType' => $notification['notificationType'] ?? null,
        ];
        $insert = $this->db->prepare('INSERT IGNORE INTO rtdn_events (message_id,payload_json) VALUES (?,?)');
        $insert->execute([$messageId, json_encode($eventAudit, JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR)]);
        if ($insert->rowCount() === 0) {
            Http::success(['accepted' => true, 'duplicate' => true]);
        }
        try {
            if (is_array($notification) && is_string($notification['purchaseToken'] ?? null) && $notification['purchaseToken'] !== '' && strlen($notification['purchaseToken']) <= 512) {
                $tokenHash = (new Encryption((string) $this->config['security']['encryption_key']))->tokenHash($notification['purchaseToken']);
                $known = $this->db->prepare('SELECT id FROM subscriptions WHERE token_hash=?');
                $known->execute([$tokenHash]);
                if ($known->fetchColumn()) {
                    (new GooglePlay($this->db, $this->config))->refresh($notification['purchaseToken'], $configuredPackage, null, null, false);
                }
            }
        } catch (\Throwable $error) {
            $this->db->prepare('DELETE FROM rtdn_events WHERE message_id=?')->execute([$messageId]);
            throw $error;
        }
        Http::success(['accepted' => true, 'duplicate' => false]);
    }

    private function assertExactKeys(array $body, array $allowed): void
    {
        $unknown = array_diff(array_keys($body), $allowed);
        if ($unknown !== []) {
            throw new HttpException(422, 'validation_failed', 'Request contains unsupported fields');
        }
    }

    private function page(): int
    {
        $raw = $_GET['page'] ?? '1';
        $page = filter_var($raw, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1, 'max_range' => 1000000]]);
        if ($page === false) {
            throw new HttpException(422, 'validation_failed', 'page must be a positive integer');
        }
        return (int) $page;
    }

    private function decodeStringList(string $json): array
    {
        try {
            $value = json_decode($json, true, 64, JSON_THROW_ON_ERROR);
        } catch (\JsonException) {
            throw new \RuntimeException('Stored JSON content is invalid');
        }
        if (!is_array($value) || !array_is_list($value)) {
            throw new \RuntimeException('Stored JSON content must be a list');
        }
        return array_values(array_map(static fn(mixed $item): string => (string) $item, $value));
    }

    private function mediaUrl(?string $path): ?string
    {
        if ($path === null || $path === '') {
            return null;
        }
        $baseUrl = rtrim((string) $this->config['app']['base_url'], '/');
        $parts = parse_url($baseUrl);
        if (!is_array($parts) || ($parts['scheme'] ?? null) !== 'https' || empty($parts['host'])) {
            throw new \RuntimeException('APP_BASE_URL must be an absolute HTTPS URL');
        }
        return $baseUrl . '/' . ltrim($path, '/');
    }
}
