<?php
declare(strict_types=1);

namespace JawlineFit;

final class DeletionPolicy
{
    public static function assertTransition(string $current, string $next): void
    {
        if (!in_array($current, ['pending', 'completed', 'rejected'], true) || !in_array($next, ['completed', 'rejected'], true)) {
            throw new HttpException(422, 'invalid_status', 'Deletion request status is invalid');
        }
        if ($current !== 'pending') {
            throw new HttpException(409, 'terminal_request', 'Completed or rejected requests cannot be changed');
        }
    }

    public static function assertSingleMatch(int $count): void
    {
        if ($count === 0) {
            throw new HttpException(422, 'account_not_found', 'No unique account matches this verified request');
        }
        if ($count !== 1) {
            throw new HttpException(422, 'ambiguous_account', 'More than one account matches; a Firebase UID is required');
        }
    }
}
