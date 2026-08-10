package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web;

import android.webkit.JavascriptInterface;

import androidx.annotation.Keep;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.utils.app.App;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class FermataJsInterface {
	public static final String NAME = "Fermata";
	public static final String JS_EVENT = "window.Fermata.event";
	public static final int JS_EDIT = 0;
	public static final int JS_ERR = 1;
	public static final int JS_MEDIA_PLAYING = 2;
	public static final int JS_MEDIA_PAUSED = 3;
	public static final int JS_MEDIA_ENDED = 4;
	protected static final int JS_LAST = 4;
	private final FermataWebView webView;
	private final WebMediaEngine mediaEngine;

	public FermataJsInterface(FermataWebView webView) {
		this.webView = webView;
		mediaEngine = new WebMediaEngine(webView, MainActivityDelegate.get(webView.getContext()));
	}

	public FermataWebView getWebView() {
		return webView;
	}

	@Keep
	@SuppressWarnings("unused")
	@JavascriptInterface
	public void event(int event, String data) {
		App.get().run(() -> handleEvent(event, data));
	}

	protected void handleEvent(int event, String data) {
		switch (event) {
			case JS_EDIT:
				Log.d("Edit text event: ", data);
				getWebView().showKeyboard(data);
				break;
			case JS_ERR:
				Log.e("JavaScript error: ", data);
				break;
			case JS_MEDIA_PLAYING:
				mediaEngine.playing(data);
				break;
			case JS_MEDIA_PAUSED:
				mediaEngine.paused();
				break;
			case JS_MEDIA_ENDED:
				mediaEngine.ended();
				break;
		}
	}
}
