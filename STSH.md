# stsh

`stsh` exposes Stellar's privileged process API to terminal applications such as Termux.
It uses ordinary pipes when standard streams are redirected and a remote PTY for an
interactive terminal.

## Build

Run the `Manager CI` workflow or push to the fork's `main` branch. The
`manager-debug-apk` artifact contains:

- the modified Stellar manager APK;
- the `stsh` launcher script.

## Install in Termux

Install the modified Stellar APK first and start its privileged service. Then install the
latest launcher directly from Termux:

```sh
pkg install -y curl
curl -fsSL https://github.com/xiaoancute/Stellar/releases/latest/download/stsh \
  -o "$PREFIX/bin/stsh"
chmod 700 "$PREFIX/bin/stsh"
```

The script locates the installed Stellar APK with Android's package manager and loads the
embedded shell client directly from that read-only APK. Python is optional: it is used for
the local bridge when available, while the app-process fallback works without it.

The first invocation asks Stellar to authorize Termux:

```sh
stsh id
stsh ls -la /storage/emulated/0/Android/data
```

Use `-c` for shell syntax:

```sh
stsh -c 'id; ls -la /storage/emulated/0/Android/data'
```

Direct execution is also available explicitly with `exec`:

```sh
stsh exec /system/bin/id
```

`STSH_APPLICATION_ID` defaults to `com.termux`. Set it when using a Termux fork with a
different package name. `STSH_MANAGER_APPLICATION_ID` overrides the Stellar package
name. Set `STSH_PRESERVE_ENV=1` only when the privileged process needs the terminal
app's environment.
