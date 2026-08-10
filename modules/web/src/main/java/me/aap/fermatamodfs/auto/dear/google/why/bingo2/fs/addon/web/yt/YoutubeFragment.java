package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.BuildConfig;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.AddonManager;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.FermataChromeClient;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.FermataWebView;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.WebBrowserAddon;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.WebBrowserFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine.MediaEngine;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.FermataServiceUiBinder;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.MediaSessionCallback;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.VideoView;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class YoutubeFragment extends WebBrowserFragment implements FermataServiceUiBinder.Listener {
	private static final String DEFAULT_URL = "https://m.youtube.com";
	private static final Set<String> DEFAULT_URLS = new HashSet<>(Arrays.asList(DEFAULT_URL, DEFAULT_URL + '/'));
	private static final Pref<LongSupplier> RESUME_POS = Pref.l("YT_RESUME_POS", 0L);
	private boolean playOnResume;

	@Override
	public int getFragmentId() {
		return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.youtube_fragment;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.youtube, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
		YoutubeAddon addon = getYoutubeAddon();
		if (addon == null) return;

		String url;
		boolean pause;

		if (state != null) {
			url = state.getString("url", getDefaultUrl());
			pause = state.getBoolean("pause", false);
		} else {
			url = getDefaultUrl();
			pause = false;
		}

		MainActivityDelegate.getActivityDelegate(view.getContext()).onSuccess(a -> {
			YoutubeWebView webView = view.findViewById(R.id.ytWebView);
			VideoView videoView = view.findViewById(R.id.ytVideoView);
			YoutubeWebClient webClient = createWebClient();
			YoutubeChromeClient chromeClient = new YoutubeChromeClient(webView, videoView);
			MediaEngine oldEngine = a.getMediaSessionCallback().getEngine();
			if ((oldEngine instanceof YoutubeMediaEngine yt) && (yt.getAddonId() != getFragmentId())) {
				a.getMediaSessionCallback().onStop();
			}
			webView.init(addon, webClient, chromeClient);
			registerListeners(a);
			webView.loadUrl(getDefaultUrl());
			if (!getDefaultUrl().equals(url)) a.post(() -> webView.loadUrl(url));
			a.postDelayed(() -> {
				PreferenceStore ps = addon.getPreferenceStore();
				Pref<LongSupplier> resume = getResumePositionPref();
				long pos = ps.getLongPref(resume);
				ps.removePref(resume);
				MediaSessionCallback cb = a.getMediaSessionCallback();
				if (cb.getEngine() instanceof YoutubeMediaEngine yt && (yt.getAddonId() == getFragmentId())) {
					if (pos > 0L) cb.onSeekTo(pos);
					if (pause) cb.onPause();
				}
			}, 3000L);
		});
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle state) {
		super.onSaveInstanceState(state);
		String url = getUrl();
		if (url != null) state.putString("url", url);
		WebBrowserAddon addon = getAddon();
		if (addon == null) return;
		MainActivityDelegate a = MainActivityDelegate.getActivityDelegate(getContext()).peek();
		if (a == null) return;

		SharedPreferenceStore ps = addon.getPreferenceStore();
		MediaSessionCallback cb = a.getMediaSessionCallback();
		MediaEngine eng = cb.getEngine();

		if (eng instanceof YoutubeMediaEngine yt && (yt.getAddonId() == getFragmentId())) {
			state.putBoolean("pause", !cb.isPlaying());
			eng.getPosition().onSuccess(pos -> ps.applyLongPref(getResumePositionPref(), pos));
		} else {
			ps.removePref(getResumePositionPref());
		}
	}

	@Override
	public void onDestroyView() {
		FermataWebView v = getWebView();
		if ((v != null) && (v.getWebChromeClient() != null)) {
			v.getWebChromeClient().exitFullScreen();
		}
		unregisterListeners(MainActivityDelegate.get(requireContext()));
		super.onDestroyView();
	}

	@Override
	protected void registerListeners(MainActivityDelegate a) {
		super.registerListeners(a);
		a.getMediaServiceBinder().addBroadcastListener(this);
	}

	protected void unregisterListeners(MainActivityDelegate a) {
		super.unregisterListeners(a);
		a.getMediaServiceBinder().removeBroadcastListener(this);
	}

	@Override
	public void onPause() {
		if (!BuildConfig.AUTO) {
			MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
				FermataServiceUiBinder b = a.getMediaServiceBinder();
				if (isOwnYoutubeItem(b.getCurrentItem()) && b.isPlaying()) {
					b.getMediaSessionCallback().onPause();
					playOnResume = true;
				} else {
					playOnResume = false;
				}
			});
		}
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (BuildConfig.AUTO || !playOnResume) return;
		playOnResume = false;
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			if (isOwnYoutubeItem(b.getCurrentItem())) {
				b.getMediaSessionCallback().onPlay();
			}
		});
	}

	public void loadUrl(String url) {
		FermataWebView v = getWebView();
		if (v != null) v.loadUrl(url);
	}

	@Override
	public void onPlayableChanged(MediaLib.PlayableItem oldItem, MediaLib.PlayableItem newItem) {
		if (isHidden()) return;

		if (isOwnYoutubeItem(newItem)) {
			FermataWebView v = getWebView();
			MainActivityDelegate a = MainActivityDelegate.get(getContext());
			if (v == null) return;

			FermataChromeClient chrome = v.getWebChromeClient();
			if (chrome == null) return;

			chrome.enterFullScreen();
		} else if (isOwnYoutubeItem(oldItem)) {
			FermataWebView v = getWebView();
			if (v == null) return;
			FermataChromeClient chrome = v.getWebChromeClient();
			if (chrome != null) chrome.exitFullScreen();
		}
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return ToolBarView.Mediator.Invisible.instance;
	}

	@Override
	public boolean canScrollUp() {
		FermataWebView v = getWebView();
		if (v == null) return false;
		FermataChromeClient chrome = v.getWebChromeClient();
		return (chrome != null) && (chrome.isFullScreen() || (v.getScrollY() > 0));
	}

	@Nullable
	protected WebBrowserAddon getAddon() {
		return getYoutubeAddon();
	}

	@Nullable
	protected YoutubeAddon getYoutubeAddon() {
		return AddonManager.get().getAddon(YoutubeAddon.class);
	}

	@Nullable
	protected YoutubeWebView getWebView() {
		View v = getView();
		return (v != null) ? v.findViewById(R.id.ytWebView) : null;
	}

	protected boolean isDesktopVersionSupported() {
		return false;
	}

	@Override
	protected String getSearchUrl() {
		return "https://www.youtube.com/results?search_query=";
	}

	protected String getDefaultUrl() {
		return DEFAULT_URL;
	}

	protected Pref<LongSupplier> getResumePositionPref() {
		return RESUME_POS;
	}

	protected YoutubeWebClient createWebClient() {
		return new YoutubeWebClient();
	}

	protected boolean isDefaultUrl(String url) {
		return DEFAULT_URLS.contains(url);
	}

	protected boolean isOwnYoutubeItem(MediaLib.Item item) {
		return YoutubeMediaEngine.isYoutubeItem(item, getFragmentId());
	}
}
