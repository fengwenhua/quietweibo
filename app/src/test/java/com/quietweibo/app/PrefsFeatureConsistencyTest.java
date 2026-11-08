package com.quietweibo.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * prefs.xml 与 Feature 枚举的一致性契约（纯 JVM，不依赖 Robolectric）。
 *
 * <p>非功能 key 白名单：status / warning 是设置页顶部状态卡与警告横幅，
 * footer 是页脚；以后新增非功能 key 必须同步这里，否则测试失败。
 */
public class PrefsFeatureConsistencyTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final Set<String> NON_FEATURE_KEYS = new HashSet<>(Arrays.asList(
            "status",
            "warning",
            "footer"));

    @Test
    public void everyNonBlockedFeatureHasPreference() throws Exception {
        List<String> keys = preferenceKeys();
        for (Feature f : Feature.values()) {
            if (f.blocked()) {
                continue;
            }
            assertTrue("prefs.xml 缺少功能开关: " + f.id + " (" + f.key + ")",
                    keys.contains(f.key));
        }
    }

    @Test
    public void blockedFeaturesAreNotRendered() throws Exception {
        List<String> keys = preferenceKeys();
        for (Feature f : Feature.values()) {
            if (f.blocked()) {
                assertFalse("blocked Feature 不应出现在 prefs.xml: " + f.id,
                        keys.contains(f.key));
            }
        }
    }

    @Test
    public void nonFeatureKeysAreWhitelisted() throws Exception {
        Set<String> featureKeys = featureKeys();
        for (String key : preferenceKeys()) {
            if (featureKeys.contains(key)) {
                continue;
            }
            assertTrue("prefs.xml 出现未登记的非功能 key: " + key
                    + "；如属预期请补充白名单并注释说明",
                    NON_FEATURE_KEYS.contains(key));
        }
    }

    @Test
    public void preferenceKeysAreUnique() throws Exception {
        Set<String> seen = new LinkedHashSet<>();
        for (String key : preferenceKeys()) {
            assertTrue("prefs.xml 中 key 重复: " + key, seen.add(key));
        }
    }

    @Test
    public void whitelistDoesNotShadowFeatureKeys() {
        Set<String> featureKeys = featureKeys();
        for (String key : NON_FEATURE_KEYS) {
            assertFalse("白名单 key 与 Feature 冲突: " + key, featureKeys.contains(key));
        }
    }

    private static Set<String> featureKeys() {
        Set<String> keys = new HashSet<>();
        for (Feature f : Feature.values()) {
            keys.add(f.key);
        }
        return keys;
    }

    /**
     * Gradle 单测的工作目录通常是 app/；从仓库根目录运行时回退到 app/src/...。
     */
    private static File locatePrefsXml() {
        File cwd = new File(System.getProperty("user.dir", "."));
        File[] candidates = {
                new File(cwd, "src/main/res/xml/prefs.xml"),
                new File(cwd, "../app/src/main/res/xml/prefs.xml"),
                new File(cwd, "app/src/main/res/xml/prefs.xml"),
        };
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                return candidate;
            }
        }
        fail("prefs.xml not found; cwd=" + cwd.getAbsolutePath()
                + ", tried: " + Arrays.toString(candidates));
        return null; // unreachable
    }

    private static List<String> preferenceKeys() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(locatePrefsXml());

        List<String> keys = new ArrayList<>();
        NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String key = element.getAttributeNS(ANDROID_NS, "key");
            if (key == null || key.isEmpty()) {
                key = element.getAttribute("android:key");
            }
            if (key != null && !key.isEmpty()) {
                keys.add(key);
            }
        }
        assertFalse("prefs.xml 未解析到任何 android:key", keys.isEmpty());
        return keys;
    }
}
