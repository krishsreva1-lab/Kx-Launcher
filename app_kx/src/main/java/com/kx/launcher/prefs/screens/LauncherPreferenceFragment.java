package com.kx.launcher.prefs.screens;


import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.kx.launcher.LauncherActivity;
import com.kx.launcher.R;
import com.kx.launcher.prefs.LauncherPreferences;

/**
 * Preference for the main screen, any sub-screen should inherit this class for consistent behavior,
 * overriding only onCreatePreferences
 */
public class LauncherPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected Runnable mVisibilityUpdater = () -> {};

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.setBackgroundColor(getResources().getColor(R.color.background_app));
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        mVisibilityUpdater = this::updateVisibility;
        addPreferencesFromResource(R.xml.pref_main);
        setupNotificationRequestPreference();
        setupQuickActions();
    }

    private void setupQuickActions() {
        Preference searchMods = findPreference("action_search_mods");
        if (searchMods != null) {
            searchMods.setOnPreferenceClickListener(p -> {
                com.kx.launcher.Tools.swapFragment(requireActivity(), com.kx.launcher.fragments.SearchModFragment.class, com.kx.launcher.fragments.SearchModFragment.TAG, null);
                return true;
            });
        }
        Preference customControls = findPreference("action_custom_controls");
        if (customControls != null) {
            customControls.setOnPreferenceClickListener(p -> {
                startActivity(new android.content.Intent(requireContext(), com.kx.launcher.CustomControlsActivity.class));
                return true;
            });
        }
        Preference installJar = findPreference("action_install_jar");
        if (installJar != null) {
            installJar.setOnPreferenceClickListener(p -> {
                com.kx.launcher.fragments.MainMenuFragment fragment = (com.kx.launcher.fragments.MainMenuFragment) requireActivity().getSupportFragmentManager().findFragmentById(R.id.container_fragment);
                if (fragment != null) {
                    fragment.runInstallerWithConfirmation();
                }
                return true;
            });
        }
        Preference openDir = findPreference("action_open_gamedir");
        if (openDir != null) {
            openDir.setOnPreferenceClickListener(p -> {
                com.kx.launcher.fragments.MainMenuFragment.openGameDirectory(requireContext());
                return true;
            });
        }
        Preference shareLog = findPreference("action_share_logs");
        if (shareLog != null) {
            shareLog.setOnPreferenceClickListener(p -> {
                com.kx.launcher.Tools.shareLog(requireContext());
                return true;
            });
        }
    }

    private void updateVisibility(){
        requirePreference("notification_permission_request").setVisible(!getLauncherActivity().checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS));
    }

    private void setupNotificationRequestPreference() {
        Preference mRequestNotificationPermissionPreference = requirePreference("notification_permission_request");
        Activity activity = getActivity();
        if(activity instanceof LauncherActivity) {
            mRequestNotificationPermissionPreference.setOnPreferenceClickListener(preference -> {
                ((LauncherActivity) activity).askForPermission(33, Manifest.permission.POST_NOTIFICATIONS);
                return true;
            });
        }else{
            mRequestNotificationPermissionPreference.setVisible(false);
        }
        updateVisibility();
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        mVisibilityUpdater.run();
    }

    @Override
    public void onPause() {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        LauncherPreferences.loadPreferences(getContext());
    }

    protected Preference requirePreference(CharSequence key) {
        Preference preference = findPreference(key);
        if(preference != null) return preference;
        throw new IllegalStateException("Preference "+key+" is null");
    }
    @SuppressWarnings("unchecked")
    protected <T extends Preference> T requirePreference(CharSequence key, Class<T> preferenceClass) {
        Preference preference = requirePreference(key);
        if(preferenceClass.isInstance(preference)) return (T)preference;
        throw new IllegalStateException("Preference "+key+" is not an instance of "+preferenceClass.getSimpleName());
    }
    protected LauncherActivity getLauncherActivity(){
        return ((LauncherActivity) getActivity());
    }
}
