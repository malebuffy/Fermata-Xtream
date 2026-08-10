package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.BrowsableItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Item;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.PlayableItem;
import me.aap.utils.app.App;
import me.aap.utils.text.TextUtils;

/** Persists the 10 most recently played Movies/Series tracks per Xtream source. */
public final class TvPlayHistory {
	private static final String PREFS = "tv_play_history";
	private static final int MAX = 10;

	private TvPlayHistory() {
	}

	public static void record(@Nullable PlayableItem item) {
		if (item == null) return;
		String trackId = normalizeTrackId(item.getId());
		if (trackId == null) return;
		TvM3uItem source = findSource(item);
		if (source == null) return;
		int type = source.getResource().getXtreamContentType();
		if (type != TvM3uFile.XSTREAM_TYPE_MOVIES && type != TvM3uFile.XSTREAM_TYPE_SERIES) return;

		String key = keyFor(source.getId());
		SharedPreferences prefs = prefs();
		LinkedHashSet<String> ordered = new LinkedHashSet<>();
		ordered.add(trackId);
		for (String existing : split(prefs.getString(key, ""))) {
			if (!trackId.equals(existing)) ordered.add(existing);
			if (ordered.size() >= MAX) break;
		}
		prefs.edit().putString(key, String.join("\n", ordered)).apply();
	}

	@NonNull
	public static List<String> idsFor(@NonNull TvM3uItem source) {
		return split(prefs().getString(keyFor(source.getId()), ""));
	}

	@Nullable
	private static String normalizeTrackId(@Nullable String id) {
		if (id == null || !id.startsWith(TvM3uTrackItem.SCHEME)) return null;
		int cut = id.indexOf(":xte:");
		if (cut > 0) id = id.substring(0, cut);
		cut = id.indexOf(":xts-");
		if (cut > 0) id = id.substring(0, cut);
		return id;
	}

	@Nullable
	private static TvM3uItem findSource(@NonNull Item item) {
		BrowsableItem p = item.getParent();
		while (p != null) {
			if (p instanceof TvM3uItem) return (TvM3uItem) p;
			p = p.getParent();
		}
		return null;
	}

	@NonNull
	private static String keyFor(@NonNull String sourceId) {
		return "recent:" + sourceId;
	}

	@NonNull
	private static List<String> split(@Nullable String raw) {
		List<String> out = new ArrayList<>();
		if (TextUtils.isNullOrBlank(raw)) return out;
		for (String line : raw.split("\\n")) {
			String t = line.trim();
			if (!t.isEmpty()) out.add(t);
		}
		return out;
	}

	@NonNull
	private static SharedPreferences prefs() {
		Context ctx = App.get();
		return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}
}
