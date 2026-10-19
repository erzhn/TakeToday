// TakeToday — главная, вход, регистрация, код из письма и восстановление пароля (экраны 1 и 12 в Figma).
// Работает через API /api/auth/** (AuthController). Адреса страниц: #home, #login, #register, #forgot, #code.
// Личный кабинет, кабинет заведения и админ-панель — отдельные страницы Thymeleaf (/profile, /staff, /admin).

const app = document.querySelector('#app');
const nav = document.querySelector('#nav');

const ROLES = { GUEST: 'Гость', CUSTOMER: 'Покупатель', STAFF: 'Сотрудник заведения', ADMIN: 'Администратор' };
const AUTH_PAGES = ['login', 'register', 'forgot', 'code'];

let user = { role: 'GUEST' };
let pending = readChallenge();

// ---------- Вспомогательное ----------

const escape = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const icon = (name, extra = '') => '<span class="material-symbols-outlined ' + extra + '" aria-hidden="true">' + name + '</span>';

function readChallenge() {
    try { return JSON.parse(sessionStorage.getItem('challenge') || 'null'); } catch { return null; }
}

/** Запрос к API. GET без body, POST — с JSON и CSRF-токеном. Ошибки превращаются в понятный текст. */
async function api(url, body) {
    const options = { credentials: 'include', headers: {} };
    if (body !== undefined) {
        const csrf = await fetch('/api/auth/csrf', { credentials: 'include' }).catch(() => null);
        if (!csrf || !csrf.ok) throw new Error('Не удалось подключиться к серверу. Проверьте интернет и попробуйте ещё раз.');
        const token = await csrf.json();
        options.method = 'POST';
        options.headers = { 'Content-Type': 'application/json', [token.headerName]: token.token };
        options.body = JSON.stringify(body);
    }
    let response;
    try {
        response = await fetch('/api' + url, options);
    } catch {
        throw new Error('Нет соединения с сервером. Проверьте подключение.');
    }
    if (response.status === 204) return null;
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw new Error(data.message || {
            401: 'Войдите в учётную запись.',
            403: 'Нет доступа. Обновите страницу и попробуйте снова.',
            429: 'Слишком много попыток. Попробуйте позже.'
        }[response.status] || 'Не удалось выполнить запрос. Попробуйте позже.');
    }
    return data;
}

/** Сообщение над формой: ошибка (по умолчанию) или успех. Пустой текст — скрыть. */
function notice(message, success = false) {
    const el = document.querySelector('#notice');
    if (!el) return;
    el.hidden = !message;
    el.className = 'auth-notice ' + (success ? 'is-success' : 'is-error');
    el.innerHTML = message ? icon(success ? 'check_circle' : 'error') + '<span>' + escape(message) + '</span>' : '';
}

/** Перейти на другую «страницу» без лишнего события hashchange (иначе сообщение тут же стирается). */
function go(page) {
    history.replaceState(null, '', '#' + page);
    render();
}

function saveChallenge(data, purpose) {
    pending = { ...data, purpose, receivedAt: Date.now() };
    sessionStorage.setItem('challenge', JSON.stringify(pending));
    go('code');
}

function clearChallenge() {
    pending = null;
    sessionStorage.removeItem('challenge');
}

// ---------- Шапка ----------

