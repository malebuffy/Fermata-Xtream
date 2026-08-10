package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt;

import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.net.Uri;

import androidx.annotation.NonNull;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.FermataWebClient;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.WebBrowserFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeWebClient extends FermataWebClient {

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
		if (!request.isForMainFrame()) return false;
		if (!isAllowedUri(request.getUrl())) {
			MainActivityDelegate a = MainActivityDelegate.get(view.getContext());

			try {
				if (!(a.showFragment(
						me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.web_browser_fragment) instanceof WebBrowserFragment f))
					return false;
				f.loadUrl(request.getUrl().toString());
				return true;
			} catch (IllegalArgumentException ex) {
				Log.d(ex);
			}
		}

		return false;
	}

	protected boolean isAllowedUri(Uri uri) {
		return isYoutubeUri(uri);
	}

	protected boolean isGoogleServiceUri(Uri uri) {
		String host = uri.getHost();
		return (host != null) && (host.equals("accounts.google.com") ||
				host.equals("consent.youtube.com") || host.equals("consent.google.com"));
	}
}
