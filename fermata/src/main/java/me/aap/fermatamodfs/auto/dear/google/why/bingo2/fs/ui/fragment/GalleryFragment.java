package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;

public class GalleryFragment extends DeviceGalleryFragment {
	@Override public int getFragmentId() { return R.id.gallery_fragment; }

	@Override protected MediaScope getMediaScope() { return MediaScope.PHOTOS; }

	@Override protected int getAllMediaTitleRes() { return R.string.gallery_all_photos; }
}
