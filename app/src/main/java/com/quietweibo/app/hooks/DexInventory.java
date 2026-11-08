package com.quietweibo.app.hooks;

import android.app.Application;
import android.content.pm.ApplicationInfo;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 轻量 DEX 类名清单：不加载/不执行目标代码，只读 APK 的 classes*.dex 头。
 *
 * <p>用于跨版本动态适配：微博更新后类名/方法可能变化，但官方 APK 的 DEX
 * 一定在进程启动前可读。扫描只解析 string_ids/type_ids/class_defs，速度在
 * 数百毫秒级，不依赖 DexKit/隐藏 API。
 */
final class DexInventory {

    private static volatile List<String> sCache;

    private DexInventory() {
    }

    static List<String> classNames(ClassLoader cl) {
        List<String> cached = sCache;
        if (cached != null) {
            return cached;
        }
        synchronized (DexInventory.class) {
            cached = sCache;
            if (cached != null) {
                return cached;
            }
            Set<String> out = new LinkedHashSet<>();
            for (File apk : apkFiles(cl)) {
                scan(apk, out);
            }
            cached = new ArrayList<>(out);
            sCache = cached;
            return cached;
        }
    }

    private static List<File> apkFiles(ClassLoader cl) {
        List<File> files = new ArrayList<>();
        try {
            Class<?> base = Class.forName("dalvik.system.BaseDexClassLoader");
            java.lang.reflect.Field pathListField = base.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(cl);
            if (pathList != null) {
                java.lang.reflect.Field elementsField =
                        pathList.getClass().getDeclaredField("dexElements");
                elementsField.setAccessible(true);
                Object elements = elementsField.get(pathList);
                if (elements instanceof Object[]) {
                    for (Object element : (Object[]) elements) {
                        if (element == null) {
                            continue;
                        }
                        for (String field : new String[]{"path", "zip"}) {
                            try {
                                java.lang.reflect.Field f =
                                        element.getClass().getDeclaredField(field);
                                f.setAccessible(true);
                                Object value = f.get(element);
                                if (value instanceof File) {
                                    File file = (File) value;
                                    if (file.exists() && !files.contains(file)) {
                                        files.add(file);
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (files.isEmpty()) {
            try {
                Class<?> at = Class.forName("android.app.ActivityThread");
                Object raw = at.getMethod("currentApplication").invoke(null);
                Application app = raw instanceof Application ? (Application) raw : null;
                if (app != null) {
                    ApplicationInfo info = app.getApplicationInfo();
                    add(files, info.sourceDir);
                    if (info.splitSourceDirs != null) {
                        for (String split : info.splitSourceDirs) {
                            add(files, split);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return files;
    }

    private static void add(List<File> files, String path) {
        if (path == null) {
            return;
        }
        File f = new File(path);
        if (f.exists() && !files.contains(f)) {
            files.add(f);
        }
    }

    private static void scan(File file, Set<String> out) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                if (child.getName().endsWith(".dex")) {
                    try {
                        scanDex(readAll(child), out);
                    } catch (Throwable ignored) {
                    }
                }
            }
            return;
        }
        if (file.getName().endsWith(".dex")) {
            try {
                scanDex(readAll(file), out);
            } catch (Throwable ignored) {
            }
            return;
        }
        try (ZipFile zip = new ZipFile(file)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".dex") || !name.startsWith("classes")) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    scanDex(readAll(in, (int) Math.max(16, entry.getSize())), out);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static byte[] readAll(File file) throws Exception {
        try (InputStream in = new java.io.FileInputStream(file)) {
            return readAll(in, (int) Math.max(16, file.length()));
        }
    }

    private static byte[] readAll(InputStream in, int hint) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(
                Math.max(1024, Math.min(hint, 16 * 1024 * 1024)));
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static void scanDex(byte[] b, Set<String> out) {
        if (b == null || b.length < 112 || b[0] != 'd' || b[1] != 'e' || b[2] != 'x') {
            return;
        }
        try {
            int stringIdsSize = u32(b, 56);
            int stringIdsOff = u32(b, 60);
            int typeIdsSize = u32(b, 64);
            int typeIdsOff = u32(b, 68);
            int classDefsSize = u32(b, 96);
            int classDefsOff = u32(b, 100);
            if (stringIdsOff <= 0 || typeIdsOff <= 0 || classDefsOff <= 0) {
                return;
            }
            for (int i = 0; i < classDefsSize; i++) {
                int classIdx = u32(b, classDefsOff + 32 * i);
                if (classIdx < 0 || classIdx >= typeIdsSize) {
                    continue;
                }
                int typeIdx = u32(b, typeIdsOff + 4 * classIdx);
                if (typeIdx < 0 || typeIdx >= stringIdsSize) {
                    continue;
                }
                int strOff = u32(b, stringIdsOff + 4 * typeIdx);
                if (strOff <= 0 || strOff >= b.length) {
                    continue;
                }
                int p = strOff;
                while (p < b.length && (b[p] & 0x80) != 0) {
                    p++;
                }
                p++;
                int end = p;
                while (end < b.length && b[end] != 0) {
                    end++;
                }
                if (p >= end) {
                    continue;
                }
                String desc = new String(b, p, end - p, java.nio.charset.StandardCharsets.UTF_8);
                if (desc.startsWith("L") && desc.endsWith(";")) {
                    out.add(desc.substring(1, desc.length() - 1).replace('/', '.'));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static int u32(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8)
                | ((b[o + 2] & 0xff) << 16) | ((b[o + 3] & 0xff) << 24);
    }
}
