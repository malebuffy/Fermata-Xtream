package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.FermataMediaService;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.MediaSessionCallback;

/**
 * @author Andrey Pavlenko
 */
public interface FermataMediaServiceAddon extends FermataAddon {

	default void onServiceCreate(MediaSessionCallback cb) {
	}

	default void onServiceDestroy(MediaSessionCallback cb) {
	}
}
