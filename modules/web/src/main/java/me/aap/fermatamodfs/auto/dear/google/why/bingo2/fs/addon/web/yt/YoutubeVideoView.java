package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.web.yt;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.VideoInfoView;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.VideoView;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeVideoView extends VideoView {

	public YoutubeVideoView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void init(Context context) {
		addView(new FrameLayout(context), new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		addInfoView(context);
	}

	@NonNull
	@Override
	public VideoInfoView getVideoInfoView() {
		return (VideoInfoView) getChildAt(1);
	}

	@Nullable
	@Override
	public SurfaceView getSubtitleSurface() {
		return null;
	}
}
