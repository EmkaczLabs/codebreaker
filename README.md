# Codebreaker

A small Fabric client-side mod used to detect and send verification codes.

- Target server network: craftplay.pl
- Intended use: Educational purposes only (testing/learning). Not intended to be used for cheating or malicious behavior on public servers.

## Overview
This mod listens to incoming chat messages on the client and detects lines that contain a verification code. When a code is detected it caches it and prompts the player to confirm sending it with the `/ck` client-side command.

## Build
This project uses Gradle and Fabric Loom. From the project root run:

```bash
gradle build
```

(or `./gradlew build` on systems with the Gradle wrapper configured)

## Run / Test
- Run a local Minecraft client with Fabric + this mod installed for manual testing.
- Join `craftplay.pl` (or a test server you control) and trigger the server message that contains the code pattern used by the mod (the mod looks for `Osoba ktora przepisze najszybciej kod:` and extracts `kod: (\d+)`).
- When a code is detected the client will display a chat notification. Type `/ck` (client-side command) to send the `/kod <detected_code>` command to the server.

## Notes & Safety
- This project is intended as an educational example for learning Fabric modding and client-side event handling.
- Do not use this mod to gain unfair advantage on servers where such behavior is against the rules.
- The code pattern and behavior are currently fixed; modify the regex in `src/client/java/pl/emkacz/codebreaker/client/CodebreakerClient.java` if your server's messages differ.

## Contribution & License
See `LICENSE.txt` in the repository root for license details.

If you want changes (different message patterns, localization, or a safer confirm flow), open an issue or submit a pull request.

#SkurwysynskyCorp