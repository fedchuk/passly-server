#!/usr/bin/env python3
import argparse
import getpass
import json
import os
import sys
import urllib.error
import urllib.request

DEFAULT_MENU = {
    "taxRate": "10",
    "modifierGroups": [
        {"name": "Doneness", "minSelect": 1, "maxSelect": 1,
         "options": [{"name": "Rare"}, {"name": "Medium rare"}, {"name": "Medium"}, {"name": "Well done"}]},
        {"name": "Side", "minSelect": 0, "maxSelect": 1,
         "options": [{"name": "Fries"}, {"name": "Green salad"}, {"name": "Grilled vegetables", "priceDelta": "1.50"}]},
        {"name": "Burger extras", "minSelect": 0, "maxSelect": 3,
         "options": [{"name": "Extra cheese", "priceDelta": "1.00"}, {"name": "Bacon", "priceDelta": "1.50"},
                     {"name": "No onion"}]},
        {"name": "Milk", "minSelect": 0, "maxSelect": 1,
         "options": [{"name": "Oat milk", "priceDelta": "0.40"}, {"name": "Lactose free", "priceDelta": "0.30"}]},
        {"name": "Gin", "minSelect": 1, "maxSelect": 1,
         "options": [{"name": "Beefeater"}, {"name": "Hendrick's", "priceDelta": "3.00"}, {"name": "Tanqueray", "priceDelta": "2.00"}]},
    ],
    "categories": [
        {"name": "Starters", "station": "Kitchen", "items": [
            {"name": "Patatas bravas", "price": "6.50", "description": "Crispy potatoes, spicy brava sauce, aioli"},
            {"name": "Ham croquettes", "price": "8.00", "description": "Six croquettes with Iberian ham"},
            {"name": "Padrón peppers", "price": "7.00"},
            {"name": "Gazpacho", "price": "6.00", "description": "Cold tomato soup"},
        ]},
        {"name": "Mains", "station": "Kitchen", "children": [
            {"name": "Meat", "items": [
                {"name": "Ribeye", "price": "26.00", "description": "300 g, grilled", "modifierGroups": ["Doneness", "Side"]},
                {"name": "Bistro burger", "price": "14.50", "modifierGroups": ["Doneness", "Burger extras", "Side"]},
            ]},
            {"name": "Fish", "items": [
                {"name": "Grilled sea bass", "price": "22.00", "modifierGroups": ["Side"]},
                {"name": "Galician octopus", "price": "19.50", "stopList": True},
            ]},
            {"name": "Rice", "items": [
                {"name": "Paella valenciana", "price": "18.00", "description": "Per person, minimum two"},
                {"name": "Black rice", "price": "19.00", "description": "Squid ink, aioli"},
            ]},
        ]},
        {"name": "Desserts", "station": "Kitchen", "items": [
            {"name": "Crema catalana", "price": "6.00"},
            {"name": "Basque cheesecake", "price": "6.50"},
            {"name": "Churros with chocolate", "price": "5.50"},
        ]},
        {"name": "Drinks", "station": "Bar", "children": [
            {"name": "Soft drinks", "items": [
                {"name": "Still water", "price": "2.50"},
                {"name": "Cola", "price": "3.00"},
                {"name": "Fresh orange juice", "price": "4.50"},
            ]},
            {"name": "Coffee", "items": [
                {"name": "Espresso", "price": "1.50"},
                {"name": "Cortado", "price": "1.70", "modifierGroups": ["Milk"]},
                {"name": "Café con leche", "price": "2.00", "modifierGroups": ["Milk"]},
            ]},
            {"name": "Beer", "taxRate": "21", "items": [
                {"name": "Draft beer", "price": "3.50"},
                {"name": "Alcohol free beer", "price": "3.00"},
            ]},
            {"name": "Wine", "taxRate": "21", "children": [
                {"name": "Red", "items": [
                    {"name": "Rioja Crianza, glass", "price": "4.50"},
                    {"name": "Ribera del Duero, glass", "price": "5.00"},
                ]},
                {"name": "White", "items": [
                    {"name": "Albariño, glass", "price": "4.80"},
                    {"name": "Verdejo, glass", "price": "4.00"},
                ]},
            ]},
            {"name": "Cocktails", "taxRate": "21", "items": [
                {"name": "Sangria, jug", "price": "16.00"},
                {"name": "Aperol spritz", "price": "8.00"},
                {"name": "Gin and tonic", "price": "9.00", "modifierGroups": ["Gin"]},
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
        groups = self.seed_modifier_groups()
        tree = self.api.call("GET", f"{self.api.venue}/categories")
        for spec in self.menu["categories"]:
            self.seed_category(spec, None, tree, stations, groups, self.menu.get("taxRate", "10"))
        return self.created, self.skipped

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

    def seed_category(self, spec, parent_id, siblings, stations, groups, tax_rate, path=""):
        title = f"{path}{spec['name']}"
        station_id = None
        if spec.get("station"):
            station_id = stations.get(spec["station"].lower())
            if station_id is None:
                raise ApiError(f"Station '{spec['station']}' not found. Available: {', '.join(stations) or 'none'}. "
                               f"Add it on the Stations page first.")
        node = next((c for c in siblings if c["name"].lower() == spec["name"].lower()), None)
        if node is None:
            body = {"name": spec["name"], "parentId": parent_id, "stationId": station_id}
            created = self.api.call("POST", f"{self.api.venue}/categories", body)
            node = {"id": created["id"], "name": created["name"], "children": []}
            self.created["categories"] += 1
            print(f"+ category {title}")
        else:
            self.skipped["categories"] += 1

        tax_rate = spec.get("taxRate", tax_rate)
        if spec.get("items"):
            self.seed_items(node["id"], spec["items"], groups, tax_rate, title)
        for child in spec.get("children", []):
            self.seed_category(child, node["id"], node.get("children", []), stations, groups, tax_rate, f"{title} › ")

    def seed_items(self, category_id, specs, groups, tax_rate, title):
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
                "taxRate": spec.get("taxRate", tax_rate),
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


def main():
    parser = argparse.ArgumentParser(
        description="Fill the menu of a Passly venue through the REST API. "
                    "Safe to run again: existing groups, categories and items with the same name are skipped.")
    parser.add_argument("--url", default=os.environ.get("PASSLY_URL", "http://localhost:8080"),
                        help="server address, default http://localhost:8080 or PASSLY_URL")
    parser.add_argument("--venue", required=True, help="venue id, the UUID from /admin/venues/<id>/overview")
    parser.add_argument("--email", default=os.environ.get("PASSLY_EMAIL"),
                        help="owner or manager email, or PASSLY_EMAIL")
    parser.add_argument("--menu", help="JSON file with the menu, the built-in bistro menu if omitted")
    args = parser.parse_args()

    email = args.email or input("Email: ")
    password = os.environ.get("PASSLY_PASSWORD") or getpass.getpass("Password: ")
    menu = DEFAULT_MENU
    if args.menu:
        with open(args.menu, encoding="utf-8") as file:
            menu = json.load(file)

    api = Passly(args.url, args.venue)
    try:
        api.login(email, password)
        created, skipped = Seeder(api, menu).run()
    except ApiError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    print("Created: " + ", ".join(f"{count} {kind}" for kind, count in created.items()))
    print("Already there: " + ", ".join(f"{count} {kind}" for kind, count in skipped.items()))


if __name__ == "__main__":
    main()