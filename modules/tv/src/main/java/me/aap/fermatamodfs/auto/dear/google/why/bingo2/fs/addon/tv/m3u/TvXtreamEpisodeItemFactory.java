package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VirtualResource;

public final class TvXtreamEpisodeItemFactory {

	public static FutureSupplier<TvM3uTrackItem> create(TvM3uTrackItem baseTrack, String episodeUrl,
												 String episodeTitle) {
		if ((episodeUrl == null) || episodeUrl.isBlank()) return completedNull();

		return baseTrack.getLib().getVfsManager().resolve(episodeUrl, null).then(resource -> {
			if (resource == null) return completedNull();
			BrowsableItem parent = (BrowsableItem) baseTrack.getParent();
			Rid rid = resource.getRid();
			String id = baseTrack.getId() + ":xte:" + System.currentTimeMillis() + ':' + rid;
			TvM3uTrackItem item = new TvM3uTrackItem(id, parent, baseTrack.getTrackNumber(), resource,
					episodeTitle, null, null, null, null, null, null, 0,
					(byte) 2, TvM3uFile.CATCHUP_TYPE_AUTO, -1, null);
			return completed(item);
		});
	}

	private TvXtreamEpisodeItemFactory() {
	}
}
