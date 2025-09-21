package pl.emkacz.codebreaker.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import com.mojang.brigadier.Command;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

public class CodebreakerClient implements ClientModInitializer {
    private static String pendingCode = null;
    // If true, send the code immediately when detected. Set to false to require /ck confirmation.
    private static final boolean AUTO_SEND = false;
    // If true, print debugging info to the run console (useful for dev-run debugging)
    private static final boolean DEBUG_CONSOLE = true;
    // Guard to avoid sending the same code multiple times in quick succession
    private static String lastSentCode = null;
    private static long lastSentAt = 0L; // epoch ms

    private static boolean isRelevantMessage(String raw) {
        if (raw == null) return false;
        String lower = raw.toLowerCase();
        boolean hasKodColon = lower.contains("kod:");
        boolean hasUzywaj = lower.contains("uzywaj");
        boolean hasAnnouncementPhrase = lower.contains("osoba ktora przepisze") || lower.contains("najszybciej");
        // If this is a winner/summary message like 'X przepisal kod', and it does NOT contain announcement tokens,
        // treat it as NOT relevant (we don't want to react to winner messages).
        if (lower.contains("przepisal") && !(hasKodColon || hasUzywaj || hasAnnouncementPhrase)) return false;
        // Treat the message as relevant only if it explicitly contains 'kod:' or 'uzywaj' or announcement phrases.
        return hasKodColon || hasUzywaj || hasAnnouncementPhrase;
     }

