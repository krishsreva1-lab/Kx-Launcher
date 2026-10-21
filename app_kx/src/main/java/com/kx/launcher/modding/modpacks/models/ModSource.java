package com.kx.launcher.modding.modpacks.models;

public abstract class ModSource {
    public int apiSource;
    public boolean isModpack;
    /** One of SearchFilters.TYPE_MOD / TYPE_MODPACK / TYPE_RESOURCE_PACK / TYPE_SHADER */
    public int contentType = SearchFilters.TYPE_MOD;
}
