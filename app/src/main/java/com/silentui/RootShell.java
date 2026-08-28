package com.silentui;

import java.io.DataOutputStream;
import java.io.InputStream;

public final class RootShell {

    private static SuSession session;

    private RootShell() {}

    /** Returns true if a usable root shell can be obtained. */
    public static boolean available() {
        String out = exec("id", 3000);
        return out != null && out.contains("uid=0");
    }

    public static synchronized void ensureSession() {
        if (session != null && session.alive) {
            return;
        }
        SuSession s = new SuSession();
        if (s.open()) {
            session = s;
            LogBus.log("root 会话已建立");
        } else {
            session = null;
            LogBus.log("root 会话建立失败");
        }
    }

    public static synchronized boolean sessionAlive() {
        return session != null && session.alive;
    }

    /** Fire-and-forget command, e.g. "input keyevent 4". */
    public static void send(String cmd) {
        if (session != null && session.alive) {
            session.send(cmd);
            return;
        }
        execAsync(cmd);
    }

    /** Run a command to completion and return its stdout (or null on failure). */
    public static String exec(String cmd, int timeoutMs) {
        SuSession s = new SuSession();
        if (!s.open()) {
            return null;
        }
        String out = s.exec(cmd, timeoutMs);
        s.close();
        return out;
    }

    private static void execAsync(final String cmd) {
        Thread t = new Thread(() -> {
            try {
                Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
                p.waitFor();
            } catch (Throwable ignored) {
            }
        }, "su-async");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void close() {
        if (session != null) {
            session.close();
            session = null;
        }
    }

    /** A persistent interactive root shell (one su grant, low-latency injection). */
    static final class SuSession {
        Process proc;
        DataOutputStream out;
        InputStream in;
        volatile boolean alive;
        final StringBuilder buf = new StringBuilder();
        final Object lock = new Object();
        long seq = 0;

        boolean open() {
            try {
                proc = new ProcessBuilder("su").redirectErrorStream(true).start();
                out = new DataOutputStream(proc.getOutputStream());
                in = proc.getInputStream();
                alive = true;
                Thread d = new Thread(this::drain, "su-drain");
                d.setDaemon(true);
                d.start();
                String marker = nextMarker();
                write("echo " + marker);
                return waitFor(marker, 5000) != null;
            } catch (Throwable t) {
                alive = false;
                return false;
            }
        }

        void send(String cmd) {
            write(cmd);
        }

        String exec(String cmd, int timeoutMs) {
            String marker = nextMarker();
            write(cmd + "; echo " + marker);
            return waitFor(marker, timeoutMs);
        }

        void close() {
            alive = false;
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (proc != null) {
                    proc.destroy();
                }
            } catch (Throwable ignored) {
            }
        }

        private String nextMarker() {
            return "SUOK_" + (++seq) + "_" + System.nanoTime();
        }

        private void write(String s) {
            try {
                out.writeBytes(s + "\n");
                out.flush();
            } catch (Throwable ignored) {
            }
        }

        private void drain() {
            byte[] b = new byte[1024];
            try {
                int n;
                while (alive && (n = in.read(b)) != -1) {
                    synchronized (lock) {
                        buf.append(new String(b, 0, n, "UTF-8"));
                        if (buf.length() > 16384) {
                            buf.delete(0, buf.length() - 8192);
                        }
                        lock.notifyAll();
                    }
                }
            } catch (Throwable ignored) {
            }
            alive = false;
        }

        private String waitFor(String marker, int timeoutMs) {
            long end = System.currentTimeMillis() + timeoutMs;
            synchronized (lock) {
                while (true) {
                    int idx = buf.indexOf(marker);
                    if (idx >= 0) {
                        String before = buf.substring(0, idx);
                        buf.delete(0, idx + marker.length());
                        return before;
                    }
                    long now = System.currentTimeMillis();
                    if (now >= end) {
                        return null;
                    }
                    try {
                        lock.wait(Math.max(1, end - now));
                    } catch (InterruptedException e) {
                        return null;
                    }
                }
            }
        }
    }
}
