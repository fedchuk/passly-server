#!/usr/bin/env python3
import argparse
import getpass
import json
import os
import sys
import urllib.error
import urllib.request
from decimal import Decimal, InvalidOperation

DEFAULT_MENU = {
    "modifierGroups": [
        {"name": "Doneness", "minSelect": 1, "maxSelect": 1,
         "options": [{"name": "Blue"}, {"name": "Rare"}, {"name": "Medium"}, {"name": "Well done"}]},
        {"name": "Side", "minSelect": 0, "maxSelect": 1,
         "options": [{"name": "Fries"}, {"name": "Green salad"}, {"name": "Mashed potatoes"},
                     {"name": "Green beans", "priceDelta": "1.50"}]},
        {"name": "Sauce", "minSelect": 0, "maxSelect": 1,
         "options": [{"name": "Pepper"}, {"name": "Béarnaise"}, {"name": "Roquefort", "priceDelta": "1.00"}]},
        {"name": "Milk", "minSelect": 0, "maxSelect": 1,
         "options": [{"name": "Oat milk", "priceDelta": "0.40"}, {"name": "Lactose-free", "priceDelta": "0.30"}]},
    ],
    "categories": [
        {"name": "Starters", "station": "Kitchen", "items": [
            {"name": "French onion soup", "price": "8.50", "description": "With gratinéed bread"},
            {"name": "Eggs mayonnaise", "price": "6.50"},
            {"name": "Burgundy snails", "price": "12.00", "description": "Six snails, garlic and parsley butter"},
            {"name": "Country terrine", "price": "9.00"},
            {"name": "Warm goat cheese salad", "price": "11.00", "description": "Goat cheese on toast, walnuts"},
        ]},
        {"name": "Mains", "station": "Kitchen", "children": [
            {"name": "Meat", "items": [
                {"name": "Rib steak", "price": "28.00", "description": "300 g",
                 "modifierGroups": ["Doneness", "Side", "Sauce"]},
                {"name": "Steak tartare", "price": "19.50", "modifierGroups": ["Side"]},
                {"name": "Duck confit", "price": "22.00", "description": "Duck leg, garlic potatoes"},
                {"name": "Beef bourguignon", "price": "21.00"},
            ]},
            {"name": "Fish", "items": [
                {"name": "Sole in brown butter", "price": "32.00", "stopList": True},
                {"name": "Mussels in white wine", "price": "18.00", "modifierGroups": ["Side"]},
            ]},
            {"name": "Bistro classics", "items": [
                {"name": "Ham and cheese toastie", "price": "12.00"},
                {"name": "Ham and cheese toastie with a fried egg", "price": "13.50"},
            ]},
        ]},
        {"name": "Desserts", "station": "Kitchen", "items": [
            {"name": "Crème brûlée", "price": "8.00"},
            {"name": "Upside-down apple tart", "price": "8.50"},
            {"name": "Chocolate mousse", "price": "7.50"},
            {"name": "Profiteroles", "price": "9.00"},
            {"name": "Coffee and mini desserts", "price": "9.50", "description": "Espresso with three mini desserts"},
        ]},
        {"name": "Drinks", "station": "Bar", "children": [
            {"name": "Soft drinks", "items": [
                {"name": "Mineral water 50 cl", "price": "4.00"},
                {"name": "Mint lemonade", "price": "4.50"},
                {"name": "Fresh orange juice", "price": "5.50"},
            ]},
            {"name": "Coffee and tea", "items": [
                {"name": "Espresso", "price": "2.20"},
                {"name": "White coffee", "price": "3.80", "modifierGroups": ["Milk"]},
                {"name": "Tea", "price": "3.50"},
            ]},
            {"name": "Beer", "taxRate": "Alcohol", "items": [
                {"name": "Draught beer 25 cl", "price": "4.00"},
                {"name": "Alcohol-free beer", "price": "4.50"},
            ]},
            {"name": "Wine", "taxRate": "Alcohol", "children": [
                {"name": "Red", "items": [
                    {"name": "Bordeaux, glass", "price": "6.50"},
                    {"name": "Côtes du Rhône, glass", "price": "5.50"},
                ]},
                {"name": "White", "items": [
                    {"name": "Chablis, glass", "price": "8.00"},
                    {"name": "Sancerre, glass", "price": "8.50"},
                ]},
                {"name": "Rosé", "items": [
                    {"name": "Côtes de Provence, glass", "price": "6.00"},
                ]},
            ]},
            {"name": "Aperitifs", "taxRate": "Alcohol", "items": [
                {"name": "Kir", "price": "6.00"},
                {"name": "Pastis", "price": "5.00"},
                {"name": "Glass of champagne", "price": "12.00"},
            ]},
        ]},
    ],
}


