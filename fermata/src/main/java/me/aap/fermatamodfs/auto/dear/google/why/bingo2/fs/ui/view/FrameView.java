package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.circularreveal.CircularRevealFrameLayout;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;

/**
 * @author Andrey Pavlenko
 */
public class FrameView extends CircularRevealFrameLayout {
	public FrameView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent e) {
		return MainActivityDelegate.get(getContext()).interceptTouchEvent(e, super::onTouchEvent);
	}
}
