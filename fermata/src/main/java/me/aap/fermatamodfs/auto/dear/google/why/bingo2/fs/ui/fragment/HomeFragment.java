package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.AddonManager;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.FermataAddon;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.FermataFragmentAddon;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.MediaSessionCallback;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityPrefs;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.ToolBarView;

/** Card-based launcher shared by the phone and projected Android Auto activity. */
public class HomeFragment extends MainActivityFragment
		implements MediaSessionCallback.Listener, PreferenceStore.Listener {
	private static final String WEATHER_PREFS = "home_weather";
	private static final String MENU_PREFS = "home_menu";
	private static final String PREF_USE_FAHRENHEIT = "use_fahrenheit";
	private static final String PREF_CARD_ORDER = "card_order";
	private static final long WEATHER_CACHE_MS = 30 * 60 * 1000L;
	private final ExecutorService weatherExecutor = Executors.newSingleThreadExecutor();
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final ArrayList<HomeCard> cards = new ArrayList<>();
	private final Runnable saveOrderRunnable = this::saveCardOrder;
	private final Runnable voiceStartRunnable = this::startMainMenuVoiceListening;
	private final Runnable voiceMediaCheckRunnable = this::updateVoiceForMediaState;
	private RecyclerView menuList;
	private HomeAdapter menuAdapter;
	private ItemTouchHelper menuTouchHelper;
	private SpeechRecognizer voiceRecognizer;
	private TextView title;
	private TextView temperature;
	private ImageView microphone;
	private boolean dragMode;
	private boolean orderChanged;
	private int mainMenuColor = Color.WHITE;
	private boolean voiceListening;
	private boolean voiceBlockedByMedia;
	private boolean voiceManualListen;
	private int voiceGeneration;
		private static final FloatingButton.Mediator NO_FLOATING_BUTTON = new FloatingButton.Mediator() {
		@Override public void enable(FloatingButton button, me.aap.utils.ui.fragment.ActivityFragment fragment) {
			button.setVisibility(View.GONE);
		}
		@Override public void disable(FloatingButton button) {
			FloatingButton.Mediator.super.disable(button);
			button.setVisibility(View.GONE);
		}
	};

	@Override
	public int getFragmentId() {
		return R.id.home_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return getString(R.string.main_menu);
	}

	@Override public ToolBarView.Mediator getToolBarMediator() {
		return ToolBarView.Mediator.Invisible.instance;
	}

	@Override public FloatingButton.Mediator getFloatingButtonMediator() {
		return NO_FLOATING_BUTTON;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		Context context = requireContext();
		mainMenuColor = getActivityDelegate().getPrefs().getMainMenuColorPref(getActivityDelegate());
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		TypedValue background = new TypedValue();
		if (context.getTheme().resolveAttribute(R.attr.appContentBackground, background, true)) {
			if (background.resourceId != 0) root.setBackgroundResource(background.resourceId);
			else root.setBackgroundColor(background.data);
		} else {
			root.setBackgroundColor(Color.BLACK);
		}
		root.setPadding(dp(18), dp(12), dp(18), dp(18));
		root.addView(createHeader(context), new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

		RecyclerView menuList = new RecyclerView(context);
		this.menuList = menuList;
		menuList.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
		menuList.setClipToPadding(false);
		menuList.setLayoutManager(new GridLayoutManager(context, columnCount(context)));
		loadCards();
		menuAdapter = new HomeAdapter();
		menuList.setAdapter(menuAdapter);
		menuTouchHelper = new ItemTouchHelper(createMenuTouchCallback());
		menuTouchHelper.attachToRecyclerView(menuList);
		root.addView(menuList, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		return root;
	}

	@Override
	public void onResume() {
		super.onResume();
		getActivityDelegate().getMediaSessionCallback().addBroadcastListener(this);
		getActivityDelegate().getPrefs().addBroadcastListener(this);
		applyMainMenuColor();
		refreshTemperature();
		updateVoiceForMediaState();
	}

	@Override
	public void onPause() {
		stopMainMenuVoiceListening();
		handler.removeCallbacks(voiceMediaCheckRunnable);
		getActivityDelegate().getMediaSessionCallback().removeBroadcastListener(this);
		getActivityDelegate().getPrefs().removeBroadcastListener(this);
		super.onPause();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) {
			stopMainMenuVoiceListening();
			handler.removeCallbacks(voiceMediaCheckRunnable);
		} else {
			updateVoiceForMediaState();
		}
	}

	@Override
	public void onDestroy() {
		if (orderChanged) saveCardOrder();
		handler.removeCallbacks(saveOrderRunnable);
		handler.removeCallbacks(voiceMediaCheckRunnable);
		stopMainMenuVoiceListening();
		weatherExecutor.shutdownNow();
		super.onDestroy();
	}

	private View createHeader(Context context) {
		LinearLayout header = new LinearLayout(context);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setOrientation(LinearLayout.HORIZONTAL);

		TextView title = new TextView(context);
		title.setText(R.string.app_name);
		title.setTextColor(mainMenuColor);
		title.setTextSize(24);
		title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
		this.title = title;
		header.addView(title, new LinearLayout.LayoutParams(0,
				ViewGroup.LayoutParams.WRAP_CONTENT, 1));

		temperature = headerText(context, unitPlaceholder());
		temperature.setGravity(Gravity.CENTER);
		temperature.setBackground(outlineBackground(mainMenuColor, dp(18), 1));
		temperature.setPadding(dp(14), dp(7), dp(14), dp(7));
		temperature.setClickable(true);
		temperature.setFocusable(true);
		temperature.setOnClickListener(v -> toggleTemperatureUnit());
		header.addView(temperature, wrapWithMargins(dp(8)));

		ImageView mic = new ImageView(context);
		mic.setImageResource(R.drawable.record_voice);
		mic.setColorFilter(mainMenuColor);
		mic.setBackground(outlineBackground(Color.BLACK, mainMenuColor, dp(18), 1));
		mic.setPadding(dp(10), dp(10), dp(10), dp(10));
		mic.setClickable(true);
		mic.setFocusable(true);
		mic.setContentDescription(getString(R.string.voice_control));
		mic.setOnClickListener(v -> restartMainMenuVoiceListening());
		microphone = mic;
		header.addView(mic, new LinearLayout.LayoutParams(dp(44), dp(44)));
		return header;
	}

	private TextView headerText(Context context, String value) {
		TextView text = new TextView(context);
		text.setText(value);
		text.setTextColor(mainMenuColor);
		text.setTextSize(18);
		return text;
	}

	private LinearLayout.LayoutParams wrapWithMargins(int start) {
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		params.setMarginStart(start);
		return params;
	}

	private void loadCards() {
		cards.clear();
		Set<Integer> ids = new HashSet<>();
		addCard(ids, R.id.folders_fragment, me.aap.utils.R.drawable.folder,
				getString(R.string.folders), null);
		addCard(ids, R.id.favorites_fragment, R.drawable.favorite,
				getString(R.string.favorites), null);
		addCard(ids, R.id.playlists_fragment, R.drawable.playlist,
				getString(R.string.playlists), null);
		addCard(ids, R.id.gallery_fragment, R.drawable.gallery_outline,
				getString(R.string.gallery), null);
		addCard(ids, R.id.video_gallery_fragment, R.drawable.video_outline,
				getString(R.string.video_player), null);

		for (FermataAddon addon : AddonManager.get().getAddons()) {
			if (!(addon instanceof FermataFragmentAddon)) continue;
			Object input = null;
			addCard(ids, addon.getAddonId(), addon.getInfo().icon,
					getString(addon.getInfo().addonName), input);
		}

		addCard(ids, R.id.settings_fragment, R.drawable.settings,
				getString(R.string.settings), null);
		if (!applySavedCardOrder()) normalizeCardOrder();
	}

	private void addCard(Set<Integer> ids, int id, int icon, String label,
			@Nullable Object input) {
		if (!ids.add(id)) return;
		cards.add(new HomeCard(id, icon, label, input));
	}

	private void normalizeCardOrder() {
		List<HomeCard> pinned = new ArrayList<>(4);
		HomeCard settings = null;

		for (Iterator<HomeCard> it = cards.iterator(); it.hasNext(); ) {
			HomeCard c = it.next();
			if (mainMenuPriority(c.id) != -1) {
				pinned.add(c);
				it.remove();
			} else if (c.id == R.id.settings_fragment) {
				settings = c;
				it.remove();
			}
		}

		if (!pinned.isEmpty()) {
			pinned.sort(Comparator.comparingInt(c -> mainMenuPriority(c.id)));
			cards.addAll(0, pinned);
		}

		if (settings != null) cards.add(settings);
	}

	private int mainMenuPriority(int id) {
		if (id == R.id.tv_fragment) return 0;
		if (id == R.id.radio_fragment) return 1;
		if (id == R.id.web_browser_fragment) return 2;
		if (id == R.id.youtube_fragment) return 3;
		if (id == R.id.youtube_music_fragment) return 4;
		if (id == R.id.youtube_kids_fragment) return 5;
		return -1;
	}

	private void renderCards() {
		HomeAdapter adapter = menuAdapter;
		if (adapter != null) adapter.notifyDataSetChanged();
	}

	private void updateDragHighlight() {
		renderCards();
	}

	private boolean onCardKey(View v, int keyCode, KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_UP) return false;
		if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER) || (keyCode == KeyEvent.KEYCODE_ENTER) ||
				(keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
			v.performClick();
			return true;
		}
		return false;
	}

	private RecyclerView.LayoutParams cardLayoutParams() {
		RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
		params.setMargins(dp(7), dp(7), dp(7), dp(7));
		return params;
	}

	private boolean moveCard(int from, int to) {
		if ((from < 0) || (to < 0) || (from >= cards.size()) || (to >= cards.size()) || (from == to))
			return false;
		HomeCard c = cards.remove(from);
		cards.add(to, c);
		orderChanged = true;
		scheduleSaveCardOrder();
		return true;
	}

	private boolean applySavedCardOrder() {
		SharedPreferences prefs = requireContext().getSharedPreferences(MENU_PREFS, Context.MODE_PRIVATE);
		String order = prefs.getString(PREF_CARD_ORDER, null);
		if (order == null || order.isEmpty()) return false;
		String[] ids = order.split(",");
		ArrayList<HomeCard> original = new ArrayList<>(cards);
		cards.sort(Comparator.comparingInt(c -> savedOrderIndex(ids, original, c)));
		return true;
	}

	private int savedOrderIndex(String[] ids, ArrayList<HomeCard> original, HomeCard card) {
		String value = String.valueOf(card.id);
		for (int i = 0; i < ids.length; i++) {
			if (value.equals(ids[i])) return i;
		}
		return ids.length + original.indexOf(card);
	}

	private void scheduleSaveCardOrder() {
		handler.removeCallbacks(saveOrderRunnable);
		handler.postDelayed(saveOrderRunnable, 5000);
	}

	private void saveCardOrder() {
		if (!isAdded()) return;
		Context context = getContext();
		if (context == null) return;
		if (orderChanged) {
			StringBuilder order = new StringBuilder();
			for (HomeCard c : cards) {
				if (order.length() != 0) order.append(',');
				order.append(c.id);
			}
			context.getSharedPreferences(MENU_PREFS, Context.MODE_PRIVATE)
					.edit().putString(PREF_CARD_ORDER, order.toString()).apply();
		}
		orderChanged = false;
		dragMode = false;
		updateDragHighlight();
		updateVoiceForMediaState();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		if (isMainMenuColorPrefChanged(prefs)) applyMainMenuColor();
		if (prefs.contains(MainActivityPrefs.VOICE_CONTROl_ENABLED) ||
				prefs.contains(MainActivityPrefs.VOICE_CONTROL_HOME_LISTEN) ||
				prefs.contains(MainActivityPrefs.VOICE_CONTROL_LANG)) {
			stopMainMenuVoiceListening();
			updateVoiceForMediaState();
		}
	}

	private boolean isMainMenuColorPrefChanged(List<Pref<?>> prefs) {
		return prefs.contains(MainActivityPrefs.MAIN_MENU_COLOR_RED) ||
				prefs.contains(MainActivityPrefs.MAIN_MENU_COLOR_GREEN) ||
				prefs.contains(MainActivityPrefs.MAIN_MENU_COLOR_BLUE) ||
				prefs.contains(MainActivityPrefs.MAIN_MENU_COLOR_RED_AA) ||
				prefs.contains(MainActivityPrefs.MAIN_MENU_COLOR_GREEN_AA) ||
				prefs.contains(MainActivityPrefs.MAIN_MENU_COLOR_BLUE_AA);
	}

	private void applyMainMenuColor() {
		if (getContext() == null) return;
		mainMenuColor = getActivityDelegate().getPrefs().getMainMenuColorPref(getActivityDelegate());
		if (title != null) title.setTextColor(mainMenuColor);
		if (temperature != null) {
			temperature.setTextColor(mainMenuColor);
			temperature.setBackground(outlineBackground(mainMenuColor, dp(18), 1));
		}
		updateMicrophoneListening(voiceListening);
		renderCards();
	}

	private void openModule(int id, @Nullable Object input) {
		getActivityDelegate().openModule(id, input);
	}

	private ItemTouchHelper.Callback createMenuTouchCallback() {
		return new ItemTouchHelper.Callback() {
			@Override
			public boolean isLongPressDragEnabled() {
				return true;
			}

			@Override
			public boolean isItemViewSwipeEnabled() {
				return false;
			}

			@Override
			public int getMovementFlags(@NonNull RecyclerView recyclerView,
					@NonNull RecyclerView.ViewHolder viewHolder) {
				int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN |
						ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
				return makeMovementFlags(dragFlags, 0);
			}

			@Override
			public boolean onMove(@NonNull RecyclerView recyclerView,
					@NonNull RecyclerView.ViewHolder viewHolder,
					@NonNull RecyclerView.ViewHolder target) {
				int from = viewHolder.getAdapterPosition();
				int to = target.getAdapterPosition();
				HomeAdapter adapter = menuAdapter;
				if ((adapter == null) || !moveCard(from, to)) return false;
				adapter.notifyItemMoved(from, to);
				return true;
			}

			@Override
			public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
				super.onSelectedChanged(viewHolder, actionState);
				if (actionState != ItemTouchHelper.ACTION_STATE_DRAG) return;
				dragMode = true;
				stopMainMenuVoiceListening();
				if (viewHolder instanceof HomeCardViewHolder h) h.setDragging(true);
			}

			@Override
			public void clearView(@NonNull RecyclerView recyclerView,
					@NonNull RecyclerView.ViewHolder viewHolder) {
				super.clearView(recyclerView, viewHolder);
				dragMode = false;
				if (viewHolder instanceof HomeCardViewHolder h) h.setDragging(false);
				HomeAdapter adapter = menuAdapter;
				if (adapter != null) adapter.notifyDataSetChanged();
				updateVoiceForMediaState();
			}

			@Override
			public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
			}
		};
	}

	private final class HomeAdapter extends RecyclerView.Adapter<HomeCardViewHolder> {
		@NonNull
		@Override
		public HomeCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new HomeCardViewHolder(parent.getContext());
		}

		@Override
		public void onBindViewHolder(@NonNull HomeCardViewHolder holder, int position) {
			holder.bind(cards.get(position));
		}

		@Override
		public int getItemCount() {
			return cards.size();
		}
	}

	private final class HomeCardViewHolder extends RecyclerView.ViewHolder {
		private final MaterialCardView card;
		private final ImageView image;
		private final TextView text;
		private boolean dragging;

		HomeCardViewHolder(Context context) {
			super(new MaterialCardView(context));
			card = (MaterialCardView) itemView;
			card.setLayoutParams(cardLayoutParams());
			card.setCardBackgroundColor(
					isLiquidGlass(context) ? 0x66152B46 : Color.BLACK);
			card.setRadius(dp(18));
			card.setClickable(true);
			card.setFocusable(true);
			card.setLongClickable(true);

			LinearLayout content = new LinearLayout(context);
			content.setGravity(Gravity.CENTER);
			content.setOrientation(LinearLayout.VERTICAL);
			content.setPadding(dp(12), dp(14), dp(12), dp(12));
			image = new ImageView(context);
			content.addView(image, new LinearLayout.LayoutParams(dp(54), dp(54)));
			text = new TextView(context);
			text.setTextSize(16);
			text.setGravity(Gravity.CENTER);
			LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			textParams.topMargin = dp(10);
			content.addView(text, textParams);
			card.addView(content);
			card.setOnClickListener(v -> {
				if (dragMode) return;
				Object tag = v.getTag();
				if (tag instanceof HomeCard c) openModule(c.id, c.input);
			});
			card.setOnLongClickListener(v -> {
				stopMainMenuVoiceListening();
				ItemTouchHelper helper = menuTouchHelper;
				if (helper != null) helper.startDrag(HomeCardViewHolder.this);
				return true;
			});
			card.setOnKeyListener((v, keyCode, event) -> onCardKey(v, keyCode, event));
			card.setOnFocusChangeListener((v, hasFocus) -> updateHighlight());
		}

		void bind(HomeCard c) {
			card.setTag(c);
			card.setContentDescription(c.label);
			updateHighlight();
			image.setImageResource(c.icon);
			image.setColorFilter(mainMenuColor);
			text.setText(c.label);
			text.setTextColor(mainMenuColor);
		}

		void setDragging(boolean dragging) {
			this.dragging = dragging;
			updateHighlight();
			card.setAlpha(dragging ? 0.75f : 1f);
		}

		private void updateHighlight() {
			boolean highlighted = dragging || card.hasFocus();
			card.setStrokeColor(highlighted ? 0xFF66AAFF : mainMenuColor);
			card.setStrokeWidth(dp(highlighted ? 3 : 1));
		}
	}

	private static boolean isLiquidGlass(Context context) {
		TypedValue value = new TypedValue();
		return context.getTheme().resolveAttribute(R.attr.isLiquidGlassTheme, value, true) &&
				(value.data != 0);
	}

	private void scheduleVoiceStart(long delayMs) {
		handler.removeCallbacks(voiceStartRunnable);
		handler.removeCallbacks(voiceMediaCheckRunnable);
		if (!shouldMainMenuListen()) return;
		handler.postDelayed(voiceStartRunnable, delayMs);
	}

	private void restartMainMenuVoiceListening() {
		stopMainMenuVoiceListening();
		voiceBlockedByMedia = getActivityDelegate().isMediaPlayingForVoiceControl();
		voiceManualListen = true;
		scheduleVoiceStart(250);
	}

	private boolean shouldMainMenuListen() {
		return isAdded() && !isHidden() && !dragMode &&
				!voiceBlockedByMedia && !getActivityDelegate().isMediaPlayingForVoiceControl() &&
				getActivityDelegate().getPrefs().getVoiceControlEnabledPref() &&
				(voiceManualListen || getActivityDelegate().getPrefs().getVoiceControlHomeListenPref());
	}

	private void startMainMenuVoiceListening() {
		handler.removeCallbacks(voiceStartRunnable);
		if (voiceListening || !shouldMainMenuListen()) return;
		Context context = getContext();
		if (context == null) return;
		if (getActivityDelegate().isMediaPlayingForVoiceControl()) {
			voiceBlockedByMedia = true;
			updateMicrophoneListening(false);
			return;
		}
		if (!SpeechRecognizer.isRecognitionAvailable(context)) return;
		if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
				PERMISSION_GRANTED) {
			getActivityDelegate().getAppActivity().checkPermissions(Manifest.permission.RECORD_AUDIO)
					.onCompletion((r, err) -> {
						if ((err == null) && (r.length != 0) && (r[0] == PERMISSION_GRANTED) &&
								shouldMainMenuListen())
							scheduleVoiceStart(250);
						else updateMicrophoneListening(false);
					});
			return;
		}
		if (getActivityDelegate().isMediaPlayingForVoiceControl()) {
			voiceBlockedByMedia = true;
			updateMicrophoneListening(false);
			return;
		}

		voiceListening = true;
		updateMicrophoneListening(true);
		int generation = ++voiceGeneration;
		SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(context);
		voiceRecognizer = recognizer;
		recognizer.setRecognitionListener(new MainMenuRecognitionListener(recognizer, generation));

		Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
				getActivityDelegate().getPrefs().getVoiceControlLang(getActivityDelegate()));
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
				RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
		intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
		try {
			recognizer.startListening(intent);
		} catch (Exception err) {
			finishVoiceCycle(recognizer, generation, 1500);
		}
	}

	private void stopMainMenuVoiceListening() {
		handler.removeCallbacks(voiceStartRunnable);
		voiceGeneration++;
		SpeechRecognizer recognizer = voiceRecognizer;
		voiceRecognizer = null;
		voiceListening = false;
		voiceManualListen = false;
		updateMicrophoneListening(false);
		if (recognizer == null) return;
		try {
			recognizer.cancel();
		} catch (Exception ignored) {
		}
		try {
			recognizer.destroy();
		} catch (Exception ignored) {
		}
	}

	private void finishVoiceCycle(SpeechRecognizer recognizer, int generation, long restartDelayMs) {
		if (generation != voiceGeneration) return;
		if (voiceRecognizer == recognizer) voiceRecognizer = null;
		voiceListening = false;
		updateMicrophoneListening(false);
		try {
			recognizer.destroy();
		} catch (Exception ignored) {
		}
		boolean manual = voiceManualListen;
		voiceManualListen = false;
		if (manual) return;
		voiceBlockedByMedia = getActivityDelegate().isMediaPlayingForVoiceControl();
		scheduleVoiceStart(restartDelayMs);
	}

	private void handleVoiceResults(SpeechRecognizer recognizer, int generation,
			@Nullable List<String> results) {
		if (generation != voiceGeneration) return;
		HomeCard match = findVoiceMenuMatch(results);
		if (match != null) {
			stopMainMenuVoiceListening();
			openModule(match.id, match.input);
		} else {
			finishVoiceCycle(recognizer, generation, 700);
		}
	}

	@Override
	public void onPlaybackStateChanged(MediaSessionCallback cb, PlaybackStateCompat state) {
		updateVoiceForMediaState();
	}

	private void updateVoiceForMediaState() {
		handler.removeCallbacks(voiceMediaCheckRunnable);
		if (getActivityDelegate().isMediaPlayingForVoiceControl()) {
			voiceBlockedByMedia = true;
			stopMainMenuVoiceListening();
			if (isAdded() && !isHidden()) handler.postDelayed(voiceMediaCheckRunnable, 1000);
		} else {
			voiceBlockedByMedia = false;
			scheduleVoiceStart(500);
		}
	}

	private final class MainMenuRecognitionListener implements RecognitionListener {
		private final SpeechRecognizer recognizer;
		private final int generation;

		private MainMenuRecognitionListener(SpeechRecognizer recognizer, int generation) {
			this.recognizer = recognizer;
			this.generation = generation;
		}

		@Override
		public void onReadyForSpeech(Bundle params) {
			if ((generation == voiceGeneration) && shouldMainMenuListen())
				updateMicrophoneListening(true);
		}

		@Override
		public void onResults(Bundle results) {
			handleVoiceResults(recognizer, generation,
					results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
		}

		@Override
		public void onError(int error) {
			long delay = switch (error) {
				case SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1500;
				case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 0;
				default -> 700;
			};
			finishVoiceCycle(recognizer, generation, delay);
		}

		@Override
		public void onBeginningOfSpeech() {
		}

		@Override
		public void onRmsChanged(float rmsdB) {
		}

		@Override
		public void onBufferReceived(byte[] buffer) {
		}

		@Override
		public void onEndOfSpeech() {
		}

		@Override
		public void onPartialResults(Bundle partialResults) {
		}

		@Override
		public void onEvent(int eventType, Bundle params) {
		}
	}

	private void updateMicrophoneListening(boolean listening) {
		ImageView mic = microphone;
		if (mic == null) return;
		if (listening) {
			mic.setColorFilter(Color.BLACK);
			mic.setBackground(outlineBackground(mainMenuColor, Color.BLACK, dp(18), 1));
		} else {
			mic.setColorFilter(mainMenuColor);
			mic.setBackground(outlineBackground(Color.BLACK, mainMenuColor, dp(18), 1));
		}
	}

	@Nullable
	private HomeCard findVoiceMenuMatch(@Nullable List<String> results) {
		if (results == null) return null;
		for (String spoken : results) {
			String command = normalizeVoiceText(spoken);
			if (command.isEmpty()) continue;
			for (HomeCard card : cards) {
				if (matchesVoiceCommand(command, card)) return card;
			}
		}
		return null;
	}

	private boolean matchesVoiceCommand(String command, HomeCard card) {
		if (matchesVoiceText(command, normalizeVoiceText(card.label))) return true;
		for (String alias : voiceAliases(card.id)) {
			if (matchesVoiceText(command, alias)) return true;
		}
		return false;
	}

	private boolean matchesVoiceText(String command, String target) {
		if (target.isEmpty()) return false;
		String compactCommand = command.replace(" ", "");
		String compactTarget = target.replace(" ", "");
		return command.equals(target) || command.contains(target) || target.contains(command) ||
				compactCommand.equals(compactTarget) || compactCommand.contains(compactTarget) ||
				compactTarget.contains(compactCommand);
	}

	private String normalizeVoiceText(String value) {
		if (value == null) return "";
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.US)
				.replaceAll("[^\\p{L}\\p{Nd}]+", " ")
				.trim();
		return normalized.replaceAll("\\s+", " ");
	}

	private String[] voiceAliases(int id) {
		if (id == R.id.tv_fragment) return new String[]{"tv", "television"};
		if (id == R.id.radio_fragment) return new String[]{"radio"};
		if (id == R.id.youtube_fragment) return new String[]{"youtube", "you tube"};
		if (id == R.id.youtube_music_fragment) return new String[]{"youtube music", "you tube music", "music"};
		if (id == R.id.youtube_kids_fragment) return new String[]{"youtube kids", "you tube kids", "kids"};
		if (id == R.id.web_browser_fragment) return new String[]{"browser", "web", "internet"};
		if (id == R.id.folders_fragment) return new String[]{"folders", "folder", "files"};
		if (id == R.id.favorites_fragment) return new String[]{"favorites", "favourites", "favorite"};
		if (id == R.id.playlists_fragment) return new String[]{"playlists", "playlist"};
		if (id == R.id.gallery_fragment) return new String[]{"gallery", "photos", "pictures"};
		if (id == R.id.video_gallery_fragment) return new String[]{"video", "videos", "video player"};
		if (id == R.id.settings_fragment) return new String[]{"settings", "options"};
		if (id == R.id.chat_addon) return new String[]{"chat", "chatgpt", "chat gpt"};
		return new String[0];
	}

	private int columnCount(Context context) {
		int widthDp = context.getResources().getConfiguration().screenWidthDp;
		if (widthDp >= 1000) return 4;
		if (widthDp >= 600 || context.getResources().getConfiguration().orientation ==
				Configuration.ORIENTATION_LANDSCAPE) return 3;
		return 2;
	}

	private void toggleTemperatureUnit() {
		Context context = requireContext().getApplicationContext();
		SharedPreferences prefs = context.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE);
		boolean fahrenheit = !useFahrenheit();
		prefs.edit().putBoolean(PREF_USE_FAHRENHEIT, fahrenheit)
				.remove("display").remove("updated").apply();
		if (temperature != null) temperature.setText(unitPlaceholder());
		refreshTemperature();
	}

	private void refreshTemperature() {
		TextView view = temperature;
		if (view == null || getContext() == null) return;
		Context context = requireContext().getApplicationContext();
		SharedPreferences prefs = context.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE);
		long cachedAt = prefs.getLong("updated", 0);
		String cached = prefs.getString("display", null);
		if (cached != null) view.setText(cached);
		if (System.currentTimeMillis() - cachedAt < WEATHER_CACHE_MS) return;
		if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
				!= PERMISSION_GRANTED) return;

		Location location = lastLocation(context);
		if (location != null) weatherExecutor.execute(() -> loadTemperature(context, location));
		else requestCurrentLocation(context);
	}

	private void requestCurrentLocation(Context context) {
		try {
			LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
			String provider = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?
					LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
				manager.getCurrentLocation(provider, new CancellationSignal(),
						ContextCompat.getMainExecutor(context), location -> {
							if (location != null && !weatherExecutor.isShutdown())
								weatherExecutor.execute(() -> loadTemperature(context, location));
						});
			} else {
				manager.requestSingleUpdate(provider, new LocationListener() {
					@Override public void onLocationChanged(@NonNull Location location) {
						if (!weatherExecutor.isShutdown())
							weatherExecutor.execute(() -> loadTemperature(context, location));
					}
					@Override public void onStatusChanged(String provider, int status, Bundle extras) { }
					@Override public void onProviderEnabled(@NonNull String provider) { }
					@Override public void onProviderDisabled(@NonNull String provider) { }
				}, null);
			}
		} catch (Exception ignored) {
		}
	}

	@Nullable
	private Location lastLocation(Context context) {
		try {
			LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
			Location best = null;
			for (String provider : manager.getProviders(true)) {
				Location candidate = manager.getLastKnownLocation(provider);
				if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
			}
			return best;
		} catch (SecurityException ignored) {
			return null;
		}
	}

	private void loadTemperature(Context context, Location location) {
		boolean fahrenheit = useFahrenheit();
		String unit = fahrenheit ? "fahrenheit" : "celsius";
		HttpURLConnection connection = null;
		try {
			String endpoint = String.format(Locale.US,
					"https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&current=temperature_2m&temperature_unit=%s",
					location.getLatitude(), location.getLongitude(), unit);
			connection = (HttpURLConnection) new URL(endpoint).openConnection();
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(
					connection.getInputStream(), StandardCharsets.UTF_8))) {
				StringBuilder json = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) json.append(line);
				double value = new JSONObject(json.toString()).getJSONObject("current")
						.getDouble("temperature_2m");
				String display = Math.round(value) + (fahrenheit ? "°F" : "°C");
				context.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE).edit()
						.putString("display", display).putLong("updated", System.currentTimeMillis()).apply();
				if (temperature != null) temperature.post(() -> {
					if (temperature != null) temperature.setText(display);
				});
			}
		} catch (Exception ignored) {
		} finally {
			if (connection != null) connection.disconnect();
		}
	}

	private String unitPlaceholder() {
		return "--" + (useFahrenheit() ? "°F" : "°C");
	}

	private boolean useFahrenheit() {
		SharedPreferences prefs = requireContext().getApplicationContext()
				.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE);
		if (prefs.contains(PREF_USE_FAHRENHEIT)) {
			return prefs.getBoolean(PREF_USE_FAHRENHEIT, false);
		}
		String country = Locale.getDefault().getCountry().toUpperCase(Locale.US);
		return List.of("US", "BS", "BZ", "KY", "PW", "FM", "MH", "LR").contains(country);
	}

	private GradientDrawable outlineBackground(int stroke, int radius, int strokeWidth) {
		return outlineBackground(Color.BLACK, stroke, radius, strokeWidth);
	}

	private GradientDrawable outlineBackground(int fill, int stroke, int radius, int strokeWidth) {
		GradientDrawable drawable = new GradientDrawable();
		drawable.setColor(fill);
		drawable.setCornerRadius(radius);
		drawable.setStroke(dp(strokeWidth), stroke);
		return drawable;
	}

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}

	private static final class HomeCard {
		final int id;
		final int icon;
		final String label;
		@Nullable final Object input;

		private HomeCard(int id, int icon, String label, @Nullable Object input) {
			this.id = id;
			this.icon = icon;
			this.label = label;
			this.input = input;
		}
	}
}
