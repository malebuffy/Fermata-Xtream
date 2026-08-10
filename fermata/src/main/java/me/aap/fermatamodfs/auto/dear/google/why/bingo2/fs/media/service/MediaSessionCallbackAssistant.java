package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service;

import androidx.annotation.NonNull;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public interface MediaSessionCallbackAssistant {

	default void startVoiceAssistant(){}

	@NonNull
	default FutureSupplier<MediaLib.PlayableItem> getPrevPlayable(Item i) {
		return i.getPrevPlayable();
	}

	@NonNull
	default FutureSupplier<MediaLib.PlayableItem> getNextPlayable(Item i) {
		return i.getNextPlayable();
	}
}
