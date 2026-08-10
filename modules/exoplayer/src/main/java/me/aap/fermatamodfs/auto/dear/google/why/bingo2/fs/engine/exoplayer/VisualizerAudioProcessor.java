package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.engine.exoplayer;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine.AudioVisualizerFeed;

@UnstableApi
final class VisualizerAudioProcessor implements AudioProcessor {
	private static final int MAX_SAMPLES = 512;
	private static final int LOW_HZ = 32;
	private static final int HIGH_HZ = 16000;
	private static final long MIN_PUBLISH_INTERVAL_MS = 24;
	private final float[] mono = new float[MAX_SAMPLES];
	private final float[] bands = new float[AudioVisualizerFeed.BAND_COUNT];
	private AudioFormat audioFormat = AudioFormat.NOT_SET;
	private ByteBuffer outputBuffer = EMPTY_BUFFER;
	private boolean inputEnded;
	private long lastPublishTime;

	@NonNull
	@Override
	public AudioFormat configure(@NonNull AudioFormat inputAudioFormat)
			throws UnhandledAudioFormatException {
		switch (inputAudioFormat.encoding) {
			case C.ENCODING_PCM_8BIT:
			case C.ENCODING_PCM_16BIT:
			case C.ENCODING_PCM_24BIT:
			case C.ENCODING_PCM_32BIT:
			case C.ENCODING_PCM_FLOAT:
				audioFormat = inputAudioFormat;
				return inputAudioFormat;
			default:
				throw new UnhandledAudioFormatException(inputAudioFormat);
		}
	}

	@Override
	public boolean isActive() {
		return audioFormat != AudioFormat.NOT_SET;
	}

	@Override
	public void queueInput(@NonNull ByteBuffer inputBuffer) {
		if (outputBuffer.hasRemaining()) return;
		publishLevels(inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN));
		outputBuffer = inputBuffer;
		inputBuffer.position(inputBuffer.limit());
	}

	@Override
	public void queueEndOfStream() {
		inputEnded = true;
	}

	@NonNull
	@Override
	public ByteBuffer getOutput() {
		ByteBuffer out = outputBuffer;
		outputBuffer = EMPTY_BUFFER;
		return out;
	}

	@Override
	public boolean isEnded() {
		return inputEnded && !outputBuffer.hasRemaining();
	}

	@Override
	public void flush() {
		outputBuffer = EMPTY_BUFFER;
		inputEnded = false;
	}

	@Override
	public void reset() {
		flush();
		audioFormat = AudioFormat.NOT_SET;
		lastPublishTime = 0;
	}

	private void publishLevels(ByteBuffer buf) {
		long now = System.currentTimeMillis();
		if (now - lastPublishTime < MIN_PUBLISH_INTERVAL_MS) return;
		lastPublishTime = now;

		int channels = Math.max(1, audioFormat.channelCount);
		int bytesPerFrame = Math.max(1, audioFormat.bytesPerFrame);
		int frames = Math.min(MAX_SAMPLES, buf.remaining() / bytesPerFrame);
		if (frames < 32) return;

		float sum = 0f;
		for (int i = 0; i < frames; i++) {
			float sample = 0f;
			int framePos = buf.position() + i * bytesPerFrame;
			for (int ch = 0; ch < channels; ch++) sample += readSample(buf, framePos, ch);
			sample /= channels;
			mono[i] = sample;
			sum += sample * sample;
		}

		float rms = (float) Math.sqrt(sum / frames);
		buildBands(frames, rms);
		AudioVisualizerFeed.publish(bands, rms);
	}

	private float readSample(ByteBuffer buf, int framePos, int channel) {
		int bytesPerSample = Math.max(1, audioFormat.bytesPerFrame / Math.max(1, audioFormat.channelCount));
		int pos = framePos + channel * bytesPerSample;
		return switch (audioFormat.encoding) {
			case C.ENCODING_PCM_8BIT -> ((buf.get(pos) & 0xFF) - 128) / 128f;
			case C.ENCODING_PCM_16BIT -> buf.getShort(pos) / 32768f;
			case C.ENCODING_PCM_24BIT -> {
				int v = (buf.get(pos) & 0xFF) | ((buf.get(pos + 1) & 0xFF) << 8) |
						(buf.get(pos + 2) << 16);
				yield v / 8388608f;
			}
			case C.ENCODING_PCM_32BIT -> buf.getInt(pos) / 2147483648f;
			case C.ENCODING_PCM_FLOAT -> Math.max(-1f, Math.min(1f, buf.getFloat(pos)));
			default -> 0f;
		};
	}

	private void buildBands(int frames, float rms) {
		int sampleRate = Math.max(1, audioFormat.sampleRate);

		for (int i = 0; i < bands.length; i++) {
			float low = logFrequency(i, sampleRate);
			float high = logFrequency(i + 1, sampleRate);
			float center = (float) Math.sqrt(low * high);
			float value = (magnitude(frames, center, sampleRate) * 0.72f) + (rms * 0.28f);
			value = (float) Math.pow(Math.min(1f, value * 7f), 0.62f);
			bands[i] = 0.025f + value * 0.975f;
		}
	}

	private float magnitude(int frames, float frequency, int sampleRate) {
		double omega = 2.0 * Math.PI * frequency / sampleRate;
		double coeff = 2.0 * Math.cos(omega);
		double q0;
		double q1 = 0;
		double q2 = 0;

		for (int i = 0; i < frames; i++) {
			q0 = coeff * q1 - q2 + mono[i];
			q2 = q1;
			q1 = q0;
		}

		return (float) Math.sqrt(Math.max(0, q1 * q1 + q2 * q2 - q1 * q2 * coeff)) / frames;
	}

	private static float logFrequency(int index, int sampleRate) {
		float high = Math.min(HIGH_HZ, sampleRate / 2f);
		float ratio = index / (float) AudioVisualizerFeed.BAND_COUNT;
		return (float) (LOW_HZ * Math.pow(high / LOW_HZ, ratio));
	}
}
