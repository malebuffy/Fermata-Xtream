package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment;

import static android.view.View.VISIBLE;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.LEFT;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.widget.TextView;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ForcedVisibilityButton;
import me.aap.utils.ui.view.ToolBarView;

/** Gallery toolbar: home (left), back, and title always visible. */
final class GalleryToolBarMediator implements ToolBarView.Mediator.BackTitle {
	static final GalleryToolBarMediator instance = new GalleryToolBarMediator();

	private GalleryToolBarMediator() {
	}

	@Override
	public void enable(ToolBarView tb, ActivityFragment f) {
		tb.removeAllViews();
		tb.setVisibility(VISIBLE);
		// LEFT prepends: add title, then back, then home -> [home][back][title]
		TextView title = createTitleText(tb);
		addView(tb, title, getTitleId(), LEFT);
		if (backOnTitleClick()) title.setOnClickListener(this);

		ForcedVisibilityButton back = createBackButton(tb);
		back.forceVisibility(true);
		addView(tb, back, getBackButtonId(), LEFT);

		addButton(tb, R.drawable.home_menu,
				v -> MainActivityDelegate.get(v.getContext()).showMainMenu(), R.id.tool_home, LEFT);
		refresh(tb, f);
	}

	@Override
	public void disable(ToolBarView tb) {
		tb.removeAllViews();
	}

	@Override
	public void onActivityEvent(ToolBarView tb, me.aap.utils.ui.activity.ActivityDelegate a, long e) {
		switch ((int) e) {
			case FRAGMENT_CONTENT_CHANGED, FRAGMENT_CHANGED -> {
				ActivityFragment f = tb.getActiveFragment();
				if (f != null) refresh(tb, f);
			}
			default -> BackTitle.super.onActivityEvent(tb, a, e);
		}
	}

	void refresh(ToolBarView tb, ActivityFragment f) {
		TextView title = tb.findViewById(getTitleId());
		if (title != null) title.setText(f.getTitle());
		var back = tb.findViewById(getBackButtonId());
		if (back instanceof ForcedVisibilityButton b) b.forceVisibility(true);
		else if (back != null) back.setVisibility(VISIBLE);
		var home = tb.findViewById(R.id.tool_home);
		if (home != null) home.setVisibility(VISIBLE);
	}

	@Override
	public int getBackButtonVisibility(ActivityFragment f) {
		return VISIBLE;
	}
}
