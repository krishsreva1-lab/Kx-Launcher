package com.kx.launcher.modding.modpacks.api;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import com.kx.launcher.Tools;
import com.kx.launcher.downloader.AcquireableTaskMetadata;
import com.kx.launcher.downloader.Downloader;
import com.kx.launcher.tasks.mojson.mirrors.DownloadMirror;
import com.kx.launcher.modding.fabric.FabriclikeUtils;
import com.kx.launcher.modding.forge.ForgelikeUtils;
import com.kx.launcher.modding.modpacks.api.modloader.FabriclikeLoaderInstaller;
import com.kx.launcher.modding.modpacks.api.modloader.ForgelikeLoaderInstaller;
import com.kx.launcher.modding.modpacks.api.modloader.LoaderInstaller;
import com.kx.launcher.modding.modpacks.models.Constants;
import com.kx.launcher.modding.modpacks.models.CurseManifest;
import com.kx.launcher.modding.modpacks.models.ModDetail;
import com.kx.launcher.modding.modpacks.models.ModItem;
import com.kx.launcher.modding.modpacks.models.SearchFilters;
import com.kx.launcher.modding.modpacks.models.SearchResult;
import com.kx.launcher.instances.Instance;
import com.kx.launcher.instances.Instances;
import com.kx.launcher.utils.FileUtils;
import com.kx.launcher.utils.GsonJsonUtils;
import com.kx.launcher.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class CurseforgeApi implements ModpackApi{
    private static final Pattern sMcVersionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.?([0-9]+)?");
    private static final int ALGO_SHA_1 = 1;
    // Stolen from
    // https://github.com/AnzhiZhang/CurseForgeModpackDownloader/blob/6cb3f428459f0cc8f444d16e54aea4cd1186fd7b/utils/requester.py#L93
    private static final int CURSEFORGE_MC_GAME_ID = 432;
    private static final int CURSEFORGE_MODPACK_CLASS_ID = 4471;
    // https://api.curseforge.com/v1/categories?gameId=432 and search for "Mods" (case-sensitive)
    private static final int CURSEFORGE_MOD_CLASS_ID = 6;
    private static final int CURSEFORGE_SORT_RELEVANCY = 1;
    private static final int CURSEFORGE_PAGINATION_SIZE = 50;
    private static final int CURSEFORGE_PAGINATION_END_REACHED = -1;
    private static final int CURSEFORGE_PAGINATION_ERROR = -2;

    private final ApiHandler mApiHandler;
    public CurseforgeApi(String apiKey) {
        mApiHandler = new ApiHandler("https://api.curseforge.com/v1", apiKey);
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        CurseforgeSearchResult curseforgeSearchResult = (CurseforgeSearchResult) previousPageResult;

        HashMap<String, Object> params = new HashMap<>();
        params.put("gameId", CURSEFORGE_MC_GAME_ID);
        int classId;
        if (searchFilters.isModpack || searchFilters.searchType == SearchFilters.TYPE_MODPACK) {
            classId = CURSEFORGE_MODPACK_CLASS_ID;
        } else if (searchFilters.searchType == SearchFilters.TYPE_RESOURCE_PACK) {
            classId = 12;
        } else if (searchFilters.searchType == SearchFilters.TYPE_SHADER) {
            classId = 6552;
        } else {
            classId = CURSEFORGE_MOD_CLASS_ID;
        }
        params.put("classId", classId);
        params.put("searchFilter", searchFilters.name);
        params.put("sortField", CURSEFORGE_SORT_RELEVANCY);
        params.put("sortOrder", "desc");
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            params.put("gameVersion", searchFilters.mcVersion);
        if(previousPageResult != null)
            params.put("index", curseforgeSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("mods/search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray dataArray = response.getAsJsonArray("data");
        if(dataArray == null) return null;
        JsonObject paginationInfo = response.getAsJsonObject("pagination");
        ArrayList<ModItem> modItemList = new ArrayList<>(dataArray.size());
        int contentType = searchFilters.isModpack ? SearchFilters.TYPE_MODPACK : searchFilters.searchType;
        for(int i = 0; i < dataArray.size(); i++) {
            try {
                JsonObject dataElement = dataArray.get(i).getAsJsonObject();
                JsonElement allowModDistribution = dataElement.get("allowModDistribution");
                // The flag may be missing or null; only skip when CurseForge explicitly says "false"
                if(allowModDistribution != null && !allowModDistribution.isJsonNull() && !allowModDistribution.getAsBoolean()) {
                    Log.i("CurseforgeApi", "Skipping "+safeString(dataElement, "name")+" because distribution is disabled");
                    continue;
                }
                String logoUrl = "";
                JsonElement logo = dataElement.get("logo");
                if(logo != null && logo.isJsonObject()) {
                    logoUrl = safeString(logo.getAsJsonObject(), "thumbnailUrl");
                    if(logoUrl.isEmpty()) logoUrl = safeString(logo.getAsJsonObject(), "url");
                }
                String modId = safeString(dataElement, "id");
                if(modId.isEmpty()) continue;
                modItemList.add(new ModItem(Constants.SOURCE_CURSEFORGE, contentType, modId,
                        safeString(dataElement, "name"), safeString(dataElement, "summary"), logoUrl));
            } catch (RuntimeException e) {
                Log.w("CurseforgeApi", "Skipping a malformed search entry", e);
            }
        }
        if(curseforgeSearchResult == null) curseforgeSearchResult = new CurseforgeSearchResult();
        curseforgeSearchResult.results = modItemList.toArray(new ModItem[0]);
        curseforgeSearchResult.totalResultCount = paginationInfo != null && paginationInfo.has("totalCount")
                ? paginationInfo.get("totalCount").getAsInt() : modItemList.size();
        curseforgeSearchResult.previousOffset += dataArray.size();
        return curseforgeSearchResult;

    }

    private static String safeString(JsonObject object, String key) {
        if(object == null || !object.has(key)) return "";
        JsonElement element = object.get(key);
        if(element == null || element.isJsonNull() || !element.isJsonPrimitive()) return "";
        return element.getAsString();
    }

    /** CurseForge returns a null downloadUrl for some files, in that case build the CDN link */
    private static String resolveFileUrl(JsonObject file) {
        String url = safeString(file, "downloadUrl");
        if(!url.isEmpty()) return url;
        long fileId = file.has("id") && !file.get("id").isJsonNull() ? file.get("id").getAsLong() : 0;
        String fileName = safeString(file, "fileName");
        if(fileId == 0 || fileName.isEmpty()) return "";
        return String.format("https://edge.forgecdn.net/files/%s/%s/%s", fileId/1000, fileId % 1000, fileName);
    }

    private static String findMcVersion(JsonObject file) {
        JsonArray gameVersions = file.has("gameVersions") && file.get("gameVersions").isJsonArray() ? file.getAsJsonArray("gameVersions") : null;
        if(gameVersions == null) return null;
        for(JsonElement jsonElement : gameVersions) {
            String gameVersion = jsonElement.getAsString();
            if(sMcVersionPattern.matcher(gameVersion).matches()) return gameVersion;
        }
        return null;
    }

    /** 1 Forge, 4 Fabric, 5 Quilt, 6 NeoForge, 0 unknown */
    private static int findLoaderType(JsonObject file) {
        JsonArray gameVersions = file.has("gameVersions") && file.get("gameVersions").isJsonArray() ? file.getAsJsonArray("gameVersions") : null;
        if(gameVersions == null) return 0;
        for(JsonElement jsonElement : gameVersions) {
            switch (jsonElement.getAsString().toLowerCase(java.util.Locale.ROOT)) {
                case "neoforge": return 6;
                case "fabric": return 4;
                case "quilt": return 5;
                case "forge": return 1;
            }
        }
        return 0;
    }

    private static String loaderNames(JsonObject file) {
        JsonArray gameVersions = file.has("gameVersions") && file.get("gameVersions").isJsonArray() ? file.getAsJsonArray("gameVersions") : null;
        StringBuilder builder = new StringBuilder();
        if(gameVersions != null) {
            for(JsonElement jsonElement : gameVersions) {
                String value = jsonElement.getAsString();
                String lower = value.toLowerCase(java.util.Locale.ROOT);
                if(lower.equals("forge") || lower.equals("fabric") || lower.equals("neoforge") || lower.equals("quilt")) {
                    if(builder.length() > 0) builder.append(", ");
                    builder.append(value);
                }
            }
        }
        return builder.toString();
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        ArrayList<JsonObject> allModDetails = new ArrayList<>();
        int index = 0;
        while(index != CURSEFORGE_PAGINATION_END_REACHED &&
                index != CURSEFORGE_PAGINATION_ERROR) {
            index = getPaginatedDetails(allModDetails, index, item.id);
        }
        if(index == CURSEFORGE_PAGINATION_ERROR) return null;

        ArrayList<String> versionNames = new ArrayList<>(), mcVersionNames = new ArrayList<>(),
                versionUrls = new ArrayList<>(), hashes = new ArrayList<>(), ids = new ArrayList<>(), fileNames = new ArrayList<>();
        for(JsonObject modDetail : allModDetails) {
            String url = resolveFileUrl(modDetail);
            if(url.isEmpty()) continue; // not downloadable

            String name = safeString(modDetail, "displayName");
            if(name.isEmpty()) name = safeString(modDetail, "fileName");
            if(!item.isModpack && item.contentType == SearchFilters.TYPE_MOD) {
                String loaders = loaderNames(modDetail);
                if(!loaders.isEmpty()) name += " [" + loaders + "]";
            }
            versionNames.add(name);
            versionUrls.add(url);
            String mc = findMcVersion(modDetail);
            mcVersionNames.add(mc != null ? mc : "?");
            hashes.add(getSha1FromModData(modDetail));
            ids.add(safeString(modDetail, "id"));
            fileNames.add(safeString(modDetail, "fileName"));
        }
        if(versionNames.isEmpty()) return null;
        ModDetail detail = new ModDetail(item, versionNames.toArray(new String[0]), mcVersionNames.toArray(new String[0]),
                versionUrls.toArray(new String[0]), hashes.toArray(new String[0]));
        detail.versionIds = ids.toArray(new String[0]);
        detail.fileNames = fileNames.toArray(new String[0]);
        return detail;
    }

    @Override
    public LoaderInstaller installModpack(ModDetail modDetail, int selectedVersion) throws IOException{
        if(!modDetail.isModpack) {
            installSingleContent(modDetail, selectedVersion);
            return null;
        }
        return ModpackInstaller.downloadModpack(modDetail, selectedVersion, this::installCurseforgeZip);
    }

    /** Mods, resource packs and shaders: download into the selected instance (+ required dependencies) */
    private void installSingleContent(ModDetail detail, int selectedVersion) throws IOException {
        List<ContentInstaller.Entry> entries = new ArrayList<>();
        entries.add(new ContentInstaller.Entry(detail.versionUrls[selectedVersion],
                detail.fileNames != null ? detail.fileNames[selectedVersion] : null,
                detail.versionHashes[selectedVersion]));

        if(detail.contentType == SearchFilters.TYPE_MOD && detail.versionIds != null) {
            try {
                long modId = Long.parseLong(detail.id);
                long fileId = Long.parseLong(detail.versionIds[selectedVersion]);
                Set<Long> visited = new HashSet<>();
                visited.add(modId);
                Instance instance = Instances.loadSelectedInstance();
                collectDependencies(modId, fileId, instance != null ? instance.versionId : null, visited, entries, 0);
            } catch (RuntimeException e) {
                Log.w("CurseforgeApi", "Dependency resolution failed, installing only the requested file", e);
            }
        }
        ContentInstaller.install(detail.title, detail.contentType, entries);
    }

    private void collectDependencies(long modId, long fileId, String instanceVersionId, Set<Long> visited,
                                     List<ContentInstaller.Entry> entries, int depth) {
        if(depth > 3 || entries.size() > 25) return;
        JsonObject file = getFile(modId, fileId);
        if(file == null || !file.has("dependencies") || !file.get("dependencies").isJsonArray()) return;

        String mcVersion = findMcVersion(file);
        JsonArray gameVersions = file.has("gameVersions") && file.get("gameVersions").isJsonArray() ? file.getAsJsonArray("gameVersions") : null;
        if(gameVersions != null) {
            for(JsonElement gv : gameVersions) {
                if(ContentInstaller.instanceMatchesMcVersion(instanceVersionId, gv.getAsString())) { mcVersion = gv.getAsString(); break; }
            }
        }
        int loaderType = findLoaderType(file);

        for(JsonElement dependencyElement : file.getAsJsonArray("dependencies")) {
            JsonObject dependency = dependencyElement.getAsJsonObject();
            // relationType 3 = required dependency
            if(!dependency.has("relationType") || dependency.get("relationType").getAsInt() != 3) continue;
            long depModId = dependency.get("modId").getAsLong();
            if(!visited.add(depModId)) continue;

            HashMap<String, Object> query = new HashMap<>();
            if(mcVersion != null) query.put("gameVersion", mcVersion);
            if(loaderType != 0) query.put("modLoaderType", loaderType);
            query.put("pageSize", 1);
            JsonObject response = mApiHandler.get("mods/" + depModId + "/files", query, JsonObject.class);
            JsonArray data = GsonJsonUtils.getJsonArraySafe(response, "data");
            if(data == null || data.size() == 0) continue;
            JsonObject depFile = data.get(0).getAsJsonObject();
            String url = resolveFileUrl(depFile);
            if(url.isEmpty()) continue;
            entries.add(new ContentInstaller.Entry(url, safeString(depFile, "fileName"), getSha1FromModData(depFile)));
            if(depFile.has("id") && !depFile.get("id").isJsonNull())
                collectDependencies(depModId, depFile.get("id").getAsLong(), instanceVersionId, visited, entries, depth + 1);
        }
    }

    public LoaderInstaller installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException {
        return ModpackInstaller.installModpack(modpackName, modpackName, modpackFile, icon, this::installCurseforgeZip);
    }

    private int getPaginatedDetails(ArrayList<JsonObject> objectList, int index, String modId) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("index", index);
        params.put("pageSize", CURSEFORGE_PAGINATION_SIZE);

        JsonObject response = mApiHandler.get("mods/"+modId+"/files", params, JsonObject.class);
        JsonArray data = GsonJsonUtils.getJsonArraySafe(response, "data");
        Log.i("CurseforgeApi", "data...");
        if(data == null) return CURSEFORGE_PAGINATION_ERROR;
        Log.i("CurseforgeApi", "filtering...");
        for(int i = 0; i < data.size(); i++) {
            JsonObject fileInfo = data.get(i).getAsJsonObject();
            JsonElement serverPack = fileInfo.get("isServerPack");
            if(serverPack != null && !serverPack.isJsonNull() && serverPack.getAsBoolean()) continue;
            objectList.add(fileInfo);
        }
        Log.i("CurseforgeApi", "pag_end");
        if(data.size() < CURSEFORGE_PAGINATION_SIZE) {
            return CURSEFORGE_PAGINATION_END_REACHED; // we read the remainder! yay!
        }
        return index + data.size();
    }

    private LoaderInstaller installCurseforgeZip(File zipFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(zipFile)){
            CurseManifest curseManifest = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "manifest.json")),
                    CurseManifest.class);
            if(!verifyManifest(curseManifest)) {
                Log.i("CurseforgeApi","manifest verification failed");
                return null;
            }
            try {
                new CurseDownloader().start(curseManifest, instanceDestination);
            }catch (InterruptedException e) {
                throw new IOException("NIY: InterruptedException", e);
            }
            String overridesDir = "overrides";
            if(curseManifest.overrides != null) overridesDir = curseManifest.overrides;
            ZipUtils.zipExtract(modpackZipFile, overridesDir, instanceDestination);
            return createInfo(curseManifest.minecraft);
        }
    }

    private LoaderInstaller createInfo(CurseManifest.CurseMinecraft minecraft) {
        CurseManifest.CurseModLoader primaryModLoader = null;
        for(CurseManifest.CurseModLoader modLoader : minecraft.modLoaders) {
            if(modLoader.primary) {
                primaryModLoader = modLoader;
                break;
            }
        }
        if(primaryModLoader == null) primaryModLoader = minecraft.modLoaders[0];
        String modLoaderId = primaryModLoader.id;
        int dashIndex = modLoaderId.indexOf('-');
        String modLoaderName = modLoaderId.substring(0, dashIndex);
        String modLoaderVersion = modLoaderId.substring(dashIndex+1);
        Log.i("CurseforgeApi", modLoaderId + " " + modLoaderName + " "+modLoaderVersion);
        LoaderInstaller loaderInstaller;
        switch (modLoaderName) {
            case "forge":
                return new ForgelikeLoaderInstaller(ForgelikeUtils.FORGE_UTILS, minecraft.version, modLoaderVersion);
            case "fabric":
                return new FabriclikeLoaderInstaller(FabriclikeUtils.FABRIC_UTILS, minecraft.version, modLoaderVersion);
            case "neoforge":
                return new ForgelikeLoaderInstaller(ForgelikeUtils.NEOFORGE_UTILS, minecraft.version, modLoaderVersion);
            default:
                return null;
            //TODO: Quilt is also Forge? How does that work?
        }
    }

    private String getDownloadUrl(JsonObject fileMetadata) throws IOException {
        if(fileMetadata.get("modId").isJsonNull() || fileMetadata.get("id").isJsonNull()) throw new IOException("Bad metadata schema!");
        long projectID = fileMetadata.get("modId").getAsLong();
        long fileID = fileMetadata.get("id").getAsLong();

        // First try the official api endpoint
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID+"/download-url", JsonObject.class);
        if (response != null && !response.get("data").isJsonNull())
            return response.get("data").getAsString();

        // Otherwise, fallback to building an edge link
        return String.format("https://edge.forgecdn.net/files/%s/%s/%s", fileID/1000, fileID % 1000, fileMetadata.get("fileName").getAsString());
    }

    private void checkRequiredFileFields(JsonObject fileMetadata) throws IOException {
        if(fileMetadata == null || fileMetadata.isJsonNull()) throw new IOException("File metadata is null!");
        boolean hasProjectId = fileMetadata.has("modId");
        boolean hasFileId = fileMetadata.has("id");
        boolean hasLength = fileMetadata.has("fileLength");
        if(!hasProjectId || !hasFileId || !hasLength) {
            StringBuilder builder = new StringBuilder().append("File metadata is mising the following fields:");
            if(!hasProjectId) builder.append(" modId");
            if(!hasFileId) builder.append(" id");
            if(!hasLength) builder.append(" fileLength");
            throw new IOException(builder.toString());
        }
    }

    private @Nullable JsonObject getFile(long projectID, long fileID) {
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID, JsonObject.class);
        return GsonJsonUtils.getJsonObjectSafe(response, "data");
    }

    private String getSha1FromModData(@NonNull JsonObject object) {
        JsonArray hashes = GsonJsonUtils.getJsonArraySafe(object, "hashes");
        if(hashes == null) return null;
        for (JsonElement jsonElement : hashes) {
            // The sha1 = 1; md5 = 2;
            JsonObject jsonObject = GsonJsonUtils.getJsonObjectSafe(jsonElement);
            if(GsonJsonUtils.getIntSafe(
                    jsonObject,
                    "algo",
                    -1) == ALGO_SHA_1) {
                return GsonJsonUtils.getStringSafe(jsonObject, "value");
            }
        }
        return null;
    }

    private boolean verifyManifest(CurseManifest manifest) {
        if(!"minecraftModpack".equals(manifest.manifestType)) return false;
        if(manifest.manifestVersion != 1) return false;
        if(manifest.minecraft == null) return false;
        if(manifest.minecraft.version == null) return false;
        if(manifest.minecraft.modLoaders == null) return false;
        return manifest.minecraft.modLoaders.length >= 1;
    }

    static class CurseforgeSearchResult extends SearchResult {
        int previousOffset;
    }

    class CurseDownloader extends Downloader {

        public CurseDownloader() {
            super(ProgressLayout.INSTALL_MODPACK);
        }

        public void start(CurseManifest curseManifest, File instanceDestination) throws IOException, InterruptedException {
            ArrayList<AcquireableTaskMetadata> taskMetadatas = new ArrayList<>(curseManifest.files.length);
            for(final CurseManifest.CurseFile file : curseManifest.files) {
                taskMetadatas.add(new CurseTaskMetadata(file, instanceDestination));
            }
            runDownloads(taskMetadatas);
        }
    }

    class CurseTaskMetadata extends AcquireableTaskMetadata {
        private final CurseManifest.CurseFile mFile;
        private final File mInstanceDestination;

        public CurseTaskMetadata(CurseManifest.CurseFile mFile, File mInstanceDestination) {
            super(DownloadMirror.DOWNLOAD_CLASS_METADATA);
            this.mFile = mFile;
            this.mInstanceDestination = mInstanceDestination;
        }

        @Override
        public void acquireMetadata() throws IOException {
            JsonObject fileMetadata = getFile(mFile.projectID, mFile.fileID);
            checkRequiredFileFields(fileMetadata);
            String url = getDownloadUrl(fileMetadata);
            this.url = new URL(url);
            this.path = new File(mInstanceDestination, "mods/"+ URLDecoder.decode(FileUtils.getFileName(url),"UTF-8"));
            FileUtils.ensureParentDirectorySilently(this.path);
            this.sha1Hash = getSha1FromModData(fileMetadata);
            this.size = fileMetadata.get("fileLength").getAsLong();
        }
    }
}
