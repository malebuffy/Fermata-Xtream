package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.LinkedList;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.AddonInfo;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.FermataAddon;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.MediaLibAddon;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.DefaultMediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Item;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;

@Keep
@SuppressWarnings("unused")
public class RadioAddon implements MediaLibAddon, SharedPreferenceStore {
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(RadioAddon.class.getName());
	static final Pref<BooleanSupplier> AUTOPLAY_LAST_STATION =
			Pref.b("RADIO_AUTOPLAY_LAST_STATION", false);
	private static RadioRootItem root;
	private final SharedPreferences prefs =
			App.get().getSharedPreferences("radio", Context.MODE_PRIVATE);
	private Collection<ListenerRef<Listener>> listeners;

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.radio_fragment;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new RadioFragment();
	}

	@Override
	public boolean isSupportedItem(Item i) {
		return i instanceof RadioItem;
	}

	public RadioRootItem getRootItem(DefaultMediaLib lib) {
		if ((root == null) || (root.getLib() != lib)) root = new RadioRootItem(lib);
		return root;
	}

	@Nullable
	@Override
	public FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme,
																								String id) {
		return getRootItem(lib).getItem(scheme, id);
	}

	boolean autoplayLastStation() {
		return getBooleanPref(AUTOPLAY_LAST_STATION);
	}

	@Override
	public void contributeSettings(Context ctx, PreferenceStore store, PreferenceSet set,
																 ChangeableCondition visibility) {
		set.addBooleanPref(o -> {
			o.store = this;
			o.pref = AUTOPLAY_LAST_STATION;
			o.title = R.string.autoplay_last_radio;
			o.subtitle = R.string.autoplay_last_radio_sub;
			o.visibility = visibility;
		});
	}

	@NonNull
	@Override
	public SharedPreferences getSharedPreferences() {
		return prefs;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return listeners != null ? listeners : (listeners = new LinkedList<>());
	}
}
