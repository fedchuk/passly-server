(() => {
    const csrf = document.getElementById('order-csrf');
    const lists = document.querySelectorAll('[data-order-url]');
    const steps = { ArrowUp: -1, ArrowLeft: -1, ArrowDown: 1, ArrowRight: 1 };

    const entries = list => [...list.children].filter(child => child.matches(list.dataset.draggable));

    const save = async list => {
        const body = new URLSearchParams();
        body.append(csrf.name, csrf.value);
        if (list.dataset.parentId) body.append('parentId', list.dataset.parentId);
        entries(list).forEach(entry => body.append('orderedIds', entry.dataset.id));
        list.classList.add('saving');
        try {
            const response = await fetch(list.dataset.orderUrl, { method: 'POST', body });
            if (!response.ok) throw new Error(`Order not saved: ${response.status}`);
        } catch (error) {
            window.location.reload();
        } finally {
            list.classList.remove('saving');
        }
    };

    lists.forEach(list => Sortable.create(list, {
        draggable: list.dataset.draggable,
        handle: list.dataset.handle,
        animation: 150,
        ghostClass: 'drag-ghost',
        chosenClass: 'drag-chosen',
        onEnd: event => {
            if (event.oldIndex !== event.newIndex) save(list);
        },
    }));

    document.addEventListener('keydown', event => {
        const step = steps[event.key];
        const handle = event.target.closest('.drag-handle');
        if (!step || !event.altKey || !handle) return;
        const list = handle.closest('[data-order-url]');
        const siblings = entries(list);
        const entry = siblings.find(candidate => candidate.contains(handle));
        const neighbour = siblings[siblings.indexOf(entry) + step];
        event.preventDefault();
        if (!neighbour) return;
        if (step < 0) neighbour.before(entry); else neighbour.after(entry);
        handle.focus();
        save(list);
    });
})();