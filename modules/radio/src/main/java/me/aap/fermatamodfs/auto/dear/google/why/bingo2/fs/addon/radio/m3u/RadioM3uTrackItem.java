package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.RadioItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.RadioRootItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.M3uTrackItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.BrowsableItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Item;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.StreamItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.pref.StreamItemPrefs;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u.M3uFile;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;

public class RadioM3uTrackItem extends M3uTrackItem implements StreamItem, StreamItemPrefs, RadioItem {
	public static final String SCHEME = "radiom3ut";

	protected RadioM3uTrackItem(String id, BrowsableItem parent, int trackNumber, VirtualResource file,
			String name, String album, String artist, String genre, String logo, String tvgId,
			String tvgName, long duration, byte type) {
		super(id, parent, trackNumber, file, name, album, artist, genre, logo, tvgId, tvgName, duration, type);
	}

	public static FutureSupplier<RadioM3uTrackItem> create(RadioRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int start = id.indexOf(':') + 1;
		int end = id.indexOf(':', start);
		int gid = Integer.parseInt(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		int tid = Integer.parseInt(id.substring(start, end));
		start = id.indexOf(':', end + 1);
		String uri = (start > 0) ? id.substring(start + 1) : null;
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(RadioM3uItem.SCHEME).append(id, end, (start > 0) ? start : id.length());
		FutureSupplier<? extends Item> f = root.getItem(RadioM3uItem.SCHEME, tb.releaseString());
		if (f == null) return completedNull();
		return f.then(i -> {
			RadioM3uItem m3u = (RadioM3uItem) i;
			return (m3u != null) ? m3u.getTrack(gid, tid, uri) : completedNull();
		}).cast();
	}

	@NonNull
	@Override
	public StreamItemPrefs getPrefs() {
		return this;
	}

	@Override
	public String getScheme() {
		return SCHEME;
	}

	@Override
	public int getIcon() {
		return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.radio;
	}

	@Override
	public boolean isVideo() {
		return false;
	}

	@NonNull
	@Override
	protected FutureSupplier<android.support.v4.media.MediaMetadataCompat> loadMeta() {
		return buildMeta(new me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine.MetadataBuilder());
	}

	@Nullable
	@Override
	public String getUserAgent() {
		VirtualResource r = getM3uItem().getResource();
		return (r instanceof M3uFile) ? ((M3uFile) r).getUserAgent() : null;
	}
}
