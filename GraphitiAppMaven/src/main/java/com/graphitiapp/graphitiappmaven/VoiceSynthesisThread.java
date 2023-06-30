package com.graphitiapp.graphitiappmaven;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;

public class VoiceSynthesisThread extends Thread {
    private static final String VOICENAME_KEVIN = "kevin16";

    private final String text;

    public VoiceSynthesisThread(String text) {
        this.text = text;
    }

    @Override
    public void run() {
        VoiceManager voiceManager = VoiceManager.getInstance();
        Voice voice = voiceManager.getVoice(VOICENAME_KEVIN);
        voice.allocate();
        voice.speak(text);
        voice.deallocate();
    }
}
