package fr.inria.diverse.approxosc3x;

import com.jsyn.midi.MidiConstants;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.ports.UnitPort;
import com.jsyn.unitgen.*;
import com.softsynth.shared.time.TimeStamp;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Rebuild of the popular Osc3X from Fruity Loops
 * <p>
 * Created by elmarce on 31/08/16.
 */
public class Osc3x extends Circuit implements UnitVoice {

    //Oscillator 1
    UnitOscillator osc1;
    //Oscillator 2
    UnitOscillator osc2;
    //Oscillator 3
    UnitOscillator osc3;

    //Sampler filters
    FilterBiquadCommon filterLeft;
    FilterBiquadCommon filterRight;

    TwoInDualOut out;

    //Mixer to mix the output of the oscillators
    MixerStereo mixer;

    //Volume envelope
    EnvelopeDAHDSR volumeEnvelope;

    //Frequency envelope
    EnvelopeDAHDSR freqEnvelope;

    //Detune ports (Public to stay in the JSyn code style)
    public UnitInputPort detune2;
    //Detune ports (Public to stay in the JSyn code style)
    public UnitInputPort detune3;

    //Volume of each oscillator
    public UnitInputPort getOscillatorLevel() {
        return mixer.gain;
    }

    //Pan port
    public UnitInputPort getPan() {
        return mixer.pan;
    }


    public Osc3x(String presetPath) {
        loadPresetFromFile(presetPath);
    }

    public void defaultPreset() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("osc1", "sine");
        map.put("osc2", "sine");
        map.put("osc3", "sine");
        map.put("level1", 0.5);
        map.put("level2", 0.2);
        map.put("level3", 0.1);
        map.put("pan1", 0.5);
        map.put("pan2", -0.2);
        map.put("pan3", -0.1);
        map.put("filter", "LowPass");
        map.put("filterFreq", 22500);
        map.put("filterQ", 1.0);
        loadPreset(map);
    }

    public UnitOutputPort getOutput() {
        return out.output;
    }

    public void noteOn(double freq, double amplitude, TimeStamp timeStamp) {
        osc1.noteOn(freq, amplitude, timeStamp);
        if ( detune2.get(0) == 0 && detune3.get(0) == 0 ) {
            osc2.noteOn(freq, amplitude, timeStamp);
            osc3.noteOn(freq, amplitude, timeStamp);
        } else {
            double pitch = MidiConstants.convertFrequencyToPitch(freq);
            double pitch2 = pitch + detune2.get(0);
            double pitch3 = pitch + detune3.get(0);
            osc2.noteOn(MidiConstants.convertPitchToFrequency(pitch2), amplitude, timeStamp);
            osc3.noteOn(MidiConstants.convertPitchToFrequency(pitch3), amplitude, timeStamp);
        }
    }

    public void noteOff(TimeStamp timeStamp) {
        osc1.noteOff(timeStamp);
        osc2.noteOff(timeStamp);
        osc3.noteOff(timeStamp);
    }



    private double defaultVal(Object val, double defaultValue) {
        return val != null ? ((Number) val).doubleValue() : defaultValue;
    }

    public void loadPresetFromFile(String path) {
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(path));
            loadPreset(new String(encoded, "UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    
    public void loadPreset(String preset) {
        Yaml yaml = new Yaml();
        loadPreset((Map<String, Object>) yaml.load(preset));
    }

    private void loadPreset(Map<String, Object> map) {
        mixer = new MixerStereo(3);
        out = new TwoInDualOut();
        osc1 = buildOscilatorOfKind(1, map.get("osc1"));
        osc2 = buildOscilatorOfKind(2, map.get("osc2"));
        osc3 = buildOscilatorOfKind(3, map.get("osc3"));
        
        //Build filters
        Object kind = map.get("filter");
        String strKind = kind instanceof String ? (String) kind : null;
        double defaultFreq;
        if (strKind == null) throw new RuntimeException("Filter type unspecified");
        if (strKind.toUpperCase().equals("LOWPASS")) {
            filterLeft = new FilterLowPass();
            filterRight = new FilterLowPass();
            defaultFreq = 44100;
        } else if (strKind.toUpperCase().equals("HIGHPASS")) {
            filterRight = new FilterHighPass();
            filterLeft = new FilterHighPass();
            defaultFreq = 0;
        } else if (strKind.toUpperCase().equals("BANDPASS")) {
            filterRight = new FilterBandPass();
            filterLeft = new FilterBandPass();
            defaultFreq = 11500;
        } else
            throw new RuntimeException("Cant read oscillator type: " + kind + " for oscillator.");

        filterLeft.Q.setup(0.1D, defaultVal(map.get("filterQ"), 1), 10.D);
        filterRight.Q.setup(0.1D, defaultVal(map.get("filterQ"), 1), 10.D);
        filterLeft.frequency.set(defaultVal(map.get("filterFreq"), defaultFreq));
        filterRight.frequency.set(defaultVal(map.get("filterFreq"), defaultFreq));
        
        getOscillatorLevel().set(0, defaultVal(map.get("level1"), 1.0));
        getOscillatorLevel().set(1, defaultVal(map.get("level2"), 1.0));
        getOscillatorLevel().set(2, defaultVal(map.get("level3"), 1.0));

        getPan().set(0, defaultVal(map.get("pan1"), 0.0));
        getPan().set(1, defaultVal(map.get("pan2"), 0.0));
        getPan().set(2, defaultVal(map.get("pan3"), 0.0));

        //Connect the circuit
        addPort(detune2 = new UnitInputPort("detune2"));
        addPort(detune3 = new UnitInputPort("detune3"));

        addPort(getPan(), "pan");
        addPort(getOscillatorLevel(), "osc_level");
        addPort(out.output, "output");

        add(osc1);
        add(osc2);
        add(osc3);
        add(filterRight);
        add(filterLeft);
        add(mixer);
        add(out);

        osc1.amplitude.set(0.0);
        osc2.amplitude.set(0.0);
        osc3.amplitude.set(0.0);

        osc1.output.connect(0, mixer.input, 0);
        osc2.output.connect(0, mixer.input, 1);
        osc3.output.connect(0, mixer.input, 2);

        mixer.output.connect(0, filterLeft.input, 0);
        mixer.output.connect(1, filterRight.input, 0);

        filterRight.output.connect(out.inputA);
        filterLeft.output.connect(out.inputB);

        detune2.set(0, defaultVal(map.get("detune2"), 0.0));
        detune3.set(0, defaultVal(map.get("detune3"), 0.0));
    }

    private UnitOscillator buildOscilatorOfKind(int oscIndex, Object kind) {
        Subtract sb;
        //OscilatorKind {SINE, TRIANGLE, SQUARE, SAW, NOISE}
        String strKind = kind instanceof String ? (String) kind : null;
        if (strKind == null) throw new RuntimeException("Oscilator type unspecified for oscillator " + oscIndex);
        if (strKind.toUpperCase().equals("SINE")) return new SineOscillator();
        else if (strKind.toUpperCase().equals("TRIANGLE")) return new TriangleOscillator();
        else if (strKind.toUpperCase().equals("SQUARE")) return new SquareOscillator();
        else if (strKind.toUpperCase().equals("SAW")) return new SawtoothOscillator();

        throw new RuntimeException("Cant read oscillator type: " + kind + " for oscillator " + oscIndex);
    }
}
