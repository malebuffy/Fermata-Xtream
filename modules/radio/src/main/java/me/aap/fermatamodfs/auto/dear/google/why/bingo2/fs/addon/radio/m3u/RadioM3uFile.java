package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u;

import androidx.annotation.NonNull;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u.M3uFile;
import me.aap.utils.resource.Rid;

public class RadioM3uFile extends M3uFile {

	public RadioM3uFile(Rid rid) {
		super(rid);
	}

	@NonNull
	@Override
	public RadioM3uFileSystem getVirtualFileSystem() {
		return RadioM3uFileSystem.getInstance();
	}
}
