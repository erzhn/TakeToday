// Этап 4 (Эржан): сканирование QR-кода покупателя камерой. Распознанный текст подставляется в поле кода
// и форма отправляется сама — дальше всё проверяет сервер (PickupService). Камера в браузере работает
// только на https:// или localhost; без неё всегда есть ручной ввод.
(() => {
    const root = document.querySelector('[data-pickup]');
    if (!root) return;
    const form = root.querySelector('[data-pickup-form]');
    const input = root.querySelector('[data-pickup-code]');
    const scanner = root.querySelector('[data-scanner]');
    const video = root.querySelector('[data-scanner-video]');
    const canvas = root.querySelector('[data-scanner-canvas]');
    const idle = root.querySelector('[data-scanner-idle]');
    const startButton = root.querySelector('[data-scanner-start]');
    const stopButton = root.querySelector('[data-scanner-stop]');
    const status = root.querySelector('[data-scanner-status]');
    const file = root.querySelector('[data-scanner-file]');
    const context = canvas.getContext('2d', {willReadFrequently: true});
    let stream = null;
    let detector = null;
    let sent = false;

    function say(text, isError) {
        status.textContent = text || '';
        status.classList.toggle('is-error', Boolean(isError));
    }

    // Встроенный распознаватель есть не везде (например, нет в Chrome на Windows) — тогда jsQR
    if ('BarcodeDetector' in window) {
        try { detector = new window.BarcodeDetector({formats: ['qr_code']}); } catch (e) { detector = null; }
    }

    function submit(text) {
        if (sent || !text) return;
        sent = true;
        input.value = text.trim();
        say('QR-код распознан, проверяем…');
        stop();
        form.requestSubmit ? form.requestSubmit() : form.submit();
    }

    async function decode(source, width, height) {
        if (detector) {
            try {
                const codes = await detector.detect(source);
                if (codes.length) return codes[0].rawValue;
            } catch (e) { /* попробуем jsQR */ }
        }
        if (!window.jsQR || !width || !height) return null;
        canvas.width = width;
        canvas.height = height;
        context.drawImage(source, 0, 0, width, height);
        const result = window.jsQR(context.getImageData(0, 0, width, height).data, width, height,
            {inversionAttempts: 'dontInvert'});
        return result ? result.data : null;
    }

    async function loop() {
        if (!stream) return;
        if (video.readyState >= 2) {
            // Уменьшаем кадр: распознаётся так же, а телефон не греется
            const scale = Math.min(1, 640 / video.videoWidth);
            const text = await decode(video, Math.round(video.videoWidth * scale), Math.round(video.videoHeight * scale));
            if (text) { submit(text); return; }
        }
        setTimeout(() => requestAnimationFrame(loop), 120);
    }

    async function start() {
        if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
            say('Камера доступна только по https:// или на localhost. Введите код вручную или распознайте фото.', true);
            return;
        }
        try {
            stream = await navigator.mediaDevices.getUserMedia({video: {facingMode: {ideal: 'environment'}}, audio: false});
        } catch (e) {
            say(e.name === 'NotAllowedError'
                ? 'Доступ к камере запрещён. Разрешите камеру в настройках браузера или введите код вручную.'
                : 'Не удалось включить камеру. Введите код вручную.', true);
            return;
        }
        video.srcObject = stream;
        video.hidden = false;
        idle.hidden = true;
        stopButton.hidden = false;
        scanner.classList.add('is-live');
        await video.play().catch(() => {});
        say('Наведите камеру на QR-код покупателя');
        loop();
    }

    function stop() {
        if (stream) stream.getTracks().forEach(t => t.stop());
        stream = null;
        video.hidden = true;
        idle.hidden = false;
        stopButton.hidden = true;
        scanner.classList.remove('is-live');
    }

    startButton.addEventListener('click', start);
    stopButton.addEventListener('click', () => { stop(); say(''); });
    document.addEventListener('visibilitychange', () => { if (document.hidden) stop(); });

    // Фото QR-кода: если камера недоступна (http в локальной сети) — снимок с телефона
    file.addEventListener('change', async () => {
        const picked = file.files && file.files[0];
        if (!picked) return;
        say('Распознаём фото…');
        const image = new Image();
        image.onload = async () => {
            const scale = Math.min(1, 1200 / Math.max(image.naturalWidth, image.naturalHeight));
            const text = await decode(image, Math.round(image.naturalWidth * scale), Math.round(image.naturalHeight * scale));
            URL.revokeObjectURL(image.src);
            text ? submit(text) : say('На фото не найден QR-код. Сфотографируйте ближе или введите код вручную.', true);
        };
        image.onerror = () => say('Не удалось открыть фото.', true);
        image.src = URL.createObjectURL(picked);
        file.value = '';
    });

    // Ручной ввод: «482913» показываем как «482 913»; содержимое QR (TAKETODAY:…) не трогаем
    input.addEventListener('input', () => {
        if (/^[\d\s]*$/.test(input.value)) {
            const d = input.value.replace(/\D/g, '').slice(0, 6);
            input.value = d.length > 3 ? d.slice(0, 3) + ' ' + d.slice(3) : d;
        }
    });

    // Защита от двойного нажатия «Подтвердить выдачу»; сервер всё равно не выдаст дважды
    document.querySelectorAll('[data-confirm-form]').forEach(f => f.addEventListener('submit', () => {
        setTimeout(() => f.querySelectorAll('button').forEach(b => { b.disabled = true; }), 0);
    }));
})();
