package pl.zyro.basealert;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class BaseConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("zyrobase.properties");

    public static String topic = "";
    public static int radius = 10;      // ile bloków dookoła bazy też liczy się jako "blisko"
    public static boolean enabled = true;
    public static BlockPos pos1 = null; // górny róg
    public static BlockPos pos2 = null; // dolny róg

    public static boolean hasBase() { return pos1 != null && pos2 != null; }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE)) {
            Properties p = new Properties();
            p.load(r);
            topic = p.getProperty("topic", "");
            radius = Integer.parseInt(p.getProperty("radius", "10"));
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            if (p.containsKey("p1")) pos1 = parse(p.getProperty("p1"));
            if (p.containsKey("p2")) pos2 = parse(p.getProperty("p2"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("topic", topic);
        p.setProperty("radius", String.valueOf(radius));
        p.setProperty("enabled", String.valueOf(enabled));
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
