package fr.inria.diverse.approxosc3x;

import com.jsyn.unitgen.SawtoothOscillator;
import com.jsyn.unitgen.SineOscillator;
import com.jsyn.unitgen.SquareOscillator;
import com.jsyn.unitgen.TriangleOscillator;
import org.junit.Test;

import java.io.File;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Test the load presets
 *
 * Created by elmarce on 15/09/16.
 */
public class LoadPresetTest {

    @Test
    public void loadPresetTests() {
        Osc3x osc3x = new Osc3x(new File("src/main/resources/presets/00.preset").getAbsolutePath());

        assertTrue(osc3x.osc1 instanceof SquareOscillator);
        assertTrue(osc3x.osc2 instanceof SineOscillator);
        assertTrue(osc3x.osc3 instanceof TriangleOscillator);

        assertEquals(osc3x.getOscillatorLevel().get(0), 1.0);
        assertEquals(osc3x.getOscillatorLevel().get(1), 0.5);
        assertEquals(osc3x.getOscillatorLevel().get(2), 0.8);

        assertEquals(osc3x.getPan().get(0), 0.5);
        assertEquals(osc3x.getPan().get(1), -0.5);
        assertEquals(osc3x.getPan().get(2), 0.0);
    }

}
