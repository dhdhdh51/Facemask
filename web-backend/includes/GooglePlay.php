<?php
declare(strict_types=1);

namespace JawlineFit;

use Google\Client;
use PDO;
use PDOException;

final class GooglePlay
{
    private const ENTITLED_STATES = [
        'SUBSCRIPTION_STATE_ACTIVE',
        'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
        'SUBSCRIPTION_STATE_CANCELED',
    ];
    private Encryption $encryption;

    public function __construct(private readonly PDO $db, private readonly array $config)
    {
        $this->encryption = new Encryption((string) ($config['security']['encryption_key'] ?? ''));
    }

    public function verifyForUser(int $userId, string $productId, string $purchaseToken): array
    {
        $package = trim((string) $this->config['google_play']['package_name']);
        if ($package === '') {
            throw new \RuntimeException('Google Play package is not configured');
        }
        if ($productId === '' || strlen($productId) > 191 || $purchaseToken === '' || strlen($purchaseToken) > 512) {
            throw new HttpException(422, 'validation_failed', 'Product ID and purchase token are required');
        }
        $plan = $this->db->prepare('SELECT 1 FROM plans WHERE product_id=? AND is_active=1 LIMIT 1');
        $plan->execute([$productId]);
        if (!$plan->fetchColumn()) {
            throw new HttpException(422, 'unknown_product', 'Product is not an active JawlineFit plan');
        }
        $tokenHash = $this->encryption->tokenHash($purchaseToken);
        $existing = $this->db->prepare('SELECT user_id FROM subscriptions WHERE token_hash=?');
        $existing->execute([$tokenHash]);
        $owner = $existing->fetchColumn();
        if ($owner !== false && (int) $owner !== $userId) {
            throw new HttpException(403, 'purchase_owned', 'This purchase belongs to another account');
        }
        return $this->refresh($purchaseToken, $package, $productId, $userId, true);
    }

    public function refresh(string $purchaseToken, string $package, ?string $expectedProduct, ?int $knownUserId, bool $acknowledge): array
    {
        $configuredPackage = trim((string) $this->config['google_play']['package_name']);
        if ($configuredPackage === '' || !hash_equals($configuredPackage, $package)) {
            throw new HttpException(403, 'invalid_package', 'Package name is not allowed');
        }
        if ($purchaseToken === '' || strlen($purchaseToken) > 512) {
            throw new HttpException(422, 'validation_failed', 'Purchase token is invalid');
        }
        $encodedPackage = rawurlencode($package);
        $encodedToken = rawurlencode($purchaseToken);
        $response = $this->request('GET', "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{$encodedPackage}/purchases/subscriptionsv2/tokens/{$encodedToken}");
        $selected = $this->selectActivePlanLineItem(is_array($response['lineItems'] ?? null) ? $response['lineItems'] : [], $expectedProduct);
        $productId = $selected['product_id'];
        $basePlanId = $selected['base_plan_id'];
        $expiry = $selected['expiry'];
        $autoRenewing = $selected['auto_renewing'];
        $state = (string) ($response['subscriptionState'] ?? 'SUBSCRIPTION_STATE_UNSPECIFIED');
        $ackState = (string) ($response['acknowledgementState'] ?? 'ACKNOWLEDGEMENT_STATE_UNSPECIFIED');
        if ($expiry === null && in_array($state, self::ENTITLED_STATES, true)) {
            throw new HttpException(422, 'invalid_purchase', 'Google Play returned an eligible purchase without an expiry');
        }
        $tokenHash = $this->encryption->tokenHash($purchaseToken);
        $userId = $knownUserId;
        if ($userId === null) {
            $find = $this->db->prepare('SELECT user_id FROM subscriptions WHERE token_hash=?');
            $find->execute([$tokenHash]);
            $found = $find->fetchColumn();
            if ($found === false) {
                throw new HttpException(202, 'unknown_purchase', 'Purchase notification accepted but no local owner exists yet');
            }
            $userId = (int) $found;
        }
        if ($acknowledge && $ackState === 'ACKNOWLEDGEMENT_STATE_PENDING' && in_array($state, self::ENTITLED_STATES, true) && $expiry !== null && $expiry > time()) {
            $subscription = rawurlencode($productId);
            $this->request('POST', "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{$encodedPackage}/purchases/subscriptions/{$subscription}/tokens/{$encodedToken}:acknowledge", new \stdClass());
            $ackState = 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED';
        }
        $expiresAt = $expiry === null ? null : gmdate('Y-m-d H:i:s', $expiry);
        $orderId = isset($response['latestOrderId']) ? mb_substr((string) $response['latestOrderId'], 0, 191) : null;
        $this->persist(
            $userId,
            $package,
            $productId,
            $basePlanId,
            $tokenHash,
            $this->encryption->encrypt($purchaseToken),
            $orderId,
            $state,
            $ackState,
            $autoRenewing,
            $expiresAt,
            $this->encryption->encrypt(json_encode($response, JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE | JSON_THROW_ON_ERROR))
        );
        return [
            'is_premium' => in_array($state, self::ENTITLED_STATES, true) && $expiry !== null && $expiry > time(),
            'plan_expiry' => $expiry === null ? null : gmdate('c', $expiry),
        ];
    }

