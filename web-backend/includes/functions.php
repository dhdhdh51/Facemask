<?php
declare(strict_types=1);

use JawlineFit\HttpException;

function e(?string $value): string
{
    return htmlspecialchars($value ?? '', ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function csrf_token(): string
{
    if (empty($_SESSION['csrf'])) {
        $_SESSION['csrf'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['csrf'];
}

function verify_csrf(): void
{
    $provided = $_POST['_csrf'] ?? ($_SERVER['HTTP_X_CSRF_TOKEN'] ?? '');
    if (!is_string($provided) || !hash_equals(csrf_token(), $provided)) {
        throw new HttpException(403, 'csrf_failed', 'The security token is invalid or expired');
    }
}

function valid_email(string $email): string
{
    $email = trim(mb_strtolower($email));
    if (strlen($email) > 320 || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        throw new HttpException(422, 'validation_failed', 'Enter a valid email address');
    }
    return $email;
}

function slugify(string $value): string
{
    $value = iconv('UTF-8', 'ASCII//TRANSLIT//IGNORE', $value) ?: $value;
    $value = strtolower(trim(preg_replace('/[^a-zA-Z0-9]+/', '-', $value) ?? '', '-'));
    return $value !== '' ? substr($value, 0, 180) : bin2hex(random_bytes(8));
}

function bool_value(mixed $value): int
{
    return in_array($value, [1, '1', true, 'on', 'true'], true) ? 1 : 0;
}

function contains_forbidden_claim(string $value): bool
{
    $terms = ['cu' . 're', 'treat' . 'ment', 'guaran' . 'teed'];
    $pattern = '/\\b(?:' . implode('|', array_map('preg_quote', $terms)) . ')\\b/i';
    return preg_match($pattern, $value) === 1;
}