function navigation(page) {
    const authPage = AUTH_PAGES.includes(page);
    document.body.classList.toggle('is-auth', authPage);

    if (authPage) {
        nav.innerHTML =
            '<span class="pill hide-mobile">' + icon('location_on', 'icon-sm') + 'Бишкек, KG</span>' +
            '<a class="link-button hide-mobile" href="mailto:support@taketoday.kg">' + icon('help', 'icon-sm') + 'Поддержка</a>' +
            '<a class="btn btn-sm btn-back" href="#home">' + icon('arrow_back', 'icon-sm') + 'На главную</a>';
        return;
    }

    if (user.role === 'GUEST') {
        nav.innerHTML =
            '<a class="nav-link hide-mobile" href="#how">Как это работает</a>' +
            '<a class="nav-link" href="#login">Войти</a>' +
            '<a class="btn btn-primary btn-sm" href="#register">Регистрация</a>';
        return;
    }

    const section = user.role === 'STAFF' ? '<a class="nav-link hide-mobile" href="/staff">Кабинет заведения</a>'
        : user.role === 'ADMIN' ? '<a class="nav-link hide-mobile" href="/admin">Админ-панель</a>'
        : '<a class="nav-link hide-mobile" href="/orders">Мои заказы</a>';
    nav.innerHTML = '<a class="nav-link hide-mobile" href="#how">Как это работает</a>' + section +
        '<a class="user-chip" href="/profile" title="Профиль">' +
        '<span class="avatar">' + icon('person', 'icon-sm') + '</span>' +
        '<span class="hide-mobile"><span class="user-chip-name">' + escape(shortName(user.name)) + '</span><br>' +
        '<span class="user-chip-role">' + escape(ROLES[user.role]) + '</span></span></a>' +
        '<button type="button" class="link-button" id="logout">' + icon('logout', 'icon-sm') + '<span class="hide-mobile">Выйти</span></button>';
    document.querySelector('#logout').addEventListener('click', async () => {
        try {
            await api('/auth/logout', {});
            user = { role: 'GUEST' };
            clearChallenge();
            go('home');
        } catch (e) {
            notice(e.message);
        }
    });
}

/** «Айбек Джумабаев» → «Айбек Д.» */
function shortName(name) {
    const parts = String(name || '').trim().split(/\s+/);
    return parts.length > 1 ? parts[0] + ' ' + parts[1].charAt(0) + '.' : parts[0] || '';
}

// ---------- Главная ----------

async function home() {
    const guest = user.role === 'GUEST';
    app.innerHTML =
        '<div class="container page">' +
        '<div id="notice" class="auth-notice" hidden role="alert"></div>' +
        '<div id="account"></div>' +
        '<section class="hero-home">' +
        '<div class="hero-copy">' +
        '<span class="badge badge-green">' + icon('eco', 'icon-sm') + 'Еда со скидкой · Бишкек</span>' +
        '<h1>Хорошая еда заслуживает <em>второго шанса</em></h1>' +
        '<p>Кафе и пекарни Бишкека продают оставшуюся к концу дня еду со скидкой. Оплатите набор онлайн, получите код и заберите его в заведении в указанное время.</p>' +
        '<div class="hero-actions">' +
        (guest
            ? '<a class="btn btn-primary" href="#register">Создать аккаунт' + icon('arrow_forward') + '</a><a class="btn btn-outline" href="#login">У меня есть аккаунт</a>'
            : '<a class="btn btn-primary" href="/profile">' + icon('person') + 'Личный кабинет</a>') +
        '</div>' +
        '<p class="hint">' + icon('info', 'icon-sm') + (guest ? 'Смотреть предложения можно без регистрации. Чтобы бронировать и оплачивать, войдите.' : 'Каталог предложений появится на следующем этапе.') + '</p>' +
        '</div>' +
        '<div class="hero-art" aria-hidden="true">' +
        '<div class="hero-card"><span class="hero-card-tag">−50%</span>' + icon('bakery_dining') +
        '<strong>Вечерний бокс выпечки</strong><span>Пекарня · сегодня 19:00–21:00</span>' +
        '<span class="hero-card-price"><b>250 сом</b><s>500 сом</s></span></div>' +
        '</div>' +
        '</section>' +
        '<section class="how" id="how" aria-label="Как работает TakeToday">' +
        step('search', '01', 'Выберите набор', 'Заведения выставляют оставшуюся еду: состав, цена со скидкой и время получения.') +
        step('payments', '02', 'Оплатите онлайн', 'Набор закрепляется за вами, как только оплата пройдёт. Бронь держится 3 минуты.') +
        step('qr_code', '03', 'Заберите по коду', 'Приходите в указанное время и покажите сотруднику 6-значный код или QR-код.') +
        '</section></div>';

    if (!guest) {
        document.querySelector('#account').innerHTML =
            '<section class="card card-md account-card"><div class="identity">' +
            '<span class="avatar avatar-lg">' + icon('person') + '</span><div>' +
            '<div class="identity-name">Вы вошли, ' + escape(user.name) + '</div>' +
            '<div class="identity-meta">' + escape(user.email) + ' · ' + escape(ROLES[user.role]) + '</div>' +
            '<div id="role-info" class="hint">Загружаем ваш раздел…</div></div></div>' +
            '<div class="account-actions">' + sectionButton() + '</div></section>';
        try {
            const data = await api('/' + user.role.toLowerCase() + '/home');
            const target = document.querySelector('#role-info');
            if (target) {
                target.textContent = user.role === 'STAFF'
                    ? data.name + ' · ' + data.address + ' · ' + data.contact
                    : data.message;
            }
        } catch (e) {
            notice(e.message);
        }
    }
    scrollToHashTarget();
}

