package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;

/**
 * @author Andrey Pavlenko
 */
public class SplitterView extends androidx.appcompat.widget.AppCompatImageView {

	public SplitterView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public SplitterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				return true;
			case MotionEvent.ACTION_MOVE:
				ConstraintLayout p = (ConstraintLayout) getParent();
				Guideline gl = p.findViewById(R.id.guideline);
				ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) gl.getLayoutParams();
				params.guidePercent = (event.getRawY()) / p.getRootView().getHeight();
				gl.setLayoutParams(params);
				return true;
			default:
				return false;
		}
	}
}
