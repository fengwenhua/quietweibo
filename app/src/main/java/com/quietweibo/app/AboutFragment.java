package com.quietweibo.app;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

/**
 * 关于页：模块版本、目标应用、免责声明、许可证。
 */
public class AboutFragment extends PreferenceFragmentCompat {

    private static final String TARGET_PACKAGE = "com.sina.weibo";
    private static final String SOURCE_URL = "https://github.com/fengwenhua/quietweibo";
    private static final String REFERENCE_URL = "https://github.com/yigenhuobah/quietbox";
    private static final String LICENSE_URL =
            "https://github.com/fengwenhua/quietweibo/blob/main/LICENSE";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
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
    }

    private Preference info(int titleRes, CharSequence summary) {
        Preference p = new Preference(requireContext());
        p.setTitle(titleRes);
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
