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
});