function sectionButton() {
    if (user.role === 'STAFF') return '<a class="btn btn-primary btn-sm" href="/staff">' + icon('storefront', 'icon-sm') + 'Кабинет заведения</a>';
    if (user.role === 'ADMIN') return '<a class="btn btn-primary btn-sm" href="/admin">' + icon('dashboard', 'icon-sm') + 'Админ-панель</a>';
    return '<a class="btn btn-soft btn-sm" href="/profile">' + icon('person', 'icon-sm') + 'Профиль</a>';
}

function step(name, number, title, text) {
    return '<article class="card card-md how-step"><span class="how-icon">' + icon(name) + '</span>' +
        '<span class="how-number">' + number + '</span><h3>' + title + '</h3><p>' + text + '</p></article>';
}

/** Ссылка «Как это работает» (#how) — просто прокрутка к блоку на главной. */
function scrollToHashTarget() {
    if (location.hash === '#how') document.querySelector('#how')?.scrollIntoView();
}

// ---------- Страницы входа (экран 12) ----------

function authLayout(page, title, subtitle, content) {
    const tab = (id, iconName, label) => '<a href="#' + id + '" class="auth-tab' + (page === id ? ' active' : '') + '">' + icon(iconName, 'icon-sm') + label + '</a>';
    app.innerHTML =
        '<div class="auth-bg" aria-hidden="true"><span class="blob blob-1"></span><span class="blob blob-2"></span></div>' +
        '<section class="auth-wrap">' +
        '<nav class="auth-tabs" aria-label="Вход и регистрация">' +
        tab('login', 'login', 'Вход') + tab('register', 'person', 'Регистрация') + tab('forgot', 'lock_reset', 'Сброс') +
        '</nav>' +
        '<div class="auth-card">' +
        '<div class="auth-brand"><span class="brand-mark">' + icon('eco', 'icon-fill') + '</span>' +
        '<div><span class="logo logo-sm">Take<span>Today</span></span><span class="brand-sub">Спасаем еду в Бишкеке</span></div>' +
        '<span class="badge badge-soft-dark">' + icon('location_on', 'icon-xs') + 'Бишкек</span></div>' +
        '<h1 class="auth-title">' + title + '</h1>' +
        '<p class="auth-subtitle">' + subtitle + '</p>' +
        '<div id="notice" class="auth-notice" hidden role="alert"></div>' +
        content +
        '<div class="auth-foot"><a href="#home">' + icon('arrow_back', 'icon-sm') + 'Продолжить как гость</a></div>' +
        '</div>' +
        '<p class="auth-copy">© 2026 TakeToday Кыргызстан. Бишкек.</p>' +
        '</section>';
    bindPasswordToggles();
}

