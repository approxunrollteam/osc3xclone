package fr.inria.diverse.approxosc3x.midi;

import com.jsyn.Synthesizer;
import com.jsyn.midi.MidiConstants;
import com.jsyn.util.VoiceAllocator;
import com.softsynth.shared.time.TimeStamp;

import javax.sound.midi.*;
import java.io.File;

/**
 * Reads and plays a midi file in a set of unit voices
 * <p>
 * Created by elmarce on 13/09/16.
 */
public class MidiPlayer {
    //Voices to play
    private VoiceAllocator voices = null;

    //Default MIDI file
    private String filePath = "";

    //Default track
    private int track = 1;

    //Default tempo
    private int tempo = 120;

    //Track length in milliseconds
    private long trackLength = 0;

    //Time of the first note in secods
    private double firstTime = 0.0;

    //Amount of time we will be playing
    private double playLength = 10.0;

    public MidiPlayer(VoiceAllocator voices, int track, int tempo, String filePath) {
        this.setTrack(track);
        this.setTempo(tempo);
        this.setVoices(voices);
        this.setFilePath(filePath);
    }

    public MidiPlayer() {

    }

    public void putSynthToSleep(Synthesizer synthesizer) throws InterruptedException {
        synthesizer.sleepUntil(playLength);
        //synthesizer.sleepUntil(synthesizer.getCurrentTime() + trackLength);
    }



    /**
     * Plays a midi file in a unit voice
     */
    public void play() {
        try {
            Sequence sequence = MidiSystem.getSequence(new File(filePath));
            trackLength = sequence.getMicrosecondLength();
            if (getTrack() > sequence.getTracks().length)
                throw new InvalidMidiDataException("The file has less tracks than " + getTrack());
            Track trk = sequence.getTracks()[getTrack()];
            firstTime = 0.0;

            for (int i = 0; i < trk.size(); i++) {
                MidiEvent event = trk.get(i);
                byte[] message = event.getMessage().getMessage();
                int status = message[0];
                int pitch = message[1];
                int command = status & 0xF0;
                switch (command) {
                    case MidiConstants.NOTE_ON:
                        int velocity = message[2];
                        if (velocity == 0) noteOff(pitch, getTime(event.getTick(), tempo, sequence.getResolution()));
                        else noteOn(pitch, velocity, getTime(event.getTick(), tempo, sequence.getResolution()));
                        break;
                    case MidiConstants.NOTE_OFF:
                        noteOff(pitch, getTime(event.getTick(), tempo, sequence.getResolution()));
                        break;
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void noteOn(int pitch, int velocity, TimeStamp timeStamp) {
        if ( timeStamp.getTime() > playLength ) return;
        System.out.println("Note ON - " + pitch + " V: " + velocity + " at: " + timeStamp.getTime());
        voices.noteOn(pitch, MidiConstants.convertPitchToFrequency(pitch), (double) velocity / 256.0, timeStamp);
    }

    public void noteOff(int pitch, TimeStamp timeStamp) {
        if ( timeStamp.getTime() > playLength ) return;
        System.out.println("Note OF - " + pitch + " at: " + timeStamp.getTime());
        voices.noteOff(pitch, timeStamp);
    }

    private TimeStamp getTime(double tick, double bmp, double resolution) {
        //TimeStamps is in seconds
        //The following equation came from here:
        //(http://stackoverflow.com/questions/2038313/midi-ticks-to-actual-playback-seconds-midi-music)
        double time = tick * 60.0 / (bmp * resolution);

        //Start playing right away, without waiting
        if ( firstTime == 0.0 ) firstTime = time - 1.0;
        return new TimeStamp(time - firstTime);
    }

    public VoiceAllocator getVoices() {
        return voices;
    }

    public void setVoices(VoiceAllocator voices) {
        this.voices = voices;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getTrack() {
        return track;
    }

    public void setTrack(int track) {
        this.track = track;
    }

    public int getTempo() {
        return tempo;
    }

    public void setTempo(int tempo) {
        this.tempo = tempo;
    }

    public double getPlayLength() {
        return playLength;
    }

    public void setPlayLength(double playLength) {
        this.playLength = playLength;
    }
}
