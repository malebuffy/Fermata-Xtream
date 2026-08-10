package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.gdrive;

import android.content.Context;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.activity.MainActivityDelegate;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.VfsProviderBase;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.gdrive.GdriveFileSystem;

import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.vfs.gdrive.GdriveFileSystem.Provider.GOOGLE_TOKEN;

/**
 * @author Andrey Pavlenko
 */
public class Provider extends VfsProviderBase {

	@Override
	public FutureSupplier<VirtualFileSystem> createFileSystem(
			Context ctx, Supplier<FutureSupplier<? extends AppActivity>> activitySupplier, PreferenceStore ps) {
		BasicPreferenceStore store = new BasicPreferenceStore();
		int tokenId = ctx.getResources()
				.getIdentifier("default_web_client_id", "string", ctx.getPackageName());
		if (tokenId != 0) store.applyStringPref(GOOGLE_TOKEN, ctx.getString(tokenId));
		return new GdriveFileSystem.Provider(activitySupplier).createFileSystem(store);
	}

	@Override
	protected boolean addRemoveSupported() {
		return false;
	}

	@Override
	protected FutureSupplier<? extends VirtualResource> addFolder(MainActivityDelegate a, VirtualFileSystem fs) {
		return completedNull();
	}

	@Override
	protected FutureSupplier<Void> removeFolder(MainActivityDelegate a, VirtualFileSystem fs, VirtualResource folder) {
		return completedVoid();
	}
}
