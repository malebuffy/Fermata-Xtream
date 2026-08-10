package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.pref;

/**
 * @author Andrey Pavlenko
 */
public interface StreamItemPrefs extends PlayableItemPrefs, BrowsableItemPrefs {

	@Override
	default int getSortByPref() {
		return SORT_BY_NONE;
	}
}
