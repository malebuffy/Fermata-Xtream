package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.utils.log.Log;
import me.aap.utils.text.TextUtils;

/** Indexes photos and videos from MediaStore across all device folders. */
final class LocalMediaScanner {
	private static final long CACHE_MS = 60_000L;
	private static final String DCIM = "DCIM";

	private static LocalMediaScanner instance;

	static synchronized LocalMediaScanner get(Context context) {
		if (instance == null) instance = new LocalMediaScanner(context.getApplicationContext());
		return instance;
	}

	private final Context context;
	private final File storageRoot;
	private final File dcimRoot;
	private final List<MediaFile> photos = new ArrayList<>();
	private final List<MediaFile> videos = new ArrayList<>();
	private final List<File> photoRoots = new ArrayList<>();
	private final List<File> videoRoots = new ArrayList<>();
	private final Map<String, List<MediaFile>> photoFilesByFolder = new HashMap<>();
	private final Map<String, List<MediaFile>> videoFilesByFolder = new HashMap<>();
	private final Map<String, Set<String>> photoSubfolders = new HashMap<>();
	private final Map<String, Set<String>> videoSubfolders = new HashMap<>();
	private final AtomicBoolean scanning = new AtomicBoolean(false);
	private final List<Runnable> pendingCallbacks = new ArrayList<>();
	private long lastScanMs;

	private LocalMediaScanner(Context context) {
		this.context = context;
		storageRoot = Environment.getExternalStorageDirectory();
		dcimRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
	}

	boolean isScanning() {
		return scanning.get();
	}

	boolean isFresh() {
		return (lastScanMs > 0) && ((System.currentTimeMillis() - lastScanMs) < CACHE_MS);
	}

	synchronized void scan(Runnable onComplete) {
		if (onComplete != null) pendingCallbacks.add(onComplete);
		if (scanning.get()) return;
		scanning.set(true);
		new Thread(this::runScan, "local-media-scan").start();
	}

	private void runScan() {
		try {
			doScan();
			lastScanMs = System.currentTimeMillis();
		} catch (Exception ex) {
			Log.e(ex, "Local media scan failed");
		} finally {
			List<Runnable> callbacks;
			synchronized (this) {
				scanning.set(false);
				callbacks = new ArrayList<>(pendingCallbacks);
				pendingCallbacks.clear();
			}
			for (Runnable cb : callbacks) {
				try {
					cb.run();
				} catch (Exception ex) {
					Log.e(ex, "Local media scan callback failed");
				}
			}
		}
	}

	private void doScan() {
		List<MediaFile> nextPhotos = new ArrayList<>();
		List<MediaFile> nextVideos = new ArrayList<>();
		Map<String, List<MediaFile>> nextPhotoFiles = new HashMap<>();
		Map<String, List<MediaFile>> nextVideoFiles = new HashMap<>();
		Map<String, Set<String>> nextPhotoSubs = new HashMap<>();
		Map<String, Set<String>> nextVideoSubs = new HashMap<>();

		queryImages(nextPhotos, nextPhotoFiles, nextPhotoSubs);
		queryVideos(nextVideos, nextVideoFiles, nextVideoSubs);

		nextPhotos.sort(BY_DATE);
		nextVideos.sort(BY_DATE);

		List<File> nextPhotoRoots = buildFolderRoots(nextPhotoSubs, nextPhotoFiles);
		List<File> nextVideoRoots = buildFolderRoots(nextVideoSubs, nextVideoFiles);

		synchronized (this) {
			photos.clear();
			photos.addAll(nextPhotos);
			videos.clear();
			videos.addAll(nextVideos);
			photoFilesByFolder.clear();
			photoFilesByFolder.putAll(nextPhotoFiles);
			videoFilesByFolder.clear();
			videoFilesByFolder.putAll(nextVideoFiles);
			photoSubfolders.clear();
			photoSubfolders.putAll(nextPhotoSubs);
			videoSubfolders.clear();
			videoSubfolders.putAll(nextVideoSubs);
			photoRoots.clear();
			photoRoots.addAll(nextPhotoRoots);
			videoRoots.clear();
			videoRoots.addAll(nextVideoRoots);
		}

		Log.d("Local media scan: photos=", nextPhotos.size(), ", videos=", nextVideos.size(),
				", roots=", nextVideoRoots.size());
	}

