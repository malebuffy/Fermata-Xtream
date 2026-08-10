package me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.mlkit;

import android.util.Pair;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.mlkit.common.MlKit;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.List;
import java.util.Locale;

import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.AddonInfo;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.FermataAddon;
import me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.addon.TranslateAddon;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.collection.CollectionUtils;

@Keep
@SuppressWarnings("unused")
public class MlkitTranslateAddon extends TranslateAddon {
	private static final AddonInfo info =
			FermataAddon.findAddonInfo(MlkitTranslateAddon.class.getName());

	static {
		try {
			MlKit.initialize(App.get());
		} catch (Exception ignored) {}
	}

	@Override
	protected FutureSupplier<Translator> getTranslator(String srcLang, String targetLang) {
		var translator = Translation.getClient(new TranslatorOptions.Builder()
				.setSourceLanguage(srcLang).setTargetLanguage(targetLang)
				.setExecutor(App.get().getExecutor()).build());
		var p = new Promise<Translator>();
		translator.downloadModelIfNeeded()
				.addOnSuccessListener(v -> p.complete(new MlkitTranslator(translator)))
				.addOnCanceledListener(p::cancel)
				.addOnFailureListener(p::completeExceptionally);
		return p;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@Override
	protected List<Pair<String, String>> getSupportedLanguages(@Nullable String srcLang) {
		var locale = Locale.getDefault(Locale.Category.DISPLAY);
		var langs = CollectionUtils.map(TranslateLanguage.getAllLanguages(),
				lang -> new Pair<>(lang, new Locale(lang).getDisplayLanguage(locale)));
		langs.sort((a, b) -> a.second.compareToIgnoreCase(b.second));
		return langs;
	}

	private record MlkitTranslator(com.google.mlkit.nl.translate.Translator translator)
			implements Translator {

		public FutureSupplier<String> translate(String text) {
			var p = new Promise<String>();
			translator.translate(text)
					.addOnSuccessListener(p::complete)
					.addOnCanceledListener(p::cancel)
					.addOnFailureListener(p::completeExceptionally);
			return p;
		}

		public boolean supportsBatch() {
			return true;
		}
	}
}
