package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;

public class VideoGalleryFragment extends DeviceGalleryFragment {
	@Override public int getFragmentId() { return R.id.video_gallery_fragment; }

	@Override protected MediaScope getMediaScope() { return MediaScope.VIDEOS; }

	@Override protected int getAllMediaTitleRes() { return R.string.gallery_all_videos; }
}
