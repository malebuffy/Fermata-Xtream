package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;

/**
 * @author Andrey Pavlenko
 */
public interface FermataActivityAddon extends FermataAddon {

	default void onActivityCreate(MainActivityDelegate a) {
	}

	default void onActivityDestroy(MainActivityDelegate a) {
	}

	default void onActivityResume(MainActivityDelegate a) {
	}

	default void onActivityPause(MainActivityDelegate a) {
	}
}
