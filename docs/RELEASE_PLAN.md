# Release plan (drafted 2026-11-22; target: a couple of months out)

The aim is to make Cindercast available to everyone: first as signed APKs on GitHub, then in the Amazon Appstore
(the store a Fire TV uses) and, optionally, Google Play (Android TV and Google TV devices). Nothing here is urgent;
it lists what has to be decided or built so the date can be met.

## Updates while it is only an APK
- **In-app update check (opt-in, visible in Settings).** Asks the GitHub Releases page for the latest version number,
  compares it with the installed one, downloads the signed APK and hands it to Android's installer. The viewer presses
  Install on the system prompt; there is no silent update without root. Needs the "install unknown apps" permission for
  Cindercast (Fire OS asks once). The only server contacted is GitHub; nothing about the viewer is sent.
- **Fallback:** the Downloader app, as most Fire TV sideloaders use, pointed at the same release file.
- **Signing key.** Android only installs an update signed with the same key as the installed app. Today's APKs are
  debug-signed. Before the first public release: create one release key, store it and a backup outside the repository
  and outside any synced folder, sign every release with it, and never lose it. Changing the key later forces everyone to
  uninstall (and lose their data). If Google Play is used, enrol in Play App Signing so Google holds the distribution key.
- Versioning: a rising `versionCode` per release and a readable `versionName`; the update check compares `versionCode`.

## Stores
| | Amazon Appstore | Google Play |
|---|---|---|
| Devices | Fire TV (our Fire TV Stick 4K Plus) | Android TV and Google TV; not Fire TV |
| Account | free developer account | one-time fee (about $25); a new personal account must run a closed test with about 12 testers for 14 days first |
| Updates | store handles them | store handles them |
| Review | a few days | a few days |
Both stores have accepted IPTV player apps that bundle no channels; Cindercast bundles none. Wording to keep careful in
the listing: the portal address and the registered MAC address are the viewer's own, the content is the viewer's own
subscription, no provider is named. A rejection is possible, so the GitHub APK stays as the fallback channel.

## Needed before any release (store or GitHub)
1. Release build: code shrinking on, no debug sources (the debug logging, health sampler and probe screens are already
   debug-only), release signing config reading the key from outside the repository.
2. **Decision: plain-HTTP portals.** Release builds cannot reach an HTTP-only provider today (cleartext is allowed in debug
   only) and most providers use HTTP. Usual answer: allow cleartext only for the address the viewer typed (a network
   security config cannot name it ahead of time, so this needs a custom check or a base-config allowance with the warning the
   setup screen already shows). Owner to choose.
3. Privacy policy page (short: nothing leaves the device except requests to the viewer's own portal, plus the optional
   update check to GitHub; stored sealed on the device). Host it in the repository's GitHub Pages or README.
4. Store assets: icon, 1280x720 banner for the Fire TV launcher, screenshots (1920x1080), short and long description,
   content rating questionnaire, support contact.
5. Fire TV and Android TV manifest checks: leanback launcher category, TV banner, no touchscreen requirement, 32-bit ABI,
   and no permissions beyond network (plus the install permission if the update check ships).
6. Open items in `docs/TODO.md` that affect strangers: first-run guidance without the author's setup, the Recovering and
   memory work verified over long real sessions, the decoder-failure investigation, crash capture in Settings.
7. Licence and credits review (MIT, decided 2026-11-21), a third-party notices screen for libraries, and the provider-specific
   documents kept out of the public repository (already scrubbed once; recheck at release).
8. Name: Cindercast (decided 2026-11-25). The first name, OpenStream, was dropped because an unrelated music-streaming project on
   GitHub (openstreamorg) already uses it; Fireside was dropped because a streaming app of that name (Fireside Chat Inc., with a
   USPTO filing for app software, reported to be coming to Fire TV) is in the same field. For Cindercast only web searches were
   done (nothing found under the exact name; a registered CINECAST mark for streaming services sounds similar). The USPTO and EUIPO
   registers still have to be searched before store submission.

## Decisions made (2026-11-22, by the assistant on the owner's instruction)
- **Cleartext:** allowed in release builds through a base network security config (`app/src/release`), TLS validation untouched
  (system certificates only). Reason: most portals are plain HTTP and the address is typed by the viewer. The setup screen's warning stays.
  Verified on the Fire TV: a signed release build connected to a plain-HTTP portal and browsed Live TV, Movies and Shows.
- **Signing key:** created 2026-11-22 (RSA 4096, 30-year validity, alias `openstream`) in `~/.config/firetv-iptv/release/`
  (outside the repo and Google Drive; `release.properties` holds the passwords, chmod 600). **Owner action: make an offline
  copy of that whole folder (for example an encrypted USB stick) now.** Losing it means every install must be removed before an update.
  `scripts/build_release.sh` builds, tests, signs and verifies; a clone without the key builds an unsigned APK.
- **Release build:** code and resource shrinking on, 32-bit ABI only, versionName 0.1.1 / versionCode 2 as of 2026-11-25 (versionCode rises each release; 0.1.0 was code 1). About 2.5 MB.
- **In-app update check: not in 0.1.** It needs the "install unknown apps" permission, which store builds should not carry.
  GitHub Releases plus the Downloader app is the sideload path; stores update themselves. Revisit for a sideload-only flavour.
- **Order of stores:** Amazon Appstore first (it is the Fire TV store), Google Play later if wanted. Publisher: an individual account.
- **Plain HTTP and Amazon review:** if the store objects to cleartext, answer that the address is user-supplied; the GitHub APK is the fallback.
- **Documents:** `docs/PRIVACY.md`, `docs/THIRD_PARTY_NOTICES.md`, `docs/STORE_LISTING.md` drafted (placeholder `<REPO_URL>` to fill).
- **Status 2026-11-25:** signing key, signed release build, cleartext policy, privacy policy, notices and store text are done; the app is on `main` and CI builds and tests every push.
- **Still open before submission:** trademark search for "Cindercast", store artwork and screenshots, IMDb's licensing reply
  (IMDb scores should be off in a public build unless licensed), hosting the privacy policy, a fresh scan of docs for provider detail.

## Rough timeline
1. Now to about 4 weeks: finish features, soak tests on the real portal, read the health log over long sessions.
2. About week 5: release key, release build, in-app update check, first signed GitHub Release.
3. Weeks 5 to 8: Amazon Appstore submission; start the Google Play closed test in parallel if wanted.

## Open questions for the owner
- Cleartext policy for HTTP portals (item 2 above).
- Ship the in-app update check, or rely on stores and Downloader?
- Google Play as well as Amazon, or Amazon only to begin with?
- Who is the publisher name on the stores (an individual or an organisation account)?
