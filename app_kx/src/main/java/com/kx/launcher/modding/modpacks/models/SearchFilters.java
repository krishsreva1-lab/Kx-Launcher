package com.kx.launcher.modding.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    public static final int TYPE_MOD = 0;
    public static final int TYPE_MODPACK = 1;
    public static final int TYPE_RESOURCE_PACK = 2;
    public static final int TYPE_SHADER = 3;

    public int searchType = TYPE_MOD;
    public boolean isModpack;
    public String name;
    @Nullable public String mcVersion;
}
