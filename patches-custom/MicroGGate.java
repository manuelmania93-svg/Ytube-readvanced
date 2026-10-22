package app.morphe.extension.shared.patches;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Ytube-readvanced customization: microG gate ("entry window").
 *
 * Shown by the launcher hand-off activity before the main activity is touched,
 * so the app can never crash on a missing GmsCore before the user sees why.
 * Window one (this gate) checks for microG and offers to download and install it
 * directly from this window (progress shown inline, then the system installer
 * opens). Only when microG is present does window two (YouTube itself) open.
 */
public class MicroGGate {
    private static final String GMS_CORE_PACKAGE_NAME_FALLBACK = "app.revanced.android.gms";
    private static final String MICROG_DOWNLOAD_URL =
            "https://github.com/manuelmania93-svg/Ytube-readvanced/releases/download/v21.13.164/microG-GmsCore.apk";
    private static final String MICROG_APK_NAME = "microG-GmsCore.apk";
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";

    private static final int BACKGROUND_COLOR = Color.rgb(15, 15, 15);
    private static final int TEXT_COLOR = Color.WHITE;
    private static final int SUBTEXT_COLOR = Color.rgb(170, 170, 170);
    private static final int BUTTON_COLOR = Color.rgb(204, 0, 0);

    /** Survives the gate activity being finished (noHistory) while the process lives. */
    private static long completedDownloadId = -1;
    private static boolean downloadInProgress;

    private MicroGGate() {
    }

