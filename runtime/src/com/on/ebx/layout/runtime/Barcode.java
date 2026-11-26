package com.on.ebx.layout.runtime;

import com.fasterxml.jackson.databind.JsonNode;

/** An EAN-13, EAN-8 or UPC-A code drawn as bars (SVG), with the GS1 check digit verified; GTIN-14 shows the digits only. */
final class Barcode
{
	private static final String[] L = { "0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011" };
	private static final String[] G = { "0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111" };
	private static final String[] R = { "1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100" };
	private static final String[] PARITY = { "LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL" };

	private Barcode()
	{
	}

	/** True when the last digit is the GS1 check digit of the others. */
	static boolean checkDigitOk(final String pDigits)
	{
		if (pDigits == null || !pDigits.matches("[0-9]{8,14}"))
		{
			return false;
		}
		int sum = 0;
		for (int i = pDigits.length() - 2, w = 3; i >= 0; i--, w = 4 - w)
		{
			sum += (pDigits.charAt(i) - '0') * w;
		}
		return (10 - sum % 10) % 10 == pDigits.charAt(pDigits.length() - 1) - '0';
	}

	static String bits(final String pDigits)
	{
		String d = pDigits;
		if (d.length() == 12)
		{
			d = "0" + d;
		}
		final StringBuilder b = new StringBuilder("101");
		if (d.length() == 13)
		{
			final String parity = Barcode.PARITY[d.charAt(0) - '0'];
			for (int i = 1; i <= 6; i++)
			{
				b.append(parity.charAt(i - 1) == 'L' ? Barcode.L[d.charAt(i) - '0'] : Barcode.G[d.charAt(i) - '0']);
			}
			b.append("01010");
			for (int i = 7; i <= 12; i++)
			{
				b.append(Barcode.R[d.charAt(i) - '0']);
			}
		}
		else if (d.length() == 8)
		{
			for (int i = 0; i < 4; i++)
			{
				b.append(Barcode.L[d.charAt(i) - '0']);
			}
			b.append("01010");
			for (int i = 4; i < 8; i++)
			{
				b.append(Barcode.R[d.charAt(i) - '0']);
			}
		}
		else
		{
			return null;
		}
		return b.append("101").toString();
	}

	static String html(final RenderContext rc, final String pValue, final JsonNode f)
	{
		final String digits = pValue.replaceAll("[\\s-]", "");
		final StringBuilder h = new StringBuilder("<span class=\"ldf-bc\">");
		final String bits = digits.matches("[0-9]{8}|[0-9]{12}|[0-9]{13}") ? Barcode.bits(digits) : null;
		if (bits != null)
		{
			h.append("<svg viewBox=\"0 0 ").append(bits.length() + 18).append(" 44\" width=\"").append((bits.length() + 18) * 2).append("\" height=\"60\" role=\"img\" aria-label=\"")
				.append(LayoutPane.esc(digits)).append("\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>");
			for (int i = 0; i < bits.length(); i++)
			{
				if (bits.charAt(i) == '1')
				{
					h.append("<rect x=\"").append(i + 9).append("\" y=\"2\" width=\"1\" height=\"40\" fill=\"#07073C\"/>");
				}
			}
			h.append("</svg>");
		}
		h.append("<span class=\"ldf-mono\">").append(LayoutPane.esc(digits)).append("</span>");
		if (f.path("checkDigit").asBoolean(true) && digits.matches("[0-9]+"))
		{
			final boolean ok = Barcode.checkDigitOk(digits);
			h.append(" <span class=\"ldf-badge ").append(ok ? "ldf-t-ok" : "ldf-t-bad").append("\">").append(LayoutPane.esc(RuntimeText.get(ok ? "checkOk" : "checkBad", rc.locale))).append("</span>");
		}
		return h.append("</span>").toString();
	}
}
