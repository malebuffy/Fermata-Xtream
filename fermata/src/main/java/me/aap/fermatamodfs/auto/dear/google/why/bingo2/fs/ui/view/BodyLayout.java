package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_ERROR;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.subtitles_fragment;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityPrefs.L_SPLIT_PERCENT;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityPrefs.P_SPLIT_PERCENT;
import static me.aap.utils.async.Completed.completedVoid;

import android.content.Context;
import android.content.res.Configuration;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.constraintlayout.widget.Guideline;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine.MediaEngine;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine.SubtitleStreamInfo;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.M3uTrackItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.FermataServiceUiBinder;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.MediaSessionCallback;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityListener;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.MainActivityFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.SubtitlesFragment;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
public class BodyLayout extends SplitLayout
		implements SwipeRefreshLayout.OnRefreshListener, SwipeRefreshLayout.OnChildScrollUpCallback,
		MainActivityListener, FermataServiceUiBinder.Listener, MediaSessionCallback.Listener {
	private Mode mode;
	private FutureSupplier<?> startingPlayback = completedVoid();
	@Nullable
	private Promise<Void> startingPlaybackPromise;
	@Nullable
	private Object positionWatchStamp;
	private long startingPlaybackAt;
	private boolean dualStacked;
	private boolean dualLayoutApplied;

	public BodyLayout(@NonNull Context ctx, @Nullable AttributeSet attrs) {
		super(ctx, attrs);

		SwipeRefreshLayout srl = getSwipeRefresh();
		srl.setId(R.id.swiperefresh);
		srl.setOnRefreshListener(this);
		srl.setOnChildScrollUpCallback(this);
		getDualVideoView().setMediaSessionSurface(false);
		setMode(Mode.FRAME);

		MainActivityDelegate.getActivityDelegate(ctx).onSuccess(a -> {
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			b.addBroadcastListener(this);
			a.addBroadcastListener(this, FRAGMENT_CHANGED | ACTIVITY_DESTROY);
			b.getMediaSessionCallback().addBroadcastListener(this);
			onPlayableChanged(null, b.getCurrentItem());
		});
	}

	@Override
	protected int getLayout(boolean portrait) {
		return portrait ? R.layout.body_layout : R.layout.body_layout_land;
	}

	@Override
	protected Pref<DoubleSupplier> getSplitPercentPref(boolean portrait) {
		return portrait ? P_SPLIT_PERCENT : L_SPLIT_PERCENT;
	}

	public Mode getMode() {
		return mode;
	}

	public boolean isFrameMode() {
		return getMode() == Mode.FRAME;
	}

	public boolean isVideoMode() {
		return getMode() == Mode.VIDEO;
	}

	public boolean isBothMode() {
		return getMode() == Mode.BOTH;
	}

	public boolean isRadioMode() {
		return getMode() == Mode.RADIO;
	}

	public boolean isDualMode() {
		return (getMode() == Mode.DUAL_SELECT) || (getMode() == Mode.DUAL);
	}

	public void setMode(Mode mode) {
		if (!isDualMode(mode) && dualLayoutApplied) restoreCurrentLayout();
		this.mode = mode;
		Guideline gl = getGuideline();
		ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) gl.getLayoutParams();
		MainActivityDelegate a = getActivity();
		VideoView vv = getVideoView();
		VideoView dv = getDualVideoView();
		RadioPlayerView rv = getRadioPlayerView();
		View single = getDualSingleButton();
		View toggle = getDualToggleButton();

		dv.setMediaSessionSurface(false);
		if (!isDualMode(mode)) {
			dv.setVisibility(GONE);
			single.setVisibility(GONE);
			toggle.setVisibility(GONE);
		}

		switch (mode) {
			case FRAME -> {
				rv.setVisibility(GONE);
				vv.setVisibility(GONE);
				getSplitLine().setVisibility(GONE);
				getSplitHandle().setVisibility(GONE);
				getSwipeRefresh().setVisibility(VISIBLE);
				lp.guidePercent = isPortrait() ? 0f : 1f;
				a.setVideoMode(false, vv);
			}
			case RADIO -> {
				rv.setVisibility(VISIBLE);
				vv.setVisibility(GONE);
				getSplitLine().setVisibility(GONE);
				getSplitHandle().setVisibility(GONE);
				getSwipeRefresh().setVisibility(GONE);
				lp.guidePercent = isPortrait() ? 1f : 0f;
				a.setVideoMode(false, vv);
				App.get().getHandler().post(rv::requestFocus);
			}
			case VIDEO -> {
				rv.setVisibility(GONE);
				vv.setVisibility(VISIBLE);
				getSplitLine().setVisibility(GONE);
				getSplitHandle().setVisibility(GONE);
				getSwipeRefresh().setVisibility(GONE);
				lp.guidePercent = isPortrait() ? 1f : 0f;
				vv.showVideo(true);
				a.setVideoMode(true, vv);
				App.get().getHandler().post(vv::requestFocus);
			}
			case BOTH -> {
				rv.setVisibility(GONE);
				vv.setVisibility(VISIBLE);
				getSplitLine().setVisibility(VISIBLE);
				getSplitHandle().setVisibility(VISIBLE);
				getSwipeRefresh().setVisibility(VISIBLE);
				lp.guidePercent = a.getPrefs().getFloatPref(getSplitPercentPref(isPortrait()));
				vv.showVideo(true);
				a.setVideoMode(true, vv);
				MediaItemListView.focusActive(getContext(), vv);
			}
			case DUAL_SELECT -> {
				rv.setVisibility(GONE);
				applyDualLayout();
				vv.setVisibility(VISIBLE);
				dv.setVisibility(GONE);
				getSplitLine().setVisibility(VISIBLE);
				getSplitHandle().setVisibility(VISIBLE);
				getSwipeRefresh().setVisibility(VISIBLE);
				single.setVisibility(VISIBLE);
				toggle.setVisibility(VISIBLE);
				lp.guidePercent = 0.5f;
				vv.showVideo(true);
				a.setVideoMode(true, vv);
				MediaItemListView.focusActive(getContext(), vv);
			}
			case DUAL -> {
				rv.setVisibility(GONE);
				applyDualLayout();
				vv.setVisibility(VISIBLE);
				dv.setVisibility(VISIBLE);
				getSplitLine().setVisibility(VISIBLE);
				getSplitHandle().setVisibility(VISIBLE);
				getSwipeRefresh().setVisibility(GONE);
				single.setVisibility(VISIBLE);
				toggle.setVisibility(VISIBLE);
				lp.guidePercent = 0.5f;
				vv.showVideo(true);
				dv.showVideo(true);
				a.setVideoMode(true, vv);
				App.get().getHandler().post(vv::requestFocus);
			}
		}

		gl.setLayoutParams(lp);
		a.fireBroadcastEvent(MODE_CHANGED);
	}

	public VideoView getVideoView() {
		return findViewById(R.id.video_view);
	}

	public VideoView getDualVideoView() {
		return findViewById(R.id.dual_video_view);
	}

	public RadioPlayerView getRadioPlayerView() {
		return findViewById(R.id.radio_player_view);
	}

	public View getDualSingleButton() {
		return findViewById(R.id.tv_dual_view_single);
	}

	public View getDualToggleButton() {
		return findViewById(R.id.tv_dual_view_toggle);
	}

	public void toggleDualLayout() {
		dualStacked = !dualStacked;
		applyDualLayout();
		setMode(getMode());
	}

	public void setDualLayoutStacked(boolean stacked) {
		if (dualStacked == stacked) return;
		dualStacked = stacked;
		if (dualLayoutApplied) applyDualLayout();
	}

	private SwipeRefreshLayout getSwipeRefresh() {
		return findViewById(R.id.swiperefresh);
	}

	private static boolean isDualMode(Mode mode) {
		return (mode == Mode.DUAL_SELECT) || (mode == Mode.DUAL);
	}

	private void restoreCurrentLayout() {
		var ctx = getContext();
		var cs = new ConstraintSet();
		var layout = new ConstraintLayout(ctx);
		inflate(ctx, getLayout(isPortrait()), layout);
		cs.clone(layout);
		cs.applyTo(this);
		getDualVideoView().setMediaSessionSurface(false);
		dualLayoutApplied = false;
	}

	private void applyDualLayout() {
		boolean stacked = dualStacked;
		dualLayoutApplied = true;

		Guideline gl = getGuideline();
		ConstraintLayout.LayoutParams glp = (ConstraintLayout.LayoutParams) gl.getLayoutParams();
		glp.orientation = stacked ? ConstraintLayout.LayoutParams.HORIZONTAL :
				ConstraintLayout.LayoutParams.VERTICAL;
		glp.guidePercent = 0.5f;
		gl.setLayoutParams(glp);

		int video = R.id.video_view;
		int dual = R.id.dual_video_view;
		int list = R.id.swiperefresh;
		int guide = R.id.guideline;
		int split = R.id.split_line;
		int handle = R.id.split_handle;
		var cs = new ConstraintSet();
		cs.clone(this);
		clearDualConstraints(cs, video);
		clearDualConstraints(cs, dual);
		clearDualConstraints(cs, list);
		clearDualConstraints(cs, split);
		clearDualConstraints(cs, handle);

		if (stacked) {
			connectTopPane(cs, video, guide);
			connectBottomPane(cs, dual, guide);
			connectBottomPane(cs, list, guide);
			connectHorizontalSplit(cs, split, guide);
		} else {
			connectStartPane(cs, video, guide);
			connectEndPane(cs, dual, guide);
			connectEndPane(cs, list, guide);
			connectVerticalSplit(cs, split, guide);
		}

		cs.connect(handle, ConstraintSet.START, guide, ConstraintSet.START);
		cs.connect(handle, ConstraintSet.END, guide, ConstraintSet.END);
		cs.connect(handle, ConstraintSet.TOP, guide, ConstraintSet.TOP);
		cs.connect(handle, ConstraintSet.BOTTOM, guide, ConstraintSet.BOTTOM);
		cs.applyTo(this);

		View line = getSplitLine();
		ViewGroup.LayoutParams lp = line.getLayoutParams();
		lp.width = stacked ? ViewGroup.LayoutParams.MATCH_PARENT : 1;
		lp.height = stacked ? 1 : ViewGroup.LayoutParams.MATCH_PARENT;
		line.setLayoutParams(lp);
		getSplitHandle().setImageResource(stacked ? R.drawable.horizontal_split : R.drawable.vertical_split);
	}

	private static void clearDualConstraints(ConstraintSet cs, int id) {
		cs.clear(id, ConstraintSet.START);
		cs.clear(id, ConstraintSet.END);
		cs.clear(id, ConstraintSet.TOP);
		cs.clear(id, ConstraintSet.BOTTOM);
	}

	private static void connectTopPane(ConstraintSet cs, int id, int guide) {
		cs.connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
		cs.connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
		cs.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
		cs.connect(id, ConstraintSet.BOTTOM, guide, ConstraintSet.TOP);
	}

	private static void connectBottomPane(ConstraintSet cs, int id, int guide) {
		cs.connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
		cs.connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
		cs.connect(id, ConstraintSet.TOP, guide, ConstraintSet.BOTTOM);
		cs.connect(id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
	}

	private static void connectStartPane(ConstraintSet cs, int id, int guide) {
		cs.connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
		cs.connect(id, ConstraintSet.END, guide, ConstraintSet.START);
		cs.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
		cs.connect(id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
	}

	private static void connectEndPane(ConstraintSet cs, int id, int guide) {
		cs.connect(id, ConstraintSet.START, guide, ConstraintSet.END);
		cs.connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
		cs.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
		cs.connect(id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
	}

	private static void connectHorizontalSplit(ConstraintSet cs, int id, int guide) {
		cs.connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
		cs.connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
		cs.connect(id, ConstraintSet.TOP, guide, ConstraintSet.TOP);
		cs.connect(id, ConstraintSet.BOTTOM, guide, ConstraintSet.BOTTOM);
	}

	private static void connectVerticalSplit(ConstraintSet cs, int id, int guide) {
		cs.connect(id, ConstraintSet.START, guide, ConstraintSet.START);
		cs.connect(id, ConstraintSet.END, guide, ConstraintSet.END);
		cs.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
		cs.connect(id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		getDualVideoView().setMediaSessionSurface(false);
		dualLayoutApplied = false;
		setMode(getMode());
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) {
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			b.removeBroadcastListener(this);
			b.getMediaSessionCallback().removeBroadcastListener(this);
		} else if (e == FRAGMENT_CHANGED) {
			if (a.getActiveMediaLibFragment() == null) {
				setMode(Mode.FRAME);
			} else {
				if (isDualMode()) return;
				MediaSessionCallback cb = a.getMediaSessionCallback();
				MediaEngine eng = cb.getEngine();
				MediaLib.PlayableItem i = (eng != null) ? eng.getSource() : cb.getCurrentItem();

				if (isRadioFragmentActive(a) && isRadioItem(i)) {
					setMode(Mode.RADIO);
					getRadioPlayerView().bind(i);
					return;
				}

				if (eng == null) {
					setMode(Mode.FRAME);
					return;
				}

				if ((i != null) && i.isVideo() && eng.isSplitModeSupported() &&
						(cb.getVideoView() == getVideoView())) {
					setMode(Mode.BOTH);
				} else {
					setMode(Mode.FRAME);
				}
			}
		}
	}

	public void playItem(MediaLib.PlayableItem i) {
		cancelStartingPlayback();
		MainActivityDelegate a = getActivity();

		if (i.isVideo()) {
			if (!getVideoView().isSurfaceCreated() &&
					!a.getMediaSessionCallback().hasCustomEngineProvider()) {
				setMode(BodyLayout.Mode.VIDEO);
				setVideoLoadingVisible(true);
				getVideoView().onSurfaceCreated(() -> playItem(i));
				return;
			}
			if (!isVideoMode()) setMode(BodyLayout.Mode.VIDEO);
		}

		FermataServiceUiBinder b = a.getMediaServiceBinder();
		MediaLib.PlayableItem cur = b.getCurrentItem();
		beginStartingPlayback();
		b.playItem(i);
		MediaEngine eng = b.getCurrentEngine();
		if (i.equals(cur) && (eng != null)) completeStartingPlayback();
		if (i.equals(cur) && (eng != null) && eng.isVideoModeRequired())
			setMode(BodyLayout.Mode.VIDEO);
	}

	private void beginStartingPlayback() {
		cancelStartingPlayback();
		Promise<Void> p = new Promise<>();
		startingPlaybackPromise = p;
		startingPlayback = p;
		startingPlaybackAt = android.os.SystemClock.elapsedRealtime();
		// Only the centered VideoView hourglass — not the activity ContentLoadingProgressBar.
		setVideoLoadingVisible(true);
		p.onCompletion((r, err) -> {
			if (startingPlaybackPromise == p) {
				startingPlaybackPromise = null;
				startingPlayback = completedVoid();
			}
			setVideoLoadingVisible(false);
			cancelPositionWatch();
		});
		startPositionWatch();
	}

	private void completeStartingPlayback() {
		Promise<Void> p = startingPlaybackPromise;
		if (p != null && !p.isDone()) p.complete(null);
		else {
			setVideoLoadingVisible(false);
			cancelPositionWatch();
		}
	}

	private void cancelStartingPlayback() {
		cancelPositionWatch();
		Promise<Void> p = startingPlaybackPromise;
		if (p != null && !p.isDone()) p.cancel();
		else {
			startingPlayback.cancel();
			startingPlayback = completedVoid();
			setVideoLoadingVisible(false);
		}
	}

	private void setVideoLoadingVisible(boolean visible) {
		try {
			getVideoView().setLoadingVisible(visible);
			getDualVideoView().setLoadingVisible(visible && isDualMode());
		} catch (Throwable ignore) {
		}
	}

	private void startPositionWatch() {
		Object stamp = new Object();
		positionWatchStamp = stamp;
		App.get().getHandler().postDelayed(() -> watchPlaybackPosition(stamp), 200);
	}

	private void cancelPositionWatch() {
		positionWatchStamp = null;
	}

	private void watchPlaybackPosition(Object stamp) {
		if (positionWatchStamp != stamp || startingPlayback.isDone()) return;
		FermataServiceUiBinder binder = getActivity().getMediaServiceBinder();
		MediaEngine eng = binder.getCurrentEngine();
		MediaLib.PlayableItem item = binder.getCurrentItem();
		if (eng == null) {
			App.get().getHandler().postDelayed(() -> watchPlaybackPosition(stamp), 250);
			return;
		}
		PlaybackStateCompat st = binder.getMediaSessionCallback().getPlaybackState();
		int state = (st == null) ? STATE_NONE : st.getState();

		// Live / non-seekable: video is on screen at STATE_PLAYING even when clock stays at 0.
		if (isLiveLike(item) && (state == STATE_PLAYING)) {
			completeStartingPlayback();
			return;
		}

		eng.getPosition().main().onSuccess(pos -> {
			if (positionWatchStamp != stamp || startingPlayback.isDone()) return;
			long p = (pos != null) ? pos : 0L;
			if (p > 400L) {
				completeStartingPlayback();
				return;
			}
			if (st != null && st.getPosition() > 400L) {
				completeStartingPlayback();
				return;
			}
			// Live-like fallback if PLAYING was missed: don't sit on the hourglass for seconds.
			long waited = android.os.SystemClock.elapsedRealtime() - startingPlaybackAt;
			if (isLiveLike(item) && (state == STATE_PLAYING || state == STATE_BUFFERING)
					&& waited > 600L) {
				if (state == STATE_PLAYING) {
					completeStartingPlayback();
					return;
				}
			}
			// VOD stuck-at-zero safety net
			if (!isLiveLike(item) && waited > 8000L && (state == STATE_PLAYING)) {
				completeStartingPlayback();
				return;
			}
			App.get().getHandler().postDelayed(() -> watchPlaybackPosition(stamp), 200);
		}).onFailure(err -> {
			if (positionWatchStamp != stamp || startingPlayback.isDone()) return;
			App.get().getHandler().postDelayed(() -> watchPlaybackPosition(stamp), 300);
		});
	}

	/** Live TV / radio-style sources: position often stays 0 while frames are already showing. */
	private static boolean isLiveLike(@Nullable MediaLib.PlayableItem i) {
		if (i == null) return false;
		if (i.isStream()) return true;
		try {
			return !i.isSeekable();
		} catch (Throwable ignore) {
			return false;
		}
	}

	@Override
	public void onPlaybackStateChanged(MediaSessionCallback cb, PlaybackStateCompat state) {
		if (startingPlayback.isDone() || state == null) return;
		int st = state.getState();
		if ((st == STATE_ERROR) || (st == STATE_STOPPED) || (st == STATE_NONE)) {
			cancelStartingPlayback();
			return;
		}
		MediaLib.PlayableItem cur = cb.getCurrentItem();
		// Live channels: hide as soon as playback starts (do not wait for position > 0).
		if (isLiveLike(cur) && (st == STATE_PLAYING)) {
			completeStartingPlayback();
			return;
		}
		// VOD / Stremio: hide once the clock advances.
		if (state.getPosition() > 400L) {
			completeStartingPlayback();
			return;
		}
		if ((st == STATE_PLAYING) || (st == STATE_BUFFERING)) {
			startPositionWatch();
		}
	}

	@Override
	public void onPlayableChanged(MediaLib.PlayableItem oldItem, MediaLib.PlayableItem newItem) {
		// Keep the loading spinner until playback actually starts (see onPlaybackStateChanged).
		// Clear immediately when playback stops / item cleared so Stremio leftovers don't stick.
		if (newItem == null) cancelStartingPlayback();
		MainActivityDelegate a = getActivity();
		if (isDualMode()) {
			MediaEngine eng = a.getMediaServiceBinder().getCurrentEngine();
			if ((newItem == null) || !newItem.isVideo() || (eng == null) || !eng.isSplitModeSupported()) {
				setMode(Mode.FRAME);
			} else {
				getVideoView().showVideo(false);
			}
			return;
		}
		if (!(a.getActiveFragment() instanceof MainActivityFragment f)) return;
		if (f instanceof SubtitlesFragment) a.goToCurrent();
		else if (!f.isVideoModeSupported()) return;
		MediaEngine eng = a.getMediaServiceBinder().getCurrentEngine();

		if (isRadioFragmentActive(a) && isRadioItem(newItem)) {
			setMode(Mode.RADIO);
			getRadioPlayerView().bind(newItem);
			return;
		}

		if (isRadioMode()) setMode(Mode.FRAME);

		if ((newItem == null) || !newItem.isVideo() || (eng == null) || !eng.isSplitModeSupported()) {
			setMode(Mode.FRAME);
		} else {
			if (!eng.isVideoModeRequired()) setMode(Mode.FRAME);
			else if (isFrameMode()) setMode(Mode.VIDEO);
			else getVideoView().showVideo(false);
		}

		if ((eng != null) && (newItem != null) && !newItem.isVideo() && (getMode() == Mode.FRAME)) {
			eng.selectSubtitleStream();
		}
	}

	@Override
	public void onSubtitleStreamChanged(MediaSessionCallback cb, @Nullable SubtitleStreamInfo info) {
		if (getMode() != Mode.FRAME) return;
		var i = cb.getCurrentItem();
		if ((i == null) || i.isVideo()) return;
		var a = getActivity();
		var f = a.getActiveFragment();
		if (info == null) {
			if (f instanceof SubtitlesFragment) a.goToCurrent();
		} else if (f instanceof SubtitlesFragment) {
			((SubtitlesFragment) f).restart();
		} else {
			a.showFragment(subtitles_fragment);
		}
	}

	@Override
	public void onPlaybackError(String message) {
		onPlaybackStopped();
		Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
	}

	@Override
	public void onPlaybackStopped() {
		cancelStartingPlayback();
		var a = getActivity();
		if (a.getActiveFragment() instanceof SubtitlesFragment) a.goToCurrent();
	}

	@Override
	public void onRefresh() {
		ActivityFragment f = getActivity().getActiveFragment();
		if (f != null) f.onRefresh(getSwipeRefresh()::setRefreshing);
	}

	@Override
	public boolean canChildScrollUp(@NonNull SwipeRefreshLayout parent, @Nullable View child) {
		MainActivityDelegate a = getActivity();
		if (a.isMenuActive()) return true;
		ActivityFragment f = a.getActiveFragment();
		return (f != null) && f.canScrollUp();
	}

	public enum Mode {
		FRAME, RADIO, VIDEO, BOTH, DUAL_SELECT, DUAL
	}

	private static boolean isRadioFragmentActive(MainActivityDelegate a) {
		ActivityFragment f = a.getActiveFragment();
		return (f != null) && (f.getFragmentId() == R.id.radio_fragment);
	}

	private static boolean isRadioItem(@Nullable MediaLib.PlayableItem item) {
		if (item == null) return false;
		if (item instanceof M3uTrackItem t) return "radiom3ut".equals(t.getScheme());
		return item.getId().startsWith("radiom3ut:");
	}
}
