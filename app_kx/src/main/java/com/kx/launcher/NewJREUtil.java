package com.kx.launcher;

import static com.kx.launcher.Architecture.archAsString;

import android.content.res.AssetManager;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import com.kx.launcher.instances.Instance;
import com.kx.launcher.instances.Instances;
import com.kx.launcher.multirt.MultiRTUtils;
import com.kx.launcher.multirt.Runtime;
import com.kx.launcher.progresskeeper.DownloaderProgressWrapper;
import com.kx.launcher.utils.DownloadUtils;
import com.kx.launcher.utils.MathUtils;
import com.kx.launcher.utils.SignatureCheckUtil;
import com.kx.launcher.utils.jre.RuntimeSelectionException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NewJREUtil {
    private static final String DOWNLOAD_URL = "https://pojavlauncherteam.github.io/jre-download/";
    private static final String MIRROR_BASE_URL = "https://github.com/MojoLauncher/android-openjdk-build-multiarch/releases/download/rolling/";
    
    private static String getRemoteRuntimeVersion(InternalRuntime internalRuntime) throws IOException{
        return DownloadUtils.downloadString(DOWNLOAD_URL+internalRuntime.path+"/version");
    }

    private static boolean checkLastUpdateTime(InternalRuntime internalRuntime) {
        long lastUpdateTime = MultiRTUtils.readLastUpdateTime(internalRuntime.name);
        long currentTime = System.currentTimeMillis() / 1000L;
        return lastUpdateTime != -1 && currentTime - lastUpdateTime < 259200;
    }

    private static void writeLastUpdateTime(InternalRuntime internalRuntime) {
        MultiRTUtils.writeLastUpdateTime(internalRuntime.name, System.currentTimeMillis() / 1000L);
    }

    private static void checkInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime) throws RuntimeSelectionException {
        String remote_runtime_version;
        String installed_runtime_version = MultiRTUtils.readInternalRuntimeVersion(internalRuntime.name);
        if(installed_runtime_version != null && checkLastUpdateTime(internalRuntime)) return;
        try {
            remote_runtime_version = getRemoteRuntimeVersion(internalRuntime);
        }catch (IOException exc) {
            Log.i("NewJreUtil", "Failed to get remote runtime version", exc);
            // We failed to get the version of the runtime available on the web server.
            // Let's just hope that we have an internal version installed in that case.
            if(installed_runtime_version == null) {
                // Nothing installed and the main server doesn't have it (e.g. Java 25): use the mirror
                if(installFromMirror(internalRuntime)) return;
                throw new RuntimeSelectionException(RuntimeSelectionException.RUNTIME_STATE_INTERNAL_RUNTIME_MISSING, internalRuntime.majorVersion);
            }
            return;
        }
        // this implicitly checks for null, so it will unpack the runtime even if we don't have one installed
        if(!remote_runtime_version.equals(installed_runtime_version)) {
            try {
                unpackInternalRuntime(assetManager, internalRuntime, remote_runtime_version);
            } catch (RuntimeSelectionException e) {
                // Main server install failed and nothing is installed yet: try the mirror before giving up
                if(installed_runtime_version == null && installFromMirror(internalRuntime)) return;
                throw e;
            }
        }
        writeLastUpdateTime(internalRuntime);
    }

    /**
     * Fallback installer: downloads the prebuilt jreXX-pojav.zip (universal + per-arch binpacks + version)
     * from the MojoLauncher release page and installs it as the given internal runtime.
     * @return true if the runtime was installed successfully
     */
    private static boolean installFromMirror(InternalRuntime internalRuntime) {
        if(internalRuntime.mirrorUrl == null) return false;
        File zipCache = null, universalCache = null, platformCache = null;
        try {
            zipCache = File.createTempFile("jre-mirror-", ".zip", Tools.DIR_CACHE);
            universalCache = File.createTempFile("jre-mirror-", "-universal", Tools.DIR_CACHE);
            platformCache = File.createTempFile("jre-mirror-", "-platform", Tools.DIR_CACHE);

            DownloadUtils.downloadFileMonitored(
                    internalRuntime.mirrorUrl, zipCache, new byte[8192],
                    new DownloaderProgressWrapper(R.string.downloading_java_runtime_uni, ProgressLayout.UNPACK_RUNTIME)
            );

            String platformBinFile = "bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz";
            String binpackVersion;
            try (ZipFile zipFile = new ZipFile(zipCache)) {
                ZipEntry versionEntry = zipFile.getEntry("version");
                ZipEntry universalEntry = zipFile.getEntry("universal.tar.xz");
                ZipEntry platformEntry = zipFile.getEntry(platformBinFile);
                if(versionEntry == null || universalEntry == null || platformEntry == null) {
                    Log.e("NewJreUtil", "Mirror archive is missing files for " + platformBinFile);
                    return false;
                }
                binpackVersion = Tools.read(zipFile.getInputStream(versionEntry)).trim();
                extractEntry(zipFile.getInputStream(universalEntry), universalCache);
                extractEntry(zipFile.getInputStream(platformEntry), platformCache);
            }

            try (FileInputStream universal = new FileInputStream(universalCache);
                 FileInputStream platform = new FileInputStream(platformCache)) {
                MultiRTUtils.installRuntimeNamedBinpack(universal, platform, internalRuntime.name, binpackVersion);
                MultiRTUtils.postPrepare(internalRuntime.name);
                MultiRTUtils.forceReread(internalRuntime.name);
            }
            writeLastUpdateTime(internalRuntime);
            return true;
        } catch (IOException e) {
            Log.e("NewJreUtil", "Mirror install failed for " + internalRuntime.name, e);
            return false;
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
            if(zipCache != null && zipCache.isFile()) //noinspection ResultOfMethodCallIgnored
                zipCache.delete();
            if(universalCache != null && universalCache.isFile()) //noinspection ResultOfMethodCallIgnored
                universalCache.delete();
            if(platformCache != null && platformCache.isFile()) //noinspection ResultOfMethodCallIgnored
                platformCache.delete();
        }
    }

    private static void extractEntry(InputStream in, File out) throws IOException {
        try (InputStream input = in; OutputStream output = new FileOutputStream(out)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
    }

    private static class RuntimeDownloaderVerifier {
        
        private final Map<String, byte[]> mSignatures;
        private final String mRuntimePath;
        private final byte[] mDownloadBuffer = new byte[8192];
        private final SignatureCheckUtil mSignatureCheckUtil;

        public RuntimeDownloaderVerifier(Map<String, byte[]> signatures, InternalRuntime internalRuntime, SignatureCheckUtil mSignatureCheckUtil) {
            this.mSignatures = signatures;
            this.mRuntimePath = DOWNLOAD_URL + internalRuntime.path + "/";
            this.mSignatureCheckUtil = mSignatureCheckUtil;
        }

        public boolean downloadAndVerify(String component, File output, int progressString) throws IOException {
            DownloadUtils.downloadFileMonitored(
                    mRuntimePath + component, output, mDownloadBuffer,
                    new DownloaderProgressWrapper(progressString, ProgressLayout.UNPACK_RUNTIME)
            );
            byte[] signature = mSignatures.get(component);
            try (FileInputStream fileInputStream = new FileInputStream(output)){
                return mSignatureCheckUtil.verify(fileInputStream, signature);
            }
        }
    }

    private static void throwInstallFail(InternalRuntime internalRuntime, Throwable cause) throws RuntimeSelectionException {
        RuntimeSelectionException e = new RuntimeSelectionException(RuntimeSelectionException.RUNTIME_STATE_INSTALLATION_FAILED, internalRuntime.majorVersion);
        e.initCause(cause);
        throw e;
    }

    private static void throwInstallFail(InternalRuntime internalRuntime) throws RuntimeSelectionException {
        throw new RuntimeSelectionException(RuntimeSelectionException.RUNTIME_STATE_INSTALLATION_FAILED, internalRuntime.majorVersion);
    }

    private static void unpackInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime, String versionSignatures) throws RuntimeSelectionException {
        Map<String, byte[]> signatures = SignatureCheckUtil.decodeSignatureBundle(versionSignatures);
        String platformBinFile = "bin-"+archAsString(Tools.DEVICE_ARCHITECTURE)+".tar.xz";
        if(!signatures.containsKey("universal.tar.xz") || !signatures.containsKey(platformBinFile)) {
            throwInstallFail(internalRuntime);
        }

        File universalCache = null, platformCache = null;
        try {
            SignatureCheckUtil signatureCheckUtil = SignatureCheckUtil.create(assetManager);
            universalCache = File.createTempFile("jre-install-", "-universal", Tools.DIR_CACHE);
            platformCache = File.createTempFile("jre-install-", "-platform", Tools.DIR_CACHE);
            RuntimeDownloaderVerifier runtimeDownloaderVerifier = new RuntimeDownloaderVerifier(signatures, internalRuntime, signatureCheckUtil);
            if (!runtimeDownloaderVerifier.downloadAndVerify("universal.tar.xz", universalCache, R.string.downloading_java_runtime_uni) ||
                    !runtimeDownloaderVerifier.downloadAndVerify(platformBinFile, platformCache, R.string.downloading_java_runtime_platform)) {
                throwInstallFail(internalRuntime);
            }

            try (FileInputStream universal = new FileInputStream(universalCache); FileInputStream platform = new FileInputStream(platformCache)) {
                MultiRTUtils.installRuntimeNamedBinpack(universal, platform, internalRuntime.name, versionSignatures);
                MultiRTUtils.postPrepare(internalRuntime.name);
                MultiRTUtils.forceReread(internalRuntime.name);
            }
        } catch (IOException e) {
            throwInstallFail(internalRuntime, e);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
            // Those files being deleted are on a "i wish" basis
            if(universalCache != null && universalCache.isFile()) //noinspection ResultOfMethodCallIgnored
                universalCache.delete();
            if(platformCache != null && platformCache.isFile()) //noinspection ResultOfMethodCallIgnored
                platformCache.delete();
        }
    }

    private static InternalRuntime getInternalRuntime(Runtime runtime) {
        for(InternalRuntime internalRuntime : InternalRuntime.values()) {
            if(internalRuntime.name.equals(runtime.name)) return internalRuntime;
        }
        return null;
    }

    private static MathUtils.RankedValue<Runtime> getNearestInstalledRuntime(int targetVersion) {
        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        MathUtils.RankedValue<Runtime> nearest = MathUtils.findNearestPositive(targetVersion, runtimes, (runtime)->runtime.javaVersion);
        if (nearest != null) return nearest;
        Runtime highest = null;
        for (Runtime rt : runtimes) {
            if (highest == null || rt.javaVersion > highest.javaVersion) highest = rt;
        }
        if (highest == null) return null;
        return new MathUtils.RankedValue<>(highest, Math.abs(highest.javaVersion - targetVersion));
    }

    private static MathUtils.RankedValue<InternalRuntime> getNearestInternalRuntime(int targetVersion) {
        List<InternalRuntime> runtimeList = Arrays.asList(InternalRuntime.values());
        MathUtils.RankedValue<InternalRuntime> nearest = MathUtils.findNearestPositive(targetVersion, runtimeList, (runtime)->runtime.majorVersion);
        if (nearest != null) return nearest;
        InternalRuntime highest = InternalRuntime.JRE_21;
        for (InternalRuntime rt : runtimeList) {
            if (rt.majorVersion > highest.majorVersion) highest = rt;
        }
        return new MathUtils.RankedValue<>(highest, Math.abs(highest.majorVersion - targetVersion));
    }


    public static void installNewJreIfNeeded(AssetManager assetManager, JVersionList.Version versionInfo) throws IOException, RuntimeSelectionException {
        //Now we have the reliable information to check if our runtime settings are good enough
        if (versionInfo.javaVersion == null || "jre-legacy".equalsIgnoreCase(versionInfo.javaVersion.component)) return;

        int gameRequiredVersion = versionInfo.javaVersion.majorVersion;

        Instance instance = Instances.loadSelectedInstance();
        String profileRuntime = Tools.getSelectedRuntime(instance);
        Runtime runtime = MultiRTUtils.read(profileRuntime);
        // Partly trust the user with his own selection, if the game can even try to run in this case
        if (runtime.javaVersion >= gameRequiredVersion) {
            // Check whether the selection is an internal runtime
            InternalRuntime internalRuntime = getInternalRuntime(runtime);
            // If it is, check if updates are available from the APK file
            if(internalRuntime != null) {
                // Not calling showRuntimeFail on failure here because we did, technically, find the compatible runtime
                checkInternalRuntime(assetManager, internalRuntime);
            }
            return;
        }

        // If the runtime version selected by the user is not appropriate for this version (which means the game won't run at all)
        // automatically pick from either an already installed runtime, or a runtime packed with the launcher
        MathUtils.RankedValue<?> nearestInstalledRuntime = getNearestInstalledRuntime(gameRequiredVersion);
        MathUtils.RankedValue<?> nearestInternalRuntime = getNearestInternalRuntime(gameRequiredVersion);

        MathUtils.RankedValue<?> selectedRankedRuntime = MathUtils.objectMin(
                nearestInternalRuntime, nearestInstalledRuntime, (value)->value.rank
        );

        // No possible selections
        if(selectedRankedRuntime == null) {
            throw new RuntimeSelectionException(RuntimeSelectionException.RUNTIME_STATE_SELECTION_FAILED, gameRequiredVersion);
        }

        Object selected = selectedRankedRuntime.value;
        String appropriateRuntime;
        InternalRuntime internalRuntime;

        // Perform checks on the picked runtime
        if(selected instanceof Runtime) {
            // If it's an already installed runtime, save its name and check if
            // it's actually an internal one (just in case)
            Runtime selectedRuntime = (Runtime) selected;
            appropriateRuntime = selectedRuntime.name;
            internalRuntime = getInternalRuntime(selectedRuntime);
        } else if (selected instanceof InternalRuntime) {
            // If it's an internal runtime, set it's name as the appropriate one.
            internalRuntime = (InternalRuntime) selected;
            appropriateRuntime = internalRuntime.name;
        } else {
            throw new RuntimeException("Unexpected type of selected: "+selected.getClass().getName());
        }

        // If it turns out the selected runtime is actually an internal one, attempt automatic installation or update
        if(internalRuntime != null) {
            checkInternalRuntime(assetManager, internalRuntime);
        }

        instance.selectedRuntime = appropriateRuntime;
        instance.write();
    }

    private enum InternalRuntime {
        JRE_17(17, "Internal-17", "components/jre-new", null),
        JRE_21(21, "Internal-21", "components/jre-21", null),
        JRE_25(25, "Internal-25", "components/jre-25", MIRROR_BASE_URL + "jre25-pojav.zip");
        public final int majorVersion;
        public final String name;
        public final String path;
        public final String mirrorUrl;
        InternalRuntime(int majorVersion, String name, String path, String mirrorUrl) {
            this.majorVersion = majorVersion;
            this.name = name;
            this.path = path;
            this.mirrorUrl = mirrorUrl;
        }
    }

}
