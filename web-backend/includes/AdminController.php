<?php
declare(strict_types=1);

namespace JawlineFit;

use PDO;
use PDOException;

final class AdminController
{
    private Media $media;
    private array $entities;

    public function __construct(private readonly PDO $db, private readonly array $config)
    {
        $this->media = new Media($config);
        $this->entities = [
            'nuskhe' => ['table' => 'nuskhe', 'label' => 'Nuskhe', 'title' => 'title', 'fields' => [
                'title' => ['Title','text',true], 'concern' => ['Concern','text',true], 'skin_type' => ['Skin type','text',true],
                'ingredients' => ['Ingredients (one per line)','textarea',true], 'steps' => ['Steps (one per line)','textarea',true], 'frequency' => ['Frequency','text',true], 'warning' => ['Warning','textarea',false],
                'is_premium' => ['Premium','checkbox',false], 'is_published' => ['Published','checkbox',false], 'sort_order' => ['Sort order','number',false],
            ], 'media' => true],
            'routines' => ['table' => 'routines', 'label' => 'Routines', 'title' => 'title', 'fields' => [
                'title' => ['Title','text',true], 'description' => ['Description','textarea',true], 'duration_min' => ['Duration (minutes)','number',true],
                'is_premium' => ['Premium','checkbox',false], 'is_published' => ['Published','checkbox',false], 'sort_order' => ['Sort order','number',false],
            ], 'media' => true],
            'tips' => ['table' => 'tips', 'label' => 'Tips', 'title' => 'category', 'fields' => [
                'category' => ['Category','text',true], 'text' => ['Text','textarea',true],
                'is_published' => ['Published','checkbox',false], 'sort_order' => ['Sort order','number',false],
            ], 'media' => true],
            'plans' => ['table' => 'plans', 'label' => 'Plans', 'title' => 'name', 'fields' => [
                'name' => ['Name','text',true], 'product_id' => ['Google product ID','text',true], 'base_plan_id' => ['Google base plan ID','text',true], 'duration_days' => ['Duration (days)','number',true],
                'is_active' => ['Active','checkbox',false], 'sort_order' => ['Sort order','number',false],
            ], 'media' => false],
        ];
        $this->startSession();
    }

    public function dispatch(string $method, string $path): never
    {
        if ($path === '/admin/login') {
            $method === 'POST' ? $this->login() : $this->loginPage();
        }
        if ($path === '/admin/setup') {
            throw new HttpException(404, 'not_found', 'Use /setup for one-time installation');
        }
        $this->requireAdmin();
        if ($method === 'POST') {
            verify_csrf();
        }
        if ($path === '/admin' || $path === '/admin/') {
            $this->dashboard();
        }
        if ($path === '/admin/logout' && $method === 'POST') {
            $this->logout();
        }
        if ($path === '/admin/change-password') {
            $method === 'POST' ? $this->changePassword() : $this->passwordPage();
        }
        if ($path === '/admin/users') {
            $this->users();
        }
        if ($method === 'POST' && preg_match('#^/admin/users/(\d+)/premium$#', $path, $m)) {
            $this->premium((int) $m[1]);
        }
        if ($path === '/admin/admins') {
            $this->admins();
        }
        if ($path === '/admin/admins/new') {
            $method === 'POST' ? $this->saveAdmin(null) : $this->adminForm(null);
        }
        if (preg_match('#^/admin/admins/(\d+)/edit$#', $path, $m)) {
            $method === 'POST' ? $this->saveAdmin((int) $m[1]) : $this->adminForm((int) $m[1]);
        }
        if ($method === 'POST' && preg_match('#^/admin/admins/(\d+)/(toggle|delete)$#', $path, $m)) {
            $this->adminAction((int) $m[1], $m[2]);
        }
        if ($path === '/admin/deletion-requests') {
            $this->deletionRequests();
        }
        if ($method === 'POST' && preg_match('#^/admin/deletion-requests/(\d+)$#', $path, $m)) {
            $this->deletionRequestUpdate((int) $m[1]);
        }
        if (preg_match('#^/admin/routines/(\d+)/steps(?:/(new|reorder|(\d+)/(edit|delete)))?$#', $path, $m)) {
            $routineId = (int) $m[1];
            $action = $m[2] ?? '';
            if ($action === 'new') {
                $method === 'POST' ? $this->saveStep($routineId, null) : $this->stepForm($routineId, null);
            } elseif ($action === 'reorder' && $method === 'POST') {
                $this->reorderSteps($routineId);
            } elseif (isset($m[3]) && $m[4] === 'edit') {
                $method === 'POST' ? $this->saveStep($routineId, (int) $m[3]) : $this->stepForm($routineId, (int) $m[3]);
            } elseif (isset($m[3]) && $m[4] === 'delete' && $method === 'POST') {
                $this->deleteStep($routineId, (int) $m[3]);
            } else {
                $this->steps($routineId);
            }
        }
        if (preg_match('#^/admin/(nuskhe|routines|tips|plans)(?:/(new|(\d+)/(edit|delete)))?$#', $path, $m)) {
            $entity = $m[1];
            $action = $m[2] ?? '';
            if ($action === 'new') {
                $method === 'POST' ? $this->saveEntity($entity, null) : $this->entityForm($entity, null);
            } elseif (isset($m[3]) && $m[4] === 'edit') {
                $method === 'POST' ? $this->saveEntity($entity, (int) $m[3]) : $this->entityForm($entity, (int) $m[3]);
            } elseif (isset($m[3]) && $m[4] === 'delete' && $method === 'POST') {
                $this->deleteEntity($entity, (int) $m[3]);
            } else {
                $this->entityList($entity);
            }
        }
        throw new HttpException(404, 'not_found', 'Admin page not found');
    }

    private function startSession(): void
    {
        if (session_status() === PHP_SESSION_ACTIVE) {
            return;
        }
        session_name($this->config['security']['session_name']);
        session_set_cookie_params(['lifetime' => 0, 'path' => '/admin', 'secure' => (bool) ($GLOBALS['jawlinefit_secure_request'] ?? false), 'httponly' => true, 'samesite' => 'Strict']);
        ini_set('session.use_strict_mode', '1');
        ini_set('session.use_only_cookies', '1');
        session_start();
    }

    private function requireAdmin(): void
    {
        $id = $_SESSION['admin_id'] ?? null;
        $fingerprint = hash('sha256', $_SERVER['HTTP_USER_AGENT'] ?? '');
        if (!is_int($id) || !hash_equals((string) ($_SESSION['fingerprint'] ?? ''), $fingerprint) || (int) ($_SESSION['last_activity'] ?? 0) + $this->config['security']['session_lifetime'] < time()) {
            $this->clearSession();
            Http::redirect('/admin/login');
        }
        $stmt = $this->db->prepare('SELECT id,email,display_name FROM admin_users WHERE id=? AND is_active=1');
        $stmt->execute([$id]);
        $admin = $stmt->fetch();
        if (!$admin) {
            $this->clearSession();
            Http::redirect('/admin/login');
        }
        $_SESSION['admin'] = $admin;
        $_SESSION['last_activity'] = time();
    }

