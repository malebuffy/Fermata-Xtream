package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u;

import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u.M3uFile.NAME;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u.M3uFile.URL;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.net.http.HttpFileDownloader.AGENT;

import java.util.List;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.BuildConfig;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u.M3uFile;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u.M3uFileSystemProvider;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.vfs.VirtualFileSystem;

public class RadioM3uFileSystemProvider extends M3uFileSystemProvider {

	@Override
	public FutureSupplier<? extends RadioM3uFile> select(MainActivityDelegate a,
			List<? extends VirtualFileSystem> fs) {
		PreferenceSet prefs = new PreferenceSet();
		PreferenceStore ps = PrefsHolder.instance;
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = NAME;
			o.title = me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.m3u_playlist_name;
		});
		prefs.addFilePref(o -> {
			o.store = ps;
			o.pref = URL;
			o.mode = FilePickerFragment.FILE;
			o.title = me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.m3u_playlist_location;
			o.maxLines = 3;
			o.stringHint = a.getString(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.m3u_playlist_location_hint);
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = AGENT;
			o.title = me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.m3u_playlist_agent;
			o.stringHint = "Fermata/" + BuildConfig.VERSION_NAME;
		});
		return requestPrefs(a, prefs, ps).thenRun(ps::removeBroadcastListeners).then(ok -> {
			if (!ok) return completedNull();
			return load(ps, RadioM3uFileSystem.getInstance());
		}).cast();
	}

	@Override
	protected void setPrefs(PreferenceStore ps, M3uFile f) {
		super.setPrefs(ps, f);
		f.setVideo(false);
	}

	public static void removeSource(RadioM3uFile f) {
		Log.d("Removing radio source ", f);
		f.delete();
	}

	private static final class PrefsHolder extends BasicPreferenceStore {
		static final PrefsHolder instance = new PrefsHolder();
	}
}
