package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt;

import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt.YoutubeJsInterface.JS_ERR;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt.YoutubeJsInterface.JS_EVENT;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt.YoutubeJsInterface.JS_VIDEO_ENDED;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt.YoutubeJsInterface.JS_VIDEO_FOUND;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PAUSED;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PLAYING;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt.YoutubeJsInterface.JS_VIDEO_QUALITIES;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.BuildConfig;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.FermataChromeClient;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.FermataJsInterface;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.FermataWebClient;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.FermataWebView;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.WebBrowserAddon;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.MediaSessionCallback;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeWebView extends FermataWebView {
	private static final String CLEAR_HIGHEST_VIDEO_QUALITY_JS =
			"function clearFermataQ() {\n" +
					"  if (!window.__fermataQ) return;\n" +
					"  if (window.__fermataQ.timeout) clearTimeout(window.__fermataQ.timeout);\n" +
					"  if (window.__fermataQ.player && window.__fermataQ.handler) {\n" +
					"    try { window.__fermataQ.player.removeEventListener('onStateChange', window.__fermataQ.handler); } catch(e) {}\n" +
					"  }\n" +
					"  window.__fermataQ = null;\n" +
					"}\n";
	private YoutubeJsInterface js;
	/** True after media-session / Fermata play request. */
	private boolean userRequestedPlay;
	/** True after the user touches / activates the WebView (YouTube's own play button). */
	private boolean userInteracted;

	public YoutubeWebView(Context context) {
		super(context);
	}

	public YoutubeWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public YoutubeWebView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	public void init(WebBrowserAddon addon, FermataWebClient webClient,
			FermataChromeClient chromeClient) {
		super.init(addon, webClient, chromeClient);
		updateAutoplayPolicy();
	}

	@Override
	protected FermataJsInterface createJsInterface() {
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		return js = new YoutubeJsInterface(this, new YoutubeMediaEngine(this, a));
	}

	@Override
	public YoutubeAddon getAddon() {
		return (YoutubeAddon) super.getAddon();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		super.onPreferenceChanged(store, prefs);

		if (getAddon().autoHighestQualityChanged(prefs)) {
			if (getAddon().autoHighestQuality()) setHighestVideoQuality();
			else clearHighestVideoQuality();
		}

		if (getAddon().autoplayOnOpenChanged(prefs)) updateAutoplayPolicy();
		if (YoutubeSponsorBlock.isPreferenceChanged(prefs)) injectSponsorBlock();
	}

	private void updateAutoplayPolicy() {
		getSettings().setMediaPlaybackRequiresUserGesture(!getAddon().autoplayOnOpen());
	}

	boolean allowPlaybackStart() {
		return getAddon().autoplayOnOpen() || userRequestedPlay || userInteracted;
	}

	void markUserRequestedPlay() {
		userRequestedPlay = true;
		userInteracted = true;
	}

	private void clearPlaybackGate() {
		userRequestedPlay = false;
		userInteracted = false;
	}

	void clearUserRequestedPlay() {
		clearPlaybackGate();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
			userInteracted = true;
		}
		return super.onTouchEvent(event);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			int key = event.getKeyCode();
			if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
					|| key == KeyEvent.KEYCODE_NUMPAD_ENTER || key == KeyEvent.KEYCODE_SPACE
					|| key == KeyEvent.KEYCODE_MEDIA_PLAY || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
				userInteracted = true;
			}
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public void loadUrl(@NonNull String url) {
		Log.d("Loading URL: " + url);
		if (url != null && !url.startsWith("javascript:")) {
			// New navigation: block site autoplay until the user interacts again.
			clearPlaybackGate();
		}
		super.loadUrl(url);
	}

	@Override
	public void goBack() {
		MediaSessionCallback cb = MainActivityDelegate.get(getContext()).getMediaSessionCallback();
		if (cb.getEngine() instanceof YoutubeMediaEngine yt && (yt.getAddonId() == getAddon().getAddonId()))
			cb.onStop();
		super.goBack();
	}

	@Override
	protected void pageLoaded(String uri) {
		attachListeners();
		if (!getAddon().autoplayOnOpen()) {
			// Stop site-initiated autoplay even when the WebView gesture flag is bypassed.
			loadUrl("javascript:(function(){var v=document.querySelector('video');" +
					"if(v&&!v.paused){v.pause();}})();");
		}
		injectSponsorBlock();
		addFocusHighlight();
		CookieManager.getInstance().flush();
	}

	protected void submitForm() {
		if (!me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.BuildConfig.AUTO) return;
		loadUrl("javascript:\n" +
				"var e = new KeyboardEvent('keydown',\n" +
				"{ code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });\n" +
				"document.activeElement.dispatchEvent(e);\n" +
				"e = new KeyboardEvent('keyup',\n" +
				"{ code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });\n" +
				"document.activeElement.dispatchEvent(e);");
	}

	private void attachListeners() {
		String debug = BuildConfig.D ? JS_EVENT + "(" + JS_VIDEO_FOUND + ", null);\n" : "";
		String scale = getAddon().getScale().prefName();
		loadUrl("javascript:\n" +
				"function attachVideoListeners(v) {\n" +
				"  if (v.getAttribute('FermataAttached') === 'true') return;\n" +
				"  v.setAttribute('FermataAttached', 'true');\n" +
				"  v.setAttribute('style', 'object-fit:" + scale + "');\n" + debug +
				"  if ((v.currentTime > 0) && !v.paused && !v.ended) " + JS_EVENT + "(" + JS_VIDEO_PLAYING +
				", v.currentSrc);\n" +
				"  v.addEventListener('playing', function(e) {" + JS_EVENT + "(" + JS_VIDEO_PLAYING +
				", v.currentSrc);});\n" +
				"  v.addEventListener('pause', function(e) {" + JS_EVENT + "(" + JS_VIDEO_PAUSED +
				", v.currentSrc);});\n" +
				"  v.addEventListener('ended', function(e) {" + JS_EVENT + "(" + JS_VIDEO_ENDED +
				", null);});\n" +
				"}\n" +
				"function findVideo() {\n" +
				"  var video = document.querySelectorAll('video');" +
				"  video.forEach(attachVideoListeners);\n" +
				"   setTimeout(findVideo, 1000);\n" +
				"}\n" +
				"findVideo();");
	}

	private void injectSponsorBlock() {
		String script = YoutubeSponsorBlock.getScript(getContext(), getAddon().getPreferenceStore());
		if (!script.isEmpty()) evaluateJavascript(script, result -> configureSponsorBlock());
		else configureSponsorBlock();
	}

	private void configureSponsorBlock() {
		evaluateJavascript("if (window.FermataSponsorBlock) window.FermataSponsorBlock.configure(" +
				YoutubeSponsorBlock.getConfigJson(getAddon().getPreferenceStore()) + ");", null);
	}

	protected boolean requestFullScreen() {
		loadUrl("javascript: var v = document.querySelector('video');\n" +
				"if ('webkitRequestFullscreen' in v) v.webkitRequestFullscreen();\n" +
				"else if ('requestFullscreen' in v) v.requestFullscreen();\n" +
				"else " + JS_EVENT + "(" + JS_ERR + ", 'Method requestFullscreen not found in ' + v);");
		return true;
	}

	void play() {
		markUserRequestedPlay();
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.play();");
	}

	void pause() {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.pause();");
	}

	void stop() {
		clearUserRequestedPlay();
		loadUrl("javascript:var v = document.querySelector('video');\n" +
				"if (v != null) { v.currentTime = 0; v.pause(); }");
	}

	void prev() {
		prevNext(false);
	}

	void next() {
		prevNext(true);
	}

	private void prevNext(boolean next) {
		FermataChromeClient chrome = getWebChromeClient();
		if (chrome == null) return;
		chrome.exitFullScreen().thenRun(() -> evaluateJavascript("""
				function prevNextVideo() {
				  const selectors = %s;
				  for (let i = 0; i < selectors.length; i++) {
				    const button = document.querySelector(selectors[i]);
				    if (button != null) {
				      button.click();
				      return true;
				    }
				  }

				  const middleButtons = document.querySelectorAll('button.player-middle-controls-prev-next-button');
				  if (middleButtons.length > 0) {
				    middleButtons[%s].click();
				    return true;
				  }

				  return false;
				}
				setTimeout(prevNextVideo, 600);
				""".formatted(next ? """
						[
						  'button[aria-label*="Next" i]',
						  'button[title*="Next" i]',
						  '.ytp-next-button',
						  'ytmusic-player-bar .next-button',
						  'tp-yt-paper-icon-button.next-button',
						  'button[data-testid*="next" i]'
						]""" : """
						[
						  'button[aria-label*="Previous" i]',
						  'button[aria-label*="Prev" i]',
						  'button[title*="Previous" i]',
						  'button[title*="Prev" i]',
						  '.ytp-prev-button',
						  'ytmusic-player-bar .previous-button',
						  'tp-yt-paper-icon-button.previous-button',
						  'button[data-testid*="previous" i]',
						  'button[data-testid*="prev" i]'
						]""", next ? "middleButtons.length - 1" : "0"), null));
	}

	FutureSupplier<Long> getDuration() {
		return getMilliseconds("duration");
	}

	FutureSupplier<Long> getPosition() {
		return getMilliseconds("currentTime");
	}

	FutureSupplier<String> getVideoQualities() {
		Promise<String> p = js.getResultPromise();
		loadUrl("javascript:\n" +
				"function retryGetVideoQualities(attempt, openMenu) {\n" +
				"  if (attempt < 10) setTimeout(getVideoQualities, 100, attempt + 1, openMenu);\n" +
				"  else " + JS_EVENT + '(' + JS_VIDEO_QUALITIES + ", null);\n" +
				"  return null;\n" +
				"}\n" +
				"function getVideoQualities(attempt, openMenu) {\n" +
				"  if (openMenu) {\n" +
				"    var b = document.querySelector('.player-settings-icon');\n" +
				"    if (b == null) return retryGetVideoQualities(attempt, true);\n" +
				"    b.click();\n" +
				"  }\n" +
				"  var settings = document.querySelector('.player-quality-settings');\n" +
				"  if (settings == null) return retryGetVideoQualities(attempt, false);\n" +
				"  var select = settings.querySelector('.select');\n" +
				"  if (select == null) return retryGetVideoQualities(attempt, false);\n" +
				"  var options = select.querySelectorAll('.option');\n" +
				"  var result = '';\n" +
				"  for (let i = 0; i < options.length; i++) {\n" +
				"    if (i != 0) result += ';';\n" +
				"    if (i == select.selectedIndex) result += '*';\n" +
				"    result += options[i].innerText;\n" +
				"  }\n" +
				"  " + JS_EVENT + '(' + JS_VIDEO_QUALITIES + ", result);\n" +
				"  setTimeout(()=> {settings.parentNode.parentNode.querySelector('" +
				".c3-material-button-button').click();}, 100);\n" +
				"  return result;\n" +
				"}\n" +
				"getVideoQualities(0, true);");
		return p;
	}

	void setVideoQuality(int idx) {
		loadUrl("javascript:\n" +
				"function retrySetVideoQuality(idx, attempt, openMenu) {\n" +
				"  if (attempt < 10) setTimeout(setVideoQuality, 100, idx, attempt + 1, openMenu);\n" +
				"  return false;\n" +
				"}\n" +
				"function setVideoQuality(idx, attempt, openMenu) {\n" +
				"  if (openMenu) {\n" +
				"    var b = document.querySelector('.player-settings-icon');\n" +
				"    if (b == null) return retrySetVideoQuality(idx, attempt, true);\n" +
				"    b.click();\n" +
				"  }\n" +
				"  var settings = document.querySelector('.player-quality-settings');\n" +
				"  if (settings == null) return retrySetVideoQuality(idx, attempt, false);\n" +
				"  var select = settings.querySelector('.select');\n" +
				"  if (select == null) return retrySetVideoQuality(idx, attempt, false);\n" +
				"  var options = select.querySelectorAll('.option');\n" +
				"  var evt = document.createEvent(\"HTMLEvents\");\n" +
				"  evt.initEvent(\"change\", true, true);\n" +
				"  select.selectedIndex = idx;\n" +
				"  options[idx].selected = true;\n" +
				"  select.dispatchEvent(evt);\n" +
				"  setTimeout(()=> {settings.parentNode.parentNode.querySelector('" +
				".c3-material-button-button').click();}, 100);\n" +
				"  return true;\n" +
				"}\n" +
				"setVideoQuality(" + idx + ", 0, true);");
	}

	void setHighestVideoQuality() {
		loadUrl("javascript:\n" +
				"(function() {\n" +
				CLEAR_HIGHEST_VIDEO_QUALITY_JS +
				"  clearFermataQ();\n" +
				"  var state = window.__fermataQ = { player: null, handler: null, timeout: null, attempts: 0 };\n" +
				"  function getPlayer() {\n" +
				"    return document.querySelector('#movie_player') || document.querySelector('.html5-video-player');\n" +
				"  }\n" +
				"  function applyHighest(p) {\n" +
				"    if (!p || typeof p.getAvailableQualityLevels !== 'function') return false;\n" +
				"    var levels = p.getAvailableQualityLevels();\n" +
				"    if (!levels || levels.length === 0) return false;\n" +
				"    var best = null;\n" +
				"    for (var i = 0; i < levels.length; i++) {\n" +
				"      if (levels[i] !== 'auto') { best = levels[i]; break; }\n" +
				"    }\n" +
				"    if (!best) return false;\n" +
				"    if (p.getPlaybackQuality && p.getPlaybackQuality() === best) return true;\n" +
				"    if (typeof p.setPlaybackQualityRange === 'function') p.setPlaybackQualityRange(best, best);\n" +
				"    else if (typeof p.setPlaybackQuality === 'function') p.setPlaybackQuality(best);\n" +
				"    else return false;\n" +
				"    return true;\n" +
				"  }\n" +
				"  function install() {\n" +
				"    var p = getPlayer();\n" +
				"    if (!p || typeof p.addEventListener !== 'function') {\n" +
				"      if (++state.attempts < 50) state.timeout = setTimeout(install, 200);\n" +
				"      return;\n" +
				"    }\n" +
				"    state.player = p;\n" +
				"    state.handler = function(s) {\n" +
				"      if ((s === 1) || (s === 3)) applyHighest(getPlayer() || p);\n" +
				"    };\n" +
				"    p.addEventListener('onStateChange', state.handler);\n" +
				"    applyHighest(p);\n" +
				"  }\n" +
				"  install();\n" +
				"})();");
	}

	void clearHighestVideoQuality() {
		loadUrl("javascript:\n" +
				"(function() {\n" +
				CLEAR_HIGHEST_VIDEO_QUALITY_JS +
				"  clearFermataQ();\n" +
				"})();");
	}

	private FutureSupplier<Long> getMilliseconds(String value) {
		Promise<Long> p = new Promise<>();
		evaluateJavascript(
				"(function(){var v = document.querySelector('video'); return (v != null) ? v." + value +
						" : 0})();",
				v -> {
					try {
						p.complete((long) (Double.parseDouble(v) * 1000));
					} catch (NumberFormatException ex) {
						Log.d(ex);
						p.complete(0L);
					}
				});
		return p;
	}

	void setPosition(long position) {
		double pos = position / 1000f;
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.currentTime = " +
				pos + ";");
	}

	FutureSupplier<Float> getSpeed() {
		Promise<Float> p = new Promise<>();
		evaluateJavascript(
				"(function(){var v = document.querySelector('video'); return (v != null) ? v" +
						".playbackRate" +
						" " +
						": 0})();",
				v -> {
					try {
						p.complete(Float.parseFloat(v));
					} catch (NumberFormatException ex) {
						Log.d(ex);
						p.complete(1f);
					}
				});
		return p;
	}

	void setSpeed(float speed) {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.playbackRate =" +
				" " +
				speed + ";");
	}

	FutureSupplier<String> getVideoTitle() {
		Promise<String> p = new Promise<>();
		evaluateJavascript("document.title", p::complete);
		return p;
	}

	void setScale(YoutubeAddon.VideoScale scale) {
		getAddon().setScale(scale);
		String p = scale.prefName();
		loadUrl("javascript:" +
				"document.querySelectorAll('video')" +
				".forEach(v=> v.setAttribute('style', 'object-fit:" + p + "'));");
	}
}
