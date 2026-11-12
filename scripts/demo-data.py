#!/usr/bin/env python3
"""Demo catalogue of the development shop, created through the API like an administrator would.

    ./scripts/demo-data.py            # stack running: docker compose --profile app up -d --wait

Five categories, twenty products with a generated image each (abstract visuals in the colours of the shop, not
photos), then a few orders of alice@example.com so that the best sellers show demo products, and her reviews of the
products she bought (development data, written by the demo account). Idempotent: what already exists
(same name) is kept, a product without image gets one, orders are only placed while fewer than four demo products are
among the best sellers (the smoke test's products may be there too). Tokens come from scripts/dev-token.sh (SHOP_ADMIN_EMAIL and DEMO_USER_PASSWORD from .env).
Python standard library only. Development only.
"""
import json
import math
import os
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib

BASE = os.environ.get("BASE_URL", "http://localhost:8080")
API = BASE + "/api"
CUSTOMER = "alice@example.com"

# Category: (light colour, deep colour) of its images.
CATEGORIES = {
    "Informatique": ((0xE3, 0xEE, 0xEF), (0x0E, 0x5A, 0x61)),
    "Audio": ((0xE6, 0xE9, 0xF2), (0x1F, 0x4E, 0x8C)),
    "Maison": ((0xEF, 0xE3, 0xE0), (0xB4, 0x53, 0x0D)),
    "Voyage": ((0xEC, 0xEE, 0xDF), (0x5B, 0x6B, 0x2A)),
    "Sport": ((0xEE, 0xE4, 0xEF), (0x6D, 0x3A, 0x7A)),
}

# (name, category, price, stock, description)
PRODUCTS = [
    ("Clavier mécanique TKL", "Informatique", 89.90, 24,
     "Format sans pavé numérique, interrupteurs linéaires, rétroéclairage blanc et câble USB-C détachable. "
     "Disposition AZERTY."),
    ("Souris sans fil", "Informatique", 39.90, 3,
     "Capteur 4 000 DPI, six boutons programmables, autonomie de 70 heures. Bluetooth et récepteur USB."),
    ("Écran 27 pouces QHD", "Informatique", 299.00, 12,
     "Dalle IPS 2560 × 1440, 75 Hz, pied réglable en hauteur, entrées HDMI et DisplayPort."),
    ("Webcam Full HD", "Informatique", 59.90, 18,
     "1080p à 30 images par seconde, double micro, cache de confidentialité intégré."),
    ("Hub USB-C 7 ports", "Informatique", 44.90, 30,
     "HDMI 4K, trois ports USB-A, lecteur de cartes SD, recharge 100 W en passthrough."),
    ("Casque à réduction de bruit", "Audio", 149.00, 0,
     "Réduction de bruit active, 30 heures d'autonomie, pliable, étui de transport rigide."),
    ("Enceinte Bluetooth", "Audio", 59.90, 40,
     "Son à 360°, étanche IPX7, 12 heures d'autonomie, appairage de deux enceintes."),
    ("Écouteurs sans fil", "Audio", 79.90, 15,
     "Intra-auriculaires, boîtier de charge sans fil, 24 heures d'autonomie au total."),
    ("Microphone USB", "Audio", 69.00, 2,
     "Capsule cardioïde, prise casque avec retour direct, bras articulé fourni."),
    ("Lampe de bureau LED", "Maison", 34.50, 25,
     "Trois températures de couleur, variateur tactile, port USB de recharge dans le pied."),
    ("Cafetière à piston", "Maison", 24.90, 2,
     "Verre borosilicaté et inox, un litre, filtre à triple maille."),
    ("Bouilloire inox", "Maison", 39.00, 14,
     "1,7 litre, température réglable de 40 à 100 °C, maintien au chaud 30 minutes."),
    ("Plaid en laine", "Maison", 49.90, 9,
     "Laine mérinos, 130 × 170 cm, tissé au Portugal."),
    ("Sac à dos 20 L", "Voyage", 69.00, 22,
     "Compartiment ordinateur 15 pouces, tissu déperlant, dos ventilé."),
    ("Valise cabine", "Voyage", 129.00, 7,
     "55 × 40 × 20 cm, coque polycarbonate, quatre roues pivotantes, serrure TSA."),
    ("Gourde isotherme", "Voyage", 24.90, 50,
     "750 ml, garde au frais 24 heures et au chaud 12 heures, sans BPA."),
    ("Tapis de yoga", "Sport", 29.90, 33,
     "6 mm d'épaisseur, antidérapant sur les deux faces, sangle de transport."),
    ("Haltères réglables", "Sport", 119.00, 4,
     "De 2 à 24 kg chacun, réglage rapide par molette, socle de rangement."),
    ("Corde à sauter", "Sport", 14.90, 60,
     "Câble acier gainé, poignées à roulements, longueur réglable."),
    ("Bande de résistance", "Sport", 19.90, 26,
     "Lot de cinq bandes, de 5 à 40 kg de résistance, sac de rangement."),
]

# Orders of alice@example.com, so that the best sellers have a ranking: [(product name, quantity), ...] per order.
# Quantities large enough to rank above the products the smoke test orders (3 units each).
ORDERS = [
    [("Clavier mécanique TKL", 6), ("Enceinte Bluetooth", 4), ("Gourde isotherme", 8)],
    [("Clavier mécanique TKL", 4), ("Écran 27 pouces QHD", 2), ("Tapis de yoga", 5)],
    [("Enceinte Bluetooth", 5), ("Lampe de bureau LED", 3), ("Gourde isotherme", 4)],
]

