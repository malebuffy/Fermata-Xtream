package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.auto;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.Context.LOCATION_SERVICE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.util.function.Consumer;

import me.aap.utils.function.Cancellable;
import me.aap.utils.log.Log;

final class LocationSpeedProvider implements LocationListener, Cancellable {
	private final LocationManager locationManager;
	private final Consumer<Float> listener;
	private boolean active;

	private LocationSpeedProvider(LocationManager locationManager, Consumer<Float> listener) {
		this.locationManager = locationManager;
		this.listener = listener;
	}

	static Cancellable start(Context ctx, Consumer<Float> listener) {
		if (!hasLocationPermission(ctx)) {
			Log.i("Location speed fallback unavailable: location permission is not granted");
			return Cancellable.CANCELED;
		}

		LocationManager lm = (LocationManager) ctx.getSystemService(LOCATION_SERVICE);
		if (lm == null) return Cancellable.CANCELED;

		var provider = new LocationSpeedProvider(lm, listener);
		provider.start(LocationManager.GPS_PROVIDER);
		provider.start(LocationManager.NETWORK_PROVIDER);
		return provider.active ? provider : Cancellable.CANCELED;
	}

	static boolean hasLocationPermission(Context ctx) {
		return (ContextCompat.checkSelfPermission(ctx, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) ||
				(ContextCompat.checkSelfPermission(ctx, ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED);
	}

	private void start(String provider) {
		try {
			if (!locationManager.isProviderEnabled(provider)) return;
			Location location = locationManager.getLastKnownLocation(provider);
			if (location != null) onLocationChanged(location);
			locationManager.requestLocationUpdates(provider, 1000, 0, this, Looper.getMainLooper());
			active = true;
		} catch (Throwable err) {
			Log.e(err, "Failed to start location speed fallback: ", provider);
		}
	}

	@Override
	public void onLocationChanged(Location location) {
		if (location.hasSpeed()) listener.accept(location.getSpeed());
	}

	@Override
	public void onProviderDisabled(String provider) {
	}

	@Override
	public void onProviderEnabled(String provider) {
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	@Override
	public boolean cancel() {
		if (!active) return false;
		active = false;
		try {
			locationManager.removeUpdates(this);
		} catch (Throwable err) {
			Log.e(err, "Failed to stop location speed fallback");
		}
		return true;
	}
}