    /** @return array{product_id:string,base_plan_id:string,expiry:?int,auto_renewing:bool} */
    private function selectActivePlanLineItem(array $lineItems, ?string $expectedProduct): array
    {
        $selected = null;
        foreach ($lineItems as $item) {
            if (!is_array($item)) {
                continue;
            }
            $productId = (string) ($item['productId'] ?? '');
            $basePlanId = is_array($item['offerDetails'] ?? null) ? (string) ($item['offerDetails']['basePlanId'] ?? '') : '';
            if ($productId === '' || $basePlanId === '' || ($expectedProduct !== null && !hash_equals($expectedProduct, $productId))) {
                continue;
            }
            $plan = $this->db->prepare('SELECT 1 FROM plans WHERE product_id=? AND base_plan_id=? AND is_active=1 LIMIT 1');
            $plan->execute([$productId, $basePlanId]);
            if (!$plan->fetchColumn()) {
                continue;
            }
            $expiry = isset($item['expiryTime']) ? strtotime((string) $item['expiryTime']) : false;
            $expiry = $expiry === false ? null : $expiry;
            if ($selected === null || ($expiry ?? PHP_INT_MIN) > ($selected['expiry'] ?? PHP_INT_MIN)) {
                $renewal = is_array($item['autoRenewingPlan'] ?? null) ? $item['autoRenewingPlan'] : null;
                $selected = [
                    'product_id' => $productId,
                    'base_plan_id' => $basePlanId,
                    'expiry' => $expiry,
                    'auto_renewing' => $renewal !== null && array_key_exists('autoRenewEnabled', $renewal) && $renewal['autoRenewEnabled'] === true,
                ];
            }
        }
        if ($selected === null) {
            throw new HttpException(422, 'purchase_mismatch', 'Google Play purchase does not match an active product and base plan');
        }
        return $selected;
    }

    private function persist(int $userId, string $package, string $productId, string $basePlanId, string $tokenHash, string $encryptedToken, ?string $orderId, string $state, string $ackState, bool $autoRenewing, ?string $expiresAt, string $encryptedResponse): void
    {
        for ($attempt = 0; $attempt < 2; $attempt++) {
            $this->db->beginTransaction();
            try {
                $find = $this->db->prepare('SELECT id,user_id FROM subscriptions WHERE token_hash=? FOR UPDATE');
                $find->execute([$tokenHash]);
                $existing = $find->fetch();
                if ($existing && (int) $existing['user_id'] !== $userId) {
                    throw new HttpException(403, 'purchase_owned', 'This purchase belongs to another account');
                }
                $values = [$package, $productId, $basePlanId, $encryptedToken, $orderId, $state, $ackState, $autoRenewing ? 1 : 0, $expiresAt, $encryptedResponse];
                if ($existing) {
                    $update = $this->db->prepare('UPDATE subscriptions SET package_name=?,product_id=?,base_plan_id=?,purchase_token_encrypted=?,order_id=?,state=?,acknowledgement_state=?,auto_renewing=?,expires_at=?,google_response_encrypted=?,verified_at=UTC_TIMESTAMP() WHERE id=?');
                    $update->execute([...$values, (int) $existing['id']]);
                } else {
                    $insert = $this->db->prepare('INSERT INTO subscriptions (user_id,package_name,product_id,base_plan_id,token_hash,purchase_token_encrypted,order_id,state,acknowledgement_state,auto_renewing,expires_at,google_response_encrypted,verified_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP())');
                    $insert->execute([$userId, $package, $productId, $basePlanId, $tokenHash, $encryptedToken, $orderId, $state, $ackState, $autoRenewing ? 1 : 0, $expiresAt, $encryptedResponse]);
                }
                $this->db->commit();
                return;
            } catch (PDOException $error) {
                if ($this->db->inTransaction()) {
                    $this->db->rollBack();
                }
                if ((string) $error->getCode() !== '23000' || $attempt === 1) {
                    throw $error;
                }
            } catch (\Throwable $error) {
                if ($this->db->inTransaction()) {
                    $this->db->rollBack();
                }
                throw $error;
            }
        }
    }

    private function request(string $method, string $url, array|\stdClass|null $body = null): array
    {
        $client = new Client();
        $credential = trim((string) $this->config['google_play']['service_account_json']);
        if ($credential === '') {
            throw new \RuntimeException('Google Play service account is not configured');
        }
        if (str_starts_with($credential, '{')) {
            $credential = json_decode($credential, true, 32, JSON_THROW_ON_ERROR);
        }
        $client->setAuthConfig($credential);
        $client->setScopes(['https://www.googleapis.com/auth/androidpublisher']);
        $access = $client->fetchAccessTokenWithAssertion();
        if (isset($access['error']) || empty($access['access_token'])) {
            throw new \RuntimeException('Unable to authorize with Google Play');
        }
        $handle = curl_init($url);
        $headers = ['Authorization: Bearer ' . $access['access_token'], 'Accept: application/json'];
        $options = [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 20, CURLOPT_CONNECTTIMEOUT => 5, CURLOPT_SSL_VERIFYPEER => true, CURLOPT_SSL_VERIFYHOST => 2, CURLOPT_CUSTOMREQUEST => $method, CURLOPT_HTTPHEADER => $headers];
        if ($body !== null) {
            $headers[] = 'Content-Type: application/json';
            $options[CURLOPT_HTTPHEADER] = $headers;
            $options[CURLOPT_POSTFIELDS] = json_encode($body, JSON_THROW_ON_ERROR);
        }
        curl_setopt_array($handle, $options);
        $raw = curl_exec($handle);
        $status = curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
        if (!is_string($raw)) {
            throw new \RuntimeException('Google Play request failed');
        }
        if ($status < 200 || $status >= 300) {
            if ($status === 400 || $status === 404) {
                throw new HttpException(422, 'invalid_purchase', 'Google Play could not validate this purchase');
            }
            throw new \RuntimeException('Google Play API returned an unexpected status');
        }
        if ($raw === '') {
            return [];
        }
        $decoded = json_decode($raw, true, 64, JSON_THROW_ON_ERROR);
        if (!is_array($decoded)) {
            throw new \RuntimeException('Google Play returned an invalid response');
        }
        return $decoded;
    }
}
