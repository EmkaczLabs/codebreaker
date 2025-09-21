# Codebreaker

A small Fabric client-side mod that detects contest verification codes announced on a server and helps (optionally) send them back to the server.

- Target server network: craftplay.pl
- Intended use: Educational purposes only (testing / learning). Not to be used for cheating or malicious behavior on public servers.

## Summary

Codebreaker listens for chat and server/game messages and attempts to extract verification codes announced during contests. It shows a client-side prompt and provides a client-only command to send the detected code to the server.

## Features

- Detects codes in both player chat and server/game announcement messages.
- Command `/ck` sends the detected code (client-side). Use `/ck cancel` to clear the pending code.
- Heuristics tuned for announcement formats like: "Konkurs » Osoba ktora przepisze najszybciej kod: 123456789... uzywajac: /kod (kod)".
- Debugging mode prints helpful logs to the dev-run console to aid regex tuning.
- Default safety: automatic sending is disabled. For contest announcements, the mod may force-send immediately to avoid sending after a winner message (this behavior is conservative and guarded).

## Build

This project uses Gradle and Fabric Loom. From the project root run (cmd.exe / PowerShell):

```bash
gradle build
```

To start a dev client from the project (recommended for testing):

```bash
gradle runClient
```

## Usage / Testing (developer/dev-run)

1. Start a Fabric dev client (`gradle runClient`) or run the built mod in a client with Fabric.
2. Join `craftplay.pl` (or a test server with similar contest messages).
3. When an announcement appears that matches the expected format you should see client messages:
   - `[Codebreaker] Detected code: <code>. Type /ck to send.` (requires you to run `/ck` to confirm), or
   - `[Codebreaker] Detected code (game msg): <code> — auto-sending` and the code will be sent automatically for contest-style announcements.
4. To manually send a pending code, type `/ck` in chat. To cancel a pending code use `/ck cancel`.

## Debugging / Tuning

- The code detection lives in `src/client/java/pl/emkacz/codebreaker/client/CodebreakerClient.java`.
- Two flags control behavior in that file:
  - `AUTO_SEND` (boolean) — if `true` the mod sends detected codes automatically (default in this repo: `false`).
  - `DEBUG_CONSOLE` (boolean) — if `true` the dev-run console receives logs about incoming messages and which extraction branch matched (default: `true` in dev).
- If detection fails, enable `DEBUG_CONSOLE`, reproduce the announcement, and copy the console output + any in-chat `[Codebreaker][DEBUG] Raw message: ...` lines into an issue so the regex can be tuned.

## Regex / Heuristics

Codebreaker prefers to extract digits that appear after the token `kod` or immediately before the token `uzywaj`, and falls back to a longest standalone digit group. It also avoids matching digit groups embedded in letters (for example `I914900K`). These heuristics are conservative to reduce false positives.

## Safety & Server Rules

This tool is intended for educational use (learning Fabric modding, practicing message parsing). Do not use it to gain unfair advantage or to violate server rules. The author assumes no responsibility for misuse — respect server TOS and community rules on `craftplay.pl` and any other server.

## Contributing

If you want the regex tuned, a different notification method, or extra features (logging to file, localization), open an issue or send a PR. Keep changes small and include tests or sample announcement strings for validation.

## License

See `LICENSE.txt` in the project root.

## Repository

Source repo: https://github.com/EmkaczLabs/codebreaker

---

If you want, I can also:
- Add a short troubleshooting section with sample console output and what to paste into an issue.
- Turn on a temporary development branch that enables `AUTO_SEND` for quick end-to-end testing (not recommended on public servers).