class ApiError(Exception):
    pass


class Passly:
    def __init__(self, base_url, venue_id):
        self.base_url = base_url.rstrip("/")
        self.venue = f"{self.base_url}/api/v1/venues/{venue_id}"
        self.token = None

    def login(self, email, password):
        self.token = self.call("POST", f"{self.base_url}/api/v1/auth/login",
                               {"email": email, "password": password})["accessToken"]

    def call(self, method, url, body=None):
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(url, data=data, method=method)
        request.add_header("Accept", "application/json")
        if data is not None:
            request.add_header("Content-Type", "application/json")
        if self.token:
            request.add_header("Authorization", f"Bearer {self.token}")
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:
            detail = e.read().decode(errors="replace")
            try:
                detail = json.loads(detail).get("detail") or detail
            except ValueError:
                pass
            raise ApiError(f"{method} {url} -> {e.code}: {detail}") from None
        except urllib.error.URLError as e:
            raise ApiError(f"{method} {url} -> {e.reason}") from None


class Seeder:
    def __init__(self, api, menu):
        self.api = api
        self.menu = menu
        self.created = {"modifier groups": 0, "categories": 0, "items": 0}
        self.skipped = {"modifier groups": 0, "categories": 0, "items": 0}

    def run(self):
        stations = {s["name"].lower(): s["id"] for s in self.api.call("GET", f"{self.api.venue}/stations")}
        self.tax_rates = self.api.call("GET", f"{self.api.venue}/tax-rates")
        groups = self.seed_modifier_groups()
        tree = self.api.call("GET", f"{self.api.venue}/categories")
        for spec in self.menu["categories"]:
            self.seed_category(spec, None, tree, stations, groups)
        return self.created, self.skipped

    def tax_rate_id(self, value, title):
        if value is None:
            return None
        text = str(value).strip()
        try:
            percent = Decimal(text.removesuffix("%").replace(",", "."))
        except InvalidOperation:
            percent = None
        for rate in self.tax_rates:
            if rate["name"].lower() == text.lower() or (percent is not None and Decimal(rate["rate"]) == percent):
                return rate["id"]
        known = ", ".join(f"{r['name']} {r['rate']}%" for r in self.tax_rates) or "none"
        raise ApiError(f"Tax rate '{text}' for '{title}' not found. Available: {known}. "
                       f"Add it on the Settings page first.")

    def seed_modifier_groups(self):
        existing = {g["name"].lower(): g["id"] for g in self.api.call("GET", f"{self.api.venue}/modifier-groups")}
        for spec in self.menu.get("modifierGroups", []):
            key = spec["name"].lower()
            if key in existing:
                self.skipped["modifier groups"] += 1
                continue
            body = {
                "name": spec["name"],
                "minSelect": spec["minSelect"],
                "maxSelect": spec["maxSelect"],
                "options": [{"name": o["name"], "priceDelta": o.get("priceDelta", "0")} for o in spec["options"]],
            }
            existing[key] = self.api.call("POST", f"{self.api.venue}/modifier-groups", body)["id"]
            self.created["modifier groups"] += 1
            print(f"+ modifier group {spec['name']}")
        return existing

    def seed_category(self, spec, parent_id, siblings, stations, groups, path=""):
        title = f"{path}{spec['name']}"
        station_id = None
        if spec.get("station"):
            station_id = stations.get(spec["station"].lower())
            if station_id is None:
                raise ApiError(f"Station '{spec['station']}' not found. Available: {', '.join(stations) or 'none'}. "
                               f"Add it on the Stations page first.")
        node = next((c for c in siblings if c["name"].lower() == spec["name"].lower()), None)
        if node is None:
            body = {"name": spec["name"], "parentId": parent_id, "stationId": station_id,
                    "taxRateId": self.tax_rate_id(spec.get("taxRate"), title)}
            created = self.api.call("POST", f"{self.api.venue}/categories", body)
            node = {"id": created["id"], "name": created["name"], "children": []}
            self.created["categories"] += 1
            print(f"+ category {title}")
        else:
            self.skipped["categories"] += 1

        if spec.get("items"):
            self.seed_items(node["id"], spec["items"], groups, title)
        for child in spec.get("children", []):
            self.seed_category(child, node["id"], node.get("children", []), stations, groups, f"{title} › ")

    def seed_items(self, category_id, specs, groups, title):
        url = f"{self.api.venue}/categories/{category_id}/items"
        existing = {i["name"].lower() for i in self.api.call("GET", url)}
        for spec in specs:
            if spec["name"].lower() in existing:
                self.skipped["items"] += 1
                continue
            body = {
                "name": spec["name"],
                "description": spec.get("description"),
                "price": spec["price"],
                "taxRateId": self.tax_rate_id(spec.get("taxRate"), f"{title} › {spec['name']}"),
            }
            item = self.api.call("POST", url, body)
            self.created["items"] += 1
            print(f"+ item {title} › {spec['name']} {spec['price']}")

            names = spec.get("modifierGroups", [])
            if names:
                missing = [n for n in names if n.lower() not in groups]
                if missing:
                    raise ApiError(f"Modifier groups not found for '{spec['name']}': {', '.join(missing)}")
                item = self.api.call("PUT", f"{self.api.venue}/items/{item['id']}/modifier-groups",
                                     {"groupIds": [groups[n.lower()] for n in names], "version": item["version"]})
            if spec.get("stopList"):
                self.api.call("PUT", f"{self.api.venue}/items/{item['id']}/availability", {"available": False})


