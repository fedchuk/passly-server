from pathlib import Path

CSS = Path("src/main/resources/static/css/admin.css")

OLD_GRID = """    grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
    gap: 0.6rem;
    margin-bottom: 1rem;"""
NEW_GRID = """    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 0.6rem;
    margin-bottom: 0.75rem;"""

OLD_CHEVRON = """.sub-card-link > .icon {
    margin-left: auto;
    color: var(--muted);
    transform: rotate(-90deg);
}
"""
NEW_SELECTED = """.sub-card.selected {
    border-color: var(--primary);
    background: color-mix(in srgb, var(--primary) 8%, var(--surface));
    box-shadow: inset 0 -3px 0 var(--primary);
}

.sub-cards + .sub-cards {
    padding-top: 0.75rem;
    border-top: 1px dashed var(--border);
}
"""

text = CSS.read_text()
if ".sub-card.selected {" in text:
    print("Already applied")
elif OLD_GRID not in text or OLD_CHEVRON not in text:
    print("Expected blocks not found, nothing changed")
else:
    CSS.write_text(text.replace(OLD_GRID, NEW_GRID).replace(OLD_CHEVRON, NEW_SELECTED))
    print("Subcategory rows styled")