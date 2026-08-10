package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment;

import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

/** Top app toolbar: back, title, optional filter, and home button. */
public class NavToolBarMediator implements ToolBarView.Mediator.BackTitleFilter {
	private static final NavToolBarMediator instance = new NavToolBarMediator();

	public static NavToolBarMediator getInstance() {
		return instance;
	}

	@Override
	public void enable(ToolBarView tb, ActivityFragment f) {
		BackTitleFilter.super.enable(tb, f);
		HomeToolBar.addHomeButton(tb, f);
	}
}
