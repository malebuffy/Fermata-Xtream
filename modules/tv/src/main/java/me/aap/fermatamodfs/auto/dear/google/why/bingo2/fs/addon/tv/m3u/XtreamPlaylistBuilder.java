package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.tv.m3u;

import static me.aap.utils.net.http.HttpFileDownloader.AGENT;
import static me.aap.utils.net.http.HttpFileDownloader.RESP_TIMEOUT;

import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.text.TextUtils;

final class XtreamPlaylistBuilder {
	private static final int STREAM_RETRIES = 2;
	private static final long STREAM_RETRY_DELAY_MS = 1500;
	private static final int CATEGORY_FETCH_THREADS = 4;
	private static final String CATCHUP_NAME_SUFFIX = " \u27F3";

	interface ProgressListener {
		void onProgress(String message);
	}

	static String build(PreferenceStore ps, TvM3uFile source) {
		return build(ps, source, null);
	}

	static String build(PreferenceStore ps, TvM3uFile source, ProgressListener progress) {
		report(progress, "Preparing Xtream account...");
		String serverInput = trim(ps.getStringPref(TvM3uFile.XSTREAM_SERVER));
		String username = trim(ps.getStringPref(TvM3uFile.XSTREAM_USERNAME));
		String password = trim(ps.getStringPref(TvM3uFile.XSTREAM_PASSWORD));
		ParsedXtreamInput parsed = parseXtreamInput(serverInput, username, password);
		String baseUrl = parsed.baseUrl;
		username = parsed.username;
		password = parsed.password;
		String userAgent = trim(ps.getStringPref(AGENT));
		int timeout = Math.max(ps.getIntPref(RESP_TIMEOUT), 10);
		int contentType = ps.getIntPref(TvM3uFile.XSTREAM_CONTENT_TYPE);
		if ((contentType < TvM3uFile.XSTREAM_TYPE_CHANNELS)
				|| (contentType > TvM3uFile.XSTREAM_TYPE_SERIES)) {
			contentType = TvM3uFile.XSTREAM_TYPE_CHANNELS;
		}
		boolean includeChannels = contentType == TvM3uFile.XSTREAM_TYPE_CHANNELS;
		boolean includeMovies = contentType == TvM3uFile.XSTREAM_TYPE_MOVIES;
		boolean includeSeries = contentType == TvM3uFile.XSTREAM_TYPE_SERIES;
		boolean allAtOnce = ps.getBooleanPref(TvM3uFile.XSTREAM_ALL_AT_ONCE);

		if (TextUtils.isNullOrBlank(baseUrl) || TextUtils.isNullOrBlank(username)
				|| TextUtils.isNullOrBlank(password)) {
			throw new IllegalArgumentException("Invalid Xtream account settings");
		}
		ps.applyStringPref(TvM3uFile.XSTREAM_SERVER, baseUrl);
		ps.applyStringPref(TvM3uFile.XSTREAM_USERNAME, username);
		ps.applyStringPref(TvM3uFile.XSTREAM_PASSWORD, password);
		ps.applyIntPref(TvM3uFile.XSTREAM_CONTENT_TYPE, contentType);
		source.setXtreamServer(baseUrl);
		source.setXtreamUsername(username);
		source.setXtreamPassword(password);
		source.setXtreamContentType(contentType);
		source.setXtreamIncludeChannels(includeChannels);
		source.setXtreamIncludeMovies(includeMovies);
		source.setXtreamIncludeSeries(includeSeries);
		source.setXtreamAllAtOnce(allAtOnce);

		report(progress, "Logging in...");
		JSONObject account = fetchObject(apiUrl(baseUrl, username, password, null), userAgent, timeout);
		JSONObject userInfo = account.optJSONObject("user_info");
		if ((userInfo == null) || (userInfo.optInt("auth", 0) != 1)) {
			throw new IllegalArgumentException("Xtream account authentication failed");
		}

		source.setXtreamTimezone(resolveTimezoneId(userInfo));

		String liveExt = resolveLiveExtension(userInfo);

		Map<String, String> liveCategories = new HashMap<>();
		Map<String, String> vodCategories = new HashMap<>();
		Map<String, String> seriesCategories = new HashMap<>();

		if (includeChannels) {
			report(progress, "Fetching channel categories...");
			liveCategories = fetchCategories(baseUrl, username, password,
					"get_live_categories", userAgent, timeout);
		}
		if (includeMovies) {
			report(progress, "Fetching movie categories...");
			vodCategories = fetchCategories(baseUrl, username, password,
					"get_vod_categories", userAgent, timeout);
		}
		if (includeSeries) {
			report(progress, "Fetching series categories...");
			seriesCategories = fetchCategories(baseUrl, username, password,
					"get_series_categories", userAgent, timeout);
		}

		String storageKey = storageKey(baseUrl, contentType);
		File outDir = source.getXtreamDataDir();
		File shardDir = new File(outDir, storageKey + "_db");
		if (shardDir.exists()) deleteRecursively(shardDir);
		shardDir.mkdirs();
		File out = new File(outDir, storageKey + ".m3u");
		File index = new File(outDir, storageKey + ".index.tsv");
		String playlistName = source.getName();

		report(progress, "Building disk-backed index...");
		try {
			List<ShardMeta> shards = new ArrayList<>();

			if (includeChannels) {
				if (allAtOnce) {
					report(progress, "Streaming all channels...");
					shards.addAll(buildLiveShardsAllAtOnce(shardDir, baseUrl, username, password, liveExt,
							liveCategories, userAgent, timeout));
				} else {
					report(progress, "Streaming channels by category...");
					shards.addAll(buildLiveShards(shardDir, baseUrl, username, password, liveExt,
							liveCategories, userAgent, timeout, progress));
				}
			}
			if (includeMovies) {
				if (allAtOnce) {
					report(progress, "Streaming all movies...");
					shards.addAll(buildVodShardsAllAtOnce(shardDir, baseUrl, username, password,
							vodCategories, userAgent, timeout));
				} else {
					report(progress, "Streaming movies by category...");
					shards.addAll(buildVodShards(shardDir, baseUrl, username, password,
							vodCategories, userAgent, timeout, progress));
				}
			}
			if (includeSeries) {
				if (allAtOnce) {
					report(progress, "Streaming all series...");
					shards.addAll(buildSeriesShardsAllAtOnce(shardDir, baseUrl, username, password,
							seriesCategories, userAgent, timeout));
				} else {
					report(progress, "Streaming series by category...");
					shards.addAll(buildSeriesShards(shardDir, baseUrl, username, password,
							seriesCategories, userAgent, timeout, progress));
				}
			}

			writeShardIndex(index, shards);
			mergeShards(out, shardDir, shards, playlistName, progress);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Failed to build Xtream playlist: " + errorMessage(ex), ex);
		}

		ps.applyStringPref(TvM3uFile.EPG_URL,
				baseUrl + "/xmltv.php?username=" + enc(username) + "&password=" + enc(password));
		report(progress, "Done");

		return out.getAbsolutePath();
	}

