# PDF report font

The report service embeds `NotoSansSC.ttf` for English and simplified Chinese PDF output. The font is packaged in the service JAR and does not require installation on the host.

## Code path

[PdfExport.java](../../java/com/inspectfirst/report/PdfExport.java) reads `/fonts/NotoSansSC.ttf` from the classpath and loads it with PDFBox's `PDType0Font.load`. The font is used for headings, body text, and footers. A missing file causes export to fail. Unsupported characters are represented as `[U+XXXX]` code-point labels rather than omitted.

Character widths determine wrapping, so replacing the font can change pagination. Check English and Chinese text, long lines, page breaks, and unsupported characters after replacement.

## Source and license

The font comes from [Noto Sans SC in Google Fonts](https://github.com/google/fonts/tree/main/ofl/notosanssc). The bundled file was instantiated at weight 400 from the upstream variable TrueType font using fontTools.

`OFL.txt` contains the SIL Open Font License 1.1, with Source listed as a reserved font name. Retain the license when distributing the font. Check embedding and redistribution terms before substituting another font.
