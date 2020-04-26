/*
 * Copyright (c) 2020 Cobo
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * in the file COPYING.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.cobo.cold.ui.fragment.setting;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.fingerprint.Fingerprint;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.allenliu.badgeview.BadgeFactory;
import com.allenliu.badgeview.BadgeView;
import com.android.internal.app.LocalePicker;
import com.cobo.cold.R;
import com.cobo.cold.Utilities;
import com.cobo.cold.callables.ResetCallable;
import com.cobo.cold.config.FeatureFlags;
import com.cobo.cold.databinding.CommonModalBinding;
import com.cobo.cold.fingerprint.FingerprintKit;
import com.cobo.cold.ui.MainActivity;
import com.cobo.cold.ui.SetupVaultActivity;
import com.cobo.cold.ui.modal.ModalDialog;
import com.cobo.cold.ui.modal.ProgressModalDialog;
import com.cobo.cold.ui.preference.SimplePreference;
import com.cobo.cold.ui.preference.SwitchPreference;
import com.cobo.cold.ui.views.AuthenticateModal;
import com.cobo.cold.ui.views.UpdatingHelper;
import com.cobo.cold.update.data.UpdateManifest;
import com.cobo.cold.util.DataCleaner;
import com.cobo.cold.viewmodel.UpdatingViewModel;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;

import static com.cobo.cold.Utilities.IS_SETUP_VAULT;
import static com.cobo.cold.Utilities.SHARED_PREFERENCES_KEY;
import static com.cobo.cold.ui.fragment.Constants.KEY_NAV_ID;
import static com.cobo.cold.ui.fragment.setting.LicenseFragment.KEY_TITLE;
import static com.cobo.cold.ui.fragment.setting.LicenseFragment.KEY_URL;
import static com.cobo.cold.ui.fragment.setup.SetPasswordFragment.PASSWORD;

public class MainPreferenceFragment extends PreferenceFragmentCompat {

    private static final String SETTING_PATTERN_UNLOCK = "setting_pattern_unlock";
    private static final String SETTING_CHANGE_PWD = "setting_change_pwd";
    private static final String SETTING_SYSTEM = "setting_system";
    private static final String SETTING_WHITE_LIST = "setting_white_list";
    private static final String SETTING_MNEMONIC_ENTRY = "setting_mnemonic_entry";
    private static final String SETTING_SAFETY_INSTRUCTION = "setting_safety_instruction";
    private static final String SETTING_LICENSE = "setting_license";
    private static final String SETTING_PRIVACY_POLICY = "setting_privacy_policy";
    private static final String SETTING_FACTORY_RESET = "setting_factory_reset";
    private static final String SETTING_VERSION = "setting_version";
    private static final String SETTING_FINGERPRINT = "setting_fingerprint";
    private static final String SETTING_PASSPHRASE = "setting_passphrase";

    private SwitchPreference switchPreference;
    private SimplePreference versionPreference;

    private BadgeView patternBadgeView;
    private BadgeView fingerprintBadgeView;

    private SimplePreference fingerprintPreference;

    private UpdateManifest manifest;

    private UpdatingViewModel viewModel;

    protected AppCompatActivity mActivity;


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (AppCompatActivity) context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(SHARED_PREFERENCES_KEY);
        addPreferencesFromResource(R.xml.main_preference);
        SimplePreference whiteListPreference = findPreference(SETTING_WHITE_LIST);
        if (!FeatureFlags.ENABLE_WHITE_LIST && whiteListPreference != null) {
            getPreferenceScreen().removePreference(whiteListPreference);
        }
        viewModel = ViewModelProviders.of(mActivity).get(UpdatingViewModel.class);
        subscribeUpdateChecking();
        fingerprintPreference = findPreference(SETTING_FINGERPRINT);
        if (!FingerprintKit.isHardwareDetected(mActivity)) {
            getPreferenceScreen().removePreference(fingerprintPreference);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        versionPreference = findPreference(SETTING_VERSION);
        if (versionPreference != null) {
            versionPreference.setTitle(getString(R.string.version) + getAppVersion(mActivity));
        }

        switchPreference = findPreference(SETTING_PATTERN_UNLOCK);
        if (switchPreference != null) {
            switchPreference.setChecked(Utilities.isPatternUnlock(mActivity));

        }

        Looper.getMainLooper().getQueue().addIdleHandler(() -> {

            if (!Utilities.hasUserClickPatternLock(mActivity)) {
                patternBadgeView = BadgeFactory.create(mActivity)
                        .setWidthAndHeight(10, 10)
                        .setBadgeBackground(Color.RED)
                        .setBadgeGravity(Gravity.END | Gravity.TOP)
                        .setShape(BadgeView.SHAPE_CIRCLE)
                        .setSpace(10, 0)
                        .bind(switchPreference.getViewHolder().findViewById(android.R.id.title));
            }

            if (fingerprintPreference != null
                    && !Utilities.hasUserClickFingerprint(mActivity)) {
                fingerprintBadgeView = BadgeFactory.create(mActivity)
                        .setWidthAndHeight(10, 10)
                        .setBadgeBackground(Color.RED)
                        .setBadgeGravity(Gravity.END | Gravity.TOP)
                        .setShape(BadgeView.SHAPE_CIRCLE)
                        .setSpace(10, 0)
                        .bind(fingerprintPreference.getViewHolder().findViewById(android.R.id.title));
            }
            return false;
        });
    }

    private void subscribeUpdateChecking() {
        viewModel.getUpdateManifest().observe(this, updateManifest -> {
            manifest = updateManifest;
            updateVersionPreference();
        });
    }

    private void update() {
        new UpdatingHelper(mActivity,true);
    }

    private static void restartApplication(AppCompatActivity activity) {
        final Intent intent = ((Context) activity).getPackageManager().getLaunchIntentForPackage(((Context) activity).getPackageName());
        Objects.requireNonNull(intent).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private void updateVersionPreference() {
        versionPreference = findPreference(SETTING_VERSION);
        if (versionPreference != null) {
            versionPreference.setRemindText(manifest != null ? getString(R.string.updatable) : "");
        }
    }

    private String getAppVersion(Context context) {
        String version = "";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return version;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        switch (preference.getKey()) {
            case SETTING_CHANGE_PWD:
                handleChangePassword();
                break;
            case SETTING_PATTERN_UNLOCK:
                if (patternBadgeView != null) {
                    Utilities.setUserClickPatternLock(mActivity);
                    patternBadgeView.unbind();
                    patternBadgeView = null;
                    ((MainActivity) mActivity).updateBadge();
                }
                handleSwitchPatternUnlock();
                break;
            case SETTING_FINGERPRINT:
                if (fingerprintBadgeView != null) {
                    Utilities.setUserClickFingerprint(mActivity);
                    fingerprintBadgeView.unbind();
                    fingerprintBadgeView = null;
                    ((MainActivity) mActivity).updateBadge();
                }
                handleFingerprintSetting();
                break;
            case SETTING_SYSTEM:
                Navigation.findNavController(Objects.requireNonNull(getView()))
                        .navigate(R.id.action_to_systemSettingFragment);
                break;
            case SETTING_WHITE_LIST:
                Navigation.findNavController(Objects.requireNonNull(getView()))
                        .navigate(R.id.action_settingFragment_to_whiteListFragment);
                break;
            case SETTING_MNEMONIC_ENTRY:
                handleCreateVault();
                break;
            case SETTING_PASSPHRASE:
                handlePassphrase();
                break;
            case SETTING_SAFETY_INSTRUCTION:
                navigateToLicense("safety_instruction.html", preference.getTitle().toString());
                break;
            case SETTING_LICENSE:
                navigateToLicense("license.html", preference.getTitle().toString());
                break;
            case SETTING_PRIVACY_POLICY:
                navigateToLicense("privacy_policy.html", preference.getTitle().toString());
                break;
            case SETTING_FACTORY_RESET:
                handleFactoryReset();
                break;
            case SETTING_VERSION:
                if (manifest == null) {
                    Navigation.findNavController(Objects.requireNonNull(getView()))
                            .navigate(R.id.action_to_versionFragment);
                } else {
                    update();
                }
                break;
            default:
                break;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private final Runnable forgetPassword = () -> {
        Bundle data = new Bundle();
        data.putInt(KEY_NAV_ID, R.id.action_to_setPasswordFragment1);
        Navigation.findNavController(Objects.requireNonNull(getView()))
                .navigate(R.id.action_to_verifyMnemonic, data);
    };

    private void handlePassphrase() {
        AuthenticateModal.show(mActivity, getString(R.string.password_modal_title), "",
                token -> Navigation.findNavController(Objects.requireNonNull(getView()))
                        .navigate(R.id.action_settingFragment_to_passphraseFragment,
                                Bundle.forPair(PASSWORD, token.password)),
                forgetPassword);
    }

    private void handleFingerprintSetting() {
        AuthenticateModal.show(mActivity, getString(R.string.password_modal_title),
                "",
                password -> {
                    int navId;
                    FingerprintKit fpKit = new FingerprintKit(mActivity);
                    if (fpKit.hasEnrolledFingerprint()) {
                        navId = R.id.action_to_fingerprintSettingFragment;
                    } else {
                        navId = R.id.action_to_fingerprintGuideFragment;
                    }

                    Navigation.findNavController(Objects.requireNonNull(getView()))
                            .navigate(navId, Bundle.forPair(PASSWORD, password.password));
                },
                forgetPassword
        );
    }

    private void handleCreateVault() {
        AuthenticateModal.show(mActivity, getString(R.string.password_modal_title), "",
                password -> {
                    Intent intent = new Intent(mActivity, SetupVaultActivity.class);
                    intent.putExtra(IS_SETUP_VAULT, false);
                    intent.putExtra(PASSWORD,password.password);
                    startActivity(intent);
                }, forgetPassword);
    }

    private void handleFactoryReset() {
        ModalDialog dialog = ModalDialog.newInstance();
        CommonModalBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                R.layout.common_modal,null,false);
        dialog.setBinding(binding);
        binding.title.setText(R.string.factory_reset_title);
        binding.subTitle.setText(R.string.factory_reset_warning);
        binding.confirm.setText(R.string.confirm);
        binding.close.setOnClickListener(v->dialog.dismiss());
        binding.confirm.setOnClickListener(v-> {
            dialog.dismiss();
            AuthenticateModal.show(mActivity, getString(R.string.password_modal_title),
                    "", token -> reset(mActivity), forgetPassword);
        });
        dialog.show(mActivity.getSupportFragmentManager(),"");
    }

    public static void reset(AppCompatActivity activity) {
        ProgressModalDialog dialog = ProgressModalDialog.newInstance();
        dialog.show(Objects.requireNonNull(activity.getSupportFragmentManager()), "");
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                new ResetCallable().call();
                DataCleaner.cleanApplicationData(activity);
                removeAllFingerprint(activity);
                LocalePicker.updateLocale(Locale.SIMPLIFIED_CHINESE);
            } catch (Exception ignored) {
            } finally {
                DataCleaner.cleanApplicationData(activity);
                restartApplication(activity);
            }
        });
    }

    public static void removeAllFingerprint(AppCompatActivity activity) {
        if (!FingerprintKit.isHardwareDetected(activity)) {
            return;
        }
        FingerprintKit fpKit = new FingerprintKit(activity);
        if (FingerprintKit.isHardwareDetected(activity)) {
            List<Fingerprint> fingerprints = fpKit.getEnrolledFingerprints();
            fingerprints.forEach(f -> {
                fpKit.removeFingerprint(f, null);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void handleChangePassword() {
        AuthenticateModal.show(mActivity, getString(R.string.password_modal_title), "", password -> {
            Bundle data = new Bundle();
            data.putString(PASSWORD, password.password);
            Navigation.findNavController(Objects.requireNonNull(getView()))
                    .navigate(R.id.action_to_change_password, data);
        }, forgetPassword);
    }

    private void handleSwitchPatternUnlock() {
        if (switchPreference.isChecked()) {
            switchPreference.setChecked(false);
            Utilities.clearPatternUnlock(mActivity);
        } else {
            AuthenticateModal.show(mActivity, getString(R.string.password_modal_title), "", token ->
                    Navigation.findNavController(Objects.requireNonNull(getView()))
                            .navigate(R.id.action_to_setPatternUnlock), forgetPassword);
        }
    }

    private void navigateToLicense(String url, String title) {
        Bundle data = new Bundle();
        data.putString(KEY_URL, url);
        data.putString(KEY_TITLE, title);
        Navigation.findNavController(Objects.requireNonNull(getView()))
                .navigate(R.id.action_to_licenseFragment, data);
    }
}
