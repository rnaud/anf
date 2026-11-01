# anf.io

Arnaud’s personal website: **[anf.io](https://anf.io/)**.

The site uses plain HTML and CSS, local WebP artwork, and a subtle Three.js code-wave background. No installation or build step is required. All runtime assets are served locally; there are no analytics or cookies.

`background.js` renders a grayscale glyph field with pointer parallax. It caps rendering at 30 fps, pauses in hidden tabs, respects reduced-motion preferences, and leaves the content usable without WebGL. Three.js 0.187.0 and its MIT license are vendored in `assets/vendor/three/`.

## Pages

- `/` — minimal introduction.
- `/about/` — GitHub and LinkedIn links.
- `/stuff/` — the seven-project collection.

All pages share `styles.css` and the Home / About / Stuff navigation.


## Projects

| Project | Destination |
| --- | --- |
| feels | https://rnaud.github.io/feels/ |
| Hiraeth | https://hiraeth.my/ |
| OpenKart | https://rnaud.github.io/openkart/ |
| REZ: HYRULE | https://rnaud.github.io/rez-hyrule/ |
| Little Worlds | https://rnaud.github.io/little-worlds/ |
| Alexandria | https://alexandria.my/ |
| Herochess | https://rnaud.github.io/herochess/ |

Edit the project links, text, and accessibility labels in `stuff/index.html`. Visual styling and responsive breakpoints live in `styles.css`.

## Preview

```sh
python3 -m http.server 8766 --bind 127.0.0.1
```

Open http://127.0.0.1:8766/. Check desktop, tablet, and phone layouts, keyboard focus, and the seven outgoing project links before publishing.

## Publish

GitHub Pages serves the repository’s **`master` branch, `/` root**. Push a commit to `master` to publish. `CNAME` retains the custom domain and `.nojekyll` keeps the site a static deployment. The historical `gh-pages` branch is retained as an archive of the previous site.

```sh
git add index.html about stuff styles.css assets
git commit -m "Update the project collection"
git push origin master
```

Check deployment at https://github.com/rnaud/anf/actions and domain settings at https://github.com/rnaud/anf/settings/pages.

## Domain

DNS is managed with **Gandi LiveDNS**. The web records are:

| Name | Type | Values | TTL |
| --- | --- | --- | --- |
| `@` | `A` | `185.199.108.153`, `185.199.109.153`, `185.199.110.153`, `185.199.111.153` | 300 |
| `@` | `AAAA` | `2606:50c0:8000::153`, `2606:50c0:8001::153`, `2606:50c0:8002::153`, `2606:50c0:8003::153` | 300 |
| `www` | `CNAME` | `rnaud.github.io.` | 300 |

GitHub Pages owns TLS certificate issuance and redirects `www.anf.io` to `anf.io`. Keep Enforce HTTPS enabled in Pages settings. Existing mail, SPF, and other subdomain records are independent of this website and were preserved. The pre-change Gandi backup is the manual snapshot **2026-10-31 #1**, verified in Gandi’s restore list.

See [GitHub’s custom-domain documentation](https://docs.github.com/en/pages/configuring-a-custom-domain-for-your-github-pages-site/managing-a-custom-domain-for-your-github-pages-site) for current DNS values.

## Artwork

The thumbnails reuse artwork from the projects, resized and encoded locally:

- `feels.webp`: feels’ selected home illustration (`public/references/midjourney/home.png`).
- `hiraeth.webp`: Hiraeth’s desert title artwork (`references/Title Screen/A4-desert.jpg`).
- `openkart-nyc.webp`: OpenKart’s New York Rush track reference (`assets/levels/newyork.jpg`).
- `rez-hyrule-gameplay.webp`: REZ: HYRULE’s gameplay screenshot (`screenshots/gameplay.png`).
- `little-worlds.webp`: the Banana Meadows cartridge preview from the live Little Worlds arcade, preserved at its native pixel-art resolution.
- `alexandria.webp`: Alexandria’s library screenshot (`website/public/alexandria-library.png`).
- `herochess.webp`: Herochess’s implemented Arcana Table interface (`art-direction/interface-implemented-v3.png`).
- `social-intro.jpg`: a minimal sharing card for the Home and About pages.
- `social.jpg`: a 1200 × 630 sharing card composed from this site’s typography and the six thumbnails.

The first three images are project artwork, not gameplay screenshots. REZ: HYRULE is an unofficial fan tribute, unaffiliated with Nintendo or Sega.
