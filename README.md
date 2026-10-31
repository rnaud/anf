# anf.io

Arnaud’s collection of playable worlds: **[anf.io](https://anf.io/)**.

The site is plain HTML and CSS, with local WebP artwork. No installation, build step, JavaScript, analytics, cookies, or third-party runtime resources are required.

## Projects

| Project | Destination |
| --- | --- |
| feels | https://rnaud.github.io/feels/ |
| Hiraeth | https://hiraeth.my/ |
| OpenKart | https://rnaud.github.io/openkart/ |
| REZ: HYRULE | https://rnaud.github.io/rez-hyrule/ |

Edit the project links, text, and accessibility labels in `index.html`. Visual styling and responsive breakpoints live in `styles.css`.

## Preview

```sh
python3 -m http.server 8766 --bind 127.0.0.1
```

Open http://127.0.0.1:8766/. Check desktop, tablet, and phone layouts, keyboard focus, and the four outgoing project links before publishing.

## Publish

GitHub Pages serves the repository’s **`master` branch, `/` root**. Push a commit to `master` to publish. `CNAME` retains the custom domain and `.nojekyll` keeps the site a static deployment. The historical `gh-pages` branch is retained as an archive of the previous site.

```sh
git add index.html styles.css assets
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
- `openkart.webp`: OpenKart’s garden menu artwork (`assets/ui/storybook-garden.webp`).
- `rez-hyrule.webp`: REZ: HYRULE’s title screenshot (`screenshots/title.png`).
- `social.jpg`: a 1200 × 630 sharing card composed from this site’s typography and the four thumbnails.

The first three images are project artwork, not gameplay screenshots. REZ: HYRULE is an unofficial fan tribute, unaffiliated with Nintendo or Sega.
