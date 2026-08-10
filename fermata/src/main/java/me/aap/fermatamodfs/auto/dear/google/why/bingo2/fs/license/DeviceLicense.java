package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.license;

import android.app.Activity;
import android.content.Context;

import java.util.function.Consumer;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.ui.activity.AppActivity;

/**
 * Free builds do not use online licensing. Call sites keep this API for source
 * compatibility with the shared activity startup flow.
 */
public final class DeviceLicense {
	private DeviceLicense() {
	}

	public static FutureSupplier<Void> awaitLicensed(Activity activity) {
		Promise<Void> licensed = new Promise<>();
		licensed.complete(null);
		return licensed;
	}

	public static boolean guard(Activity activity) {
		return true;
	}

	public static void guardCar(AppActivity activity, Runnable onLicensed) {
		onLicensed.run();
	}

	public static void verify(Context context, Consumer<Boolean> result) {
		result.accept(true);
	}

	public static boolean isConfirmed(Context context) {
		return true;
	}
}
