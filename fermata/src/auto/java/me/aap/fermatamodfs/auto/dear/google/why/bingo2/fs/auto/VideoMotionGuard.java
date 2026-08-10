package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.auto;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.CarContext;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.hardware.CarHardwareManager;
import androidx.car.app.hardware.common.OnCarDataAvailableListener;
import androidx.car.app.hardware.info.CarInfo;
import androidx.car.app.hardware.info.Speed;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.FermataApplication;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityPrefs;
import me.aap.utils.function.Cancellable;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

import java.util.List;
import java.util.Locale;

final class VideoMotionGuard
		implements OnCarDataAvailableListener<Speed>, PreferenceStore.Listener, Cancellable {
	interface Listener {
		void onVideoMotionGuardChanged(boolean guarded);

		default void onVideoMotionSpeedChanged(float metersPerSecond) {
		}
	}

	private final CarInfo carInfo;
	private final Listener listener;
	private Cancellable guardCheck = Cancellable.CANCELED;
	private Cancellable locationSpeed = Cancellable.CANCELED;
	private float lastSpeed = Float.NaN;
	private float fallbackSpeed = Float.NaN;
	private Boolean guarded;
	private boolean carSpeedAvailable;
	private boolean active;

	private VideoMotionGuard(@Nullable CarInfo carInfo, Listener listener) {
		this.carInfo = carInfo;
		this.listener = listener;
		MainActivityPrefs.get().addBroadcastListener(this);
	}

	static Cancellable addSpeedListener(@NonNull CarContext ctx, Listener listener) {
		VideoMotionGuard guard = null;
		try {
			CarInfo info = ctx.getCarService(CarHardwareManager.class).getCarInfo();
			guard = new VideoMotionGuard(info, listener);
			info.addSpeedListener(FermataApplication.get().getHandler(), guard);
		} catch (Throwable err) {
			Log.e(err, "Failed to add car speed listener");
			if (guard == null) guard = new VideoMotionGuard(null, listener);
		}
		guard.locationSpeed = LocationSpeedProvider.start(ctx, guard::onLocationSpeedAvailable);
		guard.scheduleGuardCheck(1500);
		return guard;
	}

	static boolean isGuarded(float metersPerSecond) {
		MainActivityPrefs prefs = MainActivityPrefs.get();
		if (prefs.getVideoInMotionPref()) return false;
		float threshold = prefs.getVideoInMotionThresholdPref() / 3.6f;
		if (threshold <= 0f) threshold = 1f / 3.6f;
		return Math.abs(metersPerSecond) > threshold;
	}

	@Override
	public void onCarDataAvailable(@NonNull Speed speed) {
		Float value = speed.getDisplaySpeedMetersPerSecond().getValue();
		if (value == null) value = speed.getRawSpeedMetersPerSecond().getValue();
		if (value == null) return;
		carSpeedAvailable = true;
		lastSpeed = value;
		listener.onVideoMotionSpeedChanged(lastSpeed);
		updateGuard();
	}

	private void onLocationSpeedAvailable(float metersPerSecond) {
		fallbackSpeed = metersPerSecond;
		if (carSpeedAvailable) return;
		lastSpeed = metersPerSecond;
		listener.onVideoMotionSpeedChanged(lastSpeed);
		updateGuard();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (!prefs.contains(MainActivityPrefs.VIDEO_IN_MOTION) &&
				!prefs.contains(MainActivityPrefs.VIDEO_IN_MOTION_THRESHOLD) &&
				!prefs.contains(MainActivityPrefs.SPEED_DISPLAY)) {
			return;
		}
		if (prefs.contains(MainActivityPrefs.SPEED_DISPLAY))
			listener.onVideoMotionSpeedChanged(lastSpeed);
		if (prefs.contains(MainActivityPrefs.VIDEO_IN_MOTION) ||
				prefs.contains(MainActivityPrefs.VIDEO_IN_MOTION_THRESHOLD)) updateGuard();
	}

	@Override
	public boolean cancel() {
		MainActivityPrefs.get().removeBroadcastListener(this);
		active = false;
		guardCheck.cancel();
		locationSpeed.cancel();
		if (carInfo != null) carInfo.removeSpeedListener(this);
		return true;
	}

	private void scheduleGuardCheck(long delay) {
		active = true;
		guardCheck.cancel();
		guardCheck = FermataApplication.get().getHandler().schedule(() -> {
			updateGuard();
			if (active) scheduleGuardCheck(1000);
		}, delay);
	}

	private void updateGuard() {
		boolean guarded = Float.isNaN(lastSpeed) ?
				false : isGuarded(lastSpeed);
		if (this.guarded != null && this.guarded == guarded) return;
		this.guarded = guarded;
		listener.onVideoMotionGuardChanged(guarded);
	}

	static String formatSpeed(float metersPerSecond) {
		if (Float.isNaN(metersPerSecond)) return "Speed: -- km/h";
		return String.format(Locale.US, "Speed: %.1f km/h", Math.abs(metersPerSecond) * 3.6f);
	}

	static void drawGuard(Context ctx, SurfaceContainer sc) {
		if ((sc == null) || (sc.getSurface() == null)) return;
		Surface surface = sc.getSurface();
		Canvas canvas = null;

		try {
			canvas = surface.lockCanvas(null);
			int w = Math.max(canvas.getWidth(), sc.getWidth());
			int h = Math.max(canvas.getHeight(), sc.getHeight());
			canvas.drawColor(Color.BLACK);

			Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
			text.setColor(Color.WHITE);
			text.setTextAlign(Paint.Align.CENTER);
			text.setTextSize(Math.max(28, Math.min(w, h) / 16f));
			text.setFakeBoldText(true);

			String[] lines = ctx.getString(R.string.video_disabled_while_driving).split("\\n");
			float lineHeight = text.getTextSize() * 1.35f;
			float y = (h - (lineHeight * (lines.length - 1))) / 2f;
			for (String line : lines) {
				canvas.drawText(line, w / 2f, y, text);
				y += lineHeight;
			}
		} catch (Throwable err) {
			Log.e(err, "Failed to draw video motion guard");
		} finally {
			if (canvas != null) surface.unlockCanvasAndPost(canvas);
		}
	}
}
