package pl.zyro.basealert;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class BaseConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("zyrobase.properties");

    // telefon
    public static String topic = "";
    public static boolean qrApp = true;        // QR: link do aplikacji (ntfy://) albo do przeglądarki (https://)

    // alarm
    public static boolean enabled = true;
    public static boolean skinPhoto = true;    // zdjęcie (skin) gracza w powiadomieniu
    public static boolean screenshot = true;   // zrzut ekranu z gry w osobnym powiadomieniu
    public static boolean sound = true;        // dźwięk alarmu w grze
    public static boolean banner = true;       // czerwony baner na ekranie
    public static int radius = 10;             // ile bloków dookoła bazy też liczy się jako "blisko"
    public static int cooldownSec = 60;        // minimalny odstęp między alarmami

    // wygląd
    public static int accent = 0x4DA6FF;       // kolor motywu GUI (RGB)
    public static int panelStyle = 0;          // 0 ciemny, 1 bardzo ciemny, 2 szkło

    // baza i bezpieczni gracze
    public static BlockPos pos1 = null;        // górny róg
    public static BlockPos pos2 = null;        // dolny róg
    public static final List<String> safePlayers = new ArrayList<>();

    public static boolean hasBase() { return pos1 != null && pos2 != null; }

    public static boolean validTopic() { return topic.matches("[A-Za-z0-9_-]{3,64}"); }

    public static boolean isSafe(String name) {
        for (String s : safePlayers) {
            if (s.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** @return true, jeśli gracz został dodany (false: zły nick albo już jest na liście) */
    public static boolean addSafe(String name) {
        name = name.trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}") || isSafe(name) || safePlayers.size() >= 60) return false;
        safePlayers.add(name);
        save();
        return true;
    }

    public static void removeSafe(String name) {
        safePlayers.removeIf(s -> s.equalsIgnoreCase(name));
        save();
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE)) {
            Properties p = new Properties();
            p.load(r);
            topic = p.getProperty("topic", "");
            qrApp = Boolean.parseBoolean(p.getProperty("qrApp", "true"));
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            skinPhoto = Boolean.parseBoolean(p.getProperty("skinPhoto", "true"));
            screenshot = Boolean.parseBoolean(p.getProperty("screenshot", "true"));
            sound = Boolean.parseBoolean(p.getProperty("sound", "true"));
            banner = Boolean.parseBoolean(p.getProperty("banner", "true"));
            radius = Integer.parseInt(p.getProperty("radius", "10"));
            cooldownSec = Integer.parseInt(p.getProperty("cooldownSec", "60"));
            accent = Integer.parseInt(p.getProperty("accent", "4DA6FF"), 16);
            panelStyle = Integer.parseInt(p.getProperty("panelStyle", "0"));
            safePlayers.clear();
            for (String s : p.getProperty("safePlayers", "").split(",")) {
                if (s.trim().matches("[A-Za-z0-9_]{1,16}")) safePlayers.add(s.trim());
            }
            if (p.containsKey("p1")) pos1 = parse(p.getProperty("p1"));
            if (p.containsKey("p2")) pos2 = parse(p.getProperty("p2"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("topic", topic);
        p.setProperty("qrApp", String.valueOf(qrApp));
        p.setProperty("enabled", String.valueOf(enabled));
        p.setProperty("skinPhoto", String.valueOf(skinPhoto));
        p.setProperty("screenshot", String.valueOf(screenshot));
        p.setProperty("sound", String.valueOf(sound));
        p.setProperty("banner", String.valueOf(banner));
        p.setProperty("radius", String.valueOf(radius));
        p.setProperty("cooldownSec", String.valueOf(cooldownSec));
        p.setProperty("accent", String.format("%06X", accent));
        p.setProperty("panelStyle", String.valueOf(panelStyle));
        p.setProperty("safePlayers", String.join(",", safePlayers));
        if (pos1 != null) p.setProperty("p1", pos1.getX() + "," + pos1.getY() + "," + pos1.getZ());
        if (pos2 != null) p.setProperty("p2", pos2.getX() + "," + pos2.getY() + "," + pos2.getZ());
        try (Writer w = Files.newBufferedWriter(FILE)) {
            p.store(w, "Zyro Base Alert");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static BlockPos parse(String s) {
        String[] a = s.split(",");
        return new BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
    }
}
