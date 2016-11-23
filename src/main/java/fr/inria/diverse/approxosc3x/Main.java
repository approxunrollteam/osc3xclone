/*
 * Copyright 2010 Phil Burk, Mobileer Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.inria.diverse.approxosc3x;

import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.scope.AudioScope;
import com.jsyn.unitgen.LineOut;
import com.jsyn.unitgen.MixerStereo;
import com.jsyn.util.VoiceAllocator;
import com.jsyn.util.WaveRecorder;
import fr.inria.diverse.approxosc3x.midi.MidiPlayer;
import fr.inria.robco.evaluators.PEAQEvaluator;
import fr.inria.robco.utils.SQLiteConnector;
import fr.log.Log;

import javax.mail.*;
import javax.mail.internet.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;

/***************************************************************
 * Play notes using a WaveShapingVoice. Allocate the notes using a VoiceAllocator.
 *
 * @author Phil Burk (C) 2010 Mobileer Inc
 */
public class Main /*extends JApplet*/ implements Runnable {
    private static final long serialVersionUID = -7459137388629333223L;
    private Synthesizer synth;
    private LineOut lineOut;
    private AudioScope scope;
    private MixerStereo mixer;
    private final static int MAX_VOICES = 8;
    private VoiceAllocator allocator;
    private WaveRecorder recorder;

    private String midiFile;
    private String preset;
    private int tempo;
    private int track;
    private File outputFile;

    //Hear the output
    private boolean hearOutput = true;

    //Indicates that the current execution of the program is using the non-approximate version
    private boolean isAccurateRun = true;

    static String[] MIDI_FILES = {
            "indiana-jones.mid",
            "mission-imposible.mid",
            "starwars.mid",
            "mario.mid",
            "casablanca.mid",
            "mortalkombat.mid",
            "century.mid",
            "panther.mid",
            "countdown.mid",
            "al-adagi.mid"
    };

    static int[] tempos = {
            133,
            160,
            120,
            110,
            88,
            130,
            110,
            116,
            118,
            60
    };

    static int[] tracks = {
            4,
            7,
            4,
            3,
            3,
            2,
            4,
            2,
            2,
            2,
    };

    private static final String LOOP_VAR_KEY = "FR_DIV_APPROX_LOOP_INDX";

    private static void assertThat(boolean condition, String msg) {
        if (!condition) {
            System.out.println(msg);
            System.exit(1);
        }
    }