    public static boolean isGmsCoreInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(getGmsCorePackageName(context), 0);
            return true;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    private static String getGmsCorePackageName(Context context) {
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);
            if (appInfo.metaData != null) {
                String name = appInfo.metaData.getString("app.revanced.MICROG_PACKAGE_NAME");
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Exception ignored) {
        }
        return GMS_CORE_PACKAGE_NAME_FALLBACK;
    }

    /**
     * Fills the launcher activity with the gate screen.
     */
    public static void showGate(final Activity activity) {
        final int padding = dp(activity, 32);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(BACKGROUND_COLOR);

        TextView title = new TextView(activity);
        title.setText("microG required");
        title.setTextColor(TEXT_COLOR);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        content.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(activity);
        message.setText("YouTube needs microG (GmsCore) to run.\n\n"
                + "1. Tap \"Download microG\" below.\n"
                + "2. Install it when the installer opens.\n"
                + "   (Allow \"install unknown apps\" if asked.)\n"
                + "3. Reopen YouTube.");
        message.setTextColor(SUBTEXT_COLOR);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, dp(activity, 20), 0, dp(activity, 32));
        content.addView(message, messageParams);

        final ProgressBar progressBar = new ProgressBar(activity, null,
                android.R.attr.progressBarStyleHorizontal);
        progressBar.setVisibility(LinearLayout.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progressParams.setMargins(0, 0, 0, dp(activity, 8));
        content.addView(progressBar, progressParams);

        final TextView statusView = new TextView(activity);
        statusView.setTextColor(SUBTEXT_COLOR);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusView.setGravity(Gravity.CENTER);
        statusView.setVisibility(LinearLayout.GONE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, 0, 0, dp(activity, 16));
        content.addView(statusView, statusParams);

        final Button downloadButton = new Button(activity);
        if (completedDownloadId >= 0) {
            // Download already finished earlier (e.g. user left for the unknown-apps setting).
            downloadButton.setText("Install downloaded microG");
            statusView.setText("microG downloaded. Tap to install.");
            statusView.setVisibility(LinearLayout.VISIBLE);
        } else {
            downloadButton.setText("Download microG");
        }
        downloadButton.setTextColor(Color.WHITE);
        downloadButton.setBackgroundColor(BUTTON_COLOR);
        downloadButton.setOnClickListener(view -> {
            if (completedDownloadId >= 0) {
                installDownloadedApk(activity, completedDownloadId);
            } else {
                startMicroGDownload(activity, progressBar, statusView, downloadButton);
            }
        });
        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        downloadParams.setMargins(0, 0, 0, dp(activity, 12));
        content.addView(downloadButton, downloadParams);

        Button clearDataButton = new Button(activity);
        clearDataButton.setText("Clear app data / sign out");
        clearDataButton.setTextColor(SUBTEXT_COLOR);
        clearDataButton.setBackgroundColor(Color.TRANSPARENT);
        clearDataButton.setOnClickListener(view -> confirmClearData(activity));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clearParams.setMargins(0, 0, 0, dp(activity, 12));
        content.addView(clearDataButton, clearParams);

        Button exitButton = new Button(activity);
        exitButton.setText("Exit");
        exitButton.setTextColor(SUBTEXT_COLOR);
        exitButton.setBackgroundColor(Color.TRANSPARENT);
        exitButton.setOnClickListener(view -> activity.finishAndRemoveTask());
        content.addView(exitButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(content);
        activity.setContentView(scrollView);

        Window window = activity.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BACKGROUND_COLOR));
        }
    }

    /**
     * Downloads the microG APK with the system DownloadManager (no extra permissions or
     * manifest entries needed) and shows the progress inside this window. On completion
     * the system package installer is opened for the downloaded APK. Falls back to the
     * browser if the DownloadManager route fails.
     */
    private static void startMicroGDownload(final Activity activity,
                                            final ProgressBar progressBar,
                                            final TextView statusView,
                                            final Button downloadButton) {
        if (downloadInProgress) {
            return;
        }
        final DownloadManager downloadManager =
                (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            openMicroGDownloadInBrowser(activity);
            return;
        }
        final long downloadId;
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(MICROG_DOWNLOAD_URL));
            request.setTitle("microG (GmsCore)");
            request.setDescription("Downloading microG for YouTube");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setMimeType(APK_MIME_TYPE);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, MICROG_APK_NAME);
            downloadId = downloadManager.enqueue(request);
        } catch (Exception exception) {
            Toast.makeText(activity, "In-app download failed, using the browser",
                    Toast.LENGTH_LONG).show();
            openMicroGDownloadInBrowser(activity);
            return;
        }

        downloadInProgress = true;
        progressBar.setVisibility(LinearLayout.VISIBLE);
        progressBar.setIndeterminate(true);
        statusView.setVisibility(LinearLayout.VISIBLE);
        statusView.setText("Downloading microG...");
        downloadButton.setEnabled(false);

        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable poller = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    // The download continues in the background; the button state is
                    // restored from completedDownloadId when the gate is shown again.
                    downloadInProgress = false;
                    return;
                }
                try {
                    Cursor cursor = downloadManager.query(
                            new DownloadManager.Query().setFilterById(downloadId));
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_STATUS));
                                long soFar = cursor.getLong(cursor.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                                long total = cursor.getLong(cursor.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                                if (total > 0) {
                                    int percent = (int) (soFar * 100 / total);
                                    progressBar.setIndeterminate(false);
                                    progressBar.setMax(100);
                                    progressBar.setProgress(percent);
                                    statusView.setText("Downloading microG... " + percent + "%");
                                }
                                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                    completedDownloadId = downloadId;
                                    downloadInProgress = false;
                                    statusView.setText("Download complete. Starting installer...");
                                    downloadButton.setText("Install downloaded microG");
                                    downloadButton.setEnabled(true);
                                    installDownloadedApk(activity, downloadId);
                                    return;
                                }
                                if (status == DownloadManager.STATUS_FAILED) {
                                    downloadInProgress = false;
                                    statusView.setText("Download failed. Using the browser...");
                                    downloadButton.setEnabled(true);
                                    openMicroGDownloadInBrowser(activity);
                                    return;
                                }
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                } catch (Exception ignored) {
                }
                handler.postDelayed(this, 500);
            }
        };
        handler.postDelayed(poller, 500);
    }

    /**
     * Opens the system package installer for a completed DownloadManager download.
     * The content Uri from the DownloadManager needs no FileProvider or manifest entry.
     * Android will ask to allow "install unknown apps" for this app once.
     */
    private static void installDownloadedApk(Activity activity, long downloadId) {
        try {
            DownloadManager downloadManager =
                    (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = downloadManager != null
                    ? downloadManager.getUriForDownloadedFile(downloadId) : null;
            if (uri == null) {
                throw new IllegalStateException("download not found");
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, APK_MIME_TYPE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(activity,
                    "Open the Downloads folder and tap " + MICROG_APK_NAME,
                    Toast.LENGTH_LONG).show();
        }
    }

    private static void openMicroGDownloadInBrowser(Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(MICROG_DOWNLOAD_URL));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(activity,
                    "No browser found. Download microG manually: " + MICROG_DOWNLOAD_URL,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Clear app data / sign out. The app can wipe its own data, but the Google account
     * lives in Android's system account storage (owned by microG), so full sign-out
     * also needs the account removed in Android Settings.
     */
    private static void confirmClearData(final Activity activity) {
        try {
            new AlertDialog.Builder(activity)
                    .setTitle("Clear app data / sign out")
                    .setMessage("This wipes all YouTube app data on this device and the app closes.\n\n"
                            + "To fully sign out, also remove the Google account under "
                            + "Android Settings > Passwords & accounts "
                            + "(use \"Open account settings\").")
                    .setPositiveButton("Clear app data", (dialog, which) -> clearAppData(activity))
                    .setNeutralButton("Open account settings", (dialog, which) -> openAccountSettings(activity))
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception exception) {
            Toast.makeText(activity,
                    "Open Android Settings > Apps > YouTube > Storage > Clear data manually",
                    Toast.LENGTH_LONG).show();
        }
    }

    private static void clearAppData(Activity activity) {
        try {
            ActivityManager activityManager =
                    (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null && activityManager.clearApplicationUserData()) {
                // Success kills this process immediately; nothing else to do.
                return;
            }
        } catch (Exception ignored) {
        }
        Toast.makeText(activity,
                "Could not clear data. Use Android Settings > Apps > YouTube > Storage > Clear data",
                Toast.LENGTH_LONG).show();
    }

    private static void openAccountSettings(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_SYNC_SETTINGS));
            return;
        } catch (Exception ignored) {
        }
        try {
            activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception exception) {
            Toast.makeText(activity,
                    "Open Android Settings > Passwords & accounts manually",
                    Toast.LENGTH_LONG).show();
        }
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }
}
