package com.kx.launcher.modding.modpacks.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import com.kx.launcher.R;
import com.kx.launcher.Tools;
import com.kx.launcher.downloader.Downloader;
import com.kx.launcher.downloader.TaskMetadata;
import com.kx.launcher.tasks.mojson.mirrors.DownloadMirror;
import com.kx.launcher.modding.fabric.FabriclikeUtils;
import com.kx.launcher.modding.forge.ForgelikeUtils;
import com.kx.launcher.modding.lwjgl3ify.Lwjgl3ifyUtils;
import com.kx.launcher.modding.modpacks.api.modloader.FabriclikeLoaderInstaller;
import com.kx.launcher.modding.modpacks.api.modloader.ForgelikeLoaderInstaller;
import com.kx.launcher.modding.modpacks.api.modloader.LoaderInstaller;
import com.kx.launcher.modding.modpacks.api.modloader.Lwjgl3ifyLoaderInstaller;
import com.kx.launcher.modding.modpacks.models.Constants;
import com.kx.launcher.modding.modpacks.models.ModDetail;
import com.kx.launcher.modding.modpacks.models.ModItem;
import com.kx.launcher.modding.modpacks.models.ModrinthIndex;
import com.kx.launcher.modding.modpacks.models.SearchFilters;
import com.kx.launcher.modding.modpacks.models.SearchResult;
import com.kx.launcher.instances.Instance;
import com.kx.launcher.instances.Instances;
import com.kx.launcher.utils.FileUtils;
import com.kx.launcher.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

