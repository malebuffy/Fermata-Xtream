package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon;

import androidx.annotation.Nullable;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.DefaultMediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public interface MediaLibAddon extends FermataFragmentAddon {

	boolean isSupportedItem(Item i);

	Item getRootItem(DefaultMediaLib lib);

	default boolean isRootItemVisible() {
		return true;
	}

	@Nullable
	default Item getFoldersItem(DefaultMediaLib lib) {
		return null;
	}

	default boolean isFoldersItemId(String id) {
		return false;
	}

	@Nullable
	FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme, String id);
}
