package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.m3u;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import me.aap.utils.log.Log;

/** Copies bundled playlist assets into app storage on first use. */
public final class BundledM3u {
	private BundledM3u() {
	}

	public static String ensureLocal(Context ctx, String assetName, String fileName) {
		File dir = new File(ctx.getFilesDir(), "bundled-m3u");
		if (!dir.exists() && !dir.mkdirs()) Log.e("Failed to create bundled M3U dir: ", dir);
		File dest = new File(dir, fileName);
		long assetLength = assetLength(ctx, assetName);
		if (dest.isFile() && dest.length() > 0 && ((assetLength <= 0) || (dest.length() == assetLength))) {
			return dest.getAbsolutePath();
		}

		try (InputStream in = ctx.getAssets().open(assetName);
			 OutputStream out = new FileOutputStream(dest)) {
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
			return dest.getAbsolutePath();
		} catch (IOException err) {
			Log.e(err, "Failed to extract bundled M3U: ", assetName);
			if (dest.isFile()) dest.delete();
			return null;
		}
	}

	private static long assetLength(Context ctx, String assetName) {
		try (AssetFileDescriptor fd = ctx.getAssets().openFd(assetName)) {
			return fd.getLength();
		} catch (IOException ignored) {
			return -1;
		}
	}
}
