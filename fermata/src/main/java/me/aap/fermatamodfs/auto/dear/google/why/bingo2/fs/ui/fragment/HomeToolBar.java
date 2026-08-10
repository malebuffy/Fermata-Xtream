package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment;

import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.LEFT;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

/** Adds a main-menu button to the top tool bar. */
public final class HomeToolBar {
	private HomeToolBar() {
	}

	public static void addHomeButton(ToolBarView tb, ActivityFragment f) {
		if (f.getFragmentId() == R.id.home_fragment) return;
		if (tb.findViewById(R.id.tool_home) != null) return;
		tb.getMediator().addButton(tb, R.drawable.home_menu,
				v -> MainActivityDelegate.get(v.getContext()).showMainMenu(), R.id.tool_home, LEFT);
	}

	public static ToolBarView.Mediator withHome(ToolBarView.Mediator delegate) {
		return new ToolBarView.Mediator() {
			@Override
			public void enable(ToolBarView tb, ActivityFragment f) {
				delegate.enable(tb, f);
				addHomeButton(tb, f);
			}

			@Override
			public void disable(ToolBarView tb) {
				delegate.disable(tb);
			}

			@Override
			public boolean onBackPressed(ToolBarView tb) {
				return delegate.onBackPressed(tb);
			}
		};
	}
}
