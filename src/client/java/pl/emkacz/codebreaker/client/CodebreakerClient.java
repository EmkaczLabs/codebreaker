package pl.emkacz.codebreaker.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

public class CodebreakerClient implements ClientModInitializer {
    private static String pendingCode = null;
    private static final boolean AUTO_SEND = false;
    private static final boolean DEBUG_CONSOLE = true;
    private static String lastSentCode = null;
    private static long lastSentAt = 0L;
    private static long SEND_COOLDOWN_MS = 5000L;

    public static void setSendCooldownMillis(long ms) {
        SEND_COOLDOWN_MS = Math.max(0L, ms);
    }

    private static boolean sendCodeSafe(MinecraftClient client, String code, String reason, Runnable onSuccess) {
        if (client == null || code == null) return false;

        long now = System.currentTimeMillis();
        if (code.equals(lastSentCode) && now - lastSentAt <= SEND_COOLDOWN_MS) {
            if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] sendCodeSafe: skipping send (cooldown) code=" + code);
            return false;
        }

        if (client.player == null) {
            if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] sendCodeSafe: no player to send from");
            return false;
        }

        client.execute(() -> {
            if (client.player == null || client.player.networkHandler == null) {
                if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] sendCodeSafe: network handler missing, cannot send code=" + code);
                return;
            }

            if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] sendCodeSafe: sending /kod " + code + " (reason=" + reason + ")");
            client.player.networkHandler.sendChatMessage("/kod " + code);
            lastSentCode = code;
            lastSentAt = System.currentTimeMillis();
            if (onSuccess != null) {
                try {
                    onSuccess.run();
                } catch (Throwable t) {
                    if (DEBUG_CONSOLE) {
                        System.out.println("[Codebreaker][LOG] onSuccess threw: " + t);
                    }
                }
            }
        });

        return true;
    }

    private static boolean isRelevantMessage(String raw) {
        if (raw == null) return false;
        String lower = raw.toLowerCase();
        boolean hasKodColon = lower.contains("kod:");
        boolean hasUzywaj = lower.contains("uzywaj");
        boolean hasAnnouncementPhrase = lower.contains("osoba ktora przepisze") || lower.contains("najszybciej");
        if (lower.contains("przepisal") && !(hasKodColon || hasUzywaj || hasAnnouncementPhrase)) return false;
        return hasKodColon || hasUzywaj || hasAnnouncementPhrase;
     }

    private static String extractCode(String msg) {
        if (msg == null) return null;
        String lower = msg.toLowerCase();

        if (!isRelevantMessage(msg)) return null;

        boolean hasKod = lower.contains("kod");
        boolean hasKodColon = lower.contains("kod:");
        boolean hasUzy = lower.contains("uzywaj");

        if (hasKod && hasUzy) {
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
        }

        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)kod\\D*(\\d{4,})");
        java.util.regex.Matcher m = p.matcher(msg);
        if (m.find()) {
            if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] extractCode: matched after-kod branch => " + m.group(1));
            return m.group(1);
        }

        p = java.util.regex.Pattern.compile("(\\d{4,})\\s*(?=uzywaj)", java.util.regex.Pattern.CASE_INSENSITIVE);
        m = p.matcher(msg);
        if (m.find()) {
            if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] extractCode: matched before-uzywaj branch => " + m.group(1));
            return m.group(1);
        }

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
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String msg = message.getString();
            if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] CHAT raw=" + message + " plain='" + msg + "'");
            MinecraftClient client = MinecraftClient.getInstance();
            if (!isRelevantMessage(msg)) return;
            String lower = msg.toLowerCase();

                 String found = extractCode(msg);
                 if (DEBUG_CONSOLE) System.out.println("[Codebreaker][LOG] CHAT extracted='" + found + "' pending='" + pendingCode + "'");

                 if (found != null) {
                     if (pendingCode == null) {
                         pendingCode = found;
                         if (client.player != null) {
                            client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Detected code: " + pendingCode + (AUTO_SEND ? " — auto-sending" : ". Type /ck to send.")));
                         }

                         boolean isContestAnnouncement = lower.contains("osoba ktora przepisze") || lower.contains("najszybciej") || (lower.contains("kod:") && lower.contains("uzywaj"));

                         if ((AUTO_SEND || isContestAnnouncement)) {
                             String codeToSend = pendingCode;
                            boolean scheduled = sendCodeSafe(client, codeToSend, "auto:chat", () -> {
                                if (client.player != null) client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Sent /kod " + codeToSend));
                                if (codeToSend.equals(pendingCode)) pendingCode = null;
                            });
                            if (!scheduled && client.player != null) {
                                client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Send skipped (cooldown/network)."));
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

                    if ((AUTO_SEND || isContestAnnouncement)) {
                        String codeToSend = pendingCode;
                        boolean scheduled = sendCodeSafe(client, codeToSend, "auto:game", () -> {
                            if (client.player != null) client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Sent /kod " + codeToSend));
                            if (codeToSend.equals(pendingCode)) pendingCode = null;
                        });
                        if (!scheduled && client.player != null) {
                            client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Send skipped (cooldown/network)."));
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

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("ck")
                    .executes(context -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (pendingCode != null && client.player != null) {
                            String codeToSend = pendingCode;
                            boolean scheduled = sendCodeSafe(client, codeToSend, "manual:ck", () -> {
                                if (client.player != null) client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Sent /kod " + codeToSend));
                                if (codeToSend.equals(pendingCode)) pendingCode = null;
                            });
                            if (scheduled) return Command.SINGLE_SUCCESS;
                            client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Send skipped (cooldown/network)."));
                            return 0;
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
                    .then(ClientCommandManager.literal("cooldown")
                        .executes(context -> {
                            MinecraftClient client = MinecraftClient.getInstance();
                            if (client.player != null) client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Send cooldown: " + (SEND_COOLDOWN_MS / 1000L) + "s"));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(0)).executes(context -> {
                            int seconds = IntegerArgumentType.getInteger(context, "seconds");
                            setSendCooldownMillis(seconds * 1000L);
                            MinecraftClient client = MinecraftClient.getInstance();
                            if (client.player != null) client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Set send cooldown to " + seconds + "s"));
                            return Command.SINGLE_SUCCESS;
                        }))
                    )
             );
         });
     }
 }
