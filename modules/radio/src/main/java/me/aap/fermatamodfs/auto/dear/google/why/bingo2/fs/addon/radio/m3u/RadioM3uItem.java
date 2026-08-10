package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u;

import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.util.Utils.dynCtx;
import static me.aap.utils.async.Completed.completed;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.BuildConfig;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.RadioItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.RadioRootItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.DefaultMediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.M3uGroupItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.M3uItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.M3uTrackItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.BrowsableItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.resource.Rid;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;

public class RadioM3uItem extends M3uItem implements RadioItem {
	public static final String SCHEME = "radiom3u";

	protected RadioM3uItem(String id, BrowsableItem parent, RadioM3uFile m3uFile) {
		super(id, parent, m3uFile);
	}

	public static RadioM3uItem create(RadioRootItem root, RadioM3uFile m3u, int srcId) {
		String id = SharedTextBuilder.get().append(SCHEME).append(':').append(srcId).releaseString();
		return create(root, m3u, id);
	}

	public static RadioM3uItem create(RadioRootItem root, RadioM3uFile m3u, String id) {
		DefaultMediaLib lib = root.getLib();
		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);
			if (i != null) {
				RadioM3uItem c = (RadioM3uItem) i;
				if (BuildConfig.D && !root.equals(c.getParent())) throw new AssertionError();
				if (BuildConfig.D && !m3u.equals(c.getResource())) throw new AssertionError();
				return c;
			}
			return new RadioM3uItem(id, root, m3u);
		}
	}

	public static FutureSupplier<RadioM3uItem> create(RadioRootItem root, int srcId, String m3uId) {
		DefaultMediaLib lib = root.getLib();
		String id = SharedTextBuilder.get().append(SCHEME).append(':').append(srcId).releaseString();
		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);
			if (i != null) return completed((RadioM3uItem) i);
		}
		RadioM3uFileSystem fs = RadioM3uFileSystem.getInstance();
		Rid rid = fs.toRid(m3uId);
		return fs.getResource(rid).map(m3u -> (m3u != null) ? create(root, m3u, id) : null);
	}

	@Override
	protected String getScheme() {
		return SCHEME;
	}

	@Override
	protected M3uGroupItem createGroup(String idPath, String name, int groupId) {
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(RadioM3uGroupItem.SCHEME).append(':').append(groupId).append(idPath).append(':').append(name);
		return new RadioM3uGroupItem(tb.releaseString(), this, name, groupId);
	}

	@Override
	protected M3uTrackItem createTrack(BrowsableItem parent, int groupNumber, int trackNumber,
			String idPath, VirtualResource file, String name, String album, String artist,
			String genre, String logo, String tvgId, String tvgName, long duration, byte type,
			String catchup, String catchupDays, String catchupSource) {
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(RadioM3uTrackItem.SCHEME).append(':').append(groupNumber).append(':')
				.append(trackNumber).append(idPath).append(':').append(file.getRid());
		byte audioType = (byte) (1 | 4);
		return new RadioM3uTrackItem(tb.releaseString(), parent, trackNumber, file, name, album, artist,
				genre, logo, tvgId, tvgName, duration, audioType);
	}

	@Override
	protected String createSubtitle(int gr, int ch) {
		Context ctx = dynCtx(getLib().getContext());
		Resources res = ctx.getResources();
		if (ch != 0) {
			if (gr == 0) return res.getString(R.string.sub_st, ch);
		} else if (gr != 0) {
			return res.getString(R.string.sub_gr, gr);
		}
		return res.getString(R.string.sub_st_gr, ch, gr);
	}

	@NonNull
	@Override
	public FutureSupplier<Void> refresh() {
		return RadioM3uFileSystem.getInstance().reload(getResource()).main().then(v -> super.refresh());
	}

	@Override
	public RadioM3uFile getResource() {
		return (RadioM3uFile) super.getResource();
	}
}
