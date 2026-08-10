package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.constraintlayout.widget.Guideline;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.pref.PreferenceStore.Pref;

/**
 * @author Andrey Pavlenko
 */
public abstract class SplitLayout extends ConstraintLayout implements View.OnTouchListener {
	private final int[] splitTouchLocation = new int[2];
	private float splitTouchStart;
	private float splitStartPercent;
	private boolean splitDragging;

	protected SplitLayout(@NonNull Context ctx, @Nullable AttributeSet attrs) {
		super(ctx, attrs);
		var portrait = isPortrait();
		inflate(ctx, getLayout(portrait), this);
		getSplitLine().setOnTouchListener(this);
		getSplitHandle().setOnTouchListener(this);
		MainActivityDelegate.getActivityDelegate(ctx)
				.onSuccess(a -> a.getPrefs().getFloatPref(getSplitPercentPref(portrait)));
	}

	@LayoutRes
	protected abstract int getLayout(boolean portrait);

	protected abstract Pref<DoubleSupplier> getSplitPercentPref(boolean portrait);

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		var ctx = getContext();
		var cs = new ConstraintSet();
		var layout = new ConstraintLayout(ctx);
		var portrait = isPortrait();
		var img = portrait ? R.drawable.horizontal_split : R.drawable.vertical_split;
		inflate(ctx, getLayout(portrait), layout);
		cs.clone(layout);
		cs.applyTo(this);
		getSplitHandle().setImageResource(img);
		setSplitPercent(getActivity().getPrefs().getFloatPref(getSplitPercentPref(portrait)));
	}

	protected Guideline getGuideline() {
		return findViewById(R.id.guideline);
	}

	protected View getSplitLine() {
		return findViewById(R.id.split_line);
	}

	protected AppCompatImageView getSplitHandle() {
		return findViewById(R.id.split_handle);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN -> {
				var horizontal = isHorizontalSplit();
				splitTouchStart = getSplitTouchPosition(event, horizontal);
				splitStartPercent = getSplitPercent();
				splitDragging = false;
				return true;
			}
			case MotionEvent.ACTION_MOVE -> {
				var horizontal = isHorizontalSplit();
				var size = horizontal ? getHeight() : getWidth();
				if (size <= 0) return true;

				var delta = getSplitTouchPosition(event, horizontal) - splitTouchStart;
				if (!splitDragging &&
						(Math.abs(delta) < ViewConfiguration.get(getContext()).getScaledTouchSlop())) {
					return true;
				}

				splitDragging = true;
				var percent = clampSplitPercent(splitStartPercent + delta / size);
				setSplitPercent(percent);
				getActivity().getPrefs().applyFloatPref(getSplitPercentPref(isPortrait()), percent);
				return true;
			}
			case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				splitDragging = false;
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	protected boolean isPortrait() {
		return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
	}

	protected MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	private void setSplitPercent(float percent) {
		var gl = getGuideline();
		var params = (LayoutParams) gl.getLayoutParams();
		params.guidePercent = clampSplitPercent(percent);
		gl.setLayoutParams(params);
	}

	private float getSplitPercent() {
		return ((LayoutParams) getGuideline().getLayoutParams()).guidePercent;
	}

	private boolean isHorizontalSplit() {
		return ((LayoutParams) getGuideline().getLayoutParams()).orientation == LayoutParams.HORIZONTAL;
	}

	private float getSplitTouchPosition(MotionEvent event, boolean horizontal) {
		getLocationOnScreen(splitTouchLocation);
		return horizontal ? event.getRawY() - splitTouchLocation[1] :
				event.getRawX() - splitTouchLocation[0];
	}

	private static float clampSplitPercent(float percent) {
		return Math.max(0.1f, Math.min(0.9f, percent));
	}
}
