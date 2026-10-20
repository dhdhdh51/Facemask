<?php
declare(strict_types=1);

namespace JawlineFit;

use PDO;

final class RateLimiter
{
    public function __construct(private readonly PDO $db) {}

    public function check(string $key, int $limit, int $windowSeconds): void
    {
        $bucket = hash('sha256', $key);
        $now = new \DateTimeImmutable('now', new \DateTimeZone('UTC'));
        $start = $now->format('Y-m-d H:i:s');
        $expiry = $now->modify('+' . $windowSeconds . ' seconds')->format('Y-m-d H:i:s');
        $sql = "INSERT INTO rate_limits (bucket,hits,window_started_at,expires_at) VALUES (?,1,?,?)
                ON DUPLICATE KEY UPDATE
                hits=IF(expires_at <= VALUES(window_started_at),1,hits+1),
                window_started_at=IF(expires_at <= VALUES(window_started_at),VALUES(window_started_at),window_started_at),
                expires_at=IF(expires_at <= VALUES(window_started_at),VALUES(expires_at),expires_at)";
        $this->db->prepare($sql)->execute([$bucket, $start, $expiry]);
        $stmt = $this->db->prepare('SELECT hits, expires_at FROM rate_limits WHERE bucket=?');
        $stmt->execute([$bucket]);
        $row = $stmt->fetch();
        if ($row && (int) $row['hits'] > $limit) {
            $retry = max(1, strtotime((string) $row['expires_at']) - time());
            header('Retry-After: ' . $retry);
            throw new HttpException(429, 'rate_limited', 'Too many requests; try again later');
        }
        if (random_int(1, 200) === 1) {
            $this->db->prepare('DELETE FROM rate_limits WHERE expires_at < UTC_TIMESTAMP()')->execute();
        }
    }

    public function enforce(string $key, int $limit): void
    {
        $stmt = $this->db->prepare('SELECT hits,expires_at FROM rate_limits WHERE bucket=? AND expires_at>UTC_TIMESTAMP()');
        $stmt->execute([hash('sha256', $key)]);
        $row = $stmt->fetch();
        if ($row && (int) $row['hits'] >= $limit) {
            $retry = max(1, strtotime((string) $row['expires_at'] . ' UTC') - time());
            header('Retry-After: ' . $retry);
            throw new HttpException(429, 'rate_limited', 'Too many requests; try again later');
        }
    }

    public function clear(string $key): void
    {
        $stmt = $this->db->prepare('DELETE FROM rate_limits WHERE bucket=?');
        $stmt->execute([hash('sha256', $key)]);
    }
}
