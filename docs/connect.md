# Connecting to Minecraft Infinite

Replace `SERVER_IP` with the host running Paper. On this phone that is usually a LAN address (`ip addr` / Wi-Fi settings), not a public IP, unless you forwarded ports.

Default ports come from `.env`: Java `25565` TCP, Bedrock `19132` UDP.

## Java Edition

1. Multiplayer → Add Server
2. Address: `SERVER_IP:25565` (port optional if it is 25565)
3. Sign in with a Microsoft account if `ONLINE_MODE=true`

Older Java versions can still join through ViaVersion when that plugin is installed.

## Bedrock (phone / Windows)

1. Play → Servers → Add Server (or Friends tab on some builds)
2. Address: `SERVER_IP`
3. Port: `19132`
4. Save and join

You do **not** need a Java copy. Floodgate authenticates the Bedrock Xbox/Microsoft account and shows the player as `.Name`.

LAN: Bedrock may list the server automatically if UDP multicast works. Adding it by IP is more reliable.

## Bedrock (Xbox / PlayStation / Switch)

Add a server with the same IP and UDP port `19132` if that platform allows third-party servers. Console NAT is picky; a VPS with both ports forwarded is more reliable than hosting on a phone.

## Linking Java and Bedrock accounts

Floodgate global linking is on by default (`enable-global-linking: true`). Players can link at https://link.geysermc.org/ so inventories/permissions follow the Java UUID. Local `/linkaccount` stays available.

## Firewall

| Port | Proto | Who |
| --- | --- | --- |
| 25565 | TCP | Java clients |
| 19132 | UDP | Bedrock clients / Geyser pings |

Do not point Geyser at the same UDP port as query or a voice-chat plugin.
