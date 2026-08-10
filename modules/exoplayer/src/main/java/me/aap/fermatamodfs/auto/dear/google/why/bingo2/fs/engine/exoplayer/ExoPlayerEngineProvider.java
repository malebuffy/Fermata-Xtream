package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.engine.exoplayer;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine.MediaEngine;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine.MediaEngine.Listener;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.engine.MediaEngineProvider;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("unused")
public class ExoPlayerEngineProvider implements MediaEngineProvider {
	private Context ctx;

	@Override
	public void init(Context ctx) {
		this.ctx = ctx;
	}

	@OptIn(markerClass = UnstableApi.class)
	@Override
	public MediaEngine createEngine(Listener listener) {
		return new ExoPlayerEngine(ctx, listener);
	}
}
