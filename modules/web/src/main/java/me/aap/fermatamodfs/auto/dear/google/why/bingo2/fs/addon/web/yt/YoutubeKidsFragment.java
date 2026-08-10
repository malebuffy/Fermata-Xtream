package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt;

import android.net.Uri;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.AddonManager;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.pref.PreferenceStore.Pref;

public class YoutubeKidsFragment extends YoutubeFragment {
	private static final String DEFAULT_URL = "https://www.youtubekids.com";
	private static final Pref<LongSupplier> RESUME_POS = Pref.l("YTK_RESUME_POS", 0L);
	private static final float PAGE_SCALE = 1f;

	@Override
	public int getFragmentId() {
		return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.youtube_kids_fragment;
	}

	@Nullable
	@Override
	protected YoutubeAddon getYoutubeAddon() {
		return AddonManager.get().getAddon(YoutubeKidsAddon.class);
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
				return (host != null) && (host.equals("youtubekids.com") ||
						host.endsWith(".youtubekids.com") || isYoutubeUri(uri) ||
						isGoogleServiceUri(uri));
			}

			@Override
			public void onPageFinished(WebView view, String url) {
				super.onPageFinished(view, url);
				fitKidsPage(view);
			}
		};
	}

	private void fitKidsPage(WebView view) {
		view.getSettings().setUseWideViewPort(false);
		view.getSettings().setLoadWithOverviewMode(true);
		view.setInitialScale(Math.round(PAGE_SCALE * 100));
	}

	@Override
	protected boolean isDefaultUrl(String url) {
		return DEFAULT_URL.equals(url) || (DEFAULT_URL + '/').equals(url);
	}

	@Override
	protected String getSearchUrl() {
		return "https://www.youtubekids.com/search?q=";
	}
}
