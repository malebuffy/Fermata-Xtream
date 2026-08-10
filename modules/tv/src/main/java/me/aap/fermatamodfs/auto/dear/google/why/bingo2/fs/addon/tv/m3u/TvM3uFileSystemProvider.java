package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u;

import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.CATCHUP_DAYS;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.CATCHUP_QUERY;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_APPEND;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_AUTO;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_DEFAULT;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.EPG_FILE_AGE;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.EPG_SHIFT;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.EPG_URL;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.LOGO_PREFER_EPG;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.LOGO_URL;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.XSTREAM_ALL_AT_ONCE;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.XSTREAM_ENABLED;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.XSTREAM_CONTENT_TYPE;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.XSTREAM_INCLUDE_CHANNELS;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.XSTREAM_INCLUDE_MOVIES;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.XSTREAM_INCLUDE_SERIES;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.XSTREAM_PASSWORD;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.XSTREAM_SERVER;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile.XSTREAM_USERNAME;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u.M3uFile.NAME;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u.M3uFile.URL;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedEmptyList;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.net.http.HttpFileDownloader.AGENT;
import static me.aap.utils.net.http.HttpFileDownloader.RESP_TIMEOUT;

import android.app.AlertDialog;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.BuildConfig;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u.M3uFile;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u.M3uFileSystemProvider;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpFileDownloader;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PrefCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.vfs.VirtualFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uFileSystemProvider extends M3uFileSystemProvider {

	@Override
	public FutureSupplier<TvM3uFile> select(MainActivityDelegate a, List<? extends VirtualFileSystem> fs) {
		return selectSources(a, fs).map(list -> (list == null || list.isEmpty()) ? null : list.get(0));
	}

	public FutureSupplier<List<TvM3uFile>> selectSources(MainActivityDelegate a,
			List<? extends VirtualFileSystem> fs) {
		PreferenceStore ps = PrefsHolder.instance;
		return requestPrefs(a, ps).thenRun(ps::removeBroadcastListeners).then(ok -> {
			if (!ok) return completedEmptyList();
			if (ps.getBooleanPref(XSTREAM_ENABLED)) {
				return loadXtreamSources(a, ps, TvM3uFileSystem.getInstance());
			}
			return load(ps, TvM3uFileSystem.getInstance()).map(f -> {
				List<TvM3uFile> list = new ArrayList<>(1);
				if (f instanceof TvM3uFile tv) list.add(tv);
				return list;
			});
		});
	}

	public FutureSupplier<Boolean> edit(MainActivityDelegate a, TvM3uFile f) {
		BasicPreferenceStore ps = new BasicPreferenceStore();
		String url = f.getUrl();
		String epgUrl = f.getEpgUrl();
		float shift = f.getEpgShift();

		try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
			e.setStringPref(NAME, f.getName());
			e.setBooleanPref(XSTREAM_ENABLED, f.isXtreamEnabled());
			e.setStringPref(URL, f.getUrl());
			e.setStringPref(XSTREAM_SERVER, f.getXtreamServer());
			e.setStringPref(XSTREAM_USERNAME, f.getXtreamUsername());
			e.setStringPref(XSTREAM_PASSWORD, f.getXtreamPassword());
			e.setIntPref(XSTREAM_CONTENT_TYPE, resolveXtreamContentType(f));
			e.setBooleanPref(XSTREAM_ALL_AT_ONCE, f.isXtreamAllAtOnce());
			e.setStringPref(EPG_URL, f.getEpgUrl());
			e.setBooleanPref(LOGO_PREFER_EPG, f.isPreferEpgLogo());
			e.setFloatPref(EPG_SHIFT, f.getEpgShift());
			e.setIntPref(CATCHUP_TYPE, f.getCatchupType());
			e.setIntPref(CATCHUP_DAYS, f.getCatchupDays());
			e.setStringPref(CATCHUP_QUERY, f.getCatchupQuery());
			e.setStringPref(LOGO_URL, f.getLogoUrl());
			e.setStringPref(AGENT, f.getUserAgent());
			e.setIntPref(RESP_TIMEOUT, f.getResponseTimeout());
		}

		return requestPrefs(a, ps).thenRun(ps::removeBroadcastListeners).then(ok -> {
			if (!ok) return completedNull().map(v -> false);
			boolean xtream = ps.getBooleanPref(XSTREAM_ENABLED);
			XtreamProgressModal modal = xtream ? XtreamProgressModal.show(a, "Saving Xtream source...") : null;
			XtreamPlaylistBuilder.ProgressListener progress = (modal == null) ? null : modal::update;

			return App.get().execute(() -> {
				xtreamProgress = progress;
				setPrefs(ps, f);

				if (!Objects.equals(url, f.getUrl())
						|| !Objects.equals(epgUrl, f.getEpgUrl())
						|| (shift != f.getEpgShift())) {
					Log.d("TV source has been modified - clearing stamps.");
					f.clearStamps();
				}

				return true;
			}).onCompletion((r, err) -> {
				xtreamProgress = null;
				if (modal != null) modal.dismiss();
			});
		});
	}

	private FutureSupplier<Boolean> requestPrefs(MainActivityDelegate a, PreferenceStore ps) {
		PreferenceSet prefs = new PreferenceSet();
		PreferenceSet sub;

		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = NAME;
			o.title = me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.m3u_playlist_name;
			o.visibility = new PrefCondition<>(ps, XSTREAM_ENABLED,
					p -> !ps.getBooleanPref(XSTREAM_ENABLED));
		});
		prefs.addBooleanPref(o -> {
			o.store = ps;
			o.pref = XSTREAM_ENABLED;
			o.title = R.string.xtream_account;
		});
		prefs.addFilePref(o -> {
			o.store = ps;
			o.pref = URL;
			o.mode = FilePickerFragment.FILE;
			o.title = me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.m3u_playlist_location;
			o.stringHint = a.getString(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.m3u_playlist_location_hint);
			o.visibility = new PrefCondition<>(ps, XSTREAM_ENABLED,
					p -> !ps.getBooleanPref(XSTREAM_ENABLED));
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = XSTREAM_SERVER;
			o.title = R.string.xtream_server_url;
			o.stringHint = "https://example.com";
			o.visibility = new PrefCondition<>(ps, XSTREAM_ENABLED,
					p -> ps.getBooleanPref(XSTREAM_ENABLED));
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = XSTREAM_USERNAME;
			o.title = R.string.xtream_username;
			o.visibility = new PrefCondition<>(ps, XSTREAM_ENABLED,
					p -> ps.getBooleanPref(XSTREAM_ENABLED));
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = XSTREAM_PASSWORD;
			o.title = R.string.xtream_password;
			o.visibility = new PrefCondition<>(ps, XSTREAM_ENABLED,
					p -> ps.getBooleanPref(XSTREAM_ENABLED));
		});
		prefs.addBooleanPref(o -> {
			o.store = ps;
			o.pref = XSTREAM_ALL_AT_ONCE;
			o.title = R.string.xtream_all_at_once;
			o.visibility = new PrefCondition<>(ps, XSTREAM_ENABLED,
					p -> ps.getBooleanPref(XSTREAM_ENABLED));
		});
		prefs.addListPref(o -> {
			o.store = ps;
			o.pref = XSTREAM_CONTENT_TYPE;
			o.title = R.string.xtream_content_type;
			o.subtitle = R.string.xtream_content_type_cur;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.xtream_include_channels, R.string.xtream_include_movies,
					R.string.xtream_include_series};
			o.visibility = new PrefCondition<>(ps, XSTREAM_ENABLED, p -> false);
		});

		sub = prefs.subSet(o -> o.title = R.string.epg);
		sub.addStringPref(o -> {
			o.store = ps;
			o.pref = EPG_URL;
			o.title = R.string.epg_url;
			o.stringHint = "http://example.com/epg.xml.gz";
			o.visibility = new PrefCondition<>(ps, XSTREAM_ENABLED,
					p -> !ps.getBooleanPref(XSTREAM_ENABLED));
		});
		sub.addBooleanPref(o -> {
			o.store = ps;
			o.pref = LOGO_PREFER_EPG;
			o.title = R.string.logo_prefer_epg;
			o.visibility = new PrefCondition<>(ps, XSTREAM_ENABLED,
					p -> !ps.getBooleanPref(XSTREAM_ENABLED));
		});
		sub.addFloatPref(o -> {
			o.store = ps;
			o.pref = EPG_SHIFT;
			o.seekMin = -12;
			o.seekMax = 12;
			o.title = R.string.epg_time_shift;
			o.visibility = new PrefCondition<>(ps, XSTREAM_ENABLED,
					p -> !ps.getBooleanPref(XSTREAM_ENABLED));
		});

		sub = prefs.subSet(o -> o.title = R.string.catchup);
		sub.addListPref(o -> {
			o.store = ps;
			o.pref = CATCHUP_TYPE;
			o.title = R.string.catchup_type;
			o.subtitle = R.string.catchup_type_cur;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.catchup_type_auto, R.string.catchup_type_append,
					R.string.catchup_type_default, R.string.catchup_type_shift, R.string.catchup_type_flussonic};
			o.visibility = new PrefCondition<>(ps, XSTREAM_ENABLED,
					p -> !ps.getBooleanPref(XSTREAM_ENABLED));
		});
		sub.addIntPref(o -> {
			o.store = ps;
			o.pref = CATCHUP_DAYS;
			o.seekMax = 30;
			o.title = R.string.catchup_days;
			o.visibility = new PrefCondition<>(ps, CATCHUP_TYPE,
					p -> ps.getIntPref(CATCHUP_TYPE) != CATCHUP_TYPE_AUTO);
		});
		sub.addStringPref(o -> {
			o.store = ps;
			o.pref = CATCHUP_QUERY;
			o.title = R.string.catchup_query;
			o.stringHint = "?timeshift=${start}&timenow=${timestamp}";
			o.visibility = new PrefCondition<>(ps, CATCHUP_TYPE,
					p -> ps.getIntPref(CATCHUP_TYPE) == CATCHUP_TYPE_APPEND);
		});
		sub.addStringPref(o -> {
			o.store = ps;
			o.pref = CATCHUP_QUERY;
			o.title = R.string.catchup_query;
			o.stringHint = "http://example.com/stream1_${offset}.m3u8";
			o.visibility = new PrefCondition<>(ps, CATCHUP_TYPE,
					p -> ps.getIntPref(CATCHUP_TYPE) == CATCHUP_TYPE_DEFAULT);
		});

		sub = prefs.subSet(o -> o.title = R.string.logo);
		sub.addFilePref(o -> {
			o.store = ps;
			o.pref = LOGO_URL;
			o.mode = FilePickerFragment.FILE;
			o.title = R.string.logo_location;
			o.stringHint = a.getString(R.string.logo_location_hint);
			o.visibility = new PrefCondition<>(ps, XSTREAM_ENABLED,
					p -> !ps.getBooleanPref(XSTREAM_ENABLED));
		});

		sub = prefs.subSet(o -> o.title = R.string.connection_settings);
		sub.addStringPref(o -> {
			o.store = ps;
			o.pref = AGENT;
			o.title = me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.m3u_playlist_agent;
			o.stringHint = "Fermata/" + BuildConfig.VERSION_NAME;
		});
		sub.addIntPref(o -> {
			o.store = ps;
			o.pref = RESP_TIMEOUT;
			o.title = me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.m3u_playlist_timeout;
		});

		return requestPrefs(a, prefs, ps);
	}

	@Override
	protected boolean validate(PreferenceStore ps) {
		if (ps.getBooleanPref(XSTREAM_ENABLED)) {
			String server = ps.getStringPref(XSTREAM_SERVER);
			String username = ps.getStringPref(XSTREAM_USERNAME);
			String password = ps.getStringPref(XSTREAM_PASSWORD);
			if (TextUtils.isNullOrBlank(server)) return false;

			if (TextUtils.isNullOrBlank(username) || TextUtils.isNullOrBlank(password)) {
				return hasCredentialsInServerUrl(server);
			}

			return true;
		}

		if (!super.validate(ps)) return false;
		String q;

		switch (ps.getIntPref(CATCHUP_TYPE)) {
			case CATCHUP_TYPE_APPEND:
				q = ps.getStringPref(CATCHUP_QUERY);
				return (q != null) && !TextUtils.isBlank(q);
			case CATCHUP_TYPE_DEFAULT:
				q = ps.getStringPref(CATCHUP_QUERY);
				return (q != null) && !TextUtils.isBlank(q) &&
						(q.startsWith("http://") || q.startsWith("https://"));
			default:
				return true;
		}
	}

	@Override
	protected void setPrefs(PreferenceStore ps, M3uFile m3u) {
		TvM3uFile f = (TvM3uFile) m3u;
		boolean xtream = ps.getBooleanPref(XSTREAM_ENABLED);
		f.setXtreamEnabled(xtream);
		f.setXtreamServer(ps.getStringPref(XSTREAM_SERVER));
		f.setXtreamUsername(ps.getStringPref(XSTREAM_USERNAME));
		f.setXtreamPassword(ps.getStringPref(XSTREAM_PASSWORD));
		int contentType = ps.getIntPref(XSTREAM_CONTENT_TYPE);
		f.setXtreamContentType(contentType);
		f.setXtreamIncludeChannels(contentType == TvM3uFile.XSTREAM_TYPE_CHANNELS);
		f.setXtreamIncludeMovies(contentType == TvM3uFile.XSTREAM_TYPE_MOVIES);
		f.setXtreamIncludeSeries(contentType == TvM3uFile.XSTREAM_TYPE_SERIES);
		f.setXtreamAllAtOnce(ps.getBooleanPref(XSTREAM_ALL_AT_ONCE));

		if (xtream) {
			String localPath = XtreamPlaylistBuilder.build(ps, f, xtreamProgress);
			ps.applyStringPref(URL, localPath);
		} else {
			KnownProviders.configure(ps);
		}

		super.setPrefs(ps, f);
		f.setVideo(true);
		f.setEpgUrl(ps.getStringPref(EPG_URL));
		f.setEpgShift(ps.getFloatPref(EPG_SHIFT));
		f.setCatchupQuery(ps.getStringPref(CATCHUP_QUERY));
		f.setCatchupType(ps.getIntPref(CATCHUP_TYPE));
		f.setCatchupDays(ps.getIntPref(CATCHUP_DAYS));
		f.setLogoUrl(ps.getStringPref(LOGO_URL));
		f.setPreferEpgLogo(ps.getBooleanPref(LOGO_PREFER_EPG));
		f.setEpgMaxAge(EPG_FILE_AGE);
		f.setResponseTimeout(ps.getIntPref(RESP_TIMEOUT));
	}

	private boolean hasCredentialsInServerUrl(String server) {
		if (TextUtils.isNullOrBlank(server)) return false;
		String s = server.toLowerCase(Locale.US);
		return s.contains("username=") && s.contains("password=");
	}

	private int resolveXtreamContentType(TvM3uFile f) {
		int type = f.getXtreamContentType();
		if ((type >= TvM3uFile.XSTREAM_TYPE_CHANNELS) && (type <= TvM3uFile.XSTREAM_TYPE_SERIES)) return type;
		if (f.isXtreamIncludeMovies()) return TvM3uFile.XSTREAM_TYPE_MOVIES;
		if (f.isXtreamIncludeSeries()) return TvM3uFile.XSTREAM_TYPE_SERIES;
		return TvM3uFile.XSTREAM_TYPE_CHANNELS;
	}

	private FutureSupplier<List<TvM3uFile>> loadXtreamSources(MainActivityDelegate a, PreferenceStore basePs,
			TvM3uFileSystem fs) {
		XtreamProgressModal modal = XtreamProgressModal.show(a, a.getString(R.string.xtream_import_title));
		XtreamPlaylistBuilder.ProgressListener progress = modal::update;

		int[] types = {
				TvM3uFile.XSTREAM_TYPE_CHANNELS,
				TvM3uFile.XSTREAM_TYPE_MOVIES,
				TvM3uFile.XSTREAM_TYPE_SERIES
		};
		int[] names = {
				R.string.xtream_folder_live,
				R.string.xtream_folder_movies,
				R.string.xtream_folder_series
		};

		FutureSupplier<List<TvM3uFile>> chain = completed(new ArrayList<>());
		for (int i = 0; i < types.length; i++) {
			int type = types[i];
			String folderName = a.getString(names[i]);
			chain = chain.then(list -> loadOneXtreamSource(basePs, fs, type, folderName, progress)
					.map(f -> {
						if (f != null) list.add(f);
						return list;
					}));
		}

		return chain.then(list -> {
			if (list.isEmpty()) {
				return failed(new IllegalArgumentException("No Xtream content was imported")).cast();
			}
			return completed(list);
		}).onCompletion((r, err) -> {
			xtreamProgress = null;
			modal.dismiss();
		});
	}

	private FutureSupplier<TvM3uFile> loadOneXtreamSource(PreferenceStore basePs, TvM3uFileSystem fs,
			int contentType, String folderName,
			XtreamPlaylistBuilder.ProgressListener progress) {
		BasicPreferenceStore ps = copyXtreamPrefs(basePs, contentType, folderName);
		return App.get().execute(() -> {
			try {
				xtreamProgress = msg -> progress.onProgress(folderName + ": " + msg);
				progress.onProgress(folderName);
				TvM3uFile f = fs.createNewFile();
				f.setName(folderName);
				setPrefs(ps, f);
				f.setName(folderName);
				return f;
			} catch (Exception ex) {
				Log.e(ex, "Failed to import Xtream folder ", folderName);
				return null;
			}
		}).then(f -> {
			if (f == null) return completedNull();
			return fs.reload(f).onFailure(err -> {
				Log.e(err, "Failed to load Xtream folder ", folderName);
				removeSource(f);
			}).cast();
		}).onFailure(err -> completedNull()).cast();
	}

	private BasicPreferenceStore copyXtreamPrefs(PreferenceStore basePs, int contentType, String folderName) {
		BasicPreferenceStore ps = new BasicPreferenceStore();
		try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
			e.setStringPref(NAME, folderName);
			e.setBooleanPref(XSTREAM_ENABLED, true);
			e.setStringPref(XSTREAM_SERVER, basePs.getStringPref(XSTREAM_SERVER));
			e.setStringPref(XSTREAM_USERNAME, basePs.getStringPref(XSTREAM_USERNAME));
			e.setStringPref(XSTREAM_PASSWORD, basePs.getStringPref(XSTREAM_PASSWORD));
			e.setIntPref(XSTREAM_CONTENT_TYPE, contentType);
			e.setBooleanPref(XSTREAM_ALL_AT_ONCE, basePs.getBooleanPref(XSTREAM_ALL_AT_ONCE));
			e.setStringPref(AGENT, basePs.getStringPref(AGENT));
			e.setIntPref(RESP_TIMEOUT, basePs.getIntPref(RESP_TIMEOUT));
		}
		return ps;
	}

	private XtreamPlaylistBuilder.ProgressListener xtreamProgress;

	private static final class XtreamProgressModal {
		private final MainActivityDelegate activity;
		private AlertDialog dialog;
		private TextView messageView;

		private XtreamProgressModal(MainActivityDelegate activity) {
			this.activity = activity;
		}

		static XtreamProgressModal show(MainActivityDelegate activity, String title) {
			XtreamProgressModal m = new XtreamProgressModal(activity);
			m.showInternal(title);
			return m;
		}

		void update(String message) {
			activity.post(() -> {
				if ((dialog != null) && dialog.isShowing() && (messageView != null)) {
					messageView.setText(message);
				}
			});
		}

		void dismiss() {
			activity.post(() -> {
				if (dialog != null) {
					dialog.dismiss();
					dialog = null;
				}
			});
		}

		private void showInternal(String title) {
			activity.post(() -> {
				LinearLayout root = new LinearLayout(activity.getContext());
				root.setOrientation(LinearLayout.VERTICAL);
				int pad = dp(16);
				root.setPadding(pad, pad, pad, pad);

				ProgressBar progress = new ProgressBar(activity.getContext());
				progress.setIndeterminate(true);
				LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				p.bottomMargin = dp(12);
				root.addView(progress, p);

				messageView = new TextView(activity.getContext());
				messageView.setText("Preparing Xtream account...");
				root.addView(messageView, new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

				dialog = new AlertDialog.Builder(activity.getContext())
						.setTitle(title)
						.setView(root)
						.setCancelable(false)
						.create();
				dialog.show();
			});
		}

		private int dp(int value) {
			return Math.round(activity.getContext().getResources().getDisplayMetrics().density * value);
		}
	}

	public static void removeSource(TvM3uFile f) {
		Log.d("Removing TV source ", f);
		f.cleanUp();
	}

	public static boolean refreshSource(TvM3uFile f) {
		return refreshSource(f, null);
	}

	public static boolean refreshSource(TvM3uFile f, java.util.function.Consumer<String> progress) {
		if ((f == null) || !f.isXtreamEnabled()) return true;
		if (!REFRESH_IN_PROGRESS.compareAndSet(false, true)) {
			throw new IllegalStateException("Another TV source refresh is already in progress");
		}

		java.util.function.Consumer<String> p = (progress != null) ? progress : s -> {
		};

		try {
			p.accept("Clearing old playlist...");
			clearPlaylistFile(f.getLocalFile());
			f.clearStamps();
			forceGc();

			int contentType = f.getXtreamContentType();
			if ((contentType < TvM3uFile.XSTREAM_TYPE_CHANNELS)
					|| (contentType > TvM3uFile.XSTREAM_TYPE_SERIES)) {
				if (f.isXtreamIncludeMovies()) contentType = TvM3uFile.XSTREAM_TYPE_MOVIES;
				else if (f.isXtreamIncludeSeries()) contentType = TvM3uFile.XSTREAM_TYPE_SERIES;
				else contentType = TvM3uFile.XSTREAM_TYPE_CHANNELS;
			}

			String name = f.getName();
			BasicPreferenceStore ps = new BasicPreferenceStore();
			try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
				e.setStringPref(NAME, name);
				e.setBooleanPref(XSTREAM_ENABLED, true);
				e.setStringPref(XSTREAM_SERVER, f.getXtreamServer());
				e.setStringPref(XSTREAM_USERNAME, f.getXtreamUsername());
				e.setStringPref(XSTREAM_PASSWORD, f.getXtreamPassword());
				e.setIntPref(XSTREAM_CONTENT_TYPE, contentType);
				e.setBooleanPref(XSTREAM_ALL_AT_ONCE, f.isXtreamAllAtOnce());
				e.setStringPref(AGENT, f.getUserAgent());
				e.setIntPref(RESP_TIMEOUT, f.getResponseTimeout());
			}

			String localPath = XtreamPlaylistBuilder.build(ps, f, p::accept);
			if (TextUtils.isNullOrBlank(localPath)) return false;

			f.setName(name);
			f.setXtreamContentType(contentType);
			f.setXtreamIncludeChannels(contentType == TvM3uFile.XSTREAM_TYPE_CHANNELS);
			f.setXtreamIncludeMovies(contentType == TvM3uFile.XSTREAM_TYPE_MOVIES);
			f.setXtreamIncludeSeries(contentType == TvM3uFile.XSTREAM_TYPE_SERIES);
			f.setUrl(localPath);
			f.clearStamps();
			forceGc();
			return true;
		} finally {
			REFRESH_IN_PROGRESS.set(false);
		}
	}

	private static void clearPlaylistFile(File file) {
		if (file == null) return;
		File parent = file.getParentFile();
		if ((parent != null) && !parent.exists()) parent.mkdirs();

		try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file, false),
				StandardCharsets.UTF_8)) {
			w.write("#EXTM3U\n");
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to clear playlist before refresh", ex);
		}
	}

	private static void forceGc() {
		Runtime r = Runtime.getRuntime();
		r.gc();
		r.runFinalization();
	}

	private static final AtomicBoolean REFRESH_IN_PROGRESS = new AtomicBoolean(false);

	protected String getTitle(MainActivityDelegate a) {
		return a.getString(R.string.add_tv_source);
	}

	private static final class PrefsHolder extends BasicPreferenceStore {
		static final PrefsHolder instance = new PrefsHolder();
	}
}
