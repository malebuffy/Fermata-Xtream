package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u;

import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.util.Utils.dynCtx;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.content.Context;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.TvItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.TvRootItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.M3uGroupItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.M3uItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Item;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.M3uTrackItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uGroupItem extends M3uGroupItem implements TvItem {
	public static final String SCHEME = "tvm3ug";

	protected TvM3uGroupItem(String id, M3uItem parent, String name, int groupId) {
		super(id, parent, name, groupId);
	}

	public static FutureSupplier<TvM3uGroupItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int gstart = id.indexOf(':') + 1;
		int gend = id.indexOf(':', gstart);
		int gid = Integer.parseInt(id.substring(gstart, gend));
		int nstart = id.indexOf(':', gend + 1);
		SharedTextBuilder tb = SharedTextBuilder.get().append(TvM3uItem.SCHEME);
		String name;

		if (nstart > 0) {
			name = id.substring(nstart + 1);
			tb.append(id, gend, nstart);
		} else {
			name = null;
			tb.append(id, gend, id.length());
		}

		FutureSupplier<? extends Item> f = root.getItem(TvM3uItem.SCHEME, tb.releaseString());
		return (f == null) ? completedNull() : f.then(i -> {
			TvM3uItem m3u = (TvM3uItem) i;
			return (m3u != null) ? m3u.getGroup(gid, name) : completedNull();
		});
	}

	@Override
	public int getIcon() {
		return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.tv;
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		Context ctx = dynCtx(getLib().getContext());
		String t = ctx.getResources().getString(R.string.sub_ch, tracks.size());
		return completed(t);
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	protected FutureSupplier<List<Item>> listChildren() {
		TvM3uFile file = (TvM3uFile) getParent().getResource();
		List<Item> items = new ArrayList<>(tracks.size());
		for (M3uTrackItem t : tracks) {
			items.add(t);
		}
		// Keep A–Z as the historical default when no explicit sort is chosen; otherwise
		// leave order for BrowsableItemBase (Latest added / name / random / …).
		if (getPrefs().getSortByPref()
				== me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.pref.BrowsableItemPrefs.SORT_BY_NONE) {
			items.sort(Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER));
		}
		return completed(items);
	}

	@Override
	public int getSupportedSortOpts() {
		TvM3uFile file = (TvM3uFile) getParent().getResource();
		int type = file.getXtreamContentType();
		if (type == TvM3uFile.XSTREAM_TYPE_MOVIES || type == TvM3uFile.XSTREAM_TYPE_SERIES) {
			return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.pref.BrowsableItemPrefs.SORT_MASK_NAME_RND_ADDED;
		}
		return super.getSupportedSortOpts();
	}

	public List<String> getChannelNames() {
		Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (M3uTrackItem t : tracks) names.add(t.getName());
		return new ArrayList<>(names);
	}
}
