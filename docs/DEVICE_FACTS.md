# Device facts (Fire TV Stick)

Recorded from read-only ADB queries. No serials, no IP addresses, no MAC addresses.

| Property | Value | Source |
|---|---|---|
| Manufacturer | Amazon | ro.product.manufacturer |
| Model | AFTMA08C15 | ro.product.model |
| Fire OS version | 8.1.8.5 (RS8185/3898); fireos 8.0 | ro.build.version.name, ro.build.version.fireos |
| Android release | 11 | ro.build.version.release |
| API level | 30 | ro.build.version.sdk |
| Build ID | RS8185.3898N | ro.build.id |
| Build incremental | 0033018559108 | ro.build.version.incremental |
| Security patch | 2025-12-01 | ro.build.version.security_patch |
| ABI list (32-bit) | armeabi-v7a, armeabi | ro.product.cpu.abilist |
| 64-bit ABI list | none | ro.product.cpu.abilist64 |
| Free storage | about 0.9 GB available of 4.9 GB on /data (ext4) | df /data |

## Not yet verified
- Compose for TV and Media3 minimum API levels are not yet checked against API 30.
- Developer Options and ADB permissions are on; StbEmu has not been inspected and must not be touched.

## Notes
- Fire OS 8.1.8.5 is confirmed, but it runs on the Android 11 base, not Android 14. The earlier plan assumed Android 14 (API 34) and a 64-bit ABI. The device matches neither.
- Storage is tight: about 0.9 GB free on /data.
- Build the APK with an armeabi-v7a ABI.
- The device was asleep when first probed; it answered after waking.
