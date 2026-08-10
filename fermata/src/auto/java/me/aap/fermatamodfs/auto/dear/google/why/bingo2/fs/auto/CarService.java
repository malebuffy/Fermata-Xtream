package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.auto;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarActivityService;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.FermataMediaServiceConnection;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.media.service.MediaSessionCallback;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class CarService extends CarActivityService {

	@Override
	public Class<? extends CarActivity> getCarActivity() {
		return MainCarActivity.class;
	}

	@Override
	public void onCreate() {
		Log.d("Creating CarService: " + this);
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		Log.d("Destroying CarService: " + this);
		FermataMediaServiceConnection s = MainCarActivity.service;
		if (s == null) return;
		MainCarActivity.service = null;
		MediaSessionCallback cb = s.getMediaSessionCallback();
		if ((cb != null) && cb.isPlaying()) cb.onPause();
		s.disconnect();
		super.onDestroy();
	}
}
