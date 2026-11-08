package com.quietweibo.app;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;

/**
 * 设置 tab：状态卡 + 异常横幅 + AD/UI/PV 分组开关 + 页脚。
 *
 * <p>设置页零诊断：这里不展示逐功能 registered/hit/skip，只保留状态卡和聚合横幅；
 * 细节统一放到诊断 tab。
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String TAG = "QuietWeibo";
    private static final String TARGET = "com.sina.weibo";

    /** 两次 refresh 的最小间隔，压掉 onCreatePreferences / onResume / 绑定回调三连。 */
    private static final long REFRESH_MIN_INTERVAL_MS = 1000L;

    private static final ExecutorService STATUS_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "qw-settings-status");
                t.setDaemon(true);
                return t;
            });

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ConfigStore mStore;
    private XposedService mStoreService;
    private long mLastRefreshMs;
    private boolean mHasRefreshed;
    private XposedService mLastRefreshService;
    private Future<?> mStatusFuture;
    private Future<?> mBannerFuture;
    private long mBannerSeq;
    private final Map<String, Long> mWriteSeq = new HashMap<>();
    private final Map<String, Boolean> mConfirmed = new HashMap<>();
    private long mWriteCounter;
    private String mVersionName;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.prefs, rootKey);
        mStoreService = ServiceBridge.get();
        mStore = new ConfigStore(requireContext());
        bindFeatures();
        bindPages();
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroyView() {
        if (mStatusFuture != null) {
            mStatusFuture.cancel(true);
            mStatusFuture = null;
        }
        if (mBannerFuture != null) {
            mBannerFuture.cancel(true);
            mBannerFuture = null;
        }
        mBannerSeq++;
        mWriteSeq.clear();
        mConfirmed.clear();
        mHasRefreshed = false;
        super.onDestroyView();
    }

    /** 把 Feature 的标题/说明/开关接到 XML 声明的偏好项上。 */
    private void bindFeatures() {
        for (Feature f : Feature.values()) {
            if (f.blocked()) {
                continue;
            }
            Preference p = findPreference(f.key);
            if (p == null) {
                Log.e(TAG, "preference missing for " + f.key);
                continue;
            }
            p.setTitle(f.titleRes);
            p.setSummary(f.summaryRes);
            p.setOnPreferenceChangeListener((pref, value) -> {
                if (!(value instanceof Boolean)) {
                    return true;
                }
                return writeBoolean(f.key, (Boolean) value);
            });
        }
    }

    private boolean writeBoolean(String key, boolean requested) {
        long seq = ++mWriteCounter;
        mWriteSeq.put(key, seq);
        boolean accepted = mStore.put(key, requested,
                committed -> onWriteResult(key, requested, seq, committed));
        if (!accepted) {
            Toast.makeText(requireContext(), R.string.action_write_failed,
                    Toast.LENGTH_SHORT).show();
        }
        return accepted;
    }

    private void onWriteResult(String key, boolean requested, long seq, boolean committed) {
        MAIN.post(() -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            Long latest = mWriteSeq.get(key);
            if (latest == null || latest.longValue() != seq) {
                return;
            }
            if (ConfigWritePolicy.keepUiState(true, committed)) {
                mConfirmed.put(key, requested);
                return;
            }
            Toast.makeText(requireContext(), R.string.action_write_failed,
                    Toast.LENGTH_SHORT).show();
            Preference p = findPreference(key);
            if (p instanceof MaterialSwitchPreference) {
                Boolean confirmed = mConfirmed.get(key);
                ((MaterialSwitchPreference) p).setCheckedValue(
                        confirmed != null ? confirmed : !requested);
            }
        });
    }

    private void bindPages() {
        Preference footer = findPreference("footer");
        if (footer != null) {
            footer.setSummary(getString(R.string.footer_restart,
                    getString(R.string.app_target_name))
                    + "\n" + getString(R.string.footer_compat,
                    getString(R.string.app_target_name), VersionGate.SUPPORTED_VERSION));
        }
    }

    /** 重新读取框架状态与开关值。service 绑定后由 MainActivity 调一次。 */
    void refresh() {
        if (getContext() == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        XposedService service = ServiceBridge.get();
        if (mHasRefreshed
                && now - mLastRefreshMs < REFRESH_MIN_INTERVAL_MS
                && service == mLastRefreshService) {
            return;
        }
        mHasRefreshed = true;
        mLastRefreshMs = now;
        mLastRefreshService = service;

        if (!ConfigWritePolicy.reuseStore(mStore != null, mStoreService, service)) {
            mStore = new ConfigStore(requireContext());
            mStoreService = service;
        }
        boolean writable = mStore.remote();

        Preference status = findPreference("status");
        if (status != null) {
            if (service == null) {
                status.setTitle(R.string.status_connecting);
                status.setSummary(getString(R.string.status_detail_connecting,
                        getString(R.string.app_target_name), shownTargetVersion()));
                appendRemoteState(status, writable);
            } else {
                loadStatusAsync(service, writable);
            }
        }

        for (Feature f : Feature.values()) {
            Preference p = findPreference(f.key);
            if (p instanceof MaterialSwitchPreference) {
                MaterialSwitchPreference sw = (MaterialSwitchPreference) p;
                boolean checked = mStore.get(f.key, f.defaultEnabled);
                sw.setCheckedValue(checked);
                mConfirmed.put(f.key, checked);
                sw.setEnabled(writable && !f.blocked());
            }
        }

        updateBanner();
    }

    private void loadStatusAsync(XposedService service, boolean writable) {
        final XposedService bound = service;
        final boolean remoteWritable = writable;
        final String version = versionName();
        final String targetVersion = shownTargetVersion();
        final Activity host = requireActivity();

        if (mStatusFuture != null) {
            mStatusFuture.cancel(false);
        }
        mStatusFuture = STATUS_EXECUTOR.submit(() -> {
            int api = 0;
            boolean scoped = false;
            try {
                api = bound.getApiVersion();
            } catch (Throwable ignored) {
            }
            try {
                List<String> scope = bound.getScope();
                scoped = scope != null && scope.contains(TARGET);
            } catch (Throwable ignored) {
            }
            boolean injected = isTargetRunning(bound);
            final int finalApi = api;
            final boolean finalScoped = scoped;
            final boolean finalInjected = injected;

            if (!isAdded() || getActivity() != host) {
                return;
            }
            host.runOnUiThread(() -> {
                if (!isAdded() || getActivity() != host) {
                    return;
                }
                if (ServiceBridge.get() != bound) {
                    return;
                }
                Preference p = findPreference("status");
                if (p == null) {
                    return;
                }
                String appName = getString(R.string.app_target_name);
                if (!finalScoped) {
                    p.setTitle(R.string.status_inactive);
                    p.setSummary(getString(R.string.status_detail_no_scope,
                            finalApi, appName, targetVersion, version));
                } else if (finalInjected) {
                    p.setTitle(R.string.status_active);
                    p.setSummary(getString(R.string.status_detail_active,
                            finalApi, appName, targetVersion, version));
                } else {
                    p.setTitle(R.string.status_connected);
                    p.setSummary(getString(R.string.status_detail_not_running,
                            finalApi, appName, targetVersion, version));
                }
                appendRemoteState(p, remoteWritable);
                updateBanner();
            });
        });
    }

    private void updateBanner() {
        Preference banner = findPreference("warning");
        if (banner == null || getContext() == null) {
            return;
        }
        String target = targetVersion();
        if (target == null) {
            mBannerSeq++;
            banner.setVisible(false);
            return;
        }
        if (!VersionGate.isSupported(target)) {
            mBannerSeq++;
            banner.setSummary(getString(R.string.status_banner_version, target,
                    VersionGate.SUPPORTED_VERSION));
            banner.setVisible(true);
            return;
        }

        final Activity host = getActivity();
        if (host == null) {
            return;
        }
        final Context app = requireContext().getApplicationContext();
        final ConfigStore store = mStore;
        final String expectedTarget = target;
        final long seq = ++mBannerSeq;
        if (mBannerFuture != null) {
            mBannerFuture.cancel(false);
        }
        mBannerFuture = STATUS_EXECUTOR.submit(() -> {
            int failed = StatusProvider.failedFeatureCount(app, store);
            host.runOnUiThread(() -> {
                if (seq != mBannerSeq || !isAdded() || getView() == null
                        || getActivity() != host) {
                    return;
                }
                if (!expectedTarget.equals(targetVersion())) {
                    return;
                }
                applyBannerCount(failed);
            });
        });
    }

    private void applyBannerCount(int failed) {
        Preference banner = findPreference("warning");
        if (banner == null) {
            return;
        }
        if (failed >= 2) {
            banner.setSummary(getString(R.string.status_banner_failed, failed));
            banner.setVisible(true);
        } else {
            banner.setVisible(false);
        }
    }

    private void appendRemoteState(Preference status, boolean writable) {
        status.setSummary(status.getSummary() + "\n"
                + getString(writable ? R.string.status_remote_ok : R.string.status_remote_fail));
    }

    private boolean isTargetRunning(XposedService service) {
        try {
            List<HookedTarget> targets = service.getRunningTargets();
            if (targets == null) {
                return false;
            }
            for (HookedTarget t : targets) {
                String name = t.getProcessName();
                if (name != null && (name.equals(TARGET) || name.startsWith(TARGET + ":"))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private String targetVersion() {
        try {
            PackageInfo pi = requireContext().getPackageManager().getPackageInfo(TARGET, 0);
            return pi.versionName;
        } catch (Throwable t) {
            return null;
        }
    }

    private String shownTargetVersion() {
        String v = targetVersion();
        return v != null ? v : getString(R.string.status_unknown_version);
    }

    private String versionName() {
        if (mVersionName != null) {
            return mVersionName;
        }
        try {
            String v = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            mVersionName = v != null ? v : "?";
        } catch (Throwable t) {
            mVersionName = "?";
        }
        return mVersionName;
    }
}
