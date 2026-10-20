<?php
declare(strict_types=1);

namespace JawlineFit;

final class Media
{
    private const IMAGE_MIMES = ['image/jpeg' => 'jpg', 'image/png' => 'png', 'image/webp' => 'webp', 'image/gif' => 'gif'];

    public function __construct(private readonly array $config) {}

    public function process(string $field = 'media', string $thumbnailField = 'thumbnail'): ?array
    {
        if (!isset($_FILES[$field]) || (int) $_FILES[$field]['error'] === UPLOAD_ERR_NO_FILE) {
            return null;
        }
        $file = $_FILES[$field];
        $this->assertUpload($file);
        $mime = (new \finfo(FILEINFO_MIME_TYPE))->file($file['tmp_name']);
        if (isset(self::IMAGE_MIMES[$mime])) {
            $maximum = $mime === 'image/gif'
                ? (int) $this->config['uploads']['gif_max_bytes']
                : (int) $this->config['uploads']['image_max_bytes'];
            if ((int) $file['size'] > $maximum) {
                throw new HttpException(422, 'media_too_large', $mime === 'image/gif' ? 'GIF exceeds the configured size limit' : 'Image exceeds the configured size limit');
            }
            $this->assertSafeImage($file['tmp_name'], $mime);
            return $this->storeImage($file['tmp_name'], $mime);
        }
        if ($mime === 'video/mp4') {
            if ((int) $file['size'] > $this->config['uploads']['video_max_bytes']) {
                throw new HttpException(422, 'media_too_large', 'Video exceeds the configured size limit');
            }
            return $this->storeVideo($file['tmp_name'], $_FILES[$thumbnailField] ?? null);
        }
        throw new HttpException(422, 'invalid_media', 'Allowed media: JPG, PNG, WebP, GIF, or H.264 MP4');
    }

    public function delete(?string $path): void
    {
        if ($path === null || $path === '') {
            return;
        }
        $prefix = rtrim($this->config['uploads']['url_path'], '/') . '/';
        if (!str_starts_with($path, $prefix)) {
            return;
        }
        $relative = substr($path, strlen($prefix));
        if ($relative === '' || str_contains($relative, '..')) {
            return;
        }
        $full = $this->config['uploads']['directory'] . '/' . $relative;
        if (is_file($full)) {
            @unlink($full);
        }
    }

    private function storeImage(string $tmp, string $mime): array
    {
        $this->ensureDirectories();
        $extension = self::IMAGE_MIMES[$mime];
        $name = bin2hex(random_bytes(20));
        $relative = 'media/' . $name . '.' . $extension;
        $full = $this->config['uploads']['directory'] . '/' . $relative;
        $thumbRelative = 'thumbs/' . $name . '.jpg';
        $thumbFull = $this->config['uploads']['directory'] . '/' . $thumbRelative;
        $source = null;
        $thumb = null;
        try {
            if ($mime === 'image/gif') {
                if (!move_uploaded_file($tmp, $full)) {
                    throw new \RuntimeException('Unable to save uploaded GIF');
                }
                $source = $this->loadImage($full, $mime);
            } else {
                $source = $this->loadImage($tmp, $mime);
                if ($mime === 'image/jpeg') {
                    $source = $this->orientJpeg($source, $tmp);
                }
                $source = $this->resizeWithin($source, (int) $this->config['uploads']['image_max_dimension']);
                $this->writeImage($source, $full, $mime);
            }
            $width = (int) ($this->config['uploads']['thumbnail_width'] ?? 400);
            $height = (int) round($width * 9 / 16);
            $thumb = $this->cover($source, $width, $height);
            if (!imagejpeg($thumb, $thumbFull, 84)) {
                throw new \RuntimeException('Unable to write image thumbnail');
            }
        } catch (\Throwable $error) {
            @unlink($full);
            @unlink($thumbFull);
            throw $error;
        } finally {
            if ($thumb instanceof \GdImage) {
                imagedestroy($thumb);
            }
            if ($source instanceof \GdImage) {
                imagedestroy($source);
            }
        }
        @chmod($full, 0640);
        @chmod($thumbFull, 0640);
        return ['media_path' => $this->url($relative), 'media_type' => $mime === 'image/gif' ? 'gif' : 'image', 'thumbnail_path' => $this->url($thumbRelative)];
    }

