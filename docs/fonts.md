# Шрифты и иконки

Все шрифты лежат в `src/main/resources/static/fonts` и подключаются через `static/css/fonts.css`.
С Google Fonts ничего не загружается: сайт одинаково выглядит без интернета и там, где Google Fonts медленный или заблокирован.

| Шрифт | Где используется | Лицензия |
|---|---|---|
| Inter 400–700 (латиница + кириллица) | основной текст | SIL OFL 1.1 |
| Tinos 400, 700 | заголовки, подписи полей, кнопки, меню | SIL OFL 1.1 |
| Plus Jakarta Sans 600, 700 | логотип | SIL OFL 1.1 |
| Space Grotesk 600, 700 | цифры | SIL OFL 1.1 |
| Material Symbols Outlined | иконки | Apache 2.0 |

## Иконки

Иконка пишется названием: `<span class="material-symbols-outlined">person</span>`.
Список названий — https://fonts.google.com/icons (стиль Outlined).

Чтобы файл был маленьким (≈12 КБ вместо 4 МБ), в шрифте оставлены **только нужные иконки**.
Если на странице вместо иконки пусто — её нет в шрифте. Как добавить:

1. Скачать полный шрифт: `npm pack material-symbols` и распаковать (`tar xzf material-symbols-*.tgz`).
2. Установить fontTools: `pip install fonttools brotli`.
3. Дописать название в `ICONS` ниже и запустить скрипт из папки, где лежит `package/`:

```python
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ICONS = """add add_circle alternate_email archive arrow_back arrow_forward badge bakery_dining block cake
calendar_today call check check_circle chevron_left chevron_right close content_copy currency_exchange
dashboard delete devices done_all eco edit error event expand_more group help history home hourglass_top
image info inventory_2 key local_cafe local_shipping location_on lock lock_open lock_reset login logout
mail map menu notifications nutrition password pause payments percent person phone_iphone photo_camera
play_arrow priority_high qr_code qr_code_scanner receipt_long refresh remove replay rocket_launch save
schedule search sell set_meal shield shield_lock shopping_bag soup_kitchen store storefront sync_alt
task_alt timer unfold_more upload verified visibility visibility_off warning""".split()

src = TTFont("package/material-symbols-outlined.woff2", lazy=False)
cmap = src.getBestCmap()
rev = {g: chr(c) for c, g in cmap.items()}
wanted = {}
for lookup in src["GSUB"].table.LookupList.Lookup:
    for st in lookup.SubTable:
        st = st.ExtSubTable if st.LookupType == 7 else st
        for first, ligs in getattr(st, "ligatures", {}).items():
            for lig in ligs:
                name = rev.get(first, "") + "".join(rev.get(c, "?") for c in lig.Component)
                if name in ICONS:
                    wanted[name] = lig.LigGlyph
print("не найдены:", sorted(set(ICONS) - set(wanted)))

font = instancer.instantiateVariableFont(src, {"wght": 400, "GRAD": 0, "opsz": 24})  # ось FILL оставляем
font.flavor = None
font.save("pinned.ttf")
font = TTFont("pinned.ttf", lazy=False)
cmap = font.getBestCmap()
letters = [cmap[ord(ch)] for ch in set("".join(ICONS)) | {"_", " "} if ord(ch) in cmap]
options = subset.Options()
options.flavor = "woff2"
options.layout_features = ["*"]
options.layout_closure = False
options.notdef_outline = True
options.name_IDs = ["*"]
subsetter = subset.Subsetter(options)
subsetter.populate(glyphs=letters + list(wanted.values()) + [".notdef"])
subsetter.subset(font)
font.flavor = "woff2"
font.save("material-symbols-outlined.woff2")   # положить в src/main/resources/static/fonts/
```
