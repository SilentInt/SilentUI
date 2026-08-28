package com.silentui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TouchDevice {

    private static final Pattern MAX_PAT = Pattern.compile("max\\s+(\\d+)");

    public static final class Info {
        public final String node;
        public final String name;
        public final boolean multiTouch;
        public final int maxX;
        public final int maxY;

        Info(String node, String name, boolean multiTouch, int maxX, int maxY) {
            this.node = node;
            this.name = name;
            this.multiTouch = multiTouch;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        @Override
        public String toString() {
            return node + "  " + name + (multiTouch ? " [多点]" : " [单点]");
        }
    }

    private TouchDevice() {}

    /** Discover touchscreen candidates via `getevent -p`. */
    public static List<Info> discover() {
        List<Info> list = new ArrayList<>();
        String out = RootShell.exec("getevent -p 2>/dev/null", 6000);
        if (out == null || out.trim().isEmpty()) {
            out = RootShell.exec("/system/bin/getevent -p 2>/dev/null", 6000);
        }
        if (out == null) {
            return list;
        }

        String curNode = null;
        String curName = "";
        boolean hasMtX = false, hasMtY = false, hasX = false, hasY = false;
        int maxMtX = 0, maxMtY = 0, maxX = 0, maxY = 0;

        for (String raw : out.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("add device")) {
                if (curNode != null && (hasMtX || (hasX && hasY))) {
                    list.add(new Info(curNode, curName, hasMtX && hasMtY,
                            hasMtX ? maxMtX : maxX, hasMtY ? maxMtY : maxY));
                }
                int idx = line.indexOf('/');
                curNode = idx >= 0 ? line.substring(idx).trim() : null;
                curName = "";
                hasMtX = hasMtY = hasX = hasY = false;
                maxMtX = maxMtY = maxX = maxY = 0;
            } else if (line.startsWith("name:")) {
                curName = line.substring("name:".length()).replace("\"", "").trim();
            } else if (line.contains(": value")) {
                int sp = line.indexOf(':');
                String code = line.substring(0, sp).trim();
                int m = parseMax(line);
                if ("0035".equals(code)) {
                    hasMtX = true;
                    maxMtX = m;
                } else if ("0036".equals(code)) {
                    hasMtY = true;
                    maxMtY = m;
                } else if ("0000".equals(code)) {
                    hasX = true;
                    maxX = m;
                } else if ("0001".equals(code)) {
                    hasY = true;
                    maxY = m;
                }
            }
        }
        if (curNode != null && (hasMtX || (hasX && hasY))) {
            list.add(new Info(curNode, curName, hasMtX && hasMtY,
                    hasMtX ? maxMtX : maxX, hasMtY ? maxMtY : maxY));
        }
        return list;
    }

    public static Info pickBest(List<Info> list, String preferredNode) {
        if (list.isEmpty()) {
            return null;
        }
        if (preferredNode != null && !preferredNode.isEmpty()) {
            for (Info i : list) {
                if (preferredNode.equals(i.node)) {
                    return i;
                }
            }
        }
        for (Info i : list) {
            if (i.multiTouch) {
                return i;
            }
        }
        return list.get(0);
    }

    /** Size of struct input_event: 24 bytes on 64-bit kernels, 16 on 32-bit. */
    public static int structSize() {
        String arch = RootShell.exec("uname -m", 3000);
        if (arch != null && arch.contains("64")) {
            return 24;
        }
        return 16;
    }

    private static int parseMax(String line) {
        Matcher mm = MAX_PAT.matcher(line);
        return mm.find() ? Integer.parseInt(mm.group(1)) : 0;
    }
}
