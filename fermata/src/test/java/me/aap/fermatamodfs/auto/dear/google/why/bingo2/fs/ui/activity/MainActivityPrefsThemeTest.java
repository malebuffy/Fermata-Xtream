package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class MainActivityPrefsThemeTest {
	@Test
	public void persistedThemeIdsRemainStable() {
		assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6, 7}, new int[]{
				MainActivityPrefs.THEME_DARK,
				MainActivityPrefs.THEME_LIGHT,
				MainActivityPrefs.THEME_SYSTEM,
				MainActivityPrefs.THEME_BLACK,
				MainActivityPrefs.THEME_STAR_WARS,
				MainActivityPrefs.THEME_PURPLE,
				MainActivityPrefs.THEME_CLASSIC,
				MainActivityPrefs.THEME_LIQUID_GLASS
		});
	}
}
