// TakeToday — небольшие улучшения интерфейса. Сайт работает и без JavaScript.
document.addEventListener('DOMContentLoaded', function () {

    // Меню на телефоне
    document.querySelectorAll('[data-menu-toggle]').forEach(function (button) {
        button.addEventListener('click', function () {
            var header = button.closest('.site-header');
            var open = header.classList.toggle('open');
            button.setAttribute('aria-expanded', open ? 'true' : 'false');
        });
    });

    // Выход. Сервер (/api/auth/logout) отвечает 204 без перехода, поэтому после выхода открываем главную.
    document.querySelectorAll('form[data-logout]').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            event.preventDefault();
            fetch(form.action, { method: 'POST', body: new FormData(form), credentials: 'same-origin' })
                .finally(function () { window.location.href = '/'; });
        });
    });

    // Закрыть уведомление
    document.querySelectorAll('[data-dismiss]').forEach(function (button) {
        button.addEventListener('click', function () {
            var target = button.closest('.toast');
            if (target) target.remove();
        });
    });

    // Показать / скрыть пароль
    document.querySelectorAll('[data-toggle-password]').forEach(function (button) {
        button.addEventListener('click', function () {
            var input = button.parentElement.querySelector('input');
            var show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            button.querySelector('.material-symbols-outlined').textContent = show ? 'visibility_off' : 'visibility';
            button.setAttribute('aria-label', show ? 'Скрыть пароль' : 'Показать пароль');
        });
    });

    // Надёжность пароля: длина ≥ 10, буквы, цифры, разный регистр или спецсимвол
    document.querySelectorAll('[data-strength-for]').forEach(function (meter) {
        var input = document.getElementById(meter.getAttribute('data-strength-for'));
        var label = meter.querySelector('.strength-label');
        var names = ['', 'Слабый', 'Средний', 'Хороший', 'Надёжный'];
        if (!input) return;
        input.addEventListener('input', function () {
            var v = input.value;
            var level = 0;
            if (v.length >= 10) level++;
            if (/[A-Za-zА-Яа-яЁё]/.test(v) && /\d/.test(v)) level++;
            if (/[a-zа-яё]/.test(v) && /[A-ZА-ЯЁ]/.test(v)) level++;
            if (/[^A-Za-zА-Яа-яЁё0-9]/.test(v) || v.length >= 14) level++;
            if (!v) level = 0;
            meter.setAttribute('data-level', String(level));
            label.textContent = names[level];
        });
    });

    // График работы: у выходного дня поля времени выключаются
    document.querySelectorAll('[data-workday]').forEach(function (row) {
        var toggle = row.querySelector('input[type=checkbox]');
        var label = row.querySelector('.switch-label');
        function sync() {
            row.classList.toggle('off', !toggle.checked);
            row.querySelectorAll('input[type=time]').forEach(function (t) { t.disabled = !toggle.checked; });
            if (label) label.textContent = toggle.checked ? 'Рабочий день' : 'Выходной';
        }
        toggle.addEventListener('change', sync);
        sync();
    });

    // «Применить ко всем будням»: копирует график понедельника на вторник–пятницу
    document.querySelectorAll('[data-copy-weekdays]').forEach(function (button) {
        button.addEventListener('click', function () {
            var monday = document.querySelector('[data-workday][data-day="1"]');
            if (!monday) return;
            var times = monday.querySelectorAll('input[type=time]');
            var working = monday.querySelector('input[type=checkbox]').checked;
            for (var day = 2; day <= 5; day++) {
                var row = document.querySelector('[data-workday][data-day="' + day + '"]');
                if (!row) continue;
                var toggle = row.querySelector('input[type=checkbox]');
                toggle.checked = working;
                var rowTimes = row.querySelectorAll('input[type=time]');
                rowTimes[0].value = times[0].value;
                rowTimes[1].value = times[1].value;
                toggle.dispatchEvent(new Event('change'));
            }
        });
    });

    // Счётчик символов в описании
    document.querySelectorAll('[data-counter-for]').forEach(function (counter) {
        var field = document.getElementById(counter.getAttribute('data-counter-for'));
        var value = counter.querySelector('span');
        if (!field || !value) return;
        function update() { value.textContent = String(field.value.length); }
        field.addEventListener('input', update);
        update();
    });

    // Перед отправкой формы включаем поля времени обратно, иначе браузер их не отправит
    document.querySelectorAll('form').forEach(function (form) {
        form.addEventListener('submit', function () {
            form.querySelectorAll('[data-workday] input[type=time]').forEach(function (t) { t.disabled = false; });
            form.querySelectorAll('[data-once]').forEach(function (b) { b.disabled = true; });
        });
    });

    // Предпросмотр нового логотипа
    document.querySelectorAll('[data-logo-input]').forEach(function (input) {
        input.addEventListener('change', function () {
            var file = input.files && input.files[0];
            var preview = document.querySelector('[data-logo-preview]');
            if (!file || !preview) return;
            var url = URL.createObjectURL(file);
            preview.innerHTML = '';
            var img = document.createElement('img');
            img.src = url;
            img.alt = 'Новый логотип';
            preview.appendChild(img);
        });
    });

    // ---------- Этап 2: таблица предложений — фильтр «Все / Активно / Снято / Время вышло» и поиск ----------
    document.querySelectorAll('[data-offers]').forEach(function (section) {
        var rows = section.querySelectorAll('tbody tr[data-group]');
        var nothing = section.querySelector('.offers-nothing');
        var search = section.querySelector('[data-offers-search]');
        var current = 'all';
        function apply() {
            var q = (search && search.value || '').trim().toLowerCase();
            var shown = 0;
            rows.forEach(function (row) {
                var ok = (current === 'all' || row.getAttribute('data-group') === current)
                    && (!q || row.getAttribute('data-title').indexOf(q) !== -1);
                row.hidden = !ok;
                if (ok) shown++;
            });
            if (nothing) nothing.hidden = shown > 0 || rows.length === 0;
        }
        section.querySelectorAll('[data-offers-filter]').forEach(function (button) {
            button.addEventListener('click', function () {
                section.querySelectorAll('[data-offers-filter]').forEach(function (b) { b.classList.remove('active'); });
                button.classList.add('active');
                current = button.getAttribute('data-offers-filter');
                apply();
            });
        });
        if (search) search.addEventListener('input', apply);
    });

    // ---------- Этап 2: форма предложения ----------
    var offerForm = document.getElementById('offer-form');
    if (offerForm) {
        var DAY = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long' });
        function val(name) {
            var el = offerForm.querySelector('[data-preview="' + name + '"]');
            if (!el) return '';
            if (el.tagName === 'SELECT') return el.value ? el.options[el.selectedIndex].text : '';
            return el.value.trim();
        }
        function out(name, text) {
            document.querySelectorAll('[data-preview-out="' + name + '"]').forEach(function (el) { el.textContent = text; });
        }
        function dayLabel(iso) {
            if (!iso) return '';
            var d = new Date(iso + 'T00:00:00');
            var today = new Date(); today.setHours(0, 0, 0, 0);
            var diff = Math.round((d - today) / 86400000);
            return diff === 0 ? 'Сегодня' : diff === 1 ? 'Завтра' : DAY.format(d);
        }
        function updatePreview() {
            out('title', val('title') || 'Название набора');
            out('foodType', val('foodType') || 'Вид еды');
            out('composition', val('composition') || 'Состав набора');
            out('totalPortions', val('totalPortions') || '0');
            var regular = parseInt(val('regularPrice'), 10);
            var discount = parseInt(val('discountPrice'), 10);
            out('regularPrice', isNaN(regular) ? '0' : String(regular));
            out('discountPrice', isNaN(discount) ? '0' : String(discount));
            var box = document.querySelector('[data-discount-box]');
            var badge = document.querySelector('[data-preview-out="discount"]');
            var valid = !isNaN(regular) && !isNaN(discount) && regular > 0 && discount > 0 && discount < regular;
            if (box) box.hidden = !valid;
            if (badge) badge.hidden = !valid;
            if (valid) {
                var percent = '−' + Math.round(100 * (regular - discount) / regular) + '%';
                document.querySelectorAll('[data-discount-percent]').forEach(function (el) { el.textContent = percent; });
                document.querySelectorAll('[data-discount-save]').forEach(function (el) { el.textContent = (regular - discount) + ' сом'; });
                if (badge) badge.textContent = percent;
            }
            var start = val('pickupStart'), end = val('pickupEnd');
            out('when', (dayLabel(val('pickupDate')) + ' ' + (start || '--:--') + ' – ' + (end || '--:--')).trim());
        }
        offerForm.addEventListener('input', updatePreview);
        offerForm.addEventListener('change', updatePreview);
        updatePreview();

        // Кнопки − / + у количества порций (не меньше уже забронированных и оплаченных)
        var portions = offerForm.querySelector('.stepper-input');
        offerForm.querySelectorAll('[data-step]').forEach(function (button) {
            button.addEventListener('click', function () {
                var min = parseInt(portions.getAttribute('data-min') || '1', 10);
                var next = (parseInt(portions.value, 10) || 0) + parseInt(button.getAttribute('data-step'), 10);
                portions.value = String(Math.max(min, Math.min(100, next)));
                updatePreview();
            });
        });

        // Новое фото: показываем сразу в форме и в карточке
        var photoInput = offerForm.querySelector('[data-photo-input]');
        if (photoInput) {
            photoInput.addEventListener('change', function () {
                var file = photoInput.files && photoInput.files[0];
                if (!file || !/^image\/(png|jpeg)$/.test(file.type)) return;
                var url = URL.createObjectURL(file);
                document.querySelectorAll('[data-photo-preview], [data-preview-photo]').forEach(function (box) {
                    var img = box.querySelector('img');
                    if (!img) {
                        img = document.createElement('img');
                        img.alt = '';
                        var placeholder = box.querySelector(':scope > .material-symbols-outlined');
                        if (placeholder) placeholder.remove();
                        box.insertBefore(img, box.firstChild);
                    }
                    img.src = url;
                });
                var name = offerForm.querySelector('[data-photo-name]');
                if (name) {
                    name.querySelector('.material-symbols-outlined').textContent = 'check_circle';
                    name.lastElementChild.textContent = 'Выбрано: ' + file.name;
                }
            });
        }
    }
});

