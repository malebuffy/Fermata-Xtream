package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS;
import static android.util.Base64.URL_SAFE;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.BuildConfig.AUTO;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.action.KeyEventHandler.handleKeyEvent;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityPrefs.BRIGHTNESS;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityPrefs.CHANGE_BRIGHTNESS;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityPrefs.CLOCK_POS;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityPrefs.LOCALE;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityPrefs.VOICE_CONTROL_SUBST;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityPrefs.VOICE_CONTROl_ENABLED;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityPrefs.VOICE_CONTROl_FB;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import static me.aap.utils.ui.UiUtils.showAlert;
import static me.aap.utils.ui.UiUtils.toIntPx;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.Manifest;
import android.Manifest.permission;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.OperationCanceledException;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;

import com.google.android.material.textview.MaterialTextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.BuildConfig;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.FermataApplication;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.action.Action;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.action.Key;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.AddonInfo;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.AddonManager;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.FermataActivityAddon;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.FermataAddon;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.FermataFragmentAddon;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.MediaLibAddon;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine.MediaEngineManager;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.AtvInterface;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.DefaultMediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.ExportedItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.ExtRoot;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.IntentPlayable;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.BrowsableItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Item;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.PlayableItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Playlist;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.SearchFolder;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.pref.PlaybackControlPrefs;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.FermataServiceUiBinder;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.MediaSessionCallback;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.MediaSessionCallbackAssistant;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.AudioEffectsFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.FavoritesFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.FoldersFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.GalleryFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.GalleryVideoPlayerFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.HomeFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.ImageViewerFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.MainActivityFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.MediaLibFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.NavBarMediator;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.PlaylistsFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.SettingsFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.SubtitlesFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.VideoGalleryFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.BodyLayout;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.ControlPanelView;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.VideoView;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.concurrent.HandlerExecutor;
import me.aap.utils.event.ListenerLeakDetector;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.Function;
import me.aap.utils.function.IntObjectFunction;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.misc.MiscUtils;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.DialogBuilder;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class MainActivityDelegate extends ActivityDelegate
		implements MediaSessionCallbackAssistant, PreferenceStore.Listener {
	public static final String INTENT_ACTION_OPEN = "open";
	public static final String INTENT_ACTION_PLAY = "play";
	public static final String INTENT_ACTION_UPDATE = "update";
	public static final String INTENT_ACTION_FINISH = "finish";
	public static final String INTENT_EXTRA_VIDEO_SETTINGS = "video_settings";
	public static final String INTENT_EXTRA_FRAGMENT_ID = "fragment_id";
	public static final String INTENT_EXTRA_FRAGMENT_INPUT = "fragment_input";
	private static final String INTENT_SCHEME = "fermata";
	private static final String PREF_LAST_MODULE = "last_used_module";
	private final HandlerExecutor handler = new HandlerExecutor(App.get().getHandler().getLooper());
	private final NavBarMediator navBarMediator = new NavBarMediator();
	private final FermataServiceUiBinder mediaServiceBinder;
	private ToolBarView toolBar;
	private NavBarView navBar;
	private BodyLayout body;
	private ControlPanelView controlPanel;
	private FloatingButton floatingButton;
	private ContentLoadingProgressBar progressBar;
	private View videoMotionGuard;
	private TextView speedDisplay;
	private FutureSupplier<?> contentLoading;
	private boolean barsHidden;
	private boolean videoMode;
	private boolean driving;
	private float carSpeed = Float.NaN;
	private String speedDisplayText = "";
	private int brightness = 255;
	private SpeechListener speechListener;
	private VoiceCommandHandler voiceCommandHandler;

	public MainActivityDelegate(AppActivity activity, FermataServiceUiBinder binder) {
		super(activity);
		mediaServiceBinder = binder;
	}

	@NonNull
	public static MainActivityDelegate get(Context ctx) {
		return (MainActivityDelegate) ActivityDelegate.get(ctx);
	}

	@NonNull
	@SuppressWarnings("unchecked")
	public static FutureSupplier<MainActivityDelegate> getActivityDelegate(Context ctx) {
		return (FutureSupplier<MainActivityDelegate>) ActivityDelegate.getActivityDelegate(ctx);
	}

	public static void attachBaseContext(Context ctx) {
		MainActivityPrefs prefs = Prefs.instance;
		if (!prefs.hasPref(LOCALE)) return;

		Resources res = ctx.getResources();
		Configuration cfg = res.getConfiguration();
		Locale loc = prefs.getLocalePref();
		cfg.setLocale(loc);
		Locale.setDefault(loc);
		res.updateConfiguration(cfg, res.getDisplayMetrics());
	}

	public static Uri toIntentUri(String action, String itemId) {
		String id = Base64.encodeToString(itemId.getBytes(US_ASCII), URL_SAFE);
		return new Uri.Builder().scheme(INTENT_SCHEME).authority(action).path(id).build();
	}

	@Nullable
	public static String intentUriToId(Uri u) {
		if ((u == null) || !INTENT_SCHEME.equals(u.getScheme())) return null;
		String id = u.getPath();
		return (id == null) ? null : new String(Base64.decode(id.substring(1), URL_SAFE), US_ASCII);
	}

	@Nullable
	public static String intentUriToAction(Uri u) {
		return (u != null) && INTENT_SCHEME.equals(u.getScheme()) ? u.getHost() : null;
	}

	/** Opens a module on the phone while Android Auto stays on the head unit. */
	public static boolean launchPhoneFragment(Context ctx, int fragmentId, @Nullable String input) {
		var app = FermataApplication.get();
		Intent intent = new Intent(app, MainActivity.class);
		intent.putExtra(INTENT_EXTRA_FRAGMENT_ID, fragmentId);
		if (input != null) intent.putExtra(INTENT_EXTRA_FRAGMENT_INPUT, input);
		intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
		try {
			app.startActivity(intent);
			return true;
		} catch (Exception err) {
			Log.e(err, "Failed to launch phone activity for fragment ", fragmentId);
			return false;
		}
	}

	@Override
	public void onActivityCreate(@Nullable Bundle state) {
		super.onActivityCreate(state);
		Intent intent = getIntent();
		if ((intent != null) && INTENT_ACTION_FINISH.equals(intent.getAction())) {
			finish();
			return;
		}

		getPrefs().addBroadcastListener(this);
		int navId;
		int fragmentId;

		if (state != null) {
			navId = state.getInt("navId", ID_NULL);
			fragmentId = state.getInt("fragmentId", ID_NULL);
		} else {
			navId = ID_NULL;
			fragmentId = ID_NULL;
		}

		AppActivity a = getAppActivity();
		FermataServiceUiBinder b = getMediaServiceBinder();
		Context ctx = a.getContext();
		b.getMediaSessionCallback().getSession().setSessionActivity(
				PendingIntent.getActivity(ctx, 0, new Intent(ctx, a.getClass()), FLAG_IMMUTABLE));
		b.getMediaSessionCallback().addAssistant(this, isCarActivityNotMirror() ? 0 : 1);
		if (b.getCurrentItem() == null) b.getMediaSessionCallback().onPrepare();
		init();

		for (FermataAddon addon : AddonManager.get().getAddons()) {
			if (addon instanceof FermataActivityAddon)
				((FermataActivityAddon) addon).onActivityCreate(this);
		}

		String[] perms = getRequiredPermissions();
		a.checkPermissions(perms).onCompletion((result, fail) -> {
			if (fail != null) {
				if (!isCarActivityNotMirror()) Log.e(fail);
			} else {
				Log.d("Requested permissions: ", Arrays.toString(perms),
						". Result: " + Arrays.toString(result));
			}

			if (fragmentId != ID_NULL) {
				setActiveNavItemId(navId);
				showFragment(fragmentId);
				return;
			}

			if ((intent != null) && intent.hasExtra(INTENT_EXTRA_FRAGMENT_ID)) {
				int moduleId = intent.getIntExtra(INTENT_EXTRA_FRAGMENT_ID, ID_NULL);
				String input = intent.getStringExtra(INTENT_EXTRA_FRAGMENT_INPUT);
				if (moduleId != ID_NULL) {
					setActiveNavItemId(moduleId);
					showFragment(moduleId, input);
					return;
				}
			}

			if ((intent != null) && intent.getBooleanExtra(INTENT_EXTRA_VIDEO_SETTINGS, false)) {
				showVideoMotionSettings();
				return;
			}

			if ((intent != null) && !Intent.ACTION_MAIN.equals(intent.getAction())) {
				handleIntent(intent).onCompletion((r, err) -> {
					if (err != null) Log.e(err, "Failed to handle intent ", intent);
					if ((r == null) || !r) defaultIntent();
				});
			} else {
				defaultIntent();
			}
		});
	}

	private void showFirstRunScamWarningIfNeeded() {
		if (getPrefs().getBooleanPref(MainActivityPrefs.FIRST_RUN_SCAM_WARNING_SHOWN)) return;
		getPrefs().applyBooleanPref(MainActivityPrefs.FIRST_RUN_SCAM_WARNING_SHOWN, true);
		post(() -> showAlert(getContext(), decodeScamWarningMessage()));
	}

	private String decodeScamWarningMessage() {
		final int[] encoded = {
				15, 51, 50, 40, 123, 50, 40, 123, 58, 123, 61, 41, 62, 62, 123, 58, 43, 43, 117,
				123, 18, 61, 123, 34, 52, 46, 123, 51, 58, 45, 62, 123, 43, 58, 50, 63, 123, 61,
				52, 41, 123, 47, 51, 50, 40, 123, 58, 43, 43, 55, 50, 56, 58, 47, 50, 52, 53, 123,
				34, 52, 46, 123, 44, 62, 41, 62, 123, 40, 56, 58, 54, 54, 62, 63, 117
		};
		final int key = 91;
		char[] decoded = new char[encoded.length];

		for (int i = 0; i < encoded.length; i++) {
			decoded[i] = (char) (encoded[i] ^ key);
		}

		return new String(decoded);
	}

	@Override
	protected void onActivityNewIntent(Intent intent) {
		super.onActivityNewIntent(intent);
		if ((intent != null) && intent.hasExtra(INTENT_EXTRA_FRAGMENT_ID)) {
			int moduleId = intent.getIntExtra(INTENT_EXTRA_FRAGMENT_ID, ID_NULL);
			String input = intent.getStringExtra(INTENT_EXTRA_FRAGMENT_INPUT);
			if (moduleId != ID_NULL) {
				setActiveNavItemId(moduleId);
				showFragment(moduleId, input);
				return;
			}
		}
		handleIntent(intent);
	}

	private FutureSupplier<Boolean> handleIntent(Intent intent) {
		if (INTENT_ACTION_FINISH.equals(intent.getAction())) {
			finish();
			return completed(true);
		}

		for (FermataAddon a : AddonManager.get().getAddons()) {
			if (a.handleIntent(this, intent)) return completed(true);
		}

		Uri u = intent.getData();

		if (u != null) {
			if (INTENT_SCHEME.equals(u.getScheme())) {
				String action = u.getHost();
				if (action == null) return completed(false);
				String id = u.getPath();
				if (id == null) return completed(false);
				id = new String(Base64.decode(id.substring(1), URL_SAFE), US_ASCII);

				if (INTENT_ACTION_OPEN.equals(action)) {
					goToItem(id).map(MiscUtils::nonNull);
					return completed(true);
				} else if (INTENT_ACTION_PLAY.equals(action)) {
					goToItem(id).map(i -> {
						if (!(i instanceof PlayableItem)) return false;
						getMediaServiceBinder().playItem((PlayableItem) i);
						return true;
					});
					return completed(true);
				}
			} else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
				PlayableItem i = new IntentPlayable(this, u);
				getMediaServiceBinder().stop();
				post(() -> {
					if (!(getActiveFragment() instanceof MediaLibFragment))
						goToCurrent().onSuccess(v -> getMediaServiceBinder().playItem(i));
					else getMediaServiceBinder().playItem(i);
				});
			}
		}

		return completed(false);
	}

	private void defaultIntent() {
		if (getActiveFragment() != null) {
			checkUpdates();
			return;
		}

		String showAddon = getPrefs().getShowAddonOnStartPref();
		if (showAddon != null && !showAddon.isEmpty()) {
			FermataAddon addon = AddonManager.get().getAddon(showAddon);
			if (addon instanceof FermataFragmentAddon) {
				int id = ((FermataFragmentAddon) addon).getAddonId();
				openModule(id);
				checkUpdates();
				return;
			}
			// Addon may not be loaded yet (dynamic feature) — try install/open by class name.
			for (AddonInfo ai : BuildConfig.ADDONS) {
				if (!showAddon.equals(ai.className)) continue;
				AddonManager.get().getOrInstallAddon(ai.className).onSuccess(a -> {
					if (a instanceof FermataFragmentAddon) {
						openModule(((FermataFragmentAddon) a).getAddonId());
					} else {
						setActiveNavItemId(R.id.home_fragment);
						showFragment(R.id.home_fragment);
					}
				}).onFailure(err -> {
					setActiveNavItemId(R.id.home_fragment);
					showFragment(R.id.home_fragment);
				});
				checkUpdates();
				return;
			}
		}

		setActiveNavItemId(R.id.home_fragment);
		showFragment(R.id.home_fragment);
		checkUpdates();
	}

	private void checkUpdates() {
		if (!AUTO) return;
		if (getAppActivity() instanceof MainActivity a) a.uninstallControl().thenRun(() -> {
			if (getPrefs().getCheckUpdatesPref()) a.checkUpdates();
		});
	}

	@Override
	protected void setUncaughtExceptionHandler() {
		if (!AUTO || getAppActivity().isCarActivity()) return;
		super.setUncaughtExceptionHandler();
	}

	@Override
	protected void onActivitySaveInstanceState(@NonNull Bundle outState) {
		super.onActivitySaveInstanceState(outState);
		if (isRecreating()) {
			outState.putInt("navId", getActiveNavItemId());
			outState.putInt("fragmentId", getActiveFragmentId());
		}
	}

	public void recreate() {
		if (AUTO && isCarActivityNotMirror()) showAlert(getContext(), R.string.please_restart_app);
		else getHandler().post(super::recreate);
	}

	@Override
	public void onActivityResume() {
		super.onActivityResume();
		checkMirroringMode(true);
		MediaSessionCallback cb = getMediaSessionCallback();
		cb.setExternalSurface(null, 0, 0);
		VideoView vv = getBody().getVideoView();
		if (vv != null) cb.addVideoView(vv, isCarActivityNotMirror() ? 0 : 1);
		for (FermataAddon addon : AddonManager.get().getAddons()) {
			if (addon instanceof FermataActivityAddon)
				((FermataActivityAddon) addon).onActivityResume(this);
		}
	}

	@Override
	public void onActivityPause() {
		super.onActivityPause();
		VideoView vv = getBody().getVideoView();
		if (vv != null) getMediaSessionCallback().removeVideoView(vv);
		for (FermataAddon addon : AddonManager.get().getAddons()) {
			if (addon instanceof FermataActivityAddon)
				((FermataActivityAddon) addon).onActivityPause(this);
		}
	}

	@Override
	public void onActivityDestroy() {
		super.onActivityDestroy();
		handler.close();
		getMediaServiceBinder().getMediaSessionCallback().removeAssistant(this);
		getPrefs().removeBroadcastListener(this);
		if (speechListener != null) speechListener.destroy();

		for (FermataAddon addon : AddonManager.get().getAddons()) {
			if (addon instanceof FermataActivityAddon)
				((FermataActivityAddon) addon).onActivityDestroy(this);
		}

		if (me.aap.utils.BuildConfig.D) {
			boolean leaks = ListenerLeakDetector.hasLeaks((b, l) -> {
				if (l instanceof ExportedItem.ListenerWrapper)
					l = ((ExportedItem.ListenerWrapper) l).getListener();
				if (l instanceof Key.PrefsListener) return false;
				if (l instanceof FermataAddon) return false;
				if (l instanceof AtvInterface) return false;
				if ((l instanceof DefaultMediaLib) && (b instanceof DefaultMediaLib)) return false;
				if ((l instanceof MediaEngineManager) && (b instanceof DefaultMediaLib)) return false;
				return (!(l instanceof AddonManager)) ||
						(b != FermataApplication.get().getPreferenceStore());
			});
			if (leaks) Log.e(new IllegalStateException("Listener leaks detected!"));
		}
	}

	public void onActivityFinish() {
		super.onActivityFinish();
	}

	public void setVideoMotionGuardDriving(boolean driving) {
		if (this.driving == driving) return;
		this.driving = driving;
		updateVideoMotionGuard();
	}

	public void setCarSpeed(float metersPerSecond) {
		carSpeed = metersPerSecond;
		updateSpeedDisplay();
	}

	public void showVideoMotionSettings() {
		showFragment(R.id.settings_fragment, SettingsFragment.INPUT_VIDEO_SETTINGS);
	}

	@NonNull
	@Override
	public FermataActivity getAppActivity() {
		return (FermataActivity) super.getAppActivity();
	}

	public boolean isCarActivity() {
		return AUTO && getAppActivity().isCarActivity() || FermataApplication.get().isMirroringMode();
	}

	public boolean isCarActivityNotMirror() {
		return isCarActivity();
	}

	@NonNull
	public MainActivityPrefs getPrefs() {
		return Prefs.instance;
	}

	@NonNull
	public PlaybackControlPrefs getPlaybackControlPrefs() {
		return getMediaServiceBinder().getMediaSessionCallback().getPlaybackControlPrefs();
	}

	public static void setTheme(Context ctx, boolean auto) {
		@StyleRes int theme = switch (Prefs.instance.getThemePref(auto)) {
			case MainActivityPrefs.THEME_LIGHT -> R.style.AppTheme_Light;
			case MainActivityPrefs.THEME_SYSTEM -> {
				if ((ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) ==
						Configuration.UI_MODE_NIGHT_YES)
					yield R.style.AppTheme_Dark;
				else yield R.style.AppTheme_Light;
			}
			case MainActivityPrefs.THEME_BLACK -> R.style.AppTheme_Black;
			case MainActivityPrefs.THEME_STAR_WARS -> R.style.AppTheme_BlackStarWars;
			case MainActivityPrefs.THEME_PURPLE -> R.style.AppTheme_Purple;
			case MainActivityPrefs.THEME_CLASSIC -> R.style.AppTheme_Classic;
			case MainActivityPrefs.THEME_LIQUID_GLASS -> R.style.AppTheme_LiquidGlass;
			default -> R.style.AppTheme_Dark;
		};
		ctx.setTheme(theme);
		if ((VERSION.SDK_INT >= VERSION_CODES.S) && (ctx instanceof Activity a)) {
			a.getSplashScreen().setSplashScreenTheme(theme);
		}
	}

		} catch (Throwable t) {
			Log.w("Failed to apply custom theme background", t);
		}
	}

	@Override
	public boolean interceptTouchEvent(MotionEvent e, Function<MotionEvent, Boolean> view) {
		if (AUTO && (e.getAction() == MotionEvent.ACTION_DOWN)) {
			FermataActivity a = getAppActivity();

			if (a.isInputActive()) {
				a.stopInput();
				return true;
			}
		}

		return super.interceptTouchEvent(e, view);
	}

	@Override
	public boolean isFullScreen() {
		if (videoMode || getPrefs().getFullscreenPref(this)) {
			if (isCarActivityNotMirror()) {
				FermataServiceUiBinder b = getMediaServiceBinder();
				return !b.getMediaSessionCallback().getPlaybackControlPrefs().getVideoAaShowStatusPref();
			} else {
				return true;
			}
		} else {
			return false;
		}
	}

	public boolean isGridView() {
		ActivityFragment f = getActiveFragment();

		if ((f instanceof MediaLibFragment) && ((MediaLibFragment) f).isGridSupported()) {
			return getPrefs().getGridViewPref(this);
		} else {
			return false;
		}
	}

	@NonNull
	public FermataServiceUiBinder getMediaServiceBinder() {
		return mediaServiceBinder;
	}

	public MediaSessionCallback getMediaSessionCallback() {
		return getMediaServiceBinder().getMediaSessionCallback();
	}

	@NonNull
	public MediaLib getLib() {
		return getMediaServiceBinder().getLib();
	}

	@Nullable
	public PlayableItem getCurrentPlayable() {
		return getMediaServiceBinder().getCurrentItem();
	}

	public ToolBarView getToolBar() {
		return toolBar;
	}

	@Override
	public float getToolBarSize() {
		return getPrefs().getToolBarSizePref(this);
	}

	public NavBarView getNavBar() {
		return navBar;
	}

	@Override
	public float getNavBarSize() {
		return getPrefs().getNavBarSizePref(this);
	}

	public BodyLayout getBody() {
		return body;
	}

	public NavBarMediator getNavBarMediator() {
		return navBarMediator;
	}


	public ControlPanelView getControlPanel() {
		return controlPanel;
	}

	public boolean isHomeFragmentActive() {
		ActivityFragment f = getActiveFragment();
		return (f != null) && (f.getFragmentId() == R.id.home_fragment);
	}

	public boolean isMediaPlayingForVoiceControl() {
		MediaSessionCallback cb = getMediaSessionCallback();
		return cb.isPlaying() ||
				getMediaServiceBinder().isPlaying() ||
				isActivePlaybackState(cb.getPlaybackState()) ||
				isSystemMediaAudioActive();
	}

	private boolean isActivePlaybackState(@Nullable PlaybackStateCompat state) {
		if (state == null) return false;
		int s = state.getState();
		return (s == PlaybackStateCompat.STATE_PLAYING) ||
				(s == PlaybackStateCompat.STATE_BUFFERING) ||
				(s == PlaybackStateCompat.STATE_CONNECTING);
	}

	private boolean isSystemMediaAudioActive() {
		AudioManager audio = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
		return (audio != null) && audio.isMusicActive();
	}

	public FloatingButton getFloatingButton() {
		return floatingButton;
	}

	@Override
	public float getTextIconSize() {
		return getPrefs().getTextIconSizePref(this);
	}

	public boolean isBarsHidden() {
		return barsHidden;
	}

	public void setBarsHidden(boolean barsHidden) {
		App.get().getHandler().post(() -> {
			this.barsHidden = barsHidden;
			int visibility = barsHidden ? GONE : VISIBLE;
			ToolBarView tb = getToolBar();
			if (tb.getMediator() != ToolBarView.Mediator.Invisible.instance) tb.setVisibility(visibility);
			getNavBar().setVisibility(GONE);
		});
	}

	public void setVideoMode(boolean videoMode, @Nullable VideoView v) {
		if (videoMode == this.videoMode) return;
		ControlPanelView cp = getControlPanel();

		if (videoMode) {
			this.videoMode = true;
			setSystemUiVisibility();
			keepScreenOn(true);
			cp.enableVideoMode(v);
		} else {
			this.videoMode = false;
			setSystemUiVisibility();
			keepScreenOn(false);
			if (cp != null) cp.disableVideoMode();
		}

		if (!checkMirroringMode(false)) {
			MainActivityPrefs p = getPrefs();

			if (p.getChangeBrightnessPref()) {
				if (videoMode) {
					brightness = getBrightness();
					setBrightness(p.getBrightnessPref());
				} else {
					setBrightness(brightness);
				}
			}
			if (p.getLandscapeVideoPref()) {
				if (videoMode) {
					getAppActivity().setRequestedOrientation(SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
				} else {
					getAppActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
				}
			}
		}

		fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
		updateSpeedDisplay();
	}

	public void rememberLastModule(int fragmentId) {
		FermataApplication.get().getDefaultSharedPreferences().edit()
				.putInt(PREF_LAST_MODULE, fragmentId).apply();
	}

	private boolean checkMirroringMode(boolean clearFlags) {
		if (!AUTO) return false;
		var screenOnFlags =
				FLAG_KEEP_SCREEN_ON | FLAG_TURN_SCREEN_ON | FLAG_DISMISS_KEYGUARD | FLAG_SHOW_WHEN_LOCKED;
		var app = FermataApplication.get();
		if (!app.isMirroringMode()) {
			if (clearFlags) getWindow().clearFlags(screenOnFlags);
			return false;
		}
		setFullScreen(true);
		getWindow().addFlags(screenOnFlags);
		getAppActivity().setRequestedOrientation(
				app.isMirroringLandscape() ? SCREEN_ORIENTATION_SENSOR_LANDSCAPE :
						SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		return true;
	}

	public void keepScreenOn(boolean on) {
		if (on) getWindow().addFlags(FLAG_KEEP_SCREEN_ON);
		else getWindow().clearFlags(FLAG_KEEP_SCREEN_ON);
	}

	public int getBrightness() {
		return Settings.System.getInt(getContext().getContentResolver(), SCREEN_BRIGHTNESS, 255);
	}

	public void setBrightness(int br) {
		try {
			Settings.System.putInt(getContext().getContentResolver(), SCREEN_BRIGHTNESS, br);
		} catch (SecurityException ex) {
			Log.e(ex, "Failed to change brightness");
		}
	}

	public boolean isVideoMode() {
		return videoMode;
	}

	public void setContentLoading(FutureSupplier<?> contentLoading) {
		if (this.contentLoading != null) {
			this.contentLoading.cancel();
			this.contentLoading = null;
		}

		progressBar.hide();
		if (contentLoading.isDone()) return;
		progressBar.show();

		var cl = this.contentLoading = contentLoading.main();
		cl.onCompletion((r, f) -> {
			if ((f != null) && !isCancellation(f)) Log.d(f);
			if (this.contentLoading == cl) {
				this.contentLoading = null;
				progressBar.hide();
			}
		});
	}

	public void backToNavFragment() {
		setActiveNavItemId(R.id.home_fragment);
		showFragment(R.id.home_fragment);
	}

	public void showMainMenu() {
		setActiveNavItemId(R.id.home_fragment);
		showFragment(R.id.home_fragment);
	}

	/**
	 * Back outside fragment-local history: leave video/radio, leave modules for the main menu.
	 * Exit (double-back) only happens from {@link #isHomeFragmentActive() home}.
	 */
	@Override
	protected boolean handleBackFallthrough(@Nullable ActivityFragment f) {
		BodyLayout b = getBody();
		if (b != null) {
			if (b.isVideoMode()) {
				// Browse-first modules and downloaded Nuvio videos return without split + slider.
				if (isBrowseFirstVideoUiActive(f)) {
					b.setMode(BodyLayout.Mode.FRAME);
				} else {
					b.setMode(BodyLayout.Mode.BOTH);
				}
				return true;
			}
			if (b.isRadioMode()) {
				b.setMode(BodyLayout.Mode.FRAME);
				return true;
			}
		}

		// Already on main menu — let ActivityDelegate do double-back-to-exit.
		if (isHomeFragmentActive()) return false;

		// Secondary screen (e.g. settings) opened from a module — return to that module.
		if (f != null) {
			int navId = getActiveNavItemId();
			if ((navId != ID_NULL) && (navId != R.id.home_fragment)
					&& (f.getFragmentId() != navId)) {
				showFragment(navId);
				return true;
			}
		}

		// Module root / any non-home screen → main menu (never exit from here).
		showMainMenu();
		return true;
	}

	public void openModule(int id) {
		openModule(id, null);
	}

	public void openModule(int id, @Nullable Object input) {
		final Object launchInput = input;
		String addonClass = findAddonClassName(id);
		if (addonClass != null && !AddonManager.get().hasAddon(id)) {
			AddonManager.get().getOrInstallAddon(addonClass).onSuccess(addon -> {
				if (launchDashcamOnPhone(id)) return;
				setActiveNavItemId(id);
				showFragmentLoaded(id, launchInput);
			}).onFailure(err -> {
				if (!isCancellation(err)) {
					Log.e(err, "Failed to open module ", id);
					showAlert(getContext(), err.getMessage());
				}
			});
			return;
		}
		if (launchDashcamOnPhone(id)) return;
		setActiveNavItemId(id);
		showFragmentLoaded(id, launchInput);
	}


	@Nullable
	public static String findAddonClassName(int fragmentId) {
		for (AddonInfo ai : BuildConfig.ADDONS) {
			FermataAddon addon = AddonManager.get().getAddon(ai.className);
			if (addon != null) {
				if (addon.getAddonId() == fragmentId) return ai.className;
				continue;
			}
			try {
				addon = (FermataAddon) Class.forName(ai.className).newInstance();
				if ((addon instanceof FermataFragmentAddon) && (addon.getAddonId() == fragmentId))
					return ai.className;
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	@Nullable
	private ActivityFragment showFragmentLoaded(int id, @Nullable Object input) {
		BodyLayout b = getBody();
		if (b.isVideoMode()) {
			b.setMode(BodyLayout.Mode.BOTH);
		}
		return super.showFragment(id, input);
	}

	@Override
	protected int getFrameContainerId() {
		return R.id.frame_layout;
	}

	@Nullable
	@Override
	public ActivityFragment showFragment(int id, Object input) {
		String addonClass = findAddonClassName(id);
		if (addonClass != null && !AddonManager.get().hasAddon(id)) {
			openModule(id, input);
			return null;
		}
		return showFragmentLoaded(id, input);
	}

	protected ActivityFragment createFragment(int id) {
		if (id == R.id.home_fragment) {
			return new HomeFragment();
		} else if (id == R.id.gallery_fragment) {
			return new GalleryFragment();
		} else if (id == R.id.video_gallery_fragment) {
			return new VideoGalleryFragment();
		} else if (id == R.id.image_viewer_fragment) {
			return new ImageViewerFragment();
		} else if (id == R.id.gallery_video_player_fragment) {
			return new GalleryVideoPlayerFragment();
		} else if (id == R.id.folders_fragment) {
			return new FoldersFragment();
		} else if (id == R.id.favorites_fragment) {
			return new FavoritesFragment();
		} else if (id == R.id.playlists_fragment) {
			return new PlaylistsFragment();
		} else if (id == R.id.settings_fragment) {
			return new SettingsFragment();
		} else if (id == R.id.audio_effects_fragment) {
			return new AudioEffectsFragment();
		} else if (id == R.id.subtitles_fragment) {
			return new SubtitlesFragment();
		}
		ActivityFragment f = FermataApplication.get().getAddonManager().createFragment(id);
		return (f != null) ? f : super.createFragment(id);
	}

	@Nullable
	public MediaLibFragment getActiveMediaLibFragment() {
		ActivityFragment f = getActiveFragment();
		return (f instanceof MediaLibFragment) ? (MediaLibFragment) f : null;
	}

	@Nullable
	public MainActivityFragment getActiveMainActivityFragment() {
		ActivityFragment f = getActiveFragment();
		return (f instanceof MainActivityFragment) ? (MainActivityFragment) f : null;
	}

	@Nullable
	public MediaLibFragment getMediaLibFragment(int id) {
		for (Fragment f : getSupportFragmentManager().getFragments()) {
			if (!(f instanceof MediaLibFragment m)) continue;
			if (m.getFragmentId() == id) return m;
		}

		return null;
	}

	public boolean hasCurrent() {
		PlayableItem pi = getMediaServiceBinder().getCurrentItem();
		return (pi != null) || (getLib().getPrefs().getLastPlayedItemPref() != null);
	}

	public FutureSupplier<Boolean> goToCurrent() {
		PlayableItem pi = getMediaServiceBinder().getCurrentItem();
		return ((pi == null) || (pi.isExternal())) ?
				getLib().getLastPlayedItem().main().map(this::goToItem) : completed(goToItem(pi));
	}

	public FutureSupplier<Item> goToItem(String id) {
		return getLib().getItem(id).main(getHandler()).map(i -> goToItem(i) ? i : null);
	}

	public boolean goToItem(Item i) {
		if (i == null) return false;
		BrowsableItem root = i.getRoot();

		if (root instanceof MediaLib.Folders) {
			showFragment(R.id.folders_fragment);
		} else if (root instanceof MediaLib.Favorites) {
			showFragment(R.id.favorites_fragment);
		} else if (root instanceof MediaLib.Playlists) {
			showFragment(R.id.playlists_fragment);
		} else if (root instanceof ExtRoot) {
			if ("youtube".equals(root.getId())) {
				showFragment(R.id.youtube_fragment);
				return true;
			}
		} else {
			MediaLibAddon a = AddonManager.get().getMediaLibAddon(root);
			if (a != null) {
				showFragment(a.getFragmentId());
			} else {
				Log.d("Unsupported item: ", i);
				return false;
			}
		}

		FermataApplication.get().getHandler().post(() -> {
			ActivityFragment f = getActiveFragment();
			if (!(f instanceof MediaLibFragment)) return;
			if (i instanceof PlayableItem) ((MediaLibFragment) f).revealItem(i);
			else if (i instanceof BrowsableItem) ((MediaLibFragment) f).openItem((BrowsableItem) i);
		});

		return true;
	}

	@Override
	protected boolean exitOnBackPressed() {
		// Exit only from the main menu (handleBackFallthrough sends everything else home first).
		return true;
	}

	@Override
	public OverlayMenu createMenu(View anchor) {
		return findViewById(R.id.context_menu);
	}

	public OverlayMenu getContextMenu() {
		return findViewById(R.id.context_menu);
	}

	public OverlayMenu getToolBarMenu() {
		return findViewById(R.id.tool_menu);
	}

	public void startVoiceAssistant() {
		ActivityFragment f = getActiveFragment();
		if (!(f instanceof MainActivityFragment) || !((MainActivityFragment) f).startVoiceAssistant())
			voiceSearch(getCurrentFocus());
	}

	private void voiceSearch(View focus) {
		startSpeechRecognizer().onSuccess(q -> {
			if (focus instanceof EditText) {
				((EditText) focus).setText(q.get(0));
				focus.requestFocus();
			} else if (getAppActivity().isInputActive()) {
				getAppActivity().setTextInput(q.get(0));
			} else {
				VoiceCommandHandler h = voiceCommandHandler;
				if (h == null) h = voiceCommandHandler = new VoiceCommandHandler(this);
				h.handle(q);
			}
		});
	}

	public FutureSupplier<List<String>> startSpeechRecognizer() {
		return startSpeechRecognizer(null, false);
	}

	public FutureSupplier<List<String>> startSpeechRecognizer(String locale, boolean textInput) {
		return startSpeechRecognizer(locale, textInput, true);
	}

	public FutureSupplier<List<String>> startSpeechRecognizer(String locale, boolean textInput, boolean showUi) {
		if (isMediaPlayingForVoiceControl())
			return failed(new OperationCanceledException("Voice control disabled while media is playing"));
		FutureSupplier<int[]> check =
				isCarActivityNotMirror() ? completed(new int[]{PERMISSION_GRANTED}) :
						getAppActivity().checkPermissions(Manifest.permission.RECORD_AUDIO);
		return check.then(r -> {
			if (r[0] == PERMISSION_GRANTED) return completedVoid();
			else return failed(new IllegalStateException("Audio recording permission is not granted"));
		}).onFailure(err -> {
			Log.e(err, "Failed to request RECORD_AUDIO permission");
			showAlert(getContext(), R.string.err_no_audio_record_perm);
		}).then(v -> {
			if (isMediaPlayingForVoiceControl())
				return failed(new OperationCanceledException("Voice control disabled while media is playing"));
			if (speechListener != null) speechListener.destroy();
			Promise<List<String>> p = new Promise<>();
			String lang = (locale == null) ? getPrefs().getVoiceControlLang(this) : locale;
			Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
			i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
			i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
			i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
			speechListener = new SpeechListener(p, textInput, showUi);
			speechListener.start(i);
			return p;
		});
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getPrevPlayable(Item i) {
		MediaLibFragment f = getActiveMediaLibFragment();
		if (f == null) return MediaSessionCallbackAssistant.super.getPrevPlayable(i);
		BrowsableItem p = f.getAdapter().getParent();
		return (p instanceof SearchFolder) ? ((SearchFolder) p).getPrevPlayable(i) :
				MediaSessionCallbackAssistant.super.getPrevPlayable(i);
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getNextPlayable(Item i) {
		MediaLibFragment f = getActiveMediaLibFragment();
		if (f == null) return MediaSessionCallbackAssistant.super.getNextPlayable(i);
		BrowsableItem p = f.getAdapter().getParent();
		return (p instanceof SearchFolder) ? ((SearchFolder) p).getNextPlayable(i) :
				MediaSessionCallbackAssistant.super.getNextPlayable(i);
	}

	@Override
	public EditText createEditText(Context ctx) {
		EditText t = getAppActivity().createEditText(ctx);
		if (isCarActivity() && getPrefs().getVoiceControlEnabledPref()) {
			t.setOnLongClickListener(v -> {
				startSpeechRecognizer().onSuccess(q -> t.setText(q.get(0)));
				return true;
			});
		}
		return t;
	}

	@Override
	public DialogBuilder createDialogBuilder(Context ctx) {
		return DialogBuilder.create(getContextMenu());
	}

	public void addPlaylistMenu(OverlayMenu.Builder builder,
															FutureSupplier<List<PlayableItem>> selection) {
		addPlaylistMenu(builder, () -> selection, () -> "");
	}

	public void addPlaylistMenu(OverlayMenu.Builder builder,
															Supplier<FutureSupplier<List<PlayableItem>>> selection,
															Supplier<? extends CharSequence> initName) {
		builder.addItem(R.id.playlist_add, R.drawable.playlist_add, R.string.playlist_add)
				.setSubmenu(b -> createPlaylistMenu(b, selection, initName));
	}

	private void createPlaylistMenu(OverlayMenu.Builder b,
																	Supplier<FutureSupplier<List<PlayableItem>>> selection,
																	Supplier<? extends CharSequence> initName) {
		getLib().getPlaylists().getUnsortedChildren().main().onSuccess(playlists -> {
			b.addItem(R.id.playlist_create, R.drawable.playlist_add, R.string.playlist_create)
					.setHandler(i -> createPlaylist(selection.get(), initName));

			for (int i = 0; i < playlists.size(); i++) {
				Playlist pl = (Playlist) playlists.get(i);
				String name = pl.getName();
				b.addItem(UiUtils.getArrayItemId(i), R.drawable.playlist, name)
						.setHandler(item -> addToPlaylist(name, selection.get()));
			}
		});
	}

	private boolean createPlaylist(FutureSupplier<List<PlayableItem>> selection,
																 Supplier<? extends CharSequence> initName) {
		UiUtils.queryText(getContext(), R.string.playlist_name, R.drawable.playlist, initName.get())
				.onSuccess(name -> {
					discardSelection();
					if (name == null) return;

					getLib().getPlaylists().addItem(name)
							.onFailure(err -> showAlert(getContext(), err.getMessage())).then(
									pl -> selection.main().then(items -> pl.addItems(items)
											.onFailure(err -> showAlert(getContext(), err.getMessage())).thenRun(() -> {
												MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
												if (f != null) f.getAdapter().reload();
											})));
				});
		return true;
	}

	private boolean addToPlaylist(String name, FutureSupplier<List<PlayableItem>> selection) {
		discardSelection();
		getLib().getPlaylists().getUnsortedChildren().main().onSuccess(playlists -> {
			for (Item i : getLib().getPlaylists().getUnsortedChildren().getOrThrow()) {
				Playlist pl = (Playlist) i;

				if (name.equals(pl.getName())) {
					selection.main().onSuccess(items -> {
						pl.addItems(items);
						MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
						if (f != null) f.getAdapter().reload();
					});
					break;
				}
			}
		});
		return true;
	}

	public void removeFromPlaylist(Playlist pl, List<PlayableItem> selection) {
		discardSelection();
		pl.removeItems(selection).onFailure(err -> showAlert(getContext(), err.getMessage()))
				.thenRun(() -> {
					MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
					if (f != null) f.getAdapter().reload();
				});
	}

	private void discardSelection() {
		ActivityFragment f = getActiveFragment();
		if (f instanceof MainActivityFragment) ((MainActivityFragment) f).discardSelection();
	}

	@Override
	protected int getExitMsg() {
		return R.string.press_back_again;
	}

	private void init() {
		FermataActivity a = getAppActivity();
		a.setContentView(getLayout());
		toolBar = a.findViewById(R.id.tool_bar);
		progressBar = a.findViewById(R.id.content_loading_progress);
		navBar = a.findViewById(R.id.nav_bar);
		navBar.setVisibility(GONE);
		body = a.findViewById(R.id.body_layout);
		controlPanel = a.findViewById(R.id.control_panel);
		floatingButton = a.findViewById(R.id.floating_button);
		floatingButton.setScale(getPrefs().getTextIconSizePref(this));
		controlPanel.bind(getMediaServiceBinder());
		ConstraintLayout parent = a.findViewById(R.id.main_activity);
		initSpeedDisplay(parent);
		initVideoMotionGuard(parent);

		if (VERSION.SDK_INT >= VERSION_CODES.VANILLA_ICE_CREAM && !a.isCarActivity()) {
			ViewCompat.setOnApplyWindowInsetsListener(toolBar, (v, insets) -> {
				var bars = insets.getInsets(
						WindowInsetsCompat.Type.systemBars()
				);
				a.findViewById(R.id.main_activity).setPadding(bars.left, bars.top, bars.right,
						bars.bottom);
				return WindowInsetsCompat.CONSUMED;
			});
		}
	}

	private void initSpeedDisplay(ConstraintLayout parent) {
		if (!AUTO || (speedDisplay != null)) return;

		var ctx = getContext();
		var text = new TextView(ctx);
		text.setTextColor(0xFFFFFFFF);
		text.setTextSize(18);
		text.setGravity(Gravity.CENTER);
		text.setBackgroundColor(0x99000000);
		text.setPadding(toIntPx(ctx, 10), toIntPx(ctx, 6), toIntPx(ctx, 10), toIntPx(ctx, 6));
		text.setVisibility(GONE);
		text.setElevation(toIntPx(ctx, 96));
		text.setTranslationZ(toIntPx(ctx, 96));

		var lp = new ConstraintLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		lp.topToTop = R.id.body_layout;
		lp.endToEnd = R.id.body_layout;
		lp.setMargins(0, toIntPx(ctx, 10), toIntPx(ctx, 10), 0);
		parent.addView(text, lp);
		speedDisplay = text;
		updateSpeedDisplay();
	}

	private void initVideoMotionGuard(ConstraintLayout parent) {
		if (!AUTO || (videoMotionGuard != null)) return;

		var ctx = getContext();
		var guard = new LinearLayoutCompat(ctx);
		guard.setOrientation(LinearLayoutCompat.VERTICAL);
		guard.setGravity(Gravity.CENTER);
		guard.setBackgroundColor(0xFF000000);
		guard.setClickable(false);
		guard.setFocusable(false);
		guard.setVisibility(GONE);
		guard.setElevation(toIntPx(ctx, 128));
		guard.setTranslationZ(toIntPx(ctx, 128));
		guard.setPadding(toIntPx(ctx, 24), toIntPx(ctx, 24), toIntPx(ctx, 24), toIntPx(ctx, 24));

		var message = new TextView(ctx);
		message.setText(R.string.video_disabled_while_driving);
		message.setTextColor(0xFFFFFFFF);
		message.setGravity(Gravity.CENTER);
		message.setTextSize(22);
		message.setAllCaps(false);

		guard.addView(message, new LinearLayoutCompat.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

		var lp = new ConstraintLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
		lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
		lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
		lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
		lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
		parent.addView(guard, lp);
		videoMotionGuard = guard;
		updateVideoMotionGuard();
	}

	private void updateVideoMotionGuard() {
		View guard = videoMotionGuard;
		if (guard == null) return;
		boolean show = driving && !getPrefs().getVideoInMotionPref();
		guard.setClickable(false);
		guard.setFocusable(false);
		guard.setVisibility(show ? VISIBLE : GONE);
		if (show) {
			guard.bringToFront();
			guard.invalidate();
		} else {
			guard.clearFocus();
		}
		updateSpeedDisplay();
	}

	private void updateSpeedDisplay() {
		TextView text = speedDisplay;
		if (text == null) return;
		boolean show = getPrefs().getSpeedDisplayPref() && isVideoMode() &&
				((videoMotionGuard == null) || (videoMotionGuard.getVisibility() != VISIBLE));
		boolean wasVisible = text.getVisibility() == VISIBLE;
		text.setVisibility(show ? VISIBLE : GONE);
		if (!show) {
			speedDisplayText = "";
			return;
		}

		if (Float.isNaN(carSpeed)) {
			updateSpeedDisplayText(text, "Speed: -- km/h");
		} else {
			updateSpeedDisplayText(text,
					String.format(Locale.US, "Speed: %.1f km/h", Math.abs(carSpeed) * 3.6f));
		}
		if (!wasVisible) text.bringToFront();
	}

	private void updateSpeedDisplayText(TextView text, String value) {
		if (value.equals(speedDisplayText)) return;
		speedDisplayText = value;
		text.setText(value);
	}

	@LayoutRes
	private int getLayout() {
		MainActivityPrefs prefs = getPrefs();
		return switch (prefs.getNavBarPosPref(this)) {
			case NavBarView.POSITION_LEFT -> R.layout.main_activity_left;
			case NavBarView.POSITION_RIGHT -> R.layout.main_activity_right;
			default -> R.layout.main_activity;
		};
	}

	private static String[] getRequiredPermissions() {
		List<String> perms = new ArrayList<>();
		perms.add(permission.READ_EXTERNAL_STORAGE);
		perms.add(permission.ACCESS_COARSE_LOCATION);
		perms.add(permission.ACCESS_FINE_LOCATION);
		if (VERSION.SDK_INT >= VERSION_CODES.P) {
			perms.add(permission.FOREGROUND_SERVICE);
		}
		if (VERSION.SDK_INT >= VERSION_CODES.Q) {
			perms.add(permission.ACCESS_MEDIA_LOCATION);
			perms.add(permission.USE_FULL_SCREEN_INTENT);
		}
		if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
			perms.add(permission.USE_FULL_SCREEN_INTENT);
			perms.add(permission.POST_NOTIFICATIONS);
			perms.add(permission.READ_MEDIA_IMAGES);
			perms.add(permission.READ_MEDIA_VIDEO);
		}
		if (VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) {
			perms.add(permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK);
		}
		return perms.toArray(new String[0]);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (MainActivityPrefs.hasThemePref(this, prefs)) {
			recreate();
		} else if (MainActivityPrefs.hasNavBarPosPref(this, prefs)) {
			recreate();
		} else if (MainActivityPrefs.hasTextIconSizePref(this, prefs)) {
			if (floatingButton != null) floatingButton.setScale(getPrefs().getTextIconSizePref(this));
		} else if (MainActivityPrefs.hasNavBarSizePref(this, prefs)) {
			if (navBar != null) navBar.setSize(getPrefs().getNavBarSizePref(this));
		} else if (MainActivityPrefs.hasToolBarSizePref(this, prefs)) {
			if (toolBar != null) toolBar.setSize(getPrefs().getToolBarSizePref(this));
		} else if (MainActivityPrefs.hasFullscreenPref(this, prefs)) {
			setSystemUiVisibility();
		} else if (prefs.contains(CHANGE_BRIGHTNESS)) {
			if (getPrefs().getChangeBrightnessPref()) {
				if (!Settings.System.canWrite(getContext())) {
					Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
					i.setData(Uri.parse("package:" + getContext().getPackageName()));
					startActivity(i);
				}
			}
		} else if (prefs.contains(BRIGHTNESS)) {
			if (isVideoMode()) setBrightness(getPrefs().getBrightnessPref());
		} else if (prefs.contains(MainActivityPrefs.VIDEO_IN_MOTION) ||
				prefs.contains(MainActivityPrefs.VIDEO_IN_MOTION_THRESHOLD)) {
			updateVideoMotionGuard();
		} else if (prefs.contains(MainActivityPrefs.SPEED_DISPLAY)) {
			updateSpeedDisplay();
		} else if (prefs.contains(VOICE_CONTROl_ENABLED)) {
			if (!getPrefs().getVoiceControlEnabledPref()) {
				getPrefs().applyBooleanPref(VOICE_CONTROl_FB, false);
				return;
			}
			getAppActivity().checkPermissions(permission.RECORD_AUDIO).onCompletion((r, err) -> {
				if ((err == null) && (r[0] == PERMISSION_GRANTED)) return;
				if (err != null) Log.e(err, "Failed to request RECORD_AUDIO permission");
				showAlert(getContext(), R.string.err_no_audio_record_perm);
				getPrefs().applyBooleanPref(VOICE_CONTROl_FB, false);
			});
		} else if (prefs.contains(VOICE_CONTROL_SUBST)) {
			if (voiceCommandHandler != null) voiceCommandHandler.updateWordSubst();
		} else if (prefs.contains(MainActivityPrefs.PASSENGER_REMOTE_ENABLED)) {
		} else if (prefs.contains(CLOCK_POS)) {
			getBody().getVideoView().setClockPos(getPrefs().getClockPosPref());
		} else if (prefs.contains(LOCALE)) {
			recreate();
		}
	}

	@Override
	public boolean onKeyDown(int code, KeyEvent event, IntObjectFunction<KeyEvent, Boolean> next) {
		return handleKeyEvent(this, event, next);
	}

	@Override
	public boolean onKeyUp(int code, KeyEvent event, IntObjectFunction<KeyEvent, Boolean> next) {
		return handleKeyEvent(this, event, next);
	}

	@Override
	public boolean onKeyLongPress(int code, KeyEvent event,
																IntObjectFunction<KeyEvent, Boolean> next) {
		return handleKeyEvent(this, event, next);
	}

	public HandlerExecutor getHandler() {
		return handler;
	}

	public Cancellable post(Runnable task) {
		return getHandler().submit(task);
	}

	public Cancellable postDelayed(Runnable task, long delay) {
		return getHandler().schedule(task, delay);
	}

	public Cancellable interruptPlayback() {
		MediaSessionCallback cb = getMediaSessionCallback();
		if (!cb.isPlaying()) return Cancellable.CANCELED;
		PlaybackStateCompat playbackState = cb.getPlaybackState();
		cb.onPause();
		return () -> {
			PlaybackStateCompat state = cb.getPlaybackState();
			if ((state.getState() == PlaybackStateCompat.STATE_PAUSED) &&
					((state == playbackState) || (state.getPosition() != playbackState.getPosition()))) {
				cb.onPlay();
			}
			return true;
		};
	}

	@Override
	protected FutureSupplier<Void> sendCrashReport(Throwable err) {
		return completedVoid();
	}

	static final class Prefs implements MainActivityPrefs {
		static final Prefs instance = new Prefs();
		private final List<ListenerRef<PreferenceStore.Listener>> listeners = new LinkedList<>();
		private final SharedPreferences prefs = FermataApplication.get().getDefaultSharedPreferences();

		private Prefs() {
			App.get().getHandler().post(this::migratePrefs);
		}

		private void migratePrefs() {
			// Rename old prefs
			var oldTheme = Pref.i("THEME", THEME_DARK);
			var oldScale = Pref.f("MEDIA_ITEM_SCALE", 1f);
			var fbLongPress = Pref.i("FB_LONG_PRESS", 0);
			var fbLongPressAA = Pref.i("FB_LONG_PRESS_AA", 0);
			var showClock = Pref.b("SHOW_CLOCK", false);
			var voiceCtrlM = Pref.b("VOICE_CONTROl_M", false);
			var theme = getIntPref(oldTheme);
			var scale = getFloatPref(oldScale);

			if ((theme != THEME_DARK) || (scale != 1f)) {
				try (PreferenceStore.Edit e = editPreferenceStore()) {
					if (theme != THEME_DARK) {
						e.setIntPref(THEME_MAIN, theme);
						if (AUTO) e.setIntPref(THEME_AA, theme);
						e.removePref(oldTheme);
					}
					if (scale != 1f) {
						e.setFloatPref(TEXT_ICON_SIZE, scale);
						if (AUTO) e.setFloatPref(TEXT_ICON_SIZE_AA, scale);
						e.removePref(oldScale);
					}
				}
			}

			if ((getIntPref(fbLongPress) == 1) || (getIntPref(fbLongPressAA) == 1)) {
				try (PreferenceStore.Edit e = editPreferenceStore()) {
					e.setBooleanPref(VOICE_CONTROl_ENABLED, true);
					e.setBooleanPref(VOICE_CONTROl_FB, true);
				}
			}

			if (getBooleanPref(showClock)) {
				try (PreferenceStore.Edit e = editPreferenceStore()) {
					e.removePref(showClock);
					e.setIntPref(CLOCK_POS, CLOCK_POS_RIGHT);
				}
			}

			if (getBooleanPref(voiceCtrlM)) {
				removePref(voiceCtrlM);
				var kp = Key.getPrefs();
				var o = Action.ACTIVATE_VOICE_CTRL.ordinal();
				kp.applyIntPref(Key.M.getLongActionPref(), o);
				kp.applyIntPref(Key.MENU.getLongActionPref(), o);
			}
		}

		@NonNull
		@Override
		public SharedPreferences getSharedPreferences() {
			return prefs;
		}

		@Override
		public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
			return listeners;
		}
	}

	private final class SpeechListener implements RecognitionListener {
		private final Promise<List<String>> promise;
		private final boolean textInput;
		private final boolean showUi;
		private final SpeechRecognizer recognizer;
		private final MaterialTextView text;
		private PlaybackStateCompat playbackState;

		private SpeechListener(Promise<List<String>> promise, boolean textInput, boolean showUi) {
			this.promise = promise;
			this.textInput = textInput;
			this.showUi = showUi;
			recognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
			recognizer.setRecognitionListener(this);
			text = new MaterialTextView(getContext());
		}

		void start(Intent i) {
			if (isMediaPlayingForVoiceControl()) {
				playbackState = null;
				promise.cancel();
				return;
			} else {
				playbackState = null;
			}
			recognizer.startListening(i);
		}

		void destroy() {
			MediaSessionCallback cb = getMediaSessionCallback();
			PlaybackStateCompat state = cb.getPlaybackState();
			if ((playbackState != null) && (state.getState() == PlaybackStateCompat.STATE_PAUSED)) {
				if ((state == playbackState) || (state.getPosition() != playbackState.getPosition())) {
					cb.onPlay();
				}
			}
			playbackState = null;
			recognizer.destroy();
			promise.cancel();
			if (speechListener == this) speechListener = null;
		}

		@Override
		public void onReadyForSpeech(Bundle params) {
			if (!showUi) return;
			getContextMenu().show(b -> {
				Context ctx = getContext();
				DisplayMetrics dm = getResources().getDisplayMetrics();
				int size = Math.min(dm.heightPixels, dm.widthPixels) / 3;
				LinearLayoutCompat layout = new LinearLayoutCompat(ctx);
				layout.setOrientation(LinearLayoutCompat.VERTICAL);
				AppCompatImageView img = new AppCompatImageView(ctx);
				TypedArray ta = ctx.getTheme()
						.obtainStyledAttributes(new int[]{com.google.android.material.R.attr.colorOnSecondary});
				int imgColor = ta.getColor(0, 0);
				ta.recycle();
				img.setMinimumWidth(size);
				img.setMinimumHeight(size);
				img.setImageResource(R.drawable.record_voice);
				img.setImageTintList(ColorStateList.valueOf(imgColor));
				text.setMaxLines(5);
				text.setGravity(Gravity.CENTER);
				text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
				ta = ctx.getTheme().obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
				text.setTextColor(ColorStateList.valueOf(ta.getColor(0, 0)));
				ta.recycle();
				text.setLayoutParams(new LinearLayoutCompat.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
				layout.setLayoutParams(new ConstraintLayout.LayoutParams(size, WRAP_CONTENT));
				b.setView(layout);
				b.setCloseHandlerHandler(m -> destroy());
				layout.addView(img);
				layout.addView(text);

				if (textInput) {
					int kbSize = size / 5;
					int margin = toIntPx(getContext(), 1);
					LinearLayoutCompat.LayoutParams lp = new LinearLayoutCompat.LayoutParams(kbSize, kbSize);
					AppCompatImageView kb = new AppCompatImageView(ctx);
					lp.gravity = Gravity.CENTER;
					lp.setMargins(0, margin, 0, margin);
					kb.setLayoutParams(lp);
					kb.setImageResource(R.drawable.keyboard);
					kb.setImageTintList(ColorStateList.valueOf(imgColor));
					layout.setOnClickListener(v -> {
						promise.completeExceptionally(new OperationCanceledException());
						hideActiveMenu();
					});
					layout.addView(kb);
				}
			});
		}

		@Override
		public void onResults(Bundle b) {
			List<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
			if ((r != null) && !r.isEmpty()) text.setText(r.get(0));
			if (showUi) postDelayed(MainActivityDelegate.this::hideActiveMenu, 1000);
			promise.complete(r);
		}

		@Override
		public void onPartialResults(Bundle b) {
			List<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
			if ((r != null) && !r.isEmpty()) text.setText(r.get(0));
		}

		@Override
		public void onError(int error) {
			String msg = "Speech recognition failed with error code " + error;
			Log.e(msg);
			promise.completeExceptionally(new IOException(msg));
			if (showUi) hideActiveMenu();

			if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
				showAlert(getContext(), R.string.err_no_audio_record_perm);
			}
		}

		@Override
		public void onBeginningOfSpeech() {
		}

		@Override
		public void onRmsChanged(float rmsdB) {
		}

		@Override
		public void onBufferReceived(byte[] buffer) {
		}

		@Override
		public void onEndOfSpeech() {
		}

		@Override
		public void onEvent(int eventType, Bundle params) {
		}
	}
}
