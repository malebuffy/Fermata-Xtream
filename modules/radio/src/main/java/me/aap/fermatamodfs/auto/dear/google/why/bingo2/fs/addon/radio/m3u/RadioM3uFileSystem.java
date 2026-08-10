package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u;

import androidx.annotation.NonNull;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u.M3uFileSystem;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.resource.Rid;

public class RadioM3uFileSystem extends M3uFileSystem {
	public static final String SCHEME_RADIOM3U = "radiom3u";
	private static final RadioM3uFileSystem instance = new RadioM3uFileSystem();

	public static RadioM3uFileSystem getInstance() {
		return instance;
	}

	@Override
	public String getScheme() {
		return SCHEME_RADIOM3U;
	}

	@NonNull
	@Override
	public FutureSupplier<RadioM3uFile> getResource(Rid rid) {
		return Completed.completed(createM3uFile(rid));
	}

	public FutureSupplier<RadioM3uFile> reload(RadioM3uFile file) {
		return load(file).cast();
	}

	public RadioM3uFile createNewFile() {
		return (RadioM3uFile) newFile();
	}

	protected RadioM3uFile createM3uFile(Rid rid) {
		return new RadioM3uFile(rid);
	}
}
