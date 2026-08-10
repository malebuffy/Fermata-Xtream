package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u;

import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.util.Utils.dynCtx;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.content.Context;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.RadioItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.RadioRootItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.M3uGroupItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.M3uItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;

public class RadioM3uGroupItem extends M3uGroupItem implements RadioItem {
	public static final String SCHEME = "radiom3ug";

	protected RadioM3uGroupItem(String id, M3uItem parent, String name, int groupId) {
		super(id, parent, name, groupId);
	}

	public static FutureSupplier<RadioM3uGroupItem> create(RadioRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int gstart = id.indexOf(':') + 1;
		int gend = id.indexOf(':', gstart);
		int gid = Integer.parseInt(id.substring(gstart, gend));
		int nstart = id.indexOf(':', gend + 1);
		SharedTextBuilder tb = SharedTextBuilder.get().append(RadioM3uItem.SCHEME);
		String name;
		if (nstart > 0) {
			name = id.substring(nstart + 1);
			tb.append(id, gend, nstart);
		} else {
			name = null;
			tb.append(id, gend, id.length());
		}
		FutureSupplier<? extends Item> f = root.getItem(RadioM3uItem.SCHEME, tb.releaseString());
		if (f == null) return completedNull();
		return f.then(i -> {
			RadioM3uItem m3u = (RadioM3uItem) i;
			return (m3u != null) ? m3u.getGroup(gid, name) : completedNull();
		}).cast();
	}

	@Override
	public int getIcon() {
		return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.radio;
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		Context ctx = dynCtx(getLib().getContext());
		return completed(ctx.getResources().getString(R.string.sub_st, tracks.size()));
	}
}
