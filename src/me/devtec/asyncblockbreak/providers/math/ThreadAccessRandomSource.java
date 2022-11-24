package me.devtec.asyncblockbreak.providers.math;

import java.util.Random;

import me.devtec.shared.utility.StringUtils;

public class ThreadAccessRandomSource extends Random {
	private static final long serialVersionUID = 1L;

	public ThreadAccessRandomSource() {
		super(StringUtils.random.nextLong());
	}

	public int percentChance(int bound) {
		if (bound <= 0)
			throw new IllegalArgumentException("Bound must be positive");
		if ((bound & bound - 1) == 0)
			return (int) ((long) bound * (long) next(31) >> 31);
		int next;
		int current;
		do {
			next = next(31);
			current = next % bound;
		} while (next - current + bound - 1 < 0);

		return current;
	}

	public float floatChance() {
		return next(24) * 5.9604645E-8F;
	}
}