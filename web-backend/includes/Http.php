<?php
declare(strict_types=1);

namespace JawlineFit;

final class Http
{
    public static function success(mixed $data, int $status = 200): never
    {
        self::json(['success' => true, 'data' => $data], $status);
    }

    public static function json(mixed $data, int $status = 200): never
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode($data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_INVALID_UTF8_SUBSTITUTE);
        exit;
    }

    public static function error(int $status, string $error): never
    {
        self::json(['success' => false, 'error' => $error], $status);
    }

    public static function body(): array
    {
        $type = strtolower(trim(explode(';', $_SERVER['CONTENT_TYPE'] ?? '')[0]));
        if ($type !== 'application/json') {
            throw new HttpException(415, 'unsupported_media_type', 'Content-Type must be application/json');
        }
        $raw = file_get_contents('php://input');
        if ($raw === false || strlen($raw) > 1048576) {
            throw new HttpException(413, 'payload_too_large', 'Request body is too large');
        }
        try {
            $value = json_decode($raw, true, 64, JSON_THROW_ON_ERROR);
        } catch (\JsonException) {
            throw new HttpException(400, 'invalid_json', 'Request body contains invalid JSON');
        }
        if (!is_array($value) || array_is_list($value)) {
            throw new HttpException(400, 'invalid_json', 'A JSON object is required');
        }
        return $value;
    }

    public static function bearer(): string
    {
        $header = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
        if (!preg_match('/^Bearer\s+([^\s]+)$/i', $header, $match)) {
            throw new HttpException(401, 'unauthenticated', 'A valid Bearer token is required');
        }
        return $match[1];
    }

    public static function clientIp(array $trustedProxies): string
    {
        $remote = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
        if (in_array($remote, $trustedProxies, true)) {
            $forwarded = trim(explode(',', $_SERVER['HTTP_X_FORWARDED_FOR'] ?? '')[0]);
            if (filter_var($forwarded, FILTER_VALIDATE_IP)) {
                return $forwarded;
            }
        }
        return filter_var($remote, FILTER_VALIDATE_IP) ? $remote : '0.0.0.0';
    }

    public static function redirect(string $path): never
    {
        header('Location: ' . $path, true, 303);
        exit;
    }
}
