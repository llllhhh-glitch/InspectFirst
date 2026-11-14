# Product catalogue

This directory contains the initial product research, guide content, and import tools. Administrator APIs write the data to the guide service; MySQL serves it at runtime. JSON files support generation, import, and comparison.

## Coverage

The catalogue has 45 products across nine categories. The existing Sony a6400 retains three checks; 44 imported products have four checks each, for 179 checks in total.

| Category | Products |
| --- | --- |
| Phones | iPhone 13, 14, 15; Galaxy S23, S24 |
| Tablets | iPad 9 Wi-Fi, iPad 10 Wi-Fi, iPad Air 5 Wi-Fi, Galaxy Tab S8 Wi-Fi, Tab S9 Wi-Fi |
| Headphones | AirPods 3, AirPods Pro 2 USB-C, Sony WH-1000XM4, WH-1000XM5, Bose QC45 |
| Computers | MacBook Air M1 2020, Air M2 2022, Pro 14-inch 2021, Dell Latitude 5420, ThinkPad T14 Gen 2 Intel |
| Furniture | IKEA BILLY, KALLAX, POANG armchair frame, MICKE 105×50 desk, LACK 55×55 table |
| Luxury | LV Neverfull MM M41178, Speedy Bandouliere 25 M46977, CHANEL Classic 11.12 lambskin, Gucci GG Marmont small camera bag, Prada Re-Edition 2005 Re-Nylon |
| Cameras | Sony a6400, Canon EOS M50 Mark II, Nikon Z 50, FUJIFILM X-T30 II, Sony ZV-E10 |
| Monitors | Dell U2720Q, LG 27UL500-W, ASUS VG249Q, Samsung S24R350, BenQ GW2480 |
| Consoles | PS5 CFI-1200 disc, Xbox Series X 1TB disc, Series S 512GB, Switch OLED, Switch HAC-001(-01) |

## Files and data flow

| File | Purpose |
| --- | --- |
| build_catalog.py | Content-generation code producing catalog.json and sources.json |
| catalog.json | Categories, products, and guide-draft request data |
| sources.json | 55 deduplicated references with scope and retrieval notes |
| import_catalog.py | File validation, administrator API import, and public API comparison |
| verification.json | Product/version IDs and results from a verification run |
| image-selections.json | Selected product reference photos |
| download_images.py | Photo download and validation; frontend image mapping and attribution |

```text
build_catalog.py → catalog.json / sources.json
                             ↓ import_catalog.py
category → product → guide draft → checks and references → publication → MySQL
```

`categorySlug` is import metadata resolved to the API's `categoryId`. Draft saves replace `expectedVersion` with the current server edit version. See [backend/API.md](../../backend/API.md) for request contracts.

## Import and verification

Start the identity and guide services and the frontend proxy, then run from this directory:

```sh
python3 import_catalog.py             # Validate files without API writes
python3 import_catalog.py --apply     # Create and publish catalogue records
python3 import_catalog.py --verify    # Compare public records with source data
```

The default API entry point is `http://127.0.0.1:5173`; use `--base-url` to override it. The importer reads private local administrator configuration, logs in with cookies, and obtains CSRF tokens for writes. Progress is stored in `.local/catalog-2026-11-manifest.json`.

Repeated imports skip matching published records rather than overwriting them. If administrators have changed a guide, reconcile its current version and import state before applying older JSON. Routine content changes can be made directly through a new draft in the management UI.

## Product photos

Photos are stored under `frontend/public/images/products/`. `frontend/src/data/product-images.json` maps models to assets, and `ProductVisual.vue` renders them. Products without a configured photo display a category icon.

`download_images.py` uses the selection records, checks response types, sizes, duplicate hashes, and image decoding when available. Filenames include source-URL hashes. Attribution is written to `frontend/public/images/SOURCES.md`. Photos illustrate product models, not a seller's item; rights remain with their owners.

Check illustrations use a separate pipeline described in [check-examples/README.md](../check-examples/README.md).

## Content maintenance

Each check has steps, expected observations, abnormal signs, limitations, and references. Manufacturer material establishes model features, controls, interfaces, or assembly instructions. Used-condition observations are editorial checks rather than manufacturer certification criteria.

References mainly use manufacturer documentation, with limited specialist resale material. `sources.json` records their scope and retrieval limitations. No Xiaohongshu posts are cited, and these products were not individually tested. Some model codes are catalogue identifiers, such as IPHONE-13; regional variants, storage, and accessories must be checked against the actual item.

New products can first be created in the management UI and later added to these files if reproducible import is needed. Review generated JSON, references, and images before importing new versions. Retain old versions referenced by inspection history.

Luxury checks record condition, version consistency, and provenance clues without a genuine/fake verdict. Furniture checks do not certify structural safety. Unperformed checks remain unchecked.
