# Product images

Each product's photograph was generated on 2026-10-16 with
[Z-Image-Turbo](https://huggingface.co/Tongyi-MAI/Z-Image-Turbo) (Apache-2.0), run locally on an
Apple M4 Pro through [mflux](https://github.com/filipstrand/mflux) 0.23.0, 8-bit, 1024 x 1024, then
saved as 800 x 800 JPEG at quality 82. No photograph shows a real person, place, or any brand but Morpho Market's own.

To regenerate one:

```sh
pip install mflux==0.23.0
mflux-generate-z-image-turbo --quantize 8 --width 1024 --height 1024 --seed <seed> --prompt "<prompt>" --output product.png
sips -s format jpeg -s formatOptions 82 -Z 800 product.png --out product_<id>.jpg   # macOS
```

Copy the JPEG to both `android/ui/src/main/res/drawable-nodpi/` and `ios/MorphoMarket/Products/`.
The same seed and prompt give the same image only with the same model, quantization and mflux version.

Text on a product was read on every image before it was kept. The mug and the magnet took
several seeds to spell their slogans; no seed spelled the tumbler's `no sleep()`, so it carries
the butterfly alone.

| id | seed | prompt |
|---|---|---|
| `tee` | 7 | Professional e-commerce product photograph of a folded heather-white cotton t-shirt with a single bold blue morpho butterfly emblem printed on the chest, centered, soft studio lighting, seamless light gray background, gentle shadow, detailed fabric texture, photorealistic |
| `hoodie` | 7 | Professional e-commerce product photograph of a heavyweight indigo navy pullover hoodie laid flat, front chest printed in clean white sans-serif text "MAP BEFORE YOU TAP" under a small blue morpho butterfly emblem, kangaroo pocket, drawstrings, soft studio lighting, seamless light gray background, gentle shadow, photorealistic |
| `hat` | 7 | Professional e-commerce product photograph of a teal cotton dad hat baseball cap with a small embroidered blue morpho butterfly on the front panel, curved brim, three-quarter view, adjustable strap visible, soft studio lighting, seamless light gray background, gentle shadow, photorealistic |
| `beanie` | 7 | Professional e-commerce product photograph of a charcoal gray ribbed knit beanie with a folded cuff and a small woven label with a blue morpho butterfly, standing upright, soft studio lighting, seamless light gray background, gentle shadow, detailed knit texture, photorealistic |
| `socks` | 7 | Professional e-commerce product photograph of a pair of purple crew socks with a repeating small blue morpho butterfly pattern laid flat side by side, cotton knit texture, soft studio lighting, seamless light gray background, gentle shadow, top-down view, photorealistic |
| `mug` | 42 | Professional e-commerce product photograph of a glossy white ceramic coffee mug with a deep blue handle and blue interior, the mug is printed with exactly three words "WAIT FOR IT" on one line in bold blue capital letters, a small blue butterfly below the text, three-quarter view, soft studio lighting, seamless light gray background, photorealistic |
| `tumbler` | 7 | Professional e-commerce product photograph of a matte dark teal stainless steel insulated travel tumbler with a clear slide lid and a brushed steel rim, a single small blue morpho butterfly emblem laser engraved on the front, no text, standing upright, soft studio lighting, seamless light gray background, gentle reflection, photorealistic |
| `case` | 7 | Professional e-commerce product photograph of a deep purple matte smartphone case seen from the back, with a small embossed blue morpho butterfly and the small text "REAL DEVICE VERIFIED" near the bottom, camera cutout, slight angle, soft studio lighting, seamless light gray background, gentle shadow, photorealistic |
| `tote` | 7 | Professional e-commerce product photograph of a forest green heavy canvas tote bag standing upright, printed in white text "LEAVE NOTHING ON THE DEVICE" with a small butterfly emblem, sturdy straps, soft studio lighting, seamless light gray background, gentle shadow, detailed canvas texture, photorealistic |
| `magnet` | 5 | Professional e-commerce product photograph of a small rectangular glossy white fridge magnet on a white refrigerator door, printed with the slogan WORKS ON MY EMULATOR in bold navy capital letters, the word EMULATOR spelled E-M-U-L-A-T-O-R, a small blue morpho butterfly in the corner, close-up, soft natural lighting, photorealistic |
| `pins` | 7 | Professional e-commerce product photograph of a set of three hard enamel pins on a light gray backing card: a blue morpho butterfly pin, a round pin reading "@e1", and a small pin shaped like a phone, gold metal outlines, glossy enamel, soft studio lighting, top-down view, photorealistic |
| `stickers` | 7 | Professional e-commerce product photograph of a pack of die-cut vinyl laptop stickers spread out on a light gray surface: a blue morpho butterfly, a phone outline, a checkmark badge, a small terminal window, and one reading "CHALLENGES", matte finish with white borders, soft studio lighting, top-down view, photorealistic |
| `giftcard` | 7 | Professional e-commerce product photograph of a premium plastic gift card with a deep blue gradient and an iridescent blue morpho butterfly, the words "Morpho Market" and "GIFT CARD" in clean white type, resting at a slight angle on a light gray surface, soft studio lighting, gentle shadow, photorealistic |