	private static List<ShardMeta> buildLiveShards(File shardDir,
											 String baseUrl,
											 String username,
											 String password,
											 String liveExt,
											 Map<String, String> categories,
											 String userAgent,
											 int timeout,
											 ProgressListener progress) {
		Map<String, String> safeCategories = normalizedCategories(categories);
		List<Map.Entry<String, String>> entries = new ArrayList<>(safeCategories.entrySet());
		return fetchCategoryShards(shardDir, entries, userAgent, timeout, progress, "Channels",
				"channels", "channels", "No channel data was imported",
				(num, categoryId, categoryName, shard) -> {
					String url = apiUrl(baseUrl, username, password, "get_live_streams");
					if (!TextUtils.isNullOrBlank(categoryId)) url += "&category_id=" + enc(categoryId);
					return streamToShard(url, userAgent, timeout, shard,
							(reader, out) -> writeLiveItem(reader, baseUrl, username, password, liveExt,
									safeCategories, out));
				});
	}

	private static List<ShardMeta> buildVodShards(File shardDir,
										 String baseUrl,
										 String username,
										 String password,
										 Map<String, String> categories,
										 String userAgent,
										 int timeout,
										 ProgressListener progress) {
		Map<String, String> safeCategories = normalizedCategories(categories);
		List<Map.Entry<String, String>> entries = new ArrayList<>(safeCategories.entrySet());
		return fetchCategoryShards(shardDir, entries, userAgent, timeout, progress, "Movies",
				"movies", "movies", "No movie data was imported",
				(num, categoryId, categoryName, shard) -> {
					String url = apiUrl(baseUrl, username, password, "get_vod_streams");
					if (!TextUtils.isNullOrBlank(categoryId)) url += "&category_id=" + enc(categoryId);
					return streamToShard(url, userAgent, timeout, shard,
							(reader, out) -> writeVodItem(reader, baseUrl, username, password, safeCategories, out));
				});
	}

	private static List<ShardMeta> buildSeriesShards(File shardDir,
											 String baseUrl,
											 String username,
											 String password,
											 Map<String, String> categories,
											 String userAgent,
											 int timeout,
											 ProgressListener progress) {
		Map<String, String> safeCategories = normalizedCategories(categories);
		List<Map.Entry<String, String>> entries = new ArrayList<>(safeCategories.entrySet());
		return fetchCategoryShards(shardDir, entries, userAgent, timeout, progress, "Series",
				"series", "series", "No series data was imported",
				(num, categoryId, categoryName, shard) -> {
					String url = apiUrl(baseUrl, username, password, "get_series");
					if (!TextUtils.isNullOrBlank(categoryId)) url += "&category_id=" + enc(categoryId);
					return streamToShard(url, userAgent, timeout, shard,
							(reader, out) -> writeSeriesItem(reader, baseUrl, username, password, safeCategories, out));
				});
	}

	@FunctionalInterface
	private interface CategoryShardFetcher {
		int fetch(int num, String categoryId, String categoryName, File shard) throws Exception;
	}

