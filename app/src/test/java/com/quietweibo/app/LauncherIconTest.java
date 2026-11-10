package com.quietweibo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

/** Launcher alias 状态映射与 manifest 契约。 */
public class LauncherIconTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Test
    public void stateMapping() {
        assertEquals(Boolean.FALSE, LauncherIcon.isHiddenState(
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT));
        assertEquals(Boolean.FALSE, LauncherIcon.isHiddenState(
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED));
        assertEquals(Boolean.TRUE, LauncherIcon.isHiddenState(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED));
        assertEquals(Boolean.TRUE, LauncherIcon.isHiddenState(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER));
        assertEquals(Boolean.TRUE, LauncherIcon.isHiddenState(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED));
        assertNull(LauncherIcon.isHiddenState(Integer.MIN_VALUE));
    }

    @Test
    public void manifestDeclaresLauncherAliasAndRecoveryEntry() throws Exception {
        File manifest = new File("src/main/AndroidManifest.xml");
        assertTrue("app 工作目录应存在 AndroidManifest.xml", manifest.isFile());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder().parse(manifest);

        Element alias = null;
        NodeList aliases = doc.getElementsByTagName("activity-alias");
        for (int i = 0; i < aliases.getLength(); i++) {
            Element e = (Element) aliases.item(i);
            if (LauncherIcon.ALIAS_CLASS.equals(e.getAttributeNS(ANDROID_NS, "name"))) {
                alias = e;
                break;
            }
        }
        assertTrue("manifest 缺少 activity-alias: " + LauncherIcon.ALIAS_CLASS, alias != null);
        assertEquals(".MainActivity", alias.getAttributeNS(ANDROID_NS, "targetActivity"));
        assertTrue("alias 必须带 MAIN/LAUNCHER intent-filter", hasLauncher(alias));
        assertFalse("alias 不能默认禁用",
                "false".equals(alias.getAttributeNS(ANDROID_NS, "enabled")));
        assertTrue("MainActivity 必须保留 MODULE_SETTINGS 入口",
                hasModuleSettings(doc));
    }

    private static boolean hasLauncher(Element alias) {
        NodeList filters = alias.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            boolean main = false;
            boolean launcher = false;
            NodeList actions = filter.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                main |= "android.intent.action.MAIN".equals(
                        ((Element) actions.item(j)).getAttributeNS(ANDROID_NS, "name"));
            }
            NodeList categories = filter.getElementsByTagName("category");
            for (int j = 0; j < categories.getLength(); j++) {
                launcher |= "android.intent.category.LAUNCHER".equals(
                        ((Element) categories.item(j)).getAttributeNS(ANDROID_NS, "name"));
            }
            if (main && launcher) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasModuleSettings(Document doc) {
        NodeList activities = doc.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (!".MainActivity".equals(activity.getAttributeNS(ANDROID_NS, "name"))) {
                continue;
            }
            NodeList categories = activity.getElementsByTagName("category");
            for (int j = 0; j < categories.getLength(); j++) {
                if ("de.robv.android.xposed.category.MODULE_SETTINGS".equals(
                        ((Element) categories.item(j)).getAttributeNS(ANDROID_NS, "name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
