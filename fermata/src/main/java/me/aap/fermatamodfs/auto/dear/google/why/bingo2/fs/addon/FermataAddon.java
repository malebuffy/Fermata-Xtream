package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.BuildConfig;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public interface FermataAddon {

	@IdRes
	int getAddonId();

	@NonNull
	AddonInfo getInfo();

	default void contributeSettings(Context ctx, PreferenceStore store, PreferenceSet set,
																	ChangeableCondition visibility) {
	}

	default void install() {
	}

	default void uninstall() {
	}

	default boolean handleIntent(MainActivityDelegate a, Intent intent) {
		return false;
	}

	@NonNull
	static AddonInfo findAddonInfo(String name) {
		boolean cn = name.indexOf('.') > 0;
		for (AddonInfo ai : BuildConfig.ADDONS) {
			if (name.equals(cn ? ai.className : ai.moduleName)) return ai;
		}
		throw new RuntimeException("Addon not found: " + name);
	}
}