    public static void sendMail() {
        final String username = "marcelino.rguez.cancio@gmail.com";
        final String password = "1qaz2wsx3edc*-";

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress("marcelino.rguez.cancio@gmail.com"));
            message.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse("marcelino.rguez.cancio@gmail.com"));
            message.setSubject("Experiment complete!");
            message.setText("The experiment is complete");
            Transport.send(message);
        } catch (javax.mail.MessagingException e) {
            e.printStackTrace();
        }
    }

    /* Can be run as either an application or as an applet. */
    public static void main(String args[]) throws InterruptedException {
        try {
            //"src/main/resources/midi"
            String midiFolder = args[0] + "/";
            assertThat(new File(midiFolder).exists(), "Unable to find MIDI FOLDER " + midiFolder);
            //"src/main/resources/presets"
            String presetFolder = args[1];
            assertThat(new File(presetFolder).exists(), "Unable to find preset FOLDER " + presetFolder);
            //"src/main/resources/output/"
            String outputFolder = args[2];
            assertThat(new File(outputFolder).exists(), "Unable to find output FOLDER: " + outputFolder);
            String refPath = args[3];
            assertThat(new File(refPath).exists(), "Unable to find reference FOLDER " + refPath);
            String peaqPath = args[4];
            assertThat(new File(peaqPath).exists(), "Unable to find PEAQ FOLDER " + peaqPath);
            String dbPath = args[5];
            assertThat(new File(peaqPath).exists(), "Unable to find DB FILE " + dbPath);

            boolean containsEnvKey = System.getenv().containsKey(LOOP_VAR_KEY);
            int loopVarKey = containsEnvKey ? Integer.parseInt(System.getenv(LOOP_VAR_KEY)) : -1;

            String experimentName = args.length < 6 ? "" : args[6];

            boolean accurateRun = args.length <= 6 || args[6].equals("Accurate") || loopVarKey == -1;

            long timeInMillis = System.currentTimeMillis();
            Calendar cal1 = Calendar.getInstance();
            cal1.setTimeInMillis(timeInMillis);
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "dd-MM-yyyy--hh-mm-ss");

            StringBuilder sb = new StringBuilder(outputFolder).append("approx-0sc3x").append(dateFormat.format(cal1.getTime()));
            if (containsEnvKey) sb.append("-LOOP-").append(loopVarKey);
            else sb.append("-REF");
            String outputFolderName = sb.append("/").toString();
            new File(outputFolderName).mkdir();

            for (int j = 0; j < 10; j++)
                for (int i = 0; i < 10; i++) {
                    //RESET AND MAKE SURE IS RESET
                    fr.log.Log.reset();
                    if (new File(System.getProperty("user.home") + "/fr_div_approx_loop_iscovered.covered").exists())
                        throw new RuntimeException("Cannot reset cover file");

                    Main applet = new Main();
                    applet.isAccurateRun = accurateRun;
                    applet.midiFile = midiFolder + MIDI_FILES[j];
                    applet.preset = new File(presetFolder + "/0" + i + ".preset").getAbsolutePath();
                    applet.tempo = tempos[j];
                    applet.track = tracks[j];
                    File outputFile = new File(outputFolderName +
                            MIDI_FILES[j].substring(0, MIDI_FILES[j].indexOf(".")) + "_0" + i + ".wav");
                    applet.outputFile = outputFile;
                    applet.start();
                    Thread.sleep(11000);
                    applet.stop();

                    //DELETE OUTPUT IF THE LOOP WAS NOT COVERED
                    if (!applet.isAccurateRun)
                        if (!Log.isCovered()) {
                            System.out.println("WARNING: The expected loop was not covered, deleting output...");
                            if (!applet.outputFile.delete()) throw new RuntimeException("Could not delete output file");
                        } else {
                            SQLiteConnector connector = new SQLiteConnector(dbPath, "loop_execution_times", "TIMES_EXECUTED",
                                    "LOOP_Id", "FILE_GENERATED", "Id");
                            connector.write(fr.log.Log.getCounter(), loopVarKey, outputFile.getName(),
                                    outputFile.getName() + "-" + loopVarKey);
                            connector.close();
                        }
                }

            if (!accurateRun && containsEnvKey) {
                //Evaluate the recently produced sound
                PEAQEvaluator peaqEvaluator = new PEAQEvaluator();
                peaqEvaluator.setPeaqPath(peaqPath);
                peaqEvaluator.setReferencePath(refPath);
                peaqEvaluator.setControlPath(outputFolderName);
                peaqEvaluator.setExperimentName("LOOP-" + loopVarKey + "-" + experimentName);
                peaqEvaluator.setDbPath(dbPath);
                peaqEvaluator.eval();

            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            sendMail();
        }
    }

    /*
     * Setup synthesis.
     */
    //@Override
    public void start() {

        //setLayout(new BorderLayout());
        synth = JSyn.createSynthesizer();

        if (hearOutput) synth.add(lineOut = new LineOut());
        synth.add(mixer = new MixerStereo(MAX_VOICES * 2));

        try {
            recorder = new WaveRecorder(synth, outputFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        if (hearOutput) {
            mixer.output.connect(0, recorder.getInput(), 0);
            mixer.output.connect(1, recorder.getInput(), 1);
            mixer.output.connect(0, lineOut.getInput(), 0);
            mixer.output.connect(1, lineOut.getInput(), 1);
        } else {
            mixer.output.connect(0, recorder.getInput(), 0);
            mixer.output.connect(1, recorder.getInput(), 1);
        }
        Osc3x[] voices = new Osc3x[MAX_VOICES];

        System.out.println("Preset: " + preset);
        for (int i = 0; i < MAX_VOICES; i++) {
            Osc3x voice = new Osc3x(preset);
            synth.add(voice);
            voice.usePreset(0);
            voice.getOutput().connect(0, mixer.input, i * 2);
            voice.getOutput().connect(1, mixer.input, i * 2 + 1);
            mixer.pan.set(i * 2, -1.0);
            mixer.pan.set(i * 2 + 1, 1.0);
            voices[i] = voice;
        }
        allocator = new VoiceAllocator(voices);
        // Start synthesizer using default stereo output at 44100 Hz.
        recorder.start();
        synth.start();
        if (hearOutput) lineOut.start();

        // Use a scope to show the mixed output.
        /*
        scope = new AudioScope(synth);
        scope.addProbe(mixer.output);
        scope.setTriggerMode(AudioScope.TriggerMode.NORMAL);
        scope.getView().setControlsVisible(false);
        //add(BorderLayout.CENTER, scope.getView());
        scope.start();

        /* Synchronize Java display. */
        //getParent().validate();
        //getToolkit().sync();

        // start thread that plays notes
        Thread thread = new Thread(this);
        thread.start();

    }

    //@Override
    public void stop() {
        // tell song thread to finish
        //removeAll();
        allocator.allNotesOff(synth.createTimeStamp());
        recorder.stop();
        if (hearOutput) lineOut.stop();
        try {
            recorder.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        synth.stop();


    }

    public void run() {
        //MidiPlayer player = new MidiPlayer(allocator, 2, 60, "/home/elmarce/PROJECTS/DATA/MIDI/al_adagi.mid");
        MidiPlayer player = new MidiPlayer(allocator, track, tempo, midiFile);
        try {
            player.play();
            player.putSynthToSleep(synth);
        } catch (Exception e) {
            e.printStackTrace();
        }
        stop();
    }
}

