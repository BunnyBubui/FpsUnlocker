# Signing

Debug builds use the local Android debug key and are test artifacts only. The previous shared debug keystore is no longer tracked, so existing debug installations may need to be uninstalled before installing another debug build. Uninstalling can remove local application settings and backups; export anything needed first.

For a distribution build, supply FPS_RELEASE_KEYSTORE, FPS_RELEASE_STORE_PASSWORD, FPS_RELEASE_KEY_ALIAS and FPS_RELEASE_KEY_PASSWORD as private build environment values. Keep the release key outside Git. Without those values the release output is unsigned; it must not be advertised as a signed, installable release. CI uploads debug artifacts and no longer automatically publishes them as releases.
