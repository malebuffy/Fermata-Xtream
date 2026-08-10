package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web;

import static me.aap.utils.async.Completed.completed;

import android.media.AudioManager;
import android.media.MediaMetadata;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioFocusRequestCompat;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine.MediaEngine;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.ExtPlayable;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.ExtRoot;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.BrowsableItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.PlayableItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.MediaSessionCallback;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.vfs.generic.GenericFileSystem;

class WebMediaEngine implements MediaEngine {
	private static final String ID = "web";
	private final FermataWebView web;
	private final MediaSessionCallback cb;
	private final ExtRoot mediaRoot;
	private final WebItem next;
	private final WebItem prev;
	private WebItem current;
	private boolean ignorePause;

	WebMediaEngine(FermataWebView web, MainActivityDelegate a) {
		this.web = web;
		cb = a.getMediaSessionCallback();
		String prefix = ID + ':' + web.getAddon().getAddonId();
		mediaRoot = new ExtRoot(prefix, a.getLib());
		next = new WebItem(prefix + ":next", mediaRoot, "next");
		prev = new WebItem(prefix + ":prev", mediaRoot, "previous");
	}

	void playing(@Nullable String url) {
		current = new Current((url == null || url.isEmpty()) ? web.getUrl() : url);
		cb.setEngine(this);
		cb.onEngineStarted(this);
	}

	void paused() {
		ignorePause = true;
		cb.onPause();
		ignorePause = false;
	}

	void ended() {
		cb.onEngineEnded(this);
	}

	@Override
	public int getId() {
		return -1;
	}

	@Override
	public void prepare(PlayableItem source) {
		if (source == next) web.mediaSkip(true);
		else if (source == prev) web.mediaSkip(false);
		else cb.onEnginePrepared(this);
	}

	@Override
	public void start() {
		web.mediaPlay();
	}

	@Override
	public void stop() {
		web.mediaStop();
		current = null;
	}

	@Override
	public void pause() {
		if (!ignorePause) web.mediaPause();
	}

	@Override
	public PlayableItem getSource() {
		return current;
	}

	@Override
	public FutureSupplier<Long> getDuration() {
		return web.getMediaMilliseconds("duration");
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		return web.getMediaMilliseconds("currentTime");
	}

	@Override
	public void setPosition(long position) {
		web.setMediaPosition(position);
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		return web.getMediaSpeed();
	}

	@Override
	public void setSpeed(float speed) {
		web.setMediaSpeed(speed);
	}

	@Override
	public void setVideoView(@Nullable VideoView view) {
	}

	@Override
	public float getVideoWidth() {
		return 0;
	}

	@Override
	public float getVideoHeight() {
		return 0;
	}

	@Override
	public void close() {
	}

	@Override
	public boolean requestAudioFocus(@Nullable AudioManager audioManager,
			@Nullable AudioFocusRequestCompat audioFocusReq) {
		return true;
	}

	@Override
	public void releaseAudioFocus(@Nullable AudioManager audioManager,
			@Nullable AudioFocusRequestCompat audioFocusReq) {
	}

	private static class WebItem extends ExtPlayable {
		WebItem(String id, @NonNull BrowsableItem parent, String url) {
			super(id, parent, GenericFileSystem.getInstance().create(url));
		}

		@Override
		public boolean isSeekable() {
			return true;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			return obj == this;
		}
	}

	private final class Current extends WebItem {
		Current(String url) {
			super(mediaRoot.getId() + ":current", mediaRoot, url);
		}

		@NonNull
		@Override
		protected FutureSupplier<MediaMetadataCompat> loadMeta() {
			FutureSupplier<String> getTitle = web.getMediaTitle();
			return web.getMediaMilliseconds("duration").then(dur -> getTitle.map(title -> {
				MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
				b.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
				b.putLong(MediaMetadata.METADATA_KEY_DURATION, dur);
				return b.build();
			}));
		}

		@NonNull
		@Override
		public FutureSupplier<PlayableItem> getPrevPlayable() {
			return completed(prev);
		}

		@NonNull
		@Override
		public FutureSupplier<PlayableItem> getNextPlayable() {
			return completed(next);
		}
	}
}
