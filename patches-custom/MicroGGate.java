package app.morphe.extension.shared.patches;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Ytube-readvanced customization: microG gate ("entry window").
 *
 * Shown by the launcher hand-off activity before the main activity is touched,
 * so the app can never crash on a missing GmsCore before the user sees why.
 * Window one (this gate) checks for microG and offers to install it;
 * only when microG is present does window two (YouTube itself) open.
 */
public class MicroGGate {
    private static final String GMS_CORE_PACKAGE_NAME_FALLBACK = "app.revanced.android.gms";
    private static final String MICROG_DOWNLOAD_URL =
            "https://github.com/manuelmania93-svg/Ytube-readvanced/releases/download/v21.13.164/microG-GmsCore.apk";

    private static final int BACKGROUND_COLOR = Color.rgb(15, 15, 15);
    private static final int TEXT_COLOR = Color.WHITE;
    private static final int SUBTEXT_COLOR = Color.rgb(170, 170, 170);
    private static final int BUTTON_COLOR = Color.rgb(204, 0, 0);

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
                + "2. Install the downloaded APK.\n"
                + "3. Reopen YouTube.");
        message.setTextColor(SUBTEXT_COLOR);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, dp(activity, 20), 0, dp(activity, 32));
        content.addView(message, messageParams);

        Button downloadButton = new Button(activity);
        downloadButton.setText("Download microG");
        downloadButton.setTextColor(Color.WHITE);
        downloadButton.setBackgroundColor(BUTTON_COLOR);
        downloadButton.setOnClickListener(view -> openMicroGDownload(activity));
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

    private static void openMicroGDownload(Activity activity) {
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
