(() => {
    const OWN_CHANGE_GRACE_MS = 2500;
    const config = document.currentScript.dataset;
    const banner = document.getElementById('live-banner');
    const loadedAt = Date.now();
    let submitting = false;
    let timer = null;

    document.addEventListener('submit', () => submitting = true);

    const concerns = notice => {
        if (config.tableId) return notice.tableId === config.tableId;
        if (config.stationId) return notice.stationIds.includes(config.stationId);
        return true;
    };

    const busy = () => {
        const active = document.activeElement;
        const typing = active && ['INPUT', 'SELECT', 'TEXTAREA'].includes(active.tagName);
        return submitting || typing || document.querySelector('dialog[open]') !== null;
    };

    const refresh = () => {
        timer = null;
        if (!busy()) {
            location.reload();
        } else if (banner) {
            banner.hidden = false;
        } else {
            timer = setTimeout(refresh, 3000);
        }
    };

    const source = new EventSource(config.events);
    source.addEventListener('order', event => {
        if (Date.now() - loadedAt < OWN_CHANGE_GRACE_MS) return;
        if (concerns(JSON.parse(event.data)) && timer === null) timer = setTimeout(refresh, 400);
    });
    window.addEventListener('pagehide', () => source.close());
})();