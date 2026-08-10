package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.content.Context;
import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import static me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.pref.MediaPrefs.SCALE_BEST;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.IntentPlayable;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.utils.log.Log;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.ToolBarView;

/** Custom folder-based gallery with hub, all-media grid, and folder browsing. */
abstract class DeviceGalleryFragment extends MainActivityFragment {
	private enum ViewMode { HUB, ALL_MEDIA, FOLDER_ROOT, FOLDER_CONTENT }

	protected enum MediaScope { PHOTOS, VIDEOS }

	private static final FloatingButton.Mediator NO_FLOATING_BUTTON = new FloatingButton.Mediator() {
		@Override
		public void enable(FloatingButton button, me.aap.utils.ui.fragment.ActivityFragment fragment) {
			button.setVisibility(View.GONE);
		}

		@Override
		public void disable(FloatingButton button) {
			FloatingButton.Mediator.super.disable(button);
			button.setVisibility(View.VISIBLE);
		}
	};

	private final ExecutorService previewLoader = Executors.newFixedThreadPool(4);
	private final List<GridItem> items = new ArrayList<>();
	private final List<GridItem> allItems = new ArrayList<>();
	private LocalMediaScanner scanner;
	private GridAdapter adapter;
	private RecyclerView grid;
	private TextView statusView;
	private LinearLayout filterBar;
	private EditText filterField;
	private String filterText = "";
	private ViewMode mode = ViewMode.HUB;
	private File currentFolder;
	private boolean requestingPermissions;

	protected abstract MediaScope getMediaScope();

	protected abstract int getAllMediaTitleRes();

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		scanner = LocalMediaScanner.get(requireContext());

		LinearLayout root = new LinearLayout(requireContext());
		root.setOrientation(LinearLayout.VERTICAL);
		applyThemeBackground(root, R.attr.appContentBackground, Color.BLACK);

		filterBar = new LinearLayout(requireContext());
		filterBar.setOrientation(LinearLayout.HORIZONTAL);
		filterBar.setGravity(Gravity.CENTER_VERTICAL);
		applyThemeBackground(filterBar, R.attr.appPanelBackground, 0xFF141414);
		filterBar.setPadding(dp(10), dp(6), dp(10), dp(6));
		filterBar.setVisibility(View.GONE);

		filterField = new EditText(requireContext());
		filterField.setHint(R.string.filter);
		filterField.setSingleLine(true);
		filterField.setTextColor(Color.WHITE);
		filterField.setHintTextColor(0xFFAAAAAA);
		filterField.setTextSize(16);
		applyThemeBackground(filterField, R.attr.appPanelBackground, 0xFF222222);
		filterField.setPadding(dp(12), dp(8), dp(12), dp(8));
		filterField.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override public void onTextChanged(CharSequence s, int start, int before, int count) {
				filterText = (s == null) ? "" : s.toString();
				applyNameFilter();
			}

