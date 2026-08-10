package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt;

import android.net.Uri;

import androidx.annotation.Nullable;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.AddonManager;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.pref.PreferenceStore.Pref;

public class YoutubeMusicFragment extends YoutubeFragment {
	private static final String DEFAULT_URL = "https://music.youtube.com";
	private static final Pref<LongSupplier> RESUME_POS = Pref.l("YTM_RESUME_POS", 0L);

	@Override
	public int getFragmentId() {
		return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.youtube_music_fragment;
	}

	@Nullable
	@Override
	protected YoutubeAddon getYoutubeAddon() {
		return AddonManager.get().getAddon(YoutubeMusicAddon.class);
	}

	@Override
	protected String getDefaultUrl() {
		return DEFAULT_URL;
	}

	@Override
	protected Pref<LongSupplier> getResumePositionPref() {
		return RESUME_POS;
	}

	@Override
	protected YoutubeWebClient createWebClient() {
		return new YoutubeWebClient() {
			@Override
			protected boolean isAllowedUri(Uri uri) {
				String host = uri.getHost();
				return (host != null) && (host.equals("music.youtube.com") || isYoutubeUri(uri) ||
						isGoogleServiceUri(uri));
			}
		};
	}

	@Override
	protected boolean isDefaultUrl(String url) {
		return DEFAULT_URL.equals(url) || (DEFAULT_URL + '/').equals(url);
	}

	@Override
	protected String getSearchUrl() {
		return "https://music.youtube.com/search?q=";
	}
}
