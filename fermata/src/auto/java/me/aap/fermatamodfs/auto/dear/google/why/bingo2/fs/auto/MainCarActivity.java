package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.auto;

import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_DPAD_UP_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP_RIGHT;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.ui.UiUtils.showAlert;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.OperationCanceledException;
import android.os.SystemClock;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView.OnEditorActionListener;

import android.support.car.Car;
import android.support.car.CarConnectionCallback;
import android.support.car.hardware.CarSensorEvent;
import android.support.car.hardware.CarSensorManager;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarUiController;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.FermataApplication;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.FermataMediaServiceConnection;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.license.DeviceLicense;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.FermataActivity;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.MediaItemListView;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;

/**
 * @author Andrey Pavlenko
 */
public class MainCarActivity extends CarActivity implements FermataActivity {
	static FermataMediaServiceConnection service;
	@SuppressWarnings("unchecked")
	@NonNull
	private FutureSupplier<MainActivityDelegate> delegate =
			(FutureSupplier<MainActivityDelegate>) NO_DELEGATE;
	private CarEditText editText;
	private TextWatcher textWatcher;
	private Car speedCar;
	private CarSensorManager speedSensorManager;
	private Cancellable speedGuardCheck = Cancellable.CANCELED;
	private Cancellable locationSpeed = Cancellable.CANCELED;
	private float lastSpeed = Float.NaN;
	private float fallbackSpeed = Float.NaN;
	private Boolean guarded;
	private boolean carSpeedAvailable;
	private boolean speedGuardCheckActive;
	private final CarSensorManager.OnSensorChangedListener speedListener =
			(manager, event) -> {
				if (event.sensorType != CarSensorManager.SENSOR_TYPE_CAR_SPEED) return;
				carSpeedAvailable = true;
				lastSpeed = event.getCarSpeedData().carSpeed;
				updateSpeedDisplay();
				updateVideoMotionGuard();
			};
	private final CarConnectionCallback carConnectionCallback = new CarConnectionCallback() {
		@Override
		public void onConnected(Car car) {
			try {
				speedSensorManager = car.getCarManager(CarSensorManager.class);
				speedSensorManager.addListener(speedListener,
						CarSensorManager.SENSOR_TYPE_CAR_SPEED, CarSensorManager.SENSOR_RATE_UI);
				CarSensorEvent event =
						speedSensorManager.getLatestSensorEvent(CarSensorManager.SENSOR_TYPE_CAR_SPEED);
				if (event != null) speedListener.onSensorChanged(speedSensorManager, event);
			} catch (Throwable err) {
				Log.e(err, "Failed to register speed listener");
				setVideoMotionGuardUnavailable();
			}
		}

		@Override
		public void onDisconnected(Car car) {
			speedSensorManager = null;
			MainActivityDelegate d = delegate.peek();
			if (d != null) d.setVideoMotionGuardDriving(false);
		}
	};

	@NonNull
	@Override
	public FutureSupplier<MainActivityDelegate> getActivityDelegate() {
		return delegate;
	}

	@Override
	protected void attachBaseContext(Context base) {
		MainActivityDelegate.attachBaseContext(base);
		super.attachBaseContext(base);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		MainActivityDelegate.setTheme(this, true);
		super.onCreate(savedInstanceState);
		DeviceLicense.guardCar(this, () -> initializeLicensed(savedInstanceState));
	}

	private void initializeLicensed(Bundle savedInstanceState) {
		initCarActivity(this);
		FermataMediaServiceConnection s = service;

		if ((s != null) && s.isConnected()) {
			onCreate(savedInstanceState, s);
		} else {
			delegate = FermataMediaServiceConnection.connect(this).main()
					.onFailure(err -> showAlert(getContext(), String.valueOf(err))).map(c -> {
						service = c;
						return onCreate(savedInstanceState, c);
					});
		}
	}

	static void initCarActivity(CarActivity a) {
		a.setIgnoreConfigChanges(0xFFFFFFFF);
		CarUiController ctrl = a.getCarUiController();
		ctrl.getStatusBarController().hideAppHeader();
		ctrl.getMenuController().hideMenuButton();
	}

	private MainActivityDelegate onCreate(Bundle state, FermataMediaServiceConnection s) {
		MainActivityDelegate d = new MainActivityDelegate(this, s.createBinder());
		ActivityDelegate.setContextToDelegate(ctx -> d);
		delegate = completed(d);
		d.onActivityCreate(state);
		return d;
	}