			@Override public void afterTextChanged(Editable s) {
			}
		});
		filterBar.addView(filterField, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		FrameLayout content = new FrameLayout(requireContext());
		applyThemeBackground(content, R.attr.appContentBackground, Color.BLACK);

		grid = new RecyclerView(requireContext());
		applyThemeBackground(grid, R.attr.appContentBackground, Color.BLACK);
		grid.setPadding(dp(8), dp(8), dp(8), dp(8));
		grid.setClipToPadding(false);
		grid.setLayoutManager(createLayoutManager());
		adapter = new GridAdapter();
		grid.setAdapter(adapter);
		content.addView(grid, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		statusView = new TextView(requireContext());
		statusView.setTextColor(Color.WHITE);
		statusView.setTextSize(17);
		statusView.setGravity(Gravity.CENTER);
		statusView.setPadding(dp(24), dp(24), dp(24), dp(24));
		statusView.setVisibility(View.GONE);
		content.addView(statusView, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

		root.addView(filterBar, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		root.addView(content, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
		return root;
	}

	private void applyThemeBackground(View view, int attr, int fallback) {
		TypedValue enabled = new TypedValue();
		if (requireContext().getTheme().resolveAttribute(
				R.attr.isLiquidGlassTheme, enabled, true) && (enabled.data != 0)) {
			TypedValue value = new TypedValue();
			if (requireContext().getTheme().resolveAttribute(attr, value, true)) {
				if (value.resourceId != 0) view.setBackgroundResource(value.resourceId);
				else view.setBackgroundColor(value.data);
				return;
			}
		}
		view.setBackgroundColor(fallback);
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return GalleryToolBarMediator.instance;
	}

	@Override
	public boolean isRootPage() {
		return mode == ViewMode.HUB;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return NO_FLOATING_BUTTON;
	}

	@Override
	public void onStart() {
		super.onStart();
		ensureToolBar();
	}

	@Override
	public void onResume() {
		super.onResume();
		ensureToolBar();
		refreshContent();
	}

	@Override
	public boolean isVideoModeSupported() {
		return true;
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) refreshContent();
	}

	@Override
	public void onDestroy() {
		previewLoader.shutdownNow();
		super.onDestroy();
	}

	@Override
	public boolean onBackPressed() {
		switch (mode) {
			case FOLDER_CONTENT -> {
				File parent = parentFolder(currentFolder);
				if (parent != null) {
					currentFolder = parent;
					mode = ViewMode.FOLDER_CONTENT;
					populateFolderContent();
					return true;
				}
				mode = ViewMode.FOLDER_ROOT;
				populateFolderRoot();
				return true;
			}
			case FOLDER_ROOT, ALL_MEDIA -> {
				mode = ViewMode.HUB;
				populateHub();
				return true;
			}
			default -> {
				return super.onBackPressed();
			}
		}
	}

	@Override
	public CharSequence getTitle() {
		return switch (mode) {
			case ALL_MEDIA -> getString(getAllMediaTitleRes());
			case FOLDER_ROOT -> getString(R.string.gallery_browse_folders);
			case FOLDER_CONTENT -> (currentFolder != null) ? currentFolder.getName() : baseTitle();
			default -> baseTitle();
		};
	}

	private CharSequence baseTitle() {
		return getMediaScope() == MediaScope.VIDEOS ?
				getString(R.string.video_player) : getString(R.string.gallery);
	}

	private GridLayoutManager createLayoutManager() {
		int width = getResources().getConfiguration().screenWidthDp;
		int span = width >= 900 ? 5 : width >= 600 ? 4 : getActivityDelegate().isCarActivityNotMirror() ? 3 : 3;
		return new GridLayoutManager(requireContext(), Math.max(span, 2));
	}

	private void refreshContent() {
		if (!ensureMediaPermissions()) return;

		if (scanner.isFresh()) {
			rebuildCurrentView();
			return;
		}

		if (mode == ViewMode.HUB) {
			populateHub();
			scanner.scan(this::onScanComplete);
			return;
		}

		showStatus(getString(R.string.gallery_scanning));
		items.clear();
		adapter.notifyDataSetChanged();
		scanner.scan(this::onScanComplete);
	}

	private void onScanComplete() {
		if (!isAdded()) return;
		requireActivity().runOnUiThread(this::rebuildCurrentView);
	}

	private boolean ensureMediaPermissions() {
		if (hasMediaPermissions()) {
			requestingPermissions = false;
			return true;
		}

		items.clear();
		adapter.notifyDataSetChanged();
		if (getActivityDelegate().isCarActivityNotMirror()) {
			showStatus(getString(R.string.use_phone_to_grant_perm));
			return false;
		}

		if (requestingPermissions) {
			showStatus(getString(R.string.use_phone_to_grant_perm));
			return false;
		}

		requestingPermissions = true;
		getActivityDelegate().getAppActivity().checkPermissions(getRequiredMediaPermissions())
				.onCompletion((result, fail) -> {
					requestingPermissions = false;
					if (!isAdded()) return;
					requireActivity().runOnUiThread(this::refreshContent);
				});
		return false;
	}

	private void rebuildCurrentView() {
		switch (mode) {
			case ALL_MEDIA -> populateAllMedia();
			case FOLDER_ROOT -> populateFolderRoot();
			case FOLDER_CONTENT -> populateFolderContent();
			default -> populateHub();
		}
	}

	private void populateHub() {
		mode = ViewMode.HUB;
		currentFolder = null;
		setFilterBarVisible(false);
		List<GridItem> next = new ArrayList<>();
		next.add(GridItem.hub(getString(getAllMediaTitleRes()),
				getMediaScope() == MediaScope.VIDEOS ? R.drawable.video_outline : R.drawable.gallery_outline,
				GridItem.Action.ALL_MEDIA));
		next.add(GridItem.hub(getString(R.string.gallery_browse_folders),
				me.aap.utils.R.drawable.folder, GridItem.Action.BROWSE_FOLDERS));
		setDisplayedItems(next);
		showStatus(null);
		updateTitle();
	}

	private void populateAllMedia() {
		mode = ViewMode.ALL_MEDIA;
		currentFolder = null;
		setFilterBarVisible(true);
		List<GridItem> next = new ArrayList<>();
		List<LocalMediaScanner.MediaFile> media = getMediaScope() == MediaScope.VIDEOS ?
				scanner.allVideos() : scanner.allPhotos();
		for (LocalMediaScanner.MediaFile file : media) next.add(GridItem.media(file));
		setDisplayedItems(next);
		showEmptyStatus(R.string.gallery_empty);
		updateTitle();
	}

	private void populateFolderRoot() {
		mode = ViewMode.FOLDER_ROOT;
		currentFolder = null;
		setFilterBarVisible(true);
		List<GridItem> next = new ArrayList<>();
		boolean videos = getMediaScope() == MediaScope.VIDEOS;
		for (File folder : scanner.folderRoots(videos)) {
			int count = scanner.countMedia(folder, !videos, videos);
			next.add(GridItem.folder(folder, count));
		}
		setDisplayedItems(next);
		showEmptyStatus(R.string.gallery_no_folders);
		updateTitle();
	}

	private void populateFolderContent() {
		mode = ViewMode.FOLDER_CONTENT;
		setFilterBarVisible(true);
		if (currentFolder == null) {
			populateFolderRoot();
			return;
		}
		List<GridItem> next = new ArrayList<>();
		boolean videos = getMediaScope() == MediaScope.VIDEOS;
		for (LocalMediaScanner.FolderEntry entry : scanner.listFolder(currentFolder, !videos, videos)) {
			if (entry.isFolder()) next.add(GridItem.folder(entry.getFolder(), entry.count()));
			else next.add(GridItem.media(entry.media()));
		}
		setDisplayedItems(next);
		showEmptyStatus(R.string.gallery_empty);
		updateTitle();
	}

	private void setDisplayedItems(List<GridItem> source) {
		allItems.clear();
		allItems.addAll(source);
		applyNameFilter();
	}

	private void applyNameFilter() {
		items.clear();
		String query = filterText.trim().toLowerCase(Locale.US);
		if (query.isEmpty()) {
			items.addAll(allItems);
		} else {
			for (GridItem item : allItems) {
				if (item.label.toLowerCase(Locale.US).contains(query)) items.add(item);
			}
		}
		if (adapter != null) adapter.notifyDataSetChanged();
		if (mode != ViewMode.HUB) {
			showEmptyStatus(mode == ViewMode.FOLDER_ROOT ?
					R.string.gallery_no_folders : R.string.gallery_empty);
		}
	}

	private void setFilterBarVisible(boolean visible) {
		if (filterBar != null) filterBar.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	private void showEmptyStatus(int emptyRes) {
		showStatus(items.isEmpty() ? getString(emptyRes) : null);
	}

	private void openItem(GridItem item) {
		switch (item.action) {
			case ALL_MEDIA -> populateAllMedia();
			case BROWSE_FOLDERS -> populateFolderRoot();
			case OPEN_FOLDER -> {
				currentFolder = item.folder;
				populateFolderContent();
			}
			case OPEN_MEDIA -> openMedia(item.media);
		}
	}

	private void openMedia(LocalMediaScanner.MediaFile media) {
		if (media == null) return;
		MainActivityDelegate a = getActivityDelegate();
		Uri uri = media.uri();
		if (media.video) {
			IntentPlayable playable = new IntentPlayable(a, uri, true);
			playable.setVideoScalePref(SCALE_BEST);
			a.getBody().playItem(playable);
			return;
		}
		a.showFragment(R.id.image_viewer_fragment, uri);
	}

	private File parentFolder(File folder) {
		if (folder == null) return null;
		boolean videos = getMediaScope() == MediaScope.VIDEOS;
		for (File root : scanner.folderRoots(videos)) {
			try {
				if (root.getCanonicalPath().equals(folder.getCanonicalPath())) return null;
			} catch (Exception ignored) {
			}
		}
		File parent = folder.getParentFile();
		if (parent == null) return null;
		try {
			String parentPath = parent.getCanonicalPath();
			for (File root : scanner.folderRoots(videos)) {
				String rootPath = root.getCanonicalPath();
				if (parentPath.equals(rootPath) || parentPath.startsWith(rootPath + "/")) {
					return parent;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private void showStatus(@Nullable CharSequence message) {
		if (statusView == null || grid == null) return;
		if (message == null || message.length() == 0) {
			statusView.setVisibility(View.GONE);
			grid.setVisibility(View.VISIBLE);
		} else {
			statusView.setText(message);
			statusView.setVisibility(View.VISIBLE);
			grid.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
		}
	}

	private void ensureToolBar() {
		MainActivityDelegate a = getActivityDelegate();
		ToolBarView tb = a.getToolBar();
		if (tb == null) return;
		getToolBarMediator().enable(tb, this);
	}

	private void updateTitle() {
		MainActivityDelegate a = getActivityDelegate();
		ToolBarView tb = a.getToolBar();
		if (tb != null) GalleryToolBarMediator.instance.refresh(tb, this);
		a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
	}

	private boolean hasMediaPermissions() {
		Context ctx = requireContext();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			String perm = getMediaScope() == MediaScope.VIDEOS ?
					Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_MEDIA_IMAGES;
			return ContextCompat.checkSelfPermission(ctx, perm) == PERMISSION_GRANTED;
		}
		return ContextCompat.checkSelfPermission(ctx,
				Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED;
	}

	private String[] getRequiredMediaPermissions() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (getMediaScope() == MediaScope.VIDEOS) {
				return new String[]{Manifest.permission.READ_MEDIA_VIDEO};
			}
			return new String[]{Manifest.permission.READ_MEDIA_IMAGES};
		}
		return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
	}

	private final class GridAdapter extends RecyclerView.Adapter<GridHolder> {
		@NonNull
		@Override
		public GridHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			Context ctx = parent.getContext();
			MaterialCardView card = new MaterialCardView(ctx);
			card.setCardBackgroundColor(0xFF141414);
			card.setStrokeColor(0xFF555555);
			card.setStrokeWidth(dp(1));
			card.setRadius(dp(12));
			card.setClickable(true);
			card.setFocusable(true);
			RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, dp(148));
			params.setMargins(dp(5), dp(5), dp(5), dp(5));
			card.setLayoutParams(params);

			LinearLayout content = new LinearLayout(ctx);
			content.setOrientation(LinearLayout.VERTICAL);
			content.setGravity(Gravity.CENTER);
			ImageView image = new ImageView(ctx);
			image.setScaleType(ImageView.ScaleType.CENTER_CROP);
			content.addView(image, new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
			TextView label = new TextView(ctx);
			label.setTextColor(Color.WHITE);
			label.setTextSize(12);
			label.setSingleLine(true);
			label.setGravity(Gravity.CENTER);
			label.setPadding(dp(4), dp(4), dp(4), dp(6));
			content.addView(label, new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			card.addView(content);
			return new GridHolder(card, image, label);
		}

		@Override
		public void onBindViewHolder(@NonNull GridHolder holder, int position) {
			GridItem item = items.get(position);
			holder.label.setText(item.label);
			holder.image.setTag(item.tag());
			holder.image.setImageResource(item.iconRes);
			holder.image.setColorFilter(Color.WHITE);
			holder.itemView.setContentDescription(item.label);
			holder.itemView.setOnClickListener(v -> openItem(item));

			if (item.media != null) {
				loadPreview(item.media, holder.image);
			} else if (item.folder != null) {
				loadFolderPreview(item.folder, holder.image);
			}
		}

		@Override
		public int getItemCount() {
			return items.size();
		}
	}

	private void loadPreview(LocalMediaScanner.MediaFile media, ImageView target) {
		Object tag = media.uri();
		previewLoader.execute(() -> {
			Bitmap bitmap = media.video ? loadVideoPreview(media) : loadImagePreview(media);
			if (bitmap == null) return;
			target.post(() -> {
				if (tag.equals(target.getTag())) {
					target.clearColorFilter();
					target.setImageBitmap(bitmap);
				}
			});
		});
	}

	private void loadFolderPreview(File folder, ImageView target) {
		boolean videos = getMediaScope() == MediaScope.VIDEOS;
		previewLoader.execute(() -> {
			Bitmap bitmap = findFirstPreview(folder, !videos, videos);
			if (bitmap == null) return;
			target.post(() -> {
				if (folder.equals(target.getTag())) {
					target.clearColorFilter();
					target.setImageBitmap(bitmap);
				}
			});
		});
	}

	private Bitmap findFirstPreview(File folder, boolean photos, boolean videos) {
		for (LocalMediaScanner.FolderEntry entry : scanner.listFolder(folder, photos, videos)) {
			if (entry.isFolder()) {
				Bitmap nested = findFirstPreview(entry.getFolder(), photos, videos);
				if (nested != null) return nested;
			} else if (entry.media() != null) {
				LocalMediaScanner.MediaFile media = entry.media();
				return media.video ? loadVideoPreview(media) : loadImagePreview(media);
			}
		}
		return null;
	}

	private Bitmap loadImagePreview(LocalMediaScanner.MediaFile media) {
		Context ctx = getContext();
		if (ctx == null) return null;
		if ((media.file != null) && media.file.canRead()) return loadImagePreviewFromFile(media.file);
		try (InputStream is = ctx.getContentResolver().openInputStream(media.uri())) {
			if (is == null) return null;
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = 4;
			return BitmapFactory.decodeStream(is, null, options);
		} catch (Exception ex) {
			Log.e(ex, "Failed to decode image preview ", media.uri);
			return null;
		}
	}

	private Bitmap loadVideoPreview(LocalMediaScanner.MediaFile media) {
		Context ctx = getContext();
		if (ctx == null) return null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			try {
				return ctx.getContentResolver().loadThumbnail(media.uri(), new Size(320, 320), null);
			} catch (Exception ignored) {
			}
		}
		if ((media.file != null) && media.file.canRead()) return loadVideoPreviewFromFile(media.file);
		try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
			retriever.setDataSource(ctx, media.uri());
			return retriever.getFrameAtTime(0);
		} catch (Exception ex) {
			Log.e(ex, "Failed to create video preview ", media.uri);
			return null;
		}
	}

	private static Bitmap loadImagePreviewFromFile(File file) {
		try {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(file.getAbsolutePath(), options);
			options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, 320, 320);
			options.inJustDecodeBounds = false;
			return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
		} catch (Exception ex) {
			Log.e(ex, "Failed to decode image preview ", file);
			return null;
		}
	}

	private static Bitmap loadVideoPreviewFromFile(File file) {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				return ThumbnailUtils.createVideoThumbnail(file, new Size(320, 320), null);
			}
		} catch (Exception ignored) {
		}
		try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
			retriever.setDataSource(file.getAbsolutePath());
			return retriever.getFrameAtTime(0);
		} catch (Exception ex) {
			try {
				return ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(),
						android.provider.MediaStore.Video.Thumbnails.MINI_KIND);
			} catch (Exception ignored) {
				Log.e(ex, "Failed to create video preview ", file);
				return null;
			}
		}
	}

	private static int calculateSampleSize(int width, int height, int reqWidth, int reqHeight) {
		int sample = 1;
		if ((height > reqHeight) || (width > reqWidth)) {
			int halfHeight = height / 2;
			int halfWidth = width / 2;
			while ((halfHeight / sample) >= reqHeight && (halfWidth / sample) >= reqWidth) sample *= 2;
		}
		return sample;
	}

	private static final class GridHolder extends RecyclerView.ViewHolder {
		final ImageView image;
		final TextView label;

		GridHolder(View itemView, ImageView image, TextView label) {
			super(itemView);
			this.image = image;
			this.label = label;
		}
	}

	private static final class GridItem {
		enum Action { ALL_MEDIA, BROWSE_FOLDERS, OPEN_FOLDER, OPEN_MEDIA, HUB }

		final Action action;
		final String label;
		final int iconRes;
		final File folder;
		final LocalMediaScanner.MediaFile media;

		private GridItem(Action action, String label, int iconRes, File folder,
				LocalMediaScanner.MediaFile media) {
			this.action = action;
			this.label = label;
			this.iconRes = iconRes;
			this.folder = folder;
			this.media = media;
		}

		static GridItem hub(String label, int iconRes, Action action) {
			return new GridItem(action, label, iconRes, null, null);
		}

		static GridItem folder(File folder, int count) {
			String label = folder.getName() + " (" + count + ")";
			return new GridItem(Action.OPEN_FOLDER, label, me.aap.utils.R.drawable.folder, folder, null);
		}

		static GridItem media(LocalMediaScanner.MediaFile media) {
			int icon = media.video ? R.drawable.video_outline : R.drawable.gallery_outline;
			return new GridItem(Action.OPEN_MEDIA, media.name, icon, null, media);
		}

		Object tag() {
			if (media != null) return media.uri();
			if (folder != null) return folder;
			return label;
		}
	}

	protected int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}
}
