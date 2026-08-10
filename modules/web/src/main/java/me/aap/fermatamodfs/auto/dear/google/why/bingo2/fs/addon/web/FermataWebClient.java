package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web;

import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;


import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt.YoutubeFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt.YoutubeKidsFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt.YoutubeMusicFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.BooleanConsumer;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class FermataWebClient extends WebViewClientCompat {
	BooleanConsumer loading;

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		if (loading != null) {
			loading.accept(true);
		} else {
			MainActivityDelegate.getActivityDelegate(view.getContext())
					.onSuccess(a -> a.setContentLoading(new Promise<>()));
		}
		super.onPageStarted(view, url, favicon);
	}

	@Override
	public void onPageFinished(WebView view, String url) {
		FermataWebView v = (FermataWebView) view;
		FutureSupplier<MainActivityDelegate> f = MainActivityDelegate.getActivityDelegate(v.getContext());
		f.onSuccess(a -> a.setContentLoading(Completed.completedVoid()));

		if (loading != null) {
			loading.accept(false);
			loading = null;
		}

		super.onPageFinished(view, url);
		((FermataWebView) view).hideKeyboard();
		v.pageLoaded(url);
		f.onSuccess(a -> a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED));
	}

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
		Uri uri = request.getUrl();

		if (isYoutubeMusicUri(uri)) {
			try {
				MainActivityDelegate a = MainActivityDelegate.getActivityDelegate(view.getContext()).peek();
				if (a == null) return false;
				if (!(a.showFragment(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.youtube_music_fragment) instanceof YoutubeMusicFragment f))
					return false;
				f.loadUrl(uri.toString());
				return true;
			} catch (IllegalArgumentException ex) {
				Log.d(ex);
			}
		} else if (isYoutubeKidsUri(uri)) {
			try {
				MainActivityDelegate a = MainActivityDelegate.getActivityDelegate(view.getContext()).peek();
				if (a == null) return false;
				if (!(a.showFragment(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.youtube_kids_fragment) instanceof YoutubeKidsFragment f))
					return false;
				f.loadUrl(uri.toString());
				return true;
			} catch (IllegalArgumentException ex) {
				Log.d(ex);
			}
		} else if (isYoutubeUri(uri)) {
			try {
				MainActivityDelegate a = MainActivityDelegate.getActivityDelegate(view.getContext()).peek();
				if (a == null) return false;
				if (!(a.showFragment(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.youtube_fragment) instanceof YoutubeFragment f))
					return false;
				f.loadUrl(uri.toString());
				return true;
			} catch (IllegalArgumentException ex) {
				Log.d(ex);
			}
		}

		return false;
	}

	public static boolean isYoutubeUri(Uri uri) {
		String host = uri.getHost();
		return ((host != null) && ((host.endsWith("youtube.com") && !host.endsWith("tv.youtube.com")) ||
				host.equals("youtu.be")));
	}

	public static boolean isYoutubeMusicUri(Uri uri) {
		String host = uri.getHost();
		return "music.youtube.com".equals(host);
	}

	public static boolean isYoutubeKidsUri(Uri uri) {
		String host = uri.getHost();
		return (host != null) && (host.equals("youtubekids.com") || host.endsWith(".youtubekids.com"));
	}

	@Override
	public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request, @NonNull WebResourceErrorCompat error) {
		if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) {
			Log.e("Web error received: " + error.getDescription());
		} else {
			Log.e("Web error received");
		}

		super.onReceivedError(view, request, error);
	}
}