# Reviews of alice@example.com on products she ordered (a review needs a confirmed order): name → (stars, comment).
REVIEWS = {
    "Clavier mécanique TKL": (5, "Frappe nette, rétroéclairage discret. Je recommande."),
    "Enceinte Bluetooth": (4, "Bon son pour la taille, l'autonomie tient la journée."),
    "Gourde isotherme": (5, None),
    "Tapis de yoga": (3, "Confortable, mais l'odeur met quelques jours à partir."),
}


def setting(name):
    value = os.environ.get(name)
    if not value and os.path.exists(".env"):
        for line in open(".env", encoding="utf-8"):
            if line.startswith(name + "="):
                value = line.split("=", 1)[1].strip()
    if not value:
        sys.exit(f"{name} is not set (environment or .env)")
    return value


def token(email):
    return subprocess.run(["./scripts/dev-token.sh", email], check=True, capture_output=True, text=True).stdout.strip()


def call(method, path, token_value, body=None, content_type="application/json"):
    """API call; returns (status, parsed JSON or None). Waits and retries when nginx rate limits uploads (429)."""
    data = json.dumps(body).encode() if content_type == "application/json" and body is not None else body
    while True:
        request = urllib.request.Request(API + path, data=data, method=method)
        request.add_header("Authorization", "Bearer " + token_value)
        if data is not None:
            request.add_header("Content-Type", content_type)
        try:
            with urllib.request.urlopen(request) as response:
                raw = response.read()
                return response.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as error:
            if error.code == 429:
                print("    upload limit reached (10 a minute): waiting 7 s")
                time.sleep(7)
                continue
            detail = error.read().decode(errors="replace")
            sys.exit(f"{method} {path}: HTTP {error.code} {detail}")


def png(width, height, light, deep, seed):
    """An abstract product visual: diagonal gradient, a soft disc and a band, in the colours of the category."""
    cx, cy = width * (0.35 + 0.3 * ((seed * 37) % 10) / 10), height * 0.48
    radius = height * (0.26 + 0.08 * ((seed * 13) % 5) / 5)
    angle = (seed * 47) % 180 * math.pi / 180
    band_dx, band_dy = math.cos(angle), math.sin(angle)
    rows = []
    for y in range(height):
        row = bytearray(b"\x00")
        for x in range(width):
            t = (x / width + y / height) / 2
            colour = [light[i] + (deep[i] - light[i]) * t * 0.35 for i in range(3)]
            if (x - cx) ** 2 + (y - cy) ** 2 < radius ** 2:
                colour = [c + (255 - c) * 0.55 for c in colour]
            if abs((x - width / 2) * band_dy - (y - height / 2) * band_dx) < height * 0.035:
                colour = [c + (d - c) * 0.6 for c, d in zip(colour, deep)]
            row += bytes(int(c) for c in colour)
        rows.append(bytes(row))

    def chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(b"".join(rows), 6)) + chunk(b"IEND", b"")


def multipart(filename, content):
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
            f"Content-Type: image/png\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
    return body, "multipart/form-data; boundary=" + boundary


def main():
    os.chdir(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    admin = token(setting("SHOP_ADMIN_EMAIL"))
    alice = token(CUSTOMER)

    print("Categories")
    _, existing = call("GET", "/categories", admin)
    category_ids = {category["name"].lower(): category["id"] for category in existing}
    for name in CATEGORIES:
        if name.lower() not in category_ids:
            _, created = call("POST", "/categories", admin, {"name": name})
            category_ids[name.lower()] = created["id"]
            print(f"  + {name}")
        else:
            print(f"  = {name}")

    print("Products and images")
    product_ids = {}
    for index, (name, category, price, stock, description) in enumerate(PRODUCTS):
        query = urllib.parse.urlencode({"name": name, "size": 100})
        _, page = call("GET", f"/products?{query}", admin)
        product = next((item for item in page["content"] if item["name"] == name), None)
        if product is None:
            _, product = call("POST", "/products", admin, {
                "name": name, "description": description, "price": price, "stock": stock,
                "categoryId": category_ids[category.lower()]})
            print(f"  + {name}")
        else:
            print(f"  = {name}")
        product_ids[name] = product["id"]
        if not product.get("imageUrl"):
            light, deep = CATEGORIES[category]
            body, content_type = multipart(f"product-{index}.png", png(800, 600, light, deep, index + 1))
            call("POST", f"/products/{product['id']}/images", admin, body, content_type)  # its main view
            print("    image")

    print("Orders (best sellers)")
    _, ranking = call("GET", "/products/top-selling?limit=4", admin)
    demo_ids = set(product_ids.values())
    if sum(1 for entry in ranking if entry["productId"] in demo_ids) >= 4:
        print("  = demo products already ranked, no order placed")
    else:
        call("DELETE", "/me/cart", alice)
        for lines in ORDERS:
            for name, quantity in lines:
                call("POST", "/me/cart/items", alice, {"productId": product_ids[name], "quantity": quantity})
            _, order = call("POST", "/me/orders", alice)
            print(f"  + order {order['id']} of {CUSTOMER}")

    print(f"Reviews of {CUSTOMER}")
    for name, (stars, comment) in REVIEWS.items():
        product_id = product_ids[name]
        for _ in range(40):  # the orders are confirmed in the background, after their commit
            _, mine = call("GET", f"/me/products/{product_id}/review", alice)
            if mine["allowed"]:
                break
            time.sleep(0.25)
        if mine.get("review"):
            print(f"  = {name}")
        elif mine["allowed"]:
            call("PUT", f"/me/products/{product_id}/review", alice, {"rating": stars, "comment": comment})
            print(f"  + {name}: {stars} stars")
        else:
            print(f"  ! {name}: no confirmed order of it, no review")

    print(f"Done: {BASE} (the best sellers are cached 5 minutes by the API)")


if __name__ == "__main__":
    main()