    private function login(): never
    {
        verify_csrf();
        $ip = Http::clientIp($this->config['app']['trusted_proxies']);
        $emailRaw = trim((string) ($_POST['email'] ?? ''));
        $key = 'admin-login:' . $ip . ':' . mb_strtolower($emailRaw);
        $limiter = new RateLimiter($this->db);
        $limiter->enforce($key, (int) $this->config['security']['login_attempts']);
        $stmt = $this->db->prepare('SELECT * FROM admin_users WHERE email=? AND is_active=1');
        $stmt->execute([mb_strtolower($emailRaw)]);
        $admin = $stmt->fetch();
        if (!$admin || !password_verify((string) ($_POST['password'] ?? ''), $admin['password_hash'])) {
            $limiter->check($key, (int) $this->config['security']['login_attempts'], (int) $this->config['security']['login_window']);
            usleep(300000);
            $this->flash('danger', 'Invalid email or password.');
            Http::redirect('/admin/login');
        }
        $limiter->clear($key);
        if (password_needs_rehash($admin['password_hash'], PASSWORD_DEFAULT)) {
            $this->db->prepare('UPDATE admin_users SET password_hash=? WHERE id=?')->execute([password_hash((string) $_POST['password'], PASSWORD_DEFAULT), $admin['id']]);
        }
        session_regenerate_id(true);
        $_SESSION['admin_id'] = (int) $admin['id'];
        $_SESSION['fingerprint'] = hash('sha256', $_SERVER['HTTP_USER_AGENT'] ?? '');
        $_SESSION['last_activity'] = time();
        $this->db->prepare('UPDATE admin_users SET last_login_at=UTC_TIMESTAMP() WHERE id=?')->execute([$admin['id']]);
        Http::redirect('/admin');
    }

    private function loginPage(): never
    {
        if (isset($_SESSION['admin_id'])) {
            Http::redirect('/admin');
        }
        $content = '<div class="row justify-content-center"><div class="col-12 col-sm-9 col-md-6 col-lg-4"><div class="card shadow-sm mt-5"><div class="card-body p-4"><h1 class="h3 mb-4">JawlineFit Admin</h1>' . $this->flashes() . '<form method="post" action="/admin/login"><input type="hidden" name="_csrf" value="' . e(csrf_token()) . '"><div class="mb-3"><label class="form-label">Email</label><input class="form-control" type="email" name="email" autocomplete="username" required></div><div class="mb-3"><label class="form-label">Password</label><input class="form-control" type="password" name="password" autocomplete="current-password" required></div><button class="btn btn-primary w-100">Sign in</button></form></div></div></div></div>';
        $this->render('Sign in', $content, false);
    }

    private function logout(): never
    {
        $this->clearSession();
        Http::redirect('/admin/login');
    }

    private function dashboard(): never
    {
        $counts = [];
        foreach (['users','nuskhe','routines','routine_steps','tips','plans'] as $table) {
            $stmt = $this->db->prepare('SELECT COUNT(*) FROM ' . $table); $stmt->execute(); $counts[$table] = (int) $stmt->fetchColumn();
        }
        $premiumStmt = $this->db->prepare("SELECT COUNT(DISTINCT u.id) FROM users u LEFT JOIN subscriptions s ON s.user_id=u.id AND s.expires_at>UTC_TIMESTAMP() AND s.state IN ('SUBSCRIPTION_STATE_ACTIVE','SUBSCRIPTION_STATE_IN_GRACE_PERIOD','SUBSCRIPTION_STATE_CANCELED') WHERE u.manual_premium_until>UTC_TIMESTAMP() OR s.id IS NOT NULL"); $premiumStmt->execute(); $premium = (int) $premiumStmt->fetchColumn();
        $cards = ['Users' => $counts['users'], 'Premium users' => $premium, 'Nuskhe' => $counts['nuskhe'], 'Routines' => $counts['routines'], 'Routine steps' => $counts['routine_steps'], 'Tips' => $counts['tips'], 'Plans' => $counts['plans']];
        $html = '<h1 class="h2 mb-4">Dashboard</h1><div class="row g-3">';
        foreach ($cards as $label => $count) {
            $html .= '<div class="col-6 col-lg-3"><div class="card h-100"><div class="card-body"><div class="text-secondary">' . e($label) . '</div><div class="display-6">' . $count . '</div></div></div></div>';
        }
        $html .= '</div><div class="d-flex flex-wrap gap-2 mt-4"><a class="btn btn-outline-secondary" href="/admin/change-password">Change password</a><form method="post" action="/admin/logout"><input type="hidden" name="_csrf" value="' . e(csrf_token()) . '"><button class="btn btn-outline-danger">Logout</button></form></div>';
        $this->render('Dashboard', $html);
    }

    private function entityList(string $entity): never
    {
        $meta = $this->entities[$entity];
        [$page, $perPage, $offset] = $this->adminPagination();
        $q = mb_substr(trim((string) ($_GET['q'] ?? '')), 0, 100);
        $where = $q === '' ? '' : ' WHERE ' . $meta['title'] . ' LIKE ?';
        $params = $q === '' ? [] : ['%' . $q . '%'];
        $count = $this->db->prepare('SELECT COUNT(*) FROM ' . $meta['table'] . $where);
        $count->execute($params);
        $total = (int) $count->fetchColumn();
        $stmt = $this->db->prepare('SELECT * FROM ' . $meta['table'] . $where . ' ORDER BY sort_order,id DESC LIMIT ? OFFSET ?');
        $i = 1;
        foreach ($params as $value) $stmt->bindValue($i++, $value);
        $stmt->bindValue($i++, $perPage, PDO::PARAM_INT);
        $stmt->bindValue($i, $offset, PDO::PARAM_INT);
        $stmt->execute();
        $html = '<div class="page-heading d-flex align-items-center justify-content-between gap-2 mb-3"><h1 class="h2 mb-0">' . e($meta['label']) . '</h1><a class="btn btn-primary" href="/admin/' . $entity . '/new">Add new</a></div>' . $this->flashes();
        $html .= $this->searchForm($q) . '<div class="table-responsive mobile-card-table"><table class="table align-middle"><thead><tr><th>ID</th><th>Media</th><th>' . e(ucfirst($meta['title'])) . '</th><th>Status</th><th class="text-end">Actions</th></tr></thead><tbody>';
        foreach ($stmt->fetchAll() as $row) {
            $status = isset($row['is_published']) ? ((bool) $row['is_published'] ? 'Published' : 'Draft') : ((bool) ($row['is_active'] ?? 1) ? 'Active' : 'Inactive');
            $thumb = !empty($row['thumbnail_path']) ? '<img class="admin-thumb" src="' . e($row['thumbnail_path']) . '" alt="">' : '<span class="text-secondary">—</span>';
            $html .= '<tr><td data-label="ID">' . (int) $row['id'] . '</td><td data-label="Media">' . $thumb . '</td><td data-label="Title"><strong>' . e($row[$meta['title']]) . '</strong></td><td data-label="Status">' . e($status) . '</td><td data-label="Actions" class="table-actions">';
            if ($entity === 'routines') $html .= '<a class="btn btn-sm btn-outline-secondary" href="/admin/routines/' . (int) $row['id'] . '/steps">Steps</a> ';
            $html .= '<a class="btn btn-sm btn-outline-primary" href="/admin/' . $entity . '/' . (int) $row['id'] . '/edit">Edit</a> <form class="d-inline confirm-delete" method="post" action="/admin/' . $entity . '/' . (int) $row['id'] . '/delete"><input type="hidden" name="_csrf" value="' . e(csrf_token()) . '"><button class="btn btn-sm btn-outline-danger">Delete</button></form></td></tr>';
        }
        $html .= '</tbody></table></div>' . $this->pagination($page, $perPage, $total, '/admin/' . $entity . '?q=' . rawurlencode($q));
        $this->render($meta['label'], $html);
    }

