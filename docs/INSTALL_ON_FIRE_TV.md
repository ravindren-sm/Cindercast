# Installing OpenStream on a Fire TV (from an APK)

OpenStream is a Stalker / Ministra (MAG) client for the Fire TV. It does **not** include any channels, movies or shows. To use it
you need your own subscription and the two things your provider gives you: a **portal address** and the **MAC address**
registered for your device. Use it only with a service you are entitled to.

**What the file is:** `OpenStream-0.1.1.apk`, version 0.1.1 (build 2), about 2.7 MB, signed with the project's release key.

SHA-256 (check that your copy is the same file):

```text
26acb01cf3182accd5b61ee2b0dff7025cdf5d57048c74cd3078fa03c2cc3212
```

On a Mac or Linux: `shasum -a 256 OpenStream-0.1.1.apk`. On Windows (PowerShell): `Get-FileHash OpenStream-0.1.1.apk`.

## What you need

- A Fire TV Stick or Fire TV (Fire OS 6 or newer; tested on a Fire TV Stick 4K Plus, Fire OS 8).
- **Apps from Unknown Sources** allowed for the app you install with (for example Downloader). Fire TV: Settings >
  My Fire TV > Developer options > Install unknown apps.
- Your portal address (often ending in `/c/` or `/stalker_portal/c/`) and your registered MAC address.
- About 50 MB of free space. The optional title index (Settings) uses more, around 20 MB.

## Option A: with the Downloader app (no computer needed)

1. Install **Downloader** (orange icon) from the Amazon Appstore on the Fire TV, open it, and allow it to access files if asked.
2. In Downloader's address box, type the link you were sent for `OpenStream-0.1.1.apk` and choose Go. (A link from Dropbox
   must end in `?dl=1`; a Google Drive link must be a direct-download link. If the link does not download, ask whoever sent
   it for another.)
3. When the download finishes, choose **Install**, then **Done** (not Open yet), then **Delete** to remove the file.

## Option B: from a computer with ADB

1. On the Fire TV: Settings > My Fire TV > Developer options > turn on **ADB debugging**. Note the TV's IP address
   (Settings > My Fire TV > About > Network).
2. On your computer (same network), with [Android platform-tools](https://developer.android.com/tools/releases/platform-tools)
   installed:

   ```bash
   adb connect <FIRETV_IP>:5555
   adb install OpenStream-0.1.1.apk
   ```

3. Accept the **Allow USB debugging?** prompt on the TV (tick "Always allow"). Run the install again if it asked first.
4. Turn ADB debugging off again when you are done (optional, recommended).

## First run

1. Open **OpenStream**. It is not on the main Home row: press the Home button, scroll to **Your Apps & Channels**, and
   choose **See all** (or hold the Home button and pick Apps). Its tile may show a plain navy block on some Fire OS versions;
   that is cosmetic.
2. The setup screen asks for the **portal address** and the **MAC address**. Both are stored on the device only, encrypted
   with a key held by Android. Choose "Skip for now" to look around without a portal.
3. **Most providers allow only one connection per MAC address.** Close every other app that uses the same subscription
   (another IPTV player, a set-top box) before you connect, and use your own MAC, not someone else's.
4. Optional, Settings: build the **title index** (a few minutes to an hour, only while the app is open) for instant search,
   sorting and saved searches; add profiles and a household PIN.

## Coming from StbEmu, TiviMate or another IPTV app

OpenStream cannot read another app's settings, and it never stops, changes or removes another app. You move the two values over
by hand, once:

1. In the old app (StbEmu, TiviMate or similar), open its profile or portal settings and write down the **portal address**
   (also called server or portal URL) and the **MAC address** it uses (in StbEmu it is the STB MAC). Labels differ by version.
2. In OpenStream's setup screen (or Settings > Portal > Change portal) type the same two values. Your provider recognises the
   MAC, so reusing it is what makes the subscription work.
3. **Stop the old app yourself before you connect.** Most providers allow one connection per MAC, and an app that is closed
   with Back often keeps running in the background. On the Fire TV: Settings > Applications > Manage installed applications >
   (the old app) > **Force stop**. Do this each time you switch between the two, or the provider may refuse one of them.
4. You can keep the old app installed as a fallback, but only run one of them at a time.

## The remote

| Key | Does |
|---|---|
| Up / Down in a live channel | next / previous channel (also `]` / `[` or the channel keys) |
| Left in a live channel | list of channels over the picture |
| Select / Play-Pause (or `P`) | pause and resume |
| Left / Right in a movie or episode | skip (hold to speed up); Rewind / Fast-forward keys skip 30 s |
| Menu (≡) | audio and subtitles; on a channel or title in lists: favourite and other options |
| Back | closes things one step at a time; on Home it leaves the app |

## Updating and removing

- **Update:** install a newer `OpenStream-x.y.z.apk` the same way; it installs over the old one and keeps your settings,
  profiles and favourites, as long as it comes from the same signer.
- **If you built the app yourself** it is signed with your own debug key, so it cannot be installed over this release (or the
  other way round). Remove one first (**Settings > Applications > Manage installed applications > OpenStream > Uninstall**);
  that deletes its data.
- **Remove:** the same menu, Uninstall.

## Good to know

- The app's window is hidden from screenshots and screen recording by design.
- Plain-HTTP portals are common. The app allows them and warns on the setup screen; HTTPS certificates are always checked.
- Nothing is sent anywhere except to your portal and, only if you add a key in Settings, title names to themoviedb.org.
  See [PRIVACY.md](PRIVACY.md).
- Found a bug? Open an issue at <https://github.com/ravindren-sm/OpenStream/issues> and say what you pressed. Never include
  your portal address, MAC address or any logs that show them.
