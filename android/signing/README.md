# Debug signing key

`foundry-debug.keystore` signs Foundry's nightly debug builds (store and key password `android`, alias
`androiddebugkey`, the same layout as Android's standard debug key). It is committed on purpose, so every CI build
has the same signature and installs over the previous one. It is not a release key and must never sign a release.
