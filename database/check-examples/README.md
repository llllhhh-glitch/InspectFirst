# Check illustrations

Each guide check can reference images. The current 45 public product guides have 179 checks, each with one original SVG illustration. Images explain an observation or method and are labelled `ILLUSTRATION / Schematic`; they are not photographs of actual defects.

## Storage and rendering

```text
SVG generation → frontend/public/images/checks/
image metadata → administrator guide API → check_item_image
public guide or inspection snapshot → CheckExamples.vue → thumbnail / zoom dialog
```

The frontend serves image files as static assets. MySQL stores URLs, alternative text, captions, types, and reference metadata. `check_item_image` links to a check in a specific version rather than directly to a product.

Existing installations add the table with `database/06-check-images.sql`. Fresh initialization schemas already include it.

## API fields

The `images` field on a guide check has this structure:

```json
{
  "images": [{
    "url": "/images/checks/example-contenthash.svg",
    "alt": "Describe the inspection location or observation shown",
    "caption": "Explain what the user should compare or do",
    "kind": "DIAGRAM",
    "sourceUrl": "https://support.example.com/manual",
    "sourceTitle": "Reference manual"
  }]
}
```

`sourceUrl` supports the written check; it does not mean that the referenced organization created or endorsed the illustration. `alt` describes the image for accessibility and loading failures. `caption` accompanies the image.

[CheckExamples.vue](../../frontend/src/CheckExamples.vue) renders images within expanded checks. Clicking opens a native dialog with Escape dismissal, focus restoration, and a loading-error fallback.

## Generation, import, and verification

| File | Purpose |
| --- | --- |
| before-examples.json | Guide snapshot taken before illustrations, used for generation and comparison |
| build_examples.py | Generate SVG assets and image metadata by check topic |
| examples.json | Products, original check IDs, topics, and asset metadata |
| import_examples.py | Create and publish illustrated guide versions through administrator APIs |
| verify_examples.py | Check coverage, public/version APIs, content hashes, and retained versions |
| verification.json | Output from a verification run |

Run from this directory:

```sh
python3 build_examples.py
python3 import_examples.py
python3 verify_examples.py
```

Generation writes local files. Import writes and publishes guide versions, so review generated images and current guide contents first. Verification reads service data and assets for comparison. Import and verification require running services and local configuration.

The importer uses administrator cookies and CSRF tokens. Progress is stored in `.local/check-examples-import.json`, allowing repeated runs to recognize imported versions. Deliberately revised images require `--revise`. If another operation has published a newer version, reconcile the difference before continuing.

## Version behavior

Publishing creates new checks and image associations while retaining old versions and assets. SVG filenames contain content hashes, so changed artwork receives a new URL. New inspection snapshots include image metadata; existing inspections are not updated when a guide changes.

The generator reads the saved guide snapshot rather than current service data. After editing guide text in the management UI, update and review the snapshot and drawing logic before importing another version.

## Verification scope

The verifier checks metadata coverage, SVG responses and XML, content hashes, script and foreignObject elements, and the original 45 versions' check IDs and text.

An image usually illustrates one main observation within a multi-step check. Sound, long-term battery behavior, and authenticity still require actual inspection and written guidance; they cannot be established by a schematic.
