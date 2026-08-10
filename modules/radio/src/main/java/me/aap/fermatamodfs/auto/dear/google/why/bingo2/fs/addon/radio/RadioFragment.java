package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.AddonManager;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u.RadioM3uFile;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u.RadioM3uFileSystem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u.RadioM3uFileSystemProvider;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.radio.m3u.RadioM3uItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.DefaultMediaLib;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.BrowsableItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.Item;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.lib.MediaLib.PlayableItem;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.FermataServiceUiBinder;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.fragment.MediaLibFragment;
import me.aap.utils.app.App;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.FloatingButton;

public class RadioFragment extends MediaLibFragment {
	@Override
	protected ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new RadioAdapter(getMainActivity(), getRootItem());
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.addon_name_radio);
	}

	@Override
	public int getFragmentId() {
		return me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.radio_fragment;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return RadioFloatingButtonMediator.instance;
	}

	@Override
	public void onResume() {
		super.onResume();
		scheduleRestoreLastPlayedChannel();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) {
			resetRestoreLastPlayedChannel();
		} else {
			resetRestoreLastPlayedChannel();
			scheduleRestoreLastPlayedChannel();
		}
	}

	public void navBarItemReselected(int itemId) {
		getAdapter().setParent(getRootItem());
	}

	public void addSource() {
		RadioM3uFileSystemProvider prov = new RadioM3uFileSystemProvider();
		prov.select(getMainActivity(), Collections.singletonList(RadioM3uFileSystem.getInstance())).main()
				.onFailure(this::failedToAddSource).onSuccess(this::addM3uSource);
	}

	public RadioRootItem getRootItem() {
		return requireNonNull(AddonManager.get().getAddon(RadioAddon.class)).getRootItem(
				(DefaultMediaLib) getMainActivity().getLib());
	}

	@Override
	public void contributeToContextMenu(OverlayMenu.Builder b, me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view.MediaItemMenuHandler h) {
		if (h.getItem() instanceof RadioM3uItem) {
			b.addItem(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.delete,
					me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.drawable.delete,
					me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.string.delete).setData(h.getItem())
					.setHandler(this::contextMenuItemSelected);
		}
		super.contributeToContextMenu(b, h);
	}

	private boolean contextMenuItemSelected(OverlayMenuItem item) {
		if (item.getItemId() != me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.delete) return false;
		Object data = item.getData();
		if (!(data instanceof RadioM3uItem src)) return false;
		getRootItem().removeItem(src);
		getAdapter().setParent(getRootItem());
		return true;
	}

	@Override
	public void switchingTo(@NonNull ActivityFragment newFragment) {
		super.switchingTo(newFragment);
		getMainActivity().getFloatingButton().clearAnimation();
	}

	private void addM3uSource(RadioM3uFile m3u) {
		if (m3u != null) getRootItem().addSource(m3u);
		getAdapter().setParent(getRootItem());
		getMainActivity().showFragment(getFragmentId());
	}

	private void failedToAddSource(Throwable ex) {
		getMainActivity().showFragment(me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.R.id.radio_fragment);
		if (isCancellation(ex)) return;
		App.get().getHandler().post(() -> {
			String msg = ex.getLocalizedMessage();
			UiUtils.showAlert(getContext(), getString(R.string.err_failed_to_add_radio_source,
					(msg != null) ? msg : ex.toString()));
		});
	}

	@Override
	public void openItem(BrowsableItem folder) {
		super.openItem(folder);
		saveLastFolder(folder);
	}

	@Override
	protected boolean shouldRestoreLastPlayedChannel() {
		RadioAddon addon = AddonManager.get().getAddon(RadioAddon.class);
		return addon != null && addon.autoplayLastStation();
	}

	@Override
	protected String getLastPlayedIdForRestore() {
		return getRootItem().getStringPref(RadioRootItem.LAST_STATION);
	}

	@Nullable
	@Override
	protected String getLastFolderIdForRestore() {
		return getRootItem().getStringPref(RadioRootItem.LAST_FOLDER);
	}

	@Override
	protected int getLastPlayedRestoreMaxRetries() {
		return 60;
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		super.onPlayableChanged(oldItem, newItem);
		if ((newItem != null) && isSupportedItem(newItem)) saveLastPlayed(newItem);
	}

	@Override
	protected boolean isSupportedItem(Item i) {
		return isRadioItem(getRootItem(), i);
	}

	@Override
	protected boolean isSupportedItemId(String id) {
		return getRootItem().isChildItemId(id);
	}

	private boolean isRadioItem(RadioRootItem root, Item i) {
		return root.isChildItemId(i.getId());
	}

	private void saveLastPlayed(PlayableItem item) {
		RadioRootItem root = getRootItem();
		try (PreferenceStore.Edit e = root.editPreferenceStore()) {
			e.setStringPref(RadioRootItem.LAST_STATION, item.getId());
			BrowsableItem parent = item.getParent();
			if ((parent != null) && !(parent instanceof RadioRootItem)) {
				e.setStringPref(RadioRootItem.LAST_FOLDER, parent.getId());
			}
		}
	}

	private void saveLastFolder(BrowsableItem folder) {
		if (folder == null) return;
		RadioRootItem root = getRootItem();
		try (PreferenceStore.Edit e = root.editPreferenceStore()) {
			if (folder instanceof RadioRootItem) {
				PlayableItem cur = getMainActivity().getMediaServiceBinder().getCurrentItem();
				if ((cur != null) && isSupportedItem(cur)) {
					BrowsableItem p = cur.getParent();
					if ((p != null) && !(p instanceof RadioRootItem)) {
						e.setStringPref(RadioRootItem.LAST_FOLDER, p.getId());
						return;
					}
				}
				e.removePref(RadioRootItem.LAST_FOLDER);
			} else {
				e.setStringPref(RadioRootItem.LAST_FOLDER, folder.getId());
			}
		}
	}

	private class RadioAdapter extends ListAdapter {
		RadioAdapter(MainActivityDelegate activity, BrowsableItem parent) {
			super(activity, parent);
		}

		@Override
		public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction, boolean scroll) {
			FutureSupplier<?> set = super.setParent(parent, userAction, scroll);
			if (userAction) saveLastFolder(parent);
			return set;
		}

		@Override
		public boolean isLongPressDragEnabled() {
			BrowsableItem p = getParent();
			return p instanceof RadioRootItem;
		}

		@Override
		protected void onItemDismiss(int position) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof RadioRootItem) ((RadioRootItem) i).removeItem(position);
			super.onItemDismiss(position);
		}
	}
}