	private static List<ShardMeta> fetchCategoryShards(File shardDir,
													List<Map.Entry<String, String>> entries,
													String userAgent,
													int timeout,
													ProgressListener progress,
													String progressLabel,
													String shardPrefix,
													String type,
													String emptyMessage,
													CategoryShardFetcher fetcher) {
		int total = entries.size();
		if (total == 0) throw new IllegalArgumentException(emptyMessage);

		List<ShardMeta> shards = Collections.synchronizedList(new ArrayList<>());
		ExecutorService pool = Executors.newFixedThreadPool(CATEGORY_FETCH_THREADS);
		try {
			List<Future<?>> tasks = new ArrayList<>(total);
			for (int i = 0; i < total; i++) {
				final int num = i;
				tasks.add(pool.submit(() -> {
					Map.Entry<String, String> c = entries.get(num);
					String categoryId = trim(c.getKey());
					String categoryName = value(trim(c.getValue()), "Uncategorized");
					report(progress, progressLabel + " " + (num + 1) + "/" + total + ": " + categoryName);
					File shard = new File(shardDir,
							String.format(Locale.US, "%s_%05d.m3u", shardPrefix, num));
					try {
						int count = fetcher.fetch(num, categoryId, categoryName, shard);
						if (count > 0) {
							shards.add(new ShardMeta(type, categoryId, categoryName, shard.getName(), count));
						} else {
							shard.delete();
						}
					} catch (Exception ex) {
						shard.delete();
					}
				}));
			}
			for (Future<?> task : tasks) task.get();
		} catch (Exception ex) {
			throw new IllegalArgumentException("Failed to import " + progressLabel.toLowerCase(Locale.US)
					+ ": " + errorMessage(ex), ex);
		} finally {
			pool.shutdownNow();
		}

		if (shards.isEmpty()) throw new IllegalArgumentException(emptyMessage);
		shards.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.categoryName, b.categoryName));
		return new ArrayList<>(shards);
	}

	private static List<ShardMeta> buildLiveShardsAllAtOnce(File shardDir,
														 String baseUrl,
														 String username,
														 String password,
														 String liveExt,
														 Map<String, String> categories,
														 String userAgent,
														 int timeout) throws IOException {
		CategoryShardManager mgr = new CategoryShardManager(shardDir, "channels", categories);
		String url = apiUrl(baseUrl, username, password, "get_live_streams");
		streamArray(url, userAgent, timeout,
				reader -> writeLiveItemToManager(reader, baseUrl, username, password, liveExt, mgr));
		return finishAllAtOnceShards(mgr, "channels", "No channel data was imported");
	}

	private static List<ShardMeta> buildVodShardsAllAtOnce(File shardDir,
														String baseUrl,
														String username,
														String password,
														Map<String, String> categories,
														String userAgent,
													 int timeout) throws IOException {
		CategoryShardManager mgr = new CategoryShardManager(shardDir, "movies", categories);
		String url = apiUrl(baseUrl, username, password, "get_vod_streams");
		streamArray(url, userAgent, timeout,
				reader -> writeVodItemToManager(reader, baseUrl, username, password, mgr));
		return finishAllAtOnceShards(mgr, "movies", "No movie data was imported");
	}

	private static List<ShardMeta> buildSeriesShardsAllAtOnce(File shardDir,
														   String baseUrl,
														   String username,
														   String password,
														   Map<String, String> categories,
														   String userAgent,
														   int timeout) throws IOException {
		CategoryShardManager mgr = new CategoryShardManager(shardDir, "series", categories);
		String url = apiUrl(baseUrl, username, password, "get_series");
		streamArray(url, userAgent, timeout,
				reader -> writeSeriesItemToManager(reader, baseUrl, username, password, mgr));
		return finishAllAtOnceShards(mgr, "series", "No series data was imported");
	}

	private static List<ShardMeta> finishAllAtOnceShards(CategoryShardManager mgr,
													 String type,
													 String emptyMessage) throws IOException {
		List<ShardMeta> shards = mgr.finish(type);
		if (shards.isEmpty()) throw new IllegalArgumentException(emptyMessage);
		return shards;
	}

	private static boolean writeLiveItemToManager(JsonReader reader,
											   String baseUrl,
											   String username,
											   String password,
											   String liveExt,
											   CategoryShardManager mgr) throws Exception {
		String streamType = null;
		String streamId = null;
		String name = null;
		String logo = null;
		String categoryId = null;
		String tvArchive = null;
		String tvArchiveDuration = null;

		while (reader.hasNext()) {
			String key = reader.nextName();
			switch (key) {
				case "stream_type":
					streamType = trim(reader.nextString());
					break;
				case "stream_id":
					streamId = trim(nextAsString(reader));
					break;
				case "name":
					name = trim(nextAsString(reader));
					break;
				case "stream_icon":
					logo = trim(nextAsString(reader));
					break;
				case "category_id":
					categoryId = trim(nextAsString(reader));
					break;
				case "tv_archive":
					tvArchive = trim(nextAsString(reader));
					break;
				case "tv_archive_duration":
					tvArchiveDuration = trim(nextAsString(reader));
					break;
				default:
					reader.skipValue();
			}
		}
		reader.endObject();

		if (!TextUtils.isNullOrBlank(streamType) && !"live".equalsIgnoreCase(streamType)) return false;
		if (TextUtils.isNullOrBlank(streamId)) return false;

		OutputStreamWriter out = mgr.writerFor(categoryId);
		String category = mgr.resolveCategoryName(categoryId);
		String catchupDays = resolveCatchupDays(tvArchive, tvArchiveDuration);
		String displayName = value(name, "Unknown");
		if (!TextUtils.isNullOrBlank(catchupDays)) displayName += CATCHUP_NAME_SUFFIX;
		appendEntry(out,
				displayName,
				baseUrl + "/live/" + enc(username) + "/" + enc(password) + "/" + streamId + "." + liveExt,
				"Channels | " + category,
				logo,
				streamId,
				catchupDays);
		mgr.recordItem(categoryId);
		return true;
	}

	private static boolean writeVodItemToManager(JsonReader reader,
											  String baseUrl,
											  String username,
											  String password,
											  CategoryShardManager mgr) throws Exception {
		String streamType = null;
		String streamId = null;
		String ext = null;
		String name = null;
		String logo = null;
		String categoryId = null;
		String added = null;

		while (reader.hasNext()) {
			String key = reader.nextName();
			switch (key) {
				case "stream_type":
					streamType = trim(nextAsString(reader));
					break;
				case "stream_id":
					streamId = trim(nextAsString(reader));
					break;
				case "container_extension":
					ext = trim(nextAsString(reader));
					break;
				case "name":
					name = trim(nextAsString(reader));
					break;
				case "stream_icon":
					logo = trim(nextAsString(reader));
					break;
				case "category_id":
					categoryId = trim(nextAsString(reader));
					break;
				case "added":
					added = trim(nextAsString(reader));
					break;
				default:
					reader.skipValue();
			}
		}
		reader.endObject();

		if (!TextUtils.isNullOrBlank(streamType) && !"movie".equalsIgnoreCase(streamType)) return false;
		if (TextUtils.isNullOrBlank(streamId)) return false;

		OutputStreamWriter out = mgr.writerFor(categoryId);
		String category = mgr.resolveCategoryName(categoryId);
		appendEntry(out,
				value(name, "Unknown"),
				baseUrl + "/movie/" + enc(username) + "/" + enc(password) + "/" + streamId + "." + value(ext, "mp4"),
				"Movies | " + category,
				logo,
				streamId,
				null,
				added);
		mgr.recordItem(categoryId);
		return true;
	}

	private static boolean writeSeriesItemToManager(JsonReader reader,
												 String baseUrl,
												 String username,
												 String password,
												 CategoryShardManager mgr) throws Exception {
		String seriesId = null;
		String name = null;
		String cover = null;
		String categoryId = null;
		String added = null;

		while (reader.hasNext()) {
			String key = reader.nextName();
			switch (key) {
				case "series_id":
					seriesId = trim(nextAsString(reader));
					break;
				case "name":
					name = trim(nextAsString(reader));
					break;
				case "cover":
					cover = trim(nextAsString(reader));
					break;
				case "category_id":
					categoryId = trim(nextAsString(reader));
					break;
				case "added":
				case "last_modified":
					if (added == null) added = trim(nextAsString(reader));
					else reader.skipValue();
					break;
				default:
					reader.skipValue();
			}
		}
		reader.endObject();

		if (TextUtils.isNullOrBlank(seriesId)) return false;

		OutputStreamWriter out = mgr.writerFor(categoryId);
		String category = mgr.resolveCategoryName(categoryId);
		appendEntry(out,
				value(name, "Series"),
				baseUrl + "/series/" + enc(username) + "/" + enc(password) + "/" + seriesId + ".mp4",
				category,
				cover,
				"xts:" + seriesId,
				null,
				added);
		mgr.recordItem(categoryId);
		return true;
	}

	private static Map<String, String> normalizedCategories(Map<String, String> categories) {
		if ((categories != null) && !categories.isEmpty()) return categories;
		Map<String, String> fallback = new LinkedHashMap<>();
		fallback.put("", "Uncategorized");
		return fallback;
	}

	private static int streamToShard(String url,
								 String userAgent,
								 int timeout,
								 File shard,
								 JsonItemWriter itemWriter) {
		try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(shard, false), StandardCharsets.UTF_8)) {
			return streamArray(url, userAgent, timeout, reader -> itemWriter.write(reader, out));
		} catch (IOException ex) {
			throw new IllegalArgumentException("Failed to write shard file: " + shard.getAbsolutePath(), ex);
		}
	}

	private static int streamArray(String url,
							   String userAgent,
							   int timeout,
							   JsonArrayConsumer consumer) {
		IllegalArgumentException last = null;
		for (int attempt = 0; attempt <= STREAM_RETRIES; attempt++) {
			HttpURLConnection c = null;
			try {
				c = (HttpURLConnection) new URL(url).openConnection();
				c.setInstanceFollowRedirects(true);
				c.setConnectTimeout(timeout * 1000);
				c.setReadTimeout(timeout * 1000);
				c.setRequestMethod("GET");
				if (!TextUtils.isNullOrBlank(userAgent)) c.setRequestProperty("User-Agent", userAgent);

				int code = c.getResponseCode();
				InputStream in = (code >= 200 && code < 400) ? c.getInputStream() : c.getErrorStream();
				if (in == null) throw new IllegalStateException("HTTP " + code);

				if (code < 200 || code >= 400) {
					String body = readStream(in);
					throw new IllegalStateException("HTTP " + code + ": " + body);
				}

				int count = 0;
				try (JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
					reader.setLenient(true);
					reader.beginArray();
					while (reader.hasNext()) {
						reader.beginObject();
						if (consumer.accept(reader)) count++;
					}
					reader.endArray();
				}

				return count;
			} catch (Exception ex) {
				last = new IllegalArgumentException("Xtream request failed for " + redactUrl(url) + ": " + errorMessage(ex), ex);
				if ((attempt >= STREAM_RETRIES) || !isRetryableServerError(last)) break;
				try {
					Thread.sleep(STREAM_RETRY_DELAY_MS);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			} finally {
				if (c != null) c.disconnect();
			}
		}
		throw (last != null) ? last : new IllegalArgumentException("Xtream request failed");
	}

	private static boolean writeLiveItem(JsonReader reader,
									  String baseUrl,
									  String username,
									  String password,
									  String liveExt,
									  Map<String, String> categories,
									  OutputStreamWriter out) throws Exception {
		String streamType = null;
		String streamId = null;
		String name = null;
		String logo = null;
		String categoryId = null;
		String tvArchive = null;
		String tvArchiveDuration = null;

		while (reader.hasNext()) {
			String key = reader.nextName();
			switch (key) {
				case "stream_type":
					streamType = trim(reader.nextString());
					break;
				case "stream_id":
					streamId = trim(nextAsString(reader));
					break;
				case "name":
					name = trim(nextAsString(reader));
					break;
				case "stream_icon":
					logo = trim(nextAsString(reader));
					break;
				case "category_id":
					categoryId = trim(nextAsString(reader));
					break;
				case "tv_archive":
					tvArchive = trim(nextAsString(reader));
					break;
				case "tv_archive_duration":
					tvArchiveDuration = trim(nextAsString(reader));
					break;
				default:
					reader.skipValue();
			}
		}
		reader.endObject();

		if (!TextUtils.isNullOrBlank(streamType) && !"live".equalsIgnoreCase(streamType)) return false;
		if (TextUtils.isNullOrBlank(streamId)) return false;

		String category = value(categories.get(categoryId), "Uncategorized");
		String catchupDays = resolveCatchupDays(tvArchive, tvArchiveDuration);
		String displayName = value(name, "Unknown");
		if (!TextUtils.isNullOrBlank(catchupDays)) displayName += CATCHUP_NAME_SUFFIX;
		appendEntry(out,
				displayName,
				baseUrl + "/live/" + enc(username) + "/" + enc(password) + "/" + streamId + "." + liveExt,
				"Channels | " + category,
				logo,
				streamId,
				catchupDays);
		return true;
	}

	private static boolean writeVodItem(JsonReader reader,
									 String baseUrl,
									 String username,
									 String password,
									 Map<String, String> categories,
									 OutputStreamWriter out) throws Exception {
		String streamType = null;
		String streamId = null;
		String ext = null;
		String name = null;
		String logo = null;
		String categoryId = null;
		String added = null;

		while (reader.hasNext()) {
			String key = reader.nextName();
			switch (key) {
				case "stream_type":
					streamType = trim(nextAsString(reader));
					break;
				case "stream_id":
					streamId = trim(nextAsString(reader));
					break;
				case "container_extension":
					ext = trim(nextAsString(reader));
					break;
				case "name":
					name = trim(nextAsString(reader));
					break;
				case "stream_icon":
					logo = trim(nextAsString(reader));
					break;
				case "category_id":
					categoryId = trim(nextAsString(reader));
					break;
				case "added":
					added = trim(nextAsString(reader));
					break;
				default:
					reader.skipValue();
			}
		}
		reader.endObject();

		if (!TextUtils.isNullOrBlank(streamType) && !"movie".equalsIgnoreCase(streamType)) return false;
		if (TextUtils.isNullOrBlank(streamId)) return false;

		String category = value(categories.get(categoryId), "Uncategorized");
		appendEntry(out,
				value(name, "Unknown"),
				baseUrl + "/movie/" + enc(username) + "/" + enc(password) + "/" + streamId + "." + value(ext, "mp4"),
				"Movies | " + category,
				logo,
				streamId,
				null,
				added);
		return true;
	}

	private static boolean writeSeriesItem(JsonReader reader,
										String baseUrl,
										String username,
										String password,
										Map<String, String> categories,
										OutputStreamWriter out) throws Exception {
		String seriesId = null;
		String name = null;
		String cover = null;
		String categoryId = null;
		String added = null;

		while (reader.hasNext()) {
			String key = reader.nextName();
			switch (key) {
				case "series_id":
					seriesId = trim(nextAsString(reader));
					break;
				case "name":
					name = trim(nextAsString(reader));
					break;
				case "cover":
					cover = trim(nextAsString(reader));
					break;
				case "category_id":
					categoryId = trim(nextAsString(reader));
					break;
				case "added":
				case "last_modified":
					if (added == null) added = trim(nextAsString(reader));
					else reader.skipValue();
					break;
				default:
					reader.skipValue();
			}
		}
		reader.endObject();

		if (TextUtils.isNullOrBlank(seriesId)) return false;

		String category = value(categories.get(categoryId), "Uncategorized");
		appendEntry(out,
				value(name, "Series"),
				baseUrl + "/series/" + enc(username) + "/" + enc(password) + "/" + seriesId + ".mp4",
				category,
				cover,
				"xts:" + seriesId,
				null,
				added);
		return true;
	}

	private static String nextAsString(JsonReader reader) throws IOException {
		switch (reader.peek()) {
			case STRING:
				return reader.nextString();
			case NUMBER:
				return reader.nextString();
			case BOOLEAN:
				return Boolean.toString(reader.nextBoolean());
			case NULL:
				reader.nextNull();
				return null;
			default:
				reader.skipValue();
				return null;
		}
	}

	private static String readStream(InputStream in) throws IOException {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			for (String line = r.readLine(); line != null; line = r.readLine()) sb.append(line);
		}
		return sb.toString();
	}

	private static void writeShardIndex(File index, List<ShardMeta> shards) {
		try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(index, false), StandardCharsets.UTF_8)) {
			out.write("type\tcategory_id\tcategory\tshard\tcount\n");
			for (ShardMeta s : shards) {
				out.write(escape(value(s.type, "")) + '\t');
				out.write(escape(value(s.categoryId, "")) + '\t');
				out.write(escape(value(s.categoryName, "")) + '\t');
				out.write(escape(value(s.shardFile, "")) + '\t');
				out.write(Integer.toString(s.count));
				out.write('\n');
			}
		} catch (IOException ex) {
			throw new IllegalArgumentException("Failed to write Xtream index", ex);
		}
	}

	private static void mergeShards(File playlist,
								  File shardDir,
								  List<ShardMeta> shards,
								  String playlistName,
								  ProgressListener progress) {
		try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(playlist, false), StandardCharsets.UTF_8)) {
			out.write("#EXTM3U\n");
			if (!TextUtils.isNullOrBlank(playlistName)) out.write("#PLAYLIST:" + escape(playlistName) + '\n');
			for (ShardMeta shard : shards) {
				report(progress, "Merging " + shard.type + " | " + shard.categoryName + " (" + shard.count + ")");
				File file = new File(shardDir, shard.shardFile);
				if (!file.exists()) continue;
				try (BufferedReader in = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
					for (String line = in.readLine(); line != null; line = in.readLine()) {
						out.write(line);
						out.write('\n');
					}
				}
			}
		} catch (Exception ex) {
			throw new IllegalArgumentException("Failed to merge Xtream shard files", ex);
		}
	}

	private static void deleteRecursively(File file) {
		if ((file == null) || !file.exists()) return;
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File c : children) deleteRecursively(c);
			}
		}
		file.delete();
	}

	private static void appendEntry(OutputStreamWriter out,
								String name,
								String url,
								String group,
								String logo,
							String tvgId,
							String catchupDays) {
		appendEntry(out, name, url, group, logo, tvgId, catchupDays, null);
	}

	private static void appendEntry(OutputStreamWriter out,
								String name,
								String url,
								String group,
								String logo,
							String tvgId,
							String catchupDays,
							@Nullable String addedUnix) {
		try {
			out.write("#EXTINF:-1");
			if (!TextUtils.isNullOrBlank(group)) out.write(" group-title=\"" + escape(group) + '"');
			if (!TextUtils.isNullOrBlank(logo)) out.write(" tvg-logo=\"" + escape(logo) + '"');
			if (!TextUtils.isNullOrBlank(tvgId)) out.write(" tvg-id=\"" + escape(tvgId) + '"');
			if (!TextUtils.isNullOrBlank(catchupDays)) {
				out.write(" tvg-rec=\"" + escape(catchupDays) + '"');
				out.write(" catchup-days=\"" + escape(catchupDays) + '"');
			}
			if (!TextUtils.isNullOrBlank(addedUnix)) {
				out.write(" added=\"" + escape(addedUnix) + '"');
			}
			out.write(',' + escape(value(name, "Unknown")) + '\n');
			out.write(url);
			out.write('\n');
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to write Xtream playlist entry", ex);
		}
	}

	private static String resolveCatchupDays(String archiveFlag, String archiveDuration) {
		if (!"1".equals(trim(archiveFlag))) return null;
		if (TextUtils.isNullOrBlank(archiveDuration)) return null;

		try {
			double hours = Double.parseDouble(archiveDuration.trim());
			if (hours <= 0d) return null;
			int days = (int) Math.ceil(hours / 24d);
			return (days > 0) ? Integer.toString(days) : null;
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Map<String, String> fetchCategories(String baseUrl,
											String username,
											String password,
											String action,
											String userAgent,
											int timeout) {
		JSONArray arr = fetchArray(apiUrl(baseUrl, username, password, action), userAgent, timeout);
		Map<String, String> map = new HashMap<>();
		for (int i = 0; i < arr.length(); i++) {
			JSONObject o = arr.optJSONObject(i);
			if (o == null) continue;
			String id = trim(o.optString("category_id"));
			String name = trim(o.optString("category_name"));
			if (!TextUtils.isNullOrBlank(id) && !TextUtils.isNullOrBlank(name)) map.put(id, name);
		}
		return map;
	}

	private static JSONArray fetchArray(String url, String userAgent, int timeout) {
		String raw = request(url, userAgent, timeout);
		try {
			return new JSONArray(raw);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Invalid Xtream JSON array response", ex);
		}
	}

	private static JSONArray fetchArrayWithRetry(String url,
										 String userAgent,
										 int timeout,
										 int retries,
										 long delayMs) {
		IllegalArgumentException last = null;

		for (int attempt = 0; attempt <= retries; attempt++) {
			try {
				return fetchArray(url, userAgent, timeout);
			} catch (IllegalArgumentException ex) {
				last = ex;
				if ((attempt >= retries) || !isRetryableServerError(ex)) break;
				try {
					Thread.sleep(delayMs);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}

		throw (last != null) ? last : new IllegalArgumentException("Xtream request failed");
	}

	private static boolean isRetryableServerError(Throwable ex) {
		String msg = errorMessage(ex);
		if (TextUtils.isNullOrBlank(msg)) return false;
		String m = msg.toLowerCase(Locale.US);
		return m.contains("http 500") || m.contains("http 502")
				|| m.contains("http 503") || m.contains("http 504")
				|| m.contains("unexpected end of stream")
				|| m.contains("eof")
				|| m.contains("connection reset")
				|| m.contains("sockettimeout")
				|| m.contains("timed out");
	}

	private static JSONObject fetchObject(String url, String userAgent, int timeout) {
		String raw = request(url, userAgent, timeout);
		try {
			return new JSONObject(raw);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Invalid Xtream JSON object response", ex);
		}
	}

	private static String request(String url, String userAgent, int timeoutSec) {
		HttpURLConnection c = null;
		try {
			c = (HttpURLConnection) new URL(url).openConnection();
			c.setInstanceFollowRedirects(true);
			c.setConnectTimeout(timeoutSec * 1000);
			c.setReadTimeout(timeoutSec * 1000);
			c.setRequestMethod("GET");
			if (!TextUtils.isNullOrBlank(userAgent)) c.setRequestProperty("User-Agent", userAgent);

			int code = c.getResponseCode();
			InputStream in = (code >= 200 && code < 400) ? c.getInputStream() : c.getErrorStream();
			if (in == null) throw new IllegalStateException("HTTP " + code);

			StringBuilder sb = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				for (String line = r.readLine(); line != null; line = r.readLine()) sb.append(line);
			}

			if (code >= 200 && code < 400) return sb.toString();
			throw new IllegalStateException("HTTP " + code + ": " + sb);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Xtream request failed for " + redactUrl(url) + ": " + errorMessage(ex), ex);
		} finally {
			if (c != null) c.disconnect();
		}
	}

	private static String resolveLiveExtension(JSONObject userInfo) {
		JSONArray formats = userInfo.optJSONArray("allowed_output_formats");
		if (formats == null) return "m3u8";
		for (int i = 0; i < formats.length(); i++) {
			String f = trim(formats.optString(i));
			if ("m3u8".equalsIgnoreCase(f) || "ts".equalsIgnoreCase(f)) return f.toLowerCase(Locale.US);
		}
		return "m3u8";
	}

	private static String resolveTimezoneId(JSONObject userInfo) {
		String[] keys = new String[]{"timezone", "server_timezone", "timezone_info", "server_timezone_info"};
		for (String key : keys) {
			String value = trim(userInfo.optString(key, null));
			if (!TextUtils.isNullOrBlank(value)) return normalizeTimezoneId(value);
		}

		int offset = userInfo.optInt("timezone_offset", Integer.MIN_VALUE);
		if (offset == Integer.MIN_VALUE) offset = userInfo.optInt("gmt_offset", Integer.MIN_VALUE);
		if (offset != Integer.MIN_VALUE) return String.format(Locale.US, "GMT%+d:00", offset);

		return TimeZone.getDefault().getID();
	}

	private static String normalizeTimezoneId(String value) {
		String v = value.trim();
		if (v.isEmpty()) return TimeZone.getDefault().getID();
		if (v.startsWith("GMT") || v.startsWith("UTC")) return v;
		return v;
	}

	private static String apiUrl(String baseUrl, String username, String password, String action) {
		StringBuilder u = new StringBuilder(baseUrl)
				.append("/player_api.php?username=").append(enc(username))
				.append("&password=").append(enc(password));
		if (!TextUtils.isNullOrBlank(action)) u.append("&action=").append(action);
		return u.toString();
	}

	private static String enc(String value) {
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
		} catch (Exception ex) {
			return value;
		}
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace('\n', ' ').replace('\r', ' ');
	}

	private static String value(String value, String fallback) {
		return TextUtils.isNullOrBlank(value) ? fallback : value;
	}

	private static String trim(String value) {
		return (value == null) ? null : value.trim();
	}

	static String storageKey(String baseUrl, int contentType) {
		String host = hostKey(baseUrl);
		String suffix;
		if (contentType == TvM3uFile.XSTREAM_TYPE_MOVIES) suffix = "movies";
		else if (contentType == TvM3uFile.XSTREAM_TYPE_SERIES) suffix = "series";
		else suffix = "live";
		return host + "_" + suffix;
	}

	private static String hostKey(String baseUrl) {
		String normalized = normalizeBaseUrl(baseUrl);
		try {
			URI uri = URI.create(normalized);
			String host = trim(uri.getHost());
			if (TextUtils.isNullOrBlank(host)) host = "xtream";
			host = host.toLowerCase(Locale.US);
			int port = uri.getPort();
			return (port > 0) ? (host + "_" + port) : host;
		} catch (Exception ex) {
			return normalized.replaceAll("[^a-zA-Z0-9]+", "_").toLowerCase(Locale.US);
		}
	}

	@NonNull
	private static String normalizeBaseUrl(String url) {
		String v = trim(url);
		if (TextUtils.isNullOrBlank(v)) return "";
		if (!v.contains("://")) v = "http://" + v;

		try {
			URI uri = URI.create(v);
			String scheme = value(uri.getScheme(), "http").toLowerCase(Locale.US);
			String host = trim(uri.getHost());
			if (TextUtils.isNullOrBlank(host)) {
				host = trim(uri.getAuthority());
				if (!TextUtils.isNullOrBlank(host)) {
					int at = host.lastIndexOf('@');
					if (at >= 0) host = host.substring(at + 1);
					int colon = host.indexOf(':');
					if (colon >= 0) host = host.substring(0, colon);
				}
			}
			if (TextUtils.isNullOrBlank(host)) return v.replaceAll("/+$", "");

			host = host.toLowerCase(Locale.US);
			int port = uri.getPort();
			return (port > 0) ? (scheme + "://" + host + ":" + port) : (scheme + "://" + host);
		} catch (Exception ignore) {
			while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
			return v;
		}
	}

	private static ParsedXtreamInput parseXtreamInput(String serverInput, String username, String password) {
		String baseUrl = normalizeBaseUrl(serverInput);
		String parsedUser = username;
		String parsedPass = password;

		String v = trim(serverInput);
		if (!TextUtils.isNullOrBlank(v)) {
			if (!v.contains("://")) v = "http://" + v;
			try {
				URI uri = URI.create(v);
				String query = uri.getRawQuery();
				if (TextUtils.isNullOrBlank(parsedUser)) parsedUser = queryParam(query, "username");
				if (TextUtils.isNullOrBlank(parsedPass)) parsedPass = queryParam(query, "password");
			} catch (Exception ignore) {
			}
		}

		return new ParsedXtreamInput(baseUrl, trim(parsedUser), trim(parsedPass));
	}

	private static String queryParam(String query, String key) {
		if (TextUtils.isNullOrBlank(query) || TextUtils.isNullOrBlank(key)) return null;
		String[] parts = query.split("&");
		for (String part : parts) {
			if (TextUtils.isNullOrBlank(part)) continue;
			int eq = part.indexOf('=');
			String k = (eq < 0) ? part : part.substring(0, eq);
			if (!key.equalsIgnoreCase(k)) continue;
			String raw = (eq < 0) ? "" : part.substring(eq + 1);
			try {
				return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8.name());
			} catch (Exception ex) {
				return raw;
			}
		}
		return null;
	}

	private static final class ParsedXtreamInput {
		final String baseUrl;
		final String username;
		final String password;

		ParsedXtreamInput(String baseUrl, String username, String password) {
			this.baseUrl = baseUrl;
			this.username = username;
			this.password = password;
		}
	}

	private static String errorMessage(Throwable ex) {
		if (ex == null) return "unknown error";
		String m = trim(ex.getMessage());
		if (!TextUtils.isNullOrBlank(m)) return m;
		Throwable c = ex.getCause();
		if (c != null) {
			String cm = trim(c.getMessage());
			if (!TextUtils.isNullOrBlank(cm)) return c.getClass().getSimpleName() + ": " + cm;
			return c.getClass().getSimpleName();
		}
		return ex.getClass().getSimpleName();
	}

	private static String redactUrl(String url) {
		if (TextUtils.isNullOrBlank(url)) return "<empty-url>";
		return url
				.replaceAll("(?i)(username=)[^&]+", "$1***")
				.replaceAll("(?i)(password=)[^&]+", "$1***");
	}

	private static void report(ProgressListener progress, String message) {
		if ((progress != null) && !TextUtils.isNullOrBlank(message)) progress.onProgress(message);
	}

	@FunctionalInterface
	private interface JsonArrayConsumer {
		boolean accept(JsonReader reader) throws Exception;
	}

	@FunctionalInterface
	private interface JsonItemWriter {
		boolean write(JsonReader reader, OutputStreamWriter out) throws Exception;
	}

	private static final class ShardMeta {
		final String type;
		final String categoryId;
		final String categoryName;
		final String shardFile;
		final int count;

		private ShardMeta(String type, String categoryId, String categoryName, String shardFile, int count) {
			this.type = type;
			this.categoryId = categoryId;
			this.categoryName = categoryName;
			this.shardFile = shardFile;
			this.count = count;
		}
	}

	private static final class CategoryShardManager {
		private final File shardDir;
		private final String shardPrefix;
		private final Map<String, String> categories;
		private final Map<String, OpenShard> shards = new LinkedHashMap<>();
		private int nextIdx;

		CategoryShardManager(File shardDir, String shardPrefix, Map<String, String> categories) {
			this.shardDir = shardDir;
			this.shardPrefix = shardPrefix;
			this.categories = normalizedCategories(categories);
		}

		OutputStreamWriter writerFor(String categoryId) throws IOException {
			String key = normalizeCategoryKey(categoryId);
			OpenShard shard = shards.get(key);
			if (shard == null) {
				File file = new File(shardDir,
						String.format(Locale.US, "%s_%05d.m3u", shardPrefix, nextIdx++));
				shard = new OpenShard(key, resolveCategoryName(categoryId), file,
						new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8));
				shards.put(key, shard);
			}
			return shard.out;
		}

		String resolveCategoryName(String categoryId) {
			String key = normalizeCategoryKey(categoryId);
			return value(categories.get(key), categoryDisplayName(key));
		}

		void recordItem(String categoryId) {
			OpenShard shard = shards.get(normalizeCategoryKey(categoryId));
			if (shard != null) shard.count++;
		}

		List<ShardMeta> finish(String type) throws IOException {
			List<ShardMeta> result = new ArrayList<>();
			for (OpenShard shard : shards.values()) {
				shard.out.close();
				if (shard.count > 0) {
					result.add(new ShardMeta(type, shard.categoryId, shard.categoryName,
							shard.file.getName(), shard.count));
				} else {
					shard.file.delete();
				}
			}
			result.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.categoryName, b.categoryName));
			return result;
		}

		static String normalizeCategoryKey(String categoryId) {
			String id = trim(categoryId);
			return TextUtils.isNullOrBlank(id) ? "" : id;
		}

		static String categoryDisplayName(String key) {
			return TextUtils.isNullOrBlank(key) ? "Uncategorized" : key;
		}

		private static final class OpenShard {
			final String categoryId;
			final String categoryName;
			final File file;
			final OutputStreamWriter out;
			int count;

			OpenShard(String categoryId, String categoryName, File file, OutputStreamWriter out) {
				this.categoryId = categoryId;
				this.categoryName = categoryName;
				this.file = file;
				this.out = out;
			}
		}
	}

	private XtreamPlaylistBuilder() {
	}
}
