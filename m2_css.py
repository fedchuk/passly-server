from pathlib import Path

CSS = Path("src/main/resources/static/css/admin.css")
MARKER = ".add-category {\n"

MENU_BLOCK = """.add-category {
    position: relative;
}

.add-category > summary {
    list-style: none;
}

.add-category > summary::-webkit-details-marker {
    display: none;
}

.add-category-form {
    position: absolute;
    top: calc(100% + 0.5rem);
    right: 0;
    z-index: 20;
    width: min(320px, calc(100vw - 2rem));
    padding: 1rem;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    box-shadow: var(--shadow);
}

.menu-accordion {
    display: grid;
    gap: 0.75rem;
}

.menu-root {
    padding: 0;
}

.menu-root-header {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.85rem 1rem;
}

.menu-root-title {
    display: flex;
    flex: 1;
    align-items: center;
    gap: 0.9rem;
    min-width: 0;
    color: var(--text);
}

.menu-root-title:hover {
    text-decoration: none;
}

.menu-root-text {
    display: grid;
    gap: 0.25rem;
    min-width: 0;
}

.menu-root-text strong {
    font-size: 1.05rem;
}

.menu-root-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 0.35rem;
}

.pill {
    display: inline-block;
    padding: 0.1rem 0.55rem;
    border-radius: 999px;
    background: var(--surface-muted);
    color: var(--muted);
    font-size: 0.75rem;
    font-weight: 600;
}

.muted-badge {
    background: var(--surface-muted);
    color: var(--muted);
}

.menu-tile {
    display: grid;
    place-items: center;
    flex: none;
    width: 44px;
    height: 44px;
    border-radius: 10px;
    background: hsl(var(--hue, 240) 70% 92%);
    color: hsl(var(--hue, 240) 45% 35%);
    font-weight: 700;
}

.menu-tile.large {
    width: 56px;
    height: 56px;
    font-size: 1.25rem;
}

@media (prefers-color-scheme: dark) {
    .menu-tile {
        background: hsl(var(--hue, 240) 30% 22%);
        color: hsl(var(--hue, 240) 70% 80%);
    }
}

.icon-button {
    width: 2.25rem;
    padding: 0.45rem 0;
}

.chevron-link {
    display: grid;
    place-items: center;
    width: 2.25rem;
    height: 2.25rem;
    border-radius: var(--radius-small);
    color: var(--muted);
}

.chevron-link:hover {
    background: var(--surface-muted);
}

.chevron-link .icon {
    transition: transform 0.15s;
}

.menu-root.open .chevron-link .icon {
    transform: rotate(180deg);
}

.menu-root.open .menu-root-header {
    border-bottom: 1px solid var(--border);
}

.menu-root-body {
    padding: 1rem;
}

.sub-cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
    gap: 0.6rem;
    margin-bottom: 1rem;
}

.sub-card {
    display: flex;
    align-items: center;
    gap: 0.35rem;
    padding: 0.6rem 0.7rem 0.6rem 0.25rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--surface);
    color: var(--text);
}

.sub-card:hover {
    border-color: var(--primary);
}

.sub-card-link {
    display: flex;
    flex: 1;
    align-items: center;
    gap: 0.7rem;
    min-width: 0;
    color: var(--text);
}

.sub-card-link:hover {
    text-decoration: none;
}

.sub-card-link > .icon {
    margin-left: auto;
    color: var(--muted);
    transform: rotate(-90deg);
}

.sub-card-text {
    display: grid;
    min-width: 0;
}

.sub-card-text strong {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
}

.add-sub-card {
    margin-bottom: 1rem;
    color: var(--muted);
}

.add-sub-card > summary {
    display: inline-flex;
    align-items: center;
    padding: 0.45rem 0.9rem;
    border: 1px dashed var(--border);
    border-radius: var(--radius);
    font-weight: 600;
    list-style: none;
    cursor: pointer;
}

.add-sub-card > summary:hover {
    border-color: var(--primary);
    color: var(--primary);
}

.add-sub-card > summary::-webkit-details-marker {
    display: none;
}

.add-sub-card form {
    display: grid;
    grid-template-columns: minmax(0, 2fr) minmax(0, 2fr) auto;
    gap: 0.5rem;
    max-width: 640px;
    margin-top: 0.6rem;
}

.add-sub-card input,
.add-sub-card select {
    margin-top: 0;
}

.dish-panel {
    padding: 0.75rem 1rem;
    border-radius: var(--radius);
    background: var(--surface-muted);
}

.dish-panel-header {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.6rem;
    margin-bottom: 0.5rem;
}

.dish-panel-header h2 {
    margin: 0;
    font-size: 1.15rem;
}

.dish-list {
    display: grid;
    gap: 0.5rem;
    margin: 0;
    padding: 0;
    list-style: none;
}

.dish-row {
    display: flex;
    align-items: center;
    gap: 0.8rem;
    padding: 0.6rem 0.75rem;
    border: 1px solid var(--border);
    border-radius: var(--radius-small);
    background: var(--surface);
}

.dish-row.stopped .menu-tile {
    filter: grayscale(1);
    opacity: 0.6;
}

.dish-text {
    display: grid;
    flex: 1;
    gap: 0.1rem;
    min-width: 0;
    color: var(--text);
}

.dish-text:hover {
    text-decoration: none;
}

.dish-text:hover strong {
    color: var(--primary);
}

.dish-text small {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
}

.dish-price {
    min-width: 4.5rem;
    font-weight: 600;
    text-align: right;
    font-variant-numeric: tabular-nums;
}

.dish-row .actions {
    margin-left: 0;
}

@media (max-width: 700px) {
    .menu-root-header,
    .dish-row {
        flex-wrap: wrap;
    }

    .menu-root-header .actions,
    .dish-row .actions {
        width: 100%;
        justify-content: flex-end;
    }

    .add-sub-card form {
        grid-template-columns: 1fr;
    }
}

.drag-handle {
    display: grid;
    place-items: center;
    flex: none;
    width: 1.75rem;
    height: 2.25rem;
    padding: 0;
    border: 0;
    border-radius: var(--radius-small);
    background: transparent;
    color: var(--muted);
    cursor: grab;
    touch-action: none;
}

.drag-handle:hover,
.drag-handle:focus-visible {
    background: var(--surface-muted);
    color: var(--text);
}

.drag-handle:active {
    cursor: grabbing;
}

.drag-handle .icon {
    width: 18px;
    height: 18px;
    stroke-width: 3;
}

.grip-hint {
    color: var(--muted);
    font-weight: 700;
    letter-spacing: -0.15em;
}

.drag-ghost {
    opacity: 0.4;
    outline: 2px dashed var(--primary);
    outline-offset: -2px;
}

.drag-chosen {
    box-shadow: var(--shadow);
}

[data-order-url].saving {
    opacity: 0.7;
    pointer-events: none;
}
"""

text = CSS.read_text()
if ".drag-handle {" in text:
    print("Already applied")
elif text.count(MARKER) != 1:
    print("Menu block not found, nothing changed")
else:
    start = text.index(MARKER)
    CSS.write_text(text[:start] + MENU_BLOCK)
    print("Menu styles replaced")