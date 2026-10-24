(() => {
    const error = document.querySelector('[data-reservation-error]');
    function showError(message) { error.textContent = message; error.hidden = !message; }
    async function json(response) {
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            if (response.status === 401) {
                const link = document.querySelector('[data-reservation-login]');
                if (link) link.hidden = false;
            }
            const failure = new Error(data.message || ({401: 'Войдите в учетную запись.', 403: 'Бронирование доступно только покупателям. Обновите страницу, если сессия истекла.'}[response.status]) || 'Не удалось проверить бронь. Попробуйте еще раз.');
            failure.definitive = response.status >= 400 && response.status < 500;
            throw failure;
        }
        return data;
    }
    const button = document.querySelector('[data-reserve-offer]');
    if (button) {
        let requestKey;
        let requestQuantity;
        const quantityInput = document.querySelector('[data-reservation-quantity]');
        const total = document.querySelector('[data-reservation-total]');
        quantityInput.addEventListener('input', () => {
            total.textContent = quantityInput.validity.valid
                ? (Number(total.dataset.unitPrice) * Number(quantityInput.value)).toLocaleString('ru-RU') + ' сом'
                : 'Укажите доступное количество порций';
        });
        button.addEventListener('click', async () => {
            if (!quantityInput.disabled && !quantityInput.reportValidity()) return;
            const quantity = Number(quantityInput.value);
            button.disabled = true;
            quantityInput.disabled = true;
            showError('');
            try {
                // Один ключ на попытку: повтор после потери ответа открывает тот же заказ.
                if (!requestKey || requestQuantity !== quantity) {
                    requestKey = crypto.randomUUID();
                    requestQuantity = quantity;
                }
                const token = await json(await fetch('/api/auth/csrf', {credentials: 'same-origin', cache: 'no-store'}));
                const data = await json(await fetch('/api/orders/reservations', {
                    method: 'POST', credentials: 'same-origin',
                    headers: {'Content-Type': 'application/json', [token.headerName]: token.token},
                    body: JSON.stringify({offerId: button.dataset.offerId, requestKey, quantity})
                }));
                location.assign('/reservations/' + data.order.id);
            } catch (e) {
                showError(e instanceof TypeError ? 'Нет соединения с сервером. Проверьте интернет и повторите попытку.' : e.message);
                button.disabled = false;
                // При потере ответа повторяем прежнее количество с тем же ключом.
                quantityInput.disabled = !e.definitive;
                if (e.definitive) requestKey = undefined;
            }
        });
    }
    const checkout = document.querySelector('[data-reservation-checkout]');
    if (!checkout) return;
    const timer = checkout.querySelector('[data-reservation-timer]');
    let status = checkout.dataset.status;
    let expiresAt = Date.parse(checkout.dataset.expiresAt);
    let serverTime = Date.parse(checkout.dataset.serverNow);
    let receivedAt = performance.now();
    let polling = false;
    function render() {
        const left = Math.max(0, Math.ceil((expiresAt - serverTime - (performance.now() - receivedAt)) / 1000));
        timer.textContent = Math.floor(left / 60) + ':' + String(left % 60).padStart(2, '0');
        checkout.querySelector('[data-reservation-active]').hidden = status !== 'AWAITING_PAYMENT';
        checkout.querySelector('[data-reservation-expired]').hidden = status !== 'CANCELLED';
        checkout.querySelector('[data-reservation-paid]').hidden = status !== 'PAID';
        checkout.querySelector('[data-reservation-status]').textContent = {
            AWAITING_PAYMENT: left > 0 ? 'Ожидает оплаты' : 'Проверяем состояние брони…',
            CANCELLED: 'Отменен', PAID: 'Оплачен'
        }[status];
    }
    async function refresh() {
        if (polling || status !== 'AWAITING_PAYMENT') return;
        polling = true;
        try {
            const data = await json(await fetch('/api/orders/reservations/' + checkout.dataset.orderId, {
                credentials: 'same-origin', cache: 'no-store'
            }));
            status = data.order.status;
            // Этап 3: оплата подтверждена (например, в другой вкладке) — сразу к коду получения
            if (status === 'PAID') { location.assign('/orders/' + checkout.dataset.orderId); return; }
            expiresAt = Date.parse(data.order.expiresAt);
            serverTime = Date.parse(data.serverNow);
            receivedAt = performance.now();
            showError('');
            render();
        } catch (e) {
            showError('Не удалось обновить состояние брони. Проверьте соединение. Срок брони продолжает действовать на сервере.');
        } finally { polling = false; }
    }
    render();
    refresh();
    const tick = setInterval(() => {
        render();
        if (status !== 'AWAITING_PAYMENT') { clearInterval(tick); clearInterval(poll); }
    }, 250);
    const poll = setInterval(refresh, 2000);
    window.addEventListener('online', refresh);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
})();
