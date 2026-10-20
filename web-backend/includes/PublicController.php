<?php
declare(strict_types=1);

namespace JawlineFit;

use PDO;

final class PublicController
{
    public function __construct(private readonly PDO $db, private readonly array $config)
    {
        if (session_status() !== PHP_SESSION_ACTIVE) {
            session_name('jawlinefit_public');
            session_set_cookie_params(['lifetime' => 0, 'path' => '/', 'secure' => (bool) ($GLOBALS['jawlinefit_secure_request'] ?? false), 'httponly' => true, 'samesite' => 'Lax']);
            ini_set('session.use_strict_mode', '1');
            ini_set('session.use_only_cookies', '1');
            session_start();
        }
    }

    public function dispatch(string $method, string $path): never
    {
        if ($path === '/delete-account' && $method === 'POST') {
            $this->deleteRequest();
        }
        if ($path === '/delete-account/verify' && $method === 'GET') {
            $this->verifyDeleteRequest();
        }
        $pages = [
            '/privacy' => ['Privacy Policy', $this->privacy()],
            '/terms' => ['Terms of Service', $this->terms()],
            '/disclaimer' => ['Wellness & Results Disclaimer', $this->disclaimer()],
            '/delete-account' => ['Delete Your Account', $this->deletePage()],
        ];
        if (isset($pages[$path])) {
            $this->render($pages[$path][0], $pages[$path][1]);
        }
        if ($path === '/' || $path === '/index.php') {
            $this->render('JawlineFit', '<section class="py-5 text-center"><h1 class="display-4 fw-bold">JawlineFit</h1><p class="lead">Guided facial wellness routines with realistic expectations.</p><p><a class="btn btn-primary" href="/privacy">Privacy Policy</a></p></section>');
        }
        throw new HttpException(404, 'not_found', 'Page not found');
    }

    private function deleteRequest(): never
    {
        verify_csrf();
        if (trim((string) ($_POST['website'] ?? '')) !== '') {
            Http::redirect('/delete-account?submitted=1');
        }
        $ip = Http::clientIp($this->config['app']['trusted_proxies']);
        (new RateLimiter($this->db))->check(
            'deletion-request:' . $ip,
            (int) $this->config['app']['delete_request_limit'],
            (int) $this->config['app']['delete_request_window']
        );
        $email = valid_email((string) ($_POST['email'] ?? ''));
        $uid = mb_substr(trim((string) ($_POST['firebase_uid'] ?? '')), 0, 128) ?: null;
        $reason = mb_substr(trim((string) ($_POST['reason'] ?? '')), 0, 2000) ?: null;
        $token = bin2hex(random_bytes(32));
        $hash = hash('sha256', $token);
        $stmt = $this->db->prepare('INSERT INTO delete_requests(email,firebase_uid,reason,requester_ip_hash,verification_token_hash) VALUES(?,?,?,?,?)');
        $stmt->execute([$email, $uid, $reason, hash('sha256', $ip), $hash]);
        $requestId = (int) $this->db->lastInsertId();

        $from = trim((string) ($this->config['app']['public_email_from'] ?? ''));
        if (!filter_var($from, FILTER_VALIDATE_EMAIL)) {
            $this->db->prepare('DELETE FROM delete_requests WHERE id=?')->execute([$requestId]);
            throw new \RuntimeException('PUBLIC_EMAIL_FROM must be configured for deletion verification');
        }
        $baseUrl = rtrim((string) $this->config['app']['base_url'], '/');
        if (!filter_var($baseUrl, FILTER_VALIDATE_URL) || !str_starts_with($baseUrl, 'https://')) {
            $this->db->prepare('DELETE FROM delete_requests WHERE id=?')->execute([$requestId]);
            throw new \RuntimeException('APP_BASE_URL must be configured as an HTTPS origin');
        }
        $link = $baseUrl . '/delete-account/verify?token=' . rawurlencode($token);
        $subject = 'Verify your JawlineFit deletion request';
        $ttl = (int) $this->config['app']['delete_verification_ttl'];
        $hours = max(1, (int) ceil($ttl / 3600));
        $message = "A request was made to delete the JawlineFit account associated with this address.\n\nVerify ownership and continue: {$link}\n\nIf you did not make this request, do not use the link. The link expires in {$hours} hours.";
        $headers = ['From: ' . $from, 'Content-Type: text/plain; charset=UTF-8', 'X-Auto-Response-Suppress: All'];
        if (!mail($email, $subject, $message, implode("\r\n", $headers))) {
            $this->db->prepare('DELETE FROM delete_requests WHERE id=?')->execute([$requestId]);
            throw new \RuntimeException('Unable to send deletion verification email');
        }
        Http::redirect('/delete-account?submitted=1');
    }