class Wiper:
    def __init__(self, api):
        self.api = api
        self.removed = {"items": 0, "categories": 0, "modifier groups": 0}

    def run(self):
        for node in self.api.call("GET", f"{self.api.venue}/categories"):
            self.wipe_category(node)
        for group in self.api.call("GET", f"{self.api.venue}/modifier-groups"):
            self.api.call("DELETE", f"{self.api.venue}/modifier-groups/{group['id']}")
            self.removed["modifier groups"] += 1
        return self.removed

    def wipe_category(self, node):
        for child in node.get("children", []):
            self.wipe_category(child)
        for item in self.api.call("GET", f"{self.api.venue}/categories/{node['id']}/items"):
            self.api.call("DELETE", f"{self.api.venue}/items/{item['id']}")
            self.removed["items"] += 1
        self.api.call("DELETE", f"{self.api.venue}/categories/{node['id']}")
        self.removed["categories"] += 1
        print(f"- category {node['name']}")


def main():
    parser = argparse.ArgumentParser(
        description="Fill the menu of a Passly venue through the REST API. "
                    "Safe to run again: existing groups, categories and items with the same name are skipped.")
    parser.add_argument("--url", default=os.environ.get("PASSLY_URL", "http://localhost:8080"),
                        help="server address, default http://localhost:8080 or PASSLY_URL")
    parser.add_argument("--venue", required=True, help="venue id, the UUID from /admin/venues/<id>/overview")
    parser.add_argument("--email", default=os.environ.get("PASSLY_EMAIL"),
                        help="owner or manager email, or PASSLY_EMAIL")
        parser.add_argument("--menu", help="JSON file with the menu, the built-in bistro menu in English if omitted")
    parser.add_argument("--wipe", action="store_true",
                        help="remove the whole current menu first: archive all items, delete categories and modifier groups")
    parser.add_argument("--yes", action="store_true", help="do not ask for confirmation before --wipe")
    args = parser.parse_args()

    email = args.email or input("Email: ")
    password = os.environ.get("PASSLY_PASSWORD") or getpass.getpass("Password: ")
    menu = DEFAULT_MENU
    if args.menu:
        with open(args.menu, encoding="utf-8") as file:
            menu = json.load(file)

    if args.wipe and not args.yes:
        answer = input(f"This removes the whole menu of venue {args.venue} at {args.url}. Type WIPE to continue: ")
        if answer.strip().upper() != "WIPE":
            print(f"Nothing changed. Expected WIPE, got {answer!r}.")
            sys.exit(1)

    api = Passly(args.url, args.venue)
    try:
        api.login(email, password)
        if args.wipe:
            removed = Wiper(api).run()
            print("Removed: " + ", ".join(f"{count} {kind}" for kind, count in removed.items()))
        created, skipped = Seeder(api, menu).run()
    except ApiError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    print("Created: " + ", ".join(f"{count} {kind}" for kind, count in created.items()))
    print("Already there: " + ", ".join(f"{count} {kind}" for kind, count in skipped.items()))


if __name__ == "__main__":
    main()