	@Override
	public void onResume() {
		super.onResume();
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityResume);
		startSpeedListener();
	}

	@Override
	public void onPause() {
		stopSpeedListener();
		super.onPause();
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityPause);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onDestroy() {
		super.onDestroy();
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityDestroy)
				.thenRun(() -> ActivityDelegate.setContextToDelegate(null));
		delegate = (FutureSupplier<MainActivityDelegate>) NO_DELEGATE;
	}

	private void startSpeedListener() {
		if (speedCar != null) return;
		locationSpeed.cancel();
		locationSpeed = LocationSpeedProvider.start(this, speed -> {
			fallbackSpeed = speed;
			if (carSpeedAvailable) return;
			lastSpeed = speed;
			updateSpeedDisplay();
			updateVideoMotionGuard();
		});
		try {
			speedCar = Car.createCar(this, carConnectionCallback);
			speedCar.connect();
		} catch (Throwable err) {
			Log.e(err, "Failed to connect to car speed service");
			speedCar = null;
			setVideoMotionGuardUnavailable();
		}
		scheduleVideoMotionGuardCheck(1500);
	}

	private void setVideoMotionGuardUnavailable() {
		carSpeedAvailable = false;
		lastSpeed = fallbackSpeed;
		updateSpeedDisplay();
		updateVideoMotionGuard();
	}

	private void scheduleVideoMotionGuardCheck(long delay) {
		speedGuardCheckActive = true;
		speedGuardCheck.cancel();
		speedGuardCheck = FermataApplication.get().getHandler().schedule(() -> {
			try {
				if (speedSensorManager != null) {
					try {
						CarSensorEvent event =
								speedSensorManager.getLatestSensorEvent(CarSensorManager.SENSOR_TYPE_CAR_SPEED);
						if (event != null) speedListener.onSensorChanged(speedSensorManager, event);
						else updateVideoMotionGuard();
					} catch (Throwable err) {
						Log.e(err, "Failed to read latest speed");
						setVideoMotionGuardUnavailable();
					}
				} else {
					updateVideoMotionGuard();
				}
			} finally {
				if (speedGuardCheckActive) scheduleVideoMotionGuardCheck(1000);
			}
		}, delay);
	}

	private void updateVideoMotionGuard() {
		MainActivityDelegate d = delegate.peek();
		if (d == null) return;
		boolean guarded = Float.isNaN(lastSpeed) ?
				false :
				VideoMotionGuard.isGuarded(lastSpeed);
		if (this.guarded != null && this.guarded == guarded) return;
		this.guarded = guarded;
		d.setVideoMotionGuardDriving(guarded);
	}

	private void updateSpeedDisplay() {
		MainActivityDelegate d = delegate.peek();
		if (d != null) d.setCarSpeed(lastSpeed);
	}

	private void stopSpeedListener() {
		speedGuardCheckActive = false;
		speedGuardCheck.cancel();
		speedGuardCheck = Cancellable.CANCELED;
		locationSpeed.cancel();
		locationSpeed = Cancellable.CANCELED;
		carSpeedAvailable = false;
		fallbackSpeed = Float.NaN;
		lastSpeed = Float.NaN;
		guarded = null;
		updateSpeedDisplay();
		try {
			if (speedSensorManager != null)
				speedSensorManager.removeListener(speedListener, CarSensorManager.SENSOR_TYPE_CAR_SPEED);
		} catch (Throwable err) {
			Log.e(err);
		}
		speedSensorManager = null;
		if (speedCar != null) {
			speedCar.disconnect();
			speedCar = null;
		}
		MainActivityDelegate d = delegate.peek();
		if (d != null) d.setVideoMotionGuardDriving(false);
	}

	@Override
	public void onConfigurationChanged(Configuration configuration) {
		Log.i("Configuration changed: ", configuration);
		super.onConfigurationChanged(configuration);
	}

	@Override
	@SuppressWarnings("unchecked")
	public View findViewById(int i) {
		return super.findViewById(i);
	}

	@NonNull
	@Override
	public FragmentManager getSupportFragmentManager() {
		return super.getSupportFragmentManager();
	}

	@Override
	public View getCurrentFocus() {
		return null;
	}

	public boolean isCarActivity() {
		return true;
	}

	@Override
	public void setRequestedOrientation(int requestedOrientation) {
	}

	public void recreate() {
	}

	public void finish() {
		MainActivityDelegate d = delegate.peek();
		if (d == null) {
			getContext().stopService(new Intent(getContext(), CarService.class));
			getWindow().getDecorView().setVisibility(View.INVISIBLE);
		}
		else d.onActivityFinish();
	}

	@Override
	public FutureSupplier<Intent> startActivityForResult(Supplier<Intent> intent) {
		return failed(new UnsupportedOperationException());
	}

	public FutureSupplier<int[]> checkPermissions(String... perms) {
		int[] result = new int[perms.length];
		for (int i = 0; i < perms.length; i++) {
			result[i] = ContextCompat.checkSelfPermission(this, perms[i]) == PERMISSION_GRANTED ?
					PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
		}
		return completed(result);
	}

	@Override
	public Window getWindow() {
		return c();
	}

	@Override
	public EditText startInput(TextWatcher w) {
		if (editText == null) editText = new CarEditText(this);
		if (textWatcher != null) editText.removeTextChangedListener(textWatcher);
		editText.addTextChangedListener(w);
		textWatcher = w;
		getActivityDelegate().onSuccess(a -> {
			if (a.getPrefs().getVoiceControlEnabledPref()) {
				a.startSpeechRecognizer(null, true).onCompletion((q, err) -> {
					stopInput();
					if (err instanceof OperationCanceledException) {
						textWatcher = w;
						editText.removeTextChangedListener(w);
						editText.addTextChangedListener(w);
						if (w instanceof OnEditorActionListener)
							editText.setOnEditorActionListener((OnEditorActionListener) w);
						a().startInput(editText);
					} else if ((q != null) && !q.isEmpty()) {
						editText.setText(q.get(0));
						w.afterTextChanged(editText.getText());
					} else {
						stopInput();
					}
				});
			} else {
				a().startInput(editText);
			}
		});
		return editText;
	}

	public void stopInput() {
		if (editText != null) {
			if (textWatcher != null) editText.removeTextChangedListener(textWatcher);
			editText.setOnEditorActionListener(null);
		}

		a().stopInput();
	}

	public boolean isInputActive() {
		return a().isInputActive();
	}

	public EditText createEditText(Context ctx) {
		CarEditText et = new CarEditText(ctx);
		et.setOnClickListener(v -> {
			if (!a().isInputActive()) a().startInput(et);
		});
		return et;
	}

	@Override
	public boolean setTextInput(String text) {
		if ((editText == null) || !isInputActive()) return false;
		editText.setText(text);
		stopInput();
		return true;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
		Log.i(keyEvent);
		MainActivityDelegate d = delegate.peek();
		if (d == null) return super.onKeyUp(keyCode, keyEvent);

		if (d.getPrefs().useDpadCursor(d)) {
			switch (keyCode) {
				case KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_LEFT,
						KEYCODE_DPAD_UP_RIGHT, KEYCODE_DPAD_DOWN_LEFT, KEYCODE_DPAD_DOWN_RIGHT -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if (c != null) c.delayedHide();
					return true;
				}
				case KEYCODE_BACK -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if ((c != null) && c.ignoreBack) {
						c.ignoreBack = false;
						return true;
					}
				}
				case KEYCODE_DPAD_CENTER, KEYCODE_ENTER, KEYCODE_NUMPAD_ENTER -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if ((c != null) && c.isFocused()) return c.click();
				}
			}
		}

		return d.onKeyUp(keyCode, keyEvent, super::onKeyDown);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
		Log.i(keyEvent);
		MainActivityDelegate d = delegate.peek();
		if (d == null) return super.onKeyDown(keyCode, keyEvent);
		if (!d.getPrefs().useDpadCursor(d)) return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);

		float x = 0;
		float y = 0;
		View screen = null;
		Cursor cursor = null;

		switch (keyCode) {
			case KEYCODE_DPAD_UP -> {
				OverlayMenu m = d.getActiveMenu();
				if (m instanceof View v) {
					View f = v.focusSearch(View.FOCUS_UP);
					if (f != null) {
						f.requestFocus();
						return true;
					}
				}
				y = -1;
			}
			case KEYCODE_DPAD_DOWN -> {
				OverlayMenu m = d.getActiveMenu();
				if (m instanceof View v) {
					View f = v.focusSearch(View.FOCUS_DOWN);
					if (f != null) {
						f.requestFocus();
						return true;
					}
				}
				y = 1;
			}
			case KEYCODE_DPAD_LEFT -> x = -1;
			case KEYCODE_DPAD_RIGHT -> x = 1;
			case KEYCODE_DPAD_UP_LEFT -> {
				y = -1;
				x = -1;
			}
			case KEYCODE_DPAD_UP_RIGHT -> {
				y = -1;
				x = 1;
			}
			case KEYCODE_DPAD_DOWN_LEFT -> {
				y = 1;
				x = -1;
			}
			case KEYCODE_DPAD_DOWN_RIGHT -> {
				y = 1;
				x = 1;
			}
			case KEYCODE_BACK -> {
				screen = findViewById(R.id.main_activity);
				cursor = screen.findViewById(R.id.cursor);
				if ((cursor == null) || cursor.isFocused())
					return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);
				cursor.ignoreBack = true;
			}
			case KEYCODE_DPAD_CENTER, KEYCODE_ENTER, KEYCODE_NUMPAD_ENTER -> {
				screen = findViewById(R.id.main_activity);
				cursor = screen.findViewById(R.id.cursor);
				if (cursor == null) return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);
			}
			default -> {return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);}
		}

		if (screen == null) {
			screen = findViewById(R.id.main_activity);
			cursor = screen.findViewById(R.id.cursor);
		}

		int w = screen.getWidth();
		int h = screen.getHeight();

		if (cursor == null) {
			cursor = new Cursor(d, (int) (Math.min(w, h) * 0.05f));
			((ViewGroup) screen).addView(cursor);
			cursor.show(w / 2f, h / 2f);
		} else if (!cursor.isVisible()) {
			cursor.show(cursor.getX(), cursor.getY());
		} else {
			int cursorSize = (int) (Math.min(w, h) * 0.05f);
			cursor.move(w, h, x, y, cursorSize / 3f, keyCode);
		}

		return true;
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent keyEvent) {
		MainActivityDelegate d = delegate.peek();
		return (d != null) ? d.onKeyLongPress(keyCode, keyEvent, super::onKeyLongPress) :
				super.onKeyLongPress(keyCode, keyEvent);
	}

	private static final class Cursor extends AppCompatImageView
			implements View.OnClickListener, View.OnLongClickListener {
		private final MainActivityDelegate activity;
		private Cancellable move = Cancellable.CANCELED;
		private Cancellable hide = Cancellable.CANCELED;
		private Cancellable resetAccel = Cancellable.CANCELED;
		boolean ignoreBack;
		int accel = 1;

		Cursor(MainActivityDelegate d, int size) {
			super(d.getContext());
			activity = d;
			setId(R.id.cursor);
			setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
			setLayoutParams(new ConstraintLayout.LayoutParams(size, size));
			setImageDrawable(AppCompatResources.getDrawable(d.getContext(), R.drawable.cursor));
			setOnClickListener(this);
			setOnLongClickListener(this);
			int z = (int) (d.getContext().getResources().getDisplayMetrics().density * 120);
			setElevation(z);
			setTranslationZ(z);

			Drawable transparent = new ColorDrawable(Color.TRANSPARENT);
			StateListDrawable bg = new StateListDrawable();
			bg.addState(new int[]{android.R.attr.state_focused}, transparent);
			bg.addState(new int[]{}, transparent);
			setBackground(bg);
		}

		boolean isVisible() {
			return getVisibility() == VISIBLE;
		}

		@Override
		public void onClick(View v) {
			click();
		}

		boolean click() {
			delayedHide();
			float x = getX();
			float y = getY();
			OverlayMenu m = activity.getActiveMenu();
			if ((m instanceof ViewGroup v) && click(v, x - v.getX(), y - v.getY())) return true;
			click(screen(), x, y);
			return true;
		}

		private boolean click(ViewGroup parent, float cursorX, float cursorY) {
			for (int i = 0, n = parent.getChildCount(); i < n; i++) {
				View v = parent.getChildAt(i);
				if (v.getVisibility() != VISIBLE) continue;
				float x = cursorX - v.getX();
				if ((x > 0) && (x < v.getWidth())) {
					float y = cursorY - v.getY();
					if ((y >= 0) && (y < v.getHeight())) {
						if (v instanceof WebView) {
							touch(v, x, y, true);
							return true;
						}
						if (v instanceof VideoView) {
							touch(v, x, y, false);
							return true;
						}
						if (v.isClickable()) {
							v.performClick();
							v.requestFocus();
							activity.post(this::requestFocus);
							return true;
						}

						if ((v instanceof ViewGroup vg) && click(vg, x, y)) return true;
						touch(v, x, y, false);
						return true;
					}
				}
			}
			return false;
		}

		private void touch(View v, float x, float y, boolean clearFocus) {
			long time = SystemClock.uptimeMillis();
			MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
			if (clearFocus) {
				clearFocus();
				setVisibility(GONE);
			}
			v.dispatchTouchEvent(down);
			down.recycle();
			activity.postDelayed(() -> {
				MotionEvent up = MotionEvent.obtain(time, time + 100, MotionEvent.ACTION_UP, x, y, 0);
				v.dispatchTouchEvent(up);
				up.recycle();
				if (!clearFocus) activity.post(this::requestFocus);
			}, 100);
		}

		@Override
		public boolean onLongClick(View v) {
			delayedHide();
			longClick(screen(), getX(), getY());
			return true;
		}

		private void longClick(ViewGroup parent, float cursorX, float cursorY) {
			for (int i = 0, n = parent.getChildCount(); i < n; i++) {
				View v = parent.getChildAt(i);
				if (v.getVisibility() != VISIBLE) continue;
				float x = cursorX - v.getX();
				if ((x > 0) && (x < v.getWidth())) {
					float y = cursorY - v.getY();
					if ((y >= 0) && (y < v.getHeight())) {
						if (v.isLongClickable()) {
							v.performLongClick();
							activity.post(this::requestFocus);
							return;
						}
						if (v instanceof ViewGroup vg) longClick(vg, x, y);
						return;
					}
				}
			}
		}

		void delayedHide() {
			move.cancel();
			move = Cancellable.CANCELED;
			hide.cancel();
			hide = activity.postDelayed(() -> {
				hide = Cancellable.CANCELED;
				clearFocus();
				setVisibility(GONE);
				MediaItemListView.focusActive(activity.getContext(), null);
			}, 5000);
		}

		void show(float cursorX, float cursorY) {
			setVisibility(VISIBLE);
			bringToFront();
			requestFocus();
			animate().x(cursorX).y(cursorY).setDuration(0).start();
		}

		void move(int w, int h, float dx, float dy, float step, int keyCode) {
			move.cancel();
			move = activity.postDelayed(() -> move(w, h, dx, dy, step, keyCode), 50);

			float cursorX = getX() + (step * dx * accel);
			float cursorY = getY() + (step * dy * accel);
			cursorX = Math.max(0, Math.min(w - step, cursorX));
			cursorY = Math.max(0, Math.min(h - step, cursorY));

			if (resetAccel.cancel()) accel += 1;
			resetAccel = activity.postDelayed(() -> {
				resetAccel = Cancellable.CANCELED;
				accel = 1;
			}, 200);

			if ((keyCode == KEYCODE_DPAD_UP) && (getY() == 0)) {
				scroll(true);
			} else if ((keyCode == KEYCODE_DPAD_DOWN) && (getY() >= screen().getHeight() - getHeight())) {
				scroll(false);
			}

			if (!focusFb(screen(), cursorX, cursorY)) requestFocus();
			bringToFront();
			animate().x(cursorX).y(cursorY).setDuration(0).start();
		}

		private boolean focusFb(ViewGroup screen, float cursorX, float cursorY) {
			View fb = screen.findViewById(R.id.floating_button);
			float fbX = fb.getX();
			float fbY = fb.getY();
			if ((cursorX >= fbX) && (cursorX < fbX + fb.getWidth()) && (cursorY >= fbY) &&
					(cursorY < fbY + fb.getHeight())) {
				fb.requestFocus();
				return true;
			}
			return false;
		}

		private void scroll(boolean up) {
			ActivityFragment f = activity.getActiveFragment();
			if (f == null) return;
			View root = f.getView();
			if (root instanceof ViewGroup vg) scroll(up, vg);
		}

		private boolean scroll(boolean up, View v) {
			if (v instanceof RecyclerView rv) {
				RecyclerView.LayoutManager lm = rv.getLayoutManager();
				if (lm == null) return false;
				int pos;
				if (lm instanceof LinearLayoutManager llm) pos = llm.findFirstVisibleItemPosition();
				else if (lm instanceof GridLayoutManager glm) pos = glm.findFirstVisibleItemPosition();
				else return false;
				if (up) {
					if (pos > 0) scrollToPosition(lm, pos - 1);
				} else {
					if (pos < lm.getItemCount() - 1) scrollToPosition(lm, pos + 1);
				}
				return true;
			} else if (v instanceof WebView wv) {
				if (up) wv.pageUp(false);
				else wv.pageDown(false);
				return true;
			} else if (v instanceof ViewGroup vg) {
				for (int i = 0, n = vg.getChildCount(); i < n; i++) {
					if (scroll(up, vg.getChildAt(i))) return true;
				}
			}
			return false;
		}

		private static void scrollToPosition(RecyclerView.LayoutManager lm, int pos) {
			if (lm instanceof LinearLayoutManager llm) llm.scrollToPositionWithOffset(pos, 0);
			else if (lm instanceof GridLayoutManager glm) glm.scrollToPositionWithOffset(pos, 0);
		}

		private ViewGroup screen() {
			return (ViewGroup) getParent();
		}
	}
}
