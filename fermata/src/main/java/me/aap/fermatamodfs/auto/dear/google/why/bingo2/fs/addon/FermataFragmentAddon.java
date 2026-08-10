package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon;

import androidx.annotation.NonNull;

import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
public interface FermataFragmentAddon extends FermataAddon {

	@NonNull
	ActivityFragment createFragment();

	default int getFragmentId() {
		return getAddonId();
	}
}
