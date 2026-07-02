package tech.onetap.util.render.builders.states;

import java.awt.*;

public class QuadColorState {

	public static final QuadColorState TRANSPARENT = new QuadColorState(0, 0, 0, 0);
	public static final QuadColorState WHITE = new QuadColorState(-1, -1, -1, -1);

	int color1;
	int color2;
	int color3;
	int color4;

	public int color1() {
		return color1;
	}

	public int color2() {
		return color2;
	}

	public int color3() {
		return color3;
	}

	public int color4() {
		return color4;
	}

	public QuadColorState(int color1, int color2, int color3, int color4) {
		this.color1 = color1;
		this.color2 = color2;
		this.color3 = color3;
		this.color4 = color4;
	}

	public QuadColorState(Color color1, Color color2, Color color3, Color color4) {
		this.color1 = color1.getRGB();
		this.color2 = color2.getRGB();
		this.color3 = color3.getRGB();
		this.color4 = color4.getRGB();
	}

	public QuadColorState(Color color) {
		this(color, color, color, color);
	}

	public QuadColorState(int color) {
		this(color, color, color, color);
	}
}