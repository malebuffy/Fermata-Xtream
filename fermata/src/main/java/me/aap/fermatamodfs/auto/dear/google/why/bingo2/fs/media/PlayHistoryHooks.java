package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.PlayableItem;

/** Optional listeners for recently-played bookkeeping (e.g. TV Movies/Series). */
public final class PlayHistoryHooks {
	public interface Recorder {
		void onPlayed(@Nullable PlayableItem item);
	}

	private static final List<Recorder> RECORDERS = new CopyOnWriteArrayList<>();

	private PlayHistoryHooks() {
	}

	public static void add(@Nullable Recorder recorder) {
		if (recorder != null) RECORDERS.add(recorder);
	}

	public static void remove(@Nullable Recorder recorder) {
		if (recorder != null) RECORDERS.remove(recorder);
	}

	public static void notifyPlayed(@Nullable PlayableItem item) {
		if (item == null || RECORDERS.isEmpty()) return;
		for (Recorder r : RECORDERS) {
			try {
				r.onPlayed(item);
			} catch (Throwable ignored) {
			}
		}
	}
}
