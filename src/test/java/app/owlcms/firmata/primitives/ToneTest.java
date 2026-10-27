package app.owlcms.firmata.primitives;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ToneTest {

	@Test
	public void lowFrequencyIsUnchanged() {
		assertEquals(262, Tone.playableFrequency(262, 1));
		assertEquals(262, Tone.playableFrequency(262, 2));
		assertEquals(523, Tone.playableFrequency(523, 1));
	}

	@Test
	public void highFrequencyIsLoweredByOctaves() {
		// C7 cannot be generated over the serial link; it is played as C5 on one port
		assertEquals(523, Tone.playableFrequency(2093, 1));
		assertEquals(262, Tone.playableFrequency(2093, 2));
		assertEquals(131, Tone.playableFrequency(2093, 3));
	}

	@Test
	public void moreSwitchedPortsMeanLowerCeiling() {
		assertEquals(523, Tone.playableFrequency(523, 1));
		assertEquals(262, Tone.playableFrequency(523, 2));
		assertEquals(131, Tone.playableFrequency(523, 3));
	}

	@Test
	public void portCountBelowOneIsTreatedAsOne() {
		assertEquals(523, Tone.playableFrequency(2093, 0));
	}
}
