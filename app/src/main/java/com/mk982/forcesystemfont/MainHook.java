package com.mk982.forcesystemfont;

import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.os.Build;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "ForceSystemFont";

    private enum FontCategory {
        DEFAULT,
        MONOSPACE,
        SERIF
    }

    // Thread-safe cache: "category_weight_italic" -> Typeface
    private static final Map<String, Typeface> cache = new ConcurrentHashMap<>();

    // Fast-path set to avoid redundant extraction & lookups on hot drawing paths (e.g. Paint.setTypeface)
    private static final Set<Typeface> systemTypefaces = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final ThreadLocal<Boolean> inHook = ThreadLocal.withInitial(() -> false);

    private static final Pattern NUMERIC_WEIGHT_PATTERN =
            Pattern.compile("(?:^|[\\W_])(100|200|300|400|500|600|700|800|900|950)(?:[\\W_]|$)");

    static {
        try {
            if (Typeface.DEFAULT != null) systemTypefaces.add(Typeface.DEFAULT);
            if (Typeface.DEFAULT_BOLD != null) systemTypefaces.add(Typeface.DEFAULT_BOLD);
            if (Typeface.SANS_SERIF != null) systemTypefaces.add(Typeface.SANS_SERIF);
            if (Typeface.SERIF != null) systemTypefaces.add(Typeface.SERIF);
            if (Typeface.MONOSPACE != null) systemTypefaces.add(Typeface.MONOSPACE);
        } catch (Throwable ignored) {
        }
    }

    private static Typeface getSystemTypeface(int weight, boolean italic) {
        return getSystemTypeface(FontCategory.DEFAULT, weight, italic);
    }

    private static Typeface getSystemTypeface(FontCategory category, int weight, boolean italic) {
        // Strictly clamp weight to [1, 1000] for Android 14/15/16/17 compliance
        weight = Math.max(1, Math.min(1000, weight));
        String key = category.name() + "_" + weight + "_" + italic;
        Typeface cached = cache.get(key);
        if (cached != null) return cached;

        Typeface tf = null;
        inHook.set(true);
        try {
            Typeface base;
            switch (category) {
                case MONOSPACE:
                    base = Typeface.MONOSPACE;
                    break;
                case SERIF:
                    base = Typeface.SERIF;
                    break;
                default:
                    base = Typeface.DEFAULT;
                    break;
            }

            if (Build.VERSION.SDK_INT >= 28) {
                tf = Typeface.create(base, weight, italic);
            } else {
                String targetFamily;
                switch (category) {
                    case MONOSPACE:
                        targetFamily = "monospace";
                        break;
                    case SERIF:
                        targetFamily = "serif";
                        break;
                    default:
                        if (weight <= 200) {
                            targetFamily = "sans-serif-thin";
                        } else if (weight <= 300) {
                            targetFamily = "sans-serif-light";
                        } else if (weight <= 550) {
                            targetFamily = "sans-serif-medium";
                        } else {
                            targetFamily = "sans-serif";
                        }
                        break;
                }

                int style;
                if (weight >= 600) {
                    style = italic ? Typeface.BOLD_ITALIC : Typeface.BOLD;
                } else {
                    style = italic ? Typeface.ITALIC : Typeface.NORMAL;
                }

                tf = Typeface.create(targetFamily, style);
            }
        } catch (Throwable t) {
            // Fallback safely to platform defaults
            tf = (category == FontCategory.MONOSPACE) ? Typeface.MONOSPACE :
                 (category == FontCategory.SERIF) ? Typeface.SERIF : Typeface.DEFAULT;
        } finally {
            inHook.set(false);
        }

        if (tf != null) {
            cache.put(key, tf);
            systemTypefaces.add(tf);
        }
        return tf;
    }

    private static int inferWeightFromName(String name) {
        if (name == null) return 400;
        String l = name.toLowerCase();

        // Check for explicit 3-digit numeric weights (e.g. font-700.ttf, _600, w500, 300)
        Matcher m = NUMERIC_WEIGHT_PATTERN.matcher(l);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }

        // Check standard keywords in descending order of specificity
        if (l.contains("extrablack") || l.contains("ultra-black") || l.contains("ultrablack")) return 950;
        if (l.contains("black") || l.contains("heavy")) return 900;
        if (l.contains("extrabold") || l.contains("extra-bold") || l.contains("ultrabold") || l.contains("ultra-bold")) return 800;
        if (l.contains("semibold") || l.contains("semi-bold") || l.contains("demibold") || l.contains("demi-bold") || l.contains("demi")) return 600;
        if (l.contains("bold")) return 700;
        if (l.contains("medium") || l.contains("med")) return 500;
        if (l.contains("extralight") || l.contains("extra-light") || l.contains("ultralight") || l.contains("ultra-light")) return 200;
        if (l.contains("light")) return 300;
        if (l.contains("thin") || l.contains("hairline")) return 100;
        if (l.contains("regular") || l.contains("normal") || l.contains("book")) return 400;

        return 400;
    }

    private static boolean isItalicFromName(String name) {
        if (name == null) return false;
        String l = name.toLowerCase();
        return l.contains("italic") || l.contains("oblique") ||
               l.contains("-it.") || l.contains("_it.") ||
               l.contains("-ita.") || l.contains("_ita.");
    }

    private static FontCategory inferCategoryFromName(String name) {
        if (name == null) return FontCategory.DEFAULT;
        String l = name.toLowerCase();
        if (l.contains("mono") || l.contains("code") || l.contains("courier") || l.contains("consolas") || l.contains("menlo")) {
            return FontCategory.MONOSPACE;
        }
        if ((l.contains("serif") && !l.contains("sans-serif") && !l.contains("sansserif")) ||
            l.contains("times") || l.contains("georgia") || l.contains("cambria")) {
            return FontCategory.SERIF;
        }
        return FontCategory.DEFAULT;
    }

    private static int extractWeight(Typeface tf) {
        if (tf == null) return 400;
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                int w = tf.getWeight();
                if (w > 0) return Math.max(1, Math.min(1000, w));
            }
            return (tf.getStyle() & Typeface.BOLD) != 0 ? 700 : 400;
        } catch (Throwable ignored) {
            return 400;
        }
    }

    private static boolean extractItalic(Typeface tf) {
        if (tf == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= 28) return tf.isItalic();
            return (tf.getStyle() & Typeface.ITALIC) != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int weightFromStyle(int style) {
        return (style & Typeface.BOLD) != 0 ? 700 : 400;
    }

    private static void safeHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {
        try {
            XposedHelpers.findAndHookMethod(className, classLoader, methodName, parameterTypesAndCallback);
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Could not hook " + className + "#" + methodName + ": " + t.getMessage());
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        // HOOK 1: Typeface.create(String family, int style)
        safeHookMethod("android.graphics.Typeface", lpparam.classLoader,
                "create", String.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (inHook.get()) return;
                        String family = (String) param.args[0];
                        int style = (int) param.args[1];

                        boolean italic = (style & Typeface.ITALIC) != 0;
                        int weight = weightFromStyle(style);

                        FontCategory category = inferCategoryFromName(family);
                        if (family != null) {
                            int inferred = inferWeightFromName(family);
                            if (inferred != 400) weight = inferred;
                            if (isItalicFromName(family)) italic = true;
                        }

                        param.setResult(getSystemTypeface(category, weight, italic));
                    }
                });

        // HOOK 2: Typeface.create(Typeface family, int style)
        safeHookMethod("android.graphics.Typeface", lpparam.classLoader,
                "create", Typeface.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (inHook.get()) return;
                        Typeface baseTf = (Typeface) param.args[0];
                        int style = (int) param.args[1];

                        int baseWeight = extractWeight(baseTf);
                        boolean baseItalic = extractItalic(baseTf);

                        boolean requestedItalic = (style & Typeface.ITALIC) != 0;
                        boolean requestedBold = (style & Typeface.BOLD) != 0;

                        int finalWeight = requestedBold ? Math.max(baseWeight, 700) : baseWeight;
                        boolean finalItalic = baseItalic || requestedItalic;

                        FontCategory category = (baseTf == Typeface.MONOSPACE) ? FontCategory.MONOSPACE :
                                                 (baseTf == Typeface.SERIF) ? FontCategory.SERIF : FontCategory.DEFAULT;

                        param.setResult(getSystemTypeface(category, finalWeight, finalItalic));
                    }
                });

        // HOOK 3: Typeface.create(Typeface family, int weight, boolean italic) [API 28+]
        if (Build.VERSION.SDK_INT >= 28) {
            safeHookMethod("android.graphics.Typeface", lpparam.classLoader,
                    "create", Typeface.class, int.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (inHook.get()) return;
                            Typeface baseTf = (Typeface) param.args[0];
                            int weight = (int) param.args[1];
                            boolean italic = (boolean) param.args[2];

                            FontCategory category = (baseTf == Typeface.MONOSPACE) ? FontCategory.MONOSPACE :
                                                     (baseTf == Typeface.SERIF) ? FontCategory.SERIF : FontCategory.DEFAULT;

                            param.setResult(getSystemTypeface(category, weight, italic));
                        }
                    });
        }

        // HOOK 4: Typeface.create(String family, int weight, boolean italic) [API 34+ / Android 14, 15, 16, 17]
        if (Build.VERSION.SDK_INT >= 34) {
            safeHookMethod("android.graphics.Typeface", lpparam.classLoader,
                    "create", String.class, int.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (inHook.get()) return;
                            String family = (String) param.args[0];
                            int weight = (int) param.args[1];
                            boolean italic = (boolean) param.args[2];

                            FontCategory category = inferCategoryFromName(family);
                            param.setResult(getSystemTypeface(category, weight, italic));
                        }
                    });
        }

        // HOOK 5: Typeface.createFromAsset(AssetManager, String path)
        safeHookMethod("android.graphics.Typeface", lpparam.classLoader,
                "createFromAsset",
                AssetManager.class, String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (inHook.get()) return;
                        String path = (String) param.args[1];
                        int weight = inferWeightFromName(path);
                        boolean italic = isItalicFromName(path);
                        FontCategory category = inferCategoryFromName(path);
                        param.setResult(getSystemTypeface(category, weight, italic));
                    }
                });

        // HOOK 6: Typeface.createFromFile(String path)
        safeHookMethod("android.graphics.Typeface", lpparam.classLoader,
                "createFromFile", String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (inHook.get()) return;
                        String path = (String) param.args[0];
                        int weight = inferWeightFromName(path);
                        boolean italic = isItalicFromName(path);
                        FontCategory category = inferCategoryFromName(path);
                        param.setResult(getSystemTypeface(category, weight, italic));
                    }
                });

        // HOOK 7: Typeface.createFromFile(File path) [NEW]
        safeHookMethod("android.graphics.Typeface", lpparam.classLoader,
                "createFromFile", File.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (inHook.get()) return;
                        File file = (File) param.args[0];
                        String name = file != null ? file.getName() : null;
                        int weight = inferWeightFromName(name);
                        boolean italic = isItalicFromName(name);
                        FontCategory category = inferCategoryFromName(name);
                        param.setResult(getSystemTypeface(category, weight, italic));
                    }
                });

        // HOOK 8: Typeface.Builder.build() [API 26+] [NEW]
        if (Build.VERSION.SDK_INT >= 26) {
            safeHookMethod("android.graphics.Typeface$Builder", lpparam.classLoader,
                    "build",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (inHook.get()) return;
                            Typeface tf = (Typeface) param.getResult();
                            if (tf == null || systemTypefaces.contains(tf)) return;

                            int weight = extractWeight(tf);
                            boolean italic = extractItalic(tf);
                            FontCategory category = (tf == Typeface.MONOSPACE) ? FontCategory.MONOSPACE :
                                                     (tf == Typeface.SERIF) ? FontCategory.SERIF : FontCategory.DEFAULT;
                            param.setResult(getSystemTypeface(category, weight, italic));
                        }
                    });
        }

        // HOOK 9: Typeface.CustomFallbackBuilder.build() [API 29+] [NEW]
        if (Build.VERSION.SDK_INT >= 29) {
            safeHookMethod("android.graphics.Typeface$CustomFallbackBuilder", lpparam.classLoader,
                    "build",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (inHook.get()) return;
                            Typeface tf = (Typeface) param.getResult();
                            if (tf == null || systemTypefaces.contains(tf)) return;

                            int weight = extractWeight(tf);
                            boolean italic = extractItalic(tf);
                            FontCategory category = (tf == Typeface.MONOSPACE) ? FontCategory.MONOSPACE :
                                                     (tf == Typeface.SERIF) ? FontCategory.SERIF : FontCategory.DEFAULT;
                            param.setResult(getSystemTypeface(category, weight, italic));
                        }
                    });
        }

        // HOOK 10: Resources.getFont(int id) [API 26+] [NEW]
        if (Build.VERSION.SDK_INT >= 26) {
            safeHookMethod("android.content.res.Resources", lpparam.classLoader,
                    "getFont", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (inHook.get()) return;
                            Typeface tf = (Typeface) param.getResult();
                            if (tf == null || systemTypefaces.contains(tf)) return;

                            int weight = extractWeight(tf);
                            boolean italic = extractItalic(tf);
                            FontCategory category = (tf == Typeface.MONOSPACE) ? FontCategory.MONOSPACE :
                                                     (tf == Typeface.SERIF) ? FontCategory.SERIF : FontCategory.DEFAULT;
                            param.setResult(getSystemTypeface(category, weight, italic));
                        }
                    });
        }

        // HOOK 11: TypedArray.getFont(int index) [API 26+] [NEW]
        if (Build.VERSION.SDK_INT >= 26) {
            safeHookMethod("android.content.res.TypedArray", lpparam.classLoader,
                    "getFont", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (inHook.get()) return;
                            Typeface tf = (Typeface) param.getResult();
                            if (tf == null || systemTypefaces.contains(tf)) return;

                            int weight = extractWeight(tf);
                            boolean italic = extractItalic(tf);
                            FontCategory category = (tf == Typeface.MONOSPACE) ? FontCategory.MONOSPACE :
                                                     (tf == Typeface.SERIF) ? FontCategory.SERIF : FontCategory.DEFAULT;
                            param.setResult(getSystemTypeface(category, weight, italic));
                        }
                    });
        }

        // HOOK 12: Paint.setTypeface(Typeface) [High-frequency optimized]
        safeHookMethod("android.graphics.Paint", lpparam.classLoader,
                "setTypeface", Typeface.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (inHook.get()) return;
                        Typeface tf = (Typeface) param.args[0];
                        if (tf == null || systemTypefaces.contains(tf)) return;

                        int weight = extractWeight(tf);
                        boolean italic = extractItalic(tf);
                        FontCategory category = (tf == Typeface.MONOSPACE) ? FontCategory.MONOSPACE :
                                                 (tf == Typeface.SERIF) ? FontCategory.SERIF : FontCategory.DEFAULT;
                        param.args[0] = getSystemTypeface(category, weight, italic);
                    }
                });

        // HOOK 13: TextView.setTypeface(Typeface)
        safeHookMethod("android.widget.TextView", lpparam.classLoader,
                "setTypeface", Typeface.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (inHook.get()) return;
                        Typeface tf = (Typeface) param.args[0];
                        if (tf == null || systemTypefaces.contains(tf)) return;

                        int weight = extractWeight(tf);
                        boolean italic = extractItalic(tf);
                        FontCategory category = (tf == Typeface.MONOSPACE) ? FontCategory.MONOSPACE :
                                                 (tf == Typeface.SERIF) ? FontCategory.SERIF : FontCategory.DEFAULT;
                        param.args[0] = getSystemTypeface(category, weight, italic);
                    }
                });

        // HOOK 14: TextView.setTypeface(Typeface, int style)
        safeHookMethod("android.widget.TextView", lpparam.classLoader,
                "setTypeface", Typeface.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (inHook.get()) return;
                        int style = (int) param.args[1];
                        Typeface baseTf = (Typeface) param.args[0];

                        int baseWeight = extractWeight(baseTf);
                        boolean baseItalic = extractItalic(baseTf);

                        boolean requestedItalic = (style & Typeface.ITALIC) != 0;
                        boolean requestedBold = (style & Typeface.BOLD) != 0;

                        int finalWeight = requestedBold ? Math.max(baseWeight, 700) : baseWeight;
                        boolean finalItalic = baseItalic || requestedItalic;

                        FontCategory category = (baseTf == Typeface.MONOSPACE) ? FontCategory.MONOSPACE :
                                                 (baseTf == Typeface.SERIF) ? FontCategory.SERIF : FontCategory.DEFAULT;

                        param.args[0] = getSystemTypeface(category, finalWeight, finalItalic);
                        param.args[1] = finalItalic ? Typeface.ITALIC : Typeface.NORMAL;
                    }
                });
    }
}