    private function storeVideo(string $tmp, ?array $manualThumbnail): array
    {
        $this->ensureDirectories();
        $probe = $this->run([$this->config['uploads']['ffprobe'], '-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=codec_name', '-of', 'json', $tmp]);
        $data = json_decode($probe, true);
        if (($data['streams'][0]['codec_name'] ?? null) !== 'h264') {
            throw new HttpException(422, 'invalid_video_codec', 'MP4 video must use the H.264 video codec');
        }
        $name = bin2hex(random_bytes(20));
        $relative = 'media/' . $name . '.mp4';
        $full = $this->config['uploads']['directory'] . '/' . $relative;
        if (!move_uploaded_file($tmp, $full)) {
            throw new \RuntimeException('Unable to save uploaded video');
        }
        $thumbRelative = 'thumbs/' . $name . '.jpg';
        $thumbFull = $this->config['uploads']['directory'] . '/' . $thumbRelative;
        try {
            if ($manualThumbnail !== null && (int) ($manualThumbnail['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_NO_FILE) {
                $this->assertUpload($manualThumbnail);
                if ((int) $manualThumbnail['size'] > $this->config['uploads']['thumbnail_max_bytes']) {
                    throw new HttpException(422, 'thumbnail_too_large', 'Thumbnail exceeds the configured size limit');
                }
                $mime = (new \finfo(FILEINFO_MIME_TYPE))->file($manualThumbnail['tmp_name']);
                if (!isset(self::IMAGE_MIMES[$mime])) {
                    throw new HttpException(422, 'invalid_thumbnail', 'Thumbnail must be JPG, PNG, WebP, or GIF');
                }
                $this->assertSafeImage($manualThumbnail['tmp_name'], $mime);
                $source = $this->loadImage($manualThumbnail['tmp_name'], $mime);
                $width = (int) ($this->config['uploads']['thumbnail_width'] ?? 400);
                $height = (int) round($width * 9 / 16);
                $thumb = $this->cover($source, $width, $height);
                if (!imagejpeg($thumb, $thumbFull, 84)) {
                    imagedestroy($thumb);
                    imagedestroy($source);
                    throw new \RuntimeException('Unable to write manual video thumbnail');
                }
                imagedestroy($thumb);
                imagedestroy($source);
            } else {
                try {
                    $width = (int) ($this->config['uploads']['thumbnail_width'] ?? 400);
                    $height = (int) round($width * 9 / 16);
                    $this->run([$this->config['uploads']['ffmpeg'], '-y', '-ss', '00:00:01', '-i', $full, '-frames:v', '1', '-vf', 'scale=' . $width . ':' . $height . ':force_original_aspect_ratio=increase,crop=' . $width . ':' . $height, $thumbFull]);
                } catch (\Throwable) {
                    $this->neutralThumbnail($thumbFull);
                }
                if (!is_file($thumbFull) || filesize($thumbFull) === 0) {
                    $this->neutralThumbnail($thumbFull);
                }
            }
        } catch (\Throwable $e) {
            @unlink($full);
            @unlink($thumbFull);
            throw $e;
        }
        @chmod($full, 0640);
        @chmod($thumbFull, 0640);
        return ['media_path' => $this->url($relative), 'media_type' => 'video', 'thumbnail_path' => $this->url($thumbRelative)];
    }

    private function assertSafeImage(string $path, string $mime): void
    {
        $info = @getimagesize($path);
        $expectedTypes = ['image/jpeg' => IMAGETYPE_JPEG, 'image/png' => IMAGETYPE_PNG, 'image/webp' => IMAGETYPE_WEBP, 'image/gif' => IMAGETYPE_GIF];
        $width = is_array($info) ? (int) ($info[0] ?? 0) : 0;
        $height = is_array($info) ? (int) ($info[1] ?? 0) : 0;
        $type = is_array($info) ? (int) ($info[2] ?? 0) : 0;
        if ($width < 1 || $height < 1 || $width * $height > 25000000 || $type !== ($expectedTypes[$mime] ?? 0)) {
            throw new HttpException(422, 'invalid_media', 'The image dimensions or format are invalid');
        }
    }

    private function loadImage(string $path, string $mime): \GdImage
    {
        $image = match ($mime) {
            'image/jpeg' => @imagecreatefromjpeg($path),
            'image/png' => @imagecreatefrompng($path),
            'image/webp' => @imagecreatefromwebp($path),
            'image/gif' => @imagecreatefromgif($path),
            default => false,
        };
        if (!$image instanceof \GdImage) {
            throw new HttpException(422, 'invalid_media', 'The image could not be decoded');
        }
        return $image;
    }

    private function writeImage(\GdImage $image, string $path, string $mime): void
    {
        $ok = match ($mime) {
            'image/jpeg' => imagejpeg($image, $path, 88),
            'image/png' => imagepng($image, $path, 7),
            'image/webp' => imagewebp($image, $path, 86),
            default => false,
        };
        if (!$ok) {
            throw new \RuntimeException('Unable to write processed image');
        }
    }

    private function resizeWithin(\GdImage $source, int $max): \GdImage
    {
        $width = imagesx($source);
        $height = imagesy($source);
        if ($width <= $max && $height <= $max) {
            return $source;
        }
        $scale = min($max / $width, $max / $height);
        $new = imagecreatetruecolor(max(1, (int) round($width * $scale)), max(1, (int) round($height * $scale)));
        if (!imagealphablending($new, false) || !imagesavealpha($new, true)) {
            imagedestroy($new);
            throw new \RuntimeException('Unable to prepare resized image');
        }
        if (!imagecopyresampled($new, $source, 0, 0, 0, 0, imagesx($new), imagesy($new), $width, $height)) {
            imagedestroy($new);
            throw new \RuntimeException('Unable to resize image');
        }
        imagedestroy($source);
        return $new;
    }

    private function cover(\GdImage $source, int $targetWidth, int $targetHeight): \GdImage
    {
        $width = imagesx($source);
        $height = imagesy($source);
        $scale = max($targetWidth / $width, $targetHeight / $height);
        $sourceWidth = (int) round($targetWidth / $scale);
        $sourceHeight = (int) round($targetHeight / $scale);
        $sourceX = max(0, (int) (($width - $sourceWidth) / 2));
        $sourceY = max(0, (int) (($height - $sourceHeight) / 2));
        $thumb = imagecreatetruecolor($targetWidth, $targetHeight);
        $background = imagecolorallocate($thumb, 248, 249, 250);
        if (!imagefill($thumb, 0, 0, $background)) {
            imagedestroy($thumb);
            throw new \RuntimeException('Unable to prepare image thumbnail');
        }
        if (!imagecopyresampled($thumb, $source, 0, 0, $sourceX, $sourceY, $targetWidth, $targetHeight, $sourceWidth, $sourceHeight)) {
            imagedestroy($thumb);
            throw new \RuntimeException('Unable to create image thumbnail');
        }
        return $thumb;
    }

    private function orientJpeg(\GdImage $image, string $path): \GdImage
    {
        if (!function_exists('exif_read_data')) {
            return $image;
        }
        $exif = @exif_read_data($path);
        $orientation = (int) ($exif['Orientation'] ?? 1);
        $rotated = match ($orientation) {
            3 => imagerotate($image, 180, 0),
            6 => imagerotate($image, -90, 0),
            8 => imagerotate($image, 90, 0),
            default => $image,
        };
        if ($rotated !== $image && $rotated instanceof \GdImage) {
            imagedestroy($image);
            return $rotated;
        }
        return $image;
    }

    private function neutralThumbnail(string $path): void
    {
        $width = (int) ($this->config['uploads']['thumbnail_width'] ?? 400);
        $height = (int) round($width * 9 / 16);
        $image = imagecreatetruecolor($width, $height);
        $background = imagecolorallocate($image, 233, 236, 239);
        $foreground = imagecolorallocate($image, 108, 117, 125);
        if (!imagefill($image, 0, 0, $background) || !imagefilledpolygon($image, [[(int) round($width * 0.445), (int) round($height * 0.36)], [(int) round($width * 0.445), (int) round($height * 0.64)], [(int) round($width * 0.586), (int) round($height * 0.5)]], $foreground)) {
            imagedestroy($image);
            throw new \RuntimeException('Unable to create fallback video thumbnail');
        }
        if (!imagejpeg($image, $path, 84)) {
            imagedestroy($image);
            throw new \RuntimeException('Unable to write fallback video thumbnail');
        }
        imagedestroy($image);
    }

    private function assertUpload(array $file): void
    {
        if ((int) ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK || !isset($file['tmp_name']) || !is_uploaded_file($file['tmp_name'])) {
            throw new HttpException(422, 'upload_failed', 'The media upload did not complete');
        }
    }

    private function ensureDirectories(): void
    {
        foreach (['media', 'thumbs'] as $directory) {
            $path = $this->config['uploads']['directory'] . '/' . $directory;
            if (!is_dir($path) && !mkdir($path, 0750, true) && !is_dir($path)) {
                throw new \RuntimeException('Unable to create upload directory');
            }
        }
    }

    private function url(string $relative): string
    {
        return rtrim($this->config['uploads']['url_path'], '/') . '/' . $relative;
    }

    private function run(array $command): string
    {
        $escaped = implode(' ', array_map('escapeshellarg', $command));
        $output = [];
        $code = 0;
        exec($escaped . ' 2>&1', $output, $code);
        if ($code !== 0) {
            throw new \RuntimeException('Media processor failed: ' . implode("\n", array_slice($output, -3)));
        }
        return implode("\n", $output);
    }
}
