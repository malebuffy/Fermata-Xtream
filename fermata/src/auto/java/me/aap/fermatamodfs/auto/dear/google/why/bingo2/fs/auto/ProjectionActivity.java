package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.auto;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.content.ComponentName;
import android.content.Intent;
import android.database.ContentObserver;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.FermataApplication;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.ActivityBase;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.activity.AppActivity;

public class ProjectionActivity extends ActivityBase {
	private static Promise<Intent> promise;
	private static boolean starting;

	static FutureSupplier<Intent> start() {
		if (promise != null) promise.cancel();
		var p = promise = new Promise<>();
		p.thenRun(() -> {
			if (promise != p) return;
			promise = null;
			starting = false;
		});
		try {
			var app = FermataApplication.get();
			var intent = new Intent(app, ProjectionActivity.class);
			intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
			app.startActivity(intent);
		} catch (Exception err) {
			Log.e(err, "Failed to start ProjectionActivity");
			p.completeExceptionally(err);
		}
		return p;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (promise != null) promise.cancel();
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (starting) return;
		var p = promise;
		if (p == null) {
			finish();
			return;
		}
		starting = true;
		checkOverlayPermission().thenIgnoreResult(this::checkWriteSettingsPermission)
				.thenIgnoreResult(this::checkAccessibilityPermission)
				.thenIgnoreResult(this::requestScreenCapturePermission).onCompletion((i, err) -> {
					if (promise != p) return;
					starting = false;
					finish();
					if (err != null) {
						if (!isCancellation(err)) Log.i(err, "Screen capture request failed");
					} else if (i == null) {
						Log.i("Screen capture request rejected");
						p.cancel();
					} else {
						p.complete(i);
					}
				});
	}

	private FutureSupplier<?> checkOverlayPermission() {
		if (Settings.canDrawOverlays(this)) return completedVoid();
		Log.i("Requesting ACTION_MANAGE_OVERLAY_PERMISSION permission");
		Toast.makeText(this, R.string.enable_overlay_permission, Toast.LENGTH_LONG).show();
		return startActivityForResult(() -> new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
				Uri.parse("package:" + getPackageName())));
	}

	private FutureSupplier<?> checkWriteSettingsPermission() {
		if (Settings.System.canWrite(this)) return completedVoid();
		Log.i("Requesting ACTION_MANAGE_WRITE_SETTINGS permission");
		Toast.makeText(this, R.string.enable_write_settings_permission, Toast.LENGTH_LONG).show();
		return startActivityForResult(() -> new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
				Uri.parse("package:" + getPackageName())));
	}

	private FutureSupplier<?> checkAccessibilityPermission() {
		if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.O) || isAccessibilityEnabled())
			return completedVoid();
		Log.i("Requesting ACTION_ACCESSIBILITY_SETTINGS permission");
		Toast.makeText(this, R.string.enable_accessibility_permission, Toast.LENGTH_LONG).show();
		var p = new Promise<>();
		var intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
		intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
		startActivity(intent);
		getContentResolver().registerContentObserver(
				Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false,
				new ContentObserver(FermataApplication.get().getHandler()) {
					@Override
					public void onChange(boolean selfChange) {
						if (!isAccessibilityEnabled()) return;
						getContentResolver().unregisterContentObserver(this);
						p.complete(null);
					}
				});
		return p;
	}

	private boolean isAccessibilityEnabled() {
		var cn = new ComponentName(this, AccessibilityEventDispatcherService.class);
		var enabledServices =
				Settings.Secure.getString(getContentResolver(), ENABLED_ACCESSIBILITY_SERVICES);
		if (enabledServices == null) return false;
		for (var c : enabledServices.split(":")) {
			var n = ComponentName.unflattenFromString(c);
			if ((n != null) && (n.equals(cn))) return true;
		}
		return false;
	}

	private FutureSupplier<Intent> requestScreenCapturePermission() {
		Log.i("Requesting screen cast permission");
		Toast.makeText(this, R.string.select_screen_capture_mode, Toast.LENGTH_LONG).show();
		var mm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			return startActivityForResult(
					() -> mm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()));
		}
		return startActivityForResult(mm::createScreenCaptureIntent);
	}

	@Override
	protected FutureSupplier<? extends ActivityDelegate> createDelegate(AppActivity a) {
		return NO_DELEGATE;
	}
}
