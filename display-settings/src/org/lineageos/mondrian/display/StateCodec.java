/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned, bounded encoding shared by the Android journal and host recovery tests. */
final class StateCodec {
    private static final int MAGIC = 0x4d525331; // MRS1

    static void write(OutputStream stream, ResolutionEngine.State state) throws IOException {
        DataOutputStream out = new DataOutputStream(stream);
        out.writeInt(MAGIC);
        writeAnchors(out, state.anchors);
        out.writeBoolean(state.pending != null);
        if (state.pending != null) {
            ResolutionEngine.Pending p = state.pending;
            out.writeUTF(p.token);
            out.writeLong(p.deadline);
            out.writeInt(p.boot);
            writeFrame(out, p.before);
            writeFrame(out, p.after);
            writeAnchors(out, p.anchors);
        }
        out.flush();
    }

    static ResolutionEngine.State read(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(stream);
        if (in.readInt() != MAGIC) throw new IOException("Unknown resolution journal version");
        Map<Integer, ResolutionEngine.Anchor> anchors = readAnchors(in);
        ResolutionEngine.Pending pending = null;
        if (in.readBoolean()) {
            String token = in.readUTF();
            if (token.isEmpty() || token.length() > 80) throw new IOException("Invalid token");
            long deadline = in.readLong();
            int boot = in.readInt();
            pending = new ResolutionEngine.Pending(token, deadline, boot,
                    readFrame(in), readFrame(in), readAnchors(in));
        }
        if (in.read() != -1) throw new IOException("Trailing resolution journal data");
        return new ResolutionEngine.State(anchors, pending);
    }

    private static void writeFrame(DataOutputStream out, ResolutionEngine.Frame frame)
            throws IOException {
        out.writeInt(frame.width);
        out.writeInt(frame.height);
        out.writeInt(frame.scaling);
        out.writeInt(frame.densities.size());
        for (Map.Entry<Integer, Integer> user : frame.densities.entrySet()) {
            out.writeInt(user.getKey());
            out.writeInt(user.getValue());
        }
    }

    private static ResolutionEngine.Frame readFrame(DataInputStream in) throws IOException {
        int width = positive(in.readInt());
        int height = positive(in.readInt());
        int scaling = in.readInt();
        if (scaling != 0 && scaling != 1) throw new IOException("Invalid scaling mode");
        Map<Integer, Integer> densities = new LinkedHashMap<>();
        int count = count(in.readInt());
        for (int i = 0; i < count; i++) {
            int user = user(in.readInt());
            if (densities.put(user, positive(in.readInt())) != null) {
                throw new IOException("Duplicate user");
            }
        }
        if (densities.isEmpty()) throw new IOException("Missing users");
        return new ResolutionEngine.Frame(width, height, scaling, densities);
    }

    private static void writeAnchors(DataOutputStream out,
            Map<Integer, ResolutionEngine.Anchor> anchors) throws IOException {
        out.writeInt(anchors.size());
        for (Map.Entry<Integer, ResolutionEngine.Anchor> entry : anchors.entrySet()) {
            ResolutionEngine.Anchor a = entry.getValue();
            out.writeInt(entry.getKey());
            out.writeInt(a.density);
            out.writeInt(a.width);
            out.writeInt(a.lastDensity);
            out.writeInt(a.lastWidth);
        }
    }

    private static Map<Integer, ResolutionEngine.Anchor> readAnchors(DataInputStream in)
            throws IOException {
        Map<Integer, ResolutionEngine.Anchor> result = new LinkedHashMap<>();
        int count = count(in.readInt());
        for (int i = 0; i < count; i++) {
            int user = user(in.readInt());
            ResolutionEngine.Anchor anchor = new ResolutionEngine.Anchor(positive(in.readInt()),
                    positive(in.readInt()), positive(in.readInt()), positive(in.readInt()));
            if (result.put(user, anchor) != null) throw new IOException("Duplicate anchor");
        }
        return result;
    }

    private static int positive(int value) throws IOException {
        if (value <= 0 || value > 10000) throw new IOException("Invalid display metric");
        return value;
    }

    private static int count(int value) throws IOException {
        if (value < 0 || value > 1000) throw new IOException("Invalid user count");
        return value;
    }

    private static int user(int value) throws IOException {
        if (value < 0) throw new IOException("Invalid user id");
        return value;
    }
}
