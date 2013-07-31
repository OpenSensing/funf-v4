package edu.mit.media.funf.probe.builtin;

import android.net.Uri;
import android.provider.MediaStore.Audio;

public class InternalAudioMediaProbe extends AudioMediaProbe {
	@Override
	protected Uri getContentProviderUri() {
		return Audio.Media.INTERNAL_CONTENT_URI;
	}

}