function bindForm(handler) {
    const form = app.querySelector('form');
    form.addEventListener('submit', async e => {
        e.preventDefault();
        const button = form.querySelector('[type=submit]');
        button.disabled = true;
        notice('');
        try {
            await handler(Object.fromEntries(new FormData(form)));
        } catch (err) {
            notice(err.message);
        } finally {
            if (button.isConnected) button.disabled = false;
        }
    });
}

const field = ({ name, label, type = 'text', iconName, autocomplete = 'off', placeholder = '', maxlength, minlength, required = true, extraLabel = '', hint = '', attrs = '' }) =>
    '<div class="field">' +
    '<label class="field-label" for="f-' + name + '"><span>' + label + '</span>' + extraLabel + '</label>' +
    '<div class="input-wrap">' + icon(iconName, 'input-icon') +
    '<input class="input has-icon' + (type === 'password' ? ' has-action' : '') + '" id="f-' + name + '" name="' + name + '" type="' + type + '"' +
    ' autocomplete="' + autocomplete + '" placeholder="' + placeholder + '"' +
    (maxlength ? ' maxlength="' + maxlength + '"' : '') + (minlength ? ' minlength="' + minlength + '"' : '') +
    (required ? ' required' : '') + ' ' + attrs + '>' +
    (type === 'password' ? '<button type="button" class="input-action" data-toggle-password aria-label="Показать пароль">' + icon('visibility') + '</button>' : '') +
    '</div>' + (hint ? '<p class="hint">' + hint + '</p>' : '') + '</div>';

const emailField = () => field({ name: 'email', label: 'Электронная почта', type: 'email', iconName: 'mail', autocomplete: 'email', placeholder: 'aibek@example.kg', maxlength: 254 });

const passwordField = (label, autocomplete, extraLabel = '', withStrength = false) =>
    field({ name: 'password', label, type: 'password', iconName: 'lock', autocomplete, minlength: 10, maxlength: 72, extraLabel }) +
    (withStrength
        ? '<div class="strength" data-level="0" id="strength"><div class="strength-bars"><span></span><span></span><span></span><span></span></div><span class="strength-label"></span></div>' +
          '<p class="hint auth-hint">От 10 символов, не более 72 байт. Лучше сочетать буквы, цифры и знаки.</p>'
        : '');

const submitButton = (label, iconName = 'arrow_forward') =>
    '<button type="submit" class="btn btn-primary btn-block auth-submit">' + label + icon(iconName) + '</button>';

function bindPasswordToggles() {
    app.querySelectorAll('[data-toggle-password]').forEach(button => {
        button.addEventListener('click', () => {
            const input = button.parentElement.querySelector('input');
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            button.querySelector('.material-symbols-outlined').textContent = show ? 'visibility_off' : 'visibility';
            button.setAttribute('aria-label', show ? 'Скрыть пароль' : 'Показать пароль');
        });
    });
}

function bindStrength() {
    const input = app.querySelector('input[name=password]');
    const meter = app.querySelector('#strength');
    if (!input || !meter) return;
    const names = ['', 'Слабый', 'Средний', 'Хороший', 'Надёжный'];
    input.addEventListener('input', () => {
        const v = input.value;
        let level = 0;
        if (v.length >= 10) level++;
        if (/[A-Za-zА-Яа-яЁё]/.test(v) && /\d/.test(v)) level++;
        if (/[a-zа-яё]/.test(v) && /[A-ZА-ЯЁ]/.test(v)) level++;
        if (/[^A-Za-zА-Яа-яЁё0-9]/.test(v) || v.length >= 14) level++;
        if (!v) level = 0;
        meter.dataset.level = String(level);
        meter.querySelector('.strength-label').textContent = names[level];
    });
}

