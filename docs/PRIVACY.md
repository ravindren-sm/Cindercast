# Cindercast privacy policy (draft, 2026-11-22)

Cindercast is a player for the viewer's own Stalker/Ministra IPTV subscription. It has no account, no analytics, no advertising
and no server of its own.

## What stays on the device
- The portal address and the registered MAC address you enter, sealed with a key held by Android (AES-256-GCM, Android Keystore).
- Profiles, the household PIN (stored only as a salted hash), favorites, watch history, resume positions, viewing statistics,
  the saved title list used for search and sorting, and the optional online tags.
- Only the portal address and MAC are encrypted. The rest is stored as is in the app's private storage, which other apps
  cannot read. That includes the portal's play commands for channels and titles kept in the history, favorites, saved
  title list and saved NFL listings; some portals put a host name or a token inside those commands.
- Nothing is backed up to the cloud (`allowBackup` is off). Uninstalling the app deletes it all.

## What leaves the device
| To | What | When |
|---|---|---|
| Your portal and its stream servers | The requests a Stalker client makes: your MAC address, the channels and titles you open | Always; this is the service you subscribed to |
| themoviedb.org (TMDB) | Title names and years, with the API key you entered | Only if you add a TMDB key (Settings, Online tags) |
| datasets.imdbws.com (IMDb) | A download request for IMDb's public ratings file; nothing about you or your titles | Only when you press Download (Settings, IMDb ratings) |

Nothing else is sent. There is no crash reporting and no remote logging. Debug builds can write diagnostics to a local file
with portal addresses, MAC addresses and tokens redacted; release builds do not.

## Plain-HTTP portals
Most IPTV portals use plain HTTP, so traffic to them is not encrypted. Cindercast allows that because the address is yours to
choose, and warns on the setup screen. HTTPS connections are validated normally against Android's system certificates.

## Children
Profiles can be restricted by age label, word filters, allowed categories and a household PIN. These are aids for supervised
viewing, not a guarantee; unlabelled titles are blocked for Kids profiles by default.

## Content
Cindercast bundles no channels, films or shows and does not provide any. Content comes from your own provider.

## Contact
Open an issue on the project's GitHub page: <REPO_URL>.