    private function verifyDeleteRequest(): never
    {
        $token = (string) ($_GET['token'] ?? '');
        if (!preg_match('/^[a-f0-9]{64}$/', $token)) {
            $this->render('Verification link', '<div class="alert alert-danger">This verification link is invalid or expired.</div>');
        }
        $cutoff = gmdate('Y-m-d H:i:s', time() - (int) $this->config['app']['delete_verification_ttl']);
        $stmt = $this->db->prepare("UPDATE delete_requests SET verified_at=UTC_TIMESTAMP() WHERE verification_token_hash=? AND status='pending' AND verified_at IS NULL AND created_at>=?");
        $stmt->execute([hash('sha256', $token), $cutoff]);
        if ($stmt->rowCount() !== 1) {
            $this->render('Verification link', '<div class="alert alert-danger">This verification link is invalid, expired, or already used.</div>');
        }
        $this->render('Request verified', '<div class="alert alert-success"><h2>Email ownership verified</h2><p>The operator can now match and process the account deletion request. You will receive any necessary follow-up at the verified address.</p></div>');
    }

    private function privacy(): string
    {
        $operator = e((string) $this->config['legal']['operator_name']);
        $support = e((string) $this->config['legal']['support_email']);
        $accountDays = (int) $this->config['legal']['account_retention_days'];
        $auditDays = (int) $this->config['legal']['request_audit_retention_days'];
        return '<p class="text-secondary">Effective: 20 October 2026</p><p>' . $operator . ' operates JawlineFit. Privacy questions may be sent to <a href="mailto:' . $support . '">' . $support . '</a>.</p><h2>Information we process</h2><p>Face and progress photos are processed only on the device, remain in app-private storage, and are not collected by JawlineFit. We process your Firebase account identifier, optional profile details, notification token, and Google Play purchase status. Payment card details are handled by Google Play and are not stored by JawlineFit.</p><h2>How information is used</h2><p>Information is used to authenticate accounts, deliver requested content, maintain premium access, protect the service, provide support, and meet legal duties. Personal information is not sold.</p><h2>Providers and retention</h2><p>Firebase, Google Play, hosting, and operational providers process information only as needed for their services. Account data is kept while the account exists and is removed when a verified deletion is completed. Residual operational backups are scheduled for removal within ' . $accountDays . ' days. The minimum request audit needed to demonstrate processing is retained for up to ' . $auditDays . ' days where legally required, then removed under the operator’s retention schedule.</p><h2>Children</h2><p>JawlineFit is not directed to children under ' . (int) $this->config['legal']['children_minimum_age'] . '. A parent or guardian who believes a child supplied account information should contact the operator.</p><h2>Your choices</h2><p>You may request access, correction, or deletion. Delete a signed-in account in the app, or use the <a href="/delete-account">ownership verification form</a> if sign-in is unavailable.</p><h2>Security</h2><p>We use encrypted transport and access controls. No internet service is risk-free.</p>';
    }

    private function terms(): string
    {
        $operator = e((string) $this->config['legal']['operator_name']);
        $support = e((string) $this->config['legal']['support_email']);
        $minimumAge = (int) $this->config['legal']['children_minimum_age'];
        return '<p class="text-secondary">Effective: 20 October 2026</p><p>These terms are between you and ' . $operator . ', the operator of JawlineFit. Contact <a href="mailto:' . $support . '">' . $support . '</a> with service questions.</p><h2>Using JawlineFit</h2><p>You must be at least ' . $minimumAge . ' to create an account. If local law requires parental consent above that age, a parent or guardian must consent. Use JawlineFit lawfully and provide accurate account information. Do not interfere with the service, bypass access controls, scrape content, or misuse another person’s account.</p><h2>Subscriptions</h2><p>Google Play processes purchases and renewals under the price and billing period shown there. Manage or cancel recurring billing through Google Play. Premium access continues only while Google confirms an eligible state.</p><h2>Content and availability</h2><p>JawlineFit content is licensed for personal, non-commercial use. Features may change, pause, or end, and uninterrupted availability is not assured.</p><h2>Safety</h2><p>The <a href="/disclaimer">Wellness &amp; Results Disclaimer</a> forms part of these terms. Stop any activity that causes pain, dizziness, numbness, breathing difficulty, or unusual symptoms and seek appropriate professional help.</p><h2>Liability and changes</h2><p>To the extent permitted by law, the service is provided “as is.” Liability is limited only where the law permits. Material changes will be communicated through the service or listing.</p>';
    }