function login() {
    authLayout('login', 'Вход в аккаунт',
        'Войдите по почте и паролю — затем подтвердите вход кодом из письма.',
        '<form class="form-grid" novalidate>' + emailField() +
        passwordField('Пароль', 'current-password', '<a class="field-meta auth-link" href="#forgot">Забыли пароль?</a>') +
        submitButton('Получить код входа') + '</form>' +
        '<p class="auth-switch">Нет аккаунта? <a href="#register">Зарегистрироваться</a></p>');
    bindForm(async data => saveChallenge(await api('/auth/login', data), 'LOGIN'));
}

function register() {
    authLayout('register', 'Создать аккаунт',
        'Укажите данные и подтвердите почту кодом из письма.',
        '<form class="form-grid" novalidate>' +
        '<fieldset class="role-choice"><legend class="field-label">Я хочу</legend>' +
        '<label class="role-option"><input type="radio" name="role" value="CUSTOMER" checked>' +
        '<span class="role-card">' + icon('shopping_bag') + '<b>Покупать наборы</b><small>Бронирую и забираю еду со скидкой</small></span></label>' +
        '<label class="role-option"><input type="radio" name="role" value="STAFF">' +
        '<span class="role-card">' + icon('storefront') + '<b>Представлять заведение</b><small>Выставляю остатки еды на продажу</small></span></label>' +
        '</fieldset>' +
        field({ name: 'name', label: 'Как вас зовут', iconName: 'badge', autocomplete: 'name', placeholder: 'Айбек Джумабаев', maxlength: 100 }) +
        emailField() +
        passwordField('Придумайте пароль', 'new-password', '', true) +
        '<div class="staff-fields" hidden>' +
        '<p class="staff-title">' + icon('storefront', 'icon-sm') + 'Заведение</p>' +
        field({ name: 'establishmentName', label: 'Название заведения', iconName: 'storefront', placeholder: 'Пекарня «Тандыр»', maxlength: 150, required: false }) +
        field({ name: 'address', label: 'Адрес', iconName: 'location_on', placeholder: 'г. Бишкек, ул. Киевская, 95', maxlength: 300, required: false }) +
        field({ name: 'contact', label: 'Телефон или почта заведения', iconName: 'call', placeholder: '+996 555 341 289', maxlength: 150, required: false }) +
        field({ name: 'workingHours', label: 'Часы работы', iconName: 'schedule', placeholder: 'Пн–Сб, 09:00–21:00', maxlength: 150, required: false,
            hint: 'Подробный график по дням можно будет указать в кабинете заведения.' }) +
        '</div>' +
        submitButton('Зарегистрироваться') + '</form>' +
        '<p class="auth-switch">Уже есть аккаунт? <a href="#login">Войти</a></p>' +
        '<p class="hint auth-hint">' + icon('shield', 'icon-sm') + 'Администратора назначает владелец сервиса — через сайт его не создать.</p>');

    const staffFields = app.querySelector('.staff-fields');
    app.querySelectorAll('input[name=role]').forEach(radio => radio.addEventListener('change', () => {
        const staff = app.querySelector('input[name=role]:checked').value === 'STAFF';
        staffFields.hidden = !staff;
        staffFields.querySelectorAll('input').forEach(i => { i.required = staff; });
    }));
    bindStrength();
    bindForm(async data => {
        if (data.role !== 'STAFF') {
            delete data.establishmentName; delete data.address; delete data.contact; delete data.workingHours;
        }
        saveChallenge(await api('/auth/register', data), 'REGISTER');
    });
}

function forgot() {
    authLayout('forgot', 'Восстановить доступ',
        'Укажите почту — если такая учётная запись есть, мы отправим код для смены пароля.',
        '<form class="form-grid" novalidate>' + emailField() + submitButton('Отправить код') + '</form>' +
        '<p class="auth-switch">Вспомнили пароль? <a href="#login">Войти</a></p>');
    bindForm(async data => saveChallenge(await api('/auth/forgot-password', data), 'RESET'));
}

