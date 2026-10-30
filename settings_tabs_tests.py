#!/usr/bin/env python3
from pathlib import Path

path = Path("src/test/kotlin/com/passly/web/admin/VenueSettingsAdminTest.kt")
text = path.read_text()

messages = [
    'flash { attribute("message", "CHF is accepted at 1.05") }',
    'flash { attribute("message", "Rate of CHF is now 1.07") }',
    'flash { attribute("message", "CHF is no longer accepted") }',
    'flash { attribute("error", "Exchange rate \'one\' is not a number") }',
]
for message in messages:
    old = "            redirectedUrl(page)\n            " + message
    new = "            redirectedUrl(\"$page#currencies\")\n            " + message
    if new in text:
        continue
    if text.count(old) != 1:
        raise SystemExit(f"Cannot find the redirect before: {message}")
    text = text.replace(old, new)

test = '''
    @Test
    fun `settings are split into tabs`() {
        acceptSwissFrancs()

        mockMvc.get(page) { with(member(StaffRole.MANAGER)) }.andExpect {
            content { string(containsString("role=\\"tablist\\"")) }
            content { string(containsString("href=\\"#general\\"")) }
            content { string(containsString("href=\\"#currencies\\"")) }
            content { string(containsString("href=\\"#tax-rates\\"")) }
            content { string(containsString("href=\\"#discounts\\"")) }
            content { string(containsString("id=\\"settings-currencies\\" role=\\"tabpanel\\"")) }
            content { string(containsString("<span class=\\"tab-count\\">1</span>")) }
        }
    }
'''
if "fun `settings are split into tabs`()" not in text:
    text = text.rstrip()
    if not text.endswith("}"):
        raise SystemExit("Unexpected end of VenueSettingsAdminTest.kt")
    text = text[:-1].rstrip() + "\n" + test + "}\n"

path.write_text(text)
print("updated", path)
