package com.kx.launcher.fragments;

import static com.kx.launcher.Tools.openPath;
import static com.kx.launcher.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import com.kx.launcher.CustomControlsActivity;
import com.kx.launcher.R;

import com.kx.launcher.Tools;
import com.kx.launcher.contracts.OpenDocumentWithExtension;
import com.kx.launcher.extra.ExtraConstants;
import com.kx.launcher.extra.ExtraCore;
import com.kx.launcher.instances.Instance;
import com.kx.launcher.instances.Instances;
import com.kx.launcher.progresskeeper.ProgressKeeper;
import com.kx.launcher.utils.FileUtils;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private static TextureView mBgVideoView;
    private static MediaPlayer mMediaPlayer;
    private static Surface mSurface;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextureView bgVideoView = view.findViewById(R.id.bg_video_view);
        setupBackgroundVideoGlobal(bgVideoView);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        
        Button welcomeButton = view.findViewById(R.id.welcome_button);
        View homeUiGroup = view.findViewById(R.id.home_ui_group);
        View accountSpinner = requireActivity().findViewById(R.id.account_spinner);
        View settingButton = requireActivity().findViewById(R.id.setting_button);
        
        if (welcomeButton != null) {
            welcomeButton.setOnClickListener(v -> {
                v.setVisibility(View.GONE);
                if (homeUiGroup != null) homeUiGroup.setVisibility(View.VISIBLE);
                if (accountSpinner != null) accountSpinner.setVisibility(View.VISIBLE);
                if (settingButton != null) settingButton.setVisibility(View.VISIBLE);
            });
        }

        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));
        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
    }

    public static void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        if (mSurface != null && mSurface.isValid() && mMediaPlayer == null) {
            playBackgroundVideo(requireContext(), mSurface);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        releaseMediaPlayer();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        releaseMediaPlayer();
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    public static void setupBackgroundVideoGlobal(TextureView textureView) {
        if (textureView == null) return;
        mBgVideoView = textureView;
        mBgVideoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
                mSurface = new Surface(surfaceTexture);
                playBackgroundVideo(mBgVideoView.getContext(), mSurface);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                releaseMediaPlayer();
                if (mSurface != null) {
                    mSurface.release();
                    mSurface = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {}
        });
    }

    private static void playBackgroundVideo(Context context, Surface surface) {
        if (surface == null || !surface.isValid() || context == null) return;
        try {
            int videoResId = context.getResources().getIdentifier("bg_video_a", "raw", context.getPackageName());

            if (videoResId == 0) return;

            releaseMediaPlayer();
            mMediaPlayer = MediaPlayer.create(context, videoResId);
            if (mMediaPlayer != null) {
                mMediaPlayer.setSurface(surface);
                mMediaPlayer.setVolume(0f, 0f); // Muted for background playback
                mMediaPlayer.setLooping(true);  // Seamless 10-15s loop

                mMediaPlayer.setOnVideoSizeChangedListener((mp, width, height) -> {
                    adjustAspectRatio(mBgVideoView, width, height);
                });

                if (mBgVideoView != null) {
                    adjustAspectRatio(mBgVideoView, mMediaPlayer.getVideoWidth(), mMediaPlayer.getVideoHeight());
                }

                mMediaPlayer.start();
            }
        } catch (Exception ignored) {
        }
    }

    private static void adjustAspectRatio(TextureView textureView, int videoWidth, int videoHeight) {
        if (textureView == null || videoWidth <= 0 || videoHeight <= 0) return;

        int viewWidth = textureView.getWidth();
        int viewHeight = textureView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        double scale = Math.max((double) viewWidth / videoWidth, (double) viewHeight / videoHeight);

        float scaledWidth = (float) (scale * videoWidth);
        float scaledHeight = (float) (scale * videoHeight);

        android.graphics.Matrix transform = new android.graphics.Matrix();
        transform.setScale(scaledWidth / viewWidth, scaledHeight / viewHeight, viewWidth / 2f, viewHeight / 2f);
        textureView.setTransform(transform);
    }

    public static void releaseMediaPlayer() {
        if (mMediaPlayer != null) {
            try {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                }
                mMediaPlayer.release();
            } catch (Exception ignored) {}
            mMediaPlayer = null;
        }
    }

    public void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
        } else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