public class ModrinthApi implements ModpackApi{
    private final ApiHandler mApiHandler;
    public ModrinthApi(){
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        ModrinthSearchResult modrinthSearchResult = (ModrinthSearchResult) previousPageResult;

        // Fixes an issue where the offset being equal or greater than total_hits is ignored
        if (modrinthSearchResult != null && modrinthSearchResult.previousOffset >= modrinthSearchResult.totalResultCount) {
            ModrinthSearchResult emptyResult = new ModrinthSearchResult();
            emptyResult.results = new ModItem[0];
            emptyResult.totalResultCount = modrinthSearchResult.totalResultCount;
            emptyResult.previousOffset = modrinthSearchResult.previousOffset;
            return emptyResult;
        }


        // Build the facets filters
        HashMap<String, Object> params = new HashMap<>();
        StringBuilder facetString = new StringBuilder();
        facetString.append("[");
        String projectType;
        if (searchFilters.isModpack || searchFilters.searchType == SearchFilters.TYPE_MODPACK) {
            projectType = "modpack";
        } else if (searchFilters.searchType == SearchFilters.TYPE_RESOURCE_PACK) {
            projectType = "resourcepack";
        } else if (searchFilters.searchType == SearchFilters.TYPE_SHADER) {
            projectType = "shader";
        } else {
            projectType = "mod";
        }
        facetString.append(String.format("[\"project_type:%s\"]", projectType));
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            facetString.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
        facetString.append("]");
        params.put("facets", facetString.toString());
        params.put("query", searchFilters.name);
        params.put("limit", 50);
        params.put("index", "relevance");
        if(modrinthSearchResult != null)
            params.put("offset", modrinthSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray responseHits = response.getAsJsonArray("hits");
        if(responseHits == null) return null;

        ArrayList<ModItem> itemList = new ArrayList<>(responseHits.size());
        for(int i=0; i<responseHits.size(); ++i){
            try {
                JsonObject hit = responseHits.get(i).getAsJsonObject();
                String hitType = str(hit, "project_type");
                int contentType;
                switch (hitType) {
                    case "modpack": contentType = SearchFilters.TYPE_MODPACK; break;
                    case "resourcepack": contentType = SearchFilters.TYPE_RESOURCE_PACK; break;
                    case "shader": contentType = SearchFilters.TYPE_SHADER; break;
                    default: contentType = SearchFilters.TYPE_MOD;
                }
                String projectId = str(hit, "project_id");
                if(projectId.isEmpty()) continue;
                // icon_url is null for many resource packs / shaders, which used to crash the whole search
                itemList.add(new ModItem(Constants.SOURCE_MODRINTH, contentType, projectId,
                        str(hit, "title"), str(hit, "description"), str(hit, "icon_url")));
            } catch (RuntimeException e) {
                Log.w("ModrinthApi", "Skipping a malformed search hit", e);
            }
        }
        ModItem[] items = itemList.toArray(new ModItem[0]);
        if(modrinthSearchResult == null) modrinthSearchResult = new ModrinthSearchResult();
        modrinthSearchResult.previousOffset += responseHits.size();
        modrinthSearchResult.results = items;
        modrinthSearchResult.totalResultCount = response.has("total_hits") && !response.get("total_hits").isJsonNull()
                ? response.get("total_hits").getAsInt() : items.length;
        return modrinthSearchResult;
    }

    /** Null-safe string getter, returns "" when the key is missing or JSON null */
    private static String str(JsonObject object, String key) {
        if(object == null || !object.has(key)) return "";
        JsonElement element = object.get(key);
        if(element == null || element.isJsonNull() || !element.isJsonPrimitive()) return "";
        return element.getAsString();
    }

    /** The file marked as primary, or the first file if none is marked */
    private static JsonObject primaryFile(JsonObject version) {
        JsonArray files = version.has("files") && version.get("files").isJsonArray() ? version.getAsJsonArray("files") : null;
        if(files == null || files.size() == 0) return null;
        for(JsonElement file : files) {
            JsonObject fileObject = file.getAsJsonObject();
            if(fileObject.has("primary") && !fileObject.get("primary").isJsonNull() && fileObject.get("primary").getAsBoolean())
                return fileObject;
        }
        return files.get(0).getAsJsonObject();
    }

    private static String joinArray(JsonObject object, String key, int max) {
        StringBuilder builder = new StringBuilder();
        if(object.has(key) && object.get(key).isJsonArray()) {
            JsonArray array = object.getAsJsonArray(key);
            for(int i = 0; i < array.size() && i < max; i++) {
                if(i > 0) builder.append(", ");
                builder.append(array.get(i).getAsString());
            }
        }
        return builder.toString();
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        JsonArray response = mApiHandler.get(String.format("project/%s/version", item.id), JsonArray.class);
        if(response == null) return null;

        ArrayList<String> names = new ArrayList<>(), mcNames = new ArrayList<>(), urls = new ArrayList<>(),
                hashes = new ArrayList<>(), ids = new ArrayList<>(), fileNames = new ArrayList<>();

        for (int i=0; i<response.size(); ++i) {
            JsonObject version = response.get(i).getAsJsonObject();
            JsonObject file = primaryFile(version);
            if(file == null || str(file, "url").isEmpty()) continue; // nothing downloadable in this version

            String name = str(version, "name");
            if(name.isEmpty()) name = str(version, "version_number");
            // Show the mod loaders next to the version so the right one can be picked
            if(!item.isModpack && item.contentType == SearchFilters.TYPE_MOD) {
                String loaders = joinArray(version, "loaders", 4);
                if(!loaders.isEmpty()) name += " [" + loaders + "]";
            }
            names.add(name);
            JsonArray gameVersions = version.has("game_versions") && version.get("game_versions").isJsonArray()
                    ? version.getAsJsonArray("game_versions") : null;
            mcNames.add(gameVersions != null && gameVersions.size() > 0 ? gameVersions.get(0).getAsString() : "?");
            urls.add(str(file, "url"));
            ids.add(str(version, "id"));
            fileNames.add(str(file, "filename"));
            JsonObject hashesMap = file.has("hashes") && file.get("hashes").isJsonObject() ? file.getAsJsonObject("hashes") : null;
            // Assume there may not be hashes, in case the API changes
            hashes.add(hashesMap == null || !hashesMap.has("sha1") ? null : hashesMap.get("sha1").getAsString());
        }
        if(names.isEmpty()) return null;

        ModDetail detail = new ModDetail(item, names.toArray(new String[0]), mcNames.toArray(new String[0]),
                urls.toArray(new String[0]), hashes.toArray(new String[0]));
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
        return ModpackInstaller.downloadModpack(modDetail, selectedVersion, this::installMrpack);
    }

    /** Mods, resource packs and shaders: download into the selected instance (+ required dependencies) */
    private void installSingleContent(ModDetail detail, int selectedVersion) throws IOException {
        List<ContentInstaller.Entry> entries = new ArrayList<>();
        entries.add(new ContentInstaller.Entry(detail.versionUrls[selectedVersion],
                detail.fileNames != null ? detail.fileNames[selectedVersion] : null,
                detail.versionHashes[selectedVersion]));

        if(detail.contentType == SearchFilters.TYPE_MOD && detail.versionIds != null) {
            try {
                Set<String> visited = new HashSet<>();
                visited.add(detail.id);
                Instance instance = Instances.loadSelectedInstance();
                collectDependencies(detail.versionIds[selectedVersion], instance != null ? instance.versionId : null,
                        visited, entries, 0);
            } catch (RuntimeException e) {
                Log.w("ModrinthApi", "Dependency resolution failed, installing only the requested file", e);
            }
        }
        ContentInstaller.install(detail.title, detail.contentType, entries);
    }

    private void collectDependencies(String versionId, String instanceVersionId, Set<String> visited,
                                     List<ContentInstaller.Entry> entries, int depth) {
        if(depth > 3 || entries.size() > 25) return;
        JsonObject version = mApiHandler.get("version/" + versionId, JsonObject.class);
        if(version == null || !version.has("dependencies") || !version.get("dependencies").isJsonArray()) return;

        // Pick the Minecraft version / loader the dependencies must match
        String mcVersion = null;
        if(version.has("game_versions") && version.get("game_versions").isJsonArray()) {
            JsonArray gameVersions = version.getAsJsonArray("game_versions");
            for(JsonElement gv : gameVersions) {
                if(ContentInstaller.instanceMatchesMcVersion(instanceVersionId, gv.getAsString())) { mcVersion = gv.getAsString(); break; }
            }
            if(mcVersion == null && gameVersions.size() > 0) mcVersion = gameVersions.get(0).getAsString();
        }
        JsonArray loaders = version.has("loaders") && version.get("loaders").isJsonArray() ? version.getAsJsonArray("loaders") : null;

        for(JsonElement dependencyElement : version.getAsJsonArray("dependencies")) {
            JsonObject dependency = dependencyElement.getAsJsonObject();
            if(!"required".equals(str(dependency, "dependency_type"))) continue;
            String projectId = str(dependency, "project_id");
            String depVersionId = str(dependency, "version_id");
            if(!projectId.isEmpty() && !visited.add(projectId)) continue;

            JsonObject chosen = null;
            if(!depVersionId.isEmpty()) {
                chosen = mApiHandler.get("version/" + depVersionId, JsonObject.class);
            } else if(!projectId.isEmpty()) {
                HashMap<String, Object> query = new HashMap<>();
                if(mcVersion != null) query.put("game_versions", "[\"" + mcVersion + "\"]");
                if(loaders != null && loaders.size() > 0) query.put("loaders", loaders.toString());
                JsonArray candidates = mApiHandler.get("project/" + projectId + "/version", query, JsonArray.class);
                if(candidates != null && candidates.size() > 0) chosen = candidates.get(0).getAsJsonObject();
            }
            if(chosen == null) continue;
            JsonObject file = primaryFile(chosen);
            if(file == null || str(file, "url").isEmpty()) continue;
            String url = str(file, "url");
            boolean duplicate = false;
            for(ContentInstaller.Entry entry : entries) if(entry.url.equals(url)) duplicate = true;
            if(duplicate) continue;
            JsonObject hashesMap = file.has("hashes") && file.get("hashes").isJsonObject() ? file.getAsJsonObject("hashes") : null;
            entries.add(new ContentInstaller.Entry(url, str(file, "filename"),
                    hashesMap == null || !hashesMap.has("sha1") ? null : hashesMap.get("sha1").getAsString()));
            collectDependencies(str(chosen, "id"), instanceVersionId, visited, entries, depth + 1);
        }
    }

    public LoaderInstaller installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException {
        return ModpackInstaller.installModpack(modpackName, modpackName, modpackFile, icon, this::installMrpack);
    }

    private static LoaderInstaller createInfo(ModrinthIndex modrinthIndex, File installDestination) throws IOException {
        if(modrinthIndex == null) return null;
        Map<String, String> dependencies = modrinthIndex.dependencies;
        String mcVersion = dependencies.get("minecraft");
        if(mcVersion == null) return null;
        String modLoaderVersion;
        if((modLoaderVersion = dependencies.get("forge")) != null) {
            return new ForgelikeLoaderInstaller(ForgelikeUtils.FORGE_UTILS, mcVersion, modLoaderVersion);
        } else if((modLoaderVersion = dependencies.get("fabric-loader")) != null) {
            return new FabriclikeLoaderInstaller(FabriclikeUtils.FABRIC_UTILS, mcVersion, modLoaderVersion);
        } else if((modLoaderVersion = dependencies.get("quilt-loader")) != null) {
            return new FabriclikeLoaderInstaller(FabriclikeUtils.QUILT_UTILS, mcVersion, modLoaderVersion);
        } else if((modLoaderVersion = dependencies.get("neoforge")) != null) {
            return new ForgelikeLoaderInstaller(ForgelikeUtils.NEOFORGE_UTILS, mcVersion, modLoaderVersion);
        } else if(dependencies.size() == 1) {
            // "Vanilla" pack. Possibly GT:NH, let's try to detect lwjgl3ify
            File lwjgl3ifyJar = Lwjgl3ifyUtils.detectLwjgl3ifyJar(installDestination);
            if(lwjgl3ifyJar != null) return new Lwjgl3ifyLoaderInstaller(lwjgl3ifyJar);
        }

        return null;
    }

    private LoaderInstaller installMrpack(File mrpackFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(mrpackFile)){
            ModrinthIndex modrinthIndex = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "modrinth.index.json")),
                    ModrinthIndex.class);
            try {
                new ModrinthDownloader().startDownloads(modrinthIndex.files, instanceDestination);
            }catch (InterruptedException e) {
                throw new IOException("NIY: InterruptedException", e);
            }
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
            ZipUtils.zipExtract(modpackZipFile, "overrides/", instanceDestination);
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
            ZipUtils.zipExtract(modpackZipFile, "client-overrides/", instanceDestination);
            return createInfo(modrinthIndex, instanceDestination);
        }
    }

    class ModrinthSearchResult extends SearchResult {
        int previousOffset;
    }

    static class ModrinthDownloader extends Downloader {
        public ModrinthDownloader() {
            super(ProgressLayout.INSTALL_MODPACK);
        }

        protected void startDownloads(ModrinthIndex.ModrinthIndexFile[] indexFiles, File instanceDestination) throws IOException, InterruptedException {
            String absoluteInstancePath = instanceDestination.getAbsolutePath();
            ArrayList<TaskMetadata> taskMetadatas = new ArrayList<>(indexFiles.length);
            for(ModrinthIndex.ModrinthIndexFile file : indexFiles) {
                File targetPath = new File(instanceDestination, file.path);
                if(!targetPath.getAbsolutePath().startsWith(absoluteInstancePath)) throw new IOException("Bad path!");
                FileUtils.ensureParentDirectory(targetPath);
                taskMetadatas.add(new TaskMetadata(
                        targetPath, new URL(file.downloads[0]), // TODO source selection
                        file.fileSize, file.hashes.sha1,
                        DownloadMirror.DOWNLOAD_CLASS_NONE
                ));
            }
            runDownloads(taskMetadatas);
        }
    }
}