    private static String extractCode(String msg) {
        if (msg == null) return null;
        String lower = msg.toLowerCase();

        if (!isRelevantMessage(msg)) return null;

        boolean hasKod = lower.contains("kod");
        boolean hasKodColon = lower.contains("kod:");
        boolean hasUzy = lower.contains("uzywaj");

        // If the announcement explicitly contains both 'kod' and 'uzywaj' prefer long numeric codes (likely full codes)
        if (hasKod && hasUzy) {
            // Prefer very long codes first (>=8 digits), then 6+ digits
            java.util.regex.Pattern pLong = java.util.regex.Pattern.compile("(?<!\\p{L})(\\d{8,})(?!\\p{L})");
            java.util.regex.Matcher mLong = pLong.matcher(msg);
            if (mLong.find()) {
                if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] extractCode: matched long code after kod+uzy branch => " + mLong.group(1));
                return mLong.group(1);
            }
            java.util.regex.Pattern pMid = java.util.regex.Pattern.compile("(?<!\\p{L})(\\d{6,})(?!\\p{L})");
            java.util.regex.Matcher mMid = pMid.matcher(msg);
            if (mMid.find()) {
                if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] extractCode: matched mid code after kod+uzy branch => " + mMid.group(1));
                return mMid.group(1);
            }
            // fallthrough to other heuristics if no long code found
        }

        // 1) Prefer digits that appear after the token "kod" (case-insensitive), tolerating non-digit separators
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)kod\\D*(\\d{4,})");
        java.util.regex.Matcher m = p.matcher(msg);
        if (m.find()) {
            if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] extractCode: matched after-kod branch => " + m.group(1));
            return m.group(1);
        }

        // 2) Fallback: digits that appear immediately before 'uzywaj'/'uzywajac'
        p = java.util.regex.Pattern.compile("(\\d{4,})\\s*(?=uzywaj)", java.util.regex.Pattern.CASE_INSENSITIVE);
        m = p.matcher(msg);
        if (m.find()) {
            if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] extractCode: matched before-uzywaj branch => " + m.group(1));
            return m.group(1);
        }

        // 3) Final fallback: choose the longest standalone digit group of length >=6 (not adjacent to letters)
        p = java.util.regex.Pattern.compile("(?<!\\p{L})(\\d{6,})(?!\\p{L})");
        m = p.matcher(msg);
        String best = null;
        while (m.find()) {
            String g = m.group(1);
            if (best == null || g.length() > best.length()) best = g;
        }
        if (DEBUG_CONSOLE && best != null) System.out.println("[Codebreaker][LOG] extractCode: matched fallback-longest => " + best);
        return best;
     }

    @Override
    public void onInitializeClient() {
        // Listen for chat messages from the server
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String msg = message.getString();
            if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] CHAT raw=" + message + " plain='" + msg + "'");
            MinecraftClient client = MinecraftClient.getInstance();
            if (!isRelevantMessage(msg)) return; // ignore non-relevant messages
            String lower = msg.toLowerCase();

                 String found = extractCode(msg);
                 if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] CHAT extracted='" + found + "' pending='" + pendingCode + "'");

                 if (found != null) {
                     if (pendingCode == null) {
                         pendingCode = found;
                         if (client.player != null) {
                            client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Detected code: " + pendingCode + (AUTO_SEND ? " — auto-sending" : ". Type /ck to send.")));
                         }

                         // Force-send immediately for contest-style announcements to avoid sending after a winner message
                         boolean isContestAnnouncement = lower.contains("osoba ktora przepisze") || lower.contains("najszybciej") || (lower.contains("kod:") && lower.contains("uzywaj"));

                         if ((AUTO_SEND || isContestAnnouncement) && client.player != null && client.player.networkHandler != null) {
                            // Prevent double-sends: don't re-send the same code within 5 seconds
                            long now = System.currentTimeMillis();
                            if (pendingCode != null && (!pendingCode.equals(lastSentCode) || now - lastSentAt > 5000)) {
                                if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] Auto-sending code due to isContestAnnouncement=" + isContestAnnouncement + " AUTO_SEND=" + AUTO_SEND + " code=" + pendingCode);
                                client.player.networkHandler.sendChatMessage("/kod " + pendingCode);
                                client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Sent /kod " + pendingCode));
                                lastSentCode = pendingCode;
                                lastSentAt = now;
                                pendingCode = null; // clear after auto-send
                            } else {
                                if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] Skipped auto-send: recent send guard for code=" + pendingCode);
                            }
                         }
                     } else {
                         // There's already a pending code; inform the player and don't overwrite
                         if (client.player != null) {
                             client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] New code detected (" + found + ") but code " + pendingCode + " is already pending. Type /ck to send or /ck cancel to discard the pending code."));
                         }
                     }
                 } else {
                     // Debug fallback: show the raw message when it looked relevant but no code was found after 'kod'
                     if (msg.contains("Konkurs") || msg.contains("Osoba") || lower.contains("kod")) {
                         if (client.player != null) {
                             String shortMsg = msg.length() > 240 ? msg.substring(0, 237) + "..." : msg;
                             client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker][DEBUG] Raw message: " + shortMsg));
                         }
                     }
                 }
        });

        // Also listen for game (server/system) messages, some announcements appear as game messages
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String msg = message.getString();
            if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] GAME raw=" + message + " plain='" + msg + "'");
            MinecraftClient client = MinecraftClient.getInstance();
            if (!isRelevantMessage(msg)) return;
            String lower = msg.toLowerCase();
            String found = extractCode(msg);
            if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] GAME extracted='" + found + "' pending='" + pendingCode + "'");

            if (found != null) {
                if (pendingCode == null) {
                    pendingCode = found;
                    if (client.player != null) {
                        client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Detected code (game msg): " + pendingCode + (AUTO_SEND ? " — auto-sending" : ". Type /ck to send.")));
                    }

                    boolean isContestAnnouncement = lower.contains("osoba ktora przepisze") || lower.contains("najszybciej") || (lower.contains("kod:") && lower.contains("uzywaj"));

                    if ((AUTO_SEND || isContestAnnouncement) && client.player != null && client.player.networkHandler != null) {
                        long now = System.currentTimeMillis();
                        if (pendingCode != null && (!pendingCode.equals(lastSentCode) || now - lastSentAt > 5000)) {
                            if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] Auto-sending code (game) due to isContestAnnouncement=" + isContestAnnouncement + " AUTO_SEND=" + AUTO_SEND + " code=" + pendingCode);
                            client.player.networkHandler.sendChatMessage("/kod " + pendingCode);
                            client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Sent /kod " + pendingCode));
                            lastSentCode = pendingCode;
                            lastSentAt = now;
                            pendingCode = null;
                        } else {
                            if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] Skipped auto-send (game): recent send guard for code=" + pendingCode);
                        }
                    }
                } else {
                    if (client.player != null) {
                        client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] New code detected (" + found + ") but code " + pendingCode + " is already pending. Type /ck to send or /ck cancel to discard the pending code."));
                    }
                }
            } else {
                if (msg.contains("Konkurs") || msg.contains("Osoba") || lower.contains("kod")) {
                    if (client.player != null) {
                        String shortMsg = msg.length() > 240 ? msg.substring(0, 237) + "..." : msg;
                        client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker][DEBUG] Raw message: " + shortMsg));
                    }
                }
            }
        });

        // Register client-side /ck command for confirmation with cancel subcommand
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("ck")
                    .executes(context -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (pendingCode != null && client.player != null) {
                            client.player.networkHandler.sendChatMessage("/kod " + pendingCode);
                            client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Sent /kod " + pendingCode));
                            pendingCode = null;
                            return Command.SINGLE_SUCCESS;
                        } else {
                            client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] No code pending."));
                            return 0;
                        }
                    })
                    .then(ClientCommandManager.literal("cancel").executes(context -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (pendingCode != null && client.player != null) {
                            client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Pending code " + pendingCode + " cancelled."));
                            pendingCode = null;
                            return Command.SINGLE_SUCCESS;
                        } else {
                            if (client.player != null) client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] No code pending to cancel."));
                            return 0;
                        }
                    }))
            );
        });
    }
}