    private function disclaimer(): string
    {
        return '<div class="alert alert-warning"><strong>Important:</strong> JawlineFit does not diagnose or prevent disease or medical conditions and does not promise weight loss, fat loss, permanent facial reshaping, a sharper jawline, or any specific outcome.</div><p>JawlineFit provides general educational and wellness content. This is not medical advice and is not a substitute for guidance from a qualified healthcare professional.</p><p>Results vary. Facial structure is largely genetic and appearance is also influenced by age, body composition, hydration, posture, lighting, and health factors. Evidence for permanent structural facial change from exercise is limited.</p><p>Ask a qualified healthcare professional before beginning if you have pain, injury, a jaw or temporomandibular joint condition, recent surgery, neurological symptoms, pregnancy-related concerns, or another health condition. Stop immediately if you experience pain, dizziness, numbness, breathing difficulty, or unusual symptoms.</p>';
    }

    private function deletePage(): string
    {
        if (isset($_GET['submitted'])) {
            return '<div class="alert alert-success"><h2>Check your email</h2><p>If the address can receive mail, a single-use ownership verification link has been sent. The request is not eligible for manual processing until that link is used.</p></div>';
        }
        return '<p>Signed-in users can permanently delete their JawlineFit account in the app. If sign-in is unavailable, submit this form and verify ownership through the email link. After verification, the operator matches exactly one account, removes the user, devices, subscriptions, and purchase data, and retains only the minimum request audit where legally required for up to ' . (int) $this->config['legal']['request_audit_retention_days'] . ' days. Ambiguous matches are held for operator follow-up. Cancel recurring billing separately in Google Play.</p><form method="post"><input type="hidden" name="_csrf" value="'.e(csrf_token()).'"><div class="visually-hidden"><label>Website<input name="website" tabindex="-1" autocomplete="off"></label></div><div class="mb-3"><label class="form-label">Account email</label><input class="form-control" type="email" name="email" maxlength="320" autocomplete="email" required></div><div class="mb-3"><label class="form-label">Firebase UID (optional)</label><input class="form-control" name="firebase_uid" maxlength="128"></div><div class="mb-3"><label class="form-label">Additional matching details (optional)</label><textarea class="form-control" name="reason" maxlength="2000" rows="4"></textarea></div><button class="btn btn-danger">Send ownership verification</button></form>';
    }

    private function render(string $title, string $body): never
    {
        $claims = '<aside class="alert alert-light border mt-4" aria-label="Results notice"><strong>Results vary, facial structure is largely genetic, this is not medical advice.</strong></aside>';
        $operator = e((string) $this->config['legal']['operator_name']);
        $support = e((string) $this->config['legal']['support_email']);
        echo '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>'.e($title).' · JawlineFit</title><link href="/public/assets/bootstrap.min.css" rel="stylesheet"><link href="/public/assets/site.css" rel="stylesheet"></head><body><nav class="navbar navbar-expand bg-dark navbar-dark"><div class="container"><a class="navbar-brand" href="/">JawlineFit</a></div></nav><main class="container py-4 legal"><h1>'.e($title).'</h1>'.$body.$claims.'</main><footer class="container py-4 border-top"><p class="small text-secondary mb-2">Operator: ' . $operator . ' · <a href="mailto:' . $support . '">' . $support . '</a></p><div class="d-flex flex-wrap gap-3"><a href="/privacy">Privacy</a><a href="/terms">Terms</a><a href="/disclaimer">Disclaimer</a><a href="/delete-account">Delete account</a></div></footer></body></html>';
        exit;
    }
}
