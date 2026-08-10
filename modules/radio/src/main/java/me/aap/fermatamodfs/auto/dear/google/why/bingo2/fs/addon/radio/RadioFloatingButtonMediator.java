package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio;

import android.view.View;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.FloatingButtonMediator;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;

class RadioFloatingButtonMediator extends FloatingButtonMediator {
	static final RadioFloatingButtonMediator instance = new RadioFloatingButtonMediator();

	@Override
	public int getIcon(FloatingButton fb) {
		MainActivityDelegate a = MainActivityDelegate.get(fb.getContext());
		return (a.isVideoMode() || !a.isRootPage()) ? getBackIcon() :
				me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.playlist_add;
	}

	@Override
	public void onClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		if (a.isVideoMode() || !a.isRootPage()) {
			a.onBackPressed();
		} else {
			ActivityFragment f = a.getActiveFragment();
			if (f instanceof RadioFragment) ((RadioFragment) f).addSource();
		}
	}
}
