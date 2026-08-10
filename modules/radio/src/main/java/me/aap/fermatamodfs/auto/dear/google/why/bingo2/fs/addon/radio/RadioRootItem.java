package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u.RadioM3uFile;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u.RadioM3uFileSystem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u.RadioM3uFileSystemProvider;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u.RadioM3uGroupItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u.RadioM3uItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u.RadioM3uTrackItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.DefaultMediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.ItemContainer;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Item;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u.BundledM3u;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

import static me.aap.utils.async.Async.forEach;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.CollectionUtils.contains;

public class RadioRootItem extends ItemContainer<RadioM3uItem> implements RadioItem {
	public static final String ID = "RADIO";
	private static final Pref<BooleanSupplier> BUILTIN_READY =
			Pref.b("RADIO_BUILTIN_READY", false).withInheritance(false);
	private static final Pref<IntSupplier> BUILTIN_SOURCE_ID =
			Pref.i("RADIO_BUILTIN_SOURCE_ID", 0).withInheritance(false);
	private static final Pref<IntSupplier> SOURCE_COUNTER = Pref.i("SOURCE_COUNTER", 0).withInheritance(false);
	private static final Pref<Supplier<int[]>> SOURCE_IDS = Pref.ia("SOURCE_IDS", () -> new int[0]).withInheritance(false);
	static final Pref<Supplier<String>> LAST_STATION = Pref.s("RADIO_LAST_STATION", (String) null).withInheritance(false);
	static final Pref<Supplier<String>> LAST_FOLDER = Pref.s("RADIO_LAST_FOLDER", (String) null).withInheritance(false);
	private final DefaultMediaLib lib;

	public RadioRootItem(DefaultMediaLib lib) {
		super(ID, null, null);
		this.lib = lib;
	}

	@Nullable
	public FutureSupplier<? extends Item> getItem(@Nullable String scheme, String id) {
		ensureBuiltinSource();
		if (scheme == null) return ID.equals(id) ? completed(this) : null;
		switch (scheme) {
			case RadioM3uItem.SCHEME:
				return create(toSourceId(id));
			case RadioM3uGroupItem.SCHEME:
				return RadioM3uGroupItem.create(this, id);
			case RadioM3uTrackItem.SCHEME:
				return RadioM3uTrackItem.create(this, id);
			default:
				return null;
		}
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(
				me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.addon_name_radio));
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return lib;
	}

	@Override
	public MediaLib.BrowsableItem getParent() {
		return null;
	}

	@NonNull
	@Override
	public PreferenceStore getParentPreferenceStore() {
		return getLib();
	}

	@NonNull
	@Override
	public MediaLib.BrowsableItem getRoot() {
		return this;
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	public boolean getTitleSeqNumPref() {
		return false;
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		ensureBuiltinSource();
		int[] ids = getIntArrayPref(SOURCE_IDS);
		List<Integer> idList = new ArrayList<>(ids.length);
		for (int i : ids) idList.add(i);
		List<Item> children = new ArrayList<>(ids.length);
		return forEach(id -> {
			FutureSupplier<RadioM3uItem> f = create(id);
			if (f != null) return f.onSuccess(i -> {
				if (i != null) children.add(i);
			});
			return completedVoid();
		}, idList).map(v -> children);
	}

	@Override
	protected String getScheme() {
		return RadioM3uItem.SCHEME;
	}

	@Override
	protected void saveChildren(List<RadioM3uItem> children) {
		applyIntArrayPref(SOURCE_IDS, CollectionUtils.map(children,
				(i, t, a) -> a[i] = toSourceId(t.getId()), int[]::new));
	}

	@Override
	public boolean isChildItemId(String id) {
		return id.startsWith(RadioM3uTrackItem.SCHEME)
				|| id.startsWith(RadioM3uGroupItem.SCHEME)
				|| id.startsWith(RadioM3uItem.SCHEME);
	}

	public int addSource(RadioM3uFile m3u) {
		int counter = getIntPref(SOURCE_COUNTER) + 1;
		Pref<Supplier<String>> id = Pref.s("M3UID#" + counter);
		int[] sourceIds = getIntArrayPref(SOURCE_IDS);
		try (PreferenceStore.Edit e = editPreferenceStore()) {
			e.setIntPref(SOURCE_COUNTER, counter);
			e.setStringPref(id, RadioM3uFileSystem.getInstance().toId(m3u.getRid()));
			if (!contains(sourceIds, counter)) {
				int[] updated = new int[sourceIds.length + 1];
				System.arraycopy(sourceIds, 0, updated, 0, sourceIds.length);
				updated[sourceIds.length] = counter;
				e.setIntArrayPref(SOURCE_IDS, updated);
			}
		}
		addItem(RadioM3uItem.create(this, m3u, counter));
		return counter;
	}

	private synchronized void ensureBuiltinSource() {
		String path = BundledM3u.ensureLocal(getLib().getContext(), "all_stations.m3u", "all_stations.m3u");
		if (path == null) return;
		int builtinId = getIntPref(BUILTIN_SOURCE_ID);
		if (getBooleanPref(BUILTIN_READY)) {
			int[] ids = getIntArrayPref(SOURCE_IDS);
			if ((builtinId == 0) ? (ids.length > 0) : contains(ids, builtinId)) return;
		}
		RadioM3uFile file = RadioM3uFileSystem.getInstance().createNewFile();
		file.setName(getLib().getContext().getString(R.string.radio_stations));
		file.setUrl(path);
		file.setVideo(false);
		int sourceId = addSource(file);
		try (PreferenceStore.Edit e = editPreferenceStore()) {
			e.setBooleanPref(BUILTIN_READY, true);
			e.setIntPref(BUILTIN_SOURCE_ID, sourceId);
		}
	}

	@Override
	protected void itemRemoved(RadioM3uItem i) {
		super.itemRemoved(i);
		RadioM3uFileSystemProvider.removeSource(i.getResource());
	}

	private FutureSupplier<RadioM3uItem> create(int srcId) {
		ensureBuiltinSource();
		if (!contains(getIntArrayPref(SOURCE_IDS), srcId)) return null;
		String m3uId = getStringPref(Pref.s("M3UID#" + srcId));
		if (m3uId == null) return null;
		return RadioM3uItem.create(this, srcId, m3uId).onFailure(err -> {
			Log.e(err, "Failed to load radio source: ", m3uId);
			if (err instanceof MalformedURLException) removeSource(srcId);
		}).ifNull(() -> {
			Log.e("Failed to load radio source: ", m3uId);
			removeSource(srcId);
			return null;
		});
	}

	private void removeSource(int srcId) {
		int[] ids = getIntArrayPref(SOURCE_IDS);
		if (ids.length == 0) return;
		int[] newIds = new int[ids.length - 1];
		boolean removed = false;
		for (int i = 0, j = 0; i < ids.length; i++) {
			if (ids[i] == srcId) removed = true;
			else if (j < newIds.length) newIds[j++] = ids[i];
			else return;
		}
		if (removed) {
			Log.i("Removing radio source: ", srcId);
			applyIntArrayPref(SOURCE_IDS, newIds);
		}
	}

	private int toSourceId(String id) {
		return Integer.parseInt(id.substring(8));
	}
}
