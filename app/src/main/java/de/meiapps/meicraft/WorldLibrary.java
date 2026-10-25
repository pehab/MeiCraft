package de.meiapps.meicraft;

import android.util.AtomicFile;
import java.io.*;
import java.util.*;

/** Each world has its own atomic save and name file; old saves stay in place. */
final class WorldLibrary {
    private final File root, directory;
    WorldLibrary(File root) {
        this.root = root;
        directory = new File(root, "worlds");
    }
    AtomicFile file(SavedWorld world) { return new AtomicFile(new File(root, world.dataPath())); }
    private AtomicFile metadata(String id) { return new AtomicFile(new File(directory, id + ".meta")); }
    SavedWorld entry(String id) throws IOException {
        if ("creative".equals(id) || "story".equals(id)) {
            AtomicFile meta = metadata(id);
            if (!meta.getBaseFile().exists() && !new File(meta.getBaseFile() + ".bak").exists())
                return SavedWorld.legacy("story".equals(id) ? 1 : 0);
        }
        try { new SavedWorld(id, "Prüfung", "story".equals(id) ? 1 : 0); }
        catch (IllegalArgumentException e) { throw new IOException("Ungültige Welt-ID", e); }
        return SavedWorld.decode(metadata(id).readFully(), id);
    }
    List<SavedWorld> list() throws IOException {
        LinkedHashMap<String, SavedWorld> entries = new LinkedHashMap<>();
        entries.put("creative", entry("creative")); entries.put("story", entry("story"));
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files == null) throw new IOException("Weltenordner nicht lesbar");
            for (File candidate : files) {
                String name = candidate.getName();
                if (name.endsWith(".meta.bak")) name = name.substring(0, name.length() - 4);
                if (name.endsWith(".meta")) {
                    String id = name.substring(0, name.length() - 5);
                    if (!entries.containsKey(id)) entries.put(id, entry(id));
                }
            }
        }
        List<SavedWorld> result = new ArrayList<>(entries.values());
        result.sort(Comparator.comparingInt((SavedWorld w) -> w.mode).thenComparing(w -> w.name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }
    void describe(SavedWorld world) throws IOException { write(metadata(world.id), world.encode()); }
    boolean nameUsed(String name, int mode, String exceptId) throws IOException {
        for (SavedWorld world : list())
            if (world.mode == mode && !world.id.equals(exceptId) && world.name.equalsIgnoreCase(name.trim())) return true;
        return false;
    }
    World read(SavedWorld entry) throws IOException {
        AtomicFile source = file(entry);
        if (("creative".equals(entry.id) || "story".equals(entry.id))
                && !source.getBaseFile().exists() && !new File(source.getBaseFile() + ".bak").exists())
            return new World(20261025L + entry.mode, entry.mode == 1);
        World world = World.decode(source.readFully());
        if (world.story != (entry.mode == 1)) throw new IOException("Weltmodus stimmt nicht überein");
        return world;
    }
    static void write(AtomicFile destination, byte[] data) throws IOException {
        File parent = destination.getBaseFile().getParentFile();
        if (parent == null || !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Speicherordner nicht verfügbar");
        FileOutputStream stream = null;
        try { stream = destination.startWrite(); stream.write(data); destination.finishWrite(stream); }
        catch (IOException e) { if (stream != null) destination.failWrite(stream); throw e; }
    }
}
