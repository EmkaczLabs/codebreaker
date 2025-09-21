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

    @Override
    public void onInitializeClient() {
        // Listen for chat messages from the server
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String msg = message.getString();
            MinecraftClient client = MinecraftClient.getInstance();
            if (msg.contains("Osoba ktora przepisze najszybciej kod:")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("kod: (\\d+)").matcher(msg);
                if (matcher.find()) {
                    pendingCode = matcher.group(1);
                    if (client.player != null) {
                        client.inGameHud.getChatHud().addMessage(Text.of("[Codebreaker] Detected code: " + pendingCode + ". Type /ck to send."));
                    }
                }
            }
        });

        // Register client-side /ck command for confirmation
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("ck").executes(context -> {
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
        ));
    }
}
