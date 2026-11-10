package com.quietweibo.app;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import java.util.List;

/**
 * 关于页：模块版本、目标应用、兼容版本、免责声明、许可证。
 *
 * <p>兼容版本使用嵌套 PreferenceScreen 展示，而不是对话框：版本越来越多时
 * 页面可以自然滚动，并且系统返回键会回到关于页主页。
 */
public class AboutFragment extends PreferenceFragmentCompat {

    private static final String TARGET_PACKAGE = "com.sina.weibo";
    private static final String SOURCE_URL = "https://github.com/fengwenhua/quietweibo";
    private static final String REFERENCE_URL = "https://github.com/yigenhuobah/quietbox";
    private static final String LICENSE_URL =
            "https://github.com/fengwenhua/quietweibo/blob/main/LICENSE";
    private static final String KEY_HIDE_ICON = "about_hide_icon";

    private PreferenceScreen mMainScreen;
    private boolean mShowingVersions;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        mMainScreen = screen;
        setPreferenceScreen(screen);

        Preference module = info(R.string.app_name,
                getString(R.string.about_version, versionName(), versionCode()));
        module.setSelectable(false);
        screen.addPreference(module);

        Preference target = info(R.string.about_target,
                getString(R.string.about_target_value, getString(R.string.app_target_name),
                        TARGET_PACKAGE, VersionGate.SUPPORTED_VERSION));
        target.setSelectable(false);
        screen.addPreference(target);

        Preference compat = info(R.string.about_compat_title,
                getString(R.string.about_compat_summary,
                        VersionGate.testedVersions().size()));
        compat.setOnPreferenceClickListener(p -> {
            showTestedVersions();
            return true;
        });
        screen.addPreference(compat);

        Preference reference = info(R.string.about_reference_title,
                getString(R.string.about_reference_value,
                        "QuietBox", REFERENCE_URL));
        reference.setOnPreferenceClickListener(p -> {
            openUrl(REFERENCE_URL);
            return true;
        });
        screen.addPreference(reference);

        Preference source = info(R.string.about_source_title,
                getString(R.string.about_source_value, SOURCE_URL));
        source.setOnPreferenceClickListener(p -> {
            openUrl(SOURCE_URL);
            return true;
        });
        screen.addPreference(source);

        Preference disclaimer = info(R.string.about_disclaimer_title,
                getString(R.string.about_disclaimer, getString(R.string.app_target_name)));
        disclaimer.setSelectable(false);
        screen.addPreference(disclaimer);

        Preference license = info(R.string.about_license_title,
                getString(R.string.about_license_summary, "MIT"));
        license.setOnPreferenceClickListener(p -> {
            openUrl(LICENSE_URL);
            return true;
        });
        screen.addPreference(license);

        PreferenceCategory iconCategory = new PreferenceCategory(requireContext());
        iconCategory.setTitle(R.string.about_icon_title);
        screen.addPreference(iconCategory);

        MaterialSwitchPreference hideIcon = new MaterialSwitchPreference(requireContext());
        hideIcon.setKey(KEY_HIDE_ICON);
        hideIcon.setTitle(R.string.about_hide_icon_title);
        hideIcon.setSummary(R.string.about_hide_icon_summary);
        hideIcon.setIcon(R.drawable.ic_tab_settings);
        hideIcon.setCheckedValue(LauncherIcon.isHidden(requireContext()));
        hideIcon.setOnPreferenceChangeListener((pref, value) -> {
            if (!(value instanceof Boolean)) {
                return false;
            }
            boolean hide = (Boolean) value;
            if (!LauncherIcon.setHidden(requireContext(), hide)) {
                Toast.makeText(requireContext(), R.string.about_hide_icon_failed,
                        Toast.LENGTH_LONG).show();
                return false;
            }
            Toast.makeText(requireContext(),
                    hide ? R.string.about_hide_icon_toast_hidden
                            : R.string.about_hide_icon_toast_shown,
                    Toast.LENGTH_SHORT).show();
            return true;
        });
        screen.addPreference(hideIcon);
    }

    @Override
    public void onResume() {
        super.onResume();
        Preference p = findPreference(KEY_HIDE_ICON);
        if (p instanceof MaterialSwitchPreference) {
            ((MaterialSwitchPreference) p)
                    .setCheckedValue(LauncherIcon.isHidden(requireContext()));
        }
    }

    private void showTestedVersions() {
        List<VersionGate.TestedVersion> versions = VersionGate.testedVersions();
        String latest = VersionGate.latestTestedVersion();
        PreferenceScreen list = getPreferenceManager().createPreferenceScreen(requireContext());

        Preference back = info(R.string.about_compat_back, null);
        back.setOnPreferenceClickListener(p -> {
            showMainScreen();
            return true;
        });
        list.addPreference(back);

        Preference header = info(R.string.about_compat_dialog_title,
                getString(R.string.about_compat_page_summary, versions.size()));
        header.setSelectable(false);
        list.addPreference(header);

        for (VersionGate.TestedVersion version : versions) {
            Preference row = new Preference(requireContext());
            row.setTitle(version.name);
            row.setSummary(getString(R.string.about_compat_version_summary,
                    version.versionCode,
                    latest.equals(version.name)
                            ? getString(R.string.about_compat_latest) : ""));
            row.setSelectable(false);
            list.addPreference(row);
        }

        setPreferenceScreen(list);
        mShowingVersions = true;
    }

    private void showMainScreen() {
        if (mMainScreen != null) {
            setPreferenceScreen(mMainScreen);
        }
        mShowingVersions = false;
    }

    /**
     * 返回键先交给关于页：兼容版本列表显示时回到关于主页。
     * 由 MainActivity 的返回回调调用，避免和 Activity 的 tab 返回逻辑抢优先级。
     */
    public boolean handleBack() {
        if (!mShowingVersions) {
            return false;
        }
        showMainScreen();
        return true;
    }

    private Preference info(int titleRes, CharSequence summary) {
        Preference p = new Preference(requireContext());
        if (titleRes != 0) {
            p.setTitle(titleRes);
        }
        p.setSummary(summary);
        return p;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            Toast.makeText(requireContext(), R.string.action_open_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String versionName() {
        try {
            String v = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            return v != null ? v : "?";
        } catch (Throwable t) {
            return "?";
        }
    }

    private int versionCode() {
        try {
            PackageInfo pi = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            return (int) pi.getLongVersionCode();
        } catch (Throwable t) {
            return 0;
        }
    }
}
