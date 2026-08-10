package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedEmptyList;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;
import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.pref.MediaPrefs.MEDIA_ENG_EXO;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.AddonManager;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFile;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFileSystem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uFileSystemProvider;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uGroupItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvM3uTrackItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u.TvXtreamEpisodeItemFactory;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.DefaultMediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine.MediaEngine;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.BrowsableItemBase;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.ItemBase;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.pref.BrowsableItemPrefs;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.BrowsableItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Item;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.PlayableItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.FermataServiceUiBinder;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.MediaLibFragment;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.BodyLayout;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.MediaItemView;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.MediaItemMenuHandler;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.MediaItemWrapper;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.VideoView;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Cancellable;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.FloatingButton;

/**
 * @author Andrey Pavlenko
 */
public class TvFragment extends MediaLibFragment {
	private static final int CATCHUP_FETCH_RETRIES = 5;
	private static final long CATCHUP_FETCH_RETRY_DELAY_MS = 1200L;

	@Override
	protected ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new TvAdapter(getMainActivity(), getRootItem());
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.addon_name_tv);
	}

	@Override
	public int getFragmentId() {
		return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.tv_fragment;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return TvFloatingButtonMediator.instance;
	}

	@Override
	public void onResume() {
		super.onResume();
		scheduleRestoreLastPlayedChannel();
	}

	public void navBarItemReselected(int itemId) {
		getAdapter().setParent(getRootItem());
	}

	@Override
	public void onPlaybackError(String message) {
		Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) {
			resetRestoreLastPlayedChannel();
			return;
		}

		TvAdapter a = getAdapter();
		if (a != null) a.animateAddButton(a.getParent());
		resetRestoreLastPlayedChannel();
		scheduleRestoreLastPlayedChannel();
	}

	@Override
	public void switchingTo(@NonNull ActivityFragment newFragment) {
		super.switchingTo(newFragment);
		getMainActivity().getFloatingButton().clearAnimation();
	}

	public void addSource() {
		TvM3uFileSystemProvider prov = new TvM3uFileSystemProvider();
		prov.selectSources(getMainActivity(), Collections.singletonList(TvM3uFileSystem.getInstance()))
				.main().onFailure(this::failedToAddSource).onSuccess(this::addM3uSources);
	}

	public TvRootItem getRootItem() {
		return requireNonNull(AddonManager.get().getAddon(TvAddon.class)).getRootItem(
				(DefaultMediaLib) getMainActivity().getLib());
	}

	@Override
	public void contributeToContextMenu(OverlayMenu.Builder b, MediaItemMenuHandler h) {
		Item i = h.getItem();

		if (i instanceof TvM3uTrackItem) {
			TvM3uTrackItem track = (TvM3uTrackItem) i;
			TvM3uFile source = findSource(track);
			if (canOpenXtreamCatchup(source, track)) {
				b.addItem(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.tv_catchup, me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.epg, R.string.tv_catchup)
						.setFutureSubmenu(sb -> buildXtreamCatchupMenu(sb, track, source));
			}
		} else if (i instanceof TvM3uItem) {
			TvM3uItem src = (TvM3uItem) i;
			b.addItem(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.refresh, me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.refresh,
						me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.refresh).setData(h.getItem())
					.setHandler(this::contextMenuItemSelected);
			b.addItem(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.edit, me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.edit,
							me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.edit).setData(h.getItem())
					.setHandler(this::contextMenuItemSelected);
			b.addItem(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.delete, me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.delete,
							me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.delete).setData(h.getItem())
					.setHandler(this::contextMenuItemSelected);
		}

		super.contributeToContextMenu(b, h);
	}

	private boolean canOpenXtreamCatchup(TvM3uFile source, TvM3uTrackItem track) {
		if (source == null) return false;
		if (!source.isXtreamEnabled()) return false;
		if (source.getXtreamContentType() != TvM3uFile.XSTREAM_TYPE_CHANNELS) return false;
		if (track == null) return false;
		if (!track.hasTrackCatchupDays()) return false;

		String tvgId = track.getTvgId();
		if ((tvgId != null) && tvgId.startsWith("xts:")) return false;

		Uri location = track.getLocation();
		if (location == null) return false;
		String path = location.getPath();
		return (path != null) && path.contains("/live/");
	}

	private FutureSupplier<Void> buildXtreamCatchupMenu(OverlayMenu.Builder b,
															 TvM3uTrackItem track,
															 TvM3uFile source) {
		b.setTitle(R.string.tv_catchup);
		OverlayMenuItem loadingItem = b.addItem(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.auto,
				me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.refresh, R.string.tv_catchup_loading);
		String timezoneId = source.getXtreamTimezone();

		return App.get().execute(() -> loadXtreamCatchupPrograms(source, track)).main().then(programs -> {
			loadingItem.setVisible(false);
			if (programs.isEmpty()) {
				b.addItem(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.auto, null, R.string.tv_catchup_empty);
				return completedVoid();
			}

			int max = Math.min(programs.size(), 60);
			for (int i = 0; i < max; i++) {
				XtreamCatchupProgram p = programs.get(i);
				b.addItem(UiUtils.getArrayItemId(i), null, formatCatchupLabel(p, timezoneId)).setData(p)
						.setHandler(item -> {
							XtreamCatchupProgram selected = item.getData();
							playXtreamCatchupProgram(track, selected);
							return true;
						});
			}

			return completedVoid();
		}).ifFail(err -> {
			loadingItem.setVisible(false);
			Log.e(err, "Failed to build Xtream catch-up menu for ", track);
			b.addItem(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.auto, null, R.string.tv_catchup_failed);
			return null;
		});
	}

	private void playXtreamCatchupProgram(TvM3uTrackItem track, XtreamCatchupProgram program) {
		String title = track.getName() + " - " + program.title;
		TvXtreamEpisodeItemFactory.create(track, program.url, title).main().onCompletion((item, err) -> {
			if (err != null) {
				Log.e(err, "Failed to create Xtream catch-up item");
				Toast.makeText(getContext(), R.string.tv_catchup_play_failed, Toast.LENGTH_SHORT).show();
				return;
			}

			if (item == null) {
				Toast.makeText(getContext(), R.string.tv_catchup_play_failed, Toast.LENGTH_SHORT).show();
				return;
			}

			getMainActivity().getMediaServiceBinder().playItem(item);
		});
	}

	private List<XtreamCatchupProgram> loadXtreamCatchupPrograms(TvM3uFile source, TvM3uTrackItem track) {
		String baseUrl = trimTrailingSlash(source.getXtreamServer());
		String username = source.getXtreamUsername();
		String password = source.getXtreamPassword();
		String streamId = extractXtreamLiveStreamId(track);
		int catchupDays = Math.max(0, track.getCatchupDaysValue());

		if (TextUtils.isEmpty(baseUrl) || TextUtils.isEmpty(username)
				|| TextUtils.isEmpty(password) || TextUtils.isEmpty(streamId)) {
			return Collections.emptyList();
		}

		JSONArray listings = fetchXtreamCatchupListingsWithRetry(baseUrl, username, password, streamId);
		if ((listings == null) || (listings.length() == 0)) {
			return Collections.emptyList();
		}

		List<XtreamCatchupProgram> programs = parseXtreamCatchupPrograms(listings, baseUrl, username,
				password, streamId, source.getXtreamTimezone(), catchupDays);

		programs.sort((a, b) -> Long.compare(b.startMs, a.startMs));
		return programs;
	}

	private JSONArray fetchXtreamCatchupListingsWithRetry(String baseUrl,
													 String username,
													 String password,
													 String streamId) {
		JSONArray best = new JSONArray();

		for (int attempt = 1; attempt <= CATCHUP_FETCH_RETRIES; attempt++) {
			JSONArray listings = fetchXtreamSimpleDataTableListings(baseUrl, username, password, streamId);
			int count = (listings == null) ? 0 : listings.length();

			if (count > best.length()) best = listings;
			if (count > 0) return listings;

			if (attempt < CATCHUP_FETCH_RETRIES) {
				try {
					Thread.sleep(CATCHUP_FETCH_RETRY_DELAY_MS);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}

		return best;
	}

	private JSONArray fetchXtreamSimpleDataTableListings(String baseUrl,
													 String username,
													 String password,
													 String streamId) {

		String simpleTableUrl = baseUrl + "/player_api.php?username=" + enc(username) +
				"&password=" + enc(password) + "&action=get_simple_data_table&stream_id=" + enc(streamId);
		try {
			return extractListings(request(simpleTableUrl));
		} catch (Throwable ex) {
			Log.d(ex, "Xtream simple_data_table request failed for stream ", streamId);
			return new JSONArray();
		}
	}

	private JSONArray extractListings(String body) {
		if (TextUtils.isEmpty(body)) return new JSONArray();
		String trimmed = body.trim();

		try {
			if (trimmed.startsWith("[")) {
				return new JSONArray(trimmed);
			}

			JSONObject root = new JSONObject(trimmed);
			JSONArray listings = root.optJSONArray("epg_listings");
			if (listings != null) return listings;
			listings = root.optJSONArray("listings");
			if (listings != null) return listings;
			listings = root.optJSONArray("epg_data");
			if (listings != null) return listings;

			String encodedListings = root.optString("epg_listings", "").trim();
			if (encodedListings.startsWith("[")) return new JSONArray(encodedListings);
		} catch (Throwable ex) {
			Log.d(ex, "Invalid Xtream catch-up listings payload");
		}

		return new JSONArray();
	}

	private List<XtreamCatchupProgram> parseXtreamCatchupPrograms(JSONArray listings,
														  String baseUrl,
														  String username,
														  String password,
													  String streamId,
								  String timezoneId,
								  int catchupDays) {
		if ((listings == null) || (listings.length() == 0)) return Collections.emptyList();

		TimeZone tz = resolveXtreamTimeZone(timezoneId);
		long nowSec = System.currentTimeMillis() / 1000L;
		int daysWindow = (catchupDays > 0) ? catchupDays : 7;
		long oldestAllowedSec = nowSec - (daysWindow * 24L * 3600L);
		List<XtreamCatchupProgram> result = new ArrayList<>();
		for (int i = 0; i < listings.length(); i++) {
			JSONObject item = listings.optJSONObject(i);
			if (item == null) {
				continue;
			}

			String title = decodeXtreamTitle(item);
			long startSec = parseEpoch(item, "start_timestamp", "start", tz);
			long endSec = parseEpoch(item, "stop_timestamp", "end", tz);

			if ((startSec <= 0) || (endSec <= startSec)) {
				continue;
			}

			if (endSec > nowSec) {
				continue;
			}

			if (endSec < oldestAllowedSec) {
				continue;
			}

			long durationMinutes = Math.max(1L, (endSec - startSec) / 60L);
			String startTimestamp = buildXtreamStartTimestamp(item, startSec, tz);
			if (TextUtils.isEmpty(title)) title = "Program";

			String url = buildXtreamTimeshiftUrl(baseUrl, username, password, streamId,
					durationMinutes, startTimestamp);
			result.add(new XtreamCatchupProgram(title, startSec * 1000L, endSec * 1000L, url));
		}

		return result;
	}

	private String extractXtreamLiveStreamId(TvM3uTrackItem track) {
		String tvgId = track.getTvgId();
		if (!TextUtils.isEmpty(tvgId) && tvgId.matches("\\d+")) return tvgId;

		Uri location = track.getLocation();
		if (location == null) return null;
		List<String> segments = location.getPathSegments();
		if ((segments == null) || (segments.size() < 4)) return null;

		for (int i = 0; i < segments.size(); i++) {
			if (!"live".equalsIgnoreCase(segments.get(i))) continue;
			if (i + 3 >= segments.size()) return null;
			String idPart = segments.get(i + 3);
			int dot = idPart.indexOf('.');
			if (dot > 0) idPart = idPart.substring(0, dot);
			return idPart.matches("\\d+") ? idPart : null;
		}

		return null;
	}

	private String buildXtreamTimeshiftUrl(String baseUrl,
											 String username,
											 String password,
											 String streamId,
												 long durationMinutes,
											 String startTimestamp) {
		long duration = Math.max(1L, durationMinutes);
		String encodedStart = Uri.encode(startTimestamp, "-:_");
		return trimTrailingSlash(baseUrl) + "/timeshift/" + Uri.encode(username) + "/" +
				Uri.encode(password) + "/" + duration + "/" + encodedStart + "/" +
				Uri.encode(streamId) + ".ts";
	}

	private String decodeXtreamTitle(JSONObject item) {
		String raw = item.optString("title", "").trim();
		if (TextUtils.isEmpty(raw)) raw = item.optString("name", "").trim();
		if (TextUtils.isEmpty(raw)) return "";

		try {
			String decoded = new String(Base64.decode(raw, Base64.DEFAULT), StandardCharsets.UTF_8).trim();
			return TextUtils.isEmpty(decoded) ? raw : decoded;
		} catch (Throwable ex) {
			return raw;
		}
	}

	private long parseEpoch(JSONObject item, String primaryField, String fallbackField, TimeZone tz) {
		long primary = parseEpochValue(item.optString(primaryField, ""), tz);
		if (primary > 0) return primary;
		return parseEpochValue(item.optString(fallbackField, ""), tz);
	}

	private long parseEpochValue(String raw, TimeZone tz) {
		if (TextUtils.isEmpty(raw)) return -1L;
		String v = raw.trim();
		if (v.isEmpty()) return -1L;

		try {
			long n = Long.parseLong(v);
			return (n > 100000000000L) ? (n / 1000L) : n;
		} catch (NumberFormatException ignored) {
		}

		String[] patterns = new String[]{
				"yyyy-MM-dd HH:mm:ss",
				"yyyy-MM-dd HH:mm",
				"yyyy-MM-dd:HH-mm",
				"yyyy-MM-dd:HH:mm"
		};

		for (String p : patterns) {
			try {
				SimpleDateFormat parser = new SimpleDateFormat(p, Locale.US);
				parser.setTimeZone(tz);
				parser.setLenient(false);
				Date d = parser.parse(v);
				if (d != null) return d.getTime() / 1000L;
			} catch (Throwable ignore) {
			}
		}

		return -1L;
	}

	private String buildXtreamStartTimestamp(JSONObject item, long startEpochSec, TimeZone tz) {
		String normalized = normalizeXtreamTimestamp(item.optString("start", ""), tz);
		if (!TextUtils.isEmpty(normalized)) return normalized;

		normalized = normalizeXtreamTimestamp(item.optString("start_datetime", ""), tz);
		if (!TextUtils.isEmpty(normalized)) return normalized;

		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US);
		formatter.setTimeZone(tz);
		return formatter.format(new Date(startEpochSec * 1000L));
	}

	private String normalizeXtreamTimestamp(String raw, TimeZone tz) {
		if (TextUtils.isEmpty(raw)) return null;
		String v = raw.trim();
		if (v.isEmpty()) return null;

		try {
			long n = Long.parseLong(v);
			if (n > 0) {
				if (n > 100000000000L) n /= 1000L;
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US);
				formatter.setTimeZone(tz);
				return formatter.format(new Date(n * 1000L));
			}
		} catch (NumberFormatException ignored) {
		}

		String[] patterns = new String[]{
				"yyyy-MM-dd HH:mm:ss",
				"yyyy-MM-dd HH:mm",
				"yyyy-MM-dd:HH-mm",
				"yyyy-MM-dd:HH:mm"
		};

		for (String p : patterns) {
			try {
				SimpleDateFormat parser = new SimpleDateFormat(p, Locale.US);
				parser.setTimeZone(tz);
				parser.setLenient(false);
				Date d = parser.parse(v);
				if (d == null) continue;
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US);
				formatter.setTimeZone(tz);
				return formatter.format(d);
			} catch (Throwable ignore) {
			}
		}

		return null;
	}

	private String formatCatchupLabel(XtreamCatchupProgram p, String timezoneId) {
		TimeZone tz = resolveXtreamTimeZone(timezoneId);
		SimpleDateFormat startFormatter = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
		SimpleDateFormat endFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
		startFormatter.setTimeZone(tz);
		endFormatter.setTimeZone(tz);
		return startFormatter.format(new Date(p.startMs)) + " - " +
				endFormatter.format(new Date(p.endMs)) + "  " + p.title;
	}

	private TimeZone resolveXtreamTimeZone(String timezoneId) {
		String id = (timezoneId == null) ? "" : timezoneId.trim();
		if (id.isEmpty()) return TimeZone.getDefault();
		return TimeZone.getTimeZone(id);
	}

	private String trimTrailingSlash(String value) {
		if (value == null) return "";
		String v = value.trim();
		while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
		return v;
	}

	private boolean contextMenuItemSelected(OverlayMenuItem item) {
		int id = item.getItemId();
		if (id == me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.refresh) {
			TvM3uItem i = item.getData();
			Toast.makeText(getContext(), "Refreshing TV source...", Toast.LENGTH_SHORT).show();
			i.clearCachedPlaylistData().main().onCompletion((v1, err1) -> {
				if (err1 != null) {
					Log.e(err1, "Failed to clear TV source cache ", i);
					Toast.makeText(getContext(), "Refresh failed", Toast.LENGTH_SHORT).show();
					return;
				}

				App.get().execute(() -> TvM3uFileSystemProvider.refreshSource(i.getResource())).main()
					.onCompletion((ok, err) -> {
						if ((err != null) && !(err instanceof CancellationException)) {
							Log.e(err, "Failed to refresh TV source ", i);
							Toast.makeText(getContext(), "Refresh failed", Toast.LENGTH_SHORT).show();
							return;
						}

						if ((ok != null) && ok) {
							Toast.makeText(getContext(), "Refresh completed", Toast.LENGTH_SHORT).show();
							i.refresh().onCompletion((v, refreshErr) -> {
								if (refreshErr != null) {
									Log.e(refreshErr, "Failed to reload TV source ", i);
									Toast.makeText(getContext(), "Refresh reload failed", Toast.LENGTH_SHORT).show();
									return;
								}

								BrowsableItem parent = getAdapter().getParent();
								if (parent == i) {
									getAdapter().setParent(i);
								} else if ((parent instanceof TvM3uGroupItem)
										&& (((TvM3uGroupItem) parent).getParent() == i)) {
									getAdapter().setParent(parent);
								}
							});
						}
					});
			});
		} else if (id == me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.edit) {
			TvM3uItem i = item.getData();
			new TvM3uFileSystemProvider().edit(getMainActivity(), i.getResource())
					.onCompletion((ok, err) -> {
						if ((err != null) && !(err instanceof CancellationException)) {
							Log.e(err, "Failed to edit TV source ", i);
							UiUtils.showAlert(getContext(), err.getLocalizedMessage());
						}
						getMainActivity().showFragment(getFragmentId());
						if ((ok != null) && ok) i.refresh().thenRun(this::refresh);
					});
		} else if (id == me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.delete) {
			TvRootItem root = getRootItem();
			root.removeItem(item.getData()).onSuccess(v -> getAdapter().setParent(root));
		}
		return true;
	}


	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
		super.contributeToNavBarMenu(builder);
		if (isRootItem()) return;
		TvAdapter a = getAdapter();

		if (a.getListView().isSelectionActive() && a.hasSelectable() && a.hasSelected()) {
			OverlayMenu.Builder b = builder.withSelectionHandler(this::navBarMenuItemSelected);
			b.addItem(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.favorites_add, me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.favorite,
					me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.favorites_add);
			getMainActivity().addPlaylistMenu(b, completed(a.getSelectedItems()));
		}
	}

	@Override
	protected boolean shouldRestoreLastPlayedChannel() {
		TvAddon addon = AddonManager.get().getAddon(TvAddon.class);
		return addon != null && addon.autoplayLastChannel();
	}

	@Override
	protected boolean isSupportedItem(Item i) {
		return getRootItem().isChildItemId(i.getId());
	}

	@Override
	protected boolean isSupportedItemId(String id) {
		return getRootItem().isChildItemId(id);
	}

	@Override
	protected boolean isRefreshSupported() {
		return true;
	}

	private void addM3uSources(List<TvM3uFile> sources) {
		MainActivityDelegate a = getMainActivity();
		if (sources != null) {
			TvRootItem root = getRootItem();
			for (TvM3uFile m3u : sources) {
				if (m3u != null) root.addSource(m3u);
			}
		}
		getAdapter().setParent(getRootItem());
		a.showFragment(getFragmentId());
	}

	private void failedToAddSource(Throwable ex) {
		getMainActivity().showFragment(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.tv_fragment);
		if (isCancellation(ex)) return;

		App.get().getHandler().post(() -> {
			String msg = ex.getLocalizedMessage();
			UiUtils.showAlert(getContext(),
					getString(R.string.err_failed_to_add_tv_source, (msg != null) ? msg : ex.toString()));
		});
	}

	private boolean isRootItem() {
		BrowsableItem p = getAdapter().getParent();
		return (p == null) || (p instanceof TvRootItem);
	}

	private void openSeriesAsList(TvM3uTrackItem track) {
		TvM3uFile source = findSource(track);
		String tvgId = track.getTvgId();

		if ((source == null) || (tvgId == null) || !tvgId.startsWith("xts:")) {
			getMainActivity().getMediaServiceBinder().playItem(track);
			return;
		}

		String seriesId = tvgId.substring(4).trim();
		if (TextUtils.isEmpty(seriesId)) {
			getMainActivity().getMediaServiceBinder().playItem(track);
			return;
		}

		Item parent = track.getParent();
		if (!(parent instanceof BrowsableItem)) {
			getMainActivity().getMediaServiceBinder().playItem(track);
			return;
		}

		XtreamSeriesItem series = new XtreamSeriesItem((BrowsableItem) parent, source, track,
				seriesId, track.getName());
		getAdapter().setParent(series).onFailure(err -> {
			if (!isCancellation(err)) UiUtils.showAlert(getContext(), err.getLocalizedMessage());
		});
	}

	private void playSeriesEpisode(XtreamEpisodeItem episode) {
		TvXtreamEpisodeItemFactory.create(episode.baseTrack, episode.url, episode.title).main()
				.onCompletion((item, err) -> {
					if (err != null) {
						UiUtils.showAlert(getContext(), err.getLocalizedMessage());
						return;
					}
					if (item != null) getMainActivity().getMediaServiceBinder().playItem(item);
				});
	}

	private TvM3uFile findSource(TvM3uTrackItem track) {
		Item parent = track.getParent();
		if (parent instanceof TvM3uGroupItem) parent = ((TvM3uGroupItem) parent).getParent();
		if (parent instanceof TvM3uItem) return ((TvM3uItem) parent).getResource();
		return null;
	}


	private Map<Integer, List<SeriesEpisode>> loadSeriesEpisodes(String baseUrl,
											 String username,
											 String password,
											 String seriesId) {
		String b = baseUrl.trim();
		while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
		String api = b + "/player_api.php?username=" + enc(username) + "&password=" + enc(password)
				+ "&action=get_series_info&series_id=" + enc(seriesId);
		String body = request(api);
		JSONObject response;
		try {
			response = new JSONObject(body);
		} catch (Exception ex) {
			throw new IllegalStateException("Invalid series JSON response", ex);
		}
		JSONObject episodes = response.optJSONObject("episodes");
		Map<Integer, List<SeriesEpisode>> bySeason = new LinkedHashMap<>();

		if (episodes != null) {
			for (Iterator<String> it = episodes.keys(); it.hasNext(); ) {
				String seasonKey = it.next();
				JSONArray seasonEpisodes = episodes.optJSONArray(seasonKey);
				if (seasonEpisodes == null) continue;
				int fallbackSeason = parseSeasonNumber(seasonKey, 1);

				for (int i = 0; i < seasonEpisodes.length(); i++) {
					JSONObject ep = seasonEpisodes.optJSONObject(i);
					if (ep == null) continue;
					addEpisode(bySeason, ep, fallbackSeason, i, b, username, password);
				}
			}
		} else {
			JSONArray arr = response.optJSONArray("episodes");
			if (arr == null) return Collections.emptyMap();
			for (int i = 0; i < arr.length(); i++) {
				JSONObject ep = arr.optJSONObject(i);
				if (ep == null) continue;
				addEpisode(bySeason, ep, 1, i, b, username, password);
			}
		}

		for (List<SeriesEpisode> list : bySeason.values()) {
			list.sort(Comparator.comparingInt(a -> a.number));
		}

		return bySeason;
	}

	private void addEpisode(Map<Integer, List<SeriesEpisode>> bySeason,
							 JSONObject ep,
							 int fallbackSeason,
							 int index,
							 String baseUrl,
							 String username,
							 String password) {
		String id = ep.optString("id", "").trim();
		if (id.isEmpty()) return;

		int epNum = ep.optInt("episode_num", index + 1);
		String title = ep.optString("title", "").trim();
		if (title.isEmpty()) title = "Episode " + epNum;
		String ext = ep.optString("container_extension", "mp4").trim();
		if (ext.isEmpty()) ext = "mp4";
		int season = ep.optInt("season", ep.optInt("season_num", fallbackSeason));
		if (season <= 0) season = fallbackSeason;

		String url = baseUrl + "/series/" + enc(username) + "/" + enc(password) + "/" + id + '.' + ext;
		bySeason.computeIfAbsent(season, k -> new ArrayList<>())
				.add(new SeriesEpisode(title, url, epNum));
	}

	private int parseSeasonNumber(String seasonKey, int fallback) {
		if (seasonKey == null) return fallback;
		String key = seasonKey.trim();
		if (key.isEmpty()) return fallback;

		try {
			int season = Integer.parseInt(key);
			return (season > 0) ? season : fallback;
		} catch (NumberFormatException ignored) {
		}

		StringBuilder digits = new StringBuilder();
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (Character.isDigit(c)) digits.append(c);
		}

		if (digits.length() == 0) return fallback;
		try {
			int season = Integer.parseInt(digits.toString());
			return (season > 0) ? season : fallback;
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private String request(String url) {
		HttpURLConnection c = null;
		try {
			c = (HttpURLConnection) new URL(url).openConnection();
			c.setRequestMethod("GET");
			c.setConnectTimeout(20000);
			c.setReadTimeout(20000);
			int code = c.getResponseCode();
			InputStream in = (code >= 200 && code < 400) ? c.getInputStream() : c.getErrorStream();
			if (in == null) throw new IllegalStateException("HTTP " + code);
			StringBuilder sb = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				for (String line = r.readLine(); line != null; line = r.readLine()) sb.append(line);
			}
			if (code < 200 || code >= 400) throw new IllegalStateException("HTTP " + code + ": " + sb);
			return sb.toString();
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to load series info: " + ex.getMessage(), ex);
		} finally {
			if (c != null) c.disconnect();
		}
	}

	private String enc(String value) {
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
		} catch (Exception ex) {
			return value;
		}
	}

	private static final class XtreamCatchupProgram {
		final String title;
		final long startMs;
		final long endMs;
		final String url;

		XtreamCatchupProgram(String title, long startMs, long endMs, String url) {
			this.title = title;
			this.startMs = startMs;
			this.endMs = endMs;
			this.url = url;
		}
	}

	private static final class SeriesEpisode {
		final String title;
		final String url;
		final int number;

		SeriesEpisode(String title, String url, int number) {
			this.title = title;
			this.url = url;
			this.number = number;
		}

		@NonNull
		@Override
		public String toString() {
			return title;
		}
	}

	private final class XtreamSeriesItem extends BrowsableItemBase implements TvItem {
		private final TvM3uFile source;
		private final TvM3uTrackItem baseTrack;
		private final String seriesId;
		private final String title;

		XtreamSeriesItem(BrowsableItem parent, TvM3uFile source, TvM3uTrackItem baseTrack,
						 String seriesId, String title) {
			super(baseTrack.getId() + ":xts-list:" + seriesId, parent, null);
			this.source = source;
			this.baseTrack = baseTrack;
			this.seriesId = seriesId;
			this.title = title;
		}

		@Override
		protected FutureSupplier<List<Item>> listChildren() {
			String baseUrl = source.getXtreamServer();
			String username = source.getXtreamUsername();
			String password = source.getXtreamPassword();

			if (TextUtils.isEmpty(baseUrl) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
				return completedEmptyList();
			}

			return App.get().execute(() -> loadSeriesEpisodes(baseUrl, username, password, seriesId)).map(bySeason -> {
				if ((bySeason == null) || bySeason.isEmpty()) return Collections.emptyList();
				List<Integer> seasons = new ArrayList<>(bySeason.keySet());
				seasons.sort(Comparator.naturalOrder());
				List<Item> items = new ArrayList<>(seasons.size());

				for (Integer season : seasons) {
					List<SeriesEpisode> episodes = bySeason.get(season);
					if ((episodes == null) || episodes.isEmpty()) continue;
					items.add(new XtreamSeasonItem(this, baseTrack, season, episodes));
				}

				return items;
			});
		}

		@Override
		protected FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs) {
			return completed(title);
		}

		@Override
		protected FutureSupplier<String> buildSubtitle() {
			return getUnsortedChildren().map(children -> "Seasons: " + children.size());
		}

		@Override
		public int getIcon() {
			return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.tv;
		}
	}

	private final class XtreamSeasonItem extends BrowsableItemBase implements TvItem {
		private final TvM3uTrackItem baseTrack;
		private final int season;
		private final List<SeriesEpisode> episodes;

		XtreamSeasonItem(BrowsableItem parent, TvM3uTrackItem baseTrack, int season,
						 List<SeriesEpisode> episodes) {
			super(baseTrack.getId() + ":xts-season:" + season, parent, null);
			this.baseTrack = baseTrack;
			this.season = season;
			this.episodes = episodes;
		}

		@Override
		protected FutureSupplier<List<Item>> listChildren() {
			if ((episodes == null) || episodes.isEmpty()) return completedEmptyList();
			List<Item> items = new ArrayList<>(episodes.size());
			for (SeriesEpisode ep : episodes) {
				items.add(new XtreamEpisodeItem(this, baseTrack, season, ep));
			}
			return completed(items);
		}

		@Override
		protected FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs) {
			return completed("Season " + season);
		}

		@Override
		protected FutureSupplier<String> buildSubtitle() {
			return completed("Episodes: " + ((episodes == null) ? 0 : episodes.size()));
		}

		@Override
		public int getIcon() {
			return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.tv;
		}
	}

	private final class XtreamEpisodeItem extends ItemBase implements TvItem {
		private final TvM3uTrackItem baseTrack;
		private final String title;
		private final String url;
		private final int season;
		private final int episode;

		XtreamEpisodeItem(BrowsableItem parent, TvM3uTrackItem baseTrack, int season,
						  SeriesEpisode episodeInfo) {
			super(baseTrack.getId() + ":xts-episode:" + season + ':' + episodeInfo.number + ':'
						+ Math.abs(episodeInfo.url.hashCode()), parent, null);
			this.baseTrack = baseTrack;
			this.title = episodeInfo.title;
			this.url = episodeInfo.url;
			this.season = season;
			this.episode = episodeInfo.number;
		}

		@Override
		protected FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs) {
			return completed(title);
		}

		@Override
		protected FutureSupplier<String> buildSubtitle() {
			return completed("Season " + season + " • Episode " + episode);
		}

		@Override
		public int getIcon() {
			return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.play;
		}

		@NonNull
		@Override
		public FutureSupplier<Uri> getIconUri() {
			BrowsableItem p = getParent();
			return (p != null) ? p.getIconUri() : completedNull();
		}
	}

	private class TvAdapter extends ListAdapter {

		TvAdapter(MainActivityDelegate activity, BrowsableItem parent) {
			super(activity, parent);
			animateAddButton(parent);
		}

		@Override
		public void onClick(android.view.View v) {
			if (v instanceof MediaItemView) {
				Item i = ((MediaItemView) v).getItem();
				if (i instanceof TvM3uTrackItem) {
					TvM3uTrackItem t = (TvM3uTrackItem) i;
						String tvgId = t.getTvgId();
					if ((tvgId != null) && tvgId.startsWith("xts:")) {
						openSeriesAsList(t);
						return;
					}
				} else if (i instanceof XtreamEpisodeItem) {
					playSeriesEpisode((XtreamEpisodeItem) i);
					return;
				}
			}
			super.onClick(v);
		}

		@Override
		public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction) {
			return super.setParent(parent, userAction).onSuccess(v -> {
				animateAddButton(parent);
			});
		}

		public boolean isLongPressDragEnabled() {
			return isRootItem();
		}

		@Override
		protected void onItemDismiss(int position) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof TvRootItem) ((TvRootItem) i).removeItem(position);
			super.onItemDismiss(position);
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof MediaLib.Folders) ((MediaLib.Folders) i).moveItem(fromPosition, toPosition);
			return super.onItemMove(fromPosition, toPosition);
		}

		private void animateAddButton(BrowsableItem parent) {
			if (!(parent instanceof TvRootItem)) return;

			parent.getUnsortedChildren().onSuccess(c -> {
				if (!c.isEmpty()) return;

				FloatingButton fb = getMainActivity().getFloatingButton();
				fb.requestFocus();
				Animation shake =
						AnimationUtils.loadAnimation(getContext(), me.aap.utils.R.anim.shake_y_20);
				fb.startAnimation(shake);
			});
		}
	}
}
