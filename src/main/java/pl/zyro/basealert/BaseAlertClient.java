package pl.zyro.basealert;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class BaseAlertClient implements ClientModInitializer {
    /** 0 = nic, 1 = czekam na górny róg, 2 = czekam na dolny róg */
    public static int step = 0;

    private static KeyBinding openKey;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static long lastAlert = 0;
    private static int tick = 0;

    @Override
    public void onInitializeClient() {
        BaseConfig.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.zyrobase.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.zyrobase"));

        // Prawy klik w blok podczas zaznaczania bazy
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient || step == 0 || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            BlockPos p = hit.getBlockPos();
            if (step == 1) {
                BaseConfig.pos1 = p;
                step = 2;
                msg("§aGórny róg: " + p.toShortString() + " §7— teraz kliknij PRAWYM DOLNY róg bazy.");
            } else {
                BaseConfig.pos2 = p;
                step = 0;
                BaseConfig.save();
                msg("§aDolny róg: " + p.toShortString() + " §7— baza zaznaczona! Alerty aktywne.");
            }
            return ActionResult.SUCCESS; // nie otwiera skrzynek itp. podczas zaznaczania
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                client.setScreen(new BaseScreen());
            }
            if (++tick % 20 == 0) checkBase(client);
        });
    }

    private static void checkBase(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        if (!BaseConfig.enabled || !BaseConfig.hasBase() || BaseConfig.topic.isEmpty()) return;

        BlockPos a = BaseConfig.pos1, b = BaseConfig.pos2;
        Box box = new Box(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1
        ).expand(BaseConfig.radius);

        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player) continue;
            if (box.contains(p.getPos())) {
                long now = System.currentTimeMillis();
                if (now - lastAlert > 60_000) { // max 1 powiadomienie na minutę
                    lastAlert = now;
                    sendPush("Gracz " + p.getName().getString() + " jest blisko Twojej bazy!");
                    msg("§c⚠ " + p.getName().getString() + " jest blisko Twojej bazy!");
                }
                return;
            }
        }
    }

    /** Wysyła powiadomienie przez ntfy.sh (darmowa aplikacja na telefon). */
    public static void sendPush(String text) {
        if (BaseConfig.topic.isEmpty()) return;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://ntfy.sh/" + BaseConfig.topic))
                .header("Title", "Minecraft Base Alert")
                .header("Priority", "high")
                .POST(HttpRequest.BodyPublishers.ofString(text))
                .build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding());
    }

    public static void msg(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }
}
