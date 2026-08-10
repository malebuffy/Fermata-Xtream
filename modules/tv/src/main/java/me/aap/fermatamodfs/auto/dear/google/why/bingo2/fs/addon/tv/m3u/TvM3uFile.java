package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u;

import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.io.FileUtils.getFileExtension;
import static me.aap.utils.net.http.HttpFileDownloader.CHARSET;
import static me.aap.utils.net.http.HttpFileDownloader.ENCODING;
import static me.aap.utils.net.http.HttpFileDownloader.ETAG;
import static me.aap.utils.net.http.HttpFileDownloader.MAX_AGE;
import static me.aap.utils.net.http.HttpFileDownloader.RESP_TIMEOUT;
import static me.aap.utils.net.http.HttpFileDownloader.TIMESTAMP;
import static me.aap.utils.text.TextUtils.trim;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.net.MalformedURLException;

import org.json.JSONArray;
import org.json.JSONObject;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u.M3uFile;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.db.SQLite;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpFileDownloader;
import me.aap.utils.net.http.HttpFileDownloader.Status;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.resource.Rid;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.notif.HttpDownloadStatusListener;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uFile extends M3uFile {
	public static final int EPG_FILE_AGE = M3U_FILE_AGE;
	public static final int CATCHUP_TYPE_AUTO = 0;
	public static final int CATCHUP_TYPE_APPEND = 1;
	public static final int CATCHUP_TYPE_DEFAULT = 2;
	public static final int CATCHUP_TYPE_SHIFT = 3;
	public static final int CATCHUP_TYPE_FLUSSONIC = 4;
	public static final Pref<Supplier<String>> EPG_URL = Pref.s("EPG_URL");
	public static final Pref<DoubleSupplier> EPG_SHIFT = Pref.f("EPG_SHIFT", 0);
	public static final Pref<Supplier<String>> CATCHUP_QUERY = Pref.s("CATCHUP_QUERY");
	public static final Pref<IntSupplier> CATCHUP_TYPE = Pref.i("CATCHUP_TYPE", CATCHUP_TYPE_AUTO);
	public static final Pref<IntSupplier> CATCHUP_DAYS = Pref.i("CATCHUP_DAYS", 0);
	public static final Pref<Supplier<String>> LOGO_URL = Pref.s("LOGO_URL");
	public static final Pref<BooleanSupplier> LOGO_PREFER_EPG = Pref.b("LOGO_PREFER_EPG", true);
	public static final Pref<BooleanSupplier> XSTREAM_ENABLED = Pref.b("XSTREAM_ENABLED", false);
	public static final Pref<Supplier<String>> XSTREAM_SERVER = Pref.s("XSTREAM_SERVER");
	public static final Pref<Supplier<String>> XSTREAM_USERNAME = Pref.s("XSTREAM_USERNAME");
	public static final Pref<Supplier<String>> XSTREAM_PASSWORD = Pref.s("XSTREAM_PASSWORD");
	public static final Pref<Supplier<String>> XSTREAM_TIMEZONE = Pref.s("XSTREAM_TIMEZONE");
	public static final int XSTREAM_TYPE_CHANNELS = 0;
	public static final int XSTREAM_TYPE_MOVIES = 1;
	public static final int XSTREAM_TYPE_SERIES = 2;
	public static final Pref<IntSupplier> XSTREAM_CONTENT_TYPE = Pref.i("XSTREAM_CONTENT_TYPE", XSTREAM_TYPE_CHANNELS);
	public static final Pref<BooleanSupplier> XSTREAM_INCLUDE_CHANNELS = Pref.b("XSTREAM_INCLUDE_CHANNELS", true);
	public static final Pref<BooleanSupplier> XSTREAM_INCLUDE_MOVIES = Pref.b("XSTREAM_INCLUDE_MOVIES", true);
	public static final Pref<BooleanSupplier> XSTREAM_INCLUDE_SERIES = Pref.b("XSTREAM_INCLUDE_SERIES", true);
	public static final Pref<BooleanSupplier> XSTREAM_ALL_AT_ONCE = Pref.b("XSTREAM_ALL_AT_ONCE", false);
	public TvM3uFile(Rid rid) {
		super(rid);
	}

	@NonNull
	@Override
	public TvM3uFileSystem getVirtualFileSystem() {
		return TvM3uFileSystem.getInstance();
	}

	/**
	 * Returns a persistent (non-cache) directory for storing Xtream playlist data.
	 * Unlike getCacheDir(), this directory is NOT subject to Android cache eviction.
	 */
	public File getXtreamDataDir() {
		App app = App.get();
		File filesDir = app.getExternalFilesDir(null);
		if (filesDir == null) filesDir = app.getFilesDir();
		File dir = new File(filesDir, getVirtualFileSystem().getScheme() + "/xtream");
		if (!dir.exists()) dir.mkdirs();
		return dir;
	}

	public String getXtreamStorageKey() {
		return XtreamPlaylistBuilder.storageKey(getXtreamServer(), getXtreamContentType());
	}

	@NonNull
	@Override
	public File getLocalFile() {
		File f = super.getLocalFile();
		if (f.exists() || !isXtreamEnabled()) return f;
		// Migrate: if URL points to old cache location, check persistent dir
		String url = getUrl();
		if (url != null && url.startsWith("/") && url.contains("/cache/")) {
			String fileName = f.getName();
			File persistentFile = new File(getXtreamDataDir(), fileName);
			if (persistentFile.exists()) {
				Log.d("Migrating Xtream URL from cache to persistent: ", persistentFile);
				setUrl(persistentFile.getAbsolutePath());
				return persistentFile;
			}
		}
		return f;
	}

	public String getEpgUrl() {
		return getPrefs().getStringPref(EPG_URL);
	}

	public void setEpgUrl(String url) {
		getPrefs().applyStringPref(EPG_URL, trim(url));
	}

	public float getEpgShift() {
		return getPrefs().getFloatPref(EPG_SHIFT);
	}

	public void setEpgShift(float shift) {
		getPrefs().applyFloatPref(EPG_SHIFT, shift);
	}

	public String getCatchupQuery() {
		return getPrefs().getStringPref(CATCHUP_QUERY);
	}

	public void setCatchupQuery(String q) {
		getPrefs().applyStringPref(CATCHUP_QUERY, trim(q));
	}

	public int getCatchupType() {
		return getPrefs().getIntPref(CATCHUP_TYPE);
	}

	public void setCatchupType(int type) {
		getPrefs().applyIntPref(CATCHUP_TYPE, type);
	}

	public int getCatchupDays() {
		return getPrefs().getIntPref(CATCHUP_DAYS);
	}

	public void setCatchupDays(int days) {
		getPrefs().applyIntPref(CATCHUP_DAYS, days);
	}

	public String getLogoUrl() {
		return getPrefs().getStringPref(LOGO_URL);
	}

	public void setLogoUrl(String url) {
		getPrefs().applyStringPref(LOGO_URL, trim(url));
	}

	public boolean isPreferEpgLogo() {
		return getPrefs().getBooleanPref(LOGO_PREFER_EPG);
	}

	public void setPreferEpgLogo(boolean prefer) {
		getPrefs().applyBooleanPref(LOGO_PREFER_EPG, prefer);
	}

	public boolean isXtreamEnabled() {
		return getPrefs().getBooleanPref(XSTREAM_ENABLED);
	}

	public void setXtreamEnabled(boolean enabled) {
		getPrefs().applyBooleanPref(XSTREAM_ENABLED, enabled);
	}

	public String getXtreamServer() {
		return getPrefs().getStringPref(XSTREAM_SERVER);
	}

	public void setXtreamServer(String server) {
		getPrefs().applyStringPref(XSTREAM_SERVER, trim(server));
	}

	public String getXtreamUsername() {
		return getPrefs().getStringPref(XSTREAM_USERNAME);
	}

	public void setXtreamUsername(String username) {
		getPrefs().applyStringPref(XSTREAM_USERNAME, trim(username));
	}

	public String getXtreamPassword() {
		return getPrefs().getStringPref(XSTREAM_PASSWORD);
	}

	public void setXtreamPassword(String password) {
		getPrefs().applyStringPref(XSTREAM_PASSWORD, trim(password));
	}

	public String getXtreamTimezone() {
		return getPrefs().getStringPref(XSTREAM_TIMEZONE);
	}

	public void setXtreamTimezone(String timezone) {
		getPrefs().applyStringPref(XSTREAM_TIMEZONE, trim(timezone));
	}

	public int getXtreamContentType() {
		return getPrefs().getIntPref(XSTREAM_CONTENT_TYPE);
	}

	public void setXtreamContentType(int type) {
		getPrefs().applyIntPref(XSTREAM_CONTENT_TYPE, type);
	}

	public boolean isXtreamIncludeChannels() {
		return getPrefs().getBooleanPref(XSTREAM_INCLUDE_CHANNELS);
	}

	public void setXtreamIncludeChannels(boolean include) {
		getPrefs().applyBooleanPref(XSTREAM_INCLUDE_CHANNELS, include);
	}

	public boolean isXtreamIncludeMovies() {
		return getPrefs().getBooleanPref(XSTREAM_INCLUDE_MOVIES);
	}

	public void setXtreamIncludeMovies(boolean include) {
		getPrefs().applyBooleanPref(XSTREAM_INCLUDE_MOVIES, include);
	}

	public boolean isXtreamIncludeSeries() {
		return getPrefs().getBooleanPref(XSTREAM_INCLUDE_SERIES);
	}

	public void setXtreamIncludeSeries(boolean include) {
		getPrefs().applyBooleanPref(XSTREAM_INCLUDE_SERIES, include);
	}

	public boolean isXtreamAllAtOnce() {
		return getPrefs().getBooleanPref(XSTREAM_ALL_AT_ONCE);
	}

	public void setXtreamAllAtOnce(boolean allAtOnce) {
		getPrefs().applyBooleanPref(XSTREAM_ALL_AT_ONCE, allAtOnce);
	}

	}

	public long getEpgTimeStamp() {
		return getPrefs().getLongPref(EpgPrefs.EPG_TIMESTAMP);
	}

	public void clearStamps() {
		try (PreferenceStore.Edit e = getPrefs().editPreferenceStore()) {
			e.removePref(ETAG);
			e.removePref(TIMESTAMP);
			e.removePref(EpgPrefs.EPG_ETAG);
			e.removePref(EpgPrefs.EPG_TIMESTAMP);
		}
	}

	public int getEpgMaxAge() {
		return getPrefs().getIntPref(EpgPrefs.EPG_MAX_AGE);
	}

	public void setEpgMaxAge(int age) {
		getPrefs().applyIntPref(EpgPrefs.EPG_MAX_AGE, age);
	}

	public File getEpgFile() {
		String epgUrl = getEpgUrl();
		String ext = (epgUrl == null) ? null : getFileExtension(epgUrl);
		return new File(getCacheDir(), getId() + '.' + ("gz".equals(ext) ? "xmltv.gz" : "xmltv"));
	}

	public File getEpgDbFile() {
		return new File(getCacheDir(), getId() + ".db");
	}

	public FutureSupplier<Status> downloadEpg() {
		String url = getEpgUrl();
		if (url == null) return failed(new MalformedURLException("EPG URL is not set"));
		File epgFile = getEpgFile();
		HttpFileDownloader d = new HttpFileDownloader();
		Context ctx = App.get();
		HttpDownloadStatusListener l = new HttpDownloadStatusListener(ctx);
		l.setSmallIcon(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.notification);
		l.setTitle(ctx.getResources().getString(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.downloading, url));
		l.setFailureTitle(s -> ctx.getResources().getString(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.err_failed_to_download, url));
		d.setStatusListener(l);
		d.setReturnExistingOnFail(true);
		return d.download(url, epgFile, getEpgPrefs());
	}

	protected void cleanUp() {
		File f = getEpgFile();
		if (f.isFile() && !f.delete()) Log.e("Failed to delete EPG cache file ", f);
		SQLite.delete(getEpgDbFile());
		// Clean up Xtream persistent data
		if (isXtreamEnabled()) {
			File xtreamDir = getXtreamDataDir();
			String key = getXtreamStorageKey();
			if (!TextUtils.isNullOrBlank(key)) {
				deleteXtreamArtifact(new File(xtreamDir, key + ".m3u"));
				deleteXtreamArtifact(new File(xtreamDir, key + ".index.tsv"));
				File db = new File(xtreamDir, key + "_db");
				if (db.isDirectory()) deleteDir(db);
			}
			String host = getRid().getHost();
			if (host != null) {
				for (File child : safeListFiles(xtreamDir)) {
					if (child.getName().startsWith(host)) {
						if (child.isDirectory()) deleteDir(child);
						else child.delete();
					}
				}
			}
		}
		super.cleanUp();
	}

	private static File[] safeListFiles(File dir) {
		File[] files = dir.listFiles();
		return (files != null) ? files : new File[0];
	}

	private static void deleteDir(File dir) {
		for (File child : safeListFiles(dir)) {
			if (child.isDirectory()) deleteDir(child);
			else child.delete();
		}
		dir.delete();
	}

	private static void deleteXtreamArtifact(File file) {
		if ((file != null) && file.exists() && !file.delete()) {
			Log.e("Failed to delete Xtream artifact ", file);
		}
	}

	private PreferenceStore getEpgPrefs() {
		return new EpgPrefs((M3uPrefs) getPrefs());
	}

	private static final class EpgPrefs extends M3uPrefs {
		static final Pref<Supplier<String>> EPG_ETAG = Pref.s("EPG_ETAG");
		static final Pref<Supplier<String>> EPG_CHARSET = Pref.s("EPG_CHARSET", CHARSET.getDefaultValue());
		static final Pref<Supplier<String>> EPG_ENCODING = Pref.s("EPG_ENCODING");
		static final Pref<IntSupplier> EPG_RESP_TIMEOUT = Pref.i("EPG_RESP_TIMEOUT", 30);
		static final Pref<LongSupplier> EPG_TIMESTAMP = Pref.l("EPG_TIMESTAMP", 0);
		static final Pref<IntSupplier> EPG_MAX_AGE = Pref.i("EPG_MAX_AGE", EPG_FILE_AGE);

		EpgPrefs(M3uPrefs prefs) {
			super(prefs);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <S> Pref<S> getPref(Pref<S> pref) {
			if (pref == ETAG) return (Pref<S>) EPG_ETAG;
			else if (pref == CHARSET) return (Pref<S>) EPG_CHARSET;
			else if (pref == ENCODING) return (Pref<S>) EPG_ENCODING;
			else if (pref == RESP_TIMEOUT) return (Pref<S>) EPG_RESP_TIMEOUT;
			else if (pref == TIMESTAMP) return (Pref<S>) EPG_TIMESTAMP;
			else if (pref == MAX_AGE) return (Pref<S>) EPG_MAX_AGE;
			return super.getPref(pref);
		}
	}
}
