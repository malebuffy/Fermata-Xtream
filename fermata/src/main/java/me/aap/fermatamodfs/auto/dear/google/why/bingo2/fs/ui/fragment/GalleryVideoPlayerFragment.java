package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.IntentPlayable;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;

public class GalleryVideoPlayerFragment extends MainActivityFragment {
	private Uri uri;
	private VideoView video;

	@Override public int getFragmentId() { return R.id.gallery_video_player_fragment; }
	@Override public CharSequence getTitle() { return getString(R.string.video_player); }

	@Nullable @Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle state) {
		FrameLayout root = new FrameLayout(requireContext());
		root.setBackgroundColor(Color.BLACK);
		video = new VideoView(requireContext());
		MediaController controls = new MediaController(requireContext());
		controls.setAnchorView(video);
		video.setMediaController(controls);
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
		root.addView(video, params);
		if (uri != null) play(uri);
		return root;
	}

	@Override public void setInput(Object input) {
		if (input instanceof Uri value) {
			uri = value;
			getActivityDelegate().getMediaServiceBinder().playItem(new IntentPlayable(getActivityDelegate(), value));
		}
	}

	@Override public void onPause() {
		if (video != null) video.pause();
		super.onPause();
	}

	private void play(Uri value) {
		video.setVideoURI(value);
		video.setOnPreparedListener(player -> video.start());
	}
}
