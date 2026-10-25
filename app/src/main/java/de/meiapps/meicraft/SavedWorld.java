package de.meiapps.meicraft;

import java.io.*;
import java.util.UUID;

/** A world's display name is independent of its permanent, safe file identity. */
final class SavedWorld {
    final String id, name;
    final int mode;

    SavedWorld(String id, String name, int mode) {
        if (!("creative".equals(id) || "story".equals(id)
                || id != null && id.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")))
            throw new IllegalArgumentException("Ungültige Welt-ID");
        if (mode != 0 && mode != 1 || "creative".equals(id) && mode != 0 || "story".equals(id) && mode != 1)
            throw new IllegalArgumentException("Ungültiger Weltmodus");
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 48 || clean.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Bitte einen Namen mit 1–48 Zeichen eingeben.");
        this.id = id;
        this.name = clean;
        this.mode = mode;
    }

    static SavedWorld legacy(int mode) {
        return new SavedWorld(mode == 1 ? "story" : "creative", mode == 1 ? "Meine Storywelt" : "Meine Kreativwelt", mode);
    }

    static SavedWorld create(String name, int mode) { return new SavedWorld(UUID.randomUUID().toString(), name, mode); }
    String label() { return name + " · " + (mode == 1 ? "Story" : "Kreativ"); }
    String dataPath() {
        return "creative".equals(id) ? "world.mcworld" : "story".equals(id) ? "story.mcworld" : "worlds/" + id + ".mcworld";
    }

    byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(1); out.writeUTF(id); out.writeUTF(name); out.writeInt(mode);
        }
        return bytes.toByteArray();
    }

    static SavedWorld decode(byte[] bytes, String expectedId) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IOException("Unbekannte Weltbeschreibung");
            SavedWorld world = new SavedWorld(in.readUTF(), in.readUTF(), in.readInt());
            if (!world.id.equals(expectedId) || in.read() != -1) throw new IOException("Ungültige Weltbeschreibung");
            return world;
        } catch (IllegalArgumentException e) { throw new IOException("Ungültige Weltbeschreibung", e); }
    }
}
