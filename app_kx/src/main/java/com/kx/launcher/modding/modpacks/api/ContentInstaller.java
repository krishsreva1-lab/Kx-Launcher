package com.kx.launcher.modding.modpacks.api;

import android.util.Log;
import android.widget.Toast;

import com.kdt.mcgui.ProgressLayout;
import com.kx.launcher.R;
import com.kx.launcher.Tools;
import com.kx.launcher.instances.Instance;
import com.kx.launcher.instances.Instances;
import com.kx.launcher.modding.modpacks.models.SearchFilters;
import com.kx.launcher.progresskeeper.DownloaderProgressWrapper;
import com.kx.launcher.utils.DownloadUtils;
import com.kx.launcher.utils.lifecycle.ContextExecutor;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Installs a single downloadable file (mod, resource pack or shader pack) straight into the
 * currently selected instance, together with the mods it depends on. Nothing needs to be moved by hand.
 */
public final class ContentInstaller {
    private ContentInstaller() {}

    /** One file to download */
    public static class Entry {
        public final String url;
        public final String fileName;
        public final String sha1; // may be null

        public Entry(String url, String fileName, String sha1) {
            this.url = url;
            this.fileName = fileName;
            this.sha1 = sha1;
        }
    }

    /** Folder (inside the instance game directory) used by every content type */
    public static String folderFor(int contentType) {
        switch (contentType) {
            case SearchFilters.TYPE_RESOURCE_PACK: return "resourcepacks";
            case SearchFilters.TYPE_SHADER: return "shaderpacks";
            default: return "mods";
        }
    }

    /** Make a safe file name out of an arbitrary name / url segment */
    public static String safeFileName(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            name = URLDecoder.decode(name.replace("+", "%2B"), "UTF-8");
        } catch (Exception ignored) {}
        int q = name.indexOf('?');
        if (q >= 0) name = name.substring(0, q);
        name = new File(name).getName(); // drops any directory part (path traversal safe)
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return name.isEmpty() ? null : name;
    }

    /**
     * @param mainFirst the requested file must be the first element, dependencies follow
     */
    public static void install(String title, int contentType, List<Entry> files) throws IOException {
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) throw new IOException("No instance is selected. Create or select an instance first.");
        File gameDir = instance.getGameDirectory();
        File targetDir = new File(gameDir, folderFor(contentType));
        if (!targetDir.isDirectory() && !targetDir.mkdirs())
            throw new IOException("Cannot create folder " + targetDir);

        byte[] buffer = new byte[8192];
        List<String> installed = new ArrayList<>();
        try {
            for (int i = 0; i < files.size(); i++) {
                final Entry entry = files.get(i);
                String fileName = safeFileName(entry.fileName);
                if (fileName == null) fileName = safeFileName(entry.url.substring(entry.url.lastIndexOf('/') + 1));
                if (fileName == null) fileName = title + ".jar";
                final File outFile = new File(targetDir, fileName);
                try {
                    DownloadUtils.ensureSha1(outFile, entry.sha1, () ->
                            DownloadUtils.downloadFileMonitored(entry.url, outFile, buffer,
                                    new DownloaderProgressWrapper(R.string.modpack_download_downloading_metadata,
                                            ProgressLayout.INSTALL_MODPACK)));
                    installed.add(fileName);
                } catch (IOException e) {
                    outFile.delete();
                    // The requested file itself must succeed; a broken dependency should not cancel everything
                    if (i == 0) throw e;
                    Log.w("ContentInstaller", "Dependency failed: " + entry.url, e);
                }
            }
            if (contentType == SearchFilters.TYPE_RESOURCE_PACK && !installed.isEmpty()) {
                try {
                    enableResourcePack(gameDir, installed.get(0));
                } catch (Exception e) {
                    Log.w("ContentInstaller", "Could not enable the resource pack automatically", e);
                }
            }
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }

        final String message = installed.size() <= 1
                ? "Installed \"" + title + "\" into " + instance.name
                : "Installed \"" + title + "\" + " + (installed.size() - 1) + " required mod(s) into " + instance.name;
        ContextExecutor.executeActivity(activity -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    /** Adds the pack to the "resourcePacks" list of options.txt so it is active on the next launch */
    private static void enableResourcePack(File gameDir, String fileName) throws IOException {
        File options = new File(gameDir, "options.txt");
        String entry = "file/" + fileName;
        String[] lines = options.isFile() ? Tools.read(options.getAbsolutePath()).split("\n", -1) : new String[0];
        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        for (String line : lines) {
            if (line.isEmpty() && out.length() == 0 && lines.length == 1) continue;
            if (line.startsWith("resourcePacks:")) {
                List<String> packs = new ArrayList<>();
                try {
                    String[] parsed = Tools.GLOBAL_GSON.fromJson(line.substring("resourcePacks:".length()).trim(), String[].class);
                    if (parsed != null) packs.addAll(Arrays.asList(parsed));
                } catch (Exception e) {
                    packs.add("vanilla");
                }
                if (!packs.contains("vanilla")) packs.add(0, "vanilla");
                if (!packs.contains(entry)) packs.add(entry);
                line = "resourcePacks:" + new com.google.gson.GsonBuilder().disableHtmlEscaping().create().toJson(packs);
                replaced = true;
            }
            out.append(line).append('\n');
        }
        if (!replaced) {
            out.append("resourcePacks:[\"vanilla\",\"").append(entry.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"]\n");
        }
        Tools.write(options, out.toString().replaceAll("\n+$", "\n"));
    }

    /** True when the instance's version id looks like it targets the given Minecraft version */
    public static boolean instanceMatchesMcVersion(String instanceVersionId, String mcVersion) {
        if (instanceVersionId == null || mcVersion == null) return false;
        if (instanceVersionId.equals(mcVersion)) return true;
        return instanceVersionId.endsWith("-" + mcVersion) || instanceVersionId.contains("-" + mcVersion + "-")
                || instanceVersionId.startsWith(mcVersion + "-");
    }
}
