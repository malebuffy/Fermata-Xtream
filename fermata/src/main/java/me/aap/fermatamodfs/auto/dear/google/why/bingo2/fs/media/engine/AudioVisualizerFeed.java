package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine;

/**
 * In-process audio level feed for visualizers.
 *
 * <p>This is updated from engines that can see decoded PCM before it reaches AudioTrack. It avoids
 * depending only on android.media.audiofx.Visualizer, which can be silent on some devices or AA
 * routes even when playback is audible.</p>
 */
public final class AudioVisualizerFeed {
	public static final int BAND_COUNT = 48;
	private static final long STALE_MS = 500;
	private static final float[] bands = new float[BAND_COUNT];
	private static long updateTime;
	private static float energy;

	private AudioVisualizerFeed() {
	}

	public static synchronized void publish(float[] values, float e) {
		System.arraycopy(values, 0, bands, 0, Math.min(values.length, BAND_COUNT));
		energy = e;
		updateTime = System.currentTimeMillis();
	}

	public static synchronized float read(float[] out) {
		if ((updateTime == 0) || (System.currentTimeMillis() - updateTime > STALE_MS)) return -1f;
		System.arraycopy(bands, 0, out, 0, Math.min(out.length, BAND_COUNT));
		return energy;
	}

	public static synchronized void clear() {
		updateTime = 0;
		energy = 0f;
	}
}