    private function entityForm(string $entity, ?int $id): never
    {
        $meta = $this->entities[$entity];
        $row = $id === null ? [] : $this->find($meta['table'], $id);
        $html = '<h1 class="h2">' . ($id === null ? 'Add ' : 'Edit ') . e(rtrim($meta['label'], 's')) . '</h1><form class="xhr-form" method="post" enctype="multipart/form-data"><input type="hidden" name="_csrf" value="' . e(csrf_token()) . '"><div class="row"><div class="col-lg-8">';
        foreach ($meta['fields'] as $name => $field) {
            if (in_array($name, ['ingredients', 'steps'], true)) {
                $value = implode("\n", json_decode($row[$name . '_json'] ?? '[]', true) ?: []);
            } else {
                $value = $row[$name] ?? '';
            }
            $html .= $this->field($name, $field, $value);
        }
        $html .= '</div>' . ($meta['media'] ? '<div class="col-lg-4">' . $this->mediaFields($row) . '</div>' : '') . '</div><div class="progress d-none mb-3" aria-label="Upload progress"><div class="progress-bar" style="width:0">0%</div></div><button class="btn btn-primary">Save</button> <a class="btn btn-outline-secondary" href="/admin/' . $entity . '">Cancel</a></form>';
        $this->render('Edit ' . $meta['label'], $html);
    }

