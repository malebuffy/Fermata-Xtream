package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.felex.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.felex.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.felex.dict.Dict;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.felex.dict.DictMgr;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.felex.dict.Translation;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.felex.dict.Word;
import me.aap.utils.ui.UiUtils;

public class FelexBar {
	private final FelexFragment fragment;
	private final LinearLayout bar;
	private View addButton;
	private View tutorButton;

	public FelexBar(FelexFragment fragment, LinearLayout bar) {
		this.fragment = fragment;
		this.bar = bar;
		build();
	}

	private void build() {
		bar.removeAllViews();
		bar.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

		addButton = addButton(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.playlist_add,
				FelexFragment.FelexActions::add, R.id.add);
		tutorButton = addButton(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.record_voice,
				FelexFragment.FelexActions::tutor, R.id.start_tutor);
		updateVisibility();
	}

	public void updateVisibility() {
		if (addButton == null) return;
		Object content = fragment.view().getContent();
		int add;
		int start;
		if (content instanceof Dict) {
			add = start = VISIBLE;
		} else if ((content instanceof DictMgr) || (content instanceof Word) ||
				(content instanceof Translation)) {
			add = VISIBLE;
			start = GONE;
		} else {
			add = start = GONE;
		}
		addButton.setVisibility(add);
		tutorButton.setVisibility(start);
	}

	private View addButton(int icon, View.OnClickListener onClick, int id) {
		ImageButton b = new ImageButton(bar.getContext(), null, androidx.appcompat.R.attr.toolbarStyle);
		b.setId(id);
		b.setImageResource(icon);
		b.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
		b.setBackgroundResource(me.aap.utils.R.drawable.focusable_shape_transparent);
		int pad = UiUtils.toIntPx(bar.getContext(), 6);
		b.setPadding(pad, pad, pad, pad);
		b.setOnClickListener(onClick);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				UiUtils.toIntPx(bar.getContext(), 48), MATCH_PARENT);
		bar.addView(b, lp);
		return b;
	}
}
