package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.AddonInfo;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.FermataAddon;
import me.aap.utils.ui.fragment.ActivityFragment;

@Keep
@SuppressWarnings("unused")
public class YoutubeMusicAddon extends YoutubeAddon {
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(YoutubeMusicAddon.class.getName());

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.youtube_music_fragment;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new YoutubeMusicFragment();
	}
}