    private function saveEntity(string $entity, ?int $id): never
    {
        $meta = $this->entities[$entity];
        $old = $id === null ? [] : $this->find($meta['table'], $id);
        $values = [];
        foreach ($meta['fields'] as $name => $field) {
            [$label, $type, $required] = $field;
            if ($type === 'checkbox') {
                $values[$name] = bool_value($_POST[$name] ?? null);
            } elseif ($type === 'number') {
                $raw = trim((string) ($_POST[$name] ?? ''));
                $values[$name] = $raw === '' && !$required ? null : filter_var($raw, FILTER_VALIDATE_INT);
                if ($values[$name] === false || ($values[$name] !== null && (int) $values[$name] < 0)) throw new HttpException(422, 'validation_failed', $label . ' must be a non-negative integer');
            } else {
                $values[$name] = trim((string) ($_POST[$name] ?? ''));
                if ($required && $values[$name] === '') throw new HttpException(422, 'validation_failed', $label . ' is required');
                $values[$name] = mb_substr($values[$name], 0, $type === 'textarea' ? 15000 : 255);
            }
        }
        $this->validateEntityValues($entity, $values);
        if (isset($values['slug']) && $values['slug'] === '') $values['slug'] = slugify((string) $values['title']);
        if ($entity === 'nuskhe') {
            foreach (['ingredients', 'steps'] as $listField) {
                $list = array_values(array_filter(array_map('trim', preg_split('/\R/', (string) $values[$listField]) ?: []), static fn(string $item): bool => $item !== ''));
                unset($values[$listField]);
                $values[$listField . '_json'] = json_encode($list, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
            }
        }
        $uploaded = $meta['media'] ? $this->media->process() : null;
        if ($uploaded !== null) $values = array_merge($values, $uploaded);
        if ($meta['media'] && $uploaded === null && bool_value($_POST['delete_media'] ?? null)) {
            $values['media_path'] = $values['media_type'] = $values['thumbnail_path'] = null;
        }
        try {
            if ($id === null) {
                $columns = array_keys($values);
                $sql = 'INSERT INTO ' . $meta['table'] . ' (' . implode(',', $columns) . ') VALUES (' . implode(',', array_fill(0, count($columns), '?')) . ')';
                $this->db->prepare($sql)->execute(array_values($values));
            } else {
                $sets = array_map(static fn(string $column): string => $column . '=?', array_keys($values));
                $update = $this->db->prepare('UPDATE ' . $meta['table'] . ' SET ' . implode(',', $sets) . ' WHERE id=?');
                $update->execute([...array_values($values), $id]);
                if (($uploaded !== null || bool_value($_POST['delete_media'] ?? null)) && $update->rowCount() !== 1) {
                    throw new HttpException(409, 'save_conflict', 'The record changed during save; retry the update');
                }
            }
        } catch (\Throwable $e) {
            if ($uploaded !== null) { $this->media->delete($uploaded['media_path']); $this->media->delete($uploaded['thumbnail_path']); }
            if ($e instanceof PDOException && (string) $e->getCode() === '23000') throw new HttpException(422, 'duplicate_value', 'Slug or product ID must be unique');
            throw $e;
        }
        if ($meta['media'] && ($uploaded !== null || bool_value($_POST['delete_media'] ?? null)) && $old) {
            $this->media->delete($old['media_path']);
            $this->media->delete($old['thumbnail_path']);
        }
        $this->flash('success', 'Saved successfully.');
        Http::redirect('/admin/' . $entity);
    }

    private function deleteEntity(string $entity, int $id): never
    {
        $meta = $this->entities[$entity];
        $row = $this->find($meta['table'], $id);
        $stepFiles = [];
        if ($entity === 'routines') {
            $steps = $this->db->prepare('SELECT media_path,thumbnail_path FROM routine_steps WHERE routine_id=?');
            $steps->execute([$id]);
            $stepFiles = $steps->fetchAll();
        }
        $this->db->beginTransaction();
        try {
            $delete = $this->db->prepare('DELETE FROM ' . $meta['table'] . ' WHERE id=?');
            $delete->execute([$id]);
            if ($delete->rowCount() !== 1) {
                throw new HttpException(404, 'not_found', 'Record not found');
            }
            $this->db->commit();
        } catch (\Throwable $error) {
            if ($this->db->inTransaction()) {
                $this->db->rollBack();
            }
            throw $error;
        }
        foreach ($stepFiles as $step) {
            $this->media->delete($step['media_path']);
            $this->media->delete($step['thumbnail_path']);
        }
        if ($meta['media']) {
            $this->media->delete($row['media_path'] ?? null);
            $this->media->delete($row['thumbnail_path'] ?? null);
        }
        $this->flash('success', 'Deleted successfully.');
        Http::redirect('/admin/' . $entity);
    }

    private function steps(int $routineId): never
    {
        $routine = $this->find('routines', $routineId);
        $stmt = $this->db->prepare('SELECT * FROM routine_steps WHERE routine_id=? ORDER BY sort_order,id');
        $stmt->execute([$routineId]);
        $html = '<div class="routine-heading d-flex justify-content-between align-items-center flex-wrap gap-2"><h1 class="h2">Steps: ' . e($routine['title']) . '</h1><a class="btn btn-primary" href="/admin/routines/' . $routineId . '/steps/new">Add step</a></div>' . $this->flashes() . '<form method="post" action="/admin/routines/' . $routineId . '/steps/reorder"><input type="hidden" name="_csrf" value="' . e(csrf_token()) . '"><div id="sortable-steps" class="list-group my-3">';
        foreach ($stmt->fetchAll() as $step) {
            $deleteUrl = '/admin/routines/' . $routineId . '/steps/' . (int) $step['id'] . '/delete';
            $html .= '<div class="routine-step-item list-group-item d-flex align-items-center gap-3" draggable="true"><span class="drag-handle" aria-label="Drag to reorder">☰</span><input type="hidden" name="step_ids[]" value="' . (int) $step['id'] . '">' . ($step['thumbnail_path'] ? '<img class="admin-thumb" src="' . e($step['thumbnail_path']) . '" alt="">' : '') . '<div class="flex-grow-1"><strong>' . e($step['title']) . '</strong><div class="small text-secondary">' . (int) $step['duration_sec'] . ' sec</div></div><a class="btn btn-sm btn-outline-primary" href="/admin/routines/' . $routineId . '/steps/' . (int) $step['id'] . '/edit">Edit</a><button type="submit" formaction="' . e($deleteUrl) . '" formmethod="post" class="btn btn-sm btn-outline-danger delete-step-button">Delete</button></div>';
        }
        $html .= '</div><button class="btn btn-primary">Save order</button> <a class="btn btn-outline-secondary" href="/admin/routines">Back</a></form>';
        $this->render('Routine steps', $html);
    }

    private function stepForm(int $routineId, ?int $id): never
    {
        $routine = $this->find('routines', $routineId);
        $row = $id === null ? [] : $this->findStep($routineId, $id);
        $fields = ['title' => ['Title','text',true], 'instruction' => ['Instruction','textarea',true], 'duration_sec' => ['Duration (seconds)','number',true]];
        $html = '<h1 class="h2">' . ($id ? 'Edit' : 'Add') . ' step for ' . e($routine['title']) . '</h1><form class="xhr-form" method="post" enctype="multipart/form-data"><input type="hidden" name="_csrf" value="' . e(csrf_token()) . '"><div class="row"><div class="col-lg-8">';
        foreach ($fields as $name => $field) $html .= $this->field($name, $field, $row[$name] ?? '');
        $html .= '</div><div class="col-lg-4">' . $this->mediaFields($row) . '</div></div><div class="progress d-none mb-3"><div class="progress-bar" style="width:0">0%</div></div><button class="btn btn-primary">Save</button> <a class="btn btn-outline-secondary" href="/admin/routines/' . $routineId . '/steps">Cancel</a></form>';
        $this->render('Routine step', $html);
    }

    private function saveStep(int $routineId, ?int $id): never
    {
        $this->find('routines', $routineId);
        $old = $id === null ? [] : $this->findStep($routineId, $id);
        $title = mb_substr(trim((string) ($_POST['title'] ?? '')), 0, 180);
        $instruction = mb_substr(trim((string) ($_POST['instruction'] ?? '')), 0, 15000);
        $duration = filter_var($_POST['duration_sec'] ?? null, FILTER_VALIDATE_INT, ['options' => ['min_range' => 0, 'max_range' => 65535]]);
        if ($title === '' || $instruction === '' || $duration === false) throw new HttpException(422, 'validation_failed', 'Valid title, instruction, and duration are required');
        if (contains_forbidden_claim($title . ' ' . $instruction)) throw new HttpException(422, 'claims_policy', 'Content contains a prohibited claims-policy term');
        $values = ['title' => $title, 'instruction' => $instruction, 'duration_sec' => $duration];
        $uploaded = $this->media->process();
        if ($uploaded !== null) $values = array_merge($values, $uploaded);
        if ($uploaded === null && bool_value($_POST['delete_media'] ?? null)) $values['media_path'] = $values['media_type'] = $values['thumbnail_path'] = null;
        try {
            if ($id === null) {
                $max = $this->db->prepare('SELECT COALESCE(MAX(sort_order),-1)+1 FROM routine_steps WHERE routine_id=?'); $max->execute([$routineId]);
                $values = ['routine_id' => $routineId, ...$values, 'sort_order' => (int) $max->fetchColumn()];
                $columns = array_keys($values);
                $this->db->prepare('INSERT INTO routine_steps (' . implode(',', $columns) . ') VALUES (' . implode(',', array_fill(0, count($columns), '?')) . ')')->execute(array_values($values));
            } else {
                $sets = array_map(static fn($k) => $k . '=?', array_keys($values));
                $update = $this->db->prepare('UPDATE routine_steps SET ' . implode(',', $sets) . ' WHERE id=? AND routine_id=?');
                $update->execute([...array_values($values), $id, $routineId]);
                if (($uploaded !== null || bool_value($_POST['delete_media'] ?? null)) && $update->rowCount() !== 1) {
                    throw new HttpException(409, 'save_conflict', 'The step changed during save; retry the update');
                }
            }
        } catch (\Throwable $error) {
            if ($uploaded !== null) {
                $this->media->delete($uploaded['media_path']);
                $this->media->delete($uploaded['thumbnail_path']);
            }
            throw $error;
        }
        if (($uploaded !== null || bool_value($_POST['delete_media'] ?? null)) && $old) { $this->media->delete($old['media_path']); $this->media->delete($old['thumbnail_path']); }
        $this->flash('success', 'Step saved.');
        Http::redirect('/admin/routines/' . $routineId . '/steps');
    }

    private function reorderSteps(int $routineId): never
    {
        $ids = array_map('intval', is_array($_POST['step_ids'] ?? null) ? $_POST['step_ids'] : []);
        $check = $this->db->prepare('SELECT id FROM routine_steps WHERE routine_id=? ORDER BY id'); $check->execute([$routineId]);
        $existing = array_map('intval', $check->fetchAll(PDO::FETCH_COLUMN));
        $sortedIds = $ids; sort($sortedIds); sort($existing);
        if ($sortedIds !== $existing || count($ids) !== count(array_unique($ids))) throw new HttpException(422, 'validation_failed', 'Step order is invalid');
        $this->db->beginTransaction();
        try { $stmt = $this->db->prepare('UPDATE routine_steps SET sort_order=? WHERE id=? AND routine_id=?'); foreach ($ids as $order => $id) $stmt->execute([$order,$id,$routineId]); $this->db->commit(); } catch (\Throwable $e) { $this->db->rollBack(); throw $e; }
        $this->flash('success', 'Step order saved.'); Http::redirect('/admin/routines/' . $routineId . '/steps');
    }

    private function deleteStep(int $routineId, int $id): never
    {
        $step = $this->findStep($routineId, $id);
        $this->db->beginTransaction();
        try {
            $delete = $this->db->prepare('DELETE FROM routine_steps WHERE id=? AND routine_id=?');
            $delete->execute([$id, $routineId]);
            if ($delete->rowCount() !== 1) {
                throw new HttpException(404, 'not_found', 'Step not found');
            }
            $this->db->commit();
        } catch (\Throwable $error) {
            if ($this->db->inTransaction()) {
                $this->db->rollBack();
            }
            throw $error;
        }
        $this->media->delete($step['media_path']);
        $this->media->delete($step['thumbnail_path']);
        $this->flash('success', 'Step deleted.'); Http::redirect('/admin/routines/' . $routineId . '/steps');
    }

    private function users(): never
    {
        [$page, $perPage, $offset] = $this->adminPagination();
        $q = mb_substr(trim((string) ($_GET['q'] ?? '')), 0, 100);
        $where = $q === '' ? '' : ' WHERE u.email LIKE ? OR u.firebase_uid LIKE ? OR u.display_name LIKE ?';
        $params = $q === '' ? [] : ['%' . $q . '%', '%' . $q . '%', '%' . $q . '%'];
        $count = $this->db->prepare('SELECT COUNT(*) FROM users u' . $where);
        $count->execute($params);
        $total = (int) $count->fetchColumn();
        $sql = "SELECT u.*,(SELECT MAX(s.expires_at) FROM subscriptions s WHERE s.user_id=u.id AND s.expires_at>UTC_TIMESTAMP() AND s.state IN ('SUBSCRIPTION_STATE_ACTIVE','SUBSCRIPTION_STATE_IN_GRACE_PERIOD','SUBSCRIPTION_STATE_CANCELED')) subscription_until FROM users u" . $where . ' ORDER BY u.id DESC LIMIT ? OFFSET ?';
        $stmt = $this->db->prepare($sql);
        $position = 1;
        foreach ($params as $value) {
            $stmt->bindValue($position++, $value, PDO::PARAM_STR);
        }
        $stmt->bindValue($position++, $perPage, PDO::PARAM_INT);
        $stmt->bindValue($position, $offset, PDO::PARAM_INT);
        $stmt->execute();
        $html = '<h1 class="h2">Users</h1>' . $this->flashes() . $this->searchForm($q) . '<div class="table-responsive mobile-card-table"><table class="table align-middle"><thead><tr><th>User</th><th>Premium</th><th>Manual access</th></tr></thead><tbody>';
        foreach ($stmt->fetchAll() as $user) {
            $timestamps = array_values(array_filter([strtotime((string) $user['manual_premium_until']), strtotime((string) $user['subscription_until'])]));
            $until = $timestamps === [] ? null : max($timestamps);
            $html .= '<tr><td data-label="User"><strong>' . e($user['display_name'] ?: $user['email'] ?: $user['firebase_uid']) . '</strong><div class="small text-secondary">' . e($user['email']) . '</div></td><td data-label="Premium">' . ($until && $until > time() ? 'Until ' . e(gmdate('Y-m-d', $until)) : 'Free') . '</td><td data-label="Manual access"><form class="manual-premium-form row g-2" method="post" action="/admin/users/' . (int) $user['id'] . '/premium"><input type="hidden" name="_csrf" value="' . e(csrf_token()) . '"><div class="col-12 col-lg"><input class="form-control form-control-sm" type="date" name="until" value="' . e($user['manual_premium_until'] ? substr($user['manual_premium_until'], 0, 10) : '') . '"></div><div class="col-12 col-lg"><input class="form-control form-control-sm" name="reason" maxlength="255" placeholder="Reason" value="' . e($user['manual_premium_reason']) . '"></div><div class="col-12 col-lg-auto d-flex gap-2"><button class="btn btn-sm btn-primary">Set</button><button class="btn btn-sm btn-outline-danger" name="revoke" value="1">Revoke</button></div></form></td></tr>';
        }
        $html .= '</tbody></table></div>' . $this->pagination($page, $perPage, $total, '/admin/users?q=' . rawurlencode($q));
        $this->render('Users', $html);
    }

    private function premium(int $id): never
    {
        $this->find('users',$id); $revoke=bool_value($_POST['revoke']??null); $until=$revoke?null:trim((string)($_POST['until']??'')); $reason=$revoke?null:mb_substr(trim((string)($_POST['reason']??'')),0,255);
        if(!$revoke && (!preg_match('/^\d{4}-\d{2}-\d{2}$/',$until)||strtotime($until.' 23:59:59 UTC')<=time())) throw new HttpException(422,'validation_failed','Premium end date must be in the future');
        $this->db->prepare('UPDATE users SET manual_premium_until=?,manual_premium_reason=? WHERE id=?')->execute([$until?$until.' 23:59:59':null,$reason?:null,$id]); $this->flash('success',$revoke?'Manual premium revoked.':'Manual premium updated.');Http::redirect('/admin/users');
    }

    private function admins(): never
    {
        [$page, $perPage, $offset] = $this->adminPagination();
        $q = mb_substr(trim((string) ($_GET['q'] ?? '')), 0, 100);
        $where = $q === '' ? '' : ' WHERE display_name LIKE ? OR email LIKE ?';
        $params = $q === '' ? [] : ['%' . $q . '%', '%' . $q . '%'];
        $count = $this->db->prepare('SELECT COUNT(*) FROM admin_users' . $where);
        $count->execute($params);
        $total = (int) $count->fetchColumn();
        $stmt = $this->db->prepare('SELECT id,email,display_name,is_active,last_login_at,created_at FROM admin_users' . $where . ' ORDER BY id DESC LIMIT ? OFFSET ?');
        $position = 1;
        foreach ($params as $value) {
            $stmt->bindValue($position++, $value, PDO::PARAM_STR);
        }
        $stmt->bindValue($position++, $perPage, PDO::PARAM_INT);
        $stmt->bindValue($position, $offset, PDO::PARAM_INT);
        $stmt->execute();
        $html = '<div class="page-heading d-flex justify-content-between align-items-center gap-2"><h1 class="h2">Admin accounts</h1><a class="btn btn-primary" href="/admin/admins/new">Add admin</a></div>' . $this->flashes() . $this->searchForm($q) . '<div class="table-responsive mobile-card-table"><table class="table align-middle"><thead><tr><th>Name</th><th>Email</th><th>Status</th><th>Last login</th><th>Actions</th></tr></thead><tbody>';
        foreach ($stmt->fetchAll() as $admin) {
            $id = (int) $admin['id'];
            $html .= '<tr><td data-label="Name">' . e($admin['display_name']) . '</td><td data-label="Email">' . e($admin['email']) . '</td><td data-label="Status">' . ($admin['is_active'] ? 'Active' : 'Disabled') . '</td><td data-label="Last login">' . e($admin['last_login_at'] ?: 'Never') . '</td><td data-label="Actions" class="table-actions"><a class="btn btn-sm btn-outline-primary" href="/admin/admins/' . $id . '/edit">Edit</a> <form class="d-inline" method="post" action="/admin/admins/' . $id . '/toggle"><input type="hidden" name="_csrf" value="' . e(csrf_token()) . '"><button class="btn btn-sm btn-outline-secondary">' . ($admin['is_active'] ? 'Disable' : 'Enable') . '</button></form> <form class="d-inline confirm-delete" method="post" action="/admin/admins/' . $id . '/delete"><input type="hidden" name="_csrf" value="' . e(csrf_token()) . '"><button class="btn btn-sm btn-outline-danger">Delete</button></form></td></tr>';
        }
        $html .= '</tbody></table></div>' . $this->pagination($page, $perPage, $total, '/admin/admins?q=' . rawurlencode($q));
        $this->render('Admins', $html);
    }

    private function adminForm(?int $id): never
    {
        $admin = $id === null ? [] : $this->find('admin_users', $id);
        $editing = $id !== null;
        $html = '<h1 class="h2">' . ($editing ? 'Edit admin' : 'Add admin') . '</h1><form method="post"><input type="hidden" name="_csrf" value="' . e(csrf_token()) . '">' . $this->field('display_name', ['Display name','text',true], $admin['display_name'] ?? '') . $this->field('email', ['Email','email',true], $admin['email'] ?? '') . $this->field('password', [$editing ? 'New password (optional, 12+ characters)' : 'Password (12+ characters)','password',!$editing], '') . '<button class="btn btn-primary">' . ($editing ? 'Save admin' : 'Create admin') . '</button> <a class="btn btn-outline-secondary" href="/admin/admins">Cancel</a></form>';
        $this->render($editing ? 'Edit admin' : 'Add admin', $html);
    }

    private function saveAdmin(?int $id): never
    {
        if ($id !== null) {
            $this->find('admin_users', $id);
        }
        $name = mb_substr(trim((string) ($_POST['display_name'] ?? '')), 0, 120);
        $email = valid_email((string) ($_POST['email'] ?? ''));
        $password = (string) ($_POST['password'] ?? '');
        if ($name === '' || ($id === null && strlen($password) < 12) || ($password !== '' && strlen($password) < 12)) {
            throw new HttpException(422, 'validation_failed', 'Name and any supplied password of at least 12 characters are required');
        }
        try {
            if ($id === null) {
                $this->db->prepare('INSERT INTO admin_users(email,password_hash,display_name) VALUES(?,?,?)')->execute([$email, password_hash($password, PASSWORD_DEFAULT), $name]);
            } elseif ($password !== '') {
                $this->db->prepare('UPDATE admin_users SET email=?,display_name=?,password_hash=?,password_changed_at=UTC_TIMESTAMP() WHERE id=?')->execute([$email, $name, password_hash($password, PASSWORD_DEFAULT), $id]);
            } else {
                $this->db->prepare('UPDATE admin_users SET email=?,display_name=? WHERE id=?')->execute([$email, $name, $id]);
            }
        } catch (PDOException $error) {
            if ((string) $error->getCode() === '23000') {
                throw new HttpException(422, 'duplicate_value', 'An admin with this email already exists');
            }
            throw $error;
        }
        $this->flash('success', $id === null ? 'Admin created.' : 'Admin updated.');
        Http::redirect('/admin/admins');
    }

    private function adminAction(int $id, string $action): never
    {
        $this->db->beginTransaction();
        try {
            $select = $this->db->prepare('SELECT id,is_active FROM admin_users ORDER BY id FOR UPDATE');
            $select->execute();
            $rows = $select->fetchAll();
            $target = null;
            $activeCount = 0;
            foreach ($rows as $row) {
                $activeCount += (int) $row['is_active'];
                if ((int) $row['id'] === $id) {
                    $target = $row;
                }
            }
            if ($target === null) {
                throw new HttpException(404, 'not_found', 'Record not found');
            }
            $targetActive = (bool) $target['is_active'];
            $unsafe = $action === 'delete' || ($action === 'toggle' && $targetActive);
            if ($unsafe && $id === (int) $_SESSION['admin_id']) {
                throw new HttpException(422, 'self_action', 'You cannot disable or delete your own account');
            }
            if ($unsafe && $targetActive && $activeCount <= 1) {
                throw new HttpException(422, 'last_admin', 'At least one active admin is required');
            }
            if ($action === 'delete') {
                $this->db->prepare('DELETE FROM admin_users WHERE id=?')->execute([$id]);
            } else {
                $this->db->prepare('UPDATE admin_users SET is_active=? WHERE id=?')->execute([$targetActive ? 0 : 1, $id]);
            }
            $this->db->commit();
        } catch (\Throwable $error) {
            if ($this->db->inTransaction()) {
                $this->db->rollBack();
            }
            throw $error;
        }
        $this->flash('success', 'Admin account updated.');
        Http::redirect('/admin/admins');
    }

    private function passwordPage(): never
    {
        $html='<h1 class="h2">Change password</h1>'.$this->flashes().'<form method="post"><input type="hidden" name="_csrf" value="'.e(csrf_token()).'"><div class="mb-3"><label class="form-label">Current password</label><input class="form-control" type="password" name="current_password" required></div><div class="mb-3"><label class="form-label">New password (12+ characters)</label><input class="form-control" type="password" name="new_password" minlength="12" required></div><button class="btn btn-primary">Change password</button></form>';$this->render('Change password',$html);
    }

    private function changePassword(): never
    {
        $stmt=$this->db->prepare('SELECT password_hash FROM admin_users WHERE id=?');$stmt->execute([$_SESSION['admin_id']]);$hash=$stmt->fetchColumn();$new=(string)($_POST['new_password']??'');if(!$hash||!password_verify((string)($_POST['current_password']??''),$hash)||strlen($new)<12)throw new HttpException(422,'validation_failed','Current password is incorrect or new password is too short');$this->db->prepare('UPDATE admin_users SET password_hash=?,password_changed_at=UTC_TIMESTAMP() WHERE id=?')->execute([password_hash($new,PASSWORD_DEFAULT),$_SESSION['admin_id']]);session_regenerate_id(true);$this->flash('success','Password changed.');Http::redirect('/admin/change-password');
    }

    private function deletionRequests(): never
    {
        [$page, $perPage, $offset] = $this->adminPagination();
        $q = mb_substr(trim((string) ($_GET['q'] ?? '')), 0, 100);
        $where = $q === '' ? '' : ' WHERE email LIKE ? OR firebase_uid LIKE ? OR status LIKE ?';
        $params = $q === '' ? [] : ['%' . $q . '%', '%' . $q . '%', '%' . $q . '%'];
        $count = $this->db->prepare('SELECT COUNT(*) FROM delete_requests' . $where);
        $count->execute($params);
        $total = (int) $count->fetchColumn();
        $stmt = $this->db->prepare('SELECT id,email,firebase_uid,reason,status,verified_at,created_at FROM delete_requests' . $where . ' ORDER BY created_at DESC LIMIT ? OFFSET ?');
        $position = 1;
        foreach ($params as $value) {
            $stmt->bindValue($position++, $value, PDO::PARAM_STR);
        }
        $stmt->bindValue($position++, $perPage, PDO::PARAM_INT);
        $stmt->bindValue($position, $offset, PDO::PARAM_INT);
        $stmt->execute();
        $html = '<h1 class="h2">Deletion requests</h1>' . $this->flashes() . $this->searchForm($q) . '<div class="table-responsive mobile-card-table"><table class="table align-middle"><thead><tr><th>Date</th><th>Account</th><th>Ownership</th><th>Reason</th><th>Status</th></tr></thead><tbody>';
        foreach ($stmt->fetchAll() as $request) {
            $html .= '<tr><td data-label="Date">' . e($request['created_at']) . '</td><td data-label="Account">' . e($request['email']) . '<div class="small text-secondary">' . e($request['firebase_uid']) . '</div></td><td data-label="Ownership">' . ($request['verified_at'] ? '<span class="badge text-bg-success">Verified</span>' : '<span class="badge text-bg-warning">Pending</span>') . '</td><td data-label="Reason">' . e($request['reason']) . '</td><td data-label="Status">';
            if ($request['status'] === 'pending') {
                $html .= '<form class="d-flex flex-wrap gap-2" method="post" action="/admin/deletion-requests/' . (int) $request['id'] . '"><input type="hidden" name="_csrf" value="' . e(csrf_token()) . '"><select class="form-select form-select-sm" name="status"><option value="completed">completed</option><option value="rejected">rejected</option></select><button class="btn btn-sm btn-primary">Save</button></form>';
            } else {
                $html .= '<span class="badge text-bg-secondary">' . e($request['status']) . '</span>';
            }
            $html .= '</td></tr>';
        }
        $html .= '</tbody></table></div>' . $this->pagination($page, $perPage, $total, '/admin/deletion-requests?q=' . rawurlencode($q));
        $this->render('Deletion requests', $html);
    }

    private function deletionRequestUpdate(int $id): never
    {
        $status = (string) ($_POST['status'] ?? '');
        if (!in_array($status, ['completed', 'rejected'], true)) {
            throw new HttpException(422, 'validation_failed', 'Invalid status');
        }
        $this->db->beginTransaction();
        try {
            $stmt = $this->db->prepare('SELECT id,email,firebase_uid,status,verified_at FROM delete_requests WHERE id=? FOR UPDATE');
            $stmt->execute([$id]);
            $request = $stmt->fetch();
            if (!$request) {
                throw new HttpException(404, 'not_found', 'Deletion request not found');
            }
            DeletionPolicy::assertTransition((string) $request['status'], $status);
            if ($status === 'completed') {
                if (!$request['verified_at']) {
                    throw new HttpException(422, 'ownership_unverified', 'Email ownership must be verified before completion');
                }
                if ($request['firebase_uid']) {
                    $match = $this->db->prepare('SELECT id FROM users WHERE firebase_uid=? AND email=? FOR UPDATE');
                    $match->execute([$request['firebase_uid'], $request['email']]);
                } else {
                    $match = $this->db->prepare('SELECT id FROM users WHERE email=? FOR UPDATE');
                    $match->execute([$request['email']]);
                }
                $ids = array_map('intval', $match->fetchAll(PDO::FETCH_COLUMN));
                DeletionPolicy::assertSingleMatch(count($ids));
                $delete = $this->db->prepare('DELETE FROM users WHERE id=?');
                $delete->execute([$ids[0]]);
                if ($delete->rowCount() !== 1) {
                    throw new HttpException(409, 'delete_conflict', 'Account changed during deletion; operator follow-up is required');
                }
            }
            $update = $this->db->prepare('UPDATE delete_requests SET status=?,reason=NULL,firebase_uid=NULL,requester_ip_hash=NULL,verification_token_hash=NULL WHERE id=? AND status=\'pending\'');
            $update->execute([$status, $id]);
            if ($update->rowCount() !== 1) {
                throw new HttpException(409, 'terminal_request', 'Completed or rejected requests cannot be changed');
            }
            $this->db->commit();
        } catch (\Throwable $error) {
            if ($this->db->inTransaction()) {
                $this->db->rollBack();
            }
            throw $error;
        }
        $this->flash('success', 'Request updated.');
        Http::redirect('/admin/deletion-requests');
    }

    private function mediaFields(array $row): string
    {
        $current=!empty($row['media_path'])?'<div class="mb-3"><label class="form-label">Current media</label><div><img class="media-preview" src="'.e($row['thumbnail_path']?:$row['media_path']).'" alt="Current media"></div><a href="'.e($row['media_path']).'" target="_blank" rel="noopener">Open original</a><div class="form-check mt-2"><input class="form-check-input" type="checkbox" name="delete_media" id="delete-media"><label class="form-check-label" for="delete-media">Delete current media</label></div></div>':'';
        return $current.'<div class="mb-3"><label class="form-label">Media</label><input class="form-control media-input" type="file" name="media" accept="image/jpeg,image/png,image/webp,image/gif,video/mp4"><div class="form-text">JPG/PNG/WebP up to '.round($this->config['uploads']['image_max_bytes']/1048576).' MB; GIF up to '.round($this->config['uploads']['gif_max_bytes']/1048576).' MB; H.264 MP4 up to '.round($this->config['uploads']['video_max_bytes']/1048576).' MB.</div><img class="media-preview selected-media-preview d-none mt-2" alt="Selected media preview"><video class="video-preview d-none mt-2" controls></video></div><div class="mb-3"><label class="form-label">Manual video thumbnail (optional)</label><input class="form-control thumbnail-input" type="file" name="thumbnail" accept="image/jpeg,image/png,image/webp,image/gif"><img class="media-preview thumbnail-preview d-none mt-2" alt="Selected thumbnail preview"></div>';
    }

    private function field(string $name,array $field,mixed $value): string
    {
        [$label,$type,$required]=$field;$id='field-'.$name;if($type==='checkbox')return '<div class="form-check mb-3"><input class="form-check-input" type="checkbox" name="'.$name.'" id="'.$id.'" value="1" '.(bool_value($value)?'checked':'').'><label class="form-check-label" for="'.$id.'">'.e($label).'</label></div>';
        if($type==='textarea')return '<div class="mb-3"><label class="form-label" for="'.$id.'">'.e($label).'</label><textarea class="form-control" rows="5" name="'.$name.'" id="'.$id.'" '.($required?'required':'').'>'.e((string)$value).'</textarea></div>';
        if($type==='select'){$html='<div class="mb-3"><label class="form-label" for="'.$id.'">'.e($label).'</label><select class="form-select" name="'.$name.'" id="'.$id.'">';foreach($field[3]as$option)$html.='<option '.((string)$value===$option?'selected':'').'>'.e($option).'</option>';return $html.'</select></div>';}
        return '<div class="mb-3"><label class="form-label" for="'.$id.'">'.e($label).'</label><input class="form-control" type="'.$type.'" name="'.$name.'" id="'.$id.'" value="'.e((string)$value).'" '.($type==='number'?'min="0" ':'').($required?'required':'').'></div>';
    }

    private function render(string $title, string $content, bool $navigation = true): never
    {
        $desktop = '';
        $mobile = '';
        $mainClass = $navigation ? 'admin-main container-fluid py-4' : 'container-fluid py-4';
        if ($navigation) {
            $links = [
                'Dashboard' => '/admin',
                'Nuskhe' => '/admin/nuskhe',
                'Routines' => '/admin/routines',
                'Tips' => '/admin/tips',
                'Plans' => '/admin/plans',
                'Users' => '/admin/users',
                'Admins' => '/admin/admins',
                'Requests' => '/admin/deletion-requests',
            ];
            $desktopLinks = '';
            $mobileLinks = '';
            foreach ($links as $label => $url) {
                $desktopLinks .= '<a class="admin-nav-link" href="' . e($url) . '">' . e($label) . '</a>';
                $mobileLinks .= '<a class="mobile-nav-link" href="' . e($url) . '">' . e($label) . '</a>';
            }
            $desktop = '<aside class="admin-sidebar bg-dark text-light"><a class="admin-brand" href="/admin">JawlineFit</a><nav class="d-grid gap-1" aria-label="Admin navigation">' . $desktopLinks . '</nav><div class="mt-auto"><a class="admin-nav-link" href="/admin/change-password">Password</a><form method="post" action="/admin/logout"><input type="hidden" name="_csrf" value="' . e(csrf_token()) . '"><button class="btn btn-outline-light w-100">Logout</button></form></div></aside>';
            $mobile = '<nav class="mobile-bottom-nav" aria-label="Mobile admin navigation">' . $mobileLinks . '</nav>';
        }
        echo '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>' . e($title) . ' · JawlineFit</title><link href="/public/assets/bootstrap.min.css" rel="stylesheet"><link href="/public/assets/admin.css" rel="stylesheet"></head><body>' . $desktop . '<main class="' . $mainClass . '">' . $content . '</main>' . $mobile . '<script src="/public/assets/bootstrap.bundle.min.js"></script><script src="/public/assets/admin.js"></script></body></html>';
        exit;
    }

    private function validateEntityValues(string $entity, array $values): void
    {
        foreach ($values as $value) {
            if (is_string($value) && contains_forbidden_claim($value)) {
                throw new HttpException(422, 'claims_policy', 'Content contains a prohibited claims-policy term');
            }
        }
        $limits = [
            'nuskhe' => ['title' => 180, 'concern' => 100, 'skin_type' => 100, 'frequency' => 180],
            'routines' => ['title' => 180],
            'tips' => ['category' => 100],
            'plans' => ['product_id' => 191, 'base_plan_id' => 191, 'name' => 120],
        ];
        foreach ($limits[$entity] ?? [] as $field => $maximum) {
            if (mb_strlen((string) ($values[$field] ?? '')) > $maximum) {
                throw new HttpException(422, 'validation_failed', ucfirst(str_replace('_', ' ', $field)) . ' is too long');
            }
        }
        if ($entity === 'plans' && (!preg_match('/^[a-zA-Z0-9._-]+$/', (string) ($values['product_id'] ?? '')) || !preg_match('/^[a-zA-Z0-9._-]+$/', (string) ($values['base_plan_id'] ?? '')))) {
            throw new HttpException(422, 'validation_failed', 'Google product or base plan ID contains invalid characters');
        }
        if ($entity === 'plans' && (int) ($values['duration_days'] ?? 0) < 1) {
            throw new HttpException(422, 'validation_failed', 'Duration days must be at least one');
        }
        foreach (['duration_min', 'duration_days', 'sort_order'] as $field) {
            if (isset($values[$field]) && (int) $values[$field] > (in_array($field, ['duration_min', 'duration_days'], true) ? 65535 : 2147483647)) {
                throw new HttpException(422, 'validation_failed', ucfirst(str_replace('_', ' ', $field)) . ' is too large');
            }
        }
    }

    private function find(string $table,int $id): array {$allowed=['nuskhe','routines','tips','plans','users','admin_users'];if(!in_array($table,$allowed,true))throw new \LogicException('Invalid table');$stmt=$this->db->prepare('SELECT * FROM '.$table.' WHERE id=?');$stmt->execute([$id]);$row=$stmt->fetch();if(!$row)throw new HttpException(404,'not_found','Record not found');return $row;}
    private function findStep(int $routineId,int $id): array {$stmt=$this->db->prepare('SELECT * FROM routine_steps WHERE id=? AND routine_id=?');$stmt->execute([$id,$routineId]);$row=$stmt->fetch();if(!$row)throw new HttpException(404,'not_found','Step not found');return $row;}
    private function adminPagination(): array
    {
        $raw = $_GET['page'] ?? '1';
        $page = filter_var($raw, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1, 'max_range' => 1000000]]);
        if ($page === false) {
            throw new HttpException(422, 'validation_failed', 'page must be a positive integer');
        }
        $perPage = 25;
        return [(int) $page, $perPage, ((int) $page - 1) * $perPage];
    }
    private function searchForm(string $q): string {return '<form class="row g-2 mb-3" method="get"><div class="col"><input class="form-control" type="search" name="q" value="'.e($q).'" placeholder="Search"></div><div class="col-auto"><button class="btn btn-outline-primary">Search</button></div></form>';}
    private function pagination(int $page,int $perPage,int $total,string $base): string {$pages=(int)ceil($total/$perPage);if($pages<=1)return'';$html='<nav aria-label="Pagination"><ul class="pagination flex-wrap">';for($i=max(1,$page-3);$i<=min($pages,$page+3);$i++)$html.='<li class="page-item '.($i===$page?'active':'').'"><a class="page-link" href="'.$base.'&page='.$i.'">'.$i.'</a></li>';return$html.'</ul></nav>';}
    private function flash(string $type,string $message): void {$_SESSION['flashes'][]=['type'=>$type,'message'=>$message];}
    private function flashes(): string {$html='';foreach($_SESSION['flashes']??[]as$f)$html.='<div class="alert alert-'.e($f['type']).'">'.e($f['message']).'</div>';unset($_SESSION['flashes']);return$html;}
    private function clearSession(): void {$_SESSION=[];if(ini_get('session.use_cookies')){$p=session_get_cookie_params();setcookie(session_name(),'',time()-42000,$p['path'],$p['domain'],$p['secure'],$p['httponly']);}if(session_status()===PHP_SESSION_ACTIVE)session_destroy();}
}
