package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.ui.view;

import static android.media.AudioManager.ERROR;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine.AudioVisualizerFeed;
import me.aap.utils.log.Log;

/**
 * Animated bar-style audio visualizer for radio playback.
 */
public class RadioVisualizerView extends View
		implements Choreographer.FrameCallback, Visualizer.OnDataCaptureListener {
	private static final int BAR_COUNT = 48;
	private static final int WAVEFORM_POINTS = 768;
	private static final float MIN_LEVEL = 0.025f;
	private static final float IDLE_LEVEL = 0.035f;
	private static final int BASS_COLOR = 0xCC4E6AA2;
	private static final int MID_COLOR = 0xE640C4FF;
	private static final int HIGH_COLOR = 0xF2B9EFFF;
	private static final long CAPTURE_STALE_MS = 2500;
	private static final long CAPTURE_SILENT_MS = 3500;
	private static final long CAPTURE_START_GRACE_MS = 3000;
	private static final long RETRY_DELAY_MS = 2500;
	private static final int LOW_HZ = 32;
	private static final int HIGH_HZ = 16000;
	private final float[] levels = new float[BAR_COUNT];
	private final float[] targets = new float[BAR_COUNT];
	private final float[] spectrum = new float[BAR_COUNT];
	private final float[] idle = new float[BAR_COUNT];
	private final float[] phase = new float[BAR_COUNT];
	private final float[] waveformPoints = new float[WAVEFORM_POINTS];
	private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final int touchSlop;
	@Nullable
	private Visualizer visualizer;
	private int audioSessionId = ERROR;
	private boolean animating;
	private boolean captureEnabled;
	private boolean waveformMode;
	private long frameTime;
	private long setupTime;
	private long lastCaptureTime;
	private long lastSignalTime;
	private long lastFftSignalTime;
	private long lastWaveformTime;
	private long nextSetupAttempt;
	private float downX;
	private float downY;
	private float pulse;

	public RadioVisualizerView(@NonNull Context ctx, @Nullable AttributeSet attrs) {
		super(ctx, attrs);
		setClickable(true);
		setFocusable(true);
		barPaint.setStyle(Paint.Style.FILL);
		wavePaint.setStyle(Paint.Style.STROKE);
		wavePaint.setStrokeCap(Paint.Cap.ROUND);
		wavePaint.setStrokeJoin(Paint.Join.ROUND);
		wavePaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
		touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
		for (int i = 0; i < BAR_COUNT; i++) phase[i] = i * 0.73f;
		resetLevels();
	}

	public void setAudioSessionId(int audioSessionId) {
		if (audioSessionId == ERROR) audioSessionId = 0;
		if (this.audioSessionId == audioSessionId) {
			ensureCapture();
			return;
		}
		this.audioSessionId = audioSessionId;
		nextSetupAttempt = 0;
		releaseVisualizer();
		Arrays.fill(spectrum, MIN_LEVEL);
		ensureCapture();
	}

	public void ensureCapture() {
		if (animating && (visualizer == null) && (System.currentTimeMillis() >= nextSetupAttempt)) {
			setupVisualizer();
		}
	}

	public boolean isAnimating() {
		return animating;
	}

	public void setAnimating(boolean animating) {
		if (this.animating == animating) return;
		this.animating = animating;
		if (animating) {
			frameTime = System.nanoTime();
			nextSetupAttempt = 0;
			ensureCapture();
			Choreographer.getInstance().postFrameCallback(this);
		} else {
			Choreographer.getInstance().removeFrameCallback(this);
			releaseVisualizer();
			resetLevels();
			invalidate();
		}
	}

	@Override
	public void doFrame(long frameTimeNanos) {
		if (!animating) return;
		float dt = (frameTimeNanos - frameTime) / 1_000_000_000f;
		frameTime = frameTimeNanos;
		if ((dt <= 0f) || (dt > 0.1f)) dt = 0.016f;

		if ((visualizer != null) && captureEnabled && shouldRetryCapture()) {
			releaseVisualizer();
			nextSetupAttempt = System.currentTimeMillis() + RETRY_DELAY_MS;
		}
		ensureCapture();
		readDirectAudioFeed();
		updateIdle(dt);
		composeTargets();
		smoothLevels(dt);
		invalidate();
		Choreographer.getInstance().postFrameCallback(this);
	}

	@Override
	public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
		if ((waveform == null) || (waveform.length == 0)) return;
		byte[] data = waveform.clone();
		post(() -> updateWaveform(data, samplingRate));
	}

	@Override
	public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
		if ((fft == null) || (fft.length < 4)) return;
		byte[] data = fft.clone();
		post(() -> updateSpectrum(data, samplingRate));
	}

	private boolean hasRecentCapture() {
		return captureEnabled && (lastCaptureTime != 0)
				&& (System.currentTimeMillis() - lastCaptureTime <= CAPTURE_STALE_MS);
	}

	private boolean hasRecentSignal() {
		return captureEnabled && (lastSignalTime != 0)
				&& (System.currentTimeMillis() - lastSignalTime <= CAPTURE_SILENT_MS);
	}

	private boolean shouldRetryCapture() {
		long now = System.currentTimeMillis();
		if (lastCaptureTime == 0) return now - setupTime > CAPTURE_START_GRACE_MS;
		return now - lastCaptureTime > CAPTURE_STALE_MS;
	}

	private void setupVisualizer() {
		if (visualizer != null) return;
		if ((audioSessionId > 0) && trySetupVisualizer(audioSessionId)) return;
		if (trySetupVisualizer(0)) return;
		nextSetupAttempt = System.currentTimeMillis() + RETRY_DELAY_MS;
	}

	private boolean trySetupVisualizer(int sessionId) {
		try {
			Visualizer v = new Visualizer(sessionId);
			int[] range = Visualizer.getCaptureSizeRange();
			v.setCaptureSize(range[1]);
			v.setDataCaptureListener(this, Visualizer.getMaxCaptureRate() / 2, true, true);
			v.setEnabled(true);
			visualizer = v;
			captureEnabled = true;
			setupTime = System.currentTimeMillis();
			lastCaptureTime = 0;
			lastSignalTime = 0;
			lastFftSignalTime = 0;
			lastWaveformTime = 0;
			return true;
		} catch (Throwable ex) {
			releaseVisualizer();
			Log.w(ex, "Failed to enable radio visualizer for session ", sessionId);
			return false;
		}
	}

	private void releaseVisualizer() {
		captureEnabled = false;
		Visualizer v = visualizer;
		visualizer = null;
		if (v == null) return;
		try {
			v.setEnabled(false);
		} catch (Throwable ignore) {
		}
		v.release();
	}

	private void markCapture() {
		lastCaptureTime = System.currentTimeMillis();
	}

	private void markSignal(float energy) {
		if (energy > 0.012f) lastSignalTime = System.currentTimeMillis();
	}

	private void readDirectAudioFeed() {
		float energy = AudioVisualizerFeed.read(spectrum);
		if (energy < 0f) return;
		markCapture();
		markSignal(Math.max(energy, 0.02f));
		pulse = Math.max(pulse, Math.min(1f, energy * 3.2f));
	}

	private void updateSpectrum(byte[] fft, int samplingRateMilliHz) {
		if (!captureEnabled) return;
		markCapture();
		int sampleRate = samplingRateMilliHz / 1000;
		if (sampleRate <= 0) sampleRate = 44100;
		float total = 0f;

		for (int i = 0; i < BAR_COUNT; i++) {
			float low = logFrequency(i, sampleRate);
			float high = logFrequency(i + 1, sampleRate);
			float value = measureBand(fft, sampleRate, low, high);
			value = (float) Math.pow(Math.min(1f, value * 1.8f), 0.65f);
			spectrum[i] = MIN_LEVEL + value * (1f - MIN_LEVEL);
			total += spectrum[i];
		}

		float energy = Math.max(0f, (total / BAR_COUNT) - MIN_LEVEL);
		if (energy > 0.01f) lastFftSignalTime = System.currentTimeMillis();
		markSignal(energy);
		pulse = Math.max(pulse, Math.min(1f, total / BAR_COUNT));
	}

	private void updateWaveform(byte[] waveform, int samplingRateMilliHz) {
		if (!captureEnabled) return;
		markCapture();
		float sum = 0f;
		for (byte b : waveform) {
			float s = sample(b);
			sum += s * s;
		}
		float rms = (float) Math.sqrt(sum / waveform.length);
		markSignal(rms);
		updateWaveformPoints(waveform);
		pulse = Math.max(pulse, Math.min(1f, rms * 2.8f));
		if (System.currentTimeMillis() - lastFftSignalTime > CAPTURE_SILENT_MS) {
			updateSpectrumFromWaveform(waveform, samplingRateMilliHz, rms);
			return;
		}

		int chunk = Math.max(1, waveform.length / BAR_COUNT);
		for (int i = 0; i < BAR_COUNT; i++) {
			int start = i * chunk;
			if (start >= waveform.length) break;
			int end = Math.min(waveform.length, start + chunk);
			float bandSum = 0f;
			for (int j = start; j < end; j++) {
				float s = sample(waveform[j]);
				bandSum += s * s;
			}
			float bandRms = (float) Math.sqrt(bandSum / Math.max(1, end - start));
			float shaped = (float) Math.pow(Math.min(1f, bandRms * 3.4f), 0.72f);
			float ripple = 0.72f + 0.28f * sin01((System.currentTimeMillis() * 0.009f) + phase[i]);
			spectrum[i] = Math.max(spectrum[i], MIN_LEVEL + shaped * ripple * (1f - MIN_LEVEL));
		}
	}

	private void updateWaveformPoints(byte[] waveform) {
		lastWaveformTime = System.currentTimeMillis();
		if (waveform.length == 0) {
			Arrays.fill(waveformPoints, 0f);
			return;
		}

		for (int i = 0; i < WAVEFORM_POINTS; i++) {
			float src = i * (waveform.length - 1f) / (WAVEFORM_POINTS - 1f);
			int left = (int) src;
			int right = Math.min(waveform.length - 1, left + 1);
			float mix = src - left;
			float a = sample(waveform[left]);
			float b = sample(waveform[right]);
			waveformPoints[i] = a + (b - a) * mix;
		}
	}

	private void updateSpectrumFromWaveform(byte[] waveform, int samplingRateMilliHz, float rms) {
		int sampleRate = samplingRateMilliHz / 1000;
		if (sampleRate <= 0) sampleRate = 44100;
		int frames = Math.min(waveform.length, 1024);
		if (frames < 32) return;

		for (int i = 0; i < BAR_COUNT; i++) {
			float low = logFrequency(i, sampleRate);
			float high = logFrequency(i + 1, sampleRate);
			float center = (float) Math.sqrt(low * high);
			float value = (measureWaveformFrequency(waveform, frames, center, sampleRate) * 0.74f) +
					(rms * 0.26f);
			value = (float) Math.pow(Math.min(1f, value * 8f), 0.68f);
			float ripple = 0.88f + 0.12f * sin01((System.currentTimeMillis() * 0.008f) + phase[i]);
			spectrum[i] = MIN_LEVEL + value * ripple * (1f - MIN_LEVEL);
		}
	}

	private float measureWaveformFrequency(byte[] waveform, int frames, float frequency, int sampleRate) {
		double omega = 2.0 * Math.PI * frequency / sampleRate;
		double coeff = 2.0 * Math.cos(omega);
		double q0;
		double q1 = 0;
		double q2 = 0;

		for (int i = 0; i < frames; i++) {
			q0 = coeff * q1 - q2 + sample(waveform[i]);
			q2 = q1;
			q1 = q0;
		}

		return (float) Math.sqrt(Math.max(0, q1 * q1 + q2 * q2 - q1 * q2 * coeff)) / frames;
	}

	private float logFrequency(int index, int sampleRate) {
		float high = Math.min(HIGH_HZ, sampleRate / 2f);
		float ratio = index / (float) BAR_COUNT;
		return (float) (LOW_HZ * Math.pow(high / LOW_HZ, ratio));
	}

	private float measureBand(byte[] fft, int sampleRate, float startHz, float endHz) {
		int binCount = fft.length / 2;
		if (binCount <= 2) return 0f;
		float nyquist = sampleRate / 2f;
		int startBin = Math.max(1, Math.round(startHz / nyquist * binCount));
		int endBin = Math.max(startBin, Math.round(endHz / nyquist * binCount));
		endBin = Math.min(endBin, binCount - 1);
		float sum = 0f;
		float peak = 0f;
		int count = 0;

		for (int bin = startBin; bin <= endBin; bin++) {
			int reIndex = bin * 2;
			int imIndex = reIndex + 1;
			if (imIndex >= fft.length) break;
			float re = fft[reIndex];
			float im = fft[imIndex];
			float mag = (float) Math.hypot(re, im) / 128f;
			sum += mag;
			if (mag > peak) peak = mag;
			count++;
		}

		if (count == 0) return 0f;
		float avg = sum / count;
		return (avg * 0.65f) + (peak * 0.35f);
	}

	private void updateIdle(float dt) {
		long t = System.currentTimeMillis();
		float time = t * 0.001f;
		pulse = Math.max(0f, pulse - dt * 1.8f);
		for (int i = 0; i < BAR_COUNT; i++) {
			float band = i / (float) (BAR_COUNT - 1);
			float bassWeight = 1f - band;
			float midWeight = 1f - Math.abs((band - 0.48f) * 2f);
			float highWeight = band;
			float wave =
					0.38f * sin01(time * 1.35f + phase[i]) +
					0.34f * sin01(time * 2.2f + phase[i] * 1.7f) +
					0.28f * sin01(time * 3.1f + phase[i] * 0.53f);
			float missingAudioLift = hasRecentSignal() ? 0f : 0.08f;
			float frequencyShape = bassWeight * 0.055f + midWeight * 0.07f + highWeight * 0.04f;
			float fallbackPulse = missingAudioLift * sin01(time * 1.15f + phase[i] * 0.31f);
			idle[i] = MIN_LEVEL + (IDLE_LEVEL + frequencyShape) * wave;
			if (!hasRecentSignal()) idle[i] += fallbackPulse;
		}
	}

	private void composeTargets() {
		boolean live = hasRecentSignal();
		float beat = 0.08f * pulse;
		for (int i = 0; i < BAR_COUNT; i++) {
			if (live) {
				float shimmer = 1f + beat * (float) Math.sin(System.currentTimeMillis() * 0.012f + phase[i]);
				targets[i] = clamp(Math.max(idle[i], spectrum[i] * shimmer));
			} else {
				targets[i] = clamp(idle[i]);
			}
		}
	}

	private void smoothLevels(float dt) {
		for (int i = 0; i < BAR_COUNT; i++) {
			float diff = targets[i] - levels[i];
			float speed = (diff >= 0f) ? 22f : 8f;
			levels[i] += diff * Math.min(1f, speed * dt);
			if (hasRecentSignal()) spectrum[i] = Math.max(MIN_LEVEL, spectrum[i] - dt * 0.7f);
		}
	}

	private void resetLevels() {
		Arrays.fill(levels, MIN_LEVEL);
		Arrays.fill(targets, MIN_LEVEL);
		Arrays.fill(spectrum, MIN_LEVEL);
		Arrays.fill(idle, MIN_LEVEL);
		Arrays.fill(waveformPoints, 0f);
		lastWaveformTime = 0;
		pulse = 0f;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				downX = event.getX();
				downY = event.getY();
				setPressed(true);
				if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
				return true;
			case MotionEvent.ACTION_UP:
				setPressed(false);
				if ((Math.abs(event.getX() - downX) <= touchSlop)
						&& (Math.abs(event.getY() - downY) <= touchSlop)) {
					performClick();
				}
				if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
				return true;
			case MotionEvent.ACTION_CANCEL:
				setPressed(false);
				if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
				return true;
			default:
				return true;
		}
	}

	@Override
	public boolean performClick() {
		super.performClick();
		waveformMode = !waveformMode;
		invalidate();
		return true;
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		super.onDraw(canvas);
		int w = getWidth();
		int h = getHeight();
		if ((w <= 0) || (h <= 0)) return;

		if (waveformMode) {
			drawWaveform(canvas, w, h);
			return;
		}

		float gap = w * 0.008f;
		float barW = (w - gap * (BAR_COUNT + 1)) / BAR_COUNT;
		float baseY = h * 0.92f;
		for (int i = 0; i < BAR_COUNT; i++) {
			float barH = h * 0.75f * levels[i];
			float left = gap + i * (barW + gap);
			barPaint.setColor(getBarColor(i));
			canvas.drawRect(left, baseY - barH, left + barW, baseY, barPaint);
		}
	}

	private void drawWaveform(@NonNull Canvas canvas, int w, int h) {
		if (System.currentTimeMillis() - lastWaveformTime <= CAPTURE_STALE_MS) {
			drawCapturedWaveform(canvas, w, h);
		} else {
			drawSynthWaveform(canvas, w, h);
		}
	}

	private void drawCapturedWaveform(@NonNull Canvas canvas, int w, int h) {
		float centerY = h * 0.52f;
		float halfH = h * 0.42f;
		float step = w / (float) (WAVEFORM_POINTS - 1);
		float minStroke = Math.max(1f, getResources().getDisplayMetrics().density);
		wavePaint.setStrokeWidth(Math.max(minStroke, step * 0.72f));

		for (int i = 0; i < WAVEFORM_POINTS; i++) {
			float x = i * step;
			float sample = waveformPoints[i];
			float envelope = 0.22f + 0.78f * Math.min(1f, Math.abs(sample) * 1.45f);
			float amp = Math.max(1.5f, Math.abs(sample) * halfH * envelope);
			wavePaint.setColor(getBarColor(i * BAR_COUNT / WAVEFORM_POINTS));
			canvas.drawLine(x, centerY - amp, x, centerY + amp, wavePaint);
		}
	}

	private void drawSynthWaveform(@NonNull Canvas canvas, int w, int h) {
		float centerY = h * 0.52f;
		float halfH = h * 0.38f;
		float step = w / (float) (WAVEFORM_POINTS - 1);
		wavePaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));

		for (int i = 0; i < WAVEFORM_POINTS; i++) {
			float pos = i / (float) (WAVEFORM_POINTS - 1);
			float bandPos = pos * (BAR_COUNT - 1);
			int band = Math.min(BAR_COUNT - 2, (int) bandPos);
			float mix = bandPos - band;
			float level = levels[band] + (levels[band + 1] - levels[band]) * mix;
			float carrier = (float) Math.sin(i * 0.72f + phase[band] + System.currentTimeMillis() * 0.006f);
			float fine = (float) Math.sin(i * 2.35f + phase[band] * 1.9f);
			float sample = (carrier * 0.72f + fine * 0.28f) * level;
			float amp = Math.max(1.5f, Math.abs(sample) * halfH * 1.8f);
			wavePaint.setColor(getBarColor(band));
			canvas.drawLine(i * step, centerY - amp, i * step, centerY + amp, wavePaint);
		}
	}

	private static float sin01(float v) {
		return (float) Math.sin(v) * 0.5f + 0.5f;
	}

	private static float sample(byte b) {
		return ((b & 0xFF) - 128) / 128f;
	}

	private static float clamp(float v) {
		return Math.max(MIN_LEVEL, Math.min(1f, v));
	}

	private static int getBarColor(int i) {
		if (i < BAR_COUNT / 3) return BASS_COLOR;
		if (i < (BAR_COUNT / 3) * 2) return MID_COLOR;
		return HIGH_COLOR;
	}

	@Override
	protected void onDetachedFromWindow() {
		setAnimating(false);
		releaseVisualizer();
		super.onDetachedFromWindow();
	}
}
