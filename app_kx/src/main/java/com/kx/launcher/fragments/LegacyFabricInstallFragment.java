package com.kx.launcher.fragments;

import com.kx.launcher.modding.fabric.FabriclikeUtils;

public class LegacyFabricInstallFragment extends FabriclikeInstallFragment {

    public static final String TAG = "LegacyFabricInstallFragment";
    public LegacyFabricInstallFragment() {
        super(FabriclikeUtils.LEGACY_FABRIC_UTILS, TAG);
    }
}
