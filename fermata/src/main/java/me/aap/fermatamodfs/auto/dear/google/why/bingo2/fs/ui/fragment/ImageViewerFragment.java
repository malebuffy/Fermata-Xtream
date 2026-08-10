package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;

public class ImageViewerFragment extends MainActivityFragment {
	private Uri uri;
	private ImageView image;

	@Override public int getFragmentId() { return R.id.image_viewer_fragment; }
	@Override public CharSequence getTitle() { return getString(R.string.gallery); }

	@Nullable @Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle state) {
		image = new ImageView(requireContext());
		image.setBackgroundColor(Color.BLACK);
		image.setScaleType(ImageView.ScaleType.FIT_CENTER);
		if (uri != null) image.setImageURI(uri);
		return image;
	}

	@Override public void setInput(Object input) {
		if (input instanceof Uri) {
			uri = (Uri) input;
			if (image != null) image.setImageURI(uri);
		}
	}
}
