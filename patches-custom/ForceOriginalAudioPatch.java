/*
 * Ytube-readvanced customization: in the mom build (package anddea.youtube.de)
 * always prefer the German audio track when a video offers one. Based on the
 * stock ForceOriginalAudioPatch (Morphe/ReVanced, GPLv3) with the selection
 * predicate changed from "original track" (id suffix ".4") to "German track"
 * (id prefix "de.") and made always-on for that package only, so Manuel's own
 * builds from this repo keep the stock "Force original audio" behavior.
 * When no German track exists, no track is marked default and YouTube falls
 * back to its normal behavior - the same code path the stock patch already
 * uses when no original track exists.
 */

package app.morphe.extension.shared.patches;

import androidx.annotation.Nullable;

import java.util.Arrays;

import app.morphe.extension.shared.settings.AppLanguage;
import app.morphe.extension.shared.settings.SharedYouTubeSettings;
import app.morphe.extension.shared.spoof.ClientType;
import app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch;
import app.morphe.extension.shared.utils.Logger;
import app.morphe.extension.shared.utils.Utils;

@SuppressWarnings("unused")
public class ForceOriginalAudioPatch {

    /**
     * Interface to use obfuscated methods.
     */
    public interface AudioTrackInterface {
        // Methods are added during patching.
        String patch_getDisplayName();
        String patch_getId();
        boolean patch_getIsDefault();
    }

    /**
     * Package of the mom build. Only that build gets always-German audio.
     */
    private static final String MOM_BUILD_PACKAGE_NAME = "anddea.youtube.de";

    /**
     * Audio track ids start with the language code: "de." marks German,
     * for both original-German and German auto-dubbed tracks.
     */
    private static final String GERMAN_AUDIO_TRACK_PREFIX = "de.";

    private static final boolean FORCE_ORIGINAL_AUDIO = SharedYouTubeSettings.FORCE_ORIGINAL_AUDIO.get();

    private static final String DEFAULT_AUDIO_TRACKS_SUFFIX = ".4";

    @Nullable
    private static Boolean preferGermanAudio;

    /**
     * @return true only inside the mom build (separate package).
     */
    private static boolean preferGermanAudio() {
        if (preferGermanAudio == null) {
            boolean detected = false;
            try {
                detected = MOM_BUILD_PACKAGE_NAME.equals(Utils.getContext().getPackageName());
            } catch (Exception ex) {
                Logger.printException(() -> "preferGermanAudio failure", ex);
            }
            final boolean enabled = detected;
            Logger.printDebug(() -> "Always-German audio: " + enabled);
            preferGermanAudio = enabled;
        }
        return preferGermanAudio;
    }

    public static void setEnabled(ClientType client) {
        if (SpoofVideoStreamsPatch.isSpoofingEnabled()
                && !client.canLogin && !client.supportsMultiAudioTracks) {
            if (preferGermanAudio()) {
                // Mom build: request German for unauthenticated spoofed clients
                // without multi-audio streams; if a video has no German track,
                // YouTube serves the original audio.
                AppLanguage override = AppLanguage.DE;
                Logger.printDebug(() -> "Setting language override: " + override);
                SpoofVideoStreamsPatch.setLocaleOverride(override.getLocale());
            } else if (FORCE_ORIGINAL_AUDIO) {
                // Stock behavior for every other build from this repo.
                AppLanguage override = AppLanguage.NB; // Norwegian Bokmal.
                Logger.printDebug(() -> "Setting language override: " + override);
                SpoofVideoStreamsPatch.setLocaleOverride(override.getLocale());
            }
        }
    }

    /**
     * The available audio tracks of the current video.
     */
    @Nullable
    private static AudioTrackInterface[] currentAudioTracks;

    /**
     * Injection point.
     * Called after {@link #isDefaultAudioStream(boolean, String, String)}.
     *
     * @param   audioTracks Audio tracks available, sorted alphabetically.
     * @return  German (mom build) or original (other builds) audio track id.
     */
    @Nullable
    public static String getDefaultAudioTrackId(AudioTrackInterface[] audioTracks) {
        // No need to override when the number of audio tracks is 1 or less.
        if ((preferGermanAudio() || FORCE_ORIGINAL_AUDIO) && audioTracks != null && audioTracks.length > 1) {
            try {
                Utils.verifyOnMainThread();

                final boolean availableAudioTracksChanged = (currentAudioTracks == null)
                        || !Arrays.equals(currentAudioTracks, audioTracks);
                if (availableAudioTracksChanged) {
                    currentAudioTracks = audioTracks;

                    for (AudioTrackInterface audioTrack : audioTracks) {
                        boolean isDefault = audioTrack.patch_getIsDefault();
                        String audioTrackDisplayName = audioTrack.patch_getDisplayName();
                        String audioTrackId = audioTrack.patch_getId();

                        if (isDefault) {
                            Logger.printDebug(() -> "Overriding audio track: " + audioTrackDisplayName);
                            return audioTrackId;
                        }
                    }
                }
            } catch (Exception ex) {
                Logger.printException(() -> "getDefaultAudioTrackId failure", ex);
            }
        }

        return null;
    }

    /**
     * Injection point.
     */
    public static boolean ignoreDefaultAudioStream(boolean original) {
        if (preferGermanAudio() || FORCE_ORIGINAL_AUDIO) {
            return false;
        }
        return original;
    }

    /**
     * Injection point.
     */
    public static boolean isDefaultAudioStream(boolean isDefault, String audioTrackId, String audioTrackDisplayName) {
        final boolean preferGerman = preferGermanAudio();
        if (preferGerman || FORCE_ORIGINAL_AUDIO) {
            try {
                if (audioTrackId == null || audioTrackId.isEmpty()) {
                    // Older app targets can have empty audio tracks and these might be placeholders.
                    // The real audio tracks are called after these.
                    return isDefault;
                }

                Logger.printDebug(() -> "default: " + String.format("%-5s", isDefault) + " id: "
                        + String.format("%-8s", audioTrackId) + " name:" + audioTrackDisplayName);

                if (preferGerman) {
                    final boolean isGerman = audioTrackId.startsWith(GERMAN_AUDIO_TRACK_PREFIX);
                    if (isGerman) {
                        Logger.printDebug(() -> "Using German audio: " + audioTrackId);
                    }
                    return isGerman;
                }

                final boolean isOriginal = audioTrackId.endsWith(DEFAULT_AUDIO_TRACKS_SUFFIX);
                if (isOriginal) {
                    Logger.printDebug(() -> "Using audio: " + audioTrackId);
                }

                return isOriginal;
            } catch (Exception ex) {
                Logger.printException(() -> "isDefaultAudioStream failure", ex);
            }
        }

        return isDefault;
    }
}
