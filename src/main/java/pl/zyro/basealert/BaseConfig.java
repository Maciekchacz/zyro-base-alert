package pl.zyro.basealert;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class BaseConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("zyrobase.properties");

    public static final int MAX_RANGE = 500;

    /** Ustawienia osobne dla każdego serwera: nazwa, IP, baza i zasięg. */
    public static class Profile {
        public String name = "";
        public String ip = "";
        public BlockPos pos1 = null;   // górny róg bazy
        public BlockPos pos2 = null;   // dolny róg bazy
        /** Zasięg w blokach od krawędzi bazy: 0 góra, 1 dół, 2 lewo (-X), 3 prawo (+X), 4 przód (-Z), 5 tył (+Z). */
        public int[] range = {10, 10, 10, 10, 10, 10};

        public boolean hasBase() { return pos1 != null && pos2 != null; }
    }

    // telefon
    public static String topic = "";
    public static boolean qrApp = true;        // QR: link do aplikacji (ntfy://) albo do przeglądarki (https://)

    // alarm
    public static boolean enabled = true;
    public static boolean skinPhoto = true;    // zdjęcie (skin) gracza w powiadomieniu
    public static boolean screenshot = true;   // zrzut ekranu z gry w osobnym powiadomieniu
    public static boolean sound = true;        // dźwięk alarmu w grze
    public static boolean banner = true;       // czerwony baner na ekranie
    public static int cooldownSec = 60;        // minimalny odstęp między alarmami

    // wygląd
    public static int accent = 0x4DA6FF;       // kolor motywu GUI (RGB)
    public static int panelStyle = 0;          // 0 ciemny, 1 bardzo ciemny, 2 szkło

    // bezpieczni gracze (wspólni dla wszystkich serwerów)
    public static final List<String> safePlayers = new ArrayList<>();

    // profile serwerów: klucz = adres serwera
    public static final Map<String, Profile> profiles = new LinkedHashMap<>();

    // dane ze starszej wersji (jedna baza dla wszystkiego) - trafiają do pierwszego profilu
    private static BlockPos legacyP1, legacyP2;
    private static int legacyRadius = -1;

    /** Klucz profilu: adres serwera, "singleplayer" albo "offline". */
    public static String currentKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            ServerInfo si = mc.getCurrentServerEntry();
            if (si != null && si.address != null) return si.address.trim().toLowerCase(Locale.ROOT);
            if (mc.isInSingleplayer()) return "singleplayer";
        }
        return "offline";
    }

    /** Profil serwera, na którym teraz grasz (tworzony automatycznie). */
    public static Profile profile() {
        String key = currentKey();
        Profile p = profiles.get(key);
        if (p == null) {
            p = new Profile();
            MinecraftClient mc = MinecraftClient.getInstance();
            ServerInfo si = mc != null ? mc.getCurrentServerEntry() : null;
            if (si != null) {
                p.name = si.name == null ? "" : si.name;
                p.ip = si.address == null ? "" : si.address;
            } else {
                p.name = key;
            }
            if (profiles.isEmpty() && (legacyP1 != null || legacyRadius >= 0)) {
                p.pos1 = legacyP1;
                p.pos2 = legacyP2;
                if (legacyRadius >= 0) java.util.Arrays.fill(p.range, Math.min(legacyRadius, MAX_RANGE));
            }
            profiles.put(key, p);
        }
        return p;
    }

    public static boolean hasBase() { return profile().hasBase(); }

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
            cooldownSec = Integer.parseInt(p.getProperty("cooldownSec", "60"));
            accent = Integer.parseInt(p.getProperty("accent", "4DA6FF"), 16);
            panelStyle = Integer.parseInt(p.getProperty("panelStyle", "0"));

            safePlayers.clear();
            for (String s : p.getProperty("safePlayers", "").split(",")) {
                if (s.trim().matches("[A-Za-z0-9_]{1,16}")) safePlayers.add(s.trim());
            }

            // starsze wersje
            if (p.containsKey("p1")) legacyP1 = parsePos(p.getProperty("p1"));
            if (p.containsKey("p2")) legacyP2 = parsePos(p.getProperty("p2"));
            if (p.containsKey("radius")) legacyRadius = Integer.parseInt(p.getProperty("radius"));

            profiles.clear();
            int n = Integer.parseInt(p.getProperty("servers", "0"));
            for (int i = 0; i < n; i++) {
                String key = p.getProperty("s" + i + ".key");
                if (key == null) continue;
                Profile pr = new Profile();
                pr.name = p.getProperty("s" + i + ".name", "");
                pr.ip = p.getProperty("s" + i + ".ip", "");
                if (p.containsKey("s" + i + ".p1")) pr.pos1 = parsePos(p.getProperty("s" + i + ".p1"));
                if (p.containsKey("s" + i + ".p2")) pr.pos2 = parsePos(p.getProperty("s" + i + ".p2"));
                String[] rg = p.getProperty("s" + i + ".range", "10,10,10,10,10,10").split(",");
                for (int k = 0; k < 6 && k < rg.length; k++) {
                    pr.range[k] = Math.max(0, Math.min(MAX_RANGE, Integer.parseInt(rg[k].trim())));
                }
                profiles.put(key, pr);
            }
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
        p.setProperty("cooldownSec", String.valueOf(cooldownSec));
        p.setProperty("accent", String.format("%06X", accent));
        p.setProperty("panelStyle", String.valueOf(panelStyle));
        p.setProperty("safePlayers", String.join(",", safePlayers));

        int i = 0;
        for (Map.Entry<String, Profile> e : profiles.entrySet()) {
            Profile pr = e.getValue();
            p.setProperty("s" + i + ".key", e.getKey());
            p.setProperty("s" + i + ".name", pr.name);
            p.setProperty("s" + i + ".ip", pr.ip);
            if (pr.pos1 != null) p.setProperty("s" + i + ".p1", fmt(pr.pos1));
            if (pr.pos2 != null) p.setProperty("s" + i + ".p2", fmt(pr.pos2));
            StringBuilder rg = new StringBuilder();
            for (int k = 0; k < 6; k++) rg.append(k == 0 ? "" : ",").append(pr.range[k]);
            p.setProperty("s" + i + ".range", rg.toString());
            i++;
        }
        p.setProperty("servers", String.valueOf(i));

        try (Writer w = Files.newBufferedWriter(FILE)) {
            p.store(w, "Zyro Base Alert");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String fmt(BlockPos b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private static BlockPos parsePos(String s) {
        String[] a = s.split(",");
        return new BlockPos(Integer.parseInt(a[0].trim()), Integer.parseInt(a[1].trim()), Integer.parseInt(a[2].trim()));
    }
}