function code() {
    if (!pending) { go('login'); return; }
    const reset = pending.purpose === 'RESET';
    authLayout(reset ? 'forgot' : (pending.purpose === 'REGISTER' ? 'register' : 'login'),
        reset ? 'Новый пароль' : 'Проверьте почту',
        'Введите 6-значный код из письма. Он действует 10 минут. Если письма нет — проверьте папку «Спам».',
        '<form class="form-grid" novalidate>' +
        '<div class="field"><label class="field-label" for="f-code"><span>Код из письма</span></label>' +
        '<input class="input code-input" id="f-code" name="code" type="text" inputmode="numeric" autocomplete="one-time-code"' +
        ' pattern="[0-9]{6}" minlength="6" maxlength="6" required placeholder="000000" autofocus></div>' +
        (reset ? passwordField('Новый пароль', 'new-password', '', true) : '') +
        submitButton(reset ? 'Изменить пароль' : 'Подтвердить и войти', reset ? 'lock_reset' : 'arrow_forward') + '</form>' +
        '<div class="code-actions">' +
        '<p id="code-time" class="hint">' + icon('timer', 'icon-sm') + '<span data-time></span></p>' +
        '<button id="resend" class="link-button" type="button">' + icon('refresh', 'icon-sm') + 'Отправить код ещё раз</button>' +
        '<a href="#' + (reset ? 'forgot' : 'login') + '" class="auth-link">Начать заново</a>' +
        '</div>');
    bindStrength();
    bindForm(async data => {
        if (reset) {
            await api('/auth/reset-password', { ...data, challengeId: pending.challengeId });
            clearChallenge();
            go('login');
            notice('Пароль изменён. Войдите с новым паролем.', true);
        } else {
            user = await api('/auth/verify', { ...data, challengeId: pending.challengeId });
            clearChallenge();
            // Сразу в свой раздел (п. 4.1.2 ТЗ)
            if (user.role === 'STAFF') { location.href = '/staff'; return; }
            if (user.role === 'ADMIN') { location.href = '/admin'; return; }
            go('home');
        }
    });
    const resend = document.querySelector('#resend');
    resend.addEventListener('click', async () => {
        resend.disabled = true;
        try {
            saveChallenge(await api('/auth/resend', { challengeId: pending.challengeId }), pending.purpose);
            notice('Новый код отправлен. Предыдущий код больше не действует.', true);
        } catch (e) {
            notice(e.message);
            resend.disabled = false;
        }
    });
    updateCountdown();
}

function updateCountdown() {
    const el = document.querySelector('#code-time [data-time]');
    const button = document.querySelector('#resend');
    if (!el || !pending) return;
    const elapsed = Math.floor((Date.now() - pending.receivedAt) / 1000);
    const left = Math.max(0, pending.expiresIn - elapsed);
    const wait = Math.max(0, pending.resendAfter - elapsed);
    el.textContent = left
        ? 'Код действует ещё ' + Math.floor(left / 60) + ':' + String(left % 60).padStart(2, '0') + (wait ? ' · новый можно запросить через ' + wait + ' с' : '')
        : 'Код истёк. Запросите новый или начните заново.';
    button.disabled = wait > 0 || left === 0;
}

// ---------- Переходы между страницами ----------

function render() {
    let page = location.hash.slice(1) || 'home';
    if (page === 'how') page = 'home';
    if (user.role !== 'GUEST' && AUTH_PAGES.includes(page)) { go('home'); return; }
    navigation(page);
    ({ home, login, register, forgot, code }[page] || home)();
    window.scrollTo(0, 0);
    scrollToHashTarget();
}

window.addEventListener('hashchange', render);
setInterval(updateCountdown, 1000);

try {
    user = await api('/auth/me');
    render();
} catch (e) {
    render();
    notice(e.message);
}
