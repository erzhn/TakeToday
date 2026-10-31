// Этап 3 (Эржан): копирование кода получения, обратный отсчёт до выдачи, таймер и форма учебной оплаты.
(() => {
    // ---- Код получения: нажатие копирует код
    const code = document.querySelector('[data-copy-code]');
    if (code) {
        const hint = code.querySelector('[data-copy-hint]');
        const initial = hint.innerHTML;
        code.addEventListener('click', async () => {
            try {
                await navigator.clipboard.writeText(code.dataset.code);
                hint.textContent = 'Код скопирован';
                hint.classList.add('is-copied');
                setTimeout(() => { hint.innerHTML = initial; hint.classList.remove('is-copied'); }, 2000);
            } catch (e) { /* буфер недоступен — код всё равно виден на экране */ }
        });
    }

    // ---- Этап 4: сотрудник подтвердил выдачу — страница сама покажет «Заказ выдан»
    const waitIssue = document.querySelector('[data-wait-issue]');
    if (waitIssue) {
        const poll = setInterval(async () => {
            if (document.hidden) return;
            try {
                const response = await fetch('/api/orders/reservations/' + waitIssue.dataset.orderId,
                    {credentials: 'same-origin', cache: 'no-store'});
                if (!response.ok) return;
                const data = await response.json();
                if (data.order && data.order.status !== 'PAID') { clearInterval(poll); location.reload(); }
            } catch (e) { /* нет сети — проверим в следующий раз */ }
        }, 5000);
    }

    // ---- Этап 5: защита от двойного нажатия «Отменить» (сервер всё равно не отменит и не вернёт дважды)
    document.querySelectorAll('[data-cancel-form]').forEach(f => f.addEventListener('submit', () => {
        setTimeout(() => f.querySelectorAll('button').forEach(b => { b.disabled = true; }), 0);
    }));

    // ---- «Осталось 1 ч 45 мин до начала выдачи»
    const countdown = document.querySelector('[data-pickup-countdown]');
    if (countdown) {
        const text = countdown.querySelector('[data-pickup-countdown-text]');
        const fallback = text.textContent;
        const start = Date.parse(countdown.dataset.start);
        const end = Date.parse(countdown.dataset.end);
        const span = ms => {
            const minutes = Math.max(1, Math.round(ms / 60000));
            const h = Math.floor(minutes / 60), m = minutes % 60;
            return (h ? h + ' ч ' : '') + (m || !h ? m + ' мин' : '').trim();
        };
        const render = () => {
            const now = Date.now();
            if (Number.isNaN(start) || Number.isNaN(end)) text.textContent = fallback;
            else if (now < start) text.textContent = 'Осталось ' + span(start - now) + ' до начала выдачи';
            else if (now < end) text.textContent = 'Выдача идёт — ещё ' + span(end - now);
            else text.textContent = 'Время выдачи закончилось';
        };
        render();
        setInterval(render, 30000);
    }

    // ---- Учебная оплата: таймер брони и формат полей карты
    const pay = document.querySelector('[data-mock-pay]');
    if (!pay) return;
    const timer = pay.querySelector('[data-mock-timer]');
    if (timer) {
        const expires = Date.parse(pay.dataset.expiresAt);
        const offset = Date.parse(pay.dataset.serverNow) - Date.now();
        const tick = () => {
            const left = Math.max(0, Math.ceil((expires - Date.now() - offset) / 1000));
            timer.textContent = left > 0 ? Math.floor(left / 60) + ':' + String(left % 60).padStart(2, '0') : 'истекла';
        };
        tick();
        setInterval(tick, 500);
    }
    const number = pay.querySelector('[data-card-number]');
    number?.addEventListener('input', () => {
        const digits = number.value.replace(/\D/g, '').slice(0, 19);
        number.value = digits.replace(/(\d{4})(?=\d)/g, '$1 ');
    });
    const expiry = pay.querySelector('[data-card-expiry]');
    expiry?.addEventListener('input', () => {
        const d = expiry.value.replace(/\D/g, '').slice(0, 4);
        expiry.value = d.length > 2 ? d.slice(0, 2) + '/' + d.slice(2) : d;
    });
    // Защита от двойного щелчка: вторая отправка всё равно ничего не изменит на сервере
    const form = pay.querySelector('[data-mock-form]');
    form?.addEventListener('submit', () => {
        setTimeout(() => form.querySelectorAll('button').forEach(b => { b.disabled = true; }), 0);
    });
})();
