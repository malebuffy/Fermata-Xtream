package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.TvItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.TvRootItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.BrowsableItemBase;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.DefaultMediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Item;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.pref.BrowsableItemPrefs;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;

/** Virtual folder listing the 10 latest played movies or series for one Xtream source. */
public final class TvRecentlyPlayedItem extends BrowsableItemBase implements TvItem {
	public static final String SCHEME = "tvm3ur";

	private TvRecentlyPlayedItem(String id, TvM3uItem source) {
		super(id, source, null);
	}

	@NonNull
	public static TvRecentlyPlayedItem create(@NonNull TvM3uItem source) {
		DefaultMediaLib lib = (DefaultMediaLib) source.getLib();
		String id = SharedTextBuilder.get().append(SCHEME).append(':')
				.append(source.getId().substring(TvM3uItem.SCHEME.length() + 1)).releaseString();
		synchronized (lib.cacheLock()) {
			Item cached = lib.getFromCache(id);
			if (cached instanceof TvRecentlyPlayedItem) return (TvRecentlyPlayedItem) cached;
			return new TvRecentlyPlayedItem(id, source);
		}
	}

	public static FutureSupplier<TvRecentlyPlayedItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		String sourceId = TvM3uItem.SCHEME + id.substring(SCHEME.length());
		return root.getItem(TvM3uItem.SCHEME, sourceId).then(i -> {
			if (!(i instanceof TvM3uItem)) return completedNull();
			return completed(create((TvM3uItem) i));
		});
	}

	@NonNull
	private TvM3uItem source() {
		return (TvM3uItem) getParent();
	}

	@Override
	public int getIcon() {
		return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.favorite;
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(R.string.tv_recently_played));
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@Override
	public int getSupportedSortOpts() {
		return BrowsableItemPrefs.SORT_BY_NONE;
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		List<String> ids = TvPlayHistory.idsFor(source());
		if (ids.isEmpty()) return completed(List.of());
		List<Item> out = new ArrayList<>(ids.size());
		return Async.forEach(id -> getLib().getItem(id).onSuccess(item -> {
			if (item != null) out.add(item);
		}), ids).map(v -> out);
	}

	@Override
	protected String getChildrenIdPattern() {
		return TvM3uTrackItem.SCHEME + ":.*";
	}
}