	private void queryImages(List<MediaFile> out, Map<String, List<MediaFile>> byFolder,
			Map<String, Set<String>> subfolders) {
		Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		String[] projection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
				? new String[]{
				MediaStore.Images.Media._ID,
				MediaStore.Images.Media.DISPLAY_NAME,
				MediaStore.Images.Media.DATE_MODIFIED,
				MediaStore.Images.Media.RELATIVE_PATH,
				MediaStore.Images.Media.DATA
		}
				: new String[]{
				MediaStore.Images.Media._ID,
				MediaStore.Images.Media.DISPLAY_NAME,
				MediaStore.Images.Media.DATE_MODIFIED,
				MediaStore.Images.Media.DATA
		};

		try (Cursor c = context.getContentResolver().query(collection, projection, null, null,
				MediaStore.Images.Media.DATE_MODIFIED + " DESC")) {
			if (c == null) return;
			int idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
			int nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
			int dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
			int relCol = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH);
			int dataCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			while (c.moveToNext()) {
				addEntry(out, byFolder, subfolders, false, collection, c.getLong(idCol),
						c.getString(nameCol), c.getLong(dateCol),
						relCol >= 0 ? c.getString(relCol) : null, c.getString(dataCol));
			}
		} catch (Exception ex) {
			Log.e(ex, "Failed to query images from MediaStore");
		}
	}

	private void queryVideos(List<MediaFile> out, Map<String, List<MediaFile>> byFolder,
			Map<String, Set<String>> subfolders) {
		Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
		String[] projection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
				? new String[]{
				MediaStore.Video.Media._ID,
				MediaStore.Video.Media.DISPLAY_NAME,
				MediaStore.Video.Media.DATE_MODIFIED,
				MediaStore.Video.Media.RELATIVE_PATH,
				MediaStore.Video.Media.DATA
		}
				: new String[]{
				MediaStore.Video.Media._ID,
				MediaStore.Video.Media.DISPLAY_NAME,
				MediaStore.Video.Media.DATE_MODIFIED,
				MediaStore.Video.Media.DATA
		};

		try (Cursor c = context.getContentResolver().query(collection, projection, null, null,
				MediaStore.Video.Media.DATE_MODIFIED + " DESC")) {
			if (c == null) return;
			int idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
			int nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
			int dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED);
			int relCol = c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH);
			int dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
			while (c.moveToNext()) {
				addEntry(out, byFolder, subfolders, true, collection, c.getLong(idCol),
						c.getString(nameCol), c.getLong(dateCol),
						relCol >= 0 ? c.getString(relCol) : null, c.getString(dataCol));
			}
		} catch (Exception ex) {
			Log.e(ex, "Failed to query videos from MediaStore");
		}
	}

	private void addEntry(List<MediaFile> out, Map<String, List<MediaFile>> byFolder,
			Map<String, Set<String>> subfolders, boolean video, Uri collection, long id,
			String name, long modifiedSec, @Nullable String relativePath, @Nullable String dataPath) {
		if (TextUtils.isNullOrBlank(name)) return;
		String folderKey = folderKey(relativePath, dataPath);
		if (folderKey == null) return;

		Uri uri = ContentUris.withAppendedId(collection, id);
		File file = !TextUtils.isNullOrBlank(dataPath) ? new File(dataPath) : null;
		long modified = modifiedSec > 0 ? modifiedSec * 1000L : System.currentTimeMillis();
		MediaFile media = new MediaFile(uri, file, name, video, modified, folderKey);
		out.add(media);
		byFolder.computeIfAbsent(folderKey, k -> new ArrayList<>()).add(media);
		registerSubfolder(subfolders, folderKey);
	}

	@Nullable
	private String folderKey(@Nullable String relativePath, @Nullable String dataPath) {
		String key = normalizeFolder(relativePath);
		if (key != null) return key;
		if (TextUtils.isNullOrBlank(dataPath) || storageRoot == null) return null;
		try {
			String rootPath = storageRoot.getCanonicalPath();
			String filePath = new File(dataPath).getCanonicalPath();
			if (!filePath.startsWith(rootPath + "/") && !filePath.equals(rootPath)) return null;
			String suffix = filePath.equals(rootPath) ? "" : filePath.substring(rootPath.length() + 1);
			int slash = suffix.lastIndexOf('/');
			if (slash >= 0) suffix = suffix.substring(0, slash);
			return suffix.isEmpty() ? null : suffix.replace('\\', '/');
		} catch (Exception ignored) {
		}
		return null;
	}

	@Nullable
	private static String normalizeFolder(@Nullable String relativePath) {
		if (TextUtils.isNullOrBlank(relativePath)) return null;
		String path = relativePath.replace('\\', '/');
		while (path.startsWith("/")) path = path.substring(1);
		while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
		return path.isEmpty() ? null : path;
	}

	private static void registerSubfolder(Map<String, Set<String>> subfolders, String folderKey) {
		String current = folderKey;
		while (true) {
			int slash = current.lastIndexOf('/');
			if (slash <= 0) {
				// Top-level folder — register under virtual "" parent for root listing.
				subfolders.computeIfAbsent("", k -> new TreeSet<>(BY_PATH)).add(current);
				return;
			}
			String parent = current.substring(0, slash);
			subfolders.computeIfAbsent(parent, k -> new TreeSet<>(BY_PATH)).add(current);
			current = parent;
		}
	}

	private List<File> buildFolderRoots(Map<String, Set<String>> subfolders,
			Map<String, List<MediaFile>> filesByFolder) {
		Set<String> paths = new LinkedHashSet<>(subfolders.getOrDefault("", Collections.emptySet()));
		// Also expose any top-level keys that only contain files directly.
		for (String key : filesByFolder.keySet()) {
			int slash = key.indexOf('/');
			paths.add(slash < 0 ? key : key.substring(0, slash));
		}
		List<File> roots = new ArrayList<>();
		for (String path : paths) {
			if (TextUtils.isNullOrBlank(path)) continue;
			File folder = toFolder(path);
			if (folder != null) roots.add(folder);
		}
		roots.sort(BY_NAME);
		return roots;
	}

	@Nullable
	File getDcimRoot() {
		return (dcimRoot != null && dcimRoot.isDirectory()) ? dcimRoot : null;
	}

	synchronized List<MediaFile> allPhotos() {
		return Collections.unmodifiableList(new ArrayList<>(photos));
	}

	synchronized List<MediaFile> allVideos() {
		return Collections.unmodifiableList(new ArrayList<>(videos));
	}

	synchronized List<File> folderRoots(boolean videos) {
		return Collections.unmodifiableList(new ArrayList<>(videos ? videoRoots : photoRoots));
	}

	synchronized List<FolderEntry> listFolder(File folder, boolean photos, boolean videos) {
		String key = toFolderKey(folder);
		if (key == null) return List.of();

		List<FolderEntry> entries = new ArrayList<>();
		if (photos) addFolderEntries(entries, key, photoSubfolders, photoFilesByFolder, false);
		if (videos) addFolderEntries(entries, key, videoSubfolders, videoFilesByFolder, true);

		entries.sort((a, b) -> {
			if (a.isFolder() != b.isFolder()) return a.isFolder() ? -1 : 1;
			return String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name());
		});
		return entries;
	}

	private void addFolderEntries(List<FolderEntry> entries, String key,
			Map<String, Set<String>> subfolders, Map<String, List<MediaFile>> filesByFolder,
			boolean video) {
		for (String sub : subfolders.getOrDefault(key, Collections.emptySet())) {
			int count = countInTree(sub, filesByFolder, subfolders);
			File folder = toFolder(sub);
			if ((folder != null) && (count > 0)) entries.add(FolderEntry.folder(folder, count));
		}
		for (MediaFile media : filesByFolder.getOrDefault(key, Collections.emptyList())) {
			if (media.video == video) entries.add(FolderEntry.media(media));
		}
	}

	synchronized int countMedia(File folder, boolean photos, boolean videos) {
		String key = toFolderKey(folder);
		if (key == null) return 0;
		int count = 0;
		if (photos) count += countInTree(key, photoFilesByFolder, photoSubfolders);
		if (videos) count += countInTree(key, videoFilesByFolder, videoSubfolders);
		return count;
	}

	private int countInTree(String key, Map<String, List<MediaFile>> filesByFolder,
			Map<String, Set<String>> subfolders) {
		int count = filesByFolder.getOrDefault(key, Collections.emptyList()).size();
		for (String sub : subfolders.getOrDefault(key, Collections.emptySet())) {
			count += countInTree(sub, filesByFolder, subfolders);
		}
		return count;
	}

	@Nullable
	private String toFolderKey(File folder) {
		if (folder == null || storageRoot == null) return null;
		try {
			String rootPath = storageRoot.getCanonicalPath();
			String path = folder.getCanonicalPath();
			if (path.equals(rootPath)) return null;
			if (path.startsWith(rootPath + "/")) {
				return path.substring(rootPath.length() + 1).replace('\\', '/');
			}
		} catch (Exception ex) {
			Log.e(ex, "Failed to resolve folder key ", folder);
		}
		return null;
	}

	@Nullable
	private File toFolder(String folderKey) {
		if (TextUtils.isNullOrBlank(folderKey) || storageRoot == null) return null;
		return new File(storageRoot, folderKey);
	}

	private static final Comparator<MediaFile> BY_DATE =
			(a, b) -> Long.compare(b.modified, a.modified);

	private static final Comparator<File> BY_NAME =
			(a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName());

	private static final Comparator<String> BY_PATH = String.CASE_INSENSITIVE_ORDER;

	static final class MediaFile {
		final Uri uri;
		final File file;
		final String name;
		final boolean video;
		final long modified;
		final String folderKey;

		MediaFile(Uri uri, File file, String name, boolean video, long modified, String folderKey) {
			this.uri = uri;
			this.file = file;
			this.name = name;
			this.video = video;
			this.modified = modified;
			this.folderKey = folderKey;
		}

		Uri uri() {
			return uri;
		}
	}

	static final class FolderEntry {
		private final File folder;
		private final MediaFile media;
		private final int count;

		private FolderEntry(File folder, MediaFile media, int count) {
			this.folder = folder;
			this.media = media;
			this.count = count;
		}

		static FolderEntry folder(File folder, int count) {
			return new FolderEntry(folder, null, count);
		}

		static FolderEntry media(MediaFile media) {
			return new FolderEntry(null, media, 0);
		}

		boolean isFolder() {
			return folder != null;
		}

		String name() {
			if (folder != null) return folder.getName();
			return (media != null) ? media.name : "";
		}

		File getFolder() {
			return folder;
		}

		MediaFile media() {
			return media;
		}

		int count() {
			return count;
		}
	}
}
