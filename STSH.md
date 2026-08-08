# stsh

`stsh` exposes Stellar's privileged process API to terminal applications such as Termux.
It uses ordinary pipes when standard streams are redirected and a remote PTY for an
interactive terminal.

## Build

Run the `Manager CI` workflow or push to the fork's `main` branch. The
`manager-debug-apk` artifact contains:

- the modified Stellar manager APK;
- the `stsh` launcher script;
- `stsh_stellar.dex`, the terminal-side loader.

## Install in Termux

Install the modified Stellar APK first, start its privileged service, then place both
exported files in the same private Termux directory:

```sh
cp stsh stsh_stellar.dex "$PREFIX/bin/"
chmod 700 "$PREFIX/bin/stsh"
chmod 400 "$PREFIX/bin/stsh_stellar.dex"
```

The first invocation asks Stellar to authorize Termux:

```sh
stsh -c 'id; ls -la /storage/emulated/0/Android/data'
```

Use `exec` to run a command directly instead of wrapping arguments with Android's
system shell:

```sh
stsh exec /system/bin/id
```

`STSH_APPLICATION_ID` defaults to `com.termux`. Set it when using a Termux fork with a
different package name. Set `STSH_PRESERVE_ENV=1` only when the privileged process
needs the terminal app's environment.
