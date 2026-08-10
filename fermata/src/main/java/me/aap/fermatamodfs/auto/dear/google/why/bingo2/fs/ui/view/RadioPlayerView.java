package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view;

import static android.media.AudioManager.ERROR;

import android.content.Context;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine.MediaEngine;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.BrowsableItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.FermataServiceUiBinder;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.MediaSessionCallback;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;

public class RadioPlayerView extends FrameLayout implements FermataServiceUiBinder.Listener,
		MediaSessionCallback.Listener {
	private static final long SESSION_POLL_MS = 2000;
	private final boolean showVisualizer = false;
	private TextView groupView;
	private TextView stationView;
	@Nullable
	private RadioVisualizerView visualizer;
	private boolean bound;
	private boolean listenersRegistered;
	private int lastAudioSessionId = ERROR;
	private final Runnable sessionPoll = new Runnable() {
		@Override
		public void run() {
			if (!showVisualizer || visualizer == null) return;
			if (!bound || !visualizer.isAnimating()) return;
			applyAudioSession();
			visualizer.ensureCapture();
			postDelayed(this, SESSION_POLL_MS);
		}
	};

	public RadioPlayerView(@NonNull Context ctx, @Nullable AttributeSet attrs) {
		super(ctx, attrs);
		setFocusable(true);
		LayoutInflater.from(ctx).inflate(R.layout.radio_player_view, this, true);
		groupView = findViewById(R.id.radio_player_group);
		stationView = findViewById(R.id.radio_player_station);
		View visualizerContainer = findViewById(R.id.radio_player_visualizer_container);
		if (showVisualizer) {
			visualizer = findViewById(R.id.radio_visualizer);
		} else {
			visualizerContainer.setVisibility(GONE);
			groupView.setVisibility(GONE);
			stationView.setTextSize(22);
		}
		registerListeners();
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		registerListeners();
	}

	@Override
	protected void onDetachedFromWindow() {
		stopSessionPoll();
		unregisterListeners();
		super.onDetachedFromWindow();
	}

	private void registerListeners() {
		if (listenersRegistered) return;
		listenersRegistered = true;
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
			if (!listenersRegistered) return;
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			b.addBroadcastListener(this);
			b.getMediaSessionCallback().addBroadcastListener(this);
			MediaLib.PlayableItem cur = b.getCurrentItem();
			if (cur != null) bind(cur);
			applyAudioSession();
			updateAnimation(b.getMediaSessionCallback().getPlaybackState());
		});
	}

	private void unregisterListeners() {
		if (!listenersRegistered) return;
		listenersRegistered = false;
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			b.removeBroadcastListener(this);
			b.getMediaSessionCallback().removeBroadcastListener(this);
		});
	}

	public void bind(@Nullable MediaLib.PlayableItem item) {
		bound = item != null;
		if (item == null) {
			stationView.setText("");
			groupView.setText("");
			lastAudioSessionId = ERROR;
			stopSessionPoll();
			if (visualizer != null) {
				visualizer.setAudioSessionId(ERROR);
				visualizer.setAnimating(false);
			}
			return;
		}

		stationView.setText(item.getName());
		if (showVisualizer) {
			BrowsableItem parent = item.getParent();
			String group = (parent != null) ? parent.getName() : null;
			groupView.setText(TextUtils.isEmpty(group) ?
					getContext().getString(R.string.radio_player_subtitle) : group);
		}
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
			applyAudioSession();
			updateAnimation(a.getMediaServiceBinder().getMediaSessionCallback().getPlaybackState());
		});
	}

	@Override
	public void onPlayableChanged(MediaLib.PlayableItem oldItem, MediaLib.PlayableItem newItem) {
		bind(newItem);
		applyAudioSession();
	}

	@Override
	public void onPlaybackStateChanged(MediaSessionCallback cb, PlaybackStateCompat state) {
		applyAudioSession();
		updateAnimation(state);
		if (showVisualizer) {
			postDelayed(this::applyAudioSession, 250);
			postDelayed(this::applyAudioSession, 1000);
		}
	}

	private void applyAudioSession() {
		if (!showVisualizer || visualizer == null) return;
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
			MediaEngine eng = a.getMediaServiceBinder().getCurrentEngine();
			int sessionId = resolveSessionId(eng);
			if (sessionId != lastAudioSessionId) {
				lastAudioSessionId = sessionId;
				visualizer.setAudioSessionId(sessionId);
			} else {
				visualizer.ensureCapture();
			}
		});
	}

	private static int resolveSessionId(@Nullable MediaEngine eng) {
		if (eng == null) return 0;
		int sessionId = eng.getAudioSessionId();
		return (sessionId == ERROR) ? 0 : sessionId;
	}

	private void updateAnimation(@Nullable PlaybackStateCompat state) {
		if (visualizer == null) return;
		if (!bound) {
			stopSessionPoll();
			visualizer.setAnimating(false);
			return;
		}
		visualizer.setAnimating(true);
		startSessionPoll();
	}

	private void startSessionPoll() {
		if (!showVisualizer) return;
		removeCallbacks(sessionPoll);
		postDelayed(sessionPoll, SESSION_POLL_MS);
	}

	private void stopSessionPoll() {
		removeCallbacks(sessionPoll);
	}
}